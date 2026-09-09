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

    /// Where the flow ends, for both the tutorial and a returning member.
    case home

    private var order: Int { Self.allCases.firstIndex(of: self) ?? 0 }

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.order < rhs.order }
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
