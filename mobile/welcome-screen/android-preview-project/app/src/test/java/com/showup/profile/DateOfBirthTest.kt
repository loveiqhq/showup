/*
 * DateOfBirthTest.kt
 * ShowUp · the date rules, without a device
 *
 * The value this screen writes is LOCKED — there is no in-app path to change an age once it is
 * stored. So a date that parses wrongly is not a bug that gets fixed on the next screen; it is a
 * support ticket. These assert the rules that keep that from happening.
 */
package com.showup.profile

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateOfBirthTest {

    /** A fixed "today" so the expected ages never drift: 18 May 2026, UTC. */
    private fun today(y: Int = 2026, m: Int = 5, d: Int = 18): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }

    private fun valid(r: DobResult): DobResult.Valid {
        assertTrue("expected a real date, got $r", r is DobResult.Valid)
        return r as DobResult.Valid
    }

    // ── formatting ────────────────────────────────────────────────────────────

    @Test
    fun `slashes are inserted, never typed`() {
        assertEquals("0", formatDob("0"))
        assertEquals("03", formatDob("03"))
        assertEquals("03/2", formatDob("032"))
        assertEquals("03/22", formatDob("0322"))
        assertEquals("03/22/1998", formatDob("03221998"))
    }

    @Test
    fun `a ninth digit is refused rather than silently dropped from the front`() {
        assertEquals("03/22/1998", formatDob("032219987"))
    }

    @Test
    fun `backspace removes a digit, and the slashes look after themselves`() {
        // What the screen does: strip to digits, drop one, re-format. The user never has to delete
        // a slash, which is the whole point of formatting rather than masking.
        val afterOneBackspace = formatDob(dobDigits("03/22/1998").dropLast(1))
        assertEquals("03/22/199", afterOneBackspace)
    }

    // ── the round trip ────────────────────────────────────────────────────────

    @Test
    fun `30 February is impossible, not silently 2 March`() {
        // The defect the round-trip check exists for. A lenient calendar rolls this over and the
        // user is locked to a birthday they never typed.
        assertEquals(DobResult.Impossible, parseDob("02/30/1990", DateOrder.MonthFirst, today()))
    }

    @Test
    fun `31 April is impossible`() {
        assertEquals(DobResult.Impossible, parseDob("04/31/1990", DateOrder.MonthFirst, today()))
    }

    @Test
    fun `29 February is real in a leap year and impossible in a common one`() {
        assertTrue(parseDob("02/29/1996", DateOrder.MonthFirst, today()) is DobResult.Valid)
        assertEquals(DobResult.Impossible, parseDob("02/29/1997", DateOrder.MonthFirst, today()))
    }

    @Test
    fun `month 13 and day 00 are refused before the calendar is consulted`() {
        assertEquals(DobResult.Impossible, parseDob("13/01/1990", DateOrder.MonthFirst, today()))
        assertEquals(DobResult.Impossible, parseDob("01/00/1990", DateOrder.MonthFirst, today()))
    }

    @Test
    fun `the year range is 1900 to this year`() {
        assertEquals(DobResult.Impossible, parseDob("01/01/1899", DateOrder.MonthFirst, today()))
        assertEquals(DobResult.Impossible, parseDob("01/01/2027", DateOrder.MonthFirst, today()))
        assertTrue(parseDob("01/01/1900", DateOrder.MonthFirst, today()) is DobResult.Valid)
    }

    @Test
    fun `fewer than eight digits is incomplete, not an error`() {
        // An error before the user has finished typing would fire on every keystroke.
        assertEquals(DobResult.Incomplete, parseDob("03/22/199", DateOrder.MonthFirst, today()))
        assertEquals(DobResult.Incomplete, parseDob("", DateOrder.MonthFirst, today()))
    }

    // ── locale order ──────────────────────────────────────────────────────────

    @Test
    fun `the same eight digits are two different dates in two locales`() {
        // 03/04 is the whole reason the product side ruled for device locale: it is valid in BOTH
        // readings, so a German user typing day-first into a US-ordered field gets a wrong date
        // that no validation can catch -- and the age is locked afterwards.
        val us = valid(parseDob("03/04/1998", DateOrder.MonthFirst, today()))
        val de = valid(parseDob("03/04/1998", DateOrder.DayFirst, today()))
        assertEquals("1998-03-04", us.iso)
        assertEquals("1998-04-03", de.iso)
    }

    @Test
    fun `a day-first locale accepts 22 in the first position`() {
        // 22/03 is impossible read month-first and fine read day-first.
        assertEquals(DobResult.Impossible, parseDob("22/03/1998", DateOrder.MonthFirst, today()))
        assertEquals("1998-03-22", valid(parseDob("22/03/1998", DateOrder.DayFirst, today())).iso)
    }

    @Test
    fun `the placeholder names the order actually being asked for`() {
        assertEquals("mm/dd/yyyy", DateOrder.MonthFirst.pattern)
        assertEquals("dd/mm/yyyy", DateOrder.DayFirst.pattern)
    }

    // ── age, in UTC, matching the server ──────────────────────────────────────

    @Test
    fun `age is whole years`() {
        assertEquals(28, valid(parseDob("03/22/1998", DateOrder.MonthFirst, today())).age)
    }

    @Test
    fun `the birthday not yet reached takes a year off`() {
        // Born 19 May, today 18 May: one day short.
        assertEquals(27, valid(parseDob("05/19/1998", DateOrder.MonthFirst, today())).age)
        // Born 18 May: it is the birthday, so the year counts.
        assertEquals(28, valid(parseDob("05/18/1998", DateOrder.MonthFirst, today())).age)
    }

    @Test
    fun `a leap-day birthday ages on 1 March in a common year`() {
        val feb28 = today(2026, 2, 28)
        val mar1 = today(2026, 3, 1)
        assertEquals(29, valid(parseDob("02/29/1996", DateOrder.MonthFirst, feb28)).age)
        assertEquals(30, valid(parseDob("02/29/1996", DateOrder.MonthFirst, mar1)).age)
    }

    @Test
    fun `the day before turning eighteen is under the gate, and the day of is not`() {
        // The boundary the server also enforces. Getting this off by one either blocks an adult or
        // admits a minor, and the second is the one that matters.
        val dayBefore = valid(parseDob("05/19/2008", DateOrder.MonthFirst, today()))
        val onTheDay = valid(parseDob("05/18/2008", DateOrder.MonthFirst, today()))
        assertTrue(dayBefore.age < MINIMUM_AGE)
        assertTrue(onTheDay.age >= MINIMUM_AGE)
    }

    @Test
    fun `the iso string is what the backend asks for`() {
        // UpsertProfileDto.dateOfBirth is documented "YYYY-MM-DD", zero-padded.
        assertEquals("1998-03-04", valid(parseDob("03/04/1998", DateOrder.MonthFirst, today())).iso)
        assertEquals("1900-01-01", valid(parseDob("01/01/1900", DateOrder.MonthFirst, today())).iso)
    }

    @Test
    fun `the minimum age is the server's`() {
        // If these ever disagree the client shows an age card for a date the server will refuse.
        assertEquals(18, MINIMUM_AGE)
    }
}
