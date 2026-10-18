//
//  PermissionReconciler.swift
//  ShowUp · the one shared permission reconciler (epic SHOWUP-151, built for SHOWUP-163)
//
//  The Swift twin of `profile/PermissionReconciler.kt`; its header holds the argument. In short,
//  the epic's rule: "On launch and on every foreground, for the life of the app: read the status ->
//  if it differs from the last value synced, update the server, register or invalidate the token,
//  and emit permission_status_changed (type, from, to, detected_on, in_flow)." Each permission
//  screen already re-reads while it is up; this runs everywhere else too, on the same readers.
//
//  OUR OWN DIALOG IS NOT AN OUTSIDE CHANGE. On iOS a system permission alert only makes the scene
//  inactive, while a trip to Settings sends it to the background -- so the host absorbs a return
//  from inactive alone (`absorbOwnAnswer`) and reconciles a return from the background. That act is
//  `permission_result`'s, and the two "must never both fire for one act".
//
//  "UPDATE THE SERVER" IS THE PUSH TOKEN, FOR NOW: the backend has no field for an OS permission
//  status, so a notifications change registers or removes the token and the status is recorded on
//  the device. Whether the server should store it too is the product owner's question.
//

import Foundation

/// The permissions the reconciler watches: the MVP's asks, as `enums.json` §8 names them.
enum WatchedPermission: String, CaseIterable, Sendable {
    case notifications
    case location
    case camera
    case microphone

    var trackingType: String { rawValue }
}

/// The last status recorded for each permission -- the `from` of the next change. Survives a
/// restart: iOS terminates an app whose camera, microphone or location access is changed in
/// Settings, so the change is found on the NEXT launch, against what this held.
@MainActor
protocol PermissionLedger {
    func last(_ permission: WatchedPermission) -> PermissionStatus?
    func record(_ permission: WatchedPermission, _ status: PermissionStatus)
}

/// The device's ledger. Statuses only, so the standard defaults are enough.
struct UserDefaultsPermissionLedger: PermissionLedger {
    private static let prefix = "showup.permissionLedger."

    func last(_ permission: WatchedPermission) -> PermissionStatus? {
        UserDefaults.standard.string(forKey: Self.prefix + permission.rawValue)
            .flatMap(PermissionStatus.init(rawValue:))
    }

    func record(_ permission: WatchedPermission, _ status: PermissionStatus) {
        UserDefaults.standard.set(status.rawValue, forKey: Self.prefix + permission.rawValue)
    }
}

/// A ledger in memory. Previews and tests.
@MainActor
final class InMemoryPermissionLedger: PermissionLedger {
    private(set) var values: [WatchedPermission: PermissionStatus]

    init(_ values: [WatchedPermission: PermissionStatus] = [:]) { self.values = values }

    func last(_ permission: WatchedPermission) -> PermissionStatus? { values[permission] }
    func record(_ permission: WatchedPermission, _ status: PermissionStatus) { values[permission] = status }
}

/// Every watched permission, read now.
protocol PermissionSnapshotting: Sendable {
    func read() async -> [WatchedPermission: PermissionStatus]
}

/// The snapshot from the readers the screens already use -- no second copy of any permission
/// check. Location reports the PERMISSION, not the device-wide switch: §24 is about the grant.
struct ReaderPermissionSnapshot: PermissionSnapshotting {
    let notifications: any NotificationAccessReading
    let location: any LocationAccessReading
    let media: any MediaAccessReading

    func read() async -> [WatchedPermission: PermissionStatus] {
        let capture = media.read()
        let notificationStatus = await notifications.read()
        let locationStatus = await location.read()
        return [
            .notifications: notificationStatus,
            .location: locationStatus.permission,
            .camera: capture.camera.permissionStatus,
            .microphone: capture.microphone.permissionStatus,
        ]
    }
}

extension MediaPermission {
    /// §24 for a capture permission: refused-but-askable and blocked are both a refusal.
    var permissionStatus: PermissionStatus {
        switch self {
        case .granted: return .granted
        case .notDetermined: return .notDetermined
        case .canAsk, .blocked: return .denied
        }
    }
}

/// One permission whose status moved since it was last recorded.
struct PermissionChange: Equatable, Sendable {
    let permission: WatchedPermission
    let from: PermissionStatus
    let to: PermissionStatus
}

/// The changes between what was recorded and what was just read, in `WatchedPermission` order. A
/// permission with nothing recorded is not a change: the first read is a starting point.
func permissionChanges(
    recorded: [WatchedPermission: PermissionStatus],
    now: [WatchedPermission: PermissionStatus]
) -> [PermissionChange] {
    WatchedPermission.allCases.compactMap { permission in
        guard let status = now[permission], let before = recorded[permission], before != status else { return nil }
        return PermissionChange(permission: permission, from: before, to: status)
    }
}

/// Which start of the app this is. The first a process sees is the launch; every later one is a
/// return to the foreground. Process-wide on purpose: `TutorialFlow`, and with it the reconciler,
/// is rebuilt on every new sign-in while the process -- and so the launch -- carries on.
@MainActor
enum ProcessLaunchMark {
    @MainActor private static var started = false

    /// True exactly once per process: on the first call.
    static func firstStart() -> Bool {
        if started { return false }
        started = true
        return true
    }
}

/// What the reconciler does with the push token. A seam, so a test can count the calls.
protocol PushTokenSyncing: Sendable {
    func register() async
    func unregister() async
}

/// The real one: `PushRegistration`'s two calls, results ignored -- never blocking anything.
struct PushRegistrationSync: PushTokenSyncing {
    var push = PushRegistration()

    func register() async { _ = await push.register() }
    func unregister() async { _ = await push.unregister() }
}

@MainActor
final class PermissionReconciler {
    private let snapshot: any PermissionSnapshotting
    private let ledger: any PermissionLedger
    private let push: (any PushTokenSyncing)?
    private let analytics: (any AnalyticsTracking)?
    private let firstStart: @MainActor () -> Bool

    init(
        snapshot: any PermissionSnapshotting,
        ledger: any PermissionLedger,
        push: (any PushTokenSyncing)?,
        analytics: (any AnalyticsTracking)?,
        firstStart: @escaping @MainActor () -> Bool = { ProcessLaunchMark.firstStart() }
    ) {
        self.snapshot = snapshot
        self.ledger = ledger
        self.push = push
        self.analytics = analytics
        self.firstStart = firstStart
    }

    /// The app launched, or came back from the background. `inFlow`: the user is still inside
    /// profile creation. Returns what changed, for a test to read.
    @discardableResult
    func onStart(inFlow: Bool) async -> [PermissionChange] {
        // Decided before the await, so two starts in quick succession cannot both be the launch.
        let detectedOn = firstStart() ? ProfileAnalytics.detectedOnLaunch : ProfileAnalytics.detectedOnForeground
        let now = await snapshot.read()
        var recorded: [WatchedPermission: PermissionStatus] = [:]
        for permission in WatchedPermission.allCases { recorded[permission] = ledger.last(permission) }
        let changes = permissionChanges(recorded: recorded, now: now)
        // Recorded before anything is reported: the ledger is what the next read compares against,
        // and nothing that follows may make the same change fire twice.
        for (permission, status) in now where ledger.last(permission) != status {
            ledger.record(permission, status)
        }
        for change in changes {
            analytics?.report(ProfileAnalytics.permissionStatusChanged(
                from: change.from, to: change.to, detectedOn: detectedOn, inFlow: inFlow,
                type: change.permission.trackingType))
            if change.permission == .notifications { syncPushToken(change.to) }
        }
        return changes
    }

    /// One of our own permission dialogs was answered: whatever it changed is recorded silently.
    func absorbOwnAnswer() async {
        let now = await snapshot.read()
        for (permission, status) in now { ledger.record(permission, status) }
    }

    /// A notifications grant registers the token; a refusal removes it. Background work, always.
    private func syncPushToken(_ to: PermissionStatus) {
        guard let push else { return }
        switch to {
        case .granted:
            Task { await push.register() }
        case .denied, .restricted:
            Task { await push.unregister() }
        case .notDetermined:
            break
        }
    }
}
