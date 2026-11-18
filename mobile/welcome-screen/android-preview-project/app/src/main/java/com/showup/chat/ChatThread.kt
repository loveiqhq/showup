/*
 * ChatThread.kt
 * ShowUp · what the thread shows: messages, suggestion cards and system lines in one time order (SHOWUP-196)
 *
 * The server keeps messages and suggestions apart (a suggestion is NOT a message: no `message_sent`, no
 * unread), so the phone merges them for the eye only, oldest first. A card sits at `createdAt`; the system
 * line an answer leaves sits at `answeredAt` (agreed) or `expiredAt` (expired) -- the card keeps its own place.
 * A replaced card stays where it was and posts no line. Messages that failed to send stay at the foot, as
 * before: they were never in the server's order.
 */
package com.showup.chat

import com.showup.change.ChangeCopy
import com.showup.change.ChangeKind
import com.showup.change.ChangeStatus
import com.showup.change.ChangeSuggestion
import com.showup.change.changeClock
import java.time.Instant
import java.time.ZoneId

sealed interface ThreadItem {
    val key: String
    val at: Instant

    data class Message(val message: ChatMessage) : ThreadItem {
        override val key get() = "message:${message.id}"
        override val at get() = message.sentAt
    }

    data class Card(val suggestion: ChangeSuggestion) : ThreadItem {
        override val key get() = "card:${suggestion.id}"
        override val at get() = suggestion.createdAt
    }

    /** [agreed]: the success tone and `check`; otherwise the muted tone and `clock` (an expiry). */
    data class System(val id: String, val text: String, val agreed: Boolean, override val at: Instant) : ThreadItem {
        override val key get() = "system:$id"
    }
}

/**
 * The line a suggestion's answer leaves, in the client's words from the server's facts; null when it left
 * none (pending, replaced) or the facts are missing. All times are `HH:mm` on [zone].
 */
fun systemLineFor(s: ChangeSuggestion, zone: ZoneId): ThreadItem.System? = when (s.status) {
    ChangeStatus.Agreed -> {
        val at = s.answeredAt
        val text = when (s.kind) {
            ChangeKind.Time -> s.time?.let { ChangeCopy.SYSTEM_TIME.format(changeClock(it, zone)) }
            ChangeKind.Place -> s.place?.let { ChangeCopy.SYSTEM_PLACE.format(it.name) }
        }
        if (at != null && text != null) ThreadItem.System(s.id, text, agreed = true, at = at) else null
    }
    ChangeStatus.Expired -> {
        val at = s.expiredAt
        val time = s.expiredBindingTime
        val place = s.expiredBindingPlaceName
        if (at != null && time != null && place != null) {
            val text = ChangeCopy.SYSTEM_EXPIRED.format(changeClock(s.deadline, zone), changeClock(time, zone), place)
            ThreadItem.System(s.id, text, agreed = false, at = at)
        } else {
            null
        }
    }
    ChangeStatus.Pending, ChangeStatus.Replaced -> null
}

/** Rank on a tie: what was said, then the card, then what the answer left. */
private fun ThreadItem.rank() = when (this) {
    is ThreadItem.Message -> 0
    is ThreadItem.Card -> 1
    is ThreadItem.System -> 2
}

/** Messages and suggestions interleaved by time, oldest first (stable), then the messages that failed to send. */
fun buildThread(messages: List<ChatMessage>, suggestions: List<ChangeSuggestion>, zone: ZoneId): List<ThreadItem> {
    val sent = messages.filter { !it.failed }.map { ThreadItem.Message(it) }
    val cards = suggestions.map { ThreadItem.Card(it) }
    val lines = suggestions.mapNotNull { systemLineFor(it, zone) }
    val merged = (sent + cards + lines).sortedWith(compareBy<ThreadItem> { it.at }.thenBy { it.rank() })
    return merged + messages.filter { it.failed }.map { ThreadItem.Message(it) }
}
