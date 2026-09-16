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

    init(repo: BasicsRepository) {
        self.repo = repo
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
        guard !busy else { return }
        busy = true
        transportFailed = false
        emailInUse = false
        Task {
            let result = await repo.sendCode(email: email)
            busy = false
            switch result {
            case let .sent(expiresAt, resendAvailableAt):
                codeDigits = ""
                attempts = 0
                lastSubmitRefused = false
                self.expiresAt = expiresAt
                self.resendAvailableAt = resendAvailableAt
                startTicker()
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
        Task {
            let result = await repo.verifyCode(codeDigits)
            busy = false
            switch result {
            case .verified:
                lastSubmitRefused = false
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
    func saveDateOfBirth(iso: String, onSaved: @escaping () -> Void) {
        guard !busy else { return }
        busy = true
        transportFailed = false
        serverRejectedAge = false
        Task {
            let result = await repo.saveDateOfBirth(iso: iso, hideAge: hideAge)
            busy = false
            switch result {
            case .saved:
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
