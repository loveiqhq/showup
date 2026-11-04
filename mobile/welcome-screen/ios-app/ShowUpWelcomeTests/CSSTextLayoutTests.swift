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
}
