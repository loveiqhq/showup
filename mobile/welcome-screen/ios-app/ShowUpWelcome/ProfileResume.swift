//
//  ProfileResume.swift
//  ShowUp · where a half-finished profile picks up (flow README rule 4a)
//
//  The Swift twin of `profile/ProfileResume.kt`. Read that file's header for the argument — in
//  particular why the progress is DERIVED FROM THE SERVER rather than mirrored on the phone, and
//  why this returns a decision rather than a route.
//
//  The rule, in its own words: "on launch, an account with an incomplete profile routes straight to
//  its LAST INCOMPLETE STEP, with everything already entered still present. Resuming is SILENT."
//

import Foundation
import ShowUpAPI

/// The facts `GET /me/profile/progress` returns.
///
/// Deliberately not "which step" — the server supplies what the decision is made from and the
/// client makes it, exactly as `TutorialRouting` already does.
struct ProfileProgress: Equatable, Sendable {
    var displayName: String?
    var email: String?
    var emailVerified = false
    var hasDateOfBirth = false
    var photoCount = 0
    var promptCount = 0
    /// The two MANDATORY "Share some details" steps (Profiles 15, 16). A mandatory step cannot be
    /// passed without an answer, so the answer is the evidence — like the name.
    var hasGender = false
    var hasOrientation = false
    /// The furthest step completed or skipped after prompts, or nil before any — see
    /// ProfileFlow.swift. The only evidence a SKIPPABLE step was passed.
    var flowPosition: String?
}

/// Where in profile creation an account has got to.
enum ResumePoint: CaseIterable {
    case name
    case email
    case verifyEmail
    case dob
    case photos
    case prompts
    // After prompts the position is no longer derived from facts alone — see ProfileFlow.swift.
    case media
    /// The notification ask. Its own skip guard still applies at routing time (`afterMedia`).
    case notifications
    case reachability
    /// The location ask. Its arrival matrix still applies at routing time (SHOWUP-165).
    case location
    case height
    case gender
    case orientation
    case datingLanguage
    case education
    case religion
    case politics
    /// Everything the flow asks for is on the account.
    case done
}

/// The last incomplete step.
///
/// IN FLOW ORDER, first gap wins. Written as a chain rather than a table because the order IS the
/// rule: each step is only reachable once the one before it is satisfied, which is what makes
/// "resume" and "walk forwards" land in the same place.
///
/// THE BRIDGE IS NEVER A RESUME POINT. SHOWUP-155's acceptance criteria say so directly —
/// "relaunching lands on photos and not on this bridge" — because the bridge is not a step and
/// holds nothing: there is no fact on the account that says whether it was seen.
func resumePoint(_ progress: ProfileProgress) -> ResumePoint {
    // A blank name is no name. The screen refuses whitespace, but a value that arrived some other
    // way must not skip the step.
    if (progress.displayName ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return .name
    }
    if (progress.email ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return .email
    }
    // An address that has not been confirmed holds at the code screen, not at the email screen —
    // the address is already stored, and sending the user back to re-type it would lose the
    // challenge that is already in flight. The progress bar holds at 2 for the same reason.
    if !progress.emailVerified { return .verifyEmail }
    if !progress.hasDateOfBirth { return .dob }
    if progress.photoCount < photosRequired { return .photos }
    if progress.promptCount < promptsRequired { return .prompts }

    // ── past prompts: the stored position ────────────────────────────────────
    //
    // Until SHOWUP-165 the chain ended here with `.done`, so a relaunch anywhere after prompts went
    // home and silently skipped media, both asks and every detail step.
    let at = progress.flowPosition
    if hasNotReached(at, .media_video) { return .media }
    if hasNotReached(at, .notifications) { return .notifications }
    if hasNotReached(at, .reachability) { return .reachability }
    if hasNotReached(at, .location) { return .location }
    // NO EMBRACE 2. The second bridge is never a resume point, for the reason the first is not.
    if hasNotReached(at, .height) { return .height }
    // MANDATORY STEPS CHECK THE ANSWER TOO: a position past gender with no gender stored — an old
    // free-text value the migration could not map — must still come back for it.
    if hasNotReached(at, .gender) || !progress.hasGender { return .gender }
    if hasNotReached(at, .orientation) || !progress.hasOrientation { return .orientation }
    if hasNotReached(at, .dating_language) { return .datingLanguage }
    if hasNotReached(at, .education) { return .education }
    if hasNotReached(at, .religion) { return .religion }
    if hasNotReached(at, .politics) { return .politics }
    return .done
}
