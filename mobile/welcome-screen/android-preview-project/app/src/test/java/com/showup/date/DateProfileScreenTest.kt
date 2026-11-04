/*
 * DateProfileScreenTest.kt
 * ShowUp · the match's profile opened from the Date screen, read-only (SHOWUP-193)
 *
 * Search's own profile screen with nothing to decide: no action bar, no Like, no Not now, no Confirm
 * date; a back pill to the date; the TabBar locked as on the Date screen, so a tap on a locked tab
 * reaches nobody. And the Date screen's own failed first read offers Try again.
 */
package com.showup.date

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.api.CallFailure
import com.showup.designsystem.Tab
import com.showup.designsystem.tabTag
import com.showup.search.SEARCH_ACTION_BAR_TAG
import com.showup.search.SearchCopy
import com.showup.search.SearchProfileScreen
import com.showup.search.SearchSamples
import com.showup.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class DateProfileScreenTest {
    private val shown = SearchUiState(loading = false, queue = listOf(SearchSamples.FULL))

    @Test
    fun `the read-only profile has no action bar, no Like, no Not now and no Confirm date`() = runComposeUiTest {
        setContent {
            SearchProfileScreen(
                state = shown, readOnly = true, backLabel = DateCopy.PROFILE_BACK,
                disabledTabs = DATE_LOCKED_TABS, disabledHint = DateCopy.TAB_HINT,
            )
        }
        onAllNodesWithTag(SEARCH_ACTION_BAR_TAG, useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText(SearchCopy.LIKE).assertCountEquals(0)
        onAllNodesWithText(SearchCopy.NOT_NOW).assertCountEquals(0)
        onAllNodesWithText("Confirm date").assertCountEquals(0)
        onAllNodesWithContentDescription(DateCopy.PROFILE_BACK).assertCountEquals(1)
    }

    @Test
    fun `its back pill returns to the date, and a locked tab reaches nobody`() = runComposeUiTest {
        var backs = 0
        val tabs = mutableListOf<Tab>()
        setContent {
            SearchProfileScreen(
                state = shown, readOnly = true, backLabel = DateCopy.PROFILE_BACK,
                disabledTabs = DATE_LOCKED_TABS, disabledHint = DateCopy.TAB_HINT,
                onBack = { backs++ }, onTab = { tabs += it },
            )
        }
        onNodeWithContentDescription(DateCopy.PROFILE_BACK).performClick()
        assertEquals(1, backs)
        onNodeWithTag(tabTag(Tab.Search)).performClick()
        onNodeWithTag(tabTag(Tab.Likes)).performClick()
        onNodeWithTag(tabTag(Tab.Instant)).performClick()
        assertTrue("a locked tab calls nothing, so nothing can be tracked", tabs.isEmpty())
        onNodeWithTag(tabTag(Tab.Settings)).performClick()
        assertEquals(listOf(Tab.Settings), tabs)
    }

    @Test
    fun `a Date screen whose first read failed offers Try again`() = runComposeUiTest {
        var retries = 0
        setContent {
            DateScreen(
                DateUi(now = Instant.parse("2026-11-03T15:00:00Z"), loading = false, failed = CallFailure.Network),
                onRetry = { retries++ },
            )
        }
        onNodeWithText(DateCopy.LOAD_FAILED).assertExists()
        onNodeWithText(SearchCopy.TRY_AGAIN).performClick()
        assertEquals(1, retries)
    }
}
