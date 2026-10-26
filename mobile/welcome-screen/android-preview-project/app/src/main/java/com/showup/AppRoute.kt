/*
 * AppRoute.kt
 * ShowUp · the destinations of the app's NavHost (Navigation Compose)
 *
 * ADOPTED WITH THE DATE SEARCH EPIC (SHOWUP-187), at the trigger `android-preview-project/CLAUDE.md`
 * names: "the profile/discovery flows" -- and a screen reachable from two places, since the search
 * profile opens from both the Premium grid and the Likes tab.
 *
 * SHELL FIRST, as the same section prescribes. The whole existing flow -- sign-up, the tutorial,
 * profile creation, the Check-In and the tab placeholders -- is ONE destination, [AppRoute.Flow],
 * keeping its own `FlowScreen` routing, exactly as the rule keeps `SignUpFlow` one destination with
 * its own `Step`. Every view model stays where it was, above the NavHost and activity-scoped, so
 * nothing in that flow changes behaviour. The destinations the Search stories add are pushed over it
 * and popped back to it, which is the case the library exists for: a real back stack, system back
 * and saved state, without a hand-rolled router.
 *
 * Type-safe routes: each destination is a `@Serializable` object or class, never a string path.
 */
package com.showup

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    /** Sign-up, tutorial, profile creation, the Check-In and the tabs: `FlowScreen` decides. */
    @Serializable
    data object Flow : AppRoute

    /**
     * The profile, opened from the Premium grid (SHOWUP-185) or the Likes tab (SHOWUP-186). Which
     * profile, and from which list, is the search view model's (`SearchUiState.opened`): the route
     * carries nothing, so a profile the view model no longer holds -- after a process death -- is
     * popped rather than drawn empty.
     */
    @Serializable
    data object SearchProfile : AppRoute
}
