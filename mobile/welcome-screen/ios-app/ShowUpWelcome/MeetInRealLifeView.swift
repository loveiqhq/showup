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

// MARK: - ⑤ One rule row: dot · rule · faint dash · purple consequence

private struct RuleRow: View {
    let rule: String
    let consequence: String

    var body: some View {
        HStack(alignment: .top, spacing: 9) {
            Circle()
                .fill(Color.liqOrange)
                .frame(width: 6, height: 6)
                .padding(.top, 6)              // sits on the first line's optical centre
            Text(TypeMetrics.attributed(
                colouredRuns: [
                    (rule, TypeMetrics.uiFont(PS.manropeSemi, 13,
                                              fallback: .systemFont(ofSize: 13, weight: .semibold)),
                     UIColor(Color.liqFg)),
                    (" — ", TypeMetrics.uiFont(PS.manropeMedium, 13,
                                               fallback: .systemFont(ofSize: 13, weight: .medium)),
                     UIColor(Color.liqFaint)),
                    (consequence, TypeMetrics.uiFont(PS.manropeSemi, 13,
                                                     fallback: .systemFont(ofSize: 13, weight: .semibold)),
                     UIColor(Color.liqPurple)),
                ],
                size: 13, multiple: 1.4, trackingEm: -0.01
            ))
            .lineLimit(1)                       // spec: no wrap. Verified to fit at 375 width.
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - ⑧ Placeholder for the artwork design will supply
//
// It holds the exact block the real asset gets — 248 x 210, centred, shrinking with the frame — so
// dropping the image in later changes nothing about the layout. Replace the placeholder with
// Image(...).resizable().scaledToFit() and the surrounding code is unchanged.

private struct IllustrationPlaceholder: View {
    var body: some View {
        ZStack {
            // radial glow — kept so the "no banding" criterion stays testable
            RadialGradient(
                gradient: Gradient(stops: [
                    .init(color: .liqOrange.opacity(0.16), location: 0.00),
                    .init(color: .liqOrange.opacity(0.13), location: 0.26),
                    .init(color: .liqPurple.opacity(0.10), location: 0.48),
                    .init(color: .liqPurple.opacity(0.00), location: 0.70),
                ]),
                center: .center, startRadius: 0, endRadius: 120
            )
            .blur(radius: 6)

            GeometryReader { geo in
                let side = min(geo.size.height, 230)
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(
                        Color.liqPurple.opacity(0.38),
                        style: StrokeStyle(lineWidth: 1.5, dash: [7, 6])
                    )
                    .overlay(
                        VStack(spacing: 3) {
                            Image(systemName: "photo")
                                .font(.system(size: 24, weight: .light))
                                .foregroundColor(.liqPurple.opacity(0.5))
                            Text("ILLUSTRATION")
                                .font(F.manrope(11, .bold))
                                .tracking(0.07 * 11)
                                .foregroundColor(.liqPurple.opacity(0.75))
                            Text("248 × 210")
                                .font(F.manrope(10.5, .semibold))
                                .foregroundColor(.liqSubtle)
                        }
                    )
                    .frame(width: side * (248.0 / 210.0), height: side)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(maxHeight: 230)
    }
}

// MARK: - The screen

struct MeetInRealLifeView: View {
    var onNext: () -> Void = {}
    var analytics: AnalyticsTracking = NoOpAnalytics()

    var body: some View {
        TutorialShell(
            step: 1,                          // first of the 5-segment tour
            totalSteps: 5,
            eyebrow: "Meet people in real life",
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
                        RuleRow(rule: "No texting for weeks", consequence: "date in real life instead")
                        RuleRow(rule: "No ghosting", consequence: "we penalize unreliability")
                        RuleRow(rule: "No collecting matches", consequence: "you meet who you match")
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
