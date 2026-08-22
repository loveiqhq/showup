/*
 * TutorialShell.kt
 * ShowUp · the shared chrome for every tutorial screen (SHOWUP-135)
 *
 * Screen 2 ("Meet in real life") is the reference implementation of this chrome; screens 3-6 reuse
 * it unchanged and pass content only. Nothing screen-specific belongs in this file.
 *
 * The layout rule that matters: this is ONE top-anchored column with a SINGLE Spacer(weight = 1f)
 * between the illustration and the nav row. That spacer is the only thing that absorbs height
 * differences between screens and between devices. No Y position is ever hard-coded — offsets that
 * look right at 844 tall break on every other frame.
 *
 * The illustration is the only flexible element. On short frames it shrinks; the type, the nav row
 * and the safe-area margins never do, and the screen never scrolls.
 */
package com.showup.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.EyebrowBg
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle
import com.showup.designsystem.Track

/** ② Step progress — 5 segments, height 5, gap 6, full content width. */
@Composable
fun StepProgress(steps: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < current) Purple else Track)
            )
        }
    }
}

/**
 * ③ Eyebrow pill — Manrope 700 / 11 / uppercase, tracking .08, padding 5/10.
 *
 * `align(Alignment.Start)` is load-bearing: in a Column the default stretches children to the full
 * content width, which is the full-width band the reference render shows. The design-system
 * component is a hug-width pill.
 */
@Composable
fun ColumnScope.EyebrowPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .align(Alignment.Start)
            .clip(RoundedCornerShape(50))
            .background(EyebrowBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(Orange, CircleShape))
        Text(
            text.uppercase(),
            color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.08.em,
        )
    }
}

/** ⑨ Next — label plus a 56dp circular arrow, gap 14. */
@Composable
fun NextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Orange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The screen shell. Everything here is identical across tutorial screens 2-6.
 *
 * @param step          which segment of the progress bar is filled (1-based)
 * @param totalSteps    how many segments — 5 for the tour
 * @param eyebrow       the pill label
 * @param showBack      false on the first screen of the tour: the slot stays, reserving layout
 *                      width, but is invisible and non-interactive
 * @param headline      the Lora headline, passed in so each screen can style its own italic run
 * @param content       ⑤⑥⑦ the per-screen body — rule rows, paragraphs
 * @param art           ⑧ the illustration block, 342 x 230, the only element allowed to shrink
 */
@Composable
fun TutorialShell(
    step: Int,
    totalSteps: Int,
    eyebrow: String,
    nextLabel: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    headline: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    art: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Cream)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // ① content region: pad-top 8 below the safe-area inset, gutter 24, floor 0
                .padding(start = 24.dp, end = 24.dp, top = 8.dp),
        ) {
            StepProgress(totalSteps, step)
            Spacer(Modifier.height(24.dp))          // progress -> eyebrow

            EyebrowPill(eyebrow)
            Spacer(Modifier.height(14.dp))          // eyebrow -> headline

            headline()
            Spacer(Modifier.height(28.dp))          // headline -> content

            content()

            Spacer(Modifier.height(24.dp))          // content -> illustration

            // The single flexible region. Compose does not shrink a fixed-height child the way CSS
            // flex does, so rather than "art at 230 + a spacer", the art's SLOT takes all remaining
            // space and the art measures min(available, 230) inside it, aligned to the top. Same
            // result as the spec's "art + flex:1 spacer": art at its natural size on tall frames,
            // shrinking on short ones, nav row pinned to the bottom either way.
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                art()
            }

            // ⑨ nav row — bottom-anchored, 24 above the content floor
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Back",
                    color = if (showBack) Subtle else Color.Transparent,
                    fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = if (showBack) Modifier.clickable(onClick = onBack) else Modifier,
                )
                NextButton(nextLabel, onNext)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
