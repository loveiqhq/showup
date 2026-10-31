//
//  CheckInRepository.swift
//  ShowUp · the 48hrs Check-In's server calls (SHOWUP-178 to SHOWUP-183)
//
//  The Swift twin of `checkin/CheckInRepository.kt`. Behind `CheckInStoring` so the model is tested
//  with no network. Every call is the generated client's, by operationId (`openapi.json`); nothing
//  here hand-writes a request.
//
//  CANCELLATION IS NOT A FAILURE, on either platform. Kotlin rethrows `CancellationException`; here
//  a call that ends because its task was cancelled returns its failure value like any other, and
//  `CheckInModel` checks `Task.isCancelled` after every await before reporting anything -- so the
//  end of a task is never a reported `check_in_commit_failed` for a commit nobody is waiting on.
//

import Foundation
import ShowUpAPI

/// What the Check-In opens with -- `GET /me/check-in`.
struct CheckInState: Equatable, Sendable {
    /// `window_id` on every Check-In event. Nil only before profile creation completes.
    var windowId: String?
    /// The gate reopens at Today once true, at the intro before.
    var introPassed: Bool
    /// A committed Check-In in the current window: date search is unlocked.
    var active: Bool
    var firstCheckIn: Bool
    /// The last accepted answers -- they pre-select Motivation and Vibe.
    var lastMotivation: String?
    var lastVibes: Set<String>
    /// The saved filter settings, or the profile pre-fill for a user with none.
    var filters: CheckInFilters
    /// The remote flag `filters_dealbreakers` (Check-In 05b), from our own server: whether Filters
    /// shows its two Dealbreaker switches. Off until matching applies the soft range.
    var filtersDealbreakers: Bool = false
}

/// One picked hour on the wire: the instant it starts and its tier.
struct CommitSlot: Equatable, Sendable {
    let startsAt: Date
    let tier: SlotTier
}

/// `Find my date`.
struct CommitRequest: Equatable, Sendable {
    let windowId: String
    let slots: [CommitSlot]
    let motivation: String
    let vibes: Set<String>
    /// Trimmed; nil when empty -- the server stores no uplift, never "".
    let uplift: String?
    /// Where the phone is, and its district: what Search measures from. Nil without location.
    var place: CheckInPlace? = nil
}

/// How a commit ended -- each maps to one state of the Uplift screen (story 06).
enum CommitResult: Equatable, Sendable {
    case saved(CheckInState)
    /// The server refused the text for contact info: state C, not E.
    case contactInfo
    /// No response -- §32 `network`.
    case network
    /// A response that is not a save -- §32 `server`.
    case server
    /// Every picked hour has started -- the flow sat open past them -- so there is nothing to
    /// commit. Found on the device before sending, or refused by the server when its clock is ahead
    /// of the device's. Not a failed save: the user picks again.
    case noHours
}

/// Why a call did not land: no response (§32 `network`), or one that is not a success (`server`).
/// The commit reports which, so a 500 on the filter save is never logged as the user being offline.
enum CallFailure: Error, Equatable, Sendable { case network, server }

protocol CheckInStoring: Sendable {
    func load() async -> Result<CheckInState, CallFailure>
    func markIntroPassed() async -> Bool
    func saveAnswers(motivation: String?, vibes: Set<String>?) async -> Bool
    /// `withDealbreakers`: the remote flag is on, so the two switches are sent; off, they are left out.
    func saveFilters(_ filters: CheckInFilters, withDealbreakers: Bool) async -> Result<Void, CallFailure>
    func commit(_ request: CommitRequest) async -> CommitResult
}

struct CheckInRepository: CheckInStoring {
    let api: ShowUpAPI

    /// The API's error code for an uplift refused for contact info (`check-in-flow.ts`).
    private static let contactInfoError = "uplift_contact_info"
    /// The API's error code for a commit whose every hour has started (`check-in-flow.ts`).
    private static let noHoursError = "check_in_no_hours"

    func load() async -> Result<CheckInState, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getCheckInState() {
                return .success(CheckInState(try ok.body.json))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func markIntroPassed() async -> Bool {
        do {
            if case .noContent = try await api.client.markCheckInIntroPassed() { return true }
            return false
        } catch {
            return false
        }
    }

    func saveAnswers(motivation: String?, vibes: Set<String>?) async -> Bool {
        // A value the contract cannot carry fails, as Android's `first {}` does -- never a smaller
        // save that answers 204 for something that was not stored.
        var wireMotivation: Components.Schemas.CheckInMotivation?
        if let motivation {
            guard let m = Components.Schemas.CheckInMotivation(rawValue: motivation) else { return false }
            wireMotivation = m
        }
        var wireVibes: [Components.Schemas.CheckInVibe]?
        if let vibes {
            guard let w = Self.wireVibes(vibes) else { return false }
            wireVibes = w
        }
        let body = Components.Schemas.SaveCheckInAnswersDto(
            motivation: wireMotivation.map { Components.Schemas.SaveCheckInAnswersDto.motivationPayload(value1: $0) },
            vibes: wireVibes)
        do {
            if case .noContent = try await api.client.saveCheckInAnswers(body: .json(body)) { return true }
            return false
        } catch {
            return false
        }
    }

    func saveFilters(_ filters: CheckInFilters, withDealbreakers: Bool) async -> Result<Void, CallFailure> {
        let genders = CheckInGender.allCases.filter { filters.genders.contains($0.id) }
        let wireGenders = genders.compactMap { Components.Schemas.MeetGender(rawValue: $0.id) }
        // Every gender or none: a save of fewer genders than the user picked is not their settings.
        guard wireGenders.count == genders.count else { return .failure(.server) }
        // Arguments in the schema's declared order, which Swift requires.
        // "With the flag off ... the fields are not sent": nil, and the server keeps what it has.
        let body = Components.Schemas.SaveFilterSettingsDto(
            ageDealbreaker: withDealbreakers ? filters.ageDealbreaker : nil,
            distanceDealbreaker: withDealbreakers ? filters.distanceDealbreaker : nil,
            distanceKm: filters.distanceKm,
            maxAge: filters.maxAgeOrNil,
            meetGender: wireGenders,
            minAge: filters.minAge)
        do {
            if case .ok = try await api.client.saveFilterSettings(body: .json(body)) { return .success(()) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func commit(_ request: CommitRequest) async -> CommitResult {
        // Kotlin's `UUID.fromString` and `entries.first` throw on a value the wire cannot carry, and
        // that lands in its `Server` catch; the same values end the same way here.
        guard let window = UUID(uuidString: request.windowId),
              let motivation = Components.Schemas.CheckInMotivation(rawValue: request.motivation),
              let vibes = Self.wireVibes(request.vibes) else {
            return .server
        }
        let body = Components.Schemas.CommitCheckInDto(
            district: request.place?.district,
            latitude: request.place?.latitude,
            longitude: request.place?.longitude,
            motivation: Components.Schemas.CommitCheckInDto.motivationPayload(value1: motivation),
            slots: request.slots.map { slot in
                Components.Schemas.CheckInSlotDto(
                    startsAt: slot.startsAt.formatted(.iso8601),
                    tier: Components.Schemas.CheckInSlotDto.tierPayload(
                        value1: slot.tier == .preferred ? .preferred : .available))
            },
            uplift: request.uplift,
            vibes: vibes,
            windowId: Self.wire(request.windowId, window))
        do {
            switch try await api.client.commitCheckIn(body: .json(body)) {
            case .ok(let ok):
                return .saved(CheckInState(try ok.body.json))
            case .unprocessableContent(let refused):
                // A 422 naming `uplift_contact_info` is the server's contact-info check, not a
                // failed save.
                return (try? refused.body.json)?.error == Self.contactInfoError ? .contactInfo : .server
            case .badRequest(let refused):
                // A 400 naming `check_in_no_hours`: every picked hour has started, by the
                // server's clock.
                return (try? refused.body.json)?.error == Self.noHoursError ? .noHours : .server
            default:
                return .server
            }
        } catch {
            return ShowUpAPI.isNoResponse(error) ? .network : .server
        }
    }

    /// §31 in list order, de-duplicated by being a set -- what the server stores. Nil when a vibe
    /// has no wire value: the call fails rather than sending fewer vibes than were picked.
    private static func wireVibes(_ vibes: Set<String>) -> [Components.Schemas.CheckInVibe]? {
        let ordered = vibeOrder.filter { vibes.contains($0) }
        let wire = ordered.compactMap(Components.Schemas.CheckInVibe.init(rawValue:))
        return wire.count == ordered.count ? wire : nil
    }

    /// No response is `network`; a response that could not be read is the server's.
    private static func failure(_ error: any Error) -> CallFailure {
        ShowUpAPI.isNoResponse(error) ? .network : .server
    }

    /// `windowId` as the generated request types it. The contract says `format: uuid`; the
    /// generator in use reads that as a string, and a later one may read it as `UUID`. The value
    /// was checked as a UUID above either way, and whichever type the initialiser wants, one of
    /// these two is the one that type-checks.
    private static func wire(_ text: String, _ uuid: UUID) -> String { text }
    private static func wire(_ text: String, _ uuid: UUID) -> UUID { uuid }
}

extension CheckInState {
    /// The server's state, read into the app's own types. Unknown values a newer server sent are
    /// dropped.
    init(_ dto: Components.Schemas.CheckInStateDto) {
        let filters = dto.filters.value1
        self.init(
            windowId: Self.text(dto.windowId),
            introPassed: dto.introPassed,
            active: dto.active,
            firstCheckIn: dto.firstCheckIn,
            lastMotivation: dto.lastMotivation.flatMap { MotivationOption.of($0.value1.rawValue)?.id },
            lastVibes: Set(dto.lastVibes.map(\.rawValue).filter { vibeOrder.contains($0) }),
            filters: CheckInFilters(
                genders: Set(filters.meetGender.map(\.rawValue)),
                minAge: filters.minAge,
                maxAge: filters.maxAge ?? ageMax,
                distanceKm: filters.distanceKm,
                ageDealbreaker: filters.ageDealbreaker,
                distanceDealbreaker: filters.distanceDealbreaker),
            filtersDealbreakers: dto.filtersDealbreakers)
    }

    /// `windowId` as the generated response types it -- see `CheckInRepository.wire`.
    private static func text(_ id: String?) -> String? { id }
    private static func text(_ id: UUID?) -> String? { id?.uuidString.lowercased() }
}
