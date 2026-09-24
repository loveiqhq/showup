//
//  NotificationsModel.swift
//  ShowUpWelcome · every rule the notification ask has, testable with no device (SHOWUP-162)
//
//  The Swift half of `NotificationsViewModel.kt`, and it exists for the same reason: granting the
//  permission kicks off a push-token registration, which is asynchronous work that must outlive a
//  redraw — the one condition under which a screen in this project gets a model at all.
//
//  Having it is what turns four tracking events, an ordering rule, a "not tappable twice" rule and
//  a foreground re-read into plain functions that `NotificationRulesTests` can prove with no OS
//  sheet and no permission.
//
//  WHAT IT DELIBERATELY DOES NOT DO.
//
//  IT DOES NOT DECIDE WHETHER THE SCREEN IS SHOWN. That is `shouldShowAsk`, which the host calls
//  BEFORE pushing the screen — the skip must be settled before mount so the user never sees it
//  appear and navigate away. A model that answered the question would already be too late.
//
//  IT DOES NOT FIRE `permission_status_changed`. That belongs to the shared reconciler and must
//  never double up with `permission_result` for one act; the foreground re-read here is the
//  navigation half only.
//

import Foundation
import Observation

@MainActor
@Observable
final class NotificationsModel {
    private let access: any NotificationAccessReading
    private let ask: any NotificationAsking
    private let push: PushRegistering
    private let analytics: (any AnalyticsTracking)?

    /// True from the moment the sheet is raised.
    ///
    /// NEVER LOWERED, because every path out of the sheet leaves this screen. The CTA is never
    /// disabled and never changes appearance — it simply stops answering, which is what "not
    /// tappable twice" means when a second request is a silent platform no-op.
    private(set) var sheetUp = false

    /// Guards `permission_prompted` against a redraw firing it twice.
    private var announced = false

    /// Guards the skip path's registration against running twice.
    private var registeredOnSkip = false

    init(
        access: any NotificationAccessReading = UNNotificationAccess(),
        ask: any NotificationAsking = UNNotificationAsk(),
        push: PushRegistering = PushRegistration(),
        analytics: (any AnalyticsTracking)? = nil
    ) {
        self.access = access
        self.ask = ask
        self.push = push
        self.analytics = analytics
    }

    /// The screen was really shown.
    ///
    /// `permission_prompted` is OUR pre-permission surface, and events.json is explicit that it
    /// "does not fire when the screen is skipped because the OS status is already determined". The
    /// skip case never constructs this, so the guarantee is structural rather than a condition.
    func arrived() {
        guard !announced else { return }
        announced = true
        analytics?.report(ProfileAnalytics.screenViewed(.notifications, referrer: .media))
        analytics?.report(ProfileAnalytics.permissionPrompted())
    }

    /// The CTA was pressed: raises the sheet, reports the result, registers on a grant.
    ///
    /// A SECOND PRESS DOES NOTHING AND REPORTS NOTHING. A tap that raises no sheet is not a sheet
    /// being shown, and counting it would inflate the denominator of the grant rate with taps that
    /// never reached the platform.
    ///
    /// BOTH OUTCOMES ADVANCE — the caller navigates either way and the user never lands back here.
    func enablePressed() async {
        guard !sheetUp else { return }
        sheetUp = true
        analytics?.report(ProfileAnalytics.permissionOsSheetShown())

        let granted = await ask.request()
        analytics?.report(ProfileAnalytics.permissionResult(granted: granted))
        if granted {
            // GRANTING AND NEVER REGISTERING is a silent failure that looks exactly like success
            // on this screen. Fire and forget: it must not hold the user here.
            Task { _ = await push.register() }
        }
    }

    /// The screen was SKIPPED, and the skipped user still needs a token.
    ///
    /// The Swift half of the Android <= 12 case. There is no API-level skip on iOS, so what
    /// reaches here is the guard case only — a status already determined, from a restored backup
    /// or an app killed mid-sheet. An APNs token does not survive a restore, so a user who comes
    /// back already granted still needs to register, and the only thing that registered was a
    /// callback on a screen they are about to be skipped past.
    ///
    /// FIRES NOTHING. `permission_prompted` is our own surface and does not fire on a skip; there
    /// is no "ask skipped" event and the ticket forbids inventing one at a call site.
    func skipped(_ status: NotificationPermission) {
        guard status == .granted, !registeredOnSkip else { return }
        registeredOnSkip = true
        Task { _ = await push.register() }
    }

    /// Read on every foreground while the screen is mounted.
    ///
    /// THE SCREEN IS NEVER A TERMINAL STATE. A user who backgrounds the sheet, turns notifications
    /// on in Settings by hand and comes back would otherwise face a button that can raise nothing
    /// — the dialog is shown once per install — on a screen with no skip, no back and no close.
    func statusIsNowDetermined() async -> Bool {
        await !shouldShowAsk(access.read())
    }
}
