/*
 * TutorialRoutingTest.kt
 * ShowUp · SHOWUP-146's four rules, one test each
 *
 * The value of these is not that the function is complicated -- it is three lines. It is that the
 * rule is easy to break from a long way away: any future change to the sign-up flow that forgets to
 * carry the entry through will silently start showing the tutorial to returning members, and the
 * only way to notice on a device is to log in and watch for a screen that should not appear.
 *
 * Each test below is named after the bullet in the ticket it enforces.
 */
package com.showup.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialRoutingTest {

    // ── the three "shows the tutorial" bullets ──────────────────────────────

    @Test
    fun `created an account via OTP then skipped on Connect`() {
        assertEquals(
            SignUpOutcome.NewAccount,
            outcomeOf(Entry.CreateAccount, ConnectExit.Skipped),
        )
    }

    @Test
    fun `created an account then successfully connected and continued`() {
        assertEquals(
            SignUpOutcome.NewAccount,
            outcomeOf(Entry.CreateAccount, ConnectExit.Connected),
        )
    }

    @Test
    fun `could not connect an account and skipped`() {
        // The ticket separates "skipped straight away" from "skipped after a failure", but the
        // Connect screen offers one skip control for both, so they are the same exit. This test
        // exists to record that the two bullets are deliberately one case, not an oversight.
        assertEquals(
            SignUpOutcome.NewAccount,
            outcomeOf(Entry.CreateAccount, ConnectExit.Skipped),
        )
    }

    @Test
    fun `every way of leaving Connect after creating an account shows the tutorial`() {
        // The general form of the three bullets: for a new account the exit is irrelevant. Written
        // over the enum rather than over three literals so a fourth exit added later fails here
        // instead of quietly picking a default.
        ConnectExit.values()
            .filter { it != ConnectExit.ResolvedConflict }
            .forEach {
                assertTrue(
                    "leaving Connect via $it should still show the tutorial",
                    showsTutorial(outcomeOf(Entry.CreateAccount, it)),
                )
            }
    }

    // ── the "does not show the tutorial" bullet ─────────────────────────────

    @Test
    fun `a returning member never sees the tutorial, by any route`() {
        val routes = ConnectExit.values().toList<ConnectExit?>() + null
        routes.forEach {
            assertFalse(
                "logging back in and leaving via $it must not show the tutorial",
                showsTutorial(outcomeOf(Entry.LogIn, it)),
            )
        }
    }

    @Test
    fun `a returning member does not reach Connect at all`() {
        // Null exit is the returning member's real path: code screen straight into the app.
        assertEquals(SignUpOutcome.ReturningMember, outcomeOf(Entry.LogIn, null))
    }

    // ── the case the ticket does not cover ──────────────────────────────────

    @Test
    fun `resolving an account conflict is treated as a returning member`() {
        // Not quoted from the ticket -- inferred, and flagged for the PO. Resolving a conflict
        // means continuing as the owner of an older account, and that person has seen the tour.
        assertEquals(
            SignUpOutcome.ReturningMember,
            outcomeOf(Entry.CreateAccount, ConnectExit.ResolvedConflict),
        )
    }

    @Test
    fun `showsTutorial agrees with the outcome it is given`() {
        assertTrue(showsTutorial(SignUpOutcome.NewAccount))
        assertFalse(showsTutorial(SignUpOutcome.ReturningMember))
    }
}
