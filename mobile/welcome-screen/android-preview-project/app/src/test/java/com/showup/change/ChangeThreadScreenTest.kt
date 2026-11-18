/*
 * ChangeThreadScreenTest.kt
 * ShowUp · the suggestion card and the system lines in the thread, frames C, F, H and L (SHOWUP-196)
 *
 * Copy is read from the tree, sides and sizes are measured from the laid-out nodes. The thread is rendered
 * through ChatScreen so the order, the sides and the wiring are the screen's own.
 */
package com.showup.change

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.chat.CHAT_SYSTEM_TAG
import com.showup.chat.ChatSamples
import com.showup.chat.ChatScreen
import com.showup.chat.ChatUi
import com.showup.chat.ThreadSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class ChangeThreadScreenTest {
    private val s = ThreadSamples
    private val agrees = mutableListOf<String>()
    private val counters = mutableListOf<ChangeKind>()

    private fun thread(
        suggestions: List<ChangeSuggestion>,
        messages: List<com.showup.chat.ChatMessage> = emptyList(),
        agreeing: Set<String> = emptySet(),
        agreeFailed: Set<String> = emptySet(),
    ) = ChatSamples.B.copy(date = s.DATE, messages = messages, suggestions = suggestions, agreeing = agreeing, agreeFailed = agreeFailed)

    /** A text drawn inside a suggestion card: the chat header repeats the same times and place. */
    private fun androidx.compose.ui.test.ComposeUiTest.cardText(text: String, substring: Boolean = false) = onNode(
        hasText(text, substring = substring) and SemanticsMatcher("inside a suggestion card") { node ->
            generateSequence(node.parent) { it.parent }.any {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("change-card-") == true
            }
        },
        useUnmergedTree = true,
    )

    @Composable
    private fun Screen(ui: ChatUi) = ChatScreen(
        ui = ui, zone = ZoneId.of("Europe/Berlin"),
        onBack = {}, onPick = { _, _ -> }, onEdit = {}, onAckGuide = {}, onSend = {}, onEndCard = {}, onKeep = {}, onEnd = {}, onRetry = {},
        onAgree = { agrees += it }, onCounter = { counters += it },
    )

    // ── C · pending, theirs ────────────────────────────────────────────────────────────────────

    @Test
    fun `C a pending time from the match shows the header, the value, the struck old one and the deadline box`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME))) }
        cardText("LEONIE SUGGESTED · MEET TIME").assertExists()
        cardText("18:30").assertExists()
        cardText("18:00").assertExists()
        cardText("Answer by 17:30").assertExists()
        cardText("Until you agree, 18:00 at Cafe Krone stays binding. Not being there counts as a no-show.", substring = true).assertExists()
        // The timestamp row: createdAt local.
        cardText("17:10").assertExists()
    }

    @Test
    fun `C Suggest another is first and I agree second, both 44 high, and each calls back`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME))) }
        val counter = onNodeWithTag(CHANGE_CARD_COUNTER_TAG).fetchSemanticsNode()
        val agree = onNodeWithTag(CHANGE_CARD_AGREE_TAG).fetchSemanticsNode()
        assertTrue("Suggest another is left of I agree", counter.boundsInRoot.left < agree.boundsInRoot.left)
        val dp = counter.layoutInfo.density.density
        assertEquals(44f, counter.size.height / dp, 0.5f)
        assertEquals(44f, agree.size.height / dp, 0.5f)
        assertEquals("gap 8", 8f, (agree.boundsInRoot.left - counter.boundsInRoot.right) / dp, 0.5f)
        onNodeWithTag(CHANGE_CARD_AGREE_TAG).performClick()
        onNodeWithTag(CHANGE_CARD_COUNTER_TAG).performClick()
        assertEquals(listOf("t1"), agrees)
        assertEquals(listOf(ChangeKind.Time), counters)
    }

    @Test
    fun `C a place suggestion shows the type and the address, and counters with the place kind`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.MINE_PLACE.copy(byYou = false)))) }
        cardText("LEONIE SUGGESTED · MEETING PLACE").assertExists()
        cardText("Bar Lenz").assertExists()
        cardText("Cafe Krone").assertExists()
        cardText("Bar · Reichenberger Str. 9").assertExists()
        onNodeWithTag(CHANGE_CARD_COUNTER_TAG).performClick()
        assertEquals(listOf(ChangeKind.Place), counters)
    }

    @Test
    fun `C theirs sits on the left and mine on the right, both 84 percent of the thread`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME, s.MINE_PLACE))) }
        val theirs = onNodeWithTag(changeCardTag("t1")).fetchSemanticsNode().boundsInRoot
        val mine = onNodeWithTag(changeCardTag("p1")).fetchSemanticsNode().boundsInRoot
        val thread = onNodeWithTag(com.showup.chat.CHAT_THREAD_TAG).fetchSemanticsNode().boundsInRoot
        val dp = onNodeWithTag(changeCardTag("t1")).fetchSemanticsNode().layoutInfo.density.density
        assertEquals("theirs is at the left padding", 14f, (theirs.left - thread.left) / dp, 1f)
        assertEquals("mine is at the right padding", 14f, (thread.right - mine.right) / dp, 1f)
        assertEquals("84 %", 0.84f, theirs.width / (thread.width - 28f * dp), 0.01f)
    }

    // ── mine, pending ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a pending suggestion of mine waits for the match with the compact binding note and no buttons`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.MINE_PLACE))) }
        cardText("YOU SUGGESTED · MEETING PLACE").assertExists()
        cardText("Waiting for Leonie").assertExists()
        cardText("18:00 at Cafe Krone stays binding unless Leonie agrees by 17:30.", substring = true).assertExists()
        onAllNodesWithTag(CHANGE_CARD_AGREE_TAG).assertCountEquals(0)
        onAllNodesWithTag(CHANGE_CARD_COUNTER_TAG).assertCountEquals(0)
    }

    // ── F · agreed ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `F an agreed card reads Agreed with the time it was answered, and its system line follows it`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.AGREED_TIME))) }
        cardText("Agreed · 17:20").assertExists()
        onNodeWithText("Meet time is now 18:30").assertExists()
        onAllNodesWithTag(CHANGE_CARD_AGREE_TAG).assertCountEquals(0)
    }

    @Test
    fun `F an agreed place leaves Meeting place is now`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.AGREED_PLACE))) }
        onNodeWithText("Meeting place is now Bar Lenz").assertExists()
    }

    // ── H · expired, replaced ──────────────────────────────────────────────────────────────────

    @Test
    fun `H an expired card says so and the line names what stays`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.EXPIRED))) }
        cardText("Expired · not agreed by 17:30").assertExists()
        onNodeWithText("No answer by 17:30 — 18:00 at Cafe Krone stays.").assertExists()
        onAllNodesWithTag(CHANGE_CARD_AGREE_TAG).assertCountEquals(0)
    }

    @Test
    fun `a replaced card stays with its footer and posts no line`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.REPLACED))) }
        cardText("Replaced by a new suggestion").assertExists()
        onAllNodesWithTag(CHAT_SYSTEM_TAG).assertCountEquals(0)
        onAllNodesWithTag(CHANGE_CARD_AGREE_TAG).assertCountEquals(0)
    }

    // ── L · I agree failed ─────────────────────────────────────────────────────────────────────

    @Test
    fun `L a failed agree shows the red line under the buttons and tapping it retries`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME), agreeFailed = setOf("t1"))) }
        onNodeWithText("Couldn't agree. Check your connection and try again.", useUnmergedTree = true).assertExists()
        val buttons = onNodeWithTag(CHANGE_CARD_AGREE_TAG).fetchSemanticsNode().boundsInRoot
        val line = onNodeWithTag(CHANGE_CARD_RETRY_TAG).fetchSemanticsNode()
        assertTrue("under the buttons", line.boundsInRoot.top >= buttons.bottom)
        assertTrue("44 floor", line.size.height / line.layoutInfo.density.density >= 43.5f)
        onNodeWithTag(CHANGE_CARD_RETRY_TAG).performClick()
        assertEquals(listOf("t1"), agrees)
    }

    @Test
    fun `no line without a failure`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME))) }
        onAllNodesWithTag(CHANGE_CARD_RETRY_TAG).assertCountEquals(0)
        onAllNodesWithText("Couldn't agree", substring = true).assertCountEquals(0)
    }

    @Test
    fun `while agreeing both buttons are inert and do not call back`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME), agreeing = setOf("t1"))) }
        onNodeWithTag(CHANGE_CARD_AGREE_TAG).assertIsNotEnabled()
        onNodeWithTag(CHANGE_CARD_COUNTER_TAG).assertIsNotEnabled()
        onNodeWithTag(CHANGE_CARD_AGREE_TAG).performClick()
        onNodeWithTag(CHANGE_CARD_COUNTER_TAG).performClick()
        assertTrue(agrees.isEmpty() && counters.isEmpty())
    }

    // ── the thread ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the thread draws a message, then the card, then its line, in that vertical order`() = runComposeUiTest {
        setContent {
            Screen(thread(listOf(s.AGREED_TIME), messages = listOf(s.message("a", "17:00", false), s.message("c", "17:40", false))))
        }
        val ys = listOf(
            onNodeWithText("ma").fetchSemanticsNode().boundsInRoot.top,
            onNodeWithTag(changeCardTag("t1")).fetchSemanticsNode().boundsInRoot.top,
            onNodeWithTag(CHAT_SYSTEM_TAG).fetchSemanticsNode().boundsInRoot.top,
            onNodeWithText("mc").fetchSemanticsNode().boundsInRoot.top,
        )
        assertEquals(ys.sorted(), ys)
    }

    @Test
    fun `a card alone replaces the start grid`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME))) }
        onAllNodesWithText("Start with a reason", substring = true).assertCountEquals(0)
    }

    // ── the value row: one baseline, wrapping (the reference's `align-items: baseline; flex-wrap: wrap`) ──

    /** Where the first line's baseline of the tagged text is drawn, in dp from the root's top. */
    private fun androidx.compose.ui.test.ComposeUiTest.baselineDp(tag: String): Float {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return (node.boundsInRoot.top + results.first().getLineBaseline(0)) / node.layoutInfo.density.density
    }

    private fun androidx.compose.ui.test.ComposeUiTest.dpPerPx() =
        onNodeWithTag(CHANGE_CARD_VALUE_TAG, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.density.density

    private fun androidx.compose.ui.test.ComposeUiTest.boundsOf(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `the struck old value sits on the value's baseline`() = runComposeUiTest {
        setContent { Screen(thread(listOf(s.THEIRS_TIME))) }
        assertEquals(baselineDp(CHANGE_CARD_VALUE_TAG), baselineDp(CHANGE_CARD_WAS_TAG), 0.5f)
        // And to its right, 10 apart.
        val value = boundsOf(CHANGE_CARD_VALUE_TAG)
        val was = boundsOf(CHANGE_CARD_WAS_TAG)
        assertEquals(10f, (was.left - value.right) / dpPerPx(), 0.5f)
    }

    @Test
    fun `a long place name drops the old name to its own line instead of squeezing it`() = runComposeUiTest {
        val long = s.MINE_PLACE.copy(
            byYou = false,
            place = s.MINE_PLACE.place!!.copy(name = "Osteria Nove Ristorante Pizzeria Bar and Lounge Kreuzberg"),
            wasPlaceName = "Cafe Krone",
        )
        setContent { Screen(thread(listOf(long))) }
        val value = boundsOf(CHANGE_CARD_VALUE_TAG)
        val was = boundsOf(CHANGE_CARD_WAS_TAG)
        val dp = dpPerPx()
        assertTrue("the old name is below the new one", was.top >= value.bottom)
        assertEquals("on its own line, at the left", value.left, was.left, 1f)
        assertTrue("one line, not squeezed", was.height / dp < 20f)
    }
}
