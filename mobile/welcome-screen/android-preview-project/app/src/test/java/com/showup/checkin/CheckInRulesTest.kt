/*
 * CheckInRulesTest.kt
 * ShowUp · every rule the six Check-In screens decide, with no UI (SHOWUP-178 to SHOWUP-183)
 *
 * The cases are the tickets' own wherever a ticket gives one -- 4:20pm starts Today at 5pm, `6–9pm`,
 * `12–2pm · 7–8pm`, `Any age`, `1 character left` -- and the ids are checked against the tracking
 * registry file itself, so a renamed value fails here before it orphans a warehouse join.
 */
package com.showup.checkin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CheckInRulesTest {

    private fun at(h: Int, m: Int, day: Int = 13) = LocalDateTime.of(2026, 10, day, h, m)

    // ── the 7am day and the next full hour (02) ─────────────────────────────────────────────────

    @Test
    fun `at 4-20pm Today starts at 5pm and Tomorrow at 7am, both ending at the 6am slot`() {
        val days = checkInDays(at(16, 20))
        assertEquals(LocalDate.of(2026, 10, 13), days.todayDate)
        assertEquals(17, days.todayStartHour)
        assertEquals(listOf(17, 18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5, 6), availabilityHours(days.todayStartHour))
        assertEquals("14 slots at 4:20, as the sheet says", 14, availabilityHours(17).size)
        val tomorrow = availabilityHours(CHECK_IN_DAY_START)
        assertEquals(24, tomorrow.size)
        assertEquals(7, tomorrow.first())
        assertEquals(6, tomorrow.last())
    }

    @Test
    fun `an hour that has started is never offered`() {
        // 4:00 sharp: 4pm has started, so Today still starts at 5pm.
        assertEquals(17, checkInDays(at(16, 0)).todayStartHour)
        assertEquals(17, checkInDays(at(16, 59)).todayStartHour)
    }

    @Test
    fun `night hours belong to the evening before`() {
        // 1:20am: Today is 2am-6am of the block that began yesterday at 7am.
        val night = checkInDays(at(1, 20))
        assertEquals(LocalDate.of(2026, 10, 12), night.todayDate)
        assertEquals(2, night.todayStartHour)
        assertEquals(listOf(2, 3, 4, 5, 6), availabilityHours(2))
        // 11:20pm: the next full hour is midnight, still the evening's block.
        val late = checkInDays(at(23, 20))
        assertEquals(LocalDate.of(2026, 10, 13), late.todayDate)
        assertEquals(0, late.todayStartHour)
        // 5:20am: one slot left, 6am.
        assertEquals(listOf(6), availabilityHours(checkInDays(at(5, 20)).todayStartHour))
    }

    @Test
    fun `at 6-20am Today is the new day from 7am`() {
        val days = checkInDays(at(6, 20))
        assertEquals(LocalDate.of(2026, 10, 13), days.todayDate)
        assertEquals(7, days.todayStartHour)
        assertEquals(24, availabilityHours(7).size)
    }

    @Test
    fun `a grid's starts put the small hours on the next calendar date`() {
        val grid = gridOf(CheckInStep.Today, checkInDays(at(16, 20)))
        assertEquals(at(17, 0), grid.starts.first())
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), grid.starts[grid.hours.indexOf(0)])
        assertEquals(LocalDateTime.of(2026, 10, 14, 6, 0), grid.starts.last())
        val tomorrow = gridOf(CheckInStep.Tomorrow, checkInDays(at(16, 20)))
        assertEquals(LocalDateTime.of(2026, 10, 14, 7, 0), tomorrow.starts.first())
        assertEquals(LocalDateTime.of(2026, 10, 15, 6, 0), tomorrow.starts.last())
    }

    @Test
    fun `a slot start is an instant in the device's zone, across a DST change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 25 Oct 2026, the night the clocks go back: 2am happens twice; the first is +02:00.
        val start = slotStart(LocalDate.of(2026, 10, 24), 2, berlin)
        assertEquals(LocalDate.of(2026, 10, 25), start.toLocalDate())
        assertEquals(2, start.hour)
    }

    // ── slots, labels and the summary (02) ──────────────────────────────────────────────────────

    @Test
    fun `a tap cycles idle, preferred, available, idle`() {
        assertEquals(SlotTier.Preferred, cycleSlot(null))
        assertEquals(SlotTier.Available, cycleSlot(SlotTier.Preferred))
        assertNull(cycleSlot(SlotTier.Available))
    }

    @Test
    fun `hour labels are 12-hour with noon and 12am`() {
        assertEquals("7am", formatHour(7))
        assertEquals("11am", formatHour(11))
        assertEquals("noon", formatHour(12))
        assertEquals("1pm", formatHour(13))
        assertEquals("11pm", formatHour(23))
        assertEquals("12am", formatHour(0))
        assertEquals("6am", formatHour(6))
    }

    @Test
    fun `the summary reads the sheet's ranges`() {
        val today = availabilityHours(17)
        val picked = mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Preferred, 20 to SlotTier.Available)
        assertEquals("6–9pm", availabilitySummary(today, picked))
        val tomorrow = availabilityHours(7)
        val d = mapOf(12 to SlotTier.Preferred, 13 to SlotTier.Preferred, 19 to SlotTier.Available)
        assertEquals("12–2pm · 7–8pm", availabilitySummary(tomorrow, d))
        assertNull("nothing selected yet is the screen's, not a range", availabilitySummary(today, emptyMap()))
    }

    @Test
    fun `a run across midnight stays one range, and tiers are not distinguished`() {
        val today = availabilityHours(17)
        assertEquals("11pm–2am", availabilitySummary(today, mapOf(23 to SlotTier.Available, 0 to SlotTier.Preferred, 1 to SlotTier.Available)))
        assertEquals("5–7am", availabilitySummary(today, mapOf(5 to SlotTier.Preferred, 6 to SlotTier.Preferred)))
        assertEquals("11am–12pm", availabilitySummary(availabilityHours(7), mapOf(11 to SlotTier.Preferred)))
    }

    @Test
    fun `past four runs the summary says how many are hidden`() {
        val tomorrow = availabilityHours(7)
        val four = listOf(7, 9, 11, 13).associateWith { SlotTier.Preferred }
        assertEquals("7–8am · 9–10am · 11am–12pm · 1–2pm", availabilitySummary(tomorrow, four))
        val six = listOf(7, 9, 11, 13, 15, 17).associateWith { SlotTier.Preferred }
        assertEquals("7–8am · 9–10am · 11am–12pm · +3 more", availabilitySummary(tomorrow, six))
    }

    @Test
    fun `tier counts feed availability_slots_set`() {
        val picked = mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Preferred, 20 to SlotTier.Available)
        assertEquals(2, tierCount(picked, SlotTier.Preferred))
        assertEquals(1, tierCount(picked, SlotTier.Available))
    }

    // ── vibes (04) ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `at five an idle chip selects nothing and a picked chip still deselects`() {
        val five = setOf("coffee_chat", "sunset", "calm", "playful", "no_rush")
        assertEquals(VibeTap.Limit, tapVibe(five, "cozy"))
        assertEquals(VibeTap.Changed(five - "calm"), tapVibe(five, "calm"))
        assertEquals(VibeTap.Changed(setOf("calm")), tapVibe(emptySet(), "calm"))
    }

    @Test
    fun `vibe_ids is list order, never tap order`() {
        assertEquals("coffee_chat,playful,no_rush", vibeIds(linkedSetOf("no_rush", "playful", "coffee_chat")))
    }

    @Test
    fun `twenty vibes in three groups, the labels the ticket gives`() {
        assertEquals(listOf(8, 6, 6), VibeGroup.entries.map { it.vibes.size })
        assertEquals(20, VIBE_ORDER.toSet().size)
        assertEquals("Free evening (no rush)", VIBE_LABEL["no_rush"])
        assertEquals(listOf("What you want to do", "How you feel right now", "Your energy & pacing"), VibeGroup.entries.map { it.label })
    }

    // ── filters (05) ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the age hint reads Any age, n+ and a range`() {
        assertEquals("Any age", ageLabel(18, 65))
        assertEquals("30+", ageLabel(30, 65))
        assertEquals("25 – 35", ageLabel(25, 35))
        assertEquals("18 – 40", ageLabel(18, 40))
        assertEquals("one age: the thumbs may meet", "30 – 30", ageLabel(30, 30))
    }

    @Test
    fun `65 is no upper limit and is sent as null`() {
        assertNull(CheckInFilters(setOf("woman")).maxAgeOrNull)
        assertEquals(64, CheckInFilters(setOf("woman"), maxAge = 64).maxAgeOrNull)
    }

    @Test
    fun `thumbs never cross -- dragging one past the other carries it`() {
        val f = CheckInFilters(setOf("woman"), minAge = 25, maxAge = 35)
        assertEquals(CheckInFilters(setOf("woman"), 40, 40), moveAgeThumb(f, 0, 40))
        assertEquals(CheckInFilters(setOf("woman"), 20, 20), moveAgeThumb(f, 1, 20))
        assertEquals(CheckInFilters(setOf("woman"), 18, 35), moveAgeThumb(f, 0, 5))
        assertEquals(CheckInFilters(setOf("woman"), 25, 65), moveAgeThumb(f, 1, 99))
    }

    @Test
    fun `filters_changed names the changed filters in registry order, or none`() {
        val entry = CheckInFilters(setOf("woman"))
        assertEquals("none", filtersChanged(entry, entry))
        assertEquals("distance_km", filtersChanged(entry, entry.copy(distanceKm = 15)))
        assertEquals(
            "meet_gender,age_range,distance_km",
            filtersChanged(entry, CheckInFilters(setOf("woman", "non_binary"), 25, 35, 15)),
        )
        assertEquals("age_range", filtersChanged(entry, entry.copy(maxAge = 64)))
    }

    // ── uplift (06) ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an emoji, a flag and a family each count as one character`() {
        assertEquals(1, graphemeCount("😀"))
        assertEquals(1, graphemeCount("🇩🇪"))
        assertEquals(1, graphemeCount("👨‍👩‍👧"))
        assertEquals(5, graphemeCount("hello"))
    }

    @Test
    fun `input past 100 is not accepted and a paste is cut at 100 graphemes`() {
        val long = "a".repeat(150)
        assertEquals(100, clampUplift(long).length)
        val emoji = "😀".repeat(120)
        val cut = clampUplift(emoji)
        assertEquals(100, graphemeCount(cut))
        assertEquals("an emoji is never split in half", 200, cut.length)
        assertEquals("short", clampUplift("short"))
    }

    @Test
    fun `the counter counts down and says character at one`() {
        assertEquals("100 characters left", upliftCounterLabel(""))
        assertEquals("1 character left", upliftCounterLabel("a".repeat(99)))
        assertEquals("0 characters left", upliftCounterLabel("a".repeat(100)))
        assertEquals("44 characters left", upliftCounterLabel("Text me on 0176 2345 6789 and we'll grab a drink tonight"))
    }

    @Test
    fun `emails, phone numbers and links are caught`() {
        listOf(
            "Text me on 0176 2345 6789",
            "+49 176 23456789",
            "call (030) 123-4567",
            "0176/2345678",
            "mail me leo@hey.com",
            "LEO@HEY.DE",
            "https://show.up/me",
            "www.leo.com",
            "find me at leo.de",
            "insta.com/leo",
        ).forEach { assertTrue(it, upliftHasContact(it)) }
    }

    @Test
    fun `ordinary uplifts pass`() {
        listOf(
            "",
            "will take you out on my boat on Wannsee",
            "have 2x tickets for Eminem today",
            "123456 is six digits",
            "meet at 7.30 by the U2",
            "If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)",
        ).forEach { assertFalse(it, upliftHasContact(it)) }
    }

    @Test
    fun `answer_value is written or empty, never the text`() {
        assertEquals("empty", upliftAnswerValue(""))
        assertEquals("empty", upliftAnswerValue("   \n "))
        assertEquals("written", upliftAnswerValue(" boat "))
    }

    // ── the registry, read from the file ────────────────────────────────────────────────────────

    private val enums: JsonObject by lazy {
        Json.parseToJsonElement(File("../../../../design_handoff_showup/tracking/enums.json").readText()).jsonObject
    }

    /** The values of enums.json set [number], in order; a grouped set's groups flattened in order. */
    private fun section(number: String): List<String> {
        val set = enums["sets"]!!.jsonArray.map { it.jsonObject }.first { it["num"]?.jsonPrimitive?.content == number }
        set["values"]?.let { values -> return values.jsonArray.map { it.jsonPrimitive.content } }
        return set["groups"]!!.jsonObject.values.flatMap { group -> group.jsonArray.map { it.jsonPrimitive.content } }
    }

    @Test
    fun `motivation, vibe and gender ids are the registry's`() {
        assertEquals(section("30"), MotivationOption.entries.map { it.id })
        assertEquals(section("31"), VIBE_ORDER)
        assertEquals(listOf("woman", "man", "non_binary", "other"), CheckInGender.entries.map { it.id })
    }

    @Test
    fun `step ids, gate targets, no-hours actions and commit failures are the registry's`() {
        assertEquals(section("28"), CheckInStep.entries.map { it.stepId })
        assertEquals(section("27"), CheckInAnalytics.GateTarget.entries.map { it.wire })
        assertEquals(section("29"), CheckInAnalytics.NoHoursAction.entries.map { it.wire })
        assertEquals(section("32"), CheckInAnalytics.CommitFailure.entries.map { it.wire })
    }
}
