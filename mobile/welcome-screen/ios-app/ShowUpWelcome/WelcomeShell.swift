//  WelcomeShell.swift
//  ShowUp · shared chrome for the welcome & sign-up flow (SHOWUP-140 / 142 / 143)
//
//  Startup and Welcome back are one visual space: the same three backdrop layers, value for value,
//  and the same wordmark at 26. The handoff is explicit that building them separately makes them
//  drift, so everything shared lives here and neither screen owns a copy.
//
//  Values come from design_handoff_showup/tokens/colors_and_type.css and components/shared.jsx,
//  which are authoritative, plus the two reference .jsx files which win on numbers.

import SwiftUI
import UIKit

// MARK: - ① Backdrop — full-bleed, BEHIND the status bar and home indicator

/// The ambient backdrop.
///
/// Startup and Welcome back carry all three layers; phone verification carries two (no peach wash)
/// at lower intensity, because its keyboard owns the bottom half and nothing should glow behind it.
struct WelcomeBackdrop: View {
    var peachWash: Bool = true
    var topWeighted: Bool = false
    var orangeAlpha: Double = 0.32
    var violetAlpha: Double = 0.28

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack(alignment: .topLeading) {
                if topWeighted {
                    // orange 460 at top -18% / right -30%, violet 420 at top -10% / left -25%
                    orb(460, .liqOrange, orangeAlpha)
                        .position(x: w * 1.30 - 230, y: -0.18 * h + 230)
                    orb(420, .liqPurple, violetAlpha)
                        .position(x: -0.25 * w + 210, y: -0.10 * h + 210)
                } else {
                    // orange 520 at top -15% / right -25%, violet 600 at bottom -20% / left -30%
                    orb(520, .liqOrange, orangeAlpha)
                        .position(x: w * 1.25 - 260, y: -0.15 * h + 260)
                    orb(600, .liqPurple, violetAlpha)
                        .position(x: -0.30 * w + 300, y: h * 1.20 - 300)
                }
                if peachWash {
                    // linear-gradient(180deg, rgba(255,229,210,.55) 0%, rgba(255,251,247,0) 70%)
                    LinearGradient(
                        stops: [
                            .init(color: Color(hex: 0xFFE5D2).opacity(0.55), location: 0.0),
                            .init(color: Color(hex: 0xFFFBF7).opacity(0.0), location: 0.7),
                            .init(color: .clear, location: 1.0),
                        ],
                        startPoint: .top, endPoint: .bottom
                    )
                    .frame(height: 360)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// One orb. 65% is where the reference puts full transparency.
    private func orb(_ size: CGFloat, _ color: Color, _ alpha: Double) -> some View {
        RadialGradient(
            gradient: Gradient(stops: [
                .init(color: color.opacity(alpha), location: 0.0),
                .init(color: color.opacity(0.0), location: 0.65),
            ]),
            center: .center, startRadius: 0, endRadius: size / 2
        )
        .frame(width: size, height: size)
    }
}

/// The launch-screen scaffold. Insets apply to the content only, so the backdrop runs full-bleed
/// behind the status bar and home indicator with no seam — an acceptance criterion on 140 and 142.
struct WelcomeScaffold<Content: View>: View {
    var peachWash: Bool = true
    var topWeighted: Bool = false
    var orangeAlpha: Double = 0.32
    var violetAlpha: Double = 0.28
    var topPadding: CGFloat = 20
    /// Scroll only as a last resort, for screens that share the frame with a keyboard.
    ///
    /// The handoff says these screens never scroll, and once the SHOWUP-143 design decisions land
    /// they will not: the content fits and this does nothing. But "never scroll" and "the CTA is
    /// always visible and accessible" are both requirements, and when the keyboard takes half a
    /// small screen only one can hold. Between a CTA the user cannot reach and a few points of
    /// scroll, the scroll is the right failure.
    ///
    /// The inner stack is floored at the viewport height, so when everything fits there is nothing
    /// to scroll and the bottom spacer still does its job — the layout is unchanged.
    var scrollWhenTight: Bool = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            WelcomeBackdrop(peachWash: peachWash, topWeighted: topWeighted,
                            orangeAlpha: orangeAlpha, violetAlpha: violetAlpha)
                .ignoresSafeArea()

            if scrollWhenTight {
                GeometryReader { geo in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 0) { content() }
                            .padding(.horizontal, 24)
                            .padding(.top, topPadding)
                            .frame(minHeight: geo.size.height, alignment: .top)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    // Bounce off means a screen that fits does not rubber-band, so it reads as a
                    // fixed layout rather than a scroll view that happens to be full.
                    .scrollBounceBehavior(.basedOnSize)
                }
            } else {
                VStack(alignment: .leading, spacing: 0) { content() }
                    .padding(.horizontal, 24)
                    .padding(.top, topPadding)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
    }
}

// MARK: - ② Wordmark

/// `.su-wordmark` — Lora 700, tracking -0.02em, baseline-aligned with a 0.18em gap.
///
/// "Up" takes `--su-grad-wordmark` clipped to the glyphs, not flat violet — `background(...)` plus
/// `.mask(Text)` is SwiftUI's equivalent of the CSS `background-clip: text`.
struct Wordmark: View {
    var size: CGFloat = 26

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: size * 0.18) {
            Text("Show").foregroundColor(.liqFg)
            LinearGradient(
                stops: [
                    .init(color: .liqPurple, location: 0.0),
                    .init(color: Color(hex: 0xD05976), location: 0.55),
                    .init(color: .liqOrange, location: 1.0),
                ],
                startPoint: .leading, endPoint: .trailing
            )
            .mask(Text("Up").font(loraItalic).tracking(-0.02 * size))
            .frame(width: upWidth, height: size * 1.2)
            Text(".").foregroundColor(.liqOrange)
        }
        .font(lora)
        .tracking(-0.02 * size)
        .accessibilityElement()
        .accessibilityLabel("Show Up")
    }

    private var lora: Font { F.lora(size, bold: true) }
    private var loraItalic: Font { .custom(PS.loraBoldItalic, size: size) }
    private var upWidth: CGFloat {
        let f = UIFont(name: PS.loraBoldItalic, size: size) ?? .boldSystemFont(ofSize: size)
        return ("Up" as NSString).size(withAttributes: [.font: f]).width + 2
    }
}

// MARK: - ③ The underline wash

/// A headline whose italic run carries the orange wash.
///
/// From `tokens/colors_and_type.css`, `.su-underlined em::after`:
///   left/right -2 · bottom -0.08em · height 0.32em · z-index -1
///   radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)
/// and the em itself is italic at **weight 500**, not the surrounding 700.
///
/// SwiftUI's `Text` cannot report where a run landed after wrapping, so this drops to TextKit —
/// the same thing the Compose side does with `getPathForRange`. A fixed-width wash only ever looks
/// right at one frame size, which is what an earlier build shipped.
struct WashHeadline: UIViewRepresentable {
    /// (text, isTheItalicRun)
    let parts: [(String, Bool)]
    let fontSize: CGFloat
    var lineHeightMultiple: CGFloat = 1.05
    var trackingEm: CGFloat = -0.02

    func makeUIView(context: Context) -> WashLabel {
        let v = WashLabel()
        v.numberOfLines = 0
        v.setContentCompressionResistancePriority(.required, for: .vertical)
        v.setContentHuggingPriority(.required, for: .vertical)
        return v
    }

    func updateUIView(_ v: WashLabel, context: Context) {
        let regular = TypeMetrics.uiFont(PS.loraBold, fontSize,
                                         fallback: .systemFont(ofSize: fontSize, weight: .bold))
        // Weight 500, as the token file specifies. The handoff shipped 400 and 700 only, so this
        // cut was interpolated from those two masters — a real 500, not a synthetic embolden.
        // See fonts/README-medium-italic.md.
        let italic = TypeMetrics.uiFont(PS.loraMediumItalic, fontSize,
                                        fallback: TypeMetrics.italicSystem(fontSize))
        let para = NSMutableParagraphStyle()
        para.lineHeightMultiple = lineHeightMultiple

        let out = NSMutableAttributedString()
        var italicRange = NSRange(location: NSNotFound, length: 0)
        for (text, isItalic) in parts {
            let start = out.length
            out.append(NSAttributedString(string: text, attributes: [
                .font: isItalic ? italic : regular,
                .foregroundColor: UIColor(Color.liqFg),
                .kern: trackingEm * fontSize,
                .paragraphStyle: para,
            ]))
            if isItalic && italicRange.location == NSNotFound {
                italicRange = NSRange(location: start, length: text.count)
            }
        }
        v.attributedText = out
        v.italicRange = italicRange
        v.emSize = fontSize
        v.setNeedsDisplay()
    }
}

/// Draws the wash behind the italic run, then lets the label draw the text over it.
final class WashLabel: UILabel {
    var italicRange = NSRange(location: NSNotFound, length: 0)
    var emSize: CGFloat = 44

    override func draw(_ rect: CGRect) {
        drawWash()
        super.draw(rect)     // text paints over the wash, matching z-index: -1
    }

    private func drawWash() {
        guard italicRange.location != NSNotFound,
              let attributed = attributedText,
              let ctx = UIGraphicsGetCurrentContext() else { return }

        // A TextKit stack matching this label, so the rect is where the glyphs actually are.
        let storage = NSTextStorage(attributedString: attributed)
        let manager = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: bounds.width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = numberOfLines
        container.lineBreakMode = lineBreakMode
        storage.addLayoutManager(manager)
        manager.addTextContainer(container)
        manager.ensureLayout(for: container)

        let glyphRange = manager.glyphRange(forCharacterRange: italicRange, actualCharacterRange: nil)
        var box = CGRect.null
        manager.enumerateEnclosingRects(forGlyphRange: glyphRange,
                                        withinSelectedGlyphRange: NSRange(location: NSNotFound, length: 0),
                                        in: container) { r, _ in
            box = box.isNull ? r : box.union(r)
        }
        guard !box.isNull, box.width > 0 else { return }

        // left/right -2 · bottom -0.08em · height 0.32em
        let left = box.minX - 2
        let right = box.maxX + 2
        let bottom = box.maxY + emSize * 0.08
        let h = emSize * 0.32
        let w = right - left
        let rx = w / 2

        ctx.saveGState()
        // ellipse at 50% 100%: horizontal radius w/2, vertical radius h
        ctx.translateBy(x: left + rx, y: bottom)
        ctx.scaleBy(x: 1, y: h / rx)
        let colors = [UIColor(Color.liqOrange).withAlphaComponent(0.55).cgColor,
                      UIColor(Color.liqOrange).withAlphaComponent(0.0).cgColor] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                              colors: colors, locations: [0.0, 0.7]) {
            ctx.drawRadialGradient(g, startCenter: .zero, startRadius: 0,
                                   endCenter: .zero, endRadius: rx, options: [])
        }
        ctx.restoreGState()
    }
}

// MARK: - ④ Buttons — always pills (9999), never rounded rectangles

/// Button treatments.
///
/// `.apple`, `.google` and `.facebook` are not style choices — each provider dictates the appearance
/// of its own sign-in button and enforces it. Google forbids recolouring or resizing the G and
/// requires a white background, making compliance a condition of app verification; Meta requires its
/// mark in white or #1877F2 and prohibits recolouring to a host brand's palette. The uniform ghost
/// treatment the design specified breaks both.
///
/// The pill silhouette, the 56 height and the Manrope label stay — those are ours.
enum PillVariant { case sunset, ghost, apple, google, facebook }

/// `Button` from components/shared.jsx at `size="lg"`: height 56, padding 0/28, radius 9999,
/// Manrope 700 16, gap 8.
///
/// Press is `scale(0.98)` over 180ms on `cubic-bezier(.22,1,.36,1)`. CLAUDE.md states it as a
/// non-negotiable, and there are no hover states because the product is mobile-first.
struct PillButton<Leading: View>: View {
    let label: String
    var variant: PillVariant = .sunset
    var enabled: Bool = true
    var action: () -> Void
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        Button(action: { if enabled { action() } }) {
            HStack(spacing: 8) {
                leading()
                Text(label)
                    .font(F.manrope(16, .bold))
                    .lineLimit(1)
                    .foregroundColor(labelColor)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .padding(.horizontal, 28)
            .background(background)
            .clipShape(Capsule())
            .overlay(
                variant == .ghost ? Capsule().strokeBorder(Color.liqBorder, lineWidth: 1)
                : variant == .google ? Capsule().strokeBorder(Color(hex: 0x747775), lineWidth: 1)
                : nil
            )
            .shadow(color: variant == .sunset ? Color.liqPurple.opacity(0.28) : .clear,
                    radius: 10, y: 8)
            .opacity(enabled ? 1 : 0.45)
        }
        .buttonStyle(PressScale())
        .disabled(!enabled)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(.isButton)
    }

    private var labelColor: Color {
        switch variant {
        case .sunset, .apple, .facebook: return .white
        case .google: return Color(hex: 0x1F1F1F)     // Google's specified label colour
        case .ghost: return .liqFg
        }
    }

    @ViewBuilder private var background: some View {
        switch variant {
        case .sunset:
            // 135°, midpoint at 38% — not an even three-stop ramp
            LinearGradient(
                stops: [
                    .init(color: .liqOrange, location: 0.00),
                    .init(color: Color(hex: 0xD05976), location: 0.38),
                    .init(color: .liqPurple, location: 1.00),
                ],
                startPoint: .topLeading, endPoint: .bottomTrailing)
        case .ghost:
            Color.clear
        // Black is one of the three appearances Apple's guidelines allow.
        case .apple:
            Color.black
        // A white background is required, not preferred — the G may not sit on anything else.
        case .google:
            Color.white
        // Facebook Blue. Recolouring to our palette is explicitly prohibited.
        case .facebook:
            Color(hex: 0x1877F2)
        }
    }
}

extension PillButton where Leading == EmptyView {
    init(_ label: String, variant: PillVariant = .sunset, enabled: Bool = true,
         action: @escaping () -> Void) {
        self.init(label: label, variant: variant, enabled: enabled, action: action,
                  leading: { EmptyView() })
    }
}

/// `transform 180ms cubic-bezier(.22,1,.36,1)`, scale 0.98. Honours reduce-motion.
struct PressScale: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: 0.18),
                       value: configuration.isPressed)
    }
}

// MARK: - ⑤ Icons — Lucide geometry, 24×24, stroke 1.7, currentColor

/// Drawn rather than imported: CLAUDE.md requires inline stroke-only SVG at Lucide geometry and
/// forbids icon fonts, PNGs and unicode glyphs as icons.
enum BrandIcon { case phone, apple, google, facebook, calendar, chevronDown, arrowLeft, pencil }

struct BrandIconView: View {
    let icon: BrandIcon
    var size: CGFloat = 20
    var stroke: CGFloat = 1.7
    var tint: Color = .liqFg

    var body: some View {
        Canvas { ctx, _ in
            let s = size / 24
            var t = CGAffineTransform(scaleX: s, y: s)
            let style = StrokeStyle(lineWidth: stroke / s, lineCap: .round, lineJoin: .round)
            let filled: Bool
            let p: Path
            switch icon {
            case .calendar:
                filled = false
                p = Path { b in
                    b.addRoundedRect(in: CGRect(x: 3, y: 4, width: 18, height: 18),
                                     cornerSize: CGSize(width: 2, height: 2))
                    b.move(to: .init(x: 16, y: 2)); b.addLine(to: .init(x: 16, y: 6))
                    b.move(to: .init(x: 8, y: 2)); b.addLine(to: .init(x: 8, y: 6))
                    b.move(to: .init(x: 3, y: 10)); b.addLine(to: .init(x: 21, y: 10))
                }
            case .chevronDown:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 6, y: 9)); b.addLine(to: .init(x: 12, y: 15))
                    b.addLine(to: .init(x: 18, y: 9))
                }
            case .arrowLeft:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 19, y: 12)); b.addLine(to: .init(x: 5, y: 12))
                    b.move(to: .init(x: 12, y: 19)); b.addLine(to: .init(x: 5, y: 12))
                    b.addLine(to: .init(x: 12, y: 5))
                }
            case .pencil:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 11, y: 4)); b.addLine(to: .init(x: 4, y: 4))
                    b.addCurve(to: .init(x: 2, y: 6), control1: .init(x: 2.9, y: 4), control2: .init(x: 2, y: 4.9))
                    b.addLine(to: .init(x: 2, y: 20))
                    b.addCurve(to: .init(x: 4, y: 22), control1: .init(x: 2, y: 21.1), control2: .init(x: 2.9, y: 22))
                    b.addLine(to: .init(x: 18, y: 22))
                    b.addCurve(to: .init(x: 20, y: 20), control1: .init(x: 19.1, y: 22), control2: .init(x: 20, y: 21.1))
                    b.addLine(to: .init(x: 20, y: 13))
                    b.move(to: .init(x: 18.5, y: 2.5))
                    b.addCurve(to: .init(x: 21.5, y: 5.5), control1: .init(x: 19.3, y: 1.7), control2: .init(x: 22.3, y: 2.5))
                    b.addLine(to: .init(x: 12, y: 15)); b.addLine(to: .init(x: 8, y: 16))
                    b.addLine(to: .init(x: 9, y: 12)); b.closeSubpath()
                }
            case .phone:
                filled = false
                p = Self.phonePath
            case .apple:
                filled = true; p = Self.applePath
            case .google:
                // The four-colour G, drawn as four fills. It ignores `tint` by design — recolouring
                // it is the thing the branding guidelines forbid most explicitly.
                ctx.fill(Self.googleBlue.applying(t), with: .color(Color(hex: 0x4285F4)))
                ctx.fill(Self.googleGreen.applying(t), with: .color(Color(hex: 0x34A853)))
                ctx.fill(Self.googleYellow.applying(t), with: .color(Color(hex: 0xFBBC05)))
                ctx.fill(Self.googleRed.applying(t), with: .color(Color(hex: 0xEA4335)))
                return
            case .facebook:
                filled = true; p = Self.facebookPath
            }
            let scaled = p.applying(t)
            if filled {
                ctx.fill(scaled, with: .color(tint))
            } else {
                ctx.stroke(scaled, with: .color(tint), style: StrokeStyle(lineWidth: stroke, lineCap: .round, lineJoin: .round))
            }
            _ = t
        }
        .frame(width: size, height: size)
    }

    static let phonePath = Path { b in
        b.move(to: .init(x: 22, y: 16.92)); b.addLine(to: .init(x: 22, y: 19.92))
        b.addCurve(to: .init(x: 19.82, y: 21.92), control1: .init(x: 22, y: 21.02), control2: .init(x: 20.92, y: 21.99))
        b.addCurve(to: .init(x: 11.19, y: 18.85), control1: .init(x: 16.7, y: 21.6), control2: .init(x: 13.7, y: 20.6))
        b.addCurve(to: .init(x: 5.19, y: 12.85), control1: .init(x: 8.85, y: 17.25), control2: .init(x: 6.87, y: 15.27))
        b.addCurve(to: .init(x: 2.12, y: 4.18), control1: .init(x: 3.43, y: 10.3), control2: .init(x: 2.44, y: 7.3))
        b.addCurve(to: .init(x: 4.11, y: 2), control1: .init(x: 2.05, y: 3.08), control2: .init(x: 3.01, y: 2))
        b.addLine(to: .init(x: 7.11, y: 2))
        b.addCurve(to: .init(x: 9.11, y: 3.72), control1: .init(x: 8.11, y: 2), control2: .init(x: 8.98, y: 2.72))
        b.addCurve(to: .init(x: 9.83, y: 6.53), control1: .init(x: 9.24, y: 4.68), control2: .init(x: 9.48, y: 5.62))
        b.addCurve(to: .init(x: 9.38, y: 8.64), control1: .init(x: 10.06, y: 7.24), control2: .init(x: 9.86, y: 8.02))
        b.addLine(to: .init(x: 8.09, y: 9.91))
        b.addCurve(to: .init(x: 14.09, y: 15.91), control1: .init(x: 9.53, y: 12.36), control2: .init(x: 11.64, y: 14.47))
        b.addLine(to: .init(x: 15.36, y: 14.64))
        b.addCurve(to: .init(x: 17.47, y: 14.19), control1: .init(x: 15.98, y: 14.16), control2: .init(x: 16.76, y: 13.96))
        b.addCurve(to: .init(x: 20.28, y: 14.91), control1: .init(x: 18.38, y: 14.54), control2: .init(x: 19.32, y: 14.78))
        b.addCurve(to: .init(x: 22, y: 16.92), control1: .init(x: 21.29, y: 15.05), control2: .init(x: 22, y: 15.92))
        b.closeSubpath()
    }

    static let applePath = Path { b in
        b.move(to: .init(x: 16, y: 4))
        b.addCurve(to: .init(x: 14, y: 7.5), control1: .init(x: 16.5, y: 5.5), control2: .init(x: 15.5, y: 7))
        b.addCurve(to: .init(x: 11, y: 5.5), control1: .init(x: 12, y: 8), control2: .init(x: 11, y: 7))
        b.addCurve(to: .init(x: 16, y: 4), control1: .init(x: 12.5, y: 4), control2: .init(x: 14, y: 3.5))
        b.closeSubpath()
        b.move(to: .init(x: 18.4, y: 13.5))
        b.addCurve(to: .init(x: 15.5, y: 16.5), control1: .init(x: 17.8, y: 15), control2: .init(x: 17, y: 16.5))
        b.addCurve(to: .init(x: 12, y: 15.7), control1: .init(x: 14.1, y: 16.5), control2: .init(x: 13.6, y: 15.7))
        b.addCurve(to: .init(x: 8.5, y: 16.5), control1: .init(x: 10.4, y: 15.7), control2: .init(x: 9.9, y: 16.5))
        b.addCurve(to: .init(x: 5.4, y: 13.6), control1: .init(x: 7, y: 16.5), control2: .init(x: 6.1, y: 15.1))
        b.addCurve(to: .init(x: 6.6, y: 6.5), control1: .init(x: 4, y: 11), control2: .init(x: 4.6, y: 7.6))
        b.addCurve(to: .init(x: 10.1, y: 6.5), control1: .init(x: 7.9, y: 5.8), control2: .init(x: 9.1, y: 6.2))
        b.addCurve(to: .init(x: 12.5, y: 6.5), control1: .init(x: 11.1, y: 6.8), control2: .init(x: 11.5, y: 6.8))
        b.addCurve(to: .init(x: 16.1, y: 6.3), control1: .init(x: 13.6, y: 6.1), control2: .init(x: 14.7, y: 5.6))
        b.addCurve(to: .init(x: 15.7, y: 11.1), control1: .init(x: 14.4, y: 7.4), control2: .init(x: 14, y: 9.8))
        b.addCurve(to: .init(x: 18.4, y: 13.5), control1: .init(x: 16.2, y: 12.2), control2: .init(x: 16.5, y: 12.7))
        b.closeSubpath()
    }

    // Google's own path data, one shape per colour. Not redrawn or simplified: the guidelines
    // forbid altering the mark, and "close enough" is altering it.
    static let googleBlue = Path { b in
        b.move(to: .init(x: 22.56, y: 12.25))
        b.addCurve(to: .init(x: 22.36, y: 10), control1: .init(x: 22.56, y: 11.47), control2: .init(x: 22.49, y: 10.72))
        b.addLine(to: .init(x: 12, y: 10)); b.addLine(to: .init(x: 12, y: 14.26))
        b.addLine(to: .init(x: 17.92, y: 14.26))
        b.addCurve(to: .init(x: 15.71, y: 17.57), control1: .init(x: 17.66, y: 15.63), control2: .init(x: 16.88, y: 16.79))
        b.addLine(to: .init(x: 15.71, y: 20.34)); b.addLine(to: .init(x: 19.28, y: 20.34))
        b.addCurve(to: .init(x: 22.56, y: 12.25), control1: .init(x: 21.36, y: 18.42), control2: .init(x: 22.56, y: 15.6))
        b.closeSubpath()
    }
    static let googleGreen = Path { b in
        b.move(to: .init(x: 12, y: 23))
        b.addCurve(to: .init(x: 19.28, y: 20.34), control1: .init(x: 14.97, y: 23), control2: .init(x: 17.46, y: 22.02))
        b.addLine(to: .init(x: 15.71, y: 17.57))
        b.addCurve(to: .init(x: 12, y: 18.63), control1: .init(x: 14.73, y: 18.23), control2: .init(x: 13.48, y: 18.63))
        b.addCurve(to: .init(x: 5.84, y: 14.1), control1: .init(x: 9.14, y: 18.63), control2: .init(x: 6.71, y: 16.7))
        b.addLine(to: .init(x: 2.18, y: 14.1)); b.addLine(to: .init(x: 2.18, y: 16.94))
        b.addCurve(to: .init(x: 12, y: 23), control1: .init(x: 3.99, y: 20.53), control2: .init(x: 7.7, y: 23))
        b.closeSubpath()
    }
    static let googleYellow = Path { b in
        b.move(to: .init(x: 5.84, y: 14.09))
        b.addCurve(to: .init(x: 5.49, y: 12), control1: .init(x: 5.62, y: 13.43), control2: .init(x: 5.49, y: 12.73))
        b.addCurve(to: .init(x: 5.84, y: 9.91), control1: .init(x: 5.49, y: 11.27), control2: .init(x: 5.62, y: 10.57))
        b.addLine(to: .init(x: 5.84, y: 7.07)); b.addLine(to: .init(x: 2.18, y: 7.07))
        b.addCurve(to: .init(x: 1, y: 12), control1: .init(x: 1.43, y: 8.55), control2: .init(x: 1, y: 10.22))
        b.addCurve(to: .init(x: 2.18, y: 16.93), control1: .init(x: 1, y: 13.78), control2: .init(x: 1.43, y: 15.45))
        b.addLine(to: .init(x: 5.03, y: 14.71))
        b.closeSubpath()
    }
    static let googleRed = Path { b in
        b.move(to: .init(x: 12, y: 5.38))
        b.addCurve(to: .init(x: 16.21, y: 7.02), control1: .init(x: 13.62, y: 5.38), control2: .init(x: 15.06, y: 5.94))
        b.addLine(to: .init(x: 19.36, y: 3.87))
        b.addCurve(to: .init(x: 12, y: 1), control1: .init(x: 17.45, y: 2.09), control2: .init(x: 14.97, y: 1))
        b.addCurve(to: .init(x: 2.18, y: 7.07), control1: .init(x: 7.7, y: 1), control2: .init(x: 3.99, y: 3.47))
        b.addLine(to: .init(x: 5.84, y: 9.91))
        b.addCurve(to: .init(x: 12, y: 5.38), control1: .init(x: 6.71, y: 7.31), control2: .init(x: 9.14, y: 5.38))
        b.closeSubpath()
    }

    static let facebookPath = Path { b in
        b.move(to: .init(x: 22, y: 12))
        b.addCurve(to: .init(x: 12, y: 2), control1: .init(x: 22, y: 6.48), control2: .init(x: 17.52, y: 2))
        b.addCurve(to: .init(x: 2, y: 12), control1: .init(x: 6.48, y: 2), control2: .init(x: 2, y: 6.48))
        b.addCurve(to: .init(x: 10.44, y: 21.88), control1: .init(x: 2, y: 16.84), control2: .init(x: 5.44, y: 20.87))
        b.addLine(to: .init(x: 10.44, y: 14.89)); b.addLine(to: .init(x: 7.9, y: 14.89))
        b.addLine(to: .init(x: 7.9, y: 12)); b.addLine(to: .init(x: 10.44, y: 12))
        b.addLine(to: .init(x: 10.44, y: 9.8))
        b.addCurve(to: .init(x: 14.22, y: 5.9), control1: .init(x: 10.44, y: 7.29), control2: .init(x: 11.93, y: 5.9))
        b.addCurve(to: .init(x: 16.46, y: 6.1), control1: .init(x: 15.32, y: 5.9), control2: .init(x: 16.46, y: 6.1))
        b.addLine(to: .init(x: 16.46, y: 8.56)); b.addLine(to: .init(x: 15.2, y: 8.56))
        b.addCurve(to: .init(x: 13.57, y: 10.12), control1: .init(x: 13.96, y: 8.56), control2: .init(x: 13.57, y: 9.33))
        b.addLine(to: .init(x: 13.57, y: 12)); b.addLine(to: .init(x: 16.35, y: 12))
        b.addLine(to: .init(x: 15.9, y: 14.89)); b.addLine(to: .init(x: 13.57, y: 14.89))
        b.addLine(to: .init(x: 13.57, y: 21.88))
        b.addCurve(to: .init(x: 22, y: 12), control1: .init(x: 18.56, y: 20.87), control2: .init(x: 22, y: 16.84))
        b.closeSubpath()
    }
}
