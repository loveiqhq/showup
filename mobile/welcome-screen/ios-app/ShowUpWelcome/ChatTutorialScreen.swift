// ChatTutorialScreen.swift
// ShowUp · A, "Must read — how messaging works" (SHOWUP-194), from `screen-chat-reference.jsx`. Android's
// `ChatTutorialScreen.kt`.
//
// Before the first open of each date's chat, once per person. Two steps and no third. Header, one scroll,
// and `Open the chat` pinned under it -- the only way forward; back returns to the Date screen and the
// tutorial shows again next time.

import SwiftUI

struct ChatTutorialScreen: View {
    let name: String
    var onOpen: () -> Void = {}
    var onBack: () -> Void = {}

    var body: some View {
        ZStack(alignment: .top) {
            Color.liqCream.ignoresSafeArea()
            sunsetWash
            VStack(spacing: 0) {
                // Back and the context line: pad 4 12 8, gap 8; 13 / 700 muted.
                HStack(spacing: 8) {
                    ChatBackButton(onBack: onBack)
                    Text(ChatCopy.tutorialHeader(name)).font(F.manrope(13, .bold)).foregroundColor(.liqMuted).lineLimit(1)
                    Spacer(minLength: 0)
                }
                .padding(EdgeInsets(top: 4, leading: 12, bottom: 8, trailing: 12))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // The hero: Lora 700 30, −0.02em, 1.12, `Must read` washed; 4 above, 8 below.
                        WashHeadline(parts: [(ChatCopy.tutorialHeadlineItalic, true), (ChatCopy.tutorialHeadlineRest, false)],
                                     fontSize: 30, lineHeightMultiple: 1.12, trackingEm: -0.02)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 4)
                        intro.padding(.top, 8)
                        step(1, icon: .navigation, tint: Color.liqOrange.opacity(0.12), color: .liqOrange,
                             title: ChatCopy.step1Title, body: ChatCopy.step1Body, bold: ChatCopy.step1Bold)
                            .padding(.top, 22)
                        examples.padding(.leading, 53).padding(.top, 14)
                        step(2, icon: .eyeOff, tint: Color.liqPurple.opacity(0.10), color: .liqPurple,
                             title: ChatCopy.step2Title, body: ChatCopy.step2Body, bold: ChatCopy.step2Bold)
                            .padding(.top, 20)
                    }
                    .padding(EdgeInsets(top: 4, leading: 20, bottom: 26, trailing: 20))
                }
                // The pinned CTA: pad 12 16 16 on the glass, `Open the chat` h 54 with the orange glow, and the
                // privacy line 10 below.
                VStack(spacing: 10) {
                    PrimaryButton(label: ChatCopy.openTheChat, height: 54, labelSize: 16, glow: shadowCta, action: onOpen) {
                        BrandIconView(icon: .messageCircle, size: 19, stroke: 2.2, tint: .white)
                    }
                    LockLine(text: ChatCopy.tutorialPrivate)
                }
                .padding(EdgeInsets(top: 12, leading: 16, bottom: 16, trailing: 16))
                .modifier(PinnedGlass())
            }
        }
    }

    /// The intro box: violet 8% with a 16% border, radius 14, pad 12 14 (plus the 1 px border CSS counts).
    private var intro: some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        return PrettyText(runs: boldRuns(ChatCopy.tutorialIntro, ChatCopy.tutorialIntroBold),
                          font: chatFont(PS.manropeRegular, 14.5, .regular), lineHeight: 14.5 * 1.5, color: .liqMuted,
                          emphasisFont: chatFont(PS.manropeBold, 14.5, .bold), emphasisColor: .liqFg)
            .padding(EdgeInsets(top: 13, leading: 15, bottom: 13, trailing: 15))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(shape.fill(Color.liqPurple.opacity(0.08)))
            .overlay(shape.strokeBorder(Color.liqPurple.opacity(0.16), lineWidth: 1))
    }

    /// The tile 40 / 13 with its icon 20 / 2.2 and the step number on a 20 white disc, 1.5 ringed, 6 out at
    /// the top right; beside it (13 away) the title, Lora 700 17 / 1.2, and the line, 13.5 / 1.5 muted.
    private func step(_ n: Int, icon: BrandIcon, tint: Color, color: Color, title: String, body: String, bold: [String]) -> some View {
        HStack(alignment: .top, spacing: 13) {
            IconTile(size: 40, radius: 13, fill: tint, icon: icon, iconSize: 20, tint: color, stroke: 2.2)
                .overlay(alignment: .topTrailing) {
                    Text("\(n)").font(F.lora(12, bold: true)).foregroundColor(color)
                        .dynamicTypeSize(.large)
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(Color.liqElevated))
                        .overlay(Circle().strokeBorder(color, lineWidth: 1.5))
                        .offset(x: 6, y: -6)
                        .accessibilityHidden(true)
                }
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(F.lora(17, bold: true)).foregroundColor(.liqFg)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                PrettyText(runs: boldRuns(body, bold), font: chatFont(PS.manropeRegular, 13.5, .regular),
                           lineHeight: 13.5 * 1.5, color: .liqMuted,
                           emphasisFont: chatFont(PS.manropeBold, 13.5, .bold), emphasisColor: .liqFg)
            }
            .padding(.top, 1)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// `Examples:` and the two example chips, one line where it fits, wrapping where it does not.
    private var examples: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { examplesLabel; exampleChip(ChatCopy.exampleArrived, .navigation); exampleChip(ChatCopy.exampleLate, .hourglass) }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) { examplesLabel; exampleChip(ChatCopy.exampleArrived, .navigation) }
                exampleChip(ChatCopy.exampleLate, .hourglass)
            }
        }
    }

    private var examplesLabel: some View {
        Text(ChatCopy.examples).font(F.manrope(11.5, .bold)).foregroundColor(.liqSubtle).fixedSize()
    }

    /// An example chip, not a button: pad 7 12 7 10 (plus the 1 px border), white, the orange chip border,
    /// 12.5 / 700 orange, icon 14 / 2.4.
    private func exampleChip(_ label: String, _ icon: BrandIcon) -> some View {
        let tone = ReasonTone.orange
        return HStack(spacing: 6) {
            BrandIconView(icon: icon, size: 14, stroke: 2.4, tint: tone.fg)
            Text(label).font(F.manrope(12.5, .bold)).foregroundColor(tone.fg).lineLimit(1).fixedSize()
        }
        .padding(EdgeInsets(top: 8, leading: 11, bottom: 8, trailing: 13))
        .background(Capsule().fill(Color.liqElevated))
        .overlay(Capsule().strokeBorder(tone.chipBorder, lineWidth: 1))
    }

    /// The sunset wash at the top: the reference's 260 band at 16% blurred by 40, drawn soft-edged -- the same
    /// band Android draws, faded down a longer box the way the blur spreads it.
    private var sunsetWash: some View {
        Gradients.sunset()
            .opacity(0.16)
            .frame(height: 300)
            .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: 0.55),
                                         .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom))
            .ignoresSafeArea(edges: .top)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
