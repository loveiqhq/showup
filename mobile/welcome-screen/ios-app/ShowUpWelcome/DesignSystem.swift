//  DesignSystem.swift
//  ShowUp · shared colour, type and text-metric helpers
//
//  Extracted from WelcomeView.swift when the second tutorial screen arrived. Two files in one
//  target cannot both write `extension Color { static let liqOrange }` — that is a redeclaration
//  error, not a warning. One definition, used everywhere.
//
//  Values come from the spec sheets, which carry colour and type tokens only. There is deliberately
//  no spacing scale: the raw px values in each screen are what design specified, not an oversight.

import SwiftUI
import UIKit

// MARK: - Colour

extension Color {
    static let liqCream    = Color(hex: 0xFFFBF7)                    // screen background
    static let liqOrange   = Color(hex: 0xFE6839)                    // accents, dots, Next circle
    static let liqPurple   = Color(hex: 0x812AEC)                    // progress fill, consequences
    static let liqFg       = Color(hex: 0x1D1129)                    // primary text
    static let liqNeutral  = Color(hex: 0x4B3B5A)                    // body text
    static let liqSubtle   = Color(hex: 0x1D1129).opacity(0.46)      // captions, Back label
    static let liqFaint    = Color(hex: 0x1D1129).opacity(0.24)      // the " — " dash in rule rows
    static let liqTrack    = Color(hex: 0x1D1129).opacity(0.12)      // unfilled progress segments
    static let liqEyebrowBg = Color(hex: 0xA78BFA).opacity(0.16)     // eyebrow pill fill

    // Added for the welcome & sign-up flow (SHOWUP-140/142/143). Verbatim from
    // design_handoff_showup/tokens/colors_and_type.css, the authoritative token file.
    static let liqElevated   = Color(hex: 0xFFFFFF)                  // input and slot fills
    static let liqMuted      = Color(hex: 0x1D1129).opacity(0.62)    // --liq-fg-muted
    static let liqBorder     = Color(hex: 0x1D1129).opacity(0.12)    // --liq-border
    static let liqDanger     = Color(hex: 0xFB323B)                  // invalid borders, the ! glyph
    static let liqDangerFg   = Color(hex: 0xB71F26)                  // error helper text
    static let liqDangerDigit = Color(hex: 0x7A1F26)                 // mismatch digits - a one-off
    static let liqLavender   = Color(hex: 0xA78BFA)

    init(hex: UInt) {
        self.init(.sRGB,
                  red:   Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8)  & 0xFF) / 255,
                  blue:  Double( hex        & 0xFF) / 255,
                  opacity: 1)
    }
}

// MARK: - Fonts
//
// The PostScript names of the bundled files — NOT the filenames, and not always what you would
// guess. `Lora-Italic.ttf` reports itself as "LoraItalic-Italic"; asking for "Lora-Italic" fails
// silently to the system font, which is exactly the kind of bug nobody notices. Verify with:
//     for f in UIFont.familyNames.sorted() { print(f, UIFont.fontNames(forFamilyName: f)) }

enum PS {
    static let loraRegular    = "Lora-Regular"
    static let loraBold       = "Lora-Bold"
    static let loraItalic     = "LoraItalic-Italic"
    static let loraBoldItalic = "LoraItalic-BoldItalic"
    static let manropeMedium  = "Manrope-Medium"
    static let manropeSemi    = "Manrope-SemiBold"
    static let manropeBold    = "Manrope-Bold"
}

enum F {
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

// MARK: - Exact line-height
//
// The spec states line-heights as multipliers (34 / 1.1, 16 / 1.5). SwiftUI's `.lineSpacing()` only
// ADDS to the font's own leading, so it can never produce a line-height TIGHTER than the font
// already has — and Lora's natural leading is roughly 1.2. A paragraph style can set line height
// directly, so the spec numbers go through here instead.
//
// Re-check visually once Lora/Manrope are actually bundled; until then the system fallback has
// different metrics.

enum TypeMetrics {
    static func uiFont(_ name: String, _ size: CGFloat, fallback: UIFont) -> UIFont {
        UIFont(name: name, size: size) ?? fallback
    }

    static func italicSystem(_ size: CGFloat, weight: UIFont.Weight = .bold) -> UIFont {
        let base = UIFont.systemFont(ofSize: size, weight: weight)
        let desc = base.fontDescriptor.withSymbolicTraits(.traitItalic) ?? base.fontDescriptor
        return UIFont(descriptor: desc, size: size)
    }

    /// Builds text whose line height is exactly `size * multiple`, across one or more styled runs.
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

    /// Same, but each run carries its own colour — used by the tutorial rule rows.
    static func attributed(
        colouredRuns: [(String, UIFont, UIColor)],
        size: CGFloat,
        multiple: CGFloat,
        trackingEm: CGFloat = 0
    ) -> AttributedString {
        let target = size * multiple
        let para = NSMutableParagraphStyle()
        para.lineHeightMultiple = target / (colouredRuns.first?.1.lineHeight ?? target)

        let out = NSMutableAttributedString()
        for (text, font, colour) in colouredRuns {
            out.append(NSAttributedString(string: text, attributes: [
                .font: font,
                .foregroundColor: colour,
                .paragraphStyle: para,
                .kern: trackingEm * size,
                .baselineOffset: (target - font.lineHeight) / 4,
            ]))
        }
        return AttributedString(out)
    }
}
