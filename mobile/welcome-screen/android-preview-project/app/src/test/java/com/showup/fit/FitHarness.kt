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
 * SCROLLING CHANGES WHAT (2) MEANS, AND ONLY (2)
 *
 * A screen that scrolls when it runs out of room puts content below the fold on purpose. That is
 * reachable, not lost, so it is reported as an advisory rather than a failure -- but it is still
 * reported, because "you have to scroll on a 360x640" is worth knowing and is exactly the kind of
 * thing that silently spreads to every screen if nobody is counting.
 *
 * The other three detectors are unchanged by scrolling and must stay failures: text clipped inside
 * a scroll view is still text you cannot read, and a 15dp button is still unusable however far you
 * scrolled to reach it.
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
import androidx.compose.ui.unit.Density
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
/**
 * The tags of every element that PAINTS rather than lays out, and so must be measured.
 *
 * Kept as strings rather than importing the constants, so the harness does not depend on the
 * screen package and a renamed tag fails loudly here rather than silently matching nothing --
 * `HarnessSelfTest` asserts each one is still reachable.
 */
val MEASURED_CANVASES = setOf("media:waveform")

/** Below this a drawing is present but too small to be read as anything. */
const val MIN_CANVAS_DP = 16f

/** Flattens a paragraph onto one line when it is used to name a link inside it. */
private val NEWLINE_RE = Regex("[\\s]+")

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
/**
 * The tag the harness hangs the device frame on.
 *
 * Internal rather than private since 15 September 2026: `EvidenceScreenshots` renders into the
 * same frame to capture the images each ticket asks for, and a second copy of the tag would be
 * two strings that have to match by inspection.
 */
internal const val FIT_ROOT = "fit-root"

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

/**
 * Renders [content] into [device]'s SAFE area and returns everything wrong with the result.
 *
 * @param fontScale the system font size, as a multiplier. 1.0 is the default; Android's largest
 *   non-accessibility step is 1.3 and its largest accessibility step is 2.0, which is what
 *   `CLAUDE.md`'s "usable at the largest system font" has always meant and what nothing here
 *   measured until 15 September 2026. Added because "The real you" is the first group with several
 *   FIXED heights -- a 158 slot, a 44 pill, a 32 retry control -- and a fixed height is exactly
 *   what large type overflows.
 *
 * @param keyboardDp how much of the bottom the keyboard has taken. 0 for a screen with no input.
 *   The IME is the one thing the shared CLAUDE.md lists as "not yet automated on either platform --
 *   check it by hand", and by hand is not a thing seventeen devices get. Modelled as a bite out of
 *   the usable height, which is exactly what `WindowInsets.ime` does to a layout that respects it;
 *   a screen that ignores the inset will not notice this and is caught by reading instead.
 */
@OptIn(ExperimentalTestApi::class)
fun measureFit(
    device: Device,
    screen: String,
    fontScale: Float = 1f,
    keyboardDp: Int = 0,
    content: @Composable () -> Unit,
): List<Violation> {
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
                this.fontScale = fontScale
            }
            // Both, not one. The Configuration is what a composable reading
            // `LocalConfiguration.fontScale` sees; LocalDensity is what actually sizes an `sp`.
            // Setting only the first scales nothing and would have reported every screen as clean
            // at every font size, which is worse than not testing it.
            CompositionLocalProvider(
                LocalConfiguration provides cfg,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                Box(
                    Modifier
                        .testTag(FIT_ROOT)
                        .requiredSize(device.width.dp, (device.safeHeight - keyboardDp).dp),
                ) { content() }
            }
        }
        waitForIdle()

        // THE UNMERGED TREE, and this is not a detail.
        //
        // The merged tree is what an accessibility service reads: a node that merges its
        // descendants absorbs their text and the children stop being separate nodes. Every check
        // below is a MEASUREMENT of an individual element, so on the merged tree it simply never
        // saw them. Dumping both trees for one screen at 320 x 2.0 found the merged tree missing
        // the prompt, the hint, the play button and the waveform -- four of the six things on it --
        // while the harness reported that screen clean on all 17 devices.
        //
        // Duplicates are the price and are cheap: a merged parent and its child can both carry the
        // same text, and `distinctBy` at the end collapses them.
        val boxNode = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
        val originX = boxNode.positionInRoot.x
        val originY = boxNode.positionInRoot.y
        val wDp = device.width.toFloat()
        val hDp = (device.safeHeight - keyboardDp).toFloat()

        // Carried DOWN the tree rather than read off each node: only the scroll container itself
        // advertises VerticalScrollAxisRange, and it is its descendants whose positions the flag
        // has to reinterpret.
        // `clipBottom` is the bottom edge, in dp, of the nearest scrolling ancestor -- null while
        // nothing above has clipped. Carried down for the same reason `inScroll` is: the container
        // knows its bounds and the descendants are the ones that fall outside them.
        // `inText` becomes true once the walk has passed a node that owns a text layout. Anything
        // clickable below that point is a LinkAnnotation inside a paragraph rather than a control,
        // and the two are judged differently -- see the tap-target rule.
        fun walk(
            node: SemanticsNode,
            inScroll: Boolean = false,
            clipBottom: Float? = null,
            inText: String? = null,
        ) {
            val scrollable = inScroll ||
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            val left = (node.positionInRoot.x - originX) / density
            val top = (node.positionInRoot.y - originY) / density
            val right = left + node.size.width / density
            val bottom = top + node.size.height / density
            val name = if (inText != null) "link in $inText" else label(node)

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

            // 1b. a DRAWING that was given no room to draw in.
            //
            // A canvas is the one element in this project that can fail completely while looking
            // perfectly healthy to every other check here: nothing is clipped, nothing overflows,
            // the node is present and reports a size, and the draw block runs -- into a box zero
            // pixels tall. The voice waveform did exactly that, because `Canvas` IS a `Spacer` and
            // Spacer takes the incoming maximum only on an axis whose constraint is FIXED and zero
            // on an axis given a range: `heightIn(min, max)` is a range. It was found by looking at
            // a screenshot, which is not a method that scales to 17 devices and 3 font scales.
            //
            // So anything that draws rather than lays out carries a tag, and the tag is measured.
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null && tag in MEASURED_CANVASES) {
                val wDpNode = node.size.width / density
                val hDpNode = node.size.height / density
                if (node.size.width == 0 || node.size.height == 0) {
                    found += Violation(device, screen, tag, "CANVAS COLLAPSED",
                        "%.1f x %.1fdp -- it drew into nothing".format(wDpNode, hDpNode))
                } else if (wDpNode < MIN_CANVAS_DP || hDpNode < MIN_CANVAS_DP) {
                    found += Violation(device, screen, tag, "CANVAS TOO SMALL TO READ",
                        "%.1f x %.1fdp".format(wDpNode, hDpNode))
                }
            }

            if (node.size.width > 0 && node.size.height > 0) {
                // 2. positioned outside the safe area
                if (bottom > hDp + SLACK_DP) {
                    // Below the fold of something that scrolls is a scroll, not a loss.
                    found += if (scrollable) {
                        Violation(device, screen, name, "BELOW THE FOLD",
                            "by %.1fdp, reachable by scrolling".format(bottom - hDp),
                            advisory = true)
                    } else {
                        Violation(device, screen, name, "OFF THE BOTTOM",
                            "by %.1fdp".format(bottom - hDp))
                    }
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
                if (clickable && inText != null) {
                    // AN INLINE LINK IS NOT A CONTROL, and the 44dp floor does not apply to it.
                    //
                    // `Terms` inside `By continuing you agree to our Terms` is a LinkAnnotation, and
                    // Compose gives its semantics node exactly the bounds of the text run -- 17.5dp
                    // for one line of 14sp, 35 when it wraps onto two. There is no padding to add:
                    // growing the box would push the words of the sentence apart. Every platform
                    // handles inline links this way, and the guideline the floor comes from is
                    // about components.
                    //
                    // Reported anyway, as an advisory, because the alternative -- saying nothing --
                    // is how a link genuinely too small to hit would go unnoticed.
                    if (heightDp < MIN_TAP_DP - SLACK_DP) {
                        found += Violation(device, screen, name, "inline link, text-sized tap area",
                            "%.1fdp -- bounded by its line, not by padding".format(heightDp),
                            advisory = true)
                    }
                } else if (clickable && heightDp < MIN_TAP_DP - SLACK_DP) {
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
            // 5. a region that scrolls, and HOW FAR.
            //
            // The first version of this reported every child that fell past its scrolling
            // ancestor, and fired 3,138 times: content below the fold of a list is what a list IS.
            // A detector that cries wolf is one nobody reads the output of, which is the same
            // lesson the horizontal-overflow check above was deleted for.
            //
            // So the container is the finding, once, with the overflow measured. That distinguishes
            // the two cases by size rather than by kind: a prompts list 400dp longer than its
            // viewport is a list working correctly, and a fixed region overflowing by 88dp is a
            // squeeze -- the same 88dp that put `Hear it back before you keep it` behind the lower
            // third at 2.0x while the report for that state said "clean on all 17 devices".
            //
            // Advisory, because scrolling does reach it. It is here to be READ.
            val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (range != null) {
                val overflowDp = range.maxValue() / density
                if (overflowDp > SLACK_DP) {
                    found += Violation(device, screen, name, "SCROLLS",
                        "%.0fdp of overflow in a %.0fdp region".format(overflowDp, bottom - top),
                        advisory = true)
                }
            }

            // The nearest scrolling ancestor is what bounds everything below it.
            val childText = inText
                ?: layout?.let { NEWLINE_RE.replace(it.layoutInput.text.text, " ").take(30) }
            node.children.forEach { walk(it, scrollable, null, childText) }
        }
        walk(boxNode)
    }
    return found.distinctBy { it.element to it.problem }
}
