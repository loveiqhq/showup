/*
 * PrettyWrap.kt
 * ShowUp · CSS `text-wrap: pretty`, which Compose does not have
 *
 * The references set it on every Check-In headline and on most of their body copy -- Check-In 02's
 * subline, the No hours today? body, the gate's body, the Uplift inspirations, the toast. It decides
 * line breaks the sheets show: Check-In 02's subline ends `… See How it works for` / `more info.`
 * under it, where a greedy wrap leaves `info.` alone; its headline reads `When can you` / `date
 * today?`, where greedy leaves `today?` alone.
 *
 * THE RULE IS CHROME'S, MEASURED, not a guess at "avoid short last lines". Headless Chrome swept
 * across 375 layouts of the step headlines (14 Oct 2026): `pretty` re-wraps only when the greedy last
 * line is ONE WORD -- an em run joined by no-break spaces counts as one -- and narrower than a third
 * of the width, and then it pulls exactly one word down to it. A two-word last line is never touched
 * however short (Motivation's `to date?`, 0.32 of the width); a long single word keeps its line
 * (`tomorrow?`, 0.43).
 *
 * HOW, here: the widest width at which one more word reaches the last line, with the line count
 * unchanged. The paragraph is laid out at that width; everything else about it is untouched.
 *
 * `text-wrap: balance` lives here too, for the same reason: `WashHeadline` had it inline, and the
 * Search prompt topic (SHOWUP-184) was its first plain Text -- a second copy is how the two would
 * drift.
 */
package com.showup.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints

/**
 * The width, in px, at which [text] in [style] wraps as CSS `text-wrap: pretty` would inside
 * [maxPx]: [maxPx] itself unless the greedy wrap ends on an orphan.
 */
fun prettyWidthPx(measurer: TextMeasurer, text: AnnotatedString, style: TextStyle, maxPx: Int): Int {
    if (maxPx <= 0) return maxPx
    val greedy = measurer.measure(text, style, constraints = Constraints(maxWidth = maxPx))
    val lines = greedy.lineCount
    if (lines <= 1) return maxPx
    val last = lines - 1
    val lastStart = greedy.getLineStart(last)
    val lastText = text.text.substring(lastStart, greedy.getLineEnd(last)).trim()
    val lastWidth = greedy.getLineRight(last) - greedy.getLineLeft(last)
    // An orphan is one word -- no breaking space in it -- under a third of the width.
    if (' ' in lastText || lastWidth >= maxPx / 3f) return maxPx
    // Narrowed, a width either still breaks as greedy did or has moved a word down (or gained a
    // line). That test is MONOTONE in the width, so the widest width that moves something is a
    // binary search away.
    fun moved(px: Int): Boolean {
        val r = measurer.measure(text, style, constraints = Constraints(maxWidth = px.coerceAtLeast(1)))
        return r.lineCount > lines || r.getLineStart(last) < lastStart
    }
    var lo = 1
    var hi = maxPx
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (moved(mid)) lo = mid else hi = mid - 1
    }
    val pulled = measurer.measure(text, style, constraints = Constraints(maxWidth = lo))
    // Only a word pulled down at the SAME line count is Chrome's re-wrap.
    return if (pulled.lineCount == lines && pulled.getLineStart(last) < lastStart) lo else maxPx
}

/**
 * The width, in px, at which [text] in [style] wraps as CSS `text-wrap: balance` would inside
 * [maxPx]: the narrowest width that keeps the greedy line count, so the lines come out even. [maxPx]
 * itself for a single line, which has nothing to balance and would only become two.
 *
 * A binary search -- eight measurements rather than a scan's three hundred, because a Text
 * re-measures on every recomposition.
 */
fun balancedWidthPx(measurer: TextMeasurer, text: AnnotatedString, style: TextStyle, maxPx: Int): Int {
    if (maxPx <= 0) return maxPx
    fun linesAt(px: Int) = measurer.measure(text, style, constraints = Constraints(maxWidth = px.coerceAtLeast(1))).lineCount
    val target = linesAt(maxPx)
    if (target <= 1) return maxPx
    var low = 1
    var high = maxPx
    while (low < high) {
        val mid = (low + high) / 2
        if (linesAt(mid) <= target) high = mid else low = mid + 1
    }
    return low
}

/**
 * `text-wrap: pretty` on a Text node: lays it out at [prettyWidthPx] inside the width it is given,
 * and still takes the whole width, so the paragraph keeps its place -- start-aligned at the start,
 * [centered] in the middle, as CSS keeps the box and only moves the breaks.
 *
 * [text] and [style] must be exactly what the Text draws, or the breaks computed here are not the
 * breaks it makes.
 */
@Composable
fun Modifier.prettyWrap(text: AnnotatedString, style: TextStyle, centered: Boolean = false): Modifier =
    wrapAt(centered) { measurer, max -> prettyWidthPx(measurer, text, style, max) }

/** `text-wrap: balance` on a Text node, as [prettyWrap] is `pretty`: the box keeps its width, the breaks move. */
@Composable
fun Modifier.balanceWrap(text: AnnotatedString, style: TextStyle, centered: Boolean = false): Modifier =
    wrapAt(centered) { measurer, max -> balancedWidthPx(measurer, text, style, max) }

@Composable
private fun Modifier.wrapAt(centered: Boolean, widthPx: (TextMeasurer, Int) -> Int): Modifier {
    val measurer = rememberTextMeasurer()
    return layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val p = measurable.measure(constraints)
            return@layout layout(p.width, p.height) { p.place(0, 0) }
        }
        val max = constraints.maxWidth
        val w = widthPx(measurer, max)
        val p = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, w), maxWidth = w))
        val width = if (w < max) max else p.width
        layout(width, p.height) { p.place(if (centered) (width - p.width) / 2 else 0, 0) }
    }
}
