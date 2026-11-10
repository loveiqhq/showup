/*
 * ArrivalSheetsTest.kt
 * ShowUp · B, C and D, the check-in sheets (SHOWUP-195), measured from the laid-out nodes
 */
package com.showup.arrival

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.showup.designsystem.SheetCopy
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
class ArrivalSheetsTest {
    private var yes = 0
    private var notYet = 0
    private var close = 0
    private var undo = 0

    private fun confirm(locError: Boolean = false, busy: Boolean = false, note: String? = null) =
        GateUi(phase = GatePhase.Confirm(ArrivalEntry.DateScreen, locError, busy, note))

    private val done = GateUi(phase = GatePhase.Done(ArrivalEntry.DateScreen))

    private fun androidx.compose.ui.test.ComposeUiTest.host(ui: GateUi) = setContent {
        ArrivalSheetHost(ui, "Leonie", onYes = { yes++ }, onNotYet = { notYet++ }, onClose = { close++ }, onUndo = { undo++ })
    }

    private fun androidx.compose.ui.test.ComposeUiTest.top(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
    private fun androidx.compose.ui.test.ComposeUiTest.bottom(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom

    @Test
    fun `Idle draws no sheet`() = runComposeUiTest {
        host(GateUi())
        onAllNodesWithTag(ARRIVAL_SHEET_TAG).assertCountEquals(0)
    }

    @Test
    fun `B shows the circle, the title, and Not yet above Yes`() = runComposeUiTest {
        host(confirm())
        onNodeWithTag(ARRIVAL_SHEET_TAG).assertExists()
        val circle = onNodeWithTag(ARRIVAL_CIRCLE_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(80f * 2f, circle.width, 1f)
        onNodeWithText("Have you arrived at the meeting location for your date with Leonie?").assertExists()
        onNodeWithText("Not yet").assertExists()
        onNodeWithText("Yes, I've arrived").assertExists()
        assertTrue("Not yet comes first", top(ARRIVAL_NOT_YET_TAG) < top(ARRIVAL_YES_TAG))
        onAllNodesWithTag(ARRIVAL_ERROR_TAG).assertCountEquals(0)
        onNodeWithTag(ARRIVAL_NOT_YET_TAG).performClick()
        onNodeWithTag(ARRIVAL_YES_TAG).performClick()
        assertEquals(1, notYet)
        assertEquals(1, yes)
    }

    @Test
    fun `B with a note puts the note under the title`() = runComposeUiTest {
        host(confirm(note = "Check in first, so we can confirm you were there."))
        val note = onNodeWithText("Check in first, so we can confirm you were there.").fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Have you arrived at the meeting location for your date with Leonie?").fetchSemanticsNode().boundsInRoot
        assertTrue(note.top >= title.bottom)
        assertTrue(note.bottom <= top(ARRIVAL_NOT_YET_TAG))
    }

    @Test
    fun `C shows the error between the title and the buttons, and Try again`() = runComposeUiTest {
        host(confirm(locError = true))
        waitForIdle()
        onNodeWithTag(ARRIVAL_ERROR_TAG).assertExists()
        onNodeWithText("We couldn't find your location. Check your signal, then try again.").assertExists()
        onNodeWithText("Try again").assertExists()
        onAllNodesWithText("Yes, I've arrived").assertCountEquals(0)
        val title = onNodeWithText("Have you arrived at the meeting location for your date with Leonie?").fetchSemanticsNode().boundsInRoot
        assertTrue(top(ARRIVAL_ERROR_TAG) >= title.bottom)
        assertTrue(bottom(ARRIVAL_ERROR_TAG) <= top(ARRIVAL_NOT_YET_TAG))
    }

    @Test
    fun `busy disables both buttons and a scrim tap calls nothing`() = runComposeUiTest {
        host(confirm(busy = true))
        onNodeWithTag(ARRIVAL_YES_TAG).assertIsNotEnabled()
        onNodeWithTag(ARRIVAL_NOT_YET_TAG).assertIsNotEnabled()
        onNodeWithContentDescription(SheetCopy.DISMISS).performTouchInput { click(Offset(centerX, 40f)) }
        assertEquals(0, notYet)
        assertEquals(0, close)
        assertEquals(0, yes)
    }

    @Test
    fun `not busy, the scrim on B is Not yet`() = runComposeUiTest {
        host(confirm())
        onNodeWithTag(ARRIVAL_YES_TAG).assertIsEnabled()
        onNodeWithContentDescription(SheetCopy.DISMISS).performTouchInput { click(Offset(centerX, 40f)) }
        assertEquals(1, notYet)
    }

    @Test
    fun `D shows Close above Undo check-in at 44 or more, and a scrim tap closes`() = runComposeUiTest {
        host(done)
        onNodeWithText("You are amazing for showing up!").assertExists()
        onNodeWithText("We will let Leonie know that you have arrived. Enjoy your date.").assertExists()
        assertTrue("Close comes first", top(ARRIVAL_CLOSE_TAG) < top(ARRIVAL_UNDO_TAG))
        onNodeWithTag(ARRIVAL_UNDO_TAG).assertHeightIsAtLeast(44.dp)
        onNodeWithTag(ARRIVAL_UNDO_TAG).performClick()
        assertEquals(1, undo)
        onNodeWithTag(ARRIVAL_CLOSE_TAG).performClick()
        assertEquals(1, close)
        onNodeWithContentDescription(SheetCopy.DISMISS).performTouchInput { click(Offset(centerX, 40f)) }
        assertEquals(2, close)
    }

    @Test
    fun `B swaps to D inside the one sheet`() = runComposeUiTest {
        var ui by mutableStateOf(confirm())
        setContent {
            ArrivalSheetHost(ui, "Leonie", onYes = {}, onNotYet = {}, onClose = {}, onUndo = {})
        }
        ui = done
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("You are amazing for showing up!").assertExists()
        onAllNodesWithText("Not yet").assertCountEquals(0)
        onAllNodesWithTag(ARRIVAL_SHEET_TAG).assertCountEquals(1)
    }
}
