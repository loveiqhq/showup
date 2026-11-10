/*
 * DateCopy.kt
 * ShowUp · every string the Date screen draws (SHOWUP-193)
 *
 * From the ticket's "Copy — final strings", verbatim; `audit/verify-date.py` quotes the ticket at
 * run time against this file and its Swift twin. `%s` is filled by [DateRules] / the screen.
 *
 * No footer bar strings: the bars are Date 03's and Date 06's, which write them (9 Nov 2026).
 */
package com.showup.date

object DateCopy {
    const val HERO = "It's a date."

    // ── time + place ────────────────────────────────────────────────────────
    const val MEETING_LEAD = "You and %s are meeting "
    const val MEETING_TIME = "Meeting time:"
    const val MEETING_PLACE = "Meeting place:"
    const val COPY = "Copy"
    const val COPIED = "Copied"

    // ── Coordinate ──────────────────────────────────────────────────────────
    const val COORD_TITLE = "Coordinate with %s"
    const val COORD_LOCKED_SUB = "Sort last-minute details before you meet"
    const val COORD_LOCKED_LINE = "Messaging available from %s"
    const val COORD_LOCKED_LINE_TOMORROW = "Messaging available tomorrow from %s"

    /** The locked card's change line (5 Nov 2026), over the two tiles; shown with them, once Date 05 ships. */
    const val COORD_CHANGE_LINE = "Request to change the location or meet time."
    const val COORD_OPEN_SUB = "Running late / Can't find your date at the spot? Tell %s here!"
    const val OPEN_CHAT = "Open chat"
    const val UNREAD_ONE = "New message"
    const val UNREAD_MANY = "%d new"
    const val NEEDS_RESPONSE = "needs a response · %s"
    const val NOW = "now"
    const val READ_AND_REPLY = "Read & reply"

    // ── the rest of the column ──────────────────────────────────────────────
    const val REPORT_TITLE = "Report a problem"
    const val REPORT_BODY = "If you feel unsafe or uncomfortable, let us know."
    const val MUTUAL_INTENT = "Mutual intent"
    const val THEIR_SHOW_UP = "%s's Show-up"
    const val YOU_BOTH_WANT = "You both want"
    const val DIRECTIONS = "Directions"
    const val SHARE_DATE = "Share Date"
    const val CALENDAR = "Calendar"
    const val NAME_AGE = "%s, %d"
    const val SEE_PROFILE = "See %s's full profile"
    const val IN_COMMON = "What you have in common"
    const val CANCEL_DATE = "Cancel this date"
    const val TAB_HINT = "Available again once your date is finished."

    // ── How the date works ──────────────────────────────────────────────────
    const val WORKS_TITLE = "How the date works"
    const val WORKS_MEET = "Meet at %s at %s"
    const val WORKS_MEET_NO_PLACE = "Meet at %s"
    const val WORKS_MEET_SUB = "A public place — a café, bar or restaurant."
    const val WORKS_CHECK_IN = "Check in when you arrive"
    const val WORKS_CHECK_IN_SUB = "Tap Check-in at the spot — we let %s know you're there."
    const val WORKS_THIRTY = "30 minutes, no pressure"
    const val WORKS_THIRTY_SUB = "Stay if you're vibing, leave with a smile."
    const val WORKS_END = "End the date in the app"
    const val WORKS_END_SUB = "Tap End date when you’re done — then a short review."

    // ── the OS hand-offs ────────────────────────────────────────────────────
    const val SHARE_TEXT = "I'm meeting %s %s at %s at %s."
    const val SHARE_TEXT_NO_PLACE = "I'm meeting %s %s at %s."
    const val CALENDAR_TITLE = "Date with %s"

    // ── 9 Nov 2026 ──────────────────────────────────────────────────────────
    /** The first read failed, with nothing to show; beside Search's `Try again`. */
    const val LOAD_FAILED = "Couldn't load your date. Check your connection and try again."

    /** The read-only profile's back pill. */
    const val PROFILE_BACK = "Your date"
}
