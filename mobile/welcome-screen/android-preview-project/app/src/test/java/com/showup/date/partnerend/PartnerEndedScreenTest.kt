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

    @Test
    fun `row count follows the payload`() = runComposeUiTest {
        setContent { PartnerEndedScreen(PartnerScreen.DateClosedPositive, "Leonie", null) {} }
        onAllNodesWithTag(PARTNER_ENDED_ROW_TAG, useUnmergedTree = true).assertCountEquals(2)
    }
}
