//
//  FitWords.swift
//  ShowUp · wrapping text that never breaks inside a word -- the twin of Android's `Modifier.fitWords`
//
//  SwiftUI breaks a word wider than its line between letters, and cuts a word wider than its slot:
//  at the largest accessibility size on a 320 phone the Date screen read `Coordi` / `nate`,
//  `atten` / `ded` and `Leo...` (the 4 Nov 2026 device audit). CSS keeps a word whole, and Android's
//  `fitWords` shrinks it to its box. This does the same: the text at its own size wherever its widest
//  word fits the width it is offered, and otherwise at the largest of a few smaller sizes where it
//  does -- never under 60 %, or under the default size where the user's type is larger than that (at
//  AX5 a word may shrink to a third, which is still the size it has at the default). Android's floor.
//
//  HOW: `ViewThatFits` tries the sizes in turn and keeps the first whose IDEAL width fits. Each try is
//  laid out by `WordFitLayout`, which reports as its ideal width the widest word's -- measured by a
//  hidden copy of the words in the text's font -- rather than the whole text on one line, which is
//  what a `Text` reports and which a paragraph never fits.
//

import SwiftUI
import UIKit

struct FitWords<Label: View>: View {
    /// The words `label` draws: what is measured.
    let text: String
    /// The words to keep whole, when they are not `text` split at white space: a run the caller holds together (an
    /// emphasis phrase whose spaces do not break) is one word here. nil: `text`'s own words.
    var words: [String]? = nil
    /// Publishes the widest word as drawn -- the hidden copy of the words, at the size chosen -- under this fit anchor
    /// id, for the fit tests (a word wider than its box is a word broken).
    var anchor: String? = nil
    /// `label`'s font at a scale.
    let font: (CGFloat) -> Font
    /// The text at a scale: 1 is its own size.
    @ViewBuilder let label: (CGFloat) -> Label

    /// The type size the user chose, as a factor of the default: how far below 60 % a word may go.
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        ViewThatFits(in: .horizontal) {
            attempt(1)
            attempt(0.9)
            attempt(0.8)
            attempt(0.7)
            attempt(0.6)
            // Below 60 % only while the word stays at least its default size.
            if typeScale >= 2 { attempt(0.5) }
            if typeScale >= 2.5 { attempt(0.4) }
            if typeScale >= 3 { attempt(1 / 3) }
        }
    }

    private func attempt(_ scale: CGFloat) -> some View {
        // Words, and the parts of a hyphenated one marked breakable (`breakAfterHyphens`).
        let kept = words ?? text.split(whereSeparator: { $0.isWhitespace || $0 == "\u{200B}" }).map(String.init)
        return WordFitLayout {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(kept.indices, id: \.self) { i in
                    Text(kept[i]).font(font(scale)).fixedSize()
                }
            }
            .fitAnchor(ifGiven: anchor)
            .hidden()
            .accessibilityHidden(true)
            label(scale)
        }
    }
}

/// The least `FitWords` shrinks a word to at `typeScale` (the type size as a factor of the default): 60 %,
/// and below that only while the word stays at least its default size. Android's `wordFitFloor`.
func wordFitFloor(typeScale: CGFloat) -> CGFloat {
    if typeScale >= 3 { return 1 / 3 }
    if typeScale >= 2.5 { return 0.4 }
    if typeScale >= 2 { return 0.5 }
    return 0.6
}

/// The widest word of `text` in `font`: what `FitWords` shrinks to its box.
func widestWordWidth(_ text: String, _ font: UIFont) -> CGFloat {
    text.split(whereSeparator: { $0.isWhitespace || $0 == "\u{200B}" })
        .map { ceil((String($0) as NSString).size(withAttributes: [.font: font]).width) }
        .max() ?? 0
}

/// Two subviews, the hidden words then the text. Asked for its ideal width, it answers the widest
/// word's; offered a real width, the text takes it.
private struct WordFitLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        guard let width = proposal.width, width.isFinite else {
            let widest = subviews[0].sizeThatFits(.unspecified).width
            let height = subviews[1].sizeThatFits(ProposedViewSize(width: widest, height: nil)).height
            return CGSize(width: widest, height: height)
        }
        return subviews[1].sizeThatFits(ProposedViewSize(width: width, height: proposal.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: .zero)
        subviews[1].place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(bounds.size))
    }
}
