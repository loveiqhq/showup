//  DevDateDemoTests.swift
//  ShowUp · the in-memory date server behind Date 05's live States frames (debug only)
//
//  Android twin: `DevDateDemoTest.kt`. What the States page promises: each frame opens in the state its picture
//  shows, at today's times, and every tap then does what the real server would.

#if DEBUG
import XCTest
@testable import ShowUpWelcome

final class DevDateDemoTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_795_251_600) // 2026-11-21T09:00:00Z

    private func demo(_ frame: String, latencyMs: Int = 0) -> DevDateDemo {
        let fixed = now
        return DevDateDemo(frame: frame, clock: { fixed }, latencyMs: latencyMs)
    }

    private func read(_ demo: DevDateDemo) async throws -> ActiveDate {
        guard case .success(.active(let date)) = await demo.active(zone: "Europe/Berlin") else {
            XCTFail("no active date")
            throw CancellationError()
        }
        return date
    }

    private func time(_ at: Date, clientId: String = "c1") -> SuggestRequest {
        SuggestRequest(kind: .time, time: at, placeId: nil, entry: .chatChip, fromSearch: nil, clientId: clientId,
                       tz: "Europe/Berlin")
    }

    // ── where each frame opens ──────────────────────────────────────────────────────────────

    func testEveryFrameLandsWhereItsPictureIsDrawn() {
        XCTAssertEqual(demoLanding("A"), .timePicker)
        XCTAssertEqual(demoLanding("K"), .timePicker)
        for f in ["B", "M", "N"] { XCTAssertEqual(demoLanding(f), .placePicker, f) }
        for f in ["C", "F", "H"] { XCTAssertEqual(demoLanding(f), .chat, f) }
        for f in ["D", "E", "G", "I", "J", "L"] { XCTAssertEqual(demoLanding(f), .dateScreen, f) }
    }

    func testTheStatesPageOpensEveryDate05FrameLiveAndEveryOtherAsAPicture() {
        let live = devStates.compactMap { state in devLiveFrame(state).map { "\(state.id)=\($0)" } }
        // 196's frames are named by their letter, 197's and 198's keep the ticket number (the letters would collide).
        XCTAssertEqual(live, ChangeSamples.letters.map { "196\($0)=\($0)" }
            + DateEndSamples.letters.map { "197\($0)=197\($0)" }
            + CancelSamples.letters.map { "198\($0)=198\($0)" })
        XCTAssertNil(devLiveFrame(DevState(id: "195A", ticket: "SHOWUP-195", label: "A")))
        XCTAssertNil(devLiveFrame(DevState(id: "198G", ticket: "SHOWUP-198", label: "G")))
        XCTAssertNil(devLiveFrame(DevState(id: "196Z", ticket: "SHOWUP-196", label: "Z")))
    }

    // ── each frame's state, at today's times ────────────────────────────────────────────────

    func testTheSheetsClockIsNowWithTheChatOpenOrLocked() async throws {
        let open = try await read(demo("A"))
        XCTAssertEqual(open.meetAt.timeIntervalSince(now), 78 * 60)
        XCTAssertTrue(open.coordinateOpen)
        XCTAssertEqual(open.change?.allowed, true)
        XCTAssertEqual(open.change?.slots.count, 10)

        let locked = try await read(demo("I"))
        XCTAssertEqual(locked.meetAt.timeIntervalSince(now), 185 * 60)
        XCTAssertFalse(locked.coordinateOpen)
    }

    func testDHoldsMineAndERequestsLeoniesTime() async throws {
        let d = try await read(demo("D"))
        XCTAssertEqual(d.change?.pending.map(\.kind), [.time, .place])
        XCTAssertNil(d.changeRequest)
        let e = try await read(demo("E"))
        XCTAssertEqual(e.changeRequest?.byYou, false)
    }

    func testGIsAgreedWithTheChatOpenAndHIsExpired() async throws {
        let g = try await read(demo("G"))
        XCTAssertEqual(g.change?.timeWas?.addingTimeInterval(30 * 60), g.meetAt)
        XCTAssertTrue(g.coordinateOpen)

        let h = demo("H")
        let hRead = try await read(h)
        XCTAssertEqual(hRead.change?.allowed, false)
        guard case .success(let cards) = await h.suggestions(dateId: "x") else { return XCTFail("no cards") }
        XCTAssertEqual(cards.map(\.status), [.expired])
    }

    func testEAndLShowLeoniesUnreadMessageAndJsLockedChatHasNone() async throws {
        for f in ["E", "L"] {
            let unread = try await read(demo(f)).unread
            XCTAssertEqual(unread?.count, 1, f)
            XCTAssertEqual(unread?.body, DateSamples.unread.body, f)
        }
        let j = try await read(demo("J"))
        XCTAssertNil(j.unread)
    }

    func testNOpensOnItsSearchAndATestDateStartsOver() async throws {
        XCTAssertEqual(demoQuery("N"), "sushi")
        XCTAssertNil(demoQuery("B"))
        let demo = demo("E")
        _ = await demo.createTestDate(minutes: 200)
        let fresh = try await read(demo)
        XCTAssertFalse(fresh.coordinateOpen)
        XCTAssertNil(fresh.changeRequest)
    }

    // ── suggesting and agreeing ─────────────────────────────────────────────────────────────

    func testASuggestedSlotWaitsAndASecondReplacesIt() async throws {
        let demo = demo("A")
        let slots = try await read(demo).change?.slots ?? []
        guard case .sent(let first) = await demo.suggest(dateId: "x", request: time(slots[1])) else { return XCTFail() }
        guard case .sent(let again) = await demo.suggest(dateId: "x", request: time(slots[1])) else { return XCTFail() }
        XCTAssertEqual(first, again)
        _ = await demo.suggest(dateId: "x", request: time(slots[3], clientId: "c9"))
        guard case .success(let all) = await demo.suggestions(dateId: "x") else { return XCTFail() }
        XCTAssertEqual(all.map(\.status), [.replaced, .pending])
    }

    func testAnyListedPlaceCanBeSuggested() async throws {
        let demo = demo("B")
        let request = SuggestRequest(kind: .place, time: nil, placeId: "p-nove", entry: .chatChip, fromSearch: false,
                                     clientId: "c2", tz: "Europe/Berlin")
        guard case .sent(let sent) = await demo.suggest(dateId: "x", request: request) else { return XCTFail() }
        XCTAssertEqual(sent.place?.name, "Osteria Nove")
    }

    func testKFailsOnceAndTheRetrySends() async throws {
        let demo = demo("K")
        let at = try await read(demo).change?.slots[1] ?? now
        guard case .failed = await demo.suggest(dateId: "x", request: time(at)) else { return XCTFail("K did not fail") }
        guard case .sent = await demo.suggest(dateId: "x", request: time(at)) else { return XCTFail("retry did not send") }
    }

    func testMFailsItsFirstPlacesReadAndNFindsNoSushi() async {
        let m = demo("M")
        guard case .failure = await m.places(dateId: "x", query: nil) else { return XCTFail("M did not fail") }
        guard case .success(let list) = await m.places(dateId: "x", query: nil) else { return XCTFail() }
        XCTAssertEqual(list.places.count, 5)
        guard case .success(let none) = await demo("N").places(dateId: "x", query: "sushi") else { return XCTFail() }
        XCTAssertTrue(none.places.isEmpty)
    }

    func testIAgreeMakesTheTimeBindingAndTheChatStaysOpen() async throws {
        let demo = demo("E")
        let before = try await read(demo)
        guard let request = before.changeRequest else { return XCTFail("no request") }
        guard case .agreed = await demo.agree(dateId: "x", suggestionId: request.id, surface: .coordinateCard) else {
            return XCTFail()
        }
        let after = try await read(demo)
        XCTAssertEqual(after.meetAt, request.time)
        XCTAssertEqual(after.change?.timeWas, before.meetAt)
        XCTAssertNil(after.changeRequest)
        XCTAssertTrue(after.coordinateOpen)
    }

    func testLFailsItsFirstAgreeAndAgreesOnTheRetry() async throws {
        let demo = demo("L")
        guard let id = try await read(demo).changeRequest?.id else { return XCTFail() }
        guard case .failed = await demo.agree(dateId: "x", suggestionId: id, surface: .coordinateCard) else { return XCTFail() }
        guard case .agreed = await demo.agree(dateId: "x", suggestionId: id, surface: .coordinateCard) else { return XCTFail() }
    }

    // ── checking in ─────────────────────────────────────────────────────────────────────────

    func testACommittedCheckInOpensTheChatAndClosesNewSuggestions() async throws {
        let demo = demo("I")
        guard let fix = await demo.fix() else { return XCTFail("no fix") }
        _ = await demo.checkIn(dateId: "x", fix: fix, entry: .dateScreen, clientId: "k1")
        _ = await demo.commit(dateId: "x")
        let state = try await read(demo)
        XCTAssertTrue(state.checkedIn)
        XCTAssertTrue(state.coordinateOpen)
        XCTAssertEqual(state.change?.allowed, false)
    }

    // ── end and cancel (Date 06, 07): the server's refusals, then the outcome the next read reports ──

    private func checkedIn(_ demo: DevDateDemo) async {
        guard let fix = await demo.fix() else { return XCTFail("no fix") }
        _ = await demo.checkIn(dateId: "x", fix: fix, entry: .dateScreen, clientId: "k1")
        _ = await demo.commit(dateId: "x")
    }

    func testAnActiveDateReadsWithTheEndsSwitchOnAndThePenalty() async throws {
        let date = try await read(demo("B"))
        XCTAssertTrue(date.endsEnabled)
        XCTAssertGreaterThan(date.cancelPenaltyPoints, 0)
    }

    func testAnEndIsRefusedBeforeTheMeetTimeAndWithoutACommittedCheckIn() async {
        let demo = demo("B")
        let early = await demo.end(dateId: "x", reason: .partnerNoShow, entry: .endDateBar)
        XCTAssertEqual(early, .refused(code: "before_meet"))
        let unchecked = await demo.end(dateId: "x", reason: .uncomfortable, entry: .report)
        XCTAssertEqual(unchecked, .refused(code: "not_checked_in"))
    }

    func testMetNeedsNoCheckInAndTheNextReadReportsTheOutcomeUntilItIsSeen() async {
        let demo = demo("B")
        let result = await demo.end(dateId: "x", reason: .met, entry: .endDateBar)
        guard case .ended(let outcome) = result else { return XCTFail("expected an end") }
        XCTAssertEqual(outcome.reason, .met)
        guard case .success(.ended(let read)) = await demo.active(zone: "UTC") else { return XCTFail("expected ended") }
        XCTAssertEqual(read.reason, .met)
        let again = await demo.end(dateId: "x", reason: .met, entry: .endDateBar)
        XCTAssertEqual(again, .refused(code: "date_not_active"))
        await demo.markEndSeen(dateId: "x")
        guard case .success(.none) = await demo.active(zone: "UTC") else { return XCTFail("expected none") }
    }

    func testUncomfortableAndANoShowEndOnceCheckedInANoShowOnlyFromTheMeetTime() async {
        let demo = demo("B")
        _ = await demo.createTestDate(minutes: 0)
        await checkedIn(demo)
        let noShow = await demo.end(dateId: "x", reason: .partnerNoShow, entry: .checkinBarLink)
        guard case .ended(let ended) = noShow else { return XCTFail("expected an end") }
        XCTAssertEqual(ended.reason, .partnerNoShow)

        let other = self.demo("B")
        await checkedIn(other)
        let early = await other.end(dateId: "x", reason: .partnerNoShow, entry: .checkinBarLink)
        XCTAssertEqual(early, .refused(code: "before_meet"))
        let uncomfortable = await other.end(dateId: "x", reason: .uncomfortable, entry: .report)
        guard case .ended(let outcome) = uncomfortable else { return XCTFail("expected an end") }
        XCTAssertEqual(outcome.reason, .uncomfortable)
    }

    func testStayForNowSucceedsAndLeavesTheDateActive() async throws {
        let demo = demo("B")
        let landed = await demo.flagDiscomfort(dateId: "x")
        XCTAssertTrue(landed)
        _ = try await read(demo)
    }

    func testACancelEndsTheDateAsCancelledAndIsRefusedOnceCheckedInOrOnceOver() async {
        let demo = demo("B")
        let result = await demo.cancel(dateId: "x")
        guard case .cancelled(let outcome) = result else { return XCTFail("expected a cancel") }
        XCTAssertEqual(outcome.reason, .cancelled)
        let again = await demo.cancel(dateId: "x")
        XCTAssertEqual(again, .refused(code: "date_not_active"))
        guard case .success(.ended(let read)) = await demo.active(zone: "UTC") else { return XCTFail("expected ended") }
        XCTAssertEqual(read.reason, .cancelled)

        let checked = self.demo("B")
        await checkedIn(checked)
        let refused = await checked.cancel(dateId: "x")
        XCTAssertEqual(refused, .refused(code: "checked_in"))
    }

    func testTheDevMenusEndTheDateMakesAnyLaterEndOrCancelDateNotActive() async {
        let demo = demo("B")
        _ = await demo.endTestDate()
        let end = await demo.end(dateId: "x", reason: .met, entry: .endDateBar)
        XCTAssertEqual(end, .refused(code: "date_not_active"))
        let cancel = await demo.cancel(dateId: "x")
        XCTAssertEqual(cancel, .refused(code: "date_not_active"))
    }

    // ── SHOWUP-197 and 198: each frame's seed, landing and once-failing call ────────────────

    func testEvery197And198FrameCanBeLaunchedAndOnlyThey() {
        for id in DateEndSamples.letters.map({ "197" + $0 }) + CancelSamples.letters.map({ "198" + $0 }) {
            XCTAssertTrue(devLiveFrames.contains(id), id)
        }
        XCTAssertFalse(devLiveFrames.contains("198G"))
        XCTAssertFalse(devLiveFrames.contains("197K"))
    }

    func testTheSheetFramesOpenTheirSheetAtTheirStepTheRestLandOnTheDateScreen() {
        XCTAssertEqual(demoSteps("197B"), [.openWrap])
        XCTAssertEqual(demoSteps("197C"), [.openWrap, .pickMet])
        for f in ["197D", "197E", "197F"] { XCTAssertEqual(demoSteps(f), [.openWrap, .pickNoShow], f) }
        for f in ["197G", "197J"] { XCTAssertEqual(demoSteps(f), [.openWrap, .pickNoShow, .confirmWait], f) }
        XCTAssertEqual(demoSteps("197H"), [.openReport])
        XCTAssertEqual(demoSteps("197I"), [.openReport, .pickUncomfortable])
        for f in ["198B", "198C", "198E", "198I"] { XCTAssertEqual(demoSteps(f), [.openCancel], f) }
        XCTAssertEqual(demoSteps("198D"), [.openCancel, .cancelSent])
        for f in ["197A", "198A", "198F", "198H"] { XCTAssertEqual(demoSteps(f), [], f) }
        for f in devLiveFrames where f.hasPrefix("19") { XCTAssertEqual(demoLanding(f), .dateScreen, f) }
    }

    func test197FramesAreCheckedInExceptDAndFGAndJAreTenMinutesPastTheMeetTime() async throws {
        for f in DateEndSamples.letters.map({ "197" + $0 }) {
            let d = try await read(demo(f))
            XCTAssertEqual(d.checkedIn, f != "197D", f)
        }
        for f in ["197A", "197E"] {
            let d = try await read(demo(f))
            XCTAssertEqual(d.meetAt.timeIntervalSince(now), 78 * 60, f)
        }
        for f in ["197F", "197G", "197J"] {
            let d = try await read(demo(f))
            XCTAssertEqual(d.meetAt.timeIntervalSince(now), -10 * 60, f)
            XCTAssertTrue(d.coordinateOpen, f)
        }
    }

    func testJFailsItsFirstEndAndThenEndsTheDateEFailsItsFirstCancelAndThenCancels() async {
        let j = demo("197J")
        let first = await j.end(dateId: "x", reason: .partnerNoShow, entry: .checkinBarLink)
        XCTAssertEqual(first, .failed(.network))
        let second = await j.end(dateId: "x", reason: .partnerNoShow, entry: .checkinBarLink)
        guard case .ended(let outcome) = second else { return XCTFail("expected an end") }
        XCTAssertEqual(outcome.reason, .partnerNoShow)

        let e = demo("198E")
        let failed = await e.cancel(dateId: "x")
        XCTAssertEqual(failed, .failed(.network))
        let cancelled = await e.cancel(dateId: "x")
        guard case .cancelled(let out) = cancelled else { return XCTFail("expected a cancel") }
        XCTAssertEqual(out.reason, .cancelled)
    }

    func testTheEndFollowsTheServersRulesBeforeMeetAndNotCheckedInRefuseWeMetNeedsNoCheckIn() async {
        let e = demo("197E")
        let early = await e.end(dateId: "x", reason: .partnerNoShow, entry: .endDateBar)
        XCTAssertEqual(early, .refused(code: "before_meet"))
        let uncomfortable = await e.end(dateId: "x", reason: .uncomfortable, entry: .report)
        guard case .ended = uncomfortable else { return XCTFail("expected an end") }

        let d = demo("197D")
        let gated = await d.end(dateId: "x", reason: .uncomfortable, entry: .report)
        XCTAssertEqual(gated, .refused(code: "not_checked_in"))
        let met = await d.end(dateId: "x", reason: .met, entry: .endDateBar)
        guard case .ended = met else { return XCTFail("expected an end") }
    }

    func test198FramesOpenWithTheChatLockedFarOutOpenNearAndTheChangeWindowClosedAtI() async throws {
        let b = try await read(demo("198B"))
        XCTAssertFalse(b.coordinateOpen)
        XCTAssertGreaterThanOrEqual(b.meetAt.timeIntervalSince(now), 91 * 60)
        XCTAssertFalse(b.checkedIn)

        let c = try await read(demo("198C"))
        XCTAssertTrue(c.coordinateOpen)
        XCTAssertLessThanOrEqual(c.meetAt.timeIntervalSince(now), 90 * 60)

        let i = try await read(demo("198I"))
        XCTAssertTrue(i.coordinateOpen)
        XCTAssertEqual(i.change?.allowed, false)
        XCTAssertEqual(i.meetAt.timeIntervalSince(now), 25 * 60)

        let f = try await read(demo("198F"))
        XCTAssertTrue(f.checkedIn)
        let a = try await read(demo("198A"))
        XCTAssertFalse(a.checkedIn)
    }

    func testDHoldsMyTimeRequestOnTheServerAndACheckedInCancelIsRefused() async throws {
        let d = try await read(demo("198D"))
        XCTAssertEqual(d.change?.pending.filter(\.byYou).map(\.kind), [.time])
        let f = demo("198F")
        let refused = await f.cancel(dateId: "x")
        XCTAssertEqual(refused, .refused(code: "checked_in"))
    }

    func test198HOpensOnTheMatchHavingCancelledOnThisPerson() async {
        guard case .success(.ended(let outcome)) = await demo("198H").active(zone: "Europe/Berlin") else {
            return XCTFail("expected an ended read")
        }
        XCTAssertEqual(outcome.reason, .cancelled)
        XCTAssertFalse(outcome.endedByYou)
    }

    func testLeonieCancellingEndsTheDateOnHerSideLeonieEndingItReportsHerReasonAndASecondEndFindsItOver() async {
        let c = demo("198C")
        let first = await c.partnerCancels()
        XCTAssertTrue(first)
        guard case .success(.ended(let out)) = await c.active(zone: "Europe/Berlin") else { return XCTFail("expected ended") }
        XCTAssertEqual(out.reason, .cancelled)
        XCTAssertFalse(out.endedByYou)
        let again = await c.partnerCancels()
        XCTAssertFalse(again)

        let expected: [(EndReason, OutcomeReason)] = [(.met, .met), (.partnerNoShow, .partnerNoShow), (.uncomfortable, .uncomfortable)]
        for (reason, outcome) in expected {
            let d = demo("197A")
            let ended = await d.partnerEnds(reason: reason)
            XCTAssertTrue(ended)
            guard case .success(.ended(let o)) = await d.active(zone: "Europe/Berlin") else { return XCTFail("expected ended") }
            XCTAssertEqual(o.reason, outcome)
            XCTAssertFalse(o.endedByYou)
            let second = await d.partnerEnds(reason: reason)
            XCTAssertFalse(second)
        }
    }

    func testATestDateMadeAfterLeonieEndedTheFirstOneStartsOver() async throws {
        let d = demo("198A")
        _ = await d.partnerEnds(reason: .partnerNoShow)
        _ = await d.createTestDate(minutes: 60)
        _ = try await read(d)
    }
}
#endif
