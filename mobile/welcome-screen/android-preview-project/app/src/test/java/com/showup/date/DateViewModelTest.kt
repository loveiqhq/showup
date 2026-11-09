/*
 * DateViewModelTest.kt
 * ShowUp · the Date screen's loading, unlock and tracking (SHOWUP-193)
 *
 * The clock is injected, so "A → B at meet − 1:30 with the screen open" is a step of the clock and
 * not a wait. Tracking is asserted against the registry's exact payloads.
 */
package com.showup.date

import com.showup.chat.ChatReason
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.designsystem.Motion
import com.showup.profile.ProfileScreen
import com.showup.search.SearchProfile
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private var now = Instant.parse("2026-11-03T15:00:00Z") // 3 h before: state A
    private val analytics = RecordingAnalytics()
    private val store = FakeDateStore()

    private fun date(unread: DateUnread? = null, checkedIn: Boolean = false) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = emptyList(), sharedInterests = emptyList(), unread = unread, checkedIn = checkedIn, chatTutorialSeen = false,
    )

    private fun vm() = DateViewModel(store, analytics, clock = { now }, zone = { ZoneId.of("Europe/Berlin") })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a visit in state A fires screen_viewed and date_screen_viewed once, with the registry's payloads`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()

        assertEquals(listOf("screen_viewed", "date_screen_viewed"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("date_screen", view["screen_id"])
        assertEquals("Date - Date screen", view["screen_name"])
        assertEquals("", view["referrer_screen_id"])
        val state = analytics.of("date_screen_viewed").single().properties
        assertEquals("d1", state["date_id"])
        assertEquals("locked", state["state"])
        assertEquals(false, state["has_unread"])
        assertEquals(false, state["checked_in"])
        assertEquals(180, state["minutes_before_start"])
        assertEquals(DateState.Locked, vm.state.value.state)
    }

    @Test
    fun `a visit in C reports available with has_unread, and the referrer`() = runTest(dispatcher) {
        now = unlock.plusSeconds(60)
        store.read = Outcome.Ok(DateRead.Active(date(unread = DateUnread(1, ChatReason.RunningLate, "Late!", now))))
        val vm = vm()
        vm.arrived(referrer = ProfileScreen.SearchProfile)
        advanceUntilIdle()

        assertEquals("search_profile", analytics.of("screen_viewed").single().properties["referrer_screen_id"])
        val state = analytics.of("date_screen_viewed").single().properties
        assertEquals("available", state["state"])
        assertEquals(true, state["has_unread"])
        assertEquals(89, state["minutes_before_start"])
        assertEquals(DateState.Unread, vm.state.value.state)
    }

    @Test
    fun `the unlock happens in place as the clock passes meet minus 1h30, with no event`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        analytics.clear()

        now = unlock.minusSeconds(1)
        vm.tick()
        assertEquals(DateState.Locked, vm.state.value.state)
        now = unlock
        vm.tick()
        assertEquals(DateState.Available, vm.state.value.state)
        assertEquals(emptyList<String>(), analytics.names())
    }

    @Test
    fun `foreground after the unlock shows B without a visit event`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        analytics.clear()

        now = unlock.plusSeconds(600)
        vm.foreground()
        advanceUntilIdle()
        assertEquals(DateState.Available, vm.state.value.state)
        assertEquals(emptyList<String>(), analytics.names())
        assertEquals(2, store.reads)
    }

    @Test
    fun `a visit that starts before the read lands reports once, when it lands`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        vm.arrived(referrer = null)
        advanceUntilIdle()
        assertEquals(1, analytics.of("date_screen_viewed").size)
    }

    @Test
    fun `a date ended in the chat shows its outcome instead of leaving, the date kept for its face`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        store.read = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = false)))
        vm.foreground()
        advanceUntilIdle()
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = false), vm.state.value.ended)
        assertFalse(vm.state.value.left)
        assertEquals("the match's photo stays for the outcome screen", date(), vm.state.value.date)
    }

    @Test
    fun `once the outcome has been seen, no date is held and the tabs unlock`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = true)))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        vm.outcomeShown()
        assertEquals(null, vm.state.value.ended)
        assertEquals(null, vm.state.value.date)
    }

    @Test
    fun `while on screen the date is read again every 15 s, and not once it is off screen`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceTimeBy(1)
        val reads = store.reads
        vm.onScreen(true)
        advanceTimeBy(15_001)
        assertTrue("re-read within 15 s", store.reads >= reads + 1)
        vm.onScreen(false)
        val after = store.reads
        advanceTimeBy(60_000)
        assertEquals(after, store.reads)
    }

    @Test
    fun `Open the chat marks the held date's tutorial as seen at once`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        vm.chatTutorialSeen()
        assertEquals(true, vm.state.value.date?.chatTutorialSeen)
    }

    @Test
    fun `a change tile fires action_chip_used on the coordinate card, never a quick action`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        analytics.clear()
        vm.changeTile(ChatReason.ChangeTime)
        val used = analytics.of("action_chip_used").single().properties
        assertEquals("d1", used["date_id"])
        assertEquals("change_time", used["reason"])
        assertEquals("coordinate_card", used["surface"])
        assertEquals(180, used["minutes_before_start"])
        assertTrue(analytics.of("date_quick_action_tapped").isEmpty())
    }

    @Test
    fun `no date on a re-read leaves the screen`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        store.read = Outcome.Ok(DateRead.None)
        vm.foreground()
        advanceUntilIdle()
        assertTrue(vm.state.value.left)
    }

    @Test
    fun `a failed re-read keeps the date on screen`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        store.read = Outcome.Failed(CallFailure.Network)
        vm.foreground()
        advanceUntilIdle()
        assertEquals("d1", vm.state.value.date?.id)
        assertFalse(vm.state.value.left)
    }

    @Test
    fun `each quick action reports its section 38 value, and ride is never one`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        analytics.clear()

        QuickAction.entries.forEach(vm::quickAction)
        val sent = analytics.of("date_quick_action_tapped").map { it.properties }
        assertEquals(listOf("directions", "share", "calendar", "copy_address"), sent.map { it["action"] })
        assertTrue(sent.all { it["date_id"] == "d1" && it["minutes_before_start"] == 180 })
        assertFalse(QuickAction.entries.any { it.wire == "ride" })
    }

    @Test
    fun `Copied shows for 1600 ms`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.copied()
        assertTrue(vm.state.value.copied)
        advanceTimeBy(Motion.COPIED - 1L)
        assertTrue(vm.state.value.copied)
        advanceTimeBy(2L)
        assertFalse(vm.state.value.copied)
        assertEquals(1600, Motion.COPIED)
    }

    @Test
    fun `a date seeded from the launch read draws at once, and the visit reports from the re-read`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.seed(date())
        assertEquals(date(), vm.state.value.date)
        assertFalse(vm.state.value.loading)
        assertTrue("seeding reports nothing", analytics.names().isEmpty())
        vm.arrived(referrer = null)
        advanceUntilIdle()
        assertEquals(listOf("screen_viewed", "date_screen_viewed"), analytics.names())
    }

    @Test
    fun `a new arrival after the date went away does not leave before its read lands`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        store.read = Outcome.Ok(DateRead.None)
        vm.foreground()
        advanceUntilIdle()
        assertTrue(vm.state.value.left)
        // A second date in the same process (or End test date, then a new one): the screen arrives
        // with the old `left` -- the host's leave rule must not see it before the new read.
        store.read = Outcome.Ok(DateRead.Active(date().copy(id = "d2")))
        vm.arrived(referrer = null)
        assertFalse("whether the date is gone is the read's to say", vm.state.value.left)
        advanceUntilIdle()
        assertEquals("d2", vm.state.value.date?.id)
    }

    @Test
    fun `seeding a new date replaces a stale one and clears a leave`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.Active(date()))
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        store.read = Outcome.Ok(DateRead.None)
        vm.foreground()
        advanceUntilIdle()
        vm.seed(date().copy(id = "d2"))
        assertEquals("d2", vm.state.value.date?.id)
        assertFalse(vm.state.value.left)
    }

    @Test
    fun `a new date read after one went away is shown, not left`() = runTest(dispatcher) {
        store.read = Outcome.Ok(DateRead.None)
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        assertTrue(vm.state.value.left)
        store.read = Outcome.Ok(DateRead.Active(date()))
        vm.foreground()
        advanceUntilIdle()
        assertFalse(vm.state.value.left)
        assertEquals(date(), vm.state.value.date)
    }

    @Test
    fun `a failed first read shows the failure, and Try again reads again and reports the visit`() = runTest(dispatcher) {
        store.read = Outcome.Failed(CallFailure.Network)
        val vm = vm()
        vm.arrived(referrer = null)
        advanceUntilIdle()
        assertEquals(CallFailure.Network, vm.state.value.failed)
        assertEquals(null, vm.state.value.date)
        assertTrue(analytics.names().isEmpty())

        store.read = Outcome.Ok(DateRead.Active(date()))
        val reads = store.reads
        vm.retry()
        assertTrue("loading again while it reads", vm.state.value.loading)
        // 9 Nov 2026: the message stays while it reloads, and a second tap does nothing.
        assertEquals(CallFailure.Network, vm.state.value.failed)
        vm.retry()
        advanceUntilIdle()
        assertEquals(reads + 1, store.reads)
        assertEquals(null, vm.state.value.failed)
        assertEquals(date(), vm.state.value.date)
        assertEquals(listOf("screen_viewed", "date_screen_viewed"), analytics.names())
    }
}

/** Answers from fields the test sets; counts the reads. */
class FakeDateStore : DateStore {
    var read: Outcome<DateRead> = Outcome.Ok(DateRead.None)
    var reads = 0
    var profile: Outcome<SearchProfile> = Outcome.Failed(CallFailure.Server)

    override suspend fun active(zone: String): Outcome<DateRead> {
        reads++
        return read
    }

    override suspend fun partnerProfile(zone: String): Outcome<SearchProfile> = profile
    override suspend fun createTestDate(minutes: Int) = Result.success(true)
    override suspend fun messageFromMatch(reason: ChatReason?, body: String) = Result.success(true)
    override suspend fun endTestDate() = Result.success(true)
    override suspend fun matchEndsInChat() = Result.success(true)
}
