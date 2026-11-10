/*
 * DateSamples.kt
 * ShowUp · the spec sheet's date, as values (SHOWUP-193)
 *
 * Leonie at Cafe Krone, 19:00 Berlin, on 3 Nov 2026 -- what every frame of the sheet shows. For the
 * previews and the developer menu's States page; the tests keep their own, so a sample edited for a
 * picture never changes what a test asserts.
 */
package com.showup.date

import com.showup.chat.ChatReason
import java.time.Instant
import java.time.ZoneId

object DateSamples {
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    private val MEET: Instant = Instant.parse("2026-11-03T18:00:00Z")
    private val UNLOCK: Instant = Instant.parse("2026-11-03T16:30:00Z")

    /** The sheet's clocks: 15:55 in A, 17:42 in B and C. */
    val A_NOW: Instant = Instant.parse("2026-11-03T14:55:00Z")
    val B_NOW: Instant = Instant.parse("2026-11-03T16:42:00Z")

    val DATE = ActiveDate(
        id = "sample-date", meetAt = MEET, meetDay = "today", unlockAt = UNLOCK, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("sample-leonie", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = listOf("coffee_chat", "nature_walk", "playful", "deep_talk"),
        sharedInterests = listOf("reading", "brunch", "hiking"),
        unread = null, checkedIn = false, chatTutorialSeen = false, arrival = null,
    )

    /** C: the sheet's running-late message, sent at 17:42; the dev menu sends the same text. */
    const val UNREAD_BODY = "Running about 10 min late — sorry! Almost there."
    val UNREAD = DateUnread(1, ChatReason.RunningLate, UNREAD_BODY, B_NOW)
}
