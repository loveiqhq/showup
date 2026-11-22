// CSSTextLayoutTests.swift
// ShowUp · `text-wrap: pretty` under a line clamp (SHOWUP-193)
//
// A UILabel writes its `lineBreakMode` into its attributed text, and a truncating paragraph lays
// TextKit out on ONE line -- so the Date screen's clamped message preview measured a single line,
// found no orphan, and kept the greedy `… sorry! Almost` / `there.”` where the sheet breaks
// `… sorry!` / `Almost there.”` (iOS evidence C, 4 Nov 2026).

import XCTest
import UIKit
@testable import ShowUpWelcome

@MainActor
final class CSSTextLayoutTests: XCTestCase {

    private let preview = "“Running about 10 min late — sorry! Almost there.”"
    private let font = TypeMetrics.uiFont(PS.manropeMedium, 14, fallback: .systemFont(ofSize: 14, weight: .medium))

    private func paragraph(_ mode: NSLineBreakMode) -> NSAttributedString {
        let style = NSMutableParagraphStyle()
        style.lineBreakMode = mode
        return NSAttributedString(string: preview, attributes: [.font: font, .paragraphStyle: style])
    }

    /// Every width from a narrow phone's card to a wide one's: the clamped text re-wraps exactly where
    /// the unclamped text does -- and at least one width has an orphan to re-wrap, or this proves nothing.
    private func assertClampKeepsTheBreaks(_ clamped: NSAttributedString, _ name: String) {
        let free = paragraph(.byWordWrapping)
        var orphans = 0
        for width in stride(from: 200, through: 380, by: 1) {
            let w = CGFloat(width)
            let expected = CSSTextLayout.prettyWidth(free, within: w)
            if expected < w { orphans += 1 }
            XCTAssertEqual(CSSTextLayout.prettyWidth(clamped, within: w), expected, "\(name) at \(width)")
        }
        XCTAssertGreaterThan(orphans, 0, "no width leaves an orphan")
    }

    func testATruncatingParagraphStillBreaksWhereItsWordsWrap() {
        assertClampKeepsTheBreaks(paragraph(.byTruncatingTail), "truncating paragraph")
    }

    /// The path the screen takes: `PrettyText.lines(2)` sets the label's mode after its text.
    func testAClampedPrettyLabelStillReWrapsItsOrphan() {
        let label = PrettyLabel()
        label.attributedText = paragraph(.byWordWrapping)
        label.numberOfLines = 2
        label.lineBreakMode = .byTruncatingTail
        guard let text = label.attributedText else { return XCTFail("the label lost its text") }
        assertClampKeepsTheBreaks(text, "clamped label")
    }

    // MARK: - what a narrowed label DRAWS

    // The fit sweeps measure frames, not ink: the Coordinate card's `Sort last-minute details before`
    // reserved two lines and drew one (`you meet` lost, iOS evidence D, 3 and 4 Nov 2026). These draw
    // the label as `PrettyText` sizes it and count the bands of ink.

    /// `text` with the paragraph `PrettyText` builds: a CSS line height as a multiple of the font's.
    private func styled(_ text: String, _ font: UIFont, lineHeight: CGFloat) -> NSAttributedString {
        let style = NSMutableParagraphStyle()
        style.lineHeightMultiple = lineHeight / font.lineHeight
        return NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: UIColor.black,
                                                             .paragraphStyle: style])
    }

    /// Rows of ink, top to bottom, as separate bands: one per drawn line.
    private func drawnLines(_ label: PrettyLabel) -> Int {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(bounds: label.bounds, format: format).image { _ in
            label.drawText(in: label.bounds)
        }
        guard let cg = image.cgImage else { return 0 }
        let w = cg.width
        let h = cg.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        let drawn: Bool = bytes.withUnsafeMutableBytes { buffer in
            guard let ctx = CGContext(data: buffer.baseAddress, width: w, height: h, bitsPerComponent: 8,
                                      bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        guard drawn else { return 0 }
        var bands = 0
        var inBand = false
        for y in 0..<h {
            let ink = (0..<w).contains { bytes[(y * w + $0) * 4 + 3] > 96 }
            if ink && !inBand { bands += 1 }
            inBand = ink
        }
        return bands
    }

    /// Every width at which `text` is narrowed: the label, sized as `PrettyText` sizes it, draws
    /// `expected(lines)` lines.
    private func assertDrawsEveryLine(_ text: NSAttributedString, clampedTo maxLines: Int, _ name: String) {
        var narrowed = 0
        for width in stride(from: 150, through: 360, by: 1) {
            let w = CGFloat(width)
            let used = CSSTextLayout.wrapWidth(text, within: w, pretty: true, balance: false)
            guard used < w else { continue }
            narrowed += 1
            let label = PrettyLabel()
            label.attributedText = text
            label.numberOfLines = maxLines
            if maxLines > 0 { label.lineBreakMode = .byTruncatingTail }
            let fit = label.sizeThatFits(CGSize(width: used, height: .greatestFiniteMagnitude))
            label.frame = CGRect(x: 0, y: 0, width: w, height: fit.height)
            let lines = CSSTextLayout.lines(text, width: used).count
            let expected = maxLines > 0 ? min(lines, maxLines) : lines
            XCTAssertEqual(drawnLines(label), expected, "\(name) at \(width) drew a line short")
        }
        XCTAssertGreaterThan(narrowed, 0, "\(name) is never narrowed: the test proves nothing")
    }

    func testANarrowedParagraphDrawsEveryLine() {
        let font = TypeMetrics.uiFont(PS.manropeRegular, 14, fallback: .systemFont(ofSize: 14))
        assertDrawsEveryLine(styled("Sort last-minute details before you meet", font, lineHeight: 14 * 1.35),
                             clampedTo: 0, "the Coordinate sub")
    }

    func testANarrowedClampedPreviewDrawsBothLines() {
        assertDrawsEveryLine(styled(preview, font, lineHeight: 14 * 1.4), clampedTo: 2, "the message preview")
    }

    // MARK: - `text-wrap: balance` never splits a word

    // Below a word's width -- or an em run's, its spaces no-break -- TextKit breaks INSIDE it by character, often at
    // the same line count, so a search for "the narrowest width that keeps the line count" settled there: Android's
    // Date 08 B drew `didn't feel comforta` / `ble in the situation.` on every phone (SHOWUP-199, 22 Nov 2026), and
    // both searches here were the same one. Android's `BalancedWidthTest.kt`, case for case.

    private let bParts: [(String, Bool)] = [("Leonie ended the date, saying they ", false),
                                            ("didn't feel comfortable", true), (" in the situation.", false)]

    /// The search as it was, from 2 half points: the control the sweep compares against.
    private func unfloored(_ text: NSAttributedString, within full: CGFloat) -> CGFloat {
        let target = CSSTextLayout.lines(text, width: full).count
        guard target > 1 else { return full }
        var lo = 2
        var hi = Int(full * 2)
        while lo < hi {
            let mid = (lo + hi) / 2
            if CSSTextLayout.lines(text, width: CGFloat(mid) / 2).count <= target { hi = mid } else { lo = mid + 1 }
        }
        return CGFloat(lo) / 2
    }

    /// Sweeps `full` up from the widest piece: never narrower than it, no word split, and wherever the old search
    /// stayed above the piece the answer is unchanged -- with at least one width where it did not, or this proves
    /// nothing.
    private func assertNeverSplits(_ text: NSAttributedString, _ name: String) {
        let piece = CSSTextLayout.widestUnbreakable(text)
        var wouldSplit = 0
        // From the first half point past the piece: at or under it the answer is `full` itself, by design.
        for halves in stride(from: Int((piece * 2).rounded(.down)) + 1, through: Int(piece * 6), by: 3) {
            let full = CGFloat(halves) / 2
            let got = CSSTextLayout.balancedWidth(text, within: full)
            let old = unfloored(text, within: full)
            if old < piece { wouldSplit += 1 }
            XCTAssertGreaterThanOrEqual(got, piece, "\(name) at \(full)")
            XCTAssertEqual(wordSplits(text, width: got), [], "\(name) at \(full)")
            if old >= piece { XCTAssertEqual(got, old, "\(name) at \(full) moved a width that split nothing") }
        }
        XCTAssertGreaterThan(wouldSplit, 0, "\(name): the old search never went below the piece, so this proves nothing")
    }

    func testARunJoinedByNoBreakSpacesCountsAsOnePiece() {
        let run = washHeadlineText([("didn't feel comfortable", true)])
        let word = washHeadlineText([("comfortable", true)])
        XCTAssertEqual(CSSTextLayout.widestUnbreakable(washHeadlineText(bParts)), CSSTextLayout.widestUnbreakable(run))
        XCTAssertGreaterThan(CSSTextLayout.widestUnbreakable(run), CSSTextLayout.widestUnbreakable(word))
    }

    func testARunJoinedByNoBreakSpacesIsNeverSplit() {
        assertNeverSplits(washHeadlineText(bParts), "B's headline")
    }

    func testASingleLongWordIsNeverSplit() {
        assertNeverSplits(washHeadlineText([("Incomprehensibilities is a word", false)]), "the long word")
    }

    func testAWidthNarrowerThanTheWidestPieceIsKeptWhole() {
        let text = washHeadlineText([("Incomprehensibilities is a word", false)])
        let full = CSSTextLayout.widestUnbreakable(text) - 5
        XCTAssertEqual(CSSTextLayout.balancedWidth(text, within: full), full)
    }

    /// The headline's own path: `WashHost` searches the label's height, not TextKit's line count.
    func testAWashHostNeverNarrowsBelowTheWidestPiece() {
        let text = washHeadlineText(bParts)
        let piece = CSSTextLayout.widestUnbreakable(text)
        let host = WashHost()
        host.label.attributedText = text
        host.balance = true
        for full in stride(from: piece.rounded(.down) + 1, through: piece * 3, by: 1.5) {
            let got = host.lineWidth(within: full)
            XCTAssertGreaterThanOrEqual(got, piece, "at \(full)")
            XCTAssertEqual(wordSplits(text, width: got), [], "at \(full)")
        }
    }
}

/// `parts` as `WashHeadline.updateUIView` builds them at Date 08's type: Lora 700 `size` (27) / 1.15, -0.01em,
/// centred, the italic run in Lora 500 italic with its spaces no-break -- or ordinary, `joinRun` false, the loose text
/// `WashHost` swaps in for a run wider than its box. Rebuilt here because the headline builds it inline.
@MainActor
func washHeadlineText(_ parts: [(String, Bool)], size: CGFloat = 27, joinRun: Bool = true) -> NSAttributedString {
    let regular = TypeMetrics.uiFont(PS.loraBold, size, fallback: .systemFont(ofSize: size, weight: .bold))
    let italic = TypeMetrics.uiFont(PS.loraMediumItalic, size, fallback: TypeMetrics.italicSystem(size))
    let para = NSMutableParagraphStyle()
    para.lineHeightMultiple = (size * 1.15) / regular.lineHeight
    para.alignment = .center
    let out = NSMutableAttributedString()
    for (text, isItalic) in parts {
        out.append(NSAttributedString(
            string: isItalic && joinRun ? text.replacingOccurrences(of: " ", with: "\u{00A0}") : text,
            attributes: [.font: isItalic ? italic : regular, .kern: -0.01 * size, .paragraphStyle: para]))
    }
    return out
}

/// The italic run's range in `parts` joined, as `WashHeadline` records it.
func washRunRange(_ parts: [(String, Bool)]) -> NSRange {
    var location = 0
    for (text, isItalic) in parts {
        let length = (text as NSString).length
        if isItalic { return NSRange(location: location, length: length) }
        location += length
    }
    return NSRange(location: NSNotFound, length: 0)
}

/// A balanced `WashHost` holding `parts` as `updateUIView` hands them over: the run joined, the loose text beside it.
@MainActor
func balancedWashHost(_ parts: [(String, Bool)], size: CGFloat = 27) -> WashHost {
    let host = WashHost()
    host.balance = true
    host.setText(washHeadlineText(parts, size: size), loose: washHeadlineText(parts, size: size, joinRun: false),
                 run: washRunRange(parts))
    return host
}

/// The text under each band of wash `WashLabel` paints for `run` when `text` is laid out at `width`.
@MainActor
func washBands(_ text: NSAttributedString, width: CGFloat, run: NSRange) -> [String] {
    let storage = NSTextStorage(attributedString: text)
    let manager = NSLayoutManager()
    let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
    container.lineFragmentPadding = 0
    storage.addLayoutManager(manager)
    manager.addTextContainer(container)
    manager.ensureLayout(for: container)
    let whole = text.string as NSString
    let glyphs = manager.glyphRange(forCharacterRange: run, actualCharacterRange: nil)
    return WashLabel.washSegments(manager, run: glyphs, in: whole)
        .map { whole.substring(with: manager.characterRange(forGlyphRange: $0, actualGlyphRange: nil)) }
}

/// Every line of `text` at `width` but the last that ends without a breaking space before the break: a split word.
@MainActor
func wordSplits(_ text: NSAttributedString, width: CGFloat) -> [String] {
    let storage = NSTextStorage(attributedString: text)
    let manager = NSLayoutManager()
    let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
    container.lineFragmentPadding = 0
    storage.addLayoutManager(manager)
    manager.addTextContainer(container)
    manager.ensureLayout(for: container)
    var ranges: [NSRange] = []
    manager.enumerateLineFragments(forGlyphRange: NSRange(location: 0, length: manager.numberOfGlyphs)) { _, _, _, glyphs, _ in
        ranges.append(manager.characterRange(forGlyphRange: glyphs, actualGlyphRange: nil))
    }
    let whole = text.string as NSString
    return ranges.dropLast()
        .filter { $0.length > 0 && whole.character(at: NSMaxRange($0) - 1) != 0x20 }
        .map { whole.substring(with: $0) }
}
