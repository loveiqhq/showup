/*
 * CheckInSemanticsTest.kt
 * ShowUp · what TalkBack is told on every Check-In screen (SHOWUP-178 to SHOWUP-183)
 *
 * Each ticket's accessibility rules, asserted on the semantics tree rather than read off the source:
 * the ✕'s label, the slot announcements, the radio group, the checkbox chips, the sliders' label,
 * value and range, the field's label and error, the live regions, the dialog pane that traps focus,
 * the headings -- and the intro's numerals, which are decoration and must say nothing.
 */
package com.showup.checkin

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class CheckInSemanticsTest {

    private val days = checkInDays(LocalDateTime.of(2026, 10, 13, 16, 20))

    private fun roleOf(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `the close button is named Close Check-In and is a button`() = runComposeUiTest {
        setContent { CheckInMotivationScreen(selected = null) }
        val close = onNodeWithContentDescription(CheckInCopy.CLOSE_LABEL).fetchSemanticsNode()
        assertEquals(Role.Button, close.config.getOrNull(SemanticsProperties.Role))
        assertNotNull(close.config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun `the intro's numerals say nothing, its headline is a heading`() = runComposeUiTest {
        setContent { CheckInIntroScreen() }
        // The merged tree: what TalkBack reads.
        for (n in 1..5) assertEquals("numeral $n is decoration", 0, onAllNodesWithText("$n").fetchSemanticsNodes().size)
        // The em run is joined by a no-break space, so match the words before it.
        assertTrue(onAllNodesWithText("Check-in to be dated", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNode(isHeading() and hasText("dated", substring = true)).assertExists()
    }

    @Test
    fun `a slot is announced with its hour and its state`() = runComposeUiTest {
        val grid = gridOf(CheckInStep.Today, days)
        setContent { CheckInAvailabilityScreen(grid, mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Available), false) }
        fun state(label: String) = onNode(hasContentDescription(label) and roleOf(Role.Button))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("not selected", state("5pm"))
        assertEquals("preferred", state("6pm"))
        assertEquals("available", state("7pm"))
    }

    @Test
    fun `How it works says whether it is open`() = runComposeUiTest {
        val grid = gridOf(CheckInStep.Today, days)
        setContent { CheckInAvailabilityScreen(grid, emptyMap(), true) }
        val disclosure = onNode(hasContentDescription(CheckInCopy.HOW_IT_WORKS) and roleOf(Role.Button)).fetchSemanticsNode()
        assertEquals("Expanded", disclosure.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun `Clear all is a button named Clear all`() = runComposeUiTest {
        // Found unnamed on a device: its touch area is a sibling of the text, so it carried no name.
        val grid = gridOf(CheckInStep.Today, days)
        setContent { CheckInAvailabilityScreen(grid, mapOf(18 to SlotTier.Preferred), false) }
        onNode(hasContentDescription(CheckInCopy.CLEAR_ALL) and roleOf(Role.Button)).assertExists()
    }

    @Test
    fun `the confirm is a named dialog pane and the screen behind is out of reach`() = runComposeUiTest {
        val grid = gridOf(CheckInStep.Today, days)
        setContent { CheckInAvailabilityScreen(grid, emptyMap(), false, dialogOpen = true) }
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, CheckInCopy.NO_HOURS_DIALOG_LABEL)).assertExists()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)).assertExists()
        assertEquals(0, onAllNodesWithText(CheckInCopy.LEGEND_LEAD).fetchSemanticsNodes().size)
        onNode(hasText(CheckInCopy.NO_HOURS_CANCEL)).assertExists()
    }

    @Test
    fun `motivation is a radio group, the picked row selected and no other`() = runComposeUiTest {
        setContent { CheckInMotivationScreen(selected = "casual") }
        onNode(hasContentDescription(CheckInCopy.MOTIVATION_GROUP) and SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
        val rows = onAllNodes(roleOf(Role.RadioButton)).fetchSemanticsNodes()
        assertEquals(5, rows.size)
        val selected = rows.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals(1, selected.size)
        assertTrue(selected.single().config.getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }.contains("Casual dating"))
    }

    @Test
    fun `vibe chips are checkboxes and the counter is a polite live region`() = runComposeUiTest {
        setContent { CheckInVibeScreen(picked = setOf("calm")) }
        val chips = onAllNodes(roleOf(Role.Checkbox)).fetchSemanticsNodes()
        assertEquals(20, chips.size)
        val on = chips.filter { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
        assertEquals(listOf("Calm & peaceful"), on.map { it.config[SemanticsProperties.ContentDescription].single() })
        val counter = onNode(hasText("1/5")).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, counter.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `each slider thumb has its label, its value, its range and can be set`() = runComposeUiTest {
        setContent { CheckInFiltersScreen(CheckInFilters(setOf("woman"), minAge = 25, maxAge = 35, distanceKm = 15)) }
        fun thumb(label: String) = onNodeWithContentDescription(label).fetchSemanticsNode().config
        val min = thumb(CheckInCopy.SLIDER_MIN_AGE)
        assertEquals("25", min.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(18f..35f, min.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.range)
        assertNotNull(min.getOrNull(SemanticsActions.SetProgress))
        val max = thumb(CheckInCopy.SLIDER_MAX_AGE)
        assertEquals(25f..65f, max.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.range)
        assertEquals("15", thumb(CheckInCopy.SLIDER_DISTANCE).getOrNull(SemanticsProperties.StateDescription))
        assertEquals(4, onAllNodes(roleOf(Role.Checkbox)).fetchSemanticsNodes().size)
    }

    @Test
    fun `the uplift field is labelled, invalid with contact info, and the counter is polite`() = runComposeUiTest {
        setContent { CheckInUpliftScreen(text = "Text me on 0176 2345 6789", blocked = true) }
        val field = onNodeWithContentDescription(CheckInCopy.UPLIFT_FIELD_LABEL).fetchSemanticsNode().config
        assertEquals(CheckInCopy.CONTACT_ERROR, field.getOrNull(SemanticsProperties.Error))
        val counter = onNode(hasText("75 characters left")).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, counter.config.getOrNull(SemanticsProperties.LiveRegion))
        // The inspirations are text, never buttons.
        val line = onNode(hasText("have 2x tickets for Eminem today", substring = true)).fetchSemanticsNode()
        assertEquals(null, line.config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun `a toast is a polite live region`() = runComposeUiTest {
        setContent { CheckInMotivationScreen(selected = null, toast = CheckInCopy.MOTIVATION_TOAST, toastTick = 1) }
        val toast = onNode(hasText(CheckInCopy.MOTIVATION_TOAST)).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, toast.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `the tab bar is four tabs with Search selected`() = runComposeUiTest {
        setContent { TabBar(active = Tab.Search, onTab = {}) }
        val tabs = onAllNodes(roleOf(Role.Tab)).fetchSemanticsNodes()
        // Search · Likes · Instant · Settings for every user (SHOWUP-187 rule 10).
        assertEquals(4, tabs.size)
        assertEquals(1, tabs.count { it.config.getOrNull(SemanticsProperties.Selected) == true })
    }
}
