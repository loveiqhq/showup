/*
 * CheckInBarTest.kt
 * ShowUp · A, the Check-in bar (SHOWUP-195): its three strings, the End date link behind its switch, and R12
 */
package com.showup.arrival

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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
class CheckInBarTest {
    @Test
    fun `the bar shows its three strings and no End date link`() = runComposeUiTest {
        setContent { CheckInBar("Leonie", onCheckIn = {}) }
        onNodeWithText("Your date with Leonie").assertExists()
        onNodeWithText("Confirm you arrived at the meeting location, we will let your date know.").assertExists()
        onNodeWithText("Check-in").assertExists()
        onAllNodesWithText("End date").assertCountEquals(0)
        assertFalse("the link switch is off (R15)", END_DATE_LINK_SHOWN)
    }

    @Test
    fun `in right-to-left the pin tile is at the end side and the button at the start`() = runComposeUiTest {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
            ) { CheckInBar("Leonie", onCheckIn = {}) }
        }
        val bar = onNodeWithTag(ARRIVAL_BAR_TAG).fetchSemanticsNode().boundsInRoot
        val pin = onNodeWithTag(ARRIVAL_PIN_TAG).fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag(ARRIVAL_CHECK_IN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("pin on the right", pin.right > bar.right - with(density) { 20.dp.toPx() })
        assertTrue("button on the left", button.left < bar.left + with(density) { 20.dp.toPx() + 1f })
        assertTrue(button.right < pin.left)
    }

    @Test
    fun `Check-in answers a tap`() = runComposeUiTest {
        var taps = 0
        setContent { CheckInBar("Leonie", onCheckIn = { taps++ }) }
        onNodeWithTag(ARRIVAL_CHECK_IN_TAG).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `with onEnd the link shows End date and answers at 44`() = runComposeUiTest {
        var ends = 0
        setContent { CheckInBar("Leonie", onCheckIn = {}, onEnd = { ends++ }) }
        onNodeWithText("End date").assertExists()
        onNodeWithTag(ARRIVAL_END_DATE_TAG).assertHeightIsAtLeast(44.dp)
        onNodeWithTag(ARRIVAL_END_DATE_TAG).performClick()
        assertEquals(1, ends)
    }

    @Test
    fun `R12 - where pin, text and button cannot share a row the button goes under the text, full width`() = runComposeUiTest {
        setContent { CheckInBar("Leonie", onCheckIn = {}, modifier = Modifier.width(250.dp)) }
        val bar = onNodeWithTag(ARRIVAL_BAR_TAG).fetchSemanticsNode().boundsInRoot
        val text = onNodeWithText("Your date with Leonie").fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag(ARRIVAL_CHECK_IN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the button sits below the text", button.top >= text.bottom)
        val pad = with(density) { 16.dp.toPx() }
        assertEquals("full width inside the 16 padding", bar.width - 2 * pad, button.width, 1.5f)
    }

    @Test
    fun `at 390 the button sits beside the text`() = runComposeUiTest {
        setContent { CheckInBar("Leonie", onCheckIn = {}) }
        val text = onNodeWithText("Your date with Leonie").fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag(ARRIVAL_CHECK_IN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("same row", button.left >= text.right)
        assertTrue("same row", button.top < text.bottom)
    }
}
