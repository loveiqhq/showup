// PartnerEndedRoute.swift
// ShowUp · Date 08 as a destination: one view per showing, and no way out but `Proceed to review` (SHOWUP-199, R6,
// R7). Android's `PartnerEndedRoute.kt`.

import SwiftUI

/// The screen with its destination's duties. No back button and no navigation bar; the edge swipe goes with the hidden
/// back button, and the host's path refuses any pop off this route (`canGoBack`), so there is nothing to go back to and
/// nowhere else to go: the date is over and the person owes a review. It is pushed, never presented, so there is no
/// swipe-down either; `interactiveDismissDisabled` keeps that true wherever it is hosted. There is no tab bar.
///
/// `onShown` runs once per showing (`onFirstAppear`): a redraw is not a second view, a new push -- the next launch's --
/// is.
struct PartnerEndedRoute: View {
    let screen: PartnerScreen
    let name: String
    let photoURL: URL?
    let onShown: () -> Void
    let onProceed: () -> Void

    var body: some View {
        PartnerEndedScreen(screen: screen, name: name, photoURL: photoURL, onProceed: onProceed)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarBackButtonHidden(true)
            .interactiveDismissDisabled(true)
            .onFirstAppear(perform: onShown)
    }
}
