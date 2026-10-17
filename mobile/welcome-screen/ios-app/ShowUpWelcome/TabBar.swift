//
//  TabBar.swift
//  ShowUp · the app's bottom navigation: Search, Instant, Settings (`components/shared.jsx` TabBar)
//
//  The Swift twin of `designsystem/TabBar.kt`. First drawn for the Check-In gate (SHOWUP-178),
//  where Search is active and "the tabs are NOT disabled": Search and Instant reopen the Check-In,
//  Settings navigates. A design-system primitive rather than a Check-In one, because every tab
//  screen after it -- Search, Match, Settings -- sits on the same bar.
//
//  NOT BUILT HERE: the kit's disabled state (lock badge, hint toast) belongs to Match's "active
//  date" lock, which no ticket has asked for yet; it lands with that ticket as a parameter of this
//  component, not as a second bar. The kit blurs what is behind the bar; over the gate's flat cream
//  the .92 glass alone is indistinguishable from it, as on Android.
//

import SwiftUI

/// The three tabs, in order. Android's `Tab`; `AppTab` here because SwiftUI has a `Tab` of its own
/// (iOS 18's TabView API), and a name that shadows the framework's reads wrong at every call site.
enum AppTab: CaseIterable, Sendable {
    case search, instant, settings

    var label: String {
        switch self {
        case .search: return "Search"
        case .instant: return "Instant"
        case .settings: return "Settings"
        }
    }

    var icon: BrandIcon {
        switch self {
        case .search: return .search
        case .instant: return .zap
        case .settings: return .settings
        }
    }
}

/// The bar: a 1px `--liq-border-soft` top rule over the .92 cream glass, padding 8 / 8 / 0, three
/// equal tabs. Each tab is a 24 icon (stroke 2.2 active, 1.7 idle) over a Manrope 600 / 10 label 3
/// below, violet when active and `--liq-fg-subtle` otherwise. The glass reaches the bottom edge,
/// under the home indicator; the tabs stay above it.
struct TabBar: View {
    let active: AppTab
    let onTab: (AppTab) -> Void

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
        return Button(action: { onTab(tab) }) {
            VStack(spacing: 3) {
                BrandIconView(icon: tab.icon, size: 24, stroke: selected ? 2.2 : 1.7, tint: tint)
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
        .accessibilityLabel(Text(tab.label))
        // A tab, selected or not -- Android's `Role.Tab` with `selected`.
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

#Preview("Tab bar · Search active") {
    VStack {
        Spacer()
        TabBar(active: .search, onTab: { _ in })
    }
    .background(Color.liqCream)
}
