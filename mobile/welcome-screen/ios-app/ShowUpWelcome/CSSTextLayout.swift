//
//  CSSTextLayout.swift
//  ShowUp · text laid out the way the references' CSS lays it out: `text-wrap: pretty`, and the CSS
//  line box
//
//  The Swift twin of `designsystem/PrettyWrap.kt` and `designsystem/CssLineBox.kt`; their headers
//  hold the measurements. In short:
//
//  `text-wrap: pretty`. THE RULE IS CHROME'S, MEASURED (headless Chrome, 375 layouts, 14 Oct 2026):
//  it re-wraps only when the greedy last line is ONE WORD -- an em run joined by no-break spaces is
//  one word -- narrower than a third of the width, and then it pulls exactly one word down to it. A
//  two-word last line is never touched however short; a long single word keeps its line. The sheets
//  show it: Check-In 02's subline ends `… See How it works for` / `more info.`, its headline reads
//  `When can you` / `date today?`, where a greedy wrap leaves `info.` and `today?` alone.
//
//  THE CSS LINE BOX. CSS gives every line a box exactly `line-height` tall and centres the font's
//  content area in it, so the first baseline sits `L / 2 + (ascent - descent) / 2` below the top.
//  TextKit with `lineHeightMultiple` takes the difference from above the first line instead, so a
//  two-line Lora 34 / 1.1 headline sat about 3pt high against the sheet. Under the line box the text
//  is moved so its FIRST BASELINE is where CSS puts it, read off the laid-out text's own baselines,
//  and the block reports n lines of L.
//
//  Measured with the same TextKit stack `WashLabel` already uses to find its italic run, so the
//  breaks computed here are the breaks the label makes.
//

import SwiftUI
import UIKit

enum CSSTextLayout {

    /// One layout of a paragraph at a width.
    struct Lines: Equatable {
        /// How many lines it takes.
        let count: Int
        /// Where the last line starts, as a character offset.
        let lastStart: Int
        /// The last line's text, trimmed.
        let lastText: String
        /// The last line's inked width.
        let lastWidth: CGFloat
        /// The first line's baseline, from the top.
        let firstBaseline: CGFloat
    }

    /// `text` laid out at `width` by TextKit, with no padding in the line fragments -- as UILabel
    /// lays it out.
    static func lines(_ text: NSAttributedString, width: CGFloat) -> Lines {
        let storage = NSTextStorage(attributedString: text)
        let manager = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: max(width, 1), height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        storage.addLayoutManager(manager)
        manager.addTextContainer(container)
        manager.ensureLayout(for: container)

        var count = 0
        var firstBaseline: CGFloat = 0
        var lastGlyphs = NSRange(location: 0, length: 0)
        var lastUsed = CGRect.zero
        manager.enumerateLineFragments(forGlyphRange: NSRange(location: 0, length: manager.numberOfGlyphs)) {
            rect, used, _, glyphs, _ in
            if count == 0 {
                firstBaseline = rect.minY + manager.location(forGlyphAt: glyphs.location).y
            }
            count += 1
            lastGlyphs = glyphs
            lastUsed = used
        }
        let chars = manager.characterRange(forGlyphRange: lastGlyphs, actualGlyphRange: nil)
        let whole = text.string as NSString
        let lastText = chars.location == NSNotFound ? "" : whole.substring(with: chars)
        return Lines(count: count,
                     lastStart: chars.location == NSNotFound ? 0 : chars.location,
                     lastText: lastText.trimmingCharacters(in: .whitespacesAndNewlines),
                     lastWidth: lastUsed.width,
                     firstBaseline: firstBaseline)
    }

    /// The width at which `text` wraps as CSS `text-wrap: pretty` would inside `full`: `full`
    /// itself unless the greedy wrap ends on an orphan. Android's `prettyWidthPx`.
    static func prettyWidth(_ text: NSAttributedString, within full: CGFloat) -> CGFloat {
        guard full > 1 else { return full }
        let greedy = lines(text, width: full)
        guard greedy.count > 1 else { return full }
        // An orphan is one word -- no breaking space in it -- under a third of the width.
        if greedy.lastText.contains(" ") || greedy.lastWidth >= full / 3 { return full }
        // Narrowed, a width either still breaks as greedy did or has moved a word down (or gained a
        // line). That test is MONOTONE in the width, so the widest width that moves something is a
        // binary search away. In half points: the label lays out in points, not pixels.
        func moved(_ halves: Int) -> Bool {
            let r = lines(text, width: CGFloat(halves) / 2)
            return r.count > greedy.count || r.lastStart < greedy.lastStart
        }
        var lo = 2
        var hi = Int(full * 2)
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if moved(mid) { lo = mid } else { hi = mid - 1 }
        }
        // Half a point inside the edge where the word comes back, so a label whose own arithmetic
        // differs from TextKit's in the last decimal still breaks where this did.
        for halves in [lo - 1, lo] where halves >= 2 {
            let pulled = lines(text, width: CGFloat(halves) / 2)
            // Only a word pulled down at the SAME line count is Chrome's re-wrap.
            if pulled.count == greedy.count && pulled.lastStart < greedy.lastStart {
                return CGFloat(halves) / 2
            }
        }
        return full
    }

    /// Where CSS puts the first baseline of a line box `lineHeight` tall, for `font`.
    static func cssFirstBaseline(lineHeight: CGFloat, font: UIFont) -> CGFloat {
        lineHeight / 2 + (font.ascender + font.descender) / 2
    }
}

/// A paragraph of body copy with CSS `text-wrap: pretty` -- the No hours today? body, the gate's
/// body, Check-In 02's subline, the uplift inspirations and the footer's toast. Android's
/// `Modifier.prettyWrap` on a `Text`.
///
/// WHY A UIKit WRAPPER (`ios-app/CLAUDE.md`: no new `UIViewRepresentable` without a reason): the
/// re-wrap needs the paragraph's line breaks at a given width -- where the last line starts and how
/// wide it is -- and SwiftUI's `Text` exposes neither. The label is laid out by the same TextKit that
/// measured it, so the breaks are the breaks it draws. Like the CSS it copies, it keeps the whole
/// width it is offered and moves only the breaks: start-aligned at the start, centred in the middle.
struct PrettyText: UIViewRepresentable {
    /// (text, emphasised) -- an emphasised run takes `emphasisFont` and `emphasisColor`.
    let runs: [(String, Bool)]
    let font: UIFont
    let lineHeight: CGFloat
    let color: Color
    var emphasisFont: UIFont? = nil
    var emphasisColor: Color? = nil
    var centered: Bool = false
    var pretty: Bool = true
    /// As wide as its widest line when `pretty` did not narrow it -- a one-line toast shrinks to its
    /// text, as the reference's centred flex column and Android's `prettyWrap` do. Off, the
    /// paragraph keeps every point it is offered.
    var hugs: Bool = false

    /// The type size the user chose, as a factor of the default: 1 at the default size. The fonts
    /// here are UIFonts built at a fixed size, which Dynamic Type never touches on its own -- so at
    /// the largest sizes these paragraphs stayed put while the `F.manrope` text around them grew.
    /// Relative to body, as `F.manrope` text scales.
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    init(_ text: String, font: UIFont, lineHeight: CGFloat, color: Color, centered: Bool = false,
         hugs: Bool = false) {
        self.runs = [(text, false)]
        self.font = font
        self.lineHeight = lineHeight
        self.color = color
        self.centered = centered
        self.hugs = hugs
    }

    init(runs: [(String, Bool)], font: UIFont, lineHeight: CGFloat, color: Color,
         emphasisFont: UIFont?, emphasisColor: Color?, centered: Bool = false) {
        self.runs = runs
        self.font = font
        self.lineHeight = lineHeight
        self.color = color
        self.emphasisFont = emphasisFont
        self.emphasisColor = emphasisColor
        self.centered = centered
    }

    func makeUIView(context: Context) -> PrettyLabel {
        let v = PrettyLabel()
        v.numberOfLines = 0
        v.setContentCompressionResistancePriority(.required, for: .vertical)
        v.setContentHuggingPriority(.required, for: .vertical)
        return v
    }

    func updateUIView(_ v: PrettyLabel, context: Context) {
        let base = font.withSize(font.pointSize * typeScale)
        let emphasis = emphasisFont.map { $0.withSize($0.pointSize * typeScale) }
        let para = NSMutableParagraphStyle()
        // A CSS line-height is a multiple of the FONT SIZE; lineHeightMultiple is a multiple of the
        // font's own line height -- the conversion WashHeadline documents.
        para.lineHeightMultiple = lineHeight * typeScale / base.lineHeight
        para.alignment = centered ? .center : .natural
        let out = NSMutableAttributedString()
        for (text, emphasised) in runs {
            out.append(NSAttributedString(string: text, attributes: [
                .font: emphasised ? (emphasis ?? base) : base,
                .foregroundColor: UIColor(emphasised ? (emphasisColor ?? color) : color),
                .paragraphStyle: para,
            ]))
        }
        v.attributedText = out
        v.pretty = pretty
        v.invalidateIntrinsicContentSize()
        v.setNeedsDisplay()
    }

    /// The whole width offered, and the height of the paragraph broken at its pretty width.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView v: PrettyLabel, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0, width < .infinity,
              let text = v.attributedText else { return nil }
        let used = pretty ? CSSTextLayout.prettyWidth(text, within: width) : width
        let fit = v.sizeThatFits(CGSize(width: used, height: .greatestFiniteMagnitude))
        // Narrowed, it keeps the whole width and the label places the text inside it. Hugging and
        // not narrowed, every line already fits its widest one, so the breaks do not change.
        let hugged = hugs && used >= width ? min(width, ceil(fit.width)) : width
        return CGSize(width: hugged, height: fit.height)
    }
}

/// Draws its paragraph inside its pretty width, placed as the paragraph's alignment says.
final class PrettyLabel: UILabel {
    var pretty = true

    override func drawText(in rect: CGRect) {
        guard pretty, let text = attributedText else { return super.drawText(in: rect) }
        let used = CSSTextLayout.prettyWidth(text, within: rect.width)
        guard used < rect.width else { return super.drawText(in: rect) }
        let centred = textAlignment == .center
            || (text.length > 0
                && (text.attribute(.paragraphStyle, at: 0, effectiveRange: nil) as? NSParagraphStyle)?.alignment == .center)
        let x = centred ? rect.minX + (rect.width - used) / 2 : rect.minX
        super.drawText(in: CGRect(x: x, y: rect.minY, width: used, height: rect.height))
    }
}
