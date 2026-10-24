//
//  CheckInPlace.swift
//  ShowUp · where the phone is at Find my date, and the district it names (SHOWUP-184)
//
//  The Swift twin of `checkin/CheckInPlace.kt` and `AndroidPlaceReader.kt`. Search measures every
//  distance from the Check-In's position, on both sides: a Check-In committed with no position is
//  offered to nobody and finds nobody. So `Find my date` sends the phone's place with the hours --
//  when location is allowed -- and the district the phone's OWN geocoder names (decided 21 Oct 2026:
//  CLGeocoder, free; the server never geocodes; no district, the distance alone).
//
//  The coordinates go to the server and nowhere else: never logged, never tracked, never shown.
//

import CoreLocation
import Foundation

/// The phone's position and, when its geocoder could name one, the district around it.
struct CheckInPlace: Equatable, Sendable {
    let latitude: Double
    let longitude: Double
    let district: String?
}

/// Reads `CheckInPlace` at commit. Nil without location access or with no fix in time.
@MainActor
protocol PlaceReading: AnyObject {
    func here() async -> CheckInPlace?
}

/// The server's column: `varchar(80)`, and it refuses a blank one.
let districtMax = 80

/// The district line from a geocoder's answer: `{sub-locality}, {locality}` -- `Neukölln, Berlin`, as
/// the spec sheet draws it -- or whichever of the two exists, never the same name twice. Nil when it
/// named neither. Cut to `districtMax`, at a word where it can be. Android's `districtOf`.
func districtOf(_ subLocality: String?, _ locality: String?) -> String? {
    let sub = subLocality?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    let city = locality?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    let line: String
    if let sub, let city, sub.caseInsensitiveCompare(city) != .orderedSame {
        line = "\(sub), \(city)"
    } else if let one = sub ?? city {
        line = one
    } else {
        return nil
    }
    if line.count <= districtMax { return line }
    // Too long for the column: the district alone if it fits, else cut at the last space.
    if let sub, sub.count <= districtMax { return sub }
    let cut = String(line.prefix(districtMax))
    let atWord = cut.lastIndex(of: " ").map { String(cut[..<$0]) } ?? cut
    return atWord.trimmingCharacters(in: CharacterSet(charactersIn: ", "))
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

/// The real reader: one fresh fix from CoreLocation, else a recent cached one; then CLGeocoder.
///
/// BOUNDED, both of them, as Android's are: `Find my date` waits on this behind its saving state, so a
/// fix that does not come in 4 seconds falls back to the cached position if it is under two hours
/// old, and a geocoder that does not answer in 3 leaves the district out.
///
/// ONE MANAGER, CREATED ON THE MAIN ACTOR ON FIRST USE, for the reason `CLLocationAsker` gives:
/// CoreLocation delivers delegate callbacks on the thread that created the manager.
@MainActor
final class CLPlaceReader: NSObject, PlaceReading, CLLocationManagerDelegate {
    private var manager: CLLocationManager?
    private var waiting: CheckedContinuation<CLLocation?, Never>?

    /// `nonisolated` for the reason `CLLocationAsker` gives: built in a stored-property initialiser.
    nonisolated override init() {
        super.init()
    }

    func here() async -> CheckInPlace? {
        let manager = self.manager ?? CLLocationManager()
        self.manager = manager
        manager.delegate = self
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: break
        default: return nil
        }
        let fresh = await fix(manager)
        guard let location = fresh ?? recent(manager.location) else { return nil }
        let district = await districtAt(location)
        return CheckInPlace(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                            district: district)
    }

    /// One fix, or nil after the timeout. The continuation is always resumed exactly once.
    private func fix(_ manager: CLLocationManager) async -> CLLocation? {
        await withCheckedContinuation { continuation in
            // NEVER STRAND A WAIT: a fix asked for while one is pending answers the first with nil.
            waiting?.resume(returning: nil)
            waiting = continuation
            manager.requestLocation()
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: Self.fixTimeout)
                self?.deliver(nil)
            }
        }
    }

    private func deliver(_ location: CLLocation?) {
        guard let continuation = waiting else { return }
        waiting = nil
        continuation.resume(returning: location)
    }

    /// The cached position, if it is recent enough to be where the user is meeting.
    private func recent(_ location: CLLocation?) -> CLLocation? {
        guard let location, -location.timestamp.timeIntervalSinceNow <= Self.lastKnownMaxAge else { return nil }
        return location
    }

    private func districtAt(_ location: CLLocation) async -> String? {
        let geocoder = CLGeocoder()
        let timer = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.geocodeTimeout)
            geocoder.cancelGeocode()
        }
        defer { timer.cancel() }
        return await withCheckedContinuation { continuation in
            // A cancelled lookup answers with an error, so the continuation resumes either way.
            geocoder.reverseGeocodeLocation(location) { placemarks, _ in
                let first = placemarks?.first
                continuation.resume(returning: districtOf(first?.subLocality, first?.locality))
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let last = locations.last
        // The manager was created on the main actor (see above), so its callbacks arrive there.
        MainActor.assumeIsolated { self.deliver(last) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {
        MainActor.assumeIsolated { self.deliver(nil) }
    }

    private static let fixTimeout: UInt64 = 4_000_000_000
    private static let geocodeTimeout: UInt64 = 3_000_000_000
    /// Two hours: still where the user is likely to be meeting, unlike yesterday's fix.
    private static let lastKnownMaxAge: TimeInterval = 2 * 60 * 60
}
