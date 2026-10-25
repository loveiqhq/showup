//
//  AppRoute.swift
//  ShowUp · the destinations of the app's NavigationStack
//
//  The Swift twin of `AppRoute.kt`. ADOPTED WITH THE DATE SEARCH EPIC (SHOWUP-187), at the trigger
//  `ios-app/CLAUDE.md` names: "the profile/discovery flows" -- and a screen reachable from two places,
//  since the search profile opens from both the Premium grid and the Likes tab.
//
//  SHELL FIRST, as the same section prescribes. The whole existing flow -- sign-up, the tutorial,
//  profile creation, the Check-In and the tab placeholders -- is the stack's ROOT, keeping its own
//  `FlowScreen` switch and its `@SceneStorage` restoration, exactly as the rule keeps `SignUpFlowView`
//  one destination with its own `Step`. Nothing in it changes behaviour. The destinations the Search
//  stories add are pushed over it and popped back to it: a real back stack, the system swipe-back
//  and the back button's semantics, without a hand-rolled router.
//
//  Each Search story adds the destination it needs, with its `navigationDestination`.
//

import Foundation

enum AppRoute: Hashable, Sendable {
    /// The profile, opened from the Premium grid (SHOWUP-185) or the Likes tab (SHOWUP-186). Which
    /// profile, and from which list, is the search model's (`SearchUiState.opened`): the route carries
    /// nothing, so a profile the model no longer holds -- after the process ended -- is popped rather
    /// than drawn empty.
    case searchProfile
}
