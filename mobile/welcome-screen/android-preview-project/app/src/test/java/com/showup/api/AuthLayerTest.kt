package com.showup.api

import com.showup.api.generated.api.AuthApi
import com.showup.api.generated.infrastructure.ApiClient
import com.showup.api.generated.model.RequestOtpDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the auth layer against a real HTTP server: injection, refresh, rotation, logout, and --
 * the one that matters most -- single-flight.
 *
 * WHY SINGLE-FLIGHT NEEDS A TEST AND NOT A CODE REVIEW
 *
 * It is a concurrency bug. Clicking through the app by hand fires one request at a time, so a
 * missing lock behaves perfectly right up until a screen loads two things at once against an
 * expired token. Then the backend rotates the refresh token on the first call, the second presents
 * the now-invalid one, and the user is signed out for no reason they can see.
 *
 * The assertion below is a count of requests that reached the refresh endpoint. Ten concurrent
 * 401s, one refresh.
 */
class AuthLayerTest {
    private lateinit var server: MockWebServer

    private val refreshHits = AtomicInteger(0)
    private val protectedHits = AtomicInteger(0)

    /** How many times the app was told the session is over. */
    private val sessionEndings = AtomicInteger(0)

    /**
     * A complete AuthResponseDto.
     *
     * "Complete" is doing work here. UserDto requires EIGHT fields and my first version of this
     * fixture carried three, so kotlinx.serialization failed to decode, the refresh returned null,
     * and every test failed with an assertion about missing tokens rather than about decoding. A
     * partial fixture does not produce a partial result; it produces a misleading one.
     */
    private val authResponse = """
        {"accessToken":"new-access","refreshToken":"new-refresh","tokenType":"Bearer","expiresIn":900,
         "user":{"id":"u1","phone":"+4917612345678","email":null,"displayName":null,
                 "status":"active","phoneVerified":true,"emailVerified":false,
                 "createdAt":"2026-09-04T10:00:00Z"}}
    """.trimIndent().replace("\n", "").replace("  ", "")

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    /** A store whose access token is stale, so the first protected call comes back 401. */
    private fun staleStore() = InMemoryTokenStore(access = "stale-access", refresh = "good-refresh")

    private fun bareApi(): AuthApi =
        ApiClient(baseUrl = server.url("/").toString()).createService(AuthApi::class.java)

    /** The full stack: interceptor plus authenticator, exactly as ShowUpApi assembles it. */
    private fun authedApi(tokens: TokenStore, refresher: TokenRefresher): AuthApi =
        ApiClient(
            baseUrl = server.url("/").toString(),
            okHttpClientBuilder = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokens))
                .authenticator(TokenAuthenticator(refresher)),
        ).createService(AuthApi::class.java)

    /** The app's own refresh mapping -- not a copy of it -- so these tests cover what ships. */
    private fun refresherFor(tokens: TokenStore) =
        TokenRefresher(tokens, onSessionEnded = { sessionEndings.incrementAndGet() }) { refreshToken ->
            ShowUpApi.exchangeRefresh(bareApi(), refreshToken)
        }

    /** The refresh endpoint answers [refresh]; every protected call is a 401. */
    private fun refreshAnswers(refresh: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path?.endsWith("/auth/refresh") == true -> {
                    refreshHits.incrementAndGet()
                    refresh
                }
                else -> MockResponse().setResponseCode(401)
                    .setBody("""{"statusCode":401,"message":"Unauthorized","error":"Unauthorized"}""")
            }
        }
    }

    /**
     * Serves 401 to any request carrying the stale token, 200 to one carrying the new token, and a
     * fresh token pair from the refresh endpoint. Counting happens here so the assertions are about
     * what actually reached the server.
     */
    private fun installDispatcher() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.endsWith("/auth/refresh") -> {
                        refreshHits.incrementAndGet()
                        MockResponse().setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(authResponse)
                    }
                    else -> {
                        protectedHits.incrementAndGet()
                        val auth = request.getHeader("Authorization")
                        if (auth == "Bearer new-access") {
                            MockResponse().setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"expiresAt":"2026-09-04T10:15:30Z","resendAvailableAt":"2026-09-04T10:16:00Z"}""")
                        } else {
                            MockResponse().setResponseCode(401)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"statusCode":401,"message":"Unauthorized","error":"Unauthorized"}""")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a 401 is retried once with a refreshed token`() = runTest {
        installDispatcher()
        val tokens = staleStore()

        val response = withContext(Dispatchers.IO) {
            authedApi(tokens, refresherFor(tokens))
                .startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        }

        assertTrue("the retried call should succeed, got ${response.code()}", response.isSuccessful)
        assertEquals("exactly one refresh", 1, refreshHits.get())
        // The original 401 and the successful retry.
        assertEquals("the protected call was attempted twice", 2, protectedHits.get())
    }

    @Test
    fun `a successful refresh stores BOTH new tokens, because the server rotates them`() = runTest {
        installDispatcher()
        val tokens = staleStore()

        withContext(Dispatchers.IO) {
            authedApi(tokens, refresherFor(tokens))
                .startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        }

        assertEquals("new-access", tokens.accessToken())
        // The part that is easy to miss: keeping the old refresh token would leave the app holding
        // one the server has already invalidated, and the NEXT refresh would sign the user out.
        assertEquals("new-refresh", tokens.refreshToken())
    }

    @Test
    fun `ten concurrent 401s produce exactly one refresh`() = runTest {
        installDispatcher()
        val tokens = staleStore()
        // One refresher shared by every request, which is how ShowUpApi wires it. A refresher per
        // request would defeat the whole mechanism, so this mirrors production deliberately.
        val api = authedApi(tokens, refresherFor(tokens))

        val results = withContext(Dispatchers.IO) {
            (1..10).map { async { api.startPhoneVerification(RequestOtpDto(phone = "+491761234567$it")) } }
                .awaitAll()
        }

        assertTrue("every call should end up succeeding", results.all { it.isSuccessful })
        assertEquals("ten concurrent 401s, ONE refresh", 1, refreshHits.get())
    }

    @Test
    fun `a refused refresh ends the session - tokens cleared and the app told once`() = runTest {
        // Refresh rejected: the token is spent, expired, revoked, or the account is gone.
        refreshAnswers(
            MockResponse().setResponseCode(401)
                .setBody("""{"statusCode":401,"message":"Invalid","error":"Unauthorized"}"""),
        )
        val tokens = staleStore()

        val response = withContext(Dispatchers.IO) {
            authedApi(tokens, refresherFor(tokens))
                .startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        }

        assertEquals("the caller sees the original 401", 401, response.code())
        // Keeping a dead token only produces a second confusing failure on the next request.
        assertNull("access token cleared", tokens.accessToken())
        assertNull("refresh token cleared", tokens.refreshToken())
        // THE BUG THIS FIXES: the tokens went and nobody was told, so every save after that
        // failed with "We couldn't save that just now" and no way back to sign-in.
        assertEquals("the app is told the session is over, once", 1, sessionEndings.get())
    }

    @Test
    fun `ten concurrent 401s against a refused session tell the app once`() = runTest {
        refreshAnswers(MockResponse().setResponseCode(401).setBody("""{"statusCode":401}"""))
        val tokens = staleStore()
        val api = authedApi(tokens, refresherFor(tokens))

        withContext(Dispatchers.IO) {
            (1..10).map {
                async { api.startPhoneVerification(RequestOtpDto(phone = "+4917612345678")) }
            }.awaitAll()
        }

        assertEquals("one refresh", 1, refreshHits.get())
        assertEquals("one notice, not ten", 1, sessionEndings.get())
    }

    @Test
    fun `a server error during refresh keeps the user signed in`() = runTest {
        // The server is down or restarting. That says nothing about the session.
        refreshAnswers(MockResponse().setResponseCode(503))
        val tokens = staleStore()

        val response = withContext(Dispatchers.IO) {
            authedApi(tokens, refresherFor(tokens))
                .startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        }

        assertEquals("the request in hand still fails", 401, response.code())
        assertEquals("tokens kept", "good-refresh", tokens.refreshToken())
        assertEquals("not told the session ended", 0, sessionEndings.get())
    }

    @Test
    fun `no connection during refresh keeps the user signed in`() = runTest {
        // The refresh call gets no answer at all -- a train in a tunnel.
        refreshAnswers(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val tokens = staleStore()

        val result = withContext(Dispatchers.IO) { refresherFor(tokens).refresh("stale-access") }

        assertNull("no token to give", result)
        assertEquals("access token kept", "stale-access", tokens.accessToken())
        assertEquals("refresh token kept", "good-refresh", tokens.refreshToken())
        assertEquals("not told the session ended", 0, sessionEndings.get())
    }

    @Test
    fun `a rate-limited or timed-out refresh is not a refusal`() = runTest {
        for (code in listOf(408, 429)) {
            refreshAnswers(MockResponse().setResponseCode(code))
            val tokens = staleStore()

            withContext(Dispatchers.IO) { refresherFor(tokens).refresh("stale-access") }

            assertEquals("$code keeps the tokens", "good-refresh", tokens.refreshToken())
        }
        assertEquals(0, sessionEndings.get())
    }

    @Test
    fun `the refusal line - every 4xx but 408 and 429 refuses, nothing else does`() {
        val refused = TokenRefresher.Outcome.Refused
        val unreachable = TokenRefresher.Outcome.Unreachable
        for (code in listOf(400, 401, 403, 404, 410)) {
            assertEquals("$code", refused, TokenRefresher.Outcome.ofFailedResponse(code))
        }
        // 2xx here means a success that came without a body: a broken answer, not a verdict.
        for (code in listOf(200, 408, 429, 500, 502, 503)) {
            assertEquals("$code", unreachable, TokenRefresher.Outcome.ofFailedResponse(code))
        }
    }

    @Test
    fun `a 401 with no refresh token does not attempt a refresh`() = runTest {
        installDispatcher()
        // A signed-out user hitting a protected endpoint. Not an error state worth a network call.
        val tokens = InMemoryTokenStore(access = "stale-access", refresh = null)

        withContext(Dispatchers.IO) {
            authedApi(tokens, refresherFor(tokens))
                .startPhoneVerification(RequestOtpDto(phone = "+4917612345678"))
        }

        assertEquals("no refresh should be attempted", 0, refreshHits.get())
    }

    @Test
    fun `a caller whose token was already refreshed reuses it without a second call`() = runTest {
        installDispatcher()
        val tokens = staleStore()
        val refresher = refresherFor(tokens)

        // First caller refreshes.
        withContext(Dispatchers.IO) { refresher.refresh("stale-access") }
        assertEquals(1, refreshHits.get())

        // Second caller presents the SAME stale token it used. The stored token has moved on, so
        // this must be recognised as already-handled rather than refreshed again.
        val reused = withContext(Dispatchers.IO) { refresher.refresh("stale-access") }

        assertEquals("new-access", reused)
        assertEquals("still exactly one refresh", 1, refreshHits.get())
    }

    @Test
    fun `refresh returns null and clears when there is no refresh token at all`() = runTest {
        installDispatcher()
        val tokens = InMemoryTokenStore(access = "stale-access", refresh = null)

        val result = withContext(Dispatchers.IO) { refresherFor(tokens).refresh("stale-access") }

        assertNull(result)
        assertEquals(0, refreshHits.get())
        // Someone who never signed in has no session to end, so no "log in again".
        assertEquals(0, sessionEndings.get())
    }

    @Test
    fun `the error body from a 401 is parseable by the hand-written parser`() {
        val error = ApiError.parse(401, """{"statusCode":401,"message":"Unauthorized","error":"Unauthorized"}""")
        assertNotNull(error)
        assertEquals(401, error.statusCode)
        assertEquals(listOf("Unauthorized"), error.messages)
    }
}
