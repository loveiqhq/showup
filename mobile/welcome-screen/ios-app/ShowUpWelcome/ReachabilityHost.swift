//
//  ReachabilityHost.swift
//  ShowUpWelcome · the two things on Stay reachable that only the app can do (SHOWUP-163)
//
//  The model owns every rule on that screen and none of the platform. These are the platform: the
//  OS notification dialog, and the Settings app. Both are things the ticket puts under *What is
//  not ours* — "We decide **when** it appears; its copy, buttons and look belong to the OS".
//
//  Keeping them behind `ReachabilityHosting` is what lets `ReachabilityRulesTests` prove the save
//  order, the two dialogs and every tracking rule with no device and no permission — a fake host
//  answers the dialog however the test needs and counts the trips to Settings.
//

import UIKit

@MainActor
final class AppReachabilityHost: ReachabilityHosting {
    private let ask: any NotificationAsking

    /// `nonisolated` because this is built in a stored-property initialiser on a `View`
    /// struct, which is not a main-actor context. It sets one `Sendable` property and touches
    /// nothing isolated, so the claim is true rather than a way to silence the compiler --
    /// which `CLAUDE.md` bans and `check-swift-concurrency.py` enforces.
    nonisolated init(ask: any NotificationAsking = UNNotificationAsk()) {
        self.ask = ask
    }

    /// Raises the OS dialog and WAITS for the answer.
    ///
    /// The waiting is the point. The ticket's save order is "(1) raise the OS dialog and wait",
    /// then register, then commit, then advance — so the answer has to come back to the caller
    /// rather than arrive later in a callback that the save has already run past.
    func requestNotificationPermission() async -> Bool {
        await ask.request()
    }

    /// ALWAYS SETTINGS ON iOS, never a second dialog.
    ///
    /// The Android half of this can sometimes re-ask in-app, because Android 13+ allows a second
    /// `POST_NOTIFICATIONS` request under conditions it decides. iOS does not: `requestAuthorization`
    /// after a denial returns immediately with `false` and shows the user nothing at all, which is
    /// a button that looks broken. So the one path out of a denial here is the Settings deep link,
    /// and the label on it says so.
    func openSettingsOrReprompt() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
