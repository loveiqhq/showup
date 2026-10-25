//  DevSearch.swift
//  ShowUp · date search on an in-memory server (debug only)
//
//  The Swift twin of `dev/DevSearchStore.kt`: once the fake Check-In commits, Search opens on the
//  samples, so the profile, the like flow, the coach, the viewer and the way to Filters and back can be
//  walked with no backend. The real host routing and the real model; only the server is swapped.
//
//  PREMIUM (the dev menu's plan switch) opens the grid and the Likes tab on the reference's people,
//  with the reference's staged answers -- the kit's demo props, here the server's: a confirm to Leonie
//  is refused (`matched`, frame H), a confirm to Anna wins; the first re-check closes Sofia (`matched`)
//  and moves Anna's meet time to 18:00 tomorrow (frame J); Clara's closed like shows on the first
//  Likes visit only (seen on load).
//
//  DEBUG BUILDS ONLY: the whole file is inside `#if DEBUG`. Nothing here is product behaviour.

#if DEBUG
import Foundation

actor DevSearchStore: SearchStoring {
    /// Nanoseconds every call takes: long enough to see the loading state, short enough to walk.
    private let latency: UInt64
    /// Leonie in full, then Mara and Ana. A like or a pass takes someone out, as the server does.
    private var queue: [SearchProfile] = [SearchSamples.full] + SearchSamples.next
    private var coachHidden = false
    private var premium = false

    /// Who the viewer has answered: out of the next grid, and -- for a liker -- off the list.
    private var answered: Set<String> = []
    /// Clara's closed card is seen on the first Likes visit and gone from the next.
    private var closedSeen = false
    /// The first re-check is the staged one.
    private var rechecks = 0

    init(latencyMs: Int = 900) {
        latency = UInt64(max(latencyMs, 0)) * 1_000_000
    }

    func queue(zone: String) async -> Result<SearchQueue, CallFailure> {
        try? await Task.sleep(nanoseconds: latency)
        return .success(SearchQueue(resultsCount: queue.count, profiles: queue, viewer: SearchSamples.viewer))
    }

    func like(targetUserId: String, message: String?, position: Int) async -> Result<Void, CallFailure> {
        try? await Task.sleep(nanoseconds: latency / 3)
        queue.removeAll { $0.userId == targetUserId }
        answered.insert(targetUserId)
        return .success(())
    }

    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure> {
        try? await Task.sleep(nanoseconds: latency / 3)
        queue.removeAll { $0.userId == targetUserId }
        if type == .notInterested { answered.insert(targetUserId) }
        return .success(())
    }

    func hideCoach() async -> Result<Void, CallFailure> {
        coachHidden = true
        return .success(())
    }

    func likesOpenCount() async -> Result<Int, CallFailure> {
        .success(SearchSamples.likes.filter { $0.closed == nil && !answered.contains($0.userId) }.count)
    }

    func session() async -> Result<SearchSession, CallFailure> {
        .success(SearchSession(premium: premium, coachHidden: coachHidden))
    }

    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure> {
        self.premium = premium
        return .success(())
    }

    func grid(zone: String) async -> Result<SearchGrid, CallFailure> {
        try? await Task.sleep(nanoseconds: latency)
        let cards = SearchSamples.grid.filter { !answered.contains($0.profile.userId) }
        return .success(SearchGrid(resultsCount: cards.count, cards: cards, viewer: SearchSamples.viewer))
    }

    func availability(userIds: [String], zone: String) async -> Result<[String: CardStatus], CallFailure> {
        try? await Task.sleep(nanoseconds: latency / 3)
        let staged = rechecks == 0
        rechecks += 1
        var statuses: [String: CardStatus] = [:]
        for id in userIds {
            let profile = SearchSamples.grid.first { $0.profile.userId == id }?.profile
            if staged && id == "sample-sofia" {
                statuses[id] = .closed(.matched)
            } else if staged, id == "sample-anna", let profile {
                statuses[id] = .available(meetAt: profile.meetAt.addingTimeInterval(24 * 3600), meetDay: "tomorrow")
            } else if let profile {
                statuses[id] = .available(meetAt: profile.meetAt, meetDay: profile.meetDay)
            } else {
                statuses[id] = .closed(.noOverlap)
            }
        }
        return .success(statuses)
    }

    func likesReceived(zone: String) async -> Result<LikesReceived, CallFailure> {
        try? await Task.sleep(nanoseconds: latency)
        let open = SearchSamples.likes.filter { $0.closed == nil && !answered.contains($0.userId) }
        let closed = closedSeen ? [] : SearchSamples.likes.filter { $0.closed != nil }
        closedSeen = true
        return .success(LikesReceived(openCount: open.count, closedCount: closed.count, cards: open + closed,
                                      viewer: SearchSamples.viewer))
    }

    func confirm(targetUserId: String, position: Int) async -> Result<ConfirmAnswer, CallFailure> {
        // Long enough to see Confirm date wait for the answer.
        try? await Task.sleep(nanoseconds: latency)
        answered.insert(targetUserId)
        return .success(targetUserId == "sample-leonie" ? .refused(.matched) : .won)
    }
}
#endif
