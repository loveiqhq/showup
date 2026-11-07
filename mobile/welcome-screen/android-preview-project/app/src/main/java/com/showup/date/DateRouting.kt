/*
 * DateRouting.kt
 * ShowUp · where a finished profile opens while a date is active (SHOWUP-193)
 *
 * "Signed in → profile complete → active date? → Date screen → otherwise the existing Check-In /
 * Search resume." The date wins over the Check-In gate because an active date blocks Search anyway
 * (the server's `dateLocksUser`). A date read that failed is no date: the launch must never open on
 * a guess, and the next foreground reads again.
 */
package com.showup.date

import com.showup.api.Outcome
import com.showup.welcome.FlowScreen

/** The launch target for a finished profile, from the date read and the Check-In's state. */
fun launchTarget(date: Outcome<DateRead>, checkInActive: Boolean?): FlowScreen = when {
    date.activeDate() != null -> FlowScreen.Date
    // A date ended in the chat and not yet seen (SHOWUP-194): the Date screen's read finds it and shows
    // G or H, which must be seen before Search -- the ending person's and the match's outcome alike.
    date.endedOutcome() != null -> FlowScreen.Date
    checkInActive == true -> FlowScreen.CheckInSearch
    else -> FlowScreen.CheckInGate
}

/** The date this read found, or null for none and for a failed read. */
fun Outcome<DateRead>.activeDate(): ActiveDate? = ((this as? Outcome.Ok)?.value as? DateRead.Active)?.date

/** The ended-in-chat outcome this read found and the viewer has not seen, or null. */
fun Outcome<DateRead>.endedOutcome(): EndedInChat? = ((this as? Outcome.Ok)?.value as? DateRead.Ended)?.outcome
