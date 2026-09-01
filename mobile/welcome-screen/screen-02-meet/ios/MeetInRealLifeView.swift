//  MeetInRealLifeView.swift
//  ShowUp · Tutorial screen 2 — "Meet in real life" (SHOWUP-135)
//
//  Screen 2 of the 6-step tutorial, reached from the Welcome screen via "Show me how".
//
//  Everything structural lives in TutorialShell — progress bar, eyebrow pill, headline slot,
//  illustration block, the flexible region and the nav row. This file is content only, which is the
//  point: screens 3-6 are the same shell with different content.
//
//  Built to 01-meet-in-real-life-spec-sheet.

import SwiftUI
import UIKit

// MARK: - The screen

struct MeetInRealLifeView: View {
    var onNext: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 1,                          // first of the 5-segment tour
            totalSteps: 5,
            eyebrow: "Meet people in real life",
            // accent under "actually"
            underlineWidth: 186,
            nextLabel: "Next",
            showBack: false,                  // first screen of the tour — slot reserved, invisible
            onNext: {
                analytics.track(TutorialAnalytics.ctaTapped,
                                properties: TutorialAnalytics.meet)
                onNext()
            },
            headline: {
                // ④ Lora 700 / 34 / 1.1 / -0.015em, "actually" italic
                Text(TypeMetrics.attributed(
                    runs: [
                        ("We want you to ", TypeMetrics.uiFont(
                            PS.loraBold, 34, fallback: .systemFont(ofSize: 34, weight: .bold))),
                        ("actually", TypeMetrics.uiFont(
                            PS.loraBoldItalic, 34, fallback: TypeMetrics.italicSystem(34))),
                        (" meet.", TypeMetrics.uiFont(
                            PS.loraBold, 34, fallback: .systemFont(ofSize: 34, weight: .bold))),
                    ],
                    size: 34, multiple: 1.1,
                    color: UIColor(Color.liqFg), trackingEm: -0.015
                ))
                .fixedSize(horizontal: false, vertical: true)
            },
            content: {
                VStack(alignment: .leading, spacing: 0) {
                    // ⑤ rule list — 3 rows, gap 8, block gap below 16
                    VStack(alignment: .leading, spacing: 8) {
                        RuleRow(rule: "No texting for weeks", consequence: "date in real life instead", wraps: false)
                        RuleRow(rule: "No ghosting", consequence: "we penalize unreliability", wraps: false)
                        RuleRow(rule: "No collecting matches", consequence: "you meet who you match", wraps: false)
                    }
                    Spacer().frame(height: 16)

                    // ⑥ commitment — "will meet" italic 700 · gap below 10
                    Text(TypeMetrics.attributed(
                        runs: [
                            ("If you match here, you ", bodyFont),
                            ("will meet", TypeMetrics.uiFont(
                                PS.manropeBold, 16, fallback: TypeMetrics.italicSystem(16))),
                            (" in real life. A match is a committed date — not a maybe.", bodyFont),
                        ],
                        size: 16, multiple: 1.5, color: UIColor(Color.liqNeutral)
                    ))
                    .fixedSize(horizontal: false, vertical: true)

                    Spacer().frame(height: 10)

                    // ⑦ reliability — two bold runs, no gap below (the flexible region takes over)
                    Text(TypeMetrics.attributed(
                        runs: [
                            ("Showing up to dates boosts your profile", boldBodyFont),
                            (" by highlighting your reliability and increasing your visibility. ", bodyFont),
                            ("Missing dates without fair notice upfront", boldBodyFont),
                            (" reduces your visibility for others.", bodyFont),
                        ],
                        size: 16, multiple: 1.5, color: UIColor(Color.liqNeutral)
                    ))
                    .fixedSize(horizontal: false, vertical: true)
                }
            },
            art: { IllustrationPlaceholder() }
        )
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed,
                            properties: TutorialAnalytics.meet)
        }
    }

    private var bodyFont: UIFont {
        TypeMetrics.uiFont(PS.manropeMedium, 16, fallback: .systemFont(ofSize: 16, weight: .medium))
    }
    private var boldBodyFont: UIFont {
        TypeMetrics.uiFont(PS.manropeBold, 16, fallback: .systemFont(ofSize: 16, weight: .bold))
    }
}

// The three frames the acceptance criteria name. 375 x 667 is the one that fails first.
#Preview("375 x 667 - iPhone SE") { MeetInRealLifeView() }
#Preview("390 x 844 - reference") { MeetInRealLifeView() }
#Preview("430 x 932 - Pro Max")   { MeetInRealLifeView() }
