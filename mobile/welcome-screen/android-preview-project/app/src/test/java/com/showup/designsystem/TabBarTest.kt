/*
 * TabBarTest.kt
 * ShowUp · the four-tab bar and its Likes badge (SHOWUP-187 rule 10, SHOWUP-186 ②), and the
 * `tab_switched` rule it feeds
 *
 * The badge is part of the Likes tab's NAME ("Likes, 2 open likes"), so it is asserted the way a
 * screen reader meets it. Its drawing -- the content-box pill, 15 / -4 off the icon -- is the fit
 * harness's and the sheet's; what can be wrong in a way the eye misses is here: the count's text, the
 * 99+ cap, 0 showing nothing, and which tab gets the badge.
 */
package com.showup.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.analytics.NavAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class TabBarTest {

    private fun description(tab: Tab, likes: Int): String? {
        var name: String? = null
        runComposeUiTest {
            setContent { TabBar(active = Tab.Search, onTab = {}, likes = likes) }
            name = onNodeWithTag(tabTag(tab)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        }
        return name
    }

    @Test
    fun `the four tabs are Search, Likes, Instant, Settings, in that order`() {
        assertEquals(listOf("Search", "Likes", "Instant", "Settings"), Tab.entries.map { it.label })
        // §37 nav_tab, the values tab_switched carries.
        assertEquals(listOf("search", "likes", "instant", "settings"), Tab.entries.map { it.trackingValue })
    }

    @Test
    fun `the badge reads the count, caps at 99+, and shows nothing at 0`() {
        assertNull(likesBadgeText(0))
        assertNull(likesBadgeText(-1))
        assertEquals("1", likesBadgeText(1))
        assertEquals("99", likesBadgeText(99))
        assertEquals("99+", likesBadgeText(100))
    }

    @Test
    fun `the Likes tab is named with its badge, 1 open like or n open likes`() {
        assertEquals("Likes, 1 open like", description(Tab.Likes, likes = 1))
        assertEquals("Likes, 2 open likes", description(Tab.Likes, likes = 2))
        // The cap is the drawing's; the spoken count is the real one.
        assertEquals("Likes, 120 open likes", description(Tab.Likes, likes = 120))
        assertEquals("Likes", description(Tab.Likes, likes = 0))
    }

    @Test
    fun `only the Likes tab carries the badge`() {
        assertEquals("Search", description(Tab.Search, likes = 3))
        assertEquals("Instant", description(Tab.Instant, likes = 3))
        assertEquals("Settings", description(Tab.Settings, likes = 3))
    }

    @Test
    fun `a tap reports the tab, and the active one is selected`() = runComposeUiTest {
        var tapped: Tab? = null
        setContent { TabBar(active = Tab.Settings, onTab = { tapped = it }, likes = 2) }
        onNodeWithTag(tabTag(Tab.Settings)).assertIsSelected()
        onNodeWithTag(tabTag(Tab.Likes)).performClick()
        assertEquals(Tab.Likes, tapped)
        val tabs = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        assertEquals(4, tabs.size)
    }

    @Test
    fun `tab_switched fires for another tab, never for the one already showing`() {
        assertNull("a tap on the active tab is no nav act", NavAnalytics.tabSwitched(Tab.Search, Tab.Search))
        val event = NavAnalytics.tabSwitched(Tab.Search, Tab.Likes)
        assertEquals(NavAnalytics.TAB_SWITCHED, event?.first)
        assertEquals("likes", event?.second?.get("tab"))
        assertEquals("sensitivity_class 0, as on every row", 0, event?.second?.get("sensitivity_class"))
        assertEquals(com.showup.profile.Stamp.of(0)["field_registry_version"], event?.second?.get("field_registry_version"))
        assertEquals("settings", NavAnalytics.tabSwitched(Tab.Likes, Tab.Settings)?.second?.get("tab"))
    }
}
