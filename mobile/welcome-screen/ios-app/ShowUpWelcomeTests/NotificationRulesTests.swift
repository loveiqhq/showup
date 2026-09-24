//
//  NotificationRulesTests.swift
//  ShowUp · the notification ask's rules, with no device and no OS sheet (SHOWUP-162)
//
//  The Swift half of `NotificationRulesTest.kt`.
//
//  WHAT THIS CAN PROVE: which statuses skip the screen, how every `UNAuthorizationStatus` maps,
//  what fires and in what order, that a second press does nothing, that both outcomes register or
//  do not, and that the foreground re-read answers correctly.
//
//  WHAT IT CANNOT: that the platform actually raises a sheet, or that a real APNs token exists.
//  `FixedNotificationAccess` asks no OS and `PushRegistration`'s token source is a stub in this
//  build — see that file for why.
//

import UserNotifications
import XCTest
@testable import ShowUpWelcome

/// Records what was reported.
/// Same shape as `MediaRulesTests`' recorder: main-actor state reached from a nonisolated
/// protocol method, with no `@unchecked Sendable` -- an escape hatch this project bans.
@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func count(_ name: String) -> Int { events.filter { $0.0 == name }.count }
    func only(_ name: String) -> [String: any Sendable] {
        let hits = events.filter { $0.0 == name }
        XCTAssertEqual(hits.count, 1, "expected exactly one \(name)")
        return hits.first?.1 ?? [:]
    }
}

/// Counts registrations without touching a network or a token.
///
/// An actor rather than a class with a counter: `PushRegistering` is `Sendable`, and the only
/// honest way for a mutable conformer to be is isolated. `@unchecked Sendable` is banned here.
private actor FakePush: PushRegistering {
    private(set) var calls = 0
    func register() async -> PushRegistrationResult {
        calls += 1
        return .noTokenSource
    }
}

@MainActor
final class NotificationRulesTests: XCTestCase {

    private func build(
        _ status: NotificationPermission = .notDetermined,
        granted: Bool = true,
        events: Recorder,
        push: FakePush
    ) -> NotificationsModel {
        NotificationsModel(
            access: FixedNotificationAccess(status),
            ask: FixedNotificationAsk(granted: granted),
            push: push,
            analytics: events
        )
    }

    // MARK: - which statuses show the ask

    func testOnlyAStatusWithNoAnswerShowsTheAsk() {
        // The real path, every time, because profile creation runs once on a fresh install.
        XCTAssertTrue(shouldShowAsk(.notDetermined))

        // The guard: restored from a backup, or killed while the sheet was up. The OS dialog is
        // shown once per install, so the button would be dead.
        XCTAssertFalse(shouldShowAsk(.granted))
        XCTAssertFalse(shouldShowAsk(.denied))

        // Parental controls or MDM. Treated as denied everywhere in the product (§24), which here
        // means determined: there is nothing a sheet could change.
        XCTAssertFalse(shouldShowAsk(.restricted))
    }

    func testEveryAuthorizationStatusMaps() {
        XCTAssertEqual(notificationPermission(for: .notDetermined), .notDetermined)
        XCTAssertEqual(notificationPermission(for: .denied), .denied)
        XCTAssertEqual(notificationPermission(for: .authorized), .granted)

        // PROVISIONAL IS AN ANSWER. The ticket forbids REQUESTING it — it delivers quietly with no
        // banner and no sound, "exactly what a date reminder must not be" — but if it were already
        // in place the status is determined and the ask would be a dead screen.
        XCTAssertEqual(notificationPermission(for: .provisional), .granted)
        XCTAssertEqual(notificationPermission(for: .ephemeral), .granted)
    }

    // MARK: - what fires, and what must not

    func testArrivingReportsTheScreenAndOurOwnSurface() {
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()

        XCTAssertEqual(
            events.names,
            [ProfileAnalytics.screenViewedName, ProfileAnalytics.permissionPromptedName]
        )
        let viewed = events.only(ProfileAnalytics.screenViewedName)
        XCTAssertEqual(viewed["screen_id"] as? String, "profile_notifications")
        XCTAssertEqual(viewed["screen_name"] as? String, "ProfileNotifications")
        XCTAssertEqual(viewed["referrer_screen_id"] as? String, "profile_media")
        XCTAssertEqual(
            events.only(ProfileAnalytics.permissionPromptedName)["type"] as? String,
            "notifications"
        )
    }

    func testArrivingTwiceReportsOnce() {
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()
        model.arrived()
        XCTAssertEqual(events.count(ProfileAnalytics.permissionPromptedName), 1)
    }

    func testNoStepEventFiresHere() async {
        // §2's note: a step_id row with a dash index is NOT a licence to fire profile_step_viewed,
        // and this screen has no skip CTA so it fires no step event at all. A phantom step here is
        // invisible until someone reads the completion funnel.
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()
        await model.enablePressed()

        XCTAssertTrue(
            events.names.allSatisfy { !$0.hasPrefix("profile_step_") },
            "no profile_step_* may fire here: \(events.names)"
        )
    }

    func testNoRecoverySettingsOrConsentEventFiresHere() async {
        // All three belong to Stay reachable (10). `consent_changed` in particular would
        // double-count the same consent from two surfaces — the OS grant is not our consent.
        let events = Recorder()
        let model = build(granted: false, events: events, push: FakePush())
        model.arrived()
        await model.enablePressed()

        let forbidden = [
            "permission_denied_recovery_shown", "permission_settings_opened", "consent_changed",
        ]
        XCTAssertTrue(
            events.names.allSatisfy { !forbidden.contains($0) },
            "none of \(forbidden) may fire here: \(events.names)"
        )
    }

    // MARK: - the sheet

    func testGrantingReportsGrantedAndRegisters() async {
        let events = Recorder()
        let push = FakePush()
        let model = build(granted: true, events: events, push: push)

        await model.enablePressed()
        // The registration is a detached Task; give it a turn.
        for _ in 0..<16 { await Task.yield() }

        XCTAssertEqual(events.count(ProfileAnalytics.permissionOsSheetShownName), 1)
        XCTAssertEqual(
            events.only(ProfileAnalytics.permissionResultName)["result"] as? String, "granted"
        )
        // Hoisted: XCTAssertEqual takes autoclosures, and `await` cannot live inside one.
        let calls = await push.calls
        XCTAssertEqual(
            calls, 1,
            "granting and never registering is the silent failure this ticket names"
        )
    }

    func testDenyingReportsDeniedAndRegistersNothing() async {
        let events = Recorder()
        let push = FakePush()
        let model = build(granted: false, events: events, push: push)

        await model.enablePressed()
        for _ in 0..<16 { await Task.yield() }

        XCTAssertEqual(
            events.only(ProfileAnalytics.permissionResultName)["result"] as? String, "denied"
        )
        let calls = await push.calls
        XCTAssertEqual(calls, 0)
    }

    func testASecondPressDoesNothingAndReportsNothing() async {
        let events = Recorder()
        let model = build(events: events, push: FakePush())

        await model.enablePressed()
        await model.enablePressed()

        XCTAssertEqual(
            events.count(ProfileAnalytics.permissionOsSheetShownName), 1,
            "a tap that raises no sheet is not a sheet being shown"
        )
        XCTAssertEqual(events.count(ProfileAnalytics.permissionResultName), 1)
        XCTAssertTrue(model.sheetUp)
    }

    func testTheResultIsNeverLimited() async {
        // `granted | denied | limited` is the family's set and `limited` is a photo-library state
        // that cannot occur for notifications. The signature takes a Bool so there is no third
        // value to pass by mistake; this asserts the mapping.
        for granted in [true, false] {
            let events = Recorder()
            let model = build(granted: granted, events: events, push: FakePush())
            await model.enablePressed()
            let result = events.only(ProfileAnalytics.permissionResultName)["result"] as? String
            XCTAssertTrue(result == "granted" || result == "denied")
        }
    }

    // MARK: - the foreground re-read

    func testAStatusThatBecameDeterminedAdvancesTheScreen() async {
        // The user backgrounds the sheet, turns notifications on in Settings by hand, comes back.
        // The screen is never a terminal state: a dead CTA must be unreachable.
        let push = FakePush()
        for status in [NotificationPermission.granted, .denied, .restricted] {
            let model = build(status, events: Recorder(), push: push)
            let determined = await model.statusIsNowDetermined()
            XCTAssertTrue(determined, "\(status) should advance the screen")
        }
        let stays = build(.notDetermined, events: Recorder(), push: push)
        let determined = await stays.statusIsNowDetermined()
        XCTAssertFalse(determined)
    }

    func testTheReconcilersEventIsNotThisScreensToFire() async {
        // `permission_status_changed` belongs to the shared reconciler and must never double up
        // with `permission_result` for one act. The foreground re-read here is navigation only.
        let events = Recorder()
        let model = build(.granted, events: events, push: FakePush())
        model.arrived()
        _ = await model.statusIsNowDetermined()
        XCTAssertEqual(events.count(ProfileAnalytics.permissionStatusChangedName), 0)
    }
}
