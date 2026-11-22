/*
 * PartnerEndedScreenTest.kt
 * ShowUp · Date 08's three payloads: the ticket's strings exactly, one control, and the words each frame must not use
 * (SHOWUP-199)
 */
package com.showup.date.partnerend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.date.PartnerScreen
import com.showup.fit.DEVICES
import com.showup.fit.measureFit
import com.showup.welcome.washSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PartnerEndedScreenTest {
    private class Expect(val screen: PartnerScreen, val texts: List<String>, val rows: List<String>)

    private val a = Expect(
        PartnerScreen.YouNoShowed,
        listOf(
            "Leonie ended the date, stating you didn't show up.",
            "Leonie ended the date after waiting for you.\nIt's now closed.",
        ),
        listOf(
            "Not showing up to a date — or being very late without reaching out to your date — shows no respect for each other's time.",
            "To clear this up, proceed to the review and give us your side.",
            "Once you've completed the review, we run an automated check: did you both check in, and did you message each other? Your Show-up Rate only drops if the no-show is proven.",
        ),
    )
    private val b = Expect(
        PartnerScreen.DateEndedDiscomfort,
        listOf("Leonie ended the date, saying they didn't feel comfortable in the situation.", "The date is now closed."),
        listOf(
            "Respect is non-negotiable at Show Up. Behaviour that leaves someone feeling uncomfortable is not accepted here.",
            "So far we only have one side of the story. In the next step, tell us what happened from your point of view.",
            "We review every report and act on it where it's needed.",
        ),
    )
    private val c = Expect(
        PartnerScreen.DateClosedPositive,
        listOf("Leonie ended the date, stating you both have met.", "Thank you for being real and showing up to your date."),
        listOf(
            "We hope it was a good one. Head into the review and tell us how it went — it takes a minute.",
            "Once the review is done, you're free to search for your next date.",
        ),
    )

    private fun texts(node: SemanticsNode, out: MutableList<String> = mutableListOf()): List<String> {
        // WashHeadline binds the washed run with no-break spaces so it never wraps inside; the ticket's are plain.
        node.config.getOrNull(SemanticsProperties.Text)?.let { l -> out += l.joinToString("") { it.text }.replace(' ', ' ') }
        node.children.forEach { texts(it, out) }
        return out
    }

    private fun render(e: Expect, check: (List<String>) -> Unit) = runComposeUiTest {
        setContent { PartnerEndedScreen(e.screen, "Leonie", null) {} }
        waitForIdle()
        check(texts(onRoot(useUnmergedTree = true).fetchSemanticsNode()))
    }

    private fun readsTheTicket(e: Expect) = render(e) { all ->
        e.texts.forEach { assertTrue("missing: $it in $all", it in all) }
        assertEquals(e.rows, all.filter { it in e.rows })
        assertTrue("Proceed to review" in all)
    }

    @Test fun `A reads the ticket exactly`() = readsTheTicket(a)
    @Test fun `B reads the ticket exactly`() = readsTheTicket(b)
    @Test fun `C reads the ticket exactly`() = readsTheTicket(c)

    @Test
    fun `Proceed to review is the only click action on each payload`() = runComposeUiTest {
        var frame by mutableStateOf(PartnerScreen.YouNoShowed)
        setContent { PartnerEndedScreen(frame, "Leonie", null) {} }
        for (s in PartnerScreen.entries) {
            frame = s
            waitForIdle()
            onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
            onNodeWithTag(PARTNER_ENDED_CTA_TAG, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `B uses no gendered pronoun and never says unsafe`() = render(b) { all ->
        val joined = all.joinToString("\n")
        // Whole words: a substring check would fail on "the".
        assertFalse(Regex("\\b(she|he|her|his|him|hers)\\b", RegexOption.IGNORE_CASE).containsMatchIn(joined))
        assertFalse(joined.contains("unsafe", ignoreCase = true))
    }

    @Test
    fun `A carries no rate value and no visibility threat`() = render(a) { all ->
        val joined = all.joinToString("\n")
        assertFalse(Regex("\\d").containsMatchIn(joined))
        assertFalse(joined.contains("less visible", ignoreCase = true))
        assertFalse(joined.contains("lose App access", ignoreCase = true))
    }

    @Test
    fun `tapping the CTA calls onProceed once`() = runComposeUiTest {
        var taps = 0
        setContent { PartnerEndedScreen(PartnerScreen.DateClosedPositive, "Leonie", null) { taps++ } }
        onNodeWithTag(PARTNER_ENDED_CTA_TAG, useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, taps)
    }

    /** B's headline as laid out on the [w] x [h] phone at [scale] type: its text and where each line but the last ends. */
    private fun bBreaks(w: Int, h: Int, scale: Float): Pair<String, List<Int>> {
        val phone = DEVICES.first { it.width == w && it.height == h }
        var breaks: Pair<String, List<Int>>? = null
        measureFit(phone, "PartnerEnded/B headline @$scale", scale, 0, extra = { f ->
            breaks = f.lineBreaks().firstOrNull { it.first.replace('\u00A0', ' ') == b.texts[0] }
            emptyList()
        }) { PartnerEndedScreen(b.screen, "Leonie", null) {} }
        return checkNotNull(breaks) { "B's headline not laid out at $w @$scale" }
    }

    /**
     * The balanced headline breaks only at a space on the three phones the ticket names. It drew `didn't feel comforta`
     * / `ble in the situation.` on all three: the balance search went below the washed run's width, where the line
     * breaker splits the run rather than overflow (22 Nov 2026).
     */
    @Test
    fun `B's headline never splits a word at 375 390 and 430`() {
        for ((w, h) in listOf(375 to 667, 390 to 844, 430 to 932)) {
            val (text, ends) = bBreaks(w, h, 1f)
            val lines = (listOf(0) + ends).zip(ends + text.length) { from, to -> text.substring(from, to) }
            assertTrue("B's headline at $w splits a word: $lines", ends.all { text[it - 1] == ' ' })
        }
    }

    private val run = "didn't feel comfortable"

    /**
     * Where the washed run is wider than the column itself -- 320 at the default type, 390 at 200 % -- no width keeps it
     * whole, and the line breaker split a word inside it (ruling R-e, 22 Nov 2026: no word may ever break inside). It
     * wraps between its own words instead.
     */
    @Test
    fun `B's headline never splits a word where its run is wider than the column`() {
        for ((size, scale) in listOf((320 to 686) to 1f, (390 to 844) to 2f, (320 to 686) to 2f)) {
            val (text, ends) = bBreaks(size.first, size.second, scale)
            val lines = (listOf(0) + ends).zip(ends + text.length) { from, to -> text.substring(from, to) }
            assertTrue("B's headline at ${size.first} @$scale splits a word: $lines", ends.all { text[it - 1] == ' ' })
        }
    }

    /** Where the run fits, it keeps its no-break spaces and its one line: nothing moves on those headlines. */
    @Test
    fun `B's run stays joined on one line where it fits`() {
        for ((w, h) in listOf(375 to 667, 390 to 844, 430 to 932)) {
            val (text, ends) = bBreaks(w, h, 1f)
            val from = text.replace('\u00A0', ' ').indexOf(run)
            val to = from + run.length
            assertTrue("B's run lost its no-break spaces at $w", '\u00A0' in text.substring(from, to))
            assertTrue("B's run wraps at $w: $ends", ends.none { it in from + 1 until to })
        }
    }

    /** The wash covers every line of a run that wraps: one band a line, each holding that line's words of the run. */
    @Test
    fun `the wash covers every fragment of a run that wraps`() {
        for ((size, scale) in listOf((320 to 686) to 1f, (390 to 844) to 2f)) {
            val (text, ends) = bBreaks(size.first, size.second, scale)
            val from = text.indexOf("didn't")
            val to = text.indexOf("comfortable") + "comfortable".length
            val bands = washSegments(text, ends + text.length, from, to).map { (a, z) -> text.substring(a, z) }
            assertTrue("the run did not wrap at ${size.first} @$scale: $bands", bands.size >= 2)
            assertEquals(run, bands.joinToString(" "))
            assertTrue("a band holds a space at its edge: $bands", bands.none { it.startsWith(' ') || it.endsWith(' ') })
        }
    }

    /** A run on one line is one band, the whole run: the wash every other headline draws is the one it always drew. */
    @Test
    fun `a run on one line is one band, the whole run`() {
        val (text, ends) = bBreaks(390, 844, 1f)
        val from = text.indexOf("didn't")
        val to = from + run.length
        assertEquals(listOf(from to to), washSegments(text, ends + text.length, from, to))
    }

    @Test
    fun `row count follows the payload`() = runComposeUiTest {
        setContent { PartnerEndedScreen(PartnerScreen.DateClosedPositive, "Leonie", null) {} }
        onAllNodesWithTag(PARTNER_ENDED_ROW_TAG, useUnmergedTree = true).assertCountEquals(2)
    }
}
