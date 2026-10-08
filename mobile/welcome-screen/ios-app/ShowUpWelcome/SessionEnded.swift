//  SessionEnded.swift
//  ShowUp · what happens when the server ends the session
//
//  The auth layer (`ShowUpAPI.TokenRefresher`) clears the tokens the moment the server refuses to
//  renew them. This file is the app's half: the news, the notice that says why the user is back at
//  the start, and the scene keys that make "back at the start" true.

import SwiftUI

/// What the user reads when the server has ended their session.
///
/// PROPOSED COPY, pending Philipp. "Log in", not "sign in", because "Log in" is the link on the
/// Startup screen this appears over — the sentence names the thing to tap. Android carries the same
/// string as `SESSION_ENDED_MESSAGE` in `SessionEndedNotice.kt`.
let sessionEndedMessage = "Your session ended. Please log in again."

/// The one piece of news that has to outlive the request that learned it.
///
/// It arrives off the main actor, from inside whichever request needed a refresh — a save on the
/// height step, the resume check at launch, an upload. None of those is the right place to decide
/// what happens next, so the request only raises this, and the scene root acts on it.
///
/// A FLAG, NOT A COUNT. Ten requests failing together refuse the same session, and the user should
/// be told once. `consume` is what makes the scene act once.
@MainActor
@Observable
final class SessionEndedSignal {
    static let shared = SessionEndedSignal()

    /// True from the moment the server refuses the session until the scene root consumes it.
    private(set) var pending = false

    func raise() {
        pending = true
    }

    /// Takes the news, returning whether there was any.
    func consume() -> Bool {
        guard pending else { return false }
        pending = false
        return true
    }
}

/// Scene-storage keys, scoped to the signed-in session.
///
/// WHY EVERY KEY CARRIES THE EPOCH
///
/// `@SceneStorage` outlives the views that declare it, and a view that is not on screen cannot be
/// asked to forget. The sign-up flow's own keys still hold the last step it showed long after the
/// user finished it, so resetting only the keys one view can reach would bring a signed-out user
/// back to a half-finished sign-up. Android gets a clean slate for free — the app restarts in a
/// cleared task, and saved state goes with it.
///
/// So every key is built from the session epoch: when the server ends the session the epoch moves
/// on, every key names a slot nothing has written yet, and every value is its default. Epoch 0 keeps
/// the bare name, so nothing a device already stored is orphaned by this change. `audit/
/// check-swift-structure.py` refuses a `@SceneStorage` with a literal key, apart from the epoch's own.
enum SceneKey {
    static func scoped(_ name: String, epoch: Int) -> String {
        epoch == 0 ? name : "\(name)#\(epoch)"
    }
}

/// The "please log in again" notice: a dark chip drawn ABOVE the Startup screen's legal line,
/// taking no space in it.
///
/// WHERE, AND WHY THERE. It first hung from the top of the screen, and on a device it sat squarely
/// on the wordmark. Above the footer is where every toast in this app already appears (the refusal
/// toast on photos, prompts and the details group), it is the empty middle of Startup at every
/// size, and it is right over "Create free account" and "Log in" — the sentence sits next to the
/// thing it tells you to do.
///
/// The same chip as `RefusalToast` — one family of messages, one look — placed the same way, as an
/// overlay aligned to its anchor's top edge (`sessionEndedNotice(visible:onDismiss:)`), so Startup
/// measures identically with and without it and nothing moves when it appears.
///
/// It goes after `Motion.notice`, or the moment Startup leaves the screen. Not tappable, matching
/// Android, where a chip drawn outside its anchor's bounds cannot receive a tap at all.
struct SessionEndedNotice: View {
    let visible: Bool
    let onDismiss: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Text(sessionEndedMessage)
            .font(F.manrope(13, .semibold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 14)
            .padding(.vertical, Spacing.lg)
            .frame(maxWidth: 300)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqFg))
            // RefusalToast's shadow, `0 8px 22px`.
            .shadow(color: .liqToastShadow, radius: 11, y: 8)
            .opacity(visible ? 1 : 0)
            // 6 up on the way in, the refusal toast's `translateY(6px)` resting state.
            .offset(y: visible ? 0 : 6)
            .animation(reduceMotion ? nil : .easeOut(duration: Motion.fast), value: visible)
            .allowsHitTesting(false)
            // Announced when it arrives — VoiceOver users are the ones who would otherwise never
            // know why they are back at the start — and absent from the tree when it is gone.
            .accessibilityHidden(!visible)
            .onChange(of: visible, initial: true) { _, up in
                if up { AccessibilityNotification.Announcement(sessionEndedMessage).post() }
            }
            // `task(id:)` restarts when `visible` changes, which is what cancels the clock once the
            // notice has gone some other way.
            .task(id: visible) {
                guard visible else { return }
                try? await Task.sleep(nanoseconds: UInt64(Motion.notice * 1_000_000_000))
                guard !Task.isCancelled else { return }
                onDismiss()
            }
            // Leaving Startup — to the phone step, or anywhere — ends it, so it cannot come back
            // with a fresh clock when the user returns.
            .onDisappear { onDismiss() }
    }
}

extension View {
    /// Places the session notice immediately above the view it is attached to, taking no space in
    /// it — the same construction as `refusalToast(_:)`, lifted by the same `Spacing.lg`.
    func sessionEndedNotice(visible: Bool, onDismiss: @escaping () -> Void) -> some View {
        overlay(alignment: .top) {
            SessionEndedNotice(visible: visible, onDismiss: onDismiss)
                .alignmentGuide(.top) { $0[.bottom] + Spacing.lg }
        }
    }
}
