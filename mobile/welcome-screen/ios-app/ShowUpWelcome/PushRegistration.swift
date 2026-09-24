//
//  PushRegistration.swift
//  ShowUpWelcome · getting the device token to the backend once the user says yes (SHOWUP-162)
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHY THIS IS ITS OWN THING
//  ───────────────────────────────────────────────────────────────────────────
//
//  The ticket names it as the one failure that looks exactly like success: "granting the
//  permission and never registering the device is a silent failure". The screen cannot tell the
//  difference — the user tapped, the sheet appeared, they said yes, the flow advanced — and
//  nothing in that sequence reveals that no token ever reached the server.
//
//  ───────────────────────────────────────────────────────────────────────────
//  THERE IS NO TOKEN SOURCE IN THIS BUILD, AND THAT IS DELIBERATE
//  ───────────────────────────────────────────────────────────────────────────
//
//  An APNs token needs `registerForRemoteNotifications()`, a push entitlement and an APNs key on
//  the Apple developer account. That is a service-enablement decision and it has not been taken —
//  the standing instruction on this project is that third-party providers stay off until they are
//  explicitly turned on, and the BACKEND already models exactly this: `FcmPushSender` is selected
//  only when credentials are configured and `LogPushSender` stands in otherwise.
//
//  This is the client half of the same arrangement. `PushTokenSource` is the seam; `NoPushToken`
//  is the stub that ships today and returns nil. When push is enabled, one implementation of one
//  protocol is added and nothing else changes.
//
//  WHAT THAT MEANS FOR THE TICKET. Its criterion — "on grant, the device registers for push and
//  the token reaches the backend, verified not assumed" — CANNOT be met by this build, and
//  pretending otherwise is the silent failure it is warning about. The upload path, the endpoint
//  and the error handling are built and tested against a fake source; the real source is one type
//  and an entitlement away. Recorded in `audit/CONFLICTS-2026-08-27.md`.
//

import Foundation
import ShowUpAPI

/// Where a device token comes from. One method, so the real one is APNs and nothing else.
protocol PushTokenSource: Sendable {
    /// The device's current push token, or nil when there is no way to get one.
    func token() async -> String?
}

/// The stub that ships today: no push entitlement, so no token.
///
/// NOT AN ERROR AND NOT SILENT. Returning nil is the honest answer, and `PushRegistration` treats
/// it as a reportable condition rather than a no-op.
struct NoPushToken: PushTokenSource {
    func token() async -> String? { nil }
}

/// What happened, so a caller can tell "registered" from "could not".
enum PushRegistrationResult: Equatable, Sendable {
    /// The token reached the backend.
    case registered
    /// There is no token source in this build. See the file header.
    case noTokenSource
    /// A token existed and the upload failed. Worth retrying; never worth blocking the flow.
    case failed(String)
}

protocol PushRegistering: Sendable {
    func register() async -> PushRegistrationResult
}

/// Sends the device token to `POST /me/push-tokens`.
///
/// NEVER BLOCKS THE FLOW. Both outcomes of the permission sheet advance to the next screen, and a
/// registration that fails is a background problem rather than something to hold a user for.
struct PushRegistration: PushRegistering {
    var source: PushTokenSource = NoPushToken()

    func register() async -> PushRegistrationResult {
        guard let token = await source.token() else {
            // RETURNED, NOT LOGGED. The project bans `print` outright, and a log line is the wrong
            // shape for this anyway: `.noTokenSource` is a value the caller and a test can both
            // see, where a console message is visible to neither. "Push silently never worked" is
            // the exact outcome this type exists to prevent, so the absence is in the type system.
            return .noTokenSource
        }
        do {
            // The GENERATED operation, by its operationId. Nothing here describes a URL or a
            // request body -- the same rule every other repository in this app follows.
            let response = try await APIAccess.client.client.registerPushToken(
                body: .json(.init(platform: .ios, token: token))
            )
            // The operation declares 200, 400, 401 and 500 -- there is no 201 to match on.
            switch response {
            case .ok:
                return .registered
            default:
                return .failed("unexpected response")
            }
        } catch {
            return .failed(String(describing: type(of: error)))
        }
    }
}
