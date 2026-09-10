/*
 * BasicsRepositoryTest.kt
 * ShowUp · what the backend's answers mean, asserted against real bytes
 *
 * The mapping this covers is the awkward part of SHOWUP-153: `/auth/email/verify` answers 401 for
 * a wrong code, an expired one, a superseded one and a missing challenge, and 401 again with a
 * different sentence once the attempt cap is reached. One status, five causes, three different
 * things for the screen to say. Getting that wrong is not a crash -- it is a user being told to
 * check their inbox for a code that can no longer work.
 *
 * Every assertion here is made against bytes that crossed a socket, served in-process. No backend,
 * no network, no emulator.
 */
package com.showup.profile

import com.showup.api.InMemoryTokenStore
import com.showup.api.ShowUpApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BasicsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: BasicsRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        repo = BasicsRepository(
            ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t")),
        )
    }

    @After
    fun stop() = server.shutdown()

    private fun respond(code: Int, body: String = "") = server.enqueue(
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body),
    )

    private val challenge = """
        {"expiresAt":"2026-09-10T10:20:30Z","resendAvailableAt":"2026-09-10T10:16:00Z"}
    """.trimIndent()

    private fun apiError(status: Int, message: String, error: String = "Unauthorized") =
        """{"statusCode":$status,"message":"$message","error":"$error"}"""

    // ── sending ───────────────────────────────────────────────────────────────

    @Test
    fun `a sent code carries both of the server's timestamps`() = runTest {
        respond(200, challenge)
        val result = repo.sendCode("leo@hey.com")
        assertTrue("expected Sent, got $result", result is SendCodeResult.Sent)
        result as SendCodeResult.Sent
        // Both are used: expiresAt decides "expired", resendAvailableAt drives the countdown.
        // This is why the client no longer holds a cooldown constant of its own.
        assertEquals(2026, result.expiresAt.year)
        assertEquals(16, result.resendAvailableAt.minute)
    }

    @Test
    fun `the request carries the address and hits the contract path`() = runTest {
        respond(200, challenge)
        repo.sendCode("leo@hey.com")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/auth/email/start", sent.path)
        assertTrue(sent.body.readUtf8().contains("leo@hey.com"))
    }

    @Test
    fun `429 is the server's cooldown, not a failure`() = runTest {
        // The client's countdown thought a resend was allowed and the server disagreed. The
        // server is the one that can refuse, so this is a distinct outcome rather than an error.
        respond(429, apiError(429, "Please wait 22s before requesting another code", "Too Many Requests"))
        assertEquals(SendCodeResult.TooSoon, repo.sendCode("leo@hey.com"))
    }

    @Test
    fun `400 on send is the address already belonging to someone else`() = runTest {
        respond(400, apiError(400, "That email is already in use", "Bad Request"))
        assertEquals(SendCodeResult.EmailInUse, repo.sendCode("leo@hey.com"))
    }

    @Test
    fun `a server error is reported as a failure, not as a wrong address`() = runTest {
        respond(500, apiError(500, "Unexpected server error", "Internal Server Error"))
        assertTrue(repo.sendCode("leo@hey.com") is SendCodeResult.Failed)
    }

    @Test
    fun `no network degrades instead of throwing`() = runTest {
        // A screen that crashes on a lost connection is worse than one that says try again.
        server.shutdown()
        assertTrue(repo.sendCode("leo@hey.com") is SendCodeResult.Failed)
    }

    // ── verifying: one status, three meanings ────────────────────────────────

    @Test
    fun `204 is a verified code`() = runTest {
        respond(204)
        assertEquals(VerifyCodeResult.Verified, repo.verifyCode("482170"))
    }

    @Test
    fun `the ordinary 401 is a refusal`() = runTest {
        respond(401, apiError(401, "Invalid or expired code"))
        assertEquals(VerifyCodeResult.Refused, repo.verifyCode("482170"))
    }

    @Test
    fun `the cap is told apart by its sentence`() = runTest {
        // The fragile one, and the reason it is written down: this is English prose, not a code.
        respond(401, apiError(401, "Too many attempts; request a new code"))
        assertEquals(VerifyCodeResult.TooManyAttempts, repo.verifyCode("482170"))
    }

    @Test
    fun `an unrecognised 401 fails safe as a refusal`() = runTest {
        // If the server ever rewords the cap message, the user gets "that didn't match" and one
        // more refused attempt -- annoying. The opposite default would lock them out of a code
        // that still works, which is worse.
        respond(401, apiError(401, "Some wording nobody has written yet"))
        assertEquals(VerifyCodeResult.Refused, repo.verifyCode("482170"))
    }

    @Test
    fun `a 500 on verify is not mistaken for a wrong code`() = runTest {
        respond(500, apiError(500, "Unexpected server error", "Internal Server Error"))
        assertTrue(repo.verifyCode("482170") is VerifyCodeResult.Failed)
    }

    // ── writing the profile ──────────────────────────────────────────────────

    @Test
    fun `a saved profile returns the server's own age`() = runTest {
        respond(200, profileJson(age = 28, hidden = "[]"))
        assertEquals(SaveBasicsResult.Saved(28), repo.saveDateOfBirth("1998-03-22", hideAge = false))
    }

    @Test
    fun `400 on the profile write is the server's own 18 check`() = runTest {
        respond(400, apiError(400, "You must be at least 18 years old", "Bad Request"))
        assertEquals(SaveBasicsResult.UnderAge, repo.saveDateOfBirth("2015-03-22", hideAge = false))
    }

    @Test
    fun `hiding the age writes hidden_fields and NEVER isVisible`() = runTest {
        // The one mistake on this screen with real consequences: isVisible means "appears in
        // discovery at all", so writing it here would remove the user from matching -- the exact
        // outcome the product decision forbids.
        respond(200, profileJson(age = 28, hidden = """["age"]"""))
        repo.saveDateOfBirth("1998-03-22", hideAge = true)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("body should carry the hidden set, was: $body", body.contains("hiddenFields"))
        assertTrue("body should name age, was: $body", body.contains("age"))
        assertFalse("isVisible must never appear, was: $body", body.contains("isVisible"))
    }

    @Test
    fun `not hiding the age writes an explicit empty set`() = runTest {
        // An omitted key means "leave it alone" to the server, so unticking has to send the empty
        // set explicitly or the choice never clears.
        respond(200, profileJson(age = 28, hidden = "[]"))
        repo.saveDateOfBirth("1998-03-22", hideAge = false)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("empty set must be sent, was: $body", body.contains("hiddenFields"))
        assertFalse("isVisible must never appear, was: $body", body.contains("isVisible"))
    }

    @Test
    fun `the date is sent in the format the contract documents`() = runTest {
        respond(200, profileJson(age = 28, hidden = "[]"))
        repo.saveDateOfBirth("1998-03-22", hideAge = false)
        // UpsertProfileDto.dateOfBirth is "YYYY-MM-DD". Sending the display string would 400.
        assertTrue(server.takeRequest().body.readUtf8().contains("1998-03-22"))
    }

    @Test
    fun `one PATCH carries the date and the visibility choice together`() = runTest {
        // Two calls would let the date land while the visibility choice failed, leaving an age
        // displayed that the user asked to hide.
        respond(200, profileJson(age = 28, hidden = """["age"]"""))
        repo.saveDateOfBirth("1998-03-22", hideAge = true)

        val sent = server.takeRequest()
        assertEquals("PATCH", sent.method)
        assertEquals("/me/profile", sent.path)
        assertEquals(0, server.requestCount - 1)
    }

    private fun profileJson(age: Int, hidden: String) = """
        {"id":"p1","displayName":"Leo","age":$age,"gender":null,"lookingFor":null,
         "isVisible":true,"hiddenFields":$hidden,"isComplete":false,
         "verificationStatus":"none"}
    """.trimIndent()
}
