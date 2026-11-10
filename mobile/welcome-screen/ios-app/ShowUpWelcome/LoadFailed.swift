// LoadFailed.swift
// ShowUp · a screen's first load failed (the Date screen, SHOWUP-193, and the chat, SHOWUP-194 I; 9 Nov 2026).
// Android's `LoadFailed.kt`.
//
// NOT Search's `CentredStatement`, a ghost button and no tile: "the same pattern as Search 01" is the
// behaviour -- the sentence and Try again in place of the content -- and both references draw this.

import SwiftUI
import UIKit

/// Centred in what it is given, gap 14, pad 0 32: the violet 10% tile 56 / r18 with `refresh` 24 / 2.1, the
/// sentence 15 / 1.45 / 500 muted at most 290, and `Try again`, the kit's primary md button (flat orange under
/// the CTA glow, h 48, pad 22, 15 / 700) 4 further down, as wide as its label.
///
/// Centred where it fits, and scrolling where it does not: in the top half of a split screen at the largest type
/// the column is taller than the room, and Try again was cut (the 9 Nov 2026 Android fit sweep). Inside a scroll
/// of its own -- the Date screen's -- the first always fits.
///
/// `busy`: the load is on its way again. The sentence stays and Try again does nothing until it answers
/// ("while it loads, the button is inert"); a screen that blanked meanwhile would flash.
struct LoadFailed: View {
    let text: String
    let action: String
    let onAction: () -> Void
    var busy = false

    var body: some View {
        ViewThatFits(in: .vertical) {
            column.frame(maxWidth: .infinity, maxHeight: .infinity)
            ScrollView { column.frame(maxWidth: .infinity) }
        }
    }

    private var column: some View {
        VStack(spacing: 14) {
            BrandIconView(icon: .refresh, size: 24, stroke: 2.1, tint: .liqPurple)
                .frame(width: 56, height: 56)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color.liqPurple.opacity(0.10)))
                .accessibilityHidden(true)
            PrettyText(text, font: TypeMetrics.uiFont(PS.manropeMedium, 15, fallback: .systemFont(ofSize: 15, weight: .medium)),
                       lineHeight: 15 * 1.45, color: .liqMuted, centered: true)
                .frame(maxWidth: 290)
            PrimaryButton(action, variant: .orange, height: 48, labelSize: 15, horizontalPadding: 22,
                          action: { if !busy { onAction() } })
                .fixedSize(horizontal: true, vertical: false)
                .padding(.top, 4)
        }
        .padding(.horizontal, 32)
    }
}
