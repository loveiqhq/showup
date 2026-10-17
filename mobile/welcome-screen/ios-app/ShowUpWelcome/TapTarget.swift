//  TapTarget.swift
//  ShowUp · a control has to be big enough to hit
//
//  WHY A MODIFIER AND NOT A COMPONENT
//
//  The two back controls in this app are not the same control and were deliberately not merged.
//  The phone screens draw a `chevron-left` icon at the top of the screen; the tutorial draws the
//  WORD "Back" in the bottom nav row beside NextButton, at a different colour, with a `showBack`
//  flag that hides it and takes it out of VoiceOver on card 01. They share an `onBack` closure and
//  nothing that is drawn.
//
//  A `BackControl` covering both would have been a component whose two variants share only a
//  closure. What they genuinely share is the RULE, so the rule is what got extracted.

import SwiftUI

extension View {
    /// Gives a control at least `ComponentSizes.minTapTarget` in both directions, and makes the
    /// whole of that area take the touch rather than only the glyph inside it.
    ///
    /// **The floor cannot be lowered, only raised.** `min` is clamped up to
    /// `ComponentSizes.minTapTarget`: asking for 48 gives 48, asking for 20 gives 44. A plain
    /// default would let somebody pass a smaller number and quietly reintroduce the defect this
    /// area exists to prevent.
    ///
    /// `minWidth`/`minHeight` rather than `width`/`height`, because the tutorial's label is wider
    /// than the icon and must be allowed to be. A `min` frame does not expand to the parent's
    /// proposal the way `maxWidth: .infinity` would, so a 24pt icon still measures 44 x 44.
    ///
    /// `contentShape(Rectangle())` is the half people forget: without it the padding around a glyph
    /// is transparent to touch, and the control is only as big as its ink no matter what frame it
    /// was given. Both call sites already had it; here it cannot be omitted.
    ///
    /// Alignment stays a parameter because one of these controls sits at the top of a screen and the
    /// other in a row at the bottom — but it defaults to `.leading`, which is what both use.
    func minTapTarget(_ min: CGFloat = ComponentSizes.minTapTarget,
                      alignment: Alignment = .leading) -> some View {
        let floor = Swift.max(min, ComponentSizes.minTapTarget)
        return frame(minWidth: floor, minHeight: floor, alignment: alignment)
            .contentShape(Rectangle())
    }
}

extension View {
    /// A control DRAWN smaller than the tap floor that still ANSWERS at it, without moving anything:
    /// the slot stays the drawn size, and a touch area of `ComponentSizes.minTapTarget` is laid over
    /// it, overflowing it symmetrically above and below.
    ///
    /// NOT `minTapTarget`, which GROWS the frame to 44 — right for a lone pill, wrong for Interests'
    /// 38 chips and Life's 34 pills in rows 8 apart, where a 44 frame would open every row by 6 or
    /// 10 and move every chip below the first. The Kotlin twin is `OverflowTapTarget`.
    ///
    /// From the MEASURED height, as the twin does: a control Dynamic Type has grown to 44 or more
    /// overflows by nothing. Until 13 October 2026 this worked from the design's drawn height, so a
    /// grown chip still reached 3 into the gap, a pill 5 and the toggle 9 — into the next row's.
    func overflowTapTarget() -> some View {
        OverflowTapLayout {
            self
            Color.clear.contentShape(Rectangle())
        }
    }
}

/// `overflowTapTarget`'s two layers: the control, reported at its own size, and a touch layer at
/// least `ComponentSizes.minTapTarget` tall centred on it. SwiftUI hit-tests a child outside its
/// parent's frame, so the overflow answers as long as nothing clips it.
private struct OverflowTapLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        subviews.first?.sizeThatFits(proposal) ?? .zero
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(bounds.size))
        let touch = Swift.max(bounds.height, ComponentSizes.minTapTarget)
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.midY - touch / 2), anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: touch))
    }
}
