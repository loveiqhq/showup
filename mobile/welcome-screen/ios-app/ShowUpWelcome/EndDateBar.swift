// EndDateBar.swift
// ShowUp · A, the End Date bar (SHOWUP-197), from `DateStatusBar` in `screen-wrap-report-reference.jsx`. Android's
// `EndDateBar.kt`.
//
// The footer slot of the Date screen once the person has checked in. ONE state: the kit's "Date complete · Browsing's
// unlocked · Rate" variant is deleted, because Search stays blocked until the review.
//
// NO BACKDROP BLUR. `rgba(255,251,247,.96)` + `blur(20px)` is composited here: the tint at 96% over the cream, opaque,
// so what scrolls behind the bar never shows through it (the 193 rule for glass bars).

import SwiftUI

/// Padding 10 14 under a 1 pt `border-top` (the soft border), the row centred, gap 12: the calendar tile (38, radius
/// 12, violet 10%, `calendar` 19 / 2.1), the text (13.5 / 700 over 11.5 / 1.35 muted, pretty) and `End Date` (h 40,
/// pad 0 16, pill, white, 1.5 violet border, 14 / 700, nowrap), drawn at 40 and answering at 44.
struct EndDateBar: View {
    let name: String
    let onEnd: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            BrandIconView(icon: .calendar, size: 19, stroke: 2.1, tint: .liqPurple)
                .frame(width: 38, height: 38)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.liqPurple.opacity(0.10)))
                .fitAnchor(FitAnchorID.endBarTile)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(DateEndCopy.barTitle(name))
                    .font(F.manrope(13.5, .bold)).foregroundColor(.liqFg)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)
                PrettyText(DateEndCopy.barLine, font: chatFont(PS.manropeRegular, 11.5, .regular),
                           lineHeight: 11.5 * 1.35, color: .liqMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onEnd) {
                Text(DateEndCopy.barButton)
                    .font(F.manrope(14, .bold)).foregroundColor(.liqPurple)
                    .lineLimit(1)
                    .fixedSize()
                    // Pad 0 16 plus the 1.5 border CSS counts in the width.
                    .padding(.horizontal, 17.5)
                    .frame(minHeight: 40)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(Color.liqPurple, lineWidth: 1.5))
                    .overflowTapTarget(wide: true)
            }
            .buttonStyle(PressScale())
            .fitAnchor(FitAnchorID.endBarButton)
        }
        // 10 14, and the 1 pt rule above the content: CSS counts a border in the box.
        .padding(EdgeInsets(top: 11, leading: 14, bottom: 10, trailing: 14))
        .frame(maxWidth: .infinity)
        .background {
            ZStack {
                Color.liqCream
                Color(hex: 0xFFFBF7).opacity(0.96)
            }
        }
        .overlay(alignment: .top) { Rectangle().fill(Color.liqBorderSoft).frame(height: 1) }
        .fitAnchor(FitAnchorID.endBar)
    }
}

#Preview("End Date bar") {
    VStack {
        Spacer()
        EndDateBar(name: "Leonie", onEnd: {})
    }
    .background(Color.liqCream)
}
