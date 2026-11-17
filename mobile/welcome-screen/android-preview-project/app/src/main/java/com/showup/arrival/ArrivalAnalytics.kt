/*
 * ArrivalAnalytics.kt
 * ShowUp · the arrival check-in's client events (SHOWUP-195; family M, registry 1.4.34)
 *
 * NOT FROM HERE: `arrival_confirmed` and `arrival_undone` (the server's), `screen_viewed` for B, C or D,
 * and anything carrying a coordinate, an accuracy, a distance or a place.
 */
package com.showup.arrival

import com.showup.profile.Stamp

object ArrivalAnalytics {
    const val ARRIVAL_CONFIRM_SHOWN = "arrival_confirm_shown"
    const val ARRIVAL_CONFIRM_SUBMITTED = "arrival_confirm_submitted"

    /** B is shown (Idle to Confirm only). */
    fun confirmShown(dateId: String, entry: ArrivalEntry, minutesVsStart: Int) =
        ARRIVAL_CONFIRM_SHOWN to payload(dateId, entry, minutesVsStart)

    /** The primary button tapped: `Yes, I've arrived` or `Try again`. */
    fun confirmSubmitted(dateId: String, entry: ArrivalEntry, minutesVsStart: Int) =
        ARRIVAL_CONFIRM_SUBMITTED to payload(dateId, entry, minutesVsStart)

    private fun payload(dateId: String, entry: ArrivalEntry, minutesVsStart: Int) = buildMap<String, Any> {
        put("date_id", dateId)
        put("entry", entry.wire)
        put("minutes_vs_start", minutesVsStart)
        putAll(Stamp.of(0))
    }
}
