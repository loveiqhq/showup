/*
 * CheckInFlexBody.kt
 * ShowUp · a Check-In step body: a header block over the ONE flexible region, with a floor
 *
 * Every step from 02 to 05 is the same column: a header block (`flex: none` -- headline, subline or
 * note, and on Vibe the counter) over one region that is `flex: 1, min-height: 0, overflow-y: auto`
 * (the grid, the motivation list, the chip groups, the filter list). The region takes whatever height
 * is left and scrolls inside itself; the header never moves.
 *
 * THE FLOOR. CSS has no answer for a header taller than the screen: open How it works on a 375 x 667,
 * or set the system font to 2x on a 320, and `flex: 1` has nothing left to give -- the region goes to
 * zero and what follows spills. The fit sweep found exactly that: Availability's summary and legend
 * collapsed to zero height under an open panel, Vibe's counter clipped at 2x. So the region never
 * gets less than [REGION_FLOOR]; when the header leaves less than that, the header and the region
 * scroll TOGETHER -- the region still scrolling inside itself -- and what sits below this body (the
 * summary, the legend, the footer) stays where it is. On every frame where the header leaves room,
 * which is every ticket frame at the default type, nothing changes: the header is still, and only the
 * region scrolls.
 */
package com.showup.checkin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The least the flexible region is ever given: two slot rows, or a motivation row and a half. */
val REGION_FLOOR: Dp = 80.dp

/**
 * @param header the step's fixed block, laid out at its natural height.
 * @param region the one flexible element: it is given an exact height and must fill it, scrolling
 *   inside itself.
 */
@Composable
fun CheckInFlexBody(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    region: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        // The viewport, read OUTSIDE the scroll: inside it the height is unbounded.
        val viewport = constraints.maxHeight
        val floor = REGION_FLOOR
        Layout(
            contents = listOf({ Column(Modifier.fillMaxWidth()) { header() } }, { Box { region() } }),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { (headerPart, regionPart), c ->
            val head = headerPart.first().measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val regionHeight = maxOf(floor.roundToPx(), viewport - head.height)
            val body = regionPart.first().measure(
                Constraints(minWidth = c.minWidth, maxWidth = c.maxWidth, minHeight = regionHeight, maxHeight = regionHeight),
            )
            layout(c.maxWidth, head.height + regionHeight) {
                head.place(0, 0)
                body.place(0, head.height)
            }
        }
    }
}
