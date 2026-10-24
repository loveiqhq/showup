//
//  Chip.swift
//  ShowUp · the kit's `Chip` (`components/shared.jsx`): a read-only pill for a vibe, an interest or a tag
//
//  The Swift twin of `designsystem/Chip.kt`. NOT the profile flow's picker chip, which is a radio or a
//  toggle with a tap area and a press scale: this one shows a value. Search draws every vibe and
//  interest of someone else's profile with it, and marks the shared ones.
//
//  Sizes, tones and weights are the kit's:
//    xs 26 / 11.5 / 10 / 5 · sm 30 / 13 / 12 / 6 · md 36 / 14 / 14 / 8   (height / type / side / gap)
//    orange   idle #fff + 1px --liq-border       selected rgba(254,104,57,.10) + 1px orange, ink orange
//    lavender idle #fff + 1px --liq-border       selected violet, ink white
//    soft     idle --liq-bg-raised, no border   selected violet, ink white
//
//  The height is a MINIMUM: at the largest type the label grows, and a label wider than its row wraps
//  onto a second line rather than being cut off (the Search fit sweep, 22 Oct 2026).
//

import SwiftUI

enum ChipTone: Sendable { case orange, lavender, soft }

enum ChipSize: Sendable {
    case xs, sm, md

    var height: CGFloat { switch self { case .xs: return 26; case .sm: return 30; case .md: return 36 } }
    var fontSize: CGFloat { switch self { case .xs: return 11.5; case .sm: return 13; case .md: return 14 } }
    var side: CGFloat { switch self { case .xs: return 10; case .sm: return 12; case .md: return 14 } }
    var gap: CGFloat { switch self { case .xs: return 5; case .sm: return 6; case .md: return 8 } }
}

struct Chip<Leading: View>: View {
    let label: String
    var selected = false
    var tone: ChipTone = .orange
    var size: ChipSize = .md
    /// Drawn before the label, in the chip's ink -- the shared chip's check.
    @ViewBuilder var leading: (Color) -> Leading

    var body: some View {
        let (fill, ink, stroke) = palette
        HStack(spacing: size.gap) {
            leading(ink)
            Text(label)
                .font(F.manrope(size.fontSize, .semibold))
                .foregroundColor(ink)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, size.side)
        // A wrapped label keeps a little air above and below; one line is centred in the minimum.
        .padding(.vertical, 4)
        .frame(minHeight: size.height)
        .background(Capsule().fill(fill))
        .overlay(Capsule().strokeBorder(stroke, lineWidth: 1))
    }

    private var palette: (Color, Color, Color) {
        switch (tone, selected) {
        // `rgba(254,104,57,.10)`, the orange tone's selected fill.
        case (.orange, true): return (Color.liqOrange.opacity(0.10), .liqOrange, .liqOrange)
        case (.soft, false): return (.liqRaised, .liqFg, .clear)
        case (_, true): return (.liqPurple, .liqElevated, .liqPurple)
        default: return (.liqElevated, .liqFg, .liqBorder)
        }
    }
}

extension Chip where Leading == EmptyView {
    init(_ label: String, selected: Bool = false, tone: ChipTone = .orange, size: ChipSize = .md) {
        self.init(label: label, selected: selected, tone: tone, size: size, leading: { _ in EmptyView() })
    }
}
