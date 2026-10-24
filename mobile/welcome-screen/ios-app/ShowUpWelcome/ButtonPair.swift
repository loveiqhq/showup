//
//  ButtonPair.swift
//  ShowUp · two buttons that share a row while their labels fit, and stack when they do not
//
//  The Swift twin of `designsystem/ButtonPair.kt`. Search's sheets and coach draw their two buttons as
//  `flex: 1` halves of one row -- Cancel and Continue, Skip message and Send message, Don't show again
//  and Got it. At the largest type, and on a 320 phone even at the default, a half is narrower than
//  its label. So the pair sits side by side, equal halves exactly as drawn, whenever both labels fit a
//  half -- and stacks full width, in the same order, only when one does not. Nothing is ever cut.
//

import SwiftUI

/// The side padding a paired button is given: enough to keep its label off the pill's ends.
let pairButtonPadding: CGFloat = 10

struct ButtonPair: Layout {
    var gap: CGFloat = 10

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? (subviews[0].sizeThatFits(.unspecified).width * 2 + gap)
        let (a, b) = (subviews[0], subviews[1])
        if sideBySide(a, b, width: width) {
            let half = ProposedViewSize(width: halfWidth(width), height: nil)
            return CGSize(width: width, height: max(a.sizeThatFits(half).height, b.sizeThatFits(half).height))
        }
        let full = ProposedViewSize(width: width, height: nil)
        return CGSize(width: width, height: a.sizeThatFits(full).height + gap + b.sizeThatFits(full).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let (a, b) = (subviews[0], subviews[1])
        if sideBySide(a, b, width: bounds.width) {
            let w = halfWidth(bounds.width)
            let half = ProposedViewSize(width: w, height: nil)
            a.place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: half)
            b.place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing, proposal: half)
        } else {
            let full = ProposedViewSize(width: bounds.width, height: nil)
            let first = a.sizeThatFits(full).height
            a.place(at: CGPoint(x: bounds.minX, y: bounds.minY), proposal: full)
            b.place(at: CGPoint(x: bounds.minX, y: bounds.minY + first + gap), proposal: full)
        }
    }

    private func halfWidth(_ width: CGFloat) -> CGFloat { max(0, (width - gap) / 2) }

    /// A label "fits" when its button's one-line width does: the button's ideal size.
    private func sideBySide(_ a: LayoutSubview, _ b: LayoutSubview, width: CGFloat) -> Bool {
        let half = halfWidth(width)
        return a.sizeThatFits(.unspecified).width <= half && b.sizeThatFits(.unspecified).width <= half
    }
}
