//
//  CheckInTabPlaceholders.swift
//  ShowUp · where the Check-In hands over to screens that are not built yet
//
//  The Swift twin of `checkin/CheckInTabPlaceholders.kt`. **Scaffolding, not product** -- the same
//  standing as `HomePlaceholderView`. Two destinations the Check-In tickets name but do not build:
//
//    · SEARCH, DATE SEARCH UNLOCKED. Story 06: "Find my date, saved -> Search, date search
//      unlocked. No toast and no confirmation screen." Search is its own ticket. Without a
//      destination the commit would land nowhere, so this screen says where the user is and that
//      the real one is not built.
//    · SETTINGS. Story 01: on the gate, "Settings navigates normally". There is no Settings screen
//      yet; a tab that did nothing would be a dead control, so it lands here, with the tab bar, and
//      Search and Instant behave as the gate's own tabs do.
//
//  Both say they are placeholders on screen, so neither can be mistaken for the product. Deleted
//  when Search and Settings land; the host then routes to those instead.
//

import SwiftUI

/// Where a successful commit lands until Search is built.
struct SearchUnlockedPlaceholderView: View {
    var body: some View {
        PlaceholderStatement(title: "Date search unlocked",
                             line: "Your Check-in is saved. Search opens here once it is built.")
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.liqCream.ignoresSafeArea())
    }
}

/// Settings from the gate's tab bar. `onTab` gets Search and Instant -- the gate's two other tabs.
struct SettingsPlaceholderView: View {
    var onTab: (AppTab) -> Void = { _ in }

    var body: some View {
        VStack(spacing: 0) {
            PlaceholderStatement(title: "Settings",
                                 line: "Profile and Show-up Rate live here once Settings is built.")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            TabBar(active: .settings, onTab: { if $0 != .settings { onTab($0) } })
        }
        .background(Color.liqCream.ignoresSafeArea())
    }
}

/// The shared statement: a title, a line, and the words that say it is a placeholder.
private struct PlaceholderStatement: View {
    let title: String
    let line: String

    var body: some View {
        // Centred both ways, and floored at the viewport so the largest type scrolls it.
        GeometryReader { geo in
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 14) {
                    Text(title)
                        .font(F.lora(30))
                        .foregroundColor(.liqFg)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    Text(line)
                        .font(F.manrope(15, .regular))
                        .foregroundColor(.liqNeutral)
                        .multilineTextAlignment(.center)
                        .lineSpacing(TypeMetrics.lineSpacing(PS.manropeRegular, size: 15, multiple: 22.0 / 15.0))
                        .frame(maxWidth: 280)
                    Text("Placeholder screen. It goes away with the real one.")
                        .font(F.manrope(12, .regular))
                        .foregroundColor(.liqSubtle)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 280)
                        .padding(.top, Spacing.sm)
                }
                .padding(.horizontal, Spacing.screenGutter)
                .frame(maxWidth: .infinity, minHeight: geo.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

#Preview("Search unlocked (placeholder)") { SearchUnlockedPlaceholderView() }
#Preview("Settings (placeholder)") { SettingsPlaceholderView() }
