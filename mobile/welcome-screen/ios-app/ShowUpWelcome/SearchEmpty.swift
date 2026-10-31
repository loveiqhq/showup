//
//  SearchEmpty.swift
//  ShowUp · Search 01's interim no-results screen (decided 27 Oct 2026)
//
//  The Swift twin of `search/SearchEmptyState.kt`. The kit's `search-empty` in its NORMAL mode, from
//  `screen-search-empty-reference.jsx`. Search 01's queue and Search 02's grid both run dry into it --
//  the reference's "1-up / grid view can come up dry" -- inside their own top bar and TabBar.
//  Headline, body and `Widen my search`, which opens Filters "until the no-results story defines the
//  one-tap widen". Left out by the ticket: the CTA sub-line, the filter read-out and `Adjust filters
//  manually`. Instant mode is out of scope.
//

import SwiftUI

/// The order of sacrifice when the region is short: the radar 184 -> 140 -> 112, then the content
/// scrolls under the pinned CTA. OURS, not the reference's -- it names no order -- and the location
/// gate's rule (SHOWUP-188): the decoration gives way, the headline and body never shrink.
enum EmptyFit: CaseIterable {
    case full, compact, tight

    var radar: CGFloat {
        switch self {
        case .full: return 184
        case .compact: return 140
        case .tight: return 112
        }
    }
}

/// The region between the top bar and the TabBar: content (`padding: 8 24 0`), ONE flexible spacer
/// (min 24), and the CTA band (`padding: 0 24 22`) -- "sits in normal flow directly above the tab bar,
/// always visible". Past the last level the content scrolls under the band, never the CTA.
struct SearchEmptyView: View {
    let onWiden: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ViewThatFits(in: .vertical) {
                column(.full)
                column(.compact)
                column(.tight)
                ScrollView(.vertical, showsIndicators: false) { column(.tight) }
            }
            .frame(maxHeight: .infinity, alignment: .top)

            PrimaryButton(label: SearchCopy.widenMySearch, variant: .sunset, labelLineLimit: 2,
                          action: onWiden,
                          // Decorative: the visible label is the name.
                          leading: {
                              BrandIconView(icon: .sliders, size: 18, stroke: 2.2, tint: .white)
                                  .accessibilityHidden(true)
                          },
                          trailing: { EmptyView() })
                // The button's own frame, not its padding: the fit test measures the 22 below it.
                .fitAnchor(FitAnchorID.searchEmptyCta)
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.bottom, 22)
        }
    }

    /// The content at one sacrifice level, ending in the spacer's 24 floor.
    private func column(_ fit: EmptyFit) -> some View {
        VStack(spacing: 0) {
            EmptyRadar(height: fit.radar)
            // No eyebrow in normal mode: the reference's `marginTop: 14` in its place.
            // Lora 700 / 30 / 1.12 / -0.015em, centred, balanced, `no people` washed. The heading.
            WashHeadline(parts: [(SearchCopy.noResultsLead, false), (SearchCopy.noResultsEm, true),
                                 (SearchCopy.noResultsTail, false)],
                         fontSize: 30, lineHeightMultiple: 1.12, trackingEm: -0.015,
                         balance: true, centered: true, cssLineBox: true, heading: true)
                .padding(.top, 14)
                .fitAnchor(FitAnchorID.searchEmptyHeadline)
            // Manrope 500 / 14.5 / 1.5, `--liq-neutral-200`, centred, pretty, at most 320 wide, 12 below.
            PrettyText(SearchCopy.noResultsBody,
                       font: TypeMetrics.uiFont(PS.manropeMedium, 14.5, fallback: .systemFont(ofSize: 14.5, weight: .medium)),
                       lineHeight: 14.5 * 1.5, color: .liqNeutral, centered: true)
                .frame(maxWidth: 320)
                .padding(.top, Spacing.xl)
            Spacer(minLength: 24)
        }
        .padding(.horizontal, Spacing.screenGutter)
        .padding(.top, Spacing.md)
        .frame(maxWidth: .infinity)
    }
}

/// `EmptyRadar` -- "came up dry": three rings around a muted search glyph, no nearby dots, the visual
/// inverse of the location gate's live radar. Drawn on the reference's 184 box and scaled as one
/// picture when an `EmptyFit` level shrinks it. Decorative, so hidden from VoiceOver.
///
/// - Halo: 220, `radial-gradient(rgba(254,104,57,.10) 0%, transparent 65%)`, overflowing the box top
///   and bottom as the reference's does. Its `blur(6px)` is not drawn: the gradient is already
///   transparent from 65% out, so the blur moves nothing visible.
/// - Rings: 176 dashed .10, 116 dashed .16, 64 solid .28, `1.25px rgba(129,42,236)`, drawn inside
///   their box as a CSS border is. THE DASH IS OURS: CSS `dashed` leaves dash and gap to the browser
///   (ProfilePhotos' note), so the gate radar's 2 on / 4 off is used and the two radars agree.
/// - Disc: 64, `--liq-bg-elevated`, 1 `--liq-border-soft`, `--liq-shadow-md` (Card's two layers);
///   the 26 `search` glyph at stroke 1.8 in `--liq-fg-faint`.
struct EmptyRadar: View {
    let height: CGFloat

    var body: some View {
        let s = height / 184
        ZStack {
            Circle()
                .fill(RadialGradient(stops: [
                    .init(color: Color.liqOrange.opacity(0.10), location: 0),
                    .init(color: Color.liqOrange.opacity(0), location: 0.65),
                ], center: .center, startRadius: 0, endRadius: 110 * s))
                .frame(width: 220 * s, height: 220 * s)
            ring(176 * s, dashed: true, opacity: 0.10, scale: s)
            ring(116 * s, dashed: true, opacity: 0.16, scale: s)
            ring(64 * s, dashed: false, opacity: 0.28, scale: s)
            ZStack {
                Circle().fill(Color.liqElevated).shadow(color: Color.liqShadowInk.opacity(0.06), radius: 6, y: 4)
                Circle().fill(Color.liqElevated).shadow(color: Color.liqShadowInk.opacity(0.04), radius: 1.5, y: 1)
                Circle().strokeBorder(Color.liqBorderSoft, lineWidth: 1)
                BrandIconView(icon: .search, size: 26 * s, stroke: 1.8, tint: .liqFaint)
            }
            .frame(width: 64 * s, height: 64 * s)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private func ring(_ diameter: CGFloat, dashed: Bool, opacity: Double, scale s: CGFloat) -> some View {
        Circle()
            .strokeBorder(Color.liqPurple.opacity(opacity),
                          style: StrokeStyle(lineWidth: 1.25 * s, dash: dashed ? [2 * s, 4 * s] : []))
            .frame(width: diameter, height: diameter)
    }
}

#Preview("Search 01 · no results") {
    SearchEmptyView(onWiden: {})
        .background(Color.liqCream)
}
