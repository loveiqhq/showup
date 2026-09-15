/*
 * StepProgress.kt
 * ShowUp · the segmented progress bar, for every flow that has steps
 *
 * WHY IT MOVED HERE, 15 SEPTEMBER 2026
 *
 * It lived in `tutorial/TutorialShell.kt` because the tutorial was the only thing with steps. Then
 * "The basics" imported it from there, and with SHOWUP-156 and SHOWUP-158 "The real you" makes a
 * third group -- at which point a shared primitive is sitting in a screen package that two other
 * packages have to reach into.
 *
 * That is the exact shape of the defect this project has already paid for once: `PillButton` sat
 * in `WelcomeShell.kt`, whoever wrote the tutorial's CTA had no reason to open the sign-up flow's
 * shell, and they wrote a second button that then drifted four ways. The rule in CLAUDE.md --
 * "a shared primitive never lives in a screen file" -- is that lesson, and this is it being
 * applied rather than the third group being asked to import from the first.
 *
 * Nothing about the component changed in the move. It already took `steps` and `current` as
 * parameters, which is what both profile groups need: one shell, the count and the index as props,
 * so a group changing from four segments to three is one call site rather than three screens.
 */
package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * ② Step progress — N segments, height 5, gap 6, full content width.
 *
 * The segment colour animates rather than snapping, so advancing a card reads as progress being
 * made rather than as the bar being redrawn. It is one colour tween per segment, which costs
 * nothing and is skipped entirely when the device asks for no motion.
 *
 * Semantics: the bar is one node reporting "Step N of M", not M anonymous boxes. Without this a
 * screen reader announces nothing at all here — the segments carry no text.
 */
@Composable
fun StepProgress(steps: Int, current: Int, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo =
                    ProgressBarRangeInfo(current.toFloat(), 0f..steps.toFloat(), steps)
                contentDescription = "Step " + current + " of " + steps
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(steps) { i ->
            val target = if (i < current) Purple else Track
            val segment by animateColorAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = if (motion.enabled) 320 else 0),
                label = "segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(segment)
            )
        }
    }
}
