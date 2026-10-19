/*
 * PushTokenRemovalWireTest.kt
 * ShowUp · removing the push token, on the wire (epic SHOWUP-151: "register or invalidate the token")
 *
 * The permission reconciler removes the token when notifications are turned off outside the app.
 * These prove the request is the generated client's DELETE with the token in its body, and that each
 * outcome is reported as what it was -- never a removal that did not happen.
 */
package com.showup.api

import com.showup.profile.NoPushToken
import com.showup.profile.PushRegistration
import com.showup.profile.PushRegistrationResult
import com.showup.profile.PushTokenSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushTokenRemovalWireTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api() = ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t"))

    private val source = object : PushTokenSource {
        override suspend fun token(): String = "device-token-1"
    }

    @Test
    fun `removal is a DELETE of the token, reported as removed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = PushRegistration(api(), source).unregister()
        assertEquals(PushRegistrationResult.Removed, result)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/me/push-tokens", request.path)
        assertTrue(request.body.readUtf8().contains("\"token\":\"device-token-1\""))
    }

    @Test
    fun `a refused removal is a failure, not a removal`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = PushRegistration(api(), source).unregister()
        assertEquals(PushRegistrationResult.Failed("http 500"), result)
    }

    @Test
    fun `with no token source nothing is sent and it says so`() = runBlocking {
        val result = PushRegistration(api(), NoPushToken).unregister()
        assertEquals(PushRegistrationResult.NoTokenSource, result)
        assertEquals(0, server.requestCount)
    }
}
