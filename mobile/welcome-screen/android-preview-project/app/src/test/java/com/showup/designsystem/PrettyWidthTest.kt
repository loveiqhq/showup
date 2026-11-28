/*
 * PrettyWidthTest.kt
 * ShowUp · `text-wrap: pretty` never narrows below the widest unbreakable piece, so no word splits
 *
 * `prettyWidthPx` looks for the widest width at which one more word reaches an orphaned last line. Where the line above
 * holds one piece wider than everything after it -- a long word, or an em run whose spaces are no-break -- the only
 * width that pulls anything down is just under that piece, where the line breaker splits it and the fragment lands on
 * the last line: `Incomprehensibiliti` / `es word`. The search accepted that (the review of SHOWUP-199, 27 Nov 2026).
 * Each case sweeps widths, proves the old search split at some of them (or proves nothing), and checks that wherever
 * the old search kept every word whole the answer is unchanged, to the pixel.
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
class PrettyWidthTest {

    /** A pretty headline's type: Lora 700 30 / 1.12, -0.015em, as the Check-In steps set it. */
    private val style = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = (30 * 1.12).sp, letterSpacing = (-0.015).em)

    private fun withMeasurer(block: (TextMeasurer) -> Unit) = runComposeUiTest {
        var measurer: TextMeasurer? = null
        setContent { measurer = rememberTextMeasurer() }
        waitForIdle()
        block(checkNotNull(measurer))
    }

    /** The search as it was, with no floor: the control the sweeps compare against. */
    private fun unflooredPrettyPx(m: TextMeasurer, text: AnnotatedString, maxPx: Int): Int {
        val greedy = m.measure(text, style, constraints = Constraints(maxWidth = maxPx))
        val lines = greedy.lineCount
        if (lines <= 1) return maxPx
        val last = lines - 1
        val lastStart = greedy.getLineStart(last)
        val lastText = text.text.substring(lastStart, greedy.getLineEnd(last)).trim()
        val lastWidth = greedy.getLineRight(last) - greedy.getLineLeft(last)
        if (' ' in lastText || lastWidth >= maxPx / 3f) return maxPx
        fun moved(px: Int): Boolean {
            val r = m.measure(text, style, constraints = Constraints(maxWidth = px.coerceAtLeast(1)))
            return r.lineCount > lines || r.getLineStart(last) < lastStart
        }
        var lo = 1
        var hi = maxPx
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (moved(mid)) lo = mid else hi = mid - 1
        }
        val pulled = m.measure(text, style, constraints = Constraints(maxWidth = lo))
        return if (pulled.lineCount == lines && pulled.getLineStart(last) < lastStart) lo else maxPx
    }

    /** The narrowest width at which [piece] lays out whole, on one line -- found here, not by the code under test. */
    private fun fitPx(m: TextMeasurer, piece: AnnotatedString): Int {
        var low = 1
        var high = m.measure(piece, style, softWrap = false).size.width
        while (low < high) {
            val mid = (low + high) / 2
            if (m.measure(piece, style, constraints = Constraints(maxWidth = mid)).lineCount == 1) high = mid else low = mid + 1
        }
        return low
    }

    /** Every line but the last that ends without a breaking space before the break: a split word. */
    private fun splitWords(m: TextMeasurer, text: AnnotatedString, px: Int): List<String> {
        val r = m.measure(text, style, constraints = Constraints(maxWidth = px))
        return (0 until r.lineCount - 1).map { r.getLineEnd(it) }.filter { text.text[it - 1] != ' ' }
            .map { text.text.substring(r.getLineStart(r.getLineForOffset(it - 1)), it) }
    }

    private fun sweep(m: TextMeasurer, text: AnnotatedString, piece: AnnotatedString, name: String) {
        val piecePx = fitPx(m, piece)
        var wouldSplit = 0
        for (maxPx in piecePx..piecePx * 3 / 2 step 2) {
            val got = prettyWidthPx(m, text, style, maxPx)
            val old = unflooredPrettyPx(m, text, maxPx)
            if (old < piecePx) wouldSplit++
            assertTrue("$name at $maxPx: $got is narrower than the unbreakable piece's $piecePx", got >= piecePx)
            assertTrue("$name at $maxPx: split ${splitWords(m, text, got)}", splitWords(m, text, got).isEmpty())
            // Only a width that split a word may move: everywhere else the re-wrap is the old one, to the pixel.
            if (old >= piecePx) assertEquals("$name at $maxPx moved a width that split nothing", old, got)
        }
        assertTrue("$name: the old search never went below the piece, so this proves nothing", wouldSplit > 0)
    }

    @Test
    fun `a pretty re-wrap never splits a long word`() = withMeasurer { m ->
        val word = "Incomprehensibilities"
        sweep(m, AnnotatedString("$word word"), AnnotatedString(word), "the long word")
    }

    @Test
    fun `a pretty re-wrap never splits a run joined by no-break spaces`() = withMeasurer { m ->
        val run = buildAnnotatedString {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)) { append("didn't\u00A0feel\u00A0comfortable") }
        }
        sweep(m, buildAnnotatedString { append(run); append(" yes") }, run, "the joined run")
    }
}
