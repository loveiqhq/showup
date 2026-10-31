//
//  SearchProfileView.swift
//  ShowUp · Date search 01, the Search tab for a Freemium user with a committed Check-In (SHOWUP-184)
//  -- and the same profile opened from the Premium grid (185) or the Likes tab (186)
//
//  EXTENDED, NOT FORKED ("a second profile component is a bug"). Opened from a list (`state.opened`)
//  the screen gains what the five reference props add: the Premium tag, the `All profiles` / `All
//  likes` back pill under the top bar, the `Liked you` badge on the hero and `Confirm date` in place of
//  Like, the Likes tab active with no filter and no chip, and every action returning to the list (the
//  model's). With none of them -- the Freemium queue -- it is Search 01, unchanged.
//
//  The Swift twin of `search/SearchProfileScreen.kt`. StatusBar -> SearchTopBar (1) -> the stack, the
//  only scroll container -> the action bar (2), sticky above the TabBar with its fade -> TabBar (3).
//  Values in, taps out: nothing here decides anything (`SearchModel` does), so every state renders at
//  every phone size with no device.
//
//  THE FRAME IS A LAYOUT, NOT A STACK OF OVERLAYS. The actions coach needs a scrim over everything --
//  top bar and TabBar included -- with the action bar lifted above it, at the bar's own place, on every
//  phone. `SearchFrameLayout` measures the top bar, the region and the TabBar, then places the scrim
//  over all three and the action bar directly on the TabBar's top edge. No fixed Y anywhere.
//

import AVFoundation
import SwiftUI

struct SearchProfileView: View {
    let state: SearchUiState
    var zone: TimeZone = .current
    var player: AVPlayer? = nil
    var onLike: () -> Void = {}
    var onPass: (SearchAnalytics.PassType) -> Void = { _ in }
    var onCoachDone: (SearchAnalytics.CoachDismiss, Bool) -> Void = { _, _ in }
    var onCancelConfirm: () -> Void = {}
    var onContinue: () -> Void = {}
    var onDraft: (String) -> Void = { _ in }
    var onDismissMessage: () -> Void = {}
    var onSkipMessage: () -> Void = {}
    var onSendMessage: () -> Void = {}
    var onOpenPhoto: (Int) -> Void = { _ in }
    var onViewerPage: (Int) -> Void = { _ in }
    var onCloseViewer: () -> Void = {}
    var onTogglePlay: (MediaKind) -> Void = { _ in }
    var onScrolledOut: (MediaKind) -> Void = { _ in }
    var onSectionSeen: (Int) -> Void = { _ in }
    var onFilters: () -> Void = {}
    var onLikesChip: () -> Void = {}
    var onTab: (AppTab) -> Void = { _ in }
    var onRetry: () -> Void = {}
    /// `All profiles` / `All likes`, and the swipe back with nothing over the profile.
    var onBack: () -> Void = {}
    /// `Confirm date` in mutual Be sure.
    var onConfirmDate: () -> Void = {}
    /// The sorry sheet closed, any way.
    var onRefusedDone: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let profile = state.current
        let opened = state.opened
        let fromLikes = opened?.from == .likes
        let activeTab: AppTab = fromLikes ? .likes : .search
        // Behind any modal, the screen is out of VoiceOver's reach -- the coach's three buttons too.
        let modal = state.coach || state.sheet != nil || state.viewerAt != nil
        ZStack {
            SearchFrameLayout {
                VStack(spacing: 0) {
                    // On the Likes tab: the wordmark and the tag only -- "the chip shows on the Search
                    // and Instant tabs only, never on ... a profile opened from it".
                    SearchTopBar(likes: state.likesBadge, onLikes: onLikesChip, onFilters: onFilters,
                                 premium: opened != nil, filter: !fromLikes, chip: !fromLikes)
                    if opened != nil {
                        BackPill(label: fromLikes ? PremiumCopy.allLikes : PremiumCopy.allProfiles, onBack: onBack)
                    }
                }
                .accessibilityHidden(modal)
                .fitAnchor(FitAnchorID.searchTopBar)
                region(profile)
                    .accessibilityHidden(modal)
                TabBar(active: activeTab, onTab: onTab, likes: state.likesBadge)
                    .accessibilityHidden(modal)
                    .fitAnchor(FitAnchorID.tabBar)
                scrim
                actionBar(profile)
            }
            // The like sheets' backdrop blur, arriving with the sheet. The coach's scrim has none.
            .blur(radius: state.sheet != nil ? searchSheetBlur : 0)
            .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.sheet), value: state.sheet)

            if let profile {
                switch state.sheet {
                case .some(.confirm):
                    BeSureSheet(name: profile.name, day: profile.meetDay,
                                onCancel: { _ in onCancelConfirm() },
                                onContinue: state.likedYou ? onConfirmDate : onContinue,
                                mutual: state.likedYou, confirming: state.confirming)
                case .some(.refused):
                    if let reason = state.refused {
                        ConfirmRefusedSheet(name: profile.name, photo: profile.photos.first ?? nil, reason: reason,
                                            buttonLabel: fromLikes ? PremiumCopy.backToLikes : PremiumCopy.findSomeoneElse,
                                            onDone: onRefusedDone)
                    }
                case .some(.message):
                    MessageSheet(name: profile.name, draft: state.draft, onDraft: onDraft,
                                 onDismiss: { _ in onDismissMessage() }, onSkip: onSkipMessage, onSend: onSendMessage)
                case .none:
                    EmptyView()
                }
                if let at = state.viewerAt {
                    SearchPhotoViewer(name: profile.name, photos: profile.photos.isEmpty ? [nil] : profile.photos,
                                      page: at, onPage: onViewerPage, onClose: onCloseViewer)
                }
            }
        }
        .background(Color.liqCream.ignoresSafeArea())
        // Opened from a list, with nothing over it: the swipe from the edge is the back pill. A sheet,
        // the viewer and the coach each keep the swipe for themselves.
        .modifier(EdgeSwipeBack(onBack: { if opened != nil && !modal { onBack() } }))
    }

    @ViewBuilder private func region(_ profile: SearchProfile?) -> some View {
        GeometryReader { g in
            ZStack {
                if let profile {
                    ScrollingStack(profile: profile, state: state, zone: zone, player: player,
                                   onOpenPhoto: onOpenPhoto, onTogglePlay: onTogglePlay,
                                   onScrolledOut: onScrolledOut, onSectionSeen: onSectionSeen)
                        // A new profile is a new scroll position: "the next profile replaces the stack
                        // and resets the scroll to the top". It slides in from a quarter width.
                        .id(profile.userId)
                        .transition(.asymmetric(
                            insertion: .offset(x: g.size.width / 4).combined(with: .opacity),
                            removal: .offset(x: -g.size.width / 4).combined(with: .opacity)))
                } else if state.loading {
                    Spinner(tint: .liqPurple, size: 24)
                        .accessibilityLabel(Text(SearchCopy.loadingLabel))
                } else if state.failure != nil {
                    // Words approved 27 Oct 2026.
                    CentredStatement(text: SearchCopy.loadFailed, action: SearchCopy.tryAgain, onAction: onRetry)
                } else {
                    // The queue ran dry: the interim no-results screen; `Widen my search` opens Filters.
                    SearchEmptyView(onWiden: onFilters)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
            .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.screen), value: profile?.userId)
        }
    }

    /// `rgba(29,17,41,.62)` over everything but the action bar. A tap is `Got it` -- never Don't show
    /// again -- reported as `backdrop`.
    @ViewBuilder private var scrim: some View {
        if state.coach {
            Color.liqFg.opacity(0.62)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { onCoachDone(.backdrop, false) }
                .accessibilityHidden(true)
        } else {
            Color.clear.frame(width: 0, height: 0)
        }
    }

    @ViewBuilder private func actionBar(_ profile: SearchProfile?) -> some View {
        if profile != nil {
            SearchActionBar(coach: state.coach, covered: state.coach || state.sheet != nil,
                            likedYou: state.likedYou, onLike: onLike, onPass: onPass,
                            onCoachDone: { dontShow in onCoachDone(.close, dontShow) })
                .accessibilityHidden(state.sheet != nil || state.viewerAt != nil)
        } else {
            Color.clear.frame(width: 0, height: 0)
        }
    }
}

/// Top bar, region and TabBar in a column; the scrim over all three; the action bar on the TabBar's
/// top edge -- never taller than the space between the top bar and the TabBar, so the coach in it
/// cannot reach under the top bar ("never clipped by the top bar") at any type size.
private struct SearchFrameLayout: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 5 else { return }
        let w = bounds.width
        let h = bounds.height
        let row = ProposedViewSize(width: w, height: nil)
        let top = subviews[0].sizeThatFits(row).height
        let tab = subviews[2].sizeThatFits(row).height
        subviews[0].place(at: bounds.origin, proposal: ProposedViewSize(width: w, height: top))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + top),
                          proposal: ProposedViewSize(width: w, height: max(0, h - top - tab)))
        subviews[2].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - tab), proposal: ProposedViewSize(width: w, height: tab))
        subviews[3].place(at: bounds.origin, proposal: ProposedViewSize(width: w, height: h))
        let room = max(0, h - top - tab)
        let bar = min(room, subviews[4].sizeThatFits(ProposedViewSize(width: w, height: room)).height)
        subviews[4].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - tab - bar), proposal: ProposedViewSize(width: w, height: bar))
    }
}

// MARK: - the top bar (callout 1)

/// Wordmark at 20, then -- for Premium -- the Premium tag at size 10, then the likes chip and the filter
/// button, gap 10. Both buttons draw smaller than 44 and answer at 44, without growing the bar. The chip
/// shows the open-likes count -- the Likes badge's number -- and its tap is the host's: Premium, the
/// Likes tab; Freemium, the likes upsell, its own story, so nothing until it ships.
///
/// `premium`: the tag (Search 02 1), a label, not a control. `filter` / `chip` false on the Likes tab and
/// a profile opened from it (Search 03 1).
struct SearchTopBar: View {
    let likes: Int
    let onLikes: () -> Void
    let onFilters: () -> Void
    var premium = false
    var filter = true
    var chip = true

    var body: some View {
        // ONE ROW AS DRAWN: the wordmark at the start, the tag, the chip and the filter at the end, gap
        // 10. Where they do not fit side by side -- the Premium bar on the 320 Fold cover screen, or any
        // bar at a large type -- the trailing group drops to a second row, still at the end, rather than
        // running off the screen or cutting the chip's count (the 185 fit sweep, 25 Oct 2026).
        TopBarLayout {
            // A logo, at 20 whatever the type size, as on the gate.
            Wordmark(size: 20, gapEm: wordmarkTabGap)
                .dynamicTypeSize(.large)
            HStack(spacing: 10) {
                if premium { PremiumTag(size: 10) }
                if chip { likesChip }
                if filter { filterButton }
            }
        }
        .padding(EdgeInsets(top: 4, leading: 16, bottom: 8, trailing: 16))
        .background(Color.liqCream)
    }

    /// The likes chip: the sunset pill, padding `6 12`, the filled heart 13 and `{n} likes`, 700 / 12.
    private var likesChip: some View {
        let label = likes == 1 ? "1 like" : "\(likes) likes"
        return Button(action: onLikes) {
            HStack(spacing: 6) {
                BrandIconView(icon: .heartFilled, size: 13, tint: .white)
                Text(label).font(F.manrope(12, .bold)).foregroundColor(.white).lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Gradients.sunset(), in: Capsule())
            .answersAt44()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }

    /// The filter button: 36 round, white, 1px `--liq-border`, the filter icon 16. `Filters`.
    private var filterButton: some View {
        Button(action: onFilters) {
            ZStack {
                Circle().fill(Color.liqElevated)
                Circle().strokeBorder(Color.liqBorder, lineWidth: 1)
                BrandIconView(icon: .filter, size: 16, tint: .liqFg)
            }
            .frame(width: 36, height: 36)
            .answersAt44()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(SearchCopy.filtersLabel))
    }
}

/// The top bar's two parts: the mark at the start and the trailing group at the end of one row,
/// centred on each other -- or, where they do not fit side by side, the group on a second row 8 below,
/// still at the end.
private struct TopBarLayout: Layout {
    private let gap: CGFloat = 10
    private let rowGap: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let mark = subviews[0].sizeThatFits(.unspecified)
        let tail = subviews[1].sizeThatFits(.unspecified)
        let w = proposal.width ?? mark.width + gap + tail.width
        if mark.width + gap + tail.width <= w { return CGSize(width: w, height: max(mark.height, tail.height)) }
        return CGSize(width: w, height: mark.height + rowGap + tail.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let mark = subviews[0].sizeThatFits(.unspecified)
        let tail = subviews[1].sizeThatFits(.unspecified)
        let w = bounds.width
        if mark.width + gap + tail.width <= w {
            let h = max(mark.height, tail.height)
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY + (h - mark.height) / 2), proposal: ProposedViewSize(mark))
            subviews[1].place(at: CGPoint(x: bounds.maxX - tail.width, y: bounds.minY + (h - tail.height) / 2),
                              proposal: ProposedViewSize(tail))
        } else {
            subviews[0].place(at: bounds.origin, proposal: ProposedViewSize(mark))
            subviews[1].place(at: CGPoint(x: max(bounds.minX, bounds.maxX - tail.width), y: bounds.minY + mark.height + rowGap),
                              proposal: ProposedViewSize(tail))
        }
    }
}

/// The back pill (Search 02 13), under the top bar: padding `0 16 10`; a 36-tall pill, padding
/// `0 14 0 8`, 1px `--liq-border` on white, the chevron 18 (stroke 2.2) and the label in Manrope 700 /
/// 13, gap 2. Drawn at 36 -- taller only when a large type needs it -- answering at 44.
/// The back pill (Search 02 13). `rowBackground` is the profile's cream band under its top bar; the
/// location gate (SHOWUP-188 ⑪) puts the same pill straight on its backdrop and passes `.clear`.
struct BackPill: View {
    let label: String
    let onBack: () -> Void
    var rowBackground: Color = .liqCream

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                HStack(spacing: 2) {
                    BrandIconView(icon: .chevronLeft, size: 18, stroke: 2.2, tint: .liqFg)
                    Text(label)
                        .font(F.manrope(13, .bold))
                        .foregroundColor(.liqFg)
                        .lineLimit(1)
                        .fixedSize()
                }
                .padding(.leading, 8)
                .padding(.trailing, 14)
                .frame(minHeight: 36)
                .background(Capsule().fill(Color.liqElevated))
                .overlay(Capsule().strokeBorder(Color.liqBorder, lineWidth: 1))
                .answersAt44()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(label))
            .fitAnchor(FitAnchorID.backPill)
            Spacer(minLength: 0)
        }
        .padding(EdgeInsets(top: 0, leading: 16, bottom: 10, trailing: 16))
        .background(rowBackground)
    }
}

private extension View {
    /// Drawn its own size, answering at 44 both ways: a 44 frame whose overflow is taken back by an
    /// equal negative padding, so the bar is laid out on the drawn controls. Android's
    /// `OverflowTapTarget`.
    func answersAt44() -> some View {
        modifier(AnswersAt44())
    }
}

private struct AnswersAt44: ViewModifier {
    @State private var drawn: CGSize = .zero

    func body(content: Content) -> some View {
        let floor = ComponentSizes.minTapTarget
        let dx = max(0, floor - drawn.width) / 2
        let dy = max(0, floor - drawn.height) / 2
        content
            .background(GeometryReader { g in
                Color.clear.onAppear { drawn = g.size }.onChange(of: g.size) { _, s in drawn = s }
            })
            .padding(.horizontal, dx)
            .padding(.vertical, dy)
            .contentShape(Rectangle())
            .padding(.horizontal, -dx)
            .padding(.vertical, -dy)
    }
}

// MARK: - the action bar (callout 2) and the coach (F)

/// `Not interested` 56, `Not now` 56, `Like` 64 with its label, gap 10, centred, in a column padded
/// 12 / 16 over a fade into the canvas. While the coach shows, the card sits above the buttons in this
/// same column (gap 14), the fade is removed, and the buttons stay visible but do nothing.
struct SearchActionBar: View {
    let coach: Bool
    /// The coach or a like sheet is over the profile; when it closes, focus goes back to Like.
    let covered: Bool
    /// A profile that liked the viewer: the third button is `Confirm date` with a check.
    var likedYou = false
    let onLike: () -> Void
    let onPass: (SearchAnalytics.PassType) -> Void
    let onCoachDone: (Bool) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// "On close, focus goes to Like" -- for the coach and both sheets, on the closing edge only.
    @AccessibilityFocusState private var likeFocused: Bool

    var body: some View {
        VStack(spacing: 14) {
            if coach {
                SearchActionsCoach(onDone: onCoachDone)
                    .fitAnchor(FitAnchorID.searchCoach)
                    .transition(.opacity)
            }
            HStack(spacing: 10) {
                RoundAction(icon: .close, label: SearchCopy.notInterested, ink: .liqDanger,
                            outline: Color.liqDanger.opacity(0.4)) { onPass(.notInterested) }
                RoundAction(icon: .clock, label: SearchCopy.notNow, ink: .liqMuted, outline: .liqBorder) { onPass(.notNow) }
                Button(action: onLike) {
                    HStack(spacing: 8) {
                        BrandIconView(icon: likedYou ? .check : .heart, size: 22, stroke: 2.4, tint: .white)
                        ActionLabel(text: likedYou ? PremiumCopy.confirmDate : SearchCopy.like)
                    }
                    .padding(.horizontal, 24)
                    // A minimum: at the largest type `Confirm date` takes two lines, and the pill grows
                    // to hold them rather than cutting the label.
                    .frame(minHeight: 64)
                    .background(Gradients.sunset(), in: Capsule())
                    .background(CapsuleVioletGlow())
                }
                .buttonStyle(PressScale())
                .accessibilityLabel(Text(likedYou ? PremiumCopy.confirmDate : SearchCopy.like))
                .accessibilityFocused($likeFocused)
                .fitAnchor(FitAnchorID.searchLike)
            }
            .frame(maxWidth: .infinity)
            // Inert under the coach: the taps are not sent at all, and the three are hidden from
            // VoiceOver while it shows ("everything behind it, the three buttons included").
            .allowsHitTesting(!coach)
            .accessibilityHidden(coach)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(fade)
        .fitAnchor(FitAnchorID.searchActionBar)
        .animation(reduceMotion ? nil : .easeOut(duration: Motion.fast), value: coach)
        .onChange(of: covered) { wasCovered, isCovered in
            if wasCovered && !isCovered { likeFocused = true }
        }
    }

    /// `linear-gradient(180deg, rgba(255,251,247,0) 0%, .95 35%, 1 100%)`, removed under the coach.
    @ViewBuilder private var fade: some View {
        if !coach {
            LinearGradient(stops: [
                .init(color: Color.liqCream.opacity(0), location: 0),
                .init(color: Color.liqCream.opacity(0.95), location: 0.35),
                .init(color: Color.liqCream, location: 1),
            ], startPoint: .top, endPoint: .bottom)
        }
    }
}

/// The primary action's label: one line wherever it fits, as drawn. Where it does not -- `Confirm date`
/// at the largest type beside two round buttons on a narrow phone -- two lines, set just small enough
/// that the longest word fits, so it is never cut and never broken inside a word (the 185 fit sweep,
/// 25 Oct 2026).
private struct ActionLabel: View {
    let text: String

    var body: some View {
        ViewThatFits(in: .horizontal) {
            Text(text).font(F.manrope(14, .bold)).foregroundColor(.white).lineLimit(1).fixedSize()
            Text(text)
                .font(F.manrope(14, .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.5)
                .allowsTightening(true)
        }
    }
}

/// One of the two icon-only actions: never unlabelled.
private struct RoundAction: View {
    let icon: BrandIcon
    let label: String
    let ink: Color
    let outline: Color
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack {
                // `--liq-shadow-sm`: `0 1px 2px rgba(46,1,71,.04)`.
                Circle().fill(Color.liqElevated).shadow(color: Color.liqShadowInk.opacity(0.04), radius: 1, y: 1)
                Circle().strokeBorder(outline, lineWidth: 1.5)
                BrandIconView(icon: icon, size: 22, stroke: 2, tint: ink)
            }
            .frame(width: 56, height: 56)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(label))
    }
}

// MARK: - the stack

private let searchStackSpace = "search-stack"

/// The stack in its scroll container, reporting what scrolls into view and what playing card leaves it.
private struct ScrollingStack: View {
    let profile: SearchProfile
    let state: SearchUiState
    let zone: TimeZone
    let player: AVPlayer?
    let onOpenPhoto: (Int) -> Void
    let onTogglePlay: (MediaKind) -> Void
    let onScrolledOut: (MediaKind) -> Void
    let onSectionSeen: (Int) -> Void

    var body: some View {
        GeometryReader { g in
            ScrollView(.vertical, showsIndicators: false) {
                SearchProfileStack(profile: profile, viewer: state.viewer, zone: zone, playback: state.playback,
                                   player: player, likedYou: state.likedYou,
                                   onOpenPhoto: onOpenPhoto, onTogglePlay: onTogglePlay,
                                   visibility: { index, kind in
                                       SectionVisibility(index: index, kind: kind, viewport: g.size.height,
                                                         onSeen: onSectionSeen, onOut: onScrolledOut)
                                   })
            }
            .coordinateSpace(name: searchStackSpace)
            .scrollDisabled(state.coach)
            .fitAnchor(FitAnchorID.searchStack)
        }
    }
}

/// "It came into view" for a section, and for a media card "it left": its frame in the scroll view's
/// own space against the viewport, re-read as it scrolls.
struct SectionVisibility: ViewModifier {
    let index: Int
    let kind: MediaKind?
    let viewport: CGFloat
    let onSeen: (Int) -> Void
    let onOut: (MediaKind) -> Void

    func body(content: Content) -> some View {
        content.background(GeometryReader { g in
            let frame = g.frame(in: .named(searchStackSpace))
            Color.clear
                .onAppear { report(frame) }
                .onChange(of: frame) { _, now in report(now) }
        })
    }

    private func report(_ frame: CGRect) {
        if frame.maxY > 0 && frame.minY < viewport {
            onSeen(index)
        } else if let kind {
            onOut(kind)
        }
    }
}

/// No visibility reporting: the unscrolled stack of previews and the full-length fit frame.
struct NoVisibility: ViewModifier {
    func body(content: Content) -> some View { content }
}

/// The stack, in the reference's JSX order, which IS the spec: hero -> 18 -> shared vibes -> highlight
/// -> vibe today -> photo 2 -> prompt 1 -> voice -> essentials -> photo 3 -> prompt 2 -> video -> How I
/// live -> interests -> prompt 3 -> photos 4 and 5 side by side -> photo 6 -> charms -> 80. Padding
/// 0 / 16 / 16, a column with gap 14. A missing section is not drawn and the gap closes by itself.
struct SearchProfileStack<Visibility: ViewModifier>: View {
    let profile: SearchProfile
    let viewer: SearchViewer
    let zone: TimeZone
    let playback: SearchPlayback?
    let player: AVPlayer?
    /// The hero's `Liked you` badge (Search 02).
    var likedYou = false
    let onOpenPhoto: (Int) -> Void
    let onTogglePlay: (MediaKind) -> Void
    /// Each section's "it came into view": its index, and the media it carries.
    let visibility: (Int, MediaKind?) -> Visibility

    var body: some View {
        let photos = profile.photos
        let prompts = profile.prompts
        let shared = sharedVibesRow(sharedVibes(profile.vibes, mine: viewer.vibes))
        let rows = essentials(profile.essentials)
        let tiles = lifestyleTiles(profile.howILive)
        let uplift = profile.uplift?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let order = StackSections(profile: profile, shared: shared, rows: rows, tiles: tiles, uplift: uplift)
        VStack(alignment: .leading, spacing: 14) {
            SearchHero(profile: profile, zone: zone, onPhoto: onOpenPhoto, likedYou: likedYou)
                .modifier(visibility(order.index(.hero), nil))
            Color.clear.frame(height: 18)

            if order.has(.shared) {
                SharedVibesCard(name: profile.name, row: shared).modifier(visibility(order.index(.shared), nil))
            }
            if order.has(.highlight) {
                HighlightCard(name: profile.name, uplift: profile.uplift ?? "").modifier(visibility(order.index(.highlight), nil))
            }
            if order.has(.vibeToday) {
                VibeTodayCard(name: profile.name, vibes: profile.vibes, mine: viewer.vibes)
                    .modifier(visibility(order.index(.vibeToday), nil))
            }
            if order.has(.photo2) { stackPhoto(photos[1], 1).modifier(visibility(order.index(.photo2), nil)) }
            if order.has(.prompt1) { PromptCard(prompt: prompts[0]).modifier(visibility(order.index(.prompt1), nil)) }
            if let voice = profile.voice {
                VoiceCard(name: profile.name, media: voice, playing: playback?.kind == .voice ? playback : nil) {
                    onTogglePlay(.voice)
                }
                .modifier(visibility(order.index(.voice), .voice))
            }
            if order.has(.essentials) {
                EssentialsCard(name: profile.name, rows: rows).modifier(visibility(order.index(.essentials), nil))
            }
            if order.has(.photo3) { stackPhoto(photos[2], 2).modifier(visibility(order.index(.photo3), nil)) }
            if order.has(.prompt2) { PromptCard(prompt: prompts[1]).modifier(visibility(order.index(.prompt2), nil)) }
            if let video = profile.video {
                VideoCard(name: profile.name, media: video, posterURL: photos.count > 3 ? photos[3] : nil,
                          playing: playback?.kind == .video ? playback : nil, player: player) {
                    onTogglePlay(.video)
                }
                .modifier(visibility(order.index(.video), .video))
            }
            if order.has(.howILive) {
                HowILiveCard(name: profile.name, tiles: tiles).modifier(visibility(order.index(.howILive), nil))
            }
            if order.has(.interests) {
                SharedInterestsBlock(name: profile.name, interests: profile.interests, mine: viewer.interests)
                    .modifier(visibility(order.index(.interests), nil))
            }
            if order.has(.prompt3) { PromptCard(prompt: prompts[2]).modifier(visibility(order.index(.prompt3), nil)) }

            if order.has(.photos45) {
                HStack(spacing: 10) {
                    SearchPhoto(url: photos[3], index: 3, height: 220, radius: 20, name: profile.name,
                                onTap: { onOpenPhoto(3) }) { EmptyView() }
                    SearchPhoto(url: photos[4], index: 4, height: 220, radius: 20, name: profile.name,
                                onTap: { onOpenPhoto(4) }) { EmptyView() }
                }
                .modifier(visibility(order.index(.photos45), nil))
            } else if order.has(.photo4) {
                stackPhoto(photos[3], 3).modifier(visibility(order.index(.photo4), nil))
            }
            if order.has(.photo6) { stackPhoto(photos[5], 5).modifier(visibility(order.index(.photo6), nil)) }
            if order.has(.charms) { CharmsCard(charms: profile.charms).modifier(visibility(order.index(.charms), nil)) }

            Color.clear.frame(height: 80)
        }
        .padding(EdgeInsets(top: 0, leading: 16, bottom: 16, trailing: 16))
    }

    /// A single photo in the stack: 300 tall, radius 24.
    private func stackPhoto(_ url: URL?, _ index: Int) -> some View {
        SearchPhoto(url: url, index: index, height: 300, radius: 24, name: profile.name,
                    onTap: { onOpenPhoto(index) }) { EmptyView() }
    }
}

extension SearchProfileStack where Visibility == NoVisibility {
    /// The stack with no visibility reporting.
    init(profile: SearchProfile, viewer: SearchViewer, zone: TimeZone, playback: SearchPlayback? = nil,
         player: AVPlayer? = nil, likedYou: Bool = false, onOpenPhoto: @escaping (Int) -> Void = { _ in },
         onTogglePlay: @escaping (MediaKind) -> Void = { _ in }) {
        self.init(profile: profile, viewer: viewer, zone: zone, playback: playback, player: player, likedYou: likedYou,
                  onOpenPhoto: onOpenPhoto, onTogglePlay: onTogglePlay, visibility: { _, _ in NoVisibility() })
    }
}

/// The stack's sections, in order, and which of them this profile draws -- so each drawn section has
/// its index for `sections_scrolled` before the body is built. Android numbers them as it goes.
private enum StackSection: CaseIterable {
    case hero, shared, highlight, vibeToday, photo2, prompt1, voice, essentials, photo3, prompt2, video,
         howILive, interests, prompt3, photos45, photo4, photo6, charms
}

private struct StackSections {
    private let drawn: [StackSection]

    init(profile: SearchProfile, shared: SharedVibesRow, rows: [Essential], tiles: [LifestyleTile], uplift: String) {
        let photos = profile.photos.count
        let prompts = profile.prompts.count
        let present: [StackSection: Bool] = [
            .hero: true,
            .shared: !shared.shown.isEmpty,
            .highlight: !uplift.isEmpty,
            .vibeToday: profile.vibes.contains { vibeLabel($0) != nil },
            .photo2: photos > 1,
            .prompt1: prompts > 0,
            .voice: profile.voice != nil,
            .essentials: !rows.isEmpty,
            .photo3: photos > 2,
            .prompt2: prompts > 1,
            .video: profile.video != nil,
            .howILive: !tiles.isEmpty,
            .interests: profile.interests.contains { interestLabel($0) != nil },
            .prompt3: prompts > 2,
            .photos45: photos > 4,
            // Photo 4 without photo 5 is a single 300.
            .photo4: photos == 4,
            .photo6: photos > 5,
            .charms: !profile.charms.isEmpty,
        ]
        drawn = StackSection.allCases.filter { present[$0] ?? false }
    }

    func has(_ section: StackSection) -> Bool { drawn.contains(section) }

    func index(_ section: StackSection) -> Int { drawn.firstIndex(of: section) ?? 0 }
}

// MARK: - loading, failed, no results

struct CentredStatement: View {
    let text: String
    let action: String
    let onAction: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Text(text)
                .font(F.manrope(15, .medium))
                .foregroundColor(.liqNeutral)
                .multilineTextAlignment(.center)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 15, multiple: 1.5))
                .cssLineBox(15 * 1.5)
                .frame(maxWidth: 300)
            PrimaryButton(action, variant: .ghost, height: 48, labelSize: 15, action: onAction)
                .frame(maxWidth: 240)
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview("Search 01 · A at rest") { SearchProfileView(state: SearchSamples.rest(), zone: SearchSamples.zone) }
#Preview("Search 01 · B Be sure") {
    SearchProfileView(state: { var s = SearchSamples.rest(); s.sheet = .confirm; return s }(), zone: SearchSamples.zone)
}
#Preview("Search 01 · C message") {
    SearchProfileView(state: { var s = SearchSamples.rest(); s.sheet = .message; s.draft = messageDraft("Leonie", "today"); return s }(),
                      zone: SearchSamples.zone)
}
#Preview("Search 01 · D minimum") { SearchProfileView(state: SearchSamples.rest(SearchSamples.minimum), zone: SearchSamples.zone) }
#Preview("Search 01 · F coach") {
    SearchProfileView(state: { var s = SearchSamples.rest(); s.coach = true; return s }(), zone: SearchSamples.zone)
}
#Preview("Search 01 · loading") { SearchProfileView(state: SearchUiState(), zone: SearchSamples.zone) }
#Preview("Search 01 · failed") {
    SearchProfileView(state: { var s = SearchUiState(); s.loading = false; s.failure = .network; return s }(), zone: SearchSamples.zone)
}
