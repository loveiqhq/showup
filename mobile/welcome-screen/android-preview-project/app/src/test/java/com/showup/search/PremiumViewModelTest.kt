/*
 * PremiumViewModelTest.kt
 * ShowUp · the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186), driven with no device
 *
 * Every row of both tickets' tracking tables with its payload, and the events that must NOT fire;
 * and the behaviour the acceptance criteria name: the plan picking the layout when the tab opens, a
 * card opening the full profile, every action returning to its list, the fly-out's states, the
 * decline's 6 s Undo (sent once, at the end, or when the user leaves), the mutual confirm that waits
 * for the server, the sorry sheet and the card closed in its slot, the availability re-check, and a
 * like flow kept across a process death.
 */
package com.showup.search

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.RecordingAnalytics
import com.showup.api.CallFailure
import com.showup.profile.ProfileScreen
import java.time.Instant
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeSearchStore
    private lateinit var events: RecordingAnalytics

    private val leonie = "sample-leonie"
    private val sofia = "sample-sofia"
    private val anna = "sample-anna"
    private val clara = "sample-clara"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeSearchStore().apply { premium = true }
        events = RecordingAnalytics()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(saved: SavedStateHandle = SavedStateHandle()) = SearchViewModel(
        store, events, null,
        millis = { testScheduler.currentTime },
        retryDelaysMs = listOf(10, 20, 40),
        saved = saved,
    )

    /** A Premium Search visit, the grid loaded. */
    private fun TestScope.onGrid(saved: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        val vm = vm(saved)
        vm.visit("Europe/Berlin", listOf("distance_km"), ProfileScreen.CheckinUplift)
        advanceUntilIdle()
        return vm
    }

    /** A Likes tab visit, the list loaded. */
    private fun TestScope.onLikes(saved: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        val vm = vm(saved)
        vm.likesArrived("Europe/Berlin", ProfileScreen.SearchGrid)
        advanceUntilIdle()
        return vm
    }

    private fun props(name: String, nth: Int = 0) = events.of(name)[nth].properties

    private fun SearchViewModel.card(from: PremiumSurface, id: String) = state.value.list(from).card(id)!!

    /** Opens a card past the coach, if the coach comes. */
    private fun SearchViewModel.open(from: PremiumSurface, id: String) {
        openCard(from, id)
        if (state.value.coach) dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
    }

    // ── the plan picks the layout (Search 02 "Who sees it") ─────────────────────────────────────

    @Test
    fun `a Premium visit is the grid -- screen_viewed search_grid, then search_viewed with layout grid`() = runTest(dispatcher) {
        val vm = onGrid()
        assertEquals(SearchAnalytics.Layout.Grid, vm.state.value.layout)
        assertEquals(listOf("screen_viewed", SearchAnalytics.SEARCH_VIEWED), events.names())
        assertEquals("search_grid", props("screen_viewed")["screen_id"])
        assertEquals("Search - Grid", props("screen_viewed")["screen_name"])
        assertEquals("checkin_uplift", props("screen_viewed")["referrer_screen_id"])
        assertEquals("grid", props(SearchAnalytics.SEARCH_VIEWED)["layout"])
        assertEquals(6, props(SearchAnalytics.SEARCH_VIEWED)["results_count"])
        assertEquals(listOf("distance_km"), props(SearchAnalytics.SEARCH_VIEWED)["filters_active"])
        assertEquals(0, store.queueReads)
        assertEquals(SearchSamples.GRID.map { it.profile.userId }, vm.state.value.grid.cards.map { it.userId })
        assertFalse("no coach over the grid: it waits for an opened profile", vm.state.value.coach)
        assertTrue(events.of(SearchAnalytics.LIKES_RECEIVED_VIEWED).isEmpty())
    }

    @Test
    fun `a Freemium visit is the queue, unchanged`() = runTest(dispatcher) {
        store.premium = false
        val vm = onGrid()
        assertEquals(SearchAnalytics.Layout.OneUp, vm.state.value.layout)
        assertEquals("search_profile", props("screen_viewed")["screen_id"])
        assertEquals("one_up", props(SearchAnalytics.SEARCH_VIEWED)["layout"])
        assertEquals(0, store.gridReads)
    }

    @Test
    fun `the plan changing mid-visit takes effect on the next visit, never under the user`() = runTest(dispatcher) {
        val vm = onGrid()
        store.premium = false
        vm.open(PremiumSurface.Grid, sofia)
        vm.backFromProfile()
        advanceUntilIdle()
        assertEquals(SearchAnalytics.Layout.Grid, vm.state.value.layout)
        vm.visit("Europe/Berlin", emptyList(), null)
        advanceUntilIdle()
        assertEquals(SearchAnalytics.Layout.OneUp, vm.state.value.layout)
    }

    @Test
    fun `a grid that cannot be read shows the failed state, and Retry reads it again`() = runTest(dispatcher) {
        store.gridFailure = CallFailure.Network
        val vm = onGrid()
        assertEquals(CallFailure.Network, vm.state.value.grid.failure)
        store.gridFailure = null
        vm.retry("Europe/Berlin")
        advanceUntilIdle()
        assertNull(vm.state.value.grid.failure)
        assertEquals(6, vm.state.value.grid.cards.size)
        assertEquals(2, store.gridReads)
    }

    // ── a card opens the full profile ───────────────────────────────────────────────────────────

    @Test
    fun `opening a card is screen_viewed search_profile with the grid as referrer, and nothing else`() = runTest(dispatcher) {
        val vm = onGrid()
        events.clear()
        vm.openCard(PremiumSurface.Grid, leonie)
        assertEquals(listOf("screen_viewed"), events.names())
        assertEquals("search_profile", props("screen_viewed")["screen_id"])
        assertEquals("search_grid", props("screen_viewed")["referrer_screen_id"])
        assertEquals(leonie, vm.state.value.current?.userId)
        assertTrue(vm.state.value.likedYou)
    }

    @Test
    fun `the coach waits for the first opened profile with Like -- never a Liked you one`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.openCard(PremiumSurface.Grid, leonie)
        assertFalse("Leonie liked the viewer: her third button is Confirm date", vm.state.value.coach)
        vm.backFromProfile()
        advanceUntilIdle()
        vm.openCard(PremiumSurface.Grid, sofia)
        assertTrue(vm.state.value.coach)
        vm.dismissCoach(SearchAnalytics.CoachDismiss.Close, dontShowAgain = false)
        vm.backFromProfile()
        advanceUntilIdle()
        vm.openCard(PremiumSurface.Grid, "sample-maya")
        assertFalse("once per Search visit", vm.state.value.coach)
        assertEquals(1, events.of(SearchAnalytics.SEARCH_COACH_SHOWN).size)
    }

    @Test
    fun `Back returns to the grid unchanged -- screen_viewed search_grid, profile_card_viewed, and the re-check`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        advanceTimeBy(3_000)
        events.clear()
        vm.backFromProfile()
        advanceUntilIdle()
        assertEquals(listOf(SearchAnalytics.PROFILE_CARD_VIEWED, "screen_viewed"), events.names())
        assertEquals(2, props(SearchAnalytics.PROFILE_CARD_VIEWED)["position"])
        assertEquals("search_grid", props("screen_viewed")["screen_id"])
        assertEquals("search_profile", props("screen_viewed")["referrer_screen_id"])
        assertNull(vm.state.value.opened)
        assertEquals(SearchSamples.gridCards(), vm.state.value.grid.cards)
        assertEquals(listOf(SearchSamples.GRID.map { it.profile.userId }), store.availabilityAsks)
        assertTrue("the queue did not reload", events.of(SearchAnalytics.SEARCH_VIEWED).isEmpty())
    }

    // ── the actions, from a profile opened from the grid ────────────────────────────────────────

    @Test
    fun `Not now marks the card and fires pass_sent at once, surface profile`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        vm.pass(SearchAnalytics.PassType.NotNow)
        advanceUntilIdle()
        val pass = props(SearchAnalytics.PASS_SENT)
        assertEquals(2, pass["position"])
        assertEquals("not_now", pass["pass_type"])
        assertEquals("profile", pass["surface"])
        assertTrue(vm.card(PremiumSurface.Grid, sofia).notNow)
        assertTrue("a Not now card still opens", vm.card(PremiumSurface.Grid, sofia).open)
        assertEquals(listOf(sofia to SearchAnalytics.PassType.NotNow), store.passes)
        assertNull(vm.state.value.undo)
    }

    @Test
    fun `Not interested on a profile that did not like you flies out with no undo`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        advanceUntilIdle()
        assertEquals(Leave.NotInterested, vm.card(PremiumSurface.Grid, sofia).leaving)
        assertEquals("not_interested", props(SearchAnalytics.PASS_SENT)["pass_type"])
        assertNull(vm.state.value.undo)
        vm.cardLeft(PremiumSurface.Grid, sofia)
        assertTrue(vm.card(PremiumSurface.Grid, sofia).gone)
        assertFalse(vm.state.value.grid.shown.any { it.userId == sofia })
        assertEquals("the rest keep their order", listOf(leonie, "sample-maya", anna, "sample-emma", "sample-liv"), vm.state.value.grid.shown.map { it.userId })
    }

    @Test
    fun `Like returns after Skip message, the card flying out with its heart filled`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        events.clear()
        vm.like()
        vm.continueToMessage()
        vm.skipMessage()
        advanceUntilIdle()
        assertEquals(
            listOf(SearchAnalytics.LIKE_TAPPED, SearchAnalytics.LIKE_CONFIRM_SHOWN, SearchAnalytics.PROFILE_CARD_VIEWED, "screen_viewed"),
            events.names(),
        )
        assertEquals(2, props(SearchAnalytics.LIKE_TAPPED)["position"])
        assertEquals("profile", props(SearchAnalytics.LIKE_TAPPED)["surface"])
        assertEquals(false, props(SearchAnalytics.LIKE_CONFIRM_SHOWN)["is_mutual"])
        assertEquals(Leave.Liked, vm.card(PremiumSurface.Grid, sofia).leaving)
        assertEquals(listOf(Triple(sofia, null as String?, 2)), store.likes)
    }

    @Test
    fun `cancelling a sheet stays on the profile and sends nothing`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        vm.like()
        vm.cancelConfirm()
        assertEquals(sofia, vm.state.value.opened?.userId)
        vm.like()
        vm.continueToMessage()
        vm.dismissMessage()
        advanceUntilIdle()
        assertEquals(sofia, vm.state.value.opened?.userId)
        assertTrue(store.likes.isEmpty())
        assertEquals(listOf("confirm", "message"), events.of(SearchAnalytics.LIKE_CONFIRM_ABANDONED).map { it.properties["stage"] })
    }

    // ── the mutual confirm ──────────────────────────────────────────────────────────────────────

    @Test
    fun `Confirm date opens mutual Be sure with no message step`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, leonie)
        events.clear()
        vm.like()
        assertEquals(LikeSheet.Confirm, vm.state.value.sheet)
        assertEquals(true, props(SearchAnalytics.LIKE_CONFIRM_SHOWN)["is_mutual"])
        assertEquals("profile", props(SearchAnalytics.LIKE_TAPPED)["surface"])
        vm.continueToMessage()
        assertEquals("never the message sheet", LikeSheet.Confirm, vm.state.value.sheet)
        vm.cancelConfirm()
        assertEquals(true, props(SearchAnalytics.LIKE_CONFIRM_ABANDONED)["is_mutual"])
        assertEquals("confirm", props(SearchAnalytics.LIKE_CONFIRM_ABANDONED)["stage"])
    }

    @Test
    fun `while the confirm is with the server the button is inert and the sheet does not close`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, leonie)
        vm.like()
        vm.confirmDate()
        assertTrue(vm.state.value.confirming)
        vm.confirmDate()
        vm.cancelConfirm()
        assertEquals(LikeSheet.Confirm, vm.state.value.sheet)
        advanceUntilIdle()
        assertEquals("one confirm sent", listOf(leonie to 1), store.confirms)
    }

    @Test
    fun `a won confirm returns to the grid and the card flies out`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, leonie)
        vm.like()
        vm.confirmDate()
        advanceUntilIdle()
        assertNull(vm.state.value.opened)
        assertFalse(vm.state.value.confirming)
        assertEquals(Leave.Liked, vm.card(PremiumSurface.Grid, leonie).leaving)
        assertTrue(events.of(SearchAnalytics.LIKE_CONFIRM_FAILED).isEmpty())
        assertTrue("no message on a confirm", events.of(SearchAnalytics.LIKE_MESSAGE_ATTACHED).isEmpty())
    }

    @Test
    fun `a refused confirm shows the sorry sheet, fires like_confirm_failed once, and closes the card in its slot`() = runTest(dispatcher) {
        store.confirmAnswer = ConfirmAnswer.Refused(CloseReason.Matched)
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, leonie)
        vm.like()
        vm.confirmDate()
        advanceUntilIdle()
        assertEquals(LikeSheet.Refused, vm.state.value.sheet)
        assertEquals(CloseReason.Matched, vm.state.value.refused)
        assertEquals(1, events.of(SearchAnalytics.LIKE_CONFIRM_FAILED).size)
        assertEquals("matched", props(SearchAnalytics.LIKE_CONFIRM_FAILED)["reason"])
        assertEquals("profile", props(SearchAnalytics.LIKE_CONFIRM_FAILED)["surface"])
        events.clear()
        vm.refusedDone()
        advanceUntilIdle()
        assertEquals("the dismissal fires nothing of its own", listOf(SearchAnalytics.PROFILE_CARD_VIEWED, "screen_viewed"), events.names())
        val card = vm.card(PremiumSurface.Grid, leonie)
        assertEquals(CloseReason.Matched, card.closed)
        assertFalse("not a tap target", card.open)
        assertNull("never flies out", card.leaving)
        assertEquals("in the same position", 1, vm.state.value.grid.positionOf(leonie))
    }

    @Test
    fun `a confirm with no answer at all stays on Be sure and reports nothing`() = runTest(dispatcher) {
        store.confirmAnswer = null
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, leonie)
        vm.like()
        vm.confirmDate()
        advanceUntilIdle()
        assertEquals(LikeSheet.Confirm, vm.state.value.sheet)
        assertFalse(vm.state.value.confirming)
        assertTrue(events.of(SearchAnalytics.LIKE_CONFIRM_FAILED).isEmpty())
    }

    // ── the decline's Undo window ───────────────────────────────────────────────────────────────

    @Test
    fun `Not interested on a Liked you profile is a decline sent only when the 6 s window ends`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        events.clear()
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        assertTrue("never on the tap", events.of(SearchAnalytics.PASS_SENT).isEmpty())
        assertEquals(Leave.NotInterested, vm.card(PremiumSurface.Grid, anna).leaving)
        assertEquals(UndoToast(anna, "Anna", PremiumSurface.Grid, 4), vm.state.value.undo)
        advanceTimeBy(5_999)
        assertTrue(store.passes.isEmpty())
        advanceTimeBy(2)
        advanceUntilIdle()
        assertNull(vm.state.value.undo)
        assertEquals(listOf(anna to SearchAnalytics.PassType.NotInterested), store.passes)
        val pass = props(SearchAnalytics.PASS_SENT)
        assertEquals(4, pass["position"])
        assertEquals("not_interested", pass["pass_type"])
        assertEquals("profile", pass["surface"])
    }

    @Test
    fun `Undo puts the card back and nothing reaches the server -- pass_undone and no pass_sent`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        vm.cardLeft(PremiumSurface.Grid, anna)
        assertTrue(vm.card(PremiumSurface.Grid, anna).gone)
        vm.undo()
        advanceTimeBy(10_000)
        advanceUntilIdle()
        val card = vm.card(PremiumSurface.Grid, anna)
        assertFalse(card.gone)
        assertNull(card.leaving)
        assertEquals("back in its old slot", 4, vm.state.value.grid.positionOf(anna))
        assertTrue(store.passes.isEmpty())
        assertTrue(events.of(SearchAnalytics.PASS_SENT).isEmpty())
        assertEquals(4, props(SearchAnalytics.PASS_UNDONE)["position"])
        assertEquals("profile", props(SearchAnalytics.PASS_UNDONE)["surface"])
    }

    @Test
    fun `Undo during the fly-out stops it -- the card stays in its slot`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        vm.undo()
        val card = vm.card(PremiumSurface.Grid, anna)
        assertNull(card.leaving)
        assertFalse(card.gone)
        vm.cardLeft(PremiumSurface.Grid, anna)
        assertFalse("a cancelled flight never finishes", vm.card(PremiumSurface.Grid, anna).gone)
    }

    @Test
    fun `a second decline inside the window sends the first and starts a new toast`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        vm.open(PremiumSurface.Grid, leonie)
        advanceUntilIdle()
        assertEquals("opening another card already sent it", listOf(anna to SearchAnalytics.PassType.NotInterested), store.passes)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        assertEquals(leonie, vm.state.value.undo?.userId)
        assertEquals(1, events.of(SearchAnalytics.PASS_SENT).size)
    }

    @Test
    fun `leaving the grid or the app sends a waiting decline at once, and only once`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        vm.leave()
        advanceUntilIdle()
        assertNull(vm.state.value.undo)
        assertEquals(1, store.passes.size)
        advanceTimeBy(10_000)
        advanceUntilIdle()
        assertEquals(1, store.passes.size)
        assertEquals(1, events.of(SearchAnalytics.PASS_SENT).size)
    }

    @Test
    fun `focus on Undo holds the window, and leaving it lets what was left run on`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        advanceTimeBy(2_000)
        vm.undoFocus(true)
        advanceTimeBy(30_000)
        assertNotNull("held while focused", vm.state.value.undo)
        vm.undoFocus(false)
        advanceTimeBy(3_999)
        assertNotNull(vm.state.value.undo)
        advanceTimeBy(2)
        advanceUntilIdle()
        assertNull(vm.state.value.undo)
        assertEquals(1, store.passes.size)
    }

    @Test
    fun `Not now on a Liked you profile leaves the like open -- no undo, the badge unchanged`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, anna)
        vm.pass(SearchAnalytics.PassType.NotNow)
        advanceUntilIdle()
        assertNull(vm.state.value.undo)
        assertTrue(vm.card(PremiumSurface.Grid, anna).notNow)
        assertEquals("not_now", props(SearchAnalytics.PASS_SENT)["pass_type"])
        assertEquals(2, vm.state.value.likesBadge)
    }

    // ── the availability re-check ───────────────────────────────────────────────────────────────

    @Test
    fun `a re-check closes a card in its slot and shows a moved meet time -- nothing reorders`() = runTest(dispatcher) {
        val vm = onGrid()
        val moved = Instant.parse("2026-10-23T16:00:00Z")
        store.statuses = mapOf(
            sofia to CardStatus.Closed(CloseReason.Matched),
            "sample-emma" to CardStatus.Closed(CloseReason.NoOverlap),
            anna to CardStatus.Available(moved, "tomorrow"),
        )
        vm.open(PremiumSurface.Grid, leonie)
        events.clear()
        vm.backFromProfile()
        advanceUntilIdle()
        val grid = vm.state.value.grid
        assertEquals(SearchSamples.GRID.map { it.profile.userId }, grid.shown.map { it.userId })
        assertEquals(CloseReason.Matched, grid.card(sofia)?.closed)
        assertEquals(CloseReason.NoOverlap, grid.card("sample-emma")?.closed)
        assertEquals(moved, grid.card(anna)?.profile?.meetAt)
        assertEquals("tomorrow", grid.card(anna)?.profile?.meetDay)
        assertEquals("the re-check fires nothing", listOf(SearchAnalytics.PROFILE_CARD_VIEWED, "screen_viewed"), events.names())
    }

    @Test
    fun `a failed re-check leaves the grid as it is`() = runTest(dispatcher) {
        val vm = onGrid()
        store.availabilityFails = true
        vm.open(PremiumSurface.Grid, leonie)
        vm.backFromProfile()
        advanceUntilIdle()
        assertEquals(SearchSamples.gridCards(), vm.state.value.grid.cards)
    }

    @Test
    fun `the re-check never touches a card the user answered`() = runTest(dispatcher) {
        val vm = onGrid()
        vm.open(PremiumSurface.Grid, sofia)
        vm.like()
        vm.continueToMessage()
        vm.skipMessage()
        advanceUntilIdle()
        assertFalse(store.availabilityAsks.last().contains(sofia))
    }

    @Test
    fun `nothing re-checks the grid but a return from a profile`() = runTest(dispatcher) {
        val vm = onGrid()
        advanceTimeBy(600_000)
        vm.leave()
        vm.resume()
        advanceUntilIdle()
        assertTrue(store.availabilityAsks.isEmpty())
    }

    // ── the Likes tab (Search 03) ───────────────────────────────────────────────────────────────

    @Test
    fun `a Likes visit fires screen_viewed likes_received, then likes_received_viewed with what it holds`() = runTest(dispatcher) {
        val vm = onLikes()
        assertEquals(listOf("screen_viewed", SearchAnalytics.LIKES_RECEIVED_VIEWED), events.names())
        assertEquals("likes_received", props("screen_viewed")["screen_id"])
        assertEquals("Search - Likes received", props("screen_viewed")["screen_name"])
        assertEquals("search_grid", props("screen_viewed")["referrer_screen_id"])
        assertEquals(2, props(SearchAnalytics.LIKES_RECEIVED_VIEWED)["open_count"])
        assertEquals(1, props(SearchAnalytics.LIKES_RECEIVED_VIEWED)["closed_count"])
        assertEquals("rendered in the server's order", listOf(anna, leonie, clara), vm.state.value.likes.cards.map { it.userId })
        assertEquals(2, vm.state.value.likesBadge)
        assertFalse("a closed card cannot be opened", vm.card(PremiumSurface.Likes, clara).open)
        assertTrue(events.of(SearchAnalytics.SEARCH_VIEWED).isEmpty())
    }

    @Test
    fun `an empty list fires likes_received_viewed with zero and zero`() = runTest(dispatcher) {
        store.likesList = emptyList()
        val vm = onLikes()
        assertTrue(vm.state.value.likes.empty)
        assertEquals(0, props(SearchAnalytics.LIKES_RECEIVED_VIEWED)["open_count"])
        assertEquals(0, props(SearchAnalytics.LIKES_RECEIVED_VIEWED)["closed_count"])
    }

    @Test
    fun `a card opens the profile with the list as referrer, and never the coach`() = runTest(dispatcher) {
        val vm = onLikes()
        events.clear()
        vm.openCard(PremiumSurface.Likes, anna)
        assertFalse(vm.state.value.coach)
        assertEquals("likes_received", props("screen_viewed")["referrer_screen_id"])
        vm.openCard(PremiumSurface.Likes, clara)
        assertEquals("a closed card does not open", anna, vm.state.value.opened?.userId)
    }

    @Test
    fun `a decline from the list is sent at the window's end with surface likes_received, and the badge counts the list`() = runTest(dispatcher) {
        val vm = onLikes()
        vm.openCard(PremiumSurface.Likes, anna)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        runCurrent()
        assertEquals("the card is still on the list while it flies", 2, vm.state.value.likesBadge)
        vm.cardLeft(PremiumSurface.Likes, anna)
        assertEquals("the badge equals the open cards on the list", 1, vm.state.value.likesBadge)
        store.openCount = 1
        advanceTimeBy(6_001)
        advanceUntilIdle()
        assertEquals("likes_received", props(SearchAnalytics.PASS_SENT)["surface"])
        assertEquals(1, props(SearchAnalytics.PASS_SENT)["position"])
        assertEquals(1, vm.state.value.likesBadge)
        assertEquals(1, vm.state.value.likesOpen)
    }

    @Test
    fun `Undo on the list is pass_undone with surface likes_received`() = runTest(dispatcher) {
        val vm = onLikes()
        vm.openCard(PremiumSurface.Likes, leonie)
        vm.pass(SearchAnalytics.PassType.NotInterested)
        vm.undo()
        assertEquals("likes_received", props(SearchAnalytics.PASS_UNDONE)["surface"])
        assertEquals(2, props(SearchAnalytics.PASS_UNDONE)["position"])
    }

    @Test
    fun `Not now on the list labels the card, sends not_now at once with surface profile, and keeps the like open`() = runTest(dispatcher) {
        val vm = onLikes()
        vm.openCard(PremiumSurface.Likes, leonie)
        vm.pass(SearchAnalytics.PassType.NotNow)
        advanceUntilIdle()
        assertTrue(vm.card(PremiumSurface.Likes, leonie).notNow)
        assertEquals("profile", props(SearchAnalytics.PASS_SENT)["surface"])
        assertEquals(2, vm.state.value.likesBadge)
    }

    @Test
    fun `a won confirm from the list takes the card off it`() = runTest(dispatcher) {
        val vm = onLikes()
        vm.openCard(PremiumSurface.Likes, anna)
        vm.like()
        vm.confirmDate()
        advanceUntilIdle()
        assertTrue(vm.card(PremiumSurface.Likes, anna).gone)
        assertEquals(1, store.countReads)
    }

    @Test
    fun `a refused confirm on the list closes the card in place with its reason`() = runTest(dispatcher) {
        store.confirmAnswer = ConfirmAnswer.Refused(CloseReason.NoOverlap)
        val vm = onLikes()
        vm.openCard(PremiumSurface.Likes, leonie)
        vm.like()
        vm.confirmDate()
        advanceUntilIdle()
        vm.refusedDone()
        advanceUntilIdle()
        assertEquals(CloseReason.NoOverlap, vm.card(PremiumSurface.Likes, leonie).closed)
        assertEquals("it moves to the end only on the next load", 2, vm.state.value.likes.positionOf(leonie))
    }

    @Test
    fun `the list's re-check closes open cards and reads the badge again`() = runTest(dispatcher) {
        val vm = onLikes()
        store.statuses = mapOf(leonie to CardStatus.Closed(CloseReason.Matched))
        store.openCount = 1
        vm.openCard(PremiumSurface.Likes, anna)
        vm.backFromProfile()
        advanceUntilIdle()
        assertEquals(listOf(listOf(anna, leonie)), store.availabilityAsks)
        assertEquals(CloseReason.Matched, vm.card(PremiumSurface.Likes, leonie).closed)
        assertEquals(1, vm.state.value.likesBadge)
    }

    @Test
    fun `coming back to the tab keeps the scroll position unless the list read back different`() = runTest(dispatcher) {
        val vm = onLikes()
        val first = vm.state.value.likes.generation
        vm.likesArrived("Europe/Berlin", ProfileScreen.SearchGrid)
        advanceUntilIdle()
        assertEquals(first, vm.state.value.likes.generation)
        store.likesList = SearchSamples.LIKES.take(2)
        vm.likesArrived("Europe/Berlin", ProfileScreen.SearchGrid)
        advanceUntilIdle()
        assertEquals(first + 1, vm.state.value.likes.generation)
    }

    // ── process death ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Be sure left open on a grid profile comes back over that profile after a process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = onGrid(saved)
        before.open(PremiumSurface.Grid, leonie)
        before.like()
        val after = onGrid(saved)
        assertEquals(Opened(PremiumSurface.Grid, leonie), after.state.value.opened)
        assertEquals(LikeSheet.Confirm, after.state.value.sheet)
        assertTrue(after.state.value.likedYou)
    }

    @Test
    fun `a kept like flow is dropped when its person is no longer on the list`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = onGrid(saved)
        before.open(PremiumSurface.Grid, sofia)
        before.like()
        store.grid = SearchSamples.GRID.filterNot { it.profile.userId == sofia }
        val after = onGrid(saved)
        assertNull(after.state.value.opened)
        assertNull(after.state.value.sheet)
    }

    @Test
    fun `the sorry sheet is never kept, the next read shows the card closed`() = runTest(dispatcher) {
        store.confirmAnswer = ConfirmAnswer.Refused(CloseReason.Matched)
        val saved = SavedStateHandle()
        val before = onGrid(saved)
        before.open(PremiumSurface.Grid, leonie)
        before.like()
        before.confirmDate()
        advanceUntilIdle()
        assertEquals(LikeSheet.Refused, before.state.value.sheet)
        val after = onGrid(saved)
        assertNull(after.state.value.opened)
    }
}
