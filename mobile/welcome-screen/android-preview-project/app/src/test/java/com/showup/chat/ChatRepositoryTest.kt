/*
 * ChatRepositoryTest.kt
 * ShowUp · what the chat's routes answer and how the app reads them (SHOWUP-194)
 *
 * Every assertion is made against bytes that crossed a socket, served in-process: the generated
 * client parses them exactly as it will in the app.
 */
package com.showup.chat

import com.showup.api.CallFailure
import com.showup.api.InMemoryTokenStore
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.date.EndedInChat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ChatRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: ApiChatStore

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        store = ApiChatStore(ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t")))
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun empty(code: Int = 204) = server.enqueue(MockResponse().setResponseCode(code))

    @Test
    fun `the thread maps every field, oldest first`() = runTest {
        json(
            """[{"id":"m1","reason":"running_late","body":"Ten minutes late!","fromYou":false,
                 "createdAt":"2026-11-03T16:30:00.000Z","read":null},
                {"id":"m2","reason":null,"body":"No worries","fromYou":true,
                 "createdAt":"2026-11-03T16:31:00.000Z","read":true}]""",
        )
        val thread = (store.thread("d1") as Outcome.Ok).value
        assertEquals(
            listOf(
                ChatMessage("m1", ChatReason.RunningLate, "Ten minutes late!", false, Instant.parse("2026-11-03T16:30:00Z"), null),
                ChatMessage("m2", null, "No worries", true, Instant.parse("2026-11-03T16:31:00Z"), true),
            ),
            thread,
        )
        assertEquals("/dates/d1/chat", server.takeRequest().path)
    }

    @Test
    fun `an old message sent with a reason alone reads as its reason's label`() = runTest {
        json("""[{"id":"m1","reason":"on_my_way","body":null,"fromYou":false,"createdAt":"2026-11-03T16:30:00.000Z","read":null}]""")
        assertEquals("I'm on my way", (store.thread("d1") as Outcome.Ok).value.single().body)
    }

    @Test
    fun `send posts the reason and the text, and reads the message back`() = runTest {
        json("""{"id":"m3","reason":"cant_make_it","body":"So sorry","fromYou":true,"createdAt":"2026-11-03T16:40:00.000Z","read":false}""", 201)
        val sent = (store.send("d1", ChatReason.CantMakeIt, "So sorry") as Outcome.Ok).value
        assertEquals(ChatMessage("m3", ChatReason.CantMakeIt, "So sorry", true, Instant.parse("2026-11-03T16:40:00Z"), false), sent)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/dates/d1/chat", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"reason\":\"cant_make_it\""))
        assertTrue(body, body.contains("\"body\":\"So sorry\""))
    }

    @Test
    fun `a message with no chip is sent with no reason`() = runTest {
        json("""{"id":"m4","reason":null,"body":"Hi","fromYou":true,"createdAt":"2026-11-03T16:40:00.000Z","read":false}""", 201)
        store.send("d1", null, "Hi")
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body, Regex("\"reason\":\"").containsMatchIn(body))
    }

    @Test
    fun `a refused send is a Server failure, nothing answering a Network one`() = runTest {
        json("""{"statusCode":409}""", 409)
        assertEquals(Outcome.Failed(CallFailure.Server), store.send("d1", null, "Hi"))
        server.shutdown()
        assertEquals(Outcome.Failed(CallFailure.Network), store.send("d1", null, "Hi"))
    }

    @Test
    fun `End date reads the outcome, a 409 is AlreadyEnded and anything else a failure`() = runTest {
        json("""{"dateId":"d1","name":"Leonie","endedByYou":true}""")
        assertEquals(EndResult.Ended(EndedInChat("d1", "Leonie", endedByYou = true)), store.endInChat("d1"))
        assertEquals("/dates/d1/end-in-chat", server.takeRequest().path)
        json("""{"statusCode":409,"message":"This date has already ended"}""", 409)
        assertEquals(EndResult.AlreadyEnded, store.endInChat("d1"))
        json("""{"statusCode":500}""", 500)
        assertEquals(EndResult.Failed(CallFailure.Server), store.endInChat("d1"))
        server.shutdown()
        assertEquals(EndResult.Failed(CallFailure.Network), store.endInChat("d1"))
    }

    @Test
    fun `the read, tutorial and seen stamps post to their routes`() = runTest {
        empty()
        empty()
        empty()
        store.markRead("d1")
        store.markTutorialSeen("d1")
        store.markEndSeen("d1")
        assertEquals(
            listOf("/dates/d1/chat/read", "/dates/d1/chat/tutorial", "/dates/d1/ended/seen"),
            List(3) { server.takeRequest().path },
        )
    }

    @Test
    fun `a stamp that fails does not throw`() = runTest {
        server.shutdown()
        store.markRead("d1")
        store.markTutorialSeen("d1")
        store.markEndSeen("d1")
    }
}
