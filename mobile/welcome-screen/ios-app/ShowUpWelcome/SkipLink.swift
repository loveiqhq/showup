//
//  SkipLink.swift
//  ShowUp · the underlined "skip this step" control (SHOWUP-161)
//
//  From `components/shared.jsx`'s `SkipLink`: Manrope 700 / 13.5, `--liq-primary-500`, underlined
//  with a 1.5 thickness at a 3 offset. Its canonical label is `Skip for now`, which is the default
//  here for the same reason it is the default there — a skip control that says something different
//  on each screen is four skip controls.
//
//  SHOWUP-161 is its first user, so this is a new primitive rather than an extraction. It lives in
//  the design system rather than in the media screen because the ticket calls it "the shared
//  SkipLink" and because the next optional step will want it — and the rule this project already
//  paid for is that the second caller never finds a component hidden in another screen's file.
//
//  IT IS NOT A `PrimaryButton` VARIANT. That is a 48-or-56-tall filled pill; this is underlined text
//  with no box at all. A variant that dropped the height, the fill, the border, the shadow and the
//  press scale would be a different component wearing the same name.
//

import SwiftUI

struct SkipLink: View {
    var label: String = "Skip for now"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(F.manrope(13.5, .bold))
                .foregroundColor(.liqPurple)
                .underline()
                // The text is about 17pt tall and the floor is 44. It sits at the LEADING edge of a
                // footer row with a large circular button at the other end, so there is nothing for
                // a grown frame to collide with.
                .minTapTarget(alignment: .leading)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(label))
    }
}
