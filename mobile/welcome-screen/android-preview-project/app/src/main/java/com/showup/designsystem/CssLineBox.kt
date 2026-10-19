/*
 * CssLineBox.kt
 * ShowUp · a text node laid out as CSS lays out a block of text: n lines of `line-height` each
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT COMPOSE DOES DIFFERENTLY, MEASURED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * CSS gives every line a box exactly `line-height` tall and centres the font's content area in it:
 * the half-leading (line-height minus ascent-plus-descent, halved) goes above and below, NEGATIVE
 * when the line height is smaller than the font. Glyphs then overflow the box, and nothing cares.
 *
 * Compose (ui-text 1.7, `LineHeightStyleSpan` with the default `Trim.Both`) applies the line height
 * BETWEEN lines only: the first line keeps the font's full ascent and the last its full descent.
 * With `Trim.None` it shrinks them -- and then `TextLayout` adds the difference back as padding so
 * no glyph pokes out. Either way the block is (ascent + descent) - line-height taller than CSS's.
 *
 * For Lora 34 / 1.1 that is 43.5 - 37.4 = 6.1pt on every headline, half of it above the first
 * baseline: `HeadlineProbeTest` measured a two-line intro headline at 81.0 where CSS lays it out
 * at 74.8, and the Check-In intro's list sat 6pt below the spec sheet's. A line height LARGER than
 * the font (body text) goes the other way: the half-leading above the first line and below the
 * last is trimmed away, a point or so shorter than CSS.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THIS DOES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Lets the text lay itself out exactly as it does today, then reports the CSS box to its parent:
 * n x line-height tall, with the text moved so its FIRST BASELINE sits where CSS puts it --
 * `line-height / 2 + (ascent - descent) / 2` below the top. Both read off the laid-out text's own
 * baselines, so nothing about any font is assumed. Glyphs may draw outside the reported box, as
 * they do in CSS; the baselines a parent aligns to are moved with the text.
 *
 * Only for text whose line height is set: without one there is no CSS line box to match.
 */
package com.showup.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

fun Modifier.cssLineBox(lineHeight: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val first = placeable[FirstBaseline]
    val last = placeable[LastBaseline]
    // Not text, or not laid out as text: report it untouched.
    if (first == androidx.compose.ui.layout.AlignmentLine.Unspecified ||
        last == androidx.compose.ui.layout.AlignmentLine.Unspecified
    ) {
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val line = lineHeight.toPx()
    // Compose's first line runs from 0 to the font's own ascent; its last from the last baseline to
    // the font's own descent. Every baseline in between is one line height after the previous one.
    val ascent = first.toFloat()
    val descent = (placeable.height - last).toFloat()
    val lines = ((last - first) / line).roundToInt() + 1
    val cssFirst = line / 2f + (ascent - descent) / 2f
    val shift = (ascent - cssFirst).roundToInt()
    val height = (lines * line).roundToInt().coerceAtLeast(constraints.minHeight)
    layout(
        placeable.width,
        height,
        mapOf(FirstBaseline to first - shift, LastBaseline to last - shift),
    ) {
        placeable.place(0, -shift)
    }
}
