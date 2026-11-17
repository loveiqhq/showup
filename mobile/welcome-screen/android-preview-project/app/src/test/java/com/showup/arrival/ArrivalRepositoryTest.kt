/*
 * ArrivalRepositoryTest.kt
 * ShowUp · what the arrival routes answer and how the app reads them (SHOWUP-195)
 */
package com.showup.arrival

import com.showup.api.CallFailure
import com.showup.api.InMemoryTokenStore
import com.showup.api.Outcome
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

class ArrivalRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: ApiArrivalStore
    private val id = "0b7d4a1e-2c3f-4e5a-8b6c-7d8e9f0a1b2c"
    private val fix = LocationFix(52.5186, 13.3761, 12.5)

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        store = ApiArrivalStore(ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t")))
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, code: Int) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun empty(code: Int = 204) = server.enqueue(MockResponse().setResponseCode(code))

    @Test
    fun `a check-in posts the fix, the entry and the client id, and 201 is Stored`() = runTest {
        json("""{"checkedInAt":"2026-11-03T17:58:00.000Z","committed":false}""", 201)
        assertEquals(CheckInResult.Stored, store.checkIn("d1", fix, ArrivalEntry.ChatChip, id))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/dates/d1/arrival", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"entry\":\"chat_chip\""))
        assertTrue(body, body.contains("\"clientId\":\"$id\""))
        assertTrue(body, body.contains("\"latitude\":52.5186"))
        assertTrue(body, body.contains("\"accuracyM\":12.5"))
    }

    @Test
    fun `a fix without an accuracy sends none`() = runTest {
        json("""{"checkedInAt":"2026-11-03T17:58:00.000Z","committed":false}""", 201)
        store.checkIn("d1", LocationFix(1.0, 2.0, null), ArrivalEntry.DateScreen, id)
        assertFalse(server.takeRequest().body.readUtf8().contains("accuracyM"))
    }

    @Test
    fun `409 is AlreadyCheckedIn, anything else a failure, no answer a Network one`() = runTest {
        json("""{"statusCode":409}""", 409)
        assertEquals(CheckInResult.AlreadyCheckedIn, store.checkIn("d1", fix, ArrivalEntry.DateScreen, id))
        json("""{"statusCode":500}""", 500)
        assertEquals(CheckInResult.Failed(CallFailure.Server), store.checkIn("d1", fix, ArrivalEntry.DateScreen, id))
        server.shutdown()
        assertEquals(CheckInResult.Failed(CallFailure.Network), store.checkIn("d1", fix, ArrivalEntry.DateScreen, id))
    }

    @Test
    fun `undo deletes, and 409 (already committed) is nothing left to retry`() = runTest {
        empty()
        assertEquals(Outcome.Ok(Unit), store.undo("d1"))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/dates/d1/arrival", request.path)
        json("""{"statusCode":409}""", 409)
        assertEquals(Outcome.Ok(Unit), store.undo("d1"))
        json("""{"statusCode":500}""", 500)
        assertEquals(Outcome.Failed(CallFailure.Server), store.undo("d1"))
        server.shutdown()
        assertEquals(Outcome.Failed(CallFailure.Network), store.undo("d1"))
    }

    @Test
    fun `every 4xx on undo and commit is settled with one request, never retried`() = runTest {
        for (code in listOf(400, 401, 403, 429)) {
            json("""{"statusCode":$code}""", code)
            assertEquals("undo $code", Outcome.Ok(Unit), store.undo("d1"))
            json("""{"statusCode":$code}""", code)
            assertEquals("commit $code", Outcome.Ok(Unit), store.commit("d1"))
        }
        assertEquals(8, server.requestCount)
    }

    @Test
    fun `a check-in refused with another 4xx is a failure, not already checked in`() = runTest {
        json("""{"statusCode":403}""", 403)
        assertEquals(CheckInResult.Failed(CallFailure.Server), store.checkIn("d1", fix, ArrivalEntry.DateScreen, id))
    }

    @Test
    fun `commit posts, and 404 (nothing to commit) is nothing left to retry`() = runTest {
        empty()
        assertEquals(Outcome.Ok(Unit), store.commit("d1"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/dates/d1/arrival/commit", request.path)
        json("""{"statusCode":404}""", 404)
        assertEquals(Outcome.Ok(Unit), store.commit("d1"))
        json("""{"statusCode":503}""", 503)
        assertEquals(Outcome.Failed(CallFailure.Server), store.commit("d1"))
        server.shutdown()
        assertEquals(Outcome.Failed(CallFailure.Network), store.commit("d1"))
    }
}
