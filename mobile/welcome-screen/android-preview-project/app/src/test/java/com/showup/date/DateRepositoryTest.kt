/*
 * DateRepositoryTest.kt
 * ShowUp · what `GET /me/date` answers and how the Date screen reads it (SHOWUP-193)
 *
 * Every assertion is made against bytes that crossed a socket, served in-process: the generated
 * client parses them exactly as it will in the app. No backend, no network, no emulator.
 */
package com.showup.date

import com.showup.api.CallFailure
import com.showup.api.InMemoryTokenStore
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: DateRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        repo = DateRepository(
            ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t")),
        )
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private val full = """
        {"date":{"id":"d1","meetAt":"2026-11-03T18:00:00.000Z","meetDay":"today",
         "unlockAt":"2026-11-03T16:30:00.000Z","coordinateOpen":false,
         "place":{"name":"Cafe Krone","address":"Oranienstrasse 42, Kreuzberg"},
         "match":{"userId":"u2","name":"Leonie","age":28,"district":"Neukölln","distanceKm":1,
                  "photoUrl":"/uploads/p1.jpg","attendedCount":4},
         "motivations":{"you":"long_term","them":"long_term"},
         "sharedVibes":["coffee_chat","playful"],"sharedInterests":["reading"],
         "unread":{"count":2,"reason":"running_late","body":"Ten minutes late!","sentAt":"2026-11-03T17:10:00.000Z"},
         "checkedIn":false}}
    """.trimIndent()

    @Test
    fun `an active date maps every field, and a relative photo path becomes a loadable URL`() = runTest {
        json(full)
        val read = repo.active("Europe/Berlin")
        assertTrue("expected Ok, got $read", read is Outcome.Ok<*>)
        val date = ((read as Outcome.Ok<DateRead>).value as DateRead.Active).date
        assertEquals("d1", date.id)
        assertEquals(Instant.parse("2026-11-03T18:00:00Z"), date.meetAt)
        assertEquals("today", date.meetDay)
        assertEquals(Instant.parse("2026-11-03T16:30:00Z"), date.unlockAt)
        assertEquals(false, date.coordinateOpen)
        assertEquals(DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"), date.place)
        assertEquals("Leonie", date.match.name)
        assertEquals(28, date.match.age)
        assertEquals("Neukölln", date.match.district)
        assertEquals(1, date.match.distanceKm)
        assertEquals(server.url("/uploads/p1.jpg").toString(), date.match.photoUrl)
        assertEquals(4, date.match.attendedCount)
        assertEquals("long_term", date.motivationYou)
        assertEquals("long_term", date.motivationThem)
        assertEquals(listOf("coffee_chat", "playful"), date.sharedVibes)
        assertEquals(listOf("reading"), date.sharedInterests)
        assertEquals(DateUnread(2, "running_late", "Ten minutes late!", Instant.parse("2026-11-03T17:10:00Z")), date.unread)
        assertEquals(false, date.checkedIn)
        assertEquals("/me/date?tz=Europe%2FBerlin", server.takeRequest().path)
    }

    @Test
    fun `what the match hid or never gave arrives as null`() = runTest {
        json(
            """{"date":{"id":"d1","meetAt":"2026-11-03T18:00:00.000Z","meetDay":"tomorrow",
               "unlockAt":"2026-11-03T16:30:00.000Z","coordinateOpen":true,"place":null,
               "match":{"userId":"u2","name":"Leonie","attendedCount":0},
               "motivations":{"you":null,"them":"casual"},"sharedVibes":[],"sharedInterests":[],
               "unread":null,"checkedIn":false}}""",
        )
        val date = (((repo.active("UTC")) as Outcome.Ok<DateRead>).value as DateRead.Active).date
        assertNull(date.place)
        assertNull(date.match.age)
        assertNull(date.match.district)
        assertNull(date.match.distanceKm)
        assertNull(date.match.photoUrl)
        assertNull(date.motivationYou)
        assertNull(date.unread)
        assertEquals("tomorrow", date.meetDay)
    }

    @Test
    fun `no active date is None, not a failure`() = runTest {
        json("""{"date":null}""")
        assertEquals(Outcome.Ok(DateRead.None), repo.active("UTC"))
    }

    @Test
    fun `a server error is a Server failure`() = runTest {
        json("""{"statusCode":500}""", code = 500)
        assertEquals(Outcome.Failed(CallFailure.Server), repo.active("UTC"))
    }

    @Test
    fun `nothing answering is a Network failure`() = runTest {
        server.shutdown()
        assertEquals(Outcome.Failed(CallFailure.Network), repo.active("UTC"))
    }

    @Test
    fun `the match's full profile is read with Search's own mapper`() = runTest {
        json(
            """{"userId":"u2","name":"Leonie","age":28,"distanceKm":0,"meetAt":"2026-11-03T18:00:00.000Z",
               "meetDay":"today","attendedCount":4,"vibes":[],"photos":[{"url":"/uploads/p1.jpg"}],
               "prompts":[],"essentials":{},"howILive":{},"interests":["reading"]}""",
        )
        val profile = (repo.partnerProfile("UTC") as Outcome.Ok<com.showup.search.SearchProfile>).value
        assertEquals("Leonie", profile.name)
        assertEquals(listOf(server.url("/uploads/p1.jpg").toString()), profile.photos)
        assertEquals("/me/date/profile?tz=UTC", server.takeRequest().path)
    }
}
