//  BasicsChrome.swift
//  ShowUp · the shell every screen in "The basics" shares
//
//  The profile epic lists four things as built-once infrastructure and says a second copy of any of
//  them is a bug: the header shell with the leading slot as a VARIANT, the step progress bar, the
//  single-question scaffold with its one flexible spacer, and the reserved status region. Three
//  screens share them; the differences between those screens are parameters, not forks.
//
//  Mirrors `profile/BasicsChrome.kt`.

import SwiftUI

/// What the header's leading 36 slot holds.
enum HeaderLeading {
    /// Nothing — but the slot still occupies its 36.
    ///
    /// Step 1. Profile creation is mandatory once entered, so there is nothing behind it to return
    /// to. The slot is kept rather than removed because removing it un-centres the title, and the
    /// title is the SAME STRING on all three steps: it must not shift as the user advances.
    case none
    /// A back chevron. Steps 2 and 3.
    case back
}

/// The section header: a centred title with a 36 slot on each side.
///
/// **The title is centred by geometry.** Both slots are a fixed 36 whatever they hold, and the
/// title takes what is between them. Centring it in the *remaining* space instead would put it 36
/// off-centre whenever only one slot is filled — and move it by 36 between step 1 and step 2, which
/// is the one thing a shared title must never do.
///
/// The back control's touch area is `ComponentSizes.minTapTarget`, not the 36 it draws. The ticket
/// says a "36 × 36 target"; 36 fails the floor every tappable thing in this app is held to, and the
/// welcome flow's back control already resolved the same conflict the same way. Nothing visible
/// changes — the control has no fill, only the 24 chevron is drawn.
struct AppHeader: View {
    let title: String
    var leading: HeaderLeading = .back
    var onBack: () -> Void = {}
    /// Spoken by VoiceOver. The chevron is drawn, so without this the control has no name.
    var backLabel: String = "Back"

    var body: some View {
        HStack(spacing: Spacing.md) {
            ZStack(alignment: .leading) {
                // Holds the slot's width whether or not anything is in it.
                Color.clear.frame(width: 36, height: 36)
                if leading == .back {
                    Button(action: onBack) {
                        BrandIconView(icon: .chevronLeft, size: 24, stroke: 2, tint: .liqFg)
                            .minTapTarget(alignment: .center)
                    }
                    .buttonStyle(PressScale())
                    .accessibilityLabel(Text(backLabel))
                }
            }
            .frame(width: 36)

            Text(title)
                .font(F.manrope(16, .semibold))
                .foregroundColor(.liqFg)
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)

            // Empty on every screen in this flow, and still occupying its 36 for the same reason
            // the leading slot does.
            Color.clear.frame(width: 36, height: 36)
        }
        .padding(.horizontal, 16)
        .frame(height: 52)
    }
}

/// The single-question scaffold: header, a top-anchored content column, ONE flexible spacer, and a
/// bottom-anchored CTA row.
///
/// **The one spacer is the whole design.** Everything in `content` is top-anchored and the `cta` is
/// bottom-anchored, with exactly one `Spacer()` between them. The keyboard's height is not ours —
/// it varies by OS, language and prediction settings — so the spacer absorbs every difference. At
/// 390 × 844 it resolves to about 102; at 375 × 667 it collapses; at 430 × 932 it takes the surplus.
/// A second flexible element would split that budget and the CTA would stop sitting where it does.
///
/// **No ambient backdrop.** Flat `liqCream`, unlike the welcome flow's orbs — the keyboard owns the
/// bottom half of every screen in this group.
struct BasicsScaffold<Content: View, CTA: View>: View {
    let title: String
    var leading: HeaderLeading
    var onBack: () -> Void = {}
    @ViewBuilder var content: () -> Content
    @ViewBuilder var cta: () -> CTA

    var body: some View {
        VStack(spacing: 0) {
            AppHeader(title: title, leading: leading, onBack: onBack)

            VStack(alignment: .leading, spacing: 0) {
                content()

                // The ONE spacer. Not two, and nothing else in this column is flexible.
                Spacer(minLength: 0)

                cta()
            }
            // Content pad-top 4, gutter 24, floor 0 — the CTA row carries its own bottom margin
            // because it differs per screen (18 on name, 10 on email).
            .padding(.init(top: 4, leading: 24, bottom: 0, trailing: 24))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.liqCream)
    }
}
