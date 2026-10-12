//
//  SkipContinueRow.swift
//  ShowUp · a footer's `Skip for now` and its Continue — side by side when they fit, stacked when
//  they do not
//
//  The Swift twin of `profile/SkipContinueRow.kt`; its header has the defect this exists for. Three
//  footers pair a skip link with the round Continue (media, the detail steps, Interests / Life /
//  Future), and at the largest text size a plain row could not hold both: Android crushed the circle,
//  and an HStack here truncates both labels. Media had fixed it on Android only.
//
//  `ViewThatFits` IS THE MEASURED RULE HERE: it offers the row first and takes it when its ideal
//  width fits, else the stack — Skip on its own line above, Continue LAST, nearest the thumb, at the
//  trailing edge. No text-size threshold, so a translation decides for itself as English does.
//

import SwiftUI

struct SkipContinueRow: View {
    let skipLabel: String
    let ctaLabel: String
    /// Nil draws Continue alone at the trailing edge — the mandatory detail steps have no skip link.
    let onSkip: (() -> Void)?
    let onContinue: () -> Void
    /// The NextButton's circle: 56 on the detail steps and the picks, 52 on media.
    var circleSize: CGFloat = IconSizes.badge

    var body: some View {
        if let onSkip {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: Spacing.xl) {
                    SkipLink(label: skipLabel, action: onSkip)
                        .fixedSize()
                    Spacer(minLength: 0)
                    cta.fixedSize()
                }
                VStack(alignment: .trailing, spacing: Spacing.md) {
                    SkipLink(label: skipLabel, action: onSkip)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    cta
                }
            }
        } else {
            HStack {
                Spacer(minLength: 0)
                cta
            }
        }
    }

    private var cta: some View {
        NextButton(label: ctaLabel, variant: .orange, arrowSize: 22, circleSize: circleSize, action: onContinue)
    }
}
