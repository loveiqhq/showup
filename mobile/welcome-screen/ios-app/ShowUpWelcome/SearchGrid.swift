//
//  SearchGrid.swift
//  ShowUp · Date search 02, the Premium grid (SHOWUP-185)
//
//  The Swift twin of `search/SearchGridScreen.kt`. The Search tab for a Premium user: the top bar with
//  the Premium tag, two columns of cards -- the whole queue, in queue order -- the one scroll container,
//  and the TabBar; the undo toast floats 12 above it. Values in, taps out: `SearchModel` decides
//  everything, so every state renders at every phone size with no device.
//
//  A card opens the Search 01 profile, extended (`SearchProfileView`); what was decided there comes
//  back as the card's state -- the filled heart and the fly-out, the `Not now` label, the closed reason.
//

import SwiftUI

/// Card height: "every card is the same height (248)".
let gridCardHeight: CGFloat = 248

/// The bottom inset of the info block.
private let infoInset: CGFloat = 12

/// What the info block leaves free at the top of a card: the heart's 8 + 36, and 8 more.
private let topReserve: CGFloat = 52

struct SearchGridView: View {
    let state: SearchUiState
    var zone: TimeZone = .current
    /// The grid is the screen again after a profile -- the back transition has finished -- so a card
    /// waiting to fly out may go.
    var settled = true
    var onOpen: (String) -> Void = { _ in }
    var onLeft: (String) -> Void = { _ in }
    var onUndo: () -> Void = {}
    var onUndoFocus: (Bool) -> Void = { _ in }
    var onFilters: () -> Void = {}
    var onLikesChip: () -> Void = {}
    var onTab: (AppTab) -> Void = { _ in }
    var onRetry: () -> Void = {}
    /// Previews only: frame K, every leaving card held this far into its flight, in milliseconds.
    var freezeLeavingMs: Double? = nil

    var body: some View {
        let grid = state.grid
        PremiumFrameLayout {
            SearchTopBar(likes: state.likesBadge, onLikes: onLikesChip, onFilters: onFilters, premium: true)
                .fitAnchor(FitAnchorID.searchTopBar)
            ZStack {
                if grid.loading && grid.cards.isEmpty {
                    ListLoading(label: SearchCopy.loadingLabel)
                } else if grid.failure != nil && grid.cards.isEmpty {
                    // The words are PROPOSED, not ticketed.
                    CentredStatement(text: SearchCopy.loadFailedProposed, action: SearchCopy.retry, onAction: onRetry)
                } else if grid.empty {
                    // The no results · widen filters screen is its own story; this stands in for it.
                    CentredStatement(text: SearchCopy.noResultsProposed, action: SearchCopy.widenFiltersProposed,
                                     onAction: onFilters)
                } else {
                    GridCards(grid: grid, zone: zone, settled: settled, onOpen: onOpen, onLeft: onLeft,
                              freezeLeavingMs: freezeLeavingMs)
                        // A new read of the grid is a new scroll position; a return from a profile keeps
                        // the old one ("Returning restores the grid's scroll position").
                        .id(grid.generation)
                }
            }
            TabBar(active: .search, onTab: onTab, likes: state.likesBadge)
                .fitAnchor(FitAnchorID.tabBar)
            UndoToastSlot(toast: state.undo?.from == .grid ? state.undo : nil, onUndo: onUndo, onUndoFocus: onUndoFocus)
        }
        .background(Color.liqCream.ignoresSafeArea())
    }
}

/// The tallest info block among the cards, measured before the cards are laid out.
private struct TallestInfoKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct GridCards: View {
    let grid: PremiumList
    let zone: TimeZone
    let settled: Bool
    let onOpen: (String) -> Void
    let onLeft: (String) -> Void
    let freezeLeavingMs: Double?

    @State private var host = FlyOutHost()
    @State private var tallest: CGFloat = 0

    var body: some View {
        GeometryReader { g in
            let column = max(0, (g.size.width - 16 - 16 - 12) / 2)
            // EVERY CARD THE SAME HEIGHT: 248 as drawn, and at a larger type the height the tallest
            // card's info block needs, so the cards grow together and a row stays level.
            let cardHeight = max(gridCardHeight, tallest + infoInset + topReserve)
            let frozen = freezeLeavingMs != nil
            ScrollView(.vertical, showsIndicators: false) {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    ForEach(grid.shown, id: \.userId) { card in
                        PremiumGridCard(card: card, placeholder: grid.positionOf(card.userId) - 1, zone: zone,
                                        onOpen: card.open && card.leaving == nil ? { onOpen(card.userId) } : nil,
                                        height: cardHeight)
                            .cardFlyOut(host: host, key: card.userId, leaving: card.leaving != nil,
                                        settled: settled, frozen: frozen)
                            // A card that flew out is already invisible; one Undo brings back appears at once.
                            .transition(.identity)
                            .fitAnchor(FitAnchorID.gridCard(card.userId))
                    }
                }
                .padding(EdgeInsets(top: 4, leading: 16, bottom: 16, trailing: 16))
                // The reflow: the rest move into their new slots, `Motion.gridReflow`.
                .animation(gridReflowAnimation, value: grid.shown.map(\.userId))
            }
            .coordinateSpace(name: premiumListSpace)
            .overlay {
                FlyOutLayer(host: host, onLeft: onLeft,
                            frozenKeys: frozen ? grid.shown.filter { $0.leaving != nil }.map(\.userId) : [],
                            frozenAt: freezeLeavingMs.map(frozenFlightProgress)) { id in
                    if let c = grid.card(id) {
                        PremiumGridCard(card: c, placeholder: grid.positionOf(id) - 1, zone: zone, onOpen: nil, height: cardHeight)
                    }
                }
            }
            // The probe: every card's info block at the column's width, invisible, for the tallest.
            .background(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    ForEach(grid.shown, id: \.userId) { card in
                        CardInfo(card: card, zone: zone, glass: false, width: max(0, column - 2 * infoInset))
                            .frame(width: max(0, column - 2 * infoInset))
                            .fixedSize(horizontal: false, vertical: true)
                            .background(GeometryReader { p in
                                Color.clear.preference(key: TallestInfoKey.self, value: p.size.height)
                            })
                    }
                }
                .opacity(0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .onPreferenceChange(TallestInfoKey.self) { tallest = $0 }
            .onAppear { host.viewport = g.size }
            .onChange(of: g.size) { _, size in host.viewport = size }
            .fitAnchor(FitAnchorID.searchGrid)
        }
    }
}

/// `PremiumGridCard`: the photo, radius 20, cover, under the bottom scrim; the `Liked you` badge top 12 /
/// left 10 with the 2.5 orange ring; the marked label top 12 / right 10; the heart 36 at top / right 8 --
/// a marker, not a button; and the info block anchored to the bottom, 12 from each edge, a column with
/// gap 5 that grows upward: Show-up count -> name -> meet time and distance -> motivation.
///
/// MARKED (Not now, and Not interested during its hold) and CLOSED: grayscale .9 at .5, no heart, no
/// ring, no badge, the label in white. Closed also drops the count and puts its reason line where the
/// meet time and motivation were, and is not a control (`role="group"` with its accessible name).
///
/// - Parameters:
///   - placeholder: which of the kit's painterly placeholders a card with no photo draws, so the grid of
///     placeholders is not six copies of one colour.
///   - onOpen: nil when the card is not a tap target (closed, or flying out).
struct PremiumGridCard: View {
    let card: PremiumCard
    let placeholder: Int
    let zone: TimeZone
    let onOpen: (() -> Void)?
    /// 248 as drawn; the grid passes more at a large type, the same for every card.
    var height: CGFloat = gridCardHeight

    var body: some View {
        let closed = card.closed
        let dim = card.notNow || card.leaving == .notInterested || closed != nil
        let ring = card.likedYou && !dim
        ZStack(alignment: .topTrailing) {
            if let onOpen {
                Button(action: onOpen) { tile(dim: dim, ring: ring) }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(gridCardLabel(card.name, notNow: card.notNow, leaving: card.leaving,
                                                           likedYou: card.likedYou)))
                    .accessibilityAddTraits(.isButton)
            } else {
                tile(dim: dim, ring: ring)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(closed.map { $0.gridLabel(card.name) }
                                             ?? gridCardLabel(card.name, notNow: card.notNow, leaving: card.leaving,
                                                              likedYou: card.likedYou)))
            }
            if dim {
                PillLabel(text: closed?.pill ?? (card.notNow ? PremiumCopy.notNowLabel : PremiumCopy.notInterestedLabel))
                    .padding(.top, 12)
                    .padding(.trailing, 10)
                    .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        // `box-shadow: 0 0 0 2.5px` -- a spread with no blur, OUTSIDE the photo: a rounded rect 2.5 larger
        // on every side, its corner 20 + 2.5, behind the opaque photo.
        .background {
            if ring { RoundedRectangle(cornerRadius: 22.5).fill(Color.liqOrange).padding(-2.5) }
        }
        // "Announced with its accessible name" when it closes in front of the user.
        .onChange(of: card.closed) { was, now in
            if was == nil, let now { AccessibilityNotification.Announcement(now.gridLabel(card.name)).post() }
        }
    }

    private func tile(dim: Bool, ring: Bool) -> some View {
        GeometryReader { g in
            ZStack(alignment: .topLeading) {
                PhotoBackdrop(url: card.photo, index: placeholder)
                // `linear-gradient(180deg, rgba(20,12,28,0) 42%, rgba(20,12,28,.66) 100%)`.
                LinearGradient(stops: [
                    .init(color: Color.liqPhotoInk.opacity(0), location: 0.42),
                    .init(color: Color.liqPhotoInk.opacity(0.66), location: 1),
                ], startPoint: .top, endPoint: .bottom)
                if ring {
                    LikedYouBadge().padding(.top, 12).padding(.leading, 10)
                }
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    CardInfo(card: card, zone: zone, glass: true, width: max(0, g.size.width - 2 * infoInset))
                        .padding(infoInset)
                }
                if !dim {
                    HStack(spacing: 0) {
                        Spacer(minLength: 0)
                        Heart(liked: card.leaving == .liked).padding(8)
                    }
                }
            }
            .frame(width: g.size.width, height: g.size.height)
            // The count's glass draws the photo again, blurred, behind itself: the card is its space.
            .coordinateSpace(name: heroSpace)
            .environment(\.heroBackdrop, HeroBackdrop(url: card.photo, index: placeholder, size: g.size))
        }
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .grayscale(dim ? 0.9 : 0)
        // One layer before the fade, so the photo and the washes fade as one, as CSS's `opacity` does.
        .compositingGroup()
        .opacity(dim ? 0.5 : 1)
        .contentShape(RoundedRectangle(cornerRadius: 20))
    }
}

/// The info block, bottom-anchored: count, name, then the meet line and motivation -- or the reason.
/// `glass` false draws the count without its blur, for measuring.
private struct CardInfo: View {
    let card: PremiumCard
    let zone: TimeZone
    let glass: Bool
    /// The block's width: what the count has to fit.
    let width: CGFloat

    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if card.closed == nil, let profile = card.profile {
                if glass {
                    ShowUpCount(count: profile.attendedCount, size: .xs, dark: true, fitWidth: width) { HeroGlass(blur: 8) }
                } else {
                    ShowUpCount(count: profile.attendedCount, size: .xs, dark: true, fitWidth: width)
                }
            }
            Text(nameLine(card.name, card.age))
                .font(F.lora(17, bold: true))
                .foregroundColor(.white)
                // `text-shadow: 0 1px 8px rgba(0,0,0,.3)`.
                .shadow(color: Color.black.opacity(0.3), radius: 4, y: 1)
                .cssLineBox(17 * 1.1 * typeScale)
            if let closed = card.closed {
                // The reason line wraps, `text-wrap: pretty`.
                PrettyText(closed.gridLine,
                           font: TypeMetrics.uiFont(PS.manropeBold, 11.5, fallback: .systemFont(ofSize: 11.5, weight: .bold)),
                           lineHeight: 11.5 * 1.3, color: .white)
            } else if let profile = card.profile {
                MetaLine(full: gridMetaLine(profile, zone: zone),
                         time: meetLine(profile.meetAt, day: profile.meetDay, zone: zone),
                         distance: searchDistance(profile.distanceKm))
                    // The card's own, not the height probe's copy of it, is the one the fit tests read.
                    .fitAnchor(glass ? FitAnchorID.gridMeta(card.userId) : "probe-meta-\(card.userId)")
                if let m = motivationOf(profile.motivation) {
                    // "The motivation label on one line with ellipsis" (Search 02): the whole label is
                    // on the profile one tap away.
                    Text(m.title)
                        .font(F.manrope(11.5, .medium))
                        .foregroundColor(Color.white.opacity(0.92))
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// `{HH:MM} {day} · ~N km` on one line wherever it fits -- every phone 375 wide or more, as the ticket
/// requires -- and the time over the distance where it does not, each wrapping if it must.
private struct MetaLine: View {
    let full: String
    let time: String
    let distance: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            line(full).lineLimit(1).fixedSize()
            VStack(alignment: .leading, spacing: 0) {
                line(time)
                line(distance)
            }
        }
    }

    private func line(_ text: String) -> Text {
        Text(text).font(F.manrope(11.5, .bold)).foregroundColor(.white)
    }
}

/// The heart: 36, white at .92 with an orange outline heart -- or sunset with a white filled one, liked.
/// A marker, not a button; lifted by `0 2px 10px rgba(0,0,0,.18)`.
private struct Heart: View {
    let liked: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(liked ? AnyShapeStyle(Gradients.sunset()) : AnyShapeStyle(Color.liqElevated.opacity(0.92)))
                .shadow(color: Color.black.opacity(0.18), radius: 5, y: 2)
            BrandIconView(icon: liked ? .heartFilled : .heart, size: 17, stroke: 2.4, tint: liked ? .white : .liqOrange)
        }
        .frame(width: 36, height: 36)
        .accessibilityHidden(true)
    }
}

#Preview("Search 02 · A at rest") { SearchGridView(state: SearchSamples.gridRest(), zone: SearchSamples.zone) }
#Preview("Search 02 · G not now and the toast") {
    SearchGridView(state: {
        var s = SearchSamples.gridRest()
        s.grid.cards[1].notNow = true
        s.grid.cards[0].gone = true
        s.undo = UndoToast(userId: s.grid.cards[0].userId, name: "Leonie", from: .grid, position: 1)
        return s
    }(), zone: SearchSamples.zone)
}
#Preview("Search 02 · J closed") {
    SearchGridView(state: {
        var s = SearchSamples.gridRest()
        s.grid.cards[1].closed = .matched
        return s
    }(), zone: SearchSamples.zone)
}
#Preview("Search 02 · K flying") {
    SearchGridView(state: {
        var s = SearchSamples.gridRest()
        s.grid.cards[2].leaving = .liked
        return s
    }(), zone: SearchSamples.zone, freezeLeavingMs: 440)
}
#Preview("Search 02 · loading") {
    SearchGridView(state: { var s = SearchSamples.gridRest(); s.grid = PremiumList(); return s }(), zone: SearchSamples.zone)
}
#Preview("Search 02 · failed") {
    SearchGridView(state: { var s = SearchSamples.gridRest(); s.grid = PremiumList(loading: false, failure: .network); return s }(),
                   zone: SearchSamples.zone)
}
