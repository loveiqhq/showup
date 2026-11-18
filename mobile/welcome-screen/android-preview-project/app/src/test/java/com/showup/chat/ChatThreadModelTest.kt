/*
 * ChatThreadModelTest.kt
 * ShowUp · the chat model's Date 05 half: the suggestions read with the thread, and `I agree` in it
 * (SHOWUP-196 C, F, H, L)
 *
 * Same scaffolding as ChatViewModelTest: injected clock, the poll on the test scheduler, every model stopped.
 */
package com.showup.chat

import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.change.AgreeResult
import com.showup.change.AgreeSurface
import com.showup.change.ChangeStatus
import com.showup.change.FakeChangeStore
import com.showup.date.DateRead
import com.showup.date.FakeDateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ChatThreadModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-11-03T16:42:00Z")
    private val analytics = RecordingAnalytics()
    private val store = FakeChatStore()
    private val dates = FakeDateStore()
    private val changes = FakeChangeStore()
    private val date = ChatSamples.DATE
    private val s = ThreadSamples
    private val models = mutableListOf<ChatViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun chatTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            models.forEach { it.onScreen(false) }
        }
    }

    private fun TestScope.opened(): ChatViewModel {
        dates.read = Outcome.Ok(DateRead.Active(date))
        val vm = ChatViewModel(store, dates, analytics = analytics, clock = { now }, zone = { ZoneId.of("Europe/Berlin") }, changes = changes)
        models += vm
        vm.open(date, ChatEntry.OpenChat)
        runCurrent()
        return vm
    }

    @Test
    fun `the suggestions are read with every poll and held beside the messages, not among them`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        store.thread = Outcome.Ok(listOf(s.message("a", "17:00", false)))
        val vm = opened()
        assertEquals(listOf(s.THEIRS_TIME), vm.ui.value.suggestions)
        assertEquals(1, vm.ui.value.messages.size)
        assertEquals(1, changes.suggestionReads)
    }

    @Test
    fun `a suggestion is not a message, so it fires no message event`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME, s.MINE_PLACE))
        val vm = opened()
        assertEquals(2, vm.ui.value.suggestions.size)
        assertTrue(analytics.names().none { it == "message_received" || it == "message_sent" })
    }

    @Test
    fun `a card with no message is not the start grid`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.MINE_PLACE))
        assertFalse(opened().ui.value.start)
    }

    @Test
    fun `a failed suggestions read keeps the cards on screen`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        changes.suggestionList = Outcome.Failed(CallFailure.Network)
        vm.retry()
        runCurrent()
        assertEquals(listOf(s.THEIRS_TIME), vm.ui.value.suggestions)
    }

    @Test
    fun `I agree sends the thread surface, then reads the date, the thread and the suggestions again`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        val dateReads = dates.reads
        val threadReads = store.calls.count { it == "thread" }
        changes.suggestionList = Outcome.Ok(listOf(s.AGREED_TIME))
        vm.agree("t1")
        runCurrent()
        assertEquals(listOf(Triple(date.id, "t1", AgreeSurface.Thread)), changes.agrees)
        assertEquals(dateReads + 1, dates.reads)
        assertEquals(threadReads + 1, store.calls.count { it == "thread" })
        assertEquals(ChangeStatus.Agreed, vm.ui.value.suggestions.single().status)
        assertTrue(vm.ui.value.agreeing.isEmpty())
        assertTrue(vm.ui.value.agreeFailed.isEmpty())
    }

    @Test
    fun `while I agree is on its way both buttons are inert and a second tap sends nothing`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        changes.agreeGate = CompletableDeferred()
        vm.agree("t1")
        runCurrent()
        assertEquals(setOf("t1"), vm.ui.value.agreeing)
        vm.agree("t1")
        runCurrent()
        assertEquals(1, changes.agrees.size)
        changes.agreeGate!!.complete(Unit)
        runCurrent()
        assertTrue(vm.ui.value.agreeing.isEmpty())
    }

    @Test
    fun `a failure shows the line and changes nothing else, and a retry is the same call`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        val before = vm.ui.value
        changes.agree = AgreeResult.Failed(CallFailure.Network)
        vm.agree("t1")
        runCurrent()
        assertEquals(setOf("t1"), vm.ui.value.agreeFailed)
        assertEquals(before.suggestions, vm.ui.value.suggestions)
        assertEquals(before.date, vm.ui.value.date)
        assertEquals(before.messages, vm.ui.value.messages)
        // Retry: the line goes while it is on its way, and stays gone once the server confirms.
        changes.agree = AgreeResult.Agreed(s.AGREED_TIME)
        changes.suggestionList = Outcome.Ok(listOf(s.AGREED_TIME))
        vm.agree("t1")
        runCurrent()
        assertEquals(2, changes.agrees.size)
        assertTrue(vm.ui.value.agreeFailed.isEmpty())
        assertEquals(ChangeStatus.Agreed, vm.ui.value.suggestions.single().status)
    }

    @Test
    fun `a 409 reads again, shows no error and fires no event`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        analytics.clear()
        val reads = changes.suggestionReads
        changes.agree = AgreeResult.Stale
        changes.suggestionList = Outcome.Ok(listOf(s.EXPIRED.copy(id = "t1")))
        vm.agree("t1")
        runCurrent()
        assertTrue(vm.ui.value.agreeFailed.isEmpty())
        assertEquals(reads + 1, changes.suggestionReads)
        assertEquals(ChangeStatus.Expired, vm.ui.value.suggestions.single().status)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `my own card, and a card that is no longer pending, cannot be agreed`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.MINE_PLACE, s.REPLACED))
        val vm = opened()
        vm.agree("p1")
        vm.agree("t0")
        vm.agree("unknown")
        runCurrent()
        assertTrue(changes.agrees.isEmpty())
    }

    @Test
    fun `a failure line is dropped once a read finds the card answered`() = chatTest {
        changes.suggestionList = Outcome.Ok(listOf(s.THEIRS_TIME))
        val vm = opened()
        changes.agree = AgreeResult.Failed(CallFailure.Server)
        vm.agree("t1")
        runCurrent()
        assertEquals(setOf("t1"), vm.ui.value.agreeFailed)
        changes.suggestionList = Outcome.Ok(listOf(s.AGREED_TIME))
        vm.retry()
        runCurrent()
        assertTrue(vm.ui.value.agreeFailed.isEmpty())
    }
}
