// ArrivalRoutingTests.swift
// ShowUp · what the host decides around the arrival gate (SHOWUP-195). Android's `ArrivalRoutingTest.kt`, case for
// case; the last case is the iOS form of its "a stop that is only a rebuild" one (iOS has no rebuild, but a
// Control Center pull or a call banner is `.inactive`, and that is not leaving).

import SwiftUI
import XCTest
@testable import ShowUpWelcome

@MainActor
final class ArrivalRoutingTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private let analytics = RecordingAnalytics()

    private var now: Date { meet.addingTimeInterval(-5 * 60) }

    private func date(checkedIn: Bool = false) -> ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: meet.addingTimeInterval(-90 * 60), coordinateOpen: true,
                   place: DatePlace(name: "Cafe Krone", address: nil),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: nil, distanceKm: 1, photoUrl: nil,
                                    attendedCount: 4),
                   motivationYou: nil, motivationThem: nil, sharedVibes: [], sharedInterests: [], unread: nil,
                   checkedIn: checkedIn, chatTutorialSeen: true, arrival: nil)
    }

    private func gate(store: FakeArrivalStore = FakeArrivalStore(), checkedIn: Bool = false) -> ArrivalGateModel {
        let at = now
        let model = ArrivalGateModel(store: store, reader: FakeFixReader(), analytics: analytics, clock: { at },
                                     newId: { "id" })
        model.bind(date(checkedIn: checkedIn))
        return model
    }

    private func settle(_ seconds: Double = 0.15) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    func testOnlyTheArrivedChipBeforeCheckInIsGated() {
        XCTAssertTrue(arrivalChipGated(.arrived, gate()))
        XCTAssertFalse(arrivalChipGated(.arrived, gate(checkedIn: true)))
        XCTAssertFalse(arrivalChipGated(.runningLate, gate()))
        XCTAssertFalse(arrivalChipGated(.cantMakeIt, gate()))
    }

    func testTheChipEventComesBeforeTheConfirmEventAndNothingIsDrafted() async {
        let dates = FakeDateStore()
        await dates.setRead(.success(.active(date())))
        let at = now
        let chat = ChatModel(store: FakeChatStore(), dates: dates, analytics: analytics, clock: { at },
                             zone: { TimeZone(identifier: "Europe/Berlin")! }, pollEvery: 0.05)
        chat.open(date(), entry: .openChat)
        await settle()
        let before = analytics.names().count
        let g = gate()

        onChatChip(.arrived, .entry, g, pick: { chat.pick($0, surface: $1, gated: $2) })
        chat.onScreen(false)

        XCTAssertEqual(Array(analytics.names().dropFirst(before)), ["action_chip_used", "arrival_confirm_shown"])
        XCTAssertEqual(chat.ui.draft, "")
        XCTAssertNil(chat.ui.selected)
        XCTAssertEqual(g.ui.phase, .confirm(entry: .chatChip, locError: false, busy: false, note: nil))
    }

    func testAnUngatedChipOnlyPicksAndOpensNoSheet() {
        let g = gate()
        var picked: [Bool] = []
        onChatChip(.runningLate, .dock, g, pick: { _, _, gated in picked.append(gated) })
        XCTAssertEqual(picked, [false])
        XCTAssertEqual(g.ui.phase, .idle)
    }

    func testAnUndoLandsOnTheDateScreenFromTheChatAndStaysPutElsewhere() {
        XCTAssertEqual(undoLanding(undone: true, chatOnTop: true), .leaveChat)
        XCTAssertEqual(undoLanding(undone: true, chatOnTop: false), .consumeOnly)
        XCTAssertEqual(undoLanding(undone: false, chatOnTop: true), .none)
        XCTAssertEqual(undoLanding(undone: false, chatOnTop: false), .none)
    }

    func testRouteUndoFromTheChatLeavesItThenConsumesOnceOnTheDateScreenItOnlyConsumes() {
        var calls: [String] = []
        routeUndo(undone: true, chatOnTop: true, leaveChat: { calls.append("leave") }, consume: { calls.append("consume") })
        XCTAssertEqual(calls, ["leave", "consume"])
        calls.removeAll()
        routeUndo(undone: true, chatOnTop: false, leaveChat: { calls.append("leave") }, consume: { calls.append("consume") })
        XCTAssertEqual(calls, ["consume"])
        calls.removeAll()
        routeUndo(undone: false, chatOnTop: true, leaveChat: { calls.append("leave") }, consume: { calls.append("consume") })
        XCTAssertEqual(calls, [])
    }

    func testOnlyTheBackgroundCommitsTheDoneSheetAndInactiveOrActiveDoNot() async {
        let store = FakeArrivalStore()
        let g = gate(store: store)
        g.open(.dateScreen)
        g.yes()
        await settle()
        XCTAssertEqual(g.ui.phase, .done(entry: .dateScreen))

        // Control Center, a call banner, Face ID: the person did not leave.
        onScenePhase(.inactive, gate: g)
        onScenePhase(.active, gate: g)
        await settle()
        XCTAssertEqual(g.ui.phase, .done(entry: .dateScreen))
        let early = await store.commitCalls
        XCTAssertEqual(early, 0)

        onScenePhase(.background, gate: g)
        await settle()
        XCTAssertEqual(g.ui.phase, .idle)
        let after = await store.commitCalls
        XCTAssertEqual(after, 1)
    }
}
