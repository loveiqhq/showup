// SheetErrorBox.swift
// ShowUp · what a sheet's action could not do, inside the sheet above its buttons (SHOWUP-194 K, 9 Nov 2026;
// the reference names Date 03's no-location error as the same box). Android's `SheetErrorBox.kt`.
//
// NOT `InlineErrorCard`: the profile flow's card is 7% / 18%, radius 12, a filled glyph and the sentence in the
// danger ink -- the date flow's sheets draw this one.

import SwiftUI
import UIKit

/// The danger 8% box under a 1 pt danger 20% rule, radius 14, pad 11 13, `alert-circle` 17 / 2.2 in the danger
/// ink 1 down, gap 9, and the sentence 13.5 / 600 / 1.4 in the ink. It announces itself, as `role="alert"` does.
struct SheetErrorBox: View {
    let message: String

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        HStack(alignment: .top, spacing: 9) {
            BrandIconView(icon: .alertCircle, size: 17, stroke: 2.2, tint: .liqDangerFg)
                .padding(.top, 1)
                .accessibilityHidden(true)
            PrettyText(message, font: TypeMetrics.uiFont(PS.manropeSemi, 13.5, fallback: .systemFont(ofSize: 13.5, weight: .semibold)),
                       lineHeight: 13.5 * 1.4, color: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(EdgeInsets(top: 11, leading: 13, bottom: 11, trailing: 13))
        .background(shape.fill(Color.liqDanger.opacity(0.08)))
        .overlay(shape.strokeBorder(Color.liqDanger.opacity(0.20), lineWidth: 1))
        .accessibilityElement(children: .combine)
        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
    }
}
