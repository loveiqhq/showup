//  ShowUpEveryTimeView.swift
//  ShowUp · Tutorial screen 6 — "Show up, every time" (SHOWUP-139)
//
//  The last screen. Screens 2-5 sell the product; this one states how reliability is measured and
//  enforced, and the CTA is the moment the user accepts it.
//
//  Three documented variations on the shell, all parameters rather than forks:
//    1. Terminal CTA — "I’m ready to show up" with the sunset-gradient circle.
//    2. Statement list — five single-colour rows, NOT the two-tone rule/consequence pattern of
//       screens 2-5. That pattern sells a benefit; this states policy. Do not "fix" it to match.
//    3. A closing paragraph, the only body paragraph in screens 3-6.
//
//  Height: the tallest screen in the set. The illustration is drawn at 0.62 and must shrink further
//  on short frames — never the type, never the nav row, never a scroll.
//
//  Built to 05-show-up-every-time-spec-sheet.

import SwiftUI
import UIKit

struct ShowUpEveryTimeView: View {
    var onFinish: () -> Void = {}
    var onBack: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 5,                              // all five segments filled
            totalSteps: 5,
            eyebrow: "Show up, every time",
            nextLabel: "I’m ready to show up",
            nextVariant: .sunset,                 // variation 1
            showBack: true,
            onNext: {
                // completion of the whole tutorial, not of one screen
                analytics.track(TutorialAnalytics.completed, properties: TutorialAnalytics.showUpRate)
                onFinish()
            },
            onBack: {
                analytics.track(TutorialAnalytics.backTapped, properties: TutorialAnalytics.showUpRate)
                onBack()
            },
            headline: {
                Text(TypeMetrics.attributed(
                    runs: [
                        ("If you don’t show up, ", TypeMetrics.uiFont(PS.loraBold, 34,
                            fallback: .systemFont(ofSize: 34, weight: .bold))),
                        ("there’s a cost", TypeMetrics.uiFont(PS.loraBoldItalic, 34,
                            fallback: TypeMetrics.italicSystem(34))),
                        (".", TypeMetrics.uiFont(PS.loraBold, 34,
                            fallback: .systemFont(ofSize: 34, weight: .bold))),
                    ],
                    size: 34, multiple: 1.1,
                    color: UIColor(Color.liqFg), trackingEm: -0.015))
                    .fixedSize(horizontal: false, vertical: true)
            },
            content: {
                VStack(alignment: .leading, spacing: 0) {
                    // variation 2 — statements, one colour, gap 11
                    VStack(alignment: .leading, spacing: 11) {
                    StatementRow(text: "Every profile has a Show-up Rate.")
                    StatementRow(text: "Showing up to dates is reflected positively.")
                    StatementRow(text: "Not showing up is reflected negatively.")
                    StatementRow(text: "A persistently low Show-up Rate reduces your visibility to others.")
                    StatementRow(text: "Miss a date without fair notice and you can’t search for new dates for 24 hours.")
                    }
                    Spacer().frame(height: 5)
                    // variation 3 — closing paragraph, lead clause bold
                    Text(TypeMetrics.attributed(
                        colouredRuns: [
                            ("Show Up is for reliable people.", TypeMetrics.uiFont(PS.manropeBold, 14.5,
                                fallback: .systemFont(ofSize: 14.5, weight: .bold)), UIColor(Color.liqFg)),
                            (" Life happens. Stay fair and show respect for each other, and your Show-up Rate will reflect it.",
                             TypeMetrics.uiFont(PS.manropeMedium, 14.5,
                                fallback: .systemFont(ofSize: 14.5, weight: .medium)), UIColor(Color.liqNeutral)),
                        ],
                        size: 14.5, multiple: 1.5))
                        .fixedSize(horizontal: false, vertical: true)
                }
            },
            art: { IllustrationPlaceholder(scale: 0.62) }
        )
        .onAppear {
            analytics.track(TutorialAnalytics.cardViewed, properties: TutorialAnalytics.showUpRate)
        }
    }
}

#Preview("375 x 667 - iPhone SE") { ShowUpEveryTimeView() }
#Preview("390 x 844 - reference") { ShowUpEveryTimeView() }
#Preview("430 x 932 - Pro Max")   { ShowUpEveryTimeView() }
