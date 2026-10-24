/*
 * SearchViewModel.kt
 * ShowUp · Date search 01, the Search tab for a user with a committed Check-In (SHOWUP-184)
 *
 * ONE OWNER FOR THE TAB: the queue, the profile on screen, the like flow's two sheets, the actions
 * coach, the photo viewer, playback, and every event of family I. The screen takes [SearchUiState]
 * and returns taps; nothing on it decides anything.
 *
 * A VISIT is one `screen_viewed` of the Search tab ([visit]). The coach, `position` and the queue
 * all reset with it -- the ticket's own definition ("once per Search visit (each screen_viewed of the
 * Search tab)").
 *
 * SENDS RUN IN THE BACKGROUND. A like or a pass moves to the next profile at once ("No toast, no
 * confirmation screen") and the request follows, retried silently: both are idempotent on the
 * server, so a retry after a timeout can never make a second like.
 *
 * WHAT SURVIVES A PROCESS DEATH. The queue does not need to: the server leaves out everyone already
 * liked or passed, so reading it again lands on the same person. A like flow left open does -- which
 * sheet, for whom, and the message as typed -- in [SavedStateHandle], as the profile drafts are kept.
 * It comes back once, when the queue shows the same person first; anyone else, and it is dropped.
 */
package com.showup.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.profile.MediaKind
import com.showup.profile.MediaPlayerFactory
import com.showup.profile.MediaPlayerSession
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Which of the like flow's two sheets is open. */
enum class LikeSheet { Confirm, Message }

/** What is playing on the profile: which card, how far, for its readout. */
data class SearchPlayback(val kind: MediaKind, val positionMs: Int, val durationMs: Int) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** The viewer's own answers the overlap is measured against -- their active Check-In and profile. */
data class SearchViewer(val vibes: Set<String> = emptySet(), val interests: Set<String> = emptySet())

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
) {
    val current: SearchProfile? get() = queue.firstOrNull()

    /** Loaded, nothing failed, and nobody to show: the no-results screen. */
    val empty: Boolean get() = !loading && failure == null && queue.isEmpty()
}

open class SearchViewModel(
    private val store: SearchStore,
    private val analytics: AnalyticsTracker? = null,
    private val players: MediaPlayerFactory? = null,
    /** Milliseconds, for `dwell_ms` and `visible_ms`. */
    private val millis: () -> Long = System::currentTimeMillis,
    /** Between silent retries of a like or a pass. Short in tests. */
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000, 9_000, 27_000),
    /** Where an open like flow is kept across a process death. */
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** A like flow a process death cut off, waiting for the first queue that shows its person. */
    private var unfinished: UnfinishedLike? = UnfinishedLike.from(saved)

    /** The stored opt-out: `Don't show again` once, on any device. */
    private var coachHidden = false
    private var coachShownThisVisit = false
    private var coachShownAt = 0L

    /** Who has been decided on this session, so a refill never brings them back mid-request. */
    private val decided = mutableSetOf<String>()

    /** The visit's reported `filters_active`, kept for a reload after Filters. */
    private var filtersActive: List<String> = emptyList()

    /** A visit has been made, so a return from Filters is a return, not a first arrival. */
    private var visited = false

    /** Filters saved new settings: the return reads the queue again under them. */
    private var reloadOnReturn = false

    // ── per profile, for `profile_card_viewed` ──────────────────────────────────────────────────
    private var shownAt = 0L
    private var photosSwiped = 0
    private val sectionsSeen = mutableSetOf<Int>()

    private var loadJob: Job? = null
    private var session: MediaPlayerSession? = null
    private var ticker: Job? = null
    /** Every stop moves it on, so a play still opening when it moves knows it was superseded. */
    private var playGeneration = 0

    // ── the visit ───────────────────────────────────────────────────────────────────────────────

    /**
     * Search is on screen. A tab, a launch or `Find my date` is a VISIT ([visit]). The way back from
     * Filters is not: no second coach, nobody already decided coming back, and the queue read again
     * only if Filters saved new settings. It IS a `screen_viewed` -- without one, everything after it
     * would be counted against Filters -- and so `position` starts again at the profile shown, as the
     * ticket has it reset "on each screen_viewed".
     */
    fun arrived(zone: String, filtersActive: List<String>, referrer: ProfileScreen?) {
        if (!visited || referrer != ProfileScreen.CheckinFilters) {
            visit(zone, filtersActive, referrer)
            return
        }
        report(ProfileAnalytics.screenViewed(ProfileScreen.SearchProfile, referrer))
        if (!reloadOnReturn) {
            _state.update { it.copy(position = 1) }
            resume()
            return
        }
        reloadOnReturn = false
        this.filtersActive = filtersActive
        stopPlayback()
        _state.update { it.copy(loading = true, failure = null, queue = emptyList(), position = 1, sheet = null, viewerAt = null) }
        load(zone)
    }

    /** Filters saved new settings; the return to Search reads the queue under them. */
    fun filtersSaved() {
        reloadOnReturn = true
    }

    /**
     * A visit to the Search tab: `screen_viewed` once, the queue read, the coach armed.
     *
     * @param zone the phone's IANA zone, for the server's `today` / `tomorrow`.
     * @param filtersActive the §10 filters set away from the defaults, for `search_viewed`.
     */
    fun visit(zone: String, filtersActive: List<String>, referrer: ProfileScreen?) {
        report(ProfileAnalytics.screenViewed(ProfileScreen.SearchProfile, referrer))
        visited = true
        reloadOnReturn = false
        this.filtersActive = filtersActive
        coachShownThisVisit = false
        stopPlayback()
        _state.update {
            it.copy(loading = true, failure = null, queue = emptyList(), position = 1, sheet = null, coach = false, viewerAt = null)
        }
        load(zone)
    }

    /** `Retry` on the failed state: the same visit, read again. */
    fun retry(zone: String) {
        _state.update { it.copy(loading = true, failure = null) }
        load(zone)
    }

    private fun load(zone: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val sessionCall = async { store.session() }
            val countCall = async { store.likesOpenCount() }
            val queue = store.queue(zone)
            (sessionCall.await() as? Outcome.Ok)?.value?.let { s ->
                // ONCE HIDDEN HERE, HIDDEN: a session read that left before `Don't show again` landed
                // on the server still says false, and must not bring the coach back on this device.
                coachHidden = coachHidden || s.coachHidden
                _state.update { it.copy(premium = s.premium) }
            }
            (countCall.await() as? Outcome.Ok)?.value?.let { n -> _state.update { it.copy(likesOpen = n) } }
            when (queue) {
                is Outcome.Failed -> _state.update { it.copy(loading = false, failure = queue.why) }
                is Outcome.Ok -> {
                    val profiles = queue.value.profiles.filterNot { it.userId in decided }
                    report(SearchAnalytics.searchViewed(queue.value.resultsCount, filtersActive))
                    val reopened = unfinished?.takeIf { it.userId == profiles.firstOrNull()?.userId }
                    unfinished = null
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
    }

    /** A profile is on screen: its dwell starts, unless the coach is over it (counted from the dismissal). */
    private fun arrive() {
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
     * reported now, while the dwell is known; [resume] starts a new one if it comes back.
     */
    fun leave() {
        stopPlayback()
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

    /** `Like`: Be sure opens. Inert under the coach -- no event either. */
    fun like() {
        val s = _state.value
        val profile = s.current ?: return
        if (s.coach || s.sheet != null) return
        stopPlayback()
        report(SearchAnalytics.likeTapped(s.position))
        _state.update { it.copy(sheet = LikeSheet.Confirm) }
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmShown(profile.attendedCount))
    }

    /** `Cancel`, a scrim tap or a swipe down on Be sure: nothing is sent, the profile stays. */
    fun cancelConfirm() {
        val profile = _state.value.current ?: return
        if (_state.value.sheet != LikeSheet.Confirm) return
        _state.update { it.copy(sheet = null) }
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(SearchAnalytics.AbandonStage.Confirm, profile.attendedCount))
    }

    /** `Continue`: the message sheet, its draft pre-filled for this person and day. */
    fun continueToMessage() {
        val profile = _state.value.current ?: return
        if (_state.value.sheet != LikeSheet.Confirm) return
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
        val message = messageToSend(_state.value.draft)
        if (message != null) report(SearchAnalytics.likeMessageAttached(graphemes(message)))
        sendLike(message)
    }

    private fun sendLike(message: String?) {
        val s = _state.value
        val profile = s.current ?: return
        if (s.sheet != LikeSheet.Message) return
        val position = s.position
        _state.update { it.copy(sheet = null) }
        keepLikeFlow()
        decided += profile.userId
        sendInBackground { store.like(profile.userId, message, position) }
        next()
    }

    // ── passes ──────────────────────────────────────────────────────────────────────────────────

    /** `Not now` / `Not interested`: one tap, no confirmation, no undo. Inert under the coach. */
    fun pass(type: SearchAnalytics.PassType) {
        val s = _state.value
        val profile = s.current ?: return
        if (s.coach || s.sheet != null) return
        report(SearchAnalytics.passSent(s.position, type))
        decided += profile.userId
        sendInBackground { store.pass(profile.userId, type) }
        next()
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
                position = s.position,
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
        report(SearchAnalytics.promptPlayed(prompt, _state.value.position, pct))
    }

    // ── the likes chip ──────────────────────────────────────────────────────────────────────────

    /** The open-likes count again, e.g. when the app comes back to the front. */
    fun refreshLikesOpen() {
        viewModelScope.launch {
            (store.likesOpenCount() as? Outcome.Ok)?.value?.let { n -> _state.update { it.copy(likesOpen = n) } }
        }
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
     * design ("If it fails, retry silently"): the user has already moved on to the next profile.
     */
    private fun sendInBackground(call: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            if (call() is Outcome.Ok) return@launch
            for (wait in retryDelaysMs) {
                delay(wait)
                if (call() is Outcome.Ok) return@launch
            }
        }
    }

    /** The like flow as it stands, into [saved]: an open sheet kept, a closed one forgotten. */
    private fun keepLikeFlow() {
        val s = _state.value
        val profile = s.current
        val sheet = s.sheet
        if (profile == null || sheet == null) UnfinishedLike.forget(saved)
        else UnfinishedLike(profile.userId, sheet, if (sheet == LikeSheet.Message) s.draft else "").keep(saved)
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

/** An open like flow, as [SavedStateHandle] keeps it: three plain values, so nothing needs a Parcelable. */
private data class UnfinishedLike(val userId: String, val sheet: LikeSheet, val draft: String) {
    fun keep(saved: SavedStateHandle) {
        saved[KEY_FOR] = userId
        saved[KEY_SHEET] = sheet.name
        saved[KEY_DRAFT] = draft
    }

    companion object {
        private const val KEY_FOR = "search.like.for"
        private const val KEY_SHEET = "search.like.sheet"
        private const val KEY_DRAFT = "search.like.draft"

        /** What a process death left, or null -- also for a sheet name this build no longer has. */
        fun from(saved: SavedStateHandle): UnfinishedLike? {
            val userId = saved.get<String>(KEY_FOR) ?: return null
            val sheet = LikeSheet.entries.firstOrNull { it.name == saved.get<String>(KEY_SHEET) } ?: return null
            return UnfinishedLike(userId, sheet, saved.get<String>(KEY_DRAFT).orEmpty())
        }

        fun forget(saved: SavedStateHandle) {
            saved.remove<String>(KEY_FOR)
            saved.remove<String>(KEY_SHEET)
            saved.remove<String>(KEY_DRAFT)
        }
    }
}
