// DateScreen.swift
// ShowUp · the Date screen, Date 01 (SHOWUP-193): the screen both people live on from the match until
// the date ends. `ScreenMatchCoord` in `screen-date-reference.jsx`; Android's `DateScreen.kt`.
//
// ─────────────────────────────────────────────────────────────────────────────
// LAYOUT -- one scroll, one sticky footer, no absolute Y
// ─────────────────────────────────────────────────────────────────────────────
//
// One scroll holds the content column (padding 0 16 24, gap 8, ending in an 80 spacer) with the sunset
// wash over it. The footer -- the bar and the TabBar -- is one container at the bottom; the scroll ends
// with a spacer of its measured height, so the last card clears it by the 80.
//
// ORDER IS FIXED (the ticket): hero → time + place → Coordinate → Report → intent → quick actions →
// About → common → How the date works → Match Mate (Date 10 adds it here) → Cancel this date. In C the
// SAME Coordinate card moves above time + place; nothing else moves. No header, no back.
//
// THE WASH IS OVER THE CARDS, NOT UNDER THEM. It is `position: absolute` in the kit and the cards are
// not, so CSS paints it above them at its .18 -- the sheet's time card is deeper lilac at its top, and
// C's card lavender, for that reason. The hero and the unread avatar are positioned and come after the
// wash, so they paint above it: they report their frames (`aboveWash`) and the wash leaves them out.
//
// THE BAR GIVES WAY AT THE LARGEST TYPE. Unless the bar and the TabBar together would cover more than
// 60% of the screen, they are one sticky footer; past that the bar ends the scroll instead and the
// TabBar alone stays pinned. Everything stays reachable, nothing is cut.

import SwiftUI

/// Search, Likes and Instant are disabled from the match until the date ends; Settings stays open.
let dateLockedTabs: Set<AppTab> = [.search, .likes, .instant]

// MARK: - the wash and what paints above it

/// A shape the wash leaves out, in the content's coordinates.
struct WashHole: Equatable, Sendable {
    enum Outline: Equatable, Sendable {
        case circle
        case rounded(CGFloat)
    }

    let outline: Outline
    let frame: CGRect
}

struct WashHoleKey: PreferenceKey {
    static let defaultValue: [WashHole] = []
    static func reduce(value: inout [WashHole], nextValue: () -> [WashHole]) { value += nextValue() }
}

/// The coordinate space the holes are reported in: the content column the wash lies over.
private let dateContentSpace = "date-content"

extension View {
    /// Paint this above the wash, as CSS does a positioned element after it.
    func aboveWash(_ outline: WashHole.Outline) -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: WashHoleKey.self,
                                   value: [WashHole(outline: outline, frame: g.frame(in: .named(dateContentSpace)))])
        })
    }
}

/// ① The sunset wash: top 0, h 320, opacity .18, `blur(40px)`. The blur is DRAWN as the fade it makes at
/// the bottom edge (the top and sides are the screen's): full until 2σ above the 320 edge, gone 2σ below.
private struct DateWash: View {
    let holes: [WashHole]

    var body: some View {
        CSSLinearGradient(angle: 135, colors: Color.suGradSunsetStops.map(\.color),
                          stops: Color.suGradSunsetStops.map(\.location), boxHeight: 320)
            .frame(height: 400)
            .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: 240.0 / 400.0),
                                         .init(color: .clear, location: 1)],
                                 startPoint: .top, endPoint: .bottom))
            .opacity(0.18)
            .mask(alignment: .topLeading) {
                Canvas { context, size in
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black))
                    context.blendMode = .destinationOut
                    for hole in holes {
                        let path: Path
                        switch hole.outline {
                        case .circle: path = Path(ellipseIn: hole.frame)
                        case .rounded(let r): path = Path(roundedRect: hole.frame, cornerRadius: r)
                        }
                        context.fill(path, with: .color(.black))
                    }
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// The measured heights the footer rule reads.
private struct DateHeightsKey: PreferenceKey {
    static let defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue()) { _, new in new }
    }
}

private extension View {
    func measuredHeight(_ id: String) -> some View {
        background(GeometryReader { g in Color.clear.preference(key: DateHeightsKey.self, value: [id: g.size.height]) })
    }
}

// MARK: - the screen

struct DateScreen: View {
    let ui: DateUi
    var zone: TimeZone = .current
    var onOpenChat: () -> Void = {}
    var onReport: () -> Void = {}
    var onDirections: () -> Void = {}
    var onShare: () -> Void = {}
    var onCalendar: () -> Void = {}
    var onCopy: () -> Void = {}
    var onSeeProfile: () -> Void = {}
    var onCancel: () -> Void = {}
    var onCheckIn: () -> Void = {}
    var onEndDate: () -> Void = {}
    var onTab: (AppTab) -> Void = { _ in }
    /// `Try again` when the first read failed and there is nothing to show.
    var onRetry: () -> Void = {}

    /// Measured, local and disposable: what the footer rule needs.
    @State private var heights: [String: CGFloat] = [:]

    /// The most of the screen the sticky footer may cover before the bar moves into the scroll.
    private static let footerMaxShare: CGFloat = 0.6

    var body: some View {
        let frame = heights["frame"] ?? 0
        let bar = heights["bar"] ?? 0
        let tab = heights["tab"] ?? 0
        let barInFlow = frame > 0 && bar + tab > frame * Self.footerMaxShare
        // THE SCREEN IS THE PHONE'S WIDTH, EXACTLY. A ZStack takes the width of its widest child, so a
        // footer a point too rigid (a long name at the 402-wide phone's exact width) widened the scroll
        // with it and moved every card off its 16 gutter. Pinned here, nothing inside can.
        GeometryReader { g in
        ZStack(alignment: .bottom) {
            // Names and districts breakable after their hyphens, as Android's (the 4 Nov 2026 audit).
            if let date = ui.date.map(withBreakableNames), let state = ui.state {
                ScrollView {
                    VStack(spacing: 0) {
                        content(date, state)
                            .coordinateSpace(name: dateContentSpace)
                            .overlayPreferenceValue(WashHoleKey.self) { holes in
                                DateWash(holes: holes)
                            }
                        if barInFlow { barView(date) }
                        Color.clear.frame(height: tab + (barInFlow ? 0 : bar))
                    }
                    // The whole scroll, reserve included: where its end comes to rest (DateFitTests).
                    .fitAnchor("date:content")
                }
                .fitAnchor("date:screen")
                VStack(spacing: 0) {
                    if !barInFlow { barView(date) }
                    tabBar
                }
                .fitAnchor("date:footer")
                .fitPinnedBar()
            } else {
                // The first read failed with nothing to show (offline as a match lands): Search's failed
                // statement, said of the date, and the locked bar -- the lock holds whatever the network.
                // Loading is cream and the bar alone: the screen is reached only once a read found a date.
                if !ui.loading, ui.failed != nil {
                    CentredStatement(text: DateCopy.loadFailed, action: SearchCopy.tryAgain, onAction: onRetry)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.bottom, tab)
                }
                tabBar
            }
        }
        .frame(width: g.size.width, height: g.size.height)
        }
        .measuredHeight("frame")
        .background(Color.liqCream.ignoresSafeArea())
        .onPreferenceChange(DateHeightsKey.self) { heights = $0 }
    }

    private var tabBar: some View {
        // Cream under the .92 glass: the kit blurs what scrolls behind the bar, which over these cards
        // reads as cream; without a backdrop blur the cards' text showed through it.
        TabBar(active: nil, onTab: onTab, likes: 0, disabled: dateLockedTabs, disabledHint: DateCopy.tabHint)
            .background(Color.liqCream.ignoresSafeArea(edges: .bottom))
            .measuredHeight("tab")
            .fitAnchor(FitAnchorID.tabBar)
    }

    private func barView(_ date: ActiveDate) -> some View {
        DateBar(checkedIn: date.checkedIn, name: date.match.name, onCheckIn: onCheckIn, onEndDate: onEndDate)
            .measuredHeight("bar")
    }

    /// The content column: padding 0 16 24, gap 8, in the ticket's fixed order, ending in the 80.
    private func content(_ date: ActiveDate, _ state: DateState) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            DateHero(photoUrl: date.match.photoUrl)
                .aboveWash(.rounded(24))
                .padding(.top, 8)
                .fitAnchor("date:hero")
            Color.clear.frame(height: 12)
            if state == .unread { coordinate(date, state) }
            TimePlaceCard(date: date, zone: zone, copied: ui.copied, onCopy: onCopy).fitAnchor("date:time-card")
            if state != .unread { coordinate(date, state) }
            ReportRow(onReport: onReport).fitAnchor("date:report")
            IntentCard(date: date).fitAnchor("date:intent")
            QuickActions(hasPlace: date.place != nil, onDirections: onDirections, onShare: onShare, onCalendar: onCalendar)
                .fitAnchor("date:actions")
            Color.clear.frame(height: 2)
            AboutCard(match: date.match, onSeeProfile: onSeeProfile).fitAnchor("date:about")
            CommonCard(date: date).fitAnchor("date:common")
            DateWorksCard(date: date, zone: zone).fitAnchor("date:works")
            // ⑮ Match Mate: Date 10 adds its card here, between How the date works and Cancel.
            Color.clear.frame(height: 24)
            CancelLink(onCancel: onCancel).fitAnchor("date:cancel")
            Color.clear.frame(height: 80)
        }
        .padding(EdgeInsets(top: 0, leading: 16, bottom: 24, trailing: 16))
    }

    private func coordinate(_ date: ActiveDate, _ state: DateState) -> some View {
        CoordinateModule(state: state, date: date, now: ui.now, zone: zone, onOpenChat: onOpenChat)
            .fitAnchor("date:coordinate")
    }
}
