/*
 * BasicsViewModel.kt
 * ShowUp · the first screen group in this app that owns asynchronous work
 *
 * WHY A VIEWMODEL, AND WHY ONLY NOW
 *
 * The Android CLAUDE.md has said from the start: "ViewModels are not used yet, deliberately -- no
 * screen owns asynchronous work. Introduce one the moment a screen loads, uploads or retries;
 * then it exposes StateFlow and the screen collects it lifecycle-aware."
 *
 * SHOWUP-153 is that moment. It sends a code, waits for a server, counts down against a server
 * timestamp and retries. Hoisted `rememberSaveable` cannot own that: a request in flight has to
 * outlive a recomposition and be cancelled with the screen, and `viewModelScope` is the thing
 * that does both.
 *
 * WHAT STILL IS NOT HERE
 *
 * No screen state. The four screens remain functions from values to pixels -- they take a
 * [BasicsUiState] and lambdas and render. That property is what keeps ScreenFitTest at 17 sizes
 * and 40 previews possible, and it survives this change intact.
 */
package com.showup.profile

import androidx.lifecycle.ViewModel
import com.showup.analytics.AnalyticsTracker
import com.showup.api.MAX_VERIFY_ATTEMPTS
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Everything the two async screens render.
 *
 * One object rather than eight flows: the screens read several of these together, and separate
 * flows would let them recompose against a half-updated picture.
 */
data class BasicsUiState(
    val email: String = "",
    val codeDigits: String = "",
    /** Submissions against the CURRENT code. Reset by a resend, because that is a new challenge. */
    val attempts: Int = 0,
    val lastSubmitRefused: Boolean = false,
    /** Server-owned. Null until a code has been sent. */
    val expiresAt: OffsetDateTime? = null,
    val resendAvailableAt: OffsetDateTime? = null,
    /**
     * The code, when a development server sent it back. See [SendCodeResult.Sent.devCode].
     *
     * Held in state rather than read at the call site because a resend replaces it, and a strip
     * still showing the previous code would be worse than no strip at all.
     */
    val devCode: String? = null,
    val cooldownSeconds: Int = 0,
    /** True while a request is in flight. The CTA is inert and says so. */
    val busy: Boolean = false,
    /** Set when a call failed for a reason the screen has no specific state for. */
    val transportFailed: Boolean = false,
    /** 400 from the send: the address belongs to someone else. Copy is NOT decided — see below. */
    val emailInUse: Boolean = false,
    /** Bumped on every refused submit, so a second failure shakes again. */
    val shakeKey: Int = 0,

    val dob: String = "",
    val hideAge: Boolean = false,
    val dobAttempted: Boolean = false,
    /** 400 from the profile write: the server's own 18 check disagreed with ours. */
    val serverRejectedAge: Boolean = false,
) {
    /**
     * Whether the code has died of age.
     *
     * Computed from the server's own timestamp rather than a local countdown, so a device that
     * slept through the expiry still gets it right on wake.
     */
    fun expired(now: OffsetDateTime): Boolean =
        expiresAt?.let { !now.isBefore(it) } ?: false

    /**
     * Named `failure`, not `verifyState`, deliberately: a member with the same name as the
     * top-level function would resolve to ITSELF on the line below and recurse until the stack
     * ran out. Kotlin prefers the member, silently.
     */
    fun failure(now: OffsetDateTime): VerifyState = verifyState(
        attempts = attempts,
        maxAttempts = MAX_VERIFY_ATTEMPTS,
        expired = expired(now),
        lastSubmitRefused = lastSubmitRefused,
    )
}

class BasicsViewModel(
    private val repo: BasicsRepository,
    /**
     * Where "The basics" reports (family D, SHOWUP-150 to 155). Null in a test that does not care.
     *
     * Until 8 October 2026 this view model took no tracker and nothing in the basics fired: the
     * events were specified and their builders written, and no call site existed. The whole top
     * of the profile funnel -- `profile_build_started` included -- was invisible.
     */
    private val analytics: AnalyticsTracker? = null,
    /** For `time_on_step_s`. Injected so a test can step through time. */
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(BasicsUiState())
    val state: StateFlow<BasicsUiState> = _state.asStateFlow()

    // ── tracking ─────────────────────────────────────────────────────────────────
    //
    // Every decision the events report is made here or reported here by the host: arrivals,
    // accepted and refused presses, the code's verdict, the date's states. The payloads are the
    // builders' (ProfileAnalytics), so nothing typed at a call site can drift from events.json.

    /** When each step was last arrived at, for `time_on_step_s`. */
    private val arrivedAt = mutableMapOf<BasicsStep, Long>()

    /** `profile_build_started` fires once per build, on the first arrival at the name step. */
    private var buildStarted = false

    /** How many times `Send a new code` delivered one, for `email_code_resent.attempt_no`. */
    private var resends = 0

    /**
     * The date-of-birth state last shown. `dob_validation_failed` and `age_gate_failed` fire on the
     * TRANSITION into C or D -- "once per transition, not per keystroke while the state holds".
     */
    private var dobShown = DobShown.Open

    /** How the field last read its digits -- handed in with every keystroke by the host. */
    private var dobOrder = DateOrder.MonthFirst

    private enum class DobShown { Open, Valid, Impossible, UnderAge }

    private fun dobShownFor(parsed: DobResult) = when (parsed) {
        DobResult.Incomplete -> DobShown.Open
        DobResult.Impossible -> DobShown.Impossible
        is DobResult.Valid -> if (parsed.age < MINIMUM_AGE) DobShown.UnderAge else DobShown.Valid
    }

    private fun report(event: Pair<String, Map<String, Any>>) {
        analytics?.report(event)
    }

    private fun secondsOn(step: BasicsStep): Int =
        (((clock() - (arrivedAt[step] ?: clock())) / 1000L).toInt()).coerceAtLeast(0)

    /**
     * A step of the basics is on screen. `screen_viewed` and `profile_step_viewed` -- two events,
     * and not duplicates: one answers which screen, the other where in the flow. The name step
     * also starts the build, once.
     */
    fun arrived(step: BasicsStep, referrer: ProfileScreen?) {
        arrivedAt[step] = clock()
        report(ProfileAnalytics.screenViewed(step.trackedScreen, referrer))
        report(ProfileAnalytics.stepViewed(step))
        if (step == BasicsStep.Name && !buildStarted) {
            buildStarted = true
            report(ProfileAnalytics.buildStarted(AppEntryPoint.COLD))
        }
        // The state already on screen is the baseline: arriving with it is not a transition.
        if (step == BasicsStep.Dob) dobShown = dobShownFor(parseDob(_state.value.dob, dobOrder))
    }

    /**
     * The bridge out of the basics (SHOWUP-155): `screen_viewed` and `embrace_bridge_viewed`, and
     * NOTHING else -- not a step, so no step events, and the build started four screens ago.
     */
    fun embraceArrived(referrer: ProfileScreen?) {
        report(ProfileAnalytics.screenViewed(ProfileScreen.EmbraceBuild, referrer))
        report(ProfileAnalytics.embraceBridgeViewed(EmbraceVariant.BUILD_PROFILE))
    }

    /** Continue with a valid name. `char_count` only -- the name itself is never sent. */
    fun nameAccepted(name: String) {
        report(ProfileAnalytics.nameSubmitted(name.trim().length))
        report(ProfileAnalytics.stepCompleted(BasicsStep.Name, secondsOn(BasicsStep.Name)))
    }

    /** Continue pressed with no name -- on the refused press, never on render. */
    fun nameRefused() {
        report(ProfileAnalytics.formValidationFailed(ProfileField.FIRST_NAME, ProfileScreen.Name, BasicsStep.Name))
    }

    /** Continue refused on the email step: empty is `required_missing`, malformed is `format`. */
    fun emailRefused(empty: Boolean) {
        report(
            if (empty) {
                ProfileAnalytics.formValidationFailed(ProfileField.EMAIL, ProfileScreen.Email, BasicsStep.Email)
            } else {
                ProfileAnalytics.emailValidationFailed()
            },
        )
    }

    /** The marketing row toggled -- in BOTH directions, never opt-out only. */
    fun marketingConsentChanged(on: Boolean) {
        report(ProfileAnalytics.consentChanged(on))
    }

    /** Held so a resend can cancel the previous countdown rather than race it. */
    private var ticker: Job? = null

    fun setEmail(value: String) = _state.update { it.copy(email = value, emailInUse = false) }
    fun setDigits(value: String) =
        _state.update { it.copy(codeDigits = value, lastSubmitRefused = false) }
    /**
     * @param order how the field reads the eight digits -- the same order the screen parses with, so
     *   the states reported are the states shown.
     */
    fun setDob(value: String, order: DateOrder = DateOrder.MonthFirst) {
        _state.update {
            it.copy(
                dob = value,
                serverRejectedAge = false,
                // The incomplete error clears the moment the eighth digit lands.
                dobAttempted = if (dobDigits(value).length == 8) false else it.dobAttempted,
            )
        }
        dobOrder = order
        val parsed = parseDob(value, order)
        val shown = dobShownFor(parsed)
        if (shown != dobShown) {
            when (shown) {
                DobShown.Impossible -> report(ProfileAnalytics.dobValidationFailed())
                DobShown.UnderAge -> report(ProfileAnalytics.ageGateFailed((parsed as DobResult.Valid).age))
                DobShown.Open, DobShown.Valid -> Unit
            }
            dobShown = shown
        }
    }
    fun setHideAge(value: Boolean) {
        // Opt-out only: the TICK reports, the untick does not -- absence means displayed.
        if (value && !_state.value.hideAge) report(ProfileAnalytics.ageDisplayOptedOut())
        _state.update { it.copy(hideAge = value) }
    }

    /**
     * Continue refused on the date step. Only an INCOMPLETE date reports here: an impossible or
     * under-18 date already reported on the way into that state, and the press adds nothing.
     */
    fun dobRefused(result: DobResult) {
        markDobAttempted()
        if (result == DobResult.Incomplete) {
            report(ProfileAnalytics.formValidationFailed(ProfileField.AGE, ProfileScreen.Dob, BasicsStep.Dob))
        }
    }
    fun markDobAttempted() = _state.update { it.copy(dobAttempted = true) }
    fun clearDob() = _state.update { it.copy(dob = "", dobAttempted = false) }

    /**
     * Sends a code, from the email step's Continue and from the resend link.
     *
     * The ticket is explicit that the send is triggered by leaving the email screen and NOT by
     * arriving at the code screen — which is also what stops a relaunch from silently issuing a
     * new code while the one in the user's inbox is still good.
     */
    fun sendCode(onSent: () -> Unit = {}) = send(resend = false, onSent = onSent)

    /** `Send a new code` on the code screen -- the same send, reported as a resend. */
    fun resendCode() = send(resend = true, onSent = {})

    private fun send(resend: Boolean, onSent: () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, transportFailed = false, emailInUse = false) }
        viewModelScope.launch {
            when (val result = repo.sendCode(_state.value.email)) {
                is SendCodeResult.Sent -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            codeDigits = "",
                            attempts = 0,
                            lastSubmitRefused = false,
                            expiresAt = result.expiresAt,
                            resendAvailableAt = result.resendAvailableAt,
                            devCode = result.devCode,
                        )
                    }
                    startTicker()
                    // Reported on DELIVERY, not on the press: a press the server refused or the
                    // network lost sent nothing, and a retry would otherwise count twice.
                    if (resend) {
                        resends += 1
                        report(ProfileAnalytics.emailCodeResent(resends))
                    } else {
                        // The domain only -- never the address.
                        report(ProfileAnalytics.emailSubmitted(_state.value.email.substringAfterLast('@').trim().lowercase()))
                        report(ProfileAnalytics.stepCompleted(BasicsStep.Email, secondsOn(BasicsStep.Email)))
                    }
                    onSent()
                }
                // The server refused because its own cooldown has not elapsed. The client's
                // countdown said otherwise, so the client's is what is wrong: adopt the server's
                // by leaving the resend blocked and letting the ticker re-read it.
                SendCodeResult.TooSoon -> _state.update { it.copy(busy = false) }
                SendCodeResult.EmailInUse -> _state.update { it.copy(busy = false, emailInUse = true) }
                is SendCodeResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
            }
        }
    }

    fun verify(onVerified: () -> Unit) {
        val s = _state.value
        if (s.busy || !canSubmitCode(s.codeDigits, s.failure(now()))) return
        _state.update { it.copy(busy = true, transportFailed = false) }
        // Which attempt at THIS code -- the count a new send resets, and the server caps.
        val attemptNo = s.attempts + 1
        viewModelScope.launch {
            val result = repo.verifyCode(_state.value.codeDigits)
            // A verdict either way; a transport failure is no verdict and reports nothing.
            if (result !is VerifyCodeResult.Failed) {
                report(ProfileAnalytics.emailCodeSubmitted(attemptNo, passed = result == VerifyCodeResult.Verified))
            }
            when (result) {
                VerifyCodeResult.Verified -> {
                    _state.update { it.copy(busy = false, lastSubmitRefused = false) }
                    report(ProfileAnalytics.stepCompleted(BasicsStep.EmailVerify, secondsOn(BasicsStep.EmailVerify)))
                    onVerified()
                }
                VerifyCodeResult.Refused -> _state.update {
                    it.copy(
                        busy = false,
                        attempts = it.attempts + 1,
                        lastSubmitRefused = true,
                        shakeKey = it.shakeKey + 1,
                    )
                }
                // The server says the cap is reached, so the count goes TO the cap rather than up
                // by one -- the two can disagree if a request was lost, and the server wins.
                VerifyCodeResult.TooManyAttempts -> _state.update {
                    it.copy(
                        busy = false,
                        attempts = MAX_VERIFY_ATTEMPTS,
                        lastSubmitRefused = true,
                        shakeKey = it.shakeKey + 1,
                    )
                }
                // THE SERVER SAYS IT IS GONE, SO THE CLIENT AGREES.
                //
                // `expiresAt` is moved to now rather than a separate flag being set, because the
                // whole screen already derives from it: `verifyState` puts Expired ahead of
                // Mismatch, `canSubmitCode` stops accepting the dead code, and `canResend`
                // releases the link. One fact, and every consequence follows.
                //
                // It is also the case the client CANNOT work out alone. A code superseded by a
                // newer send, or dropped by a restart, is gone while the clock still says it has
                // minutes left -- and a client trusting its own countdown would keep insisting
                // the user had mistyped.
                VerifyCodeResult.Expired -> _state.update {
                    it.copy(
                        busy = false,
                        expiresAt = now(),
                        lastSubmitRefused = false,
                        shakeKey = it.shakeKey + 1,
                    )
                }
                is VerifyCodeResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
            }
        }
    }

    /** Writes the date and the visibility choice together. Only a valid, 18+ date gets here. */
    /**
     * @param age whole years, as the screen computed it -- reported on `dob_submitted` when the
     *   server's answer carries none.
     */
    fun saveDateOfBirth(iso: String, age: Int, onSaved: () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, transportFailed = false, serverRejectedAge = false) }
        viewModelScope.launch {
            when (val result = repo.saveDateOfBirth(iso, _state.value.hideAge)) {
                is SaveBasicsResult.Saved -> {
                    _state.update { it.copy(busy = false) }
                    // The conversion, with the age the server computed and stored.
                    report(ProfileAnalytics.dobSubmitted(result.age ?: age))
                    report(ProfileAnalytics.stepCompleted(BasicsStep.Dob, secondsOn(BasicsStep.Dob)))
                    onSaved()
                }
                SaveBasicsResult.UnderAge ->
                    _state.update { it.copy(busy = false, serverRejectedAge = true) }
                is SaveBasicsResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
            }
        }
    }

    /**
     * Counts the cooldown down from the server's `resendAvailableAt`.
     *
     * Recomputed from the timestamp every second rather than decremented, so a device that slept
     * through half the cooldown wakes up with the right number instead of a stale one.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val until = _state.value.resendAvailableAt
                val left = until?.let {
                    Duration.between(now(), it).seconds.coerceAtLeast(0L).toInt()
                } ?: 0
                _state.update { it.copy(cooldownSeconds = left) }
                if (left <= 0) return@launch
                delay(1000)
            }
        }
    }

    /** Overridable in tests so a countdown does not need a real second to pass. */
    internal var now: () -> OffsetDateTime = { OffsetDateTime.now() }
}
