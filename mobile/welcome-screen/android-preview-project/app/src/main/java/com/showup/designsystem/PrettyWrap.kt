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
 * [maxPx]: [maxPx] itself unless the greedy wrap ends on an orphan -- and never a width under
 * [minContentPx], where a word would break inside to move, as [balancedWidthPx] never goes either.
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
    if (pulled.lineCount != lines || pulled.getLineStart(last) >= lastStart) return maxPx
    // And never under the widest unbreakable piece. Where the line above the orphan holds one piece
    // wider than the rest, the only width that pulls anything down is under it: the line breaker
    // splits the piece and its fragment is what moves (`Incomprehensibiliti` / `es word`; the review
    // of SHOWUP-199, 27 Nov 2026). The search found the WIDEST width that moves, so nothing at or
    // above the floor does: there is no pretty width, and the paragraph keeps its own.
    return if (lo >= minContentPx(measurer, text, style)) lo else maxPx
}

/**
 * The width, in px, at which [text] in [style] wraps as CSS `text-wrap: balance` would inside
 * [maxPx]: the narrowest width that keeps the greedy line count, so the lines come out even -- and
 * never narrower than the widest unbreakable piece, a word or an em run whose spaces are no-break.
 * [maxPx] itself for a single line, which has nothing to balance and would only become two, and
 * where that piece is already wider than [maxPx].
 *
 * THE FLOOR IS CSS'S MIN-CONTENT WIDTH. Chrome never balances a block below its widest unbreakable
 * piece: it would overflow rather than break inside a word. Android's line breaker breaks inside
 * instead, often at the same line count, so a search from 1 px settled below the piece and split
 * it -- Date 08 B drew `didn't feel comforta` / `ble in the situation.` on every phone (SHOWUP-199,
 * 22 Nov 2026). Where the old search stayed above the floor, nothing moves.
 *
 * A binary search -- eight measurements rather than a scan's three hundred, because a Text
 * re-measures on every recomposition -- and one per word for the floor ([minContentPx]).
 */
fun balancedWidthPx(measurer: TextMeasurer, text: AnnotatedString, style: TextStyle, maxPx: Int): Int {
    if (maxPx <= 0) return maxPx
    fun linesAt(px: Int) = measurer.measure(text, style, constraints = Constraints(maxWidth = px.coerceAtLeast(1))).lineCount
    val target = linesAt(maxPx)
    if (target <= 1) return maxPx
    val minContent = minContentPx(measurer, text, style)
    if (minContent >= maxPx) return maxPx
    var low = minContent.coerceAtLeast(1)
    var high = maxPx
    while (low < high) {
        val mid = (low + high) / 2
        if (linesAt(mid) <= target) high = mid else low = mid + 1
    }
    return low
}

/**
 * CSS's min-content width of [text] in [style], in px: the narrowest width at which every
 * unbreakable piece ([wordsOf]) still lays out on one line -- the floor under [balancedWidthPx].
 *
 * LAID OUT, NOT READ OFF THE SIZE ALONE. A measured size is rounded up, and the line breaker can fit
 * a piece a pixel narrower: the Check-In gate's `start dating.` at 200 % measures 414 px and stays
 * whole at 413. Floored at the size, three headlines that split nothing moved a pixel (the 22 Nov
 * 2026 fit sweep); floored here, a width moves only where the old search split a word.
 */
internal fun minContentPx(measurer: TextMeasurer, text: AnnotatedString, style: TextStyle): Int {
    val sized = wordsOf(text).map { it to measurer.measure(it, style, softWrap = false).size.width }
    var floor = sized.maxOfOrNull { it.second } ?: return 0
    // A word no wider than `px` fits there; only the ones the rounding could have overstated are laid out.
    fun allWhole(px: Int) = sized.all { (word, w) ->
        w <= px || measurer.measure(word, style, constraints = Constraints(maxWidth = px)).lineCount == 1
    }
    while (floor > 1 && allWhole(floor - 1)) floor--
    return floor
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
