//
//  SearchCoach.swift
//  ShowUp · the actions coach, Search 01's frame F (callouts 24 and 25)
//
//  The Swift twin of `search/SearchCoach.kt`. A card in the action bar's own column, above the three
//  buttons with a 14 gap, so it follows the bar on every phone and never takes a fixed Y. The scrim,
//  the bar lifted above it and the buttons' inertness are the screen's; this is the card: `Three ways
//  to decide`, one row per button in that button's tone, and `Don't show again` / `Got it`.
//

import SwiftUI

struct SearchActionsCoach: View {
    /// `dontShowAgain` true only for that button.
    let onDone: (Bool) -> Void

    @AccessibilityFocusState private var gotItFocused: Bool

    var body: some View {
        ZStack(alignment: .bottom) {
            // Scrolls inside the card only when the bar's column cannot give it its height -- the
            // largest type on a small phone. Where it fits it is its own height, exactly as drawn.
            ViewThatFits(in: .vertical) {
                card
                ScrollView { card }.scrollBounceBehavior(.basedOnSize)
            }
            // The content is clipped, then the surface drawn behind it: clipped after, the shadow
            // would be cut away with everything else outside the corners.
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .background(
                RoundedRectangle(cornerRadius: 24)
                    .fill(Color.liqElevated)
                    // `0 16px 40px rgba(29,17,41,.28)` -- the card lifted over the scrim.
                    .shadow(color: Color.liqFg.opacity(0.28), radius: 20, y: 16)
            )
            // The caret: a 14 square turned 45 degrees, half below the card, pointing at the buttons.
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.liqElevated)
                .frame(width: 14, height: 14)
                .rotationEffect(.degrees(45))
                .offset(y: 7)
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityLabel(Text(SearchCopy.coachLabel))
        .onAppear { gotItFocused = true }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 14) {
            WashHeadline(parts: [(SearchCopy.coachTitleLead, false), (SearchCopy.coachTitleEmphasis, true)],
                         fontSize: 22, lineHeightMultiple: 1.15, trackingEm: -0.01, cssLineBox: true, heading: true)
            VStack(alignment: .leading, spacing: 12) {
                ForEach(CoachRow.allCases, id: \.self) { row in CoachRowView(row: row) }
            }
            ButtonPair {
                PrimaryButton(SearchCopy.coachDontShow, variant: .ghost, height: 48, labelSize: 15,
                              horizontalPadding: pairButtonPadding) { onDone(true) }
                PrimaryButton(SearchCopy.coachGotIt, variant: .violet, height: 48, labelSize: 15,
                              horizontalPadding: pairButtonPadding) { onDone(false) }
                    .accessibilityFocused($gotItFocused)
            }
        }
        .padding(EdgeInsets(top: 20, leading: 18, bottom: 16, trailing: 18))
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct CoachRowView: View {
    let row: CoachRow

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                switch row.tone {
                case .danger:
                    // `rgba(251,50,59,.4)` -- the Not interested outline, the button's and this row's.
                    Circle().fill(Color.liqElevated)
                    Circle().strokeBorder(Color.liqDanger.opacity(0.4), lineWidth: 1.5)
                case .muted:
                    Circle().fill(Color.liqElevated)
                    Circle().strokeBorder(Color.liqBorder, lineWidth: 1.5)
                case .primary:
                    Circle().fill(Gradients.sunset())
                }
                BrandIconView(icon: row.icon, size: 18, stroke: row.tone == .primary ? 2.4 : 2, tint: ink)
            }
            .frame(width: 38, height: 38)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                // CSS line boxes: at 15 / 1.2 the title's natural box made the three rows 11 taller
                // than the sheet's card (the 184 evidence review).
                Text(row.title).font(F.manrope(15, .bold)).foregroundColor(.liqFg).cssLineBox(15 * 1.2)
                // `text-wrap: pretty`, as the reference sets on each row's line.
                PrettyText(row.body,
                           font: TypeMetrics.uiFont(PS.manropeMedium, 13.5, fallback: .systemFont(ofSize: 13.5, weight: .medium)),
                           lineHeight: 13.5 * 1.4, color: .liqMuted)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var ink: Color {
        switch row.tone {
        case .danger: return .liqDanger
        case .muted: return .liqMuted
        case .primary: return .white
        }
    }
}
