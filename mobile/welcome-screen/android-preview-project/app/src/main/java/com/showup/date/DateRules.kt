/*
 * DateRules.kt
 * ShowUp · the Date screen's rules, as plain functions (SHOWUP-193)
 *
 * Which state the screen is in, the minutes every event carries, and every string built from
 * values. No view, so each is tested without one (`DateRulesTest`); Swift has the same functions
 * under the same names.
 */
package com.showup.date

import com.showup.search.interestLabel
import com.showup.search.meetTime
import com.showup.search.motivationOf
import com.showup.search.searchDistance
import com.showup.welcome.BrandIcon
import java.time.Instant
import java.time.ZoneId

/**
 * The three time states. D, the full scroll, is not a state -- it is the same screen, scrolled.
 *
 * UNREAD ONLY ONCE OPEN. A message cannot arrive before the chat opens, and if one somehow had, the
 * locked card has nothing to hoist: it is not tappable and has no reply.
 */
enum class DateState { Locked, Available, Unread }

/**
 * [coordinateOpen] is the server's word at the last read: the device clock flips the card at
 * [unlockAt], and the foreground re-read corrects a clock that is behind (spec section 3).
 */
fun dateState(now: Instant, unlockAt: Instant, unread: DateUnread?, coordinateOpen: Boolean = false): DateState = when {
    !coordinateOpen && now < unlockAt -> DateState.Locked
    unread != null -> DateState.Unread
    else -> DateState.Available
}

/**
 * Whole minutes from [now] to the latest agreed meet time, negative after it, rounded toward zero
 * -- the server's `minutesBeforeStart`, so the client's and the server's events agree.
 */
fun minutesBeforeStart(meetAt: Instant, now: Instant): Int {
    val seconds = meetAt.epochSecond - now.epochSecond
    return (seconds / 60).toInt()
}

/**
 * `Messaging available from 17:30`, or `… tomorrow from …` "if the date is tomorrow" -- by [meetDay],
 * the date's own day that the headline shows, so the two never disagree.
 */
fun lockedLine(meetDay: String, unlockAt: Instant, zone: ZoneId): String {
    val template = if (meetDay == "tomorrow") DateCopy.COORD_LOCKED_LINE_TOMORROW else DateCopy.COORD_LOCKED_LINE
    return template.format(meetTime(unlockAt, zone))
}

/** `{place}, {address}` -- what Copy writes, and where Calendar says the event is. */
fun placeText(place: DatePlace): String =
    place.address?.let { "${place.name}, $it" } ?: place.name

/** The Share Date sentence. Without a place it ends at the time. */
fun shareText(date: ActiveDate, zone: ZoneId): String {
    val time = meetTime(date.meetAt, zone)
    return date.place?.let {
        DateCopy.SHARE_TEXT.format(date.match.name, date.meetDay, time, placeText(it))
    } ?: DateCopy.SHARE_TEXT_NO_PLACE.format(date.match.name, date.meetDay, time)
}

/** What the OS calendar sheet is pre-filled with. One hour, as the ticket says. */
data class CalendarEvent(val title: String, val start: Instant, val end: Instant, val location: String?)

fun calendarEvent(date: ActiveDate): CalendarEvent = CalendarEvent(
    title = DateCopy.CALENDAR_TITLE.format(date.match.name),
    start = date.meetAt,
    end = date.meetAt.plusSeconds(3600),
    location = date.place?.let(::placeText),
)

/** The unread card's `{time}`: `now` within the minute, then the clock time it was sent. */
fun unreadTime(sentAt: Instant, now: Instant, zone: ZoneId): String =
    if (now.epochSecond - sentAt.epochSecond < 60) DateCopy.NOW else meetTime(sentAt, zone)

/** `New message` for one, `{n} new` for more. */
fun unreadTag(count: Int): String = if (count == 1) DateCopy.UNREAD_ONE else DateCopy.UNREAD_MANY.format(count)

/**
 * About's line: `{area} · {distance} away`. The ticket's `{job}` has no source -- no profile question
 * asks for one -- so the line starts at the area; whatever is missing is left out, never a placeholder.
 */
fun aboutLine(district: String?, distanceKm: Int?): String? =
    listOfNotNull(district, distanceKm?.let { "${searchDistance(it)} away" })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

/** Only what matches is shown: the intent line only when both motivations are the same. */
fun intentShared(you: String?, them: String?): Boolean = you != null && you == them

/**
 * ⑬'s chips in order -- the shared motivation, then the shared interests -- each with the icon the
 * sheet draws: the kit's `heart` on the motivation; none on an interest, whose reference names
 * (`reading`, `brunch`, `hiking`, `festivals`) are not in the kit's icon set and render nothing.
 */
fun commonChips(date: ActiveDate): List<Pair<String, BrandIcon?>> = buildList {
    if (intentShared(date.motivationYou, date.motivationThem)) {
        add((motivationOf(date.motivationThem)?.title ?: date.motivationThem.orEmpty()) to BrandIcon.Heart)
    }
    date.sharedInterests.forEach { add((interestLabel(it) ?: it) to null) }
}

/**
 * ⑩ `You both want`: the icon the reference gives a vibe's chip. It names four -- `coffee`,
 * `navigation`, `flame`, and `reading` for Deep conversation, which the kit does not have and so
 * draws nothing -- and no other vibe has one. No icon is invented for the rest.
 */
fun vibeIcon(id: String): BrandIcon? = when (id) {
    "coffee_chat" -> BrandIcon.Coffee
    "nature_walk" -> BrandIcon.Navigation
    "playful" -> BrandIcon.Flame
    else -> null
}

/** How the date works, row 1: the agreed place and time, or the time alone with no place. */
fun worksMeetLine(date: ActiveDate, zone: ZoneId): String {
    val time = meetTime(date.meetAt, zone)
    return date.place?.let { DateCopy.WORKS_MEET.format(it.name, time) }
        ?: DateCopy.WORKS_MEET_NO_PLACE.format(time)
}

/** The time + place card's headline, split so the day can be set italic and violet. */
fun meetingHeadline(name: String, day: String): Pair<String, String> = DateCopy.MEETING_LEAD.format(name) to day

/** A chat reason's tone: the four the reference's `REASON_TONES` defines. */
enum class ReasonTone { Violet, Orange, Danger, DarkRed }

/** How a chat reason looks on the unread card: the reference's `CHAT_REASONS` entry. */
data class ChatReasonStyle(val label: String, val icon: BrandIcon, val tone: ReasonTone)

/**
 * The server's six chat reasons onto the reference's `CHAT_REASONS` (Date 02 owns the vocabulary;
 * this screen only reads it for the unread badge). An unknown reason falls back to the first entry,
 * as the reference's `reasonFor` does, so a newer server never blanks the card.
 */
fun chatReasonStyle(reason: String): ChatReasonStyle = when (reason) {
    "running_late" -> ChatReasonStyle("I'm running late", BrandIcon.Hourglass, ReasonTone.Orange)
    "cant_find_you" -> ChatReasonStyle("I can't spot you", BrandIcon.Binoculars, ReasonTone.Orange)
    "change_meet_time" -> ChatReasonStyle("Change meet time", BrandIcon.Clock, ReasonTone.Violet)
    "change_location" -> ChatReasonStyle("Change location", BrandIcon.MapPin, ReasonTone.Violet)
    "cant_make_it_today" -> ChatReasonStyle("I can't make it today", BrandIcon.Frown, ReasonTone.DarkRed)
    else -> ChatReasonStyle("I'm on my way", BrandIcon.Navigation, ReasonTone.Orange)
}
