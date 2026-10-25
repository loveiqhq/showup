/*
 * SearchRulesTest.kt
 * ShowUp · Date search 01's rules, with no UI (SHOWUP-184)
 *
 * The Swift twin is `SearchRulesTests.swift`; the same cases, the same answers.
 */
package com.showup.search

import com.showup.designsystem.showUpCountLabel
import com.showup.profile.LifestyleField
import com.showup.welcome.BrandIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SearchRulesTest {

    @Test
    fun `distance reads under one km or about N km, and the district only when there is one`() {
        assertEquals("< 1 km", searchDistance(0))
        assertEquals("~1 km", searchDistance(1))
        assertEquals("~14 km", searchDistance(14))
        assertEquals("~3 km · Kreuzberg", distanceLine(3, "Kreuzberg"))
        assertEquals("< 1 km · Neukölln, Berlin", distanceLine(0, "Neukölln, Berlin"))
        assertEquals("~3 km", distanceLine(3, null))
        assertEquals("~3 km", distanceLine(3, "   "))
        // Three runs, drawn 6 apart as the sheet renders them; the line is their join.
        assertEquals(listOf("~3 km", "·", "Kreuzberg"), distanceParts(3, " Kreuzberg "))
        assertEquals(listOf("< 1 km"), distanceParts(0, null))
    }

    @Test
    fun `filters_active names age and distance set away from Check-In 05's defaults, never gender`() {
        val defaults = com.showup.checkin.CheckInFilters(genders = setOf("woman"))
        assertEquals(emptyList<String>(), filtersActive(defaults))
        assertEquals(emptyList<String>(), filtersActive(defaults.copy(genders = setOf("man", "woman"))))
        assertEquals(listOf("age_range"), filtersActive(defaults.copy(minAge = 25)))
        assertEquals(listOf("age_range"), filtersActive(defaults.copy(maxAge = 40)))
        assertEquals(listOf("age_range", "distance_km"), filtersActive(defaults.copy(maxAge = 40, distanceKm = 25)))
        assertEquals(listOf("distance_km"), filtersActive(defaults.copy(distanceKm = 5)))
    }

    @Test
    fun `a hidden age leaves the name alone, with no comma`() {
        assertEquals("Leonie, 28", nameLine("Leonie", 28))
        assertEquals("Leonie", nameLine("Leonie", null))
    }

    @Test
    fun `the meet line is the hour on the phone's clock and the server's day`() {
        val at = Instant.parse("2026-10-22T17:00:00Z")
        assertEquals("19:00 today", meetLine(at, "today", ZoneId.of("Europe/Berlin")))
        assertEquals("13:00 tomorrow", meetLine(at, "tomorrow", ZoneId.of("America/New_York")))
    }

    @Test
    fun `show-up is a count, and zero is new in the app`() {
        assertEquals("New in the App", showUpCountLabel(0))
        assertEquals("1 date attended", showUpCountLabel(1))
        assertEquals("4 dates attended", showUpCountLabel(4))
        assertFalse(showUpCountLabel(4).contains("%"))
    }

    @Test
    fun `shared vibes keep the profile's order, at most three then plus N, nothing when nothing is shared`() {
        val theirs = listOf("coffee_chat", "sunset", "deep_talk", "playful", "no_rush")
        val shared = sharedVibes(theirs, setOf("no_rush", "coffee_chat", "sunset", "deep_talk"))
        assertEquals(listOf("coffee_chat", "sunset", "deep_talk", "no_rush"), shared)
        assertEquals(SharedVibesRow(listOf("coffee_chat", "sunset", "deep_talk"), 1), sharedVibesRow(shared))
        assertEquals(SharedVibesRow(listOf("sunset"), 0), sharedVibesRow(listOf("sunset")))
        assertTrue(sharedVibes(theirs, emptySet()).isEmpty())
    }

    @Test
    fun `the interests sub adds in common only from one`() {
        assertEquals("10 things Leonie is into · 4 in common", interestsSub("Leonie", 10, 4))
        assertEquals("10 things Leonie is into", interestsSub("Leonie", 10, 0))
        assertEquals(setOf("hiking", "reading"), sharedInterests(listOf("yoga_pilates", "hiking", "reading"), setOf("reading", "hiking")))
        assertTrue(sharedInterests(listOf("hiking"), emptySet()).isEmpty())
    }

    @Test
    fun `labels come from the profile's and the Check-In's own tables, and an unknown value has none`() {
        assertEquals("Coffee & a chat", vibeLabel("coffee_chat"))
        assertNull(vibeLabel("teleportation"))
        assertEquals("Long-term partner", motivationOf("long_term")?.title)
        assertEquals(BrandIcon.HeartFilled, motivationOf("long_term")?.icon)
        assertNull(motivationOf("something_new"))
    }

    @Test
    fun `media subtitles use the take's length as its owner saw it`() {
        assertEquals("Voice prompt · 0:14", voiceSub(14_600))
        assertEquals("Video prompt · 0:10", videoSub(10_000))
    }

    @Test
    fun `at rest the waveform is the reference's 18 violet bars, while playing it follows the playhead`() {
        assertEquals(48, VOICE_BARS.size)
        assertEquals(18, voiceVioletBars(playing = false, progress = 0f))
        assertEquals(18, voiceVioletBars(playing = false, progress = 0.9f))
        assertEquals(0, voiceVioletBars(playing = true, progress = 0f))
        assertEquals(24, voiceVioletBars(playing = true, progress = 0.5f))
        assertEquals(48, voiceVioletBars(playing = true, progress = 1f))
        assertEquals(48, voiceVioletBars(playing = true, progress = 1.4f))
    }

    @Test
    fun `essentials are in the ticket's order, and a missing or unknown answer drops its row`() {
        val rows = essentials(
            EssentialAnswers(
                heightCm = 172, gender = "woman", orientation = "straight",
                datingLanguages = listOf("german", "english", "klingon"),
                education = null, religion = "spiritual_other", politics = "unknown_value",
            ),
        )
        assertEquals(
            listOf(EssentialRow.Height, EssentialRow.Gender, EssentialRow.Orientation, EssentialRow.DatesIn, EssentialRow.Religion),
            rows.map { it.row },
        )
        assertEquals("172 cm", rows[0].value)
        assertEquals("German, English", rows[3].value)
        assertEquals(listOf("Height", "Gender", "Orientation", "Dates in", "Education", "Religion", "Politics"), EssentialRow.entries.map { it.label })
        assertTrue(essentials(EssentialAnswers()).isEmpty())
    }

    @Test
    fun `How I live draws only the answered groups, in the group order, with their icons`() {
        val tiles = lifestyleTiles(mapOf("social" to "balanced", "activity" to "active", "diet" to "nope"))
        assertEquals(listOf(LifestyleField.Activity, LifestyleField.Social), tiles.map { it.field })
        assertEquals(listOf("Active", "Balanced"), tiles.map { it.value })
        assertEquals(BrandIcon.Leaf, lifestyleIcon(LifestyleField.Diet))
        assertTrue(lifestyleTiles(emptyMap()).isEmpty())
    }

    @Test
    fun `the message is counted and cut in graphemes, never splitting one`() {
        // One family: a man, a woman and a girl joined by zero-width joiners -- five code points,
        // spelled as escapes because the house style has no emoji in source.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(1, graphemes(family))
        val long = "a".repeat(99) + family + "b"
        assertEquals(101, graphemes(long))
        val cut = limitMessage(long)
        assertEquals(100, graphemes(cut))
        assertTrue(cut.endsWith(family))
        assertEquals("a".repeat(100), limitMessage("a".repeat(150)))
        assertEquals("12 / 100", messageCounter("Hello there!"))
    }

    @Test
    fun `the draft names the person and the day, and a long name never starts it over the limit`() {
        assertEquals(
            "Hi Leonie, I love the vibe of your profile. Would be great to meet you today :)",
            messageDraft("Leonie", "today"),
        )
        assertEquals(100, graphemes(messageDraft("A".repeat(60), "tomorrow")))
    }

    @Test
    fun `send message sends the trimmed text, and blank sends none`() {
        assertEquals("Hi!", messageToSend("  Hi!  "))
        assertNull(messageToSend("   "))
        assertNull(messageToSend(""))
    }

    @Test
    fun `the coach shows once a visit over a profile with Like, until don't show again`() {
        assertTrue(shouldShowCoach(hiddenForever = false, shownThisVisit = false, hasProfile = true, likedYou = false))
        assertFalse(shouldShowCoach(hiddenForever = true, shownThisVisit = false, hasProfile = true, likedYou = false))
        assertFalse(shouldShowCoach(hiddenForever = false, shownThisVisit = true, hasProfile = true, likedYou = false))
        assertFalse(shouldShowCoach(hiddenForever = false, shownThisVisit = false, hasProfile = false, likedYou = false))
        assertFalse(shouldShowCoach(hiddenForever = false, shownThisVisit = false, hasProfile = true, likedYou = true))
    }

    @Test
    fun `the coach rows mirror the three buttons, in their order`() {
        assertEquals(listOf("Not interested", "Not now", "Like"), CoachRow.entries.map { it.title })
        assertEquals(listOf(BrandIcon.Close, BrandIcon.Clock, BrandIcon.Heart), CoachRow.entries.map { it.icon })
    }
}
