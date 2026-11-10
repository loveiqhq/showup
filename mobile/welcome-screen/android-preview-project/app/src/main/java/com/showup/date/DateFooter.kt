/*
 * DateFooter.kt
 * ShowUp · the Date screen's sticky footer slot, over the TabBar (SHOWUP-193 ⑯ ⑰)
 *
 * ONE CONTAINER, swapped on `checkedIn`: the Check-in bar's place from the match until check-in, the End
 * Date bar's after. This ticket builds the slot and the swap only (9 Nov 2026: "Footer bars are not built
 * here ... do not take them from the reference file"): the Check-in bar arrives with Date 03 and its
 * final strings, the End Date bar with Date 06, and until then the slot draws nothing -- "a slot with
 * nothing built ships empty, not as a placeholder". The TabBar is the shared one, in its disabled state.
 */
package com.showup.date

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val DATE_FOOTER_BAR_TAG = "date:footer-bar"

/** ⑯ The slot for [checkedIn]: [checkInBar] before check-in, [endDateBar] after, in one container. */
@Composable
internal fun FooterSlot(
    checkedIn: Boolean,
    checkInBar: @Composable () -> Unit,
    endDateBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().testTag(DATE_FOOTER_BAR_TAG)) {
        if (checkedIn) endDateBar() else checkInBar()
    }
}
