/*
 * BalancedWidthTest.kt
 * ShowUp · `text-wrap: balance` never narrows below the widest unbreakable piece, so no word splits
 *
 * Below a word's width -- or an em run's, its spaces no-break -- Android's line breaker breaks INSIDE it rather than
 * overflow, and often at the same line count, so a search for "the narrowest width that keeps the line count" settled
 * there: Date 08 B drew `didn't feel comforta` / `ble in the situation.` on every phone (SHOWUP-199, 22 Nov 2026). CSS
 * never narrows a balanced block below its min-content width. Each case sweeps widths, proves the old search went below
 * the piece at some of them (or the case proves nothing), and checks that wherever the old search stayed above it the
 * answer is unchanged, to the pixel.
 */
package com.showup.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class BalancedWidthTest {

    /** Date 08's headline type: Lora 700 27 / 1.15, -0.01em. */
    private val style = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = (27 * 1.15).sp, letterSpacing = (-0.01).em)

    /** The washed run as `WashHeadline` builds it: Lora 500 italic, its spaces no-break. */
    private val run = buildAnnotatedString {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)) { append("didn't\u00A0feel\u00A0comfortable") }
    }

    /** B's headline, the run in it. */
    private val bHeadline = buildAnnotatedString {
        append("Leonie ended the date, saying they ")
        append(run)
        append(" in the situation.")
    }

    private fun withMeasurer(block: (TextMeasurer) -> Unit) = runComposeUiTest {
        var measurer: TextMeasurer? = null
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()
        block(checkNotNull(measurer))
    }

    /** The search as it was: from 1, so it could settle inside a word. The control the sweeps compare against. */
    private fun unflooredWidthPx(m: TextMeasurer, text: AnnotatedString, maxPx: Int): Int {
        fun linesAt(px: Int) = m.measure(text, style, constraints = Constraints(maxWidth = px.coerceAtLeast(1))).lineCount
        val target = linesAt(maxPx)
        if (target <= 1) return maxPx
        var low = 1
        var high = maxPx
        while (low < high) {
            val mid = (low + high) / 2
            if (linesAt(mid) <= target) high = mid else low = mid + 1
        }
        return low
    }

    /** The narrowest width at which [piece] lays out whole, on one line -- found here independently of the code under test. */
    private fun fitPx(m: TextMeasurer, piece: AnnotatedString): Int {
        var low = 1
        var high = m.measure(piece, style, softWrap = false).size.width
        while (low < high) {
            val mid = (low + high) / 2
            if (m.measure(piece, style, constraints = Constraints(maxWidth = mid)).lineCount == 1) high = mid else low = mid + 1
        }
        return low
    }

    /** Every line but the last, where it ends without a breaking space before the break: a word split. */
    private fun splitWords(m: TextMeasurer, text: AnnotatedString, px: Int): List<String> {
        val r = m.measure(text, style, constraints = Constraints(maxWidth = px))
        return (0 until r.lineCount - 1).map { r.getLineEnd(it) }.filter { text.text[it - 1] != ' ' }
            .map { text.text.substring(r.getLineStart(r.getLineForOffset(it - 1)), it) }
    }

    private fun sweep(m: TextMeasurer, text: AnnotatedString, piece: AnnotatedString, name: String) {
        val piecePx = fitPx(m, piece)
        var wouldSplit = 0
        for (maxPx in piecePx..piecePx * 3 step 3) {
            val got = balancedWidthPx(m, text, style, maxPx)
            val old = unflooredWidthPx(m, text, maxPx)
            if (old < piecePx) wouldSplit++
            assertTrue("$name at $maxPx: $got is narrower than the unbreakable piece's $piecePx", got >= piecePx)
            assertTrue("$name at $maxPx: split ${splitWords(m, text, got)}", splitWords(m, text, got).isEmpty())
            // Only a width that split a word may move: everywhere else the breaks are the old ones, to the pixel.
            if (old >= piecePx) assertEquals("$name at $maxPx moved a width that split nothing", old, got)
        }
        assertTrue("$name: the old search never went below the piece, so this proves nothing", wouldSplit > 0)
    }

    @Test
    fun `a run joined by no-break spaces is never split`() = withMeasurer { m ->
        sweep(m, bHeadline, run, "B's headline")
    }

    @Test
    fun `a single long word is never split`() = withMeasurer { m ->
        val word = "Incomprehensibilities"
        sweep(m, AnnotatedString("$word is a word"), AnnotatedString(word), "the long word")
    }

    @Test
    fun `a width narrower than the widest piece is kept whole`() = withMeasurer { m ->
        val maxPx = fitPx(m, AnnotatedString("Incomprehensibilities")) - 5
        assertEquals(maxPx, balancedWidthPx(m, AnnotatedString("Incomprehensibilities is a word"), style, maxPx))
    }

    /** The floor is where the piece stays whole, not its rounded size: the two differ by a pixel at some sizes. */
    @Test
    fun `the floor is the width the widest piece stays whole at`() = withMeasurer { m ->
        assertEquals(fitPx(m, run), minContentPx(m, bHeadline, style))
    }
}
