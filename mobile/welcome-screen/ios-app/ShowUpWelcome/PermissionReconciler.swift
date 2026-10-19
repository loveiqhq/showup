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
//  "UPDATE THE SERVER" IS TWO THINGS: every watched status goes to `PUT /me/permissions`, whose
//  push path then skips a phone with notifications off, and a notifications change registers or
//  removes the push token. The whole snapshot is sent whenever it differs from what the server last
//  took in this process -- once at launch, after any change (our own dialogs' answers included:
//  silent for the analytics, not for the server), and again for a new sign-in. A failed send is
//  tried again at the next start.
//

import Foundation
import ShowUpAPI

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

/// What the reconciler tells the server: every watched permission, as just read. A seam, so a test
/// can see what was sent. True when the server took it.
protocol PermissionStatusSyncing: Sendable {
    func save(_ statuses: [WatchedPermission: PermissionStatus]) async -> Bool
}

/// The real one: `PUT /me/permissions`. Signed out, it sends nothing -- there is no account to store
/// it against, and the sign-in sends it instead.
struct APIPermissionStatusSync: PermissionStatusSyncing {
    func save(_ statuses: [WatchedPermission: PermissionStatus]) async -> Bool {
        guard await APIAccess.tokens.accessToken() != nil else { return false }
        let items = WatchedPermission.allCases.compactMap { permission in
            statuses[permission].map { status in
                Components.Schemas.PermissionStatusItemDto(
                    permission: Components.Schemas.PermissionStatusItemDto.permissionPayload(value1: permission.wire),
                    status: Components.Schemas.PermissionStatusItemDto.statusPayload(value1: status.wire))
            }
        }
        do {
            let response = try await APIAccess.client.client.savePermissionStatuses(
                body: .json(.init(platform: .ios, statuses: items))
            )
            // The operation declares 204, 400, 401 and 500.
            switch response {
            case .noContent:
                return true
            default:
                return false
            }
        } catch {
            return false
        }
    }
}

// Spelled out rather than matched on the strings: a value added on either side fails to compile
// here instead of being sent as something the server refuses.
private extension WatchedPermission {
    var wire: Components.Schemas.DevicePermission {
        switch self {
        case .notifications: return .notifications
        case .location: return .location
        case .camera: return .camera
        case .microphone: return .microphone
        }
    }
}

private extension PermissionStatus {
    var wire: Components.Schemas.DevicePermissionStatus {
        switch self {
        case .notDetermined: return .not_determined
        case .granted: return .granted
        case .denied: return .denied
        case .restricted: return .restricted
        }
    }
}

@MainActor
final class PermissionReconciler {
    private let snapshot: any PermissionSnapshotting
    private let ledger: any PermissionLedger
    private let push: (any PushTokenSyncing)?
    private let server: (any PermissionStatusSyncing)?
    private let analytics: (any AnalyticsTracking)?
    private let firstStart: @MainActor () -> Bool

    /// What the server last took, in this process. Nil: nothing yet, so the next read is sent.
    private var lastSent: [WatchedPermission: PermissionStatus]?
    /// The send in flight. Each one waits for the one before, so an older read can never land on
    /// the server after a newer one.
    private var sending: Task<Void, Never>?
    /// Which sign-in a send belongs to. A send for an earlier one is skipped, and its answer ignored.
    private var session = 0

    init(
        snapshot: any PermissionSnapshotting,
        ledger: any PermissionLedger,
        push: (any PushTokenSyncing)?,
        server: (any PermissionStatusSyncing)?,
        analytics: (any AnalyticsTracking)?,
        firstStart: @escaping @MainActor () -> Bool = { ProcessLaunchMark.firstStart() }
    ) {
        self.snapshot = snapshot
        self.ledger = ledger
        self.push = push
        self.server = server
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
        sendIfMoved(now)
        return changes
    }

    /// One of our own permission dialogs was answered: whatever it changed is recorded silently.
    /// The server is still told: what it stores is the status, however it came about.
    func absorbOwnAnswer() async {
        let now = await snapshot.read()
        for (permission, status) in now { ledger.record(permission, status) }
        sendIfMoved(now)
    }

    /// A session began, possibly a different account's. What the server took before was for the
    /// account before, so the statuses go again -- and a send still on its way no longer counts.
    func accountChanged() async {
        session += 1
        lastSent = nil
        sendIfMoved(await snapshot.read())
    }

    /// Waits for every send started so far. For a test.
    func settled() async {
        await sending?.value
    }

    private func sendIfMoved(_ now: [WatchedPermission: PermissionStatus]) {
        guard let server, now != lastSent else { return }
        let forSession = session
        let before = sending
        sending = Task {
            await before?.value
            // Queued for the account before, or already carried by the send ahead of this one.
            guard forSession == session, now != lastSent else { return }
            // The session can change while the call is out; then what it took was the old one's.
            if await server.save(now), forSession == session { lastSent = now }
        }
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
