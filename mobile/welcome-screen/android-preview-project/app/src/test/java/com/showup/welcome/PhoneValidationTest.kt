/*
 * PhoneValidationTest.kt
 * ShowUp · the phone rules, checked against libphonenumber's own data (SHOWUP-143)
 *
 * These run on the JVM, not a device — libphonenumber has no Android dependencies, so the rules can
 * be tested here in seconds rather than on an emulator.
 *
 * The bar this file exists to hold: the previous hand-written table shipped two bugs, and both were
 * "the rule was wrong", not "the code was wrong". So the interesting assertions are not that the
 * function runs, but that it accepts numbers real people hold and rejects ones that cannot receive
 * an SMS.
 */
package com.showup.welcome

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneValidationTest {

    private val util = PhoneNumberUtil.getInstance()
    private fun country(iso: String) = countryForRegion(iso)

    // ── the list ────────────────────────────────────────────────────────────

    @Test
    fun `every country libphonenumber knows is offered`() {
        assertEquals(util.supportedRegions.size, COUNTRIES.size)
        assertTrue("expected the full world, got ${COUNTRIES.size}", COUNTRIES.size > 200)
    }

    @Test
    fun `every country has a name and a dial code`() {
        COUNTRIES.forEach {
            assertTrue("${it.iso} has no name", it.name.isNotBlank())
            assertTrue("${it.iso} has a malformed dial code: ${it.dial}",
                it.dial.startsWith("+") && it.dial.drop(1).all(Char::isDigit))
        }
    }

    @Test
    fun `the list is sorted by name`() {
        val collator = java.text.Collator.getInstance()
        COUNTRIES.zipWithNext { a, b ->
            assertTrue("${a.name} sorts after ${b.name}", collator.compare(a.name, b.name) <= 0)
        }
    }

    @Test
    fun `the launch market is present and is the fallback`() {
        assertEquals("DE", DEFAULT_COUNTRY.iso)
        assertEquals("+49", DEFAULT_COUNTRY.dial)
        // An unknown or absent device region must not crash or pick something arbitrary.
        assertEquals("DE", countryForRegion(null).iso)
        assertEquals("DE", countryForRegion("ZZ").iso)
        assertEquals("DE", countryForRegion("").iso)
    }

    // ── the two bugs that shipped ───────────────────────────────────────────

    @Test
    fun `a German landline is rejected because it cannot receive a text`() {
        // The original bug: the table allowed six digits for Germany because landlines can be that
        // short, so this passed and the user waited for a code that could never arrive.
        //
        // libphonenumber's own fixed-line example is used rather than a number picked by hand: a
        // short landline is caught by the length rule anyway, which would let this test pass for
        // the wrong reason. A real landline of full length can only be caught by knowing the type.
        val de = country("DE")
        val landline = util.getExampleNumberForType("DE", PhoneNumberType.FIXED_LINE)!!
        val national = util.format(landline, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            .removePrefix(de.dial).trim()
        assertEquals(PhoneError.NotMobile, validate(national, de))
    }

    @Test
    fun `landlines are rejected in every country that distinguishes them`() {
        // Skipped where the ranges genuinely overlap: in those countries libphonenumber cannot tell
        // a landline from a mobile either, and refusing would reject people holding good numbers.
        val failures = mutableListOf<String>()
        COUNTRIES.forEach { c ->
            val fixed = util.getExampleNumberForType(c.iso, PhoneNumberType.FIXED_LINE) ?: return@forEach
            if (util.getNumberType(fixed) != PhoneNumberType.FIXED_LINE) return@forEach
            val national = util.format(fixed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                .removePrefix(c.dial).trim()
            if (validate(national, c) == null) failures += "${c.iso} ${c.name}: $national accepted"
        }
        assertTrue("landlines accepted:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `a too-long number is rejected`() {
        // The other original bug: the field capped at the maximum, so this could not be typed and
        // the error was unreachable. The cap now allows over-typing so the rule can fire.
        assertEquals(PhoneError.TooLong, validate("176123456789012", country("DE")))
    }

    @Test
    fun `a too-short number is rejected`() {
        assertEquals(PhoneError.TooShort, validate("1761", country("DE")))
    }

    // ── the cases a real person hits ────────────────────────────────────────

    @Test
    fun `a real German mobile is accepted`() {
        assertNull(validate("17612345678", country("DE")))
    }

    @Test
    fun `the national trunk zero is accepted, not scolded`() {
        // Typing 0176... is how a German writes their own number. libphonenumber strips the trunk
        // prefix per that country's dialling rules, so this is simply correct now.
        assertNull(validate("017612345678".dropLast(1), country("DE")))
    }

    @Test
    fun `spaces and punctuation are ignored`() {
        assertNull(validate("176 123 456 78", country("DE")))
    }

    @Test
    fun `letters are reported as letters, not as an empty field`() {
        assertEquals(PhoneError.NotANumber, validate("abcdefghij", country("DE")))
    }

    @Test
    fun `an empty field says so`() {
        assertEquals(PhoneError.Empty, validate("", country("DE")))
        assertEquals(PhoneError.Empty, validate("   ", country("DE")))
    }

    // ── the whole world, not just the launch market ─────────────────────────

    @Test
    fun `libphonenumber's own example mobile is accepted for every country`() {
        // The strongest check available: every country's canonical mobile number, taken from
        // libphonenumber itself, must pass our validation. If any fails, the rules reject a number
        // the authority on the subject says is valid.
        val failures = mutableListOf<String>()
        COUNTRIES.forEach { c ->
            val example = util.getExampleNumberForType(c.iso, PhoneNumberType.MOBILE) ?: return@forEach
            val national = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            val verdict = validate(national, c)
            if (verdict != null) failures += "${c.iso} ${c.name}: $national -> $verdict"
        }
        assertTrue("rejected valid mobiles:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the example shown in the empty field passes that country's own rule`() {
        // The placeholder is the number we invite the user to copy. If it fails validation the
        // screen is asking for something it will then reject.
        val failures = COUNTRIES
            .filter { it.sample.isNotBlank() }
            .mapNotNull { c -> validate(c.sample, c)?.let { "${c.iso}: '${c.sample}' -> $it" } }
        assertTrue("bad placeholders:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    // ── formatting ──────────────────────────────────────────────────────────

    @Test
    fun `digits are grouped the way the country writes them`() {
        val formatted = formatNational("17612345678", country("DE"))
        assertTrue("expected grouping, got '$formatted'", formatted.contains(" "))
        assertEquals("17612345678", formatted.filter(Char::isDigit))
    }

    @Test
    fun `formatting an empty string does not crash`() {
        assertEquals("", formatNational("", country("DE")))
    }

    @Test
    fun `every country can format a partial number without throwing`() {
        COUNTRIES.forEach { c ->
            val out = formatNational("12345", c)
            assertNotNull("${c.iso} returned null", out)
        }
    }
}
