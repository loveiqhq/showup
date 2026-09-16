//  DateOfBirthTests.swift
//  ShowUp · the date rules, without a device. The iOS half of DateOfBirthTest.kt.
//
//  The value this screen writes is LOCKED — there is no in-app path to change an age once it is
//  stored. So a date that parses wrongly is not a bug that gets fixed on the next screen; it is a
//  support ticket.

import XCTest
@testable import ShowUpWelcome

final class DateOfBirthTests: XCTestCase {

    /// A fixed "today" so the expected ages never drift: 18 May 2026, UTC.
    private func today(_ y: Int = 2026, _ m: Int = 5, _ d: Int = 18) -> Date {
        var c = DateComponents()
        c.year = y; c.month = m; c.day = d
        return utcCalendar.date(from: c)!
    }

    private func valid(_ r: DobResult, file: StaticString = #filePath, line: UInt = #line)
        -> (age: Int, iso: String) {
        guard case let .valid(age, iso) = r else {
            XCTFail("expected a real date, got \(r)", file: file, line: line)
            return (0, "")
        }
        return (age, iso)
    }

    // MARK: - formatting

    func testSlashesAreInsertedNeverTyped() {
        XCTAssertEqual(formatDob("0"), "0")
        XCTAssertEqual(formatDob("03"), "03")
        XCTAssertEqual(formatDob("032"), "03/2")
        XCTAssertEqual(formatDob("0322"), "03/22")
        XCTAssertEqual(formatDob("03221998"), "03/22/1998")
    }

    func testANinthDigitIsRefused() {
        XCTAssertEqual(formatDob("032219987"), "03/22/1998")
    }

    func testBackspaceRemovesADigitAndTheSlashesLookAfterThemselves() {
        XCTAssertEqual(formatDob(String(dobDigits("03/22/1998").dropLast())), "03/22/199")
    }

    // MARK: - the round trip

    func testThirtyFebruaryIsImpossibleNotSilentlySecondMarch() {
        // The defect the round-trip check exists for. A lenient calendar rolls this over and the
        // user is locked to a birthday they never typed.
        XCTAssertEqual(parseDob("02/30/1990", order: .monthFirst, today: today()), .impossible)
    }

    func testThirtyFirstAprilIsImpossible() {
        XCTAssertEqual(parseDob("04/31/1990", order: .monthFirst, today: today()), .impossible)
    }

    func testLeapDayIsRealInALeapYearAndImpossibleInACommonOne() {
        if case .valid = parseDob("02/29/1996", order: .monthFirst, today: today()) {} else {
            XCTFail("29 Feb 1996 is a real date")
        }
        XCTAssertEqual(parseDob("02/29/1997", order: .monthFirst, today: today()), .impossible)
    }

    func testOutOfRangeIsRefusedBeforeTheCalendarIsConsulted() {
        XCTAssertEqual(parseDob("13/01/1990", order: .monthFirst, today: today()), .impossible)
        XCTAssertEqual(parseDob("01/00/1990", order: .monthFirst, today: today()), .impossible)
        XCTAssertEqual(parseDob("01/01/1899", order: .monthFirst, today: today()), .impossible)
        XCTAssertEqual(parseDob("01/01/2027", order: .monthFirst, today: today()), .impossible)
    }

    func testFewerThanEightDigitsIsIncompleteNotAnError() {
        // An error before the user has finished typing would fire on every keystroke.
        XCTAssertEqual(parseDob("03/22/199", order: .monthFirst, today: today()), .incomplete)
        XCTAssertEqual(parseDob("", order: .monthFirst, today: today()), .incomplete)
    }

    // MARK: - locale order

    func testTheSameEightDigitsAreTwoDifferentDatesInTwoLocales() {
        // 03/04 is the whole reason the product side ruled for device locale: it is valid in BOTH
        // readings, so a German user typing day-first into a US-ordered field gets a wrong date
        // that no validation can catch — and the age is locked afterwards.
        XCTAssertEqual(valid(parseDob("03/04/1998", order: .monthFirst, today: today())).iso,
                       "1998-03-04")
        XCTAssertEqual(valid(parseDob("03/04/1998", order: .dayFirst, today: today())).iso,
                       "1998-04-03")
    }

    func testADayFirstLocaleAcceptsTwentyTwoInTheFirstPosition() {
        XCTAssertEqual(parseDob("22/03/1998", order: .monthFirst, today: today()), .impossible)
        XCTAssertEqual(valid(parseDob("22/03/1998", order: .dayFirst, today: today())).iso,
                       "1998-03-22")
    }

    func testThePlaceholderNamesTheOrderBeingAskedFor() {
        XCTAssertEqual(DateOrder.monthFirst.pattern, "mm/dd/yyyy")
        XCTAssertEqual(DateOrder.dayFirst.pattern, "dd/mm/yyyy")
    }

    func testTheLocaleReaderAgreesWithKnownLocales() {
        XCTAssertEqual(DateOrder.forCurrentLocale(Locale(identifier: "en_US")), .monthFirst)
        XCTAssertEqual(DateOrder.forCurrentLocale(Locale(identifier: "de_DE")), .dayFirst)
        XCTAssertEqual(DateOrder.forCurrentLocale(Locale(identifier: "en_GB")), .dayFirst)
    }

    // MARK: - age, in UTC, matching the server

    func testAgeIsWholeYears() {
        XCTAssertEqual(valid(parseDob("03/22/1998", order: .monthFirst, today: today())).age, 28)
    }

    func testTheBirthdayNotYetReachedTakesAYearOff() {
        XCTAssertEqual(valid(parseDob("05/19/1998", order: .monthFirst, today: today())).age, 27)
        XCTAssertEqual(valid(parseDob("05/18/1998", order: .monthFirst, today: today())).age, 28)
    }

    func testALeapDayBirthdayAgesOnFirstMarchInACommonYear() {
        XCTAssertEqual(valid(parseDob("02/29/1996", order: .monthFirst, today: today(2026, 2, 28))).age, 29)
        XCTAssertEqual(valid(parseDob("02/29/1996", order: .monthFirst, today: today(2026, 3, 1))).age, 30)
    }

    func testTheDayBeforeTurningEighteenIsUnderTheGate() {
        // The boundary the server also enforces. Off by one either blocks an adult or admits a
        // minor, and the second is the one that matters.
        XCTAssertLessThan(valid(parseDob("05/19/2008", order: .monthFirst, today: today())).age, minimumAge)
        XCTAssertGreaterThanOrEqual(
            valid(parseDob("05/18/2008", order: .monthFirst, today: today())).age, minimumAge)
    }

    func testTheIsoStringIsWhatTheBackendAsksFor() {
        // UpsertProfileDto.dateOfBirth is documented "YYYY-MM-DD", zero-padded.
        XCTAssertEqual(valid(parseDob("01/01/1900", order: .monthFirst, today: today())).iso,
                       "1900-01-01")
    }

    func testTheMinimumAgeIsTheServers() {
        XCTAssertEqual(minimumAge, 18)
    }
}

final class EmailVerificationTests: XCTestCase {

    private func state(attempts: Int = 0, expired: Bool = false, refused: Bool = false) -> VerifyState {
        verifyState(attempts: attempts, maxAttempts: 5, expired: expired, lastSubmitRefused: refused)
    }

    func testTheFourStates() {
        XCTAssertEqual(state(), .calm)
        XCTAssertEqual(state(attempts: 1, refused: true), .mismatch)
        XCTAssertEqual(state(attempts: 5, refused: true), .lockedOut)
        XCTAssertEqual(state(expired: true), .expired)
    }

    func testTheCapIsTheServersFive() {
        XCTAssertEqual(state(attempts: 4, refused: true), .mismatch)
        XCTAssertEqual(state(attempts: 5, refused: true), .lockedOut)
        XCTAssertEqual(maxVerifyAttempts, 5)
    }

    func testPrecedence() {
        // A code that died of age on the second try did not fail for too many tries, and saying so
        // would tell the user they had used up something they had not.
        XCTAssertEqual(state(attempts: 5, expired: true, refused: true), .expired)
        // Reaching the cap means the last submit WAS a mismatch, so "try again" cannot be followed.
        XCTAssertEqual(state(attempts: 5, refused: true), .lockedOut)
        XCTAssertEqual(state(attempts: 1, expired: true, refused: true), .expired)
    }

    func testWhatTheControlsDo() {
        XCTAssertFalse(canSubmitCode("48217", state: .calm))
        XCTAssertTrue(canSubmitCode("482170", state: .calm))
        // The fix is usually one digit, so a mismatch still allows a resubmit.
        XCTAssertTrue(canSubmitCode("482170", state: .mismatch))
        // Neither blocking state should ask the server for a refusal it has already decided.
        XCTAssertFalse(canSubmitCode("482170", state: .lockedOut))
        XCTAssertFalse(canSubmitCode("482170", state: .expired))
    }

    func testEveryFailureReleasesTheResend() {
        XCTAssertFalse(canResend(cooldownSeconds: 45, state: .calm))
        XCTAssertTrue(canResend(cooldownSeconds: 0, state: .calm))
        // A user who can neither submit nor resend has no move left.
        for s in [VerifyState.mismatch, .expired, .lockedOut] {
            XCTAssertTrue(canResend(cooldownSeconds: 45, state: s), "\(s) must release the resend")
        }
    }
}
