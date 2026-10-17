//  FlowScreen.swift
//  ShowUp · where in the app the user is, and how that survives being killed
//
//  WHAT THIS REPLACED
//
//  An Int. `@State private var screen = -4`, where negative meant the pre-account flow and positive
//  meant the tutorial. Three things were wrong with it and only one was cosmetic:
//
//    * -4 was arbitrary. The comment said "negative ids" plural; exactly one was left.
//    * Card 6 was never written as a case. It was the `default:` branch, so ANY unexpected value
//      rendered ShowUpEveryTime rather than failing or falling back to a sensible screen.
//    * Nothing typed stopped `screen = 9` from compiling.
//
//  WHY THE ORDER OF THESE CASES IS LOAD-BEARING
//
//  The screen transition reads its direction from a comparison -- forward if the destination is
//  later than the origin. That was `next > screen` on Ints and is `next > current` on this enum,
//  which is why `Comparable` is derived from declaration order rather than being hand-written.
//
//  Every reachable transition was checked against the Int scheme before the change: sign-up to
//  tutorial and to home both read forward, each tutorial card forward and back, and "start over"
//  from home back to sign-up still reads BACKWARD, which it did as 7 -> -4.
//
//  Android mirrors this file as `welcome/FlowScreen.kt`, without the raw values: Kotlin enums are
//  Serializable, so `rememberSaveable` already persists them with nothing added.

import Foundation

/// Where in the app the user is. One flat list -- this flow is linear and has no side routes.
///
/// `String` raw values rather than the implicit Int ordinal, because these are persisted by
/// `@SceneStorage` and an ordinal is not a stable identity: inserting a tutorial card would
/// silently move every restored user one screen along. A name does not drift when the list does.
enum FlowScreen: String, CaseIterable, Comparable {
    /// The whole pre-account flow. `SignUpFlowView` owns its own internal step.
    case signUp

    case tutorialWelcome
    case meetInRealLife
    case matchOnAvailability
    case matchMeansMeet
    case thirtyMinutes
    case showUpEveryTime

    /// Profile creation, step 1 of "The basics".
    ///
    /// After the tutorial and before `home`, which is the real order: the user signs up, is shown
    /// how the product works, and only then is asked to build a profile.
    case profileName

    /// Profile creation, step 2.
    case profileEmail

    /// Profile creation, the code screen.
    ///
    /// Its own position but NOT its own progress segment: the bar holds at 2 because the user is
    /// on the email step until the code is confirmed. See `BasicsStep`.
    case profileVerifyEmail

    /// Profile creation, step 3. The last screen of "The basics".
    case profileDob

    /// The bridge out of "The basics" (SHOWUP-155).
    ///
    /// A position in the flow, and deliberately NOT a step: it belongs to neither progress bar,
    /// has no header and collects nothing. It sits between `profileDob` and the first screen of
    /// "The real you", which is exactly where the user meets it.
    case profileEmbrace

    /// "The real you", step 1 of 3 (SHOWUP-156).
    ///
    /// A DIFFERENT GROUP from "The basics", with its own header title and its own progress bar.
    /// See `RealYouStep`.
    case profilePhotos

    /// "The real you", step 2 of 3 (SHOWUP-158).
    case profilePrompts

    /// "The real you", step 3 of 3 and the last screen of the group (SHOWUP-161).
    ///
    /// A RESUME POINT SINCE SHOWUP-165. The step is optional and no fact told a skip from a step
    /// never reached, so a relaunch here used to land on Home. The server now keeps the saved flow
    /// position; Continue and Skip both report `media_video`. See ProfileFlow.swift.
    case profileMedia

    /// The notification permission ask (SHOWUP-162), between media and Stay reachable.
    ///
    /// NOT A STEP; a resume point since SHOWUP-165 — Continue reports `notifications`, and a resume
    /// still goes through `afterMedia`, so a determined status skips it exactly as the forward walk
    /// does. It raises no dialog any more (SHOWUP-163), so there is no dead button to relaunch onto.
    case profileNotifications

    /// Stay reachable (SHOWUP-163).
    ///
    /// NOT A STEP; a resume point since SHOWUP-165 — Save preferences reports `reachability`. It is
    /// also where the OS notification dialog lives. 09 raises nothing.
    case profileReachability

    /// The location ask (SHOWUP-165): three states, one position — A, B and C are states of this
    /// screen, never separate positions. Reached through the arrival matrix, which can skip it.
    case profileLocation

    /// The second bridge (SHOWUP-166). Never a resume point, like the first: it holds nothing and no
    /// fact says it was seen. Past location, a relaunch lands on height.
    case profileEmbraceDetails

    // "Share some details", steps 1 to 7 (SHOWUP-167 to SHOWUP-173), in walk order — which is also
    // back order: each one's back pops to the one above it.
    case profileHeight
    case profileGender
    case profileOrientation
    case profileDatingLanguage
    case profileEducation
    case profileReligion
    case profilePolitics

    // "Share some details", steps 8 to 10 (SHOWUP-174 to SHOWUP-176): their own column, not the
    // detail scaffold, and still the same walk — each back pops to the one above, and Future's
    // Continue and Skip end profile creation.
    case profileInterests
    case profileLifeNow
    case profileLifeAhead

    // The 48hrs Check-In (SHOWUP-178 to SHOWUP-183).
    //
    // THE GATE COMES FIRST, the order the transitions read: the intro's close lands on it and reads
    // as a close (backward), and every reopen from it reads forward. The steps are in walk order,
    // which is also back order. Android's `FlowScreen.kt` in the same order.

    /// Check-In 01 B, "date search not available" -- the Search tab for a user with no committed
    /// Check-In. Where the intro's and every step's close land, and where a launch lands once
    /// profile creation is done and no Check-In is active.
    case checkInGate

    /// Check-In 01 A, the intro. Future's Continue and Skip end profile creation here.
    case checkInIntro
    case checkInToday
    case checkInTomorrow
    case checkInMotivation
    case checkInVibe
    case checkInFilters
    case checkInUplift

    /// Where `Find my date` lands, and a launch with an active Check-In: Search is not built yet.
    case checkInSearch

    /// The gate's Settings tab: no Settings screen exists yet.
    case checkInSettings

    /// Where the flow ends for a NEW SIGN-IN, tutorial or returning member (SHOWUP-146). A launch
    /// with a finished profile goes to the gate or Search instead.
    case home

    private var order: Int { Self.allCases.firstIndex(of: self) ?? 0 }

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.order < rhs.order }

    /// A screen of the 48hrs Check-In or the tabs it gates -- what the foreground re-read covers.
    var isCheckIn: Bool { (Self.checkInGate...Self.checkInSettings).contains(self) }

    /// One of the six steps or the intro: a screen whose flow lives in `CheckInModel`.
    var isCheckInStep: Bool { (Self.checkInIntro...Self.checkInUplift).contains(self) }

    /// The Check-In step this screen is, or nil.
    var checkInStep: CheckInStep? {
        switch self {
        case .checkInIntro: return .intro
        case .checkInToday: return .today
        case .checkInTomorrow: return .tomorrow
        case .checkInMotivation: return .motivation
        case .checkInVibe: return .vibe
        case .checkInFilters: return .filters
        case .checkInUplift: return .uplift
        default: return nil
        }
    }
}

extension CheckInStep {
    /// The screen a step is drawn on.
    var flowScreen: FlowScreen {
        switch self {
        case .intro: return .checkInIntro
        case .today: return .checkInToday
        case .tomorrow: return .checkInTomorrow
        case .motivation: return .checkInMotivation
        case .vibe: return .checkInVibe
        case .filters: return .checkInFilters
        case .uplift: return .checkInUplift
        }
    }
}

/// THE SEARCH TAB FOLLOWS THE SERVER. "B is the Search tab whenever the user has no completed
/// Check-In": a Check-In committed elsewhere unlocks it, and a window that closed while the app was
/// away locks it again. Only between these two -- never out of the middle of a Check-In. Nil when
/// nothing moves; `active` is nil before the first read.
func checkInTabTarget(screen: FlowScreen, active: Bool?) -> FlowScreen? {
    if screen == .checkInGate && active == true { return .checkInSearch }
    if screen == .checkInSearch && active == false { return .checkInGate }
    return nil
}

// MARK: - Scene storage adapters

//  Persistence lives HERE and not on the enums themselves, deliberately.
//
//  `TutorialRouting.swift` holds the two decisions this product actually makes -- `outcomeOf` and
//  `showsTutorial` -- and it currently imports Foundation and nothing else. Keeping storage out of
//  it means it knows nothing about scenes, SwiftUI, or how anything is written down, which is the
//  property that lets it survive a navigation migration untouched.
//
//  Adapters rather than raw values on the enums for the same reason: `Entry` and `SignUpOutcome`
//  are routing vocabulary, and a `RawValue` on them would be a storage concern living in a file
//  that has no other storage concerns.

extension Entry {
    /// Stable across releases; not the case name, which is free to be renamed.
    var storageKey: String {
        switch self {
        case .createAccount: return "createAccount"
        case .logIn: return "logIn"
        }
    }

    init?(storageKey: String) {
        switch storageKey {
        case "createAccount": self = .createAccount
        case "logIn": self = .logIn
        default: return nil
        }
    }
}

extension SignUpOutcome {
    var storageKey: String {
        switch self {
        case .newAccount: return "newAccount"
        case .returningMember: return "returningMember"
        }
    }

    init?(storageKey: String) {
        switch storageKey {
        case "newAccount": self = .newAccount
        case "returningMember": self = .returningMember
        default: return nil
        }
    }
}
