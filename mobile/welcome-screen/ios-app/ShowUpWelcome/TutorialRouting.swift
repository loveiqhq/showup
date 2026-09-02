//  TutorialRouting.swift
//  ShowUp · who is shown the app tutorial, and who is not (SHOWUP-146)
//
//  The Swift twin of TutorialRouting.kt, deliberately identical in shape so the two platforms can
//  be read side by side and so a change to the rule is obviously a change to both.
//
//  SHOWUP-146 joins two flows that were built separately: sign-up/sign-in (140/142/143/144/145) and
//  the six-card tutorial. The join is one decision, taken once, when the sign-up flow ends.
//
//  The ticket states it as four rules. A first-time user sees the tutorial:
//
//    1. after creating an account via OTP and tapping "Skip and continue to profile" on Connect
//    2. after successfully connecting an account and tapping "Continue"
//    3. after failing to connect an account and tapping "Skip and continue to profile"
//
//  and a returning user, who re-logged in by any method, does not see it at all.
//
//  Read together those are not four rules but one: the tutorial belongs to account CREATION, not to
//  the Connect screen and not to any particular way of leaving it. Rules 1-3 enumerate every exit
//  from Connect precisely to say that the exit does not matter.
//
//  AT PARITY: both sides are covered by 8 cases each -- TutorialRoutingTest.kt on Android and
//  ShowUpWelcomeTests/TutorialRoutingTests.swift here, the latter running since 2026-09-01. The
//  third guard, audit/check-tutorial-routing.py, compares the two files for sameness, so a rule
//  changed on one platform and not the other is caught even when both suites pass.

import Foundation

/// How the person entered the flow. Decided at the two doors on Startup (SHOWUP-140) and never
/// revised, because it is a statement about intent rather than about outcome: someone who taps
/// "Create free account" is creating an account even while they are still typing their number.
enum Entry {
    /// "Create free account" on Startup.
    case createAccount
    /// "Log in" on Startup, or a launch onto Welcome back because the device remembers someone.
    case logIn
}

/// How the Connect screen (SHOWUP-144) was left.
///
/// Nil means it was never reached. That is not a missing value -- it is the returning member's
/// path, which goes from the code screen straight into the app.
enum ConnectExit: CaseIterable {
    /// "Skip and continue to profile" -- from the idle screen, or after a cancel or an error.
    case skipped
    /// A provider was linked and "Continue" was tapped on the success state.
    case connected
    /// The provider account was already attached to a different Show Up account, and the user
    /// chose to go to that account instead of this one. That makes them a returning member -- see
    /// `outcomeOf`, where the reasoning and the date it was decided are recorded.
    case resolvedConflict
}

/// Where the flow leaves the user, which is all the host needs in order to route.
enum SignUpOutcome {
    /// Straight into the tutorial.
    case newAccount
    /// Straight into the app. The tutorial is skipped.
    case returningMember
}

/// SHOWUP-146, entire.
///
/// The conflict case is a decision, not a quotation. The ticket lists three ways to leave Connect
/// and an account conflict is not one of them, so it was put to the product side on 2026-08-30 and
/// answered: resolving a conflict means abandoning the account just created and continuing as the
/// owner of an older one, and that person is a returning member. They have seen the tour already.
///
/// Still worth adding to the ticket text -- the rule now lives here and in the tests, but a reader
/// of SHOWUP-146 alone would not find it.
func outcomeOf(_ entry: Entry, _ connectExit: ConnectExit?) -> SignUpOutcome {
    if entry == .logIn { return .returningMember }
    if connectExit == .resolvedConflict { return .returningMember }
    return .newAccount
}

/// True when `outcomeOf` says this person should be shown the six tutorial cards.
func showsTutorial(_ outcome: SignUpOutcome) -> Bool { outcome == .newAccount }
