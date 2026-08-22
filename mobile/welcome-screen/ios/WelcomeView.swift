//  WelcomeView.swift
//  ShowUp · Tutorial card 1 — Welcome screen (SHOWUP-117)
//
//  Faithful implementation of 00-welcome-spec-sheet.
//  Drop this file into the iOS app target and preview it in Xcode (see the folder README).
//
//  Tokens, fonts, text metrics and the analytics seam now live in shared/ — two files in one
//  target cannot both declare Color.liqOrange. The heart is drawn in code (HeartShape), so there
//  is no asset to export and nothing to keep in sync at 2x/3x.

import SwiftUI
import UIKit

// MARK: - ⑧ Reusable "sunset / lg" button

struct SunsetButton: View {
    let title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Text(title).font(F.manrope(17, .bold))
                Image(systemName: "arrow.right").font(.system(size: 20, weight: .bold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)                                   // size lg
            .background(LinearGradient(colors: [.liqOrange, .liqPurple],
                                       startPoint: .leading, endPoint: .trailing))
            .clipShape(Capsule())                                // pill
            .shadow(color: .liqPurple.opacity(0.45), radius: 18, y: 12)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - ② Background: cream base + peach top fade + two blurred orbs

private struct WelcomeBackground: View {
    var body: some View {
        ZStack {
            Color.liqCream
            LinearGradient(colors: [Color.liqOrange.opacity(0.14), .clear],
                           startPoint: .top, endPoint: .center)
            // Orbs are anchored to the screen CORNERS, not offset from the centre. A centre-relative
            // y offset lands somewhere different on a 667-tall frame than on a 932-tall one; corner
            // anchoring keeps them where the design puts them on every device.
            Circle().fill(Color.liqOrange.opacity(0.32))         // orange · top-right
                .frame(width: 340).blur(radius: 70)
                .offset(x: 104, y: -132)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)

            Circle().fill(Color.liqPurple.opacity(0.28))         // purple · bottom-left
                .frame(width: 400).blur(radius: 82)
                .offset(x: -140, y: 150)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .ignoresSafeArea()
    }
}

// MARK: - ④ Hero heart (200 × 190, gradient orange → purple)

/// The design's heart, drawn from its own curves in a 200 × 190 space and scaled to fit whatever
/// box it is given. Drawn rather than exported as a PNG so it is resolution-independent, identical
/// to the Android path, and cannot go stale against the design.
private struct HeartShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width / 200, rect.height / 190)
        let dx = (rect.width - 200 * s) / 2
        let dy = (rect.height - 190 * s) / 2
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: dx + x * s, y: dy + y * s) }

        // Traced from the spec sheet's actual outline, not fitted to its width profile — many
        // different shapes share a width profile, and the earlier attempt produced flat-topped
        // lobes with hard corners. These control points come from a least-squares fit against the
        // real contour (RMS 0.24 and 0.16 design units).
        //
        // The apex tangent is horizontal on both sides — incoming (8.4, 0.4), outgoing (16, 0) —
        // which is what makes each lobe a smooth dome instead of a flat top meeting a corner.
        var p = Path()
        p.move(to: pt(99.8, 172.9))
        p.addCurve(to: pt(20, 63.7),    control1: pt(78, 152.1),    control2: pt(17.9, 111.3))
        p.addCurve(to: pt(66.6, 17.1),  control1: pt(22.3, 22.2),   control2: pt(58.2, 16.7))
        p.addCurve(to: pt(100, 36.6),   control1: pt(82.6, 17.1),   control2: pt(97, 27.6))
        p.addCurve(to: pt(133.4, 17.1), control1: pt(103, 27.6),    control2: pt(117.4, 17.1))
        p.addCurve(to: pt(180, 63.7),   control1: pt(141.8, 16.7),  control2: pt(177.7, 22.2))
        p.addCurve(to: pt(99.8, 172.9), control1: pt(182.1, 111.3), control2: pt(122, 152.1))
        p.closeSubpath()
        return p
    }
}

/// Four-point sparkle. Also drawn — SF Symbols has no star of this shape.
private struct SparkleShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w / 2, y: 0))
        p.addLine(to: CGPoint(x: w * 0.58, y: h * 0.42))
        p.addLine(to: CGPoint(x: w,        y: h / 2))
        p.addLine(to: CGPoint(x: w * 0.58, y: h * 0.58))
        p.addLine(to: CGPoint(x: w / 2,    y: h))
        p.addLine(to: CGPoint(x: w * 0.42, y: h * 0.58))
        p.addLine(to: CGPoint(x: 0,        y: h / 2))
        p.addLine(to: CGPoint(x: w * 0.42, y: h * 0.42))
        p.closeSubpath()
        return p
    }
}

private struct HeroHeart: View {
    var body: some View {
        ZStack {
            // soft peach glow behind the heart
            Ellipse()
                .fill(RadialGradient(colors: [.liqOrange.opacity(0.26), .liqOrange.opacity(0)],
                                     center: .center, startRadius: 0, endRadius: 96))
                .frame(width: 192, height: 176)

            HeartShape()
                .fill(LinearGradient(colors: [.liqOrange, Color(hex: 0xE8565E), Color(hex: 0x9333D9)],
                                     startPoint: UnitPoint(x: 0.18, y: 0.06),
                                     endPoint:   UnitPoint(x: 0.82, y: 0.96)))
                .frame(width: 200, height: 190)
                .shadow(color: .liqPurple.opacity(0.25), radius: 22, y: 16)

            // highlight on the upper-left lobe
            Ellipse().fill(Color.white.opacity(0.17))
                .frame(width: 48, height: 30)
                .rotationEffect(.degrees(-18))
                .offset(x: -34, y: -43)

            // floating accents — offsets from the centre of the 200 × 190 box. The silhouette is
            // wider than before, so these moved outward to stay clear of it.
            Circle().fill(Color(hex: 0xA877E6)).frame(width: 9).offset(x: -87, y: -55)
            Circle().fill(Color.liqOrange.opacity(0.7)).frame(width: 7).offset(x: 62, y: 35)
            SparkleShape().fill(Color(hex: 0xFBBF4B))
                .frame(width: 22, height: 24).offset(x: 86, y: -67)
        }
        .frame(width: 200, height: 190)
    }
}

// MARK: - The screen

struct WelcomeView: View {
    /// Navigation into tutorial card 2 (wired by the caller).
    var onContinue: () -> Void = {}

    /// Swap in a real tracker once there is an endpoint to send to.
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        ZStack {
            WelcomeBackground()

            // Nested flex columns — SwiftUI Spacers are the two `flex: 1` spacers.
            VStack(alignment: .leading, spacing: 0) {

                // ③ Wordmark — top-anchored (safe area + 20)
                (Text("Show ").foregroundColor(.liqFg)
                 + Text("Up").foregroundColor(.liqPurple).italic()
                 + Text(".").foregroundColor(.liqOrange))
                    .font(F.lora(26))
                    .padding(.top, 20)

                Spacer(minLength: 8)                               // flex:1

                // ④⑤⑥⑦ Heart + text cluster
                VStack(alignment: .leading, spacing: 16) {         // text gaps 16
                    HeroHeart()
                        .frame(maxWidth: .infinity, alignment: .center)  // heart centred
                        .padding(.bottom, 4)

                    // ⑤ Headline — line-height 1.05 set absolutely (see TypeMetrics), italic
                    //    "Show Up.", plus the underline accent the spec calls for.
                    Text(TypeMetrics.attributed(
                        runs: [
                            ("Welcome\nto ", TypeMetrics.uiFont(
                                PS.loraBold, 42,
                                fallback: .systemFont(ofSize: 42, weight: .bold))),
                            ("Show Up.", TypeMetrics.uiFont(
                                PS.loraBoldItalic, 42,
                                fallback: UIFont(
                                    descriptor: UIFont.systemFont(ofSize: 42, weight: .bold)
                                        .fontDescriptor.withSymbolicTraits(.traitItalic)
                                        ?? UIFont.systemFont(ofSize: 42, weight: .bold).fontDescriptor,
                                    size: 42))),
                        ],
                        size: 42,
                        multiple: 1.05,
                        color: UIColor(Color.liqFg),
                        trackingEm: -0.02
                    ))
                    .overlay(alignment: .bottomLeading) {           // ⑤ underline accent
                        Capsule()
                            .fill(LinearGradient(
                                colors: [.liqOrange.opacity(0), .liqOrange.opacity(0.55),
                                         Color(hex: 0xE0567A).opacity(0.45), .liqPurple.opacity(0)],
                                startPoint: .leading, endPoint: .trailing))
                            .frame(width: 210, height: 12)
                            .blur(radius: 5)
                            .offset(y: 6)
                            .allowsHitTesting(false)
                    }

                    HStack(spacing: 8) {                            // ⑥ subhead
                        Text("We're happy to see you").font(F.manrope(18, .semibold)).foregroundColor(.liqFg)
                        Image(systemName: "heart.fill").font(.system(size: 20)).foregroundColor(.liqOrange)
                    }

                    // ⑦ Body — 16 / 1.5, set the same absolute way as the headline so the two
                    //    platforms agree (Compose expresses this directly as lineHeight = 24.sp).
                    Text(TypeMetrics.attributed(
                        runs: [("Let us quickly explain how Show Up works.",
                                TypeMetrics.uiFont(PS.manropeMedium, 16,
                                                   fallback: .systemFont(ofSize: 16, weight: .medium)))],
                        size: 16,
                        multiple: 1.5,
                        color: UIColor(Color.liqNeutral)
                    ))
                    .frame(maxWidth: 320, alignment: .leading)
                }

                Spacer(minLength: 8)                               // flex:1

                // ⑧⑨ Button + caption — bottom-anchored (20 above floor)
                VStack(spacing: 10) {                              // button → caption 10
                    SunsetButton(title: "Show me how") {
                        analytics.track(TutorialAnalytics.ctaTapped,
                                        properties: TutorialAnalytics.welcome)
                        onContinue()
                    }
                    Text("Takes less than a minute")
                        .font(F.manrope(12, .semibold)).foregroundColor(.liqSubtle)
                }
                .padding(.bottom, 20)
            }
            .padding(.horizontal, 24)                              // gutter 24
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed,
                            properties: TutorialAnalytics.welcome)
        }
    }
}

// Xcode shows this live in the Preview canvas (Editor ▸ Canvas).
#Preview {
    WelcomeView()
}
