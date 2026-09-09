//  ProfileAnalytics.swift
//  ShowUp · the events "The basics" emits, and the four that are BLOCKED
//
//  Names and payloads come from design_handoff_showup/tracking/events.json, family D, and the
//  vocabularies from enums.json §11 (screen registry) and §2 (step_id). Nothing here is invented
//  and nothing is a literal at a call site. Mirrors `profile/ProfileAnalytics.kt`.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  BLOCKED — four things are NOT decided, and are marked rather than guessed
//  ─────────────────────────────────────────────────────────────────────────────
//
//  B1 · consent_changed payload.  The ticket says `on: bool`. The registry defines THREE
//       properties: channel ("call"|"calendar"|"push"|"whatsapp"|"sms"|"email"), on (bool) and
//       surface (str) — and the ticket says "do not restate a payload the registry already
//       defines", so the registry governs. But NO `surface` vocabulary exists in enums.json, and
//       the registry's own trigger describes a different screen: "One of the six Stay reachable
//       toggles changed ... surface separates the profile-creation screen from Settings".
//       => `consentChanged` is written but NOT called.
//
//  B2 · referrer_screen_id.  screen_viewed gains it in the alignment brief's Step 3, unbuilt.
//
//  B3 · sensitivity_class + field_registry_version, stamped at EMIT time from the registry and
//       failing closed to class 2. That emitter does not exist; `Stamp` is the seam, not it.
//
//  B4 · email_validation_failed rule="disposable". The enum allows it; nothing specifies a list,
//       a source or a behaviour. Only "format" is emitted.
//
//  Two notes for the design side, found while reading the registry:
//    - enums.json §2 step_id values read "namev1.2", "emailv1.2", "email_verifyv1.2", "dobv1.2" —
//      the version marker is concatenated into the value. Codegen would emit those verbatim.
//    - enums.json §2 gives step_index as "—" for all four, so BasicsStep.stepIndex takes the
//      ticket's numbers. The tracking note says never to use a literal; here there is nothing else.

import Foundation

/// The §11 screen registry, as constants.
///
/// Both values ship: `screenId` is the stable key a saved funnel binds to and is never edited;
/// `screenName` is the human label searched for in the analytics tool and may be. Two vocabularies,
/// and collapsing them breaks one of the two uses.
enum ProfileScreen: String {
    case name = "profile_name"
    case email = "profile_email"
    case emailVerification = "profile_email_verification"
    case dob = "profile_dob"

    var screenId: String { rawValue }

    var screenName: String {
        switch self {
        case .name: return "ProfileName"
        case .email: return "ProfileEmail"
        case .emailVerification: return "ProfileEmailVerification"
        case .dob: return "ProfileDoB"
        }
    }
}

/// `field_id` from §1.
enum ProfileField {
    static let firstName = "first_name"
    static let email = "email"
}

/// The `rule` vocabulary shared by the validation events.
enum ValidationRule {
    static let requiredMissing = "required_missing"
    static let format = "format"
}

/// The emit-time stamp every attribute event must carry.
///
/// **BLOCKED (B3).** The brief requires `sensitivity_class` resolved from the registry at emit
/// time, failing closed to 2 for an unrecognised field_id, plus `field_registry_version`. Neither
/// the registry file nor the codegen is in this repo, so this is the seam and not the
/// implementation: a version constant and a hard-coded class per event, which is exactly what the
/// brief says not to do long-term.
enum Stamp {
    /// enums.json → registry_version at the time of writing.
    static let fieldRegistryVersion = "1.2.0"

    static func of(_ sensitivityClass: Int) -> [String: any Sendable] {
        ["sensitivity_class": sensitivityClass, "field_registry_version": fieldRegistryVersion]
    }
}

/// Family D events for "The basics".
///
/// Each function returns a name and a payload; nothing here reaches a sink. The app still has no
/// analytics sink and no consent gate — the brief is explicit that those are separate work, and
/// that "the apps have no sink at all, which is not the same thing as a gate".
enum ProfileAnalytics {
    static let screenViewedName = "screen_viewed"
    static let profileStepViewed = "profile_step_viewed"
    static let profileBuildStarted = "profile_build_started"
    static let profileStepCompleted = "profile_step_completed"
    static let profileStepSkipped = "profile_step_skipped"
    static let nameSubmitted = "name_submitted"
    static let emailSubmitted = "email_submitted"
    static let emailValidationFailed = "email_validation_failed"
    static let formValidationFailed = "form_validation_failed"
    static let consentChangedName = "consent_changed"

    /// T1, class 0. `referrer_screen_id` is B2 and travels empty until Step 3 lands.
    static func screenViewed(_ screen: ProfileScreen,
                             referrer: ProfileScreen? = nil) -> (String, [String: any Sendable]) {
        (screenViewedName, [
            "screen_id": screen.screenId,
            "screen_name": screen.screenName,
            "referrer_screen_id": referrer?.screenId ?? "",
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Not a duplicate of `screenViewed`: that answers WHICH SCREEN, this answers
    /// WHERE IN THE FLOW. `emailVerify` is the case that proves they differ — its own screen,
    /// holding at step 2 of 3.
    static func stepViewed(_ step: BasicsStep) -> (String, [String: any Sendable]) {
        (profileStepViewed, [
            "step_id": step.stepId, "step_index": step.stepIndex,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Once per profile build, on the first step only.
    static func buildStarted(entryPoint: String) -> (String, [String: any Sendable]) {
        (profileBuildStarted, ["entry_point": entryPoint].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The name is free text and is NEVER sent — char_count only.
    static func nameSubmitted(charCount: Int) -> (String, [String: any Sendable]) {
        (nameSubmitted, ["char_count": charCount].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. The address is never sent, only its domain.
    static func emailSubmitted(domain: String) -> (String, [String: any Sendable]) {
        (emailSubmitted, ["domain": domain].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0. Only `format` is emitted — "disposable" is B4.
    static func emailValidationFailed(
        rule: String = ValidationRule.format
    ) -> (String, [String: any Sendable]) {
        (emailValidationFailed, ["rule": rule].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 0.
    static func stepCompleted(_ step: BasicsStep,
                              timeOnStepSeconds: Int) -> (String, [String: any Sendable]) {
        (profileStepCompleted, [
            "step_id": step.stepId, "time_on_step_s": timeOnStepSeconds,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T1, class 0. Fires on the REFUSED PRESS, not on render — typing three characters and
    /// stopping emits nothing.
    static func formValidationFailed(fieldId: String, screen: ProfileScreen,
                                     step: BasicsStep) -> (String, [String: any Sendable]) {
        (formValidationFailed, [
            "field_id": fieldId,
            "rule": ValidationRule.requiredMissing,
            "screen_id": screen.screenId,
            "step_id": step.stepId,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// T2, class 1. **BLOCKED (B1) — written, deliberately not called.**
    ///
    /// `surface` has no vocabulary in enums.json and the registry's trigger describes the Settings
    /// "Stay reachable" toggles rather than this row. Calling it with a guessed surface would put
    /// an unowned string into a consent record, which is the one payload where an invented value is
    /// least acceptable. The toggle works; only its event is withheld.
    static func consentChanged(on: Bool, surface: String) -> (String, [String: any Sendable]) {
        (consentChangedName, [
            "channel": "email", "on": on, "surface": surface,
        ].merging(Stamp.of(1)) { a, _ in a })
    }

    /// T2, class 0. **BLOCKED** — the skip path itself is an open question in ticket 02.
    static func stepSkipped(_ step: BasicsStep) -> (String, [String: any Sendable]) {
        (profileStepSkipped, ["step_id": step.stepId].merging(Stamp.of(0)) { a, _ in a })
    }
}
