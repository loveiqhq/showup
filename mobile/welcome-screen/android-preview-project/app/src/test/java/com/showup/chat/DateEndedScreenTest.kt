/*
 * DateEndedScreenTest.kt
 * ShowUp · G and H, after a date ended in the chat (SHOWUP-194)
 */
package com.showup.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class DateEndedScreenTest {

    @Test
    fun `G tells the one who ended it that their Show-up Rate is untouched, and finds the next date`() = runComposeUiTest {
        var found = 0
        setContent { DateEndedScreen(ChatSamples.G, photoUrl = null, onFindNext = { found++ }) }
        onNodeWithText("You ended the date", substring = true).assertExists()
        onNodeWithText("We hope you and Leonie found another day to meet.", substring = true).assertExists()
        onNodeWithText("You told Leonie yourself, so your Show-up Rate stays untouched.", substring = true).assertExists()
        onAllNodesWithText("had to cancel", substring = true).assertCountEquals(0)
        onNodeWithText("Find your next date").performClick()
        assertEquals(1, found)
    }

    @Test
    fun `H tells the match that Leonie had to cancel, with the same promise and the same way on`() = runComposeUiTest {
        setContent { DateEndedScreen(ChatSamples.H, photoUrl = null, onFindNext = {}) }
        onNodeWithText("Leonie had to cancel the date", substring = true).assertExists()
        onNodeWithText("We hope you two found another day to meet.", substring = true).assertExists()
        onNodeWithText("Your Show-up Rate stays untouched.", substring = true).assertExists()
        onAllNodesWithText("You told", substring = true).assertCountEquals(0)
        onNodeWithText("Find your next date").assertExists()
    }
}
