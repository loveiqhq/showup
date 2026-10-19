/*
 * PermissionStatusWireTest.kt
 * ShowUp · the phone's permission statuses, on the wire (epic SHOWUP-151: "update the server")
 *
 * The reconciler sends every watched status to `PUT /me/permissions`. These prove the request is the
 * generated client's, with the values the server's enums accept, that a refusal or an unreachable
 * server is reported as not taken -- so the next start tries again -- and that a signed-out phone
 * sends nothing at all.
 */
package com.showup.api

import com.showup.profile.ApiPermissionStatusSync
import com.showup.profile.PermissionStatus
import com.showup.profile.WatchedPermission
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionStatusWireTest {

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

    private fun sync(tokens: TokenStore = InMemoryTokenStore(access = "t")) =
        ApiPermissionStatusSync(ShowUpApi(baseUrl = server.url("/").toString(), tokens = tokens), tokens)

    private val statuses = mapOf(
        WatchedPermission.Notifications to PermissionStatus.Denied,
        WatchedPermission.Location to PermissionStatus.NotDetermined,
        WatchedPermission.Camera to PermissionStatus.Granted,
        WatchedPermission.Microphone to PermissionStatus.Restricted,
    )

    @Test
    fun `the statuses go as a PUT with the server's own values`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(sync().save(statuses))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/me/permissions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"platform\":\"android\""))
        assertTrue(body, body.contains("{\"permission\":\"notifications\",\"status\":\"denied\"}"))
        assertTrue(body, body.contains("{\"permission\":\"location\",\"status\":\"not_determined\"}"))
        assertTrue(body, body.contains("{\"permission\":\"camera\",\"status\":\"granted\"}"))
        assertTrue(body, body.contains("{\"permission\":\"microphone\",\"status\":\"restricted\"}"))
    }

    @Test
    fun `a refusal and an unreachable server are both not taken`() = runBlocking {
        val sync = sync()
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(sync.save(statuses))
        server.shutdown()
        assertFalse("no answer at all", sync.save(statuses))
    }

    @Test
    fun `signed out, nothing is sent`() = runBlocking {
        assertFalse(sync(InMemoryTokenStore()).save(statuses))
        assertEquals(0, server.requestCount)
    }
}
