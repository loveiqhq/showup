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

private struct SheetScrollsToEndKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// EVIDENCE ONLY, false in the app: a sheet taller than its frame scrolls to the END of its content when it appears,
    /// so a still picture of one (the Date 06 and Date 07 evidence at 375 x 667) shows what tells the frame apart, the
    /// buttons and boxes below the fold. Only the sample frames and the evidence harness set it; no host passes it
    /// down. Android's `LocalSheetScroll`.
    var sheetScrollsToEnd: Bool {
        get { self[SheetScrollsToEndKey.self] }
        set { self[SheetScrollsToEndKey.self] = newValue }
    }
}

/// The kit's `ModalShell` surface inside the app's one sheet behaviour: docked to the bottom, as tall as its
/// content, at most 88 % of the frame and scrolling past it -- Android's `heightIn(max = cap)` + `verticalScroll`.
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

    @Environment(\.sheetScrollsToEnd) private var scrollsToEnd

    var body: some View {
        SheetScaffold(onDismiss: onDismiss) {
            GeometryReader { g in
                // `max-height: 88%` of the frame the sheet is docked in; past it the sheet scrolls.
                let cap = g.size.height * 0.88
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    // HEIGHT FROM CONTENT (the kit's sheet is as tall as what it holds). Offered at most the cap,
                    // `ViewThatFits` keeps the whole surface when its own height fits and the scrolling copy, cap
                    // tall, when it does not -- and is as tall as the one it keeps. So the cream is drawn on it,
                    // BEFORE the cap's frame: a frame given only a `maxHeight` takes the height it is offered, up
                    // to that max, even when its child is shorter, and centres the child in it. Drawn on that
                    // frame, every sheet was cap tall with its content floating in the middle (CI, 14 Nov 2026).
                    ViewThatFits(in: .vertical) {
                        // Its own height whatever it is offered: a view inside that grows to fill (the message
                        // sheet's editor) would otherwise stretch the sheet to the cap again.
                        surface.fixedSize(horizontal: false, vertical: true)
                        ScrollViewReader { proxy in
                            ScrollView { surface }
                                .scrollBounceBehavior(.basedOnSize)
                                .onAppear {
                                    guard scrollsToEnd else { return }
                                    proxy.scrollTo(modalShellEnd, anchor: .bottom)
                                    // Again once laid out: the first call can run before the content has its height.
                                    Task { @MainActor in
                                        try? await Task.sleep(nanoseconds: 120_000_000)
                                        proxy.scrollTo(modalShellEnd, anchor: .bottom)
                                    }
                                }
                                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)) { _ in
                                    if endOnKeyboard { proxy.scrollTo(modalShellEnd, anchor: .bottom) }
                                }
                        }
                    }
                    .background(
                        UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28)
                            .fill(Color.liqCream)
                            // `0 -8px 32px rgba(46,1,71,.18)`, cast upward over the screen.
                            .shadow(color: Color.liqShadowInk.opacity(0.18), radius: 16, y: -8)
                            // Docked to the edge: its colour runs down into the home indicator's inset.
                            .ignoresSafeArea(edges: .bottom)
                    )
                    .fitAnchor(FitAnchorID.modalShell)
                    // Only the most the sheet may take; the sheet rests on its foot inside it, and the space
                    // above draws nothing and answers nothing (a tap there reaches the scrim).
                    .frame(maxHeight: cap, alignment: .bottom)
                }
            }
            .fitAnchor(FitAnchorID.modalShellDock)
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
