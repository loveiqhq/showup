/*
 * DateEndRepositoryTest.kt
 * ShowUp · the end, Stay for now and cancel calls, and the ended payload they and `GET /me/date` share (SHOWUP-197, 198)
 *
 * Bytes cross a socket, served in-process, so the generated client parses them as it will in the app.
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DateEndRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var ends: ApiDateEndStore
    private lateinit var cancels: ApiCancelStore
    private lateinit var dates: DateRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val api = ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t"))
        ends = ApiDateEndStore(api)
        cancels = ApiCancelStore(api)
        dates = DateRepository(api)
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun outcome(reason: String, screen: String? = null): String {
        val extra = screen?.let { ",\"partnerScreen\":\"$it\"" }.orEmpty()
        return "{\"dateId\":\"d1\",\"name\":\"Leonie\",\"endedByYou\":true,\"reason\":\"$reason\"$extra}"
    }

    private fun refusal(code: String) =
        "{\"statusCode\":409,\"code\":\"$code\",\"error\":\"$code\",\"message\":\"no\"}"

    @Test
    fun `an end sends its reason and entry by wire value and reads the outcome`() = runTest {
        json(outcome("partner_no_show", "you_no_showed"))
        val result = ends.end("d1", EndReason.PartnerNoShow, EndEntry.CheckinBarLink)
        val ended = (result as EndDateResult.Ended).outcome
        assertEquals(OutcomeReason.PartnerNoShow, ended.reason)
        assertEquals(PartnerScreen.YouNoShowed, ended.partnerScreen)
        assertEquals("Leonie", ended.name)
        val request = server.takeRequest()
        assertEquals("/dates/d1/end", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"reason\":\"partner_no_show\""))
        assertTrue(body, body.contains("\"entry\":\"checkin_bar_link\""))
    }

    @Test
    fun `every end reason and entry has the servers wire value`() {
        assertEquals(listOf("met", "partner_no_show", "uncomfortable"), EndReason.entries.map { it.wire })
        assertEquals(listOf("end_date_bar", "checkin_bar_link", "report"), EndEntry.entries.map { it.wire })
    }

    @Test
    fun `an end refused with 409 carries the servers code`() = runTest {
        for (code in listOf("date_not_active", "before_meet", "not_checked_in")) {
            json(refusal(code), 409)
            assertEquals(EndDateResult.Refused(code), ends.end("d1", EndReason.Met, EndEntry.EndDateBar))
        }
    }

    @Test
    fun `an end with the switch off is a refusal, not a failure`() = runTest {
        json("{\"statusCode\":404,\"message\":\"Not Found\"}", 404)
        assertEquals(EndDateResult.Refused(REFUSED_NOT_FOUND), ends.end("d1", EndReason.Met, EndEntry.EndDateBar))
    }

    @Test
    fun `an end that fails is the servers, and a dead network is the networks`() = runTest {
        json("{}", 500)
        assertEquals(EndDateResult.Failed(CallFailure.Server), ends.end("d1", EndReason.Met, EndEntry.EndDateBar))
        server.shutdown()
        assertEquals(EndDateResult.Failed(CallFailure.Network), ends.end("d1", EndReason.Met, EndEntry.EndDateBar))
    }

    @Test
    fun `Stay for now posts the discomfort flag and reports whether it landed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(true, ends.flagDiscomfort("d1").getOrNull())
        assertEquals("/dates/d1/discomfort", server.takeRequest().path)
        json("{}", 500)
        assertEquals(false, ends.flagDiscomfort("d1").getOrNull())
    }

    @Test
    fun `a cancel posts an empty body and reads the outcome`() = runTest {
        json(outcome("cancelled"))
        val cancelled = (cancels.cancel("d1") as CancelResult.Cancelled).outcome
        assertEquals(OutcomeReason.Cancelled, cancelled.reason)
        val request = server.takeRequest()
        assertEquals("/dates/d1/cancel", request.path)
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `a cancel refused with 409 carries the code, 404 is the switch, the rest fail`() = runTest {
        for (code in listOf("date_not_active", "checked_in")) {
            json(refusal(code), 409)
            assertEquals(CancelResult.Refused(code), cancels.cancel("d1"))
        }
        json("{}", 404)
        assertEquals(CancelResult.Refused(REFUSED_NOT_FOUND), cancels.cancel("d1"))
        json("{}", 500)
        assertEquals(CancelResult.Failed(CallFailure.Server), cancels.cancel("d1"))
        server.shutdown()
        assertEquals(CancelResult.Failed(CallFailure.Network), cancels.cancel("d1"))
    }

    private suspend fun readEnded(reason: String, screen: String?): EndedOutcome {
        val extra = screen?.let { ",\"partnerScreen\":\"$it\"" }.orEmpty()
        json("{\"date\":null,\"ended\":{\"dateId\":\"d1\",\"name\":\"Leonie\",\"endedByYou\":false,\"reason\":\"$reason\"$extra}}")
        return ((dates.active("Europe/Berlin") as Outcome.Ok<DateRead>).value as DateRead.Ended).outcome
    }

    @Test
    fun `the ended object maps every reason and the matchs screen`() = runTest {
        val screens = mapOf(
            "met" to "date_closed_positive",
            "partner_no_show" to "you_no_showed",
            "uncomfortable" to "date_ended_discomfort",
        )
        val expected = mapOf(
            "chat" to OutcomeReason.Chat,
            "cancelled" to OutcomeReason.Cancelled,
            "met" to OutcomeReason.Met,
            "partner_no_show" to OutcomeReason.PartnerNoShow,
            "uncomfortable" to OutcomeReason.Uncomfortable,
        )
        for ((wire, reason) in expected) {
            val screen = screens[wire]
            val ended = readEnded(wire, screen)
            assertEquals(wire, reason, ended.reason)
            assertEquals(wire, screen?.let { PartnerScreen.fromWire(it) }, ended.partnerScreen)
        }
    }

    @Test
    fun `an ended reason from an older server, or none, reads as Chat`() {
        assertEquals(OutcomeReason.Chat, OutcomeReason.fromWire(null))
        assertEquals(OutcomeReason.Chat, OutcomeReason.fromWire("something_new"))
    }

    @Test
    fun `an active date carries the ends switch and the cancel penalty from the server`() = runTest {
        json(
            "{\"date\":{\"id\":\"d1\",\"meetAt\":\"2026-11-03T18:00:00.000Z\",\"meetDay\":\"today\"," +
                "\"unlockAt\":\"2026-11-03T16:30:00.000Z\",\"coordinateOpen\":false,\"place\":null," +
                "\"match\":{\"userId\":\"u2\",\"name\":\"Leonie\",\"age\":null,\"district\":null,\"distanceKm\":null,\"photoUrl\":null,\"attendedCount\":0}," +
                "\"motivations\":{\"you\":null,\"them\":null},\"sharedVibes\":[],\"sharedInterests\":[],\"unread\":null," +
                "\"checkedIn\":false,\"arrival\":null,\"chatTutorialSeen\":true,\"endsEnabled\":true,\"cancelPenaltyPoints\":7},\"ended\":null}",
        )
        val date = ((dates.active("Europe/Berlin") as Outcome.Ok<DateRead>).value as DateRead.Active).date
        assertEquals(true, date.endsEnabled)
        assertEquals(7, date.cancelPenaltyPoints)
    }
}
