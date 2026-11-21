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
        XCTAssertEqual(live, ChangeSamples.letters.map { "196\($0)=\($0)" })
        XCTAssertNil(devLiveFrame(DevState(id: "195A", ticket: "SHOWUP-195", label: "A")))
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
}
#endif
