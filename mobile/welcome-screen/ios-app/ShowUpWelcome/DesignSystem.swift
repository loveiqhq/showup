//  DesignSystem.swift
//  ShowUp · shared colour, type and text-metric helpers
//
//  Extracted from WelcomeView.swift when the second tutorial screen arrived. Two files in one
//  target cannot both write `extension Color { static let liqOrange }` — that is a redeclaration
//  error, not a warning. One definition, used everywhere.
//
//  Values come from the spec sheets, which carry colour and type tokens only. There is deliberately
//  no spacing scale: the raw px values in each screen are what design specified, not an oversight.

import Foundation
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
    static let liqEyebrowBg = Color(hex: 0xA78BFA).opacity(0.16)     // eyebrow pill, lavender tone
    // The tone is per screen, not global: tutorial cards lavender, phone screens orange.
    static let liqEyebrowOrangeBg = Color(hex: 0xFE6839).opacity(0.12) // eyebrow pill, orange tone

    // Added for the welcome & sign-up flow (SHOWUP-140/142/143). Verbatim from
    // design_handoff_showup/tokens/colors_and_type.css, the authoritative token file.
    static let liqElevated   = Color(hex: 0xFFFFFF)                  // input and slot fills
    static let liqRaised     = Color(hex: 0xF7F2FA)                  // --liq-bg-raised, the notice
    static let liqBorderSoft = Color(hex: 0x1D1129).opacity(0.06)    // --liq-border-soft
    static let liqSuccess    = Color(hex: 0x00AB55)                  // --liq-success, the badge
    /// `--liq-success-fg`. A DIFFERENT value from `liqSuccess`, not a shade of it: #00AB55 is
    /// the badge fill, #0A7A47 the darker ink for a success glyph on a light ground. The
    /// profile email screen's helper tick is the first use; only the badge one had been ported.
    static let liqSuccessFg  = Color(hex: 0x0A7A47)                  // --liq-success-fg
    static let liqMuted      = Color(hex: 0x1D1129).opacity(0.62)    // --liq-fg-muted
    static let liqBorder     = Color(hex: 0x1D1129).opacity(0.12)    // --liq-border
    static let liqDanger     = Color(hex: 0xFB323B)                  // invalid borders, the ! glyph
    static let liqDangerFg   = Color(hex: 0xB71F26)                  // error helper text
    static let liqDangerDigit = Color(hex: 0x7A1F26)                 // mismatch digits - a one-off
    static let liqLavender   = Color(hex: 0xA78BFA)

    /// `--liq-lavender-50`. The quietest lavender in the ramp, and a SURFACE rather than an accent.
    ///
    /// Nearly white with a violet cast, so a card can read as "ours" without competing with
    /// anything on it. Used by the prompts screen's worked-example card and the small pip behind
    /// its plus glyph (SHOWUP-158). Distinct from `liqEyebrowBg`, which is lavender-400 at 16% —
    /// that one is a tint OF the accent, this one is a named step in the ramp.
    static let liqLavenderWash = Color(hex: 0xF9F7FF)

    /// `--su-grad-lilac`, 180°. The fill of every "this is ours, and it is a question" card:
    /// the age-confirmation card on screen 04 and the saved prompt cards on screen 07.
    ///
    /// CORRECTED 15 September 2026, from `#F2EAFB -> #F8F2FB`. That pair came from the INLINE
    /// FALLBACK in the date-of-birth reference — `var(--su-grad-lilac, linear-gradient(…))` — and
    /// a CSS fallback only applies when the variable is undefined. `tokens/colors_and_type.css`
    /// defines the variable, so the fallback never rendered anywhere and the ported pair was a
    /// colour nothing in the design actually uses. The prompts screen reads the variable with no
    /// fallback at all, so there is only one correct answer there.
    ///
    /// A pair of stops rather than a `LinearGradient`, so a call site can choose its own direction
    /// and shape — the same shape the Kotlin side's `LilacStops` has.
    static let suGradLilac: [Color] = [Color(hex: 0xF1E6FF), Color(hex: 0xE8DCF5)]

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
    /// Weight 500 italic, interpolated from the 400 and 700 masters. See
    /// fonts/README-medium-italic.md. Replace if design ships a foundry file.
    static let loraMediumItalic = "Lora-MediumItalic"
    static let manropeMedium  = "Manrope-Medium"
    static let manropeSemi    = "Manrope-SemiBold"
    static let manropeBold    = "Manrope-Bold"
    static let manropeXBold   = "Manrope-ExtraBold"
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
        case .bold:          name = PS.manropeBold
        // 800, and it used to fall in with 700 above. The design's eyebrow labels are authored at
        // `fontWeight: 800` and eleven call sites ask for `.heavy`; every one of them rendered a
        // step light, and Android did the same by having no 800 in its family to match.
        case .heavy:         name = PS.manropeXBold
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

    /// A synthesised oblique, for a family that ships no italic of its own.
    ///
    /// Manrope has no italic cut -- the bundle carries Medium, SemiBold and Bold and nothing else --
    /// and the spec sheets ask for one anyway ("will meet" italic 700 on tutorial card 1). The sheet
    /// was drawn in a browser, which answers `font-style: italic` on such a family by skewing the
    /// upright, so this skews it by the same 0.2 browsers use. Asking UIKit for `.traitItalic`
    /// instead returns the upright unchanged: there is no italic face for it to find, and the run
    /// silently renders as plain bold, which is what it did here.
    static func oblique(_ name: String, _ size: CGFloat, fallback: UIFont) -> UIFont {
        guard let base = UIFont(name: name, size: size) else { return fallback }
        let skew = CGAffineTransform(a: 1, b: 0, c: 0.2, d: 1, tx: 0, ty: 0)
        return UIFont(descriptor: base.fontDescriptor.withMatrix(skew), size: 0)
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

extension String {
    /// `text-transform: uppercase`, which SwiftUI has no equivalent of.
    ///
    /// The design's eyebrow labels — Manrope 800 at 10.5 with 0.08em tracking — are authored in
    /// sentence case and transformed by CSS. The ticket's copy section quotes them in sentence
    /// case too, which is why the STRINGS stay that way and the transform happens here: the copy a
    /// verifier checks against the ticket and the pixels a person sees are two different things,
    /// and baking the capitals into the constant would make the first one lie.
    ///
    /// Two of these were already written in capitals by hand — `MAIN` and `FOR EXAMPLE` — which is
    /// how the omission hid: some of the eyebrows looked right, so none of them looked wrong.
    ///
    /// The invariant locale rather than the device's: these are English product strings, and a
    /// Turkish locale uppercases "i" to a dotted capital that is not in our font's Latin set.
    var eyebrowCase: String { uppercased(with: Locale(identifier: "en_US_POSIX")) }
}
