/*
 * SearchSemanticsTest.kt
 * ShowUp · what TalkBack is told on Date search 01 (SHOWUP-184)
 *
 * The ticket's Accessibility section, asserted on the semantics tree rather than read off the source:
 * the three actions' names, the top bar's labels, the shared chips' announced state, the coach and
 * both sheets as named dialogs with everything behind them hidden, the counter's live region -- and
 * focus going back to Like when the coach or a sheet closes.
 */
package com.showup.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class SearchSemanticsTest {

    private fun roleOf(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun pane(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
    private fun button(label: String) = hasContentDescription(label) and roleOf(Role.Button)

    @Test
    fun `the three actions are buttons with the ticket's names, and Like shows its label`() = runComposeUiTest {
        setContent { SearchProfileScreen(SearchSamples.rest()) }
        onNode(button(SearchCopy.NOT_INTERESTED)).assertExists()
        onNode(button(SearchCopy.NOT_NOW)).assertExists()
        val like = onNodeWithTag(SEARCH_LIKE_TAG).fetchSemanticsNode()
        assertEquals(Role.Button, like.config.getOrNull(SemanticsProperties.Role))
        onNodeWithTag(SEARCH_LIKE_TAG).assert(hasText(SearchCopy.LIKE))
    }

    @Test
    fun `the filter button is named Filters and the likes chip says its count, one in the singular`() = runComposeUiTest {
        var likes by mutableStateOf(2)
        setContent { SearchProfileScreen(SearchSamples.rest(likes = likes)) }
        onNode(button(SearchCopy.FILTERS_LABEL)).assertExists()
        onNode(button("2 likes")).assertExists()
        likes = 1
        waitForIdle()
        onNode(button("1 like")).assertExists()
    }

    @Test
    fun `a shared chip is announced as shared, not only drawn in orange`() = runComposeUiTest {
        setContent { SearchProfileScreen(SearchSamples.rest()) }
        val shared = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "shared"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the shared vibes row announces its state", shared.isNotEmpty())
    }

    @Test
    fun `the coach is a dialog named for what it explains, and the three buttons are hidden behind it`() = runComposeUiTest {
        setContent { SearchProfileScreen(SearchSamples.rest().copy(coach = true)) }
        val coach = onNode(pane(SearchCopy.COACH_LABEL)).fetchSemanticsNode()
        assertTrue("a dialog", coach.config.contains(SemanticsProperties.IsDialog))
        for (label in listOf(SearchCopy.NOT_INTERESTED, SearchCopy.NOT_NOW)) {
            assertEquals("$label is out of reach under the coach", 0, onAllNodes(button(label)).fetchSemanticsNodes().size)
        }
        assertEquals(0, onAllNodes(hasText(SearchCopy.LIKE) and roleOf(Role.Button)).fetchSemanticsNodes().size)
    }

    @Test
    fun `both sheets are named dialogs, the screen behind them is hidden, and the counter is a polite live region`() = runComposeUiTest {
        var state by mutableStateOf(SearchSamples.rest().copy(sheet = LikeSheet.Confirm))
        setContent { SearchProfileScreen(state) }
        onNode(pane(SearchCopy.BE_SURE)).assertExists()
        assertEquals(0, onAllNodes(button(SearchCopy.NOT_NOW)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(button(SearchCopy.FILTERS_LABEL)).fetchSemanticsNodes().size)

        state = state.copy(sheet = LikeSheet.Message, draft = messageDraft("Leonie", "today"))
        waitForIdle()
        val title = SearchCopy.MESSAGE_TITLE_EMPHASIS + SearchCopy.MESSAGE_TITLE_REST.format("Leonie")
        onNode(pane(title)).assertExists()
        val counter = onAllNodes(hasText("/ 100", substring = true), useUnmergedTree = true).fetchSemanticsNodes().single()
        assertEquals(LiveRegionMode.Polite, counter.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    /** Keyboard input mode: a `clickable` takes focus only outside touch mode, as under TalkBack. */
    private fun androidx.compose.ui.test.ComposeUiTest.keyboardMode(input: () -> InputModeManager) {
        runOnIdle { input().requestInputMode(InputMode.Keyboard) }
    }

    @Test
    fun `closing a sheet gives focus back to Like, and nothing takes it before`() = runComposeUiTest {
        var state by mutableStateOf(SearchSamples.rest().copy(sheet = LikeSheet.Confirm))
        lateinit var input: InputModeManager
        setContent {
            input = LocalInputModeManager.current
            SearchProfileScreen(state)
        }
        keyboardMode { input }
        // Behind the sheet Like is out of TalkBack's tree altogether, so only the unmerged tree has it.
        onNodeWithTag(SEARCH_LIKE_TAG, useUnmergedTree = true).assertIsNotFocused()
        state = state.copy(sheet = null)
        waitForIdle()
        onNodeWithTag(SEARCH_LIKE_TAG).assertIsFocused()
    }

    @Test
    fun `closing the coach gives focus back to Like`() = runComposeUiTest {
        var state by mutableStateOf(SearchSamples.rest().copy(coach = true))
        lateinit var input: InputModeManager
        setContent {
            input = LocalInputModeManager.current
            SearchProfileScreen(state)
        }
        keyboardMode { input }
        state = state.copy(coach = false)
        waitForIdle()
        onNodeWithTag(SEARCH_LIKE_TAG).assertIsFocused()
    }
}
