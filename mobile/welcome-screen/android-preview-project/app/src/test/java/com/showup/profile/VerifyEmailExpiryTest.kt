/*
 * VerifyEmailExpiryTest.kt
 * ShowUp · a correct code that has aged out must not be called a typo (SHOWUP-153)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE BUG THIS EXISTS FOR
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `/auth/email/verify` answered the same sentence -- "Invalid or expired code" -- for a mistyped
 * code, an aged-out one, a superseded one and a missing challenge. The client could not tell them
 * apart, so it called all four a mismatch, and the screen said:
 *
 *     "That code doesn't match. Check your inbox or request a new one."
 *
 * A user holding a CORRECT code that had simply expired was therefore told to check their inbox
 * for the code they had already typed -- and did, and was told the same thing again. Reported
 * twice from a device, the second time with a screenshot of the test-build banner displaying the
 * very code being refused, because that banner showed the last code ISSUED with nothing to say
 * whether it still worked.
 *
 * `BasicsRepositoryTest` covers the wire: which status and which reason become which result, on a
 * real server. THIS covers what the screen does with that result, which is the half a user
 * experiences -- the state a refusal leaves behind, and whether there is a way out of it.
 *
 * NO SERVER HERE, DELIBERATELY. The first version of this file drove the view model through
 * MockWebServer, which suspends on real OkHttp I/O that a test scheduler cannot wait for -- so it
 * alternated virtual time with real sleeps, and the suite took NINE HOURS to run once. The
 * repository is `open` for exactly this reason.
 */
package com.showup.profile

import com.showup.api.InMemoryTokenStore
import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.ShowUpApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailExpiryTest {

    /**
     * Answers without a socket.
     *
     * [verdict] is what `/auth/email/verify` decides. The send always succeeds with a challenge
     * that has five minutes on it, so nothing in this file expires by the CLOCK -- every expiry
     * tested here is the server's verdict, which is exactly the case the client could not see.
     */
    private class FakeRepo(
        var verdict: VerifyCodeResult,
        private val now: () -> OffsetDateTime,
    ) : BasicsRepository(
        ShowUpApi(baseUrl = "http://127.0.0.1:1/", tokens = InMemoryTokenStore()),
        offline = null,
    ) {
        var sends = 0

        override suspend fun sendCode(email: String): SendCodeResult {
            sends++
            return SendCodeResult.Sent(
                expiresAt = now().plusMinutes(5),
                resendAvailableAt = now().plusMinutes(1),
                devCode = "336935",
            )
        }

        override suspend fun verifyCode(code: String): VerifyCodeResult = verdict
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun start() = Dispatchers.setMain(dispatcher)

    @After
    fun stop() = Dispatchers.resetMain()

    private fun build(verdict: VerifyCodeResult): Pair<FakeRepo, BasicsViewModel> {
        val repo = FakeRepo(verdict) { now() }
        // ONE CLOCK, AND IT IS THE SCHEDULER'S.
        //
        // `BasicsViewModel.now` exists to be overridden here -- "so a countdown does not need a
        // real second to pass" -- and leaving it on the real clock is not merely slow, it does
        // not terminate. The resend ticker recomputes its remaining seconds from `now()` and then
        // `delay(1000)`s; on virtual time that delay costs nothing real, so the remainder never
        // shrinks and the loop runs forever. `runTest` then waits a full minute for it at the end
        // of EVERY test, which is how this file first took six minutes to assert six things.
        //
        // Tied to `currentTime`, the countdown converges in sixty virtual seconds and no real
        // ones, and every timestamp in the fixture comes from the same place.
        return repo to BasicsViewModel(repo).also { it.now = { now() } }
    }

    /** Virtual time, as a wall clock. The base is arbitrary and fixed so nothing drifts. */
    private fun now(): OffsetDateTime =
        OffsetDateTime.parse("2026-09-28T10:00:00Z")
            .plusNanos(dispatcher.scheduler.currentTime * 1_000_000)

    /**
     * Runs what is pending, and nothing more.
     *
     * `runCurrent()` RATHER THAN `advanceUntilIdle()`: a successful send starts the resend
     * countdown, which reschedules itself against the real clock, so draining virtual time to
     * idle never reaches idle. That hung the suite until `runTest` gave up after a minute.
     */
    private fun settle() = dispatcher.scheduler.runCurrent()

    /** Sends a code and types [digits] -- the state every rule below starts from. */
    private fun BasicsViewModel.sendAndType(digits: String) {
        sendCode()
        settle()
        setDigits(digits)
    }

    // ── the report ──────────────────────────────────────────────────────────

    /**
     * THE EXACT SITUATION IN THE SCREENSHOT.
     *
     * The clock still says the code is live -- the challenge had five minutes on it -- and the
     * server refuses it anyway, because it was superseded, consumed or dropped. Only the server
     * knows that, so only the server can say it, and the client has to believe it over its own
     * countdown.
     */
    @Test
    fun `a server that says expired beats the client's own countdown`() = runTest(dispatcher) {
        val (_, vm) = build(VerifyCodeResult.Expired)
        vm.sendAndType("336935")
        assertFalse("the clock thinks it is live", vm.state.value.expired(now()))

        vm.verify { }
        settle()

        val state = vm.state.value
        assertEquals(
            "the screen must say expired, never 'that code doesn't match'",
            VerifyState.Expired, state.failure(now()),
        )
        assertFalse("and it must not still be blaming the typing", state.lastSubmitRefused)
    }

    @Test
    fun `an expired verdict opens the way out instead of closing it`() = runTest(dispatcher) {
        val (_, vm) = build(VerifyCodeResult.Expired)
        vm.sendAndType("336935")
        vm.verify { }
        settle()

        val state = vm.state.value
        // THE USER MUST HAVE A MOVE. Retyping cannot fix an expired code, so submitting is off
        // and resending is on. A screen where neither is possible is a dead end, and this is the
        // one refusal where the obvious action is the useless one.
        assertFalse(
            "the dead code cannot be resubmitted",
            canSubmitCode(state.codeDigits, state.failure(now())),
        )
        assertTrue(
            "and a new code can be asked for",
            canResend(cooldownSeconds = 0, state = state.failure(now())),
        )
    }

    @Test
    fun `an expired verdict never advances the flow`() = runTest(dispatcher) {
        // The callback IS the step completing. A refusal of any kind must not run it, and this is
        // the one that changes `expiresAt` -- a state change is not a success.
        val (_, vm) = build(VerifyCodeResult.Expired)
        vm.sendAndType("336935")

        var advanced = false
        vm.verify { advanced = true }
        settle()

        assertFalse("an expired code is not a verified email", advanced)
    }

    // ── the other two refusals are unchanged ────────────────────────────────

    @Test
    fun `a mismatch is still a mismatch, and still shakes`() = runTest(dispatcher) {
        val (_, vm) = build(VerifyCodeResult.Refused)
        vm.sendAndType("000000")
        val before = vm.state.value.shakeKey

        vm.verify { }
        settle()

        val state = vm.state.value
        assertEquals(VerifyState.Mismatch, state.failure(now()))
        assertTrue(
            "a wrong code is the one case where the typing IS the problem",
            state.lastSubmitRefused,
        )
        assertEquals("and it shakes", before + 1, state.shakeKey)
        assertEquals(1, state.attempts)
    }

    @Test
    fun `the cap is the cap, whichever code was typed`() = runTest(dispatcher) {
        val (_, vm) = build(VerifyCodeResult.TooManyAttempts)
        vm.sendAndType("336935")

        vm.verify { }
        settle()

        val state = vm.state.value
        assertEquals(VerifyState.LockedOut, state.failure(now()))
        // The count goes TO the cap rather than up by one: the server has the authority, and the
        // two can disagree if a request was lost.
        assertEquals(MAX_VERIFY_ATTEMPTS, state.attempts)
    }

    // ── and a fresh send is a fresh start ───────────────────────────────────

    @Test
    fun `sending a new code clears the expiry the old one left behind`() = runTest(dispatcher) {
        val (repo, vm) = build(VerifyCodeResult.Expired)
        vm.sendAndType("336935")
        vm.verify { }
        settle()
        assertEquals(VerifyState.Expired, vm.state.value.failure(now()))

        vm.sendCode()
        settle()

        val state = vm.state.value
        assertEquals("the resend really happened", 2, repo.sends)
        assertEquals(
            "a new code is a clean screen, not an expired one with new digits",
            VerifyState.Calm, state.failure(now()),
        )
        assertEquals("and the boxes are empty", "", state.codeDigits)
        assertEquals("with the attempt count reset", 0, state.attempts)
    }
}
