//
//  TabBar.swift
//  ShowUp · the app's bottom navigation: Search, Likes, Instant, Settings (`components/shared.jsx` TabBar)
//
//  The Swift twin of `designsystem/TabBar.kt`. First drawn for the Check-In gate (SHOWUP-178),
//  where Search is active and "the tabs are NOT disabled": Search and Instant reopen the Check-In,
//  Settings navigates. A design-system primitive rather than a Check-In one, because every tab
//  screen after it -- Search, Match, Settings -- sits on the same bar.
//
//  FOUR TABS FOR EVERY USER since the Date search epic (SHOWUP-187 rule 10, 21 Oct 2026). The Likes
//  badge counts open received likes, Freemium too; where the tab goes is the host's decision. One
//  bar with a `likes` count -- a second bar for Premium is the bug the epic names.
//
//  THE DISABLED STATE (SHOWUP-193 ⑰) is the active-date lock: from the match until the date ends,
//  Search, Likes and Instant are `disabled` and Settings stays open. A disabled tab is drawn faint
//  with the kit's lock badge; a tap shows `disabledHint` above the bar for `Motion.tabHint` and does
//  not call `onTab`, so nothing navigates and nothing is tracked. No tab is active on the Date screen,
//  so `active` is optional. The kit blurs what is behind the bar; over flat cream the .92 glass alone
//  is indistinguishable from it, as on Android.
//

import SwiftUI

/// The four tabs, in bar order. Android's `Tab`; `AppTab` here because SwiftUI has a `Tab` of its own
/// (iOS 18's TabView API), and a name that shadows the framework's reads wrong at every call site.
enum AppTab: CaseIterable, Sendable {
    case search, likes, instant, settings

    var label: String {
        switch self {
        case .search: return "Search"
        case .likes: return "Likes"
        case .instant: return "Instant"
        case .settings: return "Settings"
        }
    }

    var icon: BrandIcon {
        switch self {
        case .search: return .search
        case .likes: return .heart
        case .instant: return .zap
        case .settings: return .settings
        }
    }

    /// `enums.json` §37 `nav_tab`.
    var trackingValue: String {
        switch self {
        case .search: return "search"
        case .likes: return "likes"
        case .instant: return "instant"
        case .settings: return "settings"
        }
    }
}

/// What the badge reads: the count, capped at `99+` (SHOWUP-186 ②). Nil for none at all.
func likesBadgeText(_ count: Int) -> String? {
    if count <= 0 { return nil }
    return count > 99 ? "99+" : String(count)
}

/// What `tab` shows over its icon: the Likes count, on the Likes tab while it is open -- a locked tab shows no
/// number, and the count returns when the tab does (SHOWUP-193, 9 Nov 2026).
func tabBadgeText(_ tab: AppTab, likes: Int, locked: Bool) -> String? {
    tab == .likes && !locked ? likesBadgeText(likes) : nil
}

/// The badge's accessible label, `{n} open likes` / `1 open like` (SHOWUP-186 copy).
func likesBadgeLabel(_ count: Int) -> String {
    count == 1 ? "1 open like" : "\(count) open likes"
}

/// The bar: a 1px `--liq-border-soft` top rule over the .92 cream glass, padding 8 / 8 / 0, four
/// equal tabs. Each tab is a 24 icon (stroke 2.2 active, 1.7 idle) over a Manrope 600 / 10 label 3
/// below, violet when active and `--liq-fg-subtle` otherwise. The glass reaches the bottom edge,
/// under the home indicator; the tabs stay above it. `likes` is the open received likes: `0` shows
/// no badge.
struct TabBar: View {
    let active: AppTab?
    let onTab: (AppTab) -> Void
    var likes: Int = 0
    var disabled: Set<AppTab> = []
    var disabledHint: String?

    /// Bumped by each disabled tap. The hint's task is keyed on it, so a re-tap cancels the running
    /// 3200 ms and starts a new one -- a restart, never a second hint.
    @State private var hintTaps = 0
    @State private var hintShown = false

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
            HStack(spacing: 0) {
                ForEach(AppTab.allCases, id: \.self) { tab in
                    item(tab)
                }
            }
            .padding(.horizontal, Spacing.md)
            .padding(.top, Spacing.md)
        }
        .background(Color.liqTabBarGlass.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            if let disabledHint, hintShown {
                DisabledHint(text: disabledHint)
                    // 10 above the bar's top edge, taking no room: the bar never moves.
                    .alignmentGuide(.top) { d in d[.bottom] + 10 }
                    .transition(.opacity.combined(with: .offset(y: 8)))
                    .allowsHitTesting(false)
            }
        }
        .animation(.easeOut(duration: Motion.reveal), value: hintShown)
        .task(id: hintTaps) {
            guard hintTaps > 0 else { return }
            hintShown = true
            try? await Task.sleep(nanoseconds: UInt64(Motion.tabHint * 1_000_000_000))
            // A re-tap cancelled this one and started its own; only the last one hides the hint.
            if !Task.isCancelled { hintShown = false }
        }
    }

    private func item(_ tab: AppTab) -> some View {
        let selected = tab == active
        let locked = disabled.contains(tab)
        let tint = selected ? Color.liqPurple : (locked ? Color.liqFaint : Color.liqSubtle)
        let badge = tabBadgeText(tab, likes: likes, locked: locked)
        return Button(action: { if locked { hintTaps += 1 } else { onTab(tab) } }) {
            VStack(spacing: 3) {
                BrandIconView(icon: tab.icon, size: 24, stroke: selected ? 2.2 : 1.7, tint: tint)
                    .frame(width: 24, height: 24)
                    .overlay(alignment: .topLeading) {
                        if let badge { LikesBadge(text: badge).offset(x: 15, y: -4) }
                    }
                    .overlay(alignment: .bottomTrailing) {
                        // The kit's `right: -7; bottom: -3`.
                        if locked { LockBadge().offset(x: 7, y: 3) }
                    }
                // The label grows with the type up to the largest standard size and no further, as
                // the system's own tab bar does; past it a long press shows it large (below). At AX5 the
                // four labels ran into each other (the 4 Nov 2026 device audit). One still too wide
                // for its quarter shrinks rather than truncates.
                Text(tab.label)
                    .font(F.manrope(10, .semibold))
                    .tracking(0.2)
                    .foregroundColor(tint)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            }
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // The large content viewer: at the accessibility sizes a long press shows the tab's icon and
        // name large, since the label itself stops growing.
        .accessibilityShowsLargeContentViewer {
            BrandIconView(icon: tab.icon, size: 24, stroke: 2, tint: tint)
            Text(tab.label)
        }
        .accessibilityElement(children: .ignore)
        // "Likes, 2 open likes" (SHOWUP-186 accessibility): the badge is part of the tab's name.
        .accessibilityLabel(Text(badge != nil ? "\(tab.label), \(likesBadgeLabel(likes))" : tab.label))
        // A tab, selected or not -- Android's `Role.Tab` with `selected`.
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// The disabled-tab hint (the kit's `TabBar` hint): a fg pill, inset 14, at most 320 wide, with an
/// orange-400 lock. Announced, so a VoiceOver user hears why nothing opened.
private struct DisabledHint: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            BrandIconView(icon: .lock, size: 14, stroke: 2.4, tint: .liqOrange400)
                .frame(width: 14, height: 14)
                .padding(.top, 1)
            Text(text)
                .font(F.manrope(13, .medium))
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 13, multiple: 1.4))
                .tracking(0.01)
                .foregroundColor(.white)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .frame(maxWidth: 320)
        .background(Color.liqFg, in: RoundedRectangle(cornerRadius: 14))
        // `0 12px 28px rgba(20,12,28,.22)`: a SwiftUI shadow radius is the CSS blur halved.
        .shadow(color: Color(red: 20 / 255, green: 12 / 255, blue: 28 / 255).opacity(0.22), radius: 14, x: 0, y: 12)
        // The kit's `left: 14; right: 14` -- no spacing token holds 14.
        .padding(.horizontal, 14)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
        .onAppear { UIAccessibility.post(notification: .announcement, argument: text) }
    }
}

/// The kit's lock badge on a disabled tab: a 14 cream disc ringed 1.5 in fg-faint, an 8 lock in
/// fg-muted.
private struct LockBadge: View {
    var body: some View {
        BrandIconView(icon: .lock, size: 8, stroke: 3, tint: .liqMuted)
            .frame(width: 14, height: 14)
            .background(Color.liqCream, in: Circle())
            .overlay(Circle().strokeBorder(Color.liqFaint, lineWidth: 1.5))
    }
}

/// The kit's badge, `top: -4; left: 15` of the 24 icon. CONTENT-BOX: the 18 min-width and 18 height
/// are the number's box, the 0 / 5 padding and the 2px cream ring sit outside it -- so a single digit
/// is a 28 x 18 sunset pill inside a 32 x 22 ring, which is what the sheet draws (a pill, not a
/// circle). Fixed-size, so the icon stays centred over its label whatever the count.
private struct LikesBadge: View {
    let text: String

    var body: some View {
        Text(text)
            .font(F.manrope(10.5, .heavy))
            .monospacedDigit()
            .foregroundColor(.white)
            .lineLimit(1)
            .frame(minWidth: 18, minHeight: 18, maxHeight: 18)
            .padding(.horizontal, 5)
            .background(Gradients.sunset(), in: Capsule())
            .padding(2)
            .background(Color.liqCream, in: Capsule())
            .fixedSize()
    }
}

#Preview("Tab bar · Search active") {
    VStack {
        Spacer()
        TabBar(active: .search, onTab: { _ in }, likes: 2)
    }
    .background(Color.liqCream)
}
