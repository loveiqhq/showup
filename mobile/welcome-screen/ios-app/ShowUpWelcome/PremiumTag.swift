//
//  PremiumTag.swift
//  ShowUp · the kit's `PremiumTag` (`screens-premium.jsx`), the label a Premium user's top bar carries
//
//  The Swift twin of `designsystem/PremiumTag.kt`. Built with the Premium grid (SHOWUP-185), which puts
//  it in the Search top bar at `size={10}`, and used again by the Likes tab (SHOWUP-186). The kit file
//  is not in the handoff folder; its source was read from the spec sheets' own bundle.
//
//  At size s: padding `s/2  s  s/2  0.82s`, gap `0.55s`, the sunset, Manrope 800 at s with
//  `letter-spacing: 0.06` (React writes a number as px) and capitals; `--liq-shadow-violet` under it, a
//  1px inner highlight along the top (`inset 0 1px 0 rgba(255,255,255,.55)`), a soft violet inner shade
//  along the bottom (`inset 0 -1px 2px rgba(129,42,236,.35)`), and the glossy top-half sheen
//  (`linear-gradient(180deg, rgba(255,255,255,.45) 0%, rgba(255,255,255,.08) 42%, transparent 60%)`).
//  The sparkles icon is `s + 2` at stroke 2.4.
//
//  A LABEL, NOT A CONTROL (Search 02: "PremiumTag is a label, not a control"). VoiceOver reads the word
//  as written, `Premium`, not the capitals it is drawn in.
//

import SwiftUI

struct PremiumTag: View {
    /// The kit's `size`: 10 in the Search top bar.
    var size: CGFloat = 11

    var body: some View {
        HStack(spacing: size * 0.55) {
            // `filter: drop-shadow(0 1px 1px rgba(129,42,236,.4))`: the icon again, 1 lower, violet,
            // softened by its 1px blur.
            ZStack {
                BrandIconView(icon: .sparkles, size: size + 2, stroke: 2.4, tint: Color.liqPurple.opacity(0.4))
                    .offset(y: 1)
                    .blur(radius: 0.5)
                BrandIconView(icon: .sparkles, size: size + 2, stroke: 2.4, tint: .white)
            }
            Text(PremiumCopy.premiumTag.uppercased())
                .font(F.manrope(size, .heavy))
                .tracking(0.06)
                .foregroundColor(.white)
                .lineLimit(1)
                .fixedSize()
                .cssLineBox(size)
        }
        .padding(.leading, size * 0.82)
        .padding(.trailing, size)
        .padding(.vertical, size * 0.5)
        .background(surface)
        .background(CapsuleVioletGlow())
        // A mark, like the wordmark: it keeps its drawn proportions at every type size, and its one
        // word is read out in full.
        .dynamicTypeSize(.large)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(PremiumCopy.premiumTag))
    }

    private var surface: some View {
        ZStack {
            Capsule().fill(Gradients.sunset())
            Capsule().fill(LinearGradient(stops: [
                .init(color: Color.white.opacity(0.45), location: 0),
                .init(color: Color.white.opacity(0.08), location: 0.42),
                .init(color: .clear, location: 0.60),
            ], startPoint: .top, endPoint: .bottom))
            // Each inset shadow is the pill minus itself moved by the shadow's offset: the sliver along
            // the edge the shadow falls on.
            ZStack {
                Capsule().fill(Color.white.opacity(0.55))
                Capsule().offset(y: 1).blendMode(.destinationOut)
            }
            .compositingGroup()
            ZStack {
                Capsule().fill(Color.liqPurple.opacity(0.35))
                Capsule().offset(y: -1).blendMode(.destinationOut)
            }
            .compositingGroup()
            // A 2px CSS blur is a sigma of 1.
            .blur(radius: 1)
        }
        .clipShape(Capsule())
    }
}

/// `--liq-shadow-violet` under a pill: `0 8px 20px` at .28 and `0 2px 6px` at .18, each layer on its
/// own capsule, so neither is a shadow of the other. Behind the sunset Like pill and the Premium tag.
struct CapsuleVioletGlow: View {
    var body: some View {
        ZStack {
            Capsule().fill(Color.liqPurple).shadow(color: Color.liqPurple.opacity(0.28), radius: 10, y: 8)
            Capsule().fill(Color.liqPurple).shadow(color: Color.liqPurple.opacity(0.18), radius: 3, y: 2)
        }
    }
}

#Preview("Premium tag · 10 and 11") {
    HStack(spacing: 16) {
        PremiumTag(size: 10)
        PremiumTag()
    }
    .padding(24)
    .background(Color.liqCream)
}
