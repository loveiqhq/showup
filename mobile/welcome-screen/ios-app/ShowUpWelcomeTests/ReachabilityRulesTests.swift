//
//  ReachabilityRulesTests.swift
//  ShowUp · every rule Stay reachable has, with no device and no OS dialog (SHOWUP-163)
//
//  The Swift half of `ReachabilityRulesTest.kt`, assertion for assertion.
//
//  WHAT THIS CAN PROVE: the save order, that the dialog is raised only when it should be, that a
//  user-initiated toggle-off and an OS denial produce the same dialog with different primaries and
//  different consequences, that the toggle does not move until deactivation is confirmed, that an
//  interest box records and does nothing else, that nothing advances until the server confirms,
//  and that the events the ticket forbids never fire.
//
//  WHAT IT CANNOT: that the platform actually raises a dialog, that a token reaches APNs, or that
//  Settings opens. `FakeHost` asks no OS and `NoConsentBackend` has no endpoint behind it.
//

import XCTest
@testable import ShowUpWelcome

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
    func clear() { events.removeAll() }
}

private actor FakePush: PushRegistering {
    private(set) var calls = 0
    func register() async -> PushRegistrationResult {
        calls += 1
        return .noTokenSource
    }
}

/// A consent write that can be made to fail, which is the only way to test "not optimistic".
private actor FakeConsent: ReachabilityRepository {
    private(set) var calls = 0
    private(set) var lastValue: Bool?
    private var succeeds: Bool

    init(succeeds: Bool = true) { self.succeeds = succeeds }
    func setSucceeds(_ value: Bool) { succeeds = value }

    func savePushConsent(on: Bool) async -> Bool {
        calls += 1
        lastValue = on
        return succeeds
    }
}

@MainActor
private final class FakeHost: ReachabilityHosting {
    var grants = true
    var dialogs = 0
    var settingsOpened = 0

    /// Holds the dialog open until the test lets it answer.
    ///
    /// THE ONLY WAY TO HAVE TWO TAPS IN FLIGHT AT ONCE. `savePressed` returns when the save is
    /// finished, so a test that awaits it twice is testing two SEQUENTIAL presses, which the
    /// guard is not about and which should genuinely save twice. The double tap the ticket means
    /// -- "input is locked from the tap until navigation or failure" -- only exists while the
    /// first save is suspended, and the OS dialog is where it suspends.
    private var gate: CheckedContinuation<Bool, Never>?
    var gated = false

    func requestNotificationPermission() async -> Bool {
        dialogs += 1
        guard gated else { return grants }
        return await withCheckedContinuation { continuation in
            gate = continuation
        }
    }

    /// Answers the held dialog. Safe to call when nothing is waiting.
    func release() {
        let waiting = gate
        gate = nil
        waiting?.resume(returning: grants)
    }

    func openSettingsOrReprompt() { settingsOpened += 1 }
}

/// A status that MOVES, which `FixedNotificationAccess` cannot express.
///
/// Answering the OS dialog changes what the next `read()` returns, and two rules on this screen
/// depend on that rather than on the answer we were handed: the toggle a scrim-dismiss leaves
/// behind, and the permission `reachability_saved` carries.
private actor MovingAccess: NotificationAccessReading {
    private var status: NotificationPermission
    init(_ status: NotificationPermission = .notDetermined) { self.status = status }
    func set(_ value: NotificationPermission) { status = value }
    func read() async -> NotificationPermission { status }
}

@MainActor
final class ReachabilityRulesTests: XCTestCase {

    private var analytics = Recorder()
    private var push = FakePush()
    private var consent = FakeConsent()
    private var host = FakeHost()

    override func setUp() {
        super.setUp()
        analytics = Recorder()
        push = FakePush()
        consent = FakeConsent()
        host = FakeHost()
    }

    private func build(
        _ status: NotificationPermission = .notDetermined
    ) -> ReachabilityModel {
        let model = ReachabilityModel(
            access: FixedNotificationAccess(status),
            push: push,
            consent: consent,
            analytics: analytics
        )
        model.attach(host)
        return model
    }

    private func build(moving access: MovingAccess) -> ReachabilityModel {
        let model = ReachabilityModel(
            access: access, push: push, consent: consent, analytics: analytics
        )
        model.attach(host)
        return model
    }

    // MARK: - the defaults

    func testPushIsOnByDefaultAndNothingIsChecked() async {
        let model = build()
        await model.arrived()

        // AN OPT-OUT, NOT AN OPT-IN. The ticket decided this on 25 September: "the user switches
        // it off actively, through the confirm dialog".
        XCTAssertTrue(model.state.pushOn, "push defaults on")
        XCTAssertEqual(model.state.interest, [], "the demand test starts empty")
        XCTAssertNil(model.state.prompt)
    }

    func testADeniedStatusShowsTheToggleOff() async {
        // An opt-out cannot promise what the OS refused.
        let model = build(.denied)
        await model.arrived()
        XCTAssertFalse(model.state.pushOn)
    }

    // MARK: - what fires on arrival

    func testArrivingReportsTheScreenAndThePrePermissionSurface() async {
        let model = build(.notDetermined)
        await model.arrived()

        XCTAssertEqual(
            analytics.names,
            [ProfileAnalytics.screenViewedName, ProfileAnalytics.permissionPromptedName]
        )
        let viewed = analytics.only(ProfileAnalytics.screenViewedName)
        XCTAssertEqual(viewed["screen_id"] as? String, "profile_reachability")
        XCTAssertEqual(viewed["screen_name"] as? String, "ProfileReachability")
        XCTAssertEqual(viewed["referrer_screen_id"] as? String, "profile_notifications")
    }

    func testAnAlreadyAnsweredStatusIsNotAPrePermissionSurface() async {
        // The ticket qualifies it: `permission_prompted` fires "only when the status is not
        // determined". Somebody whose answer is already on file is not being pre-permissioned.
        let model = build(.granted)
        await model.arrived()
        XCTAssertEqual(analytics.count(ProfileAnalytics.permissionPromptedName), 0)
        XCTAssertEqual(analytics.count(ProfileAnalytics.screenViewedName), 1)
    }

    func testNoStepEventFiresHereInAnyState() async {
        let model = build()
        await model.arrived()
        await model.pushChanged(false)
        await model.confirmDeactivation()
        model.interestToggled(.sms)

        XCTAssertTrue(
            analytics.names.allSatisfy { !$0.hasPrefix("profile_step_") },
            "this screen is not a step and has no skip: \(analytics.names)"
        )
    }

    // MARK: - the demand test

    func testCheckingABoxReportsTheInterestAndDoesNothingElse() async {
        let model = build()
        await model.arrived()
        analytics.clear()

        model.interestToggled(.aiCall)

        // ONE EVENT, and it is not a consent. No dialog, no navigation, no network.
        XCTAssertEqual(analytics.names, [ProfileAnalytics.channelInterestChangedName])
        let e = analytics.only(ProfileAnalytics.channelInterestChangedName)
        XCTAssertEqual(e["channel"] as? String, "ai_call")
        XCTAssertEqual(e["on"] as? Bool, true)
        XCTAssertEqual(e["screen_id"] as? String, "profile_reachability")
        XCTAssertTrue(model.state.isInterested(.aiCall))
        XCTAssertNil(model.state.prompt, "no dialog")
        let calls = await consent.calls
        XCTAssertEqual(calls, 0, "no consent write")
    }

    func testAnInterestIsNeverAConsent() async {
        // THE TICKET'S HARDEST TRACKING RULE: "no consent_changed for a checkbox. Ever."
        let model = build()
        await model.arrived()
        analytics.clear()

        for channel in InterestChannel.order { model.interestToggled(channel) }
        for channel in InterestChannel.order { model.interestToggled(channel) }

        XCTAssertEqual(analytics.count(ProfileAnalytics.consentChangedName), 0)
        XCTAssertEqual(analytics.count(ProfileAnalytics.channelInterestChangedName), 6)
    }

    func testEveryFlipIsReported() async {
        // "channel_interest_changed adds every flip, so a check followed by an uncheck is not
        // counted as interest."
        let model = build()
        await model.arrived()
        analytics.clear()

        model.interestToggled(.whatsApp)
        model.interestToggled(.whatsApp)

        let flips = analytics.events
            .filter { $0.0 == ProfileAnalytics.channelInterestChangedName }
            .map { $0.1["on"] as? Bool }
        XCTAssertEqual(flips, [true, false])
        XCTAssertFalse(model.state.isInterested(.whatsApp))
    }

    // MARK: - the push toggle

    func testSwitchingOffOpensTheConfirmAndDoesNotMoveTheToggle() async {
        let model = build()
        await model.arrived()
        analytics.clear()

        await model.pushChanged(false)

        XCTAssertEqual(model.state.prompt, .userTurnedItOff)
        XCTAssertTrue(model.state.pushOn, "the toggle stays on until Confirm deactivation")
        XCTAssertEqual(analytics.names, [ProfileAnalytics.consentDeactivationConfirmShownName])
        XCTAssertEqual(
            analytics.only(ProfileAnalytics.consentDeactivationConfirmShownName)["channel"]
                as? String,
            "push"
        )
    }

    func testKeepActiveLeavesItOnAndReportsAnAbandonment() async {
        let model = build()
        await model.arrived()
        await model.pushChanged(false)
        analytics.clear()

        await model.keepActive()

        XCTAssertTrue(model.state.pushOn)
        XCTAssertNil(model.state.prompt)
        XCTAssertEqual(analytics.names, [ProfileAnalytics.consentDeactivationAbandonedName])
    }

    func testConfirmingTurnsItOffAndRecordsTheConsentChange() async {
        let model = build()
        await model.arrived()
        await model.pushChanged(false)
        analytics.clear()

        await model.confirmDeactivation()

        XCTAssertFalse(model.state.pushOn)
        XCTAssertNil(model.state.prompt)
        XCTAssertEqual(
            analytics.names,
            [
                ProfileAnalytics.consentDeactivationConfirmedName,
                ProfileAnalytics.consentChangedName,
            ]
        )
        let consentEvent = analytics.only(ProfileAnalytics.consentChangedName)
        XCTAssertEqual(consentEvent["channel"] as? String, "push")
        XCTAssertEqual(consentEvent["on"] as? Bool, false)
        XCTAssertEqual(consentEvent["surface"] as? String, "profile_creation")
    }

    func testSwitchingBackOnRaisesNoDialogAndIsImmediate() async {
        let model = build()
        await model.arrived()
        await model.pushChanged(false)
        await model.confirmDeactivation()
        analytics.clear()

        await model.pushChanged(true)

        XCTAssertTrue(model.state.pushOn)
        XCTAssertNil(model.state.prompt, "no dialog appears when push is switched back on")
        XCTAssertEqual(analytics.names, [ProfileAnalytics.consentChangedName])
        XCTAssertEqual(host.dialogs, 0)
    }

    func testSwitchingOnAgainstARefusalOpensSettings() async {
        // A DEAD TOGGLE IS WORSE THAN NO TOGGLE. iOS has no re-prompt: a second
        // `requestAuthorization` after a denial returns false and shows nothing at all.
        let model = build(.denied)
        await model.arrived()
        analytics.clear()

        await model.pushChanged(true)

        XCTAssertFalse(model.state.pushOn, "nothing is promised that the OS has refused")
        XCTAssertEqual(host.settingsOpened, 1)
        XCTAssertEqual(analytics.names, [ProfileAnalytics.permissionSettingsOpenedName])
        XCTAssertEqual(analytics.count(ProfileAnalytics.consentChangedName), 0)
    }

    // MARK: - save

    func testSavingWithPushOnAndNothingAnsweredRaisesTheDialogThenAdvances() async {
        let access = MovingAccess(.notDetermined)
        let model = build(moving: access)
        await model.arrived()
        model.interestToggled(.aiCall)
        analytics.clear()

        var advanced = false
        await access.set(.notDetermined)
        host.grants = true
        await model.savePressed { advanced = true }
        // The registration is awaited inside the save, so nothing to yield for.

        XCTAssertEqual(host.dialogs, 1)
        XCTAssertTrue(advanced)

        // THE ORDER IS THE RULE: sheet, result, then the save event -- never the other way round.
        XCTAssertEqual(
            analytics.names,
            [
                ProfileAnalytics.permissionOsSheetShownName,
                ProfileAnalytics.permissionResultName,
                ProfileAnalytics.reachabilitySavedName,
            ]
        )
        let calls = await push.calls
        XCTAssertEqual(calls, 1, "a grant with no token looks exactly like success")

        let saved = analytics.only(ProfileAnalytics.reachabilitySavedName)
        XCTAssertEqual(saved["push_on"] as? Bool, true)
        XCTAssertEqual(saved["interest"] as? [String], ["ai_call"])
        // channels_on, count and has_phone were REMOVED from this payload on 25 September 2026.
        XCTAssertNil(saved["channels_on"])
        XCTAssertNil(saved["count"])
        XCTAssertNil(saved["has_phone"])
    }

    func testNoDialogWhenPushIsOff() async {
        let model = build(.notDetermined)
        await model.arrived()
        await model.pushChanged(false)
        await model.confirmDeactivation()
        analytics.clear()

        var advanced = false
        await model.savePressed { advanced = true }

        XCTAssertEqual(host.dialogs, 0, "with push off, no dialog appears")
        XCTAssertTrue(advanced, "Save preferences works with push off -- push is not forced")
        XCTAssertEqual(analytics.names, [ProfileAnalytics.reachabilitySavedName])
        let lastValue = await consent.lastValue
        XCTAssertEqual(lastValue, false)
    }

    func testNoDialogWhenTheStatusIsAlreadyDetermined() async {
        // Android <= 12 always, and the restore guard on both platforms.
        let model = build(.granted)
        await model.arrived()
        analytics.clear()

        var advanced = false
        await model.savePressed { advanced = true }

        XCTAssertEqual(host.dialogs, 0)
        XCTAssertTrue(advanced)
        XCTAssertEqual(analytics.names, [ProfileAnalytics.reachabilitySavedName])
        XCTAssertEqual(
            analytics.only(ProfileAnalytics.reachabilitySavedName)["permission"] as? String,
            "granted"
        )
    }

    func testADenialShowsTheConfirmWithOpenSettingsAndDoesNotAdvance() async {
        let model = build(.notDetermined)
        await model.arrived()
        analytics.clear()
        host.grants = false

        var advanced = false
        await model.savePressed { advanced = true }

        XCTAssertEqual(model.state.prompt, .afterOsDenial)
        XCTAssertFalse(advanced, "the save stops: the user has a decision to make")
        let calls = await consent.calls
        XCTAssertEqual(calls, 0)
        XCTAssertEqual(
            analytics.only(ProfileAnalytics.permissionResultName)["result"] as? String, "denied"
        )
        XCTAssertEqual(analytics.count(ProfileAnalytics.consentDeactivationConfirmShownName), 1)
        // The CTA works again afterwards -- it is never a dead end.
        XCTAssertFalse(model.state.saving)
    }

    func testOpenSettingsIsNotAnAbandonment() async {
        let model = build(.notDetermined)
        await model.arrived()
        host.grants = false
        await model.savePressed { }
        analytics.clear()

        model.openSettings()

        // THE TICKET NAMES THE DISTINCTION: this is `permission_settings_opened`, and NOT
        // `consent_deactivation_abandoned` -- they answer different questions.
        XCTAssertEqual(analytics.names, [ProfileAnalytics.permissionSettingsOpenedName])
        XCTAssertEqual(host.settingsOpened, 1)
        XCTAssertNil(model.state.prompt)
    }

    /// THE HALF OF STATE D THAT THE FIRST BUILD DROPPED.
    ///
    /// "Confirm deactivation → push off → **save** → Location (12)". Confirming in state D is the
    /// user finishing the save the OS dialog interrupted, not starting a new one.
    func testConfirmingAfterADenialFinishesTheSaveAndAdvances() async {
        let access = MovingAccess(.notDetermined)
        let model = build(moving: access)
        await model.arrived()
        model.interestToggled(.sms)
        host.grants = false

        var advanced = false
        await model.savePressed { advanced = true }
        XCTAssertEqual(model.state.prompt, .afterOsDenial)
        XCTAssertFalse(advanced, "not yet -- the dialog is the decision")
        analytics.clear()

        // The OS answer is on file now, which is what the device would report from here on.
        await access.set(.denied)

        await model.confirmDeactivation { advanced = true }

        XCTAssertTrue(advanced, "state D's confirm IS the save finishing")
        XCTAssertFalse(model.state.pushOn)
        let calls = await consent.calls
        XCTAssertEqual(calls, 1, "the consent is written once, with push off")
        let lastValue = await consent.lastValue
        XCTAssertEqual(lastValue, false)

        let saved = analytics.only(ProfileAnalytics.reachabilitySavedName)
        XCTAssertEqual(saved["push_on"] as? Bool, false)
        // READ AFTER THE ANSWER, not captured at the tap.
        XCTAssertEqual(saved["permission"] as? String, "denied")
        XCTAssertEqual(saved["interest"] as? [String], ["sms"])
        XCTAssertFalse(model.state.saving)
    }

    func testConfirmingAUserInitiatedSwitchOffSavesNothingAndGoesNowhere() async {
        // STATE C, the other half: "the user stays on this screen".
        let model = build()
        await model.arrived()
        await model.pushChanged(false)
        analytics.clear()

        var advanced = false
        await model.confirmDeactivation { advanced = true }

        XCTAssertFalse(advanced, "nothing was being saved, so nothing finishes")
        let calls = await consent.calls
        XCTAssertEqual(calls, 0, "no consent write until Save preferences")
        XCTAssertEqual(analytics.count(ProfileAnalytics.reachabilitySavedName), 0)
        XCTAssertFalse(model.state.pushOn)
        XCTAssertNil(model.state.prompt)
    }

    func testDismissingTheDenialDialogLeavesAToggleThatTellsTheTruth() async {
        // A scrim tap on state D is the one path that can leave a switch claiming a permission the
        // DEVICE has refused -- `Keep active` is not even drawn there.
        let access = MovingAccess(.notDetermined)
        let model = build(moving: access)
        await model.arrived()
        host.grants = false
        await model.savePressed { }
        await access.set(.denied)

        await model.keepActive()

        XCTAssertFalse(model.state.pushOn, "the OS refused it, so the switch must not claim it")
        XCTAssertNil(model.state.prompt)
        XCTAssertEqual(analytics.count(ProfileAnalytics.consentDeactivationAbandonedName), 1)
    }

    func testNothingAdvancesUntilTheServerConfirms() async {
        // NOT OPTIMISTIC. On a failure the user stays on 10 with their choices kept, and the CTA
        // works again.
        await consent.setSucceeds(false)
        let model = build(.granted)
        await model.arrived()
        model.interestToggled(.whatsApp)
        analytics.clear()

        var advanced = false
        await model.savePressed { advanced = true }

        XCTAssertFalse(advanced, "the flow position advances only after the server confirms")
        XCTAssertTrue(model.state.saveFailed)
        XCTAssertFalse(model.state.saving, "and the CTA answers again")
        XCTAssertTrue(model.state.isInterested(.whatsApp), "the choices are kept")
        XCTAssertEqual(analytics.count(ProfileAnalytics.reachabilitySavedName), 0,
                       "the event is after the confirmation, never on the tap")

        // And a retry that succeeds goes through.
        await consent.setSucceeds(true)
        await model.savePressed { advanced = true }
        XCTAssertTrue(advanced)
        XCTAssertFalse(model.state.saveFailed)
    }

    func testTheSaveCarriesTheFinalInterestInTheDrawnOrder() async {
        let model = build(.granted)
        await model.arrived()
        // Checked out of order, and one of them undone.
        model.interestToggled(.sms)
        model.interestToggled(.aiCall)
        model.interestToggled(.whatsApp)
        model.interestToggled(.whatsApp)
        analytics.clear()

        await model.savePressed { }

        // THE DRAWN ORDER, so two identical states never serialise two ways.
        XCTAssertEqual(
            analytics.only(ProfileAnalytics.reachabilitySavedName)["interest"] as? [String],
            ["ai_call", "sms"]
        )
    }

    func testTheCTACannotBeTappedTwice() async {
        let model = build(.notDetermined)
        await model.arrived()
        analytics.clear()
        host.gated = true

        // Tap one, left suspended on the OS dialog.
        var advances = 0
        let first = Task { await model.savePressed { advances += 1 } }
        while host.dialogs == 0 { await Task.yield() }
        XCTAssertTrue(model.state.saving, "input is locked from the tap")

        // Tap two, while tap one is still in flight. THIS IS THE DOUBLE TAP.
        await model.savePressed { advances += 1 }
        XCTAssertEqual(host.dialogs, 1, "the second press must not raise a second dialog")

        // Let the first finish.
        host.release()
        await first.value

        XCTAssertEqual(advances, 1, "one navigation, not two")
        let calls = await consent.calls
        XCTAssertEqual(calls, 1, "and one consent write")
        XCTAssertEqual(analytics.count(ProfileAnalytics.reachabilitySavedName), 1)
        XCTAssertEqual(analytics.count(ProfileAnalytics.permissionOsSheetShownName), 1)
        XCTAssertFalse(model.state.saving, "and the CTA answers again afterwards")
    }

    // MARK: - the foreground re-read

    func testComingBackFromSettingsUpdatesTheToggleAndAdvancesNothing() async {
        let access = MovingAccess(.denied)
        let model = build(moving: access)
        await model.arrived()
        XCTAssertFalse(model.state.pushOn)
        analytics.clear()

        await access.set(.granted)
        await model.foregrounded()

        XCTAssertTrue(model.state.pushOn, "returning with notifications allowed shows it on")
        // NOTHING AUTO-ADVANCES and nothing is reported: `permission_status_changed` belongs to
        // the shared reconciler, and firing it here would double up with `permission_result`.
        XCTAssertEqual(analytics.names, [])
    }

    func testTheReconcilersEventIsNotThisScreensToFire() async {
        let model = build(.notDetermined)
        await model.arrived()
        host.grants = false
        await model.savePressed { }
        await model.foregrounded()

        XCTAssertEqual(analytics.count(ProfileAnalytics.permissionStatusChangedName), 0)
    }

    // MARK: - what must never fire here

    func testTheForbiddenEventsNeverFire() async {
        let model = build(.notDetermined)
        await model.arrived()
        model.interestToggled(.aiCall)
        await model.pushChanged(false)
        await model.confirmDeactivation()
        await model.pushChanged(true)
        model.privacyTapped()
        await model.savePressed { }

        let forbidden = [
            "profile_step_viewed", "profile_step_completed", "profile_step_skipped",
            "concierge_contact_saved", "concierge_contact_skipped",
            ProfileAnalytics.permissionStatusChangedName,
        ]
        for name in forbidden {
            XCTAssertEqual(analytics.count(name), 0, "\(name) must not fire on this screen")
        }

        // And every consent_changed this screen emits is `push`, never one of the reserved six.
        let channels = analytics.events
            .filter { $0.0 == ProfileAnalytics.consentChangedName }
            .compactMap { $0.1["channel"] as? String }
        XCTAssertFalse(channels.isEmpty)
        XCTAssertTrue(channels.allSatisfy { $0 == "push" },
                      "call, whatsapp, sms, calendar and email are the later scope: \(channels)")
    }

    func testThePrivacyLinkCarriesTheKeyNotTheLabel() async {
        let model = build()
        await model.arrived()
        analytics.clear()

        model.privacyTapped()

        let e = analytics.only(SignUpAnalytics.legalLinkTapped)
        XCTAssertEqual(e["link"] as? String, "privacy")
        XCTAssertEqual(e["screen_id"] as? String, "profile_reachability")
        XCTAssertNil(e["screen_name"], "the label belongs to screen_viewed")
    }
}
