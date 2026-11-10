/*
 * ArrivalCopy.kt
 * ShowUp · the arrival check-in's strings, exactly the ticket's (SHOWUP-195)
 *
 * `%s` is the match's first name. `audit/verify-arrival.py` compares these with the ticket.
 */
package com.showup.arrival

object ArrivalCopy {
    // A: the Check-in bar
    const val BAR_TITLE = "Your date with %s"
    const val BAR_LINE = "Confirm you arrived at the meeting location, we will let your date know."
    const val BAR_BUTTON = "Check-in"
    const val END_DATE = "End date"

    // B: the confirmation
    const val CONFIRM_TITLE = "Have you arrived at the meeting location for your date with %s?"
    const val NOT_YET = "Not yet"
    const val YES = "Yes, I've arrived"

    // C: the location could not be read
    const val LOC_ERROR = "We couldn't find your location. Check your signal, then try again."
    const val TRY_AGAIN = "Try again"

    // D: stored
    const val DONE_TITLE = "You are amazing for showing up!"
    const val DONE_BODY = "We will let %s know that you have arrived. Enjoy your date."
    const val CLOSE = "Close"
    const val UNDO = "Undo check-in"

    // H: the match has arrived
    const val ARRIVED_TAG = "Arrived"
    const val ARRIVED_TIME = "checked in · %s"
    const val ARRIVED_BODY = "%s has arrived at the meeting location."
    const val OPEN_CHAT = "Open chat"

    /** Built for Date 06's report flow, shown by no screen yet. */
    const val REPORT_NOTE = "Check in first, so we can confirm you were there."
}
