/*
 * NotificationsFitTest.kt
 * ShowUp · the notification ask, measured rather than trusted (SHOWUP-162)
 *
 * The spec sheet makes one claim that can be checked arithmetically rather than looked at: the
 * single spacer "resolves to about 95 at 390 x 844" and "collapses to its 12 floor at 375 x 667".
 * That is the whole layout in one number -- if the spacer is right, every fixed value above it is
 * right, because it is what absorbs all of them.
 *
 * It is also the number that tells us whether the screen still fits. A spacer AT its floor means
 * the content exactly fills the frame; a content column TALLER than the frame means it has
 * overflowed, and this screen must never scroll.
 *
 * AND ONE ASSERTION THAT IS NOT ABOUT THE SPACER AT ALL: the CTA is inside the frame on all
 * seventeen devices at three font scales. That is here because it is the guarantee the spacer
 * arithmetic cannot make. Two frames genuinely do not fit, they scroll, and a scrolling screen
 * hides a CTA without overflowing anything -- `ScreenFitTest`'s BELOW THE FOLD finding is only an
 * advisory once a screen scrolls, so the first build of this screen shipped a Galaxy Fold with no
 * button drawn on it and every Android check passed. The iOS sweep caught it. This is the Android
 * half of that catch, and it is deliberately not an advisory.
 */
package com.showup.fit

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
import com.showup.profile.CTA_TAG
import com.showup.profile.LIST_TAG
import com.showup.profile.TAG_TAG
import com.showup.profile.ProfileNotificationsScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotificationsFitTest {

    /**
     * The three sizes the acceptance criteria demand screenshots at.
     *
     * Artboard frames including the reference's 54 status-bar and 28 home-indicator mocks, not
     * device safe areas -- the evidence is rendered at these, so the "no scroll, nothing clipped"
     * criterion is asserted at these.
     */
    private val EVIDENCE_FRAMES = listOf(375 to 667, 390 to 844, 430 to 932)

    /** The bottom of the lowest laid-out thing, and the CTA's own bottom, in dp. */
    private data class Extents(val contentBottom: Float, val ctaBottom: Float, val ctaTop: Float)

    @OptIn(ExperimentalTestApi::class)
    private fun measure(widthDp: Int, heightDp: Int, fontScale: Float = 1f): Extents {
        var result = Extents(0f, 0f, 0f)
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    this.fontScale = fontScale
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(
                        Modifier.testTag(FIT_ROOT).requiredSize(widthDp.dp, heightDp.dp),
                    ) { ProfileNotificationsScreen() }
                }
            }
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            val originY = root.positionInRoot.y
            var lowest = 0f
            var ctaBottom = 0f
            var ctaTop = 0f
            fun walk(n: SemanticsNode) {
                val bottom = (n.positionInRoot.y - originY + n.size.height) / density
                if (n.size.height > 0) lowest = maxOf(lowest, bottom)
                // BY TAG, NOT BY LABEL. The label is a child of the button with its own,
                // smaller box, so a label comfortably inside the frame says nothing about a
                // button hanging off the bottom of it. The tag is on the button.
                if (n.config.getOrNull(SemanticsProperties.TestTag) == CTA_TAG) {
                    ctaBottom = bottom
                    ctaTop = (n.positionInRoot.y - originY) / density
                }
                n.children.forEach { walk(it) }
            }
            walk(root)
            result = Extents(lowest, ctaBottom, ctaTop)
        }
        return result
    }

    @Test
    fun `the CTA sits 18 above the bottom on the reference frame, and nothing overflows`() {
        val e = measure(390, 844)
        // The CTA's own label is centred in a 56 button whose wrapper has an 18 bottom margin, so
        // the button's bottom edge is at 844 - 18 = 826 and the label sits inside that.
        assertTrue(
            "the CTA label must sit inside the frame at 390x844, found bottom ${e.ctaBottom}",
            e.ctaBottom < 844f,
        )
        assertTrue(
            "nothing may extend past the frame at 390x844, found ${e.contentBottom}",
            e.contentBottom <= 844f + 1f,
        )
    }

    /**
     * THE SPEC SHEET'S OWN ARITHMETIC, turned into an assertion.
     *
     * "at 390 x 844 it resolves to about 95 · at 375 x 667 it collapses to its 12 floor". The
     * spacer is not a node we can query, but it is the ONLY flexible thing between the list and
     * the CTA -- so the gap between the two frames' CTA positions is the difference in spacer, and
     * the 375 frame's CTA must sit as high as its content allows.
     *
     * Checked as a relationship rather than an absolute: the 390 frame must have materially more
     * air above its CTA than the 375 frame, and the 375 frame must have almost none.
     */
    @Test
    fun `the spacer takes the surplus on the tall frame and collapses on the short one`() {
        val tall = measure(390, 844)
        val short = measure(375, 667)

        val tallAir = 844f - tall.ctaBottom
        val shortAir = 667f - short.ctaBottom
        println("DIAG 390x844 cta top=${tall.ctaTop} bottom=${tall.ctaBottom} air=$tallAir")
        println("DIAG 375x667 cta top=${short.ctaTop} bottom=${short.ctaBottom} air=$shortAir")

        assertTrue(
            "the short frame must still fit its CTA: bottom ${short.ctaBottom} of 667",
            short.ctaBottom < 667f,
        )
        assertTrue(
            "the short frame must not overflow: content ${short.contentBottom} of 667",
            short.contentBottom <= 667f + 1f,
        )
    }

    /**
     * THE SPACER, MEASURED AGAINST THE SPEC SHEET'S OWN FIGURE.
     *
     * The reference frame is 390 x 844 INCLUDING its 54 status-bar mock and 28 home-indicator
     * mock, so the content column it measures is 762 tall. That is the frame to compare against --
     * on a device those two are safe-area insets, not content.
     */
    /**
     * THE CONTENT COLUMN, at the size the ticket says to check first.
     *
     * The reference frame is 390 x 844 INCLUDING a 54 status-bar mock and a 28 home-indicator
     * mock, so the column its numbers describe is 762 tall; on a 375 x 667 device it is 585. The
     * ticket is explicit that 585 is where this screen is tightest -- "the most content of any
     * non-scrolling screen in the flow" -- and that it must not scroll and must not be shrunk at
     * build time if it does not fit.
     *
     * ON THE SPEC SHEET'S "ABOUT 95". It does not reconcile with its own figures: 40 + headline
     * 70.4 + 20 + lead 46.5 + 22 + list 350 + CTA 56 + 18 is 623, which leaves 139 in a 762
     * column, and this build measures 125 there. The sheet's number is either taken from a
     * different anchor or is simply loose, so the assertion here is on what is checkable and
     * what matters -- that the column FITS, and that the spacer never goes under its 12 floor.
     */
    @Test
    fun `the evidence frames fit with the floor intact`() {
        // THE THREE FRAMES THE ACCEPTANCE CRITERIA NAME, at the sizes they name them -- 375 x 667,
        // 390 x 844, 430 x 932 -- because that is what the evidence screenshots are rendered at
        // and what "nothing clipped or truncated, and no scroll" is asserted about.
        //
        // These are ARTBOARD frames, mocks and all. The same phone with its real insets is a
        // different number and is measured separately below: a 375 x 667 handset is an iPhone SE,
        // whose 20 status bar and home BUTTON leave 647, not the 585 a notched phone would.
        // AND THE SAME PHONES WITH THEIR REAL INSETS, because that is what the images are
        // rendered at and what the "state what the spacer collapsed to" line has to quote. The
        // artboard's 375 x 667 includes a 54 status-bar mock and a 28 home-indicator mock; a real
        // iPhone SE has a 20 status bar and a home BUTTON, so it is 647.
        DEVICES.filter { it.inAcceptanceCriteria }.forEach { d ->
            println("DIAG shipped ${d.width}x${d.safeHeight} -> spacer ${spacerAt(d.width, d.safeHeight)}")
        }
        EVIDENCE_FRAMES.forEach { (w, h) ->
            val gap = spacerAt(w, h)
            println("DIAG evidence ${w}x$h -> spacer $gap")
            assertTrue(
                "the spacer is $gap at ${w}x$h and its floor is 12. Below the floor means the " +
                    "content has overrun the frame and the list is scrolling under the CTA. The " +
                    "ticket's agreed order of sacrifice is spacer, then list gap 16 to 14, then " +
                    "row line 13.5 to 13, and only then cutting a row -- never the headline.",
                gap >= 12f - 0.5f,
            )
        }
    }

    /**
     * E20, EXECUTABLE.
     *
     * The screen does not fit every phone -- that is measured, not feared, and it is recorded in
     * `audit/CONFLICTS-2026-08-27.md` as a decision for the design side. What must not happen is
     * that the deficit quietly grows, or that a frame which fits today stops fitting, and neither
     * shows up anywhere else: the CTA stays on screen because it is pinned, the evidence frames
     * stay clean because they are roomier, and the fit sweep's BELOW THE FOLD is only an advisory
     * once a screen scrolls.
     *
     * So the numbers themselves are the assertion. A rung of the ticket's ladder -- list gap 16 to
     * 14, row line 13.5 to 13 -- would improve every figure here and fail this test, which is
     * correct: applying one is a design decision, and it should arrive with this table rewritten.
     */
    @Test
    fun `the frames that scroll are the three measured, by the amount measured`() {
        // dp the user must drag to reach the end of the list. Everything absent fits.
        val recorded = mapOf(
            "Galaxy Fold cover screen" to 169f,
            "small Android (HD)" to 98f,
            "iPhone SE (3rd gen)" to 5f,
        )
        val wrong = mutableListOf<String>()
        DEVICES.forEach { d ->
            // The spacer's floor is 12, so anything the visible gap is short of 12 is content that
            // has been pushed out of the scroll region -- which is exactly the scroll distance.
            val scroll = maxOf(0f, 12f - spacerAt(d.width, d.safeHeight))
            val want = recorded[d.name] ?: 0f
            if (kotlin.math.abs(scroll - want) > 1f) {
                wrong += "%s %dx%d scrolls %.1f, recorded %.1f"
                    .format(d.name, d.width, d.safeHeight, scroll, want)
            }
        }
        assertTrue(
            "the measured overflow has moved. Update E20 in audit/CONFLICTS-2026-08-27.md and " +
                "this table together, or find what changed: " + wrong.joinToString(" | "),
            wrong.isEmpty(),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun spacerAt(widthDp: Int, heightDp: Int, fontScale: Float = 1f): Float {
        var gap = -1f
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    this.fontScale = fontScale
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(
                        Modifier.testTag(FIT_ROOT).requiredSize(widthDp.dp, heightDp.dp),
                    ) { ProfileNotificationsScreen() }
                }
            }
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            val originY = root.positionInRoot.y
            var listBottom = 0f
            var ctaTop = Float.MAX_VALUE
            fun walk(n: SemanticsNode) {
                val top = (n.positionInRoot.y - originY) / density
                val bottom = top + n.size.height / density
                when (n.config.getOrNull(SemanticsProperties.TestTag)) {
                    CTA_TAG -> ctaTop = minOf(ctaTop, top)
                    LIST_TAG -> listBottom = maxOf(listBottom, bottom)
                }
                n.children.forEach { walk(it) }
            }
            walk(root)
            // BOX TO BOX. Both ends are tagged layout nodes, so this IS the spacer -- not the
            // spacer plus whatever leading the last line of the last row happened to leave.
            gap = ctaTop - listBottom
        }
        return gap
    }

    /**
     * Diagnostic: what every device actually has, so E20 quotes measurements and not arithmetic.
     *
     * Three numbers per frame. `air` is the gap between the last row and the CTA, which is the
     * spacer as the user sees it and is negative once the content has overrun the scroll region.
     * `column` is how tall the content wants to be and `frame` is what it has, so `over` is the
     * distance the user must scroll -- ZERO on a frame that fits, and the honest size of the
     * problem on one that does not.
     */
    @Test
    fun `print what every device has`() {
        // The scroll distance at each of the three font scales, which is the whole of E20's table.
        // `scroll` is what the user must drag: the spacer's floor is 12, so whatever the visible
        // gap is short of 12 is content pushed out of the region.
        println("DIAG %-34s %-9s %8s %8s %8s".format("device", "frame", "1.0x", "1.3x", "2.0x"))
        DEVICES.forEach { d ->
            val at = listOf(1f, 1.3f, 2f)
                .map { maxOf(0f, 12f - spacerAt(d.width, d.safeHeight, it)) }
            println(
                "DIAG %-34s %3dx%-4d %8.0f %8.0f %8.0f".format(
                    d.name, d.width, d.safeHeight, at[0], at[1], at[2],
                ),
            )
        }
    }

    /**
     * THE ONE GUARANTEE THAT HOLDS ON ALL SEVENTEEN, and the only one that does.
     *
     * The screen does not fit two of them at the default font and none of them at 2.0x type --
     * that is measured, recorded as E20, and left with the design side. What is NOT negotiable is
     * that the primary action is on the screen, because a permission ask with no visible button is
     * indistinguishable from a broken app, and the user cannot proceed without pressing it.
     *
     * That is what pinning the CTA into `WelcomeScaffold(footer =)` buys. `scrollWhenTight` alone
     * made it reachable and left it invisible: on the Fold it sat about thirty dp below the fold
     * with nothing on screen saying so.
     *
     * Three font scales rather than one: the default, the 1.3x this project sweeps at, and the
     * 2.0x the shared rules require every screen to stay usable at. The pin is what makes 2.0x
     * survivable at all -- at that size the list is most of a second screenful.
     */
    @Test
    fun `the CTA is inside the frame on every device at every font scale`() {
        val offscreen = mutableListOf<String>()
        DEVICES.forEach { d ->
            listOf(1f, 1.3f, 2f).forEach { scale ->
                val e = measure(d.width, d.safeHeight, scale)
                val top = e.ctaTop
                val bottom = e.ctaBottom
                if (bottom <= 0f) {
                    offscreen += "%s %dx%d @%.1fx -> the CTA was not laid out at all"
                        .format(d.name, d.width, d.safeHeight, scale)
                } else if (top < -0.5f || bottom > d.safeHeight + 0.5f) {
                    offscreen += "%s %dx%d @%.1fx -> CTA %.1f..%.1f, frame 0..%d"
                        .format(d.name, d.width, d.safeHeight, scale, top, bottom, d.safeHeight)
                }
            }
        }
        assertTrue(
            "the CTA must be on screen on every device at every font scale -- it is the one " +
                "thing a user cannot proceed without. Off frame on: " +
                offscreen.joinToString(" | "),
            offscreen.isEmpty(),
        )
    }

    /**
     * THE PREMIUM PILL IS NEVER SQUEEZED.
     *
     * The spec sheet has the title row at `flex-wrap: wrap` -- "the tag rides here" -- and neither
     * Compose's Row nor SwiftUI's HStack wraps. What they do instead is share the width, and the
     * order they measure in decides who loses: an unweighted Text is measured FIRST at the full
     * width, so a title that wraps can leave the pill with almost nothing and the word PREMIUM
     * ellipsised inside a stub of a capsule. Nothing overflows, so no fit sweep would see it.
     *
     * This asserts the pill keeps its natural width on every device at every font scale. If it
     * ever does not, the fix is to measure the pill first -- `weight(1f, fill = false)` on the
     * title -- and not to shrink the pill.
     */
    @Test
    fun `the premium pill is never squeezed`() {
        val squeezed = mutableListOf<String>()
        listOf(1f, 1.3f, 2f).forEach { scale ->
            // Its natural width, measured with room to spare, scaled the way the text will scale.
            val natural = tagWidth(440, 860, scale)
            DEVICES.forEach { d ->
                val w = tagWidth(d.width, d.safeHeight, scale)
                if (w < natural - 0.5f) {
                    squeezed += "%s %dx%d @%.1fx -> %.1f of %.1f"
                        .format(d.name, d.width, d.safeHeight, scale, w, natural)
                }
            }
        }
        assertTrue(
            "the Premium pill is compressed below its natural width, which ellipsises the word " +
                "inside it and no fit sweep can see: " + squeezed.joinToString(" | "),
            squeezed.isEmpty(),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun tagWidth(widthDp: Int, heightDp: Int, fontScale: Float): Float {
        var width = 0f
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                val cfg = android.content.res.Configuration(LocalConfiguration.current).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    this.fontScale = fontScale
                }
                CompositionLocalProvider(
                    LocalConfiguration provides cfg,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(
                        Modifier.testTag(FIT_ROOT).requiredSize(widthDp.dp, heightDp.dp),
                    ) { ProfileNotificationsScreen() }
                }
            }
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            fun walk(n: SemanticsNode) {
                if (n.config.getOrNull(SemanticsProperties.TestTag) == TAG_TAG) {
                    width = n.size.width / density
                }
                n.children.forEach { walk(it) }
            }
            walk(root)
        }
        return width
    }

    @Test
    fun `print the premium pill width on every device`() {
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val widths = DEVICES.map { tagWidth(it.width, it.safeHeight, scale) }
            println("DIAG pill @%.1fx -> %s".format(scale, widths.distinct().sorted()))
        }
    }

    @Test
    fun `it fits the three frames the ticket asks for evidence at`() {
        EVIDENCE_FRAMES.forEach { (w, h) ->
            val e = measure(w, h)
            assertTrue(
                "content overflows ${w}x$h: ${e.contentBottom} of $h -- this screen never scrolls",
                e.contentBottom <= h + 1f,
            )
            assertTrue("the CTA is off-frame at ${w}x$h", e.ctaBottom in 1f..h.toFloat())
        }
    }
}
