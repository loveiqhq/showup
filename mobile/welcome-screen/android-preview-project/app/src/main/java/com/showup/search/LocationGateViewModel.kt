/*
 * LocationGateViewModel.kt
 * ShowUp · Search 04, the location gate's rules (SHOWUP-188), testable with no device
 *
 * The gate is Profile 12's screen in its app context; its RULES are not Profile 12's. Profile 12 is a
 * sign-up step: it records a flow position, offers `Not now`, and fires `permission_prompted` and
 * `permission_denied_recovery_shown`. The gate does none of that -- it blocks the Search tab until
 * location is usable, and reports `location_gate_shown` instead. So it has its own view model, and
 * reuses everything Profile 12 built that is not a rule: the status reader ([LocationAccessReader]),
 * the arrival matrix ([locationArrival], which IS the ticket's state table), the OS request and the
 * two Settings intents ([LocationHost]).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DECIDED BEFORE THE SEARCH TAB RENDERS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * "No frame of empty Search or of the wrong state." The status is read when this view model is made
 * and again on every foreground ([refresh]), whatever screen is up -- it can only change outside the
 * app, or through our own dialog. So when the Search tab composes, [LocationGateUiState.blocked] is
 * already the answer, and the tab draws the gate or Search from its first frame.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE ACT, ONE EVENT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Profile 12's rule, kept: while our own dialog is up the foreground re-read does nothing, so a
 * grant from the dialog is `permission_result` and never also a lift noticed "outside". A grant
 * noticed on a foreground is the shared reconciler's `permission_status_changed`, never ours.
 */
package com.showup.search

import androidx.lifecycle.ViewModel
import com.showup.analytics.AnalyticsTracker
import com.showup.profile.LocationAccessReader
import com.showup.profile.LocationArrival
import com.showup.profile.LocationHost
import com.showup.profile.LocationState
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import com.showup.profile.gateReason
import com.showup.profile.report
import com.showup.profile.locationArrival
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the Search tab, and the gate pushed over a Likes profile, draw. */
data class LocationGateUiState(
    /** Null: location is usable and Search shows. Otherwise the state the gate draws (A, B or C). */
    val blocked: LocationState? = null,
    /** Our dialog is up. The CTA is never disabled; it stops answering, so it cannot fire twice. */
    val requesting: Boolean = false,
    /**
     * D: `Confirm date` on a profile opened from the Likes tab found location unavailable, and the
     * gate is pushed over that profile. Cleared by the back pill, Android back, or the gate lifting.
     */
    val overProfile: Boolean = false,
)

class LocationGateViewModel(
    private val access: LocationAccessReader,
    private val analytics: AnalyticsTracker? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationGateUiState(blocked = read()))
    val state: StateFlow<LocationGateUiState> = _state.asStateFlow()

    private var host: LocationHost? = null

    fun attach(host: LocationHost) {
        this.host = host
    }

    /** The host's Activity is going away; the view model must not keep it. */
    fun detach(host: LocationHost) {
        if (this.host === host) this.host = null
    }

    /** The gate is on screen: `screen_viewed` once per mount, never per state. */
    private var mounted = false

    /** The reason last reported while mounted, so only a CHANGE reports again. */
    private var reported: LocationState? = null

    /**
     * The gate lifted on the Search tab while it was up. The Search screen that replaces it was
     * reached FROM the gate: its `screen_viewed` names `search_location_gate` as the referrer.
     */
    private var liftedOnTab = false

    /** The matrix, now: the state to block with, or null when location is usable. */
    private fun read(): LocationState? = when (val arrival = locationArrival(access.read())) {
        LocationArrival.Skip -> null
        is LocationArrival.Show -> arrival.state
    }

    /**
     * Every foreground, whatever is on screen, and every arrival on the Search tab: the status read
     * again. Usable -> the gate lifts (D: back to the profile). Blocked for a new reason while up ->
     * the state swaps in place and is reported. NOTHING while our own dialog is up.
     */
    fun refresh() {
        if (_state.value.requesting) return
        apply(read())
    }

    private fun apply(now: LocationState?) {
        val was = _state.value
        if (now == null) {
            if (was.blocked != null && mounted && !was.overProfile) liftedOnTab = true
            _state.value = LocationGateUiState()
            return
        }
        _state.update { it.copy(blocked = now) }
        if (mounted && now != reported) announce(now)
    }

    private fun announce(shown: LocationState) {
        reported = shown
        analytics?.report(ProfileAnalytics.locationGateShown(shown.gateReason))
    }

    /**
     * The gate is on display -- the Search tab blocked (A-C), or pushed over a Likes profile (D).
     * [referrer] is the screen it was reached from: D's is `search_profile`.
     */
    fun mounted(referrer: ProfileScreen?) {
        val shown = _state.value.blocked ?: return
        if (mounted) return
        mounted = true
        liftedOnTab = false
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.SearchLocationGate, referrer))
        announce(shown)
    }

    /** The gate left the screen -- a tab, the back pill, or the lift. The next showing reports again. */
    fun left() {
        mounted = false
        reported = null
    }

    /**
     * Asked once by the Search screen that replaces the gate: was it reached from the gate? True
     * only right after a lift on the Search tab, and only once.
     */
    fun takeLift(): Boolean = liftedOnTab.also { liftedOnTab = false }

    /** `Allow location access` on A: raises the OS dialog AND NOTHING ELSE. */
    fun allowPressed() {
        val now = _state.value
        if (!mounted || now.requesting || now.blocked != LocationState.Ask) return
        val host = host ?: return
        _state.update { it.copy(requesting = true) }
        analytics?.report(
            ProfileAnalytics.permissionOsSheetShown(ProfileAnalytics.PERMISSION_LOCATION, ProfileScreen.SearchLocationGate),
        )
        host.requestLocationPermission()
    }

    /**
     * The OS dialog was answered. Granted at any precision (Allow Once and Only this time included)
     * -> the gate lifts and Search loads in place, no toast; D -> back to the profile. Denied -> B IN
     * PLACE, no navigation.
     *
     * Accepted on A whether or not this view model saw the press: an Activity rebuilt while the
     * dialog was up delivers the answer to the launcher it registers again, and it is still the
     * user's real answer -- Profile 12's rule.
     */
    fun permissionAnswered(granted: Boolean) {
        if (_state.value.blocked != LocationState.Ask) return
        analytics?.report(
            ProfileAnalytics.permissionResult(granted, ProfileAnalytics.PERMISSION_LOCATION, ProfileScreen.SearchLocationGate),
        )
        _state.update { it.copy(requesting = false) }
        // Read rather than assumed: a grant with the phone's Location switched off still blocks (C).
        apply(if (granted) read() else LocationState.Denied)
    }

    /** No answer -- the system cancelled the request. Not a denial: A keeps a working button. */
    fun permissionCancelled() {
        _state.update { it.copy(requesting = false) }
    }

    /** `Open Settings` on B or C: the page the routing table names, and the gate STAYS. */
    fun openSettingsPressed() {
        val shown = _state.value.blocked ?: return
        if (!mounted || shown == LocationState.Ask) return
        analytics?.report(
            ProfileAnalytics.permissionSettingsOpened(ProfileAnalytics.PERMISSION_LOCATION, ProfileScreen.SearchLocationGate),
        )
        host?.openSettings(shown)
    }

    /**
     * D: `Confirm date` on a profile opened from the Likes tab, checked BEFORE Be sure opens. True
     * when location is unavailable and the gate goes over the profile instead -- Be sure does not
     * open and nothing is sent. False: location is usable, and the caller opens Be sure itself.
     */
    fun confirmNeedsLocation(): Boolean {
        refresh()
        if (_state.value.blocked == null) return false
        _state.update { it.copy(overProfile = true) }
        return true
    }

    /** D's back pill, Android back or swipe-back: back to the profile, the date not confirmed. */
    fun backToProfile() {
        _state.update { it.copy(overProfile = false) }
    }
}
