/*
 * ChatRules.kt
 * ShowUp · the coordinate chat's rules (SHOWUP-194)
 *
 * Plain functions, tested with no UI: what the end card, the locked field and the Send button follow,
 * the header's countdown, and the two values family L derives from the thread. The model reads them to
 * decide and the screen reads them to draw, so the two can never disagree.
 */
package com.showup.chat

import com.showup.date.minutesBeforeStart
import java.time.Instant
import java.util.Locale

/** The server's limit on one message: `SendChatMessageDto.body`, 1 to 1000 characters after trimming. */
const val MESSAGE_MAX = 1000

/**
 * How often the open chat re-reads the thread and the date. New messages arrive by polling -- there is
 * no socket -- and 5 s is the design's call (§3): quick enough to coordinate a meeting, light enough to
 * leave running while the chat is on screen, and only then.
 */
const val CHAT_POLL_MS = 5_000L

/** The cancel guide: not shown, shown with the field locked, or acknowledged and collapsed to one line. */
enum class Guide { None, Locked, Acked }

/**
 * The End date card: only once an "I can't make it today" message is in the thread, from either
 * person, and not while that chip is selected -- the guide takes its place at the foot of the thread.
 */
fun endCardVisible(messages: List<ChatMessage>, selected: ChatReason?): Boolean =
    selected != ChatReason.CantMakeIt && messages.any { it.reason == ChatReason.CantMakeIt }

/** The field and Send wait for `Got it` after "I can't make it today" is picked, and only then. */
fun fieldLocked(selected: ChatReason?, guide: Guide): Boolean =
    selected == ChatReason.CantMakeIt && guide == Guide.Locked

/** Send is enabled whenever the field has text that is not only spaces, and is not locked. */
fun canSend(text: String, locked: Boolean): Boolean = !locked && text.isNotBlank()

/**
 * `in 1h 18m` to the latest agreed meet time, by whole minutes; null at and after it, when the header
 * leaves it out (the chat stays open until the date ends, and the ticket gives no copy for after).
 */
fun countdown(now: Instant, meetAt: Instant): String? {
    if (!now.isBefore(meetAt)) return null
    val minutes = minutesBeforeStart(meetAt, now)
    return String.format(Locale.ROOT, ChatCopy.COUNTDOWN, minutes / 60, minutes % 60)
}

/**
 * `message_sent.prefill_edited`: whether the text sent differs from the chip's draft, spaces at either
 * end aside (the server trims them too). Null for a chip with no draft and for no chip.
 */
fun prefillEdited(reason: ChatReason?, sent: String): Boolean? =
    reason?.draft?.let { draft -> sent.trim() != draft.trim() }

/**
 * `cant_make_it_from`: who sent the latest "I can't make it today" message, seen from this phone --
 * `self` or `match` -- or null when the thread has none. The server decides it the same way.
 */
fun cantMakeItFrom(messages: List<ChatMessage>): String? =
    messages.lastOrNull { it.reason == ChatReason.CantMakeIt }?.let { if (it.fromYou) "self" else "match" }
