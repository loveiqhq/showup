//
//  CheckInTabPlaceholders.swift
//  ShowUp · where the tabs hand over to a screen that is not built yet
//
//  The Swift twin of `checkin/CheckInTabPlaceholders.kt`. **Scaffolding, not product** -- the same
//  standing as `HomePlaceholderView`. SETTINGS. Story 01: on the gate, "Settings navigates
//  normally". There is no Settings screen yet; a tab that did nothing would be a dead control, so it
//  lands here, with the tab bar, and Search and Instant behave as the gate's own tabs do.
//
//  It says it is a placeholder on screen, so it cannot be mistaken for the product. Deleted when
//  Settings lands. (Its twin for Search went with Search 01, SHOWUP-184.)
//

import SwiftUI

/// Settings from the gate's tab bar. `onTab` gets the other three tabs; `likes` is the Likes badge.
struct SettingsPlaceholderView: View {
    var onTab: (AppTab) -> Void = { _ in }
    var likes: Int = 0

    var body: some View {
        VStack(spacing: 0) {
            PlaceholderStatement(title: "Settings",
                                 line: "Profile and Show-up Rate live here once Settings is built.")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            TabBar(active: .settings, onTab: { if $0 != .settings { onTab($0) } }, likes: likes)
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

#Preview("Settings (placeholder)") { SettingsPlaceholderView() }
