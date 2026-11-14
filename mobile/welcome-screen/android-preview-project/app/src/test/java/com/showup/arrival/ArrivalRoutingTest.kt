/*
 * ArrivalRoutingTest.kt
 * ShowUp · what the host decides around the arrival gate (SHOWUP-195)
 */
package com.showup.arrival

import com.showup.analytics.RecordingAnalytics
import com.showup.api.Outcome
import com.showup.chat.ChatEntry
import com.showup.chat.ChatReason
import com.showup.chat.ChatViewModel
import com.showup.chat.ChipSurface
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateRead
import com.showup.date.FakeDateStore
import com.showup.chat.FakeChatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalRoutingTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val now = Instant.parse("2026-11-03T17:55:00Z")
    private val analytics = RecordingAnalytics()

    private fun date(checkedIn: Boolean = false) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = true,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null,
    )

    private fun gate(checkedIn: Boolean = false) = ArrivalGateViewModel(
        store = FakeArrivalStore(), reader = { null }, analytics = analytics, clock = { now }, newId = { "id" },
    ).also { it.bind(date(checkedIn)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `only the arrived chip before check-in is gated`() {
        assertTrue(arrivalChipGated(ChatReason.Arrived, gate()))
        assertFalse(arrivalChipGated(ChatReason.Arrived, gate(checkedIn = true)))
        assertFalse(arrivalChipGated(ChatReason.RunningLate, gate()))
        assertFalse(arrivalChipGated(ChatReason.CantMakeIt, gate()))
    }

    @Test
    fun `the chip event comes before the confirm event and nothing is drafted`() = runTest(dispatcher) {
        val dates = FakeDateStore().apply { read = Outcome.Ok(DateRead.Active(date())) }
        val chat = ChatViewModel(FakeChatStore(), dates, analytics = analytics, clock = { now }, zone = { ZoneId.of("Europe/Berlin") })
        chat.open(date(), ChatEntry.OpenChat)
        runCurrent()
        val before = analytics.names().size
        val gate = gate()

        onChatChip(ChatReason.Arrived, ChipSurface.Entry, gate, chat::pick)
        chat.onScreen(false)

        assertEquals(listOf("action_chip_used", "arrival_confirm_shown"), analytics.names().drop(before))
        assertEquals("", chat.ui.value.draft)
        assertEquals(null, chat.ui.value.selected)
        assertTrue(gate.ui.value.phase is GatePhase.Confirm)
    }

    @Test
    fun `a gated chip puts the keyboard away before the sheet opens, an ungated one does not`() {
        val gate = gate()
        val order = mutableListOf<String>()
        onChatChip(ChatReason.Arrived, ChipSurface.Entry, gate, { _, _, _ -> order += "pick" }, beforeGate = {
            order += "keyboard:" + (gate.ui.value.phase is GatePhase.Idle)
        })
        assertEquals(listOf("pick", "keyboard:true"), order)
        assertTrue(gate.ui.value.phase is GatePhase.Confirm)

        val other = mutableListOf<String>()
        onChatChip(ChatReason.RunningLate, ChipSurface.Entry, gate(), { _, _, _ -> }, beforeGate = { other += "keyboard" })
        assertTrue(other.isEmpty())
    }

    @Test
    fun `an undo lands on the Date screen from the chat and stays put elsewhere`() {
        assertEquals(UndoLanding.LeaveChat, undoLanding(undone = true, chatOnTop = true))
        assertEquals(UndoLanding.ConsumeOnly, undoLanding(undone = true, chatOnTop = false))
        assertEquals(UndoLanding.None, undoLanding(undone = false, chatOnTop = true))
        assertEquals(UndoLanding.None, undoLanding(undone = false, chatOnTop = false))
    }

    @Test
    fun `routeUndo from the chat leaves it then consumes once, on the Date screen it only consumes`() {
        val calls = mutableListOf<String>()
        routeUndo(true, chatOnTop = true, leaveChat = { calls += "leave" }, consume = { calls += "consume" })
        assertEquals(listOf("leave", "consume"), calls)
        calls.clear()
        routeUndo(true, chatOnTop = false, leaveChat = { calls += "leave" }, consume = { calls += "consume" })
        assertEquals(listOf("consume"), calls)
        calls.clear()
        routeUndo(false, chatOnTop = true, leaveChat = { calls += "leave" }, consume = { calls += "consume" })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a stop that is only a rebuild keeps the done sheet and commits nothing`() = runTest(dispatcher) {
        val store = FakeArrivalStore()
        val g = ArrivalGateViewModel(store, { LocationFix(52.5, 13.4, 5.0) }, analytics, { now }, { "id" }).also { it.bind(date()) }
        g.open(ArrivalEntry.DateScreen)
        g.yes()
        runCurrent()
        assertTrue(g.ui.value.phase is GatePhase.Done)

        onAppStopped(g, changingConfigurations = true)
        runCurrent()
        assertTrue(g.ui.value.phase is GatePhase.Done)
        assertEquals(0, store.commitCalls)

        onAppStopped(g, changingConfigurations = false)
        runCurrent()
        assertEquals(GatePhase.Idle, g.ui.value.phase)
        assertEquals(1, store.commitCalls)
    }
}
