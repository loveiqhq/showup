// DateModelTests.swift
// ShowUp · the Date screen's loading, unlock and tracking, and the read-only profile (SHOWUP-193)
//
// Android's `DateViewModelTest.kt` and `DateProfileViewModelTest.kt`: the same cases and payloads. The
// clock is injected, so "A → B at meet − 1:30 with the screen open" is a step of the clock, not a wait.

import XCTest
@testable import ShowUpWelcome

/// Answers from values the test sets; counts the reads.
actor FakeDateStore: DateStoring {
    private(set) var read: Result<DateRead, CallFailure> = .success(.none)
    private(set) var reads = 0
    private(set) var profile: Result<SearchProfile, CallFailure> = .failure(.server)

    func setRead(_ value: Result<DateRead, CallFailure>) { read = value }
    func setProfile(_ value: Result<SearchProfile, CallFailure>) { profile = value }

    func active(zone: String) async -> Result<DateRead, CallFailure> {
        reads += 1
        return read
    }

    func partnerProfile(zone: String) async -> Result<SearchProfile, CallFailure> { profile }
    func createTestDate(minutes: Int) async -> Bool { true }
    func messageFromMatch(reason: ChatReason?, body: String) async -> Bool { true }
    func endTestDate() async -> Bool { true }
    func matchEndsInChat() async -> Bool { true }
}

/// The model's clock: read through its closure, moved by the test.
private final class TestClock {
    var now: Date
    init(_ now: Date) { self.now = now }
}

@MainActor
final class DateModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private var unlock: Date { meet.addingTimeInterval(-90 * 60) }

    private func date(unread: DateUnread? = nil, checkedIn: Bool = false) -> ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: unlock, coordinateOpen: false,
                   place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term",
                   sharedVibes: [], sharedInterests: [], unread: unread, checkedIn: checkedIn, chatTutorialSeen: false)
    }

    private func settle(_ seconds: Double = 0.05) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func model(_ store: FakeDateStore, _ analytics: RecordingAnalytics, _ clock: TestClock,
                       copiedFor: TimeInterval = Motion.copied) -> DateModel {
        DateModel(store: store, analytics: analytics, clock: { clock.now },
                  zone: { TimeZone(identifier: "Europe/Berlin")! }, copiedFor: copiedFor)
    }

    private func names(_ analytics: RecordingAnalytics) -> [String] { analytics.entries.map(\.event) }

    private func props(_ analytics: RecordingAnalytics, _ name: String) -> [String: any Sendable] {
        analytics.entries.first { $0.event == name }?.properties ?? [:]
    }

    func testAVisitInStateAFiresScreenViewedAndDateScreenViewedOnceWithTheRegistrysPayloads() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        XCTAssertEqual(names(analytics), ["screen_viewed", "date_screen_viewed"])
        let view = props(analytics, "screen_viewed")
        XCTAssertEqual(view["screen_id"] as? String, "date_screen")
        XCTAssertEqual(view["screen_name"] as? String, "Date - Date screen")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "")
        let state = props(analytics, "date_screen_viewed")
        XCTAssertEqual(state["date_id"] as? String, "d1")
        XCTAssertEqual(state["state"] as? String, "locked")
        XCTAssertEqual(state["has_unread"] as? Bool, false)
        XCTAssertEqual(state["checked_in"] as? Bool, false)
        XCTAssertEqual(state["minutes_before_start"] as? Int, 180)
        XCTAssertEqual(m.state.state, .locked)
    }

    func testAVisitInCReportsAvailableWithHasUnreadAndTheReferrer() async {
        let store = FakeDateStore()
        let u = DateUnread(count: 1, reason: .runningLate, body: "Ten minutes late!", sentAt: unlock)
        await store.setRead(.success(.active(date(unread: u))))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(unlock.addingTimeInterval(60)))
        m.arrived(referrer: .searchProfile)
        await settle()
        let state = props(analytics, "date_screen_viewed")
        XCTAssertEqual(state["state"] as? String, "available")
        XCTAssertEqual(state["has_unread"] as? Bool, true)
        XCTAssertEqual(props(analytics, "screen_viewed")["referrer_screen_id"] as? String, "search_profile")
        XCTAssertEqual(m.state.state, .unread)
    }

    func testTheUnlockHappensInPlaceAsTheClockPassesMeetMinus1h30WithNoEvent() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let clock = TestClock(unlock.addingTimeInterval(-1))
        let m = model(store, analytics, clock)
        m.arrived(referrer: nil)
        await settle()
        XCTAssertEqual(m.state.state, .locked)
        let reads = await store.reads
        clock.now = unlock
        m.tick()
        XCTAssertEqual(m.state.state, .available)
        XCTAssertEqual(names(analytics).count, 2, "no event for the unlock")
        let after = await store.reads
        XCTAssertEqual(after, reads, "no reload for the unlock")
    }

    func testForegroundAfterTheUnlockShowsBWithoutAVisitEvent() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let clock = TestClock(unlock.addingTimeInterval(-60))
        let m = model(store, analytics, clock)
        m.arrived(referrer: nil)
        await settle()
        clock.now = unlock.addingTimeInterval(60)
        m.foreground()
        await settle()
        XCTAssertEqual(m.state.state, .available)
        XCTAssertEqual(names(analytics), ["screen_viewed", "date_screen_viewed"])
    }

    func testADateEndedInTheChatShowsItsOutcomeInsteadOfLeavingTheDateKeptForItsFace() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        await store.setRead(.success(.ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))))
        m.foreground()
        await settle()
        XCTAssertEqual(m.state.ended, EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))
        XCTAssertFalse(m.state.left)
        XCTAssertEqual(m.state.date, date(), "the match's photo stays for the outcome screen")
    }

    func testOnceTheOutcomeHasBeenSeenNoDateIsHeld() async {
        let store = FakeDateStore()
        await store.setRead(.success(.ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: true))))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        m.outcomeShown()
        XCTAssertNil(m.state.ended)
        XCTAssertNil(m.state.date)
    }

    func testWhileOnScreenTheDateIsReadAgainAndNotOnceItIsOffScreen() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = DateModel(store: store, analytics: RecordingAnalytics(), clock: { self.meet },
                          zone: { TimeZone(identifier: "Europe/Berlin")! }, pollEvery: 0.05)
        m.arrived(referrer: nil)
        await settle()
        let before = await store.reads
        m.onScreen(true)
        await settle(0.2)
        let during = await store.reads
        XCTAssertGreaterThan(during, before, "re-read while on screen")
        m.onScreen(false)
        await settle(0.05)
        let stopped = await store.reads
        await settle(0.2)
        let after = await store.reads
        XCTAssertEqual(after, stopped)
    }

    func testAChangeTileFiresActionChipUsedOnTheCoordinateCardNeverAQuickAction() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        m.changeTile(.changeTime)
        let used = analytics.entries.filter { $0.event == "action_chip_used" }.map(\.properties)
        XCTAssertEqual(used.count, 1)
        XCTAssertEqual(used.first?["reason"] as? String, "change_time")
        XCTAssertEqual(used.first?["surface"] as? String, "coordinate_card")
        XCTAssertEqual(used.first?["minutes_before_start"] as? Int, 180)
        XCTAssertFalse(names(analytics).contains("date_quick_action_tapped"))
    }

    func testTheChangeTilesStayHiddenUntilDate05Ships() {
        XCTAssertTrue(changeTileReasons.isEmpty, "no change chip is shown yet")
    }

    func testOpenTheChatMarksTheHeldDatesTutorialAsSeenAtOnce() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        m.chatTutorialSeen()
        XCTAssertEqual(m.state.date?.chatTutorialSeen, true)
    }

    func testNoDateOnAReReadLeavesTheScreen() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        await store.setRead(.success(.none))
        m.foreground()
        await settle()
        XCTAssertTrue(m.state.left)
    }

    func testAFailedReReadKeepsTheDateOnScreen() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        await store.setRead(.failure(.network))
        m.foreground()
        await settle()
        XCTAssertEqual(m.state.date, date())
        XCTAssertFalse(m.state.left)
    }

    func testEachQuickActionReportsItsSection38Value() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        for action in QuickAction.allCases { m.quickAction(action) }
        let actions = analytics.entries.filter { $0.event == "date_quick_action_tapped" }.compactMap { $0.properties["action"] as? String }
        XCTAssertEqual(actions, ["directions", "share", "calendar", "copy_address"])
        XCTAssertEqual(analytics.entries.last?.properties["minutes_before_start"] as? Int, 180)
    }

    func testCopiedShowsForItsWhileThenGoes() async {
        let m = model(FakeDateStore(), RecordingAnalytics(), TestClock(meet), copiedFor: 0.1)
        m.copied()
        XCTAssertTrue(m.state.copied)
        await settle(0.02)
        XCTAssertTrue(m.state.copied)
        await settle(0.2)
        XCTAssertFalse(m.state.copied)
        XCTAssertEqual(Motion.copied, 1.6)
    }

    func testADateSeededFromTheLaunchReadDrawsAtOnceAndTheVisitReportsFromTheReRead() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.seed(date())
        XCTAssertEqual(m.state.date, date())
        XCTAssertFalse(m.state.loading)
        XCTAssertTrue(analytics.entries.isEmpty, "seeding reports nothing")
        m.arrived(referrer: nil)
        await settle()
        XCTAssertEqual(names(analytics), ["screen_viewed", "date_screen_viewed"])
    }

    func testANewArrivalAfterTheDateWentAwayDoesNotLeaveBeforeItsReadLands() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        await store.setRead(.success(.none))
        m.foreground()
        await settle()
        XCTAssertTrue(m.state.left)
        var next = date()
        next.id = "d2"
        await store.setRead(.success(.active(next)))
        m.arrived(referrer: nil)
        XCTAssertFalse(m.state.left, "whether the date is gone is the read's to say")
        await settle()
        XCTAssertEqual(m.state.date?.id, "d2")
    }

    func testSeedingANewDateReplacesAStaleOneAndClearsALeave() async {
        let store = FakeDateStore()
        await store.setRead(.success(.active(date())))
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        await store.setRead(.success(.none))
        m.foreground()
        await settle()
        var next = date()
        next.id = "d2"
        m.seed(next)
        XCTAssertEqual(m.state.date?.id, "d2")
        XCTAssertFalse(m.state.left)
    }

    func testANewDateReadAfterOneWentAwayIsShownNotLeft() async {
        let store = FakeDateStore()
        let m = model(store, RecordingAnalytics(), TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        XCTAssertTrue(m.state.left)
        await store.setRead(.success(.active(date())))
        m.foreground()
        await settle()
        XCTAssertFalse(m.state.left)
        XCTAssertEqual(m.state.date, date())
    }

    func testAFailedFirstReadShowsTheFailureAndTryAgainReadsAgainAndReportsTheVisit() async {
        let store = FakeDateStore()
        await store.setRead(.failure(.network))
        let analytics = RecordingAnalytics()
        let m = model(store, analytics, TestClock(meet.addingTimeInterval(-3 * 3600)))
        m.arrived(referrer: nil)
        await settle()
        XCTAssertEqual(m.state.failed, .network)
        XCTAssertNil(m.state.date)
        XCTAssertTrue(analytics.entries.isEmpty)
        await store.setRead(.success(.active(date())))
        let reads = await store.reads
        m.retry()
        XCTAssertTrue(m.state.loading, "loading again while it reads")
        // 9 Nov 2026: the message stays while it reloads, and a second tap does nothing.
        XCTAssertEqual(m.state.failed, .network)
        m.retry()
        await settle()
        let after = await store.reads
        XCTAssertEqual(after, reads + 1)
        XCTAssertNil(m.state.failed)
        XCTAssertEqual(m.state.date, date())
        XCTAssertEqual(names(analytics), ["screen_viewed", "date_screen_viewed"])
    }

    // MARK: - the read-only profile

    private func profileModel(_ store: FakeDateStore, _ analytics: RecordingAnalytics) -> DateProfileModel {
        DateProfileModel(store: store, analytics: analytics, players: nil, zone: { TimeZone(identifier: "Europe/Berlin")! })
    }

    func testOpeningReadsTheMatchsProfileAndFiresScreenViewedForSearchProfileFromDateScreenNothingElse() async {
        let store = FakeDateStore()
        await store.setProfile(.success(SearchSamples.full))
        let analytics = RecordingAnalytics()
        let m = profileModel(store, analytics)
        m.open()
        XCTAssertEqual(m.state?.loading, true, "loading until the read lands")
        await settle()
        XCTAssertEqual(m.state?.current, SearchSamples.full)
        XCTAssertEqual(names(analytics), ["screen_viewed"])
        XCTAssertEqual(props(analytics, "screen_viewed")["screen_id"] as? String, "search_profile")
        XCTAssertEqual(props(analytics, "screen_viewed")["referrer_screen_id"] as? String, "date_screen")
    }

    func testAFailedProfileReadShowsTheFailedStateAndTryAgainReadsAgainWithNoSecondView() async {
        let store = FakeDateStore()
        await store.setProfile(.failure(.server))
        let analytics = RecordingAnalytics()
        let m = profileModel(store, analytics)
        m.open()
        await settle()
        XCTAssertEqual(m.state?.failure, .server)
        XCTAssertNil(m.state?.current)
        await store.setProfile(.success(SearchSamples.full))
        m.retry()
        XCTAssertEqual(m.state?.loading, true)
        await settle()
        XCTAssertEqual(m.state?.current, SearchSamples.full)
        XCTAssertEqual(analytics.entries.filter { $0.event == "screen_viewed" }.count, 1, "one visit, one view")
    }

    func testItsPhotosOpenAndPageWithNoEvent() async {
        let store = FakeDateStore()
        await store.setProfile(.success(SearchSamples.full))
        let analytics = RecordingAnalytics()
        let m = profileModel(store, analytics)
        m.open()
        await settle()
        m.openPhoto(1)
        XCTAssertEqual(m.state?.viewerAt, 1)
        m.viewerPage(2)
        XCTAssertEqual(m.state?.viewerAt, 2)
        m.closeViewer()
        XCTAssertNil(m.state?.viewerAt)
        XCTAssertEqual(names(analytics), ["screen_viewed"])
    }

    func testClosingClearsItAndASecondOpenIsANewVisit() async {
        let store = FakeDateStore()
        await store.setProfile(.success(SearchSamples.full))
        let analytics = RecordingAnalytics()
        let m = profileModel(store, analytics)
        m.open()
        await settle()
        m.open()
        XCTAssertEqual(analytics.entries.filter { $0.event == "screen_viewed" }.count, 1, "an open profile is not opened twice")
        m.close()
        XCTAssertNil(m.state)
        m.open()
        await settle()
        XCTAssertEqual(analytics.entries.filter { $0.event == "screen_viewed" }.count, 2)
    }
}
