//  TutorialRoutingTests.swift
//  ShowUp · SHOWUP-146's four rules, one test each — the Swift half
//
//  ─────────────────────────────────────────────────────────────────────────────
//  NOT YET RUN. Written on Windows, where no Swift compiler exists.
//
//  This file is the missing half of the pair: TutorialRoutingTest.kt has covered the Kotlin rule
//  since 30 August, and the Swift twin has been unguarded the whole time. It is written now so the
//  gap closes the day a Mac is available rather than a week after, but nobody has compiled it and
//  it should be treated as a draft until someone has.
//
//  TO ENABLE, on the Mac (about ten minutes):
//    1. In Xcode: File > New > Target > Unit Testing Bundle, named ShowUpWelcomeTests.
//    2. Set its Host Application to ShowUpWelcome.
//    3. Add this file to that target.
//    4. cmd-U. Expect eight passes; if anything fails, the rule has drifted from Android and that
//       is exactly the finding this file exists to produce.
//    5. Teach gen_pbxproj.py about the test target, so the next new test file cannot go missing
//       the same way source files used to.
//
//  Deliberately identical in shape and in test names to TutorialRoutingTest.kt, so the two can be
//  read side by side and a difference is obvious rather than buried.
//  ─────────────────────────────────────────────────────────────────────────────

import XCTest
@testable import ShowUpWelcome

final class TutorialRoutingTests: XCTestCase {

    // MARK: - the three "shows the tutorial" bullets

    func testCreatedAnAccountViaOTPThenSkippedOnConnect() {
        XCTAssertEqual(outcomeOf(.createAccount, .skipped), .newAccount)
    }

    func testCreatedAnAccountThenSuccessfullyConnectedAndContinued() {
        XCTAssertEqual(outcomeOf(.createAccount, .connected), .newAccount)
    }

    func testCouldNotConnectAnAccountAndSkipped() {
        // The ticket separates "skipped straight away" from "skipped after a failure", but the
        // Connect screen offers one skip control for both, so they are the same exit. This test
        // exists to record that the two bullets are deliberately one case, not an oversight.
        XCTAssertEqual(outcomeOf(.createAccount, .skipped), .newAccount)
    }

    func testEveryWayOfLeavingConnectAfterCreatingAnAccountShowsTheTutorial() {
        // The general form of the three bullets: for a new account the exit is irrelevant. Written
        // over the enum rather than over three literals so a fourth exit added later fails here
        // instead of quietly picking a default.
        for exit in ConnectExit.allCases where exit != .resolvedConflict {
            XCTAssertTrue(
                showsTutorial(outcomeOf(.createAccount, exit)),
                "leaving Connect via \(exit) should still show the tutorial"
            )
        }
    }

    // MARK: - the "does not show the tutorial" bullet

    func testAReturningMemberNeverSeesTheTutorialByAnyRoute() {
        let routes: [ConnectExit?] = ConnectExit.allCases.map { $0 } + [nil]
        for exit in routes {
            XCTAssertFalse(
                showsTutorial(outcomeOf(.logIn, exit)),
                "logging back in and leaving via \(String(describing: exit)) must not show the tutorial"
            )
        }
    }

    func testAReturningMemberDoesNotReachConnectAtAll() {
        // Nil exit is the returning member's real path: code screen straight into the app.
        XCTAssertEqual(outcomeOf(.logIn, nil), .returningMember)
    }

    // MARK: - the case the ticket does not cover, decided separately

    func testResolvingAnAccountConflictIsTreatedAsAReturningMember() {
        // Not quoted from the ticket. Raised as a gap and decided on 2026-08-30: resolving a
        // conflict means continuing as the owner of an older account, and that person has seen the
        // tour. The ticket text still does not say so, which is why this test spells it out.
        XCTAssertEqual(outcomeOf(.createAccount, .resolvedConflict), .returningMember)
    }

    func testShowsTutorialAgreesWithTheOutcomeItIsGiven() {
        XCTAssertTrue(showsTutorial(.newAccount))
        XCTAssertFalse(showsTutorial(.returningMember))
    }
}
