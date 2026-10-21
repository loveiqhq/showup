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
//  NOT BUILT HERE: the kit's disabled state (lock badge, hint toast) belongs to Match's "active
//  date" lock, which no ticket has asked for yet; it lands with that ticket as a parameter of this
//  component, not as a second bar. The kit blurs what is behind the bar; over the gate's flat cream
//  the .92 glass alone is indistinguishable from it, as on Android.
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
    let active: AppTab
    let onTab: (AppTab) -> Void
    var likes: Int = 0

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
    }

    private func item(_ tab: AppTab) -> some View {
        let selected = tab == active
        let tint = selected ? Color.liqPurple : Color.liqSubtle
        let badge = tab == .likes ? likesBadgeText(likes) : nil
        return Button(action: { onTab(tab) }) {
            VStack(spacing: 3) {
                BrandIconView(icon: tab.icon, size: 24, stroke: selected ? 2.2 : 1.7, tint: tint)
                    .frame(width: 24, height: 24)
                    .overlay(alignment: .topLeading) {
                        if let badge { LikesBadge(text: badge).offset(x: 15, y: -4) }
                    }
                Text(tab.label)
                    .font(F.manrope(10, .semibold))
                    .tracking(0.2)
                    .foregroundColor(tint)
                    .lineLimit(1)
            }
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        // "Likes, 2 open likes" (SHOWUP-186 accessibility): the badge is part of the tab's name.
        .accessibilityLabel(Text(badge != nil ? "\(tab.label), \(likesBadgeLabel(likes))" : tab.label))
        // A tab, selected or not -- Android's `Role.Tab` with `selected`.
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
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
