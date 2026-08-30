/*
 * FitHarness.kt
 * ShowUp · renders a screen at a given phone size and reports what does not fit
 *
 * WHAT "DOES NOT FIT" ACTUALLY LOOKS LIKE IN COMPOSE
 *
 * The obvious test -- is anything positioned past the bottom edge -- turns out to be the wrong
 * question most of the time. Compose passes each child the space that is left, and a child asked
 * for more than that is given less: a Column running out of room does not push its last item off
 * the screen, it squashes it. So a layout can be badly broken while every element still reports a
 * position comfortably inside the frame.
 *
 * What that squashing produces is clipped text, and Compose knows precisely when text has been
 * clipped: the laid-out paragraph is taller than the box it was given, or a line runs past its
 * right edge. That is the primary detector here, and it is not an approximation -- it is the
 * renderer's own measurements saying that what it drew is not all of what it was handed.
 *
 * Four things are checked:
 *
 *   1. text clipped by its own box                 (the squashing case, and the common one)
 *   2. an element positioned outside the safe area (the overlay and fixed-size case)
 *   3. text collapsed to nothing                   (zero height, so it is simply gone)
 *   4. a tap target below the 56dp the tickets require
 *
 * Insets are modelled rather than borrowed. Robolectric reports no status bar and no gesture bar,
 * so a screen tested against the raw size would be handed ~80dp it does not have on a real phone.
 * Each device carries its own inset figures and the content is rendered into what is left.
 *
 * KNOWN BOUNDARY: only nodes carrying semantics are visible here -- all text, every button, every
 * labelled icon. A purely decorative Box is not measured. That is the right trade: the question is
 * whether what a person reads and taps survives the screen size.
 */
package com.showup.fit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/** One thing in the wrong place or the wrong size, named well enough to find it in the source. */
data class Violation(
    val device: Device,
    val screen: String,
    val element: String,
    val problem: String,
    val detail: String,
    /** Advisory findings are worth seeing but are not failures. See MIN_TAP_DP. */
    val advisory: Boolean = false,
) {
    override fun toString() =
        "%s%-36s %-22s %-44s %-22s %s".format(
            if (advisory) "note " else "FAIL ", device.toString(), screen,
            element.take(44), problem, detail,
        )
}

/** Identifies the box representing the device's safe area, so measurements have a known origin. */
private const val FIT_ROOT = "fit-root"

/** A tolerance, because sub-pixel rounding is not a bug. Anything past this is real. */
private const val SLACK_DP = 0.75f

/** A whole pixel of slack before text counts as clipped. Below this it is integer rounding. */
private const val CLIP_TOLERANCE_PX = 1.0f

/**
 * Two bars, because one produced nothing but noise.
 *
 * SHOWUP-140/142 say "Buttons never shrink below 56", and that is right for the four method
 * buttons. But the same screens deliberately use 44 for the "Log in" text link, 50 for "Use a
 * different account", 52 for the dashed skip row and 54 in the conflict sheet -- none of which are
 * those buttons. Holding every tappable thing to 56 flagged all of them on every device, which
 * buried the genuine finding (a button crushed to 7.5dp) under a thousand lines about links that
 * were exactly as designed.
 *
 * So: below 44dp -- Apple's stated minimum and the smallest value this design uses on purpose --
 * is a real failure. Between 44 and 56 is reported and not failed, because whether it is wrong
 * depends on which control it is, and that is a judgement for the designer.
 */
private const val MIN_TAP_DP = 44f
private const val SPEC_BUTTON_DP = 56f

private fun label(node: SemanticsNode): String {
    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
    if (!text.isNullOrBlank()) return "\"" + text.replace("\n", " ").trim() + "\""
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    if (!tag.isNullOrBlank()) return "<" + tag + ">"
    val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
    if (!desc.isNullOrBlank()) return "[" + desc + "]"
    return "node #" + node.id
}

/** Asks the node for the text layout the renderer actually produced. Null when it is not text. */
private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
    val sink = mutableListOf<TextLayoutResult>()
    action.action?.invoke(sink)
    return sink.firstOrNull()
}

/** Renders [content] into [device]'s SAFE area and returns everything wrong with the result. */
@OptIn(ExperimentalTestApi::class)
fun measureFit(device: Device, screen: String, content: @Composable () -> Unit): List<Violation> {
    val found = mutableListOf<Violation>()
    runComposeUiTest {
        var density = 1f
        setContent {
            density = LocalDensity.current.density
            // Several screens choose a compact layout from LocalConfiguration.screenHeightDp.
            // Left alone that reports the test host's size, so every device would be measured on
            // the roomy branch and the small phones -- the ones that actually break -- would be
            // tested on a layout they never run. The configuration is overridden to match.
            val cfg = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = device.width
                screenHeightDp = device.height
            }
            CompositionLocalProvider(LocalConfiguration provides cfg) {
                Box(
                    Modifier
                        .testTag(FIT_ROOT)
                        .requiredSize(device.width.dp, device.safeHeight.dp),
                ) { content() }
            }
        }
        waitForIdle()

        val boxNode = onNodeWithTag(FIT_ROOT).fetchSemanticsNode()
        val originX = boxNode.positionInRoot.x
        val originY = boxNode.positionInRoot.y
        val wDp = device.width.toFloat()
        val hDp = device.safeHeight.toFloat()

        fun walk(node: SemanticsNode) {
            val left = (node.positionInRoot.x - originX) / density
            val top = (node.positionInRoot.y - originY) / density
            val right = left + node.size.width / density
            val bottom = top + node.size.height / density
            val name = label(node)

            // 1. the squashing case: the renderer could not draw all it was given.
            //
            // Deliberately NOT TextLayoutResult.hasVisualOverflow. That flag compares the laid-out
            // size, an integer number of pixels, against the paragraph's true float width, so a
            // paragraph 105.4px wide inside a 105px box reports itself as clipped. It fires on
            // rounding, on ordinary text, constantly -- a detector that cries wolf on "hello" is
            // one nobody will read the output of. The measurement below asks the same question
            // with a pixel of tolerance, which is the difference between noise and a finding.
            val layout = layoutOf(node)
            if (layout != null) {
                val para = layout.multiParagraph
                val overHeight = para.height - layout.size.height
                // Vertical only, plus explicit truncation.
                //
                // A horizontal check was tried and removed. Comparing the rightmost line extent
                // against the node width looks reasonable and is wrong for centred text: the line
                // extent is measured inside the constraint box while the node shrink-wraps to the
                // ink, so the gap is the centring margin. It was caught because the reported
                // "overflow" GREW with the screen -- 24px at 360dp, 54px at 390dp, 92px at 428dp --
                // which is the opposite of how clipping behaves. Text too wide for its box in
                // Compose either wraps (caught by the height check) or ellipsises (caught by
                // didExceedMaxLines), so nothing real is lost.
                val clipped = para.didExceedMaxLines || overHeight > CLIP_TOLERANCE_PX
                if (clipped) {
                    val how = if (para.didExceedMaxLines) {
                        "truncated, over its maxLines"
                    } else {
                        "%.0fpx of text below the cut, %d line(s) drawn"
                            .format(overHeight, layout.lineCount)
                    }
                    found += Violation(device, screen, name, "TEXT CLIPPED", how)
                }
            }

            if (node.size.width > 0 && node.size.height > 0) {
                // 2. positioned outside the safe area
                if (bottom > hDp + SLACK_DP) {
                    found += Violation(device, screen, name, "OFF THE BOTTOM",
                        "by %.1fdp".format(bottom - hDp))
                }
                if (right > wDp + SLACK_DP) {
                    found += Violation(device, screen, name, "OFF THE RIGHT",
                        "by %.1fdp".format(right - wDp))
                }
                if (left < -SLACK_DP) {
                    found += Violation(device, screen, name, "OFF THE LEFT",
                        "by %.1fdp".format(-left))
                }
                if (top < -SLACK_DP) {
                    found += Violation(device, screen, name, "OFF THE TOP",
                        "by %.1fdp".format(-top))
                }
                // 4. a tap target squeezed below the stated minimum
                val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
                val heightDp = node.size.height / density
                if (clickable && heightDp < MIN_TAP_DP - SLACK_DP) {
                    found += Violation(device, screen, name, "TAP TARGET TOO SMALL",
                        "%.1fdp, unusable below %.0f".format(heightDp, MIN_TAP_DP))
                } else if (clickable && heightDp < SPEC_BUTTON_DP - SLACK_DP) {
                    found += Violation(device, screen, name, "under the 56dp spec",
                        "%.1fdp".format(heightDp), advisory = true)
                }
            } else if (node.size.height == 0 &&
                !node.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty()
            ) {
                // 3. text collapsed to nothing at all
                found += Violation(device, screen, name, "TEXT COLLAPSED", "zero height")
            }
            node.children.forEach(::walk)
        }
        walk(boxNode)
    }
    return found.distinctBy { it.element to it.problem }
}
