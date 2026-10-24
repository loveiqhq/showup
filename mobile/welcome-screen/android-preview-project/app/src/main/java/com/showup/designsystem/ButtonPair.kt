/*
 * ButtonPair.kt
 * ShowUp · two buttons that share a row while their labels fit, and stack when they do not
 *
 * Search's sheets and coach draw their two buttons as `flex: 1` halves of one row -- Cancel and
 * Continue, Skip message and Send message, Don't show again and Got it. At the largest system font,
 * and on a 320 phone even at the default, a half is narrower than its label and the label was CUT
 * (the Search fit sweep, 22 Oct 2026: 127 clipped labels). So this lays the pair side by side, equal
 * halves exactly as drawn, whenever both labels fit a half -- and stacks them full width, in the
 * same order, only when one does not. Nothing is ever cut and nothing is ever squeezed.
 *
 * The buttons are passed a compact side padding (see [PAIR_BUTTON_PADDING]) and centre their label,
 * so a pair with room to spare looks exactly like the drawn one.
 */
package com.showup.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The side padding a paired button is given: enough to keep its label off the pill's ends. */
val PAIR_BUTTON_PADDING = 10.dp

@Composable
fun ButtonPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
) {
    Layout(contents = listOf(first, second), modifier = modifier) { (a, b), constraints ->
        val w = constraints.maxWidth
        val space = gap.roundToPx()
        val half = ((w - space) / 2).coerceAtLeast(0)
        val first0 = a.first()
        val second0 = b.first()
        // A label "fits" when its button's single-line width does: the button's own intrinsic.
        val sideBySide = first0.maxIntrinsicWidth(Constraints.Infinity) <= half &&
            second0.maxIntrinsicWidth(Constraints.Infinity) <= half
        if (sideBySide) {
            val pa = first0.measure(Constraints.fixedWidth(half))
            val pb = second0.measure(Constraints.fixedWidth(half))
            val h = maxOf(pa.height, pb.height)
            layout(w, h) {
                pa.place(0, (h - pa.height) / 2)
                pb.place(w - half, (h - pb.height) / 2)
            }
        } else {
            val pa = first0.measure(Constraints.fixedWidth(w))
            val pb = second0.measure(Constraints.fixedWidth(w))
            layout(w, pa.height + space + pb.height) {
                pa.place(0, 0)
                pb.place(0, pa.height + space)
            }
        }
    }
}
