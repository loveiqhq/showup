// LocationFixReader.swift
// ShowUp · one fresh position for the arrival check-in (SHOWUP-195). Android's `LocationFixReader.kt`.
//
// FRESH OR NOTHING. A check-in is evidence that flow 10 compares at 150 m, so there is no cached fallback here
// (unlike Find my date's `CLPlaceReader`): an old position would say the person was somewhere they were not.
// The 10 s bound is the model's, not this class's.
//
// Nothing here logs a coordinate.

import CoreLocation
import Foundation

/// A position. `accuracyM` is nil when the platform reported none.
struct LocationFix: Equatable, Sendable {
    let latitude: Double
    let longitude: Double
    let accuracyM: Double?
}

protocol LocationFixReading: Sendable {
    /// One fresh fix, or nil: no permission, services off, or no answer. Returns promptly when cancelled.
    func fix() async -> LocationFix?
}

/// The real reader: `requestLocation()` at the best accuracy, nothing cached.
///
/// ONE MANAGER, CREATED ON THE MAIN ACTOR ON FIRST USE, for the reason `CLLocationAsker` gives: CoreLocation
/// delivers delegate callbacks on the thread that created the manager. The continuation is resumed exactly once.
@MainActor
final class CLLocationFixReader: NSObject, LocationFixReading, CLLocationManagerDelegate {
    private var manager: CLLocationManager?
    private var waiting: CheckedContinuation<LocationFix?, Never>?

    /// `nonisolated` for the reason `CLLocationAsker` gives: built in a stored-property initialiser.
    nonisolated override init() {
        super.init()
    }

    func fix() async -> LocationFix? {
        let manager = self.manager ?? CLLocationManager()
        self.manager = manager
        manager.delegate = self
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: break
        default: return nil
        }
        manager.desiredAccuracy = kCLLocationAccuracyBest
        return await withTaskCancellationHandler {
            await withCheckedContinuation { (continuation: CheckedContinuation<LocationFix?, Never>) in
                // Cancelled before it began: nothing was asked, nothing is left waiting.
                if Task.isCancelled {
                    continuation.resume(returning: nil)
                    return
                }
                // NEVER STRAND A WAIT: a fix asked for while one is pending answers the first with nil.
                waiting?.resume(returning: nil)
                waiting = continuation
                manager.requestLocation()
            }
        } onCancel: {
            Task { @MainActor in self.deliver(nil) }
        }
    }

    private func deliver(_ fix: LocationFix?) {
        guard let continuation = waiting else { return }
        waiting = nil
        continuation.resume(returning: fix)
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let fix = locations.last.map {
            LocationFix(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude,
                        accuracyM: $0.horizontalAccuracy >= 0 ? $0.horizontalAccuracy : nil)
        }
        // The manager was created on the main actor (see above), so its callbacks arrive there.
        MainActor.assumeIsolated { self.deliver(fix) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {
        MainActor.assumeIsolated { self.deliver(nil) }
    }
}
