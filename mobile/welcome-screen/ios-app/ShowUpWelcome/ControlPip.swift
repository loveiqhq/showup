//
//  ControlPip.swift
//  ShowUp · the small pill-shaped secondary action (SHOWUP-161)
//
//  `Retake` and `Delete` on both filled media cards. One component with two tones, because the
//  acceptance criteria require it in as many words — "`Retake` and `Delete` are the shared 32px
//  ControlPips — neutral and danger tones — IDENTICAL ON BOTH CARDS" — and because a second copy is
//  how the video card's Delete and the voice card's Delete drift apart.
//
//  It lives here rather than in a screen file for the reason `PillButton` taught this project the
//  hard way. See `SheetScaffold.swift`, which was extracted the same afternoon.
//
//  NOT A `PrimaryButton` VARIANT. That component is a 56-tall full-width CTA with six variants and
//  two slots; this is a 32-tall bordered pill that sits in a row of two. They share no geometry, no
//  type and no role, and forcing one into the other would mean a variant that ignores most of its
//  own parameters.
//

import SwiftUI

/// Which of the two pills this is. Delete is destructive and says so in red.
enum ControlPipTone { case neutral, danger }

struct ControlPip: View {
    // Argument order mirrors the stored properties, because Swift's memberwise init is positional
    // and a call site that reorders them will not compile. See audit/check-swift-arg-order.py.
    let icon: BrandIcon
    let label: String
    var tone: ControlPipTone = .neutral
    let action: () -> Void

    /// The drawn height. The touch area is `ComponentSizes.minTapTarget` — see below.
    private let pipHeight: CGFloat = 32

    private var content: Color { tone == .danger ? .liqDangerFg : .liqFg }
    private var outline: Color { tone == .danger ? Color.liqDanger.opacity(0.30) : .liqBorder }

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                BrandIconView(icon: icon, size: 13, stroke: 2, tint: content)
                Text(label)
                    .font(F.manrope(12.5, .bold))
                    .foregroundColor(content)
                    // A PILL'S LABEL NEVER WRAPS. Squeezed into a narrow card the layout would
                    // rather break "Delete" over several lines than let the pill overflow, which
                    // the Android fit harness caught at 320 wide. The pill keeps its intrinsic
                    // width and the row around it is what yields.
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, Spacing.xl)
            .frame(height: pipHeight)
            .background(Color.white, in: Capsule())
            .overlay(Capsule().strokeBorder(outline, lineWidth: 1))
            // DRAWS 32 AND ANSWERS AT 44, through the same helper the sheet's close X uses.
            //
            // On iOS the frame GROWS to the floor rather than overflowing its slot, which is the
            // established approach here and the reason the two pips cannot collide: a wider frame
            // takes room in the HStack instead of spilling into its neighbour. The Kotlin twin
            // overflows a fixed 32 slot vertically and had to be restricted to one axis for
            // exactly that reason -- the platforms differ in mechanism and agree on the result.
            //
            // The pill is already wider than 44, so the minimum width changes nothing.
            .minTapTarget(alignment: .center)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(label))
    }
}
