//  BasicsRepository.swift
//  ShowUp · what "The basics" asks the backend, and what the answers mean
//
//  Mirrors `profile/BasicsRepository.kt`. Every call goes through the GENERATED client — nothing
//  here describes a request body or a URL.
//
//  WHAT THIS FILE IS REALLY FOR: TRANSLATING 401
//
//  `/auth/email/verify` answers 401 for a wrong code, an expired one, a superseded one AND a
//  missing challenge, and 401 again with a different sentence once the attempt cap is reached.
//  One status, five causes, three different things for the screen to say.
//
//   - "too many attempts" is told apart by MATCHING THE MESSAGE TEXT. Fragile — it is English,
//     and a sentence rather than a code — so it fails SAFE: an unrecognised 401 is reported as a
//     refusal, which invites a retry the cap would block one attempt later. Worth replacing with
//     a domain error code on the server.
//
//   - EXPIRY IS NOT DECIDED HERE. The caller decides it from the `expiresAt` the start call
//     already returned, because the response genuinely cannot distinguish it.

import Foundation
import ShowUpAPI

/// The answer to "send this address a code".
enum SendCodeResult: Equatable {
    /// Both timestamps come from the server, and both are used rather than assumed.
    case sent(expiresAt: Date, resendAvailableAt: Date)
    /// 429. The previous code's cooldown has not elapsed.
    case tooSoon
    /// 400 — the address belongs to another account.
    case emailInUse
    case failed
}

/// The answer to "is this the code".
enum VerifyCodeResult: Equatable {
    case verified
    /// Wrong, expired, or superseded — the server does not say which.
    case refused
    /// The cap. Recognised by message text; see the file header.
    case tooManyAttempts
    case failed
}

/// The answer to "store this date of birth".
enum SaveBasicsResult: Equatable {
    /// `age` is the server's own derivation, not the client's.
    case saved(age: Int?)
    /// 400 from `isAtLeast18`. The client checks too, so this means the two disagreed.
    case underAge
    case failed
}

/// The one place the profile flow talks to the backend.
///
/// Takes a `ShowUpAPI` rather than building one, so a test can hand it a client on a stub
/// transport — which is how every mapping below is verified without a backend.
struct BasicsRepository: Sendable {
    let api: ShowUpAPI

    /// The server's sentence for the cap. Matched, not parsed — see the file header.
    private static let tooManyMarker = "Too many attempts"
    /// The registry `field_id`, and the only value the server's allow-list accepts today.
    private static let hiddenFieldAge = "age"

    func sendCode(email: String) async -> SendCodeResult {
        do {
            let response = try await api.client.startEmailVerification(body: .json(.init(email: email)))
            switch response {
            case .ok(let ok):
                let json = try ok.body.json
                return .sent(expiresAt: json.expiresAt, resendAvailableAt: json.resendAvailableAt)
            case .badRequest:
                // The server's only 400 on this route is the uniqueness check.
                return .emailInUse
            default:
                // 429 has no documented case on this operation, so it arrives undocumented and is
                // read off the status. Anything else is a plain failure.
                if case let .undocumented(statusCode, _) = response, statusCode == 429 {
                    return .tooSoon
                }
                return .failed
            }
        } catch {
            return .failed
        }
    }

    func verifyCode(_ code: String) async -> VerifyCodeResult {
        do {
            let response = try await api.client.verifyEmail(body: .json(.init(code: code)))
            switch response {
            case .noContent:
                return .verified
            case .badRequest(let bad):
                // The route documents 400 for validation; a malformed code lands here rather than
                // as a refusal, but for the user it means the same thing.
                _ = bad
                return .refused
            default:
                // 401 is undocumented on this operation, so it arrives here with its body
                // unparsed. Read it inline rather than through a helper: naming
                // `UndocumentedPayload` would need OpenAPIRuntime imported into the app target,
                // and the type is only ever used for this one string match.
                if case let .undocumented(statusCode, payload) = response, statusCode == 401 {
                    var text = ""
                    if let body = payload.body {
                        text = (try? await String(collecting: body, upTo: 8 * 1024)) ?? ""
                    }
                    return text.localizedCaseInsensitiveContains(Self.tooManyMarker)
                        ? .tooManyAttempts
                        : .refused
                }
                return .failed
            }
        } catch {
            return .failed
        }
    }

    /// Writes the date of birth and the age-visibility choice.
    ///
    /// One PATCH, not two. They are set on the same screen by the same press, and splitting them
    /// would let the date land while the visibility choice failed — leaving the user with an age
    /// displayed that they asked to hide.
    func saveDateOfBirth(iso: String, hideAge: Bool) async -> SaveBasicsResult {
        do {
            let response = try await api.client.updateProfile(body: .json(.init(
                dateOfBirth: iso,
                // Never isVisible. That flag means "appears in discovery at all", and the product
                // decision is explicit that hiding an age must not affect matching.
                hiddenFields: hideAge ? [Self.hiddenFieldAge] : []
            )))
            switch response {
            case .ok(let ok):
                return .saved(age: try ok.body.json.age)
            case .badRequest:
                // The server re-derives age and refuses under 18. The client already checked, so
                // this only fires when the two disagree.
                return .underAge
            default:
                return .failed
            }
        } catch {
            return .failed
        }
    }
}
