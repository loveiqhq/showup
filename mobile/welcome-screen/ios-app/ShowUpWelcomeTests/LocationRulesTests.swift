//
//  LocationRulesTests.swift
//  ShowUp · the location ask's rules, with no device and no permission (SHOWUP-165)
//
//  The Swift half of `LocationRulesTest.kt`. The arrival matrix, CoreLocation's statuses in §24's
//  words, and the model: every tracking row, the position advancing AFTER the answer and only on a
//  grant or `Not now`, one act producing one event, and the foreground reconciler doing nothing
//  while our own dialog is up.
//
//  What it cannot prove: that iOS raises the dialog, or that Settings opens. `FakeHost` asks no OS.
//

import CoreLocation
import ShowUpAPI
import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
    func clear() { events.removeAll() }
}

private actor FakePositions: FlowPositionReporting {
    private(set) var reported: [Components.Schemas.FlowPosition] = []
    func report(_ position: Components.Schemas.FlowPosition) async -> Bool {
        reported.append(position)
        return true
    }
}

/// A status that MOVES — what Settings does while the app is away. `FixedLocationAccess` cannot.
private actor MovingLocationAccess: LocationAccessReading {
    private var status: LocationStatus
    init(_ permission: PermissionStatus = .notDetermined, servicesOn: Bool = true) {
        status = LocationStatus(permission: permission, servicesOn: servicesOn)
    }
    func set(_ permission: PermissionStatus, servicesOn: Bool = true) {
        status = LocationStatus(permission: permission, servicesOn: servicesOn)
    }
    func read() async -> LocationStatus { status }
}

@MainActor
private final class FakeHost: LocationHosting {
    var grants = true
    var dialogs = 0
    var settings: [LocationState] = []
    /// Holds the dialog open until the test answers it — the only way to have a second press, or a
    /// foreground, land while our own dialog is up.
    var gated = false
    private var gate: CheckedContinuation<Bool, Never>?

    func requestLocationPermission() async -> Bool {
        dialogs += 1
        guard gated else { return grants }
        return await withCheckedContinuation { gate = $0 }
    }

    func release() {
        let waiting = gate
        gate = nil
        waiting?.resume(returning: grants)
    }

    func openSettings(for state: LocationState) { settings.append(state) }
}

@MainActor
final class LocationRulesTests: XCTestCase {

    // MARK: - the matrix

    private func status(_ permission: PermissionStatus, on: Bool = true) -> LocationStatus {
        LocationStatus(permission: permission, servicesOn: on)
    }

    func testThePhonesSwitchOffIsCWhateverThePermissionSaysCheckedFirst() {
        for permission in [PermissionStatus.notDetermined, .granted, .denied, .restricted] {
            XCTAssertEqual(locationArrival(status(permission, on: false)), .show(.servicesOff), "\(permission)")
        }
    }

    func testNotDeterminedIsAGrantedIsSkippedDeniedAndRestrictedAreB() {
        XCTAssertEqual(locationArrival(status(.notDetermined)), .show(.ask))
        XCTAssertEqual(locationArrival(status(.granted)), .skip)
        XCTAssertEqual(locationArrival(status(.denied)), .show(.denied))
        XCTAssertEqual(locationArrival(status(.restricted)), .show(.denied))
    }

    func testBAndCCarryTheirSection26ReasonsAndACarriesNone() {
        XCTAssertNil(LocationState.ask.unavailableReason)
        XCTAssertEqual(LocationState.denied.unavailableReason?.rawValue, "denied")
        XCTAssertEqual(LocationState.servicesOff.unavailableReason?.rawValue, "services_off")
    }

    func testWhileInUseAlwaysAndAllowOnceAreAllAGrant() {
        // Allow Once reads as `authorizedWhenInUse` for the session — exactly what the ticket
        // counts as a grant. Always is never asked for, but granted elsewhere it is still a grant.
        XCTAssertEqual(locationPermission(for: .authorizedWhenInUse), .granted)
        XCTAssertEqual(locationPermission(for: .authorizedAlways), .granted)
        XCTAssertEqual(locationPermission(for: .notDetermined), .notDetermined)
        XCTAssertEqual(locationPermission(for: .denied), .denied)
        XCTAssertEqual(locationPermission(for: .restricted), .restricted)
    }

    func testThePurposeStringIsTheTicketsCopyExactly() throws {
        // Without it iOS terminates the app on the request — the one item that cannot ship hidden.
        // The test bundle is HOSTED by the app (TEST_HOST in gen_pbxproj.py), so the main bundle
        // is the app's, carrying the Info.plist that ships.
        let text = Bundle.main.object(forInfoDictionaryKey: "NSLocationWhenInUseUsageDescription")
        XCTAssertEqual(
            text as? String,
            "We use your location to find people nearby and choose a halfway meeting spot for your date.")
        // And never the Always key: the app needs location only while it is open.
        XCTAssertNil(Bundle.main.object(forInfoDictionaryKey: "NSLocationAlwaysAndWhenInUseUsageDescription"))
    }

    // MARK: - the model

    private var analytics = Recorder()
    private var positions = FakePositions()
    private var host = FakeHost()

    override func setUp() {
        super.setUp()
        analytics = Recorder()
        positions = FakePositions()
        host = FakeHost()
    }

    private func build(_ access: any LocationAccessReading = FixedLocationAccess()) -> LocationModel {
        let model = LocationModel(access: access, positions: positions, analytics: analytics)
        model.attach(host)
        return model
    }

    private func settle() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
    }

    /// The phone in the state `state` reads as.
    private func access(for state: LocationState) -> FixedLocationAccess {
        switch state {
        case .ask: return FixedLocationAccess(.notDetermined)
        case .denied: return FixedLocationAccess(.denied)
        case .servicesOff: return FixedLocationAccess(.notDetermined, servicesOn: false)
        }
    }

    /// A model on a phone that reads as `state`, with the screen arrived.
    private func shown(_ state: LocationState,
                       referrer: ProfileScreen? = .reachability) async -> LocationModel {
        let model = build(access(for: state))
        await model.arrived(initial: state, referrer: referrer) { XCTFail("\(state) must not advance") }
        return model
    }

    func testAFiresScreenViewedAndPermissionPromptedLocationOncePerMount() async {
        let model = await shown(.ask)
        await model.arrived(initial: .ask, referrer: .reachability) {}
        XCTAssertEqual(analytics.names, ["screen_viewed", "permission_prompted"])
        let view = analytics.of("screen_viewed")[0]
        XCTAssertEqual(view["screen_id"] as? String, "profile_location")
        XCTAssertEqual(view["screen_name"] as? String, "ProfileLocation")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "profile_reachability")
        XCTAssertEqual(analytics.of("permission_prompted")[0]["type"] as? String, "location")
    }

    func testBFiresTheRecoveryEventWithReasonDeniedAndNeverPermissionPrompted() async {
        _ = await shown(.denied)
        XCTAssertEqual(analytics.names, ["screen_viewed", "permission_denied_recovery_shown"])
        let recovery = analytics.of("permission_denied_recovery_shown")[0]
        XCTAssertEqual(recovery["type"] as? String, "location")
        XCTAssertEqual(recovery["reason"] as? String, "denied")
    }

    func testCFiresTheRecoveryEventWithReasonServicesOff() async {
        _ = await shown(.servicesOff)
        XCTAssertEqual(analytics.of("permission_denied_recovery_shown")[0]["reason"] as? String, "services_off")
    }

    func testAllowRaisesTheDialogAndNothingElseAndCannotRaiseItTwice() async {
        let model = await shown(.ask)
        analytics.clear()
        host.gated = true
        let press = Task { await model.allowPressed {} }
        while host.dialogs == 0 { await Task.yield() }
        await model.allowPressed {}
        XCTAssertEqual(host.dialogs, 1)
        XCTAssertEqual(analytics.names, ["permission_os_sheet_shown"])
        XCTAssertTrue(model.ui.requesting)
        host.release()
        await press.value
    }

    func testGrantedRecordsThePositionAfterTheAnswerAndAdvances() async {
        let model = await shown(.ask)
        host.gated = true
        var advanced = false
        let press = Task { await model.allowPressed { advanced = true } }
        while host.dialogs == 0 { await Task.yield() }
        await settle()
        let whileUp = await positions.reported
        XCTAssertTrue(whileUp.isEmpty, "nothing recorded while the dialog is up")
        host.release()
        await press.value
        await settle()
        XCTAssertTrue(advanced)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
        let result = analytics.of("permission_result")[0]
        XCTAssertEqual(result["result"] as? String, "granted")
        XCTAssertEqual(result["type"] as? String, "location")
    }

    func testDeniedShowsBInPlaceRecordsNothingAndSoARelaunchLandsOnB() async {
        let model = await shown(.ask)
        host.grants = false
        var advanced = false
        await model.allowPressed { advanced = true }
        await settle()
        XCTAssertFalse(advanced)
        XCTAssertEqual(model.ui.state, .denied)
        XCTAssertFalse(model.ui.requesting)
        let reported = await positions.reported
        XCTAssertTrue(reported.isEmpty, "the position stays at Stay reachable")
        XCTAssertEqual(analytics.of("permission_result")[0]["result"] as? String, "denied")
        XCTAssertEqual(analytics.of("permission_denied_recovery_shown")[0]["reason"] as? String, "denied")
    }

    func testOpenSettingsOpensThePageForTheStateAndStays() async {
        let model = await shown(.servicesOff)
        analytics.clear()
        model.openSettingsPressed()
        XCTAssertEqual(host.settings, [.servicesOff])
        XCTAssertEqual(analytics.names, ["permission_settings_opened"])
        XCTAssertEqual(model.ui.state, .servicesOff)
    }

    func testNotNowRecordsThePositionFiresProfileStepSkippedLocationAndAdvances() async {
        let model = await shown(.denied)
        analytics.clear()
        var advanced = false
        model.notNowPressed { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
        let skipped = analytics.of("profile_step_skipped")[0]
        XCTAssertEqual(skipped["step_id"] as? String, "location")
        XCTAssertEqual(skipped["screen_id"] as? String, "profile_location")
    }

    func testAHasNoNotNowEquivalent() async {
        let model = await shown(.ask)
        var advanced = false
        model.notNowPressed { advanced = true }
        XCTAssertFalse(advanced)
    }

    func testAGrantInSettingsAdvancesSilentlyOnReturnNoPermissionResult() async {
        let access = MovingLocationAccess(.denied)
        let model = build(access)
        await model.arrived(initial: .denied, referrer: .reachability) {}
        analytics.clear()
        await access.set(.granted)
        var advanced = false
        await model.foregrounded { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        XCTAssertTrue(analytics.of("permission_result").isEmpty)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testTurningLocationOnFromCShowsAAndAAnnouncesItselfThen() async {
        let access = MovingLocationAccess(.notDetermined, servicesOn: false)
        let model = build(access)
        await model.arrived(initial: .servicesOff, referrer: .reachability) {}
        analytics.clear()
        await access.set(.notDetermined, servicesOn: true)
        await model.foregrounded {}
        XCTAssertEqual(model.ui.state, .ask)
        XCTAssertEqual(analytics.names, ["permission_prompted"])
    }

    func testWhileOurDialogIsUpTheReconcilerDoesNothingOneActOneEvent() async {
        let access = MovingLocationAccess(.notDetermined)
        let model = build(access)
        await model.arrived(initial: .ask, referrer: .reachability) {}
        host.gated = true
        let press = Task { await model.allowPressed {} }
        while host.dialogs == 0 { await Task.yield() }
        await access.set(.granted)
        var advanced = false
        await model.foregrounded { advanced = true }
        XCTAssertFalse(advanced)
        let reported = await positions.reported
        XCTAssertTrue(reported.isEmpty)
        host.release()
        await press.value
    }

    func testAGrantOnArrivalIsASilentSkipThatStillRecordsThePosition() async {
        let model = build(FixedLocationAccess(.granted))
        let arrival = await model.decideArrival()
        await settle()
        XCTAssertEqual(arrival, .skip)
        XCTAssertTrue(analytics.events.isEmpty, "nothing about the screen is reported")
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testLeavingAndComingBackIsANewMountThatReportsAgain() async {
        let model = await shown(.denied)
        model.left()
        await model.arrived(initial: .denied, referrer: .reachability) {}
        XCTAssertEqual(analytics.of("screen_viewed").count, 2)
    }

    func testAGrantMadeWhileTheAppWasGoneIsASilentSkipOnArrival() async {
        // Restored with the permission granted in Settings meanwhile: the arrival reads it, records
        // the position and advances, and reports no view of a screen the user never saw.
        let model = build(FixedLocationAccess(.granted))
        var advanced = false
        await model.arrived(initial: .denied, referrer: .reachability) { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        XCTAssertTrue(analytics.events.isEmpty)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testTheArrivalShowsWhatThePhoneSaysNotWhatThePushDecided() async {
        // Denied in place, then restored with the stored start still "ask".
        let model = build(FixedLocationAccess(.denied))
        await model.arrived(initial: .ask, referrer: .reachability) { XCTFail("no advance") }
        XCTAssertEqual(model.ui.state, .denied)
        XCTAssertEqual(analytics.names, ["screen_viewed", "permission_denied_recovery_shown"])
    }

    func testTapsOnTheOutgoingScreenAfterLeavingDoNothing() async {
        let model = await shown(.denied)
        var advances = 0
        model.notNowPressed { advances += 1 }
        model.left()
        model.notNowPressed { advances += 1 }
        await settle()
        XCTAssertEqual(advances, 1)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testNothingThatMustNotFireHereEverDoes() async {
        let model = await shown(.ask)
        host.grants = false
        await model.allowPressed {}
        model.openSettingsPressed()
        model.notNowPressed {}
        await settle()
        let forbidden: Set<String> = [
            "profile_step_viewed", "profile_step_completed", "location_gate_shown", "consent_changed",
        ]
        XCTAssertTrue(analytics.names.allSatisfy { !forbidden.contains($0) }, "\(analytics.names)")
    }
}
