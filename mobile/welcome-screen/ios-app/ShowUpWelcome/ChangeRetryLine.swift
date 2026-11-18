// ChangeRetryLine.swift
// ShowUp · the red line that is also the retry button (Date 02 J, Date 05 L)
//
// One component for the failed message's `Couldn't send.` and the thread card's `Couldn't agree.`; Date 05's
// request card on the Date screen reuses it for the same line. `alert-circle` 14 / 2.4, gap 6, 12 / 600 / 1.35 in
// the danger ink, the sentence at most 280 wide, the line at least 44 high. It announces itself, as
// `role="alert"` does, and a tap anywhere on it retries. Android's `ChangeRetryLine.kt`.

import SwiftUI
import UIKit

struct ChangeRetryLine: View {
    let message: String
    /// The text aligns right, under a right-aligned bubble; false under a card's buttons.
    var trailing = false
    let onRetry: () -> Void

    var body: some View {
        Button(action: onRetry) {
            HStack(spacing: 6) {
                BrandIconView(icon: .alertCircle, size: 14, stroke: 2.4, tint: .liqDangerFg)
                // The last two words kept together for the reference's `text-wrap: pretty`, which `PrettyText`
                // cannot draw here (it has no right alignment).
                Text(lastWordsTogether(message))
                    .font(F.manrope(12, .semibold))
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeSemi, size: 12, multiple: 1.35))
                    .foregroundColor(.liqDangerFg)
                    .multilineTextAlignment(trailing ? .trailing : .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 2)
            .frame(maxWidth: 280, minHeight: 44, alignment: trailing ? .trailing : .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .accessibilityLabel(message)
        .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
    }
}
