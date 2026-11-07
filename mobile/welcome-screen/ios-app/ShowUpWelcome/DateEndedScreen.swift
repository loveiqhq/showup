// DateEndedScreen.swift
// ShowUp · G and H, after a date is ended in the chat (SHOWUP-194): the reference's `ScreenPartnerCancelled`
// with its `viaChat` variant -- G when you ended it, H when the match did. Android's `DateEndedScreen.kt`.
//
// Date 07 extends this component for Cancel this date; it never forks it. No review follows and nobody's
// Show-up Rate moves; `Find your next date` opens Search.

import SwiftUI

/// The hero atmosphere; pad 4 20 0. The lilac card (radius 28, pad 26 24 24, centred): the avatar 72 with the
/// orange `x` badge 28 (white 3 ring, the md shadow) at its corner, the headline (Lora 700 26, −0.01em, 1.15,
/// balanced, the one word washed), and the line (14 / 1.55 muted). Then the one row (14 below): white, the
/// soft border, radius 14, pad 13, the shield tile 34 / 11 in green, the line 13 / 1.42. The flexible space,
/// then `Find your next date` with `search` 18 / 2.4. It scrolls only when it must.
struct DateEndedScreen: View {
    let outcome: EndedInChat
    var photoURL: URL?
    var onFindNext: () -> Void = {}

    var body: some View {
        let g = outcome.endedByYou
        let name = outcome.name
        ZStack {
            Color.liqCream.ignoresSafeArea()
            WelcomeBackdrop(peachWash: false, placement: .atmosphere, orangeAlpha: 0.26, violetAlpha: 0.22)
                .ignoresSafeArea()
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 0) {
                        hero(g: g, name: name)
                        row(g: g, name: name).padding(.top, 14)
                        Spacer(minLength: 12)
                        PrimaryButton(label: ChatCopy.findNext, action: onFindNext) {
                            BrandIconView(icon: .search, size: 18, stroke: 2.4, tint: .white)
                        }
                        .padding(.bottom, 6)
                    }
                    .padding(EdgeInsets(top: 4, leading: 20, bottom: 0, trailing: 20))
                    .frame(minHeight: geo.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
        }
    }

    private func hero(g: Bool, name: String) -> some View {
        VStack(spacing: 0) {
            MatchPhoto(name: name, photoURL: photoURL, size: 72)
                .overlay(alignment: .bottomTrailing) {
                    BrandIconView(icon: .close, size: 15, stroke: 2.6, tint: .white)
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(Color.liqOrange))
                        .padding(3)
                        .background(ShadowedShape(shape: Circle(), fill: Color.white, shadows: shadowMd))
                        .offset(x: 3, y: 3)
                        .accessibilityHidden(true)
                }
            WashHeadline(
                parts: g ? [(ChatCopy.gHeadlineLead, false), (ChatCopy.gHeadlineItalic, true), (ChatCopy.gHeadlineRest, false)]
                         : [(ChatCopy.hHeadlineLead(name), false), (ChatCopy.hHeadlineItalic, true), (ChatCopy.hHeadlineRest, false)],
                fontSize: 26, lineHeightMultiple: 1.15, trackingEm: -0.01, balance: true, centered: true, heading: true)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 16)
            PrettyText(runs: boldRuns(g ? ChatCopy.gBody(name) : ChatCopy.hBody, ChatCopy.outcomeBold),
                       font: chatFont(PS.manropeRegular, 14, .regular), lineHeight: 14 * 1.55, color: .liqMuted,
                       emphasisFont: chatFont(PS.manropeBold, 14, .bold), emphasisColor: .liqFg, centered: true)
                .padding(.top, 12)
        }
        .padding(EdgeInsets(top: 26, leading: 24, bottom: 24, trailing: 24))
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 28).fill(Gradients.lilac()))
    }

    private func row(g: Bool, name: String) -> some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        return HStack(spacing: 12) {
            IconTile(size: 34, radius: 11, fill: Color.liqSuccess.opacity(0.12), icon: .shieldCheck, iconSize: 17,
                     tint: .liqSuccessFg, stroke: 2)
            PrettyText(runs: boldRuns(g ? ChatCopy.gRow(name) : ChatCopy.hRow, ChatCopy.outcomeBold),
                       font: chatFont(PS.manropeRegular, 13, .regular), lineHeight: 13 * 1.42, color: .liqFg,
                       emphasisFont: chatFont(PS.manropeBold, 13, .bold), emphasisColor: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        // Pad 13 plus the 1 px border, which CSS counts in the box.
        .padding(14)
        .background(shape.fill(Color.liqElevated))
        .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
    }
}
