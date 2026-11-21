/*
 * DateEndViewModel.kt
 * ShowUp · Date 06's sheets: which is up, its step, the gate it waits on, the end in flight (SHOWUP-197)
 *
 * The sheets stay functions from [DateEndUi] to pixels; this owns what must outlive a redraw: the end is a
 * server request, the discomfort flag is one, and the gate's detour must remember where it was going.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE RULES THIS FILE ENFORCES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE DATE IS NOT ENDED UNTIL THE SERVER CONFIRMS. An end that fails (J) leaves the sheet on the same step with
 * [DateEndUi.failed] set; the CTA keeps its label and retries. [DateEndUi.landed] is set only on the server's
 * answer, and the host leaves the Date screen on it (`landAfterEnd`).
 *
 * THE GATE CONTINUES TO THE PICKED STEP (R9). The two problem routes need a stored check-in. Not checked in, the
 * sheet hides and [DateEndUi.openGate] asks the host for Date 03's gate with the note; this model, not the gate,
 * remembers the step ([DateEndUi.gated]): Close (commit) reopens the sheet there, Not yet reopens the menu, Undo
 * closes everything. `We met` needs no check-in.
 *
 * TRACKING FIRES ON THE TAP. The outcome or category goes out before the gate or the confirm step. Cancel, the
 * scrim, Back, `Message {name}` and a tap on the disabled end button fire nothing. `date_ended` and
 * `problem_stay_flagged` are the server's and are never reported from here.
 *
 * A 409 IS NOT J. `date_not_active` closes the sheet and the date is read again (the end already happened, the
 * read routes on it); `before_meet` returns to the guide and `not_checked_in` to the menu, each with a re-read.
 * A 404 (the switch is off) closes the sheet.
 *
 * THE SHEET, ITS STEP AND THE GATED STEP SURVIVE PROCESS DEATH in the [SavedStateHandle] (R14), and are
 * re-validated against the date read: a step that needs a check-in goes back to the menu without one, the
 * no-show confirm back to the guide before the meet time, and a gate that died with the process becomes the
 * step it was heading for if the check-in landed, the menu if not.
 */
package com.showup.date.end

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.arrival.ArrivalEntry
import com.showup.arrival.minutesVsStart
import com.showup.date.ActiveDate
import com.showup.date.DateEndStore
import com.showup.date.EndDateResult
import com.showup.date.EndEntry
import com.showup.date.EndedOutcome
import com.showup.date.REFUSED_NOT_FOUND
import com.showup.profile.report
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Everything the sheets render, and the one-shot asks the host acts on. */
data class DateEndUi(
    /** The sheet that is open; null is closed. */
    val route: EndRoute? = null,
    val step: EndStep = EndStep.Menu,
    /** Where B was opened (the bar or the link); H's end is always `report`. */
    val entry: EndEntry = EndEntry.EndDateBar,
    /** The step the gate is serving: set while Date 03's sheets are up, the sheet hidden. */
    val gated: EndStep? = null,
    /** Ask the host to open the gate with this entry (then [DateEndViewModel.consumeGate]). */
    val openGate: ArrivalEntry? = null,
    /** An end is in flight: both buttons are inert. */
    val ending: Boolean = false,
    /** J: the last end got no server OK. */
    val failed: Boolean = false,
    /** `Message {name}` was tapped: the host opens the chat (then [DateEndViewModel.consumeOpenChat]). */
    val openChat: Boolean = false,
    /** The server confirmed an end: the host lands (then [DateEndViewModel.consumeLanded]). */
    val landed: EndedOutcome? = null,
    val date: ActiveDate? = null,
    val now: Instant = Instant.EPOCH,
    val checkedIn: Boolean = false,
) {
    /** The sheet is on screen: open, and not hidden behind the gate. */
    val visible: Boolean get() = route != null && gated == null
    val pastMeet: Boolean get() = date?.let { pastMeet(it.meetAt, now) } ?: false
    val chatOpen: Boolean get() = date?.coordinateOpen == true
}

private const val KEY_ROUTE = "route"
private const val KEY_STEP = "step"
private const val KEY_ENTRY = "entry"
private const val KEY_GATED = "gated"
private const val KEY_DATE = "date_id"

class DateEndViewModel(
    private val store: DateEndStore,
    private val analytics: AnalyticsTracker? = null,
    private val saved: SavedStateHandle = SavedStateHandle(),
    /** The date must be read again: a 409 says the screen is out of date. */
    private val reread: () -> Unit = {},
    /** Where Stay for now runs, so its flag outlives this model; null is [viewModelScope]. */
    work: CoroutineScope? = null,
) : ViewModel() {

    private val workScope: CoroutineScope = work ?: viewModelScope

    private val _ui = MutableStateFlow(restored())
    val ui: StateFlow<DateEndUi> = _ui.asStateFlow()

    /** The date this model has been bound to, and the one a restored sheet belongs to. */
    private var boundId: String? = null
    private val savedDateId: String? = saved.get<String>(KEY_DATE)

    /** Restored and not yet checked against a date read: the first [bind] validates it. */
    private var unvalidated = _ui.value.route != null

    /** This session committed a check-in: counts as checked in before the date read says so. */
    private var committed = false

    private fun restored(): DateEndUi {
        val route = EndRoute.entries.firstOrNull { it.name == saved.get<String>(KEY_ROUTE) } ?: return DateEndUi()
        return DateEndUi(
            route = route,
            step = EndStep.entries.firstOrNull { it.name == saved.get<String>(KEY_STEP) } ?: EndStep.Menu,
            entry = EndEntry.entries.firstOrNull { it.name == saved.get<String>(KEY_ENTRY) } ?: EndEntry.EndDateBar,
            gated = EndStep.entries.firstOrNull { it.name == saved.get<String>(KEY_GATED) },
        )
    }

    /** What must outlive the process: the open sheet, its step and the gated step. A closed sheet leaves nothing. */
    private fun persist() {
        val u = _ui.value
        saved[KEY_ROUTE] = u.route?.name
        saved[KEY_STEP] = u.route?.let { u.step.name }
        saved[KEY_ENTRY] = u.route?.let { u.entry.name }
        saved[KEY_GATED] = u.gated?.name
        saved[KEY_DATE] = u.route?.let { boundId }
    }

    private fun change(block: (DateEndUi) -> DateEndUi) {
        _ui.update(block)
        persist()
    }

    /** Every date read, and every clock tick, goes through here: another date resets everything. */
    fun bind(date: ActiveDate, now: Instant) {
        val previous = boundId
        val other = if (previous != null) previous != date.id else _ui.value.route != null && savedDateId != date.id
        if (other) {
            committed = false
            unvalidated = false
            _ui.value = DateEndUi()
        }
        boundId = date.id
        val checkedIn = date.checkedIn || committed
        change { it.copy(date = date, now = now, checkedIn = checkedIn) }
        validate()
    }

    /** R14: a step the date no longer supports goes back to where it is allowed. */
    private fun validate() {
        val u = _ui.value
        if (u.route == null) return
        val date = u.date ?: return
        if (!date.endsEnabled) {
            change { it.copy(route = null, step = EndStep.Menu, gated = null, openGate = null, failed = false) }
            unvalidated = false
            return
        }
        if (unvalidated) {
            // A gate that died with the process: the check-in either landed or it did not.
            unvalidated = false
            val gated = u.gated
            if (gated != null) {
                change { it.copy(gated = null, step = if (it.checkedIn) gated else EndStep.Menu) }
            }
        }
        val now = _ui.value
        if (now.gated != null) return
        val needsCheckIn = now.step == EndStep.Guide || now.step == EndStep.NoShow || now.step == EndStep.Uncomfortable
        when {
            needsCheckIn && !now.checkedIn -> change { it.copy(step = EndStep.Menu, failed = false) }
            now.step == EndStep.NoShow && !now.pastMeet -> change { it.copy(step = EndStep.Guide, failed = false) }
        }
    }

    /** The End Date bar or the Check-in bar's link: B. */
    fun openWrap(entry: EndEntry) {
        val u = _ui.value
        val date = u.date ?: return
        if (u.route != null || !date.endsEnabled) return
        change { it.copy(route = EndRoute.Wrap, step = EndStep.Menu, entry = entry, failed = false) }
        analytics?.report(DateEndAnalytics.wrapupOpened(date.id, entry, u.checkedIn, minutesVsStart(date.meetAt, u.now)))
    }

    /** The Report card: H. */
    fun openReport() {
        val u = _ui.value
        val date = u.date ?: return
        if (u.route != null || !date.endsEnabled) return
        change { it.copy(route = EndRoute.Report, step = EndStep.Menu, failed = false) }
        analytics?.report(DateEndAnalytics.problemReportOpened(date.id, u.checkedIn, minutesVsStart(date.meetAt, u.now)))
    }

    /**
     * A menu row. The event goes out on the tap, before the gate; then the step, or the gate when the route needs
     * a check-in this person has not made.
     */
    fun pick(option: EndOption) {
        val u = _ui.value
        val date = u.date ?: return
        val route = u.route ?: return
        if (!u.visible || u.step != EndStep.Menu || u.ending) return
        val belongs = when (route) {
            EndRoute.Wrap -> option != EndOption.Uncomfortable
            EndRoute.Report -> option != EndOption.Met
        }
        if (!belongs) return
        analytics?.report(
            when (route) {
                EndRoute.Wrap -> DateEndAnalytics.wrapupOutcomeSelected(date.id, option, u.checkedIn)
                EndRoute.Report -> DateEndAnalytics.problemCategorySelected(date.id, option, u.checkedIn)
            },
        )
        val target = endStepAfterGate(option)
        if (optionNeedsCheckIn(option) && !u.checkedIn) {
            change { it.copy(gated = target, openGate = gateEntryFor(route)) }
        } else {
            change { it.copy(step = target, failed = false) }
        }
    }

    /** The guide's end button: G, from the meet time. Before it the button is disabled and this is a no-op. */
    fun confirmWait() {
        val u = _ui.value
        if (!u.visible || u.step != EndStep.Guide || u.ending || !u.pastMeet) return
        change { it.copy(step = EndStep.NoShow, failed = false) }
    }

    /** `Message {name}`: the sheet closes and the host opens the chat. Hidden while the chat is locked. */
    fun message() {
        val u = _ui.value
        if (!u.visible || u.step != EndStep.Guide || u.ending || !messageShown(u.chatOpen)) return
        change { it.copy(route = null, step = EndStep.Menu, failed = false, openChat = true) }
    }

    /** `Back`, Cancel on C, G's `Back` and the scrim on G, E / F and I: one step up. On a menu it is [close]. */
    fun back() {
        val u = _ui.value
        if (u.route == null || u.ending) return
        when (u.step) {
            EndStep.Menu -> close()
            EndStep.NoShow -> change { it.copy(step = EndStep.Guide, failed = false) }
            EndStep.Met, EndStep.Guide, EndStep.Uncomfortable -> change { it.copy(step = EndStep.Menu, failed = false) }
        }
    }

    /** `Cancel`, the scrim or a swipe-down on B or H. Nothing is stored. Inert while an end is in flight. */
    fun close() {
        val u = _ui.value
        if (u.route == null || u.ending) return
        change { it.copy(route = null, step = EndStep.Menu, gated = null, openGate = null, failed = false) }
    }

    /** The CTA of C, G or I: end the date. Inert while one is already in flight. */
    fun end() {
        val u = _ui.value
        val date = u.date ?: return
        val route = u.route ?: return
        if (!u.visible || u.ending) return
        val reason = endReasonFor(u.step) ?: return
        val entry = if (route == EndRoute.Report) EndEntry.Report else u.entry
        // `failed` stays set while a retry is in flight: the box above the CTA does not leave and come back, and the
        // layout does not jump. It goes with the success (the sheet closes) and a second failure keeps it.
        change { it.copy(ending = true) }
        viewModelScope.launch {
            when (val r = store.end(date.id, reason, entry)) {
                is EndDateResult.Ended -> {
                    change { it.copy(route = null, step = EndStep.Menu, gated = null, ending = false, failed = false, landed = r.outcome) }
                }
                is EndDateResult.Refused -> refused(r.code)
                is EndDateResult.Failed -> change { it.copy(ending = false, failed = true) }
            }
        }
    }

    private fun refused(code: String) {
        when (code) {
            "before_meet" -> {
                change { it.copy(ending = false, failed = false, step = EndStep.Guide) }
                reread()
            }
            "not_checked_in" -> {
                committed = false
                change { it.copy(ending = false, failed = false, step = EndStep.Menu, checkedIn = false) }
                reread()
            }
            "date_not_active", REFUSED_NOT_FOUND -> {
                change { it.copy(route = null, step = EndStep.Menu, gated = null, ending = false, failed = false) }
                reread()
            }
            // Unknown: the person cannot tell it from a failure, and a retry is the honest answer.
            else -> change { it.copy(ending = false, failed = true) }
        }
    }

    /** `Stay for now` on I: an internal flag, one more each tap; the sheet closes and the date continues. */
    fun stay() {
        val u = _ui.value
        val date = u.date ?: return
        if (!u.visible || u.step != EndStep.Uncomfortable || u.ending) return
        change { it.copy(route = null, step = EndStep.Menu, failed = false) }
        // Not awaited: the person is not kept waiting on evidence, and a failed flag shows nothing (the ticket).
        workScope.launch { store.flagDiscomfort(date.id) }
    }

    // ── the gate (R9) ────────────────────────────────────────────────────────────────────────

    /** The host has opened the gate. */
    fun consumeGate() = change { it.copy(openGate = null) }

    /** Close on the done sheet (or the app leaving it): reopen the sheet at the step the person picked. */
    fun gateCommitted() {
        val gated = _ui.value.gated ?: return
        committed = true
        change { it.copy(gated = null, openGate = null, step = gated, checkedIn = true) }
    }

    /** Not yet: back to the menu the route came from. */
    fun gateNotYet() {
        if (_ui.value.gated == null) return
        change { it.copy(gated = null, openGate = null, step = EndStep.Menu) }
    }

    /** Undo: everything closes and the Check-in bar is back (Date 03). */
    fun gateUndone() {
        if (_ui.value.gated == null) return
        committed = false
        change { it.copy(route = null, step = EndStep.Menu, gated = null, openGate = null, checkedIn = false) }
    }

    fun consumeOpenChat() = change { it.copy(openChat = false) }

    fun consumeLanded() = change { it.copy(landed = null) }
}
