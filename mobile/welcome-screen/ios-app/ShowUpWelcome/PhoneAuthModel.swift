//  PhoneAuthModel.swift
//  ShowUp · the sign-up flow's asynchronous half
//
//  Mirrors `welcome/PhoneAuthViewModel.kt`. Owns only what talks to a server: sending a code,
//  confirming it, and the countdown between. Everything the flow already held — the step, the
//  typed digits, the chosen country — stays in SignUpFlowView.
//
//  WHAT REPLACING DevAuth ACTUALLY CHANGED
//
//  The code is no longer compared to a constant. It is sent to `/auth/phone/verify`, and what
//  comes back is a real JWT pair written to the Keychain — which is the whole reason 153 and 154
//  could not work: every route they call is authenticated, and nothing had ever produced a token.

import Foundation
import Observation
import ShowUpAPI

@MainActor
@Observable
final class PhoneAuthModel {
    private let repo: PhoneAuthRepository

    init(repo: PhoneAuthRepository) {
        self.repo = repo
    }

    private(set) var cooldownSeconds = 0
    private(set) var attempts = 0
    private(set) var lastSubmitRefused = false
    private(set) var expiresAt: Date?
    private(set) var resendAvailableAt: Date?
    private(set) var busy = false
    private(set) var transportFailed = false
    /// The code, straight from the server, shown ONLY in a debug build.
    private(set) var devCode: String?

    private var ticker: Task<Void, Never>?

    /// Overridable in tests so a countdown does not need a real second to pass.
    var now: @Sendable () -> Date = { Date() }

    var locked: Bool { attempts >= maxVerifyAttempts }

    /// Sends a code. Used by the phone screen's CTA and by the code screen's resend.
    func start(phoneE164: String, onSent: @escaping () -> Void = {}) {
        guard !busy else { return }
        busy = true
        transportFailed = false
        Task {
            let result = await repo.start(phoneE164: phoneE164)
            busy = false
            switch result {
            case let .sent(expiresAt, resendAvailableAt, devCode):
                attempts = 0
                lastSubmitRefused = false
                self.expiresAt = expiresAt
                self.resendAvailableAt = resendAvailableAt
                self.devCode = devCode
                startTicker()
                onSent()
            case .tooSoon:
                // The server's own cooldown, or the route's throttle. Either way the client's
                // countdown was the optimistic one, so let the ticker re-read the server's
                // timestamp rather than arguing with it.
                break
            case .failed:
                transportFailed = true
            }
        }
    }

    /// Confirms the code; on success the tokens are already stored.
    ///
    /// - Parameter onSignedIn: receives whether the account already has a complete profile.
    ///   Complete goes to Home, incomplete continues onboarding.
    func verify(phoneE164: String, code: String,
                onSignedIn: @escaping (Bool) -> Void) {
        guard !busy, !locked, code.count == 6 else { return }
        busy = true
        transportFailed = false
        Task {
            let result = await repo.verify(phoneE164: phoneE164, code: code)
            busy = false
            switch result {
            case let .signedIn(profileComplete):
                lastSubmitRefused = false
                onSignedIn(profileComplete)
            case .refused:
                attempts += 1
                lastSubmitRefused = true
            case .tooManyAttempts:
                // The server says the cap is reached, so the count goes TO the cap rather than
                // up by one — the two can disagree if a request was lost, and the server wins.
                attempts = maxVerifyAttempts
                lastSubmitRefused = true
            case .failed:
                transportFailed = true
            }
        }
    }

    /// Editing a digit clears the refusal, so the row stops being red while it is corrected.
    ///
    /// Only the flag — the attempt COUNT stays, because the server counted those attempts and
    /// they are what the cap is measured against.
    func clearRefusal() { lastSubmitRefused = false }

    /// Recomputed from the server's timestamp each second rather than decremented, so a device
    /// that slept through half the cooldown wakes up with the right number.
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
    // which Swift 6 rejects. The task captures self weakly and returns the moment it is nil.
}
