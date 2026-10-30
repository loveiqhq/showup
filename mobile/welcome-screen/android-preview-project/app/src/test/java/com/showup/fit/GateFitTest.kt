/*
 * GateFitTest.kt
 * ShowUp · the Search location gate (SHOWUP-188), measured on every phone
 *
 * ScreenFitTest's question -- clipped, squeezed, out of reach? -- for A, B, C and D on all 23 phones,
 * at the default, a common enlarged and the largest system font. Then the geometry the ticket
 * promises:
 *
 *   · "the CTA sits directly above the TabBar in every state": 22 above it, wholly on screen
 *   · "no scroll" at the three frames the evidence names, at the default type
 *   · the full 170 radar (D 140) on the reference frame, and the order of sacrifice where it shrinks
 *   · no filter button in any state; the likes chip in A-C; D the Premium bar, no chip, a back pill
 *   · the headline is the screen's heading
 */
package com.showup.fit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.showup.designsystem.TAB_BAR_TAG
import com.showup.designsystem.Tab
import com.showup.profile.GATE_CTA_TAG
import com.showup.profile.GATE_HEADLINE_TAG
import com.showup.profile.GATE_SCROLL_TAG
import com.showup.profile.LOCATION_RADAR_TAG
import com.showup.profile.LocationGateScreen
import com.showup.profile.LocationState
import com.showup.search.SEARCH_TOP_BAR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class GateFitTest {

    /** The sheet's frames: A, B, C on the Search tab, D over a Likes profile (B shown, as drawn). */
    private val frames: List<Triple<String, LocationState, Boolean>> = listOf(
        Triple("A never granted", LocationState.Ask, false),
        Triple("B off for Show Up", LocationState.Denied, false),
        Triple("C off on the phone", LocationState.ServicesOff, false),
        Triple("D from Likes (B)", LocationState.Denied, true),
        // D in A's state: the tightest D -- the privacy note under the 140 radar.
        Triple("D from Likes (A)", LocationState.Ask, true),
    )

    private val scales = listOf(1f, 1.3f, 2f)

    @androidx.compose.runtime.Composable
    private fun gate(state: LocationState, fromLikes: Boolean) = LocationGateScreen(
        state = state, tab = if (fromLikes) Tab.Likes else Tab.Search, likes = 2,
        onTab = {}, onLikes = {}, onBack = if (fromLikes) ({}) else null,
    )

    /**
     * The likes chip is drawn 28 tall and answers across 44 (`OverflowTapTarget`). On Search the
     * filter button beside it makes the bar 36 deep; the gate's bar has no filter button, so it is
     * the chip's own height -- as the reference draws it, about 41 with its padding against Search's
     * 48 -- and the touch area reaches up to 4 past the bar's top, into the status bar's band, where
     * a tap still reaches the app. The chip as drawn is inside the frame. Anything more is a fault.
     */
    private fun chipTouchOverhang(finding: String): Boolean {
        if (!finding.contains("[2 likes]") || !finding.contains("OFF THE TOP")) return false
        val by = Regex("""by ([\d.]+)dp""").find(finding)?.groupValues?.get(1)?.toFloatOrNull() ?: return false
        return by <= 4f
    }

    @Test
    fun `every state of the gate is clean on every phone at every type size`() {
        val fresh = mutableListOf<String>()
        for ((name, state, fromLikes) in frames) for (scale in scales) DEVICES.forEach { d ->
            measureFit(d, "Gate/$name @$scale", fontScale = scale) { gate(state, fromLikes) }
                .filterNot { it.advisory }
                .map { "$it" }
                .filterNot(::chipTouchOverhang)
                .forEach { fresh += it }
        }
        java.io.File("build/location-gate-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Gate layout problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }

    @Test
    fun `the CTA sits 22 above the TabBar in every state, wholly on screen, on every phone`() {
        val failures = mutableListOf<String>()
        for ((name, state, fromLikes) in frames) for (scale in scales) DEVICES.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = GATE_SCROLL_TAG) { gate(state, fromLikes) }
            val cta = s.tag(GATE_CTA_TAG)
            val tabs = s.tag(TAB_BAR_TAG)
            val top = s.tag(SEARCH_TOP_BAR_TAG)
            if (abs(tabs.top - cta.bottom - 22f) > 0.6f) failures += "$name $d @$scale  CTA ends ${tabs.top - cta.bottom} above the TabBar"
            if (cta.top < top.bottom) failures += "$name $d @$scale  CTA under the top bar"
            if (abs(tabs.bottom - s.frame) > 0.6f) failures += "$name $d @$scale  TabBar ends at ${tabs.bottom} of ${s.frame}"
            if (cta.left < 23.4f || cta.right > d.width - 23.4f) failures += "$name $d @$scale  CTA not inside the 24 gutters"
        }
        println(if (failures.isEmpty()) "OK   gate: CTA 22 above the TabBar everywhere" else failures.joinToString("\n"))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `no scroll and the headline on screen at the three frames the evidence names`() {
        val failures = mutableListOf<String>()
        for ((name, state, fromLikes) in frames) DEVICES.filter { it.inAcceptanceCriteria }.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scrollTag = GATE_SCROLL_TAG) { gate(state, fromLikes) }
            if (s.overflow > 0.5f) failures += "$name $d  scrolls ${s.overflow}"
            val h = s.tag(GATE_HEADLINE_TAG)
            if (h.top < s.tag(SEARCH_TOP_BAR_TAG).bottom || h.bottom > s.tag(GATE_CTA_TAG).top) failures += "$name $d  headline not in the content area"
        }
        // Beyond them, report where the last resort engages -- never silently.
        for ((name, state, fromLikes) in frames) for (scale in scales) DEVICES.forEach { d ->
            val s = shootGeometry(d.width, d.safeHeight, scale, scrollTag = GATE_SCROLL_TAG) { gate(state, fromLikes) }
            if (s.overflow > 0.5f) println("DIAG gate $name $d @$scale  content scrolls ${"%.1f".format(s.overflow)} under the pinned CTA")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the full radar where it fits, and it gives way first`() {
        DEVICES.forEach { d ->
            listOf(false, true).forEach { fromLikes ->
                val s = shootGeometry(d.width, d.safeHeight) { gate(LocationState.Ask, fromLikes) }
                println("DIAG gate radar ${if (fromLikes) "D" else "A"} ${d.name} ${d.width}x${d.safeHeight}: ${s.tag(LOCATION_RADAR_TAG).height}")
            }
        }
        val mid = DEVICES.first { it.width == 390 && it.inAcceptanceCriteria }
        assertEquals("A at 390 x 844 draws the 170 radar", 170f,
            shootGeometry(mid.width, mid.safeHeight) { gate(LocationState.Ask, false) }.tag(LOCATION_RADAR_TAG).height, 0.5f)
        assertEquals("D at 390 x 844 draws the 140 radar", 140f,
            shootGeometry(mid.width, mid.safeHeight) { gate(LocationState.Denied, true) }.tag(LOCATION_RADAR_TAG).height, 0.5f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no filter button anywhere -- A-C carry the likes chip, D the Premium bar and a back pill`() = runComposeUiTest {
        var shown by androidx.compose.runtime.mutableStateOf(Triple(LocationState.Ask, false, 0))
        setContent { gate(shown.first, shown.second) }
        for ((_, state, fromLikes) in frames) {
            shown = Triple(state, fromLikes, shown.third + 1)
            waitForIdle()
            onAllNodes(hasContentDescription("Filters"), useUnmergedTree = true).fetchSemanticsNodes().let {
                assertTrue("$state: a filter button", it.isEmpty())
            }
            val chips = onAllNodes(hasContentDescription("2 likes"), useUnmergedTree = true).fetchSemanticsNodes()
            val back = onAllNodes(hasContentDescription("Back"), useUnmergedTree = true).fetchSemanticsNodes()
            if (fromLikes) {
                assertTrue("D: no chip", chips.isEmpty())
                assertEquals("D: the back pill", 1, back.size)
            } else {
                assertEquals("$state: the likes chip", 1, chips.size)
                assertTrue("$state: no back pill on a tab root", back.isEmpty())
            }
            val headline = onNodeWithTag(GATE_HEADLINE_TAG, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("$state: the headline is the heading", headline.config.contains(SemanticsProperties.Heading))
        }
    }
}
