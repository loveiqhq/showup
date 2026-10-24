/*
 * CheckInViewModelTest.kt
 * ShowUp · the 48hrs Check-In's state machine, driven with no device (SHOWUP-178 to SHOWUP-183)
 *
 * Every row of the six tickets' tracking tables with its payload, the order they fire in ("the act's
 * event, then navigation, then the next screen_viewed"), every event that must NOT fire, and the
 * behaviour the acceptance criteria name: single-fire CTAs, inert controls while saving, what ✕
 * discards and what back keeps, the pre-fills, the clock, the commit body, retries and failures.
 *
 * Payloads are also checked against `events.json` itself: a builder that drifts from the registry
 * fails here, not in the warehouse.
 */
package com.showup.checkin

import com.showup.api.Outcome
import com.showup.api.CallFailure
import com.showup.analytics.JsonNull
import com.showup.analytics.RecordingAnalytics
import com.showup.profile.ProfileScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    /** A server that records every call, answers from what it holds, and can be made to fail. */
    private class FakeStore : CheckInStore {
        var held = CheckInState(
            windowId = WINDOW, introPassed = false, active = false, firstCheckIn = true,
            lastMotivation = null, lastVibes = emptySet(), filters = CheckInFilters(setOf("woman")),
        )
        var reads = true
        var readFailure = CallFailure.Network
        var filterSaves = true
        var filterFailure = CallFailure.Network
        /** Per filter save, in order: how long it takes and whether it lands. Empty: instant, [filterSaves]. */
        val filterScript = ArrayDeque<Pair<Long, Boolean>>()
        var introMarksLand = true
        var commitAnswers = ArrayDeque<CommitResult>()
        var commitDelayMs = 0L
        var loadDelayMs = 0L
        val commits = mutableListOf<CommitRequest>()
        val answers = mutableListOf<Pair<String?, Set<String>?>>()
        val filters = mutableListOf<CheckInFilters>()
        var introMarks = 0
        var loads = 0

        override suspend fun load(): Outcome<CheckInState> {
            loads++
            val snapshot = held
            if (loadDelayMs > 0) delay(loadDelayMs)
            return if (reads) Outcome.Ok(snapshot) else Outcome.Failed(readFailure)
        }
        override suspend fun markIntroPassed(): Boolean {
            introMarks++
            if (introMarksLand) held = held.copy(introPassed = true)
            return introMarksLand
        }
        override suspend fun saveAnswers(motivation: String?, vibes: Set<String>?): Boolean {
            answers += motivation to vibes
            return true
        }
        override suspend fun saveFilters(filters: CheckInFilters): Outcome<Unit> {
            this.filters += filters
            val (delayMs, lands) = filterScript.removeFirstOrNull() ?: (0L to filterSaves)
            if (delayMs > 0) delay(delayMs)
            if (lands) held = held.copy(filters = filters)
            return if (lands) Outcome.Ok(Unit) else Outcome.Failed(filterFailure)
        }
        override suspend fun commit(request: CommitRequest): CommitResult {
            commits += request
            if (commitDelayMs > 0) delay(commitDelayMs)
            return commitAnswers.removeFirstOrNull() ?: CommitResult.Saved(held.copy(active = true))
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeStore
    private lateinit var analytics: RecordingAnalytics
    private var now: LocalDateTime = LocalDateTime.of(2026, 10, 13, 16, 20)
    private var ms = 1_000L
    private lateinit var model: CheckInViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
        analytics = RecordingAnalytics()
        now = LocalDateTime.of(2026, 10, 13, 16, 20)
        ms = 1_000L
        model = CheckInViewModel(store, analytics, clock = { now }, zone = { ZoneOffset.UTC }, millis = { ms })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = model.state.value

    private fun TestScope.begin() {
        model.beginFlow()
        advanceUntilIdle()
    }

    private fun TestScope.arrive(step: CheckInStep, referrer: ProfileScreen? = null) {
        model.arrived(step, step.screen, referrer)
        advanceUntilIdle()
    }

    /** Drives the flow from the intro to [to], answering each step the simplest valid way. */
    private fun TestScope.walkTo(to: CheckInStep) {
        begin()
        arrive(CheckInStep.Today)
        if (to == CheckInStep.Today) return
        model.slotTapped(CheckInStep.Today, state.todayGrid!!.starts[1])
        model.availabilityContinue(CheckInStep.Today) {}
        arrive(CheckInStep.Tomorrow, ProfileScreen.CheckinToday)
        if (to == CheckInStep.Tomorrow) return
        model.slotTapped(CheckInStep.Tomorrow, state.tomorrowGrid!!.starts[5])
        model.availabilityContinue(CheckInStep.Tomorrow) {}
        arrive(CheckInStep.Motivation, ProfileScreen.CheckinTomorrow)
        if (to == CheckInStep.Motivation) return
        model.motivationTapped("long_term")
        model.motivationContinue {}
        arrive(CheckInStep.Vibe, ProfileScreen.CheckinMotivation)
        if (to == CheckInStep.Vibe) return
        model.vibeTapped("calm")
        model.vibeContinue {}
        arrive(CheckInStep.Filters, ProfileScreen.CheckinVibe)
        if (to == CheckInStep.Filters) return
        model.filtersContinue {}
        arrive(CheckInStep.Uplift, ProfileScreen.CheckinFilters)
        advanceUntilIdle()
    }

    private fun props(event: String) = analytics.of(event).single().properties

    // ── 01: the intro and the gate ──────────────────────────────────────────────────────────────

    @Test
    fun `the intro reports its view with the navigation's referrer`() = runTest(dispatcher) {
        begin()
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, ProfileScreen.LifeAhead)
        val viewed = props("screen_viewed")
        assertEquals("checkin_intro", viewed["screen_id"])
        assertEquals("CheckinIntro", viewed["screen_name"])
        assertEquals("profile_life_ahead", viewed["referrer_screen_id"])
    }

    @Test
    fun `the intro CTA fires check_in_started once, marks the intro passed and advances`() = runTest(dispatcher) {
        begin()
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, null)
        analytics.clear()
        var advanced = 0
        model.introStarted { advanced++ }
        model.introStarted { advanced++ }
        advanceUntilIdle()
        assertEquals("single-fire: a second tap during the push does nothing", 1, advanced)
        assertEquals(listOf("check_in_started"), analytics.names())
        assertEquals(WINDOW, props("check_in_started")["window_id"])
        assertEquals(1, store.introMarks)
        assertEquals(CheckInStep.Today, model.gateDestination())
    }

    @Test
    fun `a read already in flight when the intro is passed cannot un-pass it`() = runTest(dispatcher) {
        begin()
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, null)  // starts a read
        model.introStarted {}                                               // before it lands
        store.held = store.held.copy(introPassed = false)                    // the read says "not yet"
        advanceUntilIdle()
        assertEquals(CheckInStep.Today, model.gateDestination())
    }

    @Test
    fun `a read that beats the intro write to the server cannot send the gate back to the intro`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Intro)
        store.introMarksLand = false                                         // not on the server yet
        model.introStarted {}
        arrive(CheckInStep.Today, ProfileScreen.CheckinIntro)                // reads at the NEW sequence
        assertEquals(true, state.server?.introPassed)
        assertEquals(CheckInStep.Today, model.gateDestination())
    }

    @Test
    fun `an intro write that did not land is sent again on the next read, and only until it lands`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Intro)
        store.introMarksLand = false
        model.introStarted {}
        advanceUntilIdle()
        assertEquals(1, store.introMarks)
        store.introMarksLand = true
        model.refresh(); advanceUntilIdle()
        assertEquals("the read disagreed, so the flag was sent again", 2, store.introMarks)
        assertTrue(store.held.introPassed)
        model.refresh(); advanceUntilIdle()
        assertEquals("landed: nothing more is sent", 2, store.introMarks)
    }

    @Test
    fun `a read that started before a commit cannot lock Search again`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.loadDelayMs = 1_000
        store.commitDelayMs = 500
        model.refresh()                                                      // reads "not active", lands at 1s
        model.findMyDate {}                                                  // saves at 0.5s
        advanceTimeBy(600)
        assertEquals(true, state.server?.active)
        advanceUntilIdle()
        assertEquals("the stale read cannot lock Search again", true, state.server?.active)
    }

    @Test
    fun `the intro's close lands on the gate with check_in_closed intro and keeps nothing`() = runTest(dispatcher) {
        begin()
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, null)
        var closed = false
        model.closePressed(CheckInStep.Intro) { closed = true }
        assertTrue(closed)
        assertEquals("intro", props("check_in_closed")["step_id"])
        assertEquals(WINDOW, props("check_in_closed")["window_id"])
        assertFalse(model.flowAlive)
    }

    @Test
    fun `the gate reopens at the intro until it has been passed, then at Today`() = runTest(dispatcher) {
        begin()
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, ProfileScreen.CheckinIntro)
        var to: CheckInStep? = null
        model.gateTapped(CheckInAnalytics.GateTarget.Cta) { to = it }
        assertEquals(CheckInStep.Intro, to)
        assertEquals(false, analytics.of("check_in_gate_tapped").last().properties["intro_passed"])
        assertEquals("cta", analytics.of("check_in_gate_tapped").last().properties["target"])

        // The real walk: the intro, its CTA never tapped, closed back to the gate -- then the
        // intro is passed on another device, and the next read says so.
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, ProfileScreen.CheckinSearchUnavailable)
        model.closePressed(CheckInStep.Intro) {}
        store.held = store.held.copy(introPassed = true)
        model.refresh(); advanceUntilIdle()
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, ProfileScreen.CheckinIntro)
        model.gateTapped(CheckInAnalytics.GateTarget.InstantTab) { to = it }
        assertEquals(CheckInStep.Today, to)
        val last = analytics.of("check_in_gate_tapped").last().properties
        assertEquals(true, last["intro_passed"])
        assertEquals("instant_tab", last["target"])
        assertTrue("check_in_started is the intro CTA's alone", analytics.of("check_in_started").isEmpty())
    }

    @Test
    fun `the gate fires once per showing, and Settings' Instant tab is still the gate`() = runTest(dispatcher) {
        begin()
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, null)
        var opened = 0
        model.gateTapped(CheckInAnalytics.GateTarget.SearchTab) { opened++ }
        model.gateTapped(CheckInAnalytics.GateTarget.Cta) { opened++ }
        assertEquals(1, opened)
        assertEquals(1, analytics.of("check_in_gate_tapped").size)
        model.atTabs()
        model.gateTapped(CheckInAnalytics.GateTarget.InstantTab) { opened++ }
        assertEquals(2, opened)
        assertEquals("instant_tab", analytics.of("check_in_gate_tapped").last().properties["target"])
    }

    @Test
    fun `the gate's view carries its own screen id and is reported again after Settings`() = runTest(dispatcher) {
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, ProfileScreen.CheckinIntro)
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, ProfileScreen.CheckinIntro)
        assertEquals("a recomposition is not an arrival", 1, analytics.of("screen_viewed").size)
        assertEquals("checkin_search_unavailable", props("screen_viewed")["screen_id"])
        model.atTabs()
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, null)
        assertEquals(2, analytics.of("screen_viewed").size)
    }

    // ── 02: availability ────────────────────────────────────────────────────────────────────────

    @Test
    fun `Today starts empty on every entry and its grid begins at the next full hour`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        assertTrue(state.picks.isEmpty())
        assertEquals(17, state.todayGrid!!.hours.first())
        assertEquals(24, state.tomorrowGrid!!.hours.size)
    }

    @Test
    fun `slot taps cycle and fire nothing`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        analytics.clear()
        val six = state.todayGrid!!.starts[1]
        model.slotTapped(CheckInStep.Today, six)
        assertEquals(SlotTier.Preferred, state.picks[six])
        model.slotTapped(CheckInStep.Today, six)
        assertEquals(SlotTier.Available, state.picks[six])
        model.slotTapped(CheckInStep.Today, six)
        assertNull(state.picks[six])
        model.slotTapped(CheckInStep.Today, six)
        model.clearAll(CheckInStep.Today)
        model.toggleHowItWorks()
        assertTrue(state.picks.isEmpty())
        assertTrue("no per-tap, Clear all or How it works event", analytics.names().isEmpty())
    }

    @Test
    fun `Today's Continue with hours fires step_answered then slots_set then advances`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        analytics.clear()
        val g = state.todayGrid!!
        model.slotTapped(CheckInStep.Today, g.starts[1])
        model.slotTapped(CheckInStep.Today, g.starts[2])
        model.slotTapped(CheckInStep.Today, g.starts[3]); model.slotTapped(CheckInStep.Today, g.starts[3])
        ms += 4_000
        val order = mutableListOf<String>()
        model.availabilityContinue(CheckInStep.Today) { order += "navigate" }
        order.addAll(0, analytics.names())
        assertEquals(listOf("check_in_step_answered", "availability_slots_set", "navigate"), order)
        val answered = props("check_in_step_answered")
        assertEquals("today", answered["step_id"])
        assertEquals("hours", answered["answer_value"])
        assertEquals(4_000L, answered["time_to_answer_ms"])
        val slots = props("availability_slots_set")
        assertEquals("today", slots["day"])
        assertEquals(2, slots["preferred_hours"])
        assertEquals(1, slots["available_hours"])
        assertEquals(WINDOW, slots["window_id"])
    }

    @Test
    fun `an empty Today raises the confirm, never an error, and fires nothing until answered`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        analytics.clear()
        var moved = false
        model.availabilityContinue(CheckInStep.Today) { moved = true }
        assertTrue(state.noHoursDialog)
        assertFalse(moved)
        assertNull(state.toast)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `Continue to tomorrow records an empty Today, resolution first`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        analytics.clear()
        model.availabilityContinue(CheckInStep.Today) {}
        var moved = false
        model.noHoursConfirmed { moved = true }
        assertTrue(moved)
        assertEquals(
            listOf("check_in_no_hours_today_resolved", "check_in_step_answered", "availability_slots_set"),
            analytics.names(),
        )
        assertEquals("continue_tomorrow", props("check_in_no_hours_today_resolved")["action"])
        assertEquals("none", props("check_in_step_answered")["answer_value"])
        assertEquals(0, props("availability_slots_set")["preferred_hours"])
        assertEquals(0, props("availability_slots_set")["available_hours"])
        assertFalse(state.noHoursDialog)
    }

    @Test
    fun `cancel and dismiss stay on Today with their own actions`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        model.availabilityContinue(CheckInStep.Today) {}
        model.noHoursCancelled(dismissed = false)
        assertFalse(state.noHoursDialog)
        model.availabilityContinue(CheckInStep.Today) {}
        model.noHoursCancelled(dismissed = true)
        assertEquals(listOf("pick_today", "dismissed"), analytics.of("check_in_no_hours_today_resolved").map { it.properties["action"] })
        assertTrue(analytics.of("check_in_step_answered").isEmpty())
        model.noHoursCancelled(dismissed = true)
        assertEquals("no dialog, no event", 2, analytics.of("check_in_no_hours_today_resolved").size)
    }

    @Test
    fun `an empty Tomorrow raises the toast for 2600 ms, a repeat restarts it, and fires nothing`() = runTest(dispatcher) {
        walkTo(CheckInStep.Tomorrow)
        analytics.clear()
        assertTrue(state.tomorrowGrid!!.picked(state.picks).isEmpty())
        var moved = false
        model.availabilityContinue(CheckInStep.Tomorrow) { moved = true }
        assertFalse(moved)
        assertEquals(CheckInCopy.TOMORROW_TOAST, state.toast)
        val tick = state.toastTick
        advanceTimeBy(2_000)
        model.availabilityContinue(CheckInStep.Tomorrow) {}
        assertEquals(tick + 1, state.toastTick)
        advanceTimeBy(2_000)
        assertNotNull("restarted, so still up 4s after the first tap", state.toast)
        advanceTimeBy(700)
        assertNull(state.toast)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `back and forward keep both days' picks, and a close discards them`() = runTest(dispatcher) {
        walkTo(CheckInStep.Tomorrow)
        val tomorrowPick = state.tomorrowGrid!!.starts[0]
        model.slotTapped(CheckInStep.Tomorrow, tomorrowPick)
        var back = false
        model.backPressed(CheckInStep.Tomorrow) { back = true }
        assertTrue(back)
        arrive(CheckInStep.Today, ProfileScreen.CheckinTomorrow)
        assertEquals(1, state.todayGrid!!.picked(state.picks).size)
        assertEquals(1, state.tomorrowGrid!!.picked(state.picks).size)
        assertEquals("checkin_tomorrow", analytics.of("screen_viewed").last().properties["referrer_screen_id"])
        model.closePressed(CheckInStep.Today) {}
        assertTrue(state.picks.isEmpty())
        assertEquals("today", analytics.of("check_in_closed").single().properties["step_id"])
    }

    @Test
    fun `an hour that starts while the flow is open falls out of the grid and the selection`() = runTest(dispatcher) {
        begin()
        arrive(CheckInStep.Today)
        val five = state.todayGrid!!.starts[0]
        val six = state.todayGrid!!.starts[1]
        model.slotTapped(CheckInStep.Today, five)
        model.slotTapped(CheckInStep.Today, six)
        now = LocalDateTime.of(2026, 10, 13, 17, 5)
        model.evaluateClock()
        assertEquals(18, state.todayGrid!!.hours.first())
        assertNull(state.picks[five])
        assertEquals(SlotTier.Preferred, state.picks[six])
    }

    @Test
    fun `across 7am the day that was Tomorrow becomes Today and keeps its picks`() = runTest(dispatcher) {
        now = LocalDateTime.of(2026, 10, 13, 5, 20)
        model.evaluateClock()
        begin()
        arrive(CheckInStep.Tomorrow)
        val eight = state.tomorrowGrid!!.starts[1]
        model.slotTapped(CheckInStep.Tomorrow, eight)
        now = LocalDateTime.of(2026, 10, 13, 6, 30)
        model.evaluateClock()
        assertEquals(eight, state.todayGrid!!.starts[1])
        assertEquals(SlotTier.Preferred, state.todayGrid!!.picked(state.picks)[8])
    }

    // ── 03: motivation ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a first Check-In starts with nothing selected and an empty Continue toasts`() = runTest(dispatcher) {
        walkTo(CheckInStep.Motivation)
        assertNull(state.motivation)
        analytics.clear()
        var moved = false
        model.motivationContinue { moved = true }
        assertFalse(moved)
        assertEquals(CheckInCopy.MOTIVATION_TOAST, state.toast)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `Continue fires step_answered then motivation_selected at class 1, and saves the answer`() = runTest(dispatcher) {
        walkTo(CheckInStep.Motivation)
        analytics.clear()
        model.motivationTapped("casual")
        model.motivationTapped("long_term")
        model.motivationTapped("long_term")
        model.motivationContinue {}
        advanceUntilIdle()
        assertEquals(listOf("check_in_step_answered", "motivation_selected"), analytics.names())
        assertEquals("picked", props("check_in_step_answered")["answer_value"])
        val selected = props("motivation_selected")
        assertEquals("long_term", selected["motivation_id"])
        assertEquals(1, selected["sensitivity_class"])
        assertEquals("long_term" to null, store.answers.last())
    }

    @Test
    fun `a returning user lands on the last accepted answer and Continue reports prefilled`() = runTest(dispatcher) {
        store.held = store.held.copy(lastMotivation = "casual", lastVibes = setOf("calm", "playful"), firstCheckIn = false)
        walkTo(CheckInStep.Motivation)
        assertEquals("casual", state.motivation)
        model.motivationContinue {}
        assertEquals("prefilled", analytics.of("check_in_step_answered").last().properties["answer_value"])
        assertEquals("casual", analytics.of("motivation_selected").single().properties["motivation_id"])
    }

    @Test
    fun `a close on Motivation saves nothing it did not accept`() = runTest(dispatcher) {
        walkTo(CheckInStep.Motivation)
        model.motivationTapped("friends")
        model.closePressed(CheckInStep.Motivation) {}
        advanceUntilIdle()
        assertTrue(store.answers.isEmpty())
        assertEquals("motivation", analytics.of("check_in_closed").single().properties["step_id"])
    }

    // ── 04: vibe ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sixth chip selects nothing and raises the max toast, with no event`() = runTest(dispatcher) {
        walkTo(CheckInStep.Vibe)
        listOf("coffee_chat", "sunset", "calm", "playful", "no_rush").forEach(model::vibeTapped)
        analytics.clear()
        model.vibeTapped("cozy")
        assertEquals(5, state.vibes.size)
        assertFalse("cozy" in state.vibes)
        assertEquals(CheckInCopy.VIBE_TOAST_MAX, state.toast)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `Continue reports the whole set in list order, once`() = runTest(dispatcher) {
        walkTo(CheckInStep.Vibe)
        analytics.clear()
        model.vibeTapped("no_rush"); model.vibeTapped("playful"); model.vibeTapped("coffee_chat")
        model.vibeContinue {}
        assertEquals(listOf("check_in_step_answered", "vibe_selected"), analytics.names())
        val selected = props("vibe_selected")
        assertEquals("coffee_chat,playful,no_rush", selected["vibe_ids"])
        assertEquals(3, selected["vibe_count"])
        assertEquals(0, selected["sensitivity_class"])
    }

    @Test
    fun `an empty Vibe toasts and same values in any tap order are prefilled`() = runTest(dispatcher) {
        store.held = store.held.copy(lastVibes = setOf("calm", "playful"))
        walkTo(CheckInStep.Vibe)
        model.vibeTapped("calm"); model.vibeTapped("playful")
        assertTrue(state.vibes.isEmpty())
        model.vibeContinue {}
        assertEquals(CheckInCopy.VIBE_TOAST_EMPTY, state.toast)
        model.vibeTapped("playful"); model.vibeTapped("calm")
        model.vibeContinue {}
        assertEquals("prefilled", analytics.of("check_in_step_answered").last().properties["answer_value"])
    }

    // ── 05: filters ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `filters pre-fill, report what changed but never the genders, and send 65+ as null`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        assertEquals(setOf("woman"), state.filters!!.genders)
        analytics.clear()
        model.genderTapped("non_binary")
        model.ageMoved(0, 25); model.ageMoved(1, 35)
        model.distanceMoved(15)
        assertTrue("no event per drag or tap", analytics.names().isEmpty())
        model.filtersContinue {}
        advanceUntilIdle()
        assertEquals(listOf("check_in_step_answered", "check_in_filters_changed"), analytics.names())
        val changed = props("check_in_filters_changed")
        assertEquals("meet_gender,age_range,distance_km", changed["filters_changed"])
        assertEquals(25, changed["min_age"]); assertEquals(35, changed["max_age"]); assertEquals(15, changed["distance_km"])
        assertFalse("the genders are Class 2 and never travel", changed.values.any { it.toString().contains("non_binary") })
        assertEquals(CheckInFilters(setOf("woman", "non_binary"), 25, 35, 15), store.filters.last())
    }

    @Test
    fun `unchanged filters still fire, with none and a null max age`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        analytics.clear()
        model.filtersContinue {}
        assertEquals("prefilled", props("check_in_step_answered")["answer_value"])
        assertEquals("none", props("check_in_filters_changed")["filters_changed"])
        assertEquals(JsonNull, props("check_in_filters_changed")["max_age"])
    }

    @Test
    fun `no gender toasts and stays`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        model.genderTapped("woman")
        var moved = false
        model.filtersContinue { moved = true }
        assertFalse(moved)
        assertEquals(CheckInCopy.FILTERS_TOAST, state.toast)
    }

    // ── 05 from Search's top bar (SHOWUP-184) ───────────────────────────────────────────────────

    /** A committed Check-In and its settings read: the user is on Search. */
    private fun TestScope.inSearch() {
        store.held = store.held.copy(active = true, filters = CheckInFilters(setOf("woman"), distanceKm = 20))
        model.refresh()
        advanceUntilIdle()
        analytics.clear()
    }

    private fun TestScope.filtersFromSearch() {
        model.searchFiltersOpened()
        advanceUntilIdle()
    }

    @Test
    fun `Filters from Search opens on the saved settings and is a screen, never a Check-In step`() = runTest(dispatcher) {
        inSearch()
        filtersFromSearch()
        assertEquals(CheckInFilters(setOf("woman"), distanceKm = 20), state.filters)
        assertEquals(listOf("screen_viewed"), analytics.names())
        assertEquals("checkin_filters", props("screen_viewed")["screen_id"])
        assertEquals("search_profile", props("screen_viewed")["referrer_screen_id"])
    }

    @Test
    fun `its Continue saves, and goes back only once the save has landed`() = runTest(dispatcher) {
        inSearch()
        filtersFromSearch()
        analytics.clear()
        model.distanceMoved(5)
        store.filterScript += 500L to true
        var back = false
        model.searchFiltersContinue { back = true }
        advanceTimeBy(100)
        assertFalse("a queue read now would still be under the old settings", back)
        assertTrue(state.saving)
        advanceUntilIdle()
        assertTrue(back)
        assertFalse(state.saving)
        assertEquals(CheckInFilters(setOf("woman"), distanceKm = 5), store.filters.single())
        assertEquals(5, state.server!!.filters.distanceKm)
        assertEquals("the funnel's step event is not fired from Search", listOf("check_in_filters_changed"), analytics.names())
        assertEquals("distance_km", props("check_in_filters_changed")["filters_changed"])
    }

    @Test
    fun `a refused save keeps Filters on screen with its toast, and Continue works again`() = runTest(dispatcher) {
        inSearch()
        filtersFromSearch()
        model.distanceMoved(5)
        store.filterSaves = false
        var back = false
        model.searchFiltersContinue { back = true }
        // The answer, not the toast's own timer: run what is due, nothing later.
        runCurrent()
        assertFalse(back)
        assertEquals(CheckInCopy.FILTERS_SAVE_ERROR_PROPOSED, state.toast)
        assertFalse(state.saving)
        store.filterSaves = true
        model.searchFiltersContinue { back = true }
        advanceUntilIdle()
        assertTrue(back)
    }

    @Test
    fun `back and the X drop the edits and never discard the committed Check-In`() = runTest(dispatcher) {
        inSearch()
        filtersFromSearch()
        model.distanceMoved(5)
        var back = false
        model.searchFiltersLeft { back = true }
        assertTrue(back)
        assertEquals(20, state.filters!!.distanceKm)
        assertTrue("nothing saved", store.filters.isEmpty())
        assertTrue(analytics.names().none { it == "check_in_closed" })
        assertTrue(state.server!!.active)
        // And the screen is left: its controls no longer act.
        model.distanceMoved(9)
        assertEquals(20, state.filters!!.distanceKm)
    }

    @Test
    fun `unchanged settings go back with no save, reported as none`() = runTest(dispatcher) {
        inSearch()
        filtersFromSearch()
        var back = false
        model.searchFiltersContinue { back = true }
        advanceUntilIdle()
        assertTrue(back)
        assertTrue(store.filters.isEmpty())
        assertEquals("none", props("check_in_filters_changed")["filters_changed"])
    }

    @Test
    fun `offline from the start, a stand-in is never saved over the user's settings`() = runTest(dispatcher) {
        store.reads = false
        filtersFromSearch()
        assertEquals(emptySet<String>(), state.filters!!.genders)
        model.genderTapped("man")
        var back = false
        model.searchFiltersContinue { back = true }
        runCurrent()
        assertFalse(back)
        assertTrue(store.filters.isEmpty())
        assertEquals(CheckInCopy.FILTERS_SAVE_ERROR_PROPOSED, state.toast)
        // Back online: the read resolves the stand-in -- the edit over the server's -- then it saves.
        store.reads = true
        model.searchFiltersContinue { back = true }
        advanceUntilIdle()
        assertTrue(back)
        assertEquals(CheckInFilters(setOf("man")), store.filters.single())
    }

    @Test
    fun `an offline start still gives Filters a working stand-in, replaced by a late read if untouched`() = runTest(dispatcher) {
        store.reads = false
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        assertEquals("no gender ticked: Continue toasts, never a dead button", emptySet<String>(), state.filters!!.genders)
        store.reads = true
        store.held = store.held.copy(filters = CheckInFilters(setOf("man"), distanceKm = 20))
        model.refresh(); advanceUntilIdle()
        assertEquals(setOf("man"), state.filters!!.genders)
    }

    @Test
    fun `a late read never overwrites a filter the user has touched`() = runTest(dispatcher) {
        store.reads = false
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        model.genderTapped("other")
        store.reads = true
        model.refresh(); advanceUntilIdle()
        assertEquals(setOf("other"), state.filters!!.genders)
    }

    @Test
    fun `a read that lands on the stand-in keeps what the user set and takes the rest from the server`() = runTest(dispatcher) {
        store.reads = false
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        model.distanceMoved(30)
        store.reads = true
        store.held = store.held.copy(filters = CheckInFilters(setOf("man"), minAge = 25, maxAge = 35, distanceKm = 20))
        model.refresh(); advanceUntilIdle()
        assertEquals(CheckInFilters(setOf("man"), minAge = 25, maxAge = 35, distanceKm = 30), state.filters)
        analytics.clear()
        model.filtersContinue {}
        assertEquals(
            "measured against what the read showed, not the stand-in it replaced",
            "distance_km", props("check_in_filters_changed")["filters_changed"],
        )
    }

    @Test
    fun `filters set on the stand-in never replace saved settings the user did not touch`() = runTest(dispatcher) {
        store.reads = false
        store.held = store.held.copy(filters = CheckInFilters(setOf("man"), minAge = 25, maxAge = 35, distanceKm = 20))
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        model.genderTapped("woman")
        model.filtersContinue {}
        advanceUntilIdle()
        assertTrue("nothing built on the stand-in is sent before the server's settings are known", store.filters.isEmpty())
        arrive(CheckInStep.Uplift, ProfileScreen.CheckinFilters)
        store.reads = true
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals(
            "the gender the user ticked, the ages and distance they never saw",
            listOf(CheckInFilters(setOf("woman"), minAge = 25, maxAge = 35, distanceKm = 20)), store.filters,
        )
        assertEquals(1, store.commits.size)
    }

    // ── 06: uplift and the commit ───────────────────────────────────────────────────────────────

    @Test
    fun `the field is empty on every entry, cut at 100 graphemes, and back keeps it`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        assertEquals("", state.uplift)
        model.upliftChanged("😀".repeat(130))
        assertEquals(100, graphemeCount(state.uplift))
        model.upliftChanged("boat")
        model.backPressed(CheckInStep.Uplift) {}
        arrive(CheckInStep.Filters, ProfileScreen.CheckinUplift)
        model.filtersContinue {}
        arrive(CheckInStep.Uplift, ProfileScreen.CheckinFilters)
        assertEquals("boat", state.uplift)
    }

    @Test
    fun `contact info blocks the commit, toasts and sends nothing`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        analytics.clear()
        model.upliftChanged("Text me on 0176 2345 6789")
        assertTrue(state.upliftBlocked)
        model.findMyDate {}
        assertEquals(CheckInCopy.CONTACT_ERROR, state.toast)
        advanceUntilIdle()
        assertTrue(store.commits.isEmpty())
        assertTrue("no request, no event", analytics.names().isEmpty())
        model.upliftChanged("Text me")
        assertFalse(state.upliftBlocked)
    }

    @Test
    fun `an empty field commits with no prompt, and success fires step_answered then navigates`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        analytics.clear()
        ms += 9_000
        val order = mutableListOf<String>()
        model.findMyDate { order += "navigate" }
        advanceUntilIdle()
        order.addAll(0, analytics.names())
        assertEquals(listOf("check_in_step_answered", "navigate"), order)
        val answered = props("check_in_step_answered")
        assertEquals("uplift", answered["step_id"])
        assertEquals("empty", answered["answer_value"])
        assertEquals(9_000L, answered["time_to_answer_ms"])
        assertTrue("check_in_created is the server's", analytics.of("check_in_created").isEmpty())
        assertTrue(analytics.of("check_in_completed").isEmpty())
        assertNull("empty is stored as no uplift, never \"\"", store.commits.single().uplift)
        assertFalse(model.flowAlive)
    }

    @Test
    fun `the commit carries both days' slots as instants, the answers and the trimmed text`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        model.upliftChanged("  will take you out on my boat  ")
        model.findMyDate {}
        advanceUntilIdle()
        val request = store.commits.single()
        assertEquals(WINDOW, request.windowId)
        assertEquals("will take you out on my boat", request.uplift)
        assertEquals("long_term", request.motivation)
        assertEquals(setOf("calm"), request.vibes)
        assertEquals(
            listOf(
                CommitSlot(Instant.parse("2026-10-13T18:00:00Z"), SlotTier.Preferred),
                CommitSlot(Instant.parse("2026-10-14T12:00:00Z"), SlotTier.Preferred),
            ),
            request.slots,
        )
        assertEquals("written", analytics.of("check_in_step_answered").last().properties["answer_value"])
    }

    // ── the place Search measures from (SHOWUP-184) ─────────────────────────────────────────────

    @Test
    fun `the commit carries the phone's place, read once per flow, again in a new one`() = runTest(dispatcher) {
        var reads = 0
        val here = CheckInPlace(52.4811, 13.4352, "Neukölln, Berlin")
        model = CheckInViewModel(
            store, analytics, clock = { now }, zone = { ZoneOffset.UTC }, millis = { ms },
            places = { reads++; here },
        )
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.Network)
        model.findMyDate {}
        advanceUntilIdle()
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals(listOf(here, here), store.commits.map { it.place })
        assertEquals("a retry reuses the fix rather than waiting on another", 1, reads)
        model.beginFlow()
        advanceUntilIdle()
        walkTo(CheckInStep.Uplift)
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals("a new flow reads again", 2, reads)
    }

    @Test
    fun `no location, no place -- the commit still goes`() = runTest(dispatcher) {
        model = CheckInViewModel(store, analytics, clock = { now }, zone = { ZoneOffset.UTC }, millis = { ms }, places = { null })
        walkTo(CheckInStep.Uplift)
        var landed = false
        model.findMyDate { landed = true }
        advanceUntilIdle()
        assertTrue(landed)
        assertNull(store.commits.single().place)
    }

    @Test
    fun `while saving the button, back and close are inert and a double tap sends one request`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitDelayMs = 1_000
        var landed = 0
        model.findMyDate { landed++ }
        model.findMyDate { landed++ }
        advanceTimeBy(100)
        assertTrue(state.saving)
        var back = false; var closed = false
        model.backPressed(CheckInStep.Uplift) { back = true }
        model.closePressed(CheckInStep.Uplift) { closed = true }
        model.upliftChanged("changed while saving")
        assertFalse(back); assertFalse(closed)
        assertEquals("", state.uplift)
        advanceUntilIdle()
        assertEquals(1, store.commits.size)
        assertEquals(1, landed)
        assertTrue(analytics.of("check_in_closed").isEmpty())
    }

    @Test
    fun `a failed save keeps everything, reports once per request, and the retry counts up`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.addAll(listOf(CommitResult.Network, CommitResult.Server))
        model.upliftChanged("boat")
        model.findMyDate {}
        advanceUntilIdle()
        assertTrue(state.saveFailed)
        assertEquals("boat", state.uplift)
        assertEquals(2, state.picks.size)
        model.findMyDate {}
        advanceUntilIdle()
        var landed = false
        model.findMyDate { landed = true }
        advanceUntilIdle()
        assertTrue(landed)
        val failures = analytics.of("check_in_commit_failed").map { it.properties }
        assertEquals(listOf("network", "server"), failures.map { it["reason"] })
        assertEquals(listOf(1, 2), failures.map { it["attempt"] })
        assertEquals("step_answered fires on the save, once", 1, analytics.of("check_in_step_answered").count { it.properties["step_id"] == "uplift" })
    }

    @Test
    fun `the save-failed card clears on the next tap or an edit`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.Network)
        model.findMyDate {}
        advanceUntilIdle()
        assertTrue(state.saveFailed)
        model.upliftChanged("x")
        assertFalse(state.saveFailed)
    }

    @Test
    fun `a server contact-info refusal shows state C, not the save-failed card`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.ContactInfo)
        model.upliftChanged("ping me on insta")
        model.findMyDate {}
        advanceUntilIdle()
        assertTrue(state.upliftBlocked)
        assertFalse(state.saveFailed)
        assertEquals("contact_info", props("check_in_commit_failed")["reason"])
        model.findMyDate {}
        assertEquals("a dimmed tap toasts and sends nothing", 1, store.commits.size)
        assertEquals(CheckInCopy.CONTACT_ERROR, state.toast)
        model.upliftChanged("ping me")
        assertFalse(state.upliftBlocked)
    }

    @Test
    fun `filters that did not save are saved before the commit`() = runTest(dispatcher) {
        store.filterSaves = false
        walkTo(CheckInStep.Uplift)
        store.filterSaves = true
        val before = store.filters.size
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals(before + 1, store.filters.size)
        assertEquals(1, store.commits.size)
    }

    @Test
    fun `a slow filter save cannot land after a later one, and the commit sends the last settings`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        store.filterScript.addAll(listOf(1_000L to true, 0L to false))       // A: slow, lands. B: fast, fails.
        store.filterFailure = CallFailure.Server                              // so the commit sends B again
        model.genderTapped("man")
        model.filtersContinue {}                                              // A
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.backPressed(CheckInStep.Uplift) {}
        model.arrived(CheckInStep.Filters, ProfileScreen.CheckinFilters, ProfileScreen.CheckinUplift)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                              // B, after A
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        advanceTimeBy(500)
        model.findMyDate {}
        advanceUntilIdle()
        val a = CheckInFilters(setOf("woman", "man"))
        val b = CheckInFilters(setOf("woman", "man", "non_binary"))
        assertEquals("A, then B after it, then B again: A landing never stood in for B", listOf(a, b, b), store.filters)
        assertEquals(b, store.held.filters)
        assertEquals(1, store.commits.size)
    }

    @Test
    fun `a filter save that failed does not become the settings the next flow starts from`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        store.filterSaves = false
        model.genderTapped("man")
        model.filtersContinue {}
        advanceUntilIdle()
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.closePressed(CheckInStep.Uplift) {}
        assertEquals(setOf("woman"), state.server!!.filters.genders)
        begin()
        assertEquals(setOf("woman"), state.filters!!.genders)
    }

    @Test
    fun `a filter save the server refuses is reported as server, not as offline`() = runTest(dispatcher) {
        store.filterSaves = false
        store.filterFailure = CallFailure.Server
        walkTo(CheckInStep.Uplift)
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals("server", props("check_in_commit_failed")["reason"])
        assertTrue(state.saveFailed)
        assertTrue("nothing is committed over settings that did not save", store.commits.isEmpty())
    }

    @Test
    fun `a read the commit needs that fails is reported as what it was`() = runTest(dispatcher) {
        store.reads = false
        store.readFailure = CallFailure.Server
        walkTo(CheckInStep.Uplift)
        model.findMyDate {}
        advanceUntilIdle()
        store.readFailure = CallFailure.Network
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals(listOf("server", "network"), analytics.of("check_in_commit_failed").map { it.properties["reason"] })
        assertTrue(store.commits.isEmpty())
    }

    @Test
    fun `a flow left open past every picked hour asks for hours again and sends nothing`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        now = LocalDateTime.of(2026, 10, 14, 13, 30)                        // both picks have started
        model.findMyDate {}
        advanceTimeBy(500)                                                    // inside the toast's 2.6 s
        assertEquals(CheckInCopy.TOMORROW_TOAST, state.toast)
        assertFalse("not a failed save", state.saveFailed)
        assertFalse(state.saving)
        assertTrue("nothing is sent", store.commits.isEmpty())
        assertTrue("not a commit failure either", analytics.of("check_in_commit_failed").isEmpty())
    }

    @Test
    fun `a server that finds every hour started asks for hours again, not for a connection`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.NoHours)
        model.findMyDate {}
        advanceTimeBy(500)                                                    // inside the toast's 2.6 s
        assertEquals(CheckInCopy.TOMORROW_TOAST, state.toast)
        assertFalse(state.saveFailed)
        assertTrue(analytics.of("check_in_commit_failed").isEmpty())
    }

    @Test
    fun `a filter save still in flight when the flow is reopened is what the new flow starts from`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        store.filterScript.add(1_000L to true)
        model.genderTapped("man")
        model.filtersContinue {}
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.closePressed(CheckInStep.Uplift) {}
        model.beginFlow()                                                     // before the save answers
        val chosen = CheckInFilters(setOf("woman", "man"))
        assertEquals(chosen, state.filters)
        advanceUntilIdle()
        arrive(CheckInStep.Filters)
        model.filtersContinue {}
        advanceUntilIdle()
        assertEquals("Continue never sends the older settings back", listOf(chosen, chosen), store.filters)
        assertEquals(chosen, store.held.filters)
    }

    @Test
    fun `filters accepted on the stand-in are saved once the server's arrive, even if the flow is then closed`() = runTest(dispatcher) {
        store.reads = false
        store.held = store.held.copy(filters = CheckInFilters(setOf("man"), minAge = 25, maxAge = 35, distanceKm = 20))
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        model.genderTapped("woman")
        model.filtersContinue {}
        store.reads = true
        arrive(CheckInStep.Uplift, ProfileScreen.CheckinFilters)              // its read resolves the stand-in
        val resolved = CheckInFilters(setOf("woman"), minAge = 25, maxAge = 35, distanceKm = 20)
        assertEquals(listOf(resolved), store.filters)
        model.closePressed(CheckInStep.Uplift) {}
        assertEquals(resolved, store.held.filters)
    }

    @Test
    fun `a commit does not resend filters the save it waited for just failed to send`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        store.filterScript.add(500L to false)                                 // no network, after a wait
        model.filtersContinue {}
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals("one wait, not two", 1, store.filters.size)
        assertEquals("network", props("check_in_commit_failed")["reason"])
        model.findMyDate {}
        advanceUntilIdle()
        assertEquals("the next tap tries again", 2, store.filters.size)
        assertEquals(1, store.commits.size)
    }

    @Test
    fun `the stand-in save sends what Continue accepted, not an edit made after going back`() = runTest(dispatcher) {
        store.reads = false
        store.held = store.held.copy(filters = CheckInFilters(setOf("man"), minAge = 25, maxAge = 35, distanceKm = 20))
        walkTo(CheckInStep.Vibe)
        model.vibeContinue {}
        arrive(CheckInStep.Filters)
        model.genderTapped("woman")
        model.filtersContinue {}                                              // accepted: a gender
        arrive(CheckInStep.Uplift, ProfileScreen.CheckinFilters)
        model.backPressed(CheckInStep.Uplift) {}
        arrive(CheckInStep.Filters, ProfileScreen.CheckinUplift)
        model.ageMoved(0, 40)                                                 // not confirmed by any Continue
        store.reads = true
        model.refresh(); advanceUntilIdle()
        assertEquals(
            "the accepted settings, resolved against the server's",
            listOf(CheckInFilters(setOf("woman"), minAge = 25, maxAge = 35, distanceKm = 20)), store.filters,
        )
        assertEquals("the step still shows the edit in progress", 40, state.filters!!.minAge)
    }

    @Test
    fun `after a commit with no answer, a retry past every hour goes to the server, which may replay it`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.Network)                         // saved? nobody knows
        model.findMyDate {}
        advanceUntilIdle()
        now = LocalDateTime.of(2026, 10, 14, 13, 30)                        // every pick has started since
        var landed = false
        model.findMyDate { landed = true }
        advanceUntilIdle()
        assertEquals("sent, not stopped on the device", 2, store.commits.size)
        assertTrue(store.commits.last().slots.isEmpty())
        assertTrue("the server's replay lands it", landed)
    }

    @Test
    fun `only the newest filter save's answer clears what a reopened flow starts from`() = runTest(dispatcher) {
        walkTo(CheckInStep.Filters)
        store.filterScript.addAll(listOf(1_000L to false, 1_000L to true, 1_000L to true))
        val x = CheckInFilters(setOf("woman", "man"))
        model.genderTapped("man")
        model.filtersContinue {}                                              // X
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.backPressed(CheckInStep.Uplift) {}
        model.arrived(CheckInStep.Filters, ProfileScreen.CheckinFilters, ProfileScreen.CheckinUplift)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                              // Y
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        model.backPressed(CheckInStep.Uplift) {}
        model.arrived(CheckInStep.Filters, ProfileScreen.CheckinFilters, ProfileScreen.CheckinUplift)
        model.genderTapped("non_binary")
        model.filtersContinue {}                                              // X again
        model.arrived(CheckInStep.Uplift, ProfileScreen.CheckinUplift, ProfileScreen.CheckinFilters)
        advanceTimeBy(1_100)                                                  // the first X has answered, failed
        model.closePressed(CheckInStep.Uplift) {}
        model.beginFlow()
        assertEquals("Y and the second X are still out: the flow starts from the newest", x, state.filters)
    }

    @Test
    fun `the uplift close discards the text and fires closed uplift`() = runTest(dispatcher) {
        walkTo(CheckInStep.Uplift)
        model.upliftChanged("boat")
        model.closePressed(CheckInStep.Uplift) {}
        assertEquals("", state.uplift)
        assertEquals("uplift", props("check_in_closed")["step_id"])
    }

    // ── the whole flow ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a full walk fires only registered names, and no event carries the uplift or a gender`() = runTest(dispatcher) {
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, null)
        model.gateTapped(CheckInAnalytics.GateTarget.Cta) {}
        walkTo(CheckInStep.Uplift)
        model.upliftChanged("will take you out on my boat")
        model.findMyDate {}
        advanceUntilIdle()
        val allowed = setOf(
            "screen_viewed", "check_in_started", "check_in_closed", "check_in_gate_tapped", "check_in_step_answered",
            "availability_slots_set", "check_in_no_hours_today_resolved", "motivation_selected", "vibe_selected",
            "check_in_filters_changed", "check_in_commit_failed",
        )
        assertTrue(analytics.names().toString(), analytics.names().all { it in allowed })
        analytics.all().forEach { e ->
            e.properties.values.forEach { v ->
                assertFalse("${e.event} carries the uplift", v.toString().contains("boat"))
                assertFalse("${e.event} carries a gender", v.toString() == "woman")
            }
        }
        assertEquals(
            listOf("today", "tomorrow", "motivation", "vibe", "filters", "uplift"),
            analytics.of("check_in_step_answered").map { it.properties["step_id"] },
        )
    }

    @Test
    fun `every payload names exactly the registry's fields`() = runTest(dispatcher) {
        model.arrived(null, ProfileScreen.CheckinSearchUnavailable, null)
        model.gateTapped(CheckInAnalytics.GateTarget.Cta) {}
        begin()
        model.arrived(CheckInStep.Intro, ProfileScreen.CheckinIntro, null)
        model.introStarted {}
        arrive(CheckInStep.Today)
        model.availabilityContinue(CheckInStep.Today) {}
        model.noHoursConfirmed {}
        walkTo(CheckInStep.Uplift)
        store.commitAnswers.add(CommitResult.Network)
        model.findMyDate {}
        advanceUntilIdle()
        model.closePressed(CheckInStep.Uplift) {}

        val registry = Json.parseToJsonElement(File("../../../../design_handoff_showup/tracking/events.json").readText())
            .jsonObject["families"]!!.jsonArray
            .flatMap { it.jsonObject["events"]!!.jsonArray }
            .associate { e ->
                e.jsonObject["name"]!!.jsonPrimitive.content to
                    e.jsonObject["payload"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
            }
        val stamp = setOf("sensitivity_class", "field_registry_version")
        val seen = analytics.all().filter { it.event != "screen_viewed" }.groupBy { it.event }
        assertEquals(
            setOf(
                "check_in_gate_tapped", "check_in_started", "check_in_no_hours_today_resolved", "check_in_step_answered",
                "availability_slots_set", "motivation_selected", "vibe_selected", "check_in_filters_changed",
                "check_in_commit_failed", "check_in_closed",
            ),
            seen.keys,
        )
        seen.forEach { (name, entries) ->
            val expected = registry[name] ?: error("$name is not in events.json")
            entries.forEach { assertEquals(name, expected, it.properties.keys - stamp) }
        }
    }

    private companion object {
        const val WINDOW = "8a1f2c44-0b6e-4f0a-9d2b-3c7e5a1b9f00"
    }
}
