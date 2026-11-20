/*
 * ThreadSamples.kt
 * ShowUp · the Date 05 suggestions the thread tests share (SHOWUP-196): the Date screen's sample, 18:00 in
 * Berlin, read at 17:42.
 */
package com.showup.chat

import com.showup.change.DateChange
import com.showup.change.ChangeKind
import com.showup.change.ChangePlace
import com.showup.change.ChangeStatus
import com.showup.change.ChangeSuggestion
import com.showup.change.PlaceType
import java.time.Instant

object ThreadSamples {
    /** The sample date moved to 18:00 Berlin, the time the suggestions below are measured against. */
    val DATE = ChatSamples.DATE.copy(
        meetAt = Instant.parse("2026-11-03T17:00:00Z"),
        // New suggestions still allowed (a cutoff after the sample clock, ChatSamples.NOW 16:42Z), so a pending card
        // from the match offers Suggest another; a test that closes them sets `allowed = false`.
        change = DateChange(Instant.parse("2026-11-03T23:00:00Z"), allowed = true, slots = emptyList(), timeWas = null, placeWas = null, pending = emptyList()),
    )

    private fun at(hhmmBerlin: String) = Instant.parse("2026-11-03T$hhmmBerlin:00Z").minusSeconds(3600)

    /** Leonie's pending time suggestion: 18:30, answer by 17:30, sent 17:10. */
    val THEIRS_TIME = ChangeSuggestion(
        id = "t1", kind = ChangeKind.Time, byYou = false, time = at("18:30"), place = null,
        wasTime = at("18:00"), wasPlaceName = null, deadline = at("17:30"), createdAt = at("17:10"),
    )

    /** My pending place suggestion, sent 17:12. */
    val MINE_PLACE = ChangeSuggestion(
        id = "p1", kind = ChangeKind.Place, byYou = true, time = null,
        place = ChangePlace("pl1", "Bar Lenz", PlaceType.Bar, "Reichenberger Str. 9"),
        wasTime = at("18:00"), wasPlaceName = "Cafe Krone", deadline = at("17:30"), createdAt = at("17:12"),
    )

    val AGREED_TIME = THEIRS_TIME.copy(status = ChangeStatus.Agreed, answeredAt = at("17:20"))
    val AGREED_PLACE = MINE_PLACE.copy(id = "p2", status = ChangeStatus.Agreed, answeredAt = at("17:25"))
    val REPLACED = THEIRS_TIME.copy(id = "t0", status = ChangeStatus.Replaced, createdAt = at("17:05"))
    val EXPIRED = THEIRS_TIME.copy(
        id = "t2", status = ChangeStatus.Expired, expiredAt = at("17:30"),
        expiredBindingTime = at("18:00"), expiredBindingPlaceName = "Cafe Krone",
    )

    fun message(id: String, hhmm: String, fromYou: Boolean) =
        ChatMessage(id, null, "m$id", fromYou, sentAt = at(hhmm), read = if (fromYou) false else null)
}
