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
}
