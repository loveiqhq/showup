/*
 * TabBarDisabledTest.kt
 * ShowUp · the TabBar's disabled state: the active-date lock (SHOWUP-193 ⑰)
 *
 * While a date is active, Search, Likes and Instant are disabled and Settings stays open. A tap on a
 * disabled tab shows `Available again once your date is finished.` above the bar for 3200 ms, a
 * re-tap restarts it, and nothing navigates. The clock is driven by hand, so "3200 ms" is asserted
 * as a duration rather than waited out.
 */
package com.showup.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
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
class TabBarDisabledTest {
    private val hint = "Available again once your date is finished."
    private val locked = setOf(Tab.Search, Tab.Likes, Tab.Instant)

    @Test
    fun `a disabled tap shows the hint and never navigates`() = runComposeUiTest {
        val taps = mutableListOf<Tab>()
        setContent { TabBar(active = null, onTab = { taps += it }, disabled = locked, disabledHint = hint) }
        onNodeWithText(hint).assertDoesNotExist()

        onNodeWithTag(tabTag(Tab.Likes)).performClick()
        onNodeWithText(hint).assertExists()
        assertEquals(emptyList<Tab>(), taps)
    }

    @Test
    fun `the hint is gone 3200 ms after the tap`() = runComposeUiTest {
        setContent { TabBar(active = null, onTab = {}, disabled = locked, disabledHint = hint) }
        mainClock.autoAdvance = false
        onNodeWithTag(tabTag(Tab.Search)).performClick()
        mainClock.advanceTimeBy(Motion.TAB_HINT - 100L)
        onNodeWithText(hint).assertExists()
        mainClock.advanceTimeBy(100L + Motion.REVEAL + 50L)
        onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun `a second tap restarts the 3200 ms rather than stacking a second hint`() = runComposeUiTest {
        setContent { TabBar(active = null, onTab = {}, disabled = locked, disabledHint = hint) }
        mainClock.autoAdvance = false
        onNodeWithTag(tabTag(Tab.Search)).performClick()
        mainClock.advanceTimeBy(3000L)
        onNodeWithTag(tabTag(Tab.Instant)).performClick()
        // 6100 ms from the first tap is 3100 from the second: still up, and only once.
        mainClock.advanceTimeBy(3100L)
        onNodeWithText(hint).assertExists()
        mainClock.advanceTimeBy(100L + Motion.REVEAL + 50L)
        onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun `Settings stays open while the others are locked`() = runComposeUiTest {
        val taps = mutableListOf<Tab>()
        setContent { TabBar(active = null, onTab = { taps += it }, disabled = locked, disabledHint = hint) }
        onNodeWithTag(tabTag(Tab.Settings)).performClick()
        assertEquals(listOf(Tab.Settings), taps)
        onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun `every tab, locked or not, keeps a 48 dp target`() = runComposeUiTest {
        setContent { TabBar(active = null, onTab = {}, disabled = locked, disabledHint = hint) }
        Tab.entries.forEach {
            onNodeWithTag(tabTag(it)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}
