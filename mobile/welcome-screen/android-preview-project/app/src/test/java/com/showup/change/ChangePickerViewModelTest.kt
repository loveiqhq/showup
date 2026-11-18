/*
 * ChangePickerViewModelTest.kt
 * ShowUp · the picker's loading, search, selection and sending (SHOWUP-196, Date 05 A, B, K, M, N)
 *
 * The clock, the id source and the store are injected, so the 300 ms debounce is a step of the test
 * dispatcher and a retry's id is read off the recorded request.
 */
package com.showup.change

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

val NEARBY = listOf(
    PlaceOption("p-lenz", "Bar Lenz", PlaceType.Bar, "Lausitzer Platz 9, Kreuzberg", 350),
    PlaceOption("p-nove", "Osteria Nove", PlaceType.Restaurant, "Wiener Str. 14, Kreuzberg", 600),
    PlaceOption("p-fraeu", "Café Fräulein", PlaceType.Cafe, "Manteuffelstr. 30, Kreuzberg", 800),
)

class FakeChangeStore : ChangeStore {
    val placeCalls = mutableListOf<String?>()
    var places: (String?) -> Outcome<PlacesList> = { Outcome.Ok(PlacesList(google = true, places = NEARBY)) }
    val suggests = mutableListOf<SuggestRequest>()
    var suggest: SuggestResult = SuggestResult.Sent(sampleSuggestion())
    val agrees = mutableListOf<Triple<String, String, AgreeSurface>>()
    var agree: Outcome<ChangeSuggestion> = Outcome.Ok(sampleSuggestion())

    override suspend fun places(dateId: String, query: String?): Outcome<PlacesList> {
        placeCalls += query
        return places(query)
    }

    override suspend fun suggest(dateId: String, request: SuggestRequest): SuggestResult {
        suggests += request
        return suggest
    }

    override suspend fun agree(dateId: String, suggestionId: String, surface: AgreeSurface): Outcome<ChangeSuggestion> {
        agrees += Triple(dateId, suggestionId, surface)
        return agree
    }

    override suspend fun suggestions(dateId: String): Outcome<List<ChangeSuggestion>> = Outcome.Ok(emptyList())
}

fun sampleSuggestion() = ChangeSuggestion(
    id = "s1", kind = ChangeKind.Time, byYou = true, time = Instant.parse("2026-11-17T18:30:00Z"), place = null,
    wasTime = Instant.parse("2026-11-17T18:00:00Z"), wasPlaceName = null,
    deadline = Instant.parse("2026-11-17T17:30:00Z"), createdAt = Instant.parse("2026-11-17T15:00:00Z"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-17T18:00:00Z")
    private val now = Instant.parse("2026-11-17T15:00:00Z")
    private val slot = Instant.parse("2026-11-17T18:30:00Z")
    private val analytics = RecordingAnalytics()
    private val store = FakeChangeStore()
    private var ids = 0

    private fun date() = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(5400), coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = emptyList(), sharedInterests = emptyList(), unread = null, checkedIn = false, chatTutorialSeen = false,
    )

    private fun vm(kind: ChangeKind, entry: ChangeEntry = ChangeEntry.CoordinateTile, saved: SavedStateHandle = SavedStateHandle()) = ChangePickerViewModel(
        kind = kind, entry = entry, store = store, analytics = analytics, clock = { now },
        newId = { "id-${++ids}" }, zone = { ZoneId.of("Europe/Berlin") }, saved = saved,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── opening ─────────────────────────────────────────────────────────────

    @Test
    fun `opening reports change_picker_opened once, with the entry and the minutes to the start`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Time, ChangeEntry.CounterCard)
        vm.open(date())
        vm.open(date())
        advanceUntilIdle()
        assertEquals(listOf("change_picker_opened"), analytics.names())
        val p = analytics.of("change_picker_opened").single().properties
        assertEquals("d1", p["date_id"])
        assertEquals("time", p["kind"])
        assertEquals("counter_card", p["entry"])
        assertEquals(180, p["minutes_before_start"])
    }

    @Test
    fun `the time picker asks for no places`() = runTest(dispatcher) {
        vm(ChangeKind.Time).open(date())
        advanceUntilIdle()
        assertTrue(store.placeCalls.isEmpty())
    }

    @Test
    fun `the place picker loads what is nearby on open, with no query`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        assertEquals(PlacesView.Loading, vm.ui.value.list)
        vm.open(date())
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), store.placeCalls)
        assertEquals(PlacesView.Loaded(NEARBY, google = true, forQuery = ""), vm.ui.value.list)
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    fun `typing searches once, 300 ms after the last key, on the trimmed query`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        vm.setQuery("s")
        advanceTimeBy(200)
        vm.setQuery("su  ")
        advanceTimeBy(299)
        assertEquals("nothing yet", listOf<String?>(null), store.placeCalls)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null, "su"), store.placeCalls)
        assertEquals("su  ", vm.ui.value.query)
    }

    @Test
    fun `an emptied query shows what is nearby again`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        store.places = { q -> Outcome.Ok(PlacesList(google = true, places = if (q == null) NEARBY else NEARBY.take(1))) }
        vm.setQuery("lenz")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals("lenz", (vm.ui.value.list as PlacesView.Loaded).forQuery)
        vm.setQuery("")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null, "lenz", null), store.placeCalls)
        assertEquals(PlacesView.Loaded(NEARBY, google = true, forQuery = ""), vm.ui.value.list)
    }

    @Test
    fun `a query with no result is N, not a failure`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        store.places = { Outcome.Ok(PlacesList(google = true, places = emptyList())) }
        vm.setQuery("sushi")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(PlacesView.Loaded(emptyList(), google = true, forQuery = "sushi"), vm.ui.value.list)
    }

    // ── M ───────────────────────────────────────────────────────────────────

    @Test
    fun `a failed load is M, keeps the search usable, and Try again reads again`() = runTest(dispatcher) {
        store.places = { Outcome.Failed(CallFailure.Network) }
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        assertEquals(PlacesView.Failed, vm.ui.value.list)
        assertNull(vm.ui.value.place)

        store.places = { Outcome.Ok(PlacesList(google = true, places = NEARBY)) }
        vm.retryPlaces()
        advanceUntilIdle()
        assertEquals(PlacesView.Loaded(NEARBY, true, ""), vm.ui.value.list)
        assertEquals(listOf<String?>(null, null), store.placeCalls)
    }

    @Test
    fun `Try again keeps the typed query`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        store.places = { Outcome.Failed(CallFailure.Server) }
        vm.setQuery("bar")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(PlacesView.Failed, vm.ui.value.list)
        store.places = { Outcome.Ok(PlacesList(google = false, places = NEARBY)) }
        vm.retryPlaces()
        advanceUntilIdle()
        assertEquals("bar", store.placeCalls.last())
    }

    // ── selection ───────────────────────────────────────────────────────────

    @Test
    fun `a slot or a place is selected, and another replaces it`() = runTest(dispatcher) {
        val t = vm(ChangeKind.Time)
        t.open(date())
        t.selectSlot(slot)
        assertEquals(slot, t.ui.value.slot)
        t.selectSlot(slot.plusSeconds(900))
        assertEquals(slot.plusSeconds(900), t.ui.value.slot)

        val p = vm(ChangeKind.Place)
        p.open(date())
        advanceUntilIdle()
        p.selectPlace(NEARBY[0])
        p.selectPlace(NEARBY[1])
        assertEquals(NEARBY[1], p.ui.value.place)
    }

    @Test
    fun `a selection survives a list change only while the place is still listed`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        vm.selectPlace(NEARBY[1])
        store.places = { Outcome.Ok(PlacesList(true, listOf(NEARBY[0], NEARBY[1]))) }
        vm.setQuery("o")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(NEARBY[1], vm.ui.value.place)
        store.places = { Outcome.Ok(PlacesList(true, listOf(NEARBY[0]))) }
        vm.setQuery("ol")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertNull(vm.ui.value.place)
    }

    // ── sending ─────────────────────────────────────────────────────────────

    @Test
    fun `Suggest with nothing selected sends nothing`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.suggest()
        advanceUntilIdle()
        assertTrue(store.suggests.isEmpty())
    }

    @Test
    fun `a suggested time goes with the entry, a fresh id and the zone, and on success the picker is done`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Time, ChangeEntry.ChatChip)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        assertTrue("both buttons are inert while it is in flight", vm.ui.value.sending)
        advanceUntilIdle()
        val sent = store.suggests.single()
        assertEquals(SuggestRequest(ChangeKind.Time, slot, null, ChangeEntry.ChatChip, null, "id-1", "Europe/Berlin"), sent)
        assertTrue(vm.ui.value.done)
        assertFalse(vm.ui.value.sending)
        assertEquals("a successful send fires nothing client-side", listOf("change_picker_opened"), analytics.names())
    }

    @Test
    fun `a place picked from a search is flagged fromSearch, one picked from the nearby list is not`() = runTest(dispatcher) {
        val nearby = vm(ChangeKind.Place)
        nearby.open(date())
        advanceUntilIdle()
        nearby.selectPlace(NEARBY[0])
        nearby.suggest()
        advanceUntilIdle()
        assertEquals(false, store.suggests.last().fromSearch)
        assertEquals("p-lenz", store.suggests.last().placeId)

        val searched = vm(ChangeKind.Place)
        searched.open(date())
        advanceUntilIdle()
        searched.setQuery("lenz")
        advanceTimeBy(301)
        advanceUntilIdle()
        searched.selectPlace(NEARBY[0])
        searched.suggest()
        advanceUntilIdle()
        assertEquals(true, store.suggests.last().fromSearch)
    }

    @Test
    fun `a failed send is K -- the picker stays open with the selection, and nothing is done`() = runTest(dispatcher) {
        store.suggest = SuggestResult.Failed(CallFailure.Network)
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        advanceUntilIdle()
        val ui = vm.ui.value
        assertTrue(ui.sendFailed)
        assertFalse(ui.sending)
        assertFalse(ui.done)
        assertEquals(slot, ui.slot)
    }

    @Test
    fun `the retry reuses the same clientId, and a different value gets a new one`() = runTest(dispatcher) {
        store.suggest = SuggestResult.Failed(CallFailure.Network)
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        advanceUntilIdle()
        vm.suggest()
        advanceUntilIdle()
        assertEquals(listOf("id-1", "id-1"), store.suggests.map { it.clientId })

        vm.selectSlot(slot.plusSeconds(900))
        vm.suggest()
        advanceUntilIdle()
        assertEquals("id-2", store.suggests.last().clientId)

        store.suggest = SuggestResult.Sent(sampleSuggestion())
        vm.suggest()
        advanceUntilIdle()
        assertEquals("id-2", store.suggests.last().clientId)
        assertTrue(vm.ui.value.done)
    }

    @Test
    fun `a second tap while it is in flight sends nothing more`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        vm.suggest()
        advanceUntilIdle()
        assertEquals(1, store.suggests.size)
    }

    // ── 409, 503 ────────────────────────────────────────────────────────────

    @Test
    fun `a 409 is not K -- the picker is stale, nothing is shown as failed, and no event fires`() = runTest(dispatcher) {
        store.suggest = SuggestResult.Stale
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        advanceUntilIdle()
        val ui = vm.ui.value
        assertTrue("the host closes the picker and reads the date again", ui.stale)
        assertFalse("no K", ui.sendFailed)
        assertFalse(ui.sending)
        assertFalse(ui.done)
        assertEquals(listOf("change_picker_opened"), analytics.names())
        vm.cancel()
        assertTrue("a stale picker is not dismissed", analytics.of("change_picker_dismissed").isEmpty())
    }

    @Test
    fun `a 503 stays K, with the selection kept and the same clientId on the retry`() = runTest(dispatcher) {
        store.suggest = SuggestResult.Failed(CallFailure.Server)
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        advanceUntilIdle()
        assertTrue(vm.ui.value.sendFailed)
        assertFalse(vm.ui.value.stale)
        assertEquals(slot, vm.ui.value.slot)
        vm.suggest()
        advanceUntilIdle()
        assertEquals(listOf("id-1", "id-1"), store.suggests.map { it.clientId })
    }

    // ── process death ───────────────────────────────────────────────────────

    @Test
    fun `a selected slot, the retry's id and a place with its query come back from the saved state`() = runTest(dispatcher) {
        store.suggest = SuggestResult.Failed(CallFailure.Network)
        val handle = SavedStateHandle()
        val first = vm(ChangeKind.Time, saved = handle)
        first.open(date())
        first.selectSlot(slot)
        first.suggest()
        advanceUntilIdle()

        // The process dies: a new model over the same saved state.
        val restored = vm(ChangeKind.Time, saved = handle)
        assertEquals(slot, restored.ui.value.slot)
        restored.open(date())
        restored.suggest()
        advanceUntilIdle()
        assertEquals("the retry is the same send", listOf("id-1", "id-1"), store.suggests.map { it.clientId })
    }

    @Test
    fun `a restored place and query reload the list for that query, and the place stays only while it is listed`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = vm(ChangeKind.Place, saved = handle)
        first.open(date())
        advanceUntilIdle()
        first.setQuery("lenz")
        advanceTimeBy(301)
        advanceUntilIdle()
        first.selectPlace(NEARBY[0])

        store.placeCalls.clear()
        val restored = vm(ChangeKind.Place, saved = handle)
        assertEquals("lenz", restored.ui.value.query)
        assertEquals(NEARBY[0], restored.ui.value.place)
        restored.open(date())
        advanceUntilIdle()
        assertEquals("the list answers the restored query", listOf<String?>("lenz"), store.placeCalls)
        assertEquals("still listed: kept", NEARBY[0], restored.ui.value.place)

        store.places = { Outcome.Ok(PlacesList(true, listOf(NEARBY[1]))) }
        val gone = vm(ChangeKind.Place, saved = handle)
        gone.open(date())
        advanceUntilIdle()
        assertNull("no longer listed: dropped (R18)", gone.ui.value.place)
    }

    @Test
    fun `the search field stops at the contract's 100 characters`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Place)
        vm.open(date())
        advanceUntilIdle()
        vm.setQuery("x".repeat(130))
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(100, vm.ui.value.query.length)
        assertEquals(100, store.placeCalls.last()!!.length)
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    @Test
    fun `Cancel or back reports change_picker_dismissed with whether anything was selected, and sends nothing`() = runTest(dispatcher) {
        val empty = vm(ChangeKind.Time, ChangeEntry.ChatChip)
        empty.open(date())
        empty.cancel()
        val p = analytics.of("change_picker_dismissed").single().properties
        assertEquals("d1", p["date_id"])
        assertEquals("time", p["kind"])
        assertEquals("chat_chip", p["entry"])
        assertEquals(false, p["had_selection"])
        assertTrue(empty.ui.value.closed)

        val chosen = vm(ChangeKind.Time)
        chosen.open(date())
        chosen.selectSlot(slot)
        chosen.cancel()
        assertEquals(true, analytics.of("change_picker_dismissed").last().properties["had_selection"])
        assertTrue(store.suggests.isEmpty())
    }

    @Test
    fun `Cancel while a send is in flight is ignored`() = runTest(dispatcher) {
        val vm = vm(ChangeKind.Time)
        vm.open(date())
        vm.selectSlot(slot)
        vm.suggest()
        vm.cancel()
        assertFalse(vm.ui.value.closed)
        assertTrue(analytics.of("change_picker_dismissed").isEmpty())
    }
}
