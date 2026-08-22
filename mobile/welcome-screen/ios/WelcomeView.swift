//  WelcomeView.swift
//  ShowUp · Tutorial card 1 — Welcome screen (SHOWUP-117)
//
//  Faithful implementation of 00-welcome-spec-sheet.
//  Drop this file into the iOS app target and preview it in Xcode (see the folder README).
//
//  Fonts: Lora + Manrope ship in ../fonts/. Add the .ttf files to the target and list them under
//  UIAppFonts in Info.plist — see the folder README. The heart is drawn in code (HeartShape), so
//  there is no asset to export and nothing to keep in sync at 2x/3x.

import SwiftUI
import UIKit

// MARK: - Analytics
//
// A seam, not an integration. Real code rather than a commented-out reminder: a comment claiming
// something is tracked is indistinguishable from tracking that works, and this project has already
// been bitten by that more than once.
//
// The default does nothing, and will keep doing nothing until there is somewhere to send events —
// the backend's analytics module has no ingest endpoint yet, and consent capture is not built, so
// AnalyticsService.track() would discard anything sent today. When both land, inject a real
// implementation here and this screen needs no further change.

public protocol AnalyticsTracking {
    func track(_ event: String, properties: [String: Any])
}

public struct NoOpAnalytics: AnalyticsTracking {
    public init() {}
    public func track(_ event: String, properties: [String: Any]) {}
}

/// Names and properties for the tutorial flow.
///
/// "Tutorial", deliberately not "onboarding" — onboarding is the separate flow where someone fills
/// in their profile. Conflating them makes the funnel unreadable later.
///
/// Names follow the backend taxonomy: snake_case, `<noun>_<verb-ed>`, snake_case properties, no
/// free text and no personal data. Card number is a property rather than part of the event name so
/// the remaining cards reuse these two events instead of inventing ten more.
public enum TutorialAnalytics {
    public static let cardViewed = "tutorial_card_viewed"
    public static let ctaTapped  = "tutorial_cta_tapped"

    /// Card 1 of the tutorial. The spec sheet file is named `00-welcome-spec-sheet`, but the
    /// ticket's tracking section names this screen "Tutorial 1 - Welcome", so it is card 1.
    public static let card = 1
    public static let cardName = "welcome"

    public static var properties: [String: Any] {
        ["card": card, "card_name": cardName]
    }
}


// MARK: - Exact line-height
//
// The spec states line-heights as multipliers (headline 1.05, body 1.5). SwiftUI's `.lineSpacing()`
// only ADDS to the font's own leading, so it can never produce a line-height TIGHTER than the font
// already has — and Lora's natural leading is roughly 1.2. Using `.lineSpacing(2)` for a 1.05 spec
// therefore renders ~18% too loose, which is what this replaces.
//
// A paragraph style can set line height directly, so the spec numbers go through here instead.
// Re-check this visually once Lora/Manrope are actually bundled; until then the system fallback
// has different metrics.
private enum TypeMetrics {
    static func uiFont(_ name: String, _ size: CGFloat, fallback: UIFont) -> UIFont {
        UIFont(name: name, size: size) ?? fallback
    }

    /// Builds text whose line height is exactly `size * multiple`, with optional italic run.
    static func attributed(
        runs: [(String, UIFont)],
        size: CGFloat,
        multiple: CGFloat,
        color: UIColor,
        trackingEm: CGFloat = 0
    ) -> AttributedString {
        let target = size * multiple
        let para = NSMutableParagraphStyle()
        // lineHeightMultiple scales rather than clamps, so ascenders are not clipped the way
        // min/maximumLineHeight alone can clip them.
        para.lineHeightMultiple = target / (runs.first?.1.lineHeight ?? target)

        let out = NSMutableAttributedString()
        for (text, font) in runs {
            out.append(NSAttributedString(string: text, attributes: [
                .font: font,
                .foregroundColor: color,
                .paragraphStyle: para,
                .kern: trackingEm * size,
                // standard correction for the shift lineHeightMultiple introduces
                .baselineOffset: (target - font.lineHeight) / 4,
            ]))
        }
        return AttributedString(out)
    }
}

// MARK: - ① Design tokens (from the spec sheet)

extension Color {
    static let liqCream   = Color(hex: 0xFFFBF7)   // screen background
    static let liqOrange  = Color(hex: 0xFE6839)   // sunset start · dot · heart icon
    static let liqPurple  = Color(hex: 0x812AEC)   // sunset end · "Up"
    static let liqFg      = Color(hex: 0x1D1129)   // primary text
    static let liqNeutral = Color(hex: 0x4B3B5A)   // body text
    static let liqSubtle  = Color(hex: 0x1D1129).opacity(0.46) // caption

    init(hex: UInt) {
        self.init(.sRGB,
                  red:   Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8)  & 0xFF) / 255,
                  blue:  Double( hex        & 0xFF) / 255,
                  opacity: 1)
    }
}

// MARK: - Fonts (swap these names once Lora/Manrope are bundled)

// The PostScript names of the bundled files — NOT the filenames, and not always what you would
// guess. `Lora-Italic.ttf` reports itself as "LoraItalic-Italic"; asking for "Lora-Italic" fails
// silently to the system font, which is exactly the kind of bug nobody notices. Verify with:
//     for f in UIFont.familyNames.sorted() { print(f, UIFont.fontNames(forFamilyName: f)) }
private enum PS {
    static let loraRegular    = "Lora-Regular"
    static let loraBold       = "Lora-Bold"
    static let loraItalic     = "LoraItalic-Italic"
    static let loraBoldItalic = "LoraItalic-BoldItalic"
    static let manropeMedium  = "Manrope-Medium"
    static let manropeSemi    = "Manrope-SemiBold"
    static let manropeBold    = "Manrope-Bold"
}

private enum F {
    static func lora(_ size: CGFloat, bold: Bool = false, italic: Bool = false) -> Font {
        let name = bold ? (italic ? PS.loraBoldItalic : PS.loraBold)
                        : (italic ? PS.loraItalic : PS.loraRegular)
        return .custom(name, size: size)
    }
    static func manrope(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy:  name = PS.manropeBold
        case .semibold:      name = PS.manropeSemi
        default:             name = PS.manropeMedium
        }
        return .custom(name, size: size)
    }
}

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

        // Curve fitted to the spec sheet's own silhouette, measured rather than eyeballed:
        // aspect 1.035, widest 30% down, cleft 12.5% deep, and — the one that matters — the two
        // lobe peaks sitting 65.8% of the width apart. The earlier path had them only 39.6%
        // apart, which is what made the top read flat instead of as two lobes.
        var p = Path()
        p.move(to: pt(100, 172.6))
        p.addCurve(to: pt(20, 64.4),    control1: pt(86.6, 157.3), control2: pt(18.9, 115.2))
        p.addCurve(to: pt(47.3, 18),    control1: pt(19.8, 45),    control2: pt(41.3, 18))
        p.addCurve(to: pt(100, 37.3),   control1: pt(79, 18.9),    control2: pt(98.2, 25.6))
        p.addCurve(to: pt(152.7, 18),   control1: pt(101.8, 25.6), control2: pt(121, 18.9))
        p.addCurve(to: pt(180, 64.4),   control1: pt(158.7, 18),   control2: pt(180.2, 45))
        p.addCurve(to: pt(100, 172.6),  control1: pt(181.1, 115.2), control2: pt(113.4, 157.3))
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
                .frame(width: 50, height: 30)
                .rotationEffect(.degrees(-18))
                .offset(x: -30, y: -40)

            // floating accents — offsets from the centre of the 200 × 190 box. The silhouette is
            // wider than before, so these moved outward to stay clear of it.
            Circle().fill(Color(hex: 0xA877E6)).frame(width: 9).offset(x: -85, y: -63)
            Circle().fill(Color.liqOrange.opacity(0.7)).frame(width: 7).offset(x: 72, y: 23)
            SparkleShape().fill(Color(hex: 0xFBBF4B))
                .frame(width: 24, height: 26).offset(x: 80, y: -62)
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
                                        properties: TutorialAnalytics.properties)
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
                            properties: TutorialAnalytics.properties)
        }
    }
}

// Xcode shows this live in the Preview canvas (Editor ▸ Canvas).
#Preview {
    WelcomeView()
}
