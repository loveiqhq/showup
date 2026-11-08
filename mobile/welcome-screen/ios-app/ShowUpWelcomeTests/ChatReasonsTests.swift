// ChatReasonsTests.swift
// ShowUp · the chat's reason vocabulary (Date 02, SHOWUP-194): the ticket's chip table, built once,
// read by Date 01 too. Android's `ChatReasonsTest.kt`.

import XCTest
@testable import ShowUpWelcome

final class ChatReasonsTests: XCTestCase {

    func testFiveChipsAreShownInTheGridOrderUntilDate03AndDate05Ship() {
        XCTAssertEqual(shownReasons, [.onMyWay, .cantFind, .whenArrive, .runningLate, .cantMakeIt])
    }

    func testEveryChipIsTheRegistrysWireValueWithTheTicketsLabelAndDraft() {
        let table = ChatReason.allCases.map { [$0.rawValue, $0.label, $0.draft ?? "-"] }
        XCTAssertEqual(table, [
            ["on_my_way", "I'm on my way", "On my way now — see you in a few!"],
            ["arrived", "I arrived at the spot", "I'm here at the meeting spot — see you soon!"],
            ["cant_find", "I can't spot you", "I'm here but can't spot you — where are you sitting?"],
            ["when_arrive", "When will you arrive?", "When do you think you'll get here?"],
            ["change_time", "Change meet time", "-"],
            ["change_place", "Change location", "-"],
            ["running_late", "I'm running late", "Running about 10 min late — sorry! Almost there."],
            ["cant_make_it", "I can't make it today", "-"],
        ])
    }

    func testTheTonesAreTheReferences() {
        XCTAssertEqual(ChatReason.changeTime.tone, .violet)
        XCTAssertEqual(ChatReason.changePlace.tone, .violet)
        XCTAssertEqual(ChatReason.cantMakeIt.tone, .darkRed)
        XCTAssertEqual(ChatReason.runningLate.tone, .orange)
    }

    func testAWireValueReadsBackToItsChipAndAnOldUnknownOrMissingOneToNone() {
        XCTAssertEqual(ChatReason.fromWire("cant_make_it"), .cantMakeIt)
        XCTAssertEqual(ChatReason.fromWire("when_arrive"), .whenArrive)
        XCTAssertNil(ChatReason.fromWire("cant_make_it_today"))
        XCTAssertNil(ChatReason.fromWire("something_new"))
        XCTAssertNil(ChatReason.fromWire(nil))
    }
}
