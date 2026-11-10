/*
 * ChatViewModelTest.kt
 * ShowUp · the chat's model: selection, drafts, the guide lock, the end card, polling, the end and
 * tracking (SHOWUP-194)
 *
 * The clock is injected and the poll runs on the test scheduler, so "every 5 s" is a step of virtual
 * time. The poll never ends by itself: each test stops it, never waits for the scheduler to go idle.
 */
package com.showup.chat

import com.showup.analytics.JsonNull
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateRead
import com.showup.date.EndedInChat
import com.showup.date.FakeDateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private var now = Instant.parse("2026-11-03T16:42:00Z") // 17:42 in Berlin: 78 min to go
    private val analytics = RecordingAnalytics()
    private val store = FakeChatStore()
    private val dates = FakeDateStore()

    private val date = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = true,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = emptyList(), sharedInterests = emptyList(), unread = null, checkedIn = false,
        chatTutorialSeen = true,
    )

    private fun theirs(id: String, reason: ChatReason?, body: String = "From Leonie $id") =
        ChatMessage(id, reason, body, fromYou = false, sentAt = now, read = null)

    private fun mine(id: String, reason: ChatReason?, body: String = "From me $id") =
        ChatMessage(id, reason, body, fromYou = true, sentAt = now, read = false)

    private val models = mutableListOf<ChatViewModel>()

    private fun vm(ledger: ReceivedLedger = InMemoryReceivedLedger()) =
        ChatViewModel(store, dates, analytics = analytics, clock = { now }, zone = { ZoneId.of("Europe/Berlin") }, received = ledger)
            .also { models += it }

    /**
     * [runTest] that stops every poll its models started, whatever happens. A poll left running keeps
     * the scheduler busy forever, so an assertion that failed before a test's own stop would hang the
     * run instead of failing it.
     */
    private fun chatTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            models.forEach { it.onScreen(false) }
        }
    }

    /** Opens the chat and lets the first read land. */
    private fun TestScope.opened(entry: ChatEntry = ChatEntry.OpenChat): ChatViewModel {
        dates.read = Outcome.Ok(DateRead.Active(date))
        val vm = vm()
        vm.open(date, entry)
        runCurrent()
        return vm
    }

    private fun TestScope.stop(vm: ChatViewModel) {
        vm.onScreen(false)
        runCurrent()
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── selection, drafts and the guide ───────────────────────────────────────────────────────

    @Test
    fun `a chip selects itself and replaces the field with its draft`() = chatTest {
        val vm = opened()
        vm.edit("Something I typed")
        vm.pick(ChatReason.RunningLate, ChipSurface.Dock)
        assertEquals(ChatReason.RunningLate, vm.ui.value.selected)
        assertEquals("Running about 10 min late — sorry! Almost there.", vm.ui.value.draft)
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        assertEquals("On my way now — see you in a few!", vm.ui.value.draft)
        stop(vm)
    }

    @Test
    fun `a gated chip fires the event and changes nothing else`() = chatTest {
        val vm = opened()
        vm.edit("Typed before")
        val before = vm.ui.value
        analytics.clear()
        vm.pick(ChatReason.Arrived, ChipSurface.Dock, gated = true)
        assertEquals(listOf("action_chip_used"), analytics.names())
        assertEquals("arrived", analytics.of("action_chip_used").single().properties["reason"])
        assertEquals(before.selected, vm.ui.value.selected)
        assertEquals(before.draft, vm.ui.value.draft)
        assertEquals(before.messages, vm.ui.value.messages)
        vm.pick(ChatReason.Arrived, ChipSurface.Dock)
        assertEquals(ChatReason.Arrived, vm.ui.value.selected)
        assertEquals(ChatReason.Arrived.draft, vm.ui.value.draft)
        stop(vm)
    }

    @Test
    fun `I can't make it today empties and locks the field until Got it, and another chip removes the guide`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.RunningLate, ChipSurface.Entry)
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        assertEquals("", vm.ui.value.draft)
        assertEquals(Guide.Locked, vm.ui.value.guide)
        assertTrue(vm.ui.value.locked)
        assertFalse(vm.ui.value.canSend)
        vm.edit("typed while locked")
        assertEquals("the locked field takes nothing", "", vm.ui.value.draft)

        vm.ackGuide()
        assertEquals(Guide.Acked, vm.ui.value.guide)
        assertFalse(vm.ui.value.locked)
        vm.edit("So sorry, something came up.")
        assertTrue(vm.ui.value.canSend)

        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        assertEquals(Guide.None, vm.ui.value.guide)
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        assertEquals("picking it again shows the guide again, locked", Guide.Locked, vm.ui.value.guide)
        assertEquals("", vm.ui.value.draft)
        stop(vm)
    }

    // ── sending ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `send posts the chip's reason and the trimmed text, then clears the chip and the field`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.RunningLate, ChipSurface.Dock)
        vm.edit("Running 20 min late, sorry!  ")
        vm.send()
        runCurrent()
        assertEquals(listOf(ChatReason.RunningLate to "Running 20 min late, sorry!"), store.sent)
        assertNull(vm.ui.value.selected)
        assertEquals("", vm.ui.value.draft)
        assertEquals(listOf("Running 20 min late, sorry!"), vm.ui.value.messages.map { it.body })
        stop(vm)
    }

    @Test
    fun `message_sent carries the registry's fields and never the text`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.RunningLate, ChipSurface.Dock)
        vm.send()
        runCurrent()
        val first = analytics.of("message_sent").single().properties
        assertEquals("d1", first["date_id"])
        assertEquals("running_late", first["reason"])
        assertEquals("Running about 10 min late — sorry! Almost there.".length, first["char_count"])
        assertEquals(false, first["prefill_edited"])
        assertEquals(78, first["minutes_before_start"])
        assertEquals(true, first["is_first"])
        assertEquals(
            setOf("date_id", "reason", "char_count", "prefill_edited", "minutes_before_start", "is_first",
                  "sensitivity_class", "field_registry_version"),
            first.keys,
        )
        assertFalse(first.values.any { it.toString().contains("Running about") })

        vm.edit("No chip this time")
        vm.send()
        runCurrent()
        val second = analytics.of("message_sent").last().properties
        assertEquals(JsonNull, second["reason"])
        assertEquals(JsonNull, second["prefill_edited"])
        assertEquals(false, second["is_first"])
        stop(vm)
    }

    @Test
    fun `a failed send stays in the thread, unsent, and frees the field (J)`() = chatTest {
        val vm = opened()
        store.sendFails = CallFailure.Network
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        vm.send()
        runCurrent()
        val failed = vm.ui.value.messages.single()
        assertTrue(failed.failed)
        assertTrue(failed.fromYou)
        assertEquals(ChatReason.OnMyWay, failed.reason)
        assertEquals("On my way now — see you in a few!", failed.body)
        assertNull("the field is free for the next message", vm.ui.value.selected)
        assertEquals("", vm.ui.value.draft)
        assertFalse(vm.ui.value.sending)
        assertTrue(analytics.of("message_sent").isEmpty())
        stop(vm)
    }

    @Test
    fun `tapping the line resends it once with the same reason and id, and message_sent fires then`() = chatTest {
        val vm = opened()
        store.sendFails = CallFailure.Network
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        vm.send()
        runCurrent()
        val failed = vm.ui.value.messages.single()
        store.sendFails = null
        now = now.plusSeconds(60)
        vm.resend(failed.clientId!!)
        runCurrent()
        assertEquals(listOf(ChatReason.OnMyWay to "On my way now — see you in a few!"), store.sent)
        assertEquals("one id for the message, every attempt", 1, store.clientIds.toSet().size)
        val sent = vm.ui.value.messages.single()
        assertFalse(sent.failed)
        val event = analytics.of("message_sent").single().properties
        assertEquals("on_my_way", event["reason"])
        assertEquals(false, event["prefill_edited"])
        assertEquals(true, event["is_first"])
        assertEquals("counted when it was actually sent", 77, event["minutes_before_start"])
        stop(vm)
    }

    @Test
    fun `a resend that fails again stays failed, and a second tap while one is on its way sends nothing`() = chatTest {
        val vm = opened()
        store.sendFails = CallFailure.Network
        vm.edit("Here!")
        vm.send()
        runCurrent()
        val id = vm.ui.value.messages.single().clientId!!
        val gate = CompletableDeferred<Unit>()
        store.sendGate = gate
        vm.resend(id)
        runCurrent()
        assertTrue(id in vm.ui.value.resending)
        vm.resend(id)
        runCurrent()
        assertEquals(2, store.calls.count { it == "send" })
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.ui.value.messages.single().failed)
        assertTrue(vm.ui.value.resending.isEmpty())
        assertTrue(analytics.of("message_sent").isEmpty())
        stop(vm)
    }

    @Test
    fun `a send that landed though its answer was lost is matched by the next poll, never shown twice`() = chatTest {
        val vm = opened()
        store.landsThenFails = true
        vm.edit("Here!")
        vm.send()
        runCurrent()
        assertTrue(vm.ui.value.messages.single().failed)
        store.landsThenFails = false
        advanceTimeBy(5_001)
        val sent = vm.ui.value.messages.single()
        assertFalse(sent.failed)
        assertEquals("the poll found it sent: counted once", 1, analytics.of("message_sent").size)
        stop(vm)
    }

    @Test
    fun `a send whose answer is lost after a poll already brought it is shown once and counted once`() = chatTest {
        val vm = opened()
        store.landsThenFails = true
        val gate = CompletableDeferred<Unit>()
        store.sendGate = gate
        vm.edit("Here!")
        vm.send()
        runCurrent()
        // The server has it; a poll reads it before the send's answer is given up on.
        advanceTimeBy(5_001)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, vm.ui.value.messages.size)
        assertFalse(vm.ui.value.messages.single().failed)
        assertEquals(1, analytics.of("message_sent").size)
        advanceTimeBy(5_001)
        assertEquals(1, vm.ui.value.messages.size)
        assertEquals("counted once", 1, analytics.of("message_sent").size)
        stop(vm)
    }

    @Test
    fun `a failed I can't make it today message brings no end card, and no end sheet`() = chatTest {
        val vm = opened()
        store.sendFails = CallFailure.Network
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        vm.ackGuide()
        vm.edit("So sorry Leonie")
        vm.send()
        runCurrent()
        assertTrue(vm.ui.value.messages.single().failed)
        assertFalse("nothing reaches the match until it sends", vm.ui.value.endCard)
        vm.openEndSheet()
        assertFalse(vm.ui.value.endSheet)
        stop(vm)
    }

    // ── polling ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a poll never touches the dock`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        vm.ackGuide()
        vm.edit("So sorry Leonie")
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate)))
        advanceTimeBy(5_001)
        assertEquals(1, vm.ui.value.messages.size)
        assertEquals(ChatReason.CantMakeIt, vm.ui.value.selected)
        assertEquals("So sorry Leonie", vm.ui.value.draft)
        assertEquals(Guide.Acked, vm.ui.value.guide)
        stop(vm)
    }

    @Test
    fun `a poll that brings the match's messages reports each once and marks the thread read`() = chatTest {
        val vm = opened()
        assertEquals("open marks the thread read", 1, store.calls.count { it == "read" })
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate, "Ten min late")))
        advanceTimeBy(5_001)
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate, "Ten min late"), theirs("t2", null, "Here")))
        advanceTimeBy(5_000)
        advanceTimeBy(5_000)
        val received = analytics.of("message_received").map { it.properties }
        assertEquals(2, received.size)
        assertEquals("running_late", received[0]["reason"])
        assertEquals("Ten min late".length, received[0]["char_count"])
        assertEquals("d1", received[0]["date_id"])
        assertEquals(JsonNull, received[1]["reason"])
        assertEquals("one more read for each poll that brought something", 3, store.calls.count { it == "read" })
        stop(vm)
    }

    @Test
    fun `a message reported in an earlier launch is not reported again, and a new one is, once`() = chatTest {
        val ledger = InMemoryReceivedLedger()
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate)))
        dates.read = Outcome.Ok(DateRead.Active(date))
        val first = vm(ledger)
        first.open(date, ChatEntry.OpenChat)
        runCurrent()
        stop(first)
        assertEquals(1, analytics.of("message_received").size)
        // The next launch: a new model on the same phone, the same thread.
        now = now.plusSeconds(60)
        val second = vm(ledger)
        second.open(date, ChatEntry.OpenChat)
        runCurrent()
        assertEquals("an old message is not news", 1, analytics.of("message_received").size)
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate), theirs("t2", null)))
        advanceTimeBy(5_001)
        advanceTimeBy(5_000)
        assertEquals(2, analytics.of("message_received").size)
        stop(second)
    }

    @Test
    fun `the countdown moves with the clock while every read fails, and leaves at the meet time`() = chatTest {
        val vm = opened()
        assertEquals("in 1h 18m", vm.ui.value.countdown)
        dates.read = Outcome.Failed(CallFailure.Network)
        store.thread = Outcome.Failed(CallFailure.Network)
        now = meet.plusSeconds(60)
        advanceTimeBy(5_001)
        assertNull("offline too, past the meet time the countdown leaves the header", vm.ui.value.countdown)
        stop(vm)
    }

    @Test
    fun `a send refused because the date has ended shows the outcome, not a send failure`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.RunningLate, ChipSurface.Dock)
        store.sendFails = CallFailure.Server
        dates.read = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = false)))
        vm.send()
        runCurrent()
        assertTrue(vm.ui.value.messages.none { it.failed })
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = false), vm.ui.value.outcome)
        stop(vm)
    }

    @Test
    fun `the header follows the latest agreed time from the date read`() = chatTest {
        val vm = opened()
        val later = date.copy(meetAt = meet.plusSeconds(30 * 60), place = DatePlace("Bar Nova", null))
        dates.read = Outcome.Ok(DateRead.Active(later))
        advanceTimeBy(5_001)
        assertEquals(later, vm.ui.value.date)
        stop(vm)
    }

    @Test
    fun `onScreen false stops the poll, and onScreen true reads at once and resumes it`() = chatTest {
        val vm = opened()
        vm.onScreen(false)
        runCurrent()
        val before = store.calls.size to dates.reads
        advanceTimeBy(30_000)
        assertEquals(before, store.calls.size to dates.reads)
        vm.onScreen(true)
        runCurrent()
        assertTrue(store.calls.size > before.first)
        stop(vm)
    }

    @Test
    fun `the thread, the draft, the chip and the guide survive leaving and coming back`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate)))
        val vm = opened()
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        vm.ackGuide()
        vm.edit("Sorry Leonie")
        vm.close()
        runCurrent()
        vm.open(date, ChatEntry.OpenChat)
        runCurrent()
        assertEquals(ChatReason.CantMakeIt, vm.ui.value.selected)
        assertEquals("Sorry Leonie", vm.ui.value.draft)
        assertEquals(Guide.Acked, vm.ui.value.guide)
        assertEquals(1, vm.ui.value.messages.size)
        stop(vm)
    }

    @Test
    fun `a first read that fails shows the failure, and a later failed poll keeps the thread`() = chatTest {
        store.thread = Outcome.Failed(CallFailure.Network)
        val vm = opened()
        assertEquals(CallFailure.Network, vm.ui.value.failed)
        store.thread = Outcome.Ok(listOf(theirs("t1", null)))
        vm.retry()
        runCurrent()
        assertNull(vm.ui.value.failed)
        assertEquals(1, vm.ui.value.messages.size)
        store.thread = Outcome.Failed(CallFailure.Server)
        advanceTimeBy(5_001)
        assertNull(vm.ui.value.failed)
        assertEquals(1, vm.ui.value.messages.size)
        stop(vm)
    }

    // ── ending the date ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the end card waits while I can't make it today is selected`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.CantMakeIt)))
        val vm = opened()
        assertTrue(vm.ui.value.endCard)
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        assertFalse(vm.ui.value.endCard)
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        assertTrue(vm.ui.value.endCard)
        stop(vm)
    }

    @Test
    fun `End date ends it - the sheet opens with cant_make_it_from, and the ender gets their outcome`() = chatTest {
        store.thread = Outcome.Ok(listOf(mine("m1", ChatReason.CantMakeIt)))
        store.end = EndResult.Ended(EndedInChat("d1", "Leonie", endedByYou = true))
        val vm = opened()
        vm.openEndSheet()
        assertTrue(vm.ui.value.endSheet)
        val opened = analytics.of("end_date_sheet_opened").single().properties
        assertEquals("self", opened["cant_make_it_from"])
        assertEquals(78, opened["minutes_before_start"])
        assertEquals("d1", opened["date_id"])
        vm.keepDate()
        assertFalse(vm.ui.value.endSheet)
        vm.openEndSheet()
        vm.endDate()
        runCurrent()
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = true), vm.ui.value.outcome)
        assertFalse(vm.ui.value.endSheet)
        stop(vm)
    }

    @Test
    fun `no end sheet without an I can't make it today message`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.RunningLate)))
        val vm = opened()
        vm.openEndSheet()
        assertFalse(vm.ui.value.endSheet)
        assertTrue(analytics.of("end_date_sheet_opened").isEmpty())
        stop(vm)
    }

    @Test
    fun `a poll that finds the date ended shows the outcome, closing the sheet and dropping the draft`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.CantMakeIt)))
        val vm = opened()
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        vm.openEndSheet()
        dates.read = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = false)))
        advanceTimeBy(5_001)
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = false), vm.ui.value.outcome)
        assertFalse(vm.ui.value.endSheet)
        assertEquals("", vm.ui.value.draft)
        assertNull(vm.ui.value.selected)
        val calls = store.calls.size
        advanceTimeBy(30_000)
        assertEquals("the poll stops once the date has ended", calls, store.calls.size)
    }

    @Test
    fun `a 409 on End date shows the outcome the next date read brings`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.CantMakeIt)))
        store.end = EndResult.AlreadyEnded
        val vm = opened()
        vm.openEndSheet()
        dates.read = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = false)))
        vm.endDate()
        runCurrent()
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = false), vm.ui.value.outcome)
        assertFalse(vm.ui.value.endSheet)
        stop(vm)
    }

    @Test
    fun `Keep the date and the scrim do nothing while the end is on its way`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.CantMakeIt)))
        store.end = EndResult.Ended(EndedInChat("d1", "Leonie", endedByYou = true))
        val gate = CompletableDeferred<Unit>()
        store.endGate = gate
        val vm = opened()
        vm.openEndSheet()
        vm.endDate()
        runCurrent()
        assertTrue(vm.ui.value.ending)
        vm.keepDate()
        assertTrue("the sheet stays while the end is on its way", vm.ui.value.endSheet)
        gate.complete(Unit)
        runCurrent()
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = true), vm.ui.value.outcome)
        stop(vm)
    }

    @Test
    fun `a failed End date keeps the sheet open and says why`() = chatTest {
        store.thread = Outcome.Ok(listOf(theirs("t1", ChatReason.CantMakeIt)))
        store.end = EndResult.Failed(CallFailure.Network)
        val vm = opened()
        vm.openEndSheet()
        vm.endDate()
        runCurrent()
        assertNull(vm.ui.value.outcome)
        assertTrue(vm.ui.value.endSheet)
        assertFalse(vm.ui.value.ending)
        assertEquals(CallFailure.Network, vm.ui.value.endFailed)
        stop(vm)
    }

    @Test
    fun `a date gone some other way leaves the chat`() = chatTest {
        val vm = opened()
        dates.read = Outcome.Ok(DateRead.None)
        advanceTimeBy(5_001)
        assertTrue(vm.ui.value.left)
    }

    // ── the rest of family L ─────────────────────────────────────────────────────────────────

    @Test
    fun `chat_opened carries entry, is_first and minutes_before_start, once per visit`() = chatTest {
        val vm = opened(ChatEntry.Tutorial)
        val first = analytics.of("chat_opened").single().properties
        assertEquals("d1", first["date_id"])
        assertEquals("tutorial", first["entry"])
        assertEquals(true, first["is_first"])
        assertEquals(78, first["minutes_before_start"])
        vm.close()
        vm.open(date, ChatEntry.ReadReply)
        runCurrent()
        val second = analytics.of("chat_opened").last().properties
        assertEquals("read_reply", second["entry"])
        assertEquals(false, second["is_first"])
        stop(vm)
    }

    @Test
    fun `chat_opened waits for the chat to load - none for a failed load, one once Try again lands`() = chatTest {
        store.thread = Outcome.Failed(CallFailure.Network)
        val vm = opened()
        assertTrue(analytics.of("chat_opened").isEmpty())
        store.thread = Outcome.Ok(emptyList())
        vm.retry()
        runCurrent()
        assertEquals(1, analytics.of("chat_opened").size)
        advanceTimeBy(5_001)
        assertEquals("once per visit", 1, analytics.of("chat_opened").size)
        stop(vm)
    }

    @Test
    fun `action_chip_used fires on every chip tap with its surface`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.RunningLate, ChipSurface.Entry)
        vm.pick(ChatReason.RunningLate, ChipSurface.Dock)
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        val used = analytics.of("action_chip_used").map { it.properties }
        assertEquals(listOf("running_late", "running_late", "cant_make_it"), used.map { it["reason"] })
        assertEquals(listOf("entry", "dock", "dock"), used.map { it["surface"] })
        assertEquals(78, used[0]["minutes_before_start"])
        assertEquals("d1", used[0]["date_id"])
        stop(vm)
    }

    @Test
    fun `Got it fires cancel_guide_acknowledged with how long the guide was read`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.CantMakeIt, ChipSurface.Dock)
        now = now.plusSeconds(12)
        vm.ackGuide()
        val ack = analytics.of("cancel_guide_acknowledged").single().properties
        assertEquals("d1", ack["date_id"])
        assertEquals(12, ack["read_time_s"])
        assertEquals(77, ack["minutes_before_start"])
        stop(vm)
    }

    @Test
    fun `chat_closed carries the visit's messages and how long it lasted`() = chatTest {
        val vm = opened()
        vm.pick(ChatReason.OnMyWay, ChipSurface.Dock)
        vm.send()
        runCurrent()
        now = now.plusSeconds(95)
        vm.close()
        val closed = analytics.of("chat_closed").single().properties
        assertEquals("d1", closed["date_id"])
        assertEquals(1, closed["messages_sent"])
        assertEquals(95, closed["duration_s"])
        val calls = store.calls.size
        advanceTimeBy(30_000)
        assertEquals("leaving stops the poll", calls, store.calls.size)
    }
}

/** Answers from fields the test sets; records every call by name. */
class FakeChatStore : ChatStore {
    var thread: Outcome<List<ChatMessage>> = Outcome.Ok(emptyList())
    var sendFails: CallFailure? = null

    /** The server keeps the message but the answer is lost: the phone sees a failure. */
    var landsThenFails = false

    /** While set, a send waits on it: the message is on its way. */
    var sendGate: CompletableDeferred<Unit>? = null
    val clientIds = mutableListOf<String>()
    var end: EndResult = EndResult.Ended(EndedInChat("d1", "Leonie", endedByYou = true))
    val calls = mutableListOf<String>()
    val sent = mutableListOf<Pair<ChatReason?, String>>()
    private var next = 0

    override suspend fun thread(dateId: String): Outcome<List<ChatMessage>> {
        calls += "thread"
        return thread
    }

    override suspend fun send(dateId: String, reason: ChatReason?, body: String, clientId: String): Outcome<ChatMessage> {
        calls += "send"
        clientIds += clientId
        // A message that lands and then loses its answer waits AFTER the server has it: a read meanwhile sees it.
        if (!landsThenFails) sendGate?.await()
        sendFails?.let { return Outcome.Failed(it) }
        // As the server: a message it already has under this id is answered with that copy.
        (thread as? Outcome.Ok)?.value?.firstOrNull { it.clientId == clientId }?.let { return Outcome.Ok(it) }
        sent += reason to body
        val message = ChatMessage("s${++next}", reason, body, fromYou = true, sentAt = Instant.EPOCH, read = false, clientId = clientId)
        // The server has it from now on: the next read returns it.
        thread = (thread as? Outcome.Ok)?.let { Outcome.Ok(it.value + message) } ?: thread
        if (landsThenFails) sendGate?.await()
        return if (landsThenFails) Outcome.Failed(CallFailure.Network) else Outcome.Ok(message)
    }

    override suspend fun markRead(dateId: String) {
        calls += "read"
    }

    override suspend fun markTutorialSeen(dateId: String) {
        calls += "tutorial"
    }

    /** While set, End date waits on it: the end is on its way. */
    var endGate: CompletableDeferred<Unit>? = null

    override suspend fun endInChat(dateId: String): EndResult {
        calls += "end"
        endGate?.await()
        return end
    }

    override suspend fun markEndSeen(dateId: String) {
        calls += "end-seen"
    }
}
