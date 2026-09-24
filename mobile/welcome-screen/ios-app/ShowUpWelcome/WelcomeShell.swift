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
/// Where the two orbs sit, and how big they are.
///
/// Four recipes, because four groups of screens ask for four. They were a boolean until 15
/// September 2026, when "The real you" arrived with two more — and a second backdrop component
/// would have been the primary button all over again. The differences are small and deliberate:
/// every one of them is a number the reference file for that screen sets, and none of them mean
/// anything on their own, which is why they live here as named recipes rather than as five
/// properties a call site has to get right.
enum OrbPlacement {
    /// The voice capture screen: orange 520 at top -25% / right -30%, violet 520 at BOTTOM -22% /
    /// left -30%, both a touch more saturated than elsewhere (SHOWUP-161).
    ///
    /// A fifth placement rather than the nearest existing one, because this is the only screen in
    /// the app with no chrome at all — no header, no progress bar, no footer — so the orbs ARE the
    /// composition rather than atmosphere behind one, and 40pt of orb radius is visible where it
    /// would not be on a screen with content over it.
    case voiceCapture

    /// Startup and Welcome back: orange 520 at top -15% / right -25%, violet 600 at bottom -20% / left -30%.
    case startup
    /// Phone verification: orange 460 at top -18% / right -30%, violet 420 at top -10% / left -25%.
    case phoneVerify
    /// Profile photos: orange 460 at top -22% / right -30%, violet 420 at top -12% / left -28%.
    ///
    /// Both orbs at the top, because the screen below them scrolls: an orb anchored to the bottom
    /// of a scrolling screen sits behind the sticky footer, where it reads as a smudge under the
    /// CTA rather than as atmosphere.
    case realYouTop
    /// Profile prompts: orange 460 at top -20% / right -25%, violet 420 at BOTTOM -12% / left -28%.
    ///
    /// The one split pair. This screen's footer is a bare CTA row with no gradient mask over it, so
    /// there is nothing for a low violet to muddy — and the suggestion cards fill the middle, which
    /// a second top orb would sit behind.
    case realYouSplit
}

struct WelcomeBackdrop: View {
    var peachWash: Bool = true
    var placement: OrbPlacement = .startup
    var orangeAlpha: Double = 0.32
    var violetAlpha: Double = 0.28

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack(alignment: .topLeading) {
                switch placement {
                case .phoneVerify:
                    orb(460, .liqOrange, orangeAlpha)
                        .position(x: w * 1.30 - 230, y: -0.18 * h + 230)
                    orb(420, .liqPurple, violetAlpha)
                        .position(x: -0.25 * w + 210, y: -0.10 * h + 210)
                case .realYouTop:
                    orb(460, .liqOrange, orangeAlpha)
                        .position(x: w * 1.30 - 230, y: -0.22 * h + 230)
                    orb(420, .liqPurple, violetAlpha)
                        .position(x: -0.28 * w + 210, y: -0.12 * h + 210)
                case .realYouSplit:
                    orb(460, .liqOrange, orangeAlpha)
                        .position(x: w * 1.25 - 230, y: -0.20 * h + 230)
                    orb(420, .liqPurple, violetAlpha)
                        .position(x: -0.28 * w + 210, y: h * 1.12 - 210)
                case .voiceCapture:
                    orb(520, .liqOrange, orangeAlpha)
                        .position(x: w * 1.30 - 260, y: -0.25 * h + 260)
                    orb(520, .liqPurple, violetAlpha)
                        .position(x: -0.30 * w + 260, y: h * 1.22 - 260)
                case .startup:
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
    var placement: OrbPlacement = .startup
    var orangeAlpha: Double = 0.32
    var violetAlpha: Double = 0.28
    var topPadding: CGFloat = 20
    /// The side gutter.
    ///
    /// `Spacing.screenGutter` on every screen this scaffold was written for. A property because
    /// the embrace bridge (SHOWUP-155) draws at 28 — its reference sets `padding: '64px 28px 0'`
    /// on the headline and `'28px 28px 0'` on the body, and a bridge is a wider, quieter beat than
    /// the screens either side of it. Not a token: 28 means "this one screen", which is exactly
    /// the case the design-system rules say to keep local.
    var gutter: CGFloat = Spacing.screenGutter
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
            WelcomeBackdrop(peachWash: peachWash, placement: placement,
                            orangeAlpha: orangeAlpha, violetAlpha: violetAlpha)
                .ignoresSafeArea()

            if scrollWhenTight {
                GeometryReader { geo in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 0) { content() }
                            .padding(.horizontal, gutter)
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
                    .padding(.horizontal, gutter)
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
    /// `text-wrap: balance`, which CSS has and SwiftUI does not.
    ///
    /// The references set it on these headlines and the criteria name it. A greedy wrap fills each
    /// line to the edge and leaves whatever is left on the last one; balance evens them. On the
    /// notifications ask at 390 the two disagree and the artboard shows the balanced form.
    ///
    /// HOW. Balance is "the narrowest width that still fits in the same number of lines". The
    /// UILabel this wraps already reports its height at a given width, so the search is over that:
    /// measure at the full width to learn the line count, then binary-search downward for the
    /// narrowest width whose height is unchanged. Eight measurements, not three hundred.
    ///
    /// OFF BY DEFAULT: it moves where existing headlines break.
    var balance: Bool = false
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
        // The rasterised trailing mark is Connect's, and Connect draws the FILLED heart.
        case .heartFilled: path = BrandIconView.heartPath
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
        let used = balance ? balancedWidth(for: v, within: width) : width
        v.preferredMaxLayoutWidth = used
        let fit = v.sizeThatFits(CGSize(width: used, height: .greatestFiniteMagnitude))
        return CGSize(width: min(fit.width, width), height: fit.height)
    }

    /// The narrowest width that still fits in the same number of lines. See `balance`.
    ///
    /// Searched over HEIGHT rather than line count, because the label reports height and height is
    /// a faithful proxy: one more line is one more line's worth of points, and nothing else here
    /// changes it. Binary search over whole points, so eight probes settle a 400-point range.
    private func balancedWidth(for v: WashLabel, within full: CGFloat) -> CGFloat {
        let tallest = v.sizeThatFits(
            CGSize(width: full, height: .greatestFiniteMagnitude)
        ).height
        var low: CGFloat = 1
        var high = full
        while high - low > 1 {
            let mid = ((low + high) / 2).rounded()
            let h = v.sizeThatFits(
                CGSize(width: mid, height: .greatestFiniteMagnitude)
            ).height
            if h <= tallest { high = mid } else { low = mid }
        }
        return high
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
                 chevronRight, arrowLeft, arrowRight, pencil, pen, edit, close, check,
                 shield, heart, eyeOff, plus, image, camera, lock, sliders,
                 // The media step (SHOWUP-161).
                 video, mic, play, refresh, trash,
                 // The notifications ask (SHOWUP-162). `x` is `close`, already at the kit's
                 // geometry. `heartFilled` is the kit's own second heart -- see heartPath.
                 sparkles, messageCircle, clock, heartFilled }

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

            // ── added for "The real you" (SHOWUP-156 / SHOWUP-158) ────────────────
            //
            // Every one is copied from `components/shared.jsx`, the design system's own icon set,
            // at its exact 24-grid geometry — not from a screen's inline SVG and not from memory.
            // The back control was drawn `arrow-left` where the design says `chevron-left` once
            // already, and no test catches that.
            case .chevronRight:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 9, y: 18)); b.addLine(to: .init(x: 15, y: 12))
                    b.addLine(to: .init(x: 9, y: 6))
                }
            case .plus:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 12, y: 5)); b.addLine(to: .init(x: 12, y: 19))
                    b.move(to: .init(x: 5, y: 12)); b.addLine(to: .init(x: 19, y: 12))
                }
            case .image:
                filled = false
                p = Path { b in
                    b.addRoundedRect(in: CGRect(x: 3, y: 3, width: 18, height: 18),
                                     cornerSize: CGSize(width: 2, height: 2))
                    b.addEllipse(in: CGRect(x: 6.9, y: 6.9, width: 3.2, height: 3.2))
                    b.move(to: .init(x: 21, y: 15)); b.addLine(to: .init(x: 16, y: 10))
                    b.addLine(to: .init(x: 5, y: 21))
                }
            case .camera:
                filled = false
                p = Path { b in
                    // The body: a 2-radius rounded rect with a notch cut into its top edge for the
                    // lens housing. One path rather than a rect plus a trapezium, so the stroke has
                    // no join artefacts where the notch meets the edge.
                    b.move(to: .init(x: 23, y: 19))
                    b.addArc(center: .init(x: 21, y: 19), radius: 2,
                             startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
                    b.addLine(to: .init(x: 3, y: 21))
                    b.addArc(center: .init(x: 3, y: 19), radius: 2,
                             startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
                    b.addLine(to: .init(x: 1, y: 8))
                    b.addArc(center: .init(x: 3, y: 8), radius: 2,
                             startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
                    b.addLine(to: .init(x: 7, y: 6)); b.addLine(to: .init(x: 9, y: 3))
                    b.addLine(to: .init(x: 15, y: 3)); b.addLine(to: .init(x: 17, y: 6))
                    b.addLine(to: .init(x: 21, y: 6))
                    b.addArc(center: .init(x: 21, y: 8), radius: 2,
                             startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
                    b.closeSubpath()
                    b.addEllipse(in: CGRect(x: 8, y: 9, width: 8, height: 8))
                }
            case .lock:
                filled = false
                p = Path { b in
                    b.addRoundedRect(in: CGRect(x: 3, y: 11, width: 18, height: 11),
                                     cornerSize: CGSize(width: 2, height: 2))
                    // The shackle: up, over the top as a true semicircle, back down.
                    b.move(to: .init(x: 7, y: 11)); b.addLine(to: .init(x: 7, y: 7))
                    b.addArc(center: .init(x: 12, y: 7), radius: 5,
                             startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
                    b.addLine(to: .init(x: 17, y: 11))
                }
            case .sliders:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 4, y: 21)); b.addLine(to: .init(x: 4, y: 14))
                    b.move(to: .init(x: 4, y: 10)); b.addLine(to: .init(x: 4, y: 3))
                    b.move(to: .init(x: 12, y: 21)); b.addLine(to: .init(x: 12, y: 12))
                    b.move(to: .init(x: 12, y: 8)); b.addLine(to: .init(x: 12, y: 3))
                    b.move(to: .init(x: 20, y: 21)); b.addLine(to: .init(x: 20, y: 16))
                    b.move(to: .init(x: 20, y: 12)); b.addLine(to: .init(x: 20, y: 3))
                    b.move(to: .init(x: 1, y: 14)); b.addLine(to: .init(x: 7, y: 14))
                    b.move(to: .init(x: 9, y: 8)); b.addLine(to: .init(x: 15, y: 8))
                    b.move(to: .init(x: 17, y: 16)); b.addLine(to: .init(x: 23, y: 16))
                }
            // `edit` from the shared set: a pen over a baseline stroke. The arc in the source path
            // — `a2.121 2.121 0 0 1 3 3` — is a true semicircle, because the chord (3, 3) has
            // length 4.243 and the radius is 2.121, so 2r is the chord. Centre is the midpoint.
            case .edit:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 12, y: 20)); b.addLine(to: .init(x: 21, y: 20))
                    b.move(to: .init(x: 16.5, y: 3.5))
                    b.addArc(center: .init(x: 18, y: 5), radius: 2.121,
                             startAngle: .degrees(225), endAngle: .degrees(45), clockwise: false)
                    b.addLine(to: .init(x: 7, y: 19)); b.addLine(to: .init(x: 3, y: 20))
                    b.addLine(to: .init(x: 4, y: 16))
                    b.closeSubpath()
                }
            // The prompts card's edit pip. A pen with NO baseline stroke, which is what makes it a
            // different glyph from `edit` rather than the same one at another size — SHOWUP-158's
            // reference draws this one inline.
            case .pen:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 17, y: 3))
                    b.addArc(center: .init(x: 19, y: 5), radius: 2.83,
                             startAngle: .degrees(225), endAngle: .degrees(45), clockwise: false)
                    b.addLine(to: .init(x: 7.5, y: 20.5)); b.addLine(to: .init(x: 2, y: 22))
                    b.addLine(to: .init(x: 3.5, y: 16.5))
                    b.closeSubpath()
                    b.move(to: .init(x: 15, y: 5)); b.addLine(to: .init(x: 19, y: 9))
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
            // ── the media step (SHOWUP-161) ─────────────────────────────────
            //
            // All five copied from `components/shared.jsx` at its exact 24-grid geometry, like the
            // rest of the set and for the same reason: an icon redrawn from memory is a real icon,
            // faithfully drawn, and the wrong one -- which no test catches. The Kotlin twin carries
            // the identical coordinates.
            case .video:
                filled = false
                p = Path { b in
                    // polygon 23 7 -> 16 12 -> 23 17, the lens flare.
                    b.move(to: .init(x: 23, y: 7)); b.addLine(to: .init(x: 16, y: 12))
                    b.addLine(to: .init(x: 23, y: 17)); b.closeSubpath()
                    b.addRoundedRect(in: .init(x: 1, y: 5, width: 15, height: 14),
                                     cornerSize: .init(width: 2, height: 2))
                }
            case .mic:
                filled = false
                p = Path { b in
                    // The capsule is a 6x12 rect at radius 3 -- a rounded rect whose radius is half
                    // its width, which is a capsule exactly.
                    b.addRoundedRect(in: .init(x: 9, y: 2, width: 6, height: 12),
                                     cornerSize: .init(width: 3, height: 3))
                    // `M5 11 a7 7 0 0 0 14 0` -- the lower half of a circle centred (12,11) r 7.
                    b.move(to: .init(x: 5, y: 11))
                    b.addArc(center: .init(x: 12, y: 11), radius: 7,
                             startAngle: .degrees(180), endAngle: .degrees(0), clockwise: true)
                    b.move(to: .init(x: 12, y: 18)); b.addLine(to: .init(x: 12, y: 22))
                    b.move(to: .init(x: 8, y: 22)); b.addLine(to: .init(x: 16, y: 22))
                }
            // FILLED, not stroked. The design draws it as a solid polygon in every size it appears
            // at, the same way the provider marks are filled paths in the source.
            case .play:
                filled = true
                p = Path { b in
                    b.move(to: .init(x: 6, y: 4)); b.addLine(to: .init(x: 20, y: 12))
                    b.addLine(to: .init(x: 6, y: 20)); b.closeSubpath()
                }
            case .refresh:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 23, y: 4)); b.addLine(to: .init(x: 23, y: 10))
                    b.addLine(to: .init(x: 17, y: 10))
                    b.move(to: .init(x: 1, y: 20)); b.addLine(to: .init(x: 1, y: 14))
                    b.addLine(to: .init(x: 7, y: 14))
                    // Both 9-unit arcs run on the circle centred (12,12) -- solved from the SVG's
                    // endpoints rather than eyeballed, which is why the sweeps are the same 115.5
                    // degrees in opposite directions.
                    b.move(to: .init(x: 3.51, y: 9))
                    b.addArc(center: .init(x: 12, y: 12), radius: 9,
                             startAngle: .degrees(-160.5), endAngle: .degrees(-45), clockwise: true)
                    b.addLine(to: .init(x: 23, y: 10))
                    b.move(to: .init(x: 1, y: 14))
                    b.addLine(to: .init(x: 5.64, y: 18.36))
                    b.addArc(center: .init(x: 12, y: 12), radius: 9,
                             startAngle: .degrees(135), endAngle: .degrees(19.5), clockwise: false)
                }
            case .trash:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 3, y: 6)); b.addLine(to: .init(x: 21, y: 6))
                    // The can. Its lower corners are 2-unit rounds, drawn as quadratics with the
                    // control point at the corner the curve replaces -- indistinguishable from the
                    // SVG arc at every size this is drawn at, and far less arithmetic.
                    b.move(to: .init(x: 19, y: 6)); b.addLine(to: .init(x: 18, y: 20))
                    b.addQuadCurve(to: .init(x: 16, y: 22), control: .init(x: 18, y: 22))
                    b.addLine(to: .init(x: 8, y: 22))
                    b.addQuadCurve(to: .init(x: 6, y: 20), control: .init(x: 6, y: 22))
                    b.addLine(to: .init(x: 5, y: 6))
                    b.move(to: .init(x: 10, y: 11)); b.addLine(to: .init(x: 10, y: 17))
                    b.move(to: .init(x: 14, y: 11)); b.addLine(to: .init(x: 14, y: 17))
                    // The lid's handle, 1-unit rounds.
                    b.move(to: .init(x: 9, y: 6)); b.addLine(to: .init(x: 9, y: 4))
                    b.addQuadCurve(to: .init(x: 10, y: 3), control: .init(x: 9, y: 3))
                    b.addLine(to: .init(x: 14, y: 3))
                    b.addQuadCurve(to: .init(x: 15, y: 4), control: .init(x: 15, y: 3))
                    b.addLine(to: .init(x: 15, y: 6))
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
                filled = false; p = Self.heartPath
            case .heartFilled:
                filled = true; p = Self.heartPath
            // ── the notifications ask (SHOWUP-162) ───────────────────────────────
            //
            // From `components/shared.jsx` at its 24-grid geometry. `sparkles` IS NOT A LUCIDE
            // SPARKLE: the kit draws an eight-ray starburst, four axis rays and four diagonals,
            // not the familiar four-point twinkle. Copied as drawn.
            case .sparkles:
                filled = false
                p = Path { b in
                    for (from, to) in [
                        (CGPoint(x: 12, y: 3), CGPoint(x: 12, y: 6)),
                        (CGPoint(x: 12, y: 18), CGPoint(x: 12, y: 21)),
                        (CGPoint(x: 3, y: 12), CGPoint(x: 6, y: 12)),
                        (CGPoint(x: 18, y: 12), CGPoint(x: 21, y: 12)),
                        (CGPoint(x: 5.6, y: 5.6), CGPoint(x: 7.6, y: 7.6)),
                        (CGPoint(x: 16.4, y: 16.4), CGPoint(x: 18.4, y: 18.4)),
                        (CGPoint(x: 5.6, y: 18.4), CGPoint(x: 7.6, y: 16.4)),
                        (CGPoint(x: 16.4, y: 7.6), CGPoint(x: 18.4, y: 5.6)),
                    ] {
                        b.move(to: from); b.addLine(to: to)
                    }
                }
            case .messageCircle:
                filled = false
                p = Path { b in
                    b.move(to: .init(x: 21, y: 11.5))
                    b.addCurve(to: .init(x: 20.1, y: 15.3),
                               control1: .init(x: 21, y: 12.84), control2: .init(x: 20.69, y: 14.15))
                    b.addCurve(to: .init(x: 12.5, y: 20),
                               control1: .init(x: 18.66, y: 18.18), control2: .init(x: 15.72, y: 20))
                    b.addCurve(to: .init(x: 8.7, y: 19.1),
                               control1: .init(x: 11.18, y: 20), control2: .init(x: 9.88, y: 19.69))
                    b.addLine(to: .init(x: 3, y: 21))
                    b.addLine(to: .init(x: 4.9, y: 15.3))
                    b.addCurve(to: .init(x: 4, y: 11.5),
                               control1: .init(x: 4.31, y: 14.12), control2: .init(x: 4, y: 12.82))
                    b.addCurve(to: .init(x: 8.7, y: 3.9),
                               control1: .init(x: 4, y: 8.28), control2: .init(x: 5.82, y: 5.34))
                    b.addCurve(to: .init(x: 12.5, y: 3),
                               control1: .init(x: 9.85, y: 3.31), control2: .init(x: 11.16, y: 3))
                    b.addLine(to: .init(x: 13, y: 3))
                    b.addCurve(to: .init(x: 21, y: 11),
                               control1: .init(x: 17.4, y: 3.25), control2: .init(x: 20.75, y: 6.6))
                    b.closeSubpath()
                }
            case .clock:
                filled = false
                p = Path { b in
                    b.addEllipse(in: .init(x: 3, y: 3, width: 18, height: 18))
                    b.move(to: .init(x: 12, y: 7))
                    b.addLine(to: .init(x: 12, y: 12))
                    b.addLine(to: .init(x: 15, y: 14))
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

    /// `heart` / `heart-filled` from `components/shared.jsx`: one path, drawn two ways.
    ///
    /// `M20.84 4.61 a5.5 5.5 0 0 0-7.78 0 L12 5.67 l-1.06-1.06 a5.5 5.5 0 0 0-7.78 7.78 l1.06 1.06
    /// L12 21.23 l7.78-7.78 1.06-1.06 a5.5 5.5 0 0 0 0-7.78z`
    ///
    /// THE THREE ARCS ARE NOT THE SAME ARC. A chord of length c across radius r subtends
    /// 2 asin(c/2r):
    ///
    ///   arc 1  (20.84, 4.61) to (13.06, 4.61)   chord 7.78 = 5.5 root 2   ->  90 degrees
    ///   arc 2  (10.94, 4.61) to (3.16, 12.39)   chord 11.0 = 2r           -> 180, a semicircle
    ///   arc 3  (20.84, 12.39) to (20.84, 4.61)  chord 7.78                ->  90 degrees
    ///
    /// Arcs 1 and 3 are two pieces of ONE circle, the right lobe centred (16.95, 8.50), separated
    /// in the path by the notch and the point; arc 2 is the left lobe, centred on its own chord's
    /// midpoint (7.05, 8.50) because that is where a semicircle's centre is. Both radii are 5.5.
    ///
    /// REPLACED A HAND-DRAWN ONE. The previous path was neither of the kit's two hearts -- it was
    /// filled, which is `heart-filled`, but drawn from memory. The notifications ask needs the
    /// stroked outline, so both are now the kit's own path under the kit's own names.
    static let heartPath = Path { b in
        b.move(to: .init(x: 20.84, y: 4.61))
        // Right lobe, over the top: anticlockwise from -45 to -135 degrees.
        b.addArc(center: .init(x: 16.95, y: 8.50), radius: 5.5,
                 startAngle: .degrees(-45), endAngle: .degrees(-135), clockwise: true)
        b.addLine(to: .init(x: 12, y: 5.67))
        b.addLine(to: .init(x: 10.94, y: 4.61))
        // Left lobe: a true semicircle, -45 to 135 the long way round the top.
        b.addArc(center: .init(x: 7.05, y: 8.50), radius: 5.5,
                 startAngle: .degrees(-45), endAngle: .degrees(135), clockwise: true)
        b.addLine(to: .init(x: 4.22, y: 13.45))
        b.addLine(to: .init(x: 12, y: 21.23))
        b.addLine(to: .init(x: 19.78, y: 13.45))
        b.addLine(to: .init(x: 20.84, y: 12.39))
        // The right lobe's outer side, closing back to the start.
        b.addArc(center: .init(x: 16.95, y: 8.50), radius: 5.5,
                 startAngle: .degrees(45), endAngle: .degrees(-45), clockwise: true)
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

    // The Apple mark, on the 24 grid, in two subpaths: the body and the leaf.
    //
    // REDRAWN 11 September 2026, because the previous one was not the Apple logo. It was a
    // hand-drawn approximation and it showed: the body had no bite, the shoulders were square,
    // and the leaf sat as a lens floating clear of the fruit. On a 16pt mark inside a black pill
    // it read as a smudge.
    //
    // Apple's Sign in with Apple guidelines say the mark may be scaled but not redrawn, so the
    // one thing this must not be is somebody's impression of an apple. Same geometry as
    // `applePath()` in WelcomeShell.kt, and the same argument as Google's G beside it.
    //
    // Two subpaths, non-zero winding. The bite is not a hole to be subtracted -- it is part of
    // the body's own outline, curving inward on the right.
    static let applePath = Path { b in
        // The body. Starts at the top of the bite and runs anticlockwise around the fruit.
        b.move(to: .init(x: 17.05, y: 12.54))
        b.addCurve(to: .init(x: 19.07, y: 9.01), control1: .init(x: 17.04, y: 10.2), control2: .init(x: 18.98, y: 9.07))
        b.addCurve(to: .init(x: 15.65, y: 7.17), control1: .init(x: 17.97, y: 7.41), control2: .init(x: 16.26, y: 7.19))
        b.addCurve(to: .init(x: 12.06, y: 8.03), control1: .init(x: 14.2, y: 7.02), control2: .init(x: 12.8, y: 8.03))
        b.addCurve(to: .init(x: 8.93, y: 7.21), control1: .init(x: 11.31, y: 8.03), control2: .init(x: 10.16, y: 7.19))
        b.addCurve(to: .init(x: 5.08, y: 9.56), control1: .init(x: 7.35, y: 7.23), control2: .init(x: 5.89, y: 8.14))
        b.addCurve(to: .init(x: 6.27, y: 18.99), control1: .init(x: 3.42, y: 12.43), control2: .init(x: 4.66, y: 16.67))
        b.addCurve(to: .init(x: 9.22, y: 21.36), control1: .init(x: 7.06, y: 20.13), control2: .init(x: 7.99, y: 21.4))
        b.addCurve(to: .init(x: 12.3, y: 20.59), control1: .init(x: 10.41, y: 21.31), control2: .init(x: 10.86, y: 20.59))
        b.addCurve(to: .init(x: 15.39, y: 21.33), control1: .init(x: 13.73, y: 20.59), control2: .init(x: 14.14, y: 21.36))
        b.addCurve(to: .init(x: 18.25, y: 19.04), control1: .init(x: 16.67, y: 21.31), control2: .init(x: 17.47, y: 20.18))
        b.addCurve(to: .init(x: 19.55, y: 16.37), control1: .init(x: 19.16, y: 17.72), control2: .init(x: 19.53, y: 16.44))
        b.addCurve(to: .init(x: 17.05, y: 12.54), control1: .init(x: 19.52, y: 16.36), control2: .init(x: 17.07, y: 15.42))
        b.closeSubpath()
        // The leaf, meeting the body at the stem rather than floating above it.
        b.move(to: .init(x: 14.7, y: 5.64))
        b.addCurve(to: .init(x: 15.68, y: 2.64), control1: .init(x: 15.36, y: 4.85), control2: .init(x: 15.8, y: 3.74))
        b.addCurve(to: .init(x: 12.92, y: 4.06), control1: .init(x: 14.74, y: 2.68), control2: .init(x: 13.6, y: 3.27))
        b.addCurve(to: .init(x: 11.92, y: 6.96), control1: .init(x: 12.31, y: 4.76), control2: .init(x: 11.78, y: 5.89))
        b.addCurve(to: .init(x: 14.7, y: 5.64), control1: .init(x: 12.97, y: 7.04), control2: .init(x: 14.04, y: 6.42))
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
