//
//  CheckInModelTests.swift
//  ShowUp · the 48hrs Check-In's state machine, driven with no device (SHOWUP-178 to SHOWUP-183)
//
//  The Swift half of `CheckInViewModelTest.kt`, case for case: every row of the six tickets'
//  tracking tables with its payload, the order they fire in ("the act's event, then navigation,
//  then the next screen_viewed"), every event that must NOT fire, and the behaviour the acceptance
//  criteria name -- single-fire CTAs, inert controls while saving, what close discards and what back
//  keeps, the pre-fills, the clock, the commit body, retries and failures, and the two stale-read
//  races. Kotlin advances a virtual clock; here the model's tasks run for real and `settle()` waits
//  for them, with the toast's 2600 ms injected short.
//
//  The payload field sets are written out verbatim from `events.json` (the Kotlin twin reads the
//  file itself, so a registry change fails there and here).
//

import XCTest
@testable import ShowUpWelcome

private let window = "8a1f2c44-0b6e-4f0a-9d2b-3c7e5a1b9f00"

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

/// A server that records every call, answers from what it holds, and can be made to fail or wait.
private actor FakeStore: CheckInStoring {
    private(set) var held = CheckInState(
        windowId: window, introPassed: false, active: false, firstCheckIn: true,
        lastMotivation: nil, lastVibes: [], filters: CheckInFilters(genders: ["woman"]))
    private var reads = true
    private var readFailure: CallFailure = .network
    private var filterSaves = true
    private var filterFailure: CallFailure = .network
    /// Per filter save, in order: how long it takes and whether it lands. Empty: instant, `filterSaves`.
    private var filterScript: [(UInt64, Bool)] = []
    /// Whether marking the intro passed reaches what later reads return.
    private var marksStick = true
    private var commitAnswers: [CommitResult] = []
    private var commitDelay: UInt64 = 0
    private var loadDelay: UInt64 = 0
    private(set) var commits: [CommitRequest] = []
    private(set) var answers: [(String?, Set<String>?)] = []
    private(set) var filterBodies: [CheckInFilters] = []
    /// Per filter save: whether the two Dealbreaker switches were sent (Check-In 05b).
    private(set) var withDealbreakers: [Bool] = []
    private(set) var introMarks = 0

    func setHeld(_ value: CheckInState) { held = value }
    func setReads(_ value: Bool) { reads = value }
    func setFilterSaves(_ value: Bool) { filterSaves = value }
    func setReadFailure(_ value: CallFailure) { readFailure = value }
    func setFilterFailure(_ value: CallFailure) { filterFailure = value }
    func scriptFilterSaves(_ script: [(UInt64, Bool)]) { filterScript.append(contentsOf: script) }
    func setMarksStick(_ value: Bool) { marksStick = value }
    func answerCommits(_ results: [CommitResult]) { commitAnswers.append(contentsOf: results) }
    func slowCommits(_ nanoseconds: UInt64) { commitDelay = nanoseconds }
    func slowLoads(_ nanoseconds: UInt64) { loadDelay = nanoseconds }

    func load() async -> Result<CheckInState, CallFailure> {
        let snapshot = held
        if loadDelay > 0 { try? await Task.sleep(nanoseconds: loadDelay) }
        return reads ? .success(snapshot) : .failure(readFailure)
    }

    func markIntroPassed() async -> Bool {
        introMarks += 1
        if marksStick { held.introPassed = true }
        return true
    }

    func saveAnswers(motivation: String?, vibes: Set<String>?) async -> Bool {
        answers.append((motivation, vibes))
        return true
    }

    func saveFilters(_ filters: CheckInFilters, withDealbreakers: Bool) async -> Result<Void, CallFailure> {
        filterBodies.append(filters)
        self.withDealbreakers.append(withDealbreakers)
        let (delay, lands) = filterScript.isEmpty ? (UInt64(0), filterSaves) : filterScript.removeFirst()
        if delay > 0 { try? await Task.sleep(nanoseconds: delay) }
        if lands { held.filters = filters }
        return lands ? .success(()) : .failure(filterFailure)
    }

    func commit(_ request: CommitRequest) async -> CommitResult {
        commits.append(request)
        if commitDelay > 0 { try? await Task.sleep(nanoseconds: commitDelay) }
        if !commitAnswers.isEmpty { return commitAnswers.removeFirst() }
        var saved = held
        saved.active = true
        return .saved(saved)
    }
}

/// A place reader that answers from memory and counts its reads.
@MainActor
private final class FakePlaces: PlaceReading {
    let place: CheckInPlace?
    private(set) var reads = 0

    init(_ place: CheckInPlace?) { self.place = place }

    func here() async -> CheckInPlace? {
        reads += 1
        return place
    }
}

@MainActor
final class CheckInModelTests: XCTestCase {

    private var store = FakeStore()
    private var analytics = Recorder()
    private var now = CivilDateTime(date: CivilDate(year: 2026, month: 10, day: 13), hour: 16, minute: 20)
    private var ms = 1_000
    /// How many times a commit navigated -- a property, so a task's closure can count it.
    private var landed = 0
    private var model: CheckInModel!

    override func setUp() {
        super.setUp()
        store = FakeStore()
        analytics = Recorder()
        now = at(16, 20)
        ms = 1_000
        landed = 0
        model = CheckInModel(
            store: store, analytics: analytics,
            clock: { [unowned self] in self.now },
            zone: { TimeZone(secondsFromGMT: 0) ?? .current },
            millis: { [unowned self] in self.ms },
            toastSeconds: 0.5)
    }

    private func at(_ h: Int, _ m: Int, day: Int = 13) -> CivilDateTime {
        CivilDateTime(date: CivilDate(year: 2026, month: 10, day: day), hour: h, minute: m)
    }

    private var state: CheckInUiState { model.state }

    func testDirectUpliftPreviewHasSkippedAnswersAndCanCommit() async {
        model.beginFlow(previewStep: .uplift)
        await arrive(.uplift)

        var committed = false
        await model.findMyDate { committed = true }

        XCTAssertTrue(committed)
        XCTAssertFalse(state.saveFailed)
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
        XCTAssertEqual(commits.first?.motivation, "long_term")
        XCTAssertEqual(commits.first?.vibes, ["calm"])
        XCTAssertEqual(commits.first?.slots.count, 1)
        XCTAssertNil(commits.first?.uplift)
    }

    func testRealFlowClearsPreviewAnswers() async {
        model.beginFlow(previewStep: .uplift)
        await settle()
        await begin()

        XCTAssertTrue(state.picks.isEmpty)
        XCTAssertNil(state.motivation)
        XCTAssertTrue(state.vibes.isEmpty)
    }

    /// Lets the model's own tasks -- a read, a save -- run to the end.
    private func settle(_ seconds: Double = 0.05) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func begin() async {
        model.beginFlow()
        await settle()
    }

    private func arrive(_ step: CheckInStep, referrer: ProfileScreen? = nil) async {
        model.arrived(step, screen: step.screen, referrer: referrer)
        await settle()
    }

    /// Drives the flow from the intro to `to`, answering each step the simplest valid way.
    private func walkTo(_ to: CheckInStep) async {
        await begin()
        await arrive(.today)
        if to == .today { return }
        model.slotTapped(.today, start: state.todayGrid!.starts[1])
        model.availabilityContinue(.today) {}
        await arrive(.tomorrow, referrer: .checkinToday)
        if to == .tomorrow { return }
        model.slotTapped(.tomorrow, start: state.tomorrowGrid!.starts[5])
        model.availabilityContinue(.tomorrow) {}
        await arrive(.motivation, referrer: .checkinTomorrow)
        if to == .motivation { return }
        model.motivationTapped("long_term")
        model.motivationContinue {}
        await arrive(.vibe, referrer: .checkinMotivation)
        if to == .vibe { return }
        model.vibeTapped("calm")
        model.vibeContinue {}
        await arrive(.filters, referrer: .checkinVibe)
        if to == .filters { return }
        model.filtersContinue {}
        await arrive(.uplift, referrer: .checkinFilters)
    }

    /// The one event of that name -- fails if it fired more or less than once.
    private func props(_ event: String, file: StaticString = #filePath, line: UInt = #line) -> [String: any Sendable] {
        let all = analytics.of(event)
        XCTAssertEqual(all.count, 1, "\(event) fired \(all.count) times", file: file, line: line)
        return all.first ?? [:]
    }

    // MARK: - 01: the intro and the gate

    func testTheIntroReportsItsViewWithTheNavigationsReferrer() async {
        await begin()
        model.arrived(.intro, screen: .checkinIntro, referrer: .lifeAhead)
        let viewed = props("screen_viewed")
        XCTAssertEqual(viewed["screen_id"] as? String, "checkin_intro")
        XCTAssertEqual(viewed["screen_name"] as? String, "CheckinIntro")
        XCTAssertEqual(viewed["referrer_screen_id"] as? String, "profile_life_ahead")
    }

    func testTheIntroCtaFiresCheckInStartedOnceMarksTheIntroPassedAndAdvances() async {
        await begin()
        model.arrived(.intro, screen: .checkinIntro, referrer: nil)
        analytics.clear()
        var advanced = 0
        model.introStarted { advanced += 1 }
        model.introStarted { advanced += 1 }
        await settle()
        XCTAssertEqual(advanced, 1, "single-fire: a second tap during the push does nothing")
        XCTAssertEqual(analytics.names, ["check_in_started"])
        XCTAssertEqual(props("check_in_started")["window_id"] as? String, window)
        let marks = await store.introMarks
        XCTAssertEqual(marks, 1)
        XCTAssertEqual(model.gateDestination(), .today)
    }

    func testAReadAlreadyInFlightWhenTheIntroIsPassedCannotUnPassIt() async {
        await begin()
        // The server keeps saying "not yet" to every read, as one that began before the tap does.
        await store.setMarksStick(false)
        await store.slowLoads(100_000_000)
        model.arrived(.intro, screen: .checkinIntro, referrer: nil)   // starts a read
        model.introStarted {}                                        // before it lands
        await settle(0.3)
        XCTAssertEqual(model.gateDestination(), .today)
    }

    func testAReadThatBeatsTheIntroWriteToTheServerCannotSendTheGateBackToTheIntro() async {
        await begin()
        await arrive(.intro)
        await store.setMarksStick(false)                             // not on the server yet
        model.introStarted {}
        await arrive(.today, referrer: .checkinIntro)                // reads at the NEW sequence
        XCTAssertEqual(state.server?.introPassed, true)
        XCTAssertEqual(model.gateDestination(), .today)
    }

    func testAnIntroWriteThatDidNotLandIsSentAgainOnTheNextReadAndOnlyUntilItLands() async {
        await begin()
        await arrive(.intro)
        await store.setMarksStick(false)
        model.introStarted {}
        await settle()
        var marks = await store.introMarks
        XCTAssertEqual(marks, 1)
        await store.setMarksStick(true)
        model.refresh()
        await settle(0.15)
        marks = await store.introMarks
        XCTAssertEqual(marks, 2, "the read disagreed, so the flag was sent again")
        model.refresh()
        await settle(0.15)
        marks = await store.introMarks
        XCTAssertEqual(marks, 2, "landed: nothing more is sent")
    }

    func testAReadThatStartedBeforeACommitCannotLockSearchAgain() async {
        await walkTo(.uplift)
        await store.slowLoads(400_000_000)
        await store.slowCommits(100_000_000)
        model.refresh()                                              // reads "not active", lands at 0.4s
        let commit = Task { await self.model.findMyDate {} }              // saves at 0.1s
        await settle(0.25)
        XCTAssertEqual(state.server?.active, true)
        await commit.value
        await settle(0.35)
        XCTAssertEqual(state.server?.active, true, "the stale read cannot lock Search again")
    }

    func testTheIntrosCloseLandsOnTheGateWithCheckInClosedIntroAndKeepsNothing() async {
        await begin()
        model.arrived(.intro, screen: .checkinIntro, referrer: nil)
        var closed = false
        model.closePressed(.intro) { closed = true }
        XCTAssertTrue(closed)
        XCTAssertEqual(props("check_in_closed")["step_id"] as? String, "intro")
        XCTAssertEqual(props("check_in_closed")["window_id"] as? String, window)
        XCTAssertFalse(model.flowAlive)
    }

    func testTheGateReopensAtTheIntroUntilItHasBeenPassedThenAtToday() async {
        await begin()
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: .checkinIntro)
        var to: CheckInStep?
        model.gateTapped(.cta) { to = $0 }
        XCTAssertEqual(to, .intro)
        XCTAssertEqual(analytics.of("check_in_gate_tapped").last?["intro_passed"] as? Bool, false)
        XCTAssertEqual(analytics.of("check_in_gate_tapped").last?["target"] as? String, "cta")

        // The real walk: the intro, its CTA never tapped, closed back to the gate -- then the intro
        // is passed on another device, and the next read says so.
        model.arrived(.intro, screen: .checkinIntro, referrer: .checkinSearchUnavailable)
        model.closePressed(.intro) {}
        var held = await store.held
        held.introPassed = true
        await store.setHeld(held)
        await settle()
        model.refresh()
        await settle()
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: .checkinIntro)
        model.gateTapped(.instantTab) { to = $0 }
        XCTAssertEqual(to, .today)
        let last = analytics.of("check_in_gate_tapped").last
        XCTAssertEqual(last?["intro_passed"] as? Bool, true)
        XCTAssertEqual(last?["target"] as? String, "instant_tab")
        XCTAssertTrue(analytics.of("check_in_started").isEmpty, "check_in_started is the intro CTA's alone")
    }

    func testTheGateFiresOncePerShowingAndSettingsInstantTabIsStillTheGate() async {
        await begin()
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: nil)
        var opened = 0
        model.gateTapped(.searchTab) { _ in opened += 1 }
        model.gateTapped(.cta) { _ in opened += 1 }
        XCTAssertEqual(opened, 1)
        XCTAssertEqual(analytics.of("check_in_gate_tapped").count, 1)
        model.atTabs()
        model.gateTapped(.instantTab) { _ in opened += 1 }
        XCTAssertEqual(opened, 2)
        XCTAssertEqual(analytics.of("check_in_gate_tapped").last?["target"] as? String, "instant_tab")
    }

    func testTheGatesViewCarriesItsOwnScreenIdAndIsReportedAgainAfterSettings() {
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: .checkinIntro)
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: .checkinIntro)
        XCTAssertEqual(analytics.of("screen_viewed").count, 1, "a redraw is not an arrival")
        XCTAssertEqual(analytics.of("screen_viewed").first?["screen_id"] as? String, "checkin_search_unavailable")
        model.atTabs()
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: nil)
        XCTAssertEqual(analytics.of("screen_viewed").count, 2)
    }

    // MARK: - 02: availability

    func testTodayStartsEmptyOnEveryEntryAndItsGridBeginsAtTheNextFullHour() async {
        await begin()
        await arrive(.today)
        XCTAssertTrue(state.picks.isEmpty)
        XCTAssertEqual(state.todayGrid?.hours.first, 17)
        XCTAssertEqual(state.tomorrowGrid?.hours.count, 24)
    }

    func testSlotTapsCycleAndFireNothing() async {
        await begin()
        await arrive(.today)
        analytics.clear()
        let six = state.todayGrid!.starts[1]
        model.slotTapped(.today, start: six)
        XCTAssertEqual(state.picks[six], .preferred)
        model.slotTapped(.today, start: six)
        XCTAssertEqual(state.picks[six], .available)
        model.slotTapped(.today, start: six)
        XCTAssertNil(state.picks[six])
        model.slotTapped(.today, start: six)
        model.clearAll(.today)
        model.toggleHowItWorks()
        XCTAssertTrue(state.picks.isEmpty)
        XCTAssertTrue(analytics.names.isEmpty, "no per-tap, Clear all or How it works event")
    }

    func testTodaysContinueWithHoursFiresStepAnsweredThenSlotsSetThenAdvances() async {
        await begin()
        await arrive(.today)
        analytics.clear()
        let g = state.todayGrid!
        model.slotTapped(.today, start: g.starts[1])
        model.slotTapped(.today, start: g.starts[2])
        model.slotTapped(.today, start: g.starts[3])
        model.slotTapped(.today, start: g.starts[3])
        ms += 4_000
        var order: [String] = []
        model.availabilityContinue(.today) { order.append("navigate") }
        order.insert(contentsOf: analytics.names, at: 0)
        XCTAssertEqual(order, ["check_in_step_answered", "availability_slots_set", "navigate"])
        let answered = props("check_in_step_answered")
        XCTAssertEqual(answered["step_id"] as? String, "today")
        XCTAssertEqual(answered["answer_value"] as? String, "hours")
        XCTAssertEqual(answered["time_to_answer_ms"] as? Int, 4_000)
        let slots = props("availability_slots_set")
        XCTAssertEqual(slots["day"] as? String, "today")
        XCTAssertEqual(slots["preferred_hours"] as? Int, 2)
        XCTAssertEqual(slots["available_hours"] as? Int, 1)
        XCTAssertEqual(slots["window_id"] as? String, window)
    }

    func testAnEmptyTodayRaisesTheConfirmNeverAnErrorAndFiresNothingUntilAnswered() async {
        await begin()
        await arrive(.today)
        analytics.clear()
        var moved = false
        model.availabilityContinue(.today) { moved = true }
        XCTAssertTrue(state.noHoursDialog)
        XCTAssertFalse(moved)
        XCTAssertNil(state.toast)
        XCTAssertTrue(analytics.names.isEmpty)
    }

    func testContinueToTomorrowRecordsAnEmptyTodayResolutionFirst() async {
        await begin()
        await arrive(.today)
        analytics.clear()
        model.availabilityContinue(.today) {}
        var moved = false
        model.noHoursConfirmed { moved = true }
        XCTAssertTrue(moved)
        XCTAssertEqual(analytics.names,
                       ["check_in_no_hours_today_resolved", "check_in_step_answered", "availability_slots_set"])
        XCTAssertEqual(props("check_in_no_hours_today_resolved")["action"] as? String, "continue_tomorrow")
        XCTAssertEqual(props("check_in_step_answered")["answer_value"] as? String, "none")
        XCTAssertEqual(props("availability_slots_set")["preferred_hours"] as? Int, 0)
        XCTAssertEqual(props("availability_slots_set")["available_hours"] as? Int, 0)
        XCTAssertFalse(state.noHoursDialog)
    }

    func testCancelAndDismissStayOnTodayWithTheirOwnActions() async {
        await begin()
        await arrive(.today)
        model.availabilityContinue(.today) {}
        model.noHoursCancelled(dismissed: false)
        XCTAssertFalse(state.noHoursDialog)
        model.availabilityContinue(.today) {}
        model.noHoursCancelled(dismissed: true)
        XCTAssertEqual(analytics.of("check_in_no_hours_today_resolved").map { $0["action"] as? String },
                       ["pick_today", "dismissed"])
        XCTAssertTrue(analytics.of("check_in_step_answered").isEmpty)
        model.noHoursCancelled(dismissed: true)
        XCTAssertEqual(analytics.of("check_in_no_hours_today_resolved").count, 2, "no dialog, no event")
    }

    func testAnEmptyTomorrowRaisesTheToastARepeatRestartsItAndFiresNothing() async {
        await walkTo(.tomorrow)
        analytics.clear()
        XCTAssertTrue(state.tomorrowGrid!.picked(state.picks).isEmpty)
        var moved = false
        model.availabilityContinue(.tomorrow) { moved = true }
        XCTAssertFalse(moved)
        XCTAssertEqual(state.toast, CheckInCopy.tomorrowToast)
        let tick = state.toastTick
        await settle(0.3)
        model.availabilityContinue(.tomorrow) {}
        XCTAssertEqual(state.toastTick, tick + 1)
        await settle(0.3)
        XCTAssertNotNil(state.toast, "restarted, so still up after the first tap's time ran out")
        await settle(0.45)
        XCTAssertNil(state.toast)
        XCTAssertTrue(analytics.names.isEmpty)
    }

    func testBackAndForwardKeepBothDaysPicksAndACloseDiscardsThem() async {
        await walkTo(.tomorrow)
        model.slotTapped(.tomorrow, start: state.tomorrowGrid!.starts[0])
        var back = false
        model.backPressed(.tomorrow) { back = true }
        XCTAssertTrue(back)
        await arrive(.today, referrer: .checkinTomorrow)
        XCTAssertEqual(state.todayGrid!.picked(state.picks).count, 1)
        XCTAssertEqual(state.tomorrowGrid!.picked(state.picks).count, 1)
        XCTAssertEqual(analytics.of("screen_viewed").last?["referrer_screen_id"] as? String, "checkin_tomorrow")
        model.closePressed(.today) {}
        XCTAssertTrue(state.picks.isEmpty)
        XCTAssertEqual(analytics.of("check_in_closed").map { $0["step_id"] as? String }, ["today"])
    }

    func testAnHourThatStartsWhileTheFlowIsOpenFallsOutOfTheGridAndTheSelection() async {
        await begin()
        await arrive(.today)
        let five = state.todayGrid!.starts[0]
        let six = state.todayGrid!.starts[1]
        model.slotTapped(.today, start: five)
        model.slotTapped(.today, start: six)
        now = at(17, 5)
        model.evaluateClock()
        XCTAssertEqual(state.todayGrid?.hours.first, 18)
        XCTAssertNil(state.picks[five])
        XCTAssertEqual(state.picks[six], .preferred)
    }

    func testAcross7amTheDayThatWasTomorrowBecomesTodayAndKeepsItsPicks() async {
        now = at(5, 20)
        model.evaluateClock()
        await begin()
        await arrive(.tomorrow)
        let eight = state.tomorrowGrid!.starts[1]
        model.slotTapped(.tomorrow, start: eight)
        now = at(6, 30)
        model.evaluateClock()
        XCTAssertEqual(state.todayGrid?.starts[1], eight)
        XCTAssertEqual(state.todayGrid?.picked(state.picks)[8], .preferred)
    }

    // MARK: - 03: motivation

    func testAFirstCheckInStartsWithNothingSelectedAndAnEmptyContinueToasts() async {
        await walkTo(.motivation)
        XCTAssertNil(state.motivation)
        analytics.clear()
        var moved = false
        model.motivationContinue { moved = true }
        XCTAssertFalse(moved)
        XCTAssertEqual(state.toast, CheckInCopy.motivationToast)
        XCTAssertTrue(analytics.names.isEmpty)
    }

    func testContinueFiresStepAnsweredThenMotivationSelectedAtClass1AndSavesTheAnswer() async {
        await walkTo(.motivation)
        analytics.clear()
        model.motivationTapped("casual")
        model.motivationTapped("long_term")
        model.motivationTapped("long_term")
        model.motivationContinue {}
        await settle()
        XCTAssertEqual(analytics.names, ["check_in_step_answered", "motivation_selected"])
        XCTAssertEqual(props("check_in_step_answered")["answer_value"] as? String, "picked")
        let selected = props("motivation_selected")
        XCTAssertEqual(selected["motivation_id"] as? String, "long_term")
        XCTAssertEqual(selected["sensitivity_class"] as? Int, 1)
        let last = await store.answers.last
        XCTAssertEqual(last?.0, "long_term")
        XCTAssertNil(last?.1)
    }

    func testAReturningUserLandsOnTheLastAcceptedAnswerAndContinueReportsPrefilled() async {
        var held = await store.held
        held.lastMotivation = "casual"
        held.lastVibes = ["calm", "playful"]
        held.firstCheckIn = false
        await store.setHeld(held)
        await walkTo(.motivation)
        XCTAssertEqual(state.motivation, "casual")
        model.motivationContinue {}
        XCTAssertEqual(analytics.of("check_in_step_answered").last?["answer_value"] as? String, "prefilled")
        XCTAssertEqual(analytics.of("motivation_selected").map { $0["motivation_id"] as? String }, ["casual"])
    }

    func testACloseOnMotivationSavesNothingItDidNotAccept() async {
        await walkTo(.motivation)
        model.motivationTapped("friends")
        model.closePressed(.motivation) {}
        await settle()
        let answers = await store.answers
        XCTAssertTrue(answers.isEmpty)
        XCTAssertEqual(analytics.of("check_in_closed").map { $0["step_id"] as? String }, ["motivation"])
    }

    // MARK: - 04: vibe

    func testASixthChipSelectsNothingAndRaisesTheMaxToastWithNoEvent() async {
        await walkTo(.vibe)
        for id in ["coffee_chat", "sunset", "calm", "playful", "no_rush"] { model.vibeTapped(id) }
        analytics.clear()
        model.vibeTapped("cozy")
        XCTAssertEqual(state.vibes.count, 5)
        XCTAssertFalse(state.vibes.contains("cozy"))
        XCTAssertEqual(state.toast, CheckInCopy.vibeToastMax)
        XCTAssertTrue(analytics.names.isEmpty)
    }

    func testContinueReportsTheWholeSetInListOrderOnce() async {
        await walkTo(.vibe)
        analytics.clear()
        model.vibeTapped("no_rush")
        model.vibeTapped("playful")
        model.vibeTapped("coffee_chat")
        model.vibeContinue {}
        XCTAssertEqual(analytics.names, ["check_in_step_answered", "vibe_selected"])
        let selected = props("vibe_selected")
        XCTAssertEqual(selected["vibe_ids"] as? String, "coffee_chat,playful,no_rush")
        XCTAssertEqual(selected["vibe_count"] as? Int, 3)
        XCTAssertEqual(selected["sensitivity_class"] as? Int, 0)
    }

    func testAnEmptyVibeToastsAndSameValuesInAnyTapOrderArePrefilled() async {
        var held = await store.held
        held.lastVibes = ["calm", "playful"]
        await store.setHeld(held)
        await walkTo(.vibe)
        model.vibeTapped("calm")
        model.vibeTapped("playful")
        XCTAssertTrue(state.vibes.isEmpty)
        model.vibeContinue {}
        XCTAssertEqual(state.toast, CheckInCopy.vibeToastEmpty)
        model.vibeTapped("playful")
        model.vibeTapped("calm")
        model.vibeContinue {}
        XCTAssertEqual(analytics.of("check_in_step_answered").last?["answer_value"] as? String, "prefilled")
    }

    // MARK: - 05: filters

    func testFiltersPrefillReportWhatChangedButNeverTheGendersAndSend65PlusAsNull() async {
        await walkTo(.filters)
        XCTAssertEqual(state.filters?.genders, ["woman"])
        analytics.clear()
        model.genderTapped("non_binary")
        model.ageMoved(thumb: 0, value: 25)
        model.ageMoved(thumb: 1, value: 35)
        model.distanceMoved(15)
        XCTAssertTrue(analytics.names.isEmpty, "no event per drag or tap")
        model.filtersContinue {}
        await settle()
        XCTAssertEqual(analytics.names, ["check_in_step_answered", "check_in_filters_changed"])
        let changed = props("check_in_filters_changed")
        XCTAssertEqual(changed["filters_changed"] as? String, "meet_gender,age_range,distance_km")
        XCTAssertEqual(changed["min_age"] as? Int, 25)
        XCTAssertEqual(changed["max_age"] as? Int, 35)
        XCTAssertEqual(changed["distance_km"] as? Int, 15)
        XCTAssertFalse(changed.values.contains { String(describing: $0).contains("non_binary") },
                       "the genders are Class 2 and never travel")
        let saved = await store.filterBodies.last
        XCTAssertEqual(saved, CheckInFilters(genders: ["woman", "non_binary"], minAge: 25, maxAge: 35, distanceKm: 15))
    }

    func testUnchangedFiltersStillFireWithNoneAndANullMaxAge() async {
        await walkTo(.filters)
        analytics.clear()
        model.filtersContinue {}
        XCTAssertEqual(props("check_in_step_answered")["answer_value"] as? String, "prefilled")
        XCTAssertEqual(props("check_in_filters_changed")["filters_changed"] as? String, "none")
        XCTAssertEqual(props("check_in_filters_changed")["max_age"] as? JSONNull, JSONNull())
    }

    // MARK: - 05b: the Dealbreaker switches (SHOWUP-189)

    /// The server's remote flag `filters_dealbreakers`, held before the flow reads it.
    private func dealbreakersOn(filters: CheckInFilters? = nil, active: Bool = false) async {
        var held = await store.held
        held.filtersDealbreakers = true
        held.active = active
        if let filters { held.filters = filters }
        await store.setHeld(held)
    }

    func testBothSwitchesDefaultOffAndATapSwapsTheStateAndReportsNothing() async {
        await dealbreakersOn()
        await walkTo(.filters)
        XCTAssertEqual(state.filters?.ageDealbreaker, false)
        XCTAssertEqual(state.filters?.distanceDealbreaker, false)
        analytics.clear()
        model.ageDealbreakerToggled(true)
        XCTAssertEqual(state.filters?.ageDealbreaker, true)
        XCTAssertEqual(state.filters?.distanceDealbreaker, false, "nothing else on the screen changes")
        XCTAssertTrue(analytics.names.isEmpty, "a switch tap fires nothing")
        let saved = await store.filterBodies
        XCTAssertTrue(saved.isEmpty, "never saved on the tap")
    }

    func testAnAcceptedContinueSavesBothSwitchesAndReportsTheExtendedOrder() async {
        await dealbreakersOn()
        await walkTo(.filters)
        analytics.clear()
        model.ageMoved(thumb: 0, value: 25)
        model.ageDealbreakerToggled(true)
        model.distanceDealbreakerToggled(true)
        model.filtersContinue {}
        await settle()
        let changed = props("check_in_filters_changed")
        XCTAssertEqual(changed["filters_changed"] as? String, "age_range,age_dealbreaker,distance_dealbreaker")
        XCTAssertEqual(changed["age_dealbreaker"] as? Bool, true)
        XCTAssertEqual(changed["distance_dealbreaker"] as? Bool, true)
        let sent = await store.withDealbreakers
        XCTAssertEqual(sent, [true])
        let saved = await store.filterBodies.last
        XCTAssertEqual(saved?.ageDealbreaker, true)
    }

    func testWithTheFlagOffTheSwitchesDoNothingAreNotSentAndReportFalse() async {
        var held = await store.held
        held.filters = CheckInFilters(genders: ["woman"], ageDealbreaker: true)
        await store.setHeld(held)
        await walkTo(.filters)
        analytics.clear()
        model.distanceDealbreakerToggled(true)
        XCTAssertEqual(state.filters?.distanceDealbreaker, false, "no switch is drawn, so none can change")
        model.filtersContinue {}
        await settle()
        XCTAssertEqual(props("check_in_filters_changed")["age_dealbreaker"] as? Bool, false)
        XCTAssertEqual(props("check_in_filters_changed")["distance_dealbreaker"] as? Bool, false)
        let sent = await store.withDealbreakers
        XCTAssertEqual(sent, [false], "the fields are not sent")
    }

    func testASwitchChangedInSearchIsSavedAndSharedWithTheNextCheckIn() async {
        await dealbreakersOn(filters: CheckInFilters(genders: ["woman"]), active: true)
        model.refresh()
        await settle()
        model.searchFiltersOpened()
        await settle()
        model.distanceDealbreakerToggled(true)
        var back = false
        model.searchFiltersContinue { back = true }
        await settle()
        XCTAssertTrue(back)
        XCTAssertEqual(props("check_in_filters_changed")["distance_dealbreaker"] as? Bool, true)
        XCTAssertEqual(props("check_in_filters_changed")["opened_from"] as? String, "search")
        let held = await store.held
        XCTAssertTrue(held.filters.distanceDealbreaker, "one store with Search")
    }

    func testASwitchChangedInSearchAndBackedOutOfIsNotStored() async {
        await dealbreakersOn(filters: CheckInFilters(genders: ["woman"]), active: true)
        model.refresh()
        await settle()
        model.searchFiltersOpened()
        await settle()
        model.ageDealbreakerToggled(true)
        model.searchFiltersLeft {}
        await settle()
        let saved = await store.filterBodies
        XCTAssertTrue(saved.isEmpty)
        XCTAssertEqual(state.filters?.ageDealbreaker, false)
    }

    func testNoGenderToastsAndStays() async {
        await walkTo(.filters)
        model.genderTapped("woman")
        var moved = false
        model.filtersContinue { moved = true }
        XCTAssertFalse(moved)
        XCTAssertEqual(state.toast, CheckInCopy.filtersToast)
    }

    func testAnOfflineStartStillGivesFiltersAWorkingStandInReplacedByALateReadIfUntouched() async {
        await store.setReads(false)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        XCTAssertEqual(state.filters?.genders, [], "no gender ticked: Continue toasts, never a dead button")
        await store.setReads(true)
        var held = await store.held
        held.filters = CheckInFilters(genders: ["man"], distanceKm: 20)
        await store.setHeld(held)
        model.refresh()
        await settle()
        XCTAssertEqual(state.filters?.genders, ["man"])
    }

    func testALateReadNeverOverwritesAFilterTheUserHasTouched() async {
        await store.setReads(false)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        model.genderTapped("other")
        await store.setReads(true)
        model.refresh()
        await settle()
        XCTAssertEqual(state.filters?.genders, ["other"])
    }

    func testAReadThatLandsOnTheStandInKeepsWhatTheUserSetAndTakesTheRestFromTheServer() async {
        await store.setReads(false)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        model.distanceMoved(30)
        await store.setReads(true)
        var held = await store.held
        held.filters = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 20)
        await store.setHeld(held)
        model.refresh()
        await settle()
        XCTAssertEqual(state.filters, CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 30))
        analytics.clear()
        model.filtersContinue {}
        XCTAssertEqual(props("check_in_filters_changed")["filters_changed"] as? String, "distance_km",
                       "measured against what the read showed, not the stand-in it replaced")
    }

    func testFiltersSetOnTheStandInNeverReplaceSavedSettingsTheUserDidNotTouch() async {
        await store.setReads(false)
        var held = await store.held
        held.filters = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 20)
        await store.setHeld(held)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        model.genderTapped("woman")
        model.filtersContinue {}
        await settle()
        var bodies = await store.filterBodies
        XCTAssertTrue(bodies.isEmpty, "nothing built on the stand-in is sent before the server's settings are known")
        await arrive(.uplift, referrer: .checkinFilters)
        await store.setReads(true)
        await model.findMyDate {}
        bodies = await store.filterBodies
        XCTAssertEqual(bodies, [CheckInFilters(genders: ["woman"], minAge: 25, maxAge: 35, distanceKm: 20)],
                       "the gender the user ticked, the ages and distance they never saw")
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
    }

    // MARK: - 05 from Search's top bar (SHOWUP-184)

    /// A committed Check-In and its settings read: the user is on Search.
    private func inSearch() async {
        var held = await store.held
        held.active = true
        held.filters = CheckInFilters(genders: ["woman"], distanceKm: 20)
        await store.setHeld(held)
        model.refresh()
        await settle()
        analytics.clear()
    }

    private func filtersFromSearch() async {
        model.searchFiltersOpened()
        await settle()
    }

    func testFiltersFromSearchOpensOnTheSavedSettingsAndIsAScreenNeverACheckInStep() async {
        await inSearch()
        await filtersFromSearch()
        XCTAssertEqual(state.filters, CheckInFilters(genders: ["woman"], distanceKm: 20))
        XCTAssertEqual(analytics.names, ["screen_viewed"])
        XCTAssertEqual(props("screen_viewed")["screen_id"] as? String, "checkin_filters")
        XCTAssertEqual(props("screen_viewed")["referrer_screen_id"] as? String, "search_profile")
    }

    func testItsContinueSavesAndGoesBackOnlyOnceTheSaveHasLanded() async {
        await inSearch()
        await filtersFromSearch()
        analytics.clear()
        model.distanceMoved(5)
        await store.scriptFilterSaves([(300_000_000, true)])
        var back = false
        model.searchFiltersContinue { back = true }
        await settle(0.05)
        XCTAssertFalse(back, "a queue read now would still be under the old settings")
        XCTAssertTrue(state.saving)
        await settle(0.4)
        XCTAssertTrue(back)
        XCTAssertFalse(state.saving)
        let bodies = await store.filterBodies
        XCTAssertEqual(bodies, [CheckInFilters(genders: ["woman"], distanceKm: 5)])
        XCTAssertEqual(state.server?.filters.distanceKm, 5)
        XCTAssertEqual(analytics.names, ["check_in_filters_changed"], "the funnel's step event is not fired from Search")
        XCTAssertEqual(props("check_in_filters_changed")["filters_changed"] as? String, "distance_km")
        XCTAssertEqual(props("check_in_filters_changed")["opened_from"] as? String, "search")
    }

    func testARefusedSaveKeepsFiltersOnScreenWithItsToastAndContinueWorksAgain() async {
        await inSearch()
        await filtersFromSearch()
        model.distanceMoved(5)
        await store.setFilterSaves(false)
        var back = false
        model.searchFiltersContinue { back = true }
        await settle(0.05)
        XCTAssertFalse(back)
        XCTAssertEqual(state.toast, CheckInCopy.filtersSaveErrorProposed)
        XCTAssertFalse(state.saving)
        await store.setFilterSaves(true)
        model.searchFiltersContinue { back = true }
        await settle()
        XCTAssertTrue(back)
    }

    func testBackAndTheCloseDropTheEditsAndNeverDiscardTheCommittedCheckIn() async {
        await inSearch()
        await filtersFromSearch()
        model.distanceMoved(5)
        var back = false
        model.searchFiltersLeft { back = true }
        XCTAssertTrue(back)
        XCTAssertEqual(state.filters?.distanceKm, 20)
        let bodies = await store.filterBodies
        XCTAssertTrue(bodies.isEmpty, "nothing saved")
        XCTAssertFalse(analytics.names.contains("check_in_closed"))
        XCTAssertEqual(state.server?.active, true)
        // And the screen is left: its controls no longer act.
        model.distanceMoved(9)
        XCTAssertEqual(state.filters?.distanceKm, 20)
    }

    func testUnchangedSettingsGoBackWithNoSaveReportedAsNone() async {
        await inSearch()
        await filtersFromSearch()
        var back = false
        model.searchFiltersContinue { back = true }
        await settle()
        XCTAssertTrue(back)
        let bodies = await store.filterBodies
        XCTAssertTrue(bodies.isEmpty)
        XCTAssertEqual(props("check_in_filters_changed")["filters_changed"] as? String, "none")
    }

    func testOfflineFromTheStartAStandInIsNeverSavedOverTheUsersSettings() async {
        await store.setReads(false)
        await filtersFromSearch()
        XCTAssertEqual(state.filters?.genders, [])
        model.genderTapped("man")
        var back = false
        model.searchFiltersContinue { back = true }
        await settle(0.05)
        XCTAssertFalse(back)
        var bodies = await store.filterBodies
        XCTAssertTrue(bodies.isEmpty)
        XCTAssertEqual(state.toast, CheckInCopy.filtersSaveErrorProposed)
        // Back online: the read resolves the stand-in -- the edit over the server's -- then it saves.
        await store.setReads(true)
        model.searchFiltersContinue { back = true }
        await settle()
        XCTAssertTrue(back)
        bodies = await store.filterBodies
        XCTAssertEqual(bodies, [CheckInFilters(genders: ["man"])])
    }

    // MARK: - the place Search measures from (SHOWUP-184)

    func testTheCommitCarriesThePhonesPlaceReadOncePerFlowAgainInANewOne() async {
        let places = FakePlaces(CheckInPlace(latitude: 52.4811, longitude: 13.4352, district: "Neukölln, Berlin"))
        model = CheckInModel(store: store, analytics: analytics,
                             clock: { [unowned self] in self.now }, zone: { TimeZone(secondsFromGMT: 0) ?? .current },
                             millis: { [unowned self] in self.ms }, toastSeconds: 0.5, places: places)
        await walkTo(.uplift)
        await store.answerCommits([.network])
        await model.findMyDate {}
        await model.findMyDate {}
        let commits = await store.commits
        XCTAssertEqual(commits.map(\.place), [places.place, places.place])
        XCTAssertEqual(places.reads, 1, "a retry reuses the fix rather than waiting on another")
        await walkTo(.uplift)
        await model.findMyDate {}
        XCTAssertEqual(places.reads, 2, "a new flow reads again")
    }

    func testNoLocationNoPlaceAndTheCommitStillGoes() async {
        let places = FakePlaces(nil)
        model = CheckInModel(store: store, analytics: analytics,
                             clock: { [unowned self] in self.now }, zone: { TimeZone(secondsFromGMT: 0) ?? .current },
                             millis: { [unowned self] in self.ms }, toastSeconds: 0.5, places: places)
        await walkTo(.uplift)
        var landed = false
        await model.findMyDate { landed = true }
        XCTAssertTrue(landed)
        let commits = await store.commits
        XCTAssertNil(commits.first?.place)
    }

    // MARK: - 06: uplift and the commit

    func testTheFieldIsEmptyOnEveryEntryCutAt100GraphemesAndBackKeepsIt() async {
        await walkTo(.uplift)
        XCTAssertEqual(state.uplift, "")
        model.upliftChanged(String(repeating: "\u{1F600}", count: 130))
        XCTAssertEqual(graphemeCount(state.uplift), 100)
        model.upliftChanged("boat")
        model.backPressed(.uplift) {}
        await arrive(.filters, referrer: .checkinUplift)
        model.filtersContinue {}
        await arrive(.uplift, referrer: .checkinFilters)
        XCTAssertEqual(state.uplift, "boat")
    }

    func testContactInfoBlocksTheCommitToastsAndSendsNothing() async {
        await walkTo(.uplift)
        analytics.clear()
        model.upliftChanged("Text me on 0176 2345 6789")
        XCTAssertTrue(state.upliftBlocked)
        await model.findMyDate {}
        XCTAssertEqual(state.toast, CheckInCopy.contactError)
        await settle()
        let commits = await store.commits
        XCTAssertTrue(commits.isEmpty)
        XCTAssertTrue(analytics.names.isEmpty, "no request, no event")
        model.upliftChanged("Text me")
        XCTAssertFalse(state.upliftBlocked)
    }

    func testAnEmptyFieldCommitsWithNoPromptAndSuccessFiresStepAnsweredThenNavigates() async {
        await walkTo(.uplift)
        analytics.clear()
        ms += 9_000
        var order: [String] = []
        await model.findMyDate { order.append("navigate") }
        order.insert(contentsOf: analytics.names, at: 0)
        XCTAssertEqual(order, ["check_in_step_answered", "navigate"])
        let answered = props("check_in_step_answered")
        XCTAssertEqual(answered["step_id"] as? String, "uplift")
        XCTAssertEqual(answered["answer_value"] as? String, "empty")
        XCTAssertEqual(answered["time_to_answer_ms"] as? Int, 9_000)
        XCTAssertTrue(analytics.of("check_in_created").isEmpty, "check_in_created is the server's")
        XCTAssertTrue(analytics.of("check_in_completed").isEmpty)
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
        XCTAssertNil(commits.first?.uplift, "empty is stored as no uplift, never \"\"")
        XCTAssertFalse(model.flowAlive)
    }

    func testTheCommitCarriesBothDaysSlotsAsInstantsTheAnswersAndTheTrimmedText() async throws {
        await walkTo(.uplift)
        model.upliftChanged("  will take you out on my boat  ")
        await model.findMyDate {}
        let commits = await store.commits
        let request = try XCTUnwrap(commits.first)
        XCTAssertEqual(commits.count, 1)
        XCTAssertEqual(request.windowId, window)
        XCTAssertEqual(request.uplift, "will take you out on my boat")
        XCTAssertEqual(request.motivation, "long_term")
        XCTAssertEqual(request.vibes, ["calm"])
        let iso = ISO8601DateFormatter()
        XCTAssertEqual(request.slots, [
            CommitSlot(startsAt: try XCTUnwrap(iso.date(from: "2026-10-13T18:00:00Z")), tier: .preferred),
            CommitSlot(startsAt: try XCTUnwrap(iso.date(from: "2026-10-14T12:00:00Z")), tier: .preferred),
        ])
        XCTAssertEqual(analytics.of("check_in_step_answered").last?["answer_value"] as? String, "written")
    }

    func testWhileSavingTheButtonBackAndCloseAreInertAndADoubleTapSendsOneRequest() async {
        await walkTo(.uplift)
        await store.slowCommits(300_000_000)
        let first = Task { await self.model.findMyDate { self.landed += 1 } }
        await settle()
        await model.findMyDate { landed += 1 }
        XCTAssertTrue(state.saving)
        var back = false
        var closed = false
        model.backPressed(.uplift) { back = true }
        model.closePressed(.uplift) { closed = true }
        model.upliftChanged("changed while saving")
        XCTAssertFalse(back)
        XCTAssertFalse(closed)
        XCTAssertEqual(state.uplift, "")
        await first.value
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
        XCTAssertEqual(landed, 1)
        XCTAssertTrue(analytics.of("check_in_closed").isEmpty)
    }

    func testAFailedSaveKeepsEverythingReportsOncePerRequestAndTheRetryCountsUp() async {
        await walkTo(.uplift)
        await store.answerCommits([.network, .server])
        model.upliftChanged("boat")
        await model.findMyDate {}
        XCTAssertTrue(state.saveFailed)
        XCTAssertEqual(state.uplift, "boat")
        XCTAssertEqual(state.picks.count, 2)
        await model.findMyDate {}
        var landed = false
        await model.findMyDate { landed = true }
        XCTAssertTrue(landed)
        let failures = analytics.of("check_in_commit_failed")
        XCTAssertEqual(failures.map { $0["reason"] as? String }, ["network", "server"])
        XCTAssertEqual(failures.map { $0["attempt"] as? Int }, [1, 2])
        XCTAssertEqual(analytics.of("check_in_step_answered").filter { $0["step_id"] as? String == "uplift" }.count, 1,
                       "step_answered fires on the save, once")
    }

    func testTheSaveFailedCardClearsOnTheNextTapOrAnEdit() async {
        await walkTo(.uplift)
        await store.answerCommits([.network])
        await model.findMyDate {}
        XCTAssertTrue(state.saveFailed)
        model.upliftChanged("x")
        XCTAssertFalse(state.saveFailed)
    }

    func testAServerContactInfoRefusalShowsStateCNotTheSaveFailedCard() async {
        await walkTo(.uplift)
        await store.answerCommits([.contactInfo])
        model.upliftChanged("ping me on insta")
        await model.findMyDate {}
        XCTAssertTrue(state.upliftBlocked)
        XCTAssertFalse(state.saveFailed)
        XCTAssertEqual(props("check_in_commit_failed")["reason"] as? String, "contact_info")
        await model.findMyDate {}
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1, "a dimmed tap toasts and sends nothing")
        XCTAssertEqual(state.toast, CheckInCopy.contactError)
        model.upliftChanged("ping me")
        XCTAssertFalse(state.upliftBlocked)
    }

    func testFiltersThatDidNotSaveAreSavedBeforeTheCommit() async {
        await store.setFilterSaves(false)
        await walkTo(.uplift)
        await store.setFilterSaves(true)
        let before = await store.filterBodies.count
        await model.findMyDate {}
        let after = await store.filterBodies.count
        let commits = await store.commits
        XCTAssertEqual(after, before + 1)
        XCTAssertEqual(commits.count, 1)
    }

    func testASlowFilterSaveCannotLandAfterALaterOneAndTheCommitSendsTheLastSettings() async {
        await walkTo(.filters)
        await store.scriptFilterSaves([(300_000_000, true), (0, false)])   // A: slow, lands. B: fast, fails.
        await store.setFilterFailure(.server)                             // so the commit sends B again
        model.genderTapped("man")
        model.filtersContinue {}                                          // A
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        model.backPressed(.uplift) {}
        model.arrived(.filters, screen: CheckInStep.filters.screen, referrer: CheckInStep.uplift.screen)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                          // B, after A
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        await settle(0.1)
        await model.findMyDate {}
        let a = CheckInFilters(genders: ["woman", "man"])
        let b = CheckInFilters(genders: ["woman", "man", "non_binary"])
        let bodies = await store.filterBodies
        XCTAssertEqual(bodies, [a, b, b], "A, then B after it, then B again: A landing never stood in for B")
        let held = await store.held
        XCTAssertEqual(held.filters, b)
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
    }

    func testAFilterSaveThatFailedDoesNotBecomeTheSettingsTheNextFlowStartsFrom() async {
        await walkTo(.filters)
        await store.setFilterSaves(false)
        model.genderTapped("man")
        model.filtersContinue {}
        await settle()
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        model.closePressed(.uplift) {}
        XCTAssertEqual(state.server?.filters.genders, ["woman"])
        await begin()
        XCTAssertEqual(state.filters?.genders, ["woman"])
    }

    func testAFilterSaveTheServerRefusesIsReportedAsServerNotAsOffline() async {
        await store.setFilterSaves(false)
        await store.setFilterFailure(.server)
        await walkTo(.uplift)
        await model.findMyDate {}
        XCTAssertEqual(props("check_in_commit_failed")["reason"] as? String, "server")
        XCTAssertTrue(state.saveFailed)
        let commits = await store.commits
        XCTAssertTrue(commits.isEmpty, "nothing is committed over settings that did not save")
    }

    func testAReadTheCommitNeedsThatFailsIsReportedAsWhatItWas() async {
        await store.setReads(false)
        await store.setReadFailure(.server)
        await walkTo(.uplift)
        await model.findMyDate {}
        await store.setReadFailure(.network)
        await model.findMyDate {}
        XCTAssertEqual(analytics.of("check_in_commit_failed").map { $0["reason"] as? String }, ["server", "network"])
        let commits = await store.commits
        XCTAssertTrue(commits.isEmpty)
    }

    func testAFlowLeftOpenPastEveryPickedHourAsksForHoursAgainAndSendsNothing() async {
        await walkTo(.uplift)
        now = at(13, 30, day: 14)                                    // both picks have started
        await model.findMyDate {}
        XCTAssertEqual(state.toast, CheckInCopy.tomorrowToast)
        XCTAssertFalse(state.saveFailed, "not a failed save")
        XCTAssertFalse(state.saving)
        let commits = await store.commits
        XCTAssertTrue(commits.isEmpty, "nothing is sent")
        XCTAssertTrue(analytics.of("check_in_commit_failed").isEmpty, "not a commit failure either")
    }

    func testAServerThatFindsEveryHourStartedAsksForHoursAgainNotForAConnection() async {
        await walkTo(.uplift)
        await store.answerCommits([.noHours])
        await model.findMyDate {}
        XCTAssertEqual(state.toast, CheckInCopy.tomorrowToast)
        XCTAssertFalse(state.saveFailed)
        XCTAssertTrue(analytics.of("check_in_commit_failed").isEmpty)
    }

    func testAFilterSaveStillInFlightWhenTheFlowIsReopenedIsWhatTheNewFlowStartsFrom() async {
        await walkTo(.filters)
        await store.scriptFilterSaves([(300_000_000, true)])
        model.genderTapped("man")
        model.filtersContinue {}
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        model.closePressed(.uplift) {}
        model.beginFlow()                                             // before the save answers
        let chosen = CheckInFilters(genders: ["woman", "man"])
        XCTAssertEqual(state.filters, chosen)
        await settle(0.4)
        await arrive(.filters)
        model.filtersContinue {}
        await settle()
        let bodies = await store.filterBodies
        XCTAssertEqual(bodies, [chosen, chosen], "Continue never sends the older settings back")
        let held = await store.held
        XCTAssertEqual(held.filters, chosen)
    }

    func testFiltersAcceptedOnTheStandInAreSavedOnceTheServersArriveEvenIfTheFlowIsThenClosed() async {
        await store.setReads(false)
        var held = await store.held
        held.filters = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 20)
        await store.setHeld(held)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        model.genderTapped("woman")
        model.filtersContinue {}
        await store.setReads(true)
        await arrive(.uplift, referrer: .checkinFilters)             // its read resolves the stand-in
        await settle()
        let resolved = CheckInFilters(genders: ["woman"], minAge: 25, maxAge: 35, distanceKm: 20)
        let bodies = await store.filterBodies
        XCTAssertEqual(bodies, [resolved])
        model.closePressed(.uplift) {}
        let after = await store.held
        XCTAssertEqual(after.filters, resolved)
    }

    func testACommitDoesNotResendFiltersTheSaveItWaitedForJustFailedToSend() async {
        await walkTo(.filters)
        await store.scriptFilterSaves([(200_000_000, false)])         // no network, after a wait
        model.filtersContinue {}
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        await model.findMyDate {}
        var count = await store.filterBodies.count
        XCTAssertEqual(count, 1, "one wait, not two")
        XCTAssertEqual(props("check_in_commit_failed")["reason"] as? String, "network")
        await model.findMyDate {}
        count = await store.filterBodies.count
        XCTAssertEqual(count, 2, "the next tap tries again")
        let commits = await store.commits
        XCTAssertEqual(commits.count, 1)
    }

    func testTheStandInSaveSendsWhatContinueAcceptedNotAnEditMadeAfterGoingBack() async {
        await store.setReads(false)
        var held = await store.held
        held.filters = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 20)
        await store.setHeld(held)
        await walkTo(.vibe)
        model.vibeContinue {}
        await arrive(.filters)
        model.genderTapped("woman")
        model.filtersContinue {}                                     // accepted: a gender
        await arrive(.uplift, referrer: .checkinFilters)
        model.backPressed(.uplift) {}
        await arrive(.filters, referrer: CheckInStep.uplift.screen)
        model.ageMoved(thumb: 0, value: 40)                          // not confirmed by any Continue
        await store.setReads(true)
        model.refresh()
        await settle()
        let bodies = await store.filterBodies
        XCTAssertEqual(bodies, [CheckInFilters(genders: ["woman"], minAge: 25, maxAge: 35, distanceKm: 20)],
                       "the accepted settings, resolved against the server's")
        XCTAssertEqual(state.filters?.minAge, 40, "the step still shows the edit in progress")
    }

    func testAfterACommitWithNoAnswerARetryPastEveryHourGoesToTheServerWhichMayReplayIt() async {
        await walkTo(.uplift)
        await store.answerCommits([.network])                        // saved? nobody knows
        await model.findMyDate {}
        now = at(13, 30, day: 14)                                    // every pick has started since
        await model.findMyDate { self.landed += 1 }
        let commits = await store.commits
        XCTAssertEqual(commits.count, 2, "sent, not stopped on the device")
        XCTAssertEqual(commits.last?.slots.isEmpty, true)
        XCTAssertEqual(landed, 1, "the server's replay lands it")
    }

    func testOnlyTheNewestFilterSavesAnswerClearsWhatAReopenedFlowStartsFrom() async {
        await walkTo(.filters)
        // The first X answers at 0.4 s; Y not before 2.4 s -- a window wide enough that a slow runner
        // still checks the flow between the two, rather than after every save has answered.
        await store.scriptFilterSaves([(400_000_000, false), (2_000_000_000, true), (2_000_000_000, true)])
        let x = CheckInFilters(genders: ["woman", "man"])
        model.genderTapped("man")
        model.filtersContinue {}                                     // X
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        model.backPressed(.uplift) {}
        model.arrived(.filters, screen: CheckInStep.filters.screen, referrer: CheckInStep.uplift.screen)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                     // Y
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        model.backPressed(.uplift) {}
        model.arrived(.filters, screen: CheckInStep.filters.screen, referrer: CheckInStep.uplift.screen)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                     // X again
        model.arrived(.uplift, screen: CheckInStep.uplift.screen, referrer: .checkinFilters)
        await settle(0.6)                                            // the first X has answered, failed
        model.closePressed(.uplift) {}
        model.beginFlow()
        XCTAssertEqual(state.filters, x, "Y and the second X are still out: the flow starts from the newest")
    }

    func testTheUpliftCloseDiscardsTheTextAndFiresClosedUplift() async {
        await walkTo(.uplift)
        model.upliftChanged("boat")
        model.closePressed(.uplift) {}
        XCTAssertEqual(state.uplift, "")
        XCTAssertEqual(props("check_in_closed")["step_id"] as? String, "uplift")
    }

    /// The Kotlin twin's `CancellationException` path: a commit whose task ended is not a failure.
    func testACommitWhoseTaskIsCancelledReportsNothingAndFreesTheScreen() async {
        await walkTo(.uplift)
        await store.slowCommits(300_000_000)
        await store.answerCommits([.network])
        let commit = Task { await self.model.findMyDate {} }
        await settle()
        XCTAssertTrue(state.saving)
        commit.cancel()
        await commit.value
        XCTAssertTrue(analytics.of("check_in_commit_failed").isEmpty)
        XCTAssertFalse(state.saving)
        XCTAssertFalse(state.saveFailed)
    }

    /// Unreachable through the walk -- Motivation's Continue needs one -- but never a dead button.
    func testFindMyDateWithNoMotivationShowsTheSaveFailedCardAndSendsNothing() async {
        await begin()
        await arrive(.uplift)
        XCTAssertNil(state.motivation)
        await model.findMyDate {}
        XCTAssertTrue(state.saveFailed)
        let commits = await store.commits
        XCTAssertTrue(commits.isEmpty)
        XCTAssertTrue(analytics.of("check_in_commit_failed").isEmpty)
    }

    // MARK: - the whole flow

    func testAFullWalkFiresOnlyRegisteredNamesAndNoEventCarriesTheUpliftOrAGender() async {
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: nil)
        model.gateTapped(.cta) { _ in }
        await walkTo(.uplift)
        model.upliftChanged("will take you out on my boat")
        await model.findMyDate {}
        let allowed: Set<String> = [
            "screen_viewed", "check_in_started", "check_in_closed", "check_in_gate_tapped", "check_in_step_answered",
            "availability_slots_set", "check_in_no_hours_today_resolved", "motivation_selected", "vibe_selected",
            "check_in_filters_changed", "check_in_commit_failed",
        ]
        XCTAssertTrue(analytics.names.allSatisfy { allowed.contains($0) }, "\(analytics.names)")
        for (name, properties) in analytics.events {
            for value in properties.values {
                XCTAssertFalse(String(describing: value).contains("boat"), "\(name) carries the uplift")
                XCTAssertNotEqual(String(describing: value), "woman", "\(name) carries a gender")
            }
        }
        XCTAssertEqual(analytics.of("check_in_step_answered").map { $0["step_id"] as? String },
                       ["today", "tomorrow", "motivation", "vibe", "filters", "uplift"])
    }

    func testEveryPayloadNamesExactlyTheRegistrysFields() async {
        model.arrived(nil, screen: .checkinSearchUnavailable, referrer: nil)
        model.gateTapped(.cta) { _ in }
        await begin()
        model.arrived(.intro, screen: .checkinIntro, referrer: nil)
        model.introStarted {}
        await arrive(.today)
        model.availabilityContinue(.today) {}
        model.noHoursConfirmed {}
        await walkTo(.uplift)
        await store.answerCommits([.network])
        await model.findMyDate {}
        model.closePressed(.uplift) {}

        // events.json, family H, verbatim.
        let registry: [String: Set<String>] = [
            "check_in_started": ["window_id"],
            "check_in_closed": ["step_id", "window_id"],
            "check_in_gate_tapped": ["target", "intro_passed"],
            "check_in_step_answered": ["step_id", "answer_value", "time_to_answer_ms"],
            "availability_slots_set": ["day", "preferred_hours", "available_hours", "window_id"],
            "check_in_no_hours_today_resolved": ["action", "window_id"],
            "motivation_selected": ["motivation_id", "window_id"],
            "vibe_selected": ["vibe_ids", "vibe_count", "window_id"],
            "check_in_filters_changed": ["filters_changed", "min_age", "max_age", "distance_km", "age_dealbreaker",
                                         "distance_dealbreaker", "window_id"],
            "check_in_commit_failed": ["reason", "attempt", "window_id"],
        ]
        let stamp: Set<String> = ["sensitivity_class", "field_registry_version"]
        let seen = analytics.events.filter { $0.0 != "screen_viewed" }
        XCTAssertEqual(Set(seen.map(\.0)), Set(registry.keys))
        for (name, properties) in seen {
            XCTAssertEqual(Set(properties.keys).subtracting(stamp), registry[name], name)
            XCTAssertEqual(properties["field_registry_version"] as? String, "1.4.31", name)
        }
    }
}
