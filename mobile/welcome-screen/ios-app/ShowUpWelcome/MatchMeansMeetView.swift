//  MatchMeansMeetView.swift
//  ShowUp · Tutorial screen 4 — "Match means meet" (SHOWUP-137)
//
//  Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
//  styling lives here. If something in the chrome needs to change, change it in TutorialShell and
//  re-verify every screen.
//
//  Built to 03-match-means-meet-spec-sheet.

import SwiftUI
import UIKit

struct MatchMeansMeetView: View {
    var onNext: () -> Void = {}
    var onBack: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 3,
            totalSteps: 5,
            eyebrow: "Match means meet",
            // accent under "binding"
            underlineWidth: 170,
            nextLabel: "Next",
            showBack: true,
            onNext: {
                analytics.track(TutorialAnalytics.ctaTapped, properties: TutorialAnalytics.binding)
                onNext()
            },
            onBack: {
                analytics.track(TutorialAnalytics.backTapped, properties: TutorialAnalytics.binding)
                onBack()
            },
            headline: {
                Text(TypeMetrics.attributed(
                    runs: [
                        ("A match is a ", TypeMetrics.uiFont(PS.loraBold, 34,
                            fallback: .systemFont(ofSize: 34, weight: .bold))),
                        ("binding", TypeMetrics.uiFont(PS.loraBoldItalic, 34,
                            fallback: TypeMetrics.italicSystem(34))),
                        (" date.", TypeMetrics.uiFont(PS.loraBold, 34,
                            fallback: .systemFont(ofSize: 34, weight: .bold))),
                    ],
                    size: 34, multiple: 1.1,
                    color: UIColor(Color.liqFg), trackingEm: -0.015))
                    .fixedSize(horizontal: false, vertical: true)
            },
            content: {
                // ⑤ rule list — gap 12, rows wrap. No body paragraphs here: the slot is simply
                // unused, so it reserves no height.
                VStack(alignment: .leading, spacing: 12) {
                    RuleRow(rule: "You decide who you like", consequence: "if you match, you will meet")
                    RuleRow(rule: "We suggest the time", consequence: "a date and time that works for both of your schedules")
                    RuleRow(rule: "We pick the place", consequence: "a safe, public spot halfway between you")
                }
            },
            art: { IllustrationPlaceholder() }
        )
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed, properties: TutorialAnalytics.binding)
        }
    }
}

#Preview("375 x 667 - iPhone SE") { MatchMeansMeetView() }
#Preview("390 x 844 - reference") { MatchMeansMeetView() }
#Preview("430 x 932 - Pro Max")   { MatchMeansMeetView() }
