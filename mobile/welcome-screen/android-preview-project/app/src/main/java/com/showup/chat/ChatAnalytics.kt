/*
 * ChatAnalytics.kt
 * ShowUp · the coordinate chat's events (SHOWUP-194; family L, registry 1.4.33 / taxonomy 1.4.26)
 *
 * Builders only: each returns the name and the payload, and the model or the host reports them. Names
 * and values are the registry's, never typed at a call site. Every payload carries `date_id`; none
 * carries the text of a message, a draft, the guide or the place -- lengths and the chip only.
 *
 * NOT FROM HERE: `screen_viewed` (the host's, as every screen's), `date_ended_in_chat` (the server's),
 * and the events this ticket retires -- `coordinate_viewed`, `date_status_sent`, `date_status_undone`.
 */
package com.showup.chat

import com.showup.analytics.JsonNull
import com.showup.date.ActiveDate
import com.showup.date.minutesBeforeStart
import com.showup.profile.Stamp
import java.time.Instant

/** `enums.json` §39 `chat_entry`: how the chat was reached. */
enum class ChatEntry(val wire: String) {
    OpenChat("open_chat"),
    ReadReply("read_reply"),
    Tutorial("tutorial"),
    Notification("notification"),
    ;

    companion object {
        /** The entry a wire value names (a route's argument), or null for one this build does not know. */
        fun fromWire(wire: String): ChatEntry? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `action_chip_used.surface`: the start grid (B), the dock under the thread (C to E), or the change tiles
 * on the Date screen's locked Coordinate card (Date 01, 5 Nov 2026) -- the same act before the chat opens.
 */
enum class ChipSurface(val wire: String) {
    Entry("entry"),
    Dock("dock"),
    CoordinateCard("coordinate_card"),
}

object ChatAnalytics {
    const val CHAT_TUTORIAL_SHOWN = "chat_tutorial_shown"
    const val CHAT_TUTORIAL_ACKNOWLEDGED = "chat_tutorial_acknowledged"
    const val CHAT_OPENED = "chat_opened"
    const val ACTION_CHIP_USED = "action_chip_used"
    const val MESSAGE_SENT = "message_sent"
    const val MESSAGE_RECEIVED = "message_received"
    const val CANCEL_GUIDE_ACKNOWLEDGED = "cancel_guide_acknowledged"
    const val END_DATE_SHEET_OPENED = "end_date_sheet_opened"
    const val CHAT_CLOSED = "chat_closed"

    /** The tutorial (A) is shown: before the first open of each date's chat, per user. */
    fun chatTutorialShown(dateId: String) = CHAT_TUTORIAL_SHOWN to buildMap<String, Any> {
        put("date_id", dateId)
        putAll(Stamp.of(0))
    }

    /** `Open the chat` tapped: [readTimeS] from the tutorial showing. */
    fun chatTutorialAcknowledged(dateId: String, readTimeS: Int) = CHAT_TUTORIAL_ACKNOWLEDGED to buildMap<String, Any> {
        put("date_id", dateId)
        put("read_time_s", readTimeS)
        putAll(Stamp.of(0))
    }

    /** The chat is shown (B or the thread), once per visit. [isFirst]: the first open of this date's chat. */
    fun chatOpened(date: ActiveDate, entry: ChatEntry, isFirst: Boolean, now: Instant) = CHAT_OPENED to buildMap<String, Any> {
        put("date_id", date.id)
        put("entry", entry.wire)
        put("is_first", isFirst)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }

    /** Every chip tap, whatever it then opens. */
    fun actionChipUsed(date: ActiveDate, reason: ChatReason, surface: ChipSurface, now: Instant) = ACTION_CHIP_USED to buildMap<String, Any> {
        put("date_id", date.id)
        put("reason", reason.wire)
        put("surface", surface.wire)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }

    /**
     * Send tapped and the message accepted. [reason] and [prefillEdited] are JSON null for a message
     * with no chip; [prefillEdited] is null for a chip with no draft. [isFirst]: this person's first
     * message for this date.
     */
    fun messageSent(
        date: ActiveDate,
        reason: ChatReason?,
        charCount: Int,
        prefillEdited: Boolean?,
        isFirst: Boolean,
        now: Instant,
    ) = MESSAGE_SENT to buildMap<String, Any> {
        put("date_id", date.id)
        put("reason", reason?.wire ?: JsonNull)
        put("char_count", charCount)
        put("prefill_edited", prefillEdited ?: JsonNull)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        put("is_first", isFirst)
        putAll(Stamp.of(0))
    }

    /** One of the match's messages is shown in the thread, once per message. */
    fun messageReceived(dateId: String, reason: ChatReason?, charCount: Int) = MESSAGE_RECEIVED to buildMap<String, Any> {
        put("date_id", dateId)
        put("reason", reason?.wire ?: JsonNull)
        put("char_count", charCount)
        putAll(Stamp.of(0))
    }

    /** `Got it` on the cancel guide: [readTimeS] from the guide showing. */
    fun cancelGuideAcknowledged(date: ActiveDate, readTimeS: Int, now: Instant) = CANCEL_GUIDE_ACKNOWLEDGED to buildMap<String, Any> {
        put("date_id", date.id)
        put("read_time_s", readTimeS)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }

    /** `End date` on the card: the sheet opens. Keeping the date is this with no `date_ended_in_chat` after it. */
    fun endDateSheetOpened(date: ActiveDate, cantMakeItFrom: String, now: Instant) = END_DATE_SHEET_OPENED to buildMap<String, Any> {
        put("date_id", date.id)
        put("cant_make_it_from", cantMakeItFrom)
        put("minutes_before_start", minutesBeforeStart(date.meetAt, now))
        putAll(Stamp.of(0))
    }

    /** The person leaves the chat (back, or a hand-off that replaces it): this visit's messages and length. */
    fun chatClosed(dateId: String, messagesSent: Int, durationS: Int) = CHAT_CLOSED to buildMap<String, Any> {
        put("date_id", dateId)
        put("messages_sent", messagesSent)
        put("duration_s", durationS)
        putAll(Stamp.of(0))
    }
}
