/*
 * DateRules.kt
 * ShowUp · the Date screen's rules, as plain functions (SHOWUP-193)
 *
 * Which state the screen is in, the minutes every event carries, and every string built from
 * values. No view, so each is tested without one (`DateRulesTest`); Swift has the same functions
 * under the same names.
 */
package com.showup.date

import com.showup.change.ChangeCopy
import com.showup.change.ChangeKind
import com.showup.change.ChangeStatus
import com.showup.change.ChangeSuggestion
import com.showup.change.changeClock
import com.showup.change.changeDeadline
import com.showup.chat.ChatReason
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
 * Whether a card is hoisted under the hero: the match's arrival (Date 03), the match's change request (Date 05)
 * or an unread message, in that order (R11, R8: the request has a deadline, a message does not). The slot holds one.
 */
fun hoisted(state: DateState, arrival: DateArrival?, request: ChangeSuggestion? = null): Boolean =
    arrival != null || request != null || state == DateState.Unread

/** The request card shows: the server sent one and the match's arrival, which outranks it, is not in the slot. */
fun requestShown(date: ActiveDate): Boolean = date.arrival == null && date.changeRequest != null

/**
 * How often the Date screen re-reads the date while it is on screen (SHOWUP-194, the design's §3):
 * the match's End date lands on H within seconds when the app is open. Polling, as the chat's; there
 * is no socket.
 */
const val DATE_POLL_MS = 15_000L

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

/**
 * [s] with a zero-width space after each hyphen, so a long hyphenated name or district may break after
 * its hyphen, as CSS and iOS break it. Android's line breaker does not: `Friedrichshain-Kreuzberg`
 * broke mid-letter wherever the whole word did not fit (the 4 Nov 2026 device audit). Invisible, and
 * silent to TalkBack.
 */
fun breakAfterHyphens(s: String): String = s.replace(HYPHEN_UNMARKED, "-\u200B")

private val HYPHEN_UNMARKED = Regex("-(?!\u200B)")

/** [date] with its match's name and district breakable after their hyphens ([breakAfterHyphens]). */
fun withBreakableNames(date: ActiveDate): ActiveDate = date.copy(
    match = date.match.copy(name = breakAfterHyphens(date.match.name), district = date.match.district?.let(::breakAfterHyphens)),
)

// ── Date 05: the purple card's change tags and note, and the request card's text ─────────────

/** What sits under the time or place value on the purple card: the viewer's own waiting suggestion, or the agreed change. */
sealed interface ChangeTagText {
    /** `Waiting for {name} to agree` / `You suggested {value}`: amber, the BINDING value stays big above it. */
    data class Waiting(val value: String) : ChangeTagText

    /** `Changed · was {original}`: the ORIGINAL only, never a chain. */
    data class Changed(val was: String) : ChangeTagText
}

/**
 * The tag under the [kind]'s value (the reference's `shownTag`). The match's pending suggestion never gets one: purple
 * is what is real, orange what needs a reaction (3 Nov 2026). The viewer's own waiting suggestion wins over an earlier
 * agreed change of the same kind: it is the one that needs an answer from the match. `Changed` is hidden after the
 * meet time, and appears only once a change was agreed (the server sends the original then).
 */
fun changeTagFor(date: ActiveDate, kind: ChangeKind, now: Instant, zone: ZoneId): ChangeTagText? {
    val change = date.change ?: return null
    val own = change.pending.firstOrNull { it.byYou && it.kind == kind && it.status == ChangeStatus.Pending }
    if (own != null) {
        val value = if (kind == ChangeKind.Time) own.time?.let { changeClock(it, zone) } else own.place?.name
        return value?.let { ChangeTagText.Waiting(it) }
    }
    val was = if (kind == ChangeKind.Time) change.timeWas?.let { changeClock(it, zone) } else change.placeWas
    return if (was != null && now < date.meetAt) ChangeTagText.Changed(was) else null
}

/** The compact binding note's values: the CURRENT binding time and place and the binding deadline (D). */
data class BindingNoteValues(val time: String, val place: String, val deadline: String)

/** One note at the card's bottom while the viewer has ANY own pending suggestion -- one, even with a time and a place. */
fun bindingNoteFor(date: ActiveDate, zone: ZoneId): BindingNoteValues? {
    val pending = date.change?.pending?.any { it.byYou && it.status == ChangeStatus.Pending } == true
    if (!pending) return null
    return BindingNoteValues(changeClock(date.meetAt, zone), date.place?.name.orEmpty(), changeClock(changeDeadline(date.meetAt), zone))
}

/** Every string the request card (E, J) draws from the date and the request. */
data class RequestCardText(
    val reason: ChatReason,
    val needs: String,
    val value: String,
    val was: String,
    /** `{Type} · {address}`, a place's only. */
    val placeLine: String?,
    val deadline: String,
    val bindingTime: String,
    val bindingPlace: String,
    /** J's `Messaging opens at {opensAt}`: the unlock, meet - 1:30. */
    val opensLine: String,
)

fun requestCardText(date: ActiveDate, request: ChangeSuggestion, now: Instant, zone: ZoneId): RequestCardText {
    val isTime = request.kind == ChangeKind.Time
    return RequestCardText(
        reason = if (isTime) ChatReason.ChangeTime else ChatReason.ChangePlace,
        needs = ChangeCopy.NEEDS_ANSWER.format(unreadTime(request.createdAt, now, zone)),
        value = if (isTime) request.time?.let { changeClock(it, zone) }.orEmpty() else request.place?.name.orEmpty(),
        was = if (isTime) changeClock(request.wasTime, zone) else request.wasPlaceName.orEmpty(),
        placeLine = request.place?.takeIf { !isTime }?.let { "${com.showup.change.placeTypeLabel(it.type)} · ${it.address}" },
        deadline = changeClock(request.deadline, zone),
        bindingTime = changeClock(date.meetAt, zone),
        bindingPlace = date.place?.name.orEmpty(),
        opensLine = ChangeCopy.MESSAGING_OPENS.format(changeClock(date.unlockAt, zone)),
    )
}
