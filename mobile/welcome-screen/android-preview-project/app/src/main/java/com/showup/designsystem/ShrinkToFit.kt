/*
 * ShrinkToFit.kt
 * ShowUp · one line of text that shrinks to its box instead of being cut
 *
 * For a label that must stay on one line inside a fixed control -- an availability slot's `noon` in
 * its 36 pill, the summary's ranges -- and that the system font can make wider than the control. At
 * 2x on a 320 phone a slot is 64 wide and `noon` at 26sp is not: truncated, it read `no...`. Shrunk,
 * it is still bigger than it is at the default size, and whole. UIKit's `minimumScaleFactor` is the
 * same idea; Compose 1.7 has none.
 *
 * The text is laid out at its natural width and DRAWN scaled about [origin], so the glyphs are the
 * real ones, not a re-flowed smaller font, and nothing changes at all when it already fits. Apply it to
 * a `Text` with `maxLines = 1, softWrap = false`.
 */
package com.showup.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * @param minScale the least it shrinks to; past it the text is laid out at `1 / minScale` of the
 *   box and may still be cut, which the caller avoids by choosing to wrap instead.
 * @param origin the horizontal pivot: 0 shrinks toward the leading edge, 0.5 about the centre.
 */
fun Modifier.shrinkToFit(minScale: Float, origin: Float = 0.5f): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val natural = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val box = constraints.maxWidth
    if (natural.width <= box) {
        val baseline = natural[FirstBaseline]
        return@layout layout(
            natural.width.coerceAtLeast(constraints.minWidth),
            natural.height,
            if (baseline == AlignmentLine.Unspecified) emptyMap() else mapOf(FirstBaseline to baseline),
        ) { natural.place(0, 0) }
    }
    val scale = maxOf(minScale, box.toFloat() / natural.width)
    // Laid out at the width the shrink makes room for, then drawn scaled.
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (box / scale).toInt()))
    val baseline = p[FirstBaseline]
    val width = (p.width * scale).toInt().coerceIn(constraints.minWidth, box)
    layout(
        width,
        p.height,
        if (baseline == AlignmentLine.Unspecified) emptyMap()
        else mapOf(FirstBaseline to (p.height / 2f + (baseline - p.height / 2f) * scale).toInt()),
    ) {
        p.placeWithLayer(((width - p.width) * origin).toInt(), 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(origin, 0.5f)
        }
    }
}

/**
 * Wrapping text that never breaks inside a word: laid out at its box's width, wrapping between words
 * -- and where its longest word is wider than the box (a narrow card at the largest type), laid out
 * at that word's width and drawn scaled down to the box, so every word stays whole. Nothing changes
 * when the longest word fits. Apply it to a wrapping `Text`; [longestWord] is that word's width at
 * full size, in px, measured by the caller.
 *
 * @param minScale the least it shrinks to; past it a word is broken as the text would break it.
 */
fun Modifier.fitWords(longestWord: Int, minScale: Float = 0.6f): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || longestWord <= constraints.maxWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val box = constraints.maxWidth
    val scale = maxOf(minScale, box.toFloat() / longestWord)
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (box / scale).toInt()))
    val width = (p.width * scale).toInt().coerceIn(constraints.minWidth, box)
    val height = (p.height * scale).toInt()
    layout(width, height) {
        p.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
