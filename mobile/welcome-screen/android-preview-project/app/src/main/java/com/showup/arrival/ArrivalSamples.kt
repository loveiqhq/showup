/*
 * ArrivalSamples.kt
 * ShowUp · SHOWUP-195's frames drawn still -- the gate's phases over the Date screen or the chat, the
 * arrival card, the date location gate -- with nothing wired. One copy for the previews
 * ([ArrivalPreviews]) and the debug States page, as [com.showup.date.DateSamples] and
 * [com.showup.chat.ChatSamples] are for theirs.
 */
package com.showup.arrival

import androidx.compose.runtime.Composable
import com.showup.chat.ChatSamples
import com.showup.chat.ChatScreen
import com.showup.date.ActiveDate
import com.showup.date.DateArrival
import com.showup.date.DateSamples
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.profile.LocationScreenContext
import com.showup.profile.LocationState
import com.showup.profile.ProfileLocationScreen
import java.time.Instant

object ArrivalSamples {
    private val name: String get() = DateSamples.DATE.match.name

    /** A: the Check-in bar in the Date screen's footer slot. */
    val bar: @Composable () -> Unit = { CheckInBar(name = DateSamples.DATE.match.name, onCheckIn = {}) }

    /** B: the guard, from the Check-in bar. */
    val B = GateUi(GatePhase.Confirm(ArrivalEntry.DateScreen, error = null, busy = false, note = null))

    /** B with the report note (Date 06's entry, built here). */
    val B_NOTE = GateUi(GatePhase.Confirm(ArrivalEntry.Report, error = null, busy = false, note = ArrivalCopy.REPORT_NOTE))

    /** B while the location is read: both buttons wait. */
    val B_BUSY = GateUi(GatePhase.Confirm(ArrivalEntry.DateScreen, error = null, busy = true, note = null))

    /** C: no location fix. */
    val C = GateUi(GatePhase.Confirm(ArrivalEntry.DateScreen, error = ConfirmError.NoFix, busy = false, note = null))

    /** C2: the location was read, the check-in did not save (16 Nov): C's box with the other string. */
    val C2 = GateUi(GatePhase.Confirm(ArrivalEntry.DateScreen, error = ConfirmError.NotSaved, busy = false, note = null))

    /** D: checked in, with Undo. */
    val D = GateUi(GatePhase.Done(ArrivalEntry.DateScreen))

    /** F: the guard from the chat's chip. */
    val F = GateUi(GatePhase.Confirm(ArrivalEntry.ChatChip, error = null, busy = false, note = null))

    /** E: the date once this person has checked in -- the footer slot empty until Date 06. */
    val CHECKED_IN: ActiveDate = DateSamples.DATE.copy(checkedIn = true)

    /** H: the match checked in two minutes ago. */
    val ARRIVED: ActiveDate = DateSamples.DATE.copy(arrival = DateArrival(DateSamples.B_NOW.minusSeconds(120)))

    /** The gate's phase drawn over [content], as the host does. */
    @Composable
    fun over(ui: GateUi, leave: () -> Unit = {}, content: @Composable () -> Unit) =
        ArrivalOver(ui, name, onYes = {}, onNotYet = leave, onClose = leave, onUndo = leave, content = content)

    /** The Date screen with the Check-in bar, at [now] (B_NOW: the chat open; A_NOW: still locked). */
    @Composable
    fun dateScreen(date: ActiveDate = DateSamples.DATE, now: Instant = DateSamples.B_NOW) =
        DateScreen(DateUi(date, now, loading = false), DateSamples.ZONE, checkInBar = bar)

    /** The chat's start grid, behind F. */
    @Composable
    fun chat(leave: () -> Unit = {}) =
        ChatScreen(ChatSamples.B, DateSamples.ZONE, leave, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

    /** G: the location gate over the Date screen, in [state], at [now] (the pill follows the meet time). */
    @Composable
    fun gate(state: LocationState, now: Instant = DateSamples.B_NOW, date: ActiveDate = DateSamples.DATE) =
        ProfileLocationScreen(
            state = state,
            context = LocationScreenContext.Date(
                name = date.match.name, meetDay = date.meetDay, meetAt = date.meetAt,
                place = date.place?.name, photoUrl = null, now = now, zone = DateSamples.ZONE, onTab = {},
            ),
        )
}
