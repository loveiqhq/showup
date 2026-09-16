/*
 * EmailVerificationTest.kt
 * ShowUp · the four things the code screen can be saying, and which one wins
 */
package com.showup.profile

import com.showup.api.MAX_VERIFY_ATTEMPTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailVerificationTest {

    private fun state(
        attempts: Int = 0,
        expired: Boolean = false,
        refused: Boolean = false,
    ) = verifyState(attempts, maxAttempts = 5, expired = expired, lastSubmitRefused = refused)

    // ── the four states ───────────────────────────────────────────────────────

    @Test
    fun `nothing submitted is calm`() {
        assertEquals(VerifyState.Calm, state())
    }

    @Test
    fun `a refused submit is a mismatch`() {
        assertEquals(VerifyState.Mismatch, state(attempts = 1, refused = true))
    }

    @Test
    fun `the cap is the server's five`() {
        assertEquals(VerifyState.Mismatch, state(attempts = 4, refused = true))
        assertEquals(VerifyState.LockedOut, state(attempts = 5, refused = true))
    }

    @Test
    fun `a passed expiry is expired`() {
        assertEquals(VerifyState.Expired, state(expired = true))
    }

    // ── precedence ────────────────────────────────────────────────────────────

    @Test
    fun `expired beats locked out`() {
        // A code that died of age on the second try did not fail for too many tries, and saying so
        // would tell the user they had used up something they had not.
        assertEquals(VerifyState.Expired, state(attempts = 5, expired = true, refused = true))
    }

    @Test
    fun `locked out beats mismatch`() {
        // Reaching the cap means the last submit WAS a mismatch. "Try again" would be an
        // instruction that cannot be followed.
        assertEquals(VerifyState.LockedOut, state(attempts = 5, refused = true))
    }

    @Test
    fun `expired beats mismatch`() {
        assertEquals(VerifyState.Expired, state(attempts = 1, expired = true, refused = true))
    }

    // ── what the controls do ──────────────────────────────────────────────────

    @Test
    fun `six digits are required before the CTA can submit`() {
        assertFalse(canSubmitCode("48217", VerifyState.Calm))
        assertTrue(canSubmitCode("482170", VerifyState.Calm))
    }

    @Test
    fun `a mismatch still allows a resubmit, because the fix is usually one digit`() {
        assertTrue(canSubmitCode("482170", VerifyState.Mismatch))
    }

    @Test
    fun `neither blocking state allows a submit the server would refuse`() {
        assertFalse(canSubmitCode("482170", VerifyState.LockedOut))
        assertFalse(canSubmitCode("482170", VerifyState.Expired))
    }

    @Test
    fun `the resend waits out the cooldown only while nothing has gone wrong`() {
        assertFalse(canResend(cooldownSeconds = 45, state = VerifyState.Calm))
        assertTrue(canResend(cooldownSeconds = 0, state = VerifyState.Calm))
    }

    @Test
    fun `every failure releases the resend immediately`() {
        // A user who can neither submit nor resend has no move left. This is the assertion that
        // would fail if someone made the cooldown apply to a locked-out or expired code.
        for (s in listOf(VerifyState.Mismatch, VerifyState.Expired, VerifyState.LockedOut)) {
            assertTrue("$s must release the resend", canResend(cooldownSeconds = 45, state = s))
        }
    }

    @Test
    fun `the client cap equals the constant the server's is mirrored into`() {
        assertEquals(5, MAX_VERIFY_ATTEMPTS)
    }
}
