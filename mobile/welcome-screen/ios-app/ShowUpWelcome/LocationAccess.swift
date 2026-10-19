//
//  LocationAccess.swift
//  ShowUp · the location status, and the arrival matrix that decides what screen 12 shows
//  (SHOWUP-165)
//
//  The Swift twin of `profile/LocationAccess.kt`. Two facts, not one: the permission, in §24's
//  vocabulary (`PermissionStatus`, shared with the notification ask), and the phone's Location
//  Services switch, which "is not a permission status; it is the reason C exists".
//
//  THE MATRIX IS DECIDED BEFORE THE SCREEN IS PUSHED, and re-run on every foreground while it is up:
//
//      Location Services off, any permission    C
//      not determined                           A   (also the relaunch after a kill mid-dialog)
//      granted (incl. Allow Once)                skipped silently — straight to Embrace 2
//      denied / restricted                      B
//

import CoreLocation
import Foundation

/// What screen 12 can show. One component, three states.
enum LocationState: String, Sendable {
    /// A — not determined, Location Services on. One CTA: `Allow location access`.
    case ask
    /// B — denied or restricted. `Open Settings` + `Not now — ask me when I search`.
    case denied
    /// C — Location Services off for the whole phone. Same layout as B; only the copy differs.
    case servicesOff
}

/// `enums.json` §26 `location_unavailable_reason`. New in registry 1.4.7.
enum LocationUnavailableReason: String, Sendable {
    case denied
    case servicesOff = "services_off"
}

/// The two facts the matrix reads.
struct LocationStatus: Equatable, Sendable {
    let permission: PermissionStatus
    /// `CLLocationManager.locationServicesEnabled()` — the phone-wide switch.
    let servicesOn: Bool
}

/// A state to show, or a silent skip to Embrace 2.
enum LocationArrival: Equatable, Sendable {
    case show(LocationState)
    case skip
}

/// The arrival matrix, and the foreground reconciler — one rule for both.
func locationArrival(_ status: LocationStatus) -> LocationArrival {
    guard status.servicesOn else { return .show(.servicesOff) }
    switch status.permission {
    case .granted: return .skip
    case .notDetermined: return .show(.ask)
    // Restricted is treated as denied — the ticket and §24 both say so.
    case .denied, .restricted: return .show(.denied)
    }
}

extension LocationState {
    /// The §26 reason this state reports, or nil for the ask.
    var unavailableReason: LocationUnavailableReason? {
        switch self {
        case .ask: return nil
        case .denied: return .denied
        case .servicesOff: return .servicesOff
        }
    }
}

/// CoreLocation's answer in §24's words. While-in-use and Always are both granted — Always is
/// never asked for here, and if a user granted it elsewhere it is still a grant. Allow Once reads as
/// `authorizedWhenInUse` for the session, which is exactly what the ticket counts as a grant.
func locationPermission(for status: CLAuthorizationStatus) -> PermissionStatus {
    switch status {
    case .notDetermined: return .notDetermined
    case .denied: return .denied
    case .restricted: return .restricted
    case .authorizedAlways, .authorizedWhenInUse: return .granted
    @unknown default: return .denied
    }
}

/// Reads the status. Re-read on every foreground — never remembered.
protocol LocationAccessReading: Sendable {
    func read() async -> LocationStatus
}

/// The real reader.
///
/// OFF THE MAIN THREAD. `locationServicesEnabled()` can block, and Apple's own runtime check flags
/// it on the main thread as a cause of UI unresponsiveness. A detached task keeps the arrival
/// decision from stalling the transition into the screen.
struct CLLocationAccess: LocationAccessReading {
    func read() async -> LocationStatus {
        await Task.detached(priority: .userInitiated) {
            let servicesOn = CLLocationManager.locationServicesEnabled()
            let status = CLLocationManager().authorizationStatus
            return LocationStatus(permission: locationPermission(for: status), servicesOn: servicesOn)
        }.value
    }
}

/// Answers from memory. Previews, and every test about the rules rather than the device.
struct FixedLocationAccess: LocationAccessReading {
    let status: LocationStatus

    init(_ permission: PermissionStatus = .notDetermined, servicesOn: Bool = true) {
        status = LocationStatus(permission: permission, servicesOn: servicesOn)
    }

    func read() async -> LocationStatus { status }
}

/// Raises the OS location dialog and waits for the answer.
///
/// WHILE-IN-USE ONLY — `requestWhenInUseAuthorization`, never `requestAlwaysAuthorization`, anywhere
/// in this flow. The dialog's copy, buttons, precision toggle and map are the OS's; the one string
/// that is ours is `NSLocationWhenInUseUsageDescription` in Info.plist, without which iOS terminates
/// the app on this call.
///
/// ONE MANAGER, CREATED ON THE MAIN ACTOR ON FIRST USE. CoreLocation delivers delegate callbacks on
/// the thread that created the manager, so it must be the main thread; creating it lazily inside
/// the main-actor method is what guarantees that, whatever context built this object.
@MainActor
final class CLLocationAsker: NSObject, CLLocationManagerDelegate {
    private var manager: CLLocationManager?
    private var waiting: CheckedContinuation<Bool, Never>?

    /// `nonisolated` because the app shell builds this in a stored-property initialiser on a
    /// `View`, which is not a main-actor context. It touches no isolated state, so the claim is
    /// true rather than a way to silence the compiler — the same reasoning as AppReachabilityHost.
    nonisolated override init() {
        super.init()
    }

    /// Returns whether location was granted, at any precision.
    func request() async -> Bool {
        let manager = self.manager ?? CLLocationManager()
        self.manager = manager
        manager.delegate = self
        // Already answered: nothing to raise. The screen is never shown in that case — the matrix
        // skips it — so this only guards a race with Settings.
        let current = manager.authorizationStatus
        if current != .notDetermined {
            return locationPermission(for: current) == .granted
        }
        return await withCheckedContinuation { continuation in
            // NEVER STRAND A WAIT. A request while one is pending would otherwise drop the first
            // continuation unresumed, and the press that made it would wait for ever.
            waiting?.resume(returning: false)
            waiting = continuation
            manager.requestWhenInUseAuthorization()
        }
    }

    /// CoreLocation calls this on creation as well as on every change; only a DETERMINED status
    /// while a request is waiting is an answer.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        // The manager was created on the main actor (see above), so its callbacks arrive there.
        MainActor.assumeIsolated { self.answer(status) }
    }

    private func answer(_ status: CLAuthorizationStatus) {
        guard status != .notDetermined, let continuation = waiting else { return }
        waiting = nil
        continuation.resume(returning: locationPermission(for: status) == .granted)
    }
}
