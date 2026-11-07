/*
 * ActiveDate.kt
 * ShowUp · the Date screen's data, in the app's own types (SHOWUP-193)
 *
 * Read from `GET /me/date` by [DateRepository]. The screen takes these and returns pixels; nothing
 * here knows about HTTP or the generated client.
 */
package com.showup.date

import com.showup.chat.ChatReason
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

/** The newest unread message from the match. [reason] is its chip, null for a message sent with none. */
data class DateUnread(val count: Int, val reason: ChatReason?, val body: String?, val sentAt: Instant)

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
    /** The viewer has opened the chat past its tutorial on this date (Date 02); false shows the tutorial. */
    val chatTutorialSeen: Boolean,
)

/**
 * A date ended in the chat after "I can't make it today" (Date 02) that the viewer has not seen yet:
 * the other person's first name and whether the viewer ended it. Shown once; after it is marked seen
 * the read is [DateRead.None].
 */
data class EndedInChat(val dateId: String, val name: String, val endedByYou: Boolean)

/**
 * What `GET /me/date` found: a date, an ended-in-chat outcome to show, or neither. A failed call is the
 * [com.showup.api.Outcome]'s.
 */
sealed interface DateRead {
    data class Active(val date: ActiveDate) : DateRead
    data class Ended(val outcome: EndedInChat) : DateRead
    data object None : DateRead
}
