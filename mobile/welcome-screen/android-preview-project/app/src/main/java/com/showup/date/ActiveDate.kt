/*
 * ActiveDate.kt
 * ShowUp · the Date screen's data, in the app's own types (SHOWUP-193)
 *
 * Read from `GET /me/date` by [DateRepository]. The screen takes these and returns pixels; nothing
 * here knows about HTTP or the generated client.
 */
package com.showup.date

import com.showup.change.ChangeSuggestion
import com.showup.change.DateChange
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

/** The match's committed arrival (SHOWUP-195): shown until the viewer opens the chat or taps the card. */
data class DateArrival(val checkedInAt: Instant)

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
    /** The viewer has a committed arrival check-in (Date 03). */
    val checkedIn: Boolean,
    /** The viewer has opened the chat past its tutorial on this date (Date 02); false shows the tutorial. */
    val chatTutorialSeen: Boolean,
    /** The match's committed check-in the viewer has not seen; null once seen (Date 03). */
    val arrival: DateArrival? = null,
    /**
     * Date 05: the cutoff, the later slots and the open suggestions. Null from a server before Date 05, which
     * shows no change tile or chip (the one gate, `ChangeRules`).
     */
    val change: DateChange? = null,
    /** Date 05: the match's open suggestion with the earliest deadline, for the hoisted request card. */
    val changeRequest: ChangeSuggestion? = null,
    /** Date 06 / 07: the end and cancel routes are on (the server's `DATE_ENDS_ENABLED`). False renders no entry for either. */
    val endsEnabled: Boolean = false,
    /** Date 07: the Show-up Rate points a cancel costs, as the server's rate config says; never a number written in the app. */
    val cancelPenaltyPoints: Int = 0,
)

/**
 * A date that ended -- in the chat (Date 02), by a cancel (Date 07) or by an end (Date 06) -- that the viewer
 * has not seen yet: the other person's first name and whether the viewer ended it. Shown once; after it is
 * marked seen the read is [DateRead.None]. [reason] says how it ended; [partnerScreen] is set only for the
 * match of a Date 06 end.
 */
data class EndedOutcome(
    val dateId: String,
    val name: String,
    val endedByYou: Boolean,
    /** The match's first photo, as the server sent it: G or H drawn with no date held, at the next launch. */
    val photoUrl: String? = null,
    val reason: OutcomeReason = OutcomeReason.Chat,
    val partnerScreen: PartnerScreen? = null,
    /** The latest agreed meet time, for Date 09's `{time}`; null from a server that does not send it. */
    val meetAt: Instant? = null,
)

/** How an ended date ended, the server's `reason`. */
enum class OutcomeReason(val wire: String) {
    Chat("chat"),
    Cancelled("cancelled"),
    Met("met"),
    PartnerNoShow("partner_no_show"),
    Uncomfortable("uncomfortable"),

    /** The +2 h switch closed the date; nobody ended it (Date 09). */
    TimedOut("timed_out");

    companion object {
        /** A payload with no reason is an older server's, from when the chat was the only way a date ended. */
        fun fromWire(wire: String?): OutcomeReason = entries.firstOrNull { it.wire == wire } ?: Chat
    }
}

/** Which screen the MATCH of a Date 06 end sees (Date 08), as the server picked it. */
enum class PartnerScreen(val wire: String) {
    DateClosedPositive("date_closed_positive"),
    YouNoShowed("you_no_showed"),
    DateEndedDiscomfort("date_ended_discomfort"),
    DateTimedOut("date_timed_out");

    companion object {
        fun fromWire(wire: String?): PartnerScreen? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * What `GET /me/date` found: a date, an ended-in-chat outcome to show, or neither. A failed call is the
 * [com.showup.api.Outcome]'s.
 */
sealed interface DateRead {
    data class Active(val date: ActiveDate) : DateRead
    data class Ended(val outcome: EndedOutcome) : DateRead
    data object None : DateRead
}
