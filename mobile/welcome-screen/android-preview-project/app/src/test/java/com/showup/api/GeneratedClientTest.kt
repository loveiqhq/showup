package com.showup.api

import com.showup.api.generated.api.AuthApi
import com.showup.api.generated.infrastructure.ApiClient
import com.showup.api.generated.model.RequestOtpDto
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the generated client works: it compiles, it can be built, and a real request goes out over
 * HTTP carrying the shape the backend contract describes.
 *
 * WHY THIS IS NOT JUST A COMPILE CHECK
 *
 * "It compiles" was, more than once on this project, the whole basis for calling something done --
 * and eight Swift tests once sat in no target while being counted as passing. A generated client
 * that compiles can still send the wrong path, drop the body, or fail to decode the response. So
 * every assertion here is made against bytes that actually crossed a socket, served by a
 * MockWebServer in-process. No backend, no network, no emulator.
 *
 * Every payload below is the real shape from openapi.json. `OtpChallengeResponseDto` is
 * `{ expiresAt, resendAvailableAt, devCode? }` -- not anything more convenient.
 */
class GeneratedClientTest {
    private lateinit var server: MockWebServer

    /** The real response shape for POST /auth/phone/start, as the contract declares it. */
    private val otpChallenge = """
        {"expiresAt":"2026-09-04T10:15:30Z","resendAvailableAt":"2026-09-04T10:16:00Z"}
    """.trimIndent()

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun api(tokens: TokenStore = InMemoryTokenStore()): AuthApi =
        ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)),
        ).createService(AuthApi::class.java)

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `the generated client can be instantiated against the development base URL`() {
        // The development URL exactly as BuildConfig carries it, rather than the mock server's.
        // Retrofit rejects a base URL without a trailing slash at construction time -- a mistake
        // that otherwise surfaces much later as a confusing 404.
        val client = ApiClient(baseUrl = "http://10.0.2.2:3000/")
        assertNotNull(client.createService(AuthApi::class.java))
    }

    @Test
    fun `startPhoneVerification sends the path and body the contract describes`() = runTest {
        server.enqueue(json(otpChallenge))

        val response = api().startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        assertTrue("call should succeed, got ${response.code()}", response.isSuccessful)

        val sent = server.takeRequest()
        // readUtf8() drains the buffer, so it is read once and held. Reading it twice returns an
        // empty string the second time, which silently passes a naive assertion.
        val body = sent.body.readUtf8()

        assertEquals("POST", sent.method)
        assertEquals("/auth/phone/start", sent.path)
        assertTrue("body should carry the phone number, was: $body", body.contains("+4917612345678"))
    }

    @Test
    fun `the response decodes into the generated model, dates included`() = runTest {
        server.enqueue(json(otpChallenge))

        val body = api().startPhoneVerification(RequestOtpDto(phone = "+4917612345678")).body()

        // Decoding is what a compile check cannot reach: kotlinx.serialization fails at runtime,
        // and only when a real payload disagrees with the generated model. The two OffsetDateTime
        // fields go through a contextual adapter, which is the part most likely to be missing.
        assertNotNull("response should decode into the generated model", body)
        assertEquals(2026, body!!.expiresAt.year)
        assertNull("devCode is absent in production payloads", body.devCode)
    }

    @Test
    fun `no Authorization header is sent when nobody is signed in`() = runTest {
        server.enqueue(json(otpChallenge))

        api(InMemoryTokenStore()).startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))

        // A public endpoint called with no session must not carry an empty or literal-null bearer.
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `the interceptor attaches the bearer without any generated method mentioning it`() = runTest {
        server.enqueue(json(otpChallenge))

        val tokens = InMemoryTokenStore(access = "token-abc", refresh = "refresh-xyz")
        api(tokens).startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))

        // The whole point of the interceptor: the call above passed no token, and the generated
        // interface has no parameter for one.
        assertEquals("Bearer token-abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a validation error body parses into every constraint message`() {
        // The 400 shape the global ValidationPipe produces: `message` is an ARRAY. This is the
        // case the generated ApiErrorDto cannot decode, which is why ApiError is hand-written.
        val error = ApiError.parse(
            400,
            """{"statusCode":400,"message":["phone must be a valid number","phone should not be empty"],"error":"Bad Request"}""",
        )

        assertEquals(400, error.statusCode)
        assertEquals("Bad Request", error.error)
        assertEquals(2, error.messages.size)
        assertTrue(error.messages[0].contains("valid number"))
    }

    @Test
    fun `a single-string error body parses too`() {
        val error = ApiError.parse(
            403,
            """{"statusCode":403,"message":"Please re-verify your identity to continue.","error":"step_up_required"}""",
        )

        assertEquals("step_up_required", error.error)
        assertEquals(1, error.messages.size)
    }

    @Test
    fun `a malformed error body degrades instead of throwing`() {
        // A parse failure here would replace a useful server error with a parse error, which is
        // strictly less information than we started with.
        val error = ApiError.parse(500, "<html>gateway timeout</html>")

        assertEquals(500, error.statusCode)
        assertTrue(error.messages.isNotEmpty())
    }
}
