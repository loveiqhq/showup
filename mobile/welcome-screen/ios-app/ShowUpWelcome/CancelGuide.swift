// CancelGuide.swift
// ShowUp · the two thread items at the foot of the chat (SHOWUP-194): `CancelGuide` (D) and `EndDateCard`
// (E), from `screen-chat-reference.jsx`, with its numbers. Android's `CancelGuide.kt`.
//
// Neither is a message: the guide is local to this phone and never sent, and the card is drawn for both
// people once an "I can't make it today" message is in the thread.

import SwiftUI

/// The guide's and the card's paragraphs: 12.5 / 1.38, muted.
private let itemSize: CGFloat = 12.5

/// D. Waiting for `Got it`: the dark-red 9% card with its 28% border 1.5, radius 16, pad 10 12, gap 5 -- the
/// frown tile 26 / 8, the title 14.5 / 700 in dark red, the two paragraphs with their bold runs, and `Got it`
/// full width, at least 40 (answering at 44), 3 below the text. After it: one centred line, `check` 12 / 2.6
/// and the reminder, 12 / 600 subtle.
struct CancelGuide: View {
    let name: String
    let acked: Bool
    let onAck: () -> Void

    var body: some View {
        let tone = ReasonTone.darkRed
        if acked {
            HStack(spacing: 6) {
                BrandIconView(icon: .check, size: 12, stroke: 2.6, tint: .liqSubtle)
                Text(ChatCopy.guideAcked).font(F.manrope(12, .semibold)).foregroundColor(.liqSubtle)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        } else {
            let shape = RoundedRectangle(cornerRadius: 16)
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    IconTile(size: 26, radius: 8, fill: Color.liqElevated, icon: .frown, iconSize: 14, tint: tone.fg, stroke: 2.3)
                    Text(ChatCopy.guideTitle).font(F.manrope(14.5, .bold)).foregroundColor(tone.fg)
                        .fixedSize(horizontal: false, vertical: true)
                }
                paragraph(ChatCopy.guide1(name), ChatCopy.guide1Bold)
                paragraph(ChatCopy.guide2, ChatCopy.guide2Bold)
                Button(action: onAck) {
                    Text(ChatCopy.gotIt).font(F.manrope(14.5, .bold)).foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(Capsule().fill(tone.fg))
                }
                .buttonStyle(PressScale())
                .minTapTarget(alignment: .center)
                .fitTapArea()
                .fitAnchor("chat:got-it")
                .padding(.top, 3)
            }
            // Pad 10 12 plus the 1.5 border, which CSS counts in the box.
            .padding(EdgeInsets(top: 11.5, leading: 13.5, bottom: 11.5, trailing: 13.5))
            .background(shape.fill(tone.bg))
            .overlay(shape.strokeBorder(tone.chipBorder, lineWidth: 1.5))
            .fitAnchor("chat:guide")
        }
    }

    private func paragraph(_ text: String, _ bold: [String]) -> some View {
        PrettyText(runs: boldRuns(text, bold), font: chatFont(PS.manropeRegular, itemSize, .regular),
                   lineHeight: itemSize * 1.38, color: .liqMuted,
                   emphasisFont: chatFont(PS.manropeBold, itemSize, .bold), emphasisColor: .liqFg)
    }
}

/// E. `Not meeting today?` and the line beside `End date`: ink 4% fill, radius 16, pad 11 13, gap 10; the
/// button white with the dark-red chip border 1.5, at least 40 (answering at 44), pad 0 14, 13.5 / 700.
struct EndDateCard: View {
    let onEnd: () -> Void

    var body: some View {
        let tone = ReasonTone.darkRed
        HStack(spacing: 10) {
            PrettyText(runs: [(ChatCopy.cardLead, true), (" " + ChatCopy.cardBody, false)],
                       font: chatFont(PS.manropeRegular, itemSize, .regular), lineHeight: itemSize * 1.38,
                       color: .liqMuted, emphasisFont: chatFont(PS.manropeBold, itemSize, .bold), emphasisColor: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onEnd) {
                Text(ChatCopy.endDate).font(F.manrope(13.5, .bold)).foregroundColor(tone.fg).lineLimit(1).fixedSize()
                    .padding(.horizontal, 15.5)
                    .frame(minHeight: 40)
                    .background(Capsule().fill(Color.liqElevated))
                    .overlay(Capsule().strokeBorder(tone.chipBorder, lineWidth: 1.5))
            }
            .buttonStyle(PressScale())
            .overflowTapTarget()
        }
        .padding(EdgeInsets(top: 11, leading: 13, bottom: 11, trailing: 13))
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqFg.opacity(0.04)))
    }
}
