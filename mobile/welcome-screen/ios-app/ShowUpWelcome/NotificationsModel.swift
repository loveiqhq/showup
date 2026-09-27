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

    /// The screen was really shown. `screen_viewed`, AND NOTHING ELSE.
    ///
    /// `permission_prompted` used to fire here and no longer does. Registry 1.4.6 moved it:
    /// "From 25 Sep 2026 the notifications case is Stay reachable (Profile 10) ... Profile 09 NO
    /// LONGER fires it — 09 raises no sheet any more." A pre-permission event on a screen that
    /// pre-permissions nothing would double-count the ask against Stay reachable's own.
    func arrived() {
        guard !announced else { return }
        announced = true
        analytics?.report(ProfileAnalytics.screenViewed(.notifications, referrer: .media))
    }

    /// The CTA was pressed. IT ONLY NAVIGATES NOW.
    ///
    /// SHOWUP-163 took the request away: "Remove the OS notification request from 09. Its CTA only
    /// navigates to Stay reachable (10)." `permission_os_sheet_shown` and `permission_result` went
    /// with it, and the button was renamed `Continue` to match — a CTA reading `Enable
    /// notifications` that enables nothing is the kind of label a copy pass quietly restores.
    ///
    /// Returns whether this press is the one that navigates. A DOUBLE TAP MUST NOT PRODUCE TWO
    /// NAVIGATIONS, and nothing is reported for either press: the tap is not an event on this
    /// screen.
    ///
    /// `ask` and `push` ARE STILL HELD AND ARE NOW UNUSED BY EVERY PATH ON THIS SCREEN, which
    /// is a deliberate seam rather than dead weight: they keep the initialiser's shape so the
    /// host and the tests are unchanged, and `NotificationRulesTests` injects a counting `push`
    /// precisely to assert that NOTHING here reaches it any more. A parameter that exists to be
    /// proven unused is doing work.
    @discardableResult
    func continuePressed() -> Bool {
        guard !sheetUp else { return false }
        sheetUp = true
        return true
    }

    // `skipped(_:)` IS GONE, AND ITS DELETION IS THE POINT (SHOWUP-163).
    //
    // It existed because `register()` had exactly one call site — the grant callback on this
    // screen — so a user who never saw this screen was a device the backend had no token for.
    // SHOWUP-163 routes EVERY user through Stay reachable (10), whose `Save preferences` is the
    // only way off it and which registers when push is on. The skipped user now registers there,
    // one screen later and tied to the consent rather than to a screen they did not see.
    //
    // Keeping both would have been a double registration on every Android <= 12 device and every
    // restored install — and worse on the path that matters: this one registered on a GRANTED
    // status regardless of consent, so a user who reached 10 and switched push OFF would already
    // have had a token filed. Registering a device for push right after the user declines it is
    // the failure that version had, and it was invisible because a second register succeeds.
    //
    // Android deleted it with the same reasoning. `push` is still held here for the initialiser's
    // shape and for the tests that assert nothing registers from this screen any more.

    /// Read on every foreground while the screen is mounted.
    ///
    /// NO LONGER USED FOR NAVIGATION, and kept because the question is still a real one.
    ///
    /// It existed because this screen's only button raised a dialog the OS shows once per install:
    /// a status that became determined while the screen was mounted left a dead CTA, so the screen
    /// had to advance by itself. It raises nothing now, so `Continue` always works and there is no
    /// dead state to escape. The re-read that matters moved to 10, where it drives a toggle.
    func statusIsNowDetermined() async -> Bool {
        await !shouldShowAsk(access.read())
    }
}
