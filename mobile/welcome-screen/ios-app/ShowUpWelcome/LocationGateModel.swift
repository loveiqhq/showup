//
//  LocationGateModel.swift
//  ShowUp · Search 04, the location gate's rules (SHOWUP-188)
//
//  The Swift twin of `LocationGateViewModel.kt`; its header has the full argument. The gate is
//  Profile 12's screen in its app context, but its RULES are not Profile 12's: no flow position, no
//  `Not now`, and `location_gate_shown` instead of `permission_prompted` and
//  `permission_denied_recovery_shown`. It reuses everything Profile 12 built that is not a rule: the
//  status reader, the arrival matrix (which IS the ticket's state table), the OS request and the
//  Settings link.
//
//  DECIDED BEFORE THE SEARCH TAB RENDERS. The status is read at launch and on every return to the
//  foreground, whatever screen is up. iOS reads it off the main thread, so until the FIRST read lands
//  `known` is false and the Search tab draws neither Search nor the gate -- never a frame of empty
//  Search, never a frame of the wrong state.
//

import Foundation

/// What the Search tab, and the gate pushed over a Likes profile, draw.
struct LocationGateUiState: Equatable, Sendable {
    /// The first status read has landed. Until then the Search tab draws nothing of its own.
    var known = false
    /// Nil: location is usable and Search shows. Otherwise the state the gate draws (A, B or C).
    var blocked: LocationState?
    /// Our dialog is up. The CTA is never disabled; it stops answering, so it cannot fire twice.
    var requesting = false
    /// D: `Confirm date` on a profile opened from the Likes tab found location unavailable, and the
    /// gate is pushed over that profile.
    var overProfile = false
}

@MainActor
@Observable
final class LocationGateModel {
    private(set) var ui = LocationGateUiState()

    private let access: any LocationAccessReading
    private let analytics: (any AnalyticsTracking)?
    private weak var host: (any LocationHosting)?

    /// The gate is on screen: `screen_viewed` once per mount, never per state.
    @ObservationIgnored private var mounted = false
    /// The state last reported while mounted, so only a CHANGE reports again.
    @ObservationIgnored private var reported: LocationState?
    /// The gate lifted on the Search tab while it was up: the Search that replaces it was reached
    /// FROM the gate, and names `search_location_gate` as its referrer.
    @ObservationIgnored private var liftedOnTab = false

    init(access: any LocationAccessReading = CLLocationAccess(), analytics: (any AnalyticsTracking)? = nil) {
        self.access = access
        self.analytics = analytics
    }

    func attach(_ host: any LocationHosting) { self.host = host }

    /// The matrix, now: the state to block with, or nil when location is usable.
    private func read() async -> LocationState? {
        switch locationArrival(await access.read()) {
        case .skip: return nil
        case .show(let state): return state
        }
    }

    /// At launch, on every foreground and on every arrival on the Search tab: the status read again.
    /// Usable -> the gate lifts (D: back to the profile). Blocked for a new reason while up -> the
    /// state swaps in place and is reported. NOTHING while our own dialog is up.
    func refresh() async {
        guard !ui.requesting else { return }
        let now = await read()
        // Re-checked after the read: the dialog may have been raised meanwhile.
        guard !ui.requesting else { return }
        apply(now)
    }

    private func apply(_ now: LocationState?) {
        guard let now else {
            if ui.blocked != nil, mounted, !ui.overProfile { liftedOnTab = true }
            ui = LocationGateUiState(known: true)
            return
        }
        ui.known = true
        ui.blocked = now
        if mounted, now != reported { announce(now) }
    }

    private func announce(_ shown: LocationState) {
        reported = shown
        analytics?.report(ProfileAnalytics.locationGateShown(reason: shown.gateReason))
    }

    /// The gate is on display -- the Search tab blocked (A-C), or pushed over a Likes profile (D).
    func mounted(referrer: ProfileScreen?) {
        guard let shown = ui.blocked, !mounted else { return }
        mounted = true
        liftedOnTab = false
        analytics?.report(ProfileAnalytics.screenViewed(.searchLocationGate, referrer: referrer))
        announce(shown)
    }

    /// The gate left the screen. The next showing reports again.
    func left() {
        mounted = false
        reported = nil
    }

    /// Asked once by the Search screen that replaces the gate: was it reached from the gate?
    func takeLift() -> Bool {
        defer { liftedOnTab = false }
        return liftedOnTab
    }

    /// `Allow location access` on A: raises the OS dialog AND NOTHING ELSE, then takes its answer.
    /// Granted at any precision (Allow Once included) -> the gate lifts and Search loads in place;
    /// D -> back to the profile. Denied -> B IN PLACE, no navigation.
    func allowPressed() async {
        guard mounted, !ui.requesting, ui.blocked == .ask, let host else { return }
        ui.requesting = true
        analytics?.report(ProfileAnalytics.permissionOsSheetShown(
            type: ProfileAnalytics.permissionLocation, screen: .searchLocationGate))
        let granted = await host.requestLocationPermission()
        analytics?.report(ProfileAnalytics.permissionResult(
            granted: granted, type: ProfileAnalytics.permissionLocation, screen: .searchLocationGate))
        // Read rather than assumed: a grant with Location Services off still blocks (C).
        let now: LocationState? = granted ? await read() : .denied
        ui.requesting = false
        apply(now)
    }

    /// `Open Settings` on B or C: our app page (iOS cannot open Location Services), and the gate STAYS.
    func openSettingsPressed() {
        guard mounted, let shown = ui.blocked, shown != .ask else { return }
        analytics?.report(ProfileAnalytics.permissionSettingsOpened(
            type: ProfileAnalytics.permissionLocation, screen: .searchLocationGate))
        host?.openSettings(for: shown)
    }

    /// D: `Confirm date` on a profile opened from the Likes tab, checked BEFORE Be sure opens. True
    /// when location is unavailable and the gate goes over the profile instead -- nothing is sent.
    func confirmNeedsLocation() async -> Bool {
        await refresh()
        guard ui.blocked != nil else { return false }
        ui.overProfile = true
        return true
    }

    /// D's back pill or swipe-back: back to the profile, the date not confirmed.
    func backToProfile() {
        ui.overProfile = false
    }
}
