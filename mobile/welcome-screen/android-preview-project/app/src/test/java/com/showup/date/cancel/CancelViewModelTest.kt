/*
 * CancelViewModelTest.kt
 * ShowUp · Date 07's model: the guard's open and its exits, the picker over it, the cancel in flight, E and its
 * retry, the two 409s, the landing and process death (SHOWUP-198)
 */
package com.showup.date.cancel

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.change.ChangeKind
import com.showup.change.DateChange
import com.showup.date.ActiveDate
import com.showup.date.CancelResult
import com.showup.date.CancelStore
import com.showup.date.DateArrival
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FakeCancelStore : CancelStore {
    val calls = mutableListOf<String>()
    val answers = mutableListOf<CancelResult>()
    var holdMs = 0L

    override suspend fun cancel(dateId: String): CancelResult {
        calls += dateId
        if (holdMs > 0) delay(holdMs)
        return if (answers.isEmpty()) CancelResult.Cancelled(outcome()) else answers.removeAt(0)
    }

    companion object {
        fun outcome() = EndedOutcome("d1", "Leonie", endedByYou = true, reason = OutcomeReason.Cancelled)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CancelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-21T18:00:00Z")
    private val early = meet.minusSeconds(3 * 3600)
    private val analytics = RecordingAnalytics()
    private val store = FakeCancelStore()
    private var rereads = 0

    private fun change(allowed: Boolean = true) = DateChange(
        cutoffAt = meet.minusSeconds(30 * 60), allowed = allowed, slots = listOf(meet.plusSeconds(900)),
        timeWas = null, placeWas = null, pending = emptyList(),
    )

    private fun date(
        id: String = "d1",
        checkedIn: Boolean = false,
        endsEnabled: Boolean = true,
        chatOpen: Boolean = false,
        penalty: Int = 10,
        change: DateChange? = change(),
    ) = ActiveDate(
        id = id, meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null, endsEnabled = endsEnabled,
        change = change, cancelPenaltyPoints = penalty,
    )

    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    private fun fresh(saved: SavedStateHandle = SavedStateHandle()) =
        CancelViewModel(store, analytics, saved, reread = { rereads++ })

    private fun TestScope.vm(
        date: ActiveDate = date(),
        now: Instant = early,
        saved: SavedStateHandle = SavedStateHandle(),
    ): CancelViewModel {
        val vm = fresh(saved)
        vm.bind(date, now)
        runCurrent()
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── the guard opens ─────────────────────────────────────────────────────────────────────

    @Test
    fun `open shows the sheet and fires cancel_guard_shown with the chat, the penalty and the minutes`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = true, penalty = 7))
        vm.open()
        assertTrue(vm.ui.value.open)
        val e = analytics.of("cancel_guard_shown").single().properties
        assertEquals("d1", e["date_id"])
        assertEquals(true, e["chat_open"])
        assertEquals(7, e["penalty_points"])
        assertEquals(180, e["minutes_before_start"])
    }

    @Test
    fun `chat_open is false while the chat is locked`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = false))
        vm.open()
        assertEquals(false, analytics.of("cancel_guard_shown").single().properties["chat_open"])
    }

    @Test
    fun `minutes_before_start is negative after the meet time`() = runTest(dispatcher) {
        val vm = vm(date(), now = meet.plusSeconds(10 * 60))
        vm.open()
        assertEquals(-10, analytics.of("cancel_guard_shown").single().properties["minutes_before_start"])
    }

    @Test
    fun `it fires on every open, not once per session`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.keep()
        vm.open()
        vm.dismiss()
        vm.open()
        assertEquals(3, analytics.of("cancel_guard_shown").size)
    }

    @Test
    fun `a second open while it is up fires nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.open()
        assertEquals(1, analytics.of("cancel_guard_shown").size)
    }

    @Test
    fun `nothing opens when the ends are off, after this user's check-in, or with no date`() = runTest(dispatcher) {
        val off = vm(date(endsEnabled = false))
        off.open()
        val checked = vm(date(checkedIn = true))
        checked.open()
        val unbound = fresh()
        unbound.open()
        assertFalse(off.ui.value.open || checked.ui.value.open || unbound.ui.value.open)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `the match's check-in does not stop it opening`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = true).copy(arrival = DateArrival(meet.minusSeconds(600))))
        vm.open()
        assertTrue(vm.ui.value.open)
    }

    // ── the exits ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Don't cancel my date closes and fires one abandoned with exit keep`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.keep()
        assertFalse(vm.ui.value.open)
        val e = analytics.of("cancel_abandoned_at_guard").single().properties
        assertEquals("d1", e["date_id"])
        assertEquals("keep", e["exit"])
        assertEquals(false, e["change_requested"])
        assertEquals(emptyList<String>(), store.calls)
    }

    @Test
    fun `the scrim and a swipe close with exit dismissed, once`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.dismiss()
        vm.dismiss()
        vm.keep()
        val all = analytics.of("cancel_abandoned_at_guard")
        assertEquals(1, all.size)
        assertEquals("dismissed", all.single().properties["exit"])
    }

    @Test
    fun `Message instead closes the flow, asks the host for the chat and fires exit message`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = true))
        vm.open()
        vm.messageInstead()
        assertFalse(vm.ui.value.open)
        assertTrue(vm.ui.value.openChat)
        assertEquals("message", analytics.of("cancel_abandoned_at_guard").single().properties["exit"])
        assertEquals(emptyList<String>(), store.calls)
        vm.consumeOpenChat()
        assertFalse(vm.ui.value.openChat)
    }

    @Test
    fun `Message instead is inert while the chat is locked and the change is open`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = false))
        vm.open()
        vm.messageInstead()
        assertTrue(vm.ui.value.open)
        assertFalse(vm.ui.value.openChat)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
    }

    @Test
    fun `Message instead answers at the cutoff, the chat being open by then (I)`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = false), now = meet.minusSeconds(10 * 60))
        vm.open()
        assertTrue(vm.ui.value.changeClosed)
        vm.messageInstead()
        assertTrue(vm.ui.value.openChat)
    }

    @Test
    fun `the clock passing the cutoff while the sheet is open closes the change in place`() = runTest(dispatcher) {
        val vm = vm(date(chatOpen = false), now = meet.minusSeconds(31 * 60))
        vm.open()
        assertFalse(vm.ui.value.changeClosed)
        assertFalse(vm.ui.value.showMessage)
        vm.bind(date(chatOpen = false), meet.minusSeconds(30 * 60))
        assertTrue(vm.ui.value.changeClosed)
        assertTrue(vm.ui.value.showMessage)
        assertTrue(vm.ui.value.open)
    }

    // ── the pickers over the sheet ──────────────────────────────────────────────────────────

    @Test
    fun `a tile asks the host for its picker and is not an exit`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.picker(ChangeKind.Time)
        assertEquals(ChangeKind.Time, vm.ui.value.openPicker)
        assertTrue(vm.ui.value.open)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
        vm.consumePicker()
        assertNull(vm.ui.value.openPicker)
        // Back from the picker unchanged: the sheet is still there and nothing was sent.
        assertTrue(vm.ui.value.open)
        assertNull(vm.ui.value.sent)
    }

    @Test
    fun `a tile that Date 05 hides asks for nothing`() = runTest(dispatcher) {
        val vm = vm(date(change = change(allowed = false)))
        vm.open()
        vm.picker(ChangeKind.Place)
        assertNull(vm.ui.value.openPicker)
    }

    @Test
    fun `a sent request shows D with its value, keeps the sheet open and marks the session`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.picker(ChangeKind.Time)
        vm.consumePicker()
        vm.pickerSent(ChangeKind.Time, "20:00")
        assertTrue(vm.ui.value.open)
        assertEquals(SentRequest(ChangeKind.Time, "20:00"), vm.ui.value.sent)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
        vm.keep()
        assertEquals(true, analytics.of("cancel_abandoned_at_guard").single().properties["change_requested"])
    }

    @Test
    fun `a new open starts clean - no sent value, no change_requested`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.pickerSent(ChangeKind.Place, "Bar Lenz")
        vm.dismiss()
        vm.open()
        assertNull(vm.ui.value.sent)
        vm.keep()
        assertEquals(false, analytics.of("cancel_abandoned_at_guard").last().properties["change_requested"])
    }

    // ── Cancel anyway ─────────────────────────────────────────────────────────────────────

    @Test
    fun `success lands the canceller, closes the sheet and fires no abandoned and no date_cancelled`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertEquals(listOf("d1"), store.calls)
        assertFalse(vm.ui.value.open)
        assertFalse(vm.ui.value.cancelling)
        assertEquals(FakeCancelStore.outcome(), vm.ui.value.landed)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
        assertFalse(analytics.names().contains("date_cancelled"))
        vm.consumeLanded()
        assertNull(vm.ui.value.landed)
    }

    @Test
    fun `a cancel after a change request is still a cancel`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.pickerSent(ChangeKind.Time, "20:00")
        vm.cancel()
        runCurrent()
        assertEquals(listOf("d1"), store.calls)
        assertEquals(FakeCancelStore.outcome(), vm.ui.value.landed)
    }

    @Test
    fun `both buttons are inert while the cancel is in flight and a second tap sends nothing`() = runTest(dispatcher) {
        store.holdMs = 1_000
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertTrue(vm.ui.value.cancelling)
        vm.cancel()
        vm.keep()
        vm.dismiss()
        runCurrent()
        assertTrue(vm.ui.value.open)
        assertEquals(1, store.calls.size)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
        advanceTimeBy(1_100)
        runCurrent()
        assertFalse(vm.ui.value.open)
    }

    @Test
    fun `a failure shows E on the same sheet, nothing is cancelled and nothing new fires`() = runTest(dispatcher) {
        store.answers += CancelResult.Failed(CallFailure.Network)
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertTrue(vm.ui.value.open)
        assertTrue(vm.ui.value.failed)
        assertFalse(vm.ui.value.cancelling)
        assertNull(vm.ui.value.landed)
        assertEquals(listOf("cancel_guard_shown"), analytics.names())
    }

    @Test
    fun `the retry sends again and a success clears E`() = runTest(dispatcher) {
        store.answers += CancelResult.Failed(CallFailure.Server)
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        vm.cancel()
        runCurrent()
        assertEquals(2, store.calls.size)
        assertFalse(vm.ui.value.failed)
        assertFalse(vm.ui.value.open)
        assertEquals(FakeCancelStore.outcome(), vm.ui.value.landed)
    }

    @Test
    fun `E stays up while the retry is in flight, and Don't cancel still works after it`() = runTest(dispatcher) {
        store.answers += CancelResult.Failed(CallFailure.Network)
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        store.holdMs = 1_000
        store.answers += CancelResult.Failed(CallFailure.Network)
        vm.cancel()
        runCurrent()
        assertTrue(vm.ui.value.failed)
        assertTrue(vm.ui.value.cancelling)
        advanceTimeBy(1_100)
        runCurrent()
        vm.keep()
        assertFalse(vm.ui.value.open)
        assertEquals("keep", analytics.of("cancel_abandoned_at_guard").single().properties["exit"])
    }

    @Test
    fun `409 checked_in closes the sheet and reads the date again, so the link goes`() = runTest(dispatcher) {
        store.answers += CancelResult.Refused("checked_in")
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertFalse(vm.ui.value.open)
        assertFalse(vm.ui.value.cancelling)
        assertFalse(vm.ui.value.failed)
        assertNull(vm.ui.value.landed)
        assertEquals(1, rereads)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
    }

    @Test
    fun `409 date_not_active reads the date again, which lands on H as the cancelled-on person`() = runTest(dispatcher) {
        store.answers += CancelResult.Refused("date_not_active")
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertFalse(vm.ui.value.open)
        assertNull(vm.ui.value.landed)
        assertEquals(1, rereads)
    }

    @Test
    fun `a 404 closes and reads again, an unknown refusal is E`() = runTest(dispatcher) {
        store.answers += CancelResult.Refused("not_found")
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        assertFalse(vm.ui.value.open)
        assertEquals(1, rereads)

        store.answers += CancelResult.Refused("unknown")
        vm.open()
        vm.cancel()
        runCurrent()
        assertTrue(vm.ui.value.open)
        assertTrue(vm.ui.value.failed)
    }

    // ── a date that changes under the sheet ──────────────────────────────────────────────────

    @Test
    fun `a check-in read while the sheet is open closes it without an exit event`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.bind(date(checkedIn = true), early)
        assertFalse(vm.ui.value.open)
        assertEquals(emptyList<String>(), analytics.of("cancel_abandoned_at_guard"))
    }

    @Test
    fun `the ends switched off closes it`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.bind(date(endsEnabled = false), early)
        assertFalse(vm.ui.value.open)
    }

    @Test
    fun `another date resets the sheet`() = runTest(dispatcher) {
        val vm = vm()
        vm.open()
        vm.pickerSent(ChangeKind.Time, "20:00")
        vm.bind(date(id = "d2"), early)
        assertFalse(vm.ui.value.open)
        assertNull(vm.ui.value.sent)
    }

    // ── process death ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the open sheet and the sent value survive process death, with no second guard_shown`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.open()
        first.pickerSent(ChangeKind.Place, "Bar Lenz")
        analytics.clear()

        val restored = fresh(saved.copy())
        assertTrue(restored.ui.value.open)
        assertEquals(SentRequest(ChangeKind.Place, "Bar Lenz"), restored.ui.value.sent)
        restored.bind(date(), early)
        runCurrent()
        assertTrue(restored.ui.value.open)
        assertEquals(SentRequest(ChangeKind.Place, "Bar Lenz"), restored.ui.value.sent)
        assertEquals(emptyList<String>(), analytics.names())
        // The request was sent before: the abandon still reports it.
        restored.keep()
        assertEquals(true, analytics.of("cancel_abandoned_at_guard").single().properties["change_requested"])
    }

    @Test
    fun `a restored sheet for a date that is now checked in, or another date, closes`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        vm(saved = saved).open()
        val restoredChecked = fresh(saved.copy())
        restoredChecked.bind(date(checkedIn = true), early)
        assertFalse(restoredChecked.ui.value.open)

        val restoredOther = fresh(saved.copy())
        restoredOther.bind(date(id = "d9"), early)
        assertFalse(restoredOther.ui.value.open)
    }

    @Test
    fun `a closed sheet leaves nothing to restore`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.open()
        vm.keep()
        assertFalse(fresh(saved.copy()).ui.value.open)
    }

    // ── what is never sent ──────────────────────────────────────────────────────────────────

    @Test
    fun `no retired event and no server event is ever fired from here`() = runTest(dispatcher) {
        store.answers += CancelResult.Failed(CallFailure.Network)
        val vm = vm()
        vm.open()
        vm.cancel()
        runCurrent()
        vm.cancel()
        runCurrent()
        val banned = setOf("date_cancelled", "cancel_link_tapped", "partner_cancellation_received", "rematch_started", "date_ended", "date_ended_in_chat")
        assertTrue(analytics.names().none { it in banned })
        analytics.all().forEach { e ->
            assertFalse(e.properties.keys.any { it in setOf("initiator", "reason", "match_id", "user_id") })
        }
    }
}
