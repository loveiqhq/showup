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
                            .padding(.horizontal, Spacing.screenGutter)
                            .padding(.top, topPadding)
                            .frame(minHeight: geo.size.height, alignment: .top)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    // Bounce off means a screen that fits does not rubber-band, so it reads as a
                    // fixed layout rather than a scroll view that happens to be full.
                    //
                    // iOS 16.4+. The deployment target is 16.0, so this is gated rather than
                    // dropped: it is a refinement, and on 16.0-16.3 the only difference is that a
                    // screen which already fits can still be dragged a few points.
                    .modifier(NoBounceWhenItFits())
                }
            } else {
                VStack(alignment: .leading, spacing: 0) { content() }
                    .padding(.horizontal, Spacing.screenGutter)
                    .padding(.top, topPadding)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
    }
}

/// `scrollBounceBehavior(.basedOnSize)` -- no scroll bounce when the content already fits.
///
/// This used to carry an `if #available(iOS 16.4)` fork, because the project deployed to 16.0.
/// At a 17.0 floor the modifier is simply always there, so the fork and the second branch that had
/// to be kept identical to the first are both gone.
private struct NoBounceWhenItFits: ViewModifier {
    func body(content: Content) -> some View {
        content.scrollBounceBehavior(.basedOnSize)
    }
}

// MARK: - ② Wordmark

/// `.su-wordmark` — Lora 700, tracking -0.02em, baseline-aligned, and the gap is 0.04em, not the 0.18em this carried for months.
///
/// Measured rather than chosen. Against the Startup spec sheet the rendered wordmark showed ink
/// gaps of 0.058em between "Show" and "Up" and 0.117em before the dot; 0.18em spacing produced
/// 0.203em and 0.239em -- three times and twice too wide, which is what made the mark read as
/// three loose words instead of one lockup. 0.04em lands at 0.074em and 0.092em, inside the ±0.03em
/// a measurement off a screenshot of the sheet can actually resolve.
///
/// The two gaps differ from each other on their own: the period carries far more side bearing than
/// "U" does, so one spacing value reproduces the sheet's uneven pair without being told to.
///
/// "Up" takes `--su-grad-wordmark` clipped to the glyphs, not flat violet.
///
/// It used to get there through `LinearGradient().mask(Text("Up")).frame(width:height:)`, which is
/// the usual SwiftUI spelling of CSS `background-clip: text` and was wrong here for one reason: a
/// masked gradient is not text, so it has no text baseline. `HStack(alignment: .lastTextBaseline)`
/// fell back to the frame's bottom edge, and because the glyphs sat centred inside a `size * 1.2`
/// box, "Up" floated above the line "Show" and the dot sat on — visible in the render as a raised,
/// oversized "Up" and a trailing dot adrift to the right, the latter also carrying the hand-measured
/// `upWidth + 2` that the frame needed and the text does not.
///
/// `Text.foregroundStyle` takes a ShapeStyle directly, and a gradient given to a STANDALONE Text
/// resolves across that text's own bounds — so "Up" still ramps violet to orange over "Up" alone,
/// not over the whole wordmark, which is what concatenating the three runs would have done. The
/// text stays text, so the baseline is real and the widths are the font's.
struct Wordmark: View {
    var size: CGFloat = 26

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: size * 0.04) {
            Text("Show").foregroundColor(.liqFg)
            Text("Up")
                .font(loraItalic)
                .foregroundStyle(
                    LinearGradient(
                        stops: [
                            .init(color: .liqPurple, location: 0.0),
                            .init(color: Color(hex: 0xD05976), location: 0.55),
                            .init(color: .liqOrange, location: 1.0),
                        ],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
            Text(".").foregroundColor(.liqOrange)
        }
        .font(lora)
        .tracking(-0.02 * size)
        .accessibilityElement()
        .accessibilityLabel("Show Up")
    }

    private var lora: Font { F.lora(size, bold: true) }
    private var loraItalic: Font { .custom(PS.loraBoldItalic, size: size) }
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
    /// An icon that trails the headline, sitting on the baseline of whatever line the text ends on.
    ///
    /// Part of the text, not a sibling in an HStack. The sheet and the ticket both put the heart on
    /// SHOWUP-144 "on the baseline" at the end of the headline, and being in the flow is the only
    /// way it lands there whichever line the text happens to end on. It also removes a whole class
    /// of bug: a sibling in an HStack reserves its width against every line, so the headline had
    /// less room than it appeared to, wrapped badly, and pushed the icon past the edge.
    var trailing: BrandIcon? = nil
    var trailingSize: CGFloat = 30
    var trailingTint: Color = .liqOrange
    var trailingGap: CGFloat = 12

    func makeUIView(context: Context) -> WashLabel {
        let v = WashLabel()
        v.numberOfLines = 0
        v.setContentCompressionResistancePriority(.required, for: .vertical)
        v.setContentHuggingPriority(.required, for: .vertical)
        return v
    }


    /// The trailing mark as a bitmap, so it can travel inside an attributed string.
    ///
    /// Only the filled marks are supported, because only those are ever used here. An unhandled
    /// icon returns nil and the headline simply renders without a trailing mark rather than
    /// drawing something wrong.
    private func rasterisedIcon(_ icon: BrandIcon) -> UIImage? {
        let path: Path
        switch icon {
        case .heart: path = BrandIconView.heartPath
        default: return nil
        }
        return BrandIconView.filledImage(path, size: trailingSize, tint: UIColor(trailingTint))
    }

    /// Report the size this headline needs **at the width it is being offered**.
    ///
    /// Without this, SwiftUI falls back to the UILabel's intrinsicContentSize, and a label with
    /// numberOfLines = 0 and no preferredMaxLayoutWidth reports its ONE-LINE width -- however long
    /// that is. On its own in a VStack nothing goes wrong, because the full width is offered
    /// anyway. Inside an HStack next to the heart on Connect, the label claims the whole row, the
    /// heart gets pushed past the edge and the text is clipped.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView v: WashLabel, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0, width < .infinity else {
            // No width offered yet: this is the "how big would you like to be" pass, and the
            // honest answer is one line. SwiftUI then offers a real width and asks again.
            return nil
        }
        v.preferredMaxLayoutWidth = width
        let fit = v.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: min(fit.width, width), height: fit.height)
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
        // The spec's 1.05 is a CSS line-height: a multiple of the FONT SIZE. NSParagraphStyle's
        // lineHeightMultiple is a multiple of the FONT'S OWN line height, which for Lora is about
        // 1.28em -- so assigning 1.05 straight across asked for 1.05 x 1.28 = 1.34em and put a
        // third of a line of air between "What's your" and "number?" that the design never had.
        // Measured against the sheet: 51pt between line tops where it draws the equivalent of 40.
        //
        // TypeMetrics.attributed already did this conversion; this is the same arithmetic, and the
        // Compose twin never needed it because Compose's lineHeight is absolute to begin with.
        para.lineHeightMultiple = (fontSize * lineHeightMultiple) / regular.lineHeight

        let out = NSMutableAttributedString()
        var italicRange = NSRange(location: NSNotFound, length: 0)
        for (rawText, isItalic) in parts {
            // `white-space: nowrap` on the emphasis span, expressed the only way an attributed
            // string can: a space that is not a break opportunity. Without it "Show Up" can split
            // across two lines and the wash paints under two disconnected fragments.
            let text = isItalic ? rawText.replacingOccurrences(of: " ", with: "\u{00A0}") : rawText
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
        // The trailing icon rides in the string as an attachment, so the line breaker treats
        // it as part of the last word. bounds.origin.y = 0 puts its bottom on the baseline,
        // which is what "on the baseline" means for a solid shape with no descender. The gap
        // is a NON-BREAKING space, so the icon can never be pulled onto a line by itself.
        if let trailing, let mark = rasterisedIcon(trailing) {
            out.append(NSAttributedString(
                string: "\u{00A0}",
                attributes: [.font: UIFont.systemFont(ofSize: trailingGap)]))
            let attachment = NSTextAttachment()
            attachment.image = mark
            attachment.bounds = CGRect(x: 0, y: 0, width: trailingSize, height: trailingSize)
            out.append(NSAttributedString(attachment: attachment))
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
        // Clipped to the band, which the CSS gets for free and this did not.
        //
        // The gradient is an ellipse centred on the BOTTOM edge with a vertical radius of h, so it
        // spans h above that edge and h below it. In CSS the ::after box is only the h above, and
        // the rest is clipped away -- which is what makes the wash read as a band with an edge.
        // Unclipped, the lower half bled into the line below and it looked like a smudge.
        ctx.clip(to: CGRect(x: left, y: bottom - h, width: w, height: h))
        // ellipse at 50% 100%: horizontal radius w/2, vertical radius h
        ctx.translateBy(x: left + rx, y: bottom)
        ctx.scaleBy(x: 1, y: h / rx)
        let colors = [UIColor(Color.liqOrange).withAlphaComponent(0.55).cgColor,
                      UIColor(Color.liqOrange).withAlphaComponent(0.0).cgColor] as CFArray
        // Transparent at 1.0, not at 0.7. The stop is a fraction of the radius, and the radius is
        // half the phrase -- so stopping at 0.7 put the last ink at 70% of the way out, and
        // because alpha falls off the whole way the eye lost it around 56%. Measured against the
        // design's own render, which reaches about 78%: the wash sat under the middle of the
        // emphasised phrase rather than under all of it.
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                              colors: colors, locations: [0.0, 1.0]) {
            ctx.drawRadialGradient(g, startCenter: .zero, startRadius: 0,
                                   endCenter: .zero, endRadius: rx, options: [])
        }
        ctx.restoreGState()
    }
}

// MARK: - ⑤ Icons — Lucide geometry, 24×24, stroke 1.7, currentColor

/// Drawn rather than imported: CLAUDE.md requires inline stroke-only SVG at Lucide geometry and
/// forbids icon fonts, PNGs and unicode glyphs as icons.
enum BrandIcon { case phone, apple, google, facebook, calendar, chevronDown, chevronLeft,
                 arrowLeft, arrowRight, pencil, close, check, shield, heart, eyeOff }

struct BrandIconView: View {
    let icon: BrandIcon
    var size: CGFloat = 20
    var stroke: CGFloat = 1.7
    var tint: Color = .liqFg
    /// Centre the mark on its own ink rather than on its 24-unit artboard.
    ///
    /// The provider logos are drawn to sit optically correct BESIDE a text label, which leaves them
    /// a little high in their own box -- Apple's occupies y 3.8..16.5, so its centre is ~1.8 units
    /// above the artboard's. Against a label that is invisible. Alone inside a 120pt ring it is
    /// not, which is exactly where the linking and success heroes put it. Measured from the path
    /// rather than tabulated per icon, so it stays right if the artwork is ever replaced.
    ///
    /// Declared LAST: Swift's memberwise init is positional, and every existing call site passes
    /// icon / size / stroke / tint in that order.
    var opticalCentre: Bool = false

    var body: some View {
        Canvas { ctx, _ in
            let s = size / 24
            let nudge = opticalCentre ? Self.inkOffset(icon) : .zero
            // Every path below is authored on a 24-unit grid; this scales it to the requested size.
            // Nudge in 24-grid units first, then scale the whole thing to the requested size.
            // `concatenating` reads left to right, so this is translate-then-scale.
            let t = CGAffineTransform(translationX: nudge.x, y: nudge.y)
                .concatenating(CGAffineTransform(scaleX: s, y: s))
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
            // The back control, per the handoff's AppHeader: chevron-left, NOT arrow-left. Both
            // icons exist in the design's set and they are not interchangeable -- the arrow has a
            // shaft and reads much heavier at the top of a screen.
            case .chevronLeft:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 15, y: 18)); b.addLine(to: .init(x: 9, y: 12))
                    b.addLine(to: .init(x: 15, y: 6))
                }
            case .arrowLeft:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 19, y: 12)); b.addLine(to: .init(x: 5, y: 12))
                    b.move(to: .init(x: 12, y: 19)); b.addLine(to: .init(x: 5, y: 12))
                    b.addLine(to: .init(x: 12, y: 5))
                }
            case .arrowRight:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 5, y: 12)); b.addLine(to: .init(x: 19, y: 12))
                    b.move(to: .init(x: 12, y: 5)); b.addLine(to: .init(x: 19, y: 12))
                    b.addLine(to: .init(x: 12, y: 19))
                }
            case .close:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 18, y: 6)); b.addLine(to: .init(x: 6, y: 18))
                    b.move(to: .init(x: 6, y: 6)); b.addLine(to: .init(x: 18, y: 18))
                }
            case .check:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 20, y: 6)); b.addLine(to: .init(x: 9, y: 17))
                    b.addLine(to: .init(x: 4, y: 12))
                }
            // The visibility band's mark (SHOWUP-154 callout 11). Feather's eye-off: the eye's
            // two arcs with the pupil, struck through corner to corner. Same 24 grid as the rest.
            case .eyeOff:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 17.94, y: 17.94))
                    b.addCurve(to: .init(x: 12, y: 20),
                               control1: .init(x: 16.23, y: 19.24), control2: .init(x: 14.15, y: 19.97))
                    b.addCurve(to: .init(x: 1, y: 12),
                               control1: .init(x: 5, y: 20), control2: .init(x: 1, y: 12))
                    b.addCurve(to: .init(x: 6.06, y: 6.06),
                               control1: .init(x: 2.24, y: 9.68), control2: .init(x: 3.97, y: 7.65))
                    b.move(to: .init(x: 9.9, y: 4.24))
                    b.addCurve(to: .init(x: 12, y: 4),
                               control1: .init(x: 10.59, y: 4.08), control2: .init(x: 11.29, y: 4))
                    b.addCurve(to: .init(x: 23, y: 12),
                               control1: .init(x: 19, y: 4), control2: .init(x: 23, y: 12))
                    b.addCurve(to: .init(x: 20.84, y: 15.19),
                               control1: .init(x: 22.39, y: 13.13), control2: .init(x: 21.68, y: 14.2))
                    b.move(to: .init(x: 14.12, y: 14.12))
                    b.addCurve(to: .init(x: 11.93, y: 15.1),
                               control1: .init(x: 13.55, y: 14.73), control2: .init(x: 12.76, y: 15.09))
                    b.addCurve(to: .init(x: 8.87, y: 12.04),
                               control1: .init(x: 10.24, y: 15.1), control2: .init(x: 8.87, y: 13.73))
                    b.addCurve(to: .init(x: 9.85, y: 9.85),
                               control1: .init(x: 8.88, y: 11.21), control2: .init(x: 9.24, y: 10.42))
                    b.move(to: .init(x: 1, y: 1))
                    b.addLine(to: .init(x: 23, y: 23))
                }
            case .shield:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 12, y: 22))
                    b.addCurve(to: .init(x: 20, y: 12), control1: .init(x: 12, y: 22), control2: .init(x: 20, y: 18))
                    b.addLine(to: .init(x: 20, y: 5)); b.addLine(to: .init(x: 12, y: 2))
                    b.addLine(to: .init(x: 4, y: 5)); b.addLine(to: .init(x: 4, y: 12))
                    b.addCurve(to: .init(x: 12, y: 22), control1: .init(x: 4, y: 18), control2: .init(x: 12, y: 22))
                    b.closeSubpath()
                }
            case .heart:
                filled = true; p = Self.heartPath
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
        }
        .frame(width: size, height: size)
    }

    /// On the same 24-unit grid as every other icon here. Hoisted to a static because the
    /// headline's trailing mark rasterises it through UIKit -- one copy of the geometry, drawn two
    /// ways, so the two cannot drift.
    /// How far a mark's ink is from the centre of its artboard, in 24-grid units.
    ///
    /// Only the provider logos are handled: they are the only marks ever shown standing alone.
    /// Anything else returns zero, so an un-tabulated icon is drawn exactly as authored rather
    /// than nudged by a guess.
    static func inkOffset(_ icon: BrandIcon) -> CGPoint {
        let paths: [Path]
        switch icon {
        case .apple: paths = [applePath]
        case .facebook: paths = [facebookPath]
        case .google: paths = [googleBlue, googleGreen, googleYellow, googleRed]
        default: return .zero
        }
        let box = paths.map(\.boundingRect).reduce(CGRect.null) { $0.union($1) }
        guard !box.isNull, !box.isEmpty else { return .zero }
        return CGPoint(x: 12 - box.midX, y: 12 - box.midY)
    }

    static let heartPath = Path { b in
        b.move(to: .init(x: 12, y: 21))
        b.addCurve(to: .init(x: 3.5, y: 10), control1: .init(x: 12, y: 21), control2: .init(x: 3.5, y: 15.4))
        b.addCurve(to: .init(x: 12, y: 7.2), control1: .init(x: 3.5, y: 6.5), control2: .init(x: 8.5, y: 4.7))
        b.addCurve(to: .init(x: 20.5, y: 10), control1: .init(x: 15.5, y: 4.7), control2: .init(x: 20.5, y: 6.5))
        b.addCurve(to: .init(x: 12, y: 21), control1: .init(x: 20.5, y: 15.4), control2: .init(x: 12, y: 21))
        b.closeSubpath()
    }

    /// The same path, rasterised through UIKit for use as a text attachment.
    ///
    /// Deliberately NOT ImageRenderer: that is @MainActor, `updateUIView` is not annotated as such
    /// in the Swift 5 language mode this target builds in, and the resulting isolation error would
    /// only surface on the Mac. UIGraphicsImageRenderer has no such constraint.
    static func filledImage(_ path: Path, size: CGFloat, tint: UIColor) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: size, height: size)).image { _ in
            let scaled = path.applying(CGAffineTransform(scaleX: size / 24, y: size / 24))
            tint.setFill()
            UIBezierPath(cgPath: scaled.cgPath).fill()
        }
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
