//
//  SearchModelTests.swift
//  ShowUp · Date search 01's owner, with no UI (SHOWUP-184)
//
//  The Swift twin of `SearchViewModelTest.kt`: every row of the ticket's tracking table, its order,
//  the events that must not fire, the coach's rule, the like flow, passes, playback and the way back
//  from Filters. Time is the model's injected clock; tasks run with a short `settle`.
//

import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
    func clear() { events.removeAll() }
}

/// Leonie in full with playable media, then Mara and Ana -- the samples' queue.
private let playableQueue: [SearchProfile] = {
    var leonie = SearchSamples.full
    leonie.voice = SearchMedia(url: URL(string: "https://example.test/voice.m4a"), durationMs: 14_000, promptId: "relaxing_sound")
    leonie.video = SearchMedia(url: URL(string: "https://example.test/video.mp4"), durationMs: 10_000, promptId: "ideal_sunny_morning")
    return [leonie] + SearchSamples.next
}()

/// A server that records every call and can be made to fail.
private actor FakeStore: SearchStoring {
    var profiles = playableQueue
    var coachHidden = false
    /// False: the server has not stored the flag yet when the next session read arrives.
    var coachHideLands = true
    var queueFailure: CallFailure?
    /// How many like calls fail before one lands.
    var likeFailures = 0
    private(set) var likes: [(String, String?, Int)] = []
    private(set) var passes: [(String, SearchAnalytics.PassType)] = []
    private(set) var coachHides = 0
    private(set) var queueReads = 0

    func setProfiles(_ value: [SearchProfile]) { profiles = value }
    func setCoachHidden(_ value: Bool) { coachHidden = value }
    func setCoachHideLands(_ value: Bool) { coachHideLands = value }
    func setQueueFailure(_ value: CallFailure?) { queueFailure = value }
    func setLikeFailures(_ value: Int) { likeFailures = value }

    func queue(zone: String) async -> Result<SearchQueue, CallFailure> {
        queueReads += 1
        if let queueFailure { return .failure(queueFailure) }
        return .success(SearchQueue(resultsCount: profiles.count, profiles: profiles, viewer: SearchSamples.viewer))
    }

    func like(targetUserId: String, message: String?, position: Int) async -> Result<Void, CallFailure> {
        likes.append((targetUserId, message, position))
        if likeFailures > 0 {
            likeFailures -= 1
            return .failure(.network)
        }
        return .success(())
    }

    func pass(targetUserId: String, type: SearchAnalytics.PassType) async -> Result<Void, CallFailure> {
        passes.append((targetUserId, type))
        return .success(())
    }

    func hideCoach() async -> Result<Void, CallFailure> {
        coachHides += 1
        if coachHideLands { coachHidden = true }
        return .success(())
    }

    func likesOpenCount() async -> Result<Int, CallFailure> { .success(2) }

    func session() async -> Result<SearchSession, CallFailure> {
        .success(SearchSession(premium: false, coachHidden: coachHidden))
    }

    func setPremium(_ premium: Bool) async -> Result<Void, CallFailure> { .success(()) }

    // The Premium calls: a Freemium session never makes them (`PremiumModelTests` does).
    func grid(zone: String) async -> Result<SearchGrid, CallFailure> { .failure(.server) }
    func availability(userIds: [String], zone: String) async -> Result<[String: CardStatus], CallFailure> { .success([:]) }
    func likesReceived(zone: String) async -> Result<LikesReceived, CallFailure> { .failure(.server) }
    func confirm(targetUserId: String, position: Int) async -> Result<ConfirmAnswer, CallFailure> { .failure(.server) }
}

@MainActor
final class SearchModelTests: XCTestCase {

    private var store = FakeStore()
    private var events = Recorder()
    private var ms = 1_000
    private var playerClock: Int64 = 0

    override func setUp() {
        super.setUp()
        store = FakeStore()
        events = Recorder()
        ms = 1_000
        playerClock = 0
    }

    private func model(players: FakeMediaPlayerMaker? = nil) -> SearchModel {
        SearchModel(store: store, analytics: events, players: players,
                    millis: { [unowned self] in self.ms }, retryDelays: [0.01, 0.02, 0.04])
    }

    private func players(openingNanoseconds: UInt64 = 0) -> FakeMediaPlayerMaker {
        FakeMediaPlayerMaker(now: { [unowned self] in self.playerClock }, durationMs: 14_000,
                             openingNanoseconds: openingNanoseconds)
    }

    /// Lets the model's own tasks -- a read, a send, a tick -- run.
    private func settle(_ seconds: Double = 0.05) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func visited(players: FakeMediaPlayerMaker? = nil) async -> SearchModel {
        let m = model(players: players)
        m.visit(zone: "Europe/Berlin", filtersActive: ["distance_km"], referrer: .checkinUplift)
        await settle()
        return m
    }

    private func uncoached(players: FakeMediaPlayerMaker? = nil) async -> SearchModel {
        await store.setCoachHidden(true)
        let m = await visited(players: players)
        events.clear()
        return m
    }

    /// The nth event of that name.
    private func props(_ name: String, _ nth: Int = 0, file: StaticString = #filePath, line: UInt = #line) -> [String: any Sendable] {
        let all = events.of(name)
        guard all.count > nth else {
            XCTFail("\(name) fired \(all.count) times", file: file, line: line)
            return [:]
        }
        return all[nth]
    }

    // MARK: - the visit

    func testAVisitFiresScreenViewedOnceThenSearchViewedThenShowsTheCoach() async {
        let m = await visited()
        XCTAssertEqual(events.names, ["screen_viewed", SearchAnalytics.searchViewedName, SearchAnalytics.searchCoachShownName])
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "search_profile")
        XCTAssertEqual(props("screen_viewed")["screen_name"] as? String, "Search - Profile")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "checkin_uplift")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["layout"] as? String, "one_up")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["results_count"] as? Int, 3)
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["filters_active"] as? [String], ["distance_km"])
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["sensitivity_class"] as? Int, 0)
        XCTAssertTrue(m.state.coach)
        XCTAssertEqual(m.state.position, 1)
    }

    func testTheViewersVibesAndInterestsComeWithTheQueue() async {
        let m = await visited()
        XCTAssertEqual(m.state.viewer, SearchSamples.viewer)
    }

    func testTheCoachNeverShowsOnceStoredNorOverAnEmptyQueue() async {
        await store.setCoachHidden(true)
        let hidden = await visited()
        XCTAssertFalse(hidden.state.coach)
        XCTAssertTrue(events.of(SearchAnalytics.searchCoachShownName).isEmpty)

        events.clear()
        await store.setCoachHidden(false)
        await store.setProfiles([])
        let empty = await visited()
        XCTAssertTrue(empty.state.empty)
        XCTAssertFalse(empty.state.coach)
        XCTAssertTrue(events.of(SearchAnalytics.searchCoachShownName).isEmpty)
    }

    func testTheCoachShowsOnEveryVisitUntilTurnedOffNeverTwiceInOne() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: false)
        m.pass(.notNow)
        XCTAssertFalse(m.state.coach, "not twice in one visit")
        m.visit(zone: "Europe/Berlin", filtersActive: [], referrer: nil)
        await settle()
        XCTAssertTrue(m.state.coach, "again on the next visit")
        XCTAssertEqual(events.of(SearchAnalytics.searchCoachShownName).count, 2)
    }

    // MARK: - the coach's ways out

    func testGotItAndTheScrimCloseItForThisVisitAndStoreNothing() async {
        for method in [SearchAnalytics.CoachDismiss.close, .backdrop] {
            events.clear()
            ms = 1_000
            let m = await visited()
            ms = 3_500
            m.dismissCoach(method, dontShowAgain: false)
            await settle()
            let p = props(SearchAnalytics.searchCoachDismissedName)
            XCTAssertEqual(p["dismiss_method"] as? String, method.wire)
            XCTAssertEqual(p["dont_show_again"] as? Bool, false)
            XCTAssertEqual(p["visible_ms"] as? Int, 2_500)
            XCTAssertFalse(m.state.coach)
        }
        let hides = await store.coachHides
        XCTAssertEqual(hides, 0)
    }

    func testDontShowAgainIsTheOnlyActStoredOnce() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: true)
        await settle()
        let hides = await store.coachHides
        XCTAssertEqual(hides, 1)
        XCTAssertEqual(props(SearchAnalytics.searchCoachDismissedName)["dont_show_again"] as? Bool, true)
        m.visit(zone: "Europe/Berlin", filtersActive: [], referrer: nil)
        await settle()
        XCTAssertFalse(m.state.coach)
    }

    func testASessionReadThatMissedDontShowAgainNeverBringsTheCoachBack() async {
        await store.setCoachHideLands(false)
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: true)
        await settle()
        m.visit(zone: "Europe/Berlin", filtersActive: [], referrer: nil)
        await settle()
        XCTAssertFalse(m.state.coach)
        XCTAssertEqual(events.of(SearchAnalytics.searchCoachShownName).count, 1)
    }

    func testTheThreeButtonsAreInertUnderTheCoachAndFireNothing() async {
        let m = await visited()
        events.clear()
        m.like()
        m.pass(.notInterested)
        m.pass(.notNow)
        m.openPhoto(0)
        await settle()
        XCTAssertTrue(events.names.isEmpty)
        XCTAssertNil(m.state.sheet)
        XCTAssertEqual(m.state.current?.userId, SearchSamples.full.userId)
        let passes = await store.passes
        XCTAssertTrue(passes.isEmpty)
    }

    func testTheCoachedProfilesDwellCountsFromTheDismissal() async {
        let m = await visited()
        ms = 6_000
        m.dismissCoach(.close, dontShowAgain: false)
        ms = 9_000
        m.pass(.notNow)
        XCTAssertEqual(props(SearchAnalytics.profileCardViewedName)["dwell_ms"] as? Int, 3_000)
    }

    // MARK: - the like flow

    func testALikeWithAMessageFiresInTheTicketsOrderAndSendsTheTrimmedText() async {
        let m = await uncoached()
        m.like()
        XCTAssertEqual(m.state.sheet, .confirm)
        m.continueToMessage()
        XCTAssertEqual(m.state.draft, "Hi Leonie, I love the vibe of your profile. Would be great to meet you today :)")
        m.editDraft("  See you at the lake!  ")
        m.sendMessage()
        await settle()
        XCTAssertEqual(events.names, [
            SearchAnalytics.likeTappedName, SearchAnalytics.likeConfirmShownName,
            SearchAnalytics.likeMessageAttachedName, SearchAnalytics.profileCardViewedName,
        ])
        XCTAssertEqual(props(SearchAnalytics.likeTappedName)["position"] as? Int, 1)
        XCTAssertEqual(props(SearchAnalytics.likeTappedName)["surface"] as? String, "profile")
        XCTAssertEqual(props(SearchAnalytics.likeConfirmShownName)["target_attended_count"] as? Int, 4)
        XCTAssertEqual(props(SearchAnalytics.likeConfirmShownName)["is_mutual"] as? Bool, false)
        XCTAssertEqual(props(SearchAnalytics.likeMessageAttachedName)["char_count"] as? Int, 20)
        let likes = await store.likes
        XCTAssertEqual(likes.count, 1)
        XCTAssertEqual(likes.first?.0, SearchSamples.full.userId)
        XCTAssertEqual(likes.first?.1, "See you at the lake!")
        XCTAssertEqual(likes.first?.2, 1)
        XCTAssertEqual(m.state.current?.userId, "sample-mara", "the next profile slides in")
        XCTAssertEqual(m.state.position, 2)
        XCTAssertNil(m.state.sheet)
    }

    func testTheMessageTextIsInNoPayloadEver() async {
        let m = await uncoached()
        m.like(); m.continueToMessage(); m.editDraft("my secret words"); m.sendMessage()
        await settle()
        XCTAssertFalse(events.events.contains { e in e.1.values.contains { "\($0)".contains("secret") } })
    }

    func testSkipMessageSendsTheLikeWithNoMessageAndIsNotAnAbandon() async {
        let m = await uncoached()
        m.like(); m.continueToMessage(); m.skipMessage()
        await settle()
        let likes = await store.likes
        XCTAssertEqual(likes.count, 1)
        XCTAssertNil(likes.first?.1)
        XCTAssertTrue(events.of(SearchAnalytics.likeMessageAttachedName).isEmpty)
        XCTAssertTrue(events.of(SearchAnalytics.likeConfirmAbandonedName).isEmpty)
    }

    func testABlankMessageSendsAsNoneAndAttachesNothing() async {
        let m = await uncoached()
        m.like(); m.continueToMessage(); m.editDraft("    "); m.sendMessage()
        await settle()
        let likes = await store.likes
        XCTAssertNil(likes.first?.1)
        XCTAssertTrue(events.of(SearchAnalytics.likeMessageAttachedName).isEmpty)
    }

    func testCancellingBeSureOrDismissingTheMessageSendsNothingEachItsStage() async {
        let m = await uncoached()
        m.like(); m.cancelConfirm()
        XCTAssertEqual(props(SearchAnalytics.likeConfirmAbandonedName)["stage"] as? String, "confirm")
        m.like(); m.continueToMessage(); m.dismissMessage()
        XCTAssertEqual(props(SearchAnalytics.likeConfirmAbandonedName, 1)["stage"] as? String, "message")
        await settle()
        let likes = await store.likes
        XCTAssertTrue(likes.isEmpty)
        XCTAssertEqual(m.state.current?.userId, SearchSamples.full.userId, "the profile stays")
    }

    func testTheDraftNeverHoldsMoreThan100ACutPaste() async {
        let m = await uncoached()
        m.like(); m.continueToMessage()
        m.editDraft(String(repeating: "x", count: 250))
        XCTAssertEqual(graphemes(m.state.draft), 100)
    }

    func testAFailedLikeIsRetriedSilentlyAndNeverMadeTwiceByTheScreen() async {
        await store.setLikeFailures(2)
        let m = await uncoached()
        m.like(); m.continueToMessage(); m.skipMessage()
        await settle(0.3)
        let likes = await store.likes
        XCTAssertEqual(likes.count, 3)
        XCTAssertTrue(likes.allSatisfy { $0.0 == SearchSamples.full.userId }, "all for the one profile")
        XCTAssertEqual(m.state.current?.userId, "sample-mara")
    }

    // MARK: - passes

    func testNotNowAndNotInterestedActInOneTapAndTheNextProfileSlidesIn() async {
        let m = await uncoached()
        m.pass(.notNow)
        m.pass(.notInterested)
        await settle()
        XCTAssertEqual(events.of(SearchAnalytics.passSentName).map { $0["pass_type"] as? String }, ["not_now", "not_interested"])
        XCTAssertEqual(events.of(SearchAnalytics.passSentName).map { $0["position"] as? Int }, [1, 2])
        XCTAssertTrue(events.of(SearchAnalytics.passSentName).allSatisfy { $0["surface"] as? String == "profile" })
        let passes = await store.passes
        XCTAssertEqual(passes.map(\.0), [SearchSamples.full.userId, "sample-mara"])
        XCTAssertEqual(m.state.current?.userId, "sample-ana")
    }

    func testProfileCardViewedCarriesTheOverlapCountsThePhotosSwipedAndTheSectionsSeen() async {
        let m = await uncoached()
        m.sectionSeen(0); m.sectionSeen(1); m.sectionSeen(1); m.sectionSeen(4)
        m.openPhoto(1); m.viewerPage(2); m.viewerPage(3); m.closeViewer()
        ms += 1_200
        m.pass(.notNow)
        let p = props(SearchAnalytics.profileCardViewedName)
        XCTAssertEqual(p["position"] as? Int, 1)
        XCTAssertEqual(p["dwell_ms"] as? Int, 1_200)
        XCTAssertEqual(p["photos_swiped"] as? Int, 2)
        XCTAssertEqual(p["sections_scrolled"] as? Int, 3)
        XCTAssertEqual(p["shared_vibe_count"] as? Int, 3)
        XCTAssertEqual(p["shared_interest_count"] as? Int, 4)
        XCTAssertEqual(props(SearchAnalytics.profilePhotoOpenedName)["photo_index"] as? Int, 2)
    }

    func testLeavingTheTabReportsTheProfileAndComingBackStartsANewDwell() async {
        let m = await uncoached()
        ms += 4_000
        m.leave()
        XCTAssertEqual(props(SearchAnalytics.profileCardViewedName)["dwell_ms"] as? Int, 4_000)
        m.resume()
        ms += 500
        m.pass(.notNow)
        XCTAssertEqual(props(SearchAnalytics.profileCardViewedName, 1)["dwell_ms"] as? Int, 500)
    }

    // MARK: - the way back from Filters

    func testBackFromFiltersWithNewSettingsSavedTheQueueIsReadAgainAndTheVisitGoesOn() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: false)
        m.leave()
        m.filtersSaved()
        events.clear()
        m.arrived(zone: "Europe/Berlin", filtersActive: ["age_range"], referrer: .checkinFilters)
        await settle()
        let reads = await store.queueReads
        XCTAssertEqual(reads, 2)
        XCTAssertEqual(events.names, ["screen_viewed", SearchAnalytics.searchViewedName])
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "checkin_filters")
        XCTAssertEqual(props(SearchAnalytics.searchViewedName)["filters_active"] as? [String], ["age_range"])
        XCTAssertFalse(m.state.coach, "no second coach in the same visit")
    }

    func testBackFromFiltersWithNothingSavedNothingIsReadAgainAndPositionStartsAgain() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: false)
        m.pass(.notNow)
        await settle()
        XCTAssertEqual(m.state.position, 2)
        m.leave()
        events.clear()
        m.arrived(zone: "Europe/Berlin", filtersActive: ["distance_km"], referrer: .checkinFilters)
        await settle()
        let reads = await store.queueReads
        XCTAssertEqual(reads, 1)
        XCTAssertEqual(events.names, ["screen_viewed"])
        XCTAssertEqual(m.state.current?.userId, "sample-mara", "Mara is still the one shown")
        XCTAssertEqual(m.state.position, 1, "position starts again with the screen_viewed")
    }

    func testAnyOtherArrivalIsAVisitWithItsCoach() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: false)
        m.arrived(zone: "Europe/Berlin", filtersActive: [], referrer: .checkinSearchUnavailable)
        await settle()
        XCTAssertTrue(m.state.coach)
        // And a first arrival FROM Filters -- a scene restored on Filters -- is a visit too.
        let fresh = model()
        fresh.arrived(zone: "Europe/Berlin", filtersActive: [], referrer: .checkinFilters)
        await settle()
        let reads = await store.queueReads
        XCTAssertEqual(reads, 3)
    }

    // MARK: - playback

    func testAPlayIsReportedOnceWhenItStopsAndStartingOneStopsTheOther() async {
        let p = players()
        let m = await uncoached(players: p)
        m.togglePlay(.voice)
        await settle()
        XCTAssertEqual(m.state.playback?.kind, .voice)
        playerClock = 7_000
        await settle(0.25)
        m.togglePlay(.video)
        await settle()
        XCTAssertEqual(m.state.playback?.kind, .video)
        XCTAssertEqual(events.of(SearchAnalytics.voicePromptPlayedName).count, 1)
        let pct = events.of(SearchAnalytics.voicePromptPlayedName).first?["played_pct"] as? Double ?? -1
        XCTAssertTrue((45.0...55.0).contains(pct), "about half heard: \(pct)")
        m.scrolledOut(.video)
        XCTAssertNil(m.state.playback)
        XCTAssertEqual(events.of(SearchAnalytics.videoPromptPlayedName).count, 1)
    }

    func testAPlayStillOpeningWhenTheProfileMovesOnNeverPlaysOverTheNextOne() async {
        let p = players(openingNanoseconds: 300_000_000)
        let m = await uncoached(players: p)
        m.togglePlay(.voice)
        await settle(0.05)
        m.pass(.notNow)
        await settle(0.5)
        XCTAssertNil(m.state.playback)
        XCTAssertFalse(p.players.first?.running ?? true, "the superseded clip was stopped, not left running")
        XCTAssertTrue(events.of(SearchAnalytics.voicePromptPlayedName).isEmpty, "nothing was heard, so no play is reported")
    }

    func testOpeningBeSureStopsPlayback() async {
        let p = players()
        let m = await uncoached(players: p)
        m.togglePlay(.voice)
        await settle()
        m.like()
        XCTAssertNil(m.state.playback)
        XCTAssertEqual(events.of(SearchAnalytics.voicePromptPlayedName).count, 1)
    }

    // MARK: - failure, and what must never fire

    func testAQueueThatCannotBeReadIsTheFailedStateAndRetryReadsItAgain() async {
        await store.setQueueFailure(.network)
        let m = await visited()
        XCTAssertEqual(m.state.failure, .network)
        XCTAssertTrue(events.of(SearchAnalytics.searchViewedName).isEmpty)
        await store.setQueueFailure(nil)
        m.retry(zone: "Europe/Berlin")
        await settle()
        XCTAssertNil(m.state.failure)
        XCTAssertEqual(m.state.current?.userId, SearchSamples.full.userId)
        let reads = await store.queueReads
        XCTAssertEqual(reads, 2)
    }

    func testTheServersEventsAndTheOnesThatDoNotExistHereAreNeverFiredByTheApp() async {
        let m = await visited()
        m.dismissCoach(.close, dontShowAgain: false)
        m.like(); m.continueToMessage(); m.sendMessage()
        m.pass(.notNow)
        m.leave()
        await settle()
        let forbidden: Set<String> = [
            "like_sent", "match_created", "like_withdrawn", "profile_section_expanded", "shared_vibes_shown",
            "overlap_viewed", "search_lockout_shown", "search_lockout_expired", "profile_step_viewed",
            "profile_step_completed", "not_now_tapped", "not_interested_tapped",
        ]
        XCTAssertTrue(events.names.allSatisfy { !forbidden.contains($0) }, "\(events.names.filter { forbidden.contains($0) })")
        XCTAssertEqual(events.of("screen_viewed").count, 1, "screen_viewed fires once per visit, not per profile")
    }

    // MARK: - process death
    //
    // A second model attached to the same storage is what the app builds after the system ends the
    // process: `@SceneStorage` brings the string back, everything else starts again.

    /// The host's `@SceneStorage` string.
    private final class Scene {
        var stored = ""
    }

    private func visited(attachedTo scene: Scene) async -> SearchModel {
        let m = model()
        m.attach(stored: scene.stored) { scene.stored = $0 }
        m.visit(zone: "Europe/Berlin", filtersActive: ["distance_km"], referrer: .checkinUplift)
        await settle()
        return m
    }

    /// A visit with the coach dismissed and the message sheet open, `typed` in it.
    private func writing(_ typed: String, to scene: Scene) async {
        let m = await visited(attachedTo: scene)
        m.dismissCoach(.close, dontShowAgain: false)
        m.like()
        m.continueToMessage()
        m.editDraft(typed)
    }

    func testAnOpenMessageSheetAndItsDraftSurviveTheProcessAndNothingFiresTwice() async {
        let scene = Scene()
        await writing("See you at the lake", to: scene)
        let after = await visited(attachedTo: scene)
        XCTAssertEqual(after.state.sheet, .message)
        XCTAssertEqual(after.state.draft, "See you at the lake")
        XCTAssertFalse(after.state.coach, "no coach over a sheet the user opened past it")
        XCTAssertEqual(events.of(SearchAnalytics.searchCoachShownName).count, 1)
        XCTAssertEqual(events.of(SearchAnalytics.likeTappedName).count, 1)
        XCTAssertEqual(events.of(SearchAnalytics.likeConfirmShownName).count, 1)
        after.sendMessage()
        await settle()
        let likes = await store.likes
        XCTAssertEqual(likes.count, 1)
        XCTAssertEqual(likes.first?.0, SearchSamples.full.userId)
        XCTAssertEqual(likes.first?.1, "See you at the lake")
        XCTAssertEqual(likes.first?.2, 1)
        let again = await visited(attachedTo: scene)
        XCTAssertNil(again.state.sheet, "a sent like leaves nothing to restore")
    }

    func testBeSureSurvivesTooAndAClosedSheetLeavesNothingBehind() async {
        let scene = Scene()
        let first = await visited(attachedTo: scene)
        first.dismissCoach(.close, dontShowAgain: false)
        first.like()
        let after = await visited(attachedTo: scene)
        XCTAssertEqual(after.state.sheet, .confirm)
        XCTAssertEqual(after.state.draft, "")
        after.cancelConfirm()
        let again = await visited(attachedTo: scene)
        XCTAssertNil(again.state.sheet)
        XCTAssertTrue(again.state.coach, "an ordinary visit, so the coach is back")
    }

    func testAKeptLikeFlowIsDroppedWhenSomebodyElseIsFirstInTheQueue() async {
        let scene = Scene()
        await writing("Hello", to: scene)
        await store.setProfiles(SearchSamples.next)
        let other = await visited(attachedTo: scene)
        XCTAssertNil(other.state.sheet)
        await store.setProfiles(playableQueue)
        let back = await visited(attachedTo: scene)
        XCTAssertNil(back.state.sheet, "dropped for good, not waiting for that person to come back")
    }

    func testAQueueThatCannotBeReadKeepsTheLikeFlowForTheRetryThatCan() async {
        let scene = Scene()
        await writing("Hello", to: scene)
        await store.setQueueFailure(.network)
        let after = await visited(attachedTo: scene)
        XCTAssertNil(after.state.sheet)
        await store.setQueueFailure(nil)
        after.retry(zone: "Europe/Berlin")
        await settle()
        XCTAssertEqual(after.state.sheet, .message)
        XCTAssertEqual(after.state.draft, "Hello")
    }
}
