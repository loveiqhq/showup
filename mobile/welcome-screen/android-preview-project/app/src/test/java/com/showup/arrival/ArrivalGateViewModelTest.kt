/*
 * ArrivalGateViewModelTest.kt
 * ShowUp · the arrival gate: confirm, store, close, undo, retries (SHOWUP-195)
 *
 * Fake store, fake reader, injected clock, virtual time. No test reads a coordinate out of an event:
 * the payloads are asserted whole.
 */
package com.showup.arrival

import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FakeArrivalStore : ArrivalStore {
    data class Stored(val dateId: String, val fix: LocationFix, val entry: ArrivalEntry, val clientId: String)

    val stored = mutableListOf<Stored>()
    val checkIns: MutableList<CheckInResult> = mutableListOf()
    val undos: MutableList<Outcome<Unit>> = mutableListOf()
    val commits: MutableList<Outcome<Unit>> = mutableListOf()
    var undoCalls = 0
    var commitCalls = 0

    override suspend fun checkIn(dateId: String, fix: LocationFix, entry: ArrivalEntry, clientId: String): CheckInResult {
        stored += Stored(dateId, fix, entry, clientId)
        return if (checkIns.isEmpty()) CheckInResult.Stored else checkIns.removeAt(0)
    }

    override suspend fun undo(dateId: String): Outcome<Unit> {
        undoCalls++
        return if (undos.isEmpty()) Outcome.Ok(Unit) else undos.removeAt(0)
    }

    override suspend fun commit(dateId: String): Outcome<Unit> {
        commitCalls++
        return if (commits.isEmpty()) Outcome.Ok(Unit) else commits.removeAt(0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private var now = Instant.parse("2026-11-03T17:55:00Z") // 5 minutes before
    private val analytics = RecordingAnalytics()
    private val store = FakeArrivalStore()
    private val fix = LocationFix(52.5186, 13.3761, 8.0)
    private var reads = 0
    private var readAnswer: suspend () -> LocationFix? = { fix }
    private var ids = 0

    private fun date(id: String = "d1", checkedIn: Boolean = false) = ActiveDate(
        id = id, meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = true,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null,
    )

    private fun vm() = ArrivalGateViewModel(
        store = store,
        reader = { reads++; readAnswer() },
        analytics = analytics,
        clock = { now },
        newId = { "id-${++ids}" },
    )

    private fun TestScope.ready(checkedIn: Boolean = false): ArrivalGateViewModel {
        val vm = vm()
        vm.bind(date(checkedIn = checkedIn))
        runCurrent()
        return vm
    }

    private fun TestScope.confirmed(entry: ArrivalEntry = ArrivalEntry.DateScreen): ArrivalGateViewModel {
        val vm = ready()
        vm.open(entry)
        return vm
    }

    private fun TestScope.done(): ArrivalGateViewModel {
        val vm = confirmed()
        vm.yes()
        runCurrent()
        assertTrue(vm.ui.value.phase is GatePhase.Done)
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── opening ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `open on a checked-in date returns false and fires nothing`() = runTest(dispatcher) {
        val vm = ready(checkedIn = true)
        assertTrue(vm.checkedIn())
        assertFalse(vm.open(ArrivalEntry.DateScreen))
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `open shows B and fires arrival_confirm_shown with the registry payload and no coordinate`() = runTest(dispatcher) {
        val vm = ready()
        assertTrue(vm.open(ArrivalEntry.ChatChip, note = "Check in first"))
        assertEquals(GatePhase.Confirm(ArrivalEntry.ChatChip, locError = false, busy = false, note = "Check in first"), vm.ui.value.phase)
        val shown = analytics.of("arrival_confirm_shown").single().properties
        assertEquals("d1", shown["date_id"])
        assertEquals("chat_chip", shown["entry"])
        assertEquals(-5, shown["minutes_vs_start"])
        assertFalse(shown.keys.any { it.contains("lat") || it.contains("lon") || it.contains("accuracy") || it.contains("location") })
    }

    @Test
    fun `a second open while a sheet is up does nothing and returns true`() = runTest(dispatcher) {
        val vm = confirmed()
        assertTrue(vm.open(ArrivalEntry.ChatChip))
        assertEquals(1, analytics.of("arrival_confirm_shown").size)
        assertEquals(ArrivalEntry.DateScreen, (vm.ui.value.phase as GatePhase.Confirm).entry)
    }

    @Test
    fun `Not yet closes B with no event and no store call`() = runTest(dispatcher) {
        val vm = confirmed()
        analytics.clear()
        vm.notYet()
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertEquals(emptyList<String>(), analytics.names())
        assertEquals(0, store.stored.size)
    }

    // ── yes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Yes fires submitted, goes busy, reads once, stores and shows D`() = runTest(dispatcher) {
        val vm = confirmed()
        vm.yes()
        assertEquals(true, (vm.ui.value.phase as GatePhase.Confirm).busy)
        val submitted = analytics.of("arrival_confirm_submitted").single().properties
        assertEquals("d1", submitted["date_id"])
        assertEquals("date_screen", submitted["entry"])
        assertEquals(-5, submitted["minutes_vs_start"])
        runCurrent()
        assertEquals(1, reads)
        assertEquals(listOf(FakeArrivalStore.Stored("d1", fix, ArrivalEntry.DateScreen, "id-1")), store.stored)
        assertEquals(GatePhase.Done(ArrivalEntry.DateScreen), vm.ui.value.phase)
        assertFalse(vm.ui.value.committed)
    }

    @Test
    fun `yes while busy does nothing`() = runTest(dispatcher) {
        readAnswer = { delay(1_000); fix }
        val vm = confirmed()
        vm.yes()
        vm.yes()
        vm.notYet()
        runCurrent()
        assertTrue((vm.ui.value.phase as GatePhase.Confirm).busy)
        advanceTimeBy(1_001)
        assertEquals(1, reads)
        assertEquals(1, store.stored.size)
        assertEquals(1, analytics.of("arrival_confirm_submitted").size)
        assertTrue(vm.ui.value.phase is GatePhase.Done)
    }

    @Test
    fun `no fix is C, nothing stored, and Try again fires submitted again and no new shown`() = runTest(dispatcher) {
        readAnswer = { null }
        val vm = confirmed()
        vm.yes()
        runCurrent()
        assertEquals(GatePhase.Confirm(ArrivalEntry.DateScreen, locError = true, busy = false, note = null, attempt = 1), vm.ui.value.phase)
        assertEquals(0, store.stored.size)
        readAnswer = { fix }
        vm.yes()
        runCurrent()
        assertEquals(2, analytics.of("arrival_confirm_submitted").size)
        assertEquals(1, analytics.of("arrival_confirm_shown").size)
        assertEquals(1, store.stored.size)
        assertTrue(vm.ui.value.phase is GatePhase.Done)
    }

    @Test
    fun `a fix after the timeout is ignored`() = runTest(dispatcher) {
        readAnswer = { delay(LOCATION_FIX_TIMEOUT_MS + 1); fix }
        val vm = confirmed()
        vm.yes()
        advanceTimeBy(LOCATION_FIX_TIMEOUT_MS + 2)
        runCurrent()
        assertEquals(GatePhase.Confirm(ArrivalEntry.DateScreen, locError = true, busy = false, note = null, attempt = 1), vm.ui.value.phase)
        assertEquals(0, store.stored.size)
    }

    @Test
    fun `a failed store is C and nothing is committed`() = runTest(dispatcher) {
        store.checkIns += CheckInResult.Failed(CallFailure.Network)
        val vm = confirmed()
        vm.yes()
        runCurrent()
        assertEquals(GatePhase.Confirm(ArrivalEntry.DateScreen, locError = true, busy = false, note = null, attempt = 1), vm.ui.value.phase)
        assertEquals(0, store.commitCalls)
    }

    @Test
    fun `already checked in closes the sheet and counts as checked in`() = runTest(dispatcher) {
        store.checkIns += CheckInResult.AlreadyCheckedIn
        val vm = confirmed()
        vm.yes()
        runCurrent()
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertTrue(vm.ui.value.committed)
        assertTrue(vm.checkedIn())
    }

    @Test
    fun `every tap gets its own client id`() = runTest(dispatcher) {
        store.checkIns += CheckInResult.Failed(CallFailure.Server)
        val vm = confirmed()
        vm.yes()
        runCurrent()
        vm.yes()
        runCurrent()
        assertEquals(listOf("id-1", "id-2"), store.stored.map { it.clientId })
    }

    // ── close ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Close commits once and the date counts as checked in`() = runTest(dispatcher) {
        val vm = done()
        vm.close()
        runCurrent()
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertTrue(vm.ui.value.committed)
        assertEquals(1, store.commitCalls)
        assertTrue(vm.checkedIn())
    }

    @Test
    fun `a poll before the commit lands keeps the check-in`() = runTest(dispatcher) {
        store.commits += Outcome.Failed(CallFailure.Network)
        val vm = done()
        vm.close()
        vm.bind(date(checkedIn = false))
        runCurrent()
        assertTrue(vm.checkedIn())
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertFalse(vm.open(ArrivalEntry.DateScreen))
    }

    @Test
    fun `a failed commit is retried on the schedule until it lands`() = runTest(dispatcher) {
        store.commits += listOf(Outcome.Failed(CallFailure.Network), Outcome.Failed(CallFailure.Server))
        val vm = done()
        vm.close()
        runCurrent()
        assertEquals(1, store.commitCalls)
        advanceTimeBy(1_999)
        assertEquals(1, store.commitCalls)
        advanceTimeBy(2)
        assertEquals(2, store.commitCalls)
        advanceTimeBy(3_999)
        assertEquals(2, store.commitCalls)
        advanceTimeBy(2)
        assertEquals(3, store.commitCalls)
        advanceTimeBy(60_000)
        assertEquals("landed: no more", 3, store.commitCalls)
    }

    @Test
    fun `close does nothing outside D`() = runTest(dispatcher) {
        val vm = confirmed()
        vm.close()
        runCurrent()
        assertTrue(vm.ui.value.phase is GatePhase.Confirm)
        assertEquals(0, store.commitCalls)
    }

    // ── undo ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Undo closes D at once, deletes once and the date is not checked in`() = runTest(dispatcher) {
        val vm = done()
        vm.undo()
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertTrue(vm.ui.value.undone)
        runCurrent()
        assertEquals(1, store.undoCalls)
        assertEquals(0, store.commitCalls)
        assertFalse(vm.checkedIn())
        vm.consumeUndone()
        assertFalse(vm.ui.value.undone)
    }

    @Test
    fun `an undo that fails is retried and never committed`() = runTest(dispatcher) {
        store.undos += listOf(Outcome.Failed(CallFailure.Network), Outcome.Failed(CallFailure.Network))
        val vm = done()
        vm.undo()
        runCurrent()
        assertEquals(1, store.undoCalls)
        advanceTimeBy(2_001)
        assertEquals(2, store.undoCalls)
        advanceTimeBy(4_001)
        assertEquals(3, store.undoCalls)
        advanceTimeBy(120_000)
        assertEquals(3, store.undoCalls)
        vm.leftApp()
        vm.close()
        runCurrent()
        assertEquals(0, store.commitCalls)
        assertFalse(vm.checkedIn())
    }

    @Test
    fun `a Yes after an undo stores again, and cancels a delete still retrying`() = runTest(dispatcher) {
        store.undos += Outcome.Failed(CallFailure.Network)
        val vm = done()
        vm.undo()
        runCurrent()
        assertEquals(1, store.undoCalls)
        assertTrue(vm.open(ArrivalEntry.DateScreen))
        vm.yes()
        runCurrent()
        assertEquals(2, store.stored.size)
        assertTrue(vm.ui.value.phase is GatePhase.Done)
        advanceTimeBy(120_000)
        assertEquals("the retrying delete was cancelled", 1, store.undoCalls)
    }

    // ── leaving the app ──────────────────────────────────────────────────────────────────────

    @Test
    fun `leaving the app on D commits once`() = runTest(dispatcher) {
        val vm = done()
        vm.leftApp()
        vm.leftApp()
        runCurrent()
        assertEquals(1, store.commitCalls)
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertTrue(vm.ui.value.committed)
    }

    @Test
    fun `leaving the app on Idle or B does nothing`() = runTest(dispatcher) {
        val vm = ready()
        vm.leftApp()
        vm.open(ArrivalEntry.DateScreen)
        vm.leftApp()
        runCurrent()
        assertTrue(vm.ui.value.phase is GatePhase.Confirm)
        assertEquals(0, store.commitCalls)
    }

    @Test
    fun `leaving during the read and a store that lands away commits at once, with no D`() = runTest(dispatcher) {
        readAnswer = { delay(5_000); fix }
        val vm = confirmed()
        vm.yes()
        runCurrent()
        vm.leftApp()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, store.stored.size)
        assertEquals(1, store.commitCalls)
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
        assertTrue(vm.ui.value.committed)
    }

    @Test
    fun `leaving during the read and coming back before the store lands shows D and commits nothing`() = runTest(dispatcher) {
        readAnswer = { delay(5_000); fix }
        val vm = confirmed()
        vm.yes()
        runCurrent()
        vm.leftApp()
        vm.foregrounded()
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(vm.ui.value.phase is GatePhase.Done)
        assertEquals(0, store.commitCalls)
        assertFalse(vm.ui.value.committed)
    }

    @Test
    fun `each failed read counts an attempt, so the sheet can announce the second one`() = runTest(dispatcher) {
        readAnswer = { null }
        val vm = confirmed()
        vm.yes()
        runCurrent()
        assertEquals(1, (vm.ui.value.phase as GatePhase.Confirm).attempt)
        vm.yes()
        runCurrent()
        assertEquals(2, (vm.ui.value.phase as GatePhase.Confirm).attempt)
    }

    // ── binding ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `binding another date resets the phase and committed`() = runTest(dispatcher) {
        val vm = done()
        vm.close()
        runCurrent()
        assertTrue(vm.ui.value.committed)
        vm.bind(date(id = "d2"))
        assertEquals(GateUi(), vm.ui.value)
        assertFalse(vm.checkedIn())
        assertTrue(vm.open(ArrivalEntry.DateScreen))
    }

    @Test
    fun `binding the same date again keeps the sheet`() = runTest(dispatcher) {
        val vm = confirmed()
        vm.bind(date())
        assertTrue(vm.ui.value.phase is GatePhase.Confirm)
    }

    @Test
    fun `binding another date cancels a read in flight`() = runTest(dispatcher) {
        readAnswer = { delay(5_000); fix }
        val vm = confirmed()
        vm.yes()
        runCurrent()
        vm.bind(date(id = "d2"))
        advanceTimeBy(6_000)
        assertEquals(0, store.stored.size)
        assertEquals(GatePhase.Idle, vm.ui.value.phase)
    }

    // ── the work scope (commit and undo outlive the model) ────────────────────────────────────

    private fun TestScope.owned(work: CoroutineScope?): Pair<ViewModelStore, ArrivalGateViewModel> {
        val owner = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArrivalGateViewModel(
                store = store,
                reader = { fix },
                analytics = analytics,
                clock = { now },
                newId = { "id-${++ids}" },
                work = work,
            ) as T
        }
        val vm = ViewModelProvider(owner, factory)[ArrivalGateViewModel::class.java]
        vm.bind(date())
        runCurrent()
        vm.open(ArrivalEntry.DateScreen)
        vm.yes()
        runCurrent()
        assertTrue(vm.ui.value.phase is GatePhase.Done)
        return owner to vm
    }

    @Test
    fun `a retrying commit continues after the model is cleared, when it has a work scope`() = runTest(dispatcher) {
        store.commits += List(3) { Outcome.Failed(CallFailure.Network) }
        val (owner, vm) = owned(backgroundScope)
        vm.close()
        runCurrent()
        assertEquals(1, store.commitCalls)
        owner.clear()
        advanceTimeBy(2_001)
        assertEquals(2, store.commitCalls)
        advanceTimeBy(4_001)
        assertEquals(3, store.commitCalls)
        advanceTimeBy(8_001)
        assertEquals("landed on the fourth", 4, store.commitCalls)
        advanceTimeBy(60_000)
        assertEquals(4, store.commitCalls)
    }

    @Test
    fun `a retrying undo continues after the model is cleared, when it has a work scope`() = runTest(dispatcher) {
        store.undos += List(2) { Outcome.Failed(CallFailure.Network) }
        val (owner, vm) = owned(backgroundScope)
        vm.undo()
        runCurrent()
        owner.clear()
        advanceTimeBy(2_001)
        advanceTimeBy(4_001)
        assertEquals(3, store.undoCalls)
    }

    @Test
    fun `without a work scope clearing the model ends the retry`() = runTest(dispatcher) {
        store.commits += List(3) { Outcome.Failed(CallFailure.Network) }
        val (owner, vm) = owned(null)
        vm.close()
        runCurrent()
        owner.clear()
        advanceTimeBy(60_000)
        assertEquals(1, store.commitCalls)
    }

    @Test
    fun `binding another date still cancels a retrying commit on the work scope`() = runTest(dispatcher) {
        store.commits += List(5) { Outcome.Failed(CallFailure.Network) }
        val (_, vm) = owned(backgroundScope)
        vm.close()
        runCurrent()
        assertEquals(1, store.commitCalls)
        vm.bind(date(id = "d2"))
        advanceTimeBy(120_000)
        assertEquals(1, store.commitCalls)
    }
}
