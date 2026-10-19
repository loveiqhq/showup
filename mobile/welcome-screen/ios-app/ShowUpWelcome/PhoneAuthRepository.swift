//  PhoneAuthRepository.swift
//  ShowUp · signing in with a phone number, for real
//
//  Mirrors `welcome/PhoneAuthRepository.kt`. Both routes are `@Public` on the server — this is
//  the one part of the app that runs without a token, because it is what produces one.
//
//  NO PAID PROVIDER IS INVOLVED, AND NONE NEEDS DISABLING
//
//  `LogSmsSender` is the only SMS sender the backend has and it is hardwired in
//  `auth.module.ts`; there is no Twilio integration to switch off. It writes the code to the
//  server log, and `AUTH_EXPOSE_OTP` — which defaults to true unless NODE_ENV is production —
//  also returns it as `devCode` on the challenge. The whole flow is testable for nothing.

import Foundation
import ShowUpAPI

/// The answer to "text this number a code".
enum StartAuthResult: Equatable {
    /// - Parameter offline: true when NOTHING ANSWERED and a debug build carried on locally.
    ///   It exists to be displayed — a stand-in the user cannot tell apart from a backend is how
    ///   somebody demos a broken integration and believes it works. See DevOfflineAuth.
    case sent(expiresAt: Date, resendAvailableAt: Date, devCode: String?, offline: Bool = false)
    /// 429. Either the per-challenge cooldown or the route's 5-per-minute throttle.
    case tooSoon
    case failed
}

/// The answer to "is this the code".
enum VerifyPhoneResult: Equatable {
    /// Signed in, and the tokens are already in the Keychain.
    ///
    /// `profileComplete` is how the flow decides where to go next. Read from `/me/profile`
    /// rather than from a flag on the auth response, because the server does not send one — and
    /// because completeness survives an interrupted signup, where "was this account new" would
    /// send a half-registered user straight to Home.
    case signedIn(profileComplete: Bool)
    /// Wrong, expired or superseded — the server answers 401 for all three.
    case refused
    /// The cap. Recognised by message text, and it fails safe.
    case tooManyAttempts
    case failed
}

struct PhoneAuthRepository: Sendable {
    let api: ShowUpAPI
    /// Passed in rather than reached through `api`, so the fact that this type WRITES
    /// credentials is visible in its signature instead of buried in one line of a method.
    let tokens: any TokenStoring

    /// The stand-in used when NOTHING ANSWERED, or nil to let that failure be a failure.
    ///
    /// A stored property rather than an `#if DEBUG` inside the catch block, for the same reason
    /// as `PhoneAuthRepository.kt`: tests build in DEBUG, so an inlined guard would mean the
    /// release behaviour of this path — the one real users get — could never be asserted.
    ///
    /// LAST, deliberately. The memberwise initialiser takes its parameters in declaration order,
    /// so putting this above `api` would break every existing `PhoneAuthRepository(api:tokens:)`
    /// call site for no reason at all.
    #if DEBUG
    var offline: DevOfflineAuth? = DevOfflineAuth.shared
    #else
    var offline: DevOfflineAuth?
    #endif


    func start(phoneE164: String) async -> StartAuthResult {
        do {
            let response = try await api.client.startPhoneVerification(
                body: .json(.init(phone: phoneE164)))
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return .sent(expiresAt: json.expiresAt,
                             resendAvailableAt: json.resendAvailableAt,
                             devCode: json.devCode,
                             offline: false)
            default:
                if case let .undocumented(statusCode, _) = response, statusCode == 429 {
                    return .tooSoon
                }
                return .failed
            }
        } catch {
            // Nothing answered. A server that replies — with anything, including 500 — returns
            // above, so this cannot hide a backend bug.
            if let offline { return await offline.start(now: Date()) }
            return .failed
        }
    }

    /// Confirms the code and STORES THE TOKENS.
    ///
    /// The save happens here rather than at the call site because it is not optional: a caller
    /// that forgot it would leave the app holding a session it cannot prove, and every later
    /// request would 401 for a reason nothing on screen could explain.
    func verify(phoneE164: String, code: String) async -> VerifyPhoneResult {
        do {
            let response = try await api.client.verifyPhone(
                // The route records the user-agent against the session so a person can later see
                // where they are signed in, which is why the generated signature requires it.
                headers: .init(user_hyphen_agent: ShowUpAPI.userAgent),
                body: .json(.init(code: code, phone: phoneE164)))
            switch response {
            case .ok(let ok):
                let auth = try ok.body.json
                await tokens.save(accessToken: auth.accessToken, refreshToken: auth.refreshToken)
                return .signedIn(profileComplete: await readProfileComplete())
            default:
                if case let .undocumented(statusCode, payload) = response, statusCode == 401 {
                    var text = ""
                    if let body = payload.body {
                        text = (try? await String(collecting: body, upTo: 8 * 1024)) ?? ""
                    }
                    return text.localizedCaseInsensitiveContains("Too many attempts")
                        ? .tooManyAttempts
                        : .refused
                }
                return .failed
            }
        } catch {
            if let offline { return await offline.verify(code, now: Date()) }
            return .failed
        }
    }

    /// Whether the signed-in user already has a usable profile.
    ///
    /// A failure here is treated as INCOMPLETE, not as an error. Sending someone through
    /// onboarding they have already done is a mild annoyance; skipping them past profile
    /// creation because a request timed out leaves an account that cannot be matched.
    private func readProfileComplete() async -> Bool {
        do {
            let response = try await api.client.getProfile()
            if case .ok(let ok) = response {
                return try ok.body.json.isComplete
            }
            return false
        } catch {
            return false
        }
    }
}
