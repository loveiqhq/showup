//
//  LikesReceived.swift
//  ShowUp · Date search 03, the Likes tab for a Premium user (SHOWUP-186)
//
//  The Swift twin of `search/LikesReceivedScreen.kt`. A tab root: the top bar with the wordmark and the
//  Premium tag only (no filter, no likes chip), the intro, the received likes in the server's order --
//  open ones to open, closed ones that say why -- the one scroll container, and the TabBar; the undo
//  toast floats 12 above it. Values in, taps out: `SearchModel` decides everything.
//
//  A card opens the Search 01 profile, extended (`SearchProfileView`, `All likes`); what was decided
//  there comes back as the card's state -- the fly-out, the `Not now` label, the closed reason.
//

import SwiftUI

struct LikesReceivedView: View {
    let state: SearchUiState
    var zone: TimeZone = .current
    /// The list is the screen again after a profile, so a card waiting to fly out may go.
    var settled = true
    var onOpen: (String) -> Void = { _ in }
    var onLeft: (String) -> Void = { _ in }
    var onUndo: () -> Void = {}
    var onUndoFocus: (Bool) -> Void = { _ in }
    var onBackToSearch: () -> Void = {}
    var onTab: (AppTab) -> Void = { _ in }
    var onRetry: () -> Void = {}
    /// Previews only: frame H, every leaving card held this far into its flight, in milliseconds.
    var freezeLeavingMs: Double? = nil

    var body: some View {
        let likes = state.likes
        PremiumFrameLayout {
            SearchTopBar(likes: state.likesBadge, onLikes: {}, onFilters: {}, premium: true, filter: false, chip: false)
                .fitAnchor(FitAnchorID.searchTopBar)
            ZStack {
                if likes.loading && likes.cards.isEmpty {
                    ListLoading(label: PremiumCopy.likesLoadingLabel)
                } else if likes.failure != nil && likes.cards.isEmpty {
                    // The words are PROPOSED, not ticketed.
                    CentredStatement(text: PremiumCopy.likesLoadFailedProposed, action: SearchCopy.retry, onAction: onRetry)
                } else if likes.empty {
                    EmptyLikes(onBackToSearch: onBackToSearch)
                } else {
                    LikesList(likes: likes, zone: zone, settled: settled, onOpen: onOpen, onLeft: onLeft,
                              freezeLeavingMs: freezeLeavingMs)
                        // Kept across a tab switch and a profile, started again when the list read
                        // back different.
                        .id(likes.generation)
                }
            }
            TabBar(active: .likes, onTab: onTab, likes: state.likesBadge)
                .fitAnchor(FitAnchorID.tabBar)
            UndoToastSlot(toast: state.undo?.from == .likes ? state.undo : nil, onUndo: onUndo, onUndoFocus: onUndoFocus)
        }
        .background(Color.liqCream.ignoresSafeArea())
    }
}

private struct LikesList: View {
    let likes: PremiumList
    let zone: TimeZone
    let settled: Bool
    let onOpen: (String) -> Void
    let onLeft: (String) -> Void
    let freezeLeavingMs: Double?

    @State private var host = FlyOutHost()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { g in
            let frozen = freezeLeavingMs != nil
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: 12) {
                    LikesIntro()
                    ForEach(likes.shown, id: \.userId) { card in
                        likeCard(card, placeholder: likes.positionOf(card.userId) - 1,
                                 onOpen: card.open && card.leaving == nil ? { onOpen(card.userId) } : nil)
                            .cardFlyOut(host: host, key: card.userId, leaving: card.leaving != nil,
                                        settled: settled, frozen: frozen)
                            .transition(.identity)
                            .fitAnchor(FitAnchorID.likesCard(card.userId))
                            // Announced by its first line when it closes in front of the user -- not
                            // when a list arrives with it already closed.
                            .onChange(of: card.closed) { was, now in
                                if was == nil, let now {
                                    AccessibilityNotification.Announcement(now.likesHead(card.name)).post()
                                }
                            }
                    }
                }
                // `8 16 88`: the 88 keeps the last card clear of the toast.
                .padding(EdgeInsets(top: 8, leading: 16, bottom: toastClearance, trailing: 16))
                .animation(reduceMotion ? nil : gridReflowAnimation, value: likes.shown.map(\.userId))
            }
            .coordinateSpace(name: premiumListSpace)
            .overlay {
                FlyOutLayer(host: host, onLeft: onLeft,
                            frozenKeys: frozen ? likes.shown.filter { $0.leaving != nil }.map(\.userId) : [],
                            frozenAt: freezeLeavingMs.map(frozenFlightProgress)) { id in
                    if let c = likes.card(id) {
                        likeCard(c, placeholder: likes.positionOf(id) - 1, onOpen: nil)
                    }
                }
            }
            .onAppear { host.viewport = g.size }
            .onChange(of: g.size) { _, size in host.viewport = size }
            .fitAnchor(FitAnchorID.likesList)
        }
    }

    @ViewBuilder private func likeCard(_ card: PremiumCard, placeholder: Int, onOpen: (() -> Void)?) -> some View {
        if let closed = card.closed {
            ClosedLikeCard(card: card, reason: closed, placeholder: placeholder)
        } else {
            OpenLikeCard(card: card, placeholder: placeholder, zone: zone, onOpen: onOpen)
        }
    }
}

/// The intro (3): `People who want to meet you` (Lora 700 / 24 / 1.2, `-0.01em`, the italic run washed)
/// over its line (14 / 1.5), gap 6, with 6 more under it before the first card.
private struct LikesIntro: View {
    @ScaledMetric(relativeTo: .body) private var typeScale: CGFloat = 1

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WashHeadline(parts: [(PremiumCopy.likesIntroLead, false), (PremiumCopy.likesIntroEmphasis, true)],
                         fontSize: 24, lineHeightMultiple: 1.2, trackingEm: -0.01, cssLineBox: true, heading: true)
            Text(PremiumCopy.likesIntroBody)
                .font(F.manrope(14, .regular))
                .foregroundColor(.liqNeutral)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 14, multiple: 1.5))
                .cssLineBox(14 * 1.5 * typeScale)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 6)
    }
}

/// An open card (5-11): `Card` padding 14, a row with gap 14. The photo 88 x 120, radius 16, is a
/// button; the column beside it, gap 7: name and distance (and `Not now`) -> the Show-up count -> the
/// message, if one was written -> the meet and motivation chips -> `View profile` (sm, sunset, 2 more
/// above it).
struct OpenLikeCard: View {
    let card: PremiumCard
    let placeholder: Int
    let zone: TimeZone
    let onOpen: (() -> Void)?

    var body: some View {
        if let profile = card.profile {
            Card(padding: EdgeInsets(top: 14, leading: 14, bottom: 14, trailing: 14)) {
                HStack(alignment: .top, spacing: 14) {
                    photo
                    VStack(alignment: .leading, spacing: 7) {
                        // `display: flex; align-items: baseline; gap: 6; flex-wrap: wrap`, and `Not now`
                        // pushed to the end of the line. The label keeps its own width and the name and
                        // distance wrap in what it leaves, never the other way round.
                        HStack(alignment: .top, spacing: 6) {
                            ViewThatFits(in: .horizontal) {
                                HStack(alignment: .firstTextBaseline, spacing: 6) {
                                    name.fixedSize()
                                    distance(profile).fixedSize()
                                }
                                VStack(alignment: .leading, spacing: 2) {
                                    name
                                    distance(profile)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            if card.notNow { PillLabel(text: PremiumCopy.notNowLabel, compact: true) }
                        }
                        ShowUpCount(count: profile.attendedCount, size: .xs)
                        if let message = card.message, !message.trimmingCharacters(in: .whitespaces).isEmpty {
                            PrettyText(quotedMessage(message),
                                       font: TypeMetrics.uiFont(PS.loraItalic, 13.5, fallback: .italicSystemFont(ofSize: 13.5)),
                                       lineHeight: 13.5 * 1.4, color: .liqFg)
                        }
                        WrapLayout(spacing: 6, lineSpacing: 6) {
                            Chip(label: meetLine(profile.meetAt, day: profile.meetDay, zone: zone), selected: true,
                                 tone: .orange, size: .xs) { ink in BrandIconView(icon: .clock, size: 12, tint: ink) }
                            if let m = motivationOf(profile.motivation) {
                                Chip(label: m.title, tone: .lavender, size: .xs) { ink in BrandIconView(icon: m.icon, size: 12, tint: ink) }
                            }
                        }
                        ViewProfileButton(onOpen: onOpen)
                            .padding(.top, 2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var photo: some View {
        let shape = RoundedRectangle(cornerRadius: 16)
        return Button(action: { onOpen?() }) {
            PhotoBackdrop(url: card.photo, index: placeholder)
                .frame(width: 88, height: 120)
                .clipShape(shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(onOpen == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(likesPhotoLabel(card.name, notNow: card.notNow)))
        .accessibilityAddTraits(.isButton)
    }

    private var name: some View {
        Text(nameLine(card.name, card.age)).font(F.lora(17, bold: true)).foregroundColor(.liqFg)
    }

    private func distance(_ profile: SearchProfile) -> some View {
        Text(likesDistance(profile.distanceKm)).font(F.manrope(11.5, .semibold)).foregroundColor(.liqSubtle)
    }
}

/// `View profile`: the kit's `Button size="sm"` -- 36 tall, padding `0 16`, 700 / 14, sunset -- drawn at
/// its own width and answering at 44, the photo's target again.
private struct ViewProfileButton: View {
    let onOpen: (() -> Void)?

    var body: some View {
        PrimaryButton(PremiumCopy.viewProfile, variant: .sunset, height: 36, labelSize: 14, horizontalPadding: 16) {
            onOpen?()
        }
        .fixedSize()
        .disabled(onOpen == nil)
        // The 4 above and below that make 44 answer the same tap; the layout keeps the drawn 36.
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { onOpen?() }
        .padding(.vertical, -4)
        .fitAnchor(FitAnchorID.viewProfile)
    }
}

/// A closed card (12): the same row with no actions -- the raised background, no shadow, a 1px
/// `--liq-border`, the photo grayscale .92 at .7; the name in `--liq-fg-subtle`, the reason's first line
/// (Lora 700 / 15.5 / 1.25) and its second (12.5 / 1.45), centred in the column with gap 6. Not a tap
/// target. Announced by its first line when it closes in front of the user.
struct ClosedLikeCard: View {
    let card: PremiumCard
    let reason: CloseReason
    let placeholder: Int

    var body: some View {
        Card(fill: AnyShapeStyle(Color.liqRaised), borderColor: .liqBorder, borderWidth: 1, lifted: false,
             padding: EdgeInsets(top: 14, leading: 14, bottom: 14, trailing: 14)) {
            HStack(alignment: .center, spacing: 14) {
                PhotoBackdrop(url: card.photo, index: placeholder)
                    .frame(width: 88, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .grayscale(0.92)
                    .opacity(0.7)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text(nameLine(card.name, card.age)).font(F.lora(17, bold: true)).foregroundColor(.liqSubtle)
                    PrettyText(reason.likesHead(card.name),
                               font: TypeMetrics.uiFont(PS.loraBold, 15.5, fallback: .boldSystemFont(ofSize: 15.5)),
                               lineHeight: 15.5 * 1.25, color: .liqFg)
                    PrettyText(reason.likesBody,
                               font: TypeMetrics.uiFont(PS.manropeRegular, 12.5, fallback: .systemFont(ofSize: 12.5)),
                               lineHeight: 12.5 * 1.45, color: .liqNeutral)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// The empty state (G): padding `72 32 0`, centred, gap 14 -- a blush circle 88 with the heart 36
/// (stroke 2) in orange, `No likes yet` (the italic run washed; 6 more above), the line (14 / 1.5, at
/// most 290), and `Back to search` (md, sunset; 8 more above). Scrolls only when it must: at the largest
/// type on a short phone it outgrows the space above the TabBar, and `Back to search` would be cut.
private struct EmptyLikes: View {
    let onBackToSearch: () -> Void

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 14) {
                ZStack {
                    Circle().fill(LinearGradient(colors: Color.suGradBlush, startPoint: .top, endPoint: .bottom))
                    BrandIconView(icon: .heart, size: 36, stroke: 2, tint: .liqOrange)
                }
                .frame(width: 88, height: 88)
                .accessibilityHidden(true)
                WashHeadline(parts: [(PremiumCopy.emptyLead, false), (PremiumCopy.emptyEmphasis, true)],
                             fontSize: 24, lineHeightMultiple: 1.2, trackingEm: -0.01, centered: true,
                             cssLineBox: true, heading: true)
                    .padding(.top, 6)
                PrettyText(PremiumCopy.emptyBody,
                           font: TypeMetrics.uiFont(PS.manropeRegular, 14, fallback: .systemFont(ofSize: 14)),
                           lineHeight: 14 * 1.5, color: .liqNeutral, centered: true)
                    .frame(maxWidth: 290)
                PrimaryButton(PremiumCopy.backToSearch, variant: .sunset, height: 48, labelSize: 15, horizontalPadding: 22,
                              action: onBackToSearch)
                    .fixedSize()
                    .padding(.top, 8)
                    .fitAnchor(FitAnchorID.likesEmptyAction)
            }
            .frame(maxWidth: .infinity)
            .padding(EdgeInsets(top: 72, leading: 32, bottom: 24, trailing: 32))
        }
        .scrollBounceBehavior(.basedOnSize)
        .fitAnchor(FitAnchorID.likesEmpty)
    }
}

#Preview("Search 03 · A at rest") { LikesReceivedView(state: SearchSamples.likesRest(), zone: SearchSamples.zone) }
#Preview("Search 03 · D declined, the toast") {
    LikesReceivedView(state: {
        var s = SearchSamples.likesRest()
        s.likes.cards[0].gone = true
        s.undo = UndoToast(userId: s.likes.cards[0].userId, name: "Anna", from: .likes, position: 1)
        return s
    }(), zone: SearchSamples.zone)
}
#Preview("Search 03 · G empty") {
    LikesReceivedView(state: { var s = SearchSamples.likesRest(); s.likes = PremiumList(loading: false); return s }(),
                      zone: SearchSamples.zone)
}
#Preview("Search 03 · H flying") {
    LikesReceivedView(state: {
        var s = SearchSamples.likesRest()
        s.likes.cards[1].leaving = .notInterested
        return s
    }(), zone: SearchSamples.zone, freezeLeavingMs: 440)
}
#Preview("Search 03 · failed") {
    LikesReceivedView(state: { var s = SearchSamples.likesRest(); s.likes = PremiumList(loading: false, failure: .network); return s }(),
                      zone: SearchSamples.zone)
}
