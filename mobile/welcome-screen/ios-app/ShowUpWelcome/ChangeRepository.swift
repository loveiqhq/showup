// ChangeRepository.swift
// ShowUp · what Date 05 asks the backend, and the app's own types for the answers (SHOWUP-196)
//
// Every call goes through the GENERATED client. Nothing here describes a URL or a request body. The model is the
// app's own so a screen never imports a generated type: a regenerated enum case must not change what a screen
// reads or what the phone sends. Android's `ChangeRepository.kt`, field for field.
//
// NO PLACE NAME, ADDRESS, COORDINATE OR DISTANCE LEAVES THIS FILE FOR ANALYTICS. They are values for the screen;
// the events carry ids of dates and kinds only (ticket, Data and legal).

import Foundation
import ShowUpAPI

/// What is suggested: a later time on the date's day, or another public place.
enum ChangeKind: String, Hashable, Sendable {
    case time
    case place
}

/// §42 `change_entry`: where the picker was opened from. `cancel_sheet` is Date 07's, not built here.
enum ChangeEntry: String, Hashable, Sendable {
    case coordinateTile = "coordinate_tile"
    case chatChip = "chat_chip"
    case counterCard = "counter_card"
    case counterThread = "counter_thread"
}

/// Where `I agree` was tapped: the request card on the Date screen, or the card in the thread.
enum AgreeSurface: String, Hashable, Sendable {
    case coordinateCard = "coordinate_card"
    case thread
}

enum ChangeStatus: String, Hashable, Sendable {
    case pending, agreed, replaced, expired
}

/// The three kinds of public place the picker lists (R4).
enum PlaceType: String, Hashable, Sendable, Codable {
    case cafe, bar, restaurant
}

/// One row of the place picker. `distanceMeters` is from the BINDING meet place, whole metres.
struct PlaceOption: Equatable, Hashable, Sendable, Codable {
    var placeId: String
    var name: String
    var type: PlaceType
    var address: String
    var distanceMeters: Int
}

/// The places answer: `google` is true only when Google answered and its terms ask for the attribution line.
struct PlacesList: Equatable, Sendable {
    var google: Bool
    var places: [PlaceOption]
}

/// A suggested place, as the server stored it.
struct ChangePlace: Equatable, Sendable {
    var placeId: String
    var name: String
    var type: PlaceType
    var address: String
}

/// One suggestion, in every state it can be read in. `time` is set for a time, `place` for a place. The `expired*`
/// fields are the binding values AT the moment it expired, for the system line. A pending item from the date
/// payload carries no status history: `status` is pending and the rest nil.
struct ChangeSuggestion: Equatable, Sendable {
    var id: String
    var kind: ChangeKind
    var byYou: Bool
    var time: Date?
    var place: ChangePlace?
    var wasTime: Date
    var wasPlaceName: String?
    var deadline: Date
    var createdAt: Date
    var status: ChangeStatus = .pending
    var answeredAt: Date? = nil
    var expiredAt: Date? = nil
    var expiredBindingTime: Date? = nil
    var expiredBindingPlaceName: String? = nil
}

/// The `change` block of the date payload. `slots` are the server's later start times (the client never computes
/// them); `timeWas` and `placeWas` are the ORIGINAL values after an agreed change, for `Changed · was {original}`.
struct DateChange: Equatable, Sendable {
    var cutoffAt: Date
    var allowed: Bool
    var slots: [Date]
    var timeWas: Date?
    var placeWas: String?
    var pending: [ChangeSuggestion]
}

/// What `Suggest` sends. `clientId` is the same on every retry of one send (R14).
struct SuggestRequest: Equatable, Sendable {
    var kind: ChangeKind
    var time: Date?
    var placeId: String?
    var entry: ChangeEntry
    var fromSearch: Bool?
    var clientId: String
    var tz: String
}

/// What `Suggest` came back with. `stale` is 409: the server refuses a suggestion that can no longer be made (past
/// the cutoff, after a check-in, a slot no longer offered, the place now binding), and asking again cannot change
/// that -- so it is not K: the picker closes and the date is read again. Everything else that is not a success
/// (`failed`: no response, 503, any other status) is K, and a retry may succeed. Android's `SuggestResult`.
enum SuggestResult: Equatable, Sendable {
    case sent(ChangeSuggestion)
    case stale
    case failed(CallFailure)
}

/// Date 05's calls, behind a protocol so the models are tested with no network.
protocol ChangeStoring: Sendable {
    /// Public places near the binding meet place, or matching `query` (nil or blank: nearby).
    func places(dateId: String, query: String?) async -> Result<PlacesList, CallFailure>
    /// A suggestion; a repeat of the same clientId is answered with the stored one.
    func suggest(dateId: String, request: SuggestRequest) async -> SuggestResult
    /// `I agree`: the suggestion becomes binding for both. A refusal (no longer pending) is a failure.
    func agree(dateId: String, suggestionId: String, surface: AgreeSurface) async -> Result<ChangeSuggestion, CallFailure>
    /// Every suggestion of the date, oldest first, all statuses: the thread's cards and system lines.
    func suggestions(dateId: String) async -> Result<[ChangeSuggestion], CallFailure>
}

struct ChangeRepository: ChangeStoring {
    let api: ShowUpAPI

    func places(dateId: String, query: String?) async -> Result<PlacesList, CallFailure> {
        let q = query.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
        do {
            if case .ok(let ok) = try await api.client.listChangePlaces(path: .init(id: dateId), query: .init(q: q)) {
                let dto = try ok.body.json
                return .success(PlacesList(
                    google: dto.attribution?.rawValue == "google",
                    places: dto.places.compactMap { PlaceOption($0) }))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func suggest(dateId: String, request: SuggestRequest) async -> SuggestResult {
        guard let uuid = UUID(uuidString: request.clientId) else { return .failed(.server) }
        do {
            // By wire value, not case name: a regenerated enum case must not change what is sent.
            let dto = Components.Schemas.SuggestChangeDto(
                clientId: Self.wire(request.clientId, uuid),
                entry: .init(value1: try Self.make(request.entry.rawValue)),
                fromSearch: request.fromSearch,
                kind: .init(value1: try Self.make(request.kind.rawValue)),
                placeId: request.placeId,
                time: request.time,
                tz: request.tz)
            // The contract documents only 201, so a 409 arrives as `.undocumented` (as `endInChat`'s does).
            switch try await api.client.suggestDateChange(path: .init(id: dateId), body: .json(dto)) {
            case .created(let created):
                guard let suggestion = ChangeSuggestion(try created.body.json) else { return .failed(.server) }
                return .sent(suggestion)
            case let .undocumented(statusCode, _) where statusCode == 409:
                return .stale
            default:
                return .failed(.server)
            }
        } catch {
            return .failed(Self.failure(error))
        }
    }

    func agree(dateId: String, suggestionId: String, surface: AgreeSurface) async -> Result<ChangeSuggestion, CallFailure> {
        do {
            let dto = Components.Schemas.AgreeChangeDto(surface: try Self.make(surface.rawValue))
            if case .ok(let ok) = try await api.client.agreeDateChange(path: .init(id: dateId, sid: suggestionId), body: .json(dto)),
               let suggestion = ChangeSuggestion(try ok.body.json) {
                return .success(suggestion)
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func suggestions(dateId: String) async -> Result<[ChangeSuggestion], CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.listDateChangeSuggestions(path: .init(id: dateId)) {
                return .success(try ok.body.json.compactMap { ChangeSuggestion($0) })
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    /// No response is `network`; a response that could not be read is the server's.
    private static func failure(_ error: any Error) -> CallFailure {
        ShowUpAPI.isNoResponse(error) ? .network : .server
    }

    /// `clientId` as the generated request types it: the contract says `format: uuid`, which the generator in use
    /// reads as a string and a later one may read as `UUID` -- whichever type the initialiser wants, one of these
    /// two type-checks (`ChatRepository.wire`).
    private static func wire(_ text: String, _ uuid: UUID) -> String { text }
    private static func wire(_ text: String, _ uuid: UUID) -> UUID { uuid }

    /// A generated enum built from its wire text, whatever the generator named it; the call site's parameter type
    /// decides which. Nothing is guessed: an unknown value throws, and the call is a server failure.
    private static func make<T: RawRepresentable>(_ raw: String) throws -> T where T.RawValue == String {
        guard let value = T(rawValue: raw) else { throw ChangeWireError.unknown }
        return value
    }
}

private enum ChangeWireError: Error { case unknown }

// ── the generated types, read once ──────────────────────────────────────────────────────────────

/// A uuid as the generated response types it, in lower case either way.
private func idText(_ id: String) -> String { id.lowercased() }
private func idText(_ id: UUID) -> String { id.uuidString.lowercased() }

extension PlaceOption {
    init?(_ dto: Components.Schemas.PlaceOptionDto) {
        guard let type = PlaceType(rawValue: dto._type.rawValue) else { return nil }
        self.init(placeId: dto.placeId, name: dto.name, type: type, address: dto.address,
                  distanceMeters: Int(dto.distanceMeters.rounded()))
    }
}

extension ChangePlace {
    init?(_ dto: Components.Schemas.ChangePlaceDto) {
        guard let type = PlaceType(rawValue: dto._type.rawValue) else { return nil }
        self.init(placeId: dto.placeId, name: dto.name, type: type, address: dto.address)
    }
}

extension ChangeSuggestion {
    init?(_ dto: Components.Schemas.ChangeSuggestionDto) {
        guard let kind = ChangeKind(rawValue: dto.kind.value1.rawValue),
              let status = ChangeStatus(rawValue: dto.status.value1.rawValue) else { return nil }
        self.init(
            id: idText(dto.id), kind: kind, byYou: dto.byYou, time: dto.time,
            place: dto.place.flatMap { ChangePlace($0.value1) },
            wasTime: dto.wasTime, wasPlaceName: dto.wasPlace?.value1.name,
            deadline: dto.deadline, createdAt: dto.createdAt, status: status,
            answeredAt: dto.answeredAt, expiredAt: dto.expiredAt,
            expiredBindingTime: dto.expiredBindingTime,
            expiredBindingPlaceName: dto.expiredBindingPlace?.value1.name)
    }

    /// A pending item of the date payload: the same shape without the status history.
    init?(_ dto: Components.Schemas.ChangePendingDto) {
        guard let kind = ChangeKind(rawValue: dto.kind.value1.rawValue) else { return nil }
        self.init(
            id: idText(dto.id), kind: kind, byYou: dto.byYou, time: dto.time,
            place: dto.place.flatMap { ChangePlace($0.value1) },
            wasTime: dto.wasTime, wasPlaceName: dto.wasPlace?.value1.name,
            deadline: dto.deadline, createdAt: dto.createdAt)
    }
}

extension DateChange {
    init(_ dto: Components.Schemas.ChangeStateDto) {
        self.init(
            cutoffAt: dto.cutoffAt, allowed: dto.allowed, slots: dto.slots, timeWas: dto.timeWas,
            placeWas: dto.placeWas?.value1.name,
            pending: dto.pending.compactMap { ChangeSuggestion($0) })
    }
}
