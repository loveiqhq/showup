// PartnerEndedScreen.swift
// ShowUp · Date 08, what the person who did NOT end the date sees: one full screen, three payloads (SHOWUP-199).
// Android's `PartnerEndedScreen.kt`.
//
// Why this is not `DateEndedScreen` (spec R4): the two differ in nearly every number (avatar 76 vs 72, badge 30 vs 28,
// headline 27 vs 26, tile 36 / 12 vs 34 / 11, row text 13.5 / 1.45 vs 13 / 1.42, hero padding 30 24 vs 26 24 24) and in
// the CTA, which here scrolls as the last item where Date 02 / 07's is pinned. Parametrising every number would be a
// number with extra steps, and editing that screen puts Date 02 and 07 at risk. This one is built from the same
// primitives instead (`WelcomeBackdrop`, `MatchPhoto`, `ShadowedShape`, `IconTile`, `WashHeadline`, `PrettyText`,
// `PrimaryButton`). The reference is `screen-partner-end-reference.jsx`.

import SwiftUI

/// The ids the tests read, one place.
extension FitAnchorID {
    static let partnerEndedCta = "partnerEnded:cta"
    static let partnerEndedHero = "partnerEnded:hero"
    /// The rows' column, so the fit test can ask that the CTA lies below the last of them.
    static let partnerEndedRows = "partnerEnded:rows"
    /// The whole scrolling column, at least the viewport tall: the CTA is its last item.
    static let partnerEndedColumn = "partnerEnded:column"
    /// The space the column scrolls in: the screen inside its safe area.
    static let partnerEndedViewport = "partnerEnded:viewport"
    static func partnerEndedRow(_ index: Int) -> String { "partnerEnded:row:\(index)" }
    static func partnerEndedTile(_ index: Int) -> String { "partnerEnded:tile:\(index)" }
}

/// The id at the very end of the column, for `partnerEndedScrollsToEnd`. A file-scope `let` String, as ModalShell's.
private let partnerEndedEnd = "partner.ended.end"

private struct PartnerEndedScrollsToEndKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// EVIDENCE ONLY, false in the app: the column comes to rest at its end, so a still picture at 375 x 667 shows the CTA
    /// reached by scrolling. Only the sample frames and the evidence harness set it. Android hoists a `ScrollState`.
    var partnerEndedScrollsToEnd: Bool {
        get { self[PartnerEndedScrollsToEndKey.self] }
        set { self[PartnerEndedScrollsToEndKey.self] = newValue }
    }
}

/// The hero atmosphere (0.26 / 0.22, as Date 02's), column pad 8 20 0 inside the safe area. ONE scroll whose column is
/// at least the viewport tall: hero, rows, a flexible space of at least 14, then the CTA (6 below). The viewport is read
/// with `GeometryReader` for its height alone (ios-app/CLAUDE.md), because a `Spacer` alone does nothing under a scroll's
/// unbounded height. A tall phone sees the CTA at the foot; a short one, or the largest type, reaches it as the last item
/// by scrolling, never under or over the rows. No absolute Y.
struct PartnerEndedScreen: View {
    let screen: PartnerScreen
    let name: String
    let photoURL: URL?
    var onProceed: () -> Void = {}

    @Environment(\.partnerEndedScrollsToEnd) private var scrollsToEnd

    var body: some View {
        let payload = partnerEndedPayload(screen)
        ZStack {
            Color.liqCream.ignoresSafeArea()
            WelcomeBackdrop(peachWash: false, placement: .atmosphere, orangeAlpha: 0.26, violetAlpha: 0.22)
                .ignoresSafeArea()
            GeometryReader { g in
                ScrollViewReader { proxy in
                    ScrollView {
                        // Spacing 0: SwiftUI's spacing would be added on both sides of the flexible space.
                        VStack(spacing: 0) {
                            hero(payload)
                            rows(payload)
                                .padding(.top, payload.rowsTop)
                            Spacer(minLength: 14)
                            PrimaryButton(PartnerEndedCopy.cta, action: onProceed)
                                .fitTapArea()
                                .fitAnchor(FitAnchorID.partnerEndedCta)
                                .padding(.bottom, 6)
                                .id(partnerEndedEnd)
                        }
                        .padding(EdgeInsets(top: 8, leading: 20, bottom: 0, trailing: 20))
                        .frame(maxWidth: .infinity, minHeight: g.size.height, alignment: .top)
                        .fitAnchor(FitAnchorID.partnerEndedColumn)
                    }
                    .modifier(NoBounceWhenItFits())
                    .onAppear {
                        guard scrollsToEnd else { return }
                        proxy.scrollTo(partnerEndedEnd, anchor: .bottom)
                        // Again once laid out, as ModalShell does: the first call can run before the column has its height.
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 120_000_000)
                            proxy.scrollTo(partnerEndedEnd, anchor: .bottom)
                        }
                    }
                }
            }
            .fitAnchor(FitAnchorID.partnerEndedViewport)
        }
    }

    /// The hero card (radius 28, centred): the avatar block 76 with its badge, the headline (Lora 700 27 / 1.15, -0.01em,
    /// balanced, the italic run washed) 18 below it (16 on B), and the sub (Manrope 14 / 1.55 muted, pretty, bold runs
    /// fg) 12 below that. Padding 30 24 (28 22 on B).
    private func hero(_ p: PartnerEndedPayload) -> some View {
        VStack(spacing: 0) {
            avatar(p)
                .overlay(alignment: .bottomTrailing) { badge(p) }
            WashHeadline(
                parts: partnerEndedHeadline(screen, name: name),
                fontSize: 27, lineHeightMultiple: 1.15, trackingEm: -0.01, balance: true, centered: true,
                cssLineBox: true, heading: true)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, p.avatarGap)
            PrettyText(runs: boldPairs(partnerEndedSub(screen, name: name)),
                       font: chatFont(PS.manropeRegular, 14, .regular), lineHeight: 14 * 1.55, color: .liqMuted,
                       emphasisFont: chatFont(PS.manropeBold, 14, .bold), emphasisColor: .liqFg, centered: true)
                .padding(.top, 12)
        }
        .padding(EdgeInsets(top: p.heroPadV, leading: p.heroPadH, bottom: p.heroPadV, trailing: p.heroPadH))
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: PartnerEndedLayout.heroRadius).fill(heroFill(p.tone)))
        .fitAnchor(FitAnchorID.partnerEndedHero)
    }

    /// `--su-grad-blush` for the two accountability frames, `--su-grad-lilac` for the thank-you, both top to bottom.
    private func heroFill(_ tone: HeroTone) -> LinearGradient {
        LinearGradient(colors: tone == .lilac ? Color.suGradLilac : Color.suGradBlush, startPoint: .top, endPoint: .bottom)
    }

    /// A's photo through `MatchPhoto` (initials when there is none), as H draws it (R8). C has no photo: the white 72 %
    /// disc, 1 px violet at .16, `0 2 10` of the shadow ink at .08, with the violet `heart-filled` 34.
    @ViewBuilder
    private func avatar(_ p: PartnerEndedPayload) -> some View {
        let size = PartnerEndedLayout.avatarSize
        if p.usesPhoto {
            MatchPhoto(name: name, photoURL: photoURL, size: size)
        } else {
            // The reference's `<Icon name="heart-filled" size={34}/>` passes no stroke, so the kit's `<svg>` strokes the
            // fill at its default 1.7 grid units: 2.4 pt wider than a plain fill. `BrandIconView` strokes in points, not
            // grid units, hence the scale.
            BrandIconView(icon: .heartFilled, size: 34, stroke: 1.7 * 34 / 24, tint: .liqPurple, fillAndStroke: true)
                .frame(width: size, height: size)
                .background(ShadowedShape(shape: Circle(), fill: Color.white.opacity(0.72),
                                          shadows: [ShadowLayer(color: Color.liqShadowInk.opacity(0.08), y: 2, blur: 10)]))
                .overlay(Circle().strokeBorder(Color.liqPurple.opacity(0.16), lineWidth: 1))
                .accessibilityHidden(true)
        }
    }

    /// The badge 30 at right -3 / bottom -3: the reference's 3 px white border sits inside its 30 box, so the coloured
    /// disc is 24 in a 3 white ring under `--liq-shadow-md`. Orange 500, or `--liq-success-fg` on C (#0A7A47; the
    /// reference's #00AB55 is a CSS fallback that never renders).
    private func badge(_ p: PartnerEndedPayload) -> some View {
        BrandIconView(icon: p.badgeIcon, size: p.badgeIconSize, stroke: p.badgeStroke, tint: .white)
            .frame(width: PartnerEndedLayout.badgeSize - 6, height: PartnerEndedLayout.badgeSize - 6)
            .background(Circle().fill(p.badgeSuccess ? Color.liqSuccessFg : Color.liqOrange))
            .padding(3)
            .background(ShadowedShape(shape: Circle(), fill: Color.white, shadows: shadowMd))
            .offset(x: 3, y: 3)
            .accessibilityHidden(true)
    }

    /// The rows, gap 10: each white, 1 px `border-soft`, radius 14.
    private func rows(_ p: PartnerEndedPayload) -> some View {
        let accent = p.accent == .orange ? Color.liqOrange : Color.liqPurple
        return VStack(spacing: PartnerEndedLayout.rowGap) {
            ForEach(Array(p.rows.enumerated()), id: \.offset) { index, runs in
                row(icon: p.rowIcons[index], accent: accent, runs: runs, index: index)
            }
        }
        .fitAnchor(FitAnchorID.partnerEndedRows)
    }

    /// One row: the tile 36 / 12 with its 18 / 2 icon, 12 from the text (Manrope 13.5 / 1.45 fg, bold runs 700).
    private func row(icon: BrandIcon, accent: Color, runs: [Run], index: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: 14)
        // No `alignItems` in the reference: the tile tops the row and only the text is centred on it.
        return HStack(alignment: .top, spacing: 12) {
            IconTile(size: PartnerEndedLayout.tileSize, radius: PartnerEndedLayout.tileRadius, fill: accent.opacity(0.12),
                     icon: icon, iconSize: 18, tint: accent, stroke: 2)
                .fitAnchor(FitAnchorID.partnerEndedTile(index))
            PrettyText(runs: boldPairs(runs),
                       font: chatFont(PS.manropeRegular, 13.5, .regular), lineHeight: 13.5 * 1.45, color: .liqFg,
                       emphasisFont: chatFont(PS.manropeBold, 13.5, .bold), emphasisColor: .liqFg)
                .frame(maxWidth: .infinity, alignment: .leading)
                // A line or two is centred on the 36 tile; taller, the text sets the row's height.
                .frame(minHeight: PartnerEndedLayout.tileSize, alignment: .center)
        }
        // The reference's 14 inside its 1 px border: CSS counts the border in the box, SwiftUI only draws it.
        .padding(15)
        .background(shape.fill(Color.liqElevated))
        .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
        .fitAnchor(FitAnchorID.partnerEndedRow(index))
    }
}
