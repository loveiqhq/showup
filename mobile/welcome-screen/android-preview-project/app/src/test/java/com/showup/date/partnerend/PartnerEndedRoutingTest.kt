/*
 * PartnerEndedRoutingTest.kt
 * ShowUp · Date 08's routing: which ended date goes to it, the route and its once-per-date guard, the screen view and
 * its referrer, no way out but Proceed to review, and every sheet closed behind it (SHOWUP-199, R1, R2, R6, R7)
 */
package com.showup.date.partnerend

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.showup.AppRoute
import com.showup.analytics.RecordingAnalytics
import com.showup.chat.ChatHostEvents
import com.showup.chat.partnerEndedReferrer
import com.showup.chat.partnerEndedRoute
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.EndEntry
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.PartnerScreen
import com.showup.date.cancel.CancelViewModel
import com.showup.date.cancel.EndedRoute
import com.showup.date.cancel.FakeCancelStore
import com.showup.date.cancel.endedRoute
import com.showup.date.end.DateEndViewModel
import com.showup.date.end.EndRoute
import com.showup.date.end.FakeEndStore
import com.showup.date.end.LandedOnce
import com.showup.date.EndDateResult
import com.showup.date.end.EndOption
import com.showup.date.CancelResult
import com.showup.arrival.ArrivalEntry
import com.showup.arrival.ArrivalGateViewModel
import com.showup.arrival.CheckInResult
import com.showup.arrival.FakeArrivalStore
import com.showup.arrival.GatePhase
import com.showup.arrival.LocationFix
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.showup.profile.ProfileScreen
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class PartnerEndedRoutingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ended(reason: OutcomeReason, byYou: Boolean, screen: PartnerScreen? = null, id: String = "d1") =
        EndedOutcome(id, "Leonie", endedByYou = byYou, reason = reason, partnerScreen = screen)

    // -- endedRoute --------------------------------------------------------------------------

    @Test
    fun `Date 06 end on you is PartnerEnded for each of the three reasons`() {
        listOf(
            OutcomeReason.PartnerNoShow to PartnerScreen.YouNoShowed,
            OutcomeReason.Uncomfortable to PartnerScreen.DateEndedDiscomfort,
            OutcomeReason.Met to PartnerScreen.DateClosedPositive,
        ).forEach { (reason, screen) ->
            assertEquals(reason.name, EndedRoute.PartnerEnded, endedRoute(ended(reason, byYou = false, screen = screen)))
        }
    }

    @Test
    fun `your own Date 06 end lands`() {
        listOf(OutcomeReason.PartnerNoShow, OutcomeReason.Uncomfortable, OutcomeReason.Met).forEach {
            assertEquals(EndedRoute.Land, endedRoute(ended(it, byYou = true)))
            // Even if a server sent the match's screen to the person who ended it.
            assertEquals(EndedRoute.Land, endedRoute(ended(it, byYou = true, screen = PartnerScreen.DateClosedPositive)))
        }
    }

    @Test
    fun `a Date 06 end with no partnerScreen lands, as an older server sends it`() {
        assertEquals(EndedRoute.Land, endedRoute(ended(OutcomeReason.Met, byYou = false)))
    }

    @Test
    fun `chat end is DateEnded either way`() {
        assertEquals(EndedRoute.DateEnded, endedRoute(ended(OutcomeReason.Chat, byYou = true)))
        assertEquals(EndedRoute.DateEnded, endedRoute(ended(OutcomeReason.Chat, byYou = false)))
    }

    @Test
    fun `cancel on you is PartnerCancelled, your own cancel lands`() {
        assertEquals(EndedRoute.PartnerCancelled, endedRoute(ended(OutcomeReason.Cancelled, byYou = false)))
        assertEquals(EndedRoute.Land, endedRoute(ended(OutcomeReason.Cancelled, byYou = true)))
    }

    // -- ids and the route -------------------------------------------------------------------

    @Test
    fun `partnerEndedScreenId maps the three screens to their ids and names`() {
        assertEquals(ProfileScreen.DatePartnerEndedNoShow, partnerEndedScreenId(PartnerScreen.YouNoShowed))
        assertEquals(ProfileScreen.DatePartnerEndedUncomfortable, partnerEndedScreenId(PartnerScreen.DateEndedDiscomfort))
        assertEquals(ProfileScreen.DatePartnerEndedMet, partnerEndedScreenId(PartnerScreen.DateClosedPositive))
        assertEquals("date_partner_ended_no_show", ProfileScreen.DatePartnerEndedNoShow.screenId)
        assertEquals("Date - Partner ended no-show", ProfileScreen.DatePartnerEndedNoShow.screenName)
        assertEquals("date_partner_ended_uncomfortable", ProfileScreen.DatePartnerEndedUncomfortable.screenId)
        assertEquals("Date - Partner ended uncomfortable", ProfileScreen.DatePartnerEndedUncomfortable.screenName)
        assertEquals("date_partner_ended_met", ProfileScreen.DatePartnerEndedMet.screenId)
        assertEquals("Date - Partner ended met", ProfileScreen.DatePartnerEndedMet.screenName)
    }

    @Test
    fun `the route carries everything it draws, the outcome's own photo first`() {
        val outcome = ended(OutcomeReason.Met, byYou = false, screen = PartnerScreen.DateClosedPositive)
            .copy(photoUrl = "https://x/own.jpg")
        assertEquals(
            AppRoute.PartnerEnded("d1", "Leonie", "date_closed_positive", "https://x/own.jpg"),
            partnerEndedRoute(outcome, "https://x/held.jpg"),
        )
        assertEquals("https://x/held.jpg", partnerEndedRoute(outcome.copy(photoUrl = null), "https://x/held.jpg").photoUrl)
    }

    @Test
    fun `two reads of one end route once`() {
        val once = LandedOnce()
        assertTrue(once.first("d1"))
        assertFalse(once.first("d1"))
        assertTrue(once.first("d2"))
    }

    // -- tracking ----------------------------------------------------------------------------

    // The referrer is decided by what was on screen, not by whether a goTo happened: a launch sets the screen
    // directly (cameFrom stays null all session), and a restored cameFrom says nothing about a relaunch.
    @Test
    fun `an end found while a date is held was found on the Date screen`() {
        assertEquals(ProfileScreen.DateScreen, partnerEndedReferrer(heldDate = date(checkedIn = true), from = null))
    }

    @Test
    fun `an end found by the launch's first read, with no date held, has no referrer`() {
        assertNull(partnerEndedReferrer(heldDate = null, from = null))
    }

    @Test
    fun `an end found from the chat is the chat's, held date or not`() {
        assertEquals(ProfileScreen.DateChat, partnerEndedReferrer(heldDate = date(checkedIn = true), from = ProfileScreen.DateChat))
        assertEquals(ProfileScreen.DateChat, partnerEndedReferrer(heldDate = null, from = ProfileScreen.DateChat))
    }

    @Test
    fun `a cold start shows the payload's id with no referrer`() {
        val analytics = RecordingAnalytics()
        ChatHostEvents(analytics).partnerEndedShown(PartnerScreen.YouNoShowed, referrer = null)
        val e = analytics.of("screen_viewed").single().properties
        assertEquals("date_partner_ended_no_show", e["screen_id"])
        assertEquals("Date - Partner ended no-show", e["screen_name"])
        assertEquals("", e["referrer_screen_id"])
    }

    @Test
    fun `reached from the chat carries the chat's referrer`() {
        val analytics = RecordingAnalytics()
        ChatHostEvents(analytics).partnerEndedShown(PartnerScreen.DateEndedDiscomfort, ProfileScreen.DateChat)
        val e = analytics.of("screen_viewed").single().properties
        assertEquals("date_partner_ended_uncomfortable", e["screen_id"])
        assertEquals("date_chat", e["referrer_screen_id"])
    }

    @Test
    fun `screen_viewed fires each time the screen is shown, and Proceed fires nothing and starts the review`() = runComposeUiTest {
        val analytics = RecordingAnalytics()
        val events = ChatHostEvents(analytics)
        var proceeded = 0
        var showing by mutableStateOf(true)
        setContent {
            WithBack(OnBackPressedDispatcher()) {
                if (showing) {
                    PartnerEndedRoute(
                        PartnerScreen.DateClosedPositive, "Leonie", null,
                        onShown = { events.partnerEndedShown(PartnerScreen.DateClosedPositive, ProfileScreen.DateScreen) },
                        onProceed = { proceeded++ },
                    )
                }
            }
        }
        waitForIdle()
        assertEquals(1, analytics.of("screen_viewed").size)
        onNodeWithTag(PARTNER_ENDED_CTA_TAG).performClick()
        waitForIdle()
        assertEquals(1, proceeded)
        assertEquals("Proceed fires no event", listOf("screen_viewed"), analytics.names())
        // Shown again (a new showing): another view.
        showing = false
        waitForIdle()
        showing = true
        waitForIdle()
        assertEquals(2, analytics.of("screen_viewed").size)
    }

    @get:Rule val rule = createComposeRule()

    @Test
    fun `a rotation is not a second view, a restore after process death is`() {
        val analytics = RecordingAnalytics()
        val events = ChatHostEvents(analytics)
        val tester = StateRestorationTester(rule)
        tester.setContent {
            WithBack(OnBackPressedDispatcher()) {
                PartnerEndedRoute(
                    PartnerScreen.DateClosedPositive, "Leonie", null,
                    onShown = { events.partnerEndedShown(PartnerScreen.DateClosedPositive, ProfileScreen.DateScreen) },
                    onProceed = {},
                )
            }
        }
        rule.waitForIdle()
        assertEquals(1, analytics.of("screen_viewed").size)
        // Configuration change: same process, state saved and restored.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("a rotation adds no view", 1, analytics.of("screen_viewed").size)
        // Process death: the restore happens in a new process.
        val before = partnerEndedProcessId
        try {
            partnerEndedProcessId = "a-new-process"
            tester.emulateSavedInstanceStateRestore()
            rule.waitForIdle()
            assertEquals("B reopening the app is a view", 2, analytics.of("screen_viewed").size)
            // ...and a rotation after that one is again not.
            tester.emulateSavedInstanceStateRestore()
            rule.waitForIdle()
            assertEquals(2, analytics.of("screen_viewed").size)
        } finally {
            partnerEndedProcessId = before
        }
    }

    // -- no exit -----------------------------------------------------------------------------

    @Test
    fun `system back on the screen does nothing, the route is still on top`() = runComposeUiTest {
        val dispatcher = OnBackPressedDispatcher(fallbackOnBackPressed = { error("back reached the system: the screen can be left") })
        setContent {
            WithBack(dispatcher) {
                PartnerEndedRoute(PartnerScreen.YouNoShowed, "Leonie", null, onShown = {}, onProceed = {})
            }
        }
        waitForIdle()
        runOnUiThread { dispatcher.onBackPressed() }
        waitForIdle()
        onNodeWithTag(PARTNER_ENDED_CTA_TAG).assertIsDisplayed()
        assertTrue("the handler stays enabled", dispatcher.hasEnabledCallbacks())
    }

    @Composable
    private fun WithBack(dispatcher: OnBackPressedDispatcher, content: @Composable () -> Unit) {
        val owner = remember {
            object : OnBackPressedDispatcherOwner, LifecycleOwner {
                private val registry = LifecycleRegistry(this).also { it.currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
                override val onBackPressedDispatcher = dispatcher
            }
        }
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner, content = content)
    }

    // -- every sheet closes ------------------------------------------------------------------

    private val meet = Instant.parse("2026-11-21T18:00:00Z")

    private fun date(checkedIn: Boolean) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = true,
        place = DatePlace("Cafe Krone", null), match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null, endsEnabled = true,
    )

    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun `PartnerEnded closes the Date 06 sheet and its saved state`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = DateEndViewModel(FakeEndStore(), RecordingAnalytics(), saved, reread = {})
        vm.bind(date(checkedIn = true), meet.minusSeconds(300))
        runCurrent()
        vm.openWrap(EndEntry.EndDateBar)
        assertEquals(EndRoute.Wrap, vm.ui.value.route)
        vm.discardForPartnerEnd()
        assertNull(vm.ui.value.route)
        assertFalse(vm.ui.value.visible)
        // After a process death the model is rebuilt from what was saved: nothing comes back.
        val rebuilt = DateEndViewModel(FakeEndStore(), RecordingAnalytics(), saved.copy(), reread = {})
        assertNull(rebuilt.ui.value.route)
    }

    @Test
    fun `PartnerEnded closes the Date 07 guard and its saved state`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = CancelViewModel(FakeCancelStore(), RecordingAnalytics(), saved, reread = {})
        vm.bind(date(checkedIn = false), meet.minusSeconds(3 * 3600))
        runCurrent()
        vm.open()
        assertTrue(vm.ui.value.open)
        vm.discardForPartnerEnd()
        assertFalse(vm.ui.value.open)
        val rebuilt = CancelViewModel(FakeCancelStore(), RecordingAnalytics(), saved.copy(), reread = {})
        assertFalse(rebuilt.ui.value.open)
    }

    @Test
    fun `PartnerEnded pops the Date 05 picker, the chat and the Date screen off the stack`() = runComposeUiTest {
        lateinit var nav: NavHostController
        setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = AppRoute.Flow) {
                composable<AppRoute.Flow> {}
                composable<AppRoute.Chat> {}
                composable<AppRoute.ChangePicker> {}
                composable<AppRoute.PartnerEnded> {}
            }
        }
        waitForIdle()
        runOnUiThread {
            nav.navigate(AppRoute.Chat("open_chat"))
            nav.navigate(AppRoute.ChangePicker("time", "chat_chip"))
        }
        waitForIdle()
        runOnUiThread { nav.showPartnerEnded(AppRoute.PartnerEnded("d1", "Leonie", "you_no_showed", null)) }
        waitForIdle()
        val routes = nav.currentBackStack.value.mapNotNull { it.destination.route }.filterNot { it.endsWith("Graph") }
        assertEquals(routes.toString(), 2, routes.size)
        assertTrue(routes.first().contains("Flow"))
        assertTrue(routes.last().contains("PartnerEnded"))
    }

    // -- SHOWUP-200: Date 09 is this screen's fourth payload ---------------------------------

    @Test
    fun `a timed-out date is PartnerEnded for both people, whatever partnerScreen says, and never the chat's G`() {
        for (byYou in listOf(true, false)) for (screen in listOf(null, PartnerScreen.DateTimedOut, PartnerScreen.YouNoShowed)) {
            assertEquals(
                "byYou=$byYou screen=$screen", EndedRoute.PartnerEnded,
                endedRoute(ended(OutcomeReason.TimedOut, byYou = byYou, screen = screen)),
            )
        }
    }

    @Test
    fun `every other reason keeps its route`() {
        val table = mapOf(
            OutcomeReason.Chat to (EndedRoute.DateEnded to EndedRoute.DateEnded),
            OutcomeReason.Cancelled to (EndedRoute.PartnerCancelled to EndedRoute.Land),
            OutcomeReason.Met to (EndedRoute.Land to EndedRoute.Land),
            OutcomeReason.PartnerNoShow to (EndedRoute.Land to EndedRoute.Land),
            OutcomeReason.Uncomfortable to (EndedRoute.Land to EndedRoute.Land),
        )
        table.forEach { (reason, expected) ->
            // Left: ended by the other person; right: ended by you. Date 06's reasons need a partnerScreen to reach Date 08.
            assertEquals(reason.name, expected.first, endedRoute(ended(reason, byYou = false)))
            assertEquals(reason.name, expected.second, endedRoute(ended(reason, byYou = true)))
        }
    }

    @Test
    fun `Date 09 reports date_timed_out`() {
        assertEquals(ProfileScreen.DateTimedOut, partnerEndedScreenId(PartnerScreen.DateTimedOut))
        assertEquals("date_timed_out", ProfileScreen.DateTimedOut.screenId)
        assertEquals("Date - Timed out", ProfileScreen.DateTimedOut.screenName)
    }

    @Test
    fun `a timed-out outcome with no partnerScreen still gets a route, carrying the meet time`() {
        val at = Instant.parse("2026-11-28T18:00:00Z")
        val route = partnerEndedRoute(ended(OutcomeReason.TimedOut, byYou = false).copy(meetAt = at), "https://x/held.jpg", null)
        assertEquals(AppRoute.PartnerEnded("d1", "Leonie", "date_timed_out", "https://x/held.jpg", "2026-11-28T18:00:00Z"), route)
    }

    @Test
    fun `the meet time falls back to the held date's, and is absent when neither has one`() {
        val held = date(checkedIn = true)
        val outcome = ended(OutcomeReason.TimedOut, byYou = false)
        assertEquals(meet.toString(), partnerEndedRoute(outcome, "p", held).meetAt)
        assertEquals(
            "the outcome's own wins", "2026-11-28T18:00:00Z",
            partnerEndedRoute(outcome.copy(meetAt = Instant.parse("2026-11-28T18:00:00Z")), "p", held).meetAt,
        )
        assertNull(partnerEndedRoute(outcome, "p", null).meetAt)
    }

    @Test
    fun `the route survives a save and restore with its meet time`() {
        val route: AppRoute = AppRoute.PartnerEnded("d1", "Leonie", "date_timed_out", null, "2026-11-28T18:00:00Z")
        assertEquals(route, Json.decodeFromString<AppRoute>(Json.encodeToString(route)))
        // A route saved by Date 08 (no meetAt) still restores.
        val old: AppRoute = AppRoute.PartnerEnded("d1", "Leonie", "you_no_showed", null)
        assertNull((Json.decodeFromString<AppRoute>(Json.encodeToString(old)) as AppRoute.PartnerEnded).meetAt)
    }

    @Test
    fun `Date 09 reports its view from the chat and from a cold start`() {
        val analytics = RecordingAnalytics()
        ChatHostEvents(analytics).partnerEndedShown(PartnerScreen.DateTimedOut, ProfileScreen.DateChat)
        ChatHostEvents(analytics).partnerEndedShown(PartnerScreen.DateTimedOut, null)
        val shown = analytics.of("screen_viewed").map { it.properties }
        assertEquals(listOf("date_timed_out", "date_timed_out"), shown.map { it["screen_id"] })
        assertEquals(listOf("date_chat", ""), shown.map { it["referrer_screen_id"] })
        assertEquals(listOf("screen_viewed", "screen_viewed"), analytics.names())
    }

    @Test
    fun `a late Date 06 end answered date_not_active re-reads and routes to Date 09 with no error box`() = runTest(dispatcher) {
        val timedOut = ended(OutcomeReason.TimedOut, byYou = false)
        val routed = mutableListOf<EndedRoute>()
        val endStore = FakeEndStore().also { it.answers += EndDateResult.Refused("date_not_active") }
        val end = DateEndViewModel(endStore, RecordingAnalytics(), SavedStateHandle(), reread = { routed += endedRoute(timedOut) })
        end.bind(date(checkedIn = true), meet.plusSeconds(60))
        runCurrent()
        end.openWrap(EndEntry.EndDateBar)
        end.pick(EndOption.Met)
        end.end()
        runCurrent()
        assertFalse("no EndErrorBox", end.ui.value.failed)
        assertNull(end.ui.value.route)
        assertEquals(listOf(EndedRoute.PartnerEnded), routed)
    }

    @Test
    fun `a late Date 07 cancel answered date_not_active re-reads and routes to Date 09 with no error`() = runTest(dispatcher) {
        val timedOut = ended(OutcomeReason.TimedOut, byYou = false)
        val routed = mutableListOf<EndedRoute>()
        val store = FakeCancelStore().also { it.answers += CancelResult.Refused("date_not_active") }
        val cancel = CancelViewModel(store, RecordingAnalytics(), SavedStateHandle(), reread = { routed += endedRoute(timedOut) })
        cancel.bind(date(checkedIn = false), meet.minusSeconds(3 * 3600))
        runCurrent()
        cancel.open()
        cancel.cancel()
        runCurrent()
        assertFalse("no error box", cancel.ui.value.failed)
        assertFalse(cancel.ui.value.open)
        assertEquals(listOf(EndedRoute.PartnerEnded), routed)
    }

    // -- the switch closes every sheet, and sends nothing ------------------------------------

    private fun gate(store: FakeArrivalStore, reread: () -> Unit = {}) =
        ArrivalGateViewModel(store, { LocationFix(52.5, 13.4, 8.0) }, RecordingAnalytics(), { meet }, { "id-1" }, reread = reread)

    @Test
    fun `with the check-in done sheet open the switch closes it and neither commit nor undo reaches the store`() = runTest(dispatcher) {
        val store = FakeArrivalStore()
        val gate = gate(store)
        gate.bind(date(checkedIn = false))
        runCurrent()
        gate.open(ArrivalEntry.DateScreen)
        gate.yes()
        runCurrent()
        assertTrue(gate.ui.value.phase is GatePhase.Done)
        gate.discardForPartnerEnd()
        runCurrent()
        assertEquals(GatePhase.Idle, gate.ui.value.phase)
        assertFalse(gate.ui.value.undone)
        assertFalse("not committed either", gate.ui.value.committed)
        // Nothing can close it afterwards: Close and Undo are inert on an idle gate, and leaving the app commits nothing.
        gate.close()
        gate.undo()
        gate.leftApp()
        runCurrent()
        assertEquals(0, store.commitCalls)
        assertEquals(0, store.undoCalls)
    }

    @Test
    fun `a Yes refused with 409 closes the gate and reads the date again, with no error`() = runTest(dispatcher) {
        val store = FakeArrivalStore().also { it.checkIns += CheckInResult.AlreadyCheckedIn }
        var rereads = 0
        val gate = gate(store) { rereads++ }
        gate.bind(date(checkedIn = false))
        runCurrent()
        gate.open(ArrivalEntry.DateScreen)
        gate.yes()
        runCurrent()
        assertEquals(GatePhase.Idle, gate.ui.value.phase)
        assertEquals(1, rereads)
    }
}
