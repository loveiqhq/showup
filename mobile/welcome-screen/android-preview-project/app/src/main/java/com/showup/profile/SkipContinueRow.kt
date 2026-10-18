/*
 * SkipContinueRow.kt
 * ShowUp · a footer's `Skip for now` and its Continue -- side by side when they fit, stacked when
 * they do not
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS ONE COMPONENT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Three footers pair a skip link with the round Continue: media (SHOWUP-161), the detail steps
 * (SHOWUP-167 to 173) and Interests / Life / Future (SHOWUP-174 to 176). A plain `SpaceBetween` row
 * measures the skip link first and hands Continue whatever is left, so at the largest system font
 * the label broke over two lines and the 56 circle was crushed to a sliver -- on the 320 phone at
 * 1.3x, and on nearly every phone at 2.0x, 390 included. Media found it first, by looking, and fixed
 * it in its own footer; the detail group's footer kept the defect for want of being the same code.
 * `PickFitTest` now measures it: Continue's label on one line and its whole circle, everywhere.
 *
 * MEASURED, NOT THRESHOLDED -- media's rule, moved here. Both labels are laid out with the
 * real font at the real size, and the row decides: side by side when skip + label + circle and two
 * 12 breaths fit; otherwise Skip on its own line above, and Continue LAST, nearest the thumb, at the
 * end edge. A `fontScale > 1.3` rule would be right for English and wrong for the first translation.
 *
 * At the default font on every phone in the matrix the two always fit, so every reference frame is
 * the plain row it draws.
 */
package com.showup.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.IconSizes
import com.showup.designsystem.Manrope
import com.showup.designsystem.SkipLink
import com.showup.designsystem.Spacing
import com.showup.tutorial.NextButton

/** `NextButton`'s gap between its label and its circle -- `gap: 14` in shared.jsx. */
private val NEXT_BUTTON_GAP = 14.dp

/**
 * @param onSkip null draws Continue alone at the end edge -- the mandatory detail steps, which have
 *   no skip link ("not hidden, absent").
 * @param circleSize the NextButton's circle: 56 on the detail steps and the picks, 52 on media.
 * @param ctaModifier on the NextButton itself -- the fit harnesses' anchor tag.
 */
@Composable
fun SkipContinueRow(
    skipLabel: String,
    ctaLabel: String,
    onSkip: (() -> Unit)?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    circleSize: Dp = IconSizes.badge,
    ctaModifier: Modifier = Modifier,
) {
    val cta = @Composable {
        NextButton(
            label = ctaLabel,
            onClick = onContinue,
            modifier = ctaModifier,
            arrowSize = 22.dp,
            circleSize = circleSize,
        )
    }
    if (onSkip == null) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { cta() }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val room = maxWidth
        val fits = with(density) {
            val skip = measurer.measure(
                skipLabel,
                TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
            ).size.width.toDp()
            val label = measurer.measure(
                ctaLabel,
                TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp),
            ).size.width.toDp()
            // Everything the row really holds: the link and the 12 of its own end padding, then the
            // button -- its label, the 14 it keeps before the circle, the circle. Media's original
            // sum counted 12 for that 14, so a row inside a 2-wide window was squeezed by 2; this is
            // the exact width, so a row either fits whole or stacks.
            skip + Spacing.xl + label + NEXT_BUTTON_GAP + circleSize <= room
        }
        if (fits) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipLink(onClick = onSkip, label = skipLabel)
                cta()
            }
        } else {
            // Continue LAST, nearest the thumb, and each on its own line with the full width to
            // itself. Skip stays the quiet one above it rather than becoming a second button.
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(Modifier.fillMaxWidth()) { SkipLink(onClick = onSkip, label = skipLabel) }
                cta()
            }
        }
    }
}
