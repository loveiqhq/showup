/*
 * NotificationsViewModel.kt
 * ShowUp · every rule the notification ask has, testable with no device (SHOWUP-162)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY A VIEW MODEL FOR A SCREEN WITH ONE BUTTON
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The project's rule is that a screen holds no state and gets a view model only when it owns
 * ASYNCHRONOUS work that must outlive a redraw. This one does: granting the permission kicks off a
 * push-token registration, and the ticket is explicit that a grant without a registration is a
 * silent failure that looks exactly like success.
 *
 * The rest follows from having it. Four tracking events, an ordering rule between two of them, a
 * "not tappable twice" rule and a foreground re-read all become plain functions on a plain object
 * instead of lambdas in the host -- which is what lets `NotificationRulesTest` prove them with no
 * OS sheet, no device and no permission.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IT DELIBERATELY DOES NOT DO
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * IT DOES NOT DECIDE WHETHER THE SCREEN IS SHOWN. That is [shouldShowAsk], a pure function the
 * host calls BEFORE pushing the screen -- the ticket requires the two skip cases to be settled
 * before mount so the user never sees it appear and navigate away. A view model that answered the
 * question would already be too late, because it would have to exist first.
 *
 * IT DOES NOT FIRE `permission_status_changed`. That event belongs to the shared reconciler and
 * must never double up with `permission_result` for one act; this screen's foreground re-read is
 * the navigation half only. See [statusIsNowDetermined].
 */
package com.showup.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class NotificationsViewModel(
    private val access: NotificationAccessReader,
    private val push: PushRegistration,
    private val analytics: AnalyticsTracker? = null,
) : ViewModel() {

    /**
     * True from the moment the sheet is raised.
     *
     * NEVER LOWERED, because every path out of the sheet leaves this screen. The CTA is never
     * disabled and never changes appearance -- it simply stops answering, which is what "not
     * tappable twice" means when a second request is a silent platform no-op and a double tap
     * would otherwise read as a hang.
     */
    private val _sheetUp = MutableStateFlow(false)
    val sheetUp: StateFlow<Boolean> = _sheetUp.asStateFlow()

    /** Guards `permission_prompted` against a recomposition firing it twice. */
    private var announced = false

    /**
     * The screen was really shown.
     *
     * `permission_prompted` is OUR pre-permission surface, and events.json is explicit that it
     * "does not fire when the screen is skipped because the OS status is already determined". The
     * skip cases never construct this, so the guarantee is structural rather than a condition.
     */
    fun arrived() {
        if (announced) return
        announced = true
        analytics?.report(
            ProfileAnalytics.screenViewed(
                ProfileScreen.Notifications,
                referrer = ProfileScreen.Media,
            ),
        )
        analytics?.report(ProfileAnalytics.permissionPrompted())
    }

    /**
     * The CTA was pressed. Returns whether the caller should actually raise the sheet.
     *
     * FALSE ON A SECOND PRESS, and nothing is reported for it -- a tap that raises no sheet is not
     * a sheet being shown, and `permission_os_sheet_shown` counting it would inflate the
     * denominator of the grant rate with taps that never reached the platform.
     */
    fun enablePressed(): Boolean {
        if (_sheetUp.value) return false
        _sheetUp.value = true
        analytics?.report(ProfileAnalytics.permissionOsSheetShown())
        return true
    }

    /**
     * The user answered.
     *
     * BOTH OUTCOMES ADVANCE -- the caller navigates either way and the user never lands back here.
     * On a grant the device registers for push, and that registration is fire-and-forget: it must
     * not hold the user on a screen, and its failure is a background problem. See
     * [PushRegistration] for why this build cannot actually complete it.
     */
    fun answered(granted: Boolean) {
        analytics?.report(ProfileAnalytics.permissionResult(granted))
        if (granted) viewModelScope.launch { push.register() }
    }

    /**
     * Read on every foreground while the screen is mounted.
     *
     * THE SCREEN IS NEVER A TERMINAL STATE. A user who backgrounds the sheet, turns notifications
     * on in Settings by hand and comes back would otherwise be looking at a button that can no
     * longer raise anything -- the system dialog is shown once per install -- on a screen with no
     * skip, no back and no close. True here means "advance silently".
     */
    fun statusIsNowDetermined(): Boolean = !shouldShowAsk(access.read())
}
