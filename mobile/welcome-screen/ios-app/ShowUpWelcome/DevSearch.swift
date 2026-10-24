//  DevSearch.swift
//  ShowUp · date search on an in-memory server (debug only)
//
//  The Swift twin of `dev/DevSearchStore.kt`: once the fake Check-In commits, Search opens on the
//  samples, so the profile, the like flow, the coach, the viewer and the way to Filters and back can be
//  walked with no backend. The real host routing and the real model; only the server is swapped.
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
        return .success(())
    }

    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure> {
        try? await Task.sleep(nanoseconds: latency / 3)
        queue.removeAll { $0.userId == targetUserId }
        return .success(())
    }

    func hideCoach() async -> Result<Void, CallFailure> {
        coachHidden = true
        return .success(())
    }

    func likesOpenCount() async -> Result<Int, CallFailure> { .success(2) }

    func session() async -> Result<SearchSession, CallFailure> {
        .success(SearchSession(premium: premium, coachHidden: coachHidden))
    }

    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure> {
        self.premium = premium
        return .success(())
    }
}
#endif
