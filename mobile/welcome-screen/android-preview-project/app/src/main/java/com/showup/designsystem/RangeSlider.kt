/*
 * RangeSlider.kt
 * ShowUp · `RangeSlider` + `SliderThumb` -- one thumb or two over an integer range (SHOWUP-182)
 *
 * Built by Check-In 05 for its age and distance filters; Search's filter sheet consumes it rather
 * than building its own ("a second copy is a bug"). Numbers from `screen-checkin-filters-
 * reference.jsx`:
 *
 *   · 44 tall, the track INSET 22 each side -- half the thumb's hit box -- "so a thumb at either end,
 *     hit area included, never overflows the list: no sideways pan, no clipped target"
 *   · track: 4 tall at y 20, `--liq-border`, a full pill; the picked span in violet
 *   · thumb: 28 drawn (white, a 1.5 violet border, `0 2 8` violet glow), 44 hit target
 *
 * TWO THUMBS NEVER CROSS. The caller's `onChange` carries the other thumb when one is dragged past
 * it (`moveAgeThumb`). Thumbs that SIT on the same value are told apart by the first movement --
 * left moves the lower, right the upper -- or two thumbs parked together at an end could never be
 * separated again.
 *
 * ACCESSIBLE: each thumb is a slider with its label, its value and ±1 on swipe up / down (TalkBack
 * adjusts through `setProgress`), its range bounded by the other thumb.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** The thumb's hit box -- and twice the track's inset. */
private val THUMB_HIT = 44.dp
private val THUMB_INSET = THUMB_HIT / 2

/** Fit-harness anchor for thumb [index] of the slider tagged [tag]. */
fun sliderThumbTag(tag: String, index: Int) = "$tag-thumb-$index"

/**
 * @param values one value (a single thumb) or two (a range), each within [min]..[max].
 * @param labels each thumb's accessible label, in the same order.
 * @param onChange a thumb moved to a value; the caller keeps two thumbs from crossing.
 */
@Composable
fun RangeSlider(
    values: List<Int>,
    min: Int,
    max: Int,
    labels: List<String>,
    onChange: (thumb: Int, value: Int) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "slider",
) {
    val dual = values.size > 1
    val current by rememberUpdatedState(values)
    val change by rememberUpdatedState(onChange)
    // Which thumb a drag moves; -2 while two coincident thumbs wait for the first movement.
    var dragging by remember { mutableStateOf(-1) }

    val density = LocalDensity.current
    val insetPx = with(density) { THUMB_INSET.toPx() }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(THUMB_HIT)
            // THE DRAG COVERS THE WHOLE 44 BAND, inset included: a thumb at either end has half its
            // hit box in the inset, and that half must answer too. Positions are read back onto
            // the track by subtracting the inset.
            .pointerInput(min, max) {
                val trackPx = size.width - 2 * insetPx
                fun fraction(v: Int) = if (max == min) 0f else (v - min).toFloat() / (max - min)
                fun thumbX(v: Int) = insetPx + fraction(v) * trackPx
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        val near = current.indices.filter { abs(thumbX(current[it]) - at.x) <= insetPx }
                        dragging = when {
                            near.isEmpty() -> -1
                            near.size == 1 -> near.first()
                            current[0] == current[1] -> -2
                            else -> near.minBy { abs(thumbX(current[it]) - at.x) }
                        }
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                ) { pointer, dx ->
                    if (dragging == -2) dragging = if (dx < 0) 0 else 1
                    if (dragging < 0) return@detectHorizontalDragGestures
                    pointer.consume()
                    val ratio = ((pointer.position.x - insetPx) / trackPx).coerceIn(0f, 1f)
                    change(dragging, (min + ratio * (max - min)).roundToInt())
                }
            }
            .padding(horizontal = THUMB_INSET),
    ) {
        val trackPx = constraints.maxWidth.toFloat()
        fun fraction(v: Int) = if (max == min) 0f else (v - min).toFloat() / (max - min)

        // The track and the picked span, 4 tall at y 20.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Border),
        )
        val from = if (dual) fraction(values[0]) else 0f
        val to = if (dual) fraction(values[1]) else fraction(values[0])
        with(density) {
            Box(
                Modifier
                    .offset(x = (from * trackPx).toDp())
                    .padding(top = 20.dp)
                    .width(((to - from) * trackPx).toDp())
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Purple),
            )
        }

        values.forEachIndexed { i, v ->
            val lo = if (dual && i == 1) values[0] else min
            val hi = if (dual && i == 0) values[1] else max
            SliderThumb(
                centreX = with(density) { (fraction(v) * trackPx).toDp() },
                value = v,
                range = lo..hi,
                label = labels.getOrElse(i) { "" },
                onStep = { target -> change(i, target.coerceIn(lo, hi)) },
                modifier = Modifier.testTag(sliderThumbTag(tag, i)),
            )
        }
    }
}

/** One thumb: a 44 hit box centred on [centreX], a 28 white disc with a violet edge and glow. */
@Composable
private fun SliderThumb(
    centreX: Dp,
    value: Int,
    range: IntRange,
    label: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .offset(x = centreX - THUMB_INSET)
            .size(THUMB_HIT)
            .semantics {
                contentDescription = label
                stateDescription = "$value"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.toFloat(),
                    range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    onStep(target.roundToInt())
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                // `0 2px 8px rgba(129,42,236,.18)` -- the chips' violet glow.
                .pillShadow(Color.White, ChipGlow, offsetY = 2.dp, blur = 8.dp)
                .border(1.5.dp, Purple, CircleShape),
        )
    }
}
