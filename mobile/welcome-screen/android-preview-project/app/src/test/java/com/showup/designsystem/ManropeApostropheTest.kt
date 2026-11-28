/*
 * ManropeApostropheTest.kt
 * ShowUp · Android curls Manrope's apostrophe at a word end, where Chrome and iOS curl it
 *
 * Every copy string carries a straight U+0027. Manrope's own `calt` feature draws it as the curly
 * U+2019 glyph in some contexts: inside a word ("you're", "I'm", "don't"), and before "s" or after
 * "em" when a SPACE follows. Chrome and Core Text shape a whole run, see the space, and draw
 * "It’s now closed." curly. Android's Minikin shapes word by word and gives HarfBuzz each word
 * without the space after it, so the two space rules never matched: the spec sheet and iOS showed
 * "It’s", "other’s", "it’s", and Android "It's", "other's", "it's" (Date 08, 27 Nov 2026).
 *
 * Android's copies of the font carry one extra lookup that applies those two rules at the end of a
 * Minikin word, and nowhere else (fonts/README-android-apostrophe.md). Robolectric's native graphics
 * run the real Minikin and HarfBuzz, so these draw real glyphs and compare pixels: the straight
 * apostrophe must draw exactly as the curly character where Chrome curls it, and differently where
 * Chrome leaves it straight.
 */
package com.showup.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.showup.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// sdk 34: Robolectric 4.14.1 ships images up to 35 and the project targets 36, the same pin
// IconStrokeTest and the evidence harness carry.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ManropeApostropheTest {

    // Every weight the app's `Manrope` family loads: each file is patched separately, and the
    // Medium, SemiBold and Bold files route the rule through different nested lookups than
    // Regular and ExtraBold, so one weight passing says nothing about another.
    private val weights = mapOf(
        "regular" to R.font.manrope_regular,
        "medium" to R.font.manrope_medium,
        "semibold" to R.font.manrope_semibold,
        "bold" to R.font.manrope_bold,
        "extrabold" to R.font.manrope_extrabold,
    )

    private val curly = '’'

    /**
     * The text drawn in black on white, as its pixels. Canvas.drawText goes through the same Minikin
     * layout Compose's text does, word pieces included; 40 px makes the two glyphs' shapes differ
     * by hundreds of pixels, not by an antialiasing shade.
     */
    private fun pixels(text: String, font: Int): IntArray {
        val typeface: Typeface = RuntimeEnvironment.getApplication().resources.getFont(font)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 40f
            color = Color.BLACK
        }
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(text, 8f, 56f, paint)
        }
        return IntArray(W * H).also { bitmap.getPixels(it, 0, W, 0, 0, W, H) }
    }

    private fun differing(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }

    private fun assertDrawsAsCurly(straight: String) {
        val expected = straight.replace('\'', curly)
        for ((weight, font) in weights) {
            val off = differing(pixels(straight, font), pixels(expected, font))
            assertTrue("\"$straight\" in Manrope $weight differs from \"$expected\" by $off px", off == 0)
        }
    }

    private fun assertStaysStraight(straight: String) {
        val curled = straight.replace('\'', curly)
        for ((weight, font) in weights) {
            assertFalse(
                "\"$straight\" in Manrope $weight draws exactly as \"$curled\" -- Chrome keeps it straight",
                pixels(straight, font).contentEquals(pixels(curled, font)),
            )
        }
    }

    @Test
    fun `the comparison can see a match - you're is curly on Android already`() {
        // The rule inside a word needs no space, so Minikin always applied it. Without this the two
        // "stays straight" tests below could pass because the drawing never matches anything.
        assertDrawsAsCurly("you're")
    }

    @Test
    fun `before s at a word end the apostrophe curls, as Chrome and iOS draw It's now`() {
        assertDrawsAsCurly("It's now")
    }

    @Test
    fun `Date 08's own lines curl where the spec sheet curls them`() {
        assertDrawsAsCurly("It's now closed.")
        assertDrawsAsCurly("no respect for each other's time.")
        assertDrawsAsCurly("act on it where it's needed.")
    }

    @Test
    fun `after em at a word end the apostrophe curls too, the font's second space rule`() {
        assertDrawsAsCurly("them' now")
    }

    @Test
    fun `you've stays straight, as Chrome draws it - the font has no rule for it`() {
        assertStaysStraight("you've")
    }

    @Test
    fun `a comma after the s keeps it straight, as Chrome draws it's, with no space after`() {
        // The comma is inside Minikin's word, so the patch must not read the end of the word into it.
        assertStaysStraight("it's,")
        assertStaysStraight("them',")
    }

    private companion object {
        const val W = 760
        const val H = 80
    }
}
