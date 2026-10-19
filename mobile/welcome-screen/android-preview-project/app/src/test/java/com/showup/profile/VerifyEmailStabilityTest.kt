/*
 * VerifyEmailStabilityTest.kt
 * ShowUp · nothing on the verify-email screen moves when the code is refused
 *
 * SHOWUP-143 states the rule outright for the phone code screen: the CTA does not move when a
 * state goes wrong. The email screen inherited the layout and not the rule, and on 11 September
 * 2026 a device screenshot showed what that cost -- the mismatch card appeared, and the CTA, the
 * resend row and the change-address link all slid down the screen.
 *
 * Two separate causes, which is why this measures three things and not one:
 *
 *   1. The error region was `heightIn(min = 30)`. A minimum reserves nothing; the card was taller
 *      than 30 as soon as the copy wrapped, and everything below it moved.
 *   2. The resend link carries `minTapTarget()` and the countdown beside it does not, so swapping
 *      between them changed that row from ~18dp to 48. A mismatch RELEASES the cooldown, so the
 *      swap happens at exactly the moment the error appears -- which is why it read as the error
 *      moving the links.
 *
 * Positions come from the semantics tree, so this asserts real laid-out geometry rather than the
 * absence of a modifier. A future edit that reintroduces either cause fails here with the number
 * of dp it moved.
 */
package com.showup.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class VerifyEmailStabilityTest {

    private val root = "verify-email-root"

    /**
     * Where one labelled element sits, in dp from the top-left of the rendered frame.
     *
     * BOTH axes, since 11 September. Measuring only Y let the second defect through: the row is
     * centred, so when "Send a new code in 0:32" became the shorter "Send a new code" the whole
     * row re-centred and everything in it slid sideways -- at a constant Y, which a Y-only test
     * reports as perfectly stable.
     *
     * Null when the element is absent, which matters: "Send a new code" only exists once the
     * cooldown has been released, and a test that silently compared nothing to nothing would
     * pass on a screen where the link had vanished entirely.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun positionOf(
        label: String,
        state: VerifyState,
        cooldown: Int,
        width: Int,
        height: Int,
    ): Pair<Float, Float>? {
        var at: Pair<Float, Float>? = null
        runComposeUiTest {
            var density = 1f
            setContent {
                density = LocalDensity.current.density
                Box(Modifier.testTag(root).requiredSize(width.dp, height.dp)) {
                    ProfileVerifyEmailScreen(
                        email = "esma@gmail.com",
                        digits = "223237",
                        state = state,
                        cooldownSeconds = cooldown,
                    )
                }
            }
            waitForIdle()
            val origin = onNodeWithTag(root).fetchSemanticsNode().positionInRoot
            val node = runCatching { onNodeWithText(label).fetchSemanticsNode() }.getOrNull()
            at = node?.let {
                ((it.positionInRoot.x - origin.x) / density) to
                    ((it.positionInRoot.y - origin.y) / density)
            }
        }
        return at
    }


    /** Every state that can appear under the slots, with the cooldown each one really has. */
    private val states = listOf(
        // A mismatch and a lockout both release the cooldown, so the link is live in those.
        VerifyState.Mismatch to 0,
        VerifyState.Expired to 0,
        VerifyState.LockedOut to 0,
    )

    private fun assertDoesNotMove(label: String, width: Int, height: Int) {
        val calm = positionOf(label, VerifyState.Calm, cooldown = 41, width, height)
            ?: error("\"$label\" is not on the calm screen at ${width}x$height")
        for ((state, cooldown) in states) {
            val moved = positionOf(label, state, cooldown, width, height)
                ?: error("\"$label\" disappeared in $state at ${width}x$height")
            assertEquals(
                "\"$label\" moved ${"%.1f".format(moved.second - calm.second)}dp DOWN when the " +
                    "state became $state at ${width}x$height",
                calm.second, moved.second, 0.75f,
            )
            assertEquals(
                "\"$label\" moved ${"%.1f".format(moved.first - calm.first)}dp SIDEWAYS when " +
                    "the state became $state at ${width}x$height",
                calm.first, moved.first, 0.75f,
            )
        }
    }

    // 0.75dp of tolerance, matching FitHarness: sub-pixel rounding is not movement.

    @Test
    fun `the CTA does not move when the code is refused`() {
        // The rule SHOWUP-143 states, applied to the screen that was exempt from it by oversight.
        assertDoesNotMove(VerifyEmailCopy.CTA, 390, 844)
    }

    @Test
    fun `the CTA does not move on the narrowest phone`() {
        // 320 is where the mismatch copy wraps furthest, so it is where a reserve sized for one
        // line fails first. This is the case the old heightIn was measured against and lost.
        assertDoesNotMove(VerifyEmailCopy.CTA, 320, 686)
    }

    @Test
    fun `the change-address link does not move when the code is refused`() {
        // The bottom of the group, so it accumulates every shift above it -- the error region and
        // the resend row both. If anything moves at all, this moves most.
        assertDoesNotMove(VerifyEmailCopy.CHANGE_ADDRESS, 390, 844)
    }

    @Test
    fun `the resend question does not move when the countdown becomes a link`() {
        // The second cause on its own. Calm here still has a live cooldown, so the countdown is
        // showing; every failing state has released it and swapped in the tappable link, which
        // is 48dp where the countdown is about 18. Same row, same Y, or this fails.
        assertDoesNotMove(VerifyEmailCopy.RESEND_QUESTION, 390, 844)
    }
}
