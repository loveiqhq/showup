/*
 * CancelViewModel.kt
 * ShowUp · Date 07's guard sheet: open or not, the request sent from it, the cancel in flight (SHOWUP-198)
 *
 * The sheet stays a function from [CancelUi] to pixels; this owns what must outlive a redraw: the cancel is a
 * server request, and the sheet and the request sent from it survive process death.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE DATE IS NOT CANCELLED UNTIL THE SERVER CONFIRMS. A failure leaves the sheet up with [CancelUi.failed] set; the
 * button keeps its label and retries, and `Don't cancel my date` still works. [CancelUi.landed] is set only on the
 * server's answer, and the host lands the canceller with `landAfterEnd`. Both buttons are inert while it runs.
 *
 * A CANCEL AFTER A REQUEST IS STILL A CANCEL. The request does not soften the penalty; it only sets the session's
 * `change_requested`, which `cancel_abandoned_at_guard` reports.
 *
 * TRACKING. `cancel_guard_shown` fires on every open; `cancel_abandoned_at_guard` once per close without a cancel
 * (keep, the scrim or a swipe, Message instead). Opening a picker is not an exit. A close the server forced (a
 * check-in on another device, an already-cancelled date, a switch turned off) is no exit either: the person did
 * not choose it. `date_cancelled` is the server's and is never reported from here.
 *
 * A 409 IS NOT E. `checked_in` closes the sheet and the date is read again (the link goes); `date_not_active`
 * closes it and reads again, which routes the person to H as the cancelled-on one. A 404 (the switch is off)
 * closes it too.
 *
 * THE SHEET AND THE SENT VALUE SURVIVE PROCESS DEATH in the [SavedStateHandle], and are re-validated against the
 * date read: another date, a check-in, or the switch off closes it. A restore is not an open: no second
 * `cancel_guard_shown`.
 */
package com.showup.date.cancel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.change.ChangeKind
import com.showup.change.changeKindAvailable
import com.showup.date.ActiveDate
import com.showup.date.CancelResult
import com.showup.date.CancelStore
import com.showup.date.EndedOutcome
import com.showup.date.REFUSED_NOT_FOUND
import com.showup.date.minutesBeforeStart
import com.showup.profile.report
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** What D shows: the request that was sent from the sheet, as its bold value. */
data class SentRequest(val kind: ChangeKind, val label: String)

/** Everything the sheet renders, and the one-shot asks the host acts on. */
data class CancelUi(
    val open: Boolean = false,
    /** D: a picker sent a request from this sheet. */
    val sent: SentRequest? = null,
    /** A request was sent from this sheet in this session: `cancel_abandoned_at_guard.change_requested`. */
    val changeRequested: Boolean = false,
    /** The cancel is in flight: both buttons are inert. */
    val cancelling: Boolean = false,
    /** E: the last cancel got no server OK. */
    val failed: Boolean = false,
    /** A tile was tapped: the host opens Date 05's picker of this kind over the sheet (then [CancelViewModel.consumePicker]). */
    val openPicker: ChangeKind? = null,
    /** Message instead was tapped: the host opens the chat (then [CancelViewModel.consumeOpenChat]). */
    val openChat: Boolean = false,
    /** The server confirmed the cancel: the host lands the canceller (then [CancelViewModel.consumeLanded]). */
    val landed: EndedOutcome? = null,
    val date: ActiveDate? = null,
    val now: Instant = Instant.EPOCH,
) {
    /** I: Date 05's cutoff or a check-in has passed, evaluated with the screen's clock. */
    val changeClosed: Boolean get() = changeClosed(date?.change, now)
    val chatOpen: Boolean get() = date?.coordinateOpen == true
    val showMessage: Boolean get() = showMessage(chatOpen, changeClosed)

    /** The cost box's `{drop}`: the rate config's value, never a literal. */
    val drop: Int get() = date?.cancelPenaltyPoints ?: 0
}

private const val KEY_OPEN = "open"
private const val KEY_SENT_KIND = "sent_kind"
private const val KEY_SENT_LABEL = "sent_label"
private const val KEY_REQUESTED = "change_requested"
private const val KEY_DATE = "date_id"

class CancelViewModel(
    private val store: CancelStore,
    private val analytics: AnalyticsTracker? = null,
    private val saved: SavedStateHandle = SavedStateHandle(),
    /** The date must be read again: a 409 says the screen is out of date. */
    private val reread: () -> Unit = {},
) : ViewModel() {

    private val _ui = MutableStateFlow(restored())
    val ui: StateFlow<CancelUi> = _ui.asStateFlow()

    private var boundId: String? = null
    private val savedDateId: String? = saved.get<String>(KEY_DATE)

    private fun restored(): CancelUi {
        if (saved.get<Boolean>(KEY_OPEN) != true) return CancelUi()
        val kind = ChangeKind.entries.firstOrNull { it.name == saved.get<String>(KEY_SENT_KIND) }
        return CancelUi(
            open = true,
            sent = kind?.let { SentRequest(it, saved.get<String>(KEY_SENT_LABEL).orEmpty()) },
            changeRequested = saved.get<Boolean>(KEY_REQUESTED) == true,
        )
    }

    /** What must outlive the process: the open sheet and what was sent from it. A closed sheet leaves nothing. */
    private fun persist() {
        val u = _ui.value
        write(KEY_OPEN, u.open.takeIf { it })
        write(KEY_SENT_KIND, u.sent?.takeIf { u.open }?.kind?.name)
        write(KEY_SENT_LABEL, u.sent?.takeIf { u.open }?.label)
        write(KEY_REQUESTED, u.changeRequested.takeIf { u.open })
        write(KEY_DATE, boundId.takeIf { u.open })
    }

    /** `bind` runs on every clock tick: a value that has not changed is not written again. */
    private fun write(key: String, value: Any?) {
        if (saved.get<Any>(key) != value) saved[key] = value
    }

    private fun change(block: (CancelUi) -> CancelUi) {
        _ui.update(block)
        persist()
    }

    private fun closed(u: CancelUi) = u.copy(open = false, sent = null, changeRequested = false, cancelling = false, failed = false, openPicker = null)

    /** Every date read and every clock tick goes through here: another date resets everything. */
    fun bind(date: ActiveDate, now: Instant) {
        val previous = boundId
        val other = if (previous != null) previous != date.id else _ui.value.open && savedDateId != date.id
        if (other) _ui.value = CancelUi()
        boundId = date.id
        change { it.copy(date = date, now = now) }
        // The link is gone and the route may be off: a sheet over a date that no longer allows it closes, silently.
        val u = _ui.value
        if (u.open && (!date.endsEnabled || date.checkedIn)) change(::closed)
    }

    /**
     * The other person ended this date (Date 08): the guard closes and its saved state goes with it, so it cannot
     * restore after a process death. Not a dismissal: `cancel_abandoned_at_guard` is for the person's own exit.
     */
    fun discardForPartnerEnd() {
        if (!_ui.value.open) return
        change(::closed)
    }

    /** The red link: B or C (the sheet reads the chat from the date). */
    fun open() {
        val u = _ui.value
        val date = u.date ?: return
        if (u.open || !date.endsEnabled || date.checkedIn) return
        change { closed(it).copy(open = true) }
        analytics?.report(CancelAnalytics.guardShown(date.id, u.chatOpen, date.cancelPenaltyPoints, minutesBeforeStart(date.meetAt, u.now)))
    }

    private fun exit(how: CancelExit) {
        val u = _ui.value
        val date = u.date ?: return
        if (!u.open || u.cancelling) return
        change { closed(it) }
        analytics?.report(CancelAnalytics.abandoned(date.id, how, u.changeRequested))
    }

    /** `Don't cancel my date`. Nothing is stored. */
    fun keep() = exit(CancelExit.Keep)

    /** The scrim or a swipe-down. */
    fun dismiss() = exit(CancelExit.Dismissed)

    /** `Message {name} instead`: the flow closes and the host opens the chat. Nothing is cancelled. */
    fun messageInstead() {
        val u = _ui.value
        if (!u.open || u.cancelling || !u.showMessage) return
        exit(CancelExit.Message)
        change { it.copy(openChat = true) }
    }

    /** A tile: Date 05's picker over the sheet. Not an exit. A tile Date 05 hides asks for nothing. */
    fun picker(kind: ChangeKind) {
        val u = _ui.value
        if (!u.open || u.cancelling) return
        if (!changeKindAvailable(u.date?.change, kind, u.now)) return
        change { it.copy(openPicker = kind) }
    }

    /** A picker sent from this sheet: D replaces the tiles and the sheet stays open. */
    fun pickerSent(kind: ChangeKind, label: String) {
        if (!_ui.value.open) return
        change { it.copy(sent = SentRequest(kind, label), changeRequested = true) }
    }

    /** `Cancel anyway and let her down`, and E's retry. Inert while one is in flight. */
    fun cancel() {
        val u = _ui.value
        val date = u.date ?: return
        if (!u.open || u.cancelling) return
        // `failed` stays set while a retry is in flight so the box does not leave and come back; it goes with the
        // success (the sheet closes) and a second failure keeps it.
        change { it.copy(cancelling = true) }
        viewModelScope.launch {
            when (val r = store.cancel(date.id)) {
                is CancelResult.Cancelled -> change { closed(it).copy(landed = r.outcome) }
                is CancelResult.Refused -> refused(r.code)
                is CancelResult.Failed -> change { it.copy(cancelling = false, failed = true) }
            }
        }
    }

    private fun refused(code: String) {
        when (code) {
            "checked_in", "date_not_active", REFUSED_NOT_FOUND -> {
                change(::closed)
                reread()
            }
            // Unknown: the person cannot tell it from a failure, and a retry is the honest answer.
            else -> change { it.copy(cancelling = false, failed = true) }
        }
    }

    fun consumePicker() = change { it.copy(openPicker = null) }

    fun consumeOpenChat() = change { it.copy(openChat = false) }

    fun consumeLanded() = change { it.copy(landed = null) }
}
