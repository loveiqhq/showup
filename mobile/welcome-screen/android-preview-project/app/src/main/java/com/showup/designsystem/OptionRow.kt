/*
 * OptionRow.kt
 * ShowUp · the single-select answer row (SHOWUP-168), and the chrome it shares with CheckRow
 *
 * Built by Profile 15 (gender) and consumed by orientation, education, religion and politics:
 * "build it once here as a separate component. A second copy is a bug." Its numbers are the
 * reference's `OptionRow kind="radio"` -- pad 16 / 4, radius 10, Manrope 500 / 16 / 1.3 (700 when
 * selected), a 22 radio that fills violet with an 8 white dot.
 *
 * NO `clearsOnReselect` PROP, and the tickets say so three times: what a tap on the selected row
 * does is SCREEN LOGIC in the caller's tap handler (`pickSingle`). Gender and orientation keep a
 * selection; education, religion and politics clear it. The row only reports the tap.
 *
 * `kind="checkbox"` from the kit is NOT built: dating language uses [CheckRow], which is its own
 * component (12 / 4, a round indicator), and nothing in the app draws a square-box option row.
 */
package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The rows' corner radius -- also the radius the selected wash and the divider follow. */
private val ROW_RADIUS = 10.dp

/**
 * The background wash and the hairline divider both answer rows draw.
 *
 * THE DIVIDER IS NOT A STRAIGHT LINE, and drawing one would be a quiet difference from the
 * reference. CSS puts `border-bottom: 1px` on a box with `border-radius: 10`, and a bottom border on
 * a rounded box follows the bottom corners: it bends up along each 10 arc and thins to nothing where
 * the arc meets the (zero-width) side. That region is exactly the outer rounded rectangle minus the
 * inner padding edge, whose bottom corners are 10 wide and 9 tall -- so that is what is drawn.
 *
 * Under the selected row's wash the bend is visible as the wash's own rounded bottom edge; on an
 * unselected row it is the soft end of the line. Both rows in a list share it, which is what makes
 * the list read as one object rather than stacked cards.
 */
internal fun Modifier.answerRowChrome(wash: Color, divider: Boolean): Modifier = drawBehind {
    val r = ROW_RADIUS.toPx()
    val outer = RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))
    if (wash.alpha > 0f) {
        drawPath(Path().apply { addRoundRect(outer) }, wash)
    }
    if (divider) {
        val hair = 1.dp.toPx()
        val inner = RoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height - hair,
            topLeftCornerRadius = CornerRadius(r, r),
            topRightCornerRadius = CornerRadius(r, r),
            bottomRightCornerRadius = CornerRadius(r, r - hair),
            bottomLeftCornerRadius = CornerRadius(r, r - hair),
        )
        val border = Path().apply {
            op(Path().apply { addRoundRect(outer) }, Path().apply { addRoundRect(inner) }, PathOperation.Difference)
        }
        drawPath(border, BorderSoft)
    }
}

/** The label both rows set: Manrope 16 / 1.3, 500 at rest and 700 when chosen. */
@Composable
internal fun AnswerRowLabel(label: String, chosen: Boolean, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier,
        color = Fg,
        fontFamily = Manrope,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = (16f * 1.3f).sp,
    )
}

/**
 * The 22 round indicator: white with a 1.5 border at rest, violet filled when chosen.
 *
 * `transition: background 180ms, border-color 180ms`. The centre mark is the caller's -- an 8 dot
 * for a radio, an 11 tick for a check.
 */
@Composable
internal fun RoundIndicator(chosen: Boolean, mark: @Composable () -> Unit) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val fill by animateColorAsState(if (chosen) Purple else Elevated, tween(ms, easing = CssEase), label = "indFill")
    val stroke by animateColorAsState(if (chosen) Purple else Border, tween(ms, easing = CssEase), label = "indStroke")
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) mark()
    }
}

/**
 * One single-select answer.
 *
 * @param index the row's position, 0-based, and [count] the list's length -- so a screen reader
 *   reads "Woman, 1 of 4" (Profile 15, accessibility). The list itself carries the matching
 *   collection info; see `DetailsScaffold`'s answer group.
 * @param last no divider under the final row.
 */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    last: Boolean = index == count - 1,
) {
    val motion = rememberMotion()
    val wash by animateColorAsState(
        if (selected) SelectedRowWash else Color.Transparent,
        tween(if (motion.enabled) Motion.FAST else 0, easing = CssEase),
        label = "optionWash",
    )
    Row(
        modifier
            .fillMaxWidth()
            // THE WHOLE ROW IS THE HIT AREA -- label, indicator and the padding between them. Never
            // smaller than 48: the row is 16 + 20.8 + 16 tall at the default font, and grows with it.
            //
            // NO RIPPLE. "The change is immediate, with the 180 ms background and indicator
            // transition. Nothing else on the screen changes" -- the wash IS the feedback, and a
            // Material ripple over it would be a second, undesigned one.
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
            .answerRowChrome(wash = wash, divider = !last)
            .padding(vertical = Spacing.xxl, horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnswerRowLabel(label, chosen = selected, modifier = Modifier.weight(1f))
        RoundIndicator(chosen = selected) {
            Box(Modifier.size(8.dp).background(Elevated, CircleShape))
        }
    }
}
