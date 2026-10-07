/*
 * ReachabilityViewModel.kt
 * ShowUp · every rule Stay reachable has, testable with no device (SHOWUP-163)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS SCREEN OWNS A VIEW MODEL AND PHOTOS DOES NOT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The rule is that a screen gets one when it owns ASYNCHRONOUS work that must outlive a redraw.
 * This one owns three: the OS dialog, push registration on a grant, and a consent write that must
 * be CONFIRMED BY THE SERVER before the flow advances. Photos and prompts hold none and stay
 * hoisted values.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ORDER IN `save()` IS THE TICKET'S, AND IT IS NOT NEGOTIABLE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   1. if push is on AND the status is not determined -- raise the OS dialog and WAIT
 *   2. on Allow -- register for push
 *   3. commit the push consent to the server and WAIT FOR CONFIRMATION
 *   4. fire `reachability_saved`
 *   5. advance to Location (12)
 *
 * NOT OPTIMISTIC (epic rule). On a save failure the user stays on 10, the choices are kept, and
 * the CTA works again. Advancing first and reconciling later is the thing that produces accounts
 * past this screen with no consent record, which is the one outcome a consent screen cannot have.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT DOES NOT HAPPEN HERE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The three interest boxes record interest and NOTHING ELSE -- no dialog, no field, no toast, no
 * navigation, no consent write, and never `consent_changed`. That is four "no"s from the ticket's
 * own Non-goals and they are all one line of code: [interestToggled] reports and flips.
 */
package com.showup.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the host has to do for the view model, because only it can. */
interface ReachabilityHost {
    /** Raise the OS notification dialog and return what the user answered. */
    suspend fun requestNotificationPermission(): Boolean

    /**
     * Open Settings, or re-prompt where the platform still allows it.
     *
     * ANDROID CAN SOMETIMES STILL ASK. The ticket: "Open Settings → Settings on iOS; on Android,
     * an in-app re-prompt if it can still ask, otherwise Settings." Which of the two happened is
     * the host's business; the event is the same either way.
     */
    fun openSettingsOrReprompt()
}

open class ReachabilityViewModel(
    private val access: NotificationAccessReader,
    private val push: PushRegistration,
    private val consent: ReachabilityRepository,
    private val analytics: AnalyticsTracker? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ReachabilityState())
    val state: StateFlow<ReachabilityState> = _state.asStateFlow()

    /** Guards `screen_viewed` and `permission_prompted` against a recomposition firing them. */
    private var announced = false

    /** The host, handed in on first composition because it needs an Activity to exist. */
    private var host: ReachabilityHost? = null

    fun attach(host: ReachabilityHost) {
        this.host = host
    }

    /** The host's Activity is going away; the view model must not keep it. */
    fun detach(host: ReachabilityHost) {
        if (this.host === host) this.host = null
    }

    /**
     * The OS dialog's answer, while one is awaited.
     *
     * HELD HERE, NOT IN THE COMPOSITION. A fold, a scheduled dark-mode switch or a font change while
     * the dialog is up rebuilds the Activity; AndroidX re-delivers the answer to the NEW launcher,
     * and a deferred remembered by the old composition was never completed -- the save waited for
     * ever with the CTA swallowing every press, on a screen with no back. The view model outlives
     * the rebuild, so the answer always reaches the save that is waiting for it. The same rule
     * `LocationHost` follows.
     */
    private var pendingNotificationAnswer: CompletableDeferred<Boolean>? = null

    /** Launches the dialog with [launch] and waits for [notificationAnswered]. */
    suspend fun awaitNotificationAnswer(launch: () -> Unit): Boolean {
        // One wait at a time: a newer one cancels the one it replaces rather than orphaning it.
        pendingNotificationAnswer?.cancel()
        val answer = CompletableDeferred<Boolean>()
        pendingNotificationAnswer = answer
        try {
            launch()
            return answer.await()
        } finally {
            if (pendingNotificationAnswer === answer) pendingNotificationAnswer = null
        }
    }

    /** The launcher's callback -- the old Activity's or the rebuilt one's. */
    fun notificationAnswered(granted: Boolean) {
        pendingNotificationAnswer?.complete(granted)
        pendingNotificationAnswer = null
    }

    /**
     * The screen was shown.
     *
     * `permission_prompted` FIRES ONLY WHEN THE STATUS IS NOT DETERMINED, which is the ticket's
     * own qualifier: it is our pre-permission surface, and a user whose status is already settled
     * is not being pre-permissioned -- they are being shown a toggle that reflects reality.
     *
     * The status is read here and on every foreground, so the toggle is never a lie. It defaults
     * ON and is forced OFF only when the OS has already said no: an opt-out cannot default to a
     * state the system will not honour.
     */
    fun arrived(referrer: ProfileScreen? = ProfileScreen.Notifications) {
        val status = access.read()
        _state.update { it.copy(pushOn = status != PermissionStatus.Denied &&
            status != PermissionStatus.Restricted) }

        if (announced) return
        announced = true
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.Reachability, referrer))
        if (status == PermissionStatus.NotDetermined) {
            analytics?.report(ProfileAnalytics.permissionPrompted())
        }
    }

    /**
     * Read on every foreground. Returning from Settings with notifications allowed shows it on.
     *
     * NOTHING AUTO-ADVANCES. The ticket is explicit -- "the user taps Save preferences again, and
     * no dialog is raised". This screen always has a working CTA, which is exactly what the old
     * 09 did not, and is why it needs no relaunch special-casing.
     */
    fun foregrounded() {
        val status = access.read()
        _state.update {
            when (status) {
                PermissionStatus.Granted -> it.copy(pushOn = true)
                PermissionStatus.Denied,
                PermissionStatus.Restricted -> it.copy(pushOn = false)
                // Still unanswered: leave the user's own choice alone.
                PermissionStatus.NotDetermined -> it
            }
        }
    }

    /**
     * The push switch moved.
     *
     * ON IS IMMEDIATE. OFF OPENS THE CONFIRM AND CHANGES NOTHING until it is confirmed -- the
     * acceptance criterion says so in as many words, and it is the difference between a
     * confirmation and a notification.
     *
     * Switching ON when the OS has already refused cannot work, so it opens Settings instead of
     * setting a toggle the system will ignore. That is the "denied guard" row of the matrix.
     */
    fun pushChanged(on: Boolean) {
        if (!on) {
            _state.update { it.copy(prompt = DeactivationPrompt.UserTurnedItOff) }
            analytics?.report(ProfileAnalytics.consentDeactivationConfirmShown())
            return
        }
        when (access.read()) {
            PermissionStatus.Denied, PermissionStatus.Restricted -> {
                analytics?.report(ProfileAnalytics.permissionSettingsOpened())
                host?.openSettingsOrReprompt()
            }
            else -> {
                _state.update { it.copy(pushOn = true) }
                analytics?.report(
                    ProfileAnalytics.consentChanged(on = true, channel = ConsentChannel.PUSH),
                )
            }
        }
    }

    /** `Keep active`, or a tap on the scrim. Identical acts, identical event. */
    fun keepActive() {
        if (_state.value.prompt == null) return
        // THE STATUS IS RE-READ, and that is not belt-and-braces -- it is the difference between
        // the two dialogs. Abandoning state C leaves push exactly as it was, because nothing has
        // happened to it. Abandoning state D (the scrim; there is no `Keep active` button there)
        // leaves a toggle that would otherwise still read ON after the OS said "Don't allow" --
        // a switch claiming a permission the device has refused. The arrival matrix already says
        // what denied looks like: "Off. Switching it on opens Settings". This says it one moment
        // earlier, using the same mapping as the foreground re-read.
        val status = access.read()
        val denied = status == PermissionStatus.Denied ||
            status == PermissionStatus.Restricted
        _state.update { it.copy(prompt = null, pushOn = it.pushOn && !denied) }
        analytics?.report(ProfileAnalytics.consentDeactivationAbandoned())
    }

    /**
     * `Open Settings` — state D only.
     *
     * NOT AN ABANDONMENT, and the ticket names the distinction. The dialog closes because the
     * user is leaving for Settings, not because they changed their mind, and push stays as it is
     * -- the OS has refused it, so there is nothing here to turn off.
     */
    fun openSettings() {
        if (_state.value.prompt == null) return
        _state.update { it.copy(prompt = null) }
        analytics?.report(ProfileAnalytics.permissionSettingsOpened())
        host?.openSettingsOrReprompt()
    }

    /**
     * `Confirm deactivation` — and it does TWO DIFFERENT THINGS, one per dialog.
     *
     * The ticket specifies them in two places that have to be read together. Under *The push
     * toggle*: "Confirm deactivation sets push off, closes the dialog, and **the user stays on
     * this screen**" -- that is state C, where the user reached for the switch themselves and may
     * well have more to do here. Under *What happens after the OS dialog*: "Confirm deactivation
     * -> push off -> **save** -> Location (12)" -- that is state D, where the dialog interrupted a
     * save that was already in flight and confirming it is the user finishing that save.
     *
     * Treating them as one act was the first build of this, and what it produced was a user who
     * pressed Save, answered the OS, confirmed the consequence, and then sat on the same screen
     * with nothing having happened, needing to press Save a second time.
     */
    fun confirmDeactivation(onAdvance: () -> Unit = {}) {
        val prompt = _state.value.prompt ?: return
        _state.update { it.copy(pushOn = false, prompt = null) }
        analytics?.report(ProfileAnalytics.consentDeactivationConfirmed())
        analytics?.report(ProfileAnalytics.consentChanged(on = false, channel = ConsentChannel.PUSH))

        if (prompt == DeactivationPrompt.UserTurnedItOff) return

        // State D. Steps (1) and (2) of the save are already behind us -- the dialog was raised
        // and answered, and a denial means there is nothing to register -- so this picks the save
        // up at (3), which is the first step that has not run.
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch { commitSave(pushOn = false, onAdvance = onAdvance) }
    }

    /**
     * An interest box was checked or unchecked.
     *
     * REPORTS AND FLIPS. That is the whole function, and the whole feature: no dialog, no field,
     * no "coming soon" toast, no navigation, no consent write, no network call.
     */
    fun interestToggled(channel: InterestChannel) {
        val now = !_state.value.isInterested(channel)
        _state.update {
            it.copy(interest = if (now) it.interest + channel else it.interest - channel)
        }
        analytics?.report(ProfileAnalytics.channelInterestChanged(channel, now))
    }

    /** The Privacy Policy link — the only link on the screen. */
    fun privacyTapped() {
        analytics?.report(ProfileAnalytics.legalLinkTapped(
                com.showup.analytics.SignUpAnalytics.Legal.PRIVACY,
                ProfileScreen.Reachability,
            ))
    }

    /**
     * Save preferences. Returns true when the flow may advance.
     *
     * NOT TAPPABLE TWICE: input is locked from the tap until navigation or failure, and the CTA
     * is never disabled -- it simply stops answering.
     */
    fun savePressed(onAdvance: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }

        viewModelScope.launch {
            val before = _state.value

            // (1) The dialog, and ONLY when push is on and nothing has been answered yet. With
            // push off, or the status already determined, no dialog appears.
            var granted = access.read() == PermissionStatus.Granted
            if (before.pushOn && access.read() == PermissionStatus.NotDetermined) {
                analytics?.report(ProfileAnalytics.permissionOsSheetShown())
                granted = host?.requestNotificationPermission() ?: false
                analytics?.report(ProfileAnalytics.permissionResult(granted))

                if (!granted) {
                    // "Don't allow" -- the deactivation confirm, with the primary labelled
                    // `Open Settings`. The save stops here: the user has a decision to make.
                    _state.update {
                        it.copy(saving = false, prompt = DeactivationPrompt.AfterOsDenial)
                    }
                    analytics?.report(ProfileAnalytics.consentDeactivationConfirmShown())
                    return@launch
                }
            }

            // (2) Register on a grant. A grant with no token looks exactly like success here,
            // which is the silent failure the build inventory names.
            if (before.pushOn && granted) push.register()

            commitSave(pushOn = before.pushOn, onAdvance = onAdvance)
        }
    }

    /**
     * Steps (3), (4) and (5) of the ticket's order, shared by both ways of reaching them.
     *
     * ONE COPY, because the state-D path is the same save resumed rather than a second save. Two
     * copies is how the "not optimistic" rule gets kept on one path and quietly lost on the other.
     *
     * The interest set is read HERE rather than captured at the tap: on the state-D path the
     * dialog sat in between, and nothing on that dialog can change it -- but reading it late is
     * correct in both cases and capturing it early is only correct in one.
     */
    private suspend fun commitSave(pushOn: Boolean, onAdvance: () -> Unit) {
        val interest = _state.value.interestValues

        // (3) The consent write, CONFIRMED before anything advances.
        if (!consent.savePushConsent(pushOn)) {
            _state.update { it.copy(saving = false, saveFailed = true) }
            return
        }

        // (4) then (5). The event is after the confirmation, never on the tap.
        analytics?.report(
            ProfileAnalytics.reachabilitySaved(
                pushOn = pushOn,
                permission = access.read(),
                interest = interest,
            ),
        )
        _state.update { it.copy(saving = false) }
        onAdvance()
    }
}
