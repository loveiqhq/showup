/*
 * DateChangeLogicTest.kt
 * ShowUp · Date 05 on the Date screen without a view: the rules, the model's I agree, the dev routes (SHOWUP-196)
 */
package com.showup.date

import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.InMemoryTokenStore
import com.showup.api.Outcome
import com.showup.api.ShowUpApi
import com.showup.change.AgreeResult
import com.showup.change.AgreeSurface
import com.showup.change.ChangeKind
import com.showup.change.ChangePlace
import com.showup.change.ChangeSuggestion
import com.showup.change.DateChange
import com.showup.change.FakeChangeStore
import com.showup.change.PlaceType
import com.showup.change.counterCardRoute
import com.showup.chat.ChatEntry
import com.showup.chat.ChatReason
import com.showup.chat.chatEntryFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DateChangeLogicTest {
    private val dispatcher = StandardTestDispatcher()
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val later = Instant.parse("2026-11-03T18:30:00Z")
    private val deadline = Instant.parse("2026-11-03T17:30:00Z")
    private var now = Instant.parse("2026-11-03T15:00:00Z")

    private fun time(byYou: Boolean) = ChangeSuggestion(
        id = "t1", kind = ChangeKind.Time, byYou = byYou, time = later, place = null,
        wasTime = meet, wasPlaceName = null, deadline = deadline, createdAt = Instant.parse("2026-11-03T14:00:00Z"),
    )

    private fun place(byYou: Boolean) = ChangeSuggestion(
        id = "p1", kind = ChangeKind.Place, byYou = byYou, time = null,
        place = ChangePlace("pl", "Bar Lenz", PlaceType.Bar, "Reichenberger Str. 9"),
        wasTime = meet, wasPlaceName = "Cafe Krone", deadline = deadline, createdAt = Instant.parse("2026-11-03T14:00:00Z"),
    )

    private fun date(
        request: ChangeSuggestion? = null,
        pending: List<ChangeSuggestion> = emptyList(),
        timeWas: Instant? = null,
        arrival: DateArrival? = null,
        unread: DateUnread? = null,
    ) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term", sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = unread, checkedIn = false, chatTutorialSeen = true, arrival = arrival,
        change = DateChange(meet.minusSeconds(1800), true, listOf(later), timeWas, null, pending), changeRequest = request,
    )

    // ── rules ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hoisting order is the arrival, then the request, then an unread message`() {
        val arrival = DateArrival(now)
        assertTrue(hoisted(DateState.Locked, null, time(false)))
        assertTrue(hoisted(DateState.Available, arrival, time(false)))
        assertTrue(hoisted(DateState.Unread, null, null))
        assertFalse(hoisted(DateState.Available, null, null))
        assertFalse(requestShown(date(request = time(false), arrival = arrival)))
        assertTrue(requestShown(date(request = time(false))))
    }

    @Test
    fun `an own pending time gives a waiting tag, the match's gives none, an agreed one gives Changed was`() {
        val waiting = changeTagFor(date(pending = listOf(time(true))), ChangeKind.Time, now, berlin)
        assertEquals(ChangeTagText.Waiting("19:30"), waiting)
        assertNull(changeTagFor(date(pending = listOf(time(false))), ChangeKind.Time, now, berlin))
        assertEquals(ChangeTagText.Waiting("Bar Lenz"), changeTagFor(date(pending = listOf(place(true))), ChangeKind.Place, now, berlin))
        val agreed = date(timeWas = Instant.parse("2026-11-03T17:30:00Z"))
        assertEquals(ChangeTagText.Changed("18:30"), changeTagFor(agreed, ChangeKind.Time, now, berlin))
        assertNull("hidden after the meet time", changeTagFor(agreed, ChangeKind.Time, meet, berlin))
        assertNull(changeTagFor(agreed, ChangeKind.Place, now, berlin))
    }

    @Test
    fun `one binding note for any own pending, with the current binding values and deadline`() {
        assertNull(bindingNoteFor(date(pending = listOf(time(false))), berlin))
        assertEquals(BindingNoteValues("19:00", "Cafe Krone", "18:30"), bindingNoteFor(date(pending = listOf(time(true), place(true))), berlin))
    }

    @Test
    fun `the request card text carries every ticket value`() {
        val t = requestCardText(date(request = place(false)), place(false), now, berlin)
        assertEquals(ChatReason.ChangePlace, t.reason)
        assertEquals("needs your answer · 15:00", t.needs)
        assertEquals("Bar Lenz", t.value)
        assertEquals("Cafe Krone", t.was)
        assertEquals("Bar · Reichenberger Str. 9", t.placeLine)
        assertEquals("18:30", t.deadline)
        assertEquals("19:00", t.bindingTime)
        assertEquals("Messaging opens at 17:30", t.opensLine)
        assertNull(requestCardText(date(), time(false), now, berlin).placeLine)
    }

    @Test
    fun `date_screen_viewed has_unread is true while a request shows`() {
        val with = DateAnalytics.dateScreenViewed(date(request = time(false)), DateState.Locked, now).second
        assertEquals(true, with["has_unread"])
        val without = DateAnalytics.dateScreenViewed(date(), DateState.Locked, now).second
        assertEquals(false, without["has_unread"])
    }

    @Test
    fun `Open chat on the request card is read_reply, and Suggest another opens the counter_card picker`() {
        assertEquals(ChatEntry.ReadReply, chatEntryFor(date(request = time(false))))
        assertEquals(ChatEntry.OpenChat, chatEntryFor(date(request = time(false), arrival = DateArrival(now))))
        val route = counterCardRoute(ChangeKind.Place)
        assertEquals("place", route.kind)
        assertEquals("counter_card", route.entry)
    }

    // ── the model's I agree ──────────────────────────────────────────────────────────────────

    private val store = FakeDateStore()
    private val changes = FakeChangeStore()
    private val analytics = RecordingAnalytics()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DateViewModel(store, analytics, clock = { now }, zone = { berlin }, changes = changes)

    private suspend fun kotlinx.coroutines.test.TestScope.shown(vm: DateViewModel) {
        store.read = Outcome.Ok(DateRead.Active(date(request = time(false))))
        vm.arrived(referrer = null)
        advanceUntilIdle()
    }

    @Test
    fun `agree sends surface coordinate_card, then reads the date again and clears nothing itself`() = runTest(dispatcher) {
        val vm = vm()
        shown(vm)
        val before = store.reads
        store.read = Outcome.Ok(DateRead.Active(date(request = null, timeWas = meet)))
        vm.agreeRequest()
        advanceUntilIdle()
        assertEquals(listOf(Triple("d1", "t1", AgreeSurface.CoordinateCard)), changes.agrees)
        assertEquals(before + 1, store.reads)
        assertNull(vm.state.value.date?.changeRequest)
        assertFalse(vm.state.value.agreeing)
        assertFalse(vm.state.value.agreeFailed)
    }

    @Test
    fun `a 409 re-reads only, with no error`() = runTest(dispatcher) {
        val vm = vm()
        shown(vm)
        changes.agree = AgreeResult.Stale
        val before = store.reads
        vm.agreeRequest()
        advanceUntilIdle()
        assertEquals(before + 1, store.reads)
        assertFalse(vm.state.value.agreeFailed)
        assertFalse(vm.state.value.agreeing)
    }

    @Test
    fun `a failure shows the line and changes nothing, and a retry that succeeds clears it`() = runTest(dispatcher) {
        val vm = vm()
        shown(vm)
        changes.agree = AgreeResult.Failed(CallFailure.Network)
        val before = store.reads
        vm.agreeRequest()
        advanceUntilIdle()
        assertTrue(vm.state.value.agreeFailed)
        assertEquals("no re-read, the card stays", before, store.reads)
        assertEquals("t1", vm.state.value.date?.changeRequest?.id)
        changes.agree = AgreeResult.Agreed(time(false))
        store.read = Outcome.Ok(DateRead.Active(date(request = null)))
        vm.agreeRequest()
        advanceUntilIdle()
        assertFalse(vm.state.value.agreeFailed)
        assertEquals(2, changes.agrees.size)
    }

    @Test
    fun `while in flight a second tap sends nothing and the state says agreeing`() = runTest(dispatcher) {
        val vm = vm()
        shown(vm)
        changes.agreeGate = CompletableDeferred()
        vm.agreeRequest()
        advanceUntilIdle()
        assertTrue(vm.state.value.agreeing)
        vm.agreeRequest()
        advanceUntilIdle()
        assertEquals(1, changes.agrees.size)
        changes.agreeGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.agreeing)
    }

    @Test
    fun `agree with no request does nothing`() = runTest(dispatcher) {
        val vm = vm()
        store.read = Outcome.Ok(DateRead.Active(date()))
        vm.arrived(referrer = null)
        advanceUntilIdle()
        vm.agreeRequest()
        advanceUntilIdle()
        assertEquals(0, changes.agrees.size)
    }

    // ── the dev routes ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the dev actions call the Task 3 routes`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val repo = DateRepository(ShowUpApi(baseUrl = server.url("/").toString(), tokens = InMemoryTokenStore(access = "t")))
            repeat(4) { server.enqueue(MockResponse().setResponseCode(204)) }
            assertTrue(repo.matchSuggests(ChangeKind.Time).getOrThrow())
            assertTrue(repo.matchSuggests(ChangeKind.Place).getOrThrow())
            assertTrue(repo.matchAgrees().getOrThrow())
            assertTrue(repo.expireSuggestions().getOrThrow())
            val calls = List(4) { server.takeRequest() }
            assertEquals(listOf("POST /dev/date/change", "POST /dev/date/change", "POST /dev/date/change/agree", "POST /dev/date/change/expire"),
                calls.map { "${it.method} ${it.path}" })
            assertTrue(calls[0].body.readUtf8().contains("\"kind\":\"time\""))
            assertTrue(calls[1].body.readUtf8().contains("\"kind\":\"place\""))
        } finally {
            server.shutdown()
        }
    }
}
