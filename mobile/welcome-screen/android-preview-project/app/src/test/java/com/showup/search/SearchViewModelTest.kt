/*
 * SearchViewModelTest.kt
 * ShowUp · Date search 01's state machine and its events, driven with no device (SHOWUP-184)
 *
 * Every row of the ticket's tracking table with its payload, the emit order ("like_tapped ->
 * like_confirm_shown -> (like_message_attached) -> like_sent (server) -> profile_card_viewed"), the
 * events that must NOT fire, and the behaviour the acceptance criteria name: the coach's four ways
 * out and the one that is stored, inert buttons under it, dwell from the dismissal, the like flow's
 * sends and cancels, the 100-character cut, silent retries, playback that stops and reports once.
 */
package com.showup.search

import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.profile.FakeMediaPlayer
import com.showup.profile.MediaKind
import com.showup.profile.ProfileScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    /** A server that records every call and can be made to fail. */
    private class FakeStore : SearchStore {
        var profiles = listOf(SearchSamples.FULL) + SearchSamples.NEXT
        var coachHidden = false
        var queueFailure: CallFailure? = null
        /** How many like calls fail before one lands. */
        var likeFailures = 0
        val likes = mutableListOf<Triple<String, String?, Int>>()
        val passes = mutableListOf<Pair<String, SearchAnalytics.PassType>>()
        var coachHides = 0
        var queueReads = 0

        override suspend fun queue(zone: String): Outcome<SearchQueue> {
            queueReads++
            queueFailure?.let { return Outcome.Failed(it) }
            return Outcome.Ok(SearchQueue(profiles.size, profiles, SearchSamples.VIEWER))
        }
        override suspend fun like(targetUserId: String, message: String?, position: Int): Outcome<Unit> {
            likes += Triple(targetUserId, message, position)
            if (likeFailures > 0) { likeFailures--; return Outcome.Failed(CallFailure.Network) }
            return Outcome.Ok(Unit)
        }
        override suspend fun pass(targetUserId: String, type: SearchAnalytics.PassType): Outcome<Unit> {
            passes += targetUserId to type
            return Outcome.Ok(Unit)
        }
        override suspend fun hideCoach(): Outcome<Unit> {
            coachHides++
            if (coachHideLands) coachHidden = true
            return Outcome.Ok(Unit)
        }

        /** False: the server has not stored the flag yet when the next session read arrives. */
        var coachHideLands = true
        override suspend fun likesOpenCount(): Outcome<Int> = Outcome.Ok(2)
        override suspend fun session(): Outcome<SearchSession> = Outcome.Ok(SearchSession(premium = false, coachHidden = coachHidden))
        override suspend fun setPremium(premium: Boolean): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeStore
    private lateinit var events: RecordingAnalytics

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
        events = RecordingAnalytics()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(players: FakeMediaPlayer? = null) = SearchViewModel(
        store, events, players,
        millis = { testScheduler.currentTime },
        retryDelaysMs = listOf(10, 20, 40),
    )

    private fun TestScope.visited(players: FakeMediaPlayer? = null): SearchViewModel {
        val vm = vm(players)
        vm.visit("Europe/Berlin", listOf("distance_km"), ProfileScreen.CheckinUplift)
        advanceUntilIdle()
        return vm
    }

    private fun props(name: String, nth: Int = 0) = events.of(name)[nth].properties

    // ── the visit ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a visit fires screen_viewed once, then search_viewed with the queue's size, then shows the coach`() = runTest(dispatcher) {
        val vm = visited()
        assertEquals(listOf("screen_viewed", SearchAnalytics.SEARCH_VIEWED, SearchAnalytics.SEARCH_COACH_SHOWN), events.names())
        assertEquals("search_profile", props("screen_viewed")["screen_id"])
        assertEquals("Search - Profile", props("screen_viewed")["screen_name"])
        assertEquals("checkin_uplift", props("screen_viewed")["referrer_screen_id"])
        assertEquals("one_up", props(SearchAnalytics.SEARCH_VIEWED)["layout"])
        assertEquals(3, props(SearchAnalytics.SEARCH_VIEWED)["results_count"])
        assertEquals(listOf("distance_km"), props(SearchAnalytics.SEARCH_VIEWED)["filters_active"])
        assertTrue(vm.state.value.coach)
        assertEquals(1, vm.state.value.position)
        assertEquals(0, props(SearchAnalytics.SEARCH_VIEWED)["sensitivity_class"])
    }

    @Test
    fun `the coach never shows once Don't show again was stored, nor over an empty queue`() = runTest(dispatcher) {
        store.coachHidden = true
        assertFalse(visited().state.value.coach)
        assertTrue(events.of(SearchAnalytics.SEARCH_COACH_SHOWN).isEmpty())

        events.clear()
        store.coachHidden = false
        store.profiles = emptyList()
        val empty = visited()
        assertTrue(empty.state.value.empty)
        assertFalse(empty.state.value.coach)
        assertTrue(events.of(SearchAnalytics.SEARCH_COACH_SHOWN).isEmpty())
    }

    @Test
    fun `the coach shows on every visit until it is turned off, never twice in one`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        vm.pass(SearchAnalytics.PassType.NotNow)
        assertFalse("not twice in one visit", vm.state.value.coach)
        vm.visit("Europe/Berlin", emptyList(), null)
        advanceUntilIdle()
        assertTrue("again on the next visit", vm.state.value.coach)
        assertEquals(2, events.of(SearchAnalytics.SEARCH_COACH_SHOWN).size)
    }

    @Test
    fun `the viewer's vibes and interests come with the queue`() = runTest(dispatcher) {
        assertEquals(SearchSamples.VIEWER, visited().state.value.viewer)
    }

    // ── the way back from Filters ───────────────────────────────────────────────────────────────

    @Test
    fun `back from Filters with new settings saved, the queue is read again and the visit goes on`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        vm.leave()
        vm.filtersSaved()
        events.clear()
        vm.arrived("Europe/Berlin", listOf("age_range"), ProfileScreen.CheckinFilters)
        advanceUntilIdle()
        assertEquals(2, store.queueReads)
        assertEquals(listOf("screen_viewed", SearchAnalytics.SEARCH_VIEWED), events.names())
        assertEquals("checkin_filters", props("screen_viewed")["referrer_screen_id"])
        assertEquals(listOf("age_range"), props(SearchAnalytics.SEARCH_VIEWED)["filters_active"])
        assertFalse("no second coach in the same visit", vm.state.value.coach)
        assertEquals(SearchSamples.FULL.userId, vm.state.value.current?.userId)
    }

    @Test
    fun `back from Filters with nothing saved, nothing is read again`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        vm.pass(SearchAnalytics.PassType.NotNow)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.position)
        vm.leave()
        events.clear()
        vm.arrived("Europe/Berlin", listOf("distance_km"), ProfileScreen.CheckinFilters)
        advanceUntilIdle()
        assertEquals(1, store.queueReads)
        assertEquals(listOf("screen_viewed"), events.names())
        assertFalse(vm.state.value.loading)
        assertEquals("Mara is still the one shown", "sample-mara", vm.state.value.current?.userId)
        assertEquals("position starts again with the screen_viewed", 1, vm.state.value.position)
    }

    @Test
    fun `any other arrival is a visit, with its coach`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        vm.arrived("Europe/Berlin", emptyList(), ProfileScreen.CheckinSearchUnavailable)
        advanceUntilIdle()
        assertEquals(2, store.queueReads)
        assertTrue(vm.state.value.coach)
        // And a first arrival FROM Filters -- a process death on Filters -- is a visit too.
        val fresh = vm()
        fresh.arrived("Europe/Berlin", emptyList(), ProfileScreen.CheckinFilters)
        advanceUntilIdle()
        assertEquals(3, store.queueReads)
    }

    // ── the coach's ways out ────────────────────────────────────────────────────────────────────

    @Test
    fun `Got it, the scrim and Android back close it for this visit and store nothing`() = runTest(dispatcher) {
        for (method in listOf(SearchAnalytics.CoachDismiss.Close, SearchAnalytics.CoachDismiss.Backdrop, SearchAnalytics.CoachDismiss.SystemBack)) {
            events.clear()
            val vm = visited()
            advanceTimeBy(2_500)
            vm.dismissCoach(method, dontShowAgain = false)
            advanceUntilIdle()
            val p = props(SearchAnalytics.SEARCH_COACH_DISMISSED)
            assertEquals(method.wire, p["dismiss_method"])
            assertEquals(false, p["dont_show_again"])
            assertEquals(2_500L, p["visible_ms"])
            assertFalse(vm.state.value.coach)
        }
        assertEquals(0, store.coachHides)
    }

    @Test
    fun `Don't show again is the only act stored, once`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = true)
        advanceUntilIdle()
        assertEquals(1, store.coachHides)
        assertEquals(true, props(SearchAnalytics.SEARCH_COACH_DISMISSED)["dont_show_again"])
        assertEquals("close", props(SearchAnalytics.SEARCH_COACH_DISMISSED)["dismiss_method"])
        vm.visit("Europe/Berlin", emptyList(), null)
        advanceUntilIdle()
        assertFalse(vm.state.value.coach)
    }

    @Test
    fun `a session read that missed Don't show again never brings the coach back on this device`() = runTest(dispatcher) {
        store.coachHideLands = false
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = true)
        advanceUntilIdle()
        vm.visit("Europe/Berlin", emptyList(), null)
        advanceUntilIdle()
        assertFalse(vm.state.value.coach)
        assertEquals(1, events.of(SearchAnalytics.SEARCH_COACH_SHOWN).size)
    }

    @Test
    fun `the three buttons are inert under the coach, and fire nothing`() = runTest(dispatcher) {
        val vm = visited()
        events.clear()
        vm.like()
        vm.pass(SearchAnalytics.PassType.NotInterested)
        vm.pass(SearchAnalytics.PassType.NotNow)
        vm.openPhoto(0)
        advanceUntilIdle()
        assertTrue(events.names().isEmpty())
        assertNull(vm.state.value.sheet)
        assertEquals(SearchSamples.FULL.userId, vm.state.value.current?.userId)
        assertTrue(store.passes.isEmpty())
    }

    @Test
    fun `the coached profile's dwell counts from the coach's dismissal`() = runTest(dispatcher) {
        val vm = visited()
        advanceTimeBy(5_000)
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        advanceTimeBy(3_000)
        vm.pass(SearchAnalytics.PassType.NotNow)
        assertEquals(3_000L, props(SearchAnalytics.PROFILE_CARD_VIEWED)["dwell_ms"])
    }

    // ── the like flow ───────────────────────────────────────────────────────────────────────────

    private fun TestScope.uncoached(players: FakeMediaPlayer? = null): SearchViewModel {
        store.coachHidden = true
        val vm = visited(players)
        events.clear()
        return vm
    }

    @Test
    fun `a like with a message fires in the ticket's order and sends the trimmed text`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like()
        assertEquals(LikeSheet.Confirm, vm.state.value.sheet)
        vm.continueToMessage()
        assertEquals("Hi Leonie, I love the vibe of your profile. Would be great to meet you today :)", vm.state.value.draft)
        vm.editDraft("  See you at the lake!  ")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals(
            listOf(
                SearchAnalytics.LIKE_TAPPED, SearchAnalytics.LIKE_CONFIRM_SHOWN,
                SearchAnalytics.LIKE_MESSAGE_ATTACHED, SearchAnalytics.PROFILE_CARD_VIEWED,
            ),
            events.names(),
        )
        assertEquals(mapOf("position" to 1, "surface" to "profile"), props(SearchAnalytics.LIKE_TAPPED).filterKeys { it in setOf("position", "surface") })
        assertEquals(4, props(SearchAnalytics.LIKE_CONFIRM_SHOWN)["target_attended_count"])
        assertEquals(false, props(SearchAnalytics.LIKE_CONFIRM_SHOWN)["is_mutual"])
        assertEquals(20, props(SearchAnalytics.LIKE_MESSAGE_ATTACHED)["char_count"])
        assertEquals(listOf(Triple(SearchSamples.FULL.userId, "See you at the lake!", 1)), store.likes)
        assertEquals("the next profile slides in", "sample-mara", vm.state.value.current?.userId)
        assertEquals(2, vm.state.value.position)
        assertNull(vm.state.value.sheet)
    }

    @Test
    fun `the message text is in no payload, ever`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like(); vm.continueToMessage(); vm.editDraft("my secret words"); vm.sendMessage()
        advanceUntilIdle()
        assertFalse(events.all().any { e -> e.properties.values.any { it.toString().contains("secret") } })
    }

    @Test
    fun `Skip message sends the like with no message, and is not an abandon`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like(); vm.continueToMessage(); vm.skipMessage()
        advanceUntilIdle()
        assertEquals(listOf(Triple(SearchSamples.FULL.userId, null, 1)), store.likes)
        assertTrue(events.of(SearchAnalytics.LIKE_MESSAGE_ATTACHED).isEmpty())
        assertTrue(events.of(SearchAnalytics.LIKE_CONFIRM_ABANDONED).isEmpty())
    }

    @Test
    fun `a blank message sends as none and attaches nothing`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like(); vm.continueToMessage(); vm.editDraft("    "); vm.sendMessage()
        advanceUntilIdle()
        assertEquals(null, store.likes.single().second)
        assertTrue(events.of(SearchAnalytics.LIKE_MESSAGE_ATTACHED).isEmpty())
    }

    @Test
    fun `cancelling Be sure or dismissing the message sends nothing, each its stage`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like(); vm.cancelConfirm()
        assertEquals("confirm", props(SearchAnalytics.LIKE_CONFIRM_ABANDONED)["stage"])
        vm.like(); vm.continueToMessage(); vm.dismissMessage()
        assertEquals("message", props(SearchAnalytics.LIKE_CONFIRM_ABANDONED, 1)["stage"])
        advanceUntilIdle()
        assertTrue(store.likes.isEmpty())
        assertEquals("the profile stays", SearchSamples.FULL.userId, vm.state.value.current?.userId)
    }

    @Test
    fun `the draft never holds more than 100 characters, a paste cut at 100`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.like(); vm.continueToMessage()
        vm.editDraft("x".repeat(250))
        assertEquals(100, graphemes(vm.state.value.draft))
    }

    @Test
    fun `a failed like is retried silently and never made twice by the screen`() = runTest(dispatcher) {
        store.likeFailures = 2
        val vm = uncoached()
        vm.like(); vm.continueToMessage(); vm.skipMessage()
        advanceUntilIdle()
        assertEquals(3, store.likes.size)
        assertTrue("all for the one profile", store.likes.all { it.first == SearchSamples.FULL.userId })
        assertEquals("sample-mara", vm.state.value.current?.userId)
    }

    // ── passes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Not now and Not interested act in one tap, on the tap, and the next profile slides in`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.pass(SearchAnalytics.PassType.NotNow)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        advanceUntilIdle()
        assertEquals(listOf("not_now", "not_interested"), events.of(SearchAnalytics.PASS_SENT).map { it.properties["pass_type"] })
        assertEquals(listOf(1, 2), events.of(SearchAnalytics.PASS_SENT).map { it.properties["position"] })
        assertTrue(events.of(SearchAnalytics.PASS_SENT).all { it.properties["surface"] == "profile" })
        assertEquals(listOf(SearchSamples.FULL.userId, "sample-mara"), store.passes.map { it.first })
        assertEquals("sample-ana", vm.state.value.current?.userId)
    }

    @Test
    fun `profile_card_viewed carries the overlap counts, the photos swiped and the sections seen`() = runTest(dispatcher) {
        val vm = uncoached()
        vm.sectionSeen(0); vm.sectionSeen(1); vm.sectionSeen(1); vm.sectionSeen(4)
        vm.openPhoto(1); vm.viewerPage(2); vm.viewerPage(3); vm.closeViewer()
        advanceTimeBy(1_200)
        vm.pass(SearchAnalytics.PassType.NotNow)
        val p = props(SearchAnalytics.PROFILE_CARD_VIEWED)
        assertEquals(1, p["position"])
        assertEquals(1_200L, p["dwell_ms"])
        assertEquals(2, p["photos_swiped"])
        assertEquals(3, p["sections_scrolled"])
        assertEquals(3, p["shared_vibe_count"])
        assertEquals(4, p["shared_interest_count"])
        assertEquals(2, props(SearchAnalytics.PROFILE_PHOTO_OPENED)["photo_index"])
    }

    @Test
    fun `leaving the tab reports the profile, and coming back starts a new dwell`() = runTest(dispatcher) {
        val vm = uncoached()
        advanceTimeBy(4_000)
        vm.leave()
        assertEquals(4_000L, props(SearchAnalytics.PROFILE_CARD_VIEWED)["dwell_ms"])
        vm.resume()
        advanceTimeBy(500)
        vm.pass(SearchAnalytics.PassType.NotNow)
        assertEquals(500L, props(SearchAnalytics.PROFILE_CARD_VIEWED, 1)["dwell_ms"])
    }

    // ── playback ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a play is reported once, when it stops, and starting one stops the other`() = runTest(dispatcher) {
        val players = FakeMediaPlayer(now = { testScheduler.currentTime }, durationMs = 14_000)
        val vm = uncoached(players)
        vm.togglePlay(MediaKind.Voice)
        runCurrent()
        assertEquals(MediaKind.Voice, vm.state.value.playback?.kind)
        advanceTimeBy(7_000)
        runCurrent()
        vm.togglePlay(MediaKind.Video)
        runCurrent()
        assertEquals(MediaKind.Video, vm.state.value.playback?.kind)
        assertEquals(1, events.of(SearchAnalytics.VOICE_PROMPT_PLAYED).size)
        val pct = events.of(SearchAnalytics.VOICE_PROMPT_PLAYED).single().properties["played_pct"] as Double
        assertTrue("about half heard: $pct", pct in 45.0..55.0)
        vm.scrolledOut(MediaKind.Video)
        runCurrent()
        assertNull(vm.state.value.playback)
        assertEquals(1, events.of(SearchAnalytics.VIDEO_PROMPT_PLAYED).size)
    }

    @Test
    fun `opening Be sure stops playback`() = runTest(dispatcher) {
        val players = FakeMediaPlayer(now = { testScheduler.currentTime }, durationMs = 14_000)
        val vm = uncoached(players)
        vm.togglePlay(MediaKind.Voice)
        runCurrent()
        vm.like()
        runCurrent()
        assertNull(vm.state.value.playback)
        assertEquals(1, events.of(SearchAnalytics.VOICE_PROMPT_PLAYED).size)
    }

    // ── failure, and what must never fire ───────────────────────────────────────────────────────

    @Test
    fun `a queue that cannot be read is the failed state, and Retry reads it again`() = runTest(dispatcher) {
        store.queueFailure = CallFailure.Network
        val vm = visited()
        assertEquals(CallFailure.Network, vm.state.value.failure)
        assertTrue(events.of(SearchAnalytics.SEARCH_VIEWED).isEmpty())
        store.queueFailure = null
        vm.retry("Europe/Berlin")
        advanceUntilIdle()
        assertNull(vm.state.value.failure)
        assertEquals(SearchSamples.FULL.userId, vm.state.value.current?.userId)
        assertEquals(2, store.queueReads)
    }

    @Test
    fun `the server's events and the ones that do not exist here are never fired by the app`() = runTest(dispatcher) {
        val vm = visited()
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, false)
        vm.like(); vm.continueToMessage(); vm.sendMessage()
        vm.pass(SearchAnalytics.PassType.NotNow)
        vm.leave()
        advanceUntilIdle()
        val forbidden = setOf(
            "like_sent", "match_created", "like_withdrawn", "profile_section_expanded", "shared_vibes_shown",
            "overlap_viewed", "search_lockout_shown", "search_lockout_expired", "profile_step_viewed",
            "profile_step_completed", "not_now_tapped", "not_interested_tapped",
        )
        assertTrue(events.names().filter { it in forbidden }.toString(), events.names().none { it in forbidden })
        assertEquals("screen_viewed fires once per visit, not per profile", 1, events.of("screen_viewed").size)
    }
}
