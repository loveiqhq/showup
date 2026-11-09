// DateFooter.swift
// ShowUp · the Date screen's sticky footer slot, over the TabBar (SHOWUP-193 ⑯ ⑰). Android's `DateFooter.kt`.
//
// ONE slot, swapped on `checkedIn`: the Check-in bar's place from the match until check-in, the End Date bar's
// after. This ticket builds the slot and the swap only (9 Nov 2026: "Footer bars are not built here ... do not
// take them from the reference file"): the Check-in bar arrives with Date 03 and its final strings, the End
// Date bar with Date 06, and until then the slot draws nothing -- "a slot with nothing built ships empty, not
// as a placeholder".

import SwiftUI

/// ⑯ The slot for `checkedIn`: `checkInBar` before check-in, `endDateBar` after, in one container. Empty, it is
/// a line of no height -- still there for the screen's measurements to find.
struct FooterSlot: View {
    let checkedIn: Bool
    let checkInBar: () -> AnyView
    let endDateBar: () -> AnyView

    var body: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 0)
            if checkedIn { endDateBar() } else { checkInBar() }
        }
        .frame(maxWidth: .infinity)
        .fitAnchor("date:footer-bar")
    }
}
