/*
 * ChatTutorialScreenTest.kt
 * ShowUp · A, the chat tutorial (SHOWUP-194): two steps and no third, one way forward
 */
package com.showup.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
class ChatTutorialScreenTest {

    @Test
    fun `the tutorial names the match, has its two steps and no third, and Open the chat goes on`() = runComposeUiTest {
        var opened = 0
        var back = 0
        setContent { ChatTutorialScreen("Leonie", onOpen = { opened++ }, onBack = { back++ }) }
        onNodeWithText("Coordinate with Leonie").assertExists()
        // The washed run is one unbreakable piece (`Must` and `read` joined by a no-break space), so the rest is matched.
        onNodeWithText("— how messaging works", substring = true).assertExists()
        onNodeWithText("Use our action chips").assertExists()
        onNodeWithText("We never read your words").assertExists()
        onAllNodesWithText("It's how we keep it fair", substring = true).assertCountEquals(0)
        onNodeWithText("Examples:").assertExists()
        onNodeWithText("I arrived").assertExists()
        onNodeWithText("Running late").assertExists()
        onNodeWithText("Your messages are private. Only the signals are logged.").assertExists()
        val stepTwo = onNodeWithText("We never read your words").fetchSemanticsNode().boundsInRoot.top
        val stepOne = onNodeWithText("Use our action chips").fetchSemanticsNode().boundsInRoot.top
        assertTrue("step 1 comes first", stepOne < stepTwo)
        onNodeWithText("Open the chat").performClick()
        assertEquals(1, opened)
        onNodeWithText("Back").performClick()
        assertEquals(1, back)
    }
}
