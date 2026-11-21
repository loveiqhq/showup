/*
 * DateEndViewModelTest.kt
 * ShowUp · Date 06's model: every frame from its entry, the gate continuation, the end and its failures, the
 * events and their order, and process death (SHOWUP-197, R9, R11, R14)
 */
package com.showup.date.end

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.arrival.ArrivalEntry
import com.showup.date.ActiveDate
import com.showup.date.DateEndStore
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.EndDateResult
import com.showup.date.EndEntry
import com.showup.date.EndReason
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

class FakeEndStore : DateEndStore {
    data class Call(val dateId: String, val reason: EndReason, val entry: EndEntry)

    val calls = mutableListOf<Call>()
    val answers = mutableListOf<EndDateResult>()
    var flags = 0
    var holdMs = 0L

    override suspend fun end(dateId: String, reason: EndReason, entry: EndEntry): EndDateResult {
        calls += Call(dateId, reason, entry)
        if (holdMs > 0) delay(holdMs)
        return if (answers.isEmpty()) EndDateResult.Ended(outcome(reason)) else answers.removeAt(0)
    }

    override suspend fun flagDiscomfort(dateId: String): Result<Boolean> {
        flags++
        return Result.success(true)
    }

    companion object {
        fun outcome(reason: EndReason) = EndedOutcome(
            "d1", "Leonie", endedByYou = true,
            reason = when (reason) {
                EndReason.Met -> OutcomeReason.Met
                EndReason.PartnerNoShow -> OutcomeReason.PartnerNoShow
                EndReason.Uncomfortable -> OutcomeReason.Uncomfortable
            },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DateEndViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-21T18:00:00Z")
    private val before = meet.minusSeconds(5 * 60)
    private val after = meet.plusSeconds(11 * 60)
    private val analytics = RecordingAnalytics()
    private val store = FakeEndStore()
    private var rereads = 0

    private fun date(checkedIn: Boolean = true, endsEnabled: Boolean = true, id: String = "d1", chatOpen: Boolean = true) = ActiveDate(
        id = id, meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null, endsEnabled = endsEnabled,
    )

    /** What survives the process: the saved keys, handed to a model that has never seen them. */
    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    private fun fresh(saved: SavedStateHandle = SavedStateHandle()) =
        DateEndViewModel(store, analytics, saved, reread = { rereads++ })

    private fun TestScope.vm(
        checkedIn: Boolean = true,
        now: Instant = before,
        saved: SavedStateHandle = SavedStateHandle(),
        endsEnabled: Boolean = true,
    ): DateEndViewModel {
        val vm = fresh(saved)
        vm.bind(date(checkedIn, endsEnabled), now)
        runCurrent()
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── every frame from its entry ──────────────────────────────────────────────────────────

    @Test
    fun `the bar opens B and fires wrapup_opened with the entry, check-in state and minutes`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        val ui = vm.ui.value
        assertEquals(EndRoute.Wrap, ui.route)
        assertEquals(EndStep.Menu, ui.step)
        assertTrue(ui.visible)
        val e = analytics.of("wrapup_opened").single().properties
        assertEquals("d1", e["date_id"])
        assertEquals("end_date_bar", e["entry"])
        assertEquals(true, e["checked_in"])
        assertEquals(-5, e["minutes_vs_start"])
    }

    @Test
    fun `the Check-in bar's link opens B with entry checkin_bar_link`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openWrap(EndEntry.CheckinBarLink)
        val e = analytics.of("wrapup_opened").single().properties
        assertEquals("checkin_bar_link", e["entry"])
        assertEquals(false, e["checked_in"])
    }

    @Test
    fun `the Report card opens H and fires problem_report_opened`() = runTest(dispatcher) {
        val vm = vm()
        vm.openReport()
        assertEquals(EndRoute.Report, vm.ui.value.route)
        assertEquals(EndStep.Menu, vm.ui.value.step)
        val e = analytics.of("problem_report_opened").single().properties
        assertEquals("d1", e["date_id"])
        assertEquals(true, e["checked_in"])
        assertEquals(-5, e["minutes_vs_start"])
        assertEquals(listOf("problem_report_opened"), analytics.names())
    }

    @Test
    fun `a second open while a sheet is up does nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.openReport()
        vm.openWrap(EndEntry.CheckinBarLink)
        assertEquals(EndRoute.Wrap, vm.ui.value.route)
        assertEquals(listOf("wrapup_opened"), analytics.names())
    }

    @Test
    fun `nothing opens when the date has the ends switched off, or none is bound`() = runTest(dispatcher) {
        val off = vm(endsEnabled = false)
        off.openWrap(EndEntry.EndDateBar)
        off.openReport()
        assertNull(off.ui.value.route)
        val unbound = fresh()
        unbound.openWrap(EndEntry.EndDateBar)
        assertNull(unbound.ui.value.route)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `We met goes to C with no gate, even when not checked in, and fires the outcome on the tap`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openWrap(EndEntry.CheckinBarLink)
        vm.pick(EndOption.Met)
        assertEquals(EndStep.Met, vm.ui.value.step)
        assertNull(vm.ui.value.openGate)
        val e = analytics.of("wrapup_outcome_selected").single().properties
        assertEquals("d1", e["date_id"])
        assertEquals("met", e["outcome"])
        assertEquals(false, e["checked_in"])
    }

    @Test
    fun `checked in, My date isn't showing up goes from B to the guide`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        assertEquals(EndStep.Guide, vm.ui.value.step)
        assertNull(vm.ui.value.openGate)
        assertEquals("partner_no_show", analytics.of("wrapup_outcome_selected").single().properties["outcome"])
    }

    @Test
    fun `checked in, H reaches the guide and I with the category fired on the tap`() = runTest(dispatcher) {
        val vm = vm()
        vm.openReport()
        vm.pick(EndOption.NoShow)
        assertEquals(EndStep.Guide, vm.ui.value.step)
        vm.back()
        vm.pick(EndOption.Uncomfortable)
        assertEquals(EndStep.Uncomfortable, vm.ui.value.step)
        val cats = analytics.of("problem_category_selected").map { it.properties }
        assertEquals(listOf("partner_no_show", "uncomfortable"), cats.map { it["category"] })
        assertTrue(cats.all { it["date_id"] == "d1" && it["checked_in"] == true })
    }

    @Test
    fun `the option that does not belong to a sheet is ignored`() = runTest(dispatcher) {
        val wrap = vm()
        wrap.openWrap(EndEntry.EndDateBar)
        wrap.pick(EndOption.Uncomfortable)
        assertEquals(EndStep.Menu, wrap.ui.value.step)
        val report = fresh()
        report.bind(date(), before)
        report.openReport()
        report.pick(EndOption.Met)
        assertEquals(EndStep.Menu, report.ui.value.step)
        assertEquals(listOf("wrapup_opened", "problem_report_opened"), analytics.names())
    }

    @Test
    fun `the guide's end button opens G only from the meet time, and a tap before it fires nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        analytics.clear()
        vm.confirmWait()
        assertEquals(EndStep.Guide, vm.ui.value.step)
        assertEquals(emptyList<String>(), analytics.names())
        vm.bind(date(), meet) // the clock passes the meet time while the sheet is open
        assertTrue(vm.ui.value.pastMeet)
        vm.confirmWait()
        assertEquals(EndStep.NoShow, vm.ui.value.step)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `Back walks one step up and Cancel on B, C or H closes, none of them firing anything`() = runTest(dispatcher) {
        val vm = vm(now = after)
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        vm.confirmWait()
        analytics.clear()
        vm.back()
        assertEquals(EndStep.Guide, vm.ui.value.step)
        vm.back()
        assertEquals(EndStep.Menu, vm.ui.value.step)
        vm.pick(EndOption.Met)
        analytics.clear()
        vm.back() // Cancel on C returns to B
        assertEquals(EndStep.Menu, vm.ui.value.step)
        vm.close()
        assertNull(vm.ui.value.route)
        assertEquals(emptyList<String>(), analytics.names())
        assertEquals(0, store.calls.size)
    }

    @Test
    fun `Message closes the sheet and asks the host for the chat, only while the chat is open`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        analytics.clear()
        vm.message()
        assertNull(vm.ui.value.route)
        assertTrue(vm.ui.value.openChat)
        assertEquals(emptyList<String>(), analytics.names())
        vm.consumeOpenChat()
        assertFalse(vm.ui.value.openChat)

        val locked = fresh()
        locked.bind(date(chatOpen = false), before)
        locked.openWrap(EndEntry.EndDateBar)
        locked.pick(EndOption.NoShow)
        locked.message()
        assertEquals(EndStep.Guide, locked.ui.value.step)
        assertFalse(locked.ui.value.chatOpen)
    }

    // ── the gate (R9) ────────────────────────────────────────────────────────────────────────

    @Test
    fun `not checked in, B's no-show asks for the gate with entry wrapup and hides the sheet`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        val ui = vm.ui.value
        assertEquals(ArrivalEntry.Wrapup, ui.openGate)
        assertEquals(EndStep.Guide, ui.gated)
        assertFalse(ui.visible)
        val e = analytics.of("wrapup_outcome_selected").single().properties
        assertEquals("partner_no_show", e["outcome"])
        assertEquals(false, e["checked_in"])
    }

    @Test
    fun `not checked in, H's two problem routes ask for the gate with entry report`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openReport()
        vm.pick(EndOption.Uncomfortable)
        assertEquals(ArrivalEntry.Report, vm.ui.value.openGate)
        assertEquals(EndStep.Uncomfortable, vm.ui.value.gated)
        vm.consumeGate()
        assertNull(vm.ui.value.openGate)
        vm.gateNotYet()
        vm.pick(EndOption.NoShow)
        assertEquals(ArrivalEntry.Report, vm.ui.value.openGate)
        assertEquals(EndStep.Guide, vm.ui.value.gated)
    }

    @Test
    fun `Yes then Close reopens the sheet at the picked step, from B and from H`() = runTest(dispatcher) {
        val a = vm(checkedIn = false)
        a.openWrap(EndEntry.EndDateBar)
        a.pick(EndOption.NoShow)
        a.consumeGate()
        a.gateCommitted()
        assertEquals(EndStep.Guide, a.ui.value.step)
        assertNull(a.ui.value.gated)
        assertTrue(a.ui.value.visible)
        a.bind(date(checkedIn = true), before)
        assertEquals(EndStep.Guide, a.ui.value.step)

        val b = fresh()
        b.bind(date(checkedIn = false), before)
        b.openReport()
        b.pick(EndOption.Uncomfortable)
        b.gateCommitted()
        assertEquals(EndStep.Uncomfortable, b.ui.value.step)
        assertTrue(b.ui.value.visible)
    }

    @Test
    fun `a commit counts as checked in even before the date read says so`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openReport()
        vm.pick(EndOption.NoShow)
        vm.gateCommitted()
        vm.bind(date(checkedIn = false), before) // a stale read
        assertEquals(EndStep.Guide, vm.ui.value.step)
        assertTrue(vm.ui.value.checkedIn)
    }

    @Test
    fun `Not yet reopens the menu the route came from, B or H`() = runTest(dispatcher) {
        val a = vm(checkedIn = false)
        a.openWrap(EndEntry.EndDateBar)
        a.pick(EndOption.NoShow)
        a.gateNotYet()
        assertEquals(EndRoute.Wrap, a.ui.value.route)
        assertEquals(EndStep.Menu, a.ui.value.step)
        assertTrue(a.ui.value.visible)

        val b = fresh()
        b.bind(date(checkedIn = false), before)
        b.openReport()
        b.pick(EndOption.Uncomfortable)
        b.gateNotYet()
        assertEquals(EndRoute.Report, b.ui.value.route)
        assertEquals(EndStep.Menu, b.ui.value.step)
    }

    @Test
    fun `Undo closes everything`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false)
        vm.openReport()
        vm.pick(EndOption.NoShow)
        vm.gateUndone()
        assertNull(vm.ui.value.route)
        assertNull(vm.ui.value.gated)
        assertFalse(vm.ui.value.visible)
    }

    @Test
    fun `the gate signals do nothing when no gated route is waiting`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.gateCommitted()
        vm.gateNotYet()
        assertEquals(EndStep.Menu, vm.ui.value.step)
        assertEquals(EndRoute.Wrap, vm.ui.value.route)
    }

    // ── ending ───────────────────────────────────────────────────────────────────────────────

    private fun DateEndViewModel.toNoShowConfirm(route: EndRoute = EndRoute.Wrap) {
        if (route == EndRoute.Wrap) openWrap(EndEntry.EndDateBar) else openReport()
        pick(EndOption.NoShow)
        confirmWait()
    }

    @Test
    fun `End Date to Review ends with reason met and the bar's entry, then lands`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.Met)
        vm.end()
        assertTrue(vm.ui.value.ending)
        runCurrent()
        assertEquals(listOf(FakeEndStore.Call("d1", EndReason.Met, EndEntry.EndDateBar)), store.calls)
        assertEquals(OutcomeReason.Met, vm.ui.value.landed?.reason)
        assertNull(vm.ui.value.route)
        assertFalse(vm.ui.value.ending)
        vm.consumeLanded()
        assertNull(vm.ui.value.landed)
    }

    @Test
    fun `the link's entry and the report's entry go on the wire`() = runTest(dispatcher) {
        val a = vm(now = after)
        a.openWrap(EndEntry.CheckinBarLink)
        a.pick(EndOption.Met)
        a.end()
        runCurrent()
        val b = fresh()
        b.bind(date(), after)
        b.toNoShowConfirm(EndRoute.Report)
        b.end()
        runCurrent()
        assertEquals(listOf(EndEntry.CheckinBarLink, EndEntry.Report), store.calls.map { it.entry })
        assertEquals(listOf(EndReason.Met, EndReason.PartnerNoShow), store.calls.map { it.reason })
    }

    @Test
    fun `Head out now ends with uncomfortable`() = runTest(dispatcher) {
        val vm = vm()
        vm.openReport()
        vm.pick(EndOption.Uncomfortable)
        vm.end()
        runCurrent()
        assertEquals(EndReason.Uncomfortable, store.calls.single().reason)
        assertEquals(OutcomeReason.Uncomfortable, vm.ui.value.landed?.reason)
    }

    @Test
    fun `the end from the menu or the guide sends nothing`() = runTest(dispatcher) {
        val vm = vm(now = after)
        vm.openWrap(EndEntry.EndDateBar)
        vm.end()
        vm.pick(EndOption.NoShow)
        vm.end()
        runCurrent()
        assertEquals(0, store.calls.size)
    }

    @Test
    fun `an end in flight disables both buttons, a second tap sends nothing, and Back and Cancel are inert`() = runTest(dispatcher) {
        store.holdMs = 1_000
        val vm = vm(now = after)
        vm.toNoShowConfirm()
        vm.end()
        runCurrent()
        vm.end()
        vm.back()
        vm.close()
        assertTrue(vm.ui.value.ending)
        assertEquals(EndStep.NoShow, vm.ui.value.step)
        assertEquals(1, store.calls.size)
        advanceTimeBy(1_001)
        runCurrent()
        assertFalse(vm.ui.value.ending)
        assertEquals(1, store.calls.size)
    }

    @Test
    fun `a failure keeps the step with the error, Back still works, and a retry succeeds once`() = runTest(dispatcher) {
        store.answers += EndDateResult.Failed(CallFailure.Network)
        val vm = vm(now = after)
        vm.toNoShowConfirm()
        vm.end()
        runCurrent()
        val ui = vm.ui.value
        assertEquals(EndStep.NoShow, ui.step)
        assertTrue(ui.failed)
        assertFalse(ui.ending)
        assertNull(ui.landed)
        vm.end()
        runCurrent()
        assertEquals(2, store.calls.size)
        assertEquals(OutcomeReason.PartnerNoShow, vm.ui.value.landed?.reason)
        assertFalse(vm.ui.value.failed)

        store.answers += EndDateResult.Failed(CallFailure.Server)
        val other = fresh()
        other.bind(date(), after)
        other.toNoShowConfirm()
        other.end()
        runCurrent()
        assertTrue(other.ui.value.failed)
        other.back()
        assertEquals(EndStep.Guide, other.ui.value.step)
        assertFalse(other.ui.value.failed)
    }

    @Test
    fun `409 date_not_active closes the sheet and reads the date again`() = runTest(dispatcher) {
        store.answers += EndDateResult.Refused("date_not_active")
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.Met)
        vm.end()
        runCurrent()
        assertNull(vm.ui.value.route)
        assertNull(vm.ui.value.landed)
        assertEquals(1, rereads)
    }

    @Test
    fun `409 before_meet returns to the guide and 409 not_checked_in to the menu, each reading again`() = runTest(dispatcher) {
        store.answers += EndDateResult.Refused("before_meet")
        val vm = vm(now = after)
        vm.toNoShowConfirm()
        vm.end()
        runCurrent()
        assertEquals(EndStep.Guide, vm.ui.value.step)
        assertFalse(vm.ui.value.failed)
        assertEquals(1, rereads)

        store.answers += EndDateResult.Refused("not_checked_in")
        val other = fresh()
        other.bind(date(), after)
        other.toNoShowConfirm()
        other.end()
        runCurrent()
        assertEquals(EndStep.Menu, other.ui.value.step)
        assertEquals(2, rereads)
    }

    @Test
    fun `a switched-off route closes the sheet, an unknown refusal is a failure`() = runTest(dispatcher) {
        store.answers += EndDateResult.Refused("not_found")
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.Met)
        vm.end()
        runCurrent()
        assertNull(vm.ui.value.route)

        store.answers += EndDateResult.Refused("unknown")
        val other = fresh()
        other.bind(date(), before)
        other.openWrap(EndEntry.EndDateBar)
        other.pick(EndOption.Met)
        other.end()
        runCurrent()
        assertTrue(other.ui.value.failed)
        assertEquals(EndStep.Met, other.ui.value.step)
    }

    @Test
    fun `Stay for now flags the discomfort, closes the sheet, and each tap flags again`() = runTest(dispatcher) {
        val vm = vm()
        vm.openReport()
        vm.pick(EndOption.Uncomfortable)
        analytics.clear()
        vm.stay()
        runCurrent()
        assertEquals(1, store.flags)
        assertNull(vm.ui.value.route)
        assertEquals(emptyList<String>(), analytics.names()) // problem_stay_flagged is the server's
        vm.openReport()
        vm.pick(EndOption.Uncomfortable)
        vm.stay()
        runCurrent()
        assertEquals(2, store.flags)
    }

    @Test
    fun `Stay for now outside the uncomfortable step does nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.openReport()
        vm.stay()
        runCurrent()
        assertEquals(0, store.flags)
    }

    // ── events: the whole list, in order ─────────────────────────────────────────────────────

    @Test
    fun `a full no-show walk fires the client events in tap order and never date_ended`() = runTest(dispatcher) {
        val vm = vm(checkedIn = false, now = before)
        vm.openReport()
        vm.pick(EndOption.NoShow) // the gate
        vm.gateCommitted()
        vm.bind(date(), after)
        vm.confirmWait()
        vm.end()
        runCurrent()
        assertEquals(listOf("problem_report_opened", "problem_category_selected"), analytics.names())
        assertFalse(analytics.names().any { it == "date_ended" || it == "problem_stay_flagged" })
    }

    @Test
    fun `no payload carries a coordinate, a place or free text`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.pick(EndOption.NoShow)
        val keys = analytics.all().flatMap { it.properties.keys }
        assertTrue(keys.none { it.contains("lat") || it.contains("lon") || it.contains("place") || it.contains("text") })
    }

    // ── R14: process death ──────────────────────────────────────────────────────────────────

    @Test
    fun `a restored no-show confirm is kept after the meet time and returns to the guide before it`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved = saved, now = after)
        first.toNoShowConfirm()
        assertEquals(EndStep.NoShow, first.ui.value.step)

        val second = fresh(saved.copy())
        second.bind(date(), after)
        assertEquals(EndRoute.Wrap, second.ui.value.route)
        assertEquals(EndStep.NoShow, second.ui.value.step)

        val third = fresh(saved.copy())
        third.bind(date(), before)
        assertEquals(EndStep.Guide, third.ui.value.step)
    }

    @Test
    fun `a restored gated step without a check-in goes back to the menu`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(checkedIn = false, saved = saved)
        first.openReport()
        first.pick(EndOption.Uncomfortable)
        assertEquals(EndStep.Uncomfortable, first.ui.value.gated)

        val restored = fresh(saved.copy())
        restored.bind(date(checkedIn = false), before)
        assertEquals(EndRoute.Report, restored.ui.value.route)
        assertEquals(EndStep.Menu, restored.ui.value.step)
        assertNull(restored.ui.value.gated)
        assertTrue(restored.ui.value.visible)

        val checked = fresh(saved.copy())
        checked.bind(date(checkedIn = true), before)
        assertEquals(EndStep.Uncomfortable, checked.ui.value.step)
    }

    @Test
    fun `a restored step that needs a check-in goes to the menu when there is none`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.openReport()
        first.pick(EndOption.Uncomfortable)
        val restored = fresh(saved.copy())
        restored.bind(date(checkedIn = false), before)
        assertEquals(EndStep.Menu, restored.ui.value.step)
    }

    @Test
    fun `a restored sheet is closed when the ends are switched off or it is another date`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        vm(saved = saved).openWrap(EndEntry.EndDateBar)
        val off = fresh(saved.copy())
        off.bind(date(endsEnabled = false), before)
        assertNull(off.ui.value.route)
        val other = fresh(saved.copy())
        other.bind(date(id = "d2"), before)
        assertNull(other.ui.value.route)
    }

    @Test
    fun `a restore fires no event`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        vm(saved = saved).openWrap(EndEntry.EndDateBar)
        analytics.clear()
        val restored = fresh(saved.copy())
        restored.bind(date(), before)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `a closed sheet leaves nothing to restore`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.openWrap(EndEntry.EndDateBar)
        first.close()
        val restored = fresh(saved.copy())
        restored.bind(date(), before)
        assertNull(restored.ui.value.route)
    }

    @Test
    fun `another date resets the model`() = runTest(dispatcher) {
        val vm = vm()
        vm.openWrap(EndEntry.EndDateBar)
        vm.bind(date(id = "d2"), before)
        assertNull(vm.ui.value.route)
    }
}
