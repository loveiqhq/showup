/*
 * LocationViewModel.kt
 * ShowUp · every rule screen 12 has, testable with no device (SHOWUP-165)
 *
 * A view model because the screen owns asynchronous work that must outlive a redraw: the OS dialog,
 * awaited from the press that raised it, and a foreground reconciler that can advance the flow by
 * itself. The screen is pure -- a state in, pixels out -- and every decision is made here.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE POSITION ADVANCES AFTER THE ANSWER, NOT WHEN THE DIALOG IS RAISED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Deliberately the opposite of 09's old rule, and the ticket explains why it is safe: "A kill
 * mid-dialog relaunches onto 12, and the arrival matrix makes that safe: not answered -> A with a
 * working button · granted -> skipped · denied -> B."
 *
 * So `location` is reported on a grant, on `Not now`, and on a silent skip -- and NOT on a denial,
 * which leaves the account at Stay reachable. Deny, force-kill, relaunch: resume lands on 12, the
 * matrix reads denied, and the user sees B. Exactly the acceptance criterion.
 *
 * The report is BEST-EFFORT and never blocks the navigation. A grant that cannot be recorded
 * because the phone is offline is still a grant: the next launch resumes onto 12, the matrix reads
 * granted, and the screen is skipped -- showing a step twice is recoverable, holding a user on a
 * permission they already gave is not.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE ACT, ONE EVENT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The dialog takes the app out of the foreground and gives it back, so the reconciler runs the
 * moment the dialog closes -- and would see the grant the dialog just produced. The ticket: "the
 * two never both fire for one act". [requesting] is what stops it: while our own dialog is up the
 * reconciler does nothing, and the dialog's answer is the one thing that decides.
 */
package com.showup.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.generated.model.FlowPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What only the host can do, because it needs an Activity. */
interface LocationHost {
    /**
     * Raise the OS dialog for fine + coarse together. The answer comes back through
     * [LocationViewModel.permissionAnswered], from the activity-result callback.
     *
     * NOT A SUSPEND FUNCTION THAT RETURNS THE ANSWER, deliberately. The dialog outlives the
     * composition that raised it: a dark-mode or font-size change rebuilds the Activity while it is
     * up, and a deferred held by the old composition is never completed -- the view model would
     * wait for ever with the CTA swallowing every press. AndroidX re-delivers the result to the
     * rebuilt Activity's launcher, and the view model survives the rebuild, so the answer is handed
     * to the one thing that is still there.
     */
    fun requestLocationPermission()

    /**
     * Open the Settings page the platform table names for [state]: our app page for B, and on
     * Android the system Location page for C. iOS opens the app page for both -- it cannot open
     * Location Services directly.
     */
    fun openSettings(state: LocationState)
}

/** What the screen draws. */
data class LocationUiState(
    val state: LocationState = LocationState.Ask,
    /**
     * Our dialog is up. The CTA is NEVER disabled and never changes -- it simply stops answering,
     * so it cannot raise a second dialog ("not tappable twice").
     */
    val requesting: Boolean = false,
)

open class LocationViewModel(
    private val access: LocationAccessReader,
    private val positions: FlowPositionSink,
    private val analytics: AnalyticsTracker? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    private var host: LocationHost? = null

    fun attach(host: LocationHost) {
        this.host = host
    }

    /** The host's Activity is going away; the view model must not keep it. */
    fun detach(host: LocationHost) {
        if (this.host === host) this.host = null
    }

    /** Once per mount -- never per state, never per recomposition. */
    private var announced = false

    /** Whether `permission_prompted` has gone out for this mount. A only, once. */
    private var prompted = false

    /** The recovery reason last reported, so a recomposition does not re-send it. */
    private var reportedRecovery: LocationUnavailableReason? = null

    /**
     * A Deny that reached this view model BEFORE the screen mounted. After a process death with the
     * OS dialog up, AndroidX delivers the answer to the rebuilt Activity's launcher as soon as it is
     * registered -- ahead of the screen's arrival. The answer is the user's, and it wins.
     */
    private var deniedBeforeMount = false

    /**
     * A GRANT that reached this view model before the screen mounted. It has already recorded the
     * position and left; the arrival that follows in the same pass must not leave a second time.
     */
    private var grantedBeforeMount = false

    /**
     * The matrix, read for the host BEFORE the screen is pushed.
     *
     * A [LocationArrival.Skip] here is the "granted on arrival" row: the host goes straight to
     * Embrace 2 and nothing about this screen is drawn or reported. The position still advances --
     * the user has passed the step, and without it a relaunch would land on 12 just to skip it
     * again.
     */
    fun decideArrival(): LocationArrival {
        grantedBeforeMount = false
        val arrival = locationArrival(access.read())
        if (arrival == LocationArrival.Skip) record()
        return arrival
    }

    /**
     * The screen is on display. `screen_viewed` ONCE PER MOUNT, not per state: A -> B in place is
     * one view of one screen.
     *
     * THE STATE IS READ HERE, NOT HANDED IN. On a fresh push this reads what the matrix just read.
     * It matters on a restore: the screen comes back after the system ended the process -- perhaps
     * while the user was granting in Settings, perhaps with the dialog's answer already delivered
     * -- and the first ON_RESUME has been and gone before this runs. A grant noticed here is the
     * silent skip: nothing about this screen is reported, and the host advances.
     */
    fun arrived(referrer: ProfileScreen?, onAdvance: () -> Unit) {
        if (announced) return
        if (grantedBeforeMount) {
            grantedBeforeMount = false
            return
        }
        val shown = if (deniedBeforeMount) {
            LocationState.Denied
        } else {
            when (val now = locationArrival(access.read())) {
                LocationArrival.Skip -> {
                    record()
                    onAdvance()
                    return
                }
                is LocationArrival.Show -> now.state
            }
        }
        deniedBeforeMount = false
        announced = true
        prompted = false
        reportedRecovery = null
        _state.value = LocationUiState(state = shown)
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.Location, referrer))
        announce(shown)
    }

    /** `permission_prompted` on A, `permission_denied_recovery_shown` on B or C -- once each. */
    private fun announce(shown: LocationState) {
        val reason = shown.unavailableReason
        if (reason == null) {
            if (!prompted) {
                prompted = true
                analytics?.report(
                    ProfileAnalytics.permissionPrompted(ProfileAnalytics.PERMISSION_LOCATION),
                )
            }
        } else if (reason != reportedRecovery) {
            reportedRecovery = reason
            analytics?.report(ProfileAnalytics.permissionDeniedRecoveryShown(reason))
        }
    }

    /**
     * `Allow location access`. Raises the OS dialog AND NOTHING ELSE. The answer arrives at
     * [permissionAnswered].
     */
    fun allowPressed() {
        val current = _state.value
        // `announced` too: during the exit animation the outgoing screen still takes taps.
        if (!announced || current.requesting || current.state != LocationState.Ask) return
        val host = host ?: return
        _state.update { it.copy(requesting = true) }
        analytics?.report(
            ProfileAnalytics.permissionOsSheetShown(ProfileAnalytics.PERMISSION_LOCATION),
        )
        host.requestLocationPermission()
    }

    /**
     * The OS dialog was answered.
     *
     * Granted (any precision, Allow Once and Only this time included) -> record -> Embrace 2.
     * Denied -> B IN PLACE, no navigation, and nothing recorded -- see the file header.
     *
     * Accepted on A whether or not this view model saw the press: after a process death mid-dialog
     * the answer is delivered to a fresh one, and it is still the user's real answer.
     */
    fun permissionAnswered(granted: Boolean, onGranted: () -> Unit) {
        if (_state.value.state != LocationState.Ask) return
        analytics?.report(
            ProfileAnalytics.permissionResult(granted, ProfileAnalytics.PERMISSION_LOCATION),
        )
        if (granted) {
            // On EVERY grant, not only before mount: an Activity rebuild can deliver the answer to a
            // view model that had announced, and the arrival that follows in the rebuilt screen
            // must not leave a second time. Every new entry clears it in `decideArrival`.
            grantedBeforeMount = true
            _state.update { it.copy(requesting = false) }
            record()
            onGranted()
        } else {
            _state.update { LocationUiState(state = LocationState.Denied) }
            if (announced) announce(LocationState.Denied) else deniedBeforeMount = true
        }
    }

    /**
     * The request came back with NO answer -- the system cancelled it (another dialog took over, or
     * the request was interrupted). Not a denial: nothing is recorded, nothing is reported, and A
     * stays with a working button.
     */
    fun permissionCancelled() {
        _state.update { it.copy(requesting = false) }
    }

    /** `Open Settings` on B or C. Opens the page and STAYS -- the return is the reconciler's job. */
    fun openSettingsPressed() {
        val shown = _state.value.state
        if (shown == LocationState.Ask) return
        analytics?.report(
            ProfileAnalytics.permissionSettingsOpened(ProfileAnalytics.PERMISSION_LOCATION),
        )
        host?.openSettings(shown)
    }

    /**
     * `Not now — ask me when I search`, on B or C only. "A has no equivalent", and this returns
     * without doing anything if a caller ever reaches it from A.
     */
    fun notNowPressed(onAdvance: () -> Unit) {
        if (!announced || _state.value.state == LocationState.Ask || _state.value.requesting) return
        analytics?.report(ProfileAnalytics.locationSkipped())
        record()
        onAdvance()
    }

    /**
     * Every foreground while the screen is up: the shared reconciler's rule, applied.
     *
     * Location turned on in C -> A (or straight past, if already granted). Permission granted in B
     * or C -> advance SILENTLY: no toast, and NOT `permission_result`, which belongs to our own
     * dialog. Nothing at all while that dialog is up.
     */
    fun foregrounded(onAdvance: () -> Unit) {
        if (!announced || _state.value.requesting) return
        when (val arrival = locationArrival(access.read())) {
            LocationArrival.Skip -> {
                record()
                onAdvance()
            }
            is LocationArrival.Show -> {
                if (arrival.state != _state.value.state) {
                    _state.update { it.copy(state = arrival.state) }
                    announce(arrival.state)
                }
            }
        }
    }

    /** The screen was left. The next push is a new mount and reports again. */
    fun left() {
        announced = false
    }

    /** Advance the saved flow position past location. Best-effort; see the file header. */
    private fun record() {
        viewModelScope.launch { positions.report(FlowPosition.location) }
    }
}
