/*
 * FlowNavigator.kt
 * ShowUp · where the flow is, held where an Activity rebuild cannot orphan it
 *
 * WHY THIS IS NOT `rememberSaveable` ANY MORE
 *
 * Every screen that saves something hands its view model a closure that navigates when the save
 * comes back: `basics.sendCode(onSent = { screen = ... })`, `details.continuePressed(step) {
 * goTo(next) }`, the reachability save into location. The closure captured the `MutableState`
 * that `rememberSaveable` created for THAT composition. A dark-mode switch, a font-size change or
 * a fold while the request was in flight rebuilt the Activity, restored the screen into a NEW state
 * object, and the save then wrote its navigation into the old, dead one: the user stayed on the
 * step with the answer saved, and a second press saved and reported it twice.
 *
 * A view model outlives the rebuild, so a closure that writes here writes to the state the new
 * composition is reading. `SavedStateHandle.saveable` keeps the process-death half that
 * `rememberSaveable` gave: the same bundle, the same restore.
 */
package com.showup.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable

@OptIn(SavedStateHandleSaveableApi::class)
class FlowNavigator(handle: SavedStateHandle) : ViewModel() {

    /** The screen on display. */
    var screen by handle.saveable { mutableStateOf(FlowScreen.SignUp) }

    /**
     * Where the user came from, for `referrer_screen_id`: "set by the navigation, never hard-coded
     * per screen". Height reached from Embrace 2 and height reached by a back from gender report
     * different referrers, and only the navigation knows which.
     */
    var cameFrom by handle.saveable { mutableStateOf<FlowScreen?>(null) }

    /**
     * A navigation that records where it came from. Going to the screen already shown does
     * nothing: a second exit from one screen -- an answer delivered to a rebuilt Activity, say --
     * must not make that screen its own referrer.
     */
    fun goTo(next: FlowScreen) {
        if (next == screen) return
        cameFrom = screen
        screen = next
    }
}
