//  ThirtyMinutesView.swift
//  ShowUp · Tutorial screen 5 — "30 minutes, no pressure" (SHOWUP-138)
//
//  Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
//  styling lives here. If something in the chrome needs to change, change it in TutorialShell and
//  re-verify every screen.
//
//  Built to 04-thirty-minutes-spec-sheet.

import SwiftUI
import UIKit

struct ThirtyMinutesView: View {
    var onNext: () -> Void = {}
    var onBack: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 4,
            totalSteps: 5,
            eyebrow: "30 minutes, no pressure",
            nextLabel: "Next",
            showBack: true,
            onNext: {
                analytics.track(TutorialAnalytics.ctaTapped, properties: TutorialAnalytics.thirty)
                onNext()
            },
            onBack: {
                analytics.track(TutorialAnalytics.backTapped, properties: TutorialAnalytics.thirty)
                onBack()
            },
            headline: {
                WashHeadline(
                    parts: [("Just ", false),
                            ("thirty minutes", true),
                            (".", false)],
                    fontSize: 34, lineHeightMultiple: 1.1, trackingEm: -0.015
                )
            },
            content: {
                // ⑤ rule list — gap 12, rows wrap. No body paragraphs here: the slot is simply
                // unused, so it reserves no height.
                VStack(alignment: .leading, spacing: 12) {
                    RuleRow(rule: "Low-pressure 30-minute dates", consequence: "quick, relaxed meetups to see if you click in real life")
                    RuleRow(rule: "30 minutes up", consequence: "stay if you’re vibing, or leave with a smile — no hard feelings")
                    RuleRow(rule: "Built-in icebreakers", consequence: "fun, easy prompts to keep the conversation flowing")
                }
            },
            art: { IllustrationPlaceholder() }
        )
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed, properties: TutorialAnalytics.thirty)
        }
    }
}

#Preview("375 x 667 - iPhone SE") { ThirtyMinutesView() }
#Preview("390 x 844 - reference") { ThirtyMinutesView() }
#Preview("430 x 932 - Pro Max")   { ThirtyMinutesView() }
