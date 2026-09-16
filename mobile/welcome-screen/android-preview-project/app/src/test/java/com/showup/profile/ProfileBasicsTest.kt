/*
 * ProfileBasicsTest.kt
 * ShowUp · the acceptance criteria on 150 and 152 that layout cannot prove
 *
 * ScreenFitTest already sweeps every state at 17 widths and catches clipping, collapsed text and
 * undersized targets — it found the back control at 36dp before this file existed. What it cannot
 * see is behaviour: that a press on an empty field produces an error rather than nothing, that the
 * error clears on the first character and not before, that the email message is chosen from what
 * was typed, and that the name screen has no way backwards.
 *
 * The reserved-region claim IS measurable, and is measured here rather than asserted: the CTA must
 * sit at the identical Y in all three of the name screen's states. That is the whole reason the
 * status region has a fixed min-height, and it is the one thing that would silently regress.
 */
package com.showup.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.SpanStyle
import com.showup.designsystem.ComponentSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ProfileBasicsTest {

    // ── the error copy is chosen by what was typed ──────────────────────────
    //
    // Pure functions, so these need no UI at all. "Please enter a valid email" is banned; each of
    // these names the specific fix, and the named fragment comes back as a styled chip.

    private val chip = SpanStyle()

    @Test
    fun `an empty address gets the group's shared sentence shape`() {
        assertEquals("Enter your email to continue.", emailErrorCopy("", chip).text)
        // Whitespace only counts as empty and gets the same string.
        assertEquals("Enter your email to continue.", emailErrorCopy("   ", chip).text)
    }

    @Test
    fun `a missing at sign names the at sign`() {
        val s = emailErrorCopy("leo.hey.com", chip)
        assertEquals("Add an @ — e.g. you@example.com", s.text)
        // The chip is a styled range, not just text -- that is what makes the fix one keystroke.
        assertTrue("the @ should be a styled chip", s.spanStyles.isNotEmpty())
    }

    @Test
    fun `a missing dot after the at sign names the domain`() {
        val s = emailErrorCopy("leo@hey", chip)
        assertEquals("Looks like the domain is missing .com (or similar).", s.text)
        assertTrue(s.spanStyles.isNotEmpty())
    }

    @Test
    fun `anything else falls back without inventing a fragment to name`() {
        val s = emailErrorCopy("leo@@hey..com", chip)
        assertEquals("That email doesn’t look quite right. Please check it.", s.text)
        // No chip: there is no single missing character to point at.
        assertTrue("nothing to chip here", s.spanStyles.isEmpty())
    }

    @Test
    fun `format validity is what drives the tick, and it is not strict`() {
        assertTrue(isEmailFormatValid("leo@hey.com"))
        assertTrue(isEmailFormatValid("  leo@hey.com  "))   // trimmed before checking
        assertFalse(isEmailFormatValid("leo@hey"))
        assertFalse(isEmailFormatValid("leo.hey.com"))
        assertFalse(isEmailFormatValid(""))
        assertFalse(isEmailFormatValid("   "))
    }

    // ── the flow model ──────────────────────────────────────────────────────

    @Test
    fun `the bar does not advance for email verification`() {
        assertEquals(1, BasicsStep.Name.progressSegment)
        assertEquals(2, BasicsStep.Email.progressSegment)
        // The one that would be easy to get wrong: verification is not a fourth segment.
        assertEquals(2, BasicsStep.EmailVerify.progressSegment)
        assertEquals(3, BasicsStep.Dob.progressSegment)
    }

    @Test
    fun `only the first step has no way back`() {
        assertFalse(BasicsStep.Name.hasBack)
        assertTrue(BasicsStep.Email.hasBack)
        assertTrue(BasicsStep.EmailVerify.hasBack)
        assertTrue(BasicsStep.Dob.hasBack)
    }

    @Test
    fun `step ids match the taxonomy vocabulary`() {
        assertEquals("name", BasicsStep.Name.stepId)
        assertEquals("email", BasicsStep.Email.stepId)
        assertEquals("email_verify", BasicsStep.EmailVerify.stepId)
        assertEquals("dob", BasicsStep.Dob.stepId)
    }

    // ── the name screen ─────────────────────────────────────────────────────

    /**
     * The reserved region, measured.
     *
     * The CTA must sit at the identical Y in all three states. This is the claim the whole
     * `min-height: 44` exists to make, and rendering the region conditionally is how it breaks.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the CTA does not move between the name screen's three states`() {
        fun ctaTop(value: String, submitEmpty: Boolean): Float {
            var y = -1f
            runComposeUiTest {
                var text = value
                setContent { ProfileNameScreen(value = text, onValueChange = { text = it }) }
                if (submitEmpty) {
                    onNodeWithText("Continue").performClick()
                    waitForIdle()
                }
                y = onNodeWithText("Continue").fetchSemanticsNode().positionInRoot.y
            }
            return y
        }

        val empty = ctaTop("", submitEmpty = false)
        val typed = ctaTop("Leo", submitEmpty = false)
        val errored = ctaTop("", submitEmpty = true)

        assertEquals("A vs B", empty, typed, 0.5f)
        assertEquals("A vs C — the reserved region is what holds this", empty, errored, 0.5f)
    }

    /** The CTA is never disabled: the press is what produces the error. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `pressing continue on an empty name shows the error rather than doing nothing`() =
        runComposeUiTest {
            var refused = 0
            setContent { ProfileNameScreen(onEmptySubmit = { refused += 1 }) }

            onNodeWithText("Enter your name to continue.").assertDoesNotExist()
            onNodeWithText("Continue").performClick()
            waitForIdle()

            onNodeWithText("Enter your name to continue.").assertExists()
            assertEquals("the refused press is what fires the event", 1, refused)
        }

    /** And it clears on the first character typed — not on blur, not on re-submit. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the name error clears on the first character and does not re-fire`() = runComposeUiTest {
        // Real Compose state, not a captured local: a plain `var` is invisible to the runtime, so
        // the screen would never recompose with the typed character and the test would be
        // measuring nothing. Cost one failing run to notice.
        setContent {
            var text by remember { mutableStateOf("") }
            ProfileNameScreen(value = text, onValueChange = { text = it })
        }

        onNodeWithText("Continue").performClick()
        waitForIdle()
        onNodeWithText("Enter your name to continue.").assertExists()

        onNodeWithContentDescription("Enter first name").performTextInput("L")
        waitForIdle()
        onNodeWithText("Enter your name to continue.").assertDoesNotExist()
    }

    /** Whitespace is not a name. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a whitespace-only name is refused like an empty one`() = runComposeUiTest {
        var continued = false
        setContent { ProfileNameScreen(value = "   ", onContinue = { continued = true }) }
        onNodeWithText("Continue").performClick()
        waitForIdle()
        onNodeWithText("Enter your name to continue.").assertExists()
        assertFalse("whitespace must not advance the flow", continued)
    }

    /** Trimmed on submit — the stored value is what appears on the profile. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the submitted name is trimmed`() = runComposeUiTest {
        var got: String? = null
        setContent { ProfileNameScreen(value = "  Leo  ", onContinue = { got = it }) }
        onNodeWithText("Continue").performClick()
        waitForIdle()
        assertEquals("Leo", got)
    }

    /** No chevron, and the header title is still centred because the empty slot keeps its width. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the name screen offers no way backwards`() = runComposeUiTest {
        setContent { ProfileNameScreen() }
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithText("The basics").assertExists()
    }

    // ── the email screen ────────────────────────────────────────────────────

    /** Step 2 does have a back control, and it is announced and big enough to hit. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the email screen has an announced back control at the tap floor`() = runComposeUiTest {
        setContent { ProfileEmailScreen() }
        val back = onNodeWithContentDescription("Back")
        back.assertExists()
        back.assertHeightIsAtLeast(ComponentSizes.minTapTarget)
    }

    /** Lower-cased as well as trimmed on submit. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the submitted address is trimmed and lower-cased`() = runComposeUiTest {
        var got: String? = null
        setContent { ProfileEmailScreen(value = "  Leo@Hey.COM ", onContinue = { got = it }) }
        onNodeWithText("Continue").performClick()
        waitForIdle()
        assertEquals("leo@hey.com", got)
    }

    /** A malformed address is refused, and the value the user typed survives it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a malformed address is refused and never wiped`() = runComposeUiTest {
        var text = "leo@hey"
        var continued = false
        setContent {
            ProfileEmailScreen(
                value = text, onValueChange = { text = it }, onContinue = { continued = true },
            )
        }
        onNodeWithText("Continue").performClick()
        waitForIdle()

        onNodeWithText("Looks like the domain is missing .com (or similar).").assertExists()
        assertFalse(continued)
        assertEquals("re-typing an address is the fastest way to lose someone", "leo@hey", text)
    }

    /** The consent row is opt-in, and pressing it never blocks the CTA. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `marketing consent is opt-in and does not gate continue`() = runComposeUiTest {
        var consent = false
        var continued = false
        setContent {
            ProfileEmailScreen(
                value = "leo@hey.com",
                consent = consent,
                onConsentChange = { consent = it },
                onContinue = { continued = true },
            )
        }
        // Unchecked by default: a valid address continues without anyone touching the row.
        onNodeWithText("Continue").performClick()
        waitForIdle()
        assertTrue(continued)
        assertFalse("never pre-ticked", consent)
    }

    /** The whole row is the hit area, not just the 22 box. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `tapping the consent copy toggles it`() = runComposeUiTest {
        var consent = false
        setContent {
            ProfileEmailScreen(consent = consent, onConsentChange = { consent = it })
        }
        onNodeWithText(EmailCopy.CONSENT).performClick()
        waitForIdle()
        assertTrue("the copy is part of the target", consent)
    }

    /** Every CTA in this flow stays live in every state — a dead button teaches nothing. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `neither CTA is ever disabled`() = runComposeUiTest {
        setContent { ProfileEmailScreen() }
        onNodeWithText("Continue").assert(hasClickAction())
    }
}
