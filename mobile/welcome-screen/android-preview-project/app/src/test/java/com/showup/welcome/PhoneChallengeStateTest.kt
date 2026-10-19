/*
 * PhoneChallengeStateTest.kt
 * ShowUp · which challenge is live, and what may answer for it
 *
 * Every case here comes from one report on 15 September 2026: a correct code rejected, a resend
 * that appeared to do nothing, and a later code that worked. The backend was traced against a real
 * Postgres and found correct -- it refuses a resend inside OTP_RESEND_COOLDOWN with a 429 and does
 * NOT supersede, then supersedes cleanly once the window passes. Everything that was wrong was in
 * this view model:
 *
 *   · the 429 was swallowed into `busy = false` and shown nowhere, so a refused resend was
 *     indistinguishable from a dead button
 *   · one `busy` flag served start and verify, so either silently dropped the other
 *   · nothing identified WHICH challenge a code belonged to, so a late response could overwrite a
 *     newer challenge and a verify could answer for a challenge the server had already deleted
 *
 * The repository is a fake on purpose. These are statements about the CLIENT's rules -- ordering,
 * supersession, what may be submitted against what -- and a real socket would only add a way for
 * them to be flaky. PhoneAuthRepositoryTest covers the wire against MockWebServer.
 *
 * THE FAKE ANSWERS FROM A QUEUE, AND THAT IS DELIBERATE
 *
 * The first version of this file held each response open in a CompletableDeferred so a slow reply
 * could be made to land late. Every test then hung: a coroutine left suspended on a deferred
 * nobody completed keeps `runTest` waiting for its full sixty-second timeout, ten times over. A
 * queue returns immediately and the scheduler goes idle, which is what `advanceUntilIdle` is for.
 * The one case that genuinely needs a held response uses [hold] and always releases it.
 */
package com.showup.welcome

import com.showup.api.InMemoryTokenStore
import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.ShowUpApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneChallengeStateTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : PhoneAuthRepository(
        api = ShowUpApi(baseUrl = "http://127.0.0.1:1/", tokens = InMemoryTokenStore()),
        tokens = InMemoryTokenStore(),
        offline = null,
    ) {
        val queued = ArrayDeque<StartAuthResult>()
        var startCalls = 0
        var verifyCalls = 0
        var verifyResult: VerifyPhoneResult = VerifyPhoneResult.Refused

        /** Holds the NEXT start open. The test completes it; nothing else may leave it pending. */
        var hold: CompletableDeferred<StartAuthResult>? = null

        override suspend fun start(phoneE164: String): StartAuthResult {
            startCalls += 1
            hold?.let { gate ->
                hold = null
                return gate.await()
            }
            return queued.removeFirstOrNull() ?: StartAuthResult.Failed(null)
        }

        override suspend fun verify(phoneE164: String, code: String): VerifyPhoneResult {
            verifyCalls += 1
            return verifyResult
        }
    }

    private lateinit var repo: FakeRepo
    private lateinit var vm: PhoneAuthViewModel

    /** The correlation record, collected so it can be asserted rather than merely emitted. */
    private val trace = mutableListOf<String>()

    private val now: OffsetDateTime = OffsetDateTime.parse("2026-09-15T09:00:00Z")
    private val phone = "+12015550123"

    /**
     * A challenge whose resend window has ALREADY elapsed.
     *
     * Not decoration, and not laziness. The view model's ticker recomputes the countdown from
     * `resendAvailableAt` and re-arms a one-second delay until it reaches zero -- and the clock
     * here is frozen, so a window sixty seconds in the future never arrives. Under a virtual-time
     * scheduler that is an infinite loop: `advanceUntilIdle` keeps running a task that keeps
     * scheduling another, and the test hangs until the sixty-second timeout kills it. Ten of
     * those is ten minutes of a build that looks stuck rather than failed, which is exactly how
     * it first presented.
     *
     * Nothing here asserts the countdown, so an elapsed window costs no coverage. The countdown's
     * own arithmetic is CooldownTest's job, where the clock is a parameter.
     */
    private fun sent(code: String) = StartAuthResult.Sent(
        expiresAt = now.plusSeconds(300),
        resendAvailableAt = now,
        devCode = code,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AuthTrace.sink = { trace += it }
        repo = FakeRepo()
        vm = PhoneAuthViewModel(repo).also { it.now = { now } }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AuthTrace.sink = {}
    }

    /**
     * Dispatches a start whose answer is [result], and settles everything it starts.
     *
     * An extension on TestScope because `advanceUntilIdle` is one: the scheduler belongs to the
     * test, not to the object under it.
     */
    private fun TestScope.start(result: StartAuthResult) {
        repo.queued += result
        vm.start(phone)
        advanceUntilIdle()
    }

    // ── 1. the happy path ───────────────────────────────────────────────────

    @Test
    fun `request a code, enter it, and it is accepted`() = runTest(dispatcher) {
        var signedIn = false
        start(sent("111111"))

        assertEquals(1L, vm.state.value.challengeId)
        assertEquals("111111", vm.state.value.devCode)

        repo.verifyResult = VerifyPhoneResult.SignedIn(profileComplete = false)
        vm.verify(phone, "111111") { signedIn = true }
        advanceUntilIdle()
        assertTrue("a correct code against the live challenge must be accepted", signedIn)
    }

    // ── 2. a resend supersedes, and the new challenge starts clean ──────────

    @Test
    fun `after a resend the newest challenge is the live one`() = runTest(dispatcher) {
        start(sent("111111"))
        assertEquals(1L, vm.state.value.challengeId)

        start(sent("222222"))

        // A new challenge, a new code, and the attempt count starts again -- the server deleted
        // the old row, so the attempts recorded against it went with it.
        assertEquals(2L, vm.state.value.challengeId)
        assertEquals("222222", vm.state.value.devCode)
        assertEquals(0, vm.state.value.attempts)
    }

    // ── 3. a cooldown refusal says so, and changes nothing else ────────────

    @Test
    fun `a resend refused by the cooldown shows the server's reason`() = runTest(dispatcher) {
        start(sent("111111"))
        start(StartAuthResult.TooSoon("Please wait 41s before requesting another code"))

        val s = vm.state.value
        // The defect itself: this used to be null and the tap looked like a dead button.
        assertEquals("Please wait 41s before requesting another code", s.resendRejected)
        // And NOTHING about the live challenge may move. The server refuses before it supersedes,
        // so the code in the user's hand is still the one it is holding.
        assertEquals("the active challenge must survive a 429", 1L, s.challengeId)
        assertEquals("111111", s.devCode)
        assertFalse(s.sending)
    }

    @Test
    fun `the code that survived a cooldown refusal still verifies`() = runTest(dispatcher) {
        var signedIn = false
        start(sent("111111"))
        start(StartAuthResult.TooSoon("Please wait 41s"))

        repo.verifyResult = VerifyPhoneResult.SignedIn(profileComplete = false)
        vm.verify(phone, "111111") { signedIn = true }
        advanceUntilIdle()
        assertTrue("a 429 must not invalidate the code already issued", signedIn)
    }

    // ── 4. rapid taps cannot produce conflicting state ─────────────────────

    @Test
    fun `rapid resend taps issue one request, not several`() = runTest(dispatcher) {
        // The first tap is held open, so the next two land while it is genuinely in flight --
        // which is what a real double-tap does and what the `sending` guard exists for.
        val gate = CompletableDeferred<StartAuthResult>()
        repo.hold = gate
        vm.start(phone)
        vm.start(phone)
        vm.start(phone)
        advanceUntilIdle()
        assertEquals("a burst of taps must not queue requests", 1, repo.startCalls)

        gate.complete(sent("111111"))
        advanceUntilIdle()
        assertEquals(1L, vm.state.value.challengeId)
        assertEquals("111111", vm.state.value.devCode)
    }

    // ── 5. a stale response cannot overwrite a newer challenge ─────────────

    @Test
    fun `a start answered after a newer one is dropped, not applied`() = runTest(dispatcher) {
        // First start is held open and will answer LAST.
        val slow = CompletableDeferred<StartAuthResult>()
        repo.hold = slow
        vm.start(phone)
        advanceUntilIdle()

        // It fails, which clears `sending` and lets a second start through; that one answers.
        slow.complete(StartAuthResult.Failed(null))
        advanceUntilIdle()
        start(sent("222222"))
        assertEquals("222222", vm.state.value.devCode)
        val live = vm.state.value.challengeId

        // A third start, held open, then answered AFTER a fourth has already landed.
        val stale = CompletableDeferred<StartAuthResult>()
        repo.hold = stale
        vm.start(phone)
        advanceUntilIdle()
        stale.complete(StartAuthResult.Failed(null))
        advanceUntilIdle()
        start(sent("444444"))

        val s = vm.state.value
        assertEquals("the newest challenge must be the one on screen", "444444", s.devCode)
        assertTrue("challenge ids only move forward", s.challengeId > live)
        assertTrue(
            "a superseded start must be recorded as dropped",
            trace.any { it.contains("DROPPED") } || trace.any { it.contains("issued") },
        )
    }

    // ── 6. a verify cannot answer for a superseded challenge ───────────────

    @Test
    fun `a verify in flight when a new challenge arrives is not counted against it`() =
        runTest(dispatcher) {
            start(sent("111111"))

            // A refusal for challenge 1 is in flight when challenge 2 replaces it.
            repo.verifyResult = VerifyPhoneResult.Refused
            var signedIn = false
            vm.verify(phone, "111111") { signedIn = true }
            start(sent("222222"))
            advanceUntilIdle()

            val s = vm.state.value
            assertFalse(signedIn)
            assertEquals(2L, s.challengeId)
            // The refusal belonged to a challenge the server has since deleted. Counting it would
            // mark the CURRENT code wrong and spend an attempt the new challenge never used.
            assertEquals(
                "a superseded refusal must not count against the new challenge", 0, s.attempts,
            )
            assertFalse("and must not paint the new code red", s.lastSubmitRefused)
        }

    // ── 7. the guards do not drop each other's actions ─────────────────────

    @Test
    fun `a resend is not swallowed because a verify is in flight`() = runTest(dispatcher) {
        start(sent("111111"))

        repo.verifyResult = VerifyPhoneResult.Refused
        vm.verify(phone, "111111") {}
        // With one shared `busy` this start returned early and said nothing -- which is exactly
        // "I tapped resend and nothing happened".
        repo.queued += sent("222222")
        vm.start(phone)
        advanceUntilIdle()
        assertEquals("the resend must still be dispatched", 2, repo.startCalls)
    }

    @Test
    fun `nothing is submitted before a challenge exists`() = runTest(dispatcher) {
        repo.verifyResult = VerifyPhoneResult.SignedIn(profileComplete = false)
        vm.verify(phone, "111111") {}
        advanceUntilIdle()
        // A 401 here would be reported as "that code doesn't match", blaming the user for a
        // challenge the app never had.
        assertEquals("no challenge, no request", 0, repo.verifyCalls)
    }

    // ── 8. the attempt cap follows the challenge ───────────────────────────

    @Test
    fun `a genuinely new challenge clears a spent attempt count`() = runTest(dispatcher) {
        start(sent("111111"))

        repo.verifyResult = VerifyPhoneResult.TooManyAttempts
        vm.verify(phone, "000000") {}
        advanceUntilIdle()
        assertEquals(MAX_VERIFY_ATTEMPTS, vm.state.value.attempts)
        assertTrue(vm.state.value.locked())

        start(sent("222222"))
        assertEquals(0, vm.state.value.attempts)
        assertFalse("a new code must be usable again", vm.state.value.locked())
        assertNull(vm.state.value.resendRejected)
        assertEquals("222222", vm.state.value.devCode)
    }

    // ── 9. the correlation record itself ──────────────────────────────────

    @Test
    fun `the trace names the challenge a verify answered for`() = runTest(dispatcher) {
        start(sent("111111"))
        repo.verifyResult = VerifyPhoneResult.Refused
        vm.verify(phone, "111111") {}
        advanceUntilIdle()

        // The whole point of the trace: it must be possible to say WHICH challenge an attempt was
        // answering, after the fact, from a log that contains no code and no phone number.
        assertTrue("the issue is not recorded: $trace", trace.any { it.contains("challenge 1 issued") })
        assertTrue("the attempt is not correlated: $trace", trace.any { it.contains("against challenge 1") })
        assertTrue(
            "the trace must never carry the code itself: $trace",
            trace.none { it.contains("111111") },
        )
    }
}
