/*
 * TutorialRouting.kt
 * ShowUp · who is shown the app tutorial, and who is not (SHOWUP-146)
 *
 * SHOWUP-146 joins two flows that were built separately: sign-up/sign-in (140/142/143/144/145) and
 * the six-card tutorial. The join is one decision, taken once, at the moment the sign-up flow ends.
 *
 * The ticket states it as four rules. A first-time user sees the tutorial:
 *
 *   1. after creating an account via OTP and tapping "Skip and continue to profile" on Connect
 *   2. after successfully connecting an account and tapping "Continue"
 *   3. after failing to connect an account and tapping "Skip and continue to profile"
 *
 * and a returning user, who re-logged in by any method, does not see it at all.
 *
 * Read together, those four are not four rules but one: the tutorial belongs to account CREATION,
 * not to the Connect screen and not to any particular way of leaving it. Rules 1-3 enumerate every
 * exit from Connect precisely to say that the exit does not matter. That is why this file models
 * the question as (how you came in, how you left) rather than as a list of three screens.
 *
 * Keeping it here, as a pure function over enums, rather than inline in the flow's `when`, is what
 * makes it testable without a device -- see TutorialRoutingTest.
 */
package com.showup.welcome

/**
 * How the person entered the flow. Decided at the two doors on Startup (SHOWUP-140) and never
 * revised, because it is a statement about intent rather than about outcome: someone who taps
 * "Create free account" is creating an account even while they are still typing their number.
 */
enum class Entry {
    /** "Create free account" on Startup. */
    CreateAccount,

    /** "Log in" on Startup, or a launch onto Welcome back because the device remembers someone. */
    LogIn,
}

/**
 * How the Connect screen (SHOWUP-144) was left.
 *
 * Null means it was never reached. That is not a missing value -- it is the returning member's
 * path, which goes from the code screen straight into the app.
 */
enum class ConnectExit {
    /** "Skip and continue to profile" -- from the idle screen, or after a cancel or an error. */
    Skipped,

    /** A provider was linked and "Continue" was tapped on the success state. */
    Connected,

    /**
     * The provider account was already attached to a different ShowUp account, and the user chose
     * to go to that account instead of this one. See [outcomeOf] for why this ends up where it does.
     */
    ResolvedConflict,
}

/** Where the flow leaves the user, which is all the host needs in order to route. */
enum class SignUpOutcome {
    /** Straight into the tutorial. */
    NewAccount,

    /** Straight into the app. The tutorial is skipped. */
    ReturningMember,
}

/**
 * SHOWUP-146, entire.
 *
 * One case here is an inference rather than a quotation, and it is flagged in the audit notes:
 * resolving an account conflict. The ticket lists three ways to leave Connect and a conflict is not
 * one of them, but resolving one means abandoning the account just created and continuing as the
 * owner of an older one. That person has used Show Up before, so the returning-member rule covers
 * them and showing the tutorial would be showing it to someone who has already seen it.
 */
fun outcomeOf(entry: Entry, connectExit: ConnectExit?): SignUpOutcome = when {
    entry == Entry.LogIn -> SignUpOutcome.ReturningMember
    connectExit == ConnectExit.ResolvedConflict -> SignUpOutcome.ReturningMember
    else -> SignUpOutcome.NewAccount
}

/** True when [outcomeOf] says this person should be shown the six tutorial cards. */
fun showsTutorial(outcome: SignUpOutcome): Boolean = outcome == SignUpOutcome.NewAccount
