//  BasicsStep.swift
//  ShowUp · where in "The basics" the user is
//
//  The same shape as `FlowScreen.swift`, and for the same reasons: a typed position with no magic
//  values, an exhaustive switch at the host, and String raw values so `@SceneStorage` can persist it.
//
//  WHY THIS IS A SECOND ENUM AND NOT MORE CASES ON FlowScreen
//
//  FlowScreen is the app's outer position — sign-up, the tutorial cards, home. "The basics" is a
//  flow WITHIN one of those, with its own progress bar, its own back rules and its own persistence.
//  Folding four more cases into FlowScreen would put a tutorial card and a profile step in one list
//  whose order drives a screen transition, and the two do not share a transition.
//
//  Mirrors `profile/BasicsStep.kt`.

import Foundation

/// A step of "The basics".
enum BasicsStep: String, CaseIterable {
    case name
    /// The email address step.
    case email
    /// The code screen. **Not a fourth segment** — it holds at 2, see `progressSegment`.
    case emailVerify = "email_verify"
    case dob

    /// Which of the three segments is filled.
    ///
    /// `emailVerify` deliberately reports 2, the same as `email`: the user is on the email step
    /// until the code is confirmed, and a bar that advanced before that would claim progress they
    /// have not made. The flow README states it twice and the taxonomy's §2 registry agrees.
    var progressSegment: Int {
        switch self {
        case .name: return 1
        case .email, .emailVerify: return 2
        case .dob: return 3
        }
    }

    /// Whether the header shows a back chevron.
    ///
    /// False on `name` only. Profile creation is mandatory once entered — the user arrives from the
    /// tutorial and there is nothing behind step 1 — so the chevron is absent AND the swipe-back
    /// gesture is suppressed. A header with no chevron that the OS can still dismiss is worse than
    /// no rule at all.
    var hasBack: Bool { self != .name }

    /// `step_id` from the taxonomy's §2 vocabulary. The raw value IS the id.
    var stepId: String { rawValue }

    /// `step_index` for the funnel.
    ///
    /// Taken from the tickets, NOT the registry: `enums.json` §2 lists step_index as "—" for all
    /// four, so there is nothing to read. Recorded as a mismatch rather than silently invented.
    var stepIndex: Int { progressSegment }
}

/// Everything "The basics" collects, plus where the user got to.
///
/// One value so persistence is one write. The flow resumes at `step`, and resuming is silent: no
/// prompt, no toast, and no error state on arrival — which is why no error flag lives here. An
/// error belongs to a press, not to a saved position.
struct BasicsState: Equatable {
    var step: BasicsStep = .name
    var firstName: String = ""
    var email: String = ""
    var marketingConsent: Bool = false
}
