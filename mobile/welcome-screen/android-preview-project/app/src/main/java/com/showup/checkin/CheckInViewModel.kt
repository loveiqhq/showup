/*
 * CheckInViewModel.kt
 * ShowUp · the 48hrs Check-In's state machine (SHOWUP-178 to SHOWUP-183)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE OWNER FOR THE WHOLE FLOW, BUILT BEFORE ITS STEPS (epic, "Built once")
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The step order, the back and ✕ routing, the gate's reopen rule, what each step keeps, and every
 * event of family H. The screens take this state and return taps; the host (MainActivity) moves
 * between them through the `onX` callbacks, exactly as `PickStepsViewModel` does.
 *
 * NOTHING IS KEPT AS A DRAFT (epic rule 3, 13 Oct 2026). Availability and the uplift live in this
 * object only -- never saved, never pre-filled -- and a ✕ throws them away. Motivation and Vibe
 * pre-select the LAST ACCEPTED answer and Filters the user's filter settings, all from the server;
 * each is saved on its accepted Continue. Back and forward within one flow keep everything.
 *
 * PICKED HOURS ARE KEYED BY THEIR WALL-CLOCK TIME, not their hour number. A Check-In day runs 7am to
 * 7am and Today is re-evaluated on every foreground, so a flow left open across 7am sees Today
 * become the day that was Tomorrow: keyed by time, its picks simply appear in the right grid, and a
 * started hour falls out of both the grid and the selection, "silently". Keyed by hour number, the
 * old night's 5am would have been read as the new morning's.
 *
 * NOT SAVED ACROSS A PROCESS DEATH, on purpose: "Only ✕ (or leaving the app long enough for the
 * screen to be destroyed) clears them" (story 02). The host restarts a Check-In whose owner did not
 * survive at Today, where Motivation, Vibe and Filters will pre-fill from the server again.
 */
package com.showup.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.designsystem.Motion
import com.showup.profile.ProfileScreen
import com.showup.profile.report
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** One availability grid as drawn: its day, and each slot's hour and wall-clock start. */
data class AvailabilityGrid(val day: CheckInStep, val hours: List<Int>, val starts: List<LocalDateTime>) {
    /** The picks inside this grid, by hour -- what the screen draws and the summary reads. */
    fun picked(picks: Map<LocalDateTime, SlotTier>): Map<Int, SlotTier> =
        hours.zip(starts).mapNotNull { (h, t) -> picks[t]?.let { h to it } }.toMap()
}

/** What the Check-In screens draw. */
data class CheckInUiState(
    /** The server's state, once read. */
    val server: CheckInState? = null,
    /** Today and Tomorrow at the last evaluation of the clock. */
    val days: CheckInDays? = null,
    /** Every picked hour of this flow, both days, by wall-clock start. */
    val picks: Map<LocalDateTime, SlotTier> = emptyMap(),
    val motivation: String? = null,
    val vibes: Set<String> = emptySet(),
    val filters: CheckInFilters? = null,
    val uplift: String = "",
    /** How it works -- collapsed on every entry, per screen. */
    val howItWorksOpen: Boolean = false,
    /** The No hours today? confirm. */
    val noHoursDialog: Boolean = false,
    /** The footer's toast: its text and a tick that restarts its 2600 ms on a repeat tap. */
    val toast: String? = null,
    val toastTick: Int = 0,
    /** Find my date is in flight: the button, field, back and ✕ are inert. */
    val saving: Boolean = false,
    /** The save-failed card -- shown until the next tap or an edit. */
    val saveFailed: Boolean = false,
    /** The server refused the text for contact info: state C until the text changes. */
    val serverContactError: Boolean = false,
) {
    val todayGrid: AvailabilityGrid? get() = days?.let { gridOf(CheckInStep.Today, it) }
    val tomorrowGrid: AvailabilityGrid? get() = days?.let { gridOf(CheckInStep.Tomorrow, it) }

    /** Contact info in the field, as the client sees it or as the server reported it. */
    val upliftBlocked: Boolean get() = serverContactError || upliftHasContact(uplift)
}

/** The grid for [day] at [days]: Today from the next full hour, Tomorrow from 7am, both to the 6am slot. */
fun gridOf(day: CheckInStep, days: CheckInDays): AvailabilityGrid {
    val (date, start) = if (day == CheckInStep.Today) days.todayDate to days.todayStartHour
    else days.tomorrowDate to CHECK_IN_DAY_START
    val hours = availabilityHours(start)
    val starts = hours.map { h ->
        (if (h >= CHECK_IN_DAY_START) date else date.plusDays(1)).atTime(h, 0)
    }
    return AvailabilityGrid(day, hours, starts)
}

open class CheckInViewModel(
    private val store: CheckInStore,
    private val analytics: AnalyticsTracker? = null,
    /** The device clock and zone, injected so the 7am day and the next full hour are testable. */
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Milliseconds, for `time_to_answer_ms`. */
    private val millis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckInUiState())
    val state: StateFlow<CheckInUiState> = _state.asStateFlow()

    /** The step on screen, or null on the gate or outside the flow. */
    private var currentStep: CheckInStep? = null

    /** When the step on screen was viewed, for `time_to_answer_ms`. */
    private var viewedAt = 0L

    /** What a pre-filled step showed on entry, for `prefilled` / `picked` and `filters_changed`. */
    private var motivationOnEntry: String? = null
    private var vibesOnEntry: Set<String> = emptySet()
    private var filtersOnEntry: CheckInFilters? = null

    /** The filter settings' last save failed: the commit saves them first. */
    private var filtersUnsaved = false

    /** `attempt` on `check_in_commit_failed`: 1 for the first tap on this screen view. */
    private var commitAttempt = 0

    /** Whether this object holds a flow -- false after a process death, so the host restarts at Today. */
    var flowAlive: Boolean = false
        private set

    private var loading: Job? = null
    private var toastJob: Job? = null

    /**
     * The pre-fill of Motivation, Vibe and Filters is still owed: set when a flow begins, paid by
     * the first server read that lands. Not tied to one request -- the read that begins a flow can
     * fail (offline, a timeout), and the next one, on an arrival or a foreground, must still pay it.
     */
    private var prefillPending = false

    /** The user has changed this step's answer in this flow: a late read never overwrites it. */
    private var motivationTouched = false
    private var vibesTouched = false
    private var filtersTouched = false

    /**
     * Filters showed the offline stand-in -- no gender ticked, ages and distance at their defaults --
     * because no read had landed. A read that lands before the user touches it replaces it.
     */
    private var filtersStandIn = false

    /** The gate's three ways in may fire once per showing: a second tap during the push does nothing. */
    private var gateArmed = false

    /**
     * Bumped by every local change the server will catch up with -- the intro passed, a commit
     * saved. A read that STARTED before the latest one is stale: it may still say "not passed" or
     * "not active", and applying it as-is would send a user who has just passed the intro back to
     * it from the gate, or bounce a Check-In just committed from Search to the gate.
     */
    private var writeSeq = 0

    init {
        evaluateClock()
    }

    // ── the server and the clock ───────────────────────────────────────────────────────────────

    /** Reads the Check-In state. On every foreground and every arrival: "read on every foreground". */
    fun refresh() {
        if (loading?.isActive == true) return
        // The sequence is taken NOW, not when the coroutine first runs: a write made between the
        // two still makes this read stale.
        val seq = writeSeq
        loading = viewModelScope.launch { readAt(seq) }
    }

    /**
     * One read, awaited -- for the launch, which must know whether a Check-In is active before it
     * picks the gate or Search. Null when the server could not be reached.
     */
    suspend fun read(): CheckInState? = readAt(writeSeq)

    private suspend fun readAt(seq: Int): CheckInState? {
        val loaded = store.load() ?: return null
        // Stale: both flags only ever move forward between reads, so keep what was set locally.
        val prior = _state.value.server
        val read = if (seq == writeSeq || prior == null) loaded else loaded.copy(
            introPassed = loaded.introPassed || prior.introPassed,
            active = loaded.active || prior.active,
        )
        applyRead(read)
        return read
    }

    /** A read landed: it is the server's state, and it pays an owed pre-fill on untouched steps. */
    private fun applyRead(read: CheckInState) {
        val prefill = prefillPending && flowAlive
        if (prefill) prefillPending = false
        _state.update { s ->
            if (!prefill) return@update s.copy(server = read)
            s.copy(
                server = read,
                motivation = if (motivationTouched) s.motivation else s.motivation ?: read.lastMotivation,
                vibes = if (vibesTouched) s.vibes else s.vibes.ifEmpty { read.lastVibes },
                filters = when {
                    filtersTouched -> s.filters
                    filtersStandIn || s.filters == null -> read.filters
                    else -> s.filters
                },
            )
        }
        if (prefill && !filtersTouched) filtersStandIn = false
    }

    /**
     * The device clock, re-read: "re-evaluated on every foreground, and at minimum when the screen is
     * shown. A slot that has started disappears from the grid and from the selection, silently."
     */
    fun evaluateClock() {
        val days = checkInDays(clock())
        _state.update { s ->
            val offered = (gridOf(CheckInStep.Today, days).starts + gridOf(CheckInStep.Tomorrow, days).starts).toSet()
            s.copy(days = days, picks = s.picks.filterKeys { it in offered })
        }
    }

    // ── entering and leaving ───────────────────────────────────────────────────────────────────

    /**
     * A NEW flow begins -- from Profile 23 into the intro, or from the gate. Availability and the
     * uplift start empty; Motivation, Vibe and Filters start from the server's last answers.
     */
    fun beginFlow() {
        flowAlive = true
        prefillPending = true
        motivationTouched = false
        vibesTouched = false
        filtersTouched = false
        filtersStandIn = false
        clearToast()
        val server = _state.value.server
        _state.update {
            it.copy(
                picks = emptyMap(),
                uplift = "",
                motivation = server?.lastMotivation,
                vibes = server?.lastVibes.orEmpty(),
                filters = server?.filters,
                saving = false,
                saveFailed = false,
                serverContactError = false,
                noHoursDialog = false,
            )
        }
        evaluateClock()
        // The read may land after the flow begins -- or fail, and a later one land: whichever read
        // lands first pre-fills, untouched steps only (`applyRead`).
        refresh()
    }

    /**
     * [step] is on screen. A real arrival reports the view; a recomposition of the step already on
     * screen does neither. [referrer] comes from the navigation, never hard-coded.
     */
    fun arrived(step: CheckInStep?, screen: ProfileScreen, referrer: ProfileScreen?) {
        if (step != null && currentStep == step) return
        if (step == null && currentStep == null && lastScreen == screen) return
        currentStep = step
        lastScreen = screen
        // The gate is armed while it is the screen shown, and only then.
        gateArmed = step == null && screen == ProfileScreen.CheckinSearchUnavailable
        viewedAt = millis()
        clearToast()
        evaluateClock()
        _state.update { it.copy(howItWorksOpen = false, noHoursDialog = false, saveFailed = false) }
        when (step) {
            CheckInStep.Motivation -> motivationOnEntry = _state.value.motivation
            CheckInStep.Vibe -> vibesOnEntry = _state.value.vibes
            CheckInStep.Filters -> {
                // NO READ HAS LANDED YET (offline since the flow began): the step still works, from a
                // stand-in with no gender ticked, so Continue raises the gender toast rather than doing
                // nothing. A read that lands before the user touches it replaces the stand-in.
                if (_state.value.filters == null) {
                    filtersStandIn = true
                    _state.update { it.copy(filters = CheckInFilters(genders = emptySet())) }
                }
                filtersOnEntry = _state.value.filters
            }
            CheckInStep.Uplift -> commitAttempt = 0
            else -> Unit
        }
        refresh()
        analytics?.report(com.showup.profile.ProfileAnalytics.screenViewed(screen, referrer))
    }

    private var lastScreen: ProfileScreen? = null

    /** The flow was left by a route it does not take -- a developer-menu jump. Discards it. */
    fun leftElsewhere() {
        currentStep = null
        lastScreen = null
        gateArmed = false
        discard()
    }

    /**
     * The tabs are shown with no Check-In screen -- Settings, for a user with no Check-In. Its
     * Instant tab is still the gate, so it arms it; it has no §11 screen, so nothing is reported.
     */
    fun atTabs() {
        currentStep = null
        lastScreen = null
        gateArmed = true
        clearToast()
    }

    private val windowId: String get() = _state.value.server?.windowId.orEmpty()

    private fun onScreen(step: CheckInStep): Boolean = currentStep == step && !_state.value.saving

    // ── the intro and the gate (Check-In 01) ───────────────────────────────────────────────────

    /** `Check-in for a date`: sets the intro-passed flag and starts the Check-In. Single-fire. */
    fun introStarted(onAdvance: () -> Unit) {
        if (!onScreen(CheckInStep.Intro)) return
        currentStep = null
        analytics?.report(CheckInAnalytics.started(windowId))
        writeSeq++
        _state.update { s -> s.copy(server = s.server?.copy(introPassed = true)) }
        viewModelScope.launch { store.markIntroPassed() }
        onAdvance()
    }

    /** Where the gate reopens: Today once the intro has been passed, the intro before that. */
    fun gateDestination(): CheckInStep =
        if (_state.value.server?.introPassed == true) CheckInStep.Today else CheckInStep.Intro

    /** The gate's CTA, Search tab or Instant tab: reopens the Check-In. */
    fun gateTapped(target: CheckInAnalytics.GateTarget, onReopen: (CheckInStep) -> Unit) {
        if (!gateArmed) return
        gateArmed = false
        val destination = gateDestination()
        analytics?.report(CheckInAnalytics.gateTapped(target, destination == CheckInStep.Today))
        beginFlow()
        onReopen(destination)
    }

    // ── availability (Check-In 02) ─────────────────────────────────────────────────────────────

    /** A slot tapped: idle -> preferred -> available -> idle. No event. */
    fun slotTapped(step: CheckInStep, start: LocalDateTime) {
        if (!onScreen(step)) return
        _state.update { s ->
            val next = cycleSlot(s.picks[start])
            s.copy(picks = if (next == null) s.picks - start else s.picks + (start to next))
        }
    }

    /** `Clear all`: this day only, no confirm. No event. */
    fun clearAll(step: CheckInStep) {
        if (!onScreen(step)) return
        val grid = gridFor(step) ?: return
        _state.update { s -> s.copy(picks = s.picks - grid.starts.toSet()) }
    }

    /** How it works, toggled inline. No event. */
    fun toggleHowItWorks() {
        _state.update { it.copy(howItWorksOpen = !it.howItWorksOpen) }
    }

    /** Continue on Today or Tomorrow. */
    fun availabilityContinue(step: CheckInStep, onAdvance: () -> Unit) {
        if (!onScreen(step)) return
        evaluateClock()
        val grid = gridFor(step) ?: return
        val picked = grid.picked(_state.value.picks)
        when {
            picked.isNotEmpty() -> acceptAvailability(step, picked, onAdvance)
            // Today is optional: an empty Today raises the confirm, never an error.
            step == CheckInStep.Today -> _state.update { it.copy(noHoursDialog = true) }
            // Tomorrow is required: the dimmed tap still registers, and raises the toast.
            else -> showToast(CheckInCopy.TOMORROW_TOAST)
        }
    }

    /** The confirm's `Continue to tomorrow`: an empty Today is recorded and the flow moves on. */
    fun noHoursConfirmed(onAdvance: () -> Unit) {
        if (!onScreen(CheckInStep.Today) || !_state.value.noHoursDialog) return
        analytics?.report(CheckInAnalytics.noHoursResolved(CheckInAnalytics.NoHoursAction.ContinueTomorrow, windowId))
        _state.update { it.copy(noHoursDialog = false) }
        acceptAvailability(CheckInStep.Today, emptyMap(), onAdvance)
    }

    /** `Cancel, pick hours today` -- or, with [dismissed], a scrim tap or Android back. Stays on Today. */
    fun noHoursCancelled(dismissed: Boolean) {
        if (!_state.value.noHoursDialog) return
        val action = if (dismissed) CheckInAnalytics.NoHoursAction.Dismissed else CheckInAnalytics.NoHoursAction.PickToday
        analytics?.report(CheckInAnalytics.noHoursResolved(action, windowId))
        _state.update { it.copy(noHoursDialog = false) }
    }

    private fun acceptAvailability(step: CheckInStep, picked: Map<Int, SlotTier>, onAdvance: () -> Unit) {
        analytics?.report(
            CheckInAnalytics.stepAnswered(step, if (picked.isEmpty()) "none" else "hours", millis() - viewedAt),
        )
        analytics?.report(
            CheckInAnalytics.slotsSet(
                step,
                tierCount(picked, SlotTier.Preferred),
                tierCount(picked, SlotTier.Available),
                windowId,
            ),
        )
        leaveStep()
        onAdvance()
    }

    private fun gridFor(step: CheckInStep): AvailabilityGrid? = when (step) {
        CheckInStep.Today -> _state.value.todayGrid
        CheckInStep.Tomorrow -> _state.value.tomorrowGrid
        else -> null
    }

    // ── motivation (Check-In 03) ───────────────────────────────────────────────────────────────

    /** A row tapped. Single-select: another row replaces it; the selected row does nothing. */
    fun motivationTapped(id: String) {
        if (!onScreen(CheckInStep.Motivation)) return
        motivationTouched = true
        _state.update { it.copy(motivation = id) }
    }

    fun motivationContinue(onAdvance: () -> Unit) {
        if (!onScreen(CheckInStep.Motivation)) return
        val picked = MotivationOption.of(_state.value.motivation)
        if (picked == null) {
            showToast(CheckInCopy.MOTIVATION_TOAST)
            return
        }
        val answer = if (picked.id == motivationOnEntry) "prefilled" else "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(CheckInStep.Motivation, answer, millis() - viewedAt))
        analytics?.report(CheckInAnalytics.motivationSelected(picked, windowId))
        // The last accepted answer, saved now -- whatever happens to the flow afterwards.
        motivationTouched = true
        _state.update { s -> s.copy(server = s.server?.copy(lastMotivation = picked.id)) }
        viewModelScope.launch { store.saveAnswers(picked.id, null) }
        leaveStep()
        onAdvance()
    }

    // ── vibe (Check-In 04) ─────────────────────────────────────────────────────────────────────

    /** A chip tapped. At 5/5 an idle chip selects nothing and raises the max toast. No event. */
    fun vibeTapped(id: String) {
        if (!onScreen(CheckInStep.Vibe)) return
        when (val tap = tapVibe(_state.value.vibes, id)) {
            is VibeTap.Changed -> {
                vibesTouched = true
                _state.update { it.copy(vibes = tap.vibes) }
            }
            VibeTap.Limit -> showToast(CheckInCopy.VIBE_TOAST_MAX)
        }
    }

    fun vibeContinue(onAdvance: () -> Unit) {
        if (!onScreen(CheckInStep.Vibe)) return
        val vibes = _state.value.vibes
        if (vibes.isEmpty()) {
            showToast(CheckInCopy.VIBE_TOAST_EMPTY)
            return
        }
        // Same values in any tap order is unchanged.
        val answer = if (vibes == vibesOnEntry) "prefilled" else "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(CheckInStep.Vibe, answer, millis() - viewedAt))
        analytics?.report(CheckInAnalytics.vibeSelected(vibes, windowId))
        vibesTouched = true
        _state.update { s -> s.copy(server = s.server?.copy(lastVibes = vibes)) }
        viewModelScope.launch { store.saveAnswers(null, vibes) }
        leaveStep()
        onAdvance()
    }

    // ── filters (Check-In 05) ──────────────────────────────────────────────────────────────────

    fun genderTapped(id: String) {
        if (!onScreen(CheckInStep.Filters)) return
        filtersTouched = true
        _state.update { s -> s.copy(filters = s.filters?.let { it.copy(genders = toggleGender(it.genders, id)) }) }
    }

    /** An age thumb moved; the thumbs may meet but never cross. No event per drag. */
    fun ageMoved(thumb: Int, value: Int) {
        if (!onScreen(CheckInStep.Filters)) return
        filtersTouched = true
        _state.update { s -> s.copy(filters = s.filters?.let { moveAgeThumb(it, thumb, value) }) }
    }

    fun distanceMoved(km: Int) {
        if (!onScreen(CheckInStep.Filters)) return
        filtersTouched = true
        _state.update { s -> s.copy(filters = s.filters?.copy(distanceKm = km.coerceIn(DISTANCE_MIN, DISTANCE_MAX))) }
    }

    fun filtersContinue(onAdvance: () -> Unit) {
        if (!onScreen(CheckInStep.Filters)) return
        val filters = _state.value.filters ?: return
        if (filters.genders.isEmpty()) {
            showToast(CheckInCopy.FILTERS_TOAST)
            return
        }
        val entry = filtersOnEntry ?: filters
        val answer = if (filtersChanged(entry, filters) == "none") "prefilled" else "picked"
        analytics?.report(CheckInAnalytics.stepAnswered(CheckInStep.Filters, answer, millis() - viewedAt))
        analytics?.report(CheckInAnalytics.filtersChanged(entry, filters, windowId))
        filtersTouched = true
        filtersStandIn = false
        _state.update { s -> s.copy(server = s.server?.copy(filters = filters)) }
        filtersUnsaved = true
        viewModelScope.launch { if (store.saveFilters(filters)) filtersUnsaved = false }
        leaveStep()
        onAdvance()
    }

    // ── uplift and the commit (Check-In 06) ────────────────────────────────────────────────────

    /** The field changed: cut at 100 graphemes; an edit clears the save-failed card and a server refusal. */
    fun upliftChanged(text: String) {
        if (!onScreen(CheckInStep.Uplift)) return
        _state.update { it.copy(uplift = clampUplift(text), saveFailed = false, serverContactError = false) }
    }

    /** `Find my date`. Blocked text raises the toast and sends nothing; otherwise the whole Check-In commits. */
    fun findMyDate(onCommitted: () -> Unit) {
        if (!onScreen(CheckInStep.Uplift)) return
        val s = _state.value
        if (s.upliftBlocked) {
            showToast(CheckInCopy.CONTACT_ERROR)
            return
        }
        // Unreachable through the walk -- Motivation's Continue needs one -- but never a dead button:
        // the save-failed card, which is the truth, rather than a tap that does nothing.
        val motivation = s.motivation ?: run {
            _state.update { it.copy(saveFailed = true) }
            return
        }
        commitAttempt += 1
        val attempt = commitAttempt
        _state.update { it.copy(saving = true, saveFailed = false) }
        clearToast()
        viewModelScope.launch {
            val result = commit(motivation)
            val window = _state.value.server?.windowId.orEmpty()
            when (result) {
                is CommitResult.Saved -> {
                    writeSeq++
                    analytics?.report(
                        CheckInAnalytics.stepAnswered(CheckInStep.Uplift, upliftAnswerValue(s.uplift), millis() - viewedAt),
                    )
                    _state.update { it.copy(server = result.state, saving = false) }
                    leaveStep()
                    discard()
                    onCommitted()
                }
                CommitResult.ContactInfo -> {
                    analytics?.report(CheckInAnalytics.commitFailed(CheckInAnalytics.CommitFailure.ContactInfo, attempt, window))
                    _state.update { it.copy(saving = false, serverContactError = true) }
                }
                CommitResult.Network, CommitResult.Server -> {
                    val reason = if (result == CommitResult.Network) CheckInAnalytics.CommitFailure.Network
                    else CheckInAnalytics.CommitFailure.Server
                    analytics?.report(CheckInAnalytics.commitFailed(reason, attempt, window))
                    _state.update { it.copy(saving = false, saveFailed = true) }
                }
            }
        }
    }

    /**
     * The commit itself: a window id first if none was ever read, the filter settings first if their
     * last save did not land, then every upcoming picked hour with its instant on the device's zone.
     */
    private suspend fun commit(motivation: String): CommitResult {
        val server = _state.value.server ?: store.load()?.also { read -> _state.update { it.copy(server = read) } }
        val windowId = server?.windowId ?: return CommitResult.Network
        val s = _state.value
        val filters = s.filters
        if (filtersUnsaved && filters != null) {
            if (!store.saveFilters(filters)) return CommitResult.Network
            filtersUnsaved = false
        }
        evaluateClock()
        val zone = zone()
        val slots = _state.value.picks.entries
            .sortedBy { it.key }
            .map { (start, tier) -> CommitSlot(start.atZone(zone).toInstant(), tier) }
        val uplift = s.uplift.trim().ifEmpty { null }
        return store.commit(CommitRequest(windowId, slots, motivation, s.vibes, uplift))
    }

    // ── back and close ─────────────────────────────────────────────────────────────────────────

    /** The chevron, the swipe and Android back. Nothing is lost: back and forward keep the picks. */
    fun backPressed(step: CheckInStep, onBack: () -> Unit) {
        if (!onScreen(step)) return
        leaveStep()
        onBack()
    }

    /** The ✕ -- and Android back on the intro. Discards the flow and lands on the gate. No confirm. */
    fun closePressed(step: CheckInStep, onClosed: () -> Unit) {
        if (!onScreen(step)) return
        analytics?.report(CheckInAnalytics.closed(step, windowId))
        leaveStep()
        discard()
        onClosed()
    }

    /** The toast's own timer ran out. */
    private fun showToast(text: String) {
        toastJob?.cancel()
        _state.update { it.copy(toast = text, toastTick = it.toastTick + 1) }
        toastJob = viewModelScope.launch {
            delay(Motion.TOAST.toLong())
            _state.update { it.copy(toast = null) }
        }
    }

    private fun clearToast() {
        toastJob?.cancel()
        toastJob = null
        if (_state.value.toast != null) _state.update { it.copy(toast = null) }
    }

    private fun leaveStep() {
        currentStep = null
        clearToast()
    }

    /** Availability and the uplift are gone; the answers return to the server's on the next flow. */
    private fun discard() {
        flowAlive = false
        filtersUnsaved = false
        prefillPending = false
        filtersStandIn = false
        _state.update {
            it.copy(
                picks = emptyMap(),
                uplift = "",
                motivation = null,
                vibes = emptySet(),
                filters = null,
                noHoursDialog = false,
                saving = false,
                saveFailed = false,
                serverContactError = false,
            )
        }
    }
}
