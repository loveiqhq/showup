//
//  SearchRepository.swift
//  ShowUp · what Date search reads and writes, behind one protocol (SHOWUP-184, 185, 186)
//
//  The Swift twin of `search/SearchRepository.kt`. The screen and its model never see a generated
//  type: the server's payload is read here into the app's own `SearchProfile`, and a value this app
//  does not know is dropped rather than drawn. `DevSearchStore` implements the same protocol in
//  memory, so every state can be walked with no backend. Every call is the generated client's, by
//  operationId (`openapi.json`).
//

import Foundation
import ShowUpAPI

/// A written prompt on someone's profile: its topic id (the app's own text) and the answer.
struct SearchPrompt: Equatable, Sendable {
    let topicId: String
    let answer: String
}

/// A voice note or a video: where to load it, its real length, and the prompt it answers.
struct SearchMedia: Equatable, Sendable {
    let url: URL?
    let durationMs: Int
    let promptId: String
}

/// A charm from a past date (callout 21). Nothing sends one yet.
struct Charm: Equatable, Sendable {
    let label: String
    let star: Bool
}

/// One profile on the Search tab. Every section's source is optional: an empty list or a nil is a
/// section the screen does not draw ("Every section is optional").
struct SearchProfile: Equatable, Sendable {
    var userId: String
    var name: String
    /// Nil when the person hides their age: the name stands alone.
    var age: Int?
    /// Rounded by the server: 0 is "< 1 km".
    var distanceKm: Int
    var district: String?
    var meetAt: Date
    /// `today` or `tomorrow`, by the Check-In's 7am day, from the server.
    var meetDay: String
    var attendedCount: Int
    var motivation: String?
    var vibes: [String]
    var uplift: String?
    /// Loadable URLs, in profile order. A nil is a photo whose address could not be read: drawn as
    /// its placeholder wash, as a photo still loading is.
    var photos: [URL?]
    var prompts: [SearchPrompt]
    var voice: SearchMedia?
    var video: SearchMedia?
    var essentials: EssentialAnswers
    /// How I live: field id to value id, answered groups only.
    var howILive: [String: String]
    var interests: [String]
    /// "Charms from past dates" have no source until post-date ratings ship: always empty today.
    var charms: [Charm] = []
}

/// The viewer's own vibes and interests, for marking what a profile shares with them.
struct SearchViewer: Equatable, Sendable {
    var vibes: Set<String> = []
    var interests: Set<String> = []
}

/// One read of the queue. `viewer` comes from the same request, so shared vibes and interests are
/// marked against what the server matched on -- also on a launch straight onto Search.
struct SearchQueue: Equatable, Sendable {
    let resultsCount: Int
    let profiles: [SearchProfile]
    var viewer = SearchViewer()
}

/// What the session says about Search: the plan and the coach's stored opt-out.
struct SearchSession: Equatable, Sendable {
    let premium: Bool
    let coachHidden: Bool
}

/// One card of the Premium grid (Search 02): the whole profile, and whether they liked the viewer.
struct GridCard: Equatable, Sendable {
    let profile: SearchProfile
    let likedYou: Bool
}

/// The Premium grid: the whole queue in queue order, only people still available.
struct SearchGrid: Equatable, Sendable {
    let resultsCount: Int
    let cards: [GridCard]
    var viewer = SearchViewer()
}

/// One person's status on the re-check. A status this app does not know (a newer server) is not here
/// at all, which the model reads as "no change" -- never as closed.
enum CardStatus: Equatable, Sendable {
    /// Still available: the earliest hour both hold, which may have moved.
    case available(meetAt: Date, meetDay: String)
    case closed(CloseReason)
}

/// One like the viewer received (Search 03). Open: `profile` to open, and the message if one was
/// written. Closed: the name and photo only.
struct LikeReceived: Equatable, Sendable {
    let userId: String
    let name: String
    let age: Int?
    /// The first photo, or nil for none (the slot draws its placeholder).
    let photo: URL?
    let closed: CloseReason?
    let message: String?
    let profile: SearchProfile?
}

/// The Likes tab, as read once per visit, in the server's order.
struct LikesReceived: Equatable, Sendable {
    let openCount: Int
    let closedCount: Int
    let cards: [LikeReceived]
    var viewer = SearchViewer()
}

/// What the server said to a Confirm date (Search 02, 03).
enum ConfirmAnswer: Equatable, Sendable {
    /// The first confirm to arrive: a match.
    case won
    /// The like had closed first; the sorry sheet says why.
    case refused(CloseReason)
}

protocol SearchStoring: Sendable {
    /// The queue, with `meetDay` worked out in `zone` (an IANA id).
    func queue(zone: String) async -> Result<SearchQueue, CallFailure>
    /// Sends a like. Idempotent per person on the server, so a retry after a timeout never makes a
    /// second one -- which is what lets the model retry it silently.
    func like(targetUserId: String, message: String?, position: Int) async -> Result<Void, CallFailure>
    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure>
    /// `Don't show again`: the one act that sets the server's coach flag.
    func hideCoach() async -> Result<Void, CallFailure>
    func likesOpenCount() async -> Result<Int, CallFailure>
    func session() async -> Result<SearchSession, CallFailure>
    /// Dev menu only: the plan switch, a 404 in production.
    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure>
    /// The Premium grid, with `meetDay` worked out in `zone`.
    func grid(zone: String) async -> Result<SearchGrid, CallFailure>
    /// The availability re-check: every card on screen in one call.
    func availability(userIds: [String], zone: String) async -> Result<[String: CardStatus], CallFailure>
    /// The Likes tab, once per visit. Reading it marks the closed cards on it seen.
    func likesReceived(zone: String) async -> Result<LikesReceived, CallFailure>
    /// `Confirm date`: a like to someone who liked the viewer, sent at once and waited for -- never in
    /// the background, because the sheet waits on the answer. Idempotent on the server, so a retry
    /// after a timeout answers with the like already made.
    func confirm(targetUserId: String, position: Int) async -> Result<ConfirmAnswer, CallFailure>
}

struct SearchRepository: SearchStoring {
    let api: ShowUpAPI

    func queue(zone: String) async -> Result<SearchQueue, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getSearchQueue(query: .init(tz: zone)) {
                let dto = try ok.body.json
                let viewer = dto.viewer.value1
                return .success(SearchQueue(
                    resultsCount: dto.resultsCount,
                    profiles: dto.profiles.map { SearchProfile($0, api: api) },
                    viewer: SearchViewer(vibes: Set(viewer.vibes), interests: Set(viewer.interests))))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func like(targetUserId: String, message: String?, position: Int) async -> Result<Void, CallFailure> {
        let body = Components.Schemas.CreateLikeDto(message: message, position: position, targetUserId: targetUserId)
        do {
            if case .created = try await api.client.likeProfile(body: .json(body)) { return .success(()) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure> {
        // By wire value, as everywhere here: a value the contract cannot carry fails, never sends
        // something else.
        guard let wire = Components.Schemas.SearchPassType(rawValue: type.wire) else { return .failure(.server) }
        let body = Components.Schemas.CreateSearchPassDto(
            passType: Components.Schemas.CreateSearchPassDto.passTypePayload(value1: wire),
            targetUserId: targetUserId)
        do {
            if case .noContent = try await api.client.passSearchProfile(body: .json(body)) { return .success(()) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func hideCoach() async -> Result<Void, CallFailure> {
        do {
            if case .noContent = try await api.client.hideSearchCoach() { return .success(()) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func likesOpenCount() async -> Result<Int, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getLikesOpenCount() { return .success(try ok.body.json.count) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func session() async -> Result<SearchSession, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getCurrentUser() {
                let me = try ok.body.json
                // Read tolerantly: a tier this app does not know is Freemium, never Premium.
                return .success(SearchSession(premium: me.planTier == "premium", coachHidden: me.searchCoachHidden))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure> {
        guard let tier = Components.Schemas.PlanTier(rawValue: premium ? "premium" : "freemium") else {
            return .failure(.server)
        }
        let body = Components.Schemas.SetPlanTierDto(planTier: Components.Schemas.SetPlanTierDto.planTierPayload(value1: tier))
        do {
            if case .noContent = try await api.client.setDevPlanTier(body: .json(body)) { return .success(()) }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func grid(zone: String) async -> Result<SearchGrid, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getSearchGrid(query: .init(tz: zone)) {
                let dto = try ok.body.json
                return .success(SearchGrid(
                    resultsCount: dto.resultsCount,
                    cards: dto.cards.map { GridCard(profile: SearchProfile($0.profile, api: api), likedYou: $0.likedYou) },
                    viewer: SearchViewer(vibes: Set(dto.viewer.vibes), interests: Set(dto.viewer.interests))))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func availability(userIds: [String], zone: String) async -> Result<[String: CardStatus], CallFailure> {
        let body = Components.Schemas.SearchAvailabilityRequestDto(userIds: userIds)
        do {
            if case .ok(let ok) = try await api.client.getSearchAvailability(query: .init(tz: zone), body: .json(body)) {
                var statuses: [String: CardStatus] = [:]
                for s in try ok.body.json.statuses {
                    if let status = readStatus(s.status, meetAt: s.meetAt, meetDay: s.meetDay) { statuses[s.userId] = status }
                }
                return .success(statuses)
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func likesReceived(zone: String) async -> Result<LikesReceived, CallFailure> {
        do {
            if case .ok(let ok) = try await api.client.getLikesReceived(query: .init(tz: zone)) {
                let dto = try ok.body.json
                return .success(LikesReceived(
                    openCount: dto.openCount,
                    closedCount: dto.closedCount,
                    // A card that is neither open (with its profile) nor closed for a known reason is
                    // one this app cannot draw: dropped, never guessed at.
                    cards: dto.cards.compactMap { LikeReceived($0, api: api) },
                    viewer: SearchViewer(vibes: Set(dto.viewer.vibes), interests: Set(dto.viewer.interests))))
            }
            return .failure(.server)
        } catch {
            return .failure(Self.failure(error))
        }
    }

    func confirm(targetUserId: String, position: Int) async -> Result<ConfirmAnswer, CallFailure> {
        let body = Components.Schemas.CreateLikeDto(message: nil, position: position, targetUserId: targetUserId)
        do {
            if case .created(let created) = try await api.client.likeProfile(body: .json(body)) {
                let dto = try created.body.json
                return .success(confirmAnswer(matched: dto.matched, closed: dto.closed))
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
}

/// A re-check status as the app reads it: `available` with its hour, or closed for a reason it knows.
/// Anything else -- a value from a newer server, an available with no hour -- is nil: no change.
func readStatus(_ status: String, meetAt: Date?, meetDay: String?) -> CardStatus? {
    if status == "available" {
        guard let meetAt, let meetDay else { return nil }
        return .available(meetAt: meetAt, meetDay: meetDay)
    }
    return CloseReason.of(status).map { .closed($0) }
}

/// A confirm's answer. Not a match and no reason this app knows: the confirm did not make a date, and
/// `no_overlap` is the one reason that never claims a match that did not happen.
func confirmAnswer(matched: Bool, closed: String?) -> ConfirmAnswer {
    matched ? .won : .refused(CloseReason.of(closed) ?? .noOverlap)
}

extension LikeReceived {
    /// The payload, read into the app's own type -- or nil for a card this app cannot draw.
    init?(_ dto: Components.Schemas.LikeReceivedDto, api: ShowUpAPI) {
        var reason: CloseReason?
        if let wire = dto.closed {
            guard let known = CloseReason.of(wire) else { return nil }
            reason = known
        }
        var opened: SearchProfile?
        if reason == nil {
            guard let profile = dto.profile?.value1 else { return nil }
            opened = SearchProfile(profile, api: api)
        }
        self.init(
            userId: dto.userId,
            name: dto.name,
            age: dto.age,
            photo: dto.photo.flatMap { api.absoluteURL($0.value1.url) },
            closed: reason,
            message: reason == nil ? dto.message : nil,
            profile: opened)
    }
}

extension SearchProfile {
    /// The payload, read into the app's own types. Photo and media paths become loadable URLs here.
    init(_ dto: Components.Schemas.SearchProfileDto, api: ShowUpAPI) {
        let e = dto.essentials
        let h = dto.howILive
        var howILive: [String: String] = [:]
        if let v = h.activity { howILive["activity"] = v }
        if let v = h.diet { howILive["diet"] = v }
        if let v = h.alcohol { howILive["alcohol"] = v }
        if let v = h.smoking { howILive["smoking"] = v }
        if let v = h.rhythm { howILive["rhythm"] = v }
        if let v = h.social { howILive["social"] = v }
        self.init(
            userId: dto.userId,
            name: dto.name,
            age: dto.age,
            distanceKm: dto.distanceKm,
            district: dto.district,
            meetAt: dto.meetAt,
            meetDay: dto.meetDay,
            attendedCount: dto.attendedCount,
            motivation: dto.motivation,
            vibes: dto.vibes,
            uplift: dto.uplift,
            photos: dto.photos.map { api.absoluteURL($0.url) },
            prompts: dto.prompts.map { SearchPrompt(topicId: $0.topicId, answer: $0.answer) },
            voice: dto.voice.map { SearchMedia(url: api.absoluteURL($0.url), durationMs: $0.durationMs, promptId: $0.promptId) },
            video: dto.video.map { SearchMedia(url: api.absoluteURL($0.url), durationMs: $0.durationMs, promptId: $0.promptId) },
            essentials: EssentialAnswers(
                heightCm: e.heightCm, gender: e.gender, orientation: e.orientation,
                datingLanguages: e.datingLanguages ?? [], education: e.education,
                religion: e.religion, politics: e.politics),
            howILive: howILive,
            interests: dto.interests)
    }
}
