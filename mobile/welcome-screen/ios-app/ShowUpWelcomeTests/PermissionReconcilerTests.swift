//
//  PermissionReconcilerTests.swift
//  ShowUp · the shared permission reconciler's rules, with no device (epic SHOWUP-151, SHOWUP-163)
//
//  The Swift twin of `PermissionReconcilerTest.kt`, rule for rule: a starting point reports
//  nothing, a change made outside the app fires once with what it was and what it is, the first
//  start is the launch and every later one a foreground, our own dialog's answer never fires it,
//  and a notifications change moves the push token.
//

import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
}

/// The phone's permissions, changeable between reads.
private actor FakeDevice: PermissionSnapshotting {
    private var statuses: [WatchedPermission: PermissionStatus] =
        Dictionary(uniqueKeysWithValues: WatchedPermission.allCases.map { ($0, .notDetermined) })

    func set(_ permission: WatchedPermission, _ status: PermissionStatus) { statuses[permission] = status }
    func read() async -> [WatchedPermission: PermissionStatus] { statuses }
}

private actor CountingPush: PushTokenSyncing {
    private(set) var registered = 0
    private(set) var unregistered = 0
    func register() async { registered += 1 }
    func unregister() async { unregistered += 1 }
}

@MainActor
final class PermissionReconcilerTests: XCTestCase {

    private var device = FakeDevice()
    private var ledger = InMemoryPermissionLedger()
    private var analytics = Recorder()
    private var push = CountingPush()
    /// Launch on the first start, foreground after -- as the process-wide mark does.
    private var starts = 0

    override func setUp() async throws {
        try await super.setUp()
        device = FakeDevice()
        ledger = InMemoryPermissionLedger()
        analytics = Recorder()
        push = CountingPush()
        starts = 0
    }

    private func reconciler() -> PermissionReconciler {
        PermissionReconciler(snapshot: device, ledger: ledger, push: push, analytics: analytics,
                             firstStart: { [unowned self] in
                                 self.starts += 1
                                 return self.starts == 1
                             })
    }

    private var changes: [[String: any Sendable]] { analytics.of(ProfileAnalytics.permissionStatusChangedName) }

    /// The token calls run as their own tasks: give them a moment.
    private func settle() async { try? await Task.sleep(nanoseconds: 50_000_000) }

    func testTheFirstStartRecordsAStartingPointAndReportsNothing() async {
        await device.set(.location, .granted)
        let changed = await reconciler().onStart(inFlow: false)
        XCTAssertTrue(changed.isEmpty)
        XCTAssertTrue(changes.isEmpty)
        XCTAssertEqual(ledger.last(.location), .granted)
        XCTAssertEqual(WatchedPermission.allCases.filter { ledger.last($0) != nil }.count, WatchedPermission.allCases.count,
                       "every watched permission has a starting point")
    }

    func testAChangeMadeOutsideTheAppFiresOnceWithWhatItWasAndWhatItIs() async {
        await device.set(.notifications, .granted)
        let r = reconciler()
        await r.onStart(inFlow: false)                                  // launch: the starting point
        await device.set(.notifications, .denied)                       // turned off in Settings
        await r.onStart(inFlow: false)
        await r.onStart(inFlow: false)                                  // nothing new since
        XCTAssertEqual(changes.count, 1)
        let fired = changes.first ?? [:]
        XCTAssertEqual(fired["type"] as? String, "notifications")
        XCTAssertEqual(fired["from"] as? String, "granted")
        XCTAssertEqual(fired["to"] as? String, "denied")
        XCTAssertEqual(fired["detected_on"] as? String, "foreground")
        XCTAssertEqual(fired["in_flow"] as? Bool, false)
    }

    func testAChangeFoundAtLaunchSaysLaunchAndOneInsideProfileCreationSaysSo() async {
        // A change made while the app was not running: recorded by an earlier process.
        ledger.record(.camera, .granted)
        await device.set(.camera, .denied)
        await reconciler().onStart(inFlow: true)
        XCTAssertEqual(changes.count, 1)
        let fired = changes.first ?? [:]
        XCTAssertEqual(fired["type"] as? String, "camera")
        XCTAssertEqual(fired["detected_on"] as? String, "launch")
        XCTAssertEqual(fired["in_flow"] as? Bool, true)
    }

    func testOurOwnDialogsAnswerIsAbsorbedAndNeverFiresTheEvent() async {
        let r = reconciler()
        await r.onStart(inFlow: true)
        await device.set(.location, .granted)                           // the user answered OUR sheet
        await r.absorbOwnAnswer()
        await r.onStart(inFlow: true)
        XCTAssertTrue(changes.isEmpty, "that act is permission_result's")
        XCTAssertEqual(ledger.last(.location), .granted)
    }

    func testANotificationsGrantRegistersThePushTokenAndARefusalRemovesIt() async {
        let r = reconciler()
        await r.onStart(inFlow: false)
        await device.set(.notifications, .granted)
        await r.onStart(inFlow: false)
        await settle()
        var registered = await push.registered
        XCTAssertEqual(registered, 1)
        await device.set(.notifications, .denied)
        await r.onStart(inFlow: false)
        await settle()
        var unregistered = await push.unregistered
        XCTAssertEqual(unregistered, 1)
        await device.set(.notifications, .restricted)
        await r.onStart(inFlow: false)
        await settle()
        unregistered = await push.unregistered
        XCTAssertEqual(unregistered, 2, "restricted is treated as denied")
        registered = await push.registered
        XCTAssertEqual(registered, 1)
    }

    func testOnlyNotificationsMoveThePushToken() async {
        let r = reconciler()
        await r.onStart(inFlow: false)
        await device.set(.location, .granted)
        await device.set(.camera, .denied)
        await device.set(.microphone, .granted)
        await r.onStart(inFlow: false)
        await settle()
        let registered = await push.registered
        let unregistered = await push.unregistered
        XCTAssertEqual(registered, 0)
        XCTAssertEqual(unregistered, 0)
        XCTAssertEqual(Set(changes.compactMap { $0["type"] as? String }), ["location", "camera", "microphone"],
                       "each change is its own event")
    }

    func testTheSnapshotReadsTheScreensOwnReadersAndMapsCaptureOntoSection24() async {
        let snapshot = await ReaderPermissionSnapshot(
            notifications: FixedNotificationAccess(.granted),
            // The device-wide switch is off, the grant stands: §24 is about the grant.
            location: FixedLocationAccess(.granted, servicesOn: false),
            media: FixedMediaAccess(access: MediaAccess(camera: .canAsk, microphone: .blocked))
        ).read()
        XCTAssertEqual(snapshot[.notifications], .granted)
        XCTAssertEqual(snapshot[.location], .granted)
        XCTAssertEqual(snapshot[.camera], .denied)
        XCTAssertEqual(snapshot[.microphone], .denied)
        XCTAssertEqual(MediaPermission.notDetermined.permissionStatus, .notDetermined)
        XCTAssertEqual(MediaPermission.granted.permissionStatus, .granted)
    }

    func testTheProcessLaunchMarkIsTrueExactlyOnce() {
        // Process-wide, so whatever the first answer was after earlier tests, the second is false.
        _ = ProcessLaunchMark.firstStart()
        XCTAssertFalse(ProcessLaunchMark.firstStart())
    }
}
