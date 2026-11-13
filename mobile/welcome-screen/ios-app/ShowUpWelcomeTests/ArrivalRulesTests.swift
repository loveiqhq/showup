// ArrivalRulesTests.swift
// ShowUp · the arrival check-in's plain rules (SHOWUP-195). Android's `ArrivalRulesTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

final class ArrivalRulesTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z

    func testMinutesVsStartIsWholeMinutesFromTheMeetTimeNegativeBeforeTruncatedTowardZero() {
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(-30 * 60)), -30)
        XCTAssertEqual(minutesVsStart(meet, meet), 0)
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(7 * 60 + 59)), 7)
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(-59)), 0)
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(-60)), -1)
    }

    func testTheRetryScheduleDoublesFrom2SecondsAndHoldsAt30() {
        XCTAssertEqual((0...5).map(retryDelayMs), [2_000, 4_000, 8_000, 16_000, 30_000, 30_000])
        XCTAssertEqual(retryDelayMs(100), 30_000)
    }

    func testTheLocationReadIsBoundedAt10Seconds() {
        XCTAssertEqual(locationFixTimeoutMs, 10_000)
    }

    func testAnArrivalOrAnUnreadMessageHoistsACard() {
        let arrival = DateArrival(checkedInAt: meet)
        XCTAssertTrue(hoisted(.locked, arrival))
        XCTAssertFalse(hoisted(.available, nil))
        XCTAssertTrue(hoisted(.unread, nil))
        XCTAssertFalse(hoisted(.locked, nil))
    }

    func testSixChipsAreShownArrivedAmongThem() {
        XCTAssertEqual(shownReasons, [.onMyWay, .arrived, .cantFind, .whenArrive, .runningLate, .cantMakeIt])
    }

    func testTheEntryWireValuesAreTheRegistrys() {
        XCTAssertEqual(ArrivalEntry.allCases.map(\.wire), ["date_screen", "chat_chip", "report"])
    }
}
