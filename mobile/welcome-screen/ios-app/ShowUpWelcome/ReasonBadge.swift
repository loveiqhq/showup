// ReasonBadge.swift
// ShowUp · the reference's `ReasonBadge`, built once (SHOWUP-194, Date 02)
//
// The Date screen's unread card and the chat's bubbles both draw it. `onLight` is the reference's own
// switch: the tone's tint on a light panel, white over the tone's own fill (a sent bubble). Android's
// `ReasonBadge.kt`.

import SwiftUI

/// Uppercase reason label with its icon, in the tone's tint: pad 3 9 3 7, gap 5, Manrope 800 10.5.
struct ReasonBadge: View {
    let reason: ChatReason
    let onLight: Bool

    var body: some View {
        let tone = reason.tone
        HStack(spacing: 5) {
            BrandIconView(icon: reason.icon, size: 11, stroke: 2.5, tint: onLight ? tone.fg : .white)
            // One line wherever it fits; at the largest type on a narrow phone it wraps rather than runs
            // past the panel.
            Text(reason.label.uppercased())
                .font(F.manrope(10.5, .heavy))
                .tracking(0.05)
                .foregroundColor(onLight ? tone.fg : .white)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(EdgeInsets(top: 3, leading: 7, bottom: 3, trailing: 9))
        .background(Capsule().fill(onLight ? tone.bg : Color.white.opacity(0.22)))
    }
}
