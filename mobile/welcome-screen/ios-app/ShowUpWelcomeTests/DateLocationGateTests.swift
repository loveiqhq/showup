// DateLocationGateTests.swift
// ShowUp · G, the location gate over the Date screen (SHOWUP-195): the countdown pill and the copy. Android's
// `DateLocationGateTest.kt`, the pure cases; the drawn card is for the fit sweep and a device.

import XCTest
@testable import ShowUpWelcome

final class DateLocationGateTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_794_333_600) // 2026-11-10T18:00:00Z

    func testGatePillCountsDownTodayCountsUpAfterAndIsSilentTomorrow() {
        XCTAssertEqual(gatePill(meetDay: "today", meetAt: meet, now: meet.addingTimeInterval(-78 * 60)),
                       .starts(hours: 1, minutes: 18))
        XCTAssertEqual(gatePill(meetDay: "today", meetAt: meet, now: meet.addingTimeInterval(10 * 60)), .started(minutes: 10))
        XCTAssertNil(gatePill(meetDay: "tomorrow", meetAt: meet, now: meet.addingTimeInterval(-20 * 3600)))
    }

    func testTheCountdownRoundsUpSoTheLastMinuteIs0h1mAndNever0h0m() {
        func pill(_ offset: TimeInterval) -> GatePill? { gatePill(meetDay: "today", meetAt: meet, now: meet.addingTimeInterval(offset)) }
        XCTAssertEqual(pill(-59), .starts(hours: 0, minutes: 1))
        XCTAssertEqual(pill(-1), .starts(hours: 0, minutes: 1))
        XCTAssertEqual(pill(-(77 * 60 + 30)), .starts(hours: 1, minutes: 18))
        XCTAssertEqual(pill(-78 * 60), .starts(hours: 1, minutes: 18))
        XCTAssertEqual(pill(-3600), .starts(hours: 1, minutes: 0))
        XCTAssertEqual(pill(0), .started(minutes: 0))
        XCTAssertEqual(pill(59), .started(minutes: 0))
        XCTAssertEqual(pill(60), .started(minutes: 1))
    }

    func testTheDateCopyIsTheKitsWithCurlyApostrophes() {
        XCTAssertEqual(LocationCopy.Date.startsIn(1, 18), "Starts in 1h 18m")
        XCTAssertEqual(LocationCopy.Date.started(10), "Started 10 min ago")
        XCTAssertEqual(LocationCopy.Date.mayBeWaiting("Leonie"), "Leonie may be waiting")
        XCTAssertEqual(LocationCopy.Date.aLead + LocationCopy.Date.em + LocationCopy.Date.tail,
                       "Urgent! Allow location to get access to your date information.")
        XCTAssertEqual(LocationCopy.Date.bLead + LocationCopy.Date.em + LocationCopy.Date.tail,
                       "Urgent! Turn on location to get access to your date information.")
        XCTAssertEqual(
            LocationCopy.Date.bBody("Leonie"),
            "Location is off for Show Up, so your date screen is locked. Turn it on to see your date, get directions "
                + "and check in when you arrive, so Leonie knows you\u{2019}re there.")
        XCTAssertTrue(LocationCopy.Date.aBody("Leonie").hasSuffix("so Leonie knows you\u{2019}re there."))
        XCTAssertTrue(LocationCopy.Date.cBody("Leonie").hasPrefix("Location is off for every app on this phone"))
    }
}
