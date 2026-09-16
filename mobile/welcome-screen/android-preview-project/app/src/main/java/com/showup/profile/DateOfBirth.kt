/*
 * DateOfBirth.kt
 * ShowUp · parsing, formatting and ageing a date of birth (SHOWUP-154)
 *
 * No UI in this file, on purpose. Every rule here is one the product side settled and one the
 * backend also enforces, so it is written where it can be tested without a device and read without
 * a screenshot.
 *
 * TWO DECISIONS THAT OVERRIDE THE REFERENCE FILE
 *
 * 1. AGE IS COMPUTED IN UTC.  The ticket and the reference JSX both say "local time, not UTC".
 *    The product side ruled UTC on 10 September 2026, and that is the right call for a reason the
 *    ticket could not have known: the server already computes in UTC (`ageFromDateOfBirth` parses
 *    `T00:00:00Z` and uses `getUTC*`), and it REJECTS an under-18 date with a 400. A client
 *    computing locally would, for a user west of UTC on their eighteenth birthday, show the age
 *    card and then be refused by the server -- a failure with no message designed for it. One
 *    basis, and it has to be the server's, because the server has the final say.
 *
 * 2. THE FIELD ORDER FOLLOWS THE DEVICE LOCALE.  The ticket hard-codes `mm/dd/yyyy` and lists the
 *    consequence in its own open questions: a German user typing 03/04 gets a valid WRONG date,
 *    and age is locked afterwards so it cannot be corrected in-app. The product side ruled for
 *    device locale. [DateOrder] carries it, the placeholder is generated from it, and the two
 *    error strings name whichever order the user is actually being asked for.
 */
package com.showup.profile

import java.util.Calendar
import java.util.TimeZone

/** Which of day and month the user's locale asks for first. The year is always last. */
enum class DateOrder {
    MonthFirst,
    DayFirst;

    /** The placeholder and the mono chip in both error strings are generated from this. */
    val pattern: String
        get() = if (this == MonthFirst) "mm/dd/yyyy" else "dd/mm/yyyy"
}

/** What eight digits turned out to be. */
sealed interface DobResult {
    /** Fewer than eight digits: not an answer yet, and not an error until Continue is pressed. */
    data object Incomplete : DobResult

    /** Eight digits that do not name a day that exists — 30 February, or month 13. */
    data object Impossible : DobResult

    /** A real date. [age] is whole years in UTC; [iso] is what the backend wants. */
    data class Valid(val age: Int, val iso: String) : DobResult
}

/**
 * The digits behind the display string, at most eight.
 *
 * Slashes are inserted by [format] and never typed, so everything else in this file works on the
 * digits alone -- which is also why backspace deletes a DIGIT and the slashes look after
 * themselves.
 */
fun dobDigits(display: String): String = display.filter { it.isDigit() }.take(8)

/**
 * Group digits for display: `03221998` -> `03/22/1998`.
 *
 * Order-independent, because the first two digits are whatever the locale asked for first. Only
 * complete groups get a trailing slash, so the caret never sits after a slash the user did not
 * type.
 */
fun formatDob(digits: String): String {
    val d = dobDigits(digits)
    return listOfNotNull(
        d.take(2).ifEmpty { null },
        d.drop(2).take(2).ifEmpty { null },
        d.drop(4).take(4).ifEmpty { null },
    ).joinToString("/")
}

/**
 * Read eight digits as a date, in the order this locale asks for them.
 *
 * VALIDITY IS A ROUND TRIP, NOT A PATTERN. A regex accepts 02/30/1990 and a calendar quietly
 * turns it into 2 March, which would lock a user to a birthday they never typed. So the date is
 * built and then asked what it became: if it does not report back the same three numbers, the day
 * did not exist.
 */
fun parseDob(display: String, order: DateOrder, today: Calendar = utcToday()): DobResult {
    val d = dobDigits(display)
    if (d.length < 8) return DobResult.Incomplete

    val first = d.take(2).toInt()
    val second = d.drop(2).take(2).toInt()
    val year = d.drop(4).take(4).toInt()
    val month = if (order == DateOrder.MonthFirst) first else second
    val day = if (order == DateOrder.MonthFirst) second else first

    // Cheap range guard first, so the calendar is never asked about month 47.
    if (month !in 1..12 || day !in 1..31) return DobResult.Impossible
    if (year < 1900 || year > today.get(Calendar.YEAR)) return DobResult.Impossible

    val cal = Calendar.getInstance(UTC).apply {
        isLenient = true // so an impossible date rolls over rather than throwing, and is caught below
        clear()
        set(year, month - 1, day)
    }
    val roundTrips = cal.get(Calendar.YEAR) == year &&
        cal.get(Calendar.MONTH) == month - 1 &&
        cal.get(Calendar.DAY_OF_MONTH) == day
    if (!roundTrips) return DobResult.Impossible

    return DobResult.Valid(
        age = ageOn(today, year, month, day),
        iso = "%04d-%02d-%02d".format(year, month, day),
    )
}

/**
 * Whole years, decremented when this year's birthday has not arrived.
 *
 * Mirrors the server's `ageFromDateOfBirth` exactly, including its basis: both count in UTC. 29
 * February needs no special case -- someone born on a leap day has their birthday "arrive" on
 * 1 March in a common year, because 2 < 29 compares as not-yet.
 */
internal fun ageOn(today: Calendar, year: Int, month: Int, day: Int): Int {
    var age = today.get(Calendar.YEAR) - year
    val monthNow = today.get(Calendar.MONTH) + 1
    val dayNow = today.get(Calendar.DAY_OF_MONTH)
    if (monthNow < month || (monthNow == month && dayNow < day)) age -= 1
    return age
}

/** The minimum the product allows, and the same number `profiles.service.ts` refuses below. */
const val MINIMUM_AGE = 18

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

/** Today, in UTC — the basis the server uses, and therefore the only one worth agreeing with. */
fun utcToday(): Calendar = Calendar.getInstance(UTC)
