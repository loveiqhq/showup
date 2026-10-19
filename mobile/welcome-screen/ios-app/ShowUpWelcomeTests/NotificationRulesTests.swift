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
        _ status: PermissionStatus = .notDetermined,
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

    func testArrivingReportsTheScreenAndNothingElse() {
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()

        // `permission_prompted` USED TO FIRE HERE and no longer does. Registry 1.4.6 moved it:
        // "From 25 Sep 2026 the notifications case is Stay reachable (Profile 10) ... Profile 09
        // NO LONGER fires it — 09 raises no sheet any more." A pre-permission event on a screen
        // that pre-permissions nothing would double-count the ask against Stay reachable's own.
        XCTAssertEqual(events.names, [ProfileAnalytics.screenViewedName])
        let viewed = events.only(ProfileAnalytics.screenViewedName)
        XCTAssertEqual(viewed["screen_id"] as? String, "profile_notifications")
        XCTAssertEqual(viewed["screen_name"] as? String, "ProfileNotifications")
        XCTAssertEqual(viewed["referrer_screen_id"] as? String, "profile_media")
    }

    func testArrivingTwiceReportsOnce() {
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()
        model.arrived()
        // The guard is on the screenview now that it is the only thing arrival reports.
        XCTAssertEqual(events.count(ProfileAnalytics.screenViewedName), 1)
    }

    func testNoStepEventFiresHere() {
        // §2's note: a step_id row with a dash index is NOT a licence to fire profile_step_viewed,
        // and this screen has no skip CTA so it fires no step event at all. A phantom step here is
        // invisible until someone reads the completion funnel.
        let events = Recorder()
        let model = build(events: events, push: FakePush())
        model.arrived()
        model.continuePressed()

        XCTAssertTrue(
            events.names.allSatisfy { !$0.hasPrefix("profile_step_") },
            "no profile_step_* may fire here: \(events.names)"
        )
    }

    // MARK: - the dialog this screen no longer raises (SHOWUP-163)

    /// THE WHOLE OF WHAT 163 TOOK AWAY, asserted as an absence.
    ///
    /// This screen owned the OS notification dialog until 25 September 2026: `enablePressed`
    /// reported `permission_os_sheet_shown` and launched the request, and the answer reported
    /// `permission_result`. All three events and the request moved to Stay reachable, where the
    /// dialog is raised by `Save preferences`.
    ///
    /// Six tests were deleted with them — they were testing a thing that is now somebody else's,
    /// and `ReachabilityRulesTests` is where they live in spirit. What is left is this one, which
    /// would fail the moment any of it came back.
    func testNoPermissionEventFiresOnThisScreenAnyMore() {
        let events = Recorder()
        let model = build(granted: false, events: events, push: FakePush())
        model.arrived()
        model.continuePressed()
        model.continuePressed()

        let moved = [
            ProfileAnalytics.permissionPromptedName,
            ProfileAnalytics.permissionOsSheetShownName,
            ProfileAnalytics.permissionResultName,
        ]
        for name in moved {
            XCTAssertEqual(
                events.count(name), 0,
                "\(name) moved to Stay reachable and must not fire here"
            )
        }
        // The screenview stays: this is still a screen somebody looked at.
        XCTAssertEqual(events.count(ProfileAnalytics.screenViewedName), 1)
    }

    func testNoRecoverySettingsOrConsentEventFiresHere() {
        // All three belong to Stay reachable (10). `consent_changed` in particular would
        // double-count the same consent from two surfaces — the OS grant is not our consent.
        let events = Recorder()
        let model = build(granted: false, events: events, push: FakePush())
        model.arrived()
        model.continuePressed()

        let forbidden = [
            "permission_denied_recovery_shown", "permission_settings_opened", "consent_changed",
        ]
        XCTAssertTrue(
            events.names.allSatisfy { !forbidden.contains($0) },
            "none of \(forbidden) may fire here: \(events.names)"
        )
    }

    func testContinueNavigatesOnceAndRefusesASecondPress() {
        let events = Recorder()
        let model = build(events: events, push: FakePush())

        XCTAssertTrue(model.continuePressed(), "the first press navigates")
        XCTAssertFalse(model.continuePressed(), "a double tap must not produce two navigations")
        // And nothing is reported for either — the press is not an event on this screen.
        XCTAssertEqual(events.names, [])
    }

    func testTheCTAReadsContinue() {
        // 163 renames it, because the button no longer enables anything — it goes to the screen
        // that does. A label describing the screen after it is the kind of thing a copy pass
        // quietly reverts.
        XCTAssertEqual(NotificationsCopy.cta, "Continue")
    }

    func testNothingRegistersForPushOnThisScreenAnyMore() async {
        // The registration moved with the grant it depended on, and no path from this screen
        // reaches it now -- see the block below for what went and why.
        let push = FakePush()
        let model = build(granted: true, events: Recorder(), push: push)
        model.arrived()
        model.continuePressed()
        for _ in 0..<16 { await Task.yield() }

        let calls = await push.calls
        XCTAssertEqual(calls, 0, "a press here reaches no platform API at all")
    }

    // MARK: - the user who never sees the screen

    /**
     * FOUR TESTS USED TO LIVE HERE, and they went with the function they tested.
     *
     * `skipped(_:)` registered for push when a user was routed PAST this screen on an
     * already-determined status. It existed because `register()` had exactly one call site --
     * the grant callback on this screen -- so a user who never saw it was a device the backend
     * had no token for.
     *
     * SHOWUP-163 routes every user through Stay reachable (10), whose `Save preferences` is the
     * only way off it and which registers when push is on. The need is met one screen later and
     * tied to the consent rather than to a screen the user did not see.
     *
     * Keeping both would have registered twice on every Android <= 12 device and every restored
     * install -- and worse on the path that matters: the old one registered on a GRANTED status
     * REGARDLESS OF CONSENT, so a user who reached 10 and switched push off would already have
     * had a token filed. Registering a device for push right after the user declines it is the
     * failure that version had, and a second `register()` succeeding is what made it invisible.
     *
     * What is left is the absence, asserted two ways: nothing on this screen registers, and the
     * screen that does is the one the user always reaches. The second half is
     * `ReachabilityRulesTests.testSavingWithPushOnAndNothingAnsweredRaisesTheDialogThenAdvances`.
     */
    func testNothingOnThisScreenRegistersForPush() async {
        let push = FakePush()
        for status in [PermissionStatus.granted, .denied, .restricted, .notDetermined] {
            let model = build(status, events: Recorder(), push: push)
            model.arrived()
            model.continuePressed()
            model.continuePressed()
            _ = await model.statusIsNowDetermined()
        }
        for _ in 0..<16 { await Task.yield() }

        let calls = await push.calls
        XCTAssertEqual(
            calls, 0,
            "registration moved to Stay reachable with the consent it belongs to"
        )
    }

    // MARK: - the foreground re-read

    func testAStatusThatBecameDeterminedAdvancesTheScreen() async {
        // The user backgrounds the sheet, turns notifications on in Settings by hand, comes back.
        // The screen is never a terminal state: a dead CTA must be unreachable.
        let push = FakePush()
        for status in [PermissionStatus.granted, .denied, .restricted] {
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
