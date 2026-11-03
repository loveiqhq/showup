/*
 * DateRulesTest.kt
 * ShowUp · the Date screen's rules, with no UI (SHOWUP-193)
 */
package com.showup.date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DateRulesTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z") // 19:00 in Berlin
    private val unlock = Instant.parse("2026-11-03T16:30:00Z") // 17:30 in Berlin
    private val unread = DateUnread(1, "running_late", "Ten minutes late!", Instant.parse("2026-11-03T17:10:00Z"))
    private val place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg")

    private fun date(
        place: DatePlace? = this.place,
        meetDay: String = "today",
        name: String = "Leonie",
    ) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = meetDay, unlockAt = unlock, coordinateOpen = false,
        place = place,
        match = DateMatch("u2", name, 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = emptyList(), sharedInterests = emptyList(), unread = null, checkedIn = false,
    )

    // ── the state ───────────────────────────────────────────────────────────

    @Test
    fun `the state flips exactly at meet minus 1h30`() {
        assertEquals(DateState.Locked, dateState(unlock.minusSeconds(1), unlock, null))
        assertEquals(DateState.Available, dateState(unlock, unlock, null))
        assertEquals(DateState.Unread, dateState(unlock, unlock, unread))
    }

    @Test
    fun `an unread message never hoists the card while it is still locked`() {
        assertEquals(DateState.Locked, dateState(unlock.minusSeconds(60), unlock, unread))
    }

    @Test
    fun `minutes before start count from the agreed time, negative after, toward zero`() {
        assertEquals(90, minutesBeforeStart(meet, unlock))
        assertEquals(0, minutesBeforeStart(meet, meet))
        assertEquals(-15, minutesBeforeStart(meet, meet.plusSeconds(15 * 60)))
        assertEquals(89, minutesBeforeStart(meet, meet.minusSeconds(89 * 60 + 30)))
        assertEquals(0, minutesBeforeStart(meet, meet.plusSeconds(30)))
    }

    // ── the copy that depends on values ─────────────────────────────────────

    @Test
    fun `the locked line names the unlock time, and tomorrow when it is`() {
        assertEquals("Messaging available from 17:30", lockedLine(unlock, unlock.minusSeconds(3600), berlin))
        // At 23:00 the evening before, the unlock is on the next calendar day.
        val eveningBefore = Instant.parse("2026-11-02T22:00:00Z")
        assertEquals("Messaging available tomorrow from 17:30", lockedLine(unlock, eveningBefore, berlin))
    }

    @Test
    fun `the share text is the ticket's sentence, and drops the place when there is none`() {
        assertEquals(
            "I'm meeting Leonie today at 19:00 at Cafe Krone, Oranienstrasse 42, Kreuzberg.",
            shareText(date(), berlin),
        )
        assertEquals(
            "I'm meeting Leonie tomorrow at 19:00 at Cafe Krone.",
            shareText(date(place = DatePlace("Cafe Krone", null), meetDay = "tomorrow"), berlin),
        )
        assertEquals("I'm meeting Leonie today at 19:00.", shareText(date(place = null), berlin))
    }

    @Test
    fun `the calendar event is Date with name, one hour, at the place and address`() {
        assertEquals(
            CalendarEvent("Date with Leonie", meet, meet.plusSeconds(3600), "Cafe Krone, Oranienstrasse 42, Kreuzberg"),
            calendarEvent(date()),
        )
        assertNull(calendarEvent(date(place = null)).location)
    }

    @Test
    fun `copy writes place comma address, or the name alone`() {
        assertEquals("Cafe Krone, Oranienstrasse 42, Kreuzberg", placeText(place))
        assertEquals("Cafe Krone", placeText(DatePlace("Cafe Krone", null)))
    }

    @Test
    fun `the unread time is now under a minute, then the clock time`() {
        val sent = Instant.parse("2026-11-03T17:10:00Z")
        assertEquals("now", unreadTime(sent, sent.plusSeconds(59), berlin))
        assertEquals("18:10", unreadTime(sent, sent.plusSeconds(60), berlin))
    }

    @Test
    fun `the about line is area and distance, rounded as Search rounds, and absent with neither`() {
        assertEquals("Neukölln · ~1 km away", aboutLine("Neukölln", 1))
        assertEquals("Neukölln · < 1 km away", aboutLine("Neukölln", 0))
        assertEquals("~3 km away", aboutLine(null, 3))
        assertEquals("Neukölln", aboutLine("Neukölln", null))
        assertNull(aboutLine(null, null))
    }

    @Test
    fun `the intent line shows only when both motivations are the same`() {
        assertTrue(intentShared("long_term", "long_term"))
        assertFalse(intentShared("long_term", "casual"))
        assertFalse(intentShared(null, null))
        assertFalse(intentShared("long_term", null))
    }

    @Test
    fun `how the date works opens with the agreed place and time`() {
        assertEquals("Meet at Cafe Krone at 19:00", worksMeetLine(date(), berlin))
        assertEquals("Meet at 19:00", worksMeetLine(date(place = null), berlin))
    }

    @Test
    fun `the meeting headline puts the day in its own run`() {
        assertEquals("You and Leonie are meeting " to "today", meetingHeadline("Leonie", "today"))
    }

    @Test
    fun `every server chat reason has the reference's badge, and an unknown one falls back`() {
        assertEquals("I'm running late", chatReasonStyle("running_late").label)
        assertEquals(ReasonTone.Orange, chatReasonStyle("running_late").tone)
        assertEquals("Change meet time", chatReasonStyle("change_meet_time").label)
        assertEquals(ReasonTone.Violet, chatReasonStyle("change_location").tone)
        assertEquals(ReasonTone.DarkRed, chatReasonStyle("cant_make_it_today").tone)
        assertEquals("I can't spot you", chatReasonStyle("cant_find_you").label)
        assertEquals("I'm on my way", chatReasonStyle("on_my_way").label)
        assertEquals("I'm on my way", chatReasonStyle("something_new").label)
    }

    @Test
    fun `the unread tag says New message for one and n new for more`() {
        assertEquals("New message", unreadTag(1))
        assertEquals("3 new", unreadTag(3))
    }

    // ── the chips' icons, as the sheet draws them ───────────────────────────

    @Test
    fun `in common, the shared intent carries the heart and the interests carry none`() {
        val chips = commonChips(date().copy(sharedInterests = listOf("reading", "hiking")))
        assertEquals(
            listOf("Long-term partner" to com.showup.welcome.BrandIcon.Heart, "Reading" to null, "Hiking" to null),
            chips,
        )
    }

    @Test
    fun `in common has no intent chip when the motivations differ`() {
        val chips = commonChips(date().copy(motivationThem = "casual", sharedInterests = listOf("reading")))
        assertEquals(listOf("Reading" to null), chips)
    }

    @Test
    fun `only the three vibes the reference draws carry an icon`() {
        assertEquals(com.showup.welcome.BrandIcon.Coffee, vibeIcon("coffee_chat"))
        assertEquals(com.showup.welcome.BrandIcon.Navigation, vibeIcon("nature_walk"))
        assertEquals(com.showup.welcome.BrandIcon.Flame, vibeIcon("playful"))
        assertNull(vibeIcon("deep_talk"))
        assertNull(vibeIcon("sunset"))
    }
}
