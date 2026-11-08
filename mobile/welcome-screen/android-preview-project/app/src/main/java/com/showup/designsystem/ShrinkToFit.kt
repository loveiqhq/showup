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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

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
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = ceil(box / scale).toInt()))
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
    // Laid out at `box / scale` ROUNDED UP: that is the widest word's own width, and floored a hair
    // under it the word broke anyway -- `Direction` / `s` (the 4 Nov 2026 device audit).
    if (!constraints.hasBoundedWidth || longestWord <= constraints.maxWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val box = constraints.maxWidth
    val scale = maxOf(minScale, box.toFloat() / longestWord)
    // The height too is the drawing's, so the text laid out at full size gets it over the scale. A
    // parent that fixes the height (a grid row's tiles, one height) cut the text to the scaled height
    // -- up to 70 px of a chat tile's label at 150 % (the SHOWUP-194 fit sweep, 7 Nov 2026).
    val tall = if (constraints.hasBoundedHeight) ceil(constraints.maxHeight / scale).toInt() else Constraints.Infinity
    val p = measurable.measure(Constraints(maxWidth = ceil(box / scale).toInt(), maxHeight = tall))
    val width = (p.width * scale).toInt().coerceIn(constraints.minWidth, box)
    val height = (p.height * scale).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        p.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * [fitWords] for [text], measuring its widest word itself in [style] -- the font, size, weight and
 * letter spacing the `Text` draws with. The `Text` keeps its own parameters; [style] only measures.
 * iOS's `FitWords` is the twin.
 *
 * The floor is 60 %, or the default size where the user's type is larger than that: at 200 % a word
 * may shrink to 50 %, which is the size it has at 100 % -- never smaller. A 12-letter part of a long
 * name did not fit a 320 phone's column at 200 % even at 60 % (the 4 Nov 2026 device audit).
 */
@Composable
fun Modifier.fitWordsOf(text: String, style: TextStyle): Modifier {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = remember(text, style, density) { widestWord(measurer, text, style) }
    return fitWords(widest, wordFitFloor())
}

/** The widest word of [text] in [style], in px: what [fitWordsOf] shrinks to its box. */
fun widestWord(measurer: TextMeasurer, text: String, style: TextStyle): Int =
    text.split(' ', '\n', '\u200B').filter { it.isNotEmpty() }
        .maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0

/** The least [fitWordsOf] shrinks a word to at the user's type size: 60 %, or the default size. */
@Composable
fun wordFitFloor(): Float = minOf(0.6f, 1f / LocalDensity.current.fontScale)
