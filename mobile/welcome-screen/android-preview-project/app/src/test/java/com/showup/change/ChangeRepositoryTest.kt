/*
 * ChangeRepositoryTest.kt
 * ShowUp · what Date 05's routes answer and how the app reads them (SHOWUP-196)
 *
 * Every assertion is made against bytes that crossed a socket, served in-process: the generated client
 * parses them exactly as it will in the app, so a regenerated contract that renames a field fails here.
 */
package com.showup.change

import com.showup.api.CallFailure
import com.showup.api.InMemoryTokenStore
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.date.DateRead
import com.showup.date.DateRepository
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The JSON body that crossed the socket. (`org.json` is stubbed on the JVM, so it reads nothing here.) */
private fun String.body(): JsonObject = Json.parseToJsonElement(this).jsonObject
private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

class ChangeRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ShowUpApi
    private lateinit var store: ApiChangeStore

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t"))
        store = ApiChangeStore(api)
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private val sid = "11111111-1111-4111-8111-111111111111"
    private val suggestion = """{"id":"$sid","kind":"time","byYou":true,"time":"2026-11-17T18:30:00.000Z",
        "wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},"deadline":"2026-11-17T17:30:00.000Z",
        "createdAt":"2026-11-17T15:00:00.000Z","status":"pending"}"""

    @Test
    fun `the places list maps type, distance and the Google attribution, and sends no q for nearby`() = runTest {
        json(
            """{"attribution":"google","places":[
                {"placeId":"p1","name":"Bar Lenz","type":"bar","address":"Lausitzer Platz 9, Kreuzberg","distanceMeters":350.6},
                {"placeId":"p2","name":"Cafe X","type":"cafe","address":"A 1","distanceMeters":1100}]}""",
        )
        val list = (store.places("d1", null) as Outcome.Ok).value
        assertTrue(list.google)
        assertEquals(
            listOf(
                PlaceOption("p1", "Bar Lenz", PlaceType.Bar, "Lausitzer Platz 9, Kreuzberg", 351),
                PlaceOption("p2", "Cafe X", PlaceType.Cafe, "A 1", 1100),
            ),
            list.places,
        )
        val request = server.takeRequest()
        assertEquals("/dates/d1/change/places", request.path)
    }

    @Test
    fun `a search sends the query, and a fixture answer has no attribution`() = runTest {
        json("""{"attribution":null,"places":[]}""")
        val list = (store.places("d1", "su shi") as Outcome.Ok).value
        assertEquals(false, list.google)
        assertTrue(server.takeRequest().path!!.startsWith("/dates/d1/change/places?q=su"))
    }

    @Test
    fun `a places failure is a failure, not an empty list`() = runTest {
        json("""{"message":"x"}""", code = 503)
        assertTrue(store.places("d1", null) is Outcome.Failed)
        server.shutdown()
        assertEquals(Outcome.Failed(CallFailure.Network), store.places("d1", null))
    }

    @Test
    fun `suggest sends the kind, the value, the entry, the same clientId and the zone`() = runTest {
        json(suggestion, code = 201)
        val id = "22222222-2222-4222-8222-222222222222"
        val sent = store.suggest("d1", SuggestRequest(ChangeKind.Time, Instant.parse("2026-11-17T18:30:00Z"), null, ChangeEntry.ChatChip, null, id, "Europe/Berlin"))
        val result = (sent as SuggestResult.Sent).suggestion
        assertEquals(sid, result.id)
        assertEquals(ChangeKind.Time, result.kind)
        assertEquals(Instant.parse("2026-11-17T18:30:00Z"), result.time)
        assertEquals("Cafe Krone", result.wasPlaceName)
        assertEquals(ChangeStatus.Pending, result.status)

        val request = server.takeRequest()
        assertEquals("/dates/d1/suggestions", request.path)
        val body = request.body.readUtf8().body()
        assertEquals("time", body.str("kind"))
        assertEquals("chat_chip", body.str("entry"))
        assertEquals(id, body.str("clientId"))
        assertEquals("Europe/Berlin", body.str("tz"))
        assertTrue(body.str("time").startsWith("2026-11-17T18:30"))
        assertTrue("placeId" !in body)
    }

    @Test
    fun `a place suggestion sends the place id and whether it came from a search`() = runTest {
        json(
            """{"id":"$sid","kind":"place","byYou":true,"place":{"placeId":"p1","name":"Bar Lenz","type":"bar","address":"A 1"},
                "wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},"deadline":"2026-11-17T17:30:00.000Z",
                "createdAt":"2026-11-17T15:00:00.000Z","status":"pending"}""",
            code = 201,
        )
        val result = (store.suggest("d1", SuggestRequest(ChangeKind.Place, null, "p1", ChangeEntry.CoordinateTile, true, "22222222-2222-4222-8222-222222222222", "Europe/Berlin")) as SuggestResult.Sent).suggestion
        assertEquals(ChangePlace("p1", "Bar Lenz", PlaceType.Bar, "A 1"), result.place)
        val body = server.takeRequest().body.readUtf8().body()
        assertEquals("p1", body.str("placeId"))
        assertEquals(true, body.getValue("fromSearch").jsonPrimitive.booleanOrNull)
        assertTrue("time" !in body)
    }

    private val timeRequest = SuggestRequest(ChangeKind.Time, Instant.parse("2026-11-17T18:30:00Z"), null, ChangeEntry.ChatChip, null, "22222222-2222-4222-8222-222222222222", "UTC")

    @Test
    fun `a 409 is stale, not a failure the person can retry`() = runTest {
        json("""{"message":"past the cutoff"}""", code = 409)
        assertEquals(SuggestResult.Stale, store.suggest("d1", timeRequest))
    }

    @Test
    fun `a 503, a 500 and a dropped connection are failures, which K shows`() = runTest {
        json("""{"message":"places down"}""", code = 503)
        assertEquals(SuggestResult.Failed(CallFailure.Server), store.suggest("d1", timeRequest))
        json("""{"message":"boom"}""", code = 500)
        assertEquals(SuggestResult.Failed(CallFailure.Server), store.suggest("d1", timeRequest))
        server.shutdown()
        assertEquals(SuggestResult.Failed(CallFailure.Network), store.suggest("d1", timeRequest))
    }

    @Test
    fun `agree posts the surface and maps the agreed suggestion`() = runTest {
        json(suggestion.replace("\"pending\"", "\"agreed\"").replace("\"createdAt\"", "\"answeredAt\":\"2026-11-17T15:10:00.000Z\",\"createdAt\""))
        val result = (store.agree("d1", sid, AgreeSurface.Thread) as AgreeResult.Agreed).suggestion
        assertEquals(ChangeStatus.Agreed, result.status)
        assertEquals(Instant.parse("2026-11-17T15:10:00Z"), result.answeredAt)
        val request = server.takeRequest()
        assertEquals("/dates/d1/suggestions/$sid/agree", request.path)
        assertEquals("thread", request.body.readUtf8().body().str("surface"))
    }

    @Test
    fun `agree maps 409 to stale and 503, 500 and a dropped connection to failures`() = runTest {
        json("""{"message":"not pending"}""", code = 409)
        assertEquals(AgreeResult.Stale, store.agree("d1", sid, AgreeSurface.Thread))
        json("""{"message":"down"}""", code = 503)
        assertEquals(AgreeResult.Failed(CallFailure.Server), store.agree("d1", sid, AgreeSurface.Thread))
        server.shutdown()
        assertEquals(AgreeResult.Failed(CallFailure.Network), store.agree("d1", sid, AgreeSurface.Thread))
    }

    @Test
    fun `the suggestion list maps every status with its history fields`() = runTest {
        json(
            """[$suggestion,
                {"id":"33333333-3333-4333-8333-333333333333","kind":"place","byYou":false,
                 "place":{"placeId":"p1","name":"Bar Lenz","type":"bar","address":"A 1"},
                 "wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},
                 "deadline":"2026-11-17T17:30:00.000Z","createdAt":"2026-11-17T15:00:00.000Z","status":"expired",
                 "expiredAt":"2026-11-17T17:30:30.000Z","expiredBindingTime":"2026-11-17T18:00:00.000Z",
                 "expiredBindingPlace":{"name":"Cafe Krone"}}]""",
        )
        val list = (store.suggestions("d1") as Outcome.Ok).value
        assertEquals(listOf(ChangeStatus.Pending, ChangeStatus.Expired), list.map { it.status })
        val expired = list[1]
        assertEquals(false, expired.byYou)
        assertEquals(Instant.parse("2026-11-17T17:30:30Z"), expired.expiredAt)
        assertEquals(Instant.parse("2026-11-17T18:00:00Z"), expired.expiredBindingTime)
        assertEquals("Cafe Krone", expired.expiredBindingPlaceName)
    }

    // ── the date payload ─────────────────────────────────────────────────────

    private fun dateJson(extra: String) = """{"date":{"id":"d1","meetAt":"2026-11-17T18:00:00.000Z","meetDay":"today",
        "unlockAt":"2026-11-17T16:30:00.000Z","coordinateOpen":false,"place":{"name":"Cafe Krone","address":"X"},
        "match":{"userId":"u2","name":"Leonie","attendedCount":4},
        "motivations":{"you":null,"them":null},"sharedVibes":[],"sharedInterests":[],"unread":null,
        "checkedIn":false,"arrival":null,"chatTutorialSeen":false$extra},"ended":null}"""

    @Test
    fun `the date payload carries the change block and the request card`() = runTest {
        json(
            dateJson(
                ""","change":{"cutoffAt":"2026-11-17T17:30:00.000Z","allowed":true,
                    "slots":["2026-11-17T18:15:00.000Z","2026-11-17T18:30:00.000Z"],
                    "timeWas":"2026-11-17T17:00:00.000Z","placeWas":{"name":"Old Cafe","address":"B 2"},
                    "pending":[{"id":"$sid","kind":"time","byYou":false,"time":"2026-11-17T18:30:00.000Z",
                      "wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},
                      "deadline":"2026-11-17T17:30:00.000Z","createdAt":"2026-11-17T15:00:00.000Z"}]},
                  "changeRequest":{"id":"$sid","kind":"time","byYou":false,"time":"2026-11-17T18:30:00.000Z",
                      "wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},
                      "deadline":"2026-11-17T17:30:00.000Z","createdAt":"2026-11-17T15:00:00.000Z"}""",
            ),
        )
        val read = (DateRepository(api).active("Europe/Berlin") as Outcome.Ok).value as DateRead.Active
        val change = read.date.change!!
        assertEquals(Instant.parse("2026-11-17T17:30:00Z"), change.cutoffAt)
        assertTrue(change.allowed)
        assertEquals(listOf(Instant.parse("2026-11-17T18:15:00Z"), Instant.parse("2026-11-17T18:30:00Z")), change.slots)
        assertEquals(Instant.parse("2026-11-17T17:00:00Z"), change.timeWas)
        assertEquals("Old Cafe", change.placeWas)
        assertEquals(1, change.pending.size)
        assertEquals(false, change.pending.single().byYou)
        assertEquals(sid, read.date.changeRequest!!.id)
    }

    @Test
    fun `an older server with no change fields still reads, with none`() = runTest {
        json(dateJson(""))
        val read = (DateRepository(api).active("Europe/Berlin") as Outcome.Ok).value as DateRead.Active
        assertNull(read.date.change)
        assertNull(read.date.changeRequest)
    }
}
