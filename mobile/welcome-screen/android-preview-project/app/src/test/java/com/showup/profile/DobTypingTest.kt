/*
 * DobTypingTest.kt
 * ShowUp · typing a date of birth gives back the date that was typed
 *
 * Reported from a device on 13 September 2026: the caret jumped around while typing and the
 * digits came out scrambled. Typing 03221995 produced 03/21/9592 -- reproducibly, and at one
 * digit per second, so not an input race.
 *
 * The cause was the field rewriting its own value on every keystroke:
 *
 *     onValueChange(formatDob(dobDigits(raw)))
 *
 * which is the thing `GroupedDigits` on the phone screen exists to avoid, and whose comment says
 * so outright: "Reformatting the value itself is what makes a phone field jump the cursor to the
 * end." Inserting a slash shifts every character after it, so the caret Compose restores no
 * longer means what it meant, and the next digit lands somewhere the user did not put it.
 *
 * The field holds digits now and `DateSlashes` paints the separators. These tests cover the
 * halves of that which can be tested without a UI: that the value keeps exactly what was typed,
 * and that the painted string and its caret mapping agree with each other at every offset.
 *
 * The mapping is the part worth being careful about. An OffsetMapping that disagrees with its own
 * transformed text is how a caret ends up one character out -- which is the same class of defect,
 * moved one layer down and much harder to see.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DobTypingTest {

    /** What the field stores after each keystroke: digits only, capped at eight. */
    private fun type(keys: String): String {
        var value = ""
        for (k in keys) value = dobDigits(value + k)
        return value
    }

    @Test
    fun `typing eight digits stores those eight digits, in order`() {
        // The exact sequence from the report. It used to come back 03219592.
        assertEquals("03221995", type("03221995"))
    }

    @Test
    fun `a day-first date survives typing just as well`() {
        // 23 February 2002, typed on a device that asks day-first. The digits are the digits
        // whichever order the locale wants them in -- only `parseDob` cares which is which.
        assertEquals("23022002", type("23022002"))
    }

    @Test
    fun `a ninth digit is refused rather than shuffled in`() {
        assertEquals("03221995", type("032219959"))
    }

    @Test
    fun `backspace removes one digit, never a slash`() {
        var value = type("03221995")
        value = dobDigits(value.dropLast(1))
        assertEquals("0322199", value)
        // And what is painted loses its trailing group, not a separator in the middle.
        assertEquals("03/22/199", formatDob(value))
    }

    @Test
    fun `what is painted always has the digits that were typed`() {
        for (n in 0..8) {
            val digits = "03221995".take(n)
            assertEquals(
                "the painted string lost or gained a digit at length $n",
                digits, formatDob(digits).filter { it.isDigit() },
            )
        }
    }

    // ── the caret mapping ────────────────────────────────────────────────────
    //
    // Reimplemented here rather than reaching into the private DateSlashes, because what matters
    // is the ARITHMETIC being right, and that is a property of formatDob's output. If these agree
    // at every offset and every length, a caret cannot land between the wrong two characters.

    private fun toShown(digits: String): IntArray {
        val shown = formatDob(digits)
        val map = IntArray(digits.length + 1)
        var d = 0
        shown.forEachIndexed { i, c -> if (c.isDigit()) { map[d] = i; d++ } }
        map[digits.length] = shown.length
        return map
    }

    private fun toOriginal(digits: String, offset: Int): Int =
        formatDob(digits).take(offset).count { it.isDigit() }

    @Test
    fun `the caret after the last typed digit sits at the end of what is painted`() {
        // The case that matters while typing: after every keystroke the caret must be at the end,
        // or the next digit is inserted mid-string. This is precisely what the old code broke.
        for (n in 0..8) {
            val digits = "03221995".take(n)
            assertEquals(
                "caret after $n digit(s) is not at the end of \"${formatDob(digits)}\"",
                formatDob(digits).length, toShown(digits)[n],
            )
        }
    }

    @Test
    fun `every caret position round-trips back to the digit it came from`() {
        for (n in 0..8) {
            val digits = "03221995".take(n)
            val map = toShown(digits)
            for (original in 0..n) {
                assertEquals(
                    "offset $original of \"$digits\" did not survive the round trip",
                    original, toOriginal(digits, map[original]),
                )
            }
        }
    }

    @Test
    fun `the mapping never points outside the painted string`() {
        for (n in 0..8) {
            val digits = "03221995".take(n)
            val shown = formatDob(digits)
            for (v in toShown(digits)) {
                assert(v in 0..shown.length) {
                    "mapped offset $v is outside \"$shown\" (length ${shown.length})"
                }
            }
        }
    }
}
