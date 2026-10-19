//  BasicsModel.swift
//  ShowUp · the first screen group in this app that owns asynchronous work
//
//  WHY @Observable, AND WHY ONLY NOW
//
//  The iOS CLAUDE.md has said from the start: "@Observable — when a screen owns asynchronous work
//  that must outlive a redraw and be cancellable: loading, uploading, retrying. None of the
//  current screens qualify; profile and discovery will."
//
//  SHOWUP-153 is that moment. It sends a code, waits for a server, counts down against a server
//  timestamp and retries. `@SceneStorage` cannot own that: a request in flight has to survive a
//  redraw and be cancelled with the screen.
//
//  WHAT STILL IS NOT HERE
//
//  No screen state. The four screens remain functions from values to pixels — they take values
//  and closures and render. That property is what keeps ScreenFitTests at 17 sizes possible, and
//  it survives this change intact.

import Foundation
import Observation
import ShowUpAPI

@MainActor
@Observable
final class BasicsModel {
    private let repo: BasicsRepository

    /// Where "The basics" reports (family D, SHOWUP-150 to 155). Nil in a test that does not care.
    ///
    /// Until 8 October 2026 this model took no tracker and nothing in the basics fired: the events
    /// were specified and most builders written, and no call site existed. Android:
    /// `BasicsViewModel`, the same events at the same decisions.
    private let analytics: (any AnalyticsTracking)?

    init(repo: BasicsRepository, analytics: (any AnalyticsTracking)? = nil) {
        self.repo = repo
        self.analytics = analytics
    }

    // MARK: tracking

    /// When each step was last arrived at, for `time_on_step_s`.
    private var arrivedAt: [BasicsStep: Date] = [:]
    /// `profile_build_started` fires once per build, on the first arrival at the name step.
    private var buildStarted = false
    /// How many times `Send a new code` delivered one, for `email_code_resent.attempt_no`.
    private var resends = 0
    /// How the date field reads its digits — the same default the screen parses with.
    var dobOrder: DateOrder = .forCurrentLocale()
    /// The date state last shown: C and D report on the TRANSITION in, not per keystroke.
    private var dobShown: DobShown = .open

    private enum DobShown { case open, valid, impossible, underAge }

    private func shownState(for parsed: DobResult) -> DobShown {
        switch parsed {
        case .incomplete: return .open
        case .impossible: return .impossible
        case let .valid(age, _): return age < minimumAge ? .underAge : .valid
        }
    }

    private func report(_ event: (String, [String: any Sendable])) {
        analytics?.report(event)
    }

    private func secondsOn(_ step: BasicsStep) -> Int {
        max(0, Int(now().timeIntervalSince(arrivedAt[step] ?? now())))
    }

    /// A step of the basics is on screen: `screen_viewed` and `profile_step_viewed` — which screen,
    /// and where in the flow. The name step also starts the build, once.
    func arrived(_ step: BasicsStep, referrer: ProfileScreen?) {
        arrivedAt[step] = now()
        report(ProfileAnalytics.screenViewed(step.trackedScreen, referrer: referrer))
        report(ProfileAnalytics.stepViewed(step))
        if step == .name && !buildStarted {
            buildStarted = true
            report(ProfileAnalytics.buildStarted(entryPoint: AppEntryPoint.cold))
        }
        // The state already on screen is the baseline: arriving with it is not a transition.
        if step == .dob { dobShown = shownState(for: parseDob(dob, order: dobOrder)) }
    }

    /// The bridge out of the basics: `screen_viewed` and `embrace_bridge_viewed`, and NOTHING else.
    func embraceArrived(referrer: ProfileScreen?) {
        report(ProfileAnalytics.screenViewed(.embraceBuild, referrer: referrer))
        report(ProfileAnalytics.embraceBridgeViewed(variant: EmbraceVariant.buildProfile))
    }

    /// Continue with a valid name. `char_count` only — the name itself is never sent.
    func nameAccepted(_ name: String) {
        report(ProfileAnalytics.nameSubmitted(
            charCount: name.trimmingCharacters(in: .whitespacesAndNewlines).count))
        report(ProfileAnalytics.stepCompleted(.name, timeOnStepSeconds: secondsOn(.name)))
    }

    /// Continue pressed with no name — on the refused press, never on render.
    func nameRefused() {
        report(ProfileAnalytics.formValidationFailed(fieldId: ProfileField.firstName,
                                                     screen: .name, step: .name))
    }

    /// Continue refused on the email step: empty is `required_missing`, malformed is `format`.
    func emailRefused(empty: Bool) {
        report(empty
            ? ProfileAnalytics.formValidationFailed(fieldId: ProfileField.email, screen: .email, step: .email)
            : ProfileAnalytics.emailValidationFailed())
    }

    /// The marketing row toggled — in BOTH directions, never opt-out only.
    func marketingConsentChanged(_ on: Bool) {
        report(ProfileAnalytics.consentChanged(on: on))
    }

    /// The user typed in the date field. Restores assign `dob` directly and report nothing.
    func dobTyped(_ value: String) {
        dob = value
        let parsed = parseDob(value, order: dobOrder)
        let shown = shownState(for: parsed)
        guard shown != dobShown else { return }
        switch (shown, parsed) {
        case (.impossible, _): report(ProfileAnalytics.dobValidationFailed())
        case let (.underAge, .valid(age, _)): report(ProfileAnalytics.ageGateFailed(age: age))
        default: break
        }
        dobShown = shown
    }

    /// The user ticked or unticked the age's visibility box. Opt-out only: the TICK reports.
    /// Restores assign `hideAge` directly and report nothing.
    func hideAgeToggled(_ value: Bool) {
        if value && !hideAge { report(ProfileAnalytics.ageDisplayOptedOut()) }
        hideAge = value
    }

    /// Continue refused on the date step. Only an INCOMPLETE date reports: an impossible or under-18
    /// date already reported on the way into that state.
    func dobRefused() {
        dobAttempted = true
        if parseDob(dob, order: dobOrder) == .incomplete {
            report(ProfileAnalytics.formValidationFailed(fieldId: ProfileField.age, screen: .dob, step: .dob))
        }
    }

    /// `Send a new code` on the code screen — the same send, reported as a resend.
    func resendCode() {
        send(resend: true, onSent: {})
    }

    var email: String = ""
    var codeDigits: String = "" {
        didSet { lastSubmitRefused = false }
    }
    /// Submissions against the CURRENT code. Reset by a resend, because that is a new challenge.
    private(set) var attempts = 0
    private(set) var lastSubmitRefused = false
    /// Server-owned. Nil until a code has been sent.
    private(set) var expiresAt: Date?
    private(set) var resendAvailableAt: Date?
    /// The code, when a development server sent it back. See `SendCodeResult.sent`.
    ///
    /// Held here rather than read at the call site because a resend replaces it, and a strip
    /// still showing the previous code would be worse than no strip at all.
    private(set) var devCode: String?
    private(set) var cooldownSeconds = 0
    /// True while a request is in flight. The CTA is inert.
    private(set) var busy = false
    private(set) var transportFailed = false
    /// 400 from the send: the address belongs to someone else. Copy is NOT decided.
    private(set) var emailInUse = false

    var dob: String = "" {
        didSet {
            serverRejectedAge = false
            // The incomplete error clears the moment the eighth digit lands.
            if dobDigits(dob).count == 8 { dobAttempted = false }
        }
    }
    var hideAge = false
    var dobAttempted = false
    private(set) var serverRejectedAge = false

    /// Held so a resend can cancel the previous countdown rather than race it.
    private var ticker: Task<Void, Never>?

    /// Overridable in tests so a countdown does not need a real second to pass.
    var now: @Sendable () -> Date = { Date() }

    /// Whether the code has died of age.
    ///
    /// Computed from the server's own timestamp rather than a local countdown, so a device that
    /// slept through the expiry still gets it right on wake.
    var expired: Bool {
        guard let expiresAt else { return false }
        return now() >= expiresAt
    }

    var failure: VerifyState {
        verifyState(attempts: attempts,
                    maxAttempts: maxVerifyAttempts,
                    expired: expired,
                    lastSubmitRefused: lastSubmitRefused)
    }

    /// Sends a code, from the email step's Continue and from the resend link.
    ///
    /// The ticket is explicit that the send is triggered by leaving the email screen and NOT by
    /// arriving at the code screen — which is also what stops a relaunch from silently issuing a
    /// new code while the one in the inbox is still good.
    func sendCode(onSent: @escaping () -> Void = {}) {
        send(resend: false, onSent: onSent)
    }

    private func send(resend: Bool, onSent: @escaping () -> Void) {
        guard !busy else { return }
        busy = true
        transportFailed = false
        emailInUse = false
        Task {
            let result = await repo.sendCode(email: email)
            busy = false
            switch result {
            case let .sent(expiresAt, resendAvailableAt, devCode):
                codeDigits = ""
                attempts = 0
                lastSubmitRefused = false
                self.expiresAt = expiresAt
                self.resendAvailableAt = resendAvailableAt
                self.devCode = devCode
                startTicker()
                // Reported on DELIVERY, not on the press: a press the server refused or the network
                // lost sent nothing, and a retry would otherwise count twice.
                if resend {
                    resends += 1
                    report(ProfileAnalytics.emailCodeResent(attemptNo: resends))
                } else {
                    // The domain only — never the address.
                    let domain = email.split(separator: "@").last.map(String.init) ?? ""
                    report(ProfileAnalytics.emailSubmitted(
                        domain: domain.trimmingCharacters(in: .whitespaces).lowercased()))
                    report(ProfileAnalytics.stepCompleted(.email, timeOnStepSeconds: secondsOn(.email)))
                }
                onSent()
            case .tooSoon:
                // The server refused because its own cooldown has not elapsed, so the client's
                // countdown is the one that is wrong. Leave the resend blocked and let the ticker
                // re-read the server's timestamp.
                break
            case .emailInUse:
                emailInUse = true
            case .failed:
                transportFailed = true
            }
        }
    }

    func verify(onVerified: @escaping () -> Void) {
        guard !busy, canSubmitCode(codeDigits, state: failure) else { return }
        busy = true
        transportFailed = false
        // Which attempt at THIS code — the count a new send resets, and the server caps.
        let attemptNo = attempts + 1
        Task {
            let result = await repo.verifyCode(codeDigits)
            busy = false
            // A verdict either way; a transport failure is no verdict and reports nothing.
            if result != .failed {
                report(ProfileAnalytics.emailCodeSubmitted(attemptNo: attemptNo, passed: result == .verified))
            }
            switch result {
            case .verified:
                lastSubmitRefused = false
                report(ProfileAnalytics.stepCompleted(.emailVerify, timeOnStepSeconds: secondsOn(.emailVerify)))
                onVerified()
            case .refused:
                attempts += 1
                lastSubmitRefused = true
            case .tooManyAttempts:
                // The server says the cap is reached, so the count goes TO the cap rather than up
                // by one — the two can disagree if a request was lost, and the server wins.
                attempts = maxVerifyAttempts
                lastSubmitRefused = true
            case .failed:
                transportFailed = true
            }
        }
    }

    /// Writes the date and the visibility choice together. Only a valid, 18+ date gets here.
    /// - Parameter age: whole years as the screen computed it — reported on `dob_submitted` when
    ///   the server's answer carries none.
    func saveDateOfBirth(iso: String, age: Int, onSaved: @escaping () -> Void) {
        guard !busy else { return }
        busy = true
        transportFailed = false
        serverRejectedAge = false
        Task {
            let result = await repo.saveDateOfBirth(iso: iso, hideAge: hideAge)
            busy = false
            switch result {
            case let .saved(serverAge):
                // The conversion, with the age the server computed and stored.
                report(ProfileAnalytics.dobSubmitted(age: serverAge ?? age))
                report(ProfileAnalytics.stepCompleted(.dob, timeOnStepSeconds: secondsOn(.dob)))
                onSaved()
            case .underAge:
                serverRejectedAge = true
            case .failed:
                transportFailed = true
            }
        }
    }

    /// Counts the cooldown down from the server's `resendAvailableAt`.
    ///
    /// Recomputed from the timestamp every second rather than decremented, so a device that slept
    /// through half the cooldown wakes up with the right number instead of a stale one.
    private func startTicker() {
        ticker?.cancel()
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let left = self.resendAvailableAt.map {
                    max(0, Int($0.timeIntervalSince(self.now()).rounded(.up)))
                } ?? 0
                self.cooldownSeconds = left
                if left <= 0 { return }
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    // No deinit cancelling the ticker: deinit is nonisolated and `ticker` is MainActor-isolated,
    // which Swift 6 rejects. The task captures self weakly and returns the moment it is nil, so
    // the model going away ends the loop on its own.
}
