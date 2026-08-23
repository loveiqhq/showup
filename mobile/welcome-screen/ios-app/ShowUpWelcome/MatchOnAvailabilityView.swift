//  MatchOnAvailabilityView.swift
//  ShowUp · Tutorial screen 3 — "Match on availability" (SHOWUP-136)
//
//  Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
//  styling lives here. If something in the chrome needs to change, change it in TutorialShell and
//  re-verify every screen.
//
//  Built to 02-match-on-availability-spec-sheet.

import SwiftUI
import UIKit

struct MatchOnAvailabilityView: View {
    var onNext: () -> Void = {}
    var onBack: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 2,
            totalSteps: 5,
            eyebrow: "Match on availability",
            // accent under "free to date"
            underlineWidth: 210,
            nextLabel: "Next",
            showBack: true,
            onNext: {
                analytics.track(TutorialAnalytics.ctaTapped, properties: TutorialAnalytics.match)
                onNext()
            },
            onBack: {
                analytics.track(TutorialAnalytics.backTapped, properties: TutorialAnalytics.match)
                onBack()
            },
            headline: {
                Text(TypeMetrics.attributed(
                    runs: [
                        ("Match people who are ", TypeMetrics.uiFont(PS.loraBold, 34,
                            fallback: .systemFont(ofSize: 34, weight: .bold))),
                        ("free to date", TypeMetrics.uiFont(PS.loraBoldItalic, 34,
                            fallback: TypeMetrics.italicSystem(34))),
                        (" when you are.", TypeMetrics.uiFont(PS.loraBold, 34,
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
                    RuleRow(rule: "Visible only when you’re free to date", consequence: "check in and state your available times to meet")
                    RuleRow(rule: "Synchronised schedules", consequence: "we only show you people to date who are available when you are")
                    RuleRow(rule: "Different day, different vibe", consequence: "match on what you’re in the mood for right now, not a static bio")
                }
            },
            art: { IllustrationPlaceholder() }
        )
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed, properties: TutorialAnalytics.match)
        }
    }
}

#Preview("375 x 667 - iPhone SE") { MatchOnAvailabilityView() }
#Preview("390 x 844 - reference") { MatchOnAvailabilityView() }
#Preview("430 x 932 - Pro Max")   { MatchOnAvailabilityView() }
