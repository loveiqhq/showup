//  WelcomeView.swift
//  ShowUp · Onboarding 00 — Welcome screen (SHOWUP-117)
//
//  Faithful implementation of 00-welcome-spec-sheet.
//  Drop this file into the iOS app target and preview it in Xcode (see the folder README).
//  Fonts: the app should bundle Lora + Manrope; until then SwiftUI falls back to the system fonts.

import SwiftUI
import UIKit

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

private enum F {
    static func lora(_ size: CGFloat, bold: Bool = false, italic: Bool = false) -> Font {
        // .custom() falls back to the system font if the family isn't bundled yet.
        let name = bold ? (italic ? "Lora-BoldItalic" : "Lora-Bold")
                        : (italic ? "Lora-Italic" : "Lora-Regular")
        return .custom(name, size: size)
    }
    static func manrope(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        let name: String
        switch weight {
        case .bold, .heavy:  name = "Manrope-Bold"
        case .semibold:      name = "Manrope-SemiBold"
        case .medium:        name = "Manrope-Medium"
        default:             name = "Manrope-Regular"
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

private struct HeroHeart: View {
    var body: some View {
        ZStack {
            Image(systemName: "heart.fill")
                .resizable().scaledToFit()
                .frame(width: 178, height: 168)
                .foregroundStyle(LinearGradient(colors: [.liqOrange, Color(hex: 0xE0567A), .liqPurple],
                                                startPoint: .topLeading, endPoint: .bottomTrailing))
                .shadow(color: .liqPurple.opacity(0.25), radius: 22, y: 16)
            Circle().fill(Color(hex: 0xA877E6)).frame(width: 9).offset(x: -76, y: -58)
            Image(systemName: "sparkle").font(.system(size: 15))
                .foregroundColor(Color(hex: 0xF6C96A)).offset(x: 76, y: -52)
            Circle().fill(Color.liqOrange.opacity(0.7)).frame(width: 7).offset(x: 64, y: 40)
        }
        .frame(width: 200, height: 190)
    }
}

// MARK: - The screen

struct WelcomeView: View {
    /// Navigation into onboarding card 01 (wired by the caller).
    var onContinue: () -> Void = {}

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
                                "Lora-Bold", 42,
                                fallback: .systemFont(ofSize: 42, weight: .bold))),
                            ("Show Up.", TypeMetrics.uiFont(
                                "Lora-BoldItalic", 42,
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
                                TypeMetrics.uiFont("Manrope-Medium", 16,
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
                        // Analytics.track("cta_click", ["screen": "onboarding_welcome"])
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
            // Analytics.track("screen_view", ["screen": "onboarding_welcome"])
        }
    }
}

// Xcode shows this live in the Preview canvas (Editor ▸ Canvas).
#Preview {
    WelcomeView()
}
