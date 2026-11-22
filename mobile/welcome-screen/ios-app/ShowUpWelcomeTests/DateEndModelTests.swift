// DateEndModelTests.swift
// ShowUp · Date 06's model: every frame from its entry, the gate continuation, the end and its failures, the events and
// their order, and process death (SHOWUP-197, R9, R11, R14). Android's `DateEndViewModelTest.kt`, case for case.
//
// The store is a fake the test steers; the clock is passed to `bind`, as the host's 1 s tick does. Process death is a
// second model attached to the string the first one wrote.

import XCTest
@testable import ShowUpWelcome

/// Answers from values the test sets; records every call.
actor FakeEndStore: DateEndStoring {
    struct Call: Equatable, Sendable {
        let dateId: String
        let reason: EndReason
        let entry: EndEntry
    }

    private(set) var calls: [Call] = []
    private var answers: [EndDateResult] = []
    private(set) var flags = 0
    private var holdSeconds: Double = 0

    func queue(_ answer: EndDateResult) { answers.append(answer) }
    func hold(_ seconds: Double) { holdSeconds = seconds }

    func end(dateId: String, reason: EndReason, entry: EndEntry) async -> EndDateResult {
        calls.append(Call(dateId: dateId, reason: reason, entry: entry))
        if holdSeconds > 0 { try? await Task.sleep(nanoseconds: UInt64(holdSeconds * 1_000_000_000)) }
        return answers.isEmpty ? .ended(Self.outcome(reason)) : answers.removeFirst()
    }

    func flagDiscomfort(dateId: String) async -> Bool {
        flags += 1
        return true
    }

    static func outcome(_ reason: EndReason) -> EndedOutcome {
        let how: OutcomeReason
        switch reason {
        case .met: how = .met
        case .partnerNoShow: how = .partnerNoShow
        case .uncomfortable: how = .uncomfortable
        }
        return EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: true, reason: how)
    }
}

@MainActor
final class DateEndModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-21T18:00:00Z
    private var before: Date { meet.addingTimeInterval(-5 * 60) }
    private var after: Date { meet.addingTimeInterval(11 * 60) }
    private let analytics = RecordingAnalytics()
    private let store = FakeEndStore()
    private var rereads = 0

    private final class Draft {
        var value = ""
    }

    private func date(checkedIn: Bool = true, endsEnabled: Bool = true, id: String = "d1", chatOpen: Bool = true) -> ActiveDate {
        var d = DateSamples.date
        d.id = id
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = chatOpen
        d.checkedIn = checkedIn
        d.endsEnabled = endsEnabled
        return d
    }

    /// A model as the host builds it: attached to the draft, then bound.
    private func fresh(_ draft: Draft = Draft()) -> DateEndModel {
        let m = DateEndModel(store: store, analytics: analytics)
        m.reread = { self.rereads += 1 }
        m.attach(stored: draft.value) { draft.value = $0 }
        return m
    }

    private func model(checkedIn: Bool = true, now: Date? = nil, draft: Draft = Draft(), endsEnabled: Bool = true) -> DateEndModel {
        let m = fresh(draft)
        m.bind(date(checkedIn: checkedIn, endsEnabled: endsEnabled), now: now ?? before)
        return m
    }

    private func settle(_ seconds: Double = 0.12) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func names() -> [String] { analytics.names() }

    /// The event names fired after the first `count`.
    private func names(after count: Int) -> [String] { Array(names().dropFirst(count)) }

    private func toNoShowConfirm(_ m: DateEndModel, route: EndRoute = .wrap) {
        if route == .wrap { m.openWrap(.endDateBar) } else { m.openReport() }
        m.pick(.noShow)
        m.confirmWait()
    }

    // MARK: every frame from its entry

    func testTheBarOpensBAndFiresWrapupOpenedWithTheEntryCheckInStateAndMinutes() {
        let m = model()
        m.openWrap(.endDateBar)
        XCTAssertEqual(m.ui.route, .wrap)
        XCTAssertEqual(m.ui.step, .menu)
        XCTAssertTrue(m.ui.visible)
        let e = analytics.of("wrapup_opened")
        XCTAssertEqual(e.count, 1)
        let p = e[0].properties
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["entry"] as? String, "end_date_bar")
        XCTAssertEqual(p["checked_in"] as? Bool, true)
        XCTAssertEqual(p["minutes_vs_start"] as? Int, -5)
    }

    func testTheCheckInBarsLinkOpensBWithEntryCheckinBarLink() {
        let m = model(checkedIn: false)
        m.openWrap(.checkinBarLink)
        let p = analytics.of("wrapup_opened")[0].properties
        XCTAssertEqual(p["entry"] as? String, "checkin_bar_link")
        XCTAssertEqual(p["checked_in"] as? Bool, false)
    }

    func testTheReportCardOpensHAndFiresProblemReportOpened() {
        let m = model()
        m.openReport()
        XCTAssertEqual(m.ui.route, .report)
        XCTAssertEqual(m.ui.step, .menu)
        let p = analytics.of("problem_report_opened")[0].properties
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["checked_in"] as? Bool, true)
        XCTAssertEqual(p["minutes_vs_start"] as? Int, -5)
        XCTAssertEqual(names(), ["problem_report_opened"])
    }

    func testASecondOpenWhileASheetIsUpDoesNothing() {
        let m = model()
        m.openWrap(.endDateBar)
        m.openReport()
        m.openWrap(.checkinBarLink)
        XCTAssertEqual(m.ui.route, .wrap)
        XCTAssertEqual(names(), ["wrapup_opened"])
    }

    func testNothingOpensWhenTheDateHasTheEndsSwitchedOffOrNoneIsBound() {
        let off = model(endsEnabled: false)
        off.openWrap(.endDateBar)
        off.openReport()
        XCTAssertNil(off.ui.route)
        let unbound = fresh()
        unbound.openWrap(.endDateBar)
        XCTAssertNil(unbound.ui.route)
        XCTAssertEqual(names(), [])
    }

    func testWeMetGoesToCWithNoGateEvenWhenNotCheckedInAndFiresTheOutcomeOnTheTap() {
        let m = model(checkedIn: false)
        m.openWrap(.checkinBarLink)
        m.pick(.met)
        XCTAssertEqual(m.ui.step, .met)
        XCTAssertNil(m.ui.openGate)
        let p = analytics.of("wrapup_outcome_selected")[0].properties
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["outcome"] as? String, "met")
        XCTAssertEqual(p["checked_in"] as? Bool, false)
    }

    func testCheckedInMyDateIsntShowingUpGoesFromBToTheGuide() {
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        XCTAssertEqual(m.ui.step, .guide)
        XCTAssertNil(m.ui.openGate)
        XCTAssertEqual(analytics.of("wrapup_outcome_selected")[0].properties["outcome"] as? String, "partner_no_show")
    }

    func testCheckedInHReachesTheGuideAndIWithTheCategoryFiredOnTheTap() {
        let m = model()
        m.openReport()
        m.pick(.noShow)
        XCTAssertEqual(m.ui.step, .guide)
        m.back()
        m.pick(.uncomfortable)
        XCTAssertEqual(m.ui.step, .uncomfortable)
        let cats = analytics.of("problem_category_selected").map(\.properties)
        XCTAssertEqual(cats.map { $0["category"] as? String }, ["partner_no_show", "uncomfortable"])
        XCTAssertTrue(cats.allSatisfy { $0["date_id"] as? String == "d1" && $0["checked_in"] as? Bool == true })
    }

    func testTheOptionThatDoesNotBelongToASheetIsIgnored() {
        let wrap = model()
        wrap.openWrap(.endDateBar)
        wrap.pick(.uncomfortable)
        XCTAssertEqual(wrap.ui.step, .menu)
        let report = fresh()
        report.bind(date(), now: before)
        report.openReport()
        report.pick(.met)
        XCTAssertEqual(report.ui.step, .menu)
        XCTAssertEqual(names(), ["wrapup_opened", "problem_report_opened"])
    }

    func testTheGuidesEndButtonOpensGOnlyFromTheMeetTimeAndATapBeforeItFiresNothing() {
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        let fired = names().count
        m.confirmWait()
        XCTAssertEqual(m.ui.step, .guide)
        XCTAssertEqual(names(after: fired), [])
        m.bind(date(), now: meet) // the clock passes the meet time while the sheet is open
        XCTAssertTrue(m.ui.pastMeet)
        m.confirmWait()
        XCTAssertEqual(m.ui.step, .noShow)
        XCTAssertEqual(names(after: fired), [])
    }

    func testBackWalksOneStepUpAndCancelOnBCOrHClosesNoneOfThemFiringAnything() async {
        let m = model(now: after)
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        m.confirmWait()
        var fired = names().count
        m.back()
        XCTAssertEqual(m.ui.step, .guide)
        m.back()
        XCTAssertEqual(m.ui.step, .menu)
        m.pick(.met)
        fired = names().count
        m.back() // Cancel on C returns to B
        XCTAssertEqual(m.ui.step, .menu)
        m.close()
        XCTAssertNil(m.ui.route)
        XCTAssertEqual(names(after: fired), [])
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls.count, 0)
    }

    func testMessageClosesTheSheetAndAsksTheHostForTheChatOnlyWhileTheChatIsOpen() {
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        let fired = names().count
        m.message()
        XCTAssertNil(m.ui.route)
        XCTAssertTrue(m.ui.openChat)
        XCTAssertEqual(names(after: fired), [])
        m.consumeOpenChat()
        XCTAssertFalse(m.ui.openChat)

        let locked = fresh()
        locked.bind(date(chatOpen: false), now: before)
        locked.openWrap(.endDateBar)
        locked.pick(.noShow)
        locked.message()
        XCTAssertEqual(locked.ui.step, .guide)
        XCTAssertFalse(locked.ui.chatOpen)
    }

    // MARK: the gate (R9)

    func testNotCheckedInBsNoShowAsksForTheGateWithEntryWrapupAndHidesTheSheet() {
        let m = model(checkedIn: false)
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        XCTAssertEqual(m.ui.openGate, .wrapup)
        XCTAssertEqual(m.ui.gated, .guide)
        XCTAssertFalse(m.ui.visible)
        let p = analytics.of("wrapup_outcome_selected")[0].properties
        XCTAssertEqual(p["outcome"] as? String, "partner_no_show")
        XCTAssertEqual(p["checked_in"] as? Bool, false)
    }

    func testNotCheckedInHsTwoProblemRoutesAskForTheGateWithEntryReport() {
        let m = model(checkedIn: false)
        m.openReport()
        m.pick(.uncomfortable)
        XCTAssertEqual(m.ui.openGate, .report)
        XCTAssertEqual(m.ui.gated, .uncomfortable)
        m.consumeGate()
        XCTAssertNil(m.ui.openGate)
        m.gateNotYet()
        m.pick(.noShow)
        XCTAssertEqual(m.ui.openGate, .report)
        XCTAssertEqual(m.ui.gated, .guide)
    }

    func testYesThenCloseReopensTheSheetAtThePickedStepFromBAndFromH() {
        let a = model(checkedIn: false)
        a.openWrap(.endDateBar)
        a.pick(.noShow)
        a.consumeGate()
        a.gateCommitted()
        XCTAssertEqual(a.ui.step, .guide)
        XCTAssertNil(a.ui.gated)
        XCTAssertTrue(a.ui.visible)
        a.bind(date(checkedIn: true), now: before)
        XCTAssertEqual(a.ui.step, .guide)

        let b = fresh()
        b.bind(date(checkedIn: false), now: before)
        b.openReport()
        b.pick(.uncomfortable)
        b.gateCommitted()
        XCTAssertEqual(b.ui.step, .uncomfortable)
        XCTAssertTrue(b.ui.visible)
    }

    func testACommitCountsAsCheckedInEvenBeforeTheDateReadSaysSo() {
        let m = model(checkedIn: false)
        m.openReport()
        m.pick(.noShow)
        m.gateCommitted()
        m.bind(date(checkedIn: false), now: before) // a stale read
        XCTAssertEqual(m.ui.step, .guide)
        XCTAssertTrue(m.ui.checkedIn)
    }

    func testNotYetReopensTheMenuTheRouteCameFromBOrH() {
        let a = model(checkedIn: false)
        a.openWrap(.endDateBar)
        a.pick(.noShow)
        a.gateNotYet()
        XCTAssertEqual(a.ui.route, .wrap)
        XCTAssertEqual(a.ui.step, .menu)
        XCTAssertTrue(a.ui.visible)

        let b = fresh()
        b.bind(date(checkedIn: false), now: before)
        b.openReport()
        b.pick(.uncomfortable)
        b.gateNotYet()
        XCTAssertEqual(b.ui.route, .report)
        XCTAssertEqual(b.ui.step, .menu)
    }

    func testUndoClosesEverything() {
        let m = model(checkedIn: false)
        m.openReport()
        m.pick(.noShow)
        m.gateUndone()
        XCTAssertNil(m.ui.route)
        XCTAssertNil(m.ui.gated)
        XCTAssertFalse(m.ui.visible)
    }

    func testTheGateSignalsDoNothingWhenNoGatedRouteIsWaiting() {
        let m = model()
        m.openWrap(.endDateBar)
        m.gateCommitted()
        m.gateNotYet()
        XCTAssertEqual(m.ui.step, .menu)
        XCTAssertEqual(m.ui.route, .wrap)
    }

    // MARK: ending

    func testEndDateToReviewEndsWithReasonMetAndTheBarsEntryThenLands() async {
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.met)
        m.end()
        XCTAssertTrue(m.ui.ending)
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls, [FakeEndStore.Call(dateId: "d1", reason: .met, entry: .endDateBar)])
        XCTAssertEqual(m.ui.landed?.reason, .met)
        XCTAssertNil(m.ui.route)
        XCTAssertFalse(m.ui.ending)
        m.consumeLanded()
        XCTAssertNil(m.ui.landed)
    }

    func testTheLinksEntryAndTheReportsEntryGoOnTheWire() async {
        let a = model(now: after)
        a.openWrap(.checkinBarLink)
        a.pick(.met)
        a.end()
        await settle()
        let b = fresh()
        b.bind(date(), now: after)
        toNoShowConfirm(b, route: .report)
        b.end()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls.map(\.entry), [.checkinBarLink, .report])
        XCTAssertEqual(calls.map(\.reason), [.met, .partnerNoShow])
    }

    func testHeadOutNowEndsWithUncomfortable() async {
        let m = model()
        m.openReport()
        m.pick(.uncomfortable)
        m.end()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls.map(\.reason), [.uncomfortable])
        XCTAssertEqual(m.ui.landed?.reason, .uncomfortable)
    }

    func testTheEndFromTheMenuOrTheGuideSendsNothing() async {
        let m = model(now: after)
        m.openWrap(.endDateBar)
        m.end()
        m.pick(.noShow)
        m.end()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls.count, 0)
    }

    func testAnEndInFlightDisablesBothButtonsASecondTapSendsNothingAndBackAndCancelAreInert() async {
        await store.hold(0.3)
        let m = model(now: after)
        toNoShowConfirm(m)
        m.end()
        await settle(0.05)
        m.end()
        m.back()
        m.close()
        XCTAssertTrue(m.ui.ending)
        XCTAssertEqual(m.ui.step, .noShow)
        var calls = await store.calls
        XCTAssertEqual(calls.count, 1)
        await settle(0.5)
        XCTAssertFalse(m.ui.ending)
        calls = await store.calls
        XCTAssertEqual(calls.count, 1)
    }

    func testAFailureKeepsTheStepWithTheErrorBackStillWorksAndARetrySucceedsOnce() async {
        await store.queue(.failed(.network))
        let m = model(now: after)
        toNoShowConfirm(m)
        m.end()
        await settle()
        XCTAssertEqual(m.ui.step, .noShow)
        XCTAssertTrue(m.ui.failed)
        XCTAssertFalse(m.ui.ending)
        XCTAssertNil(m.ui.landed)
        m.end()
        await settle()
        var calls = await store.calls
        XCTAssertEqual(calls.count, 2)
        XCTAssertEqual(m.ui.landed?.reason, .partnerNoShow)
        XCTAssertFalse(m.ui.failed)

        await store.queue(.failed(.server))
        let other = fresh()
        other.bind(date(), now: after)
        toNoShowConfirm(other)
        other.end()
        await settle()
        XCTAssertTrue(other.ui.failed)
        other.back()
        XCTAssertEqual(other.ui.step, .guide)
        XCTAssertFalse(other.ui.failed)
        calls = await store.calls
        XCTAssertEqual(calls.count, 3)
    }

    func testARetryKeepsTheErrorBoxUpUntilTheResultAndASecondFailureKeepsIt() async {
        await store.queue(.failed(.network))
        let m = model(now: after)
        toNoShowConfirm(m)
        m.end()
        await settle()
        XCTAssertTrue(m.ui.failed)
        await store.hold(0.3)
        await store.queue(.failed(.server))
        m.end()
        await settle(0.05)
        XCTAssertTrue(m.ui.ending)
        XCTAssertTrue(m.ui.failed, "the box stays while the retry is in flight")
        await settle(0.5)
        XCTAssertTrue(m.ui.failed, "a second failure keeps it")
        XCTAssertFalse(m.ui.ending)
        m.end()
        await settle(0.5)
        XCTAssertFalse(m.ui.failed)
        XCTAssertEqual(m.ui.landed?.reason, .partnerNoShow)
    }

    func test409DateNotActiveClosesTheSheetAndReadsTheDateAgain() async {
        await store.queue(.refused(code: "date_not_active"))
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.met)
        m.end()
        await settle()
        XCTAssertNil(m.ui.route)
        XCTAssertNil(m.ui.landed)
        XCTAssertEqual(rereads, 1)
    }

    func test409BeforeMeetReturnsToTheGuideAnd409NotCheckedInToTheMenuEachReadingAgain() async {
        await store.queue(.refused(code: "before_meet"))
        let m = model(now: after)
        toNoShowConfirm(m)
        m.end()
        await settle()
        XCTAssertEqual(m.ui.step, .guide)
        XCTAssertFalse(m.ui.failed)
        XCTAssertEqual(rereads, 1)

        await store.queue(.refused(code: "not_checked_in"))
        let other = fresh()
        other.bind(date(), now: after)
        toNoShowConfirm(other)
        other.end()
        await settle()
        XCTAssertEqual(other.ui.step, .menu)
        XCTAssertEqual(rereads, 2)
    }

    func testASwitchedOffRouteClosesTheSheetAnUnknownRefusalIsAFailure() async {
        await store.queue(.refused(code: refusedNotFound))
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.met)
        m.end()
        await settle()
        XCTAssertNil(m.ui.route)

        await store.queue(.refused(code: refusedUnknown))
        let other = fresh()
        other.bind(date(), now: before)
        other.openWrap(.endDateBar)
        other.pick(.met)
        other.end()
        await settle()
        XCTAssertTrue(other.ui.failed)
        XCTAssertEqual(other.ui.step, .met)
    }

    func testStayForNowFlagsTheDiscomfortClosesTheSheetAndEachTapFlagsAgain() async {
        let m = model()
        m.openReport()
        m.pick(.uncomfortable)
        let fired = names().count
        m.stay()
        await settle()
        var flags = await store.flags
        XCTAssertEqual(flags, 1)
        XCTAssertNil(m.ui.route)
        XCTAssertEqual(names(after: fired), []) // problem_stay_flagged is the server's
        m.openReport()
        m.pick(.uncomfortable)
        m.stay()
        await settle()
        flags = await store.flags
        XCTAssertEqual(flags, 2)
    }

    func testStayForNowOutsideTheUncomfortableStepDoesNothing() async {
        let m = model()
        m.openReport()
        m.stay()
        await settle()
        let flags = await store.flags
        XCTAssertEqual(flags, 0)
    }

    // MARK: events: the whole list, in order

    func testAFullNoShowWalkFiresTheClientEventsInTapOrderAndNeverDateEnded() async {
        let m = model(checkedIn: false, now: before)
        m.openReport()
        m.pick(.noShow) // the gate
        m.gateCommitted()
        m.bind(date(), now: after)
        m.confirmWait()
        m.end()
        await settle()
        XCTAssertEqual(names(), ["problem_report_opened", "problem_category_selected"])
        XCTAssertFalse(names().contains { $0 == "date_ended" || $0 == "problem_stay_flagged" })
    }

    func testNoPayloadCarriesACoordinateAPlaceOrFreeText() {
        let m = model()
        m.openWrap(.endDateBar)
        m.pick(.noShow)
        let keys = analytics.entries.flatMap { $0.properties.keys }
        XCTAssertTrue(keys.allSatisfy { !($0.contains("lat") || $0.contains("lon") || $0.contains("place") || $0.contains("text")) })
    }

    // MARK: R14: process death

    func testARestoredNoShowConfirmIsKeptAfterTheMeetTimeAndReturnsToTheGuideBeforeIt() {
        let draft = Draft()
        let first = model(now: after, draft: draft)
        toNoShowConfirm(first)
        XCTAssertEqual(first.ui.step, .noShow)

        let second = fresh(draft)
        second.bind(date(), now: after)
        XCTAssertEqual(second.ui.route, .wrap)
        XCTAssertEqual(second.ui.step, .noShow)

        let third = fresh(draft)
        third.bind(date(), now: before)
        XCTAssertEqual(third.ui.step, .guide)
    }

    func testARestoredGatedStepWithoutACheckInGoesBackToTheMenu() {
        let draft = Draft()
        let first = model(checkedIn: false, draft: draft)
        first.openReport()
        first.pick(.uncomfortable)
        XCTAssertEqual(first.ui.gated, .uncomfortable)
        // What the process left behind, before either restore re-validates it and saves the result.
        let snapshot = Draft()
        snapshot.value = draft.value

        let restored = fresh(draft)
        restored.bind(date(checkedIn: false), now: before)
        XCTAssertEqual(restored.ui.route, .report)
        XCTAssertEqual(restored.ui.step, .menu)
        XCTAssertNil(restored.ui.gated)
        XCTAssertTrue(restored.ui.visible)

        let checked = fresh(snapshot)
        checked.bind(date(checkedIn: true), now: before)
        XCTAssertEqual(checked.ui.step, .uncomfortable)
    }

    func testARestoredStepThatNeedsACheckInGoesToTheMenuWhenThereIsNone() {
        let draft = Draft()
        let first = model(draft: draft)
        first.openReport()
        first.pick(.uncomfortable)
        let restored = fresh(draft)
        restored.bind(date(checkedIn: false), now: before)
        XCTAssertEqual(restored.ui.step, .menu)
    }

    func testARestoredSheetIsClosedWhenTheEndsAreSwitchedOffOrItIsAnotherDate() {
        let draft = Draft()
        model(draft: draft).openWrap(.endDateBar)
        let off = fresh(draft)
        off.bind(date(endsEnabled: false), now: before)
        XCTAssertNil(off.ui.route)
        let other = fresh(draft)
        other.bind(date(id: "d2"), now: before)
        XCTAssertNil(other.ui.route)
    }

    func testARestoreFiresNoEvent() {
        let draft = Draft()
        model(draft: draft).openWrap(.endDateBar)
        let fired = names().count
        let restored = fresh(draft)
        restored.bind(date(), now: before)
        XCTAssertEqual(names(after: fired), [])
    }

    func testADateReadThatCameBeforeTheRestoreStillRestoresItsOwnDraftAndDropsAnotherDates() {
        let draft = Draft()
        model(draft: draft).openWrap(.endDateBar)
        let late = DateEndModel(store: store, analytics: analytics)
        late.bind(date(), now: before)
        late.attach(stored: draft.value) { draft.value = $0 }
        XCTAssertEqual(late.ui.route, .wrap)

        let stale = DateEndModel(store: store, analytics: analytics)
        stale.bind(date(id: "d2"), now: before)
        stale.attach(stored: draft.value) { _ in }
        XCTAssertNil(stale.ui.route)
    }

    func testAClosedSheetLeavesNothingToRestore() {
        let draft = Draft()
        let first = model(draft: draft)
        first.openWrap(.endDateBar)
        first.close()
        XCTAssertEqual(draft.value, "")
        let restored = fresh(draft)
        restored.bind(date(), now: before)
        XCTAssertNil(restored.ui.route)
    }

    func testADraftThatDoesNotDecodeIsNoDraft() {
        let draft = Draft()
        draft.value = "{\"route\":\"nowhere\"}"
        let m = fresh(draft)
        m.bind(date(), now: before)
        XCTAssertNil(m.ui.route)
    }

    func testAnotherDateResetsTheModel() {
        let m = model()
        m.openWrap(.endDateBar)
        m.bind(date(id: "d2"), now: before)
        XCTAssertNil(m.ui.route)
    }
}
