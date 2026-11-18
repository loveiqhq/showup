/*
 * BaselineWrap.kt
 * ShowUp · two items on one baseline that drop to their own lines when they do not fit
 *
 * The reference's `display: flex; align-items: baseline; flex-wrap: wrap; gap: N`. It was private to the Date
 * screen's time + place card; the suggestion card's value and its struck old value need the same row, so it
 * lives here (a shared primitive never stays in a screen file).
 */
package com.showup.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

/**
 * [first] and [second] on one baseline, [gap] apart, [second] dropping to its own line (also [gap] below) when the
 * two do not fit. A long [first] wraps inside the row rather than squeezing [second] or truncating. Text whose
 * line height is set should carry `cssLineBox`, so its FirstBaseline is the CSS one.
 */
@Composable
fun BaselineWrap(first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier, gap: Dp = 8.dp) {
    Layout(contents = listOf(first, second), modifier = modifier) { (l, v), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val firstP = l.first().measure(loose)
        // The second at most a full line wide: a name longer than that wraps inside it, as CSS's does.
        val secondP = v.first().measure(loose)
        val gapPx = gap.roundToPx()
        if (firstP.width + gapPx + secondP.width <= constraints.maxWidth) {
            val base = maxOf(firstP[FirstBaseline], secondP[FirstBaseline])
            val firstTop = base - firstP[FirstBaseline]
            val secondTop = base - secondP[FirstBaseline]
            layout(constraints.maxWidth, maxOf(firstTop + firstP.height, secondTop + secondP.height)) {
                firstP.place(0, firstTop)
                secondP.place(firstP.width + gapPx, secondTop)
            }
        } else {
            // `flex-wrap` with `gap`, which is also the row gap: the second on its own line.
            layout(constraints.maxWidth, firstP.height + gapPx + secondP.height) {
                firstP.place(0, 0)
                secondP.place(0, firstP.height + gapPx)
            }
        }
    }
}
