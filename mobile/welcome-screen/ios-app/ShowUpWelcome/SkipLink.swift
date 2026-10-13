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
//  THE RULE IS DRAWN, NOT `.underline()`. That draws the font's own underline — about 0.34 thick,
//  0.7 under the baseline — a hairline hugging the text, where the reference's is 1.5 thick and
//  starts 3 below the baseline (`text-underline-offset` is measured from the alphabetic baseline).
//  Found 13 October 2026; until then this header described a rule the code did not draw. The rule
//  sits under the LAST line, which is the only line at every size the footer row allows (it stacks
//  first — `SkipContinueRow`).
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
                .overlay(alignment: Alignment(horizontal: .leading, vertical: .lastTextBaseline)) {
                    // Its top edge 3 below the baseline: the guide it aligns by is 3 above its top.
                    Rectangle()
                        .fill(Color.liqPurple)
                        .frame(height: 1.5)
                        .alignmentGuide(.lastTextBaseline) { d in d[.top] - 3 }
                        .accessibilityHidden(true)
                }
                // The text is about 17pt tall and the floor is 44. It sits at the LEADING edge of a
                // footer row with a large circular button at the other end, so there is nothing for
                // a grown frame to collide with.
                .minTapTarget(alignment: .leading)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(label))
    }
}
