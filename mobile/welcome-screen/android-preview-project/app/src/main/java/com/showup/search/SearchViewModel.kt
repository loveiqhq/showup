/*
 * SearchViewModel.kt
 * ShowUp · Date search: the Search tab (SHOWUP-184 queue, 185 Premium grid) and the Likes tab (186)
 *
 * ONE OWNER FOR DATE SEARCH: the queue, the grid, the likes list, the profile on screen -- wherever it
 * was opened from -- the like flow's sheets, the confirm and its sorry sheet, the actions coach, the
 * photo viewer, playback, the decline's Undo window and every event of family I. The screens take
 * [SearchUiState] and return taps; nothing on them decides anything. One owner because the one
 * profile screen serves all three ("the profile is extended, not forked"), and its playback, coach,
 * sheets and `profile_card_viewed` are the same machinery wherever it opened from.
 *
 * A VISIT is one `screen_viewed` of a tab: a Search visit ([visit]) reads the queue or the grid, a
 * Likes visit ([likesArrived]) reads the list. A return from a profile is not a visit: nothing is
 * read again but the cards' availability ([recheck]).
 *
 * THE PLAN PICKS THE LAYOUT WHEN THE TAB OPENS (Search 02): the session is read first, and Premium
 * gets the grid, Freemium the queue -- for the whole visit. A plan that changes mid-visit takes
 * effect on the next one; the screen never switches under the user.
 *
 * SENDS RUN IN THE BACKGROUND. A like or a pass moves on at once ("No toast, no confirmation
 * screen") and the request follows, retried silently: both are idempotent on the server. A CONFIRM
 * is the exception: the sheet waits for the server's answer, because the answer decides what the user
 * sees next -- a match, or the sorry sheet.
 *
 * WHAT SURVIVES A PROCESS DEATH. Neither list needs to: the server reads them again. A like flow left
 * open does -- which sheet, for whom, from where, and the message as typed -- in [SavedStateHandle],
 * as the profile drafts are kept. It comes back once, when its person is there again.
 */
package com.showup.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.designsystem.Motion
import com.showup.profile.MediaKind
import com.showup.profile.MediaPlayerFactory
import com.showup.profile.MediaPlayerSession
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Which of the like flow's sheets is open. [Refused] is the sorry sheet, after a refused confirm. */
enum class LikeSheet { Confirm, Message, Refused }

/** What is playing on the profile: which card, how far, for its readout. */
data class SearchPlayback(val kind: MediaKind, val positionMs: Int, val durationMs: Int) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** The viewer's own answers the overlap is measured against -- their active Check-In and profile. */
data class SearchViewer(val vibes: Set<String> = emptySet(), val interests: Set<String> = emptySet())

/** The two Premium lists a profile opens from. */
enum class PremiumSurface { Grid, Likes }

/**
 * One card of the grid or the likes list, as it stands this visit. Cards are never reordered and
 * never removed from the list -- a card that flew out is [gone], so Undo can put it back in its slot
 * and `position` stays the card's place in the order the visit loaded.
 */
data class PremiumCard(
    val userId: String,
    val name: String,
    val age: Int?,
    /** The first photo's URL; empty draws the placeholder. */
    val photo: String,
    /** What opening the card shows. Null for a closed like, which cannot be opened. */
    val profile: SearchProfile?,
    val likedYou: Boolean,
    /** The liker's message (Likes list only). */
    val message: String? = null,
    /** `Not now` this visit: dimmed and labelled on the grid, labelled only on the list. */
    val notNow: Boolean = false,
    /** Closed in place: a refused confirm, or the re-check. Not a tap target. */
    val closed: CloseReason? = null,
    /** A final answer: the 150 ms hold, then the fly-out. */
    val leaving: Leave? = null,
    /** Flown out. Still in the list, for Undo and for `position`. */
    val gone: Boolean = false,
) {
    /** A card the user can still open and act on. */
    val open: Boolean get() = closed == null && !gone && profile != null
}

/** The grid or the likes list for this visit. */
data class PremiumList(
    val loading: Boolean = true,
    val failure: CallFailure? = null,
    val cards: List<PremiumCard> = emptyList(),
    /** A new read that changed the list: the screen starts again at its top. */
    val generation: Int = 0,
) {
    /** What is drawn: every card but the ones that flew out. */
    val shown: List<PremiumCard> get() = cards.filterNot { it.gone }

    /** Loaded, nothing failed, and nothing to draw. */
    val empty: Boolean get() = !loading && failure == null && shown.isEmpty()

    fun card(userId: String): PremiumCard? = cards.firstOrNull { it.userId == userId }

    /** 1-based, in the order the visit loaded, gone and closed cards included. */
    fun positionOf(userId: String): Int = cards.indexOfFirst { it.userId == userId } + 1
}

/** The profile on screen, opened from a list. */
data class Opened(val from: PremiumSurface, val userId: String)

/** Where the Likes list was: its first visible item, and how far into it. */
data class LikesScroll(val index: Int = 0, val offset: Int = 0)

/** A declined like's Undo window: whose, from which list, at which position. */
data class UndoToast(val userId: String, val name: String, val from: PremiumSurface, val position: Int)

data class SearchUiState(
    /** The queue is being read: the first load of a visit, before any profile is known. */
    val loading: Boolean = true,
    /** The queue could not be read, and why -- no response, or one that was not a success. */
    val failure: CallFailure? = null,
    /** The profiles still to decide on, the one on screen first. */
    val queue: List<SearchProfile> = emptyList(),
    /** 1-based, in this visit's queue: the profile on screen. */
    val position: Int = 1,
    val sheet: LikeSheet? = null,
    /** The optional message, as typed (already cut to 100). */
    val draft: String = "",
    /** The actions coach is over the profile; the three buttons are inert. */
    val coach: Boolean = false,
    /** The open-likes count -- the likes chip and the Likes tab badge read this one number. */
    val likesOpen: Int = 0,
    val premium: Boolean = false,
    /** The fullscreen photo viewer, open at this photo; null when closed. */
    val viewerAt: Int? = null,
    val playback: SearchPlayback? = null,
    val viewer: SearchViewer = SearchViewer(),
    /** The Search tab's layout for this visit, decided by the plan when the tab opened. */
    val layout: SearchAnalytics.Layout = SearchAnalytics.Layout.OneUp,
    /** The Premium grid (Search 02). */
    val grid: PremiumList = PremiumList(),
    /** The Likes tab's list (Search 03). */
    val likes: PremiumList = PremiumList(),
    /** The profile opened from the grid or the list, while it is on screen. */
    val opened: Opened? = null,
    /** The confirm is with the server: `Confirm date` is inert and the sheet stays open. */
    val confirming: Boolean = false,
    /** Why the confirm was refused: what the sorry sheet ([LikeSheet.Refused]) says. */
    val refused: CloseReason? = null,
    /** A declined like's 6 s Undo, over the list it was declined from. */
    val undo: UndoToast? = null,
) {
    /** The list a profile was opened from. */
    fun list(from: PremiumSurface): PremiumList = if (from == PremiumSurface.Grid) grid else likes

    /** The card the opened profile belongs to. */
    val openedCard: PremiumCard? get() = opened?.let { list(it.from).card(it.userId) }

    /** The profile on screen: the opened card's, or the queue's first. */
    val current: SearchProfile? get() = if (opened != null) openedCard?.profile else queue.firstOrNull()

    /** The profile on screen liked the viewer: Like is `Confirm date`, Be sure is mutual. */
    val likedYou: Boolean get() = openedCard?.likedYou == true

    /** Loaded, nothing failed, and nobody to show: the no-results screen. */
    val empty: Boolean get() = !loading && failure == null && queue.isEmpty()

    /**
     * The Likes badge: the server's open count, less a decline from the Likes list waiting out its
     * Undo -- that card has left the list, and "the badge equals the open cards on the list". A
     * decline from the grid counts until it is sent ("it drops ... when a decline is sent").
     */
    val likesBadge: Int
        get() {
            val pending = undo?.takeIf { u -> u.from == PremiumSurface.Likes && likes.card(u.userId)?.gone == true }
            return (likesOpen - if (pending != null) 1 else 0).coerceAtLeast(0)
        }
}

open class SearchViewModel(
    private val store: SearchStore,
    private val analytics: AnalyticsTracker? = null,
    private val players: MediaPlayerFactory? = null,
    /** Milliseconds, for `dwell_ms`, `visible_ms` and the Undo window. */
    private val millis: () -> Long = System::currentTimeMillis,
    /** Between silent retries of a like or a pass. Short in tests. */
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000, 9_000, 27_000),
    /** Where an open like flow is kept across a process death. */
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** A like flow a process death cut off, waiting for the first list that shows its person. */
    private var unfinished: UnfinishedLike? = UnfinishedLike.from(saved)

    /** The stored opt-out: `Don't show again` once, on any device. */
    private var coachHidden = false
    private var coachShownThisVisit = false
    private var coachShownAt = 0L

    /** Who has been decided on this session, so a refill never brings them back mid-request. */
    private val decided = mutableSetOf<String>()

    /**
     * Who got a FINAL answer this session -- a like, a confirm, a decline, Not interested: never back
     * on the Likes list. Narrower than [decided]: a `Not now` leaves a received like open, on the
     * list ("it stays open in the Likes tab until it closes"). Both cover the moment a read returns
     * before the answer it raced has landed on the server.
     */
    private val answered = mutableSetOf<String>()

    /**
     * How long a declined like can be undone: [Motion.LIKES_UNDO], or longer when the phone's
     * accessibility settings ask for more time to act (`setUndoWindow`).
     */
    private var undoWindowMs = Motion.LIKES_UNDO.toLong()

    /**
     * Where the Likes list was when its tab was left, for the next visit -- back at the top when the
     * list read back different. Not state: nothing redraws for it.
     */
    var likesScroll = LikesScroll()
        private set

    /** The Likes list moved. */
    fun likesScrolled(at: LikesScroll) {
        likesScroll = at
    }

    /**
     * The visit's reported `filters_active`, kept for a return from Filters that reads nothing again:
     * the settings did not change, so neither did this. Taken from [filtersActiveLater] when the
     * queue or the grid is read.
     */
    private var filtersActive: List<String> = emptyList()

    /**
     * The visit's `filters_active` while it is worked out. Its gender part needs the viewer's own
     * profile (27 Oct 2026), a second request, so it runs BESIDE the queue or grid read and is
     * awaited where `search_viewed` is reported: the first profile waits for the slower of the two,
     * never for both in a row. Sent first, the profile request held Search back by its own round
     * trip, and by its whole timeout on a bad network, before the queue was even asked for.
     */
    private var filtersActiveLater: Deferred<List<String>> = CompletableDeferred(emptyList())
    private var resultsCount: Int? = null
    private var emptyReported = false

    /** The phone's zone, for the server's `today` / `tomorrow` on a re-check. */
    private var zone: String = "UTC"

    /** A visit has been made, so a return from Filters is a return, not a first arrival. */
    private var visited = false

    /** Filters saved new settings: the return reads the queue again under them. */
    private var reloadOnReturn = false

    // ── per profile, for `profile_card_viewed` ──────────────────────────────────────────────────
    private var shownAt = 0L
    private var photosSwiped = 0
    private val sectionsSeen = mutableSetOf<Int>()

    private var loadJob: Job? = null
    private var likesJob: Job? = null
    private var recheckJob: Job? = null
    private var confirmJob: Job? = null
    private var session: MediaPlayerSession? = null
    private var ticker: Job? = null
    /** Every stop moves it on, so a play still opening when it moves knows it was superseded. */
    private var playGeneration = 0

    // ── the Undo window ─────────────────────────────────────────────────────────────────────────
    private var undoJob: Job? = null
    /** What is left of the window, so focus on Undo can hold it and let it go. */
    private var undoLeftMs = 0L
    private var undoStartedAt = 0L

    // ── the visit ───────────────────────────────────────────────────────────────────────────────

    /**
     * Search is on screen. Returning from Filters starts a new visit with position 1, but keeps the
     * coach suppressed and reloads the queue or grid only when settings changed (27 Oct 2026).
     *
     * @param filtersActive works out `filters_active`; run only when the queue or grid is read again.
     */
    fun arrived(zone: String, filtersActive: suspend () -> List<String>, referrer: ProfileScreen?) {
        onScreen += PremiumSurface.Grid
        if (!visited || referrer != ProfileScreen.CheckinFilters) {
            visit(zone, filtersActive, referrer)
            return
        }
        this.zone = zone
        report(ProfileAnalytics.screenViewed(searchScreen(), referrer))
        coachShownThisVisit = true
        emptyReported = false
        _state.update { it.copy(position = 1, coach = false) }
        if (!reloadOnReturn) {
            // The visit's own filters_active: nothing changed on Filters, so nothing is worked out again.
            resultsCount?.let { report(SearchAnalytics.searchViewed(it, this.filtersActive, _state.value.layout)) }
            reportEmptyIfNeeded()
            resume()
            return
        }
        reloadOnReturn = false
        workOutFiltersActive(filtersActive)
        resultsCount = null
        stopPlayback()
        if (_state.value.layout == SearchAnalytics.Layout.Grid) {
            _state.update { it.copy(grid = it.grid.copy(loading = true, failure = null), opened = null, sheet = null, viewerAt = null) }
            loadGrid(zone)
        } else {
            _state.update { it.copy(loading = true, failure = null, queue = emptyList(), position = 1, sheet = null, viewerAt = null) }
            load(zone)
        }
        keepLikeFlow()
    }

    /** [arrived] with `filters_active` already known. */
    fun arrived(zone: String, filtersActive: List<String>, referrer: ProfileScreen?) =
        arrived(zone, { filtersActive }, referrer)

    /** Filters saved new settings; the return to Search reads the queue or the grid under them. */
    fun filtersSaved() {
        reloadOnReturn = true
    }

    /**
     * A visit to the Search tab: the session first, because the plan picks the layout; then
     * `screen_viewed` for that layout, once, and the queue or the grid read.
     *
     * @param zone the phone's IANA zone, for the server's `today` / `tomorrow`.
     * @param filtersActive works out the §10 filters set away from the defaults, for `search_viewed`,
     *   while the queue or the grid is read.
     */
    fun visit(zone: String, filtersActive: suspend () -> List<String>, referrer: ProfileScreen?) {
        onScreen += PremiumSurface.Grid
        visited = true
        reloadOnReturn = false
        this.zone = zone
        workOutFiltersActive(filtersActive)
        coachShownThisVisit = referrer == ProfileScreen.CheckinFilters
        emptyReported = false
        resultsCount = null
        stopPlayback()
        flushDecline()
        _state.update {
            it.copy(
                loading = true, failure = null, queue = emptyList(), position = 1, sheet = null, coach = false,
                viewerAt = null, opened = null, refused = null, confirming = false,
                grid = PremiumList(generation = it.grid.generation + 1),
                // The plan last read (at the last foreground) draws the loading frame; the session read
                // below confirms it before anything but the spinner is shown.
                layout = if (it.premium) SearchAnalytics.Layout.Grid else SearchAnalytics.Layout.OneUp,
            )
        }
        // The sheet the reset closed is forgotten too. One a process death left is still held in
        // [unfinished], and is kept again when its person comes back.
        keepLikeFlow()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val countCall = async { store.likesOpenCount() }
            (store.session() as? Outcome.Ok)?.value?.let { s ->
                // ONCE HIDDEN HERE, HIDDEN: a session read that left before `Don't show again` landed
                // on the server still says false, and must not bring the coach back on this device.
                coachHidden = coachHidden || s.coachHidden
                _state.update { it.copy(premium = s.premium) }
            }
            // A failed session read keeps the plan last known: the layout must be decided either way.
            val layout = if (_state.value.premium) SearchAnalytics.Layout.Grid else SearchAnalytics.Layout.OneUp
            _state.update { it.copy(layout = layout) }
            report(ProfileAnalytics.screenViewed(searchScreen(), referrer))
            (countCall.await() as? Outcome.Ok)?.value?.let { n -> _state.update { it.copy(likesOpen = n) } }
            if (layout == SearchAnalytics.Layout.Grid) readGrid(zone) else readQueue(zone)
        }
    }

    /** [visit] with `filters_active` already known. */
    fun visit(zone: String, filtersActive: List<String>, referrer: ProfileScreen?) =
        visit(zone, { filtersActive }, referrer)

    /** Starts working out this visit's `filters_active`; one still running for an older read is dropped. */
    private fun workOutFiltersActive(filtersActive: suspend () -> List<String>) {
        filtersActiveLater.cancel()
        // A statistic that fails is reported without, never in Search's way.
        filtersActiveLater = viewModelScope.async { runCatching { filtersActive() }.getOrDefault(emptyList()) }
    }

    /** `Retry` on the failed state: the same visit, read again. */
    fun retry(zone: String) {
        if (_state.value.layout == SearchAnalytics.Layout.Grid) {
            _state.update { it.copy(grid = it.grid.copy(loading = true, failure = null)) }
            loadGrid(zone)
        } else {
            _state.update { it.copy(loading = true, failure = null) }
            load(zone)
        }
    }

    /** The Search screen of this visit's layout, for `screen_viewed`. */
    private fun searchScreen(): ProfileScreen =
        if (_state.value.layout == SearchAnalytics.Layout.Grid) ProfileScreen.SearchGrid else ProfileScreen.SearchProfile

    private fun reportEmptyIfNeeded() {
        val shown = _state.value
        val empty = if (shown.layout == SearchAnalytics.Layout.Grid) shown.grid.empty else shown.empty
        if (!emptyReported && empty && PremiumSurface.Grid in onScreen) {
            emptyReported = true
            report(SearchAnalytics.emptyShown())
        }
    }

    private fun load(zone: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { readQueue(zone) }
    }

    private suspend fun readQueue(zone: String) {
        when (val queue = store.queue(zone)) {
            is Outcome.Failed -> _state.update { it.copy(loading = false, failure = queue.why) }
            is Outcome.Ok -> {
                val profiles = queue.value.profiles.filterNot { it.userId in decided }
                resultsCount = queue.value.resultsCount
                filtersActive = filtersActiveLater.await()
                report(SearchAnalytics.searchViewed(queue.value.resultsCount, filtersActive))
                val reopened = unfinished?.takeIf { it.from == null && it.userId == profiles.firstOrNull()?.userId }
                if (unfinished?.from == null) unfinished = null
                _state.update {
                    it.copy(
                        loading = false, failure = null, queue = profiles, viewer = queue.value.viewer,
                        sheet = reopened?.sheet, draft = reopened?.draft ?: it.draft,
                    )
                }
                // The user had passed the coach to tap Like; it does not come back over their sheet.
                if (reopened != null) coachShownThisVisit = true
                keepLikeFlow()
                arrive()
            }
        }
    }

    // ── the grid (Search 02) ────────────────────────────────────────────────────────────────────

    private fun loadGrid(zone: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { readGrid(zone) }
    }

    /** The grid, once per visit: only people still available, so no card is closed on arrival. */
    private suspend fun readGrid(zone: String) {
        when (val grid = store.grid(zone)) {
            is Outcome.Failed -> _state.update { it.copy(loading = false, grid = it.grid.copy(loading = false, failure = grid.why)) }
            is Outcome.Ok -> {
                resultsCount = grid.value.resultsCount
                filtersActive = filtersActiveLater.await()
                report(SearchAnalytics.searchViewed(grid.value.resultsCount, filtersActive, SearchAnalytics.Layout.Grid))
                // Nobody already decided this session, as the queue: a `Not now` card is gone on the
                // next visit (the Search 01 pool rules), and an answer still on its way to the server
                // does not bring its card back.
                val cards = grid.value.cards.filterNot { it.profile.userId in decided }.map { c ->
                    PremiumCard(
                        userId = c.profile.userId, name = c.profile.name, age = c.profile.age,
                        photo = c.profile.photos.firstOrNull().orEmpty(), profile = c.profile, likedYou = c.likedYou,
                    )
                }
                _state.update {
                    it.copy(
                        loading = false, viewer = grid.value.viewer,
                        grid = PremiumList(loading = false, cards = cards, generation = it.grid.generation + 1),
                    )
                }
                reopenUnfinished(PremiumSurface.Grid)
                reportEmptyIfNeeded()
            }
        }
    }

    // ── the Likes tab (Search 03) ───────────────────────────────────────────────────────────────

    /**
     * A visit to the Likes tab: `screen_viewed`, then the list read once, in the server's order, and
     * `likes_received_viewed` with what it holds. Switching tabs and coming back keeps the scroll
     * position "unless the list reloaded" -- read again here, it keeps it when the same cards came
     * back in the same order, and starts at the top when they did not.
     */
    fun likesArrived(zone: String, referrer: ProfileScreen?) {
        onScreen += PremiumSurface.Likes
        this.zone = zone
        stopPlayback()
        flushDecline()
        report(ProfileAnalytics.screenViewed(ProfileScreen.LikesReceived, referrer))
        _state.update { it.copy(likes = it.likes.copy(loading = it.likes.cards.isEmpty(), failure = null), opened = null) }
        readLikes(zone)
    }

    /** `Retry` on the list's failed state. */
    fun likesRetry(zone: String) {
        _state.update { it.copy(likes = it.likes.copy(loading = true, failure = null)) }
        readLikes(zone)
    }

    private fun readLikes(zone: String) {
        likesJob?.cancel()
        likesJob = viewModelScope.launch {
            when (val read = store.likesReceived(zone)) {
                is Outcome.Failed -> _state.update { it.copy(likes = it.likes.copy(loading = false, failure = read.why)) }
                is Outcome.Ok -> {
                    val l = read.value
                    // Nobody given a final answer this session: a decline or a confirm still on its
                    // way to the server leaves no card behind. The counts are the list's, as drawn --
                    // "the badge equals the open cards on the list".
                    val kept = l.cards.filterNot { it.userId in answered }
                    val openCount = kept.count { it.closed == null }
                    report(SearchAnalytics.likesReceivedViewed(openCount, kept.size - openCount))
                    val cards = kept.map { c ->
                        PremiumCard(
                            userId = c.userId, name = c.name, age = c.age, photo = c.photo, profile = c.profile,
                            likedYou = true, message = c.message, closed = c.closed,
                        )
                    }
                    val same = _state.value.likes.cards.map { c -> c.userId to c.closed } == cards.map { c -> c.userId to c.closed }
                    if (!same) likesScroll = LikesScroll()
                    _state.update {
                        it.copy(
                            likesOpen = openCount,
                            viewer = l.viewer,
                            likes = PremiumList(
                                loading = false, cards = cards,
                                generation = if (same) it.likes.generation else it.likes.generation + 1,
                            ),
                        )
                    }
                    reopenUnfinished(PremiumSurface.Likes)
                }
            }
        }
    }

    /** A like flow a process death cut off, reopened once its person is on the list again. */
    private fun reopenUnfinished(from: PremiumSurface) {
        val u = unfinished?.takeIf { it.from == from } ?: return
        unfinished = null
        val card = _state.value.list(from).card(u.userId)?.takeIf { it.open }
        if (card == null) {
            keepLikeFlow()
            return
        }
        // The user had passed the coach to tap Like; it does not come back over their sheet.
        coachShownThisVisit = true
        _state.update {
            it.copy(
                opened = Opened(from, card.userId),
                sheet = u.sheet.takeIf { s -> s != LikeSheet.Message || !card.likedYou },
                draft = u.draft,
            )
        }
        keepLikeFlow()
        shownAt = millis()
    }

    // ── opening a card, and coming back ─────────────────────────────────────────────────────────

    /**
     * A card's photo, heart or `View profile`: the full profile. Leaving the list sends a decline
     * waiting out its Undo ("when the user leaves the grid (another card ...)"), and a card still
     * flying out finishes at once -- it is not on screen to finish its flight.
     */
    fun openCard(from: PremiumSurface, userId: String) {
        val s = _state.value
        val card = s.list(from).card(userId)?.takeIf { it.open && it.leaving == null } ?: return
        if (s.opened != null) return
        flushDecline()
        stopPlayback()
        updateList(from) { list -> list.copy(cards = list.cards.map { c -> if (c.leaving != null) c.copy(leaving = null, gone = true) else c }) }
        _state.update { it.copy(opened = Opened(from, card.userId), sheet = null, viewerAt = null, draft = "", refused = null) }
        report(ProfileAnalytics.screenViewed(ProfileScreen.SearchProfile, listScreen(from)))
        photosSwiped = 0
        sectionsSeen.clear()
        // The coach: over the first profile opened from the grid each Search visit, never on a
        // `Liked you` one (its third button is Confirm date) -- it waits for the next with Like.
        if (from == PremiumSurface.Grid && shouldShowCoach(coachHidden, coachShownThisVisit, true, card.likedYou)) {
            coachShownThisVisit = true
            coachShownAt = millis()
            _state.update { it.copy(coach = true) }
            report(SearchAnalytics.coachShown())
        } else {
            shownAt = millis()
        }
    }

    /** A Search visit has been made in this view model's life -- a process death starts again. */
    val hasVisited: Boolean get() = visited

    /**
     * The list screens arrived at and not left, this view model's life: [PremiumSurface.Grid] for
     * the Search tab (grid or queue), [PremiumSurface.Likes] for the Likes tab. A screen coming back
     * while it is here -- under a popped profile, which fired its own `screen_viewed`, or in an
     * activity a system change recreated -- is not an arrival. Leaving it ([leftScreen]), or leaving
     * a profile opened from it for somewhere else, makes the next showing one. Here, not in the
     * host's saved state: a marker saved there outlived the screen and swallowed a real return from
     * Filters (the second 185/186 audit, 26 Oct 2026).
     */
    private val onScreen = mutableSetOf<PremiumSurface>()

    /** The host's question when a list screen shows: is it still here from before? */
    fun isOnScreen(surface: PremiumSurface): Boolean = surface in onScreen

    /** The list screen slid away: the next time it shows is an arrival. */
    fun leftScreen(surface: PremiumSurface) {
        onScreen -= surface
    }

    /**
     * The opened profile is left for somewhere else -- another tab, Filters, the likes chip -- not for
     * its list: its view reported, playback stopped, the profile closed, and nothing fired for the
     * list it is not returning to. The next screen fires its own `screen_viewed`.
     */
    fun closeProfileForElsewhere() {
        val opened = _state.value.opened ?: return
        // The list under it is not coming back: whenever it shows again, that is an arrival.
        onScreen -= opened.from
        stopPlayback()
        if (!_state.value.coach) reportCardViewed()
        _state.update { it.copy(opened = null, sheet = null, viewerAt = null, coach = false, refused = null, confirming = false) }
        keepLikeFlow()
    }

    /**
     * The app came to the front: the plan and the open-likes count read again. The plan decides
     * where the Likes tab goes ("read at app open"), on every tab bar, not only after a Search visit.
     */
    fun foregrounded() {
        viewModelScope.launch {
            (store.session() as? Outcome.Ok)?.value?.let { s ->
                coachHidden = coachHidden || s.coachHidden
                _state.update { it.copy(premium = s.premium) }
            }
            refreshLikesOpenNow()
        }
    }

    /** `All profiles` / `All likes`, or Android back with nothing over the profile: the card unchanged. */
    fun backFromProfile() {
        val s = _state.value
        if (s.opened == null || s.coach || s.sheet != null || s.viewerAt != null) return
        returnToList { it }
    }

    /**
     * The profile closes and its list is on screen again: the profile's view reported, the card
     * changed by what was decided ([change]), `screen_viewed` for the list (referrer
     * `search_profile`), and every card's availability read again.
     */
    private fun returnToList(change: (PremiumCard) -> PremiumCard) {
        val opened = _state.value.opened ?: return
        stopPlayback()
        reportCardViewed()
        updateList(opened.from) { list -> list.copy(cards = list.cards.map { c -> if (c.userId == opened.userId) change(c) else c }) }
        _state.update { it.copy(opened = null, sheet = null, viewerAt = null, coach = false, refused = null, confirming = false) }
        keepLikeFlow()
        report(ProfileAnalytics.screenViewed(listScreen(opened.from), ProfileScreen.SearchProfile))
        recheck(opened.from)
    }

    /**
     * The availability re-check (Search 02 19): every card still standing, in one call. `matched` /
     * `no_overlap` closes a card in its slot, liked or not; a new meet time is shown. Nothing
     * reorders, appears or disappears; a failed call leaves the list as it is, and nothing retries
     * until the next return. It fires nothing: a card closing is state, not an act.
     */
    private fun recheck(from: PremiumSurface) {
        val ids = _state.value.list(from).cards.filter { it.open && it.leaving == null }.map { it.userId }
        if (ids.isEmpty()) return
        recheckJob?.cancel()
        recheckJob = viewModelScope.launch {
            val statuses = (store.availability(ids, zone) as? Outcome.Ok)?.value ?: return@launch
            var closedAny = false
            updateList(from) { list ->
                list.copy(
                    cards = list.cards.map { c ->
                        // A card that flew out or is flying meanwhile is the user's answer: untouched.
                        if (!c.open || c.leaving != null) return@map c
                        when (val s = statuses[c.userId]) {
                            is CardStatus.Closed -> c.copy(closed = s.reason).also { closedAny = true }
                            is CardStatus.Available -> c.copy(profile = c.profile?.copy(meetAt = s.meetAt, meetDay = s.meetDay))
                            null -> c
                        }
                    },
                )
            }
            // A closed liked-you card closed its like on the server: the badge drops with it.
            if (closedAny) refreshLikesOpen()
        }
    }

    /** A card's fly-out finished: it leaves the list, and the rest close the gap. */
    fun cardLeft(from: PremiumSurface, userId: String) {
        updateList(from) { list ->
            list.copy(cards = list.cards.map { c -> if (c.userId == userId && c.leaving != null) c.copy(leaving = null, gone = true) else c })
        }
    }

    private fun listScreen(from: PremiumSurface): ProfileScreen =
        if (from == PremiumSurface.Grid) ProfileScreen.SearchGrid else ProfileScreen.LikesReceived

    private fun updateList(from: PremiumSurface, change: (PremiumList) -> PremiumList) {
        _state.update { if (from == PremiumSurface.Grid) it.copy(grid = change(it.grid)) else it.copy(likes = change(it.likes)) }
        if (from == PremiumSurface.Grid) reportEmptyIfNeeded()
    }

    /** `position` for the profile on screen: in the queue, or the card's place in its list. */
    private fun positionNow(): Int {
        val s = _state.value
        val o = s.opened ?: return s.position
        return s.list(o.from).positionOf(o.userId)
    }

    /** A profile is on screen: its dwell starts, unless the coach is over it (counted from the dismissal). */
    private fun arrive() {
        reportEmptyIfNeeded()
        val s = _state.value
        photosSwiped = 0
        sectionsSeen.clear()
        if (shouldShowCoach(coachHidden, coachShownThisVisit, s.current != null, likedYou = false)) {
            coachShownThisVisit = true
            coachShownAt = millis()
            _state.update { it.copy(coach = true) }
            report(SearchAnalytics.coachShown())
        } else {
            shownAt = millis()
        }
    }

    /**
     * The profile left the screen without a decision -- a tab change or backgrounding. Its view is
     * reported now, while the dwell is known; [resume] starts a new one if it comes back. A list
     * leaving the screen sends a decline waiting out its Undo.
     */
    fun leave() {
        stopPlayback()
        flushDecline()
        if (!_state.value.coach) reportCardViewed()
    }

    fun resume() {
        if (_state.value.current != null && !_state.value.coach) {
            shownAt = millis()
            photosSwiped = 0
            sectionsSeen.clear()
        }
    }

    // ── the coach ───────────────────────────────────────────────────────────────────────────────

    /**
     * `Got it`, a scrim tap, Android back, or `Don't show again` -- the last the only one stored. The
     * same profile is revealed either way, and its dwell starts now.
     */
    fun dismissCoach(method: SearchAnalytics.CoachDismiss, dontShowAgain: Boolean) {
        if (!_state.value.coach) return
        report(SearchAnalytics.coachDismissed(method, dontShowAgain, millis() - coachShownAt))
        _state.update { it.copy(coach = false) }
        shownAt = millis()
        if (dontShowAgain) {
            coachHidden = true
            sendInBackground { store.hideCoach() }
        }
    }

    // ── the like flow ───────────────────────────────────────────────────────────────────────────

    /**
     * `Like`, or `Confirm date` on a profile that liked the viewer: Be sure opens -- mutual for a
     * confirm. Inert under the coach -- no event either. A confirm is a like: `like_tapped` with
     * `surface: profile`, and the Be sure events with `is_mutual: true`.
     */
    fun like() {
        val s = _state.value
        val profile = s.current ?: return
        if (s.coach || s.sheet != null) return
        stopPlayback()
        report(SearchAnalytics.likeTapped(positionNow()))
        _state.update { it.copy(sheet = LikeSheet.Confirm) }
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmShown(profile.attendedCount, isMutual = s.likedYou))
    }

    /** `Cancel`, a scrim tap or a swipe down on Be sure: nothing is sent, the profile stays. */
    fun cancelConfirm() {
        val s = _state.value
        val profile = s.current ?: return
        // While the confirm is with the server the sheet does not close: "the sheet does not close early".
        if (s.sheet != LikeSheet.Confirm || s.confirming) return
        _state.update { it.copy(sheet = null) }
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(SearchAnalytics.AbandonStage.Confirm, profile.attendedCount, isMutual = s.likedYou))
    }

    /** `Continue`: the message sheet, its draft pre-filled for this person and day. Never on a confirm. */
    fun continueToMessage() {
        val s = _state.value
        val profile = s.current ?: return
        if (s.sheet != LikeSheet.Confirm || s.likedYou) return
        _state.update { it.copy(sheet = LikeSheet.Message, draft = messageDraft(profile.name, profile.meetDay)) }
        keepLikeFlow()
    }

    /** A keystroke or a paste: never more than 100, a paste cut at 100. Nothing is tracked per key. */
    fun editDraft(text: String) {
        _state.update { it.copy(draft = limitMessage(text)) }
        keepLikeFlow()
    }

    /** A scrim tap or a swipe down on the message sheet: nothing is sent. */
    fun dismissMessage() {
        val profile = _state.value.current ?: return
        if (_state.value.sheet != LikeSheet.Message) return
        _state.update { it.copy(sheet = null) }
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(SearchAnalytics.AbandonStage.Message, profile.attendedCount))
    }

    /** `Skip message`: the like, with no message. Not an abandon. */
    fun skipMessage() = sendLike(message = null)

    /** `Send message`: the trimmed text; blank sends as no message, and attaches nothing. */
    fun sendMessage() {
        if (_state.value.sheet != LikeSheet.Message) return
        val message = messageToSend(_state.value.draft)
        if (message != null) report(SearchAnalytics.likeMessageAttached(graphemes(message)))
        sendLike(message)
    }

    /**
     * The like, sent in the background. From the queue the next profile slides in; from the grid the
     * user is back on it and the card flies out -- "a plain Like is never refused", so whatever the
     * server answers, the user sees success.
     */
    private fun sendLike(message: String?) {
        val s = _state.value
        val profile = s.current ?: return
        if (s.sheet != LikeSheet.Message) return
        val position = positionNow()
        _state.update { it.copy(sheet = null) }
        keepLikeFlow()
        decided += profile.userId
        answered += profile.userId
        sendInBackground { store.like(profile.userId, message, position) }
        if (s.opened != null) returnToList { it.copy(leaving = Leave.Liked) } else next()
    }

    /**
     * `Confirm date` in mutual Be sure: sent at once, and the button stays inert until the server
     * answers. WON: the match (07a's from here) -- back on the grid the card flies out, on the list it
     * leaves; REFUSED: Be sure becomes the sorry sheet for the server's reason, and
     * `like_confirm_failed` fires, once. No answer at all (offline): the sheet stays, `Confirm date`
     * live again, and nothing is reported -- nothing was decided.
     */
    fun confirmDate() {
        val s = _state.value
        val opened = s.opened ?: return
        if (s.sheet != LikeSheet.Confirm || !s.likedYou || s.confirming) return
        val position = positionNow()
        _state.update { it.copy(confirming = true) }
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            when (val answer = store.confirm(opened.userId, position)) {
                is Outcome.Failed -> _state.update { it.copy(confirming = false) }
                is Outcome.Ok -> when (val a = answer.value) {
                    ConfirmAnswer.Won -> {
                        decided += opened.userId
                        answered += opened.userId
                        val won: (PremiumCard) -> PremiumCard = { c ->
                            if (opened.from == PremiumSurface.Grid) c.copy(leaving = Leave.Liked) else c.copy(gone = true)
                        }
                        if (_state.value.opened == opened) {
                            returnToList(won)
                        } else {
                            // The profile closed while the server decided: the match stands, and its
                            // card leaves the list all the same.
                            updateList(opened.from) { list -> list.copy(cards = list.cards.map { c -> if (c.userId == opened.userId) won(c).copy(leaving = null, gone = true) else c }) }
                        }
                        refreshLikesOpen()
                    }
                    is ConfirmAnswer.Refused -> {
                        _state.update { it.copy(confirming = false, sheet = LikeSheet.Refused, refused = a.reason) }
                        keepLikeFlow()
                        report(SearchAnalytics.likeConfirmFailed(a.reason))
                        refreshLikesOpen()
                    }
                }
            }
        }
    }

    /**
     * The sorry sheet closed -- its button, the scrim, a swipe down or Android back, all the same: the
     * sheet and the profile close, and the card is closed in its slot with the reason. No event: the
     * next `screen_viewed` of the list measures it.
     */
    fun refusedDone() {
        val s = _state.value
        val reason = s.refused ?: return
        if (s.sheet != LikeSheet.Refused) return
        returnToList { it.copy(closed = reason) }
    }

    // ── passes ──────────────────────────────────────────────────────────────────────────────────

    /**
     * `Not now` / `Not interested`: one tap, no confirmation. Inert under the coach.
     *
     * From the queue the next profile slides in, and there is no undo. From a list the user is back
     * on it: `Not now` labels the card (and leaves a received like OPEN), `Not interested` flies it
     * out -- and on a profile that liked the viewer it is a DECLINE, with a 6 s Undo, sent and
     * reported only when the window ends.
     */
    fun pass(type: SearchAnalytics.PassType) {
        val s = _state.value
        val profile = s.current ?: return
        if (s.coach || s.sheet != null) return
        val position = positionNow()
        val opened = s.opened
        if (opened != null && type == SearchAnalytics.PassType.NotInterested && s.likedYou) {
            val name = profile.name
            returnToList { it.copy(leaving = Leave.NotInterested) }
            startUndo(UndoToast(opened.userId, name, opened.from, position))
            return
        }
        report(SearchAnalytics.passSent(position, type))
        decided += profile.userId
        if (type == SearchAnalytics.PassType.NotInterested) answered += profile.userId
        sendInBackground { store.pass(profile.userId, type) }
        when {
            opened == null -> next()
            type == SearchAnalytics.PassType.NotNow -> returnToList { it.copy(notNow = true) }
            else -> returnToList { it.copy(leaving = Leave.NotInterested) }
        }
    }

    /** The profile that was on screen leaves; the next slides in, from the top. */
    private fun next() {
        stopPlayback()
        reportCardViewed()
        _state.update { it.copy(queue = it.queue.drop(1), position = it.position + 1, viewerAt = null) }
        arrive()
    }

    private fun reportCardViewed() {
        val s = _state.value
        val profile = s.current ?: return
        val sharedVibes = sharedVibes(profile.vibes, s.viewer.vibes).size
        val sharedInterests = sharedInterests(profile.interests, s.viewer.interests).size
        report(
            SearchAnalytics.profileCardViewed(
                position = positionNow(),
                dwellMs = (millis() - shownAt).coerceAtLeast(0),
                photosSwiped = photosSwiped,
                sectionsScrolled = sectionsSeen.size,
                sharedVibeCount = sharedVibes,
                sharedInterestCount = sharedInterests,
            ),
        )
    }

    /** A section of the stack came into view -- `sections_scrolled` counts the distinct ones. */
    fun sectionSeen(index: Int) {
        sectionsSeen += index
    }

    // ── the decline's Undo window ───────────────────────────────────────────────────────────────

    /**
     * A decline waits out its window: "a second decline inside the window sends the first and starts
     * a new toast".
     */
    private fun startUndo(toast: UndoToast) {
        flushDecline()
        _state.update { it.copy(undo = toast) }
        undoLeftMs = undoWindowMs
        runUndoClock()
    }

    /**
     * The Undo window, as the phone's accessibility settings want it: the user's "time to take
     * action", and the whole time the list is on screen while TalkBack is on -- its focus is not a
     * focus this app can see, and "while focus is on it, the 6 s does not run out". Never shorter
     * than the ticket's 6 s. The host sets it, from the platform.
     */
    fun setUndoWindow(ms: Long) {
        undoWindowMs = ms.coerceAtLeast(Motion.LIKES_UNDO.toLong())
    }

    private fun runUndoClock() {
        undoJob?.cancel()
        undoStartedAt = millis()
        val left = undoLeftMs
        undoJob = viewModelScope.launch {
            delay(left)
            flushDecline()
        }
    }

    /**
     * Focus on `Undo` holds the window ("while focus is on it, the 6 s does not run out"); focus
     * leaving it lets what was left run on.
     */
    fun undoFocus(focused: Boolean) {
        if (_state.value.undo == null) return
        if (focused) {
            if (undoJob?.isActive != true) return
            undoJob?.cancel()
            undoJob = null
            undoLeftMs = (undoLeftMs - (millis() - undoStartedAt)).coerceAtLeast(0)
        } else if (undoJob == null) {
            runUndoClock()
        }
    }

    /**
     * `Undo`: the card back in its old slot -- during the fly-out it simply stops, at full opacity --
     * and nothing reaches the server. `pass_undone`, and no `pass_sent`.
     */
    fun undo() {
        val toast = _state.value.undo ?: return
        undoJob?.cancel()
        undoJob = null
        updateList(toast.from) { list ->
            list.copy(cards = list.cards.map { c -> if (c.userId == toast.userId) c.copy(leaving = null, gone = false) else c })
        }
        _state.update { it.copy(undo = null) }
        report(SearchAnalytics.passUndone(toast.position, toast.surface()))
    }

    /**
     * The window is over -- it ran out, the user left the list, the app went to the background, or a
     * second decline came: the decline is sent, once, and `pass_sent` fires with it. The card stays
     * gone; the badge follows the server once the decline lands.
     */
    private fun flushDecline() {
        val toast = _state.value.undo ?: return
        undoJob?.cancel()
        undoJob = null
        _state.update { it.copy(undo = null) }
        decided += toast.userId
        answered += toast.userId
        report(SearchAnalytics.passSent(toast.position, SearchAnalytics.PassType.NotInterested, toast.surface()))
        viewModelScope.launch {
            if (sendWithRetries { store.pass(toast.userId, SearchAnalytics.PassType.NotInterested) }) refreshLikesOpenNow()
        }
    }

    /** §35 for a decline: from the Likes list's toast it is `likes_received`; from the grid, `profile`. */
    private fun UndoToast.surface() =
        if (from == PremiumSurface.Likes) SearchAnalytics.Surface.LikesReceived else SearchAnalytics.Surface.Profile

    // ── the photo viewer ────────────────────────────────────────────────────────────────────────

    /** A photo tapped: the viewer opens at it. [index] is 0-based; the event's is 1-based. */
    fun openPhoto(index: Int) {
        if (_state.value.coach) return
        stopPlayback()
        _state.update { it.copy(viewerAt = index) }
        report(SearchAnalytics.photoOpened(index + 1))
    }

    /** Swiped to another photo in the viewer. */
    fun viewerPage(index: Int) {
        if (_state.value.viewerAt != index) {
            photosSwiped++
            _state.update { it.copy(viewerAt = index) }
        }
    }

    fun closeViewer() {
        _state.update { it.copy(viewerAt = null) }
    }

    // ── playback ────────────────────────────────────────────────────────────────────────────────

    /**
     * Play or pause [kind]. On tap only, never automatically; starting one stops the other ("only one
     * item plays at a time").
     */
    fun togglePlay(kind: MediaKind) {
        val s = _state.value
        val profile = s.current ?: return
        if (s.coach) return
        val playing = s.playback
        stopPlayback()
        if (playing?.kind == kind) return
        val media = (if (kind == MediaKind.Voice) profile.voice else profile.video) ?: return
        val factory = players ?: return
        val generation = playGeneration
        viewModelScope.launch {
            val next = factory.create(kind)
            val started = next.start(media.url)
            // SUPERSEDED while the clip was opening -- a pass, a like, a sheet, the app leaving. Without
            // this, a voice note tapped just before `Not now` played over the next profile.
            if (generation != playGeneration) {
                next.stop()
                return@launch
            }
            if (!started) return@launch
            session = next
            _state.update { it.copy(playback = SearchPlayback(kind, 0, media.durationMs)) }
            // BOUNDED by the clip's own length and a grace, as iOS's clock is: a player that never
            // reports an ending cannot keep it alive.
            ticker = launch {
                var waited = 0L
                while (isActive && waited < media.durationMs + PLAY_CLOCK_GRACE_MS) {
                    delay(TICK_MS)
                    waited += TICK_MS
                    val position = next.positionMs()
                    if (next.hasFinished()) {
                        _state.update { st -> st.playback?.let { st.copy(playback = it.copy(positionMs = it.durationMs)) } ?: st }
                        stopPlayback()
                        return@launch
                    }
                    _state.update { st -> st.playback?.let { st.copy(playback = it.copy(positionMs = position)) } ?: st }
                }
                if (isActive) stopPlayback()
            }
        }
    }

    /** A playing card scrolled out of view: it pauses. */
    fun scrolledOut(kind: MediaKind) {
        if (_state.value.playback?.kind == kind) stopPlayback()
    }

    /**
     * Stops whatever plays and reports the play, once, with how much of it was heard -- "fire when
     * playback stops (end, pause, scrolled out, profile changed)".
     */
    private fun stopPlayback() {
        playGeneration++
        ticker?.cancel()
        ticker = null
        val ending = session
        session = null
        if (ending != null) viewModelScope.launch { ending.stop() }
        val playing = _state.value.playback ?: return
        _state.update { it.copy(playback = null) }
        val prompt = if (playing.kind == MediaKind.Voice) SearchAnalytics.Prompt.Voice else SearchAnalytics.Prompt.Video
        val pct = (playing.progress * 100.0).let { Math.round(it * 10) / 10.0 }
        report(SearchAnalytics.promptPlayed(prompt, positionNow(), pct))
    }

    // ── the likes chip and the badge ────────────────────────────────────────────────────────────

    /** The open-likes count again, e.g. when the app comes back to the front. */
    fun refreshLikesOpen() {
        viewModelScope.launch { refreshLikesOpenNow() }
    }

    private suspend fun refreshLikesOpenNow() {
        (store.likesOpenCount() as? Outcome.Ok)?.value?.let { n -> _state.update { it.copy(likesOpen = n) } }
    }

    // ── the dev menu ────────────────────────────────────────────────────────────────────────────

    fun setPremium(premium: Boolean) {
        viewModelScope.launch {
            if (store.setPremium(premium) is Outcome.Ok) _state.update { it.copy(premium = premium) }
        }
    }

    // ── plumbing ────────────────────────────────────────────────────────────────────────────────

    /**
     * Runs [call] until it lands, waiting longer each time; gives up after the last delay. Silent by
     * design ("If it fails, retry silently"): the user has already moved on.
     */
    private fun sendInBackground(call: suspend () -> Outcome<Unit>) {
        viewModelScope.launch { sendWithRetries(call) }
    }

    /** [sendInBackground]'s loop: true once a call landed. */
    private suspend fun sendWithRetries(call: suspend () -> Outcome<Unit>): Boolean {
        if (call() is Outcome.Ok) return true
        for (wait in retryDelaysMs) {
            delay(wait)
            if (call() is Outcome.Ok) return true
        }
        return false
    }

    /**
     * The like flow as it stands, into [saved]: an open Be sure or message sheet kept, with where its
     * profile was opened from; anything else forgotten. The sorry sheet is not kept -- its answer is
     * the server's, and the next read of the list shows the card closed.
     */
    private fun keepLikeFlow() {
        val s = _state.value
        val profile = s.current
        val sheet = s.sheet?.takeIf { it != LikeSheet.Refused }
        if (profile == null || sheet == null) UnfinishedLike.forget(saved)
        else UnfinishedLike(profile.userId, sheet, if (sheet == LikeSheet.Message) s.draft else "", s.opened?.from).keep(saved)
    }

    private fun report(event: Pair<String, Map<String, Any>>) {
        analytics?.track(event.first, event.second)
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    private companion object {
        /** How often a playing card's readout moves. */
        const val TICK_MS = 100L
        /** How long the clock keeps polling past a clip's stated length: a backstop, not a timing rule. */
        const val PLAY_CLOCK_GRACE_MS = 1_000L
    }
}

/**
 * An open like flow, as [SavedStateHandle] keeps it: plain values, so nothing needs a Parcelable.
 * [from] is the list its profile was opened from, or null for the Freemium queue.
 */
private data class UnfinishedLike(val userId: String, val sheet: LikeSheet, val draft: String, val from: PremiumSurface?) {
    fun keep(saved: SavedStateHandle) {
        saved[KEY_FOR] = userId
        saved[KEY_SHEET] = sheet.name
        saved[KEY_DRAFT] = draft
        saved[KEY_FROM] = from?.name
    }

    companion object {
        private const val KEY_FOR = "search.like.for"
        private const val KEY_SHEET = "search.like.sheet"
        private const val KEY_DRAFT = "search.like.draft"
        private const val KEY_FROM = "search.like.from"

        /** What a process death left, or null -- also for a sheet name this build no longer has. */
        fun from(saved: SavedStateHandle): UnfinishedLike? {
            val userId = saved.get<String>(KEY_FOR) ?: return null
            val sheet = LikeSheet.entries.firstOrNull { it.name == saved.get<String>(KEY_SHEET) && it != LikeSheet.Refused } ?: return null
            val from = saved.get<String>(KEY_FROM)?.let { f -> PremiumSurface.entries.firstOrNull { it.name == f } }
            return UnfinishedLike(userId, sheet, saved.get<String>(KEY_DRAFT).orEmpty(), from)
        }

        fun forget(saved: SavedStateHandle) {
            saved.remove<String>(KEY_FOR)
            saved.remove<String>(KEY_SHEET)
            saved.remove<String>(KEY_DRAFT)
            saved.remove<String>(KEY_FROM)
        }
    }
}
