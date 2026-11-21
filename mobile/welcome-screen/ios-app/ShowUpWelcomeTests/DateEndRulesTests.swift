// DateEndRulesTests.swift
// ShowUp · Date 06's plain rules (SHOWUP-197, R9, R11). Android's `DateEndRulesTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

final class DateEndRulesTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-21T18:00:00Z

    func testPastMeetIsFalseOneSecondBeforeTheAgreedTimeAndTrueAtIt() {
        XCTAssertFalse(pastMeet(meet, meet.addingTimeInterval(-1)))
        XCTAssertTrue(pastMeet(meet, meet))
        XCTAssertTrue(pastMeet(meet, meet.addingTimeInterval(1)))
    }

    func testTheUiReadsTheSameAnswerAsTheFreeFunction() {
        var date = DateSamples.date
        date.meetAt = meet
        for offset in [-1.0, 0, 1] {
            let now = meet.addingTimeInterval(offset)
            let ui = DateEndUi(date: date, now: now)
            XCTAssertEqual(ui.pastMeet, pastMeet(meet, now))
        }
        XCTAssertFalse(DateEndUi().pastMeet)
    }

    func testMinutesVsStartIsNegativeBeforeTheMeetTimeAndPositiveAfter() {
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(-600)), -10)
        XCTAssertEqual(minutesVsStart(meet, meet), 0)
        XCTAssertEqual(minutesVsStart(meet, meet.addingTimeInterval(12 * 60 + 5)), 12)
    }

    func testTheGateLeadsNoShowToTheGuideAndUncomfortableToItsStepAndWeMetToC() {
        XCTAssertEqual(endStepAfterGate(.noShow), .guide)
        XCTAssertEqual(endStepAfterGate(.uncomfortable), .uncomfortable)
        XCTAssertEqual(endStepAfterGate(.met), .met)
    }

    func testOnlyTheTwoProblemRoutesNeedACheckIn() {
        XCTAssertFalse(optionNeedsCheckIn(.met))
        XCTAssertTrue(optionNeedsCheckIn(.noShow))
        XCTAssertTrue(optionNeedsCheckIn(.uncomfortable))
    }

    func testEachEndingStepEndsTheDateWithItsReasonTheMenusAndTheGuideEndNothing() {
        XCTAssertEqual(endReasonFor(.met), .met)
        XCTAssertEqual(endReasonFor(.noShow), .partnerNoShow)
        XCTAssertEqual(endReasonFor(.uncomfortable), .uncomfortable)
        XCTAssertNil(endReasonFor(.menu))
        XCTAssertNil(endReasonFor(.guide))
    }

    func testTheGuidesEndButtonIsDisabledAndNamesTheTimeBeforeTheMeetTimeLiveAfterIt() {
        let before = guideEnd(pastMeet: false, meetTime: "19:00")
        XCTAssertFalse(before.enabled)
        XCTAssertEqual(before.label, "You can end the date from 19:00")
        XCTAssertTrue(before.clock)
        let after = guideEnd(pastMeet: true, meetTime: "19:00")
        XCTAssertTrue(after.enabled)
        XCTAssertEqual(after.label, "It’s been 10+ minutes — end date")
        XCTAssertFalse(after.clock)
    }

    func testTheScrimClosesTheSheetOnAMenuAndOnCAndStepsBackOnTheGuideGAndI() {
        XCTAssertTrue(scrimClosesSheet(.menu))
        XCTAssertTrue(scrimClosesSheet(.met))
        XCTAssertFalse(scrimClosesSheet(.guide))
        XCTAssertFalse(scrimClosesSheet(.noShow))
        XCTAssertFalse(scrimClosesSheet(.uncomfortable))
    }

    func testLandingRunsOncePerEndedDate() {
        var once = LandedOnce()
        XCTAssertTrue(once.first("d1"))
        XCTAssertFalse(once.first("d1"))
        XCTAssertTrue(once.first("d2"))
    }

    func testMessageIsOfferedOnlyWhileTheChatIsOpen() {
        XCTAssertTrue(messageShown(chatOpen: true))
        XCTAssertFalse(messageShown(chatOpen: false))
    }

    func testTheGatesEntryIsWrapupFromTheWrapUpSheetAndReportFromTheReportSheet() {
        XCTAssertEqual(gateEntryFor(.wrap), .wrapup)
        XCTAssertEqual(gateEntryFor(.report), .report)
        XCTAssertEqual(ArrivalEntry.wrapup.wire, "wrapup")
    }

    func testTheCopyTheRulesQuoteIsTheTickets() {
        XCTAssertEqual(DateEndCopy.endLocked("19:00"), "You can end the date from 19:00")
        XCTAssertEqual(DateEndCopy.endOpen, "It’s been 10+ minutes — end date")
    }
}
