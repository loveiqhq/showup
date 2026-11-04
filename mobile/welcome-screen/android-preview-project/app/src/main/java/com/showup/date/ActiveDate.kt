/*
 * ActiveDate.kt
 * ShowUp · the Date screen's data, in the app's own types (SHOWUP-193)
 *
 * Read from `GET /me/date` by [DateRepository]. The screen takes these and returns pixels; nothing
 * here knows about HTTP or the generated client.
 */
package com.showup.date

import java.time.Instant

/** The agreed meeting place. [address] is null for a venue stored without one. */
data class DatePlace(val name: String, val address: String?)

/**
 * The person the viewer is meeting. A hidden age, an unknown district or distance and a missing
 * photo are null: no value, no text on the screen.
 */
data class DateMatch(
    val userId: String,
    val name: String,
    val age: Int?,
    val district: String?,
    /** Whole km rounded by the server; 0 is "< 1 km". */
    val distanceKm: Int?,
    /** A loadable URL. */
    val photoUrl: String?,
    val attendedCount: Int,
)

/** The newest unread message from the match. [reason] is the server's chat reason wire value. */
data class DateUnread(val count: Int, val reason: String, val body: String?, val sentAt: Instant)

/** Everything the Date screen renders. Time and place are the latest AGREED values. */
data class ActiveDate(
    val id: String,
    val meetAt: Instant,
    /** `today` or `tomorrow`, by the Check-In's 7am day, from the server -- as Search's. */
    val meetDay: String,
    /** meetAt − 1:30: when Coordinate opens. */
    val unlockAt: Instant,
    val coordinateOpen: Boolean,
    val place: DatePlace?,
    val match: DateMatch,
    val motivationYou: String?,
    val motivationThem: String?,
    /** §31 vibes both chose, in the match's order. */
    val sharedVibes: List<String>,
    val sharedInterests: List<String>,
    val unread: DateUnread?,
    /** The viewer's own arrival check-in. Always false until Date 03. */
    val checkedIn: Boolean,
)

/** What `GET /me/date` found: a date, or none. A failed call is the [com.showup.api.Outcome]'s. */
sealed interface DateRead {
    data class Active(val date: ActiveDate) : DateRead
    data object None : DateRead
}
