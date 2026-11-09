/*
 * ChatSamples.kt
 * ShowUp · the spec sheet's frames as values (SHOWUP-194), for previews, the fit sweep and the evidence
 *
 * The date is the Date screen's sample (Leonie, Cafe Krone, 18:00 Berlin) with the chat open; the clock
 * is the sheet's 17:42. C to F are `ScreenChatOpen` as the sheet renders it: C with the running-late
 * chip and its draft, D with "I can't make it today" and the guide waiting, E and F with the one
 * message the sheet seeds.
 */
package com.showup.chat

import com.showup.api.CallFailure
import com.showup.date.DateSamples
import com.showup.date.EndedInChat
import java.time.Instant

object ChatSamples {
    val DATE = DateSamples.DATE.copy(coordinateOpen = true, chatTutorialSeen = true)
    val NOW: Instant = DateSamples.B_NOW

    /** The sheet's seed: my "I can't make it today" message at 17:38, read. */
    val CANCEL_SENT = ChatMessage(
        id = "c1",
        reason = ChatReason.CantMakeIt,
        body = "So sorry Leonie, something came up at work and I can't make it. Could we do Saturday instead?",
        fromYou = true,
        sentAt = Instant.parse("2026-11-03T16:38:00Z"),
        read = true,
    )

    /** J's seed: my running-late message, sent at 17:41 and failed. */
    val UNSENT = ChatMessage(
        id = "unsent:6f1c2a52-6a4e-4d8f-9b39-2f4f3b8d1e10",
        reason = ChatReason.RunningLate,
        body = ChatReason.RunningLate.draft.orEmpty(),
        fromYou = true,
        sentAt = Instant.parse("2026-11-03T16:41:00Z"),
        read = null,
        clientId = "6f1c2a52-6a4e-4d8f-9b39-2f4f3b8d1e10",
        failed = true,
    )

    /** B: no message yet. */
    val B = ChatUi(date = DATE, now = NOW, loading = false)

    /** C: `I'm running late` picked from B, its draft in the field. */
    val C = B.copy(selected = ChatReason.RunningLate, draft = ChatReason.RunningLate.draft.orEmpty())

    /** D: `I can't make it today` picked; the field empty and locked behind the guide. */
    val D = B.copy(selected = ChatReason.CantMakeIt, guide = Guide.Locked)

    /** E: the cancel message is in the thread, so the End date card is at its foot. */
    val E = B.copy(messages = listOf(CANCEL_SENT))

    /** F: E with the End the date sheet open. */
    val F = E.copy(endSheet = true)

    /** I: the chat's first load failed. */
    val I = B.copy(failed = CallFailure.Network)

    /** J: a send failed; the message stays in the thread and the field is free. */
    val J = B.copy(messages = listOf(UNSENT))

    /** K: End date on F did not go through. */
    val K = F.copy(endFailed = CallFailure.Network)

    /** G: you ended it. */
    val G = EndedInChat(DATE.id, DATE.match.name, endedByYou = true)

    /** H: the match ended it. */
    val H = G.copy(endedByYou = false)

    /** What the developer menu's "Leonie can't make it" sends, with the cancel chip. */
    const val MATCH_CANT_MAKE_IT = "So sorry, something came up at work and I can't make it today. Could we do Saturday instead?"
}
