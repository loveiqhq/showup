// ModalShell.swift
// ShowUp · the kit's `ModalShell` surface: Search's like sheets and the chat's End the date sheet
//
// MOVED HERE FROM `SearchSheets.swift` with the coordinate chat (SHOWUP-194), the second flow to draw it.
// It was Search's `SearchSheet`; a second copy in the chat would have been the start of the drift the
// primary button went through. The behaviour -- scrim, swipe, rising, the keyboard inset -- is
// `SheetScaffold`'s; this is the surface the kit draws on it. Android's `ModalShell.kt`.

import SwiftUI
import UIKit

/// `ModalShell`'s `backdrop-filter: blur(8px)` -- NOT `blurBehindSheet`'s 1.5, the profile flow's
/// "softening". Borrowing that one put this sheet over a screen barely softened (the 184 evidence
/// review, 24 Oct 2026). iOS passes the CSS value as the radius, as the Check-In dialog's 3 does.
let modalShellBlur: CGFloat = 8

/// The id at the very end of a sheet's surface, for `endOnKeyboard`. File scope because a generic
/// type cannot hold a static stored property; a `let` String, so nothing for concurrency to question.
private let modalShellEnd = "modal.shell.end"

/// The kit's `ModalShell` surface inside the app's one sheet behaviour.
struct ModalShell<Content: View>: View {
    let label: String
    let onDismiss: (SheetDismissMethod) -> Void
    /// When the keyboard comes up, scroll to the sheet's end -- Android's `endOnKeyboard`. On a short
    /// phone the message sheet outgrows the space above the keys, and its two buttons would sit
    /// behind them until the user thought to scroll; at the end, the field, the counter and both
    /// buttons show together.
    var endOnKeyboard = false
    /// The content's padding: 24 all round for Search's like sheets, `20 24 24` for its sorry sheet.
    var contentPadding = EdgeInsets(top: 24, leading: 24, bottom: 24, trailing: 24)
    @ViewBuilder let content: () -> Content

    var body: some View {
        SheetScaffold(onDismiss: onDismiss) {
            GeometryReader { g in
                // `max-height: 88%` of the frame the sheet is docked in; past it the sheet scrolls.
                let cap = g.size.height * 0.88
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    ViewThatFits(in: .vertical) {
                        surface
                        ScrollViewReader { proxy in
                            ScrollView { surface }
                                .scrollBounceBehavior(.basedOnSize)
                                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
                                    if endOnKeyboard { proxy.scrollTo(modalShellEnd, anchor: .bottom) }
                                }
                        }
                    }
                    .frame(maxHeight: cap)
                    .background(
                        UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28)
                            .fill(Color.liqCream)
                            // `0 -8px 32px rgba(46,1,71,.18)`, cast upward over the screen.
                            .shadow(color: Color.liqShadowInk.opacity(0.18), radius: 16, y: -8)
                            // Docked to the edge: its colour runs down into the home indicator's inset.
                            .ignoresSafeArea(edges: .bottom)
                    )
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityLabel(Text(label))
        }
    }

    private var surface: some View {
        VStack(spacing: 0) {
            Capsule().fill(Color.liqFaint).frame(width: 36, height: 4)
                .padding(.top, 8)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) { content() }
                .padding(contentPadding)
            Color.clear.frame(height: 0).id(modalShellEnd)
        }
        .frame(maxWidth: .infinity)
    }
}
