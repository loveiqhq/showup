/*
 * ScrollReach.kt
 * ShowUp · "the control is reachable by scrolling", driven rather than assumed
 *
 * The fit sweep measures where a control is DRAWN, and a sheet taller than the phone scrolls inside its shell, so a
 * control below the fold is not a finding there. What the Date 06 and Date 07 tickets ask is that the CTAs and
 * choices are reachable by that scroll. `performScrollTo` moves the nearest scrollable ancestor until the node is
 * visible -- the user scrolling to it -- and fails when nothing can bring it into view, so after it the node's box
 * must lie inside the frame.
 *
 * WHAT THIS CANNOT PROVE: that a finger reaches it past a gesture bar or a keyboard; Robolectric measures a layout.
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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Renders [content] in a [widthDp] x [heightDp] frame at [fontScale], scrolls to each of [tags] and returns what is
 * wrong: a tag that is not drawn, or whose box is not wholly inside the frame once scrolled to.
 */
@OptIn(ExperimentalTestApi::class)
internal fun unreachableByScroll(
    widthDp: Int,
    heightDp: Int,
    fontScale: Float,
    tags: List<String>,
    content: @Composable () -> Unit,
): List<String> {
    val problems = mutableListOf<String>()
    for (tag in tags) {
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
                    Box(Modifier.testTag(FIT_ROOT).requiredSize(widthDp.dp, heightDp.dp)) { content() }
                }
            }
            waitForIdle()
            val node = onNodeWithTag(tag, useUnmergedTree = true)
            if (onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                problems += "$tag is not drawn at ${widthDp}x$heightDp @$fontScale"
                return@runComposeUiTest
            }
            node.performScrollTo()
            waitForIdle()
            val root = onNodeWithTag(FIT_ROOT, useUnmergedTree = true).fetchSemanticsNode()
            val n = node.fetchSemanticsNode()
            val top = (n.positionInRoot.y - root.positionInRoot.y) / density
            val bottom = top + n.size.height / density
            val left = (n.positionInRoot.x - root.positionInRoot.x) / density
            val right = left + n.size.width / density
            if (top < -0.75f || bottom > heightDp + 0.75f || left < -0.75f || right > widthDp + 0.75f) {
                problems += "$tag is still outside ${widthDp}x$heightDp @$fontScale after scrolling to it: %.1f..%.1f, %.1f..%.1f".format(left, right, top, bottom)
            }
        }
    }
    return problems
}
