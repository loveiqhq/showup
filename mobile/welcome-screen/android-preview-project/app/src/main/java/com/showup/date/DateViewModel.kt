/*
 * DateViewModel.kt
 * ShowUp · the Date screen's asynchronous work (SHOWUP-193)
 *
 * The screen loads, re-reads on every foreground and changes state with the clock, which is the
 * trigger CLAUDE.md names for a ViewModel. The screen itself stays a function from [DateUi] to
 * pixels.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A VISIT IS REPORTED ONCE, WHEN THE DATE IS KNOWN. `screen_viewed` and `date_screen_viewed` need the
 * date's id and state, so [arrived] marks a visit and the first read that lands reports it. A second
 * [arrived] before that does not report twice.
 *
 * THE UNLOCK IS THE CLOCK'S, AND IT IS SILENT. [tick] moves `now`; the state is derived from it, so
 * A becomes B at meet − 1:30 in place, with no reload and no event -- the server's
 * `coordinate_unlocked` is the record of that moment.
 *
 * EVERY FOREGROUND RE-READS, so an agreed change of time or place (Date 05) lands in place, and no
 * date on the re-read means it ended elsewhere: [DateUi.left] tells the host to leave. A FAILED
 * re-read keeps what is on screen -- a dropped connection is not a cancelled date.
 */
package com.showup.date

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.designsystem.Motion
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import com.showup.profile.report
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Everything the Date screen renders, in one value. */
data class DateUi(
    val date: ActiveDate? = null,
    val now: Instant,
    val loading: Boolean = true,
    /** Why the last read failed, while nothing is on screen; null once a date is shown. */
    val failed: CallFailure? = null,
    /** The Copy pill reads `Copied`. */
    val copied: Boolean = false,
    /** The date is gone (ended or cancelled elsewhere): the host leaves the screen. */
    val left: Boolean = false,
) {
    val state: DateState? get() = date?.let { dateState(now, it.unlockAt, it.unread) }
}

class DateViewModel(
    private val repo: DateStore,
    private val analytics: AnalyticsTracker? = null,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _state = MutableStateFlow(DateUi(now = clock()))
    val state: StateFlow<DateUi> = _state.asStateFlow()

    /** A visit waiting for its first read, and where it came from. */
    private var pendingVisit: Visit? = null
    private var copiedJob: Job? = null

    private class Visit(val referrer: ProfileScreen?)

    /** The screen is on screen (from launch, a match, or back from a pushed screen): one visit. */
    fun arrived(referrer: ProfileScreen?) {
        if (pendingVisit != null) return
        pendingVisit = Visit(referrer)
        read()
    }

    /** The app came back to the foreground: re-read, report nothing. */
    fun foreground() = read()

    /**
     * The launch read already found [date]: draw it at once rather than a blank frame while the
     * screen reads it again. Reports nothing -- the visit is [arrived]'s, from its own read.
     */
    fun seed(date: ActiveDate) {
        if (_state.value.date != null) return
        _state.update { it.copy(date = date, now = clock(), loading = false, failed = null) }
    }

    /** `Try again` on the failed state: read again, the visit still waiting for it. */
    fun retry() {
        _state.update { it.copy(loading = true, failed = null) }
        read()
    }

    /** The clock moved. The state follows it; nothing is reported. */
    fun tick() = _state.update { it.copy(now = clock()) }

    fun quickAction(action: QuickAction) {
        val date = _state.value.date ?: return
        analytics?.report(DateAnalytics.quickActionTapped(date, action, clock()))
    }

    /** Copy was tapped: `Copied` for [Motion.COPIED], restarted by another tap. */
    fun copied() {
        copiedJob?.cancel()
        _state.update { it.copy(copied = true) }
        copiedJob = viewModelScope.launch {
            delay(Motion.COPIED.toLong())
            _state.update { it.copy(copied = false) }
        }
    }

    private fun read() {
        viewModelScope.launch {
            val answer = repo.active(zone().id)
            val now = clock()
            when (answer) {
                is Outcome.Ok -> when (val read = answer.value) {
                    is DateRead.Active -> {
                        // `left` back to false: a new date after one that went away is shown, not left.
                        _state.update { it.copy(date = read.date, now = now, loading = false, failed = null, left = false) }
                        reportVisit(read.date, now)
                    }
                    DateRead.None -> _state.update { it.copy(now = now, loading = false, left = true) }
                }
                is Outcome.Failed -> _state.update { it.copy(now = now, loading = false, failed = answer.why) }
            }
        }
    }

    private fun reportVisit(date: ActiveDate, now: Instant) {
        val visit = pendingVisit ?: return
        pendingVisit = null
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.DateScreen, visit.referrer))
        analytics?.report(DateAnalytics.dateScreenViewed(date, dateState(now, date.unlockAt, date.unread), now))
    }
}
