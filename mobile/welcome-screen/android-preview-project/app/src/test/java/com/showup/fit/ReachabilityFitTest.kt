/*
 * ReachabilityFitTest.kt
 * ShowUp · the four layout promises Stay reachable makes, measured (SHOWUP-163)
 *
 * `ScreenFitTest` sweeps this screen at seventeen sizes and three font scales and reports anything
 * clipped, collapsed or too small to tap. What it CANNOT do is drive a scroll, and three of this
 * ticket's acceptance criteria are about exactly that:
 *
 *   · "The body scrolls and the CTA stays pinned"
 *   · "The fine print scrolls fully clear of the CTA"
 *   · "Every toggle and checkbox row is a >= 44pt hit target"
 *
 * A pinned footer is the one arrangement where a BELOW THE FOLD finding means nothing and a CTA
 * off the bottom of the frame means everything -- so the sweep marks those advisory and this file
 * asserts the thing that actually matters, the way `NotificationsFitTest` does for 09.
 *
 * WHAT THIS CANNOT PROVE: that a finger reaches the CTA. Robolectric measures a layout; it does
 * not know about a gesture navigation bar, a keyboard, or a case. The 44 floor is the proxy.
 */
package com.showup.fit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.showup.profile.InterestChannel
import com.showup.profile.ProfileReachabilityScreen
import com.showup.profile.REACH_CTA_TAG
import com.showup.profile.REACH_ERROR_TAG
import com.showup.profile.REACH_FINEPRINT_TAG
import com.showup.profile.ReachabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReachabilityFitTest {

    /** Everything one render can tell us, in dp, measured from the frame's own top-left. */
    private data class Shot(
        val ctaTop: Float,
        val ctaBottom: Float,
        val ctaHeight: Float,
        val finePrintBottom: Float,
        /** Height and width of each row that carries a checked state, in drawn order. */
        val toggleBoxes: List<Pair<Float, Float>>,
        val toggleStates: List<ToggleableState>,
        val frameHeight: Float,
        /** The save-failure card's top edge, or 0 when it is not drawn. */
        val errorTop: Float = 0f,
        val errorBottom: Float = 0f,
    )

    /** Every state the screen has, so no assertion is only true of the empty one. */
    private val ALL = ReachabilityState(interest = InterestChannel.ORDER.toSet())

    @OptIn(ExperimentalTestApi::class)
    private fun shoot(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        scrollToBottom: Boolean = false,
        state: ReachabilityState = ReachabilityState(),
        /**
         * Which semantics tree to measure.
         *
         * THE DEFAULT IS THE UNMERGED ONE, because a merged node reports its whole subtree's
         * bounds and the CTA's own box is what the fit assertions are about. The MERGED tree is
         * what TalkBack and VoiceOver actually read, and it is the only one in which
         * `clearAndSetSemantics` has happened -- an unmerged walk still finds every descendant of
         * a cleared subtree, which is exactly how the modality bug survived its first test.
         */
        unmerged: Boolean = true,
        content: (@Composable () -> Unit)? = null,
    ): Shot {
        var out = Shot(0f, 0f, 0f, 0f, emptyList(), emptyList(), heightDp.toFloat())
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
                    Box(Modifier.testTag(FIT_ROOT).requiredSize(widthDp.dp, heightDp.dp)) {
                        content?.invoke() ?: ProfileReachabilityScreen(state)
                    }
                }
            }
            waitForIdle()

            // DRIVEN, NOT ASSUMED. `performScrollTo` moves the nearest scrollable ancestor until
            // the node is visible, which is precisely "the user scrolled to the bottom" -- and it
            // is a no-op on a frame where nothing scrolls, so one call covers both cases.
            if (scrollToBottom) {
                onNodeWithTag(REACH_FINEPRINT_TAG, useUnmergedTree = true).performScrollTo()
                waitForIdle()
            }

            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = unmerged).fetchSemanticsNode()
            val originY = root.positionInRoot.y
            var ctaTop = 0f
            var ctaBottom = 0f
            var ctaHeight = 0f
            var fineBottom = 0f
            var errorTop = 0f
            var errorBottom = 0f
            val boxes = mutableListOf<Pair<Float, Float>>()
            val states = mutableListOf<ToggleableState>()

            fun walk(n: SemanticsNode) {
                val top = (n.positionInRoot.y - originY) / density
                val bottom = top + n.size.height / density
                when (n.config.getOrNull(SemanticsProperties.TestTag)) {
                    // BY TAG, NOT BY LABEL: the label is a child of the button with its own,
                    // smaller box, so a label inside the frame says nothing about the button.
                    REACH_CTA_TAG -> {
                        ctaTop = top; ctaBottom = bottom; ctaHeight = n.size.height / density
                    }
                    REACH_FINEPRINT_TAG -> fineBottom = bottom
                    REACH_ERROR_TAG -> { errorTop = top; errorBottom = bottom }
                }
                val toggle = n.config.getOrNull(SemanticsProperties.ToggleableState)
                if (toggle != null) {
                    boxes += (n.size.height / density) to (n.size.width / density)
                    states += toggle
                }
                n.children.forEach { walk(it) }
            }
            walk(root)
            out = Shot(ctaTop, ctaBottom, ctaHeight, fineBottom, boxes, states,
                heightDp.toFloat(), errorTop, errorBottom)
        }
        return out
    }

    // ── the pinned CTA ──────────────────────────────────────────────────────

    /**
     * THE CRITERION 09 FAILED AND THE IOS SWEEP CAUGHT.
     *
     * "The body scrolls and the CTA stays pinned." A screen that scrolls can hide its CTA without
     * overflowing anything -- the content simply runs past the bottom and the button goes with it
     * -- so a fit sweep reports nothing at all. 09 shipped a Galaxy Fold with no button drawn on
     * it and every Android check passed. This is the assertion that would have failed.
     */
    @Test
    fun `the CTA is inside the frame on every device at every font scale`() {
        val misses = mutableListOf<String>()
        for (d in DEVICES) {
            for (scale in listOf(1f, 1.3f, 2.0f)) {
                for ((name, state) in listOf("A" to ReachabilityState(), "F" to ALL)) {
                    val s = shoot(d.width, d.safeHeight, scale, state = state)
                    if (s.ctaBottom <= 0f) {
                        misses += "$d @$scale/$name  CTA WAS NEVER LAID OUT"
                    } else if (s.ctaBottom > s.frameHeight + 0.5f) {
                        misses += "$d @$scale/$name  CTA bottom ${s.ctaBottom} of ${s.frameHeight}"
                    } else if (s.ctaTop < -0.5f) {
                        misses += "$d @$scale/$name  CTA top ${s.ctaTop} is above the frame"
                    }
                }
            }
        }
        assertTrue("the CTA must be drawn inside every frame:\n" + misses.joinToString("\n"),
            misses.isEmpty())
    }

    /**
     * "The fine print scrolls fully clear of the CTA."
     *
     * NOT "is reachable" -- CLEAR. The footer is opaque below its fade, so a last line that ends
     * underneath it is a line nobody can read, and it is the line that names the Privacy Policy.
     * `FooterClearance` is what buys this, and this is what proves the number is big enough at a
     * font scale nobody picked it at.
     */
    @Test
    fun `the fine print scrolls clear of the pinned CTA, at every size and scale`() {
        val misses = mutableListOf<String>()
        for (d in DEVICES) {
            for (scale in listOf(1f, 1.3f, 2.0f)) {
                val s = shoot(d.width, d.safeHeight, scale, scrollToBottom = true, state = ALL)
                if (s.finePrintBottom > s.ctaTop + 0.5f) {
                    misses += "$d @$scale  fine print ends at ${s.finePrintBottom}, " +
                        "CTA starts at ${s.ctaTop}"
                }
            }
        }
        assertTrue("the fine print must end above the footer:\n" + misses.joinToString("\n"),
            misses.isEmpty())
    }

    /**
     * THE WHOLE POINT OF THE 28 SEPTEMBER UPDATE, as one number.
     *
     * "On 393 x 852 at default text size, the headline -> Save preferences is fully visible
     * without scrolling, in states A-D." That is the acceptance criterion the seven spacing
     * changes and the folded-in push toggle exist to satisfy, and it is the kind of thing that
     * is true on the day it ships and quietly false three tickets later -- a row gains four
     * points of padding, nobody measures, and the fine print slips under the CTA again.
     *
     * ASSERTED ON THE FINE PRINT because it is the last thing in the scrolling body: if it ends
     * above the footer with no scrolling, everything above it did too. `scrollToBottom` is
     * deliberately NOT passed -- the test above already proves the fine print is reachable BY
     * scrolling on every device, and this one proves that here it does not have to be.
     *
     * 393 x 852 ONLY, and at default type only. The ticket is explicit that smaller devices and
     * larger Dynamic Type still scroll, so widening this would assert something the design
     * deliberately does not promise.
     */
    @Test
    fun `on a 393 by 852 the whole screen fits without scrolling, in every state`() {
        val device = DEVICES.first { it.width == 393 && it.height == 852 }
        val misses = mutableListOf<String>()
        for ((name, state) in FOLD_STATES) {
            val s = shoot(device.width, device.safeHeight, fontScale = 1f, state = state)
            if (s.finePrintBottom > s.ctaTop + 0.5f) {
                misses += "$name  fine print ends at ${s.finePrintBottom}, " +
                    "CTA starts at ${s.ctaTop} -- ${s.finePrintBottom - s.ctaTop}dp over"
            }
        }
        assertTrue(
            "the headline through Save preferences must fit on 393 x 852 without scrolling:\n" +
                misses.joinToString("\n"),
            misses.isEmpty(),
        )
    }

    /**
     * The four states the criterion names.
     *
     * C and D are the deactivation confirm, which is a modal over this screen rather than a
     * different layout of it -- the body underneath is state A's. They are listed anyway because
     * the ticket lists them, and because a modal that changed the body's height would be a
     * defect worth catching here rather than by eye.
     */
    private val FOLD_STATES
        get() = listOf(
            "A default" to ReachabilityState(),
            "B interest checked" to ALL,
            "C confirm" to ReachabilityState(
                prompt = com.showup.profile.DeactivationPrompt.UserTurnedItOff,
            ),
            "D after denial" to ReachabilityState(
                prompt = com.showup.profile.DeactivationPrompt.AfterOsDenial,
            ),
        )

    // ── the hit targets ─────────────────────────────────────────────────────

    /**
     * "Every toggle and checkbox row is a >= 44pt hit target. The whole interest row toggles its
     * box, not only the 22px square."
     *
     * FOUR CONTROLS: the switch and the three rows. Measured on the node that CARRIES the checked
     * state, which is the only one a finger or a screen reader actually addresses -- the 22dp
     * square is `clearAndSetSemantics {}` decoration and does not appear here at all.
     *
     * Android's floor is 48, not 44, and `CLAUDE.md` is explicit that the stricter one applies:
     * this project shipped an input measuring 23dp inside a 56dp row that responded only in its
     * middle third on 15 of 18 sizes.
     */
    @Test
    fun `the switch and all three rows are full-size targets`() {
        val small = mutableListOf<String>()
        for (d in DEVICES) {
            for (scale in listOf(1f, 1.3f, 2.0f)) {
                val s = shoot(d.width, d.safeHeight, scale, state = ALL)
                assertEquals(
                    "$d @$scale: one switch and three interest rows carry a checked state",
                    4, s.toggleBoxes.size,
                )
                s.toggleBoxes.forEachIndexed { i, (h, w) ->
                    if (h < 48f) small += "$d @$scale  control $i is ${h}dp tall"
                    // The ROW, not the square: a target as wide as the card is the criterion.
                    if (i > 0 && w < 200f) small += "$d @$scale  row $i is only ${w}dp wide"
                }
            }
        }
        assertTrue("every control must clear 48dp:\n" + small.joinToString("\n"), small.isEmpty())
    }

    /**
     * A CHECKBOX THAT NEVER SAYS WHETHER IT IS CHECKED.
     *
     * The rows were `clickable(role = Role.Checkbox)`, which announces the control TYPE and
     * nothing else -- TalkBack read "Phone call from our AI assistant, checkbox" whether the box
     * was ticked or not. Checked state is `ToggleableState`, which only `toggleable` sets, and
     * the difference is invisible in source, in a screenshot and in every fit sweep.
     *
     * The ticket makes it a criterion: "each checkbox row is `role="checkbox"`, both with
     * visible-label names".
     */
    @Test
    fun `every control reports its state to a screen reader, in both directions`() {
        val off = shoot(390, 844, state = ReachabilityState())
        assertEquals(
            "push on, three boxes off",
            listOf(ToggleableState.On, ToggleableState.Off, ToggleableState.Off,
                ToggleableState.Off),
            off.toggleStates,
        )

        val on = shoot(390, 844, state = ALL.copy(pushOn = false))
        assertEquals(
            "push off, three boxes on",
            listOf(ToggleableState.Off, ToggleableState.On, ToggleableState.On,
                ToggleableState.On),
            on.toggleStates,
        )
    }

    // ── the failure state ───────────────────────────────────────────────────

    /**
     * "Not optimistic: on a save failure the user stays on 10, the choices are kept, and the CTA
     * works again."
     *
     * THE CARD IS IN THE PINNED FOOTER, ABOVE THE CTA, and that placement is the assertion. A
     * failure the user cannot see is a CTA that appears to do nothing, and the body can be
     * scrolled anywhere when a save fails -- so a card at the end of the scroll would be
     * off-screen exactly when it is needed. It is also what the ticket says: "Use the flow's
     * shared inline error card above the CTA."
     */
    @Test
    fun `the save failure is drawn above the CTA, and only when it failed`() {
        val clean = shoot(390, 844, state = ALL)
        assertEquals("no card when nothing failed", 0f, clean.errorBottom, 0f)

        val failed = shoot(390, 844, state = ALL.copy(saveFailed = true))
        assertTrue("the card must be drawn", failed.errorBottom > 0f)
        assertTrue(
            "above the CTA: card ends at ${failed.errorBottom}, CTA starts at ${failed.ctaTop}",
            failed.errorBottom <= failed.ctaTop + 0.5f,
        )
        // AND THE CTA DOES NOT MOVE AT ALL, which is stronger than "it stays on screen" and is
        // what the pinned footer buys. The card takes its height from the SCROLLING BODY above
        // it, not from the button below it -- the button is the last thing in the footer and the
        // footer is the last thing in the frame, so its bottom edge is fixed by construction.
        //
        // The first version of this test asserted the CTA moved UP when the card appeared, which
        // is what would happen if the card were inside the body. It is not, and a CTA that jumps
        // when a save fails is exactly the kind of movement this screen is built to avoid.
        assertEquals("the CTA must not move when the failure appears",
            clean.ctaTop, failed.ctaTop, 0.5f)
        assertEquals(clean.ctaBottom, failed.ctaBottom, 0.5f)
        assertTrue("and it stays inside the frame", failed.ctaBottom <= 844f + 0.5f)
    }

    @Test
    fun `the failure card fits on every device at every font scale`() {
        val misses = mutableListOf<String>()
        for (d in DEVICES) {
            for (scale in listOf(1f, 1.3f, 2.0f)) {
                val s = shoot(d.width, d.safeHeight, scale, state = ALL.copy(saveFailed = true))
                if (s.errorBottom <= 0f) misses += "$d @$scale  the card was not drawn"
                else if (s.errorTop < -0.5f) misses += "$d @$scale  card top ${s.errorTop}"
                else if (s.errorBottom > s.ctaTop + 0.5f) {
                    misses += "$d @$scale  card ends at ${s.errorBottom}, CTA at ${s.ctaTop}"
                }
                if (s.ctaBottom > s.frameHeight + 0.5f) {
                    misses += "$d @$scale  CTA pushed out: ${s.ctaBottom} of ${s.frameHeight}"
                }
            }
        }
        assertTrue(
            "the failure footer must fit:\n" + misses.joinToString("\n"),
            misses.isEmpty(),
        )
    }

    // ── the evidence frames ─────────────────────────────────────────────────

    /**
     * The three frames the acceptance criteria name, at the artboard sizes they name them.
     *
     * "Evidence of done: screenshots of states A, B, C and D at 375 x 667, 390 x 844 and
     * 430 x 932, with 375 x 667 shown scrolled to the top and to the bottom." The images are
     * `EvidenceScreenshots`' job; what is asserted here is the claim the images are supposed to
     * SHOW -- that on the tightest of the three the screen scrolls, and that both ends of that
     * scroll are sound.
     */
    @Test
    fun `the tight evidence frame scrolls, and both ends of the scroll are sound`() {
        val top = shoot(375, 667, state = ALL)
        val bottom = shoot(375, 667, scrollToBottom = true, state = ALL)

        assertTrue(
            "375x667 is the frame the ticket says to check first; it must scroll, " +
                "fine print at ${top.finePrintBottom} vs ${bottom.finePrintBottom}",
            bottom.finePrintBottom < top.finePrintBottom,
        )
        // THE CTA DOES NOT MOVE. That is the whole of "pinned", and it is checkable in one line.
        assertEquals("the CTA must not move when the body scrolls", top.ctaTop, bottom.ctaTop, 0.5f)
        assertTrue("and it stays inside the frame", bottom.ctaBottom <= 667f + 0.5f)
        assertTrue("the fine print clears it at the bottom of the scroll",
            bottom.finePrintBottom <= bottom.ctaTop + 0.5f)
    }

    /**
     * The dialog covers the pinned CTA.
     *
     * "The dialog is `aria-modal`". A modal that leaves the primary action tappable behind it is
     * not modal, and the CTA is pinned OUTSIDE the scrolling body -- so the scrim has to be drawn
     * over the whole screen rather than over the content region, which is a real mistake to make
     * and an invisible one once the scrim is translucent.
     */
    @Test
    fun `the deactivation scrim covers the CTA, not just the body`() {
        val s = shoot(
            390, 844,
            state = ReachabilityState(
                prompt = com.showup.profile.DeactivationPrompt.UserTurnedItOff,
            ),
            // THE TREE A SCREEN READER READS. See `unmerged` above.
            unmerged = false,
        )
        // THE BACKGROUND IS GONE FROM THE ACCESSIBILITY TREE, tags and all. That is the
        // assertion: a scrim stops a finger and does nothing whatsoever to a screen reader, so
        // "the CTA is covered" has to be measured as "the CTA is not reachable" -- and its test
        // tag disappearing with it is the proof that the whole subtree went, not just the parts
        // that happened to be operable.
        assertEquals("no control behind a modal may be operable", 0, s.toggleStates.size)
        assertEquals("not even the pinned CTA is reachable behind it", 0f, s.ctaBottom, 0f)
        assertEquals("nor the fine print", 0f, s.finePrintBottom, 0f)
    }
}
