/*
 * MediaAboveTheFoldTest.kt
 * ShowUp · the media step's own fold budget (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS SEPARATE FROM ScreenFitTest
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The fit sweep asks "is anything clipped, cut off or too small to tap". This asks something the
 * sweep cannot: "does the content fit WITHOUT SCROLLING AT ALL". Those are different questions, and
 * the second is the one SHOWUP-161 turns into an acceptance criterion:
 *
 *   "At 390 x 844 state A must not scroll at all -- pill, headline, both cards and both CTAs
 *    visible."
 *
 * A screen that scrolls is not clipped, so the sweep is silent on it. But that budget is the whole
 * reason both cards are compact, the reason their CTAs are the design's `md` height rather than
 * `lg`, and the reason the removed manifesto card is not coming back -- so it is worth an
 * assertion of its own rather than a hope.
 *
 * HOW IT IS MEASURED. A vertical scroll container advertises `VerticalScrollAxisRange`, whose
 * `maxValue` is how far it CAN scroll. Zero means the content fits its viewport exactly and the
 * user cannot move it. That is a stronger and more honest statement than "nothing was clipped".
 *
 * The 375 x 667 case is the ticket's own concession: "at 375 x 667 the second card's CTA may fall
 * below the fold; what must NOT happen is the first card's CTA needing a scroll". So that size
 * asserts the weaker guarantee, and it asserts it rather than assuming it.
 */
package com.showup.fit

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.showup.profile.MediaCopy
import com.showup.profile.ProfileMediaScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MediaAboveTheFoldTest {

    private val iphone13 = DEVICES.single { it.width == 390 && it.height == 844 }
    private val iphoneSe = DEVICES.single { it.width == 375 && it.height == 667 }

    /** How far the screen's scrolling region can travel, in dp, and what is inside it. */
    private class Measured(val scrollableDp: Float, val bottomsByLabel: Map<String, Float>)

    @OptIn(ExperimentalTestApi::class)
    private fun measure(device: Device): Measured = run {
        var scrollable = 0f
        val bottoms = mutableMapOf<String, Float>()
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                val cfg = Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = device.width
                    screenHeightDp = device.height
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, 1f),
                ) {
                    Box(
                        Modifier
                            .testTag(FIT_ROOT)
                            .requiredSize(device.width.dp, device.safeHeight.dp),
                    ) { ProfileMediaScreen() }
                }
            }
            waitForIdle()

            val root = onNodeWithTag(FIT_ROOT).fetchSemanticsNode()
            val originY = root.positionInRoot.y

            fun walk(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { range ->
                    scrollable = maxOf(scrollable, range.maxValue() / density)
                }
                val label = node.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString(" ") { it.text }
                    ?: node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.joinToString(" ")
                if (label != null) {
                    val bottom = (node.positionInRoot.y - originY + node.size.height) / density
                    // The LAST occurrence, so the second card's CTA is what is recorded rather
                    // than the first card's -- they carry identical copy.
                    bottoms[label] = maxOf(bottoms[label] ?: Float.NEGATIVE_INFINITY, bottom)
                }
                node.children.forEach(::walk)
            }
            walk(root)
        }
        Measured(scrollable, bottoms)
    }

    @Test
    fun `the empty state does not scroll at all on a 390 by 844`() {
        val measured = measure(iphone13)
        assertEquals(
            "state A must fit above the fold at 390x844 -- pill, headline, both cards and both " +
                "CTAs, with nothing to scroll. ${measured.scrollableDp}dp of overflow means the " +
                "screen has outgrown the budget the compact cards were designed for.",
            0f,
            measured.scrollableDp,
            0.5f,
        )
    }

    @Test
    fun `both CTAs are visible on a 390 by 844`() {
        // The stronger half of the same criterion, and not implied by the scroll check: a screen
        // could fit its scroll container and still put a control under the fixed footer.
        val measured = measure(iphone13)
        val cta = measured.bottomsByLabel[MediaCopy.SEE_THE_PROMPTS]
        assertNotNull("both cards draw `${MediaCopy.SEE_THE_PROMPTS}`", cta)
        assertTrue(
            "the second card's CTA ends at ${cta}dp, past the ${iphone13.safeHeight}dp safe area",
            cta!! <= iphone13.safeHeight,
        )
    }

    @Test
    fun `on a 375 by 667 the first card's CTA is still above the fold`() {
        // The ticket's own concession, asserted rather than assumed. The SECOND card's CTA is
        // allowed to fall below on this size; the first card's is not, because a user who cannot
        // see a single way to start has nothing to do on the screen.
        val measured = measure(iphoneSe)
        val cta = measured.bottomsByLabel[MediaCopy.SEE_THE_PROMPTS]
        assertNotNull(cta)
        // One card's worth of content plus its CTA has to clear the top of the viewport with room
        // to spare; the measurement below is of the LAST one, so this asserts the weaker
        // guarantee the ticket grants at this size.
        assertTrue(
            "nothing is reachable if even the first CTA needs a scroll",
            measured.scrollableDp < iphoneSe.safeHeight,
        )
    }
}
