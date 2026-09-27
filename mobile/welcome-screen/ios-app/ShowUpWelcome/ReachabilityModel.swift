//
//  ReachabilityModel.swift
//  ShowUpWelcome · Profile creation 10 — Stay reachable: the state, the vocabulary, the rules
//  (SHOWUP-163)
//
//  The Swift half of `ReachabilityModel.kt` and `ReachabilityViewModel.kt`, which are two files on
//  Android and one here because Swift has no reason to split a value type from the object that
//  owns it.
//
//  ───────────────────────────────────────────────────────────────────────────
//  TWO JOBS, AND ONLY ONE OF THEM HAS ANYTHING BEHIND IT
//  ───────────────────────────────────────────────────────────────────────────
//
//  1. PUSH is the one live channel. A switch, ON by default, and the only consent this screen
//     writes. Turning it off is a deliberate act that goes through a confirmation.
//
//  2. THREE INTEREST CHECKBOXES are a DEMAND TEST for channels that do not exist. They record
//     interest and do nothing else: no delivery, no consent record, no phone number, no dialog,
//     no server write. The ticket calls them "the most likely thing in this ticket to be
//     over-built", which is why the type below cannot express anything more than a set membership.
//
//  ───────────────────────────────────────────────────────────────────────────
//  INTEREST IS NOT CONSENT, AND THE TYPES SAY SO
//  ───────────────────────────────────────────────────────────────────────────
//
//  §25 `interest_channel` is a SEPARATE VOCABULARY from §8 `channel`, and the registry says why in
//  as many words: "deliberately a separate vocabulary from §8 channel so an interest can never be
//  read as a consent". `InterestChannel` is therefore its own enum rather than a subset of the
//  consent channels, and nothing here can be handed to `consentChanged` — that builder takes a §8
//  string and this one has no member in it.
//
//  The ticket's hardest tracking rule falls straight out of that: **no `consent_changed` for a
//  checkbox. Ever.**
//

import Foundation
import Observation

/// The three channels the demand test asks about. §25, MVP only.
///
/// `aiCall` is the phone call from our AI assistant — which becomes §8 `call` if it is ever built,
/// and is deliberately spelled differently here so the two cannot be confused in a query.
enum InterestChannel: String, CaseIterable, Sendable {
    case aiCall = "ai_call"
    case whatsApp = "whatsapp"
    case sms

    var trackingValue: String { rawValue }

    /// In the order the reference file lists them, which is the order they are drawn.
    ///
    /// `CaseIterable`'s `allCases` is already declaration order, so this is a name for the fact
    /// rather than a second list — one list that can drift from the drawing is enough.
    static let order: [InterestChannel] = allCases
}

/// Which dialog the deactivation confirm is, if it is up at all.
///
/// The two differ by ONE LABEL and nothing else, which is exactly why they are one type with a
/// case rather than two dialogs: a second copy would be a second place for the body copy to drift.
enum DeactivationPrompt: Sendable {
    /// State C. The user moved the switch off themselves, so the primary offers to undo that:
    /// `Keep active`.
    case userTurnedItOff

    /// State D. The OS dialog came back with "Don't allow", so keeping it active is not something
    /// this app can do — only Settings can. The primary reads `Open Settings`.
    ///
    /// DECIDED 25 September 2026, and it is the standing one-label-per-behaviour rule: the button
    /// opens Settings, so it must not say `Keep active`, which would promise something it cannot
    /// deliver.
    case afterOsDenial
}

/// Everything the screen renders.
///
/// A value, hoisted — but this screen DOES own asynchronous work (the OS dialog, push
/// registration, and a consent write that must be confirmed before the flow advances), so the
/// owner is an `@Observable` rather than a `@State` at the host. That is `CLAUDE.md`'s rule
/// applied rather than abandoned: photos and prompts hold no async work and stay hoisted values.
struct ReachabilityState: Equatable, Sendable {
    /// The switch. TRUE BY DEFAULT — an opt-out, not an opt-in.
    ///
    /// The ticket is explicit: "Push defaults ON. It's an opt-out, not an opt-in: the user
    /// switches it off actively, through the confirm dialog."
    var pushOn = true

    /// The demand test. Empty by default, and the only thing that ever happens to it is a flip.
    var interest: Set<InterestChannel> = []

    /// The deactivation confirm, or nil. Non-nil means the dialog is up AND the switch has not
    /// moved yet — turning off is not applied until it is confirmed.
    var prompt: DeactivationPrompt?

    /// True from the tap on Save preferences until navigation or failure. The CTA is never
    /// disabled; it simply stops answering, which is the same rule 09 and the media screen use.
    var saving = false

    /// The inline error above the CTA. NOT OPTIMISTIC: the flow advances only once the server has
    /// confirmed the consent, so a failure leaves the user here with their choices.
    var saveFailed = false

    func isInterested(_ channel: InterestChannel) -> Bool { interest.contains(channel) }

    /// The interest set as the registry wants it on `reachability_saved` — the §25 values of the
    /// checked boxes, in the drawn order so two identical states never serialise two ways.
    var interestValues: [String] {
        InterestChannel.order.filter { interest.contains($0) }.map(\.trackingValue)
    }
}

/// The two things only the app shell can do.
///
/// Raising the OS dialog needs a real `UNUserNotificationCenter`, and leaving for Settings needs
/// `UIApplication`. Everything else on this screen is in the model, which is what lets every rule
/// be tested with no device.
@MainActor
protocol ReachabilityHosting: AnyObject {
    /// Raises the OS notification dialog and WAITS. Returns whether it was granted.
    func requestNotificationPermission() async -> Bool

    /// Opens Settings. On iOS there is never a re-prompt — the dialog is once per install — so
    /// this is always the Settings deep link, unlike Android where it may be an in-app re-ask.
    func openSettingsOrReprompt()
}

/// Where the push consent is written.
///
/// SEPARATE FROM THE NOTIFICATION-PREFERENCES ENDPOINT, which carries CATEGORIES (essential,
/// engagement, marketing) rather than channel consent. There is no endpoint for this yet — see
/// `NoConsentBackend` — and the seam exists so that the "not optimistic" rule is expressed in the
/// model rather than becoming true only once a backend appears.
protocol ReachabilityRepository: Sendable {
    /// Returns whether the server CONFIRMED the write. `false` keeps the user on the screen.
    func savePushConsent(on: Bool) async -> Bool
}

/// The stand-in until the consent endpoint exists.
///
/// RETURNS TRUE, and that is a decision rather than a stub's laziness: with no endpoint there is
/// nothing that can fail, and returning false would strand every user on this screen. What it
/// must never become is an endpoint that always reports success — the moment there is a real one,
/// this type goes and the failure path starts meaning something.
struct NoConsentBackend: ReachabilityRepository {
    func savePushConsent(on: Bool) async -> Bool { true }
}

@MainActor
@Observable
final class ReachabilityModel {
    private(set) var state = ReachabilityState()

    private let access: any NotificationAccessReading
    private let push: PushRegistering
    private let consent: any ReachabilityRepository
    private let analytics: (any AnalyticsTracking)?
    private weak var host: (any ReachabilityHosting)?

    /// Guards the screenview and the pre-permission event against a redraw firing them twice.
    private var announced = false

    init(
        access: any NotificationAccessReading = UNNotificationAccess(),
        push: PushRegistering = PushRegistration(),
        consent: any ReachabilityRepository = NoConsentBackend(),
        analytics: (any AnalyticsTracking)? = nil
    ) {
        self.access = access
        self.push = push
        self.consent = consent
        self.analytics = analytics
    }

    func attach(_ host: any ReachabilityHosting) { self.host = host }

    // MARK: arrival

    /// The screen was shown: read the status, set the toggle, report.
    ///
    /// `permission_prompted` fires ONLY WHEN THE STATUS IS NOT DETERMINED. The registry qualifies
    /// it that way — this is our own pre-permission surface, and somebody whose answer is already
    /// on file is not being pre-permissioned. It moved here from 09 on 25 September 2026.
    func arrived(referrer: ProfileScreen? = .notifications) async {
        let status = await access.read()
        // An opt-out cannot promise what the OS has refused. The arrival matrix: denied and
        // restricted show the toggle OFF, and switching it on opens Settings.
        state.pushOn = status != .denied && status != .restricted

        guard !announced else { return }
        announced = true
        analytics?.report(ProfileAnalytics.screenViewed(.reachability, referrer: referrer))
        if status == .notDetermined {
            analytics?.report(ProfileAnalytics.permissionPrompted())
        }
    }

    /// Read on every foreground. Coming back from Settings with notifications allowed shows the
    /// toggle on — and NOTHING AUTO-ADVANCES. The user taps Save preferences again.
    ///
    /// IT DOES NOT FIRE `permission_status_changed`. That belongs to the shared reconciler and
    /// must never double up with `permission_result` for one act.
    func foregrounded() async {
        switch await access.read() {
        case .denied, .restricted:
            state.pushOn = false
        case .granted:
            // Only a status the user went and granted turns the switch back on. A user who
            // switched it off here and then backgrounded the app has not changed their mind.
            if state.prompt == nil && !state.saving { state.pushOn = true }
        case .notDetermined:
            break
        }
    }

    // MARK: the push toggle

    /// The switch moved. The value is what the user ASKED for, not what the state becomes.
    func pushChanged(_ on: Bool) async {
        guard on else {
            // Switching OFF does not move the toggle. The dialog decides.
            state.prompt = .userTurnedItOff
            analytics?.report(ProfileAnalytics.consentDeactivationConfirmShown())
            return
        }
        switch await access.read() {
        case .denied, .restricted:
            // A DEAD TOGGLE IS WORSE THAN NO TOGGLE. The OS has refused, so the only way on is
            // Settings — and on iOS that is always Settings, never a re-prompt.
            analytics?.report(ProfileAnalytics.permissionSettingsOpened())
            host?.openSettingsOrReprompt()
        case .granted, .notDetermined:
            state.pushOn = true
            analytics?.report(
                ProfileAnalytics.consentChanged(on: true, channel: ConsentChannel.push)
            )
        }
    }

    /// `Keep active`, or a tap on the scrim. Identical acts, identical event.
    func keepActive() async {
        guard state.prompt != nil else { return }
        // THE STATUS IS RE-READ, and that is not belt-and-braces — it is the difference between
        // the two dialogs. Abandoning state C leaves push exactly as it was. Abandoning state D
        // (the scrim; there is no `Keep active` button there) would otherwise leave a toggle
        // still reading ON after the OS said "Don't allow" — a switch claiming a permission the
        // device has refused.
        let status = await access.read()
        let denied = status == .denied || status == .restricted
        state.prompt = nil
        if denied { state.pushOn = false }
        analytics?.report(ProfileAnalytics.consentDeactivationAbandoned())
    }

    /// `Open Settings` — state D only.
    ///
    /// NOT AN ABANDONMENT, and the ticket names the distinction. The dialog closes because the
    /// user is leaving for Settings, not because they changed their mind.
    func openSettings() {
        guard state.prompt != nil else { return }
        state.prompt = nil
        analytics?.report(ProfileAnalytics.permissionSettingsOpened())
        host?.openSettingsOrReprompt()
    }

    /// `Confirm deactivation` — and it does TWO DIFFERENT THINGS, one per dialog.
    ///
    /// The ticket specifies them in two places that have to be read together. Under *The push
    /// toggle*: "Confirm deactivation sets push off, closes the dialog, and **the user stays on
    /// this screen**" — that is state C. Under *What happens after the OS dialog*: "Confirm
    /// deactivation → push off → **save** → Location (12)" — that is state D, where the dialog
    /// interrupted a save already in flight and confirming it is the user finishing that save.
    ///
    /// Treating them as one act was the first build of this on Android, and what it produced was
    /// a user who pressed Save, answered the OS, confirmed the consequence, and then sat on the
    /// same screen with nothing having happened.
    func confirmDeactivation(onAdvance: @escaping () -> Void = {}) async {
        guard let prompt = state.prompt else { return }
        state.pushOn = false
        state.prompt = nil
        analytics?.report(ProfileAnalytics.consentDeactivationConfirmed())
        analytics?.report(
            ProfileAnalytics.consentChanged(on: false, channel: ConsentChannel.push)
        )

        if prompt == .userTurnedItOff { return }

        // State D. Steps (1) and (2) are already behind us — the dialog was raised and answered,
        // and a denial means there is nothing to register — so this picks the save up at (3).
        state.saving = true
        state.saveFailed = false
        await commitSave(pushOn: false, onAdvance: onAdvance)
    }

    // MARK: the demand test

    /// An interest box was checked or unchecked.
    ///
    /// REPORTS AND FLIPS. That is the whole function, and the whole feature: no dialog, no field,
    /// no "coming soon" toast, no navigation, no consent write, no network call.
    func interestToggled(_ channel: InterestChannel) {
        let now = !state.isInterested(channel)
        if now { state.interest.insert(channel) } else { state.interest.remove(channel) }
        analytics?.report(ProfileAnalytics.channelInterestChanged(channel, on: now))
    }

    /// The Privacy Policy link — the only link on the screen.
    func privacyTapped() {
        analytics?.report(
            ProfileAnalytics.legalLinkTapped(SignUpAnalytics.Legal.privacy, screen: .reachability)
        )
    }

    // MARK: save

    /// Save preferences, in the ticket's own order.
    ///
    /// (1) if push is on and the status is not determined, raise the OS dialog and wait;
    /// (2) on Allow, register for push; (3) commit the consent and WAIT FOR CONFIRMATION;
    /// (4) fire `reachability_saved`; (5) advance.
    ///
    /// NOT TAPPABLE TWICE: input is locked from the tap until navigation or failure, and the CTA
    /// is never disabled — it simply stops answering.
    func savePressed(onAdvance: @escaping () -> Void) async {
        guard !state.saving else { return }
        state.saving = true
        state.saveFailed = false

        let pushOn = state.pushOn
        var granted = await access.read() == .granted

        // (1) The dialog, and ONLY when push is on and nothing has been answered yet. With push
        // off, or the status already determined, no dialog appears.
        if pushOn, await access.read() == .notDetermined {
            analytics?.report(ProfileAnalytics.permissionOsSheetShown())
            granted = await host?.requestNotificationPermission() ?? false
            analytics?.report(ProfileAnalytics.permissionResult(granted: granted))

            if !granted {
                // "Don't allow" — the deactivation confirm, with the primary labelled
                // `Open Settings`. The save stops here: the user has a decision to make.
                state.saving = false
                state.prompt = .afterOsDenial
                analytics?.report(ProfileAnalytics.consentDeactivationConfirmShown())
                return
            }
        }

        // (2) Register on a grant. A grant with no token looks exactly like success on this
        // screen, which is the silent failure the build inventory names.
        if pushOn && granted { _ = await push.register() }

        await commitSave(pushOn: pushOn, onAdvance: onAdvance)
    }

    /// Steps (3), (4) and (5), shared by both ways of reaching them.
    ///
    /// ONE COPY, because the state-D path is the same save resumed rather than a second save. Two
    /// copies is how the "not optimistic" rule gets kept on one path and quietly lost on the other.
    private func commitSave(pushOn: Bool, onAdvance: @escaping () -> Void) async {
        let interest = state.interestValues

        // (3) The consent write, CONFIRMED before anything advances.
        guard await consent.savePushConsent(on: pushOn) else {
            state.saving = false
            state.saveFailed = true
            return
        }

        // (4) then (5). The event is after the confirmation, never on the tap.
        analytics?.report(
            ProfileAnalytics.reachabilitySaved(
                pushOn: pushOn,
                permission: await access.read(),
                interest: interest
            )
        )
        state.saving = false
        onAdvance()
    }
}
