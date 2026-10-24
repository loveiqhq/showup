//
//  CheckInIntro.swift
//  ShowUp · Check-In 01 (SHOWUP-178): A, the intro -- and B, "date search not available"
//
//  The Swift twin of `checkin/CheckInIntroScreen.kt`. Two screens, one ticket, from
//  `screen-checkin-intro-reference.jsx`.
//
//  A IS A BRIDGE, NOT A STEP: no header title, no back, no step bar, and no swipe back. The close is
//  the only control besides the CTA, in a 52 close row at the leading inset 16 -- the row replaced
//  the 52 of top padding the headline had, so the headline did not move. One flexible spacer
//  between the closing line and the CTA; the scaffold scrolls only where the largest type truly
//  cannot fit.
//
//  B IS THE SEARCH TAB for a user with no committed Check-In -- not a modal, not an error. A
//  wordmark row, a centred statement, one CTA, and the tab bar with Search active. The tabs are not
//  disabled: Search and Instant reopen the Check-In, as the CTA does.
//

import SwiftUI

/// A -- the intro. `onStart` is `Check-in for a date` (single-fire in the model); `onClose` is the
/// close: "No back. Suppress iOS swipe-back."
struct CheckInIntroView: View {
    var onStart: () -> Void = {}
    var onClose: () -> Void = {}

    var body: some View {
        // The bridges' backdrop recipe -- orange .32 top-right, violet .28 bottom-left, peach wash --
        // full-bleed behind the status bar and home indicator. Gutter 16 is the close row's inset;
        // the headline and body add 12 to reach the reference's 28.
        WelcomeScaffold(placement: .startup, orangeAlpha: 0.32, violetAlpha: 0.28,
                        topPadding: 0, gutter: 16, scrollWhenTight: true) {
            HStack(spacing: 0) {
                CheckInCloseButton(onClick: onClose)
                Spacer(minLength: 0)
            }
            .frame(height: 52)

            WashHeadline(parts: CheckInCopy.introHeadline, fontSize: 34, lineHeightMultiple: 1.1,
                         trackingEm: -0.015, balance: true, cssLineBox: true, heading: true)
                .padding(.horizontal, 12)

            VStack(alignment: .leading, spacing: Spacing.xl) {
                ForEach(Array(CheckInCopy.introList.enumerated()), id: \.offset) { i, line in
                    NumberedRow(n: i + 1, text: line)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 28)

            // `textWrap: 'pretty'` in the reference; Lora 400 in `--liq-fg-muted`.
            WashHeadline(parts: CheckInCopy.introClosing, fontSize: 17, lineHeightMultiple: 1.45,
                         trackingEm: 0, bold: false, color: .liqMuted, pretty: true, cssLineBox: true)
                .padding(.horizontal, 12)
                .padding(.top, 22)

            // THE ONLY FLEXIBLE ELEMENT. Never a hard-coded Y.
            Spacer(minLength: 0)

            PrimaryButton(label: CheckInCopy.checkInCta, variant: .sunset, action: onStart,
                          trailing: { CheckInArrow() })
                .padding(.horizontal, 12)
                .padding(.bottom, 22)
        }
    }
}

/// B -- "date search not available": the Search tab with no committed Check-In. `onCheckIn` takes
/// the target, because the CTA, the Search tab and the Instant tab each reopen the Check-In and the
/// event says which; `onSettings` navigates normally. `onLikes` is the Likes tab (SHOWUP-186: in
/// every user's bar, `likes` its badge); where it goes is the host's decision.
struct CheckInGateView: View {
    var onCheckIn: (CheckInAnalytics.GateTarget) -> Void = { _ in }
    var onSettings: () -> Void = {}
    var onLikes: () -> Void = {}
    var likes: Int = 0

    var body: some View {
        VStack(spacing: 0) {
            // The top row: 52, padding 4 / 16 / 8, the wordmark at 20 and nothing else -- 20 whatever
            // the type size: in a fixed 52 row a 2x wordmark is clipped.
            HStack(spacing: 0) {
                Wordmark(size: 20, gapEm: wordmarkTabGap)
                    .dynamicTypeSize(.large)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.md)
            .frame(height: 52)

            // THE STATEMENT BLOCK -- the only flexible element, centred both ways. Floored at the
            // viewport so the largest type scrolls it rather than clipping it.
            GeometryReader { geo in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 0) {
                        // The tile is decorative; the headline is the screen's title.
                        ZStack {
                            RoundedRectangle(cornerRadius: 20).fill(Gradients.lilac())
                            BrandIconView(icon: .calendar, size: 28, stroke: 1.9, tint: .liqPurple)
                        }
                        .frame(width: 64, height: 64)
                        .accessibilityHidden(true)

                        WashHeadline(parts: CheckInCopy.gateHeadline, fontSize: 30, lineHeightMultiple: 1.12,
                                     trackingEm: -0.015, balance: true, centered: true, cssLineBox: true,
                                     heading: true)
                            .padding(.top, 22)

                        PrettyText(CheckInCopy.gateBody,
                                   font: TypeMetrics.uiFont(PS.manropeMedium, 14.5,
                                                            fallback: .systemFont(ofSize: 14.5, weight: .medium)),
                                   lineHeight: 14.5 * 1.5, color: .liqNeutral, centered: true)
                            .frame(maxWidth: 310)
                            .padding(.top, Spacing.xl)
                    }
                    .padding(.horizontal, Spacing.screenGutter)
                    .frame(maxWidth: .infinity, minHeight: geo.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }

            // The CTA block: padding 0 / 24 / 22.
            PrimaryButton(label: CheckInCopy.checkInCta, variant: .sunset, action: { onCheckIn(.cta) },
                          trailing: { CheckInArrow() })
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.bottom, 22)

            TabBar(active: .search, onTab: { tab in
                switch tab {
                case .search: onCheckIn(.searchTab)
                case .likes: onLikes()
                case .instant: onCheckIn(.instantTab)
                case .settings: onSettings()
                }
            }, likes: likes)
        }
        .background(Color.liqCream.ignoresSafeArea())
    }
}

#Preview("Check-In 01 · A · intro") { CheckInIntroView() }
#Preview("Check-In 01 · B · gate") { CheckInGateView() }
