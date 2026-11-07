// EndDateSheet.swift
// ShowUp · F, the End the date sheet (SHOWUP-194), from `screen-chat-reference.jsx`, with its numbers.
// Android's `EndDateSheet.kt`.
//
// On the kit's `ModalShell`. `Keep the date` comes first and `End date` second, so a quick first tap never
// ends a date; the scrim and a swipe keep it.

import SwiftUI

/// Pad 14 24 24, centred: the dark-red 9% circle 80 with `x` 34 / 2.4, the title (Lora 700 26, 16 above, 8
/// below, at most 310), the line (15 / 1.5 muted, at most 300, 22 below), `Keep the date` (h 50, radius 16,
/// white with the soft border, 14.5 / 700 muted) and `End date` (10 below, h 56, radius 18, dark red, 16.5 /
/// 700 white). While it ends, `End date` waits; if it fails, the sheet says so and stays.
struct EndDateSheet: View {
    let name: String
    let ending: Bool
    let failed: Bool
    let onKeep: () -> Void
    let onEnd: () -> Void

    var body: some View {
        let tone = ReasonTone.darkRed
        let title = ChatCopy.sheetTitle(name)
        ModalShell(label: title, onDismiss: { _ in onKeep() },
                   contentPadding: EdgeInsets(top: 14, leading: 24, bottom: 24, trailing: 24)) {
            VStack(spacing: 0) {
                BrandIconView(icon: .close, size: 34, stroke: 2.4, tint: tone.fg)
                    .frame(width: 80, height: 80)
                    .background(Circle().fill(tone.bg))
                    .padding(.top, 8)
                    .accessibilityHidden(true)
                PrettyText(title, font: chatFont(PS.loraBold, 26, .bold), lineHeight: 26 * 1.15, color: .liqFg, centered: true)
                    .frame(maxWidth: 310)
                    .padding(.top, 16)
                    .accessibilityAddTraits(.isHeader)
                PrettyText(ChatCopy.sheetBody, font: chatFont(PS.manropeRegular, 15, .regular), lineHeight: 15 * 1.5,
                           color: .liqMuted, centered: true)
                    .frame(maxWidth: 300)
                    .padding(.top, 8)
                if failed {
                    Text(ChatCopy.endFailed).font(F.manrope(13.5, .medium)).foregroundColor(.liqDangerFg)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 300)
                        .padding(.top, 10)
                }
                Button(action: onKeep) {
                    Text(ChatCopy.keepTheDate).font(F.manrope(14.5, .bold)).foregroundColor(.liqMuted)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqElevated))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqBorderSoft, lineWidth: 1))
                }
                .buttonStyle(PressScale())
                .fitTapArea()
                .padding(.top, 22)
                Button(action: { if !ending { onEnd() } }) {
                    Text(ChatCopy.endDate).font(F.manrope(16.5, .bold)).foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 56)
                        .background(RoundedRectangle(cornerRadius: 18).fill(tone.fg.opacity(ending ? 0.6 : 1)))
                }
                .buttonStyle(PressScale())
                .fitTapArea()
                .padding(.top, 10)
            }
            .frame(maxWidth: .infinity)
        }
    }
}
