// CancelModelTests.swift
// ShowUp · Date 07's model: the guard's open and its exits, the picker over it, the cancel in flight, E and its retry,
// the two 409s, the landing and process death (SHOWUP-198). Android's `CancelViewModelTest.kt`, case for case.
//
// The store is a fake the test steers; the clock is passed to `bind`, as the host's 1 s tick does. Process death is a
// second model attached to the string the first one wrote.

import XCTest
@testable import ShowUpWelcome

/// Answers from values the test sets; records every call.
actor FakeCancelStore: CancelStoring {
    private(set) var calls: [String] = []
    private var answers: [CancelResult] = []
    private var holdSeconds: Double = 0

    func queue(_ answer: CancelResult) { answers.append(answer) }
    func hold(_ seconds: Double) { holdSeconds = seconds }

    func cancel(dateId: String) async -> CancelResult {
        calls.append(dateId)
        if holdSeconds > 0 { try? await Task.sleep(nanoseconds: UInt64(holdSeconds * 1_000_000_000)) }
        return answers.isEmpty ? .cancelled(Self.outcome()) : answers.removeFirst()
    }

    static func outcome() -> EndedOutcome {
        EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: true, reason: .cancelled)
    }
}

@MainActor
final class CancelModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-18T18:00:00Z (19:00 Berlin)
    private var early: Date { meet.addingTimeInterval(-3 * 3600) }
    private let analytics = RecordingAnalytics()
    private let store = FakeCancelStore()
    private var rereads = 0

    private final class Draft {
        var value = ""
    }

    private func change(allowed: Bool = true) -> DateChange {
        DateChange(cutoffAt: meet.addingTimeInterval(-30 * 60), allowed: allowed, slots: [meet.addingTimeInterval(900)],
                   timeWas: nil, placeWas: nil, pending: [])
    }

    /// The change block a test date carries: Date 05's usual one, one given, or none (a server before Date 05).
    private enum Block {
        case usual, absent
        case given(DateChange)
    }

    private func date(id: String = "d1", checkedIn: Bool = false, endsEnabled: Bool = true, chatOpen: Bool = false,
                      penalty: Int = 10, block: Block = .usual) -> ActiveDate {
        var d = DateSamples.date
        d.id = id
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = chatOpen
        d.checkedIn = checkedIn
        d.endsEnabled = endsEnabled
        d.cancelPenaltyPoints = penalty
        switch block {
        case .usual: d.change = change()
        case .absent: d.change = nil
        case .given(let given): d.change = given
        }
        return d
    }

    /// A model as the host builds it: attached to the draft, then bound.
    private func fresh(_ draft: Draft = Draft()) -> CancelModel {
        let m = CancelModel(store: store, analytics: analytics)
        m.reread = { self.rereads += 1 }
        m.attach(stored: draft.value) { draft.value = $0 }
        return m
    }

    private func model(_ d: ActiveDate? = nil, now: Date? = nil, draft: Draft = Draft()) -> CancelModel {
        let m = fresh(draft)
        m.bind(d ?? date(), now: now ?? early)
        return m
    }

    private func settle(_ seconds: Double = 0.12) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func names() -> [String] { analytics.names() }

    // MARK: the guard opens

    func testOpenShowsTheSheetAndFiresGuardShownWithTheChatThePenaltyAndTheMinutes() {
        let m = model(date(chatOpen: true, penalty: 7))
        m.open()
        XCTAssertTrue(m.ui.open)
        let p = analytics.of("cancel_guard_shown").first?.properties ?? [:]
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["chat_open"] as? Bool, true)
        XCTAssertEqual(p["penalty_points"] as? Int, 7)
        XCTAssertEqual(p["minutes_before_start"] as? Int, 180)
    }

    func testChatOpenIsFalseWhileTheChatIsLocked() {
        let m = model(date(chatOpen: false))
        m.open()
        XCTAssertEqual(analytics.of("cancel_guard_shown").first?.properties["chat_open"] as? Bool, false)
    }

    func testMinutesBeforeStartIsNegativeAfterTheMeetTime() {
        let m = model(date(), now: meet.addingTimeInterval(10 * 60))
        m.open()
        XCTAssertEqual(analytics.of("cancel_guard_shown").first?.properties["minutes_before_start"] as? Int, -10)
    }

    func testItFiresOnEveryOpenNotOncePerSession() {
        let m = model()
        m.open()
        m.keep()
        m.open()
        m.dismiss()
        m.open()
        XCTAssertEqual(analytics.of("cancel_guard_shown").count, 3)
    }

    func testASecondOpenWhileItIsUpFiresNothing() {
        let m = model()
        m.open()
        m.open()
        XCTAssertEqual(analytics.of("cancel_guard_shown").count, 1)
    }

    func testNothingOpensWhenTheEndsAreOffAfterThisUsersCheckInOrWithNoDate() {
        let off = model(date(endsEnabled: false))
        off.open()
        let checked = model(date(checkedIn: true))
        checked.open()
        let unbound = fresh()
        unbound.open()
        XCTAssertFalse(off.ui.open || checked.ui.open || unbound.ui.open)
        XCTAssertEqual(names(), [])
    }

    func testTheMatchsCheckInDoesNotStopItOpening() {
        var d = date(chatOpen: true)
        d.arrival = DateArrival(checkedInAt: meet.addingTimeInterval(-600))
        let m = model(d)
        m.open()
        XCTAssertTrue(m.ui.open)
    }

    // MARK: the exits

    func testDontCancelMyDateClosesAndFiresOneAbandonedWithExitKeep() async {
        let m = model()
        m.open()
        m.keep()
        XCTAssertFalse(m.ui.open)
        let p = analytics.of("cancel_abandoned_at_guard").first?.properties ?? [:]
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 1)
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["exit"] as? String, "keep")
        XCTAssertEqual(p["change_requested"] as? Bool, false)
        let calls = await store.calls
        XCTAssertEqual(calls, [])
    }

    func testTheScrimAndASwipeCloseWithExitDismissedOnce() {
        let m = model()
        m.open()
        m.dismiss()
        m.dismiss()
        m.keep()
        let all = analytics.of("cancel_abandoned_at_guard")
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all.first?.properties["exit"] as? String, "dismissed")
    }

    func testMessageInsteadClosesTheFlowAsksTheHostForTheChatAndFiresExitMessage() async {
        let m = model(date(chatOpen: true))
        m.open()
        m.messageInstead()
        XCTAssertFalse(m.ui.open)
        XCTAssertTrue(m.ui.openChat)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").first?.properties["exit"] as? String, "message")
        let calls = await store.calls
        XCTAssertEqual(calls, [])
        m.consumeOpenChat()
        XCTAssertFalse(m.ui.openChat)
    }

    func testMessageInsteadIsInertWhileTheChatIsLockedAndTheChangeIsOpen() {
        let m = model(date(chatOpen: false))
        m.open()
        m.messageInstead()
        XCTAssertTrue(m.ui.open)
        XCTAssertFalse(m.ui.openChat)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
    }

    func testMessageInsteadAnswersAtTheCutoffTheChatBeingOpenByThenI() {
        let m = model(date(chatOpen: false), now: meet.addingTimeInterval(-10 * 60))
        m.open()
        XCTAssertTrue(m.ui.changeClosed)
        m.messageInstead()
        XCTAssertTrue(m.ui.openChat)
    }

    func testTheClockPassingTheCutoffWhileTheSheetIsOpenClosesTheChangeInPlace() {
        let m = model(date(chatOpen: false), now: meet.addingTimeInterval(-31 * 60))
        m.open()
        XCTAssertFalse(m.ui.changeClosed)
        XCTAssertFalse(m.ui.showMessage)
        m.bind(date(chatOpen: false), now: meet.addingTimeInterval(-30 * 60))
        XCTAssertTrue(m.ui.changeClosed)
        XCTAssertTrue(m.ui.showMessage)
        XCTAssertTrue(m.ui.open)
    }

    func testWithNoChangeBlockTheSheetIsNotClosedAndMessageInsteadFollowsTheChatAlone() {
        let locked = model(date(chatOpen: false, block: .absent), now: meet.addingTimeInterval(-10 * 60))
        locked.open()
        XCTAssertFalse(locked.ui.changeClosed)
        XCTAssertFalse(locked.ui.showMessage)
        XCTAssertFalse(locked.ui.bodyShown)
        locked.messageInstead()
        XCTAssertTrue(locked.ui.open)
        XCTAssertFalse(locked.ui.openChat)
        locked.picker(.time)
        XCTAssertNil(locked.ui.openPicker)

        let open = model(date(chatOpen: true, block: .absent))
        open.open()
        XCTAssertTrue(open.ui.showMessage)
        open.messageInstead()
        XCTAssertTrue(open.ui.openChat)
    }

    // MARK: the pickers over the sheet

    func testATileAsksTheHostForItsPickerAndIsNotAnExit() {
        let m = model()
        m.open()
        m.picker(.time)
        XCTAssertEqual(m.ui.openPicker, .time)
        XCTAssertTrue(m.ui.open)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
        m.consumePicker()
        XCTAssertNil(m.ui.openPicker)
        // Back from the picker unchanged: the sheet is still there and nothing was sent.
        XCTAssertTrue(m.ui.open)
        XCTAssertNil(m.ui.sent)
    }

    func testATileThatDate05HidesAsksForNothing() {
        let m = model(date(block: .given(change(allowed: false))))
        m.open()
        m.picker(.place)
        XCTAssertNil(m.ui.openPicker)
    }

    func testASentRequestShowsDWithItsValueKeepsTheSheetOpenAndMarksTheSession() {
        let m = model()
        m.open()
        m.picker(.time)
        m.consumePicker()
        m.pickerSent(kind: .time, label: "20:00")
        XCTAssertTrue(m.ui.open)
        XCTAssertEqual(m.ui.sent, SentRequest(kind: .time, label: "20:00"))
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
        m.keep()
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").first?.properties["change_requested"] as? Bool, true)
    }

    func testANewOpenStartsCleanNoSentValueNoChangeRequested() {
        let m = model()
        m.open()
        m.pickerSent(kind: .place, label: "Bar Lenz")
        m.dismiss()
        m.open()
        XCTAssertNil(m.ui.sent)
        m.keep()
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").last?.properties["change_requested"] as? Bool, false)
    }

    // MARK: Cancel anyway

    func testSuccessLandsTheCancellerClosesTheSheetAndFiresNoAbandonedAndNoDateCancelled() async {
        let m = model()
        m.open()
        m.cancel()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls, ["d1"])
        XCTAssertFalse(m.ui.open)
        XCTAssertFalse(m.ui.cancelling)
        XCTAssertEqual(m.ui.landed, FakeCancelStore.outcome())
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
        XCTAssertFalse(names().contains("date_cancelled"))
        m.consumeLanded()
        XCTAssertNil(m.ui.landed)
    }

    func testACancelAfterAChangeRequestIsStillACancel() async {
        let m = model()
        m.open()
        m.pickerSent(kind: .time, label: "20:00")
        m.cancel()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls, ["d1"])
        XCTAssertEqual(m.ui.landed, FakeCancelStore.outcome())
    }

    func testBothButtonsAreInertWhileTheCancelIsInFlightAndASecondTapSendsNothing() async {
        await store.hold(0.3)
        let m = model()
        m.open()
        m.cancel()
        XCTAssertTrue(m.ui.cancelling)
        m.cancel()
        m.keep()
        m.dismiss()
        XCTAssertTrue(m.ui.open)
        await settle(0.6)
        let calls = await store.calls
        XCTAssertEqual(calls.count, 1)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
        XCTAssertFalse(m.ui.open)
    }

    func testAFailureShowsEOnTheSameSheetNothingIsCancelledAndNothingNewFires() async {
        await store.queue(.failed(.network))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        XCTAssertTrue(m.ui.open)
        XCTAssertTrue(m.ui.failed)
        XCTAssertFalse(m.ui.cancelling)
        XCTAssertNil(m.ui.landed)
        XCTAssertEqual(names(), ["cancel_guard_shown"])
    }

    func testTheRetrySendsAgainAndASuccessClearsE() async {
        await store.queue(.failed(.server))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        m.cancel()
        await settle()
        let calls = await store.calls
        XCTAssertEqual(calls.count, 2)
        XCTAssertFalse(m.ui.failed)
        XCTAssertFalse(m.ui.open)
        XCTAssertEqual(m.ui.landed, FakeCancelStore.outcome())
    }

    func testEStaysUpWhileTheRetryIsInFlightAndDontCancelStillWorksAfterIt() async {
        await store.queue(.failed(.network))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        await store.hold(0.3)
        await store.queue(.failed(.network))
        m.cancel()
        XCTAssertTrue(m.ui.failed)
        XCTAssertTrue(m.ui.cancelling)
        await settle(0.6)
        m.keep()
        XCTAssertFalse(m.ui.open)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").first?.properties["exit"] as? String, "keep")
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 1)
    }

    func test409CheckedInClosesTheSheetAndReadsTheDateAgainSoTheLinkGoes() async {
        await store.queue(.refused(code: "checked_in"))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        XCTAssertFalse(m.ui.open)
        XCTAssertFalse(m.ui.cancelling)
        XCTAssertFalse(m.ui.failed)
        XCTAssertNil(m.ui.landed)
        XCTAssertEqual(rereads, 1)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
    }

    func test409DateNotActiveReadsTheDateAgainWhichLandsOnHAsTheCancelledOnPerson() async {
        await store.queue(.refused(code: "date_not_active"))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        XCTAssertFalse(m.ui.open)
        XCTAssertNil(m.ui.landed)
        XCTAssertEqual(rereads, 1)
    }

    func testA404ClosesAndReadsAgainAnUnknownRefusalIsE() async {
        await store.queue(.refused(code: refusedNotFound))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        XCTAssertFalse(m.ui.open)
        XCTAssertEqual(rereads, 1)

        await store.queue(.refused(code: refusedUnknown))
        m.open()
        m.cancel()
        await settle()
        XCTAssertTrue(m.ui.open)
        XCTAssertTrue(m.ui.failed)
    }

    // MARK: a date that changes under the sheet

    func testACheckInReadWhileTheSheetIsOpenClosesItWithoutAnExitEvent() {
        let m = model()
        m.open()
        m.bind(date(checkedIn: true), now: early)
        XCTAssertFalse(m.ui.open)
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").count, 0)
    }

    func testTheEndsSwitchedOffClosesIt() {
        let m = model()
        m.open()
        m.bind(date(endsEnabled: false), now: early)
        XCTAssertFalse(m.ui.open)
    }

    func testAnotherDateResetsTheSheet() {
        let m = model()
        m.open()
        m.pickerSent(kind: .time, label: "20:00")
        m.bind(date(id: "d2"), now: early)
        XCTAssertFalse(m.ui.open)
        XCTAssertNil(m.ui.sent)
    }

    // MARK: process death

    func testTheOpenSheetAndTheSentValueSurviveProcessDeathWithNoSecondGuardShown() {
        let draft = Draft()
        let first = model(draft: draft)
        first.open()
        first.pickerSent(kind: .place, label: "Bar Lenz")
        let before = names().count

        let restored = fresh(draft)
        XCTAssertTrue(restored.ui.open)
        XCTAssertEqual(restored.ui.sent, SentRequest(kind: .place, label: "Bar Lenz"))
        restored.bind(date(), now: early)
        XCTAssertTrue(restored.ui.open)
        XCTAssertEqual(restored.ui.sent, SentRequest(kind: .place, label: "Bar Lenz"))
        XCTAssertEqual(names().count, before)
        // The request was sent before: the abandon still reports it.
        restored.keep()
        XCTAssertEqual(analytics.of("cancel_abandoned_at_guard").first?.properties["change_requested"] as? Bool, true)
    }

    func testARestoredSheetForADateThatIsNowCheckedInOrAnotherDateCloses() {
        let draft = Draft()
        model(draft: draft).open()
        let checked = fresh(draft)
        checked.bind(date(checkedIn: true), now: early)
        XCTAssertFalse(checked.ui.open)

        let draftAgain = Draft()
        model(draft: draftAgain).open()
        let other = fresh(draftAgain)
        other.bind(date(id: "d9"), now: early)
        XCTAssertFalse(other.ui.open)
    }

    func testAClosedSheetLeavesNothingToRestore() {
        let draft = Draft()
        let m = model(draft: draft)
        m.open()
        m.keep()
        XCTAssertEqual(draft.value, "")
        XCTAssertFalse(fresh(draft).ui.open)
    }

    func testAClockTickWritesNothingToTheDraft() {
        var writes = 0
        let m = CancelModel(store: store, analytics: analytics)
        m.attach(stored: "") { _ in writes += 1 }
        m.bind(date(), now: early)
        m.open()
        let before = writes
        m.bind(date(), now: early.addingTimeInterval(1))
        m.bind(date(), now: early.addingTimeInterval(2))
        XCTAssertEqual(writes, before)
    }

    func testTheDraftOfAnotherBuildIsNoDraft() {
        XCTAssertNil(CancelDraft.decode("not json"))
        XCTAssertNil(CancelDraft.decode(""))
        let draft = CancelDraft(sentKind: "time", sentLabel: "20:00", changeRequested: true, dateId: "d1")
        XCTAssertEqual(CancelDraft.decode(draft.encoded), draft)
    }

    // MARK: what is never sent

    func testNoRetiredEventAndNoServerEventIsEverFiredFromHere() async {
        await store.queue(.failed(.network))
        let m = model()
        m.open()
        m.cancel()
        await settle()
        m.cancel()
        await settle()
        let banned: Set<String> = ["date_cancelled", "cancel_link_tapped", "partner_cancellation_received", "rematch_started",
                                   "date_ended", "date_ended_in_chat"]
        XCTAssertTrue(names().allSatisfy { !banned.contains($0) })
        for entry in analytics.entries {
            XCTAssertTrue(Set(entry.properties.keys).isDisjoint(with: ["initiator", "reason", "match_id", "user_id"]))
        }
    }

    /// The dedupe compares strings, so one draft must encode to one string however many times it is encoded.
    func testADraftEncodesToOneStringEveryTime() {
        let draft = CancelDraft(sentKind: "time", sentLabel: "Fri 19:00", changeRequested: true, dateId: "d1")
        XCTAssertEqual(Set((0..<50).map { _ in draft.encoded }).count, 1)
    }
}
