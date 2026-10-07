/*
 * ConfettiRain.kt
 * ShowUp · Embrace 2's one-shot confetti (SHOWUP-166)
 *
 * "The confetti is the reason the screen exists." Every number is `ConfettiRain` in
 * `screen-embrace-details-reference.jsx`; this is that component, reproduced to the keyframe.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THE CSS ACTUALLY DOES, AND THEREFORE WHAT THIS DOES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   FALL   translateY -40 -> height + 40 over the piece's duration, `cubic-bezier(.3,.4,.6,1)`.
 *          The transform has keyframes only at 0% and 100%, so the easing spans the whole fall.
 *   FADE   opacity keyframes at 0% (1), 80% (1) and 100% (0). A CSS timing function applies PER
 *          KEYFRAME INTERVAL, so the same easing runs again over the last 20% alone -- not a linear
 *          fade, and not a fade eased across the whole fall.
 *   SWAY   translateX -sway -> +sway, rotate rot0 -> rot0 + spin, rotateY 0 -> 180deg, ease-in-out,
 *          `infinite alternate`. Alternate REVERSES the progress and the easing runs on the
 *          reversed value, which is what makes each swing ease into its turn.
 *   FLIP   `rotateY` with no `perspective` anywhere in the reference is an ORTHOGRAPHIC flip, which
 *          is exactly a horizontal scale by cos(angle). `graphicsLayer.rotationY` would add a
 *          camera and a perspective the design does not have, so the flip is drawn as that scale.
 *   FILL   `both`: during its delay a piece holds its first frame, 40 above the top edge -- off
 *          screen -- and after its fall it holds its last, below the bottom, faded. So "no piece
 *          appears or vanishes on screen", by construction.
 *
 * ONE CANVAS, REDRAWN PER FRAME, and nothing else: no recomposition, no layout, no per-piece node.
 * The frame clock drives one value; the draw phase reads it. "Transform and opacity only -- never
 * top, left, width or height" holds because every piece is drawn under a transform at a fixed size.
 *
 * DETERMINISTIC. The table is fixed and nothing is random, so every screenshot is comparable.
 */
package com.showup.profile

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.showup.designsystem.Orange
import com.showup.designsystem.Orange400
import com.showup.designsystem.Purple
import com.showup.designsystem.Purple400
import com.showup.designsystem.rememberMotion
import kotlin.math.PI
import kotlin.math.cos

/** The confetti layer, for tests. */
internal const val CONFETTI_TAG = "confetti"

/** "The layer is unmounted at 3000 ms. Never loops." */
internal const val CONFETTI_TOTAL_MS = 3_000L

private val FALL_EASE = CubicBezierEasing(0.3f, 0.4f, 0.6f, 1f)

/** CSS `ease-in-out`. */
private val SWAY_EASE = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

internal enum class ConfettiShape { Strip, Square, Dot }

/**
 * One row of `CONFETTI_PIECES`: `[left %, delay ms, fall ms, rot0 deg, spin deg, sway px, shape,
 * colour, size px]`.
 */
internal data class ConfettiPiece(
    val leftPercent: Float,
    val delayMs: Int,
    val fallMs: Int,
    val rot0: Float,
    val spin: Float,
    val sway: Float,
    val shape: ConfettiShape,
    val color: Color,
    val size: Float,
)

private fun piece(
    left: Int, delay: Int, fall: Int, rot0: Int, spin: Int, sway: Int,
    shape: ConfettiShape, color: Color, size: Int,
) = ConfettiPiece(
    left.toFloat(), delay, fall, rot0.toFloat(), spin.toFloat(), sway.toFloat(), shape, color, size.toFloat(),
)

private val S = ConfettiShape.Strip
private val Q = ConfettiShape.Square
private val D = ConfettiShape.Dot

/** The 32-row table, verbatim. Tokens only: orange 500 / 400, primary 500 / 400. */
internal val CONFETTI_PIECES: List<ConfettiPiece> = listOf(
    piece(4, 0, 2300, -18, 540, 14, S, Orange, 14),
    piece(12, 260, 2100, 42, -420, 18, Q, Purple, 10),
    piece(19, 80, 2500, 12, 600, 12, S, Orange400, 12),
    piece(26, 420, 2000, -30, -360, 20, D, Purple400, 9),
    piece(33, 140, 2400, 68, 480, 16, S, Purple, 14),
    piece(40, 560, 2200, -12, -540, 14, Q, Orange400, 11),
    piece(47, 40, 2600, 24, 420, 22, S, Orange, 13),
    piece(54, 320, 2050, -52, -600, 12, D, Purple, 8),
    piece(61, 180, 2350, 18, 360, 18, S, Orange400, 12),
    piece(68, 480, 2150, -8, -480, 16, Q, Orange, 10),
    piece(75, 100, 2450, 36, 540, 14, S, Purple400, 13),
    piece(82, 380, 2000, 54, -420, 20, S, Orange400, 11),
    piece(89, 220, 2300, -22, 600, 12, D, Orange, 9),
    piece(96, 600, 2100, 14, -360, 18, S, Purple, 12),
    piece(8, 660, 2250, -40, 480, 16, Q, Orange400, 10),
    piece(16, 360, 2550, 30, -540, 14, S, Orange, 13),
    piece(23, 20, 1950, -14, 420, 22, D, Purple, 8),
    piece(30, 500, 2400, 62, -600, 12, S, Orange400, 11),
    piece(37, 240, 2150, -36, 360, 18, Q, Purple400, 9),
    piece(44, 700, 2050, 20, -480, 16, S, Orange, 12),
    piece(51, 160, 2500, -28, 540, 14, S, Purple, 10),
    piece(58, 440, 2200, 46, -420, 20, D, Orange400, 9),
    piece(65, 60, 2350, 8, 600, 12, S, Orange, 12),
    piece(72, 620, 2000, -16, -360, 18, Q, Purple, 10),
    piece(79, 300, 2450, 58, 480, 16, S, Purple400, 13),
    piece(86, 520, 2100, -44, -540, 14, S, Orange400, 11),
    piece(93, 120, 2300, 26, 420, 22, D, Purple, 8),
    piece(2, 400, 2150, -6, -600, 12, S, Purple400, 12),
    piece(49, 680, 2250, 34, 360, 18, S, Orange, 11),
    piece(63, 280, 2600, -24, -480, 16, Q, Orange400, 9),
    piece(35, 580, 2050, 50, 540, 14, D, Orange, 9),
    piece(85, 200, 2400, -10, -420, 20, S, Purple, 12),
)

/** `confettiSwayMs(i)`: 700 · 820 · 940 · 1060 by index. */
internal fun confettiSwayMs(index: Int): Int = 700 + (index % 4) * 120

/** Where one piece is at [elapsedMs] after t = 0, on a screen [heightPx] tall. Pure; tested. */
internal data class ConfettiFrame(
    val y: Float,
    val alpha: Float,
    val swayX: Float,
    val rotation: Float,
    val flipScale: Float,
)

/**
 * The keyframes, evaluated. [pxPerUnit] converts the reference's CSS pixels (the -40, the sway) to
 * this screen's pixels; [heightPx] is "the screen's own height, measured at runtime -- not 884".
 */
internal fun confettiFrame(
    piece: ConfettiPiece,
    index: Int,
    elapsedMs: Long,
    heightPx: Float,
    pxPerUnit: Float,
): ConfettiFrame {
    val local = (elapsedMs - piece.delayMs).coerceAtLeast(0L).toFloat()

    // FALL and FADE share the piece's own duration. Before the delay ends, `local` is 0 -- the
    // first frame, held by `both`.
    val t = (local / piece.fallMs).coerceIn(0f, 1f)
    val start = -40f * pxPerUnit
    val end = heightPx + 40f * pxPerUnit
    val y = start + (end - start) * FALL_EASE.transform(t)
    val alpha = if (t < 0.8f) 1f else 1f - FALL_EASE.transform((t - 0.8f) / 0.2f)

    // SWAY: infinite, alternate, from the same delay.
    val swing = confettiSwayMs(index).toFloat()
    val cycle = (local / swing).toInt()
    val phase = (local % swing) / swing
    val directed = if (cycle % 2 == 0) phase else 1f - phase
    val s = SWAY_EASE.transform(directed)
    val swayX = (-piece.sway + 2f * piece.sway * s) * pxPerUnit
    val rotation = piece.rot0 + piece.spin * s
    val flip = cos(PI * s).toFloat() // rotateY 0 -> 180deg, orthographic

    return ConfettiFrame(y, alpha, swayX, rotation, flip)
}

/**
 * The rain. Plays ONCE: [start] is the moment the push transition has settled, and the layer is
 * gone 3000 ms later.
 *
 * NOTHING AT ALL WITH REDUCE MOTION -- "not a static scatter: still pieces on a still screen read as
 * debris" -- and nothing when [play] is false: a back-pop onto the bridge is not a push, and does
 * not replay it.
 *
 * `played` IS SAVED, AND SET WHEN THE RAIN BEGINS -- not when it ends -- so returning from the
 * background, a dark-mode switch or a process death, even mid-fall, restores a screen whose rain has
 * already started rather than raining again. Whether THIS composition may play is decided once, as
 * it is created.
 *
 * THE START IS LATCHED. Once the push has settled the rain runs to its end: tapping the CTA mid-fall
 * starts the exit transition, which turns [start] back to false, and the pieces must keep falling
 * as the screen leaves rather than hang in the air.
 */
@Composable
internal fun ConfettiRain(play: Boolean, start: Boolean, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val mayPlay = remember { !played }
    if (!play || !motion.enabled || !mayPlay) return

    val elapsed = remember { mutableLongStateOf(-1L) }
    var begun by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start) begun = true
    }
    LaunchedEffect(begun) {
        if (!begun) return@LaunchedEffect
        played = true
        var origin = -1L
        while (true) {
            val now = withFrameMillis { it }
            if (origin < 0) origin = now
            val e = now - origin
            if (e >= CONFETTI_TOTAL_MS) break
            elapsed.longValue = e
        }
        finished = true
    }
    // "The layer is unmounted at 3000 ms."
    if (finished) return

    Canvas(modifier.fillMaxSize().testTag(CONFETTI_TAG).clearAndSetSemantics {}) {
        val e = elapsed.longValue
        if (e < 0) return@Canvas
        val unit = 1.dp.toPx()
        CONFETTI_PIECES.forEachIndexed { i, p ->
            val f = confettiFrame(p, i, e, size.height, unit)
            if (f.alpha <= 0f) return@forEachIndexed
            drawPiece(p, f, x = size.width * p.leftPercent / 100f, unit = unit)
        }
    }
}

/**
 * One piece: placed with its LEFT edge at `left %` and its top at the fall offset -- the outer span
 * -- then swayed, spun and flipped about its own centre -- the inner one.
 */
private fun DrawScope.drawPiece(p: ConfettiPiece, f: ConfettiFrame, x: Float, unit: Float) {
    val w = when (p.shape) {
        ConfettiShape.Strip -> p.size * 0.5f
        else -> p.size
    } * unit
    val h = when (p.shape) {
        ConfettiShape.Strip -> p.size * 1.4f
        else -> p.size
    } * unit
    val centre = Offset(x + w / 2f, f.y + h / 2f)
    translate(left = f.swayX) {
        rotate(degrees = f.rotation, pivot = centre) {
            scale(scaleX = f.flipScale, scaleY = 1f, pivot = centre) {
                val topLeft = Offset(centre.x - w / 2f, centre.y - h / 2f)
                val color = p.color.copy(alpha = f.alpha)
                when (p.shape) {
                    ConfettiShape.Dot -> drawCircle(color, radius = w / 2f, center = centre)
                    ConfettiShape.Square -> drawRoundRect(
                        color, topLeft, Size(w, h), CornerRadius(2f * unit, 2f * unit),
                    )
                    ConfettiShape.Strip -> drawRoundRect(
                        color, topLeft, Size(w, h), CornerRadius(1.5f * unit, 1.5f * unit),
                    )
                }
            }
        }
    }
}
