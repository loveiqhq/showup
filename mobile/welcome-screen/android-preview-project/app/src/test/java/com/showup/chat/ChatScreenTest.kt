/*
 * ChatScreenTest.kt
 * ShowUp · the coordinate chat's structure (SHOWUP-194): B, C, the guide, the card and the sheet
 *
 * Measured from the laid-out nodes where the ticket gives a number (two columns, chips 36 high, Keep
 * above End), read from the tree where it gives a string.
 */
package com.showup.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.showup.designsystem.SheetCopy
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
class ChatScreenTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val picks = mutableListOf<Pair<ChatReason, ChipSurface>>()
    private var kept = 0
    private var ended = 0
    private var endCards = 0

    @androidx.compose.runtime.Composable
    private fun Screen(ui: ChatUi) = ChatScreen(
        ui = ui,
        zone = berlin,
        onBack = {},
        onPick = { r, s -> picks += r to s },
        onEdit = {},
        onAckGuide = {},
        onSend = {},
        onEndCard = { endCards++ },
        onKeep = { kept++ },
        onEnd = { ended++ },
        onRetry = {},
    )

    @Test
    fun `B shows the five chips as tiles in two columns, the privacy line, and no field`() = runComposeUiTest {
        setContent { Screen(ChatSamples.B) }
        for (r in SHOWN_REASONS) onNodeWithText(r.label).assertExists()
        for (r in listOf(ChatReason.Arrived, ChatReason.ChangeTime, ChatReason.ChangePlace)) {
            onAllNodesWithText(r.label).assertCountEquals(0)
        }
        val lefts = SHOWN_REASONS.map { onNodeWithTag(chipTag(it, ChipSurface.Entry)).fetchSemanticsNode().boundsInRoot.left }
        assertEquals("two columns", 2, lefts.map { it.toInt() }.toSet().size)
        onNodeWithText("We log which reason you pick — never what you type.").assertExists()
        onNodeWithText("Start with a reason", substring = true).assertExists()
        onAllNodesWithText("Type a message, or tap a chip below").assertCountEquals(0)
    }

    @Test
    fun `a tile on B picks its chip from the entry grid`() = runComposeUiTest {
        setContent { Screen(ChatSamples.B) }
        onNodeWithTag(chipTag(ChatReason.RunningLate, ChipSurface.Entry)).performClick()
        assertEquals(listOf(ChatReason.RunningLate to ChipSurface.Entry), picks)
    }

    @Test
    fun `C shows the draft in the field, the chip selected, and the dock picks from the dock`() = runComposeUiTest {
        setContent { Screen(ChatSamples.C) }
        onNodeWithText("Running about 10 min late — sorry! Almost there.").assertExists()
        onNodeWithTag(chipTag(ChatReason.RunningLate, ChipSurface.Dock)).assertIsSelected()
        onNodeWithTag(chipTag(ChatReason.OnMyWay, ChipSurface.Dock)).assertIsNotSelected()
        onNodeWithTag(CHAT_SEND_TAG).assertIsEnabled()
        onNodeWithTag(chipTag(ChatReason.CantFind, ChipSurface.Dock)).performClick()
        assertEquals(listOf(ChatReason.CantFind to ChipSurface.Dock), picks)
    }

    @Test
    fun `the dock has no heading line, five chips, and each chip is 36 high`() = runComposeUiTest {
        setContent { Screen(ChatSamples.C) }
        onAllNodesWithText("Tap a chip", substring = true).assertCountEquals(0)
        onAllNodesWithText("Quick", substring = true).assertCountEquals(0)
        for (r in SHOWN_REASONS) {
            val node = onNodeWithTag(chipTag(r, ChipSurface.Dock), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("${r.wire} is 36 high", 36f, node.size.height / node.layoutInfo.density.density, 0.5f)
        }
        onAllNodesWithText(ChatReason.Arrived.label).assertCountEquals(0)
    }

    @Test
    fun `with the guide waiting the field and Send are disabled, and the guide shows its title and Got it`() = runComposeUiTest {
        setContent { Screen(ChatSamples.D) }
        onNodeWithTag(CHAT_FIELD_TAG).assertIsNotEnabled()
        onNodeWithText("Read the note above, then tap Got it", useUnmergedTree = true).assertExists()
        onNodeWithTag(CHAT_SEND_TAG).assertIsNotEnabled()
        onNodeWithText("Life happens — handle it with respect").assertExists()
        onNodeWithText("Got it").assertExists()
        onNodeWithTag(chipTag(ChatReason.CantMakeIt, ChipSurface.Dock)).assertIsSelected()
    }

    @Test
    fun `after Got it the guide is one line and the field asks for an apology to the match`() = runComposeUiTest {
        setContent { Screen(ChatSamples.D.copy(guide = Guide.Acked)) }
        onNodeWithText("Explain why, say sorry — in your own words.").assertExists()
        onAllNodesWithText("Life happens — handle it with respect").assertCountEquals(0)
        onNodeWithTag(CHAT_FIELD_TAG).assertIsEnabled()
        onNodeWithText("Explain why and say sorry to Leonie…", useUnmergedTree = true).assertExists()
        onNodeWithTag(CHAT_SEND_TAG).assertIsNotEnabled()
    }

    @Test
    fun `the End date card is there only once an I can't make it today message is`() = runComposeUiTest {
        var ui by androidx.compose.runtime.mutableStateOf(ChatSamples.C)
        setContent { Screen(ui) }
        onAllNodesWithText("Not meeting today?", substring = true).assertCountEquals(0)
        ui = ChatSamples.E
        waitForIdle()
        onNodeWithText("Not meeting today?", substring = true).assertExists()
        onNodeWithTag(CHAT_END_CARD_BUTTON_TAG).performClick()
        assertEquals(1, endCards)
        ui = ChatSamples.E.copy(selected = ChatReason.CantMakeIt, guide = Guide.Locked)
        waitForIdle()
        onAllNodesWithText("Not meeting today?", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the thread shows the badge, the text and the read receipt`() = runComposeUiTest {
        setContent { Screen(ChatSamples.E) }
        onNodeWithText("I CAN'T MAKE IT TODAY").assertExists()
        onNodeWithText(ChatSamples.CANCEL_SENT.body).assertExists()
        onNodeWithText("17:38").assertExists()
        onNodeWithContentDescription("Read").assertExists()
    }

    @Test
    fun `the header shows the latest agreed day, time and place, and the countdown`() = runComposeUiTest {
        setContent { Screen(ChatSamples.C) }
        onNodeWithText("Leonie").assertExists()
        onNodeWithText("DATE ON").assertExists()
        onNodeWithText("Today · 19:00 · Cafe Krone", substring = true).assertExists()
        onNodeWithText("in 1h 18m", substring = true).assertExists()
    }

    @Test
    fun `the sheet shows Keep the date above End date, and the scrim keeps the date`() = runComposeUiTest {
        setContent { Screen(ChatSamples.F) }
        onNodeWithText("End the date with Leonie?").assertExists()
        val keep = onNodeWithTag(END_SHEET_KEEP_TAG).fetchSemanticsNode().boundsInRoot.top
        val end = onNodeWithTag(END_SHEET_END_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Keep the date comes first", keep < end)
        onNodeWithText("Keep the date").assertExists()
        onNodeWithTag(END_SHEET_END_TAG).performClick()
        assertEquals(1, ended)
        // The scrim fills the screen and the sheet covers its middle: tap it above the sheet.
        onNodeWithContentDescription(SheetCopy.DISMISS).performTouchInput { click(Offset(centerX, 40f)) }
        assertEquals(1, kept)
    }

    @Test
    fun `a failed first read offers Try again, and loading shows no thread`() = runComposeUiTest {
        var ui by androidx.compose.runtime.mutableStateOf(ChatSamples.B.copy(loading = true))
        setContent { Screen(ui) }
        onAllNodesWithText("Start with a reason", substring = true).assertCountEquals(0)
        ui = ChatSamples.B.copy(loading = false, failed = com.showup.api.CallFailure.Network)
        waitForIdle()
        onNodeWithText("Try again").assertExists()
    }
}
