/*
 * ChatReasons.kt
 * ShowUp · the chat's reasons, built once (SHOWUP-194, Date 02)
 *
 * The reference's `CHAT_REASONS` and `REASON_TONES` (screen-chat-reference.jsx), keyed by the
 * registry's §40 `chat_reason` wire values. The Date screen's unread card, the chat's chips and
 * bubbles, and Dates 03 and 05 all read this one table: 193 kept a copy keyed by the server's old
 * names, and a copy is how a renamed reason silently draws the wrong badge.
 */
package com.showup.chat

import androidx.compose.ui.graphics.Color
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Purple
import com.showup.designsystem.ReasonDarkRed
import com.showup.welcome.BrandIcon
import com.showup.designsystem.Danger as DangerInk
import com.showup.designsystem.Orange as OrangeInk

/**
 * The reference's `REASON_TONES`: the ink, the panel fill and the chip border of each tone. Two
 * colours are imported under another name because the entries `Orange` and `Danger` shadow them here.
 */
enum class ReasonTone(val fg: Color, val bg: Color, val chipBorder: Color) {
    Violet(Purple, Purple.copy(alpha = 0.10f), Purple.copy(alpha = 0.20f)),
    Orange(OrangeInk, OrangeInk.copy(alpha = 0.11f), OrangeInk.copy(alpha = 0.22f)),
    Danger(DangerFg, DangerInk.copy(alpha = 0.10f), DangerInk.copy(alpha = 0.22f)),
    DarkRed(ReasonDarkRed, ReasonDarkRed.copy(alpha = 0.09f), ReasonDarkRed.copy(alpha = 0.28f)),
}

/**
 * One chip, in the grid's order: its §40 wire value, the ticket's label and the draft its tap puts in
 * the composer. Three have no draft: the two changes are Date 05's, and "I can't make it today" is
 * written by the person after the guide, never pre-filled. [shown] is false for the chips whose
 * ticket has not shipped -- `arrived` is Date 03's, the two changes Date 05's -- so the grid shows
 * five until they do.
 */
enum class ChatReason(
    val wire: String,
    val label: String,
    val draft: String?,
    val icon: BrandIcon,
    val tone: ReasonTone,
    val shown: Boolean,
) {
    OnMyWay("on_my_way", "I'm on my way", "On my way now — see you in a few!", BrandIcon.Navigation, ReasonTone.Orange, true),
    Arrived("arrived", "I arrived at the spot", "I'm here at the meeting spot — see you soon!", BrandIcon.Check, ReasonTone.Orange, false),
    CantFind("cant_find", "I can't spot you", "I'm here but can't spot you — where are you sitting?", BrandIcon.Binoculars, ReasonTone.Orange, true),
    WhenArrive("when_arrive", "When will you arrive?", "When do you think you'll get here?", BrandIcon.Walking, ReasonTone.Orange, true),
    ChangeTime("change_time", "Change meet time", null, BrandIcon.Clock, ReasonTone.Violet, false),
    ChangePlace("change_place", "Change location", null, BrandIcon.MapPin, ReasonTone.Violet, false),
    RunningLate("running_late", "I'm running late", "Running about 10 min late — sorry! Almost there.", BrandIcon.Hourglass, ReasonTone.Orange, true),
    CantMakeIt("cant_make_it", "I can't make it today", null, BrandIcon.Frown, ReasonTone.DarkRed, true),
    ;

    companion object {
        /**
         * The chip a wire value names, or null for none: a message sent with no chip, and a name this
         * build does not know. Null, not the reference's first-entry fallback -- a badge saying "I'm
         * on my way" over a message that never said so is worse than no badge.
         */
        fun fromWire(wire: String?): ChatReason? = entries.firstOrNull { it.wire == wire }
    }
}

/** The chips the grid shows today, in its order. */
val SHOWN_REASONS: List<ChatReason> = ChatReason.entries.filter { it.shown }
