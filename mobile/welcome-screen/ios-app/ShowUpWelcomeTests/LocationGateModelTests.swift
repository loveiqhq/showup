//
//  LocationGateModelTests.swift
//  ShowUp · the Search location gate's rules (SHOWUP-188)
//
//  The Swift half of `LocationGateViewModelTest.kt`: the state table, the events the ticket names and
//  the ones it forbids, A's dialog, B and C's Settings routing, the foreground re-read, and D.
//

import XCTest
@testable import ShowUpWelcome

@MainActor
private final class GateRecorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
    func clear() { events.removeAll() }
}

/// A status that MOVES -- what Settings does while the app is away.
private actor GateAccess: LocationAccessReading {
    private var status: LocationStatus
    init(_ permission: PermissionStatus, servicesOn: Bool = true) {
        status = LocationStatus(permission: permission, servicesOn: servicesOn)
    }
    func set(_ permission: PermissionStatus, servicesOn: Bool = true) {
        status = LocationStatus(permission: permission, servicesOn: servicesOn)
    }
    func read() async -> LocationStatus { status }
}

@MainActor
private final class GateHost: LocationHosting {
    var dialogs = 0
    var settings: [LocationState] = []
    /// What the dialog answers, and what the phone reads as afterwards.
    var answer = true
    var afterwards: (() async -> Void)?

    func requestLocationPermission() async -> Bool {
        dialogs += 1
        await afterwards?()
        return answer
    }

    func openSettings(for state: LocationState) { settings.append(state) }
}

@MainActor
final class LocationGateModelTests: XCTestCase {
    private var events: GateRecorder!
    private var host: GateHost!

    override func setUp() {
        super.setUp()
        events = GateRecorder()
        host = GateHost()
    }

    private func gate(_ access: GateAccess) async -> LocationGateModel {
        let g = LocationGateModel(access: access, analytics: events)
        g.attach(host)
        await g.refresh()
        return g
    }

    private func assertNoForbidden(file: StaticString = #filePath, line: UInt = #line) {
        for name in [ProfileAnalytics.permissionPromptedName, ProfileAnalytics.permissionDeniedRecoveryShownName,
                     "profile_step_skipped", "profile_step_viewed", "profile_step_completed"] {
            XCTAssertTrue(events.of(name).isEmpty, "\(name) must not fire on the gate", file: file, line: line)
        }
    }

    // MARK: - the state table

    func testNothingIsKnownBeforeTheFirstRead() {
        let g = LocationGateModel(access: GateAccess(.denied), analytics: events)
        XCTAssertFalse(g.ui.known, "the Search tab draws neither Search nor the gate yet")
    }

    func testServicesOffFirstThenDeniedThenNeverAsked() async {
        for permission in [PermissionStatus.notDetermined, .granted, .denied, .restricted] {
            let g = await gate(GateAccess(permission, servicesOn: false))
            XCTAssertEqual(g.ui.blocked, .servicesOff, "\(permission) with Location Services off")
        }
        let denied = await gate(GateAccess(.denied))
        XCTAssertEqual(denied.ui.blocked, .denied)
        let restricted = await gate(GateAccess(.restricted))
        XCTAssertEqual(restricted.ui.blocked, .denied, "restricted is denied (§24)")
        let ask = await gate(GateAccess(.notDetermined))
        XCTAssertEqual(ask.ui.blocked, .ask)
        let granted = await gate(GateAccess(.granted))
        XCTAssertNil(granted.ui.blocked, "granted, any precision: Search, no gate")
        XCTAssertTrue(granted.ui.known)
    }

    // MARK: - what it reports

    func testAMountReportsScreenViewedOnceAndTheStateOnce() async {
        let g = await gate(GateAccess(.notDetermined))
        g.mounted(referrer: .likesReceived)
        g.mounted(referrer: .likesReceived)
        XCTAssertEqual(events.names, ["screen_viewed", ProfileAnalytics.locationGateShownName])
        XCTAssertEqual(events.of("screen_viewed").first?["screen_id"] as? String, "search_location_gate")
        XCTAssertEqual(events.of("screen_viewed").first?["screen_name"] as? String, "Search - Location gate")
        XCTAssertEqual(events.of("screen_viewed").first?["referrer_screen_id"] as? String, "likes_received")
        XCTAssertEqual(events.of(ProfileAnalytics.locationGateShownName).first?["reason"] as? String, "not_determined")
        assertNoForbidden()
    }

    func testAForegroundReportsAgainOnlyWhenTheReasonChanged() async {
        let access = GateAccess(.denied, servicesOn: false)
        let g = await gate(access)
        g.mounted(referrer: nil)
        await g.refresh()
        XCTAssertEqual(events.of(ProfileAnalytics.locationGateShownName).count, 1, "not on every foreground")
        await access.set(.denied)
        await g.refresh()
        XCTAssertEqual(g.ui.blocked, .denied, "C -> B, in place")
        XCTAssertEqual(events.of(ProfileAnalytics.locationGateShownName).compactMap { $0["reason"] as? String },
                       ["services_off", "denied"])
        XCTAssertEqual(events.of("screen_viewed").count, 1, "one screen_viewed per mount, never per state")
    }

    // MARK: - A: the dialog

    func testAllowRaisesTheDialogAndAGrantLiftsTheGateInPlace() async {
        let access = GateAccess(.notDetermined)
        let g = await gate(access)
        g.mounted(referrer: nil)
        events.clear()
        host.afterwards = { await access.set(.granted) }
        await g.allowPressed()
        XCTAssertEqual(host.dialogs, 1)
        XCTAssertEqual(events.names, [ProfileAnalytics.permissionOsSheetShownName, ProfileAnalytics.permissionResultName])
        XCTAssertEqual(events.of(ProfileAnalytics.permissionOsSheetShownName).first?["screen_id"] as? String, "search_location_gate")
        XCTAssertEqual(events.of(ProfileAnalytics.permissionResultName).first?["result"] as? String, "granted")
        XCTAssertEqual(events.of(ProfileAnalytics.permissionResultName).first?["screen_id"] as? String, "search_location_gate")
        XCTAssertNil(g.ui.blocked)
        XCTAssertFalse(g.ui.requesting)
        XCTAssertTrue(g.takeLift(), "Search loads, reached from the gate")
        XCTAssertFalse(g.takeLift(), "asked once")
        assertNoForbidden()
    }

    func testDeniedShowsBInPlace() async {
        let g = await gate(GateAccess(.notDetermined))
        g.mounted(referrer: nil)
        host.answer = false
        await g.allowPressed()
        XCTAssertEqual(g.ui.blocked, .denied)
        XCTAssertEqual(events.of(ProfileAnalytics.permissionResultName).first?["result"] as? String, "denied")
        XCTAssertEqual(events.of(ProfileAnalytics.locationGateShownName).compactMap { $0["reason"] as? String },
                       ["not_determined", "denied"])
        XCTAssertFalse(g.takeLift())
    }

    func testAGrantWithLocationServicesOffStillBlocksAsC() async {
        let access = GateAccess(.notDetermined)
        let g = await gate(access)
        g.mounted(referrer: nil)
        host.afterwards = { await access.set(.granted, servicesOn: false) }
        await g.allowPressed()
        XCTAssertEqual(g.ui.blocked, .servicesOff)
    }

    func testBAndCHaveNoDialogAndAHasNoSettings() async {
        let a = await gate(GateAccess(.notDetermined))
        a.mounted(referrer: nil)
        a.openSettingsPressed()
        let b = await gate(GateAccess(.denied))
        b.mounted(referrer: nil)
        await b.allowPressed()
        XCTAssertTrue(host.settings.isEmpty)
        XCTAssertEqual(host.dialogs, 0)
    }

    // MARK: - B and C: Settings, and the way back

    func testOpenSettingsStaysAndTheReturnLiftsWithNoTap() async {
        let access = GateAccess(.denied)
        let g = await gate(access)
        g.mounted(referrer: nil)
        g.openSettingsPressed()
        XCTAssertEqual(host.settings, [.denied])
        XCTAssertEqual(events.of(ProfileAnalytics.permissionSettingsOpenedName).first?["screen_id"] as? String,
                       "search_location_gate")
        XCTAssertEqual(g.ui.blocked, .denied, "stays on the gate")
        await access.set(.granted)
        await g.refresh()
        XCTAssertNil(g.ui.blocked)
        XCTAssertTrue(g.takeLift())
        XCTAssertTrue(events.of(ProfileAnalytics.permissionResultName).isEmpty,
                      "a grant noticed on return is the reconciler's")
    }

    func testTheStatusFollowsThePhoneOffScreenAndReportsNothing() async {
        let access = GateAccess(.granted)
        let g = await gate(access)
        await access.set(.denied)
        await g.refresh()
        XCTAssertEqual(g.ui.blocked, .denied)
        await access.set(.granted)
        await g.refresh()
        XCTAssertNil(g.ui.blocked)
        XCTAssertFalse(g.takeLift(), "not a lift of a gate that was never up")
        XCTAssertTrue(events.events.isEmpty)
    }

    // MARK: - D: Confirm date on a profile opened from the Likes tab

    func testDWithLocationUsableGoesOnToBeSure() async {
        let g = await gate(GateAccess(.granted))
        let needs = await g.confirmNeedsLocation()
        XCTAssertFalse(needs)
        XCTAssertFalse(g.ui.overProfile)
    }

    func testDWithoutLocationGoesOverTheProfileAndBackReturnsToIt() async {
        let g = await gate(GateAccess(.denied))
        let needs = await g.confirmNeedsLocation()
        XCTAssertTrue(needs)
        XCTAssertTrue(g.ui.overProfile)
        g.mounted(referrer: .searchProfile)
        XCTAssertEqual(events.of("screen_viewed").first?["referrer_screen_id"] as? String, "search_profile")
        g.backToProfile()
        XCTAssertFalse(g.ui.overProfile)
        XCTAssertEqual(g.ui.blocked, .denied, "still blocked: back is not a grant")
    }

    func testDLocationTurnedOnLiftsBackToTheProfileAndConfirmsNothing() async {
        let access = GateAccess(.denied)
        let g = await gate(access)
        _ = await g.confirmNeedsLocation()
        g.mounted(referrer: .searchProfile)
        g.openSettingsPressed()
        await access.set(.granted)
        await g.refresh()
        XCTAssertFalse(g.ui.overProfile, "back on the profile")
        XCTAssertNil(g.ui.blocked)
        XCTAssertFalse(g.takeLift(), "no Search arrival from D's lift")
    }
}
