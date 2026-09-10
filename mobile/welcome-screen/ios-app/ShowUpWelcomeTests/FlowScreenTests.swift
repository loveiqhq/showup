//  FlowScreenTests.swift
//  ShowUp · the typed screen, and the values a scene writes down
//
//  WHAT THIS COVERS, AND WHAT IT HONESTLY DOES NOT
//
//  `@SceneStorage` needs a real scene. These tests run in a hosted window with no scene, so
//  "the flow actually comes back where the user left it on iOS" is NOT asserted here and is a
//  manual check on a device. Android's half of the same behaviour IS machine-tested, in
//  `FlowRestorationTest`, using StateRestorationTester.
//
//  What is testable here is the half that would break silently: the values a scene writes down
//  have to survive the round trip, and the ORDER of the cases has to reproduce every direction the
//  Int scheme gave, because the screen transition reads its direction from a comparison.

import XCTest
@testable import ShowUpWelcome

final class FlowScreenTests: XCTestCase {

    // MARK: - the raw values a scene stores

    /// Every case survives being written down and read back.
    func testEveryScreenRoundTripsThroughItsRawValue() {
        for screen in FlowScreen.allCases {
            XCTAssertEqual(FlowScreen(rawValue: screen.rawValue), screen,
                           "\(screen) did not survive the round trip")
        }
    }

    /// A stored value that is not a screen must fall back rather than crash or pick a case.
    func testAnUnknownStoredValueIsRejectedRatherThanGuessed() {
        XCTAssertNil(FlowScreen(rawValue: ""))
        XCTAssertNil(FlowScreen(rawValue: "-4"))          // the old Int scheme
        XCTAssertNil(FlowScreen(rawValue: "tutorialwelcome"))  // case matters
    }

    /// Names, not ordinals.
    ///
    /// This is the reason the enum carries String raw values at all. If these were the implicit
    /// Int ordinals, inserting a tutorial card would move every restored user one screen along --
    /// silently, and only for people who had been killed mid-flow.
    func testRawValuesAreNamesSoInsertingAScreenCannotShiftAnyone() {
        XCTAssertEqual(FlowScreen.signUp.rawValue, "signUp")
        XCTAssertEqual(FlowScreen.showUpEveryTime.rawValue, "showUpEveryTime")
        XCTAssertEqual(FlowScreen.home.rawValue, "home")
        for screen in FlowScreen.allCases {
            XCTAssertNil(Int(screen.rawValue), "\(screen) stores something that looks like an index")
        }
    }

    // MARK: - the ordering the transition depends on

    /// Every direction the Int scheme produced, reproduced by declaration order.
    ///
    /// Taken from the scheme this replaced: -4 signUp, 1...6 the cards, 7 home. "Start over" was
    /// 7 -> -4 and read as BACKWARD, which is the one a naive ordering gets wrong.
    func testTheOrderReproducesEveryDirectionTheIntSchemeGave() {
        let cases: [(FlowScreen, FlowScreen, Bool)] = [
            (.signUp, .tutorialWelcome, true),
            (.signUp, .home, true),
            (.tutorialWelcome, .meetInRealLife, true),
            (.meetInRealLife, .matchOnAvailability, true),
            (.matchOnAvailability, .matchMeansMeet, true),
            (.matchMeansMeet, .thirtyMinutes, true),
            (.thirtyMinutes, .showUpEveryTime, true),
            (.showUpEveryTime, .home, true),
            (.matchOnAvailability, .meetInRealLife, false),
            (.matchMeansMeet, .matchOnAvailability, false),
            (.thirtyMinutes, .matchMeansMeet, false),
            (.showUpEveryTime, .thirtyMinutes, false),
            (.home, .signUp, false),
        ]
        for (from, to, wasForward) in cases {
            XCTAssertEqual(to > from, wasForward,
                           "\(from) -> \(to) should travel \(wasForward ? "forward" : "back")")
        }
    }

    func testEveryScreenIsDistinctAndNamed() {
        // 12 since "The basics" is complete: the eight originals plus the four profile steps —
        // profileName, profileEmail, profileVerifyEmail and profileDob — which sit between the
        // tutorial's end and home. Mirrors the Android assertion in FlowRestorationTest.
        XCTAssertEqual(FlowScreen.allCases.count, 12)
        XCTAssertEqual(Set(FlowScreen.allCases.map(\.rawValue)).count, 12)
    }

    // MARK: - the routing vocabulary a scene stores

    /// `Entry` and `SignUpOutcome` are persisted through adapters kept OUT of TutorialRouting.swift,
    /// so that file stays a pure decision module with no storage concerns. These assert the
    /// adapters, not the routing.
    func testRoutingValuesRoundTripThroughTheirStorageKeys() {
        for entry in [Entry.createAccount, .logIn] {
            XCTAssertEqual(Entry(storageKey: entry.storageKey), entry)
        }
        for outcome in [SignUpOutcome.newAccount, .returningMember] {
            XCTAssertEqual(SignUpOutcome(storageKey: outcome.storageKey), outcome)
        }
    }

    func testUnknownRoutingKeysAreRejected() {
        XCTAssertNil(Entry(storageKey: "createaccount"))
        XCTAssertNil(SignUpOutcome(storageKey: ""))
    }

    /// The storage keys are deliberately independent of the case names, so renaming a case cannot
    /// silently invalidate everyone's restored scene.
    func testStorageKeysAreStableStrings() {
        XCTAssertEqual(Entry.createAccount.storageKey, "createAccount")
        XCTAssertEqual(Entry.logIn.storageKey, "logIn")
        XCTAssertEqual(SignUpOutcome.newAccount.storageKey, "newAccount")
        XCTAssertEqual(SignUpOutcome.returningMember.storageKey, "returningMember")
    }
}
