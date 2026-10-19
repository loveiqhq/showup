package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The six-box code row, shared by phone verification and email verification.
 *
 * WHAT MAKES THIS ONE COMPONENT AND NOT TWO
 *
 * Both screens draw six 49 x 62 boxes at radius 14 with one digit each, and both are decoration
 * for a SINGLE text field behind them rather than six fields. That last part is the reason the row
 * is worth sharing at all: six fields would mean six focus targets, and then backspace, paste and
 * one-time-code autofill each have to be taught to hop between them, which is where per-digit code
 * inputs usually break. One field holding the whole code makes paste and autofill work for free,
 * and this row is only what that field looks like.
 *
 * WHAT THE TWO SCREENS GENUINELY DIFFER ON
 *
 * Two things, both from their own spec sheets rather than drift:
 *
 *  - [halo]. SHOWUP-153's sheet draws a 4dp ring outside the stroke on the active and error slots.
 *    SHOWUP-143's does not. Off by default so the older screen keeps what was approved.
 *  - [caretBlinks]. 153's sheet specifies a blinking caret and says why: "A static bar reads as a
 *    filled slot at a glance -- the one thing this row must never be ambiguous about." 143 shipped
 *    a static bar. Left as a parameter rather than changed on both, because altering a screen in
 *    PO Acceptance is a product call; the concern is recorded here so it can be taken.
 *
 * The compact tier is a third difference and a smaller one: 143 shrinks to 44 x 56 on a short
 * frame, which is also the floor below which a slot stops being a reliable touch target.
 */
@Composable
fun CodeSlotRow(
    digits: String,
    /** The whole row turns danger, and no slot is active — the error is the row, not a position. */
    error: Boolean = false,
    slotWidth: Dp = 49.dp,
    slotHeight: Dp = 62.dp,
    halo: Boolean = false,
    caretBlinks: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val shape = RoundedCornerShape(Radius.control)

    // One transition drives every caret; a per-slot one would restart whenever the active index
    // moved, so the caret would appear to stutter as the user types.
    val blink by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        // steps(2, end): hard on/off, not a fade. A fading caret reads as a dimmed digit.
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "caretBlink",
    )

    Row(
        modifier.fillMaxWidth(),
        // space-between, never weight(1f): at 320 an evenly divided row drops each slot below
        // 44dp and they stop being reliable targets. The gap absorbs the difference instead.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(6) { i ->
            val ch = digits.getOrNull(i)
            val active = !error && i == digits.length && digits.length < 6

            val borderColor by animateColorAsState(
                when {
                    error -> Danger
                    active -> Purple
                    ch != null -> Fg.copy(alpha = 0.32f)
                    else -> Border
                },
                tween(ms), label = "slotBorder",
            )
            val haloColor by animateColorAsState(
                when {
                    !halo -> Color.Transparent
                    error -> Danger.copy(alpha = 0.10f)
                    active -> Purple.copy(alpha = 0.10f)
                    else -> Color.Transparent
                },
                tween(ms), label = "slotHalo",
            )
            val fill by animateColorAsState(
                if (error) Danger.copy(alpha = 0.04f) else Elevated,
                tween(ms), label = "slotFill",
            )

            Box(
                Modifier
                    .size(width = slotWidth, height = slotHeight)
                    // The ring is OUTSIDE the stroke, so it is a second border on the same box
                    // rather than a shadow. Modifier.shadow is elevation and has a light source,
                    // so it would not be even on all four sides.
                    .border(4.dp, haloColor, shape)
                    .clip(shape)
                    .background(fill)
                    .border(1.5.dp, borderColor, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (ch != null) {
                    Text(
                        ch.toString(),
                        color = if (error) DangerDigit else Fg,
                        fontFamily = Lora,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                    )
                } else if (active) {
                    Box(
                        Modifier
                            .size(width = 2.dp, height = if (halo) 26.dp else 28.dp)
                            .alpha(if (caretBlinks && motion.enabled) blink else 1f)
                            .background(Purple),
                    )
                }
            }
        }
    }
}
