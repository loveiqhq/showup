//
//  NotificationAccess.swift
//  ShowUpWelcome · the notification permission status, and the case that skips the ask (SHOWUP-162)
//
//  ───────────────────────────────────────────────────────────────────────────
//  FOUR VALUES, ONE VOCABULARY, BOTH PLATFORMS
//  ───────────────────────────────────────────────────────────────────────────
//
//  `enums.json` §24 defines the set and the ticket uses it verbatim: `not_determined · granted ·
//  denied · restricted`. Not spelled per-platform — iOS's `authorizationStatus` and Android's
//  rationale flag both map onto these four — and `limited` is deliberately absent, because it is a
//  photo-library concept that lives on `permission_result` alone.
//
//  `denied` COVERS PERMANENT DENIAL, and on iOS that is the only kind: the system dialog is shown
//  once per install, so a denial here can never be re-asked. That is exactly why a denial is
//  recovered on Stay reachable (10), which deep-links to Settings, and not on this screen.
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHAT iOS RETURNS THAT ANDROID DOES NOT
//  ───────────────────────────────────────────────────────────────────────────
//
//  `.provisional` and `.ephemeral` both exist and neither is a status this product wants.
//
//  PROVISIONAL IS MAPPED TO `granted` AND NEVER REQUESTED. The ticket forbids asking for it — it
//  delivers quietly to Notification Centre with no banner and no sound, "which is exactly what a
//  date reminder must not be". But if the app were ever launched with it already in place, the
//  status IS determined and the ask would be a dead screen, so it skips like any other answer.
//
//  `.ephemeral` is an App Clip state this app cannot be in; it maps the same way for safety
//  rather than being left to a `default` that could silently become `not_determined`.
//

import Foundation
import UserNotifications

/// The OS's answer, in `enums.json` §24's vocabulary.
enum NotificationPermission: String, Sendable {
    case notDetermined = "not_determined"
    case granted
    case denied
    case restricted

    /// What goes on the wire. The names are never typed at a call site.
    var trackingValue: String { rawValue }

    /// Whether the OS has an answer. The ask exists only for the one status that does not.
    var isDetermined: Bool { self != .notDetermined }
}

/// Should the ask be shown at all?
///
/// The single decision behind the skip case, as a free function so the host can take it before the
/// screen is pushed and a test can take it with no device.
func shouldShowAsk(_ status: NotificationPermission) -> Bool { !status.isDetermined }

/// Maps `UNAuthorizationStatus` onto §24. A pure function, so every branch is testable.
func notificationPermission(for status: UNAuthorizationStatus) -> NotificationPermission {
    switch status {
    case .notDetermined: return .notDetermined
    case .denied: return .denied
    // See the header: neither is requested, and both are answers.
    case .authorized, .provisional, .ephemeral: return .granted
    @unknown default:
        // A status this build has never heard of is an ANSWER, not an absence. Treating it as
        // `not_determined` would show an ask whose sheet may never appear, and this screen has no
        // way out but that sheet.
        return .granted
    }
}

/// Reads the status. Re-read on every foreground — never remembered.
///
/// §24: a permission "is the device's answer and is re-read on every foreground rather than
/// remembered". A cached value is wrong the moment the user changes it in Settings.
protocol NotificationAccessReading: Sendable {
    func read() async -> NotificationPermission
}

/// The real reader.
struct UNNotificationAccess: NotificationAccessReading {
    func read() async -> NotificationPermission {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return notificationPermission(for: settings.authorizationStatus)
    }
}

/// Answers from memory. Previews, and every test that is about the rules rather than the device.
struct FixedNotificationAccess: NotificationAccessReading {
    let status: NotificationPermission

    init(_ status: NotificationPermission = .notDetermined) { self.status = status }

    func read() async -> NotificationPermission { status }
}

/// Raises the OS sheet.
///
/// SEPARATE FROM THE READER, because the two are different acts with different rules: the status
/// is read on every foreground and the sheet is raised exactly once, on a tap.
protocol NotificationAsking: Sendable {
    /// Requests authorisation. Returns whether it was granted.
    func request() async -> Bool
}

struct UNNotificationAsk: NotificationAsking {
    func request() async -> Bool {
        // Alert, sound and badge — the three a date reminder needs. NOT `.provisional`: see the
        // file header and the ticket, which rules it out in as many words.
        let granted = try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])
        return granted ?? false
    }
}

/// Answers from memory, for tests.
struct FixedNotificationAsk: NotificationAsking {
    let granted: Bool
    func request() async -> Bool { granted }
}
