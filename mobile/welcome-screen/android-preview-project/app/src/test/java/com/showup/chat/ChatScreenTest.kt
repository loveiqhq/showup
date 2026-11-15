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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
    private val resent = mutableListOf<String>()
    private var retries = 0

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
        onRetry = { retries++ },
        onResend = { resent += it },
    )

    @Test
    fun `B shows the chips as tiles in two columns, the privacy line, and no field`() = runComposeUiTest {
        setContent { Screen(ChatSamples.B) }
        for (r in SHOWN_REASONS) onNodeWithText(r.label).assertExists()
        for (r in listOf(ChatReason.ChangeTime, ChatReason.ChangePlace)) {
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
    fun `the dock has no heading line, the chips 38 high, each answering 3 beyond it on every side`() = runComposeUiTest {
        setContent { Screen(ChatSamples.C) }
        onAllNodesWithText("Tap a chip", substring = true).assertCountEquals(0)
        onAllNodesWithText("Quick", substring = true).assertCountEquals(0)
        for (r in SHOWN_REASONS) {
            val face = onNodeWithTag(chipFaceTag(r, ChipSurface.Dock), useUnmergedTree = true).fetchSemanticsNode()
            val touch = onNodeWithTag(chipTag(r, ChipSurface.Dock), useUnmergedTree = true).fetchSemanticsNode()
            val dp = face.layoutInfo.density.density
            assertEquals("${r.wire} is 38 high", 38f, face.size.height / dp, 0.5f)
            // 9 Nov 2026: a 44 tap area, 3 into the 6 gap on every side, so neighbours meet and never overlap.
            assertEquals("${r.wire} answers 44 high", 44f, touch.size.height / dp, 0.5f)
            assertEquals(3f, (face.boundsInRoot.left - touch.boundsInRoot.left) / dp, 0.5f)
            assertEquals(3f, (touch.boundsInRoot.right - face.boundsInRoot.right) / dp, 0.5f)
            assertEquals(3f, (face.boundsInRoot.top - touch.boundsInRoot.top) / dp, 0.5f)
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

    @Test
    fun `I - a failed load keeps the header, says so with Try again, and shows no dock`() = runComposeUiTest {
        var ui by androidx.compose.runtime.mutableStateOf(ChatSamples.C.copy(loading = false, failed = com.showup.api.CallFailure.Network, messages = emptyList()))
        setContent { Screen(ui) }
        onNodeWithText("Couldn't load your chat. Check your connection and try again.").assertExists()
        onNodeWithText("Leonie").assertExists()
        onAllNodesWithTag(CHAT_DOCK_TAG).assertCountEquals(0)
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        // Reloading: the message stays and Try again waits.
        ui = ui.copy(loading = true)
        waitForIdle()
        onNodeWithText("Couldn't load your chat. Check your connection and try again.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `J - a failed message stays in the thread with no receipt, and its line resends it`() = runComposeUiTest {
        val unsent = ChatSamples.UNSENT
        setContent { Screen(ChatSamples.J) }
        onNodeWithText(unsent.body).assertExists()
        onAllNodesWithContentDescription("Delivered").assertCountEquals(0)
        onAllNodesWithContentDescription("Read").assertCountEquals(0)
        val line = onNodeWithText("Couldn't send. Check your connection and try again.", useUnmergedTree = true)
        line.assertExists()
        onNodeWithTag(CHAT_RESEND_TAG).performClick()
        assertEquals(listOf(unsent.clientId), resent)
    }

    @Test
    fun `K - a failed End date says so inside the sheet, above the buttons, which stay`() = runComposeUiTest {
        var ui by androidx.compose.runtime.mutableStateOf(ChatSamples.F)
        setContent { Screen(ui) }
        val keepBefore = onNodeWithTag(END_SHEET_KEEP_TAG).fetchSemanticsNode().boundsInRoot
        val endBefore = onNodeWithTag(END_SHEET_END_TAG).fetchSemanticsNode().boundsInRoot
        ui = ChatSamples.K
        waitForIdle()
        val error = onNodeWithText("Couldn't end the date. Check your connection and try again.").fetchSemanticsNode().boundsInRoot
        val keep = onNodeWithTag(END_SHEET_KEEP_TAG).fetchSemanticsNode().boundsInRoot
        val end = onNodeWithTag(END_SHEET_END_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the error sits above Keep the date", error.bottom <= keep.top)
        // The sheet grows upward from the bottom: the buttons keep their size and their place.
        assertEquals(keepBefore, keep)
        assertEquals(endBefore, end)
        onNodeWithTag(END_SHEET_END_TAG).performClick()
        assertEquals("End date tries again", 1, ended)
    }
}
