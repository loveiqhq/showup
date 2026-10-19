//
//  LocationModel.swift
//  ShowUp · every rule screen 12 has, testable with no device (SHOWUP-165)
//
//  The Swift twin of `profile/LocationViewModel.kt`; its header has the two arguments:
//
//    · THE POSITION ADVANCES AFTER THE ANSWER. `location` is recorded on a grant, on `Not now` and on
//      a silent skip — never on a denial, so a Deny + force-kill relaunches onto B. Best-effort: it
//      never holds the navigation.
//    · ONE ACT, ONE EVENT. The OS alert takes the scene inactive and back; while our own dialog is
//      up the foreground reconciler does nothing, so a grant from our dialog is reported once, as
//      `permission_result`, never also as a change noticed on return.
//
//  ONE DIFFERENCE FROM ANDROID, IN THE PLUMBING ONLY: here `allowPressed` awaits the answer. Android
//  routes it through the activity-result callback because an Activity can be rebuilt while the
//  dialog is up; a SwiftUI scene is not, so the await is safe and simpler.
//

import Foundation
import ShowUpAPI
import UIKit

/// What only the app can do.
@MainActor
protocol LocationHosting: AnyObject {
    /// Raises the OS dialog and waits. Returns whether location was granted, at any precision.
    func requestLocationPermission() async -> Bool
    /// Opens Settings. iOS can only open the app's own page — for B and C alike.
    func openSettings(for state: LocationState)
}

/// The app's host: CoreLocation and the Settings deep link.
@MainActor
final class AppLocationHost: LocationHosting {
    private let asker = CLLocationAsker()

    /// `nonisolated` for the reason `AppReachabilityHost` gives: built in a `View`'s stored-property
    /// initialiser, touching nothing isolated.
    nonisolated init() {}

    func requestLocationPermission() async -> Bool {
        await asker.request()
    }

    /// `UIApplication.openSettingsURLString` — our app page. The ticket's platform table: "iOS
    /// cannot open Location Services directly", which is why C's iOS copy names the path.
    func openSettings(for state: LocationState) {
        openAppSettings()
    }
}

/// What the screen draws.
struct LocationUiState: Equatable, Sendable {
    var state: LocationState = .ask
    /// Our dialog is up. The CTA stops answering; it is never disabled and never changes.
    var requesting = false
}

@MainActor
@Observable
final class LocationModel {
    private(set) var ui = LocationUiState()

    private let access: any LocationAccessReading
    private let positions: any FlowPositionReporting
    private let analytics: (any AnalyticsTracking)?
    private weak var host: (any LocationHosting)?

    /// Once per mount — never per state, never per redraw.
    private var announced = false
    private var prompted = false
    private var reportedRecovery: LocationUnavailableReason?
    /// An arrival's status read is in flight.
    private var arriving = false

    init(
        access: any LocationAccessReading = CLLocationAccess(),
        positions: any FlowPositionReporting,
        analytics: (any AnalyticsTracking)? = nil
    ) {
        self.access = access
        self.positions = positions
        self.analytics = analytics
    }

    func attach(_ host: any LocationHosting) { self.host = host }

    /// The matrix, read BEFORE the screen is pushed. A skip records the position: the step is
    /// passed, and without it a relaunch would land here only to skip again.
    func decideArrival() async -> LocationArrival {
        let arrival = locationArrival(await access.read())
        if arrival == .skip { record() }
        return arrival
    }

    /// The screen is on display. `screen_viewed` once per mount.
    ///
    /// `initial` IS ONLY THE FIRST FRAME — what the matrix decided before the push, drawn while the
    /// status is read again. What is announced is what that read says: a scene restored after the
    /// system ended the app comes back already active (no phase change follows), perhaps with a
    /// grant made in Settings meanwhile. A grant read here is the silent skip — nothing about this
    /// screen is reported, and the host advances. Android's `arrived` makes the same read.
    func arrived(initial: LocationState, referrer: ProfileScreen?, onAdvance: () -> Void) async {
        // ONE ARRIVAL AT A TIME. Two overlapping appearances would both read a grant and both take
        // the silent skip — recording twice and pushing Embrace 2 twice. `announced` cannot stop
        // that: only the `.show` branch sets it.
        guard !announced, !arriving else { return }
        arriving = true
        defer { arriving = false }
        ui = LocationUiState(state: initial)
        let arrival = locationArrival(await access.read())
        // Re-checked after the await: a second appearance may have announced meanwhile.
        guard !announced else { return }
        switch arrival {
        case .skip:
            record()
            onAdvance()
        case .show(let shown):
            announced = true
            prompted = false
            reportedRecovery = nil
            ui = LocationUiState(state: shown)
            analytics?.report(ProfileAnalytics.screenViewed(.location, referrer: referrer))
            announce(shown)
        }
    }

    /// `permission_prompted` on A, `permission_denied_recovery_shown` on B or C — once each.
    private func announce(_ shown: LocationState) {
        if let reason = shown.unavailableReason {
            guard reason != reportedRecovery else { return }
            reportedRecovery = reason
            analytics?.report(ProfileAnalytics.permissionDeniedRecoveryShown(reason: reason))
        } else if !prompted {
            prompted = true
            analytics?.report(ProfileAnalytics.permissionPrompted(type: ProfileAnalytics.permissionLocation))
        }
    }

    /// `Allow location access`: raises the OS dialog and nothing else, then handles the answer.
    func allowPressed(onGranted: () -> Void) async {
        // `announced` too: the outgoing screen still takes taps during its exit transition.
        guard announced, !ui.requesting, ui.state == .ask, let host else { return }
        ui.requesting = true
        analytics?.report(ProfileAnalytics.permissionOsSheetShown(type: ProfileAnalytics.permissionLocation))
        let granted = await host.requestLocationPermission()
        permissionAnswered(granted, onGranted: onGranted)
    }

    /// Granted -> record -> Embrace 2. Denied -> B IN PLACE, nothing recorded.
    func permissionAnswered(_ granted: Bool, onGranted: () -> Void) {
        guard ui.state == .ask else { return }
        analytics?.report(ProfileAnalytics.permissionResult(
            granted: granted, type: ProfileAnalytics.permissionLocation))
        if granted {
            ui.requesting = false
            record()
            onGranted()
        } else {
            ui = LocationUiState(state: .denied)
            announce(.denied)
        }
    }

    /// `Open Settings` on B or C. Opens the page and STAYS.
    func openSettingsPressed() {
        guard ui.state != .ask else { return }
        analytics?.report(ProfileAnalytics.permissionSettingsOpened(type: ProfileAnalytics.permissionLocation))
        host?.openSettings(for: ui.state)
    }

    /// `Not now — ask me when I search`, on B or C only. A has no equivalent.
    func notNowPressed(onAdvance: () -> Void) {
        guard announced, ui.state != .ask, !ui.requesting else { return }
        analytics?.report(ProfileAnalytics.locationSkipped())
        record()
        onAdvance()
    }

    /// Every foreground while the screen is up. A grant noticed here advances SILENTLY — no
    /// `permission_result`. Nothing at all while our own dialog is up.
    func foregrounded(onAdvance: () -> Void) async {
        guard announced, !ui.requesting else { return }
        let arrival = locationArrival(await access.read())
        // Re-checked after the await: the dialog may have been raised meanwhile.
        guard announced, !ui.requesting else { return }
        switch arrival {
        case .skip:
            record()
            onAdvance()
        case .show(let state):
            guard state != ui.state else { return }
            ui.state = state
            announce(state)
        }
    }

    /// The screen was left. The next push is a new mount and reports again.
    func left() { announced = false }

    private func record() {
        let positions = self.positions
        Task { _ = await positions.report(.location) }
    }
}
