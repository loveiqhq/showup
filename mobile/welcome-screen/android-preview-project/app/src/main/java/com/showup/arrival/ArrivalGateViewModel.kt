/*
 * ArrivalGateViewModel.kt
 * ShowUp · the arrival check-in's confirm / store / close / undo (SHOWUP-195)
 *
 * One per date for the session, shared by the Date screen and the chat (`useArrivedGate`). The sheets
 * take [GateUi] and return pixels.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * NOTHING IS STORED WITHOUT A FRESH FIX. No fix inside [LOCATION_FIX_TIMEOUT_MS] is frame C and the
 * store is not called; a fix that arrives after the bound is dropped with the read.
 *
 * A FAILED STORE IS FRAME C TOO (R5). The person retries with `Try again`, which reads and stores again
 * under a new client id.
 *
 * CLOSE COMMITS, UNDO DELETES, AND NEITHER CAN BECOME THE OTHER (R4, R8). Both are optimistic -- the
 * sheet closes at once -- and retried on [retryDelayMs] until the server answers. An undo is never
 * followed by a commit of that record, and a new `Yes` cancels a delete still retrying.
 *
 * CHECKED IN IS THE SERVER'S WORD OR THIS SESSION'S COMMIT. A date read that still says `checkedIn =
 * false` while the commit is in flight must not bring the Check-in bar back.
 *
 * A CHECK-IN STORED WHILE THE APP IS AWAY COMMITS AT ONCE. The person tapped Yes and pocketed the phone
 * during the read; a done sheet nobody sees must not hold the match's push back, so it is Close (as the app
 * leaving D is). Coming back to the foreground before the store lands clears that, and D shows as normal.
 *
 * COMMIT AND UNDO OUTLIVE THIS MODEL when given a `work` scope: an Activity finished while offline must
 * still tell the match once the network is back. The read-and-store of `Yes` stays on the model's scope.
 *
 * NO COORDINATE IN ANY EVENT. The fix goes to the store and nowhere else; nothing here logs.
 */
package com.showup.arrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.Outcome
import com.showup.date.ActiveDate
import com.showup.profile.report
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** The sheet in front of the person: none, B / C (one sheet, [Confirm.locError] says which), or D. */
sealed interface GatePhase {
    data object Idle : GatePhase

    /** [attempt] counts the failed reads, so a second failure is a change the sheet can announce again. */
    data class Confirm(
        val entry: ArrivalEntry,
        val locError: Boolean,
        val busy: Boolean,
        val note: String?,
        val attempt: Int = 0,
    ) : GatePhase

    data class Done(val entry: ArrivalEntry) : GatePhase
}

data class GateUi(
    val phase: GatePhase = GatePhase.Idle,
    /** This session has committed (or found committed) the bound date's check-in. */
    val committed: Boolean = false,
    /** An undo was tapped and not yet consumed: the chat's host leaves for the Date screen. */
    val undone: Boolean = false,
)

class ArrivalGateViewModel(
    private val store: ArrivalStore,
    private val reader: LocationFixReader,
    private val analytics: AnalyticsTracker?,
    private val clock: () -> Instant,
    private val newId: () -> String,
    /** Where commit and undo run, so their retries outlive this model; null is [viewModelScope] (tests, previews). */
    work: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope = work ?: viewModelScope

    private val _ui = MutableStateFlow(GateUi())
    val ui: StateFlow<GateUi> = _ui.asStateFlow()

    private var date: ActiveDate? = null
    private var yesJob: Job? = null
    private var commitJob: Job? = null
    private var undoJob: Job? = null

    /** The app left while a read was busy and has not come back: a store that lands now commits. */
    private var away = false

    /** Every date read goes through here. Another date resets everything. */
    fun bind(date: ActiveDate) {
        if (this.date?.id != date.id) {
            cancelAll()
            away = false
            _ui.value = GateUi()
        }
        this.date = date
    }

    /** The server's word or this session's commit, for the bound date. */
    fun checkedIn(): Boolean = date?.checkedIn == true || _ui.value.committed

    /**
     * Shows B. False when there is nothing to check in to (already checked in, or no date bound); true
     * when a sheet is up, whether just opened or already there.
     */
    fun open(entry: ArrivalEntry, note: String? = null): Boolean {
        val date = date ?: return false
        if (checkedIn()) return false
        if (_ui.value.phase != GatePhase.Idle) return true
        _ui.update { it.copy(phase = GatePhase.Confirm(entry, locError = false, busy = false, note = note)) }
        analytics?.report(ArrivalAnalytics.confirmShown(date.id, entry, minutesVsStart(date.meetAt, clock())))
        return true
    }

    /** `Not yet`, the scrim, back or swipe on B or C. Inert while busy (R7). */
    fun notYet() {
        val phase = _ui.value.phase as? GatePhase.Confirm ?: return
        if (phase.busy) return
        _ui.update { it.copy(phase = GatePhase.Idle) }
    }

    /** `Yes, I've arrived` and `Try again`: read one fresh fix, store it. */
    fun yes() {
        val date = date ?: return
        val phase = _ui.value.phase as? GatePhase.Confirm ?: return
        if (phase.busy) return
        analytics?.report(ArrivalAnalytics.confirmSubmitted(date.id, phase.entry, minutesVsStart(date.meetAt, clock())))
        away = false
        _ui.update { it.copy(phase = phase.copy(busy = true)) }
        // The new store replaces the server's uncommitted row; a delete still retrying must not land after it.
        undoJob?.cancel()
        val clientId = newId()
        yesJob = viewModelScope.launch {
            val fix = withTimeoutOrNull(LOCATION_FIX_TIMEOUT_MS) { reader.fix() }
            if (fix == null) {
                failed(phase)
                return@launch
            }
            when (store.checkIn(date.id, fix, phase.entry, clientId)) {
                CheckInResult.Stored -> if (away) {
                    commitNow(date)
                } else {
                    _ui.update { it.copy(phase = GatePhase.Done(phase.entry)) }
                }
                CheckInResult.AlreadyCheckedIn -> _ui.update { it.copy(phase = GatePhase.Idle, committed = true) }
                is CheckInResult.Failed -> failed(phase)
            }
        }
    }

    /** `Close`, the scrim, back or swipe on D: the match is told. */
    fun close() {
        val date = date ?: return
        if (_ui.value.phase !is GatePhase.Done) return
        commitNow(date)
    }

    /** The sheet is gone and the match is told, retried until the server answers. */
    private fun commitNow(date: ActiveDate) {
        _ui.update { it.copy(phase = GatePhase.Idle, committed = true, undone = false) }
        commitJob?.cancel()
        commitJob = workScope.launch { retried { store.commit(date.id) } }
    }

    /** `Undo check-in` on D: the record is deleted, never committed. */
    fun undo() {
        val date = date ?: return
        if (_ui.value.phase !is GatePhase.Done) return
        commitJob?.cancel()
        _ui.update { it.copy(phase = GatePhase.Idle, committed = false, undone = true) }
        undoJob?.cancel()
        undoJob = workScope.launch { retried { store.undo(date.id) } }
    }

    /**
     * The app went to the background: with D up that is Close; with a read busy, a store that lands before
     * [foregrounded] commits instead of showing D.
     */
    fun leftApp() {
        when (val phase = _ui.value.phase) {
            is GatePhase.Done -> close()
            is GatePhase.Confirm -> if (phase.busy) away = true
            GatePhase.Idle -> Unit
        }
    }

    /** The app is in front again (the host's ON_START): a store that lands now is D as normal. */
    fun foregrounded() {
        away = false
    }

    /** The host has acted on [GateUi.undone]. */
    fun consumeUndone() = _ui.update { it.copy(undone = false) }

    private fun failed(phase: GatePhase.Confirm) =
        _ui.update {
            val now = it.phase as? GatePhase.Confirm ?: phase
            it.copy(phase = now.copy(locError = true, busy = false, attempt = now.attempt + 1))
        }

    /** [call] until it answers, waiting [retryDelayMs] between tries. */
    private suspend fun retried(call: suspend () -> Outcome<Unit>) {
        var attempt = 0
        while (call() !is Outcome.Ok) delay(retryDelayMs(attempt++))
    }

    private fun cancelAll() {
        yesJob?.cancel()
        commitJob?.cancel()
        undoJob?.cancel()
    }
}
