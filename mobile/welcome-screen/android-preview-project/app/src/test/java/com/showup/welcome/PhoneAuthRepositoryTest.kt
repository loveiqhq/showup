/*
 * PhoneAuthRepositoryTest.kt
 * ShowUp · signing in for real, asserted against bytes that crossed a socket
 *
 * This covers the two requests that used to be a constant. `DevAuth.TEST_CODE` could not fail, so
 * there was nothing to test; `/auth/phone/verify` can fail five ways, and one of its successes has
 * a side effect -- it writes credentials -- that nothing on screen would reveal if it were
 * skipped. Every later request in the app depends on that write having happened.
 *
 * The strongest assertion here is `the profile read carries the token that was just saved`. It is
 * the only place the whole chain is proven end to end: a verify response, into the token store,
 * back out through the interceptor, onto the next request.
 */
package com.showup.welcome

import com.showup.api.InMemoryTokenStore
import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.ShowUpApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhoneAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: InMemoryTokenStore
    private lateinit var repo: PhoneAuthRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        // Deliberately EMPTY. A pre-seeded store would let the profile read pass on a token this
        // flow never produced, which is the bug the last test in this file exists to catch.
        tokens = InMemoryTokenStore()
        // `offline = null`, so this suite measures what a RELEASE build does. The debug
        // stand-in has its own tests below; mixing the two would mean neither was checked.
        repo = PhoneAuthRepository(
            ShowUpApi(baseUrl = server.url("/").toString(), tokens = tokens),
            tokens,
            offline = null,
        )
    }

    @After
    fun stop() = server.shutdown()

    private fun respond(code: Int, body: String = "") = server.enqueue(
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body),
    )

    private fun apiError(status: Int, message: String, error: String = "Unauthorized") =
        """{"statusCode":$status,"message":"$message","error":"$error"}"""

    private val challenge =
        """{"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z",""" +
            """"devCode":"123456"}"""

    private fun authResponse(access: String = "acc", refresh: String = "ref") =
        """{"accessToken":"$access","refreshToken":"$refresh","tokenType":"Bearer",""" +
            """"expiresIn":900,"user":{"id":"u1","phone":"+4917612345678",""" +
            """"email":"leo@hey.com","displayName":"Leo","status":"registered",""" +
            """"phoneVerified":true,"emailVerified":false,"createdAt":"2026-09-10T10:00:00Z"}}"""

    private fun profile(isComplete: Boolean) =
        """{"id":"p1","displayName":"Leo","age":31,"gender":null,"lookingFor":null,""" +
            """"isVisible":true,"hiddenFields":[],"isComplete":$isComplete,""" +
            """"verificationStatus":"none"}"""

    // -- sending --------------------------------------------------------------

    @Test
    fun `a sent code carries both timestamps and the dev code`() = runTest {
        respond(200, challenge)
        val result = repo.start("+4917612345678")
        assertTrue("expected Sent, got $result", result is StartAuthResult.Sent)
        result as StartAuthResult.Sent
        // resendAvailableAt is what the countdown counts to. The client used to hold its own
        // constant and the two could disagree; now there is only the server's number.
        assertEquals(16, result.resendAvailableAt.minute)
        assertEquals(30, result.expiresAt.second)
        // Present only because AUTH_EXPOSE_OTP is on outside production. It is what makes the
        // whole flow testable against LogSmsSender with no paid provider.
        assertEquals("123456", result.devCode)
    }

    @Test
    fun `a server that does not expose the code still sends one`() = runTest {
        respond(
            200,
            """{"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z"}""",
        )
        val result = repo.start("+4917612345678")
        assertTrue("expected Sent, got $result", result is StartAuthResult.Sent)
        // Production behaviour: the challenge is valid, there is simply nothing to show a tester.
        assertNull((result as StartAuthResult.Sent).devCode)
    }

    @Test
    fun `the request hits the contract path with the number in E164`() = runTest {
        respond(200, challenge)
        repo.start("+4917612345678")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/auth/phone/start", sent.path)
        assertTrue(sent.body.readUtf8().contains("+4917612345678"))
    }

    @Test
    fun `429 is the server's cooldown, not a failure`() = runTest {
        respond(
            429,
            apiError(429, "Please wait 41s before requesting another code", "Too Many Requests"),
        )
        // The distinction is the point: TooSoon leaves the countdown running, Failed would put an
        // error card on a screen where nothing is actually wrong.
        assertEquals(StartAuthResult.TooSoon, repo.start("+4917612345678"))
    }

    // -- verifying ------------------------------------------------------------

    @Test
    fun `a correct code stores both tokens`() = runTest {
        respond(200, authResponse())
        respond(200, profile(isComplete = false))
        repo.verify("+4917612345678", "123456")
        // Both, not only the access token: the refresh token is what survives the access token
        // expiring, and without it the user is signed out fifteen minutes later.
        assertEquals("acc", tokens.accessToken())
        assertEquals("ref", tokens.refreshToken())
    }

    @Test
    fun `the profile read carries the token that was just saved`() = runTest {
        respond(200, authResponse(access = "fresh-token"))
        respond(200, profile(isComplete = true))
        repo.verify("+4917612345678", "123456")
        server.takeRequest() // the verify itself
        val profileRequest = server.takeRequest()
        assertEquals("/me/profile", profileRequest.path)
        // The point of the whole change. The interceptor reads the store per request, so a token
        // written mid-flow is attached to the very next call with nothing being rebuilt.
        assertEquals("Bearer fresh-token", profileRequest.getHeader("Authorization"))
    }

    @Test
    fun `the verify request identifies the device`() = runTest {
        respond(200, authResponse())
        respond(200, profile(isComplete = false))
        repo.verify("+4917612345678", "123456")
        val sent = server.takeRequest()
        assertEquals("/auth/phone/verify", sent.path)
        // Recorded against the session so a person can see where they are signed in. One shared
        // constant, so this row and the one a refresh creates name the same device.
        assertEquals(ShowUpApi.USER_AGENT, sent.getHeader("User-Agent"))
    }

    @Test
    fun `a complete profile is reported as complete`() = runTest {
        respond(200, authResponse())
        respond(200, profile(isComplete = true))
        assertEquals(
            VerifyPhoneResult.SignedIn(profileComplete = true),
            repo.verify("+4917612345678", "123456"),
        )
    }

    @Test
    fun `a profile that cannot be read counts as incomplete, and the user stays signed in`() =
        runTest {
            respond(200, authResponse())
            respond(500, apiError(500, "Internal server error", "Internal Server Error"))
            val result = repo.verify("+4917612345678", "123456")
            // Fails towards onboarding on purpose. Repeating a step is an annoyance; skipping
            // profile creation because one request timed out leaves an account nobody can match.
            assertEquals(VerifyPhoneResult.SignedIn(profileComplete = false), result)
            // The sign-in itself still happened -- the tokens are real regardless.
            assertEquals("acc", tokens.accessToken())
        }

    @Test
    fun `a wrong code is refused and stores nothing`() = runTest {
        respond(401, apiError(401, "Invalid or expired code"))
        assertEquals(VerifyPhoneResult.Refused, repo.verify("+4917612345678", "000000"))
        assertNull(tokens.accessToken())
        assertNull(tokens.refreshToken())
    }

    @Test
    fun `the fifth wrong code is the cap, not another mismatch`() = runTest {
        respond(401, apiError(401, "Too many attempts. Please request a new code."))
        // Same status, different sentence. The screen says something different for each: one asks
        // the user to check the digits, the other tells them to request a new code.
        assertEquals(VerifyPhoneResult.TooManyAttempts, repo.verify("+4917612345678", "000000"))
    }

    @Test
    fun `the cap is recognised regardless of the sentence's casing`() = runTest {
        respond(401, apiError(401, "too many attempts for this challenge"))
        assertEquals(VerifyPhoneResult.TooManyAttempts, repo.verify("+4917612345678", "000000"))
    }

    @Test
    fun `an unreachable server is a failure, not a refusal`() = runTest {
        server.shutdown()
        val result = repo.verify("+4917612345678", "123456")
        // Refused would tell the user their code was wrong when it may have been perfect.
        assertTrue("expected Failed, got $result", result is VerifyPhoneResult.Failed)
        assertNotEquals(VerifyPhoneResult.Refused, result)
    }

    // ── the debug stand-in ────────────────────────────────────────────────────
    //
    // Same repository, with the offline fallback wired in as a debug build has it. What these
    // check is the boundary: it takes over when nothing answered, and never when something did.

    private fun offlineRepo() = PhoneAuthRepository(
        ShowUpApi(baseUrl = server.url("/").toString(), tokens = tokens),
        tokens,
        offline = DevOfflineAuth,
    )

    @Test
    fun `with no server at all, a debug build issues its own code`() = runTest {
        DevOfflineAuth.reset()
        server.shutdown()
        val result = offlineRepo().start("+4917612345678")
        assertTrue("expected Sent, got $result", result is StartAuthResult.Sent)
        result as StartAuthResult.Sent
        // Six digits, and flagged as offline so the screen can say so. A stand-in nobody can
        // tell apart from a backend is how a broken integration gets demoed as working.
        assertEquals(6, result.devCode?.length)
        assertTrue("the code must be shown as offline", result.offline)
    }

    @Test
    fun `the offline code is the one the offline verify accepts`() = runTest {
        DevOfflineAuth.reset()
        server.shutdown()
        val repo = offlineRepo()
        val sent = repo.start("+4917612345678") as StartAuthResult.Sent
        assertEquals(
            VerifyPhoneResult.SignedIn(profileComplete = false),
            repo.verify("+4917612345678", sent.devCode!!),
        )
    }

    @Test
    fun `the offline stand-in still refuses a wrong code`() = runTest {
        DevOfflineAuth.reset()
        server.shutdown()
        val repo = offlineRepo()
        val sent = repo.start("+4917612345678") as StartAuthResult.Sent
        val wrong = if (sent.devCode == "000000") "111111" else "000000"
        // It models the failures too. A stand-in that only ever succeeds would leave the
        // mismatch card and the lockout card to rot unseen, which is most of what anyone
        // walking this flow needs to look at.
        assertEquals(VerifyPhoneResult.Refused, repo.verify("+4917612345678", wrong))
    }

    @Test
    fun `the offline stand-in enforces the same five-attempt cap`() = runTest {
        DevOfflineAuth.reset()
        server.shutdown()
        val repo = offlineRepo()
        val sent = repo.start("+4917612345678") as StartAuthResult.Sent
        val wrong = if (sent.devCode == "000000") "111111" else "000000"
        repeat(MAX_VERIFY_ATTEMPTS - 1) { repo.verify("+4917612345678", wrong) }
        assertEquals(
            VerifyPhoneResult.TooManyAttempts,
            repo.verify("+4917612345678", wrong),
        )
    }

    @Test
    fun `a server that answers is never replaced by the stand-in`() = runTest {
        DevOfflineAuth.reset()
        // The boundary, and the whole reason this is safe. A 500 is a server WORKING, and its
        // answer must reach the app untouched -- otherwise a backend bug would present as a
        // cheerful offline session and nobody would ever find it.
        respond(500, apiError(500, "Internal server error", "Internal Server Error"))
        val result = offlineRepo().start("+4917612345678")
        assertTrue("expected Failed, got $result", result is StartAuthResult.Failed)
    }
}
