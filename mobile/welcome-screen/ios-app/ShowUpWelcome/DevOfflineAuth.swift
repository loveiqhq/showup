//  DevOfflineAuth.swift
//  ShowUp · walking the sign-up flow with no backend at all
//
//  The iOS half of `welcome/DevOfflineAuth.kt`. Read that file's header for the argument; this
//  one repeats only what differs.
//
//  WHAT THIS IS, AND WHAT IT IS CAREFUL NOT TO BE
//
//  The app talks to a real NestJS backend over real HTTP, and this does not change it: every
//  request is still made, and whatever the server answers is what the app believes. What this
//  adds is one narrow case — the request could not be made AT ALL, because nothing is listening
//  — where a debug build carries on locally instead of stopping.
//
//  THE THREE RULES THAT KEEP IT HONEST
//
//    1. DEBUG ONLY. The default value of `PhoneAuthRepository.offline` is behind `#if DEBUG`, so
//       a release build passes nil and this type is never constructed.
//    2. ONLY WHEN NOTHING ANSWERED. It engages in the `catch`, which means a thrown transport
//       error. A server that answers 400, 401, 429 or 500 is a server that is working, and its
//       answer is passed through untouched — so this cannot paper over a backend bug.
//    3. IT SAYS SO, LOUDLY. The flag reaches the screen and the dev strip reads
//       "OFFLINE · no server" rather than "logged, not sent".
//
//  An `actor` rather than a class with a lock: the repository is `Sendable` and is used from a
//  `@MainActor` model, so the state this holds crosses isolation domains. The three escape
//  hatches are banned by CLAUDE.md and by check-swift-concurrency.py.

import Foundation

actor DevOfflineAuth {

    /// Shared, because it has to outlive a repository that is rebuilt on every view update.
    static let shared = DevOfflineAuth()

    private var code: String?
    private var expiresAt: Date?
    private var attempts = 0

    /// How long a code lives, matching the server's `OTP_TTL`.
    private let ttl: TimeInterval = 300

    /// Issues a code, the way `/auth/phone/start` would.
    ///
    /// Six digits and never 123456 or 000000: the first is what everyone tries by accident and
    /// the second is what a broken generator returns, and either would hide a real mismatch from
    /// whoever is testing the mismatch state.
    func start(now: Date) -> StartAuthResult {
        let issued = String(format: "%06d", Int.random(in: 100_000..<1_000_000))
        code = issued
        expiresAt = now.addingTimeInterval(ttl)
        attempts = 0
        return .sent(expiresAt: now.addingTimeInterval(ttl),
                     resendAvailableAt: now.addingTimeInterval(TimeInterval(resendCooldownSeconds)),
                     devCode: issued,
                     offline: true)
    }

    /// Checks a code the way `/auth/phone/verify` would, including the attempt cap.
    ///
    /// Models the failures too. A stand-in that only ever succeeds would let the mismatch card,
    /// the lockout card and the expiry copy rot unseen, which is most of what anyone walking
    /// this flow needs to look at.
    func verify(_ submitted: String, now: Date) -> VerifyPhoneResult {
        guard let issued = code else { return .refused }
        if attempts >= maxVerifyAttempts { return .tooManyAttempts }
        if let deadline = expiresAt, now > deadline { return .refused }
        if submitted != issued {
            attempts += 1
            return attempts >= maxVerifyAttempts ? .tooManyAttempts : .refused
        }
        code = nil
        attempts = 0
        // Incomplete, always. There is no profile offline, and the routing rule is that a profile
        // which cannot be read counts as incomplete — so the flow continues into onboarding,
        // which is the half anyone walking this wants to see.
        return .signedIn(profileComplete: false)
    }

    /// Forgets everything. For tests, so one case cannot leak into the next.
    func reset() {
        code = nil
        expiresAt = nil
        attempts = 0
    }
}
