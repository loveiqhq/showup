/*
 * PhoneAuthViewModel.kt
 * ShowUp · the sign-up flow's asynchronous half
 *
 * Owns only what talks to a server: sending a code, confirming it, and the countdown between.
 * Everything the flow already held -- the step, the typed digits, the chosen country -- stays in
 * SignUpFlow, because none of it is asynchronous and moving it would be churn for its own sake.
 *
 * WHAT REPLACING DevAuth ACTUALLY CHANGED
 *
 * The code is no longer compared to a constant. It is sent to `/auth/phone/verify`, and what
 * comes back is a real JWT pair that is written to the encrypted store -- which is the whole
 * reason 153 and 154 could not work: every route they call is authenticated, and nothing had
 * ever produced a token.
 *
 * The cooldown is no longer a constant either. It counts down to the server's
 * `resendAvailableAt`, recomputed from the timestamp each second rather than decremented, so a
 * device that slept through half of it wakes up with the right number.
 */
package com.showup.welcome

import androidx.lifecycle.ViewModel
import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.RESEND_COOLDOWN_SECONDS
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

/** Everything the phone and code screens need that comes from, or waits on, the backend. */
data class PhoneAuthState(
    /**
     * Which challenge the codes on this screen belong to. 0 means none has been issued.
     *
     * A client-side counter, not the server's id -- the contract does not return one. What it
     * buys is the only thing that matters here: the ability to say whether the answer coming back
     * is about the challenge that is still on screen. Without it the app could not tell a live
     * code from a superseded one, and a verify could be submitted against a challenge the server
     * had already deleted.
     */
    val challengeId: Long = 0,
    val cooldownSeconds: Int = 0,
    val attempts: Int = 0,
    val lastSubmitRefused: Boolean = false,
    val expiresAt: OffsetDateTime? = null,
    val resendAvailableAt: OffsetDateTime? = null,
    /**
     * A start is in flight. SEPARATE from [verifying], and that separation is a bug fix.
     *
     * One shared `busy` meant a resend tapped while a verify was in flight returned early and
     * said nothing -- and a verify tapped during a resend did the same. Two silent drops that
     * look exactly like a dead button, which is half of what was reported on 15 September.
     */
    val sending: Boolean = false,
    /** A verify is in flight. */
    val verifying: Boolean = false,
    val transportFailed: Boolean = false,
    /**
     * The server refused a resend because its cooldown has not elapsed, and this is what it said.
     *
     * Non-null means the ACTIVE challenge is unchanged and still valid -- a 429 supersedes
     * nothing. The screen shows this instead of appearing to do nothing.
     */
    val resendRejected: String? = null,
    /**
     * The code, straight from the server, shown ONLY in a debug build.
     *
     * Present because `AUTH_EXPOSE_OTP` is on outside production and `LogSmsSender` is the only
     * sender the backend has. Without it, testing a signup means reading server logs.
     */
    val devCode: String? = null,
    /** True when the code came from [DevOfflineAuth] rather than from a backend. */
    val offline: Boolean = false,
) {
    /** Anything in flight. The CTA reads this; the guards read the two flags separately. */
    val busy: Boolean get() = sending || verifying

    fun locked(): Boolean = attempts >= MAX_VERIFY_ATTEMPTS
}

/**
 * Seconds still to wait before a resend is offered, CLAMPED AT BOTH ENDS.
 *
 * The upper clamp is not defensive padding. This subtracts the DEVICE's clock from the SERVER's
 * timestamp, and nothing makes those agree: a device an hour behind computes an hour of cooldown,
 * the countdown renders "162024:02", and the resend link never comes back -- the user is locked
 * out of the only recovery the code screen offers, by a clock.
 *
 * Seen on 10 September 2026 against a stub returning a far-future timestamp, which is the same
 * arithmetic a skewed clock produces. Above the policy window the two clocks disagree rather than
 * the wait being real, so the window is the honest answer. If the server really does want longer,
 * it says so again with a 429 and [StartAuthResult.TooSoon] leaves the countdown running.
 *
 * A plain function so it can be tested with no ViewModel, no dispatcher and no clock -- which is
 * the rule in the Android CLAUDE.md and the reason this is not inlined in the ticker.
 */
fun cooldownRemaining(now: OffsetDateTime, until: OffsetDateTime?): Int {
    if (until == null) return 0
    return Duration.between(now, until).seconds.coerceIn(0L, RESEND_COOLDOWN_SECONDS).toInt()
}

class PhoneAuthViewModel(private val repo: PhoneAuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(PhoneAuthState())
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** Overridable in tests so a countdown does not need a real second to pass. */
    internal var now: () -> OffsetDateTime = { OffsetDateTime.now() }

    /**
     * Every start this view model has dispatched. Only the newest may apply its answer.
     *
     * Two requests can be in flight in ways the `sending` guard does not cover -- a retry after a
     * slow failure, a resend racing a start from the phone screen -- and the network does not
     * promise to answer them in order. Without a sequence, whichever replies LAST wins, so an
     * older challenge can overwrite a newer one and the code on screen stops being the code the
     * server is holding. That is unprovable after the fact and indistinguishable, to the user,
     * from "I typed it correctly and it said it was wrong".
     */
    private var dispatched = 0L

    /** Challenges actually accepted, which is what [PhoneAuthState.challengeId] counts. */
    private var issued = 0L

    /** Sends a code. Used by the phone screen's CTA and by the code screen's resend. */
    fun start(phoneE164: String, onSent: () -> Unit = {}) {
        // Only a start blocks a start. It used to be `busy`, which also blocked on a verify in
        // flight and dropped the tap without a word.
        if (_state.value.sending) return
        val seq = ++dispatched
        _state.update { it.copy(sending = true, transportFailed = false, resendRejected = null) }
        AuthTrace.log("start dispatched seq=$seq")
        viewModelScope.launch {
            val result = repo.start(phoneE164)
            if (seq != dispatched) {
                // A newer start was dispatched while this one was in flight. Its answer is about
                // a challenge the server has already replaced; applying it would put a dead code
                // on screen.
                AuthTrace.log("start seq=$seq DROPPED, superseded by seq=$dispatched")
                return@launch
            }
            when (result) {
                is StartAuthResult.Sent -> {
                    val id = ++issued
                    _state.update {
                        it.copy(
                            sending = false,
                            challengeId = id,
                            attempts = 0,
                            lastSubmitRefused = false,
                            resendRejected = null,
                            expiresAt = result.expiresAt,
                            resendAvailableAt = result.resendAvailableAt,
                            devCode = result.devCode,
                            offline = result.offline,
                        )
                    }
                    AuthTrace.log("challenge $id issued (seq=$seq), attempts reset")
                    startTicker()
                    onSent()
                }
                // The server's cooldown, or the route's throttle. It refuses BEFORE it supersedes,
                // so the challenge already on screen is still the live one -- nothing here may
                // touch challengeId, expiresAt, devCode or attempts. All that changes is that the
                // user is told why, which is the whole defect this branch used to have.
                is StartAuthResult.TooSoon -> {
                    _state.update {
                        it.copy(
                            sending = false,
                            resendRejected = result.message ?: VerifyCopy.RESEND_TOO_SOON_PROPOSED,
                        )
                    }
                    AuthTrace.log("start seq=$seq refused 429; challenge ${_state.value.challengeId} still active")
                }
                is StartAuthResult.Failed -> {
                    _state.update { it.copy(sending = false, transportFailed = true) }
                    AuthTrace.log("start seq=$seq failed in transport")
                }
            }
        }
    }

    /**
     * Confirms the code, and on success the tokens are already stored.
     *
     * @param onSignedIn receives whether the account already has a complete profile. That is the
     *   routing decision: complete goes to Home, incomplete continues onboarding. Read from
     *   `/me/profile` rather than from a flag on the auth response, because completeness survives
     *   an interrupted signup and "was this new" does not.
     */
    fun verify(phoneE164: String, code: String, onSignedIn: (profileComplete: Boolean) -> Unit) {
        val s = _state.value
        // Only a verify blocks a verify, for the same reason a start only blocks a start.
        if (s.verifying || s.locked() || code.length != 6) return
        // Nothing has been issued, so there is nothing this code could be an answer to. Sending it
        // would earn a 401 that the screen would report as "that code doesn't match", blaming the
        // user for a challenge the app never had.
        if (s.challengeId == 0L) {
            AuthTrace.log("verify refused locally: no challenge has been issued")
            return
        }
        val against = s.challengeId
        _state.update { it.copy(verifying = true, transportFailed = false) }
        AuthTrace.log("verify submitted against challenge $against")
        viewModelScope.launch {
            val result = repo.verify(phoneE164, code)
            if (against != _state.value.challengeId) {
                // A new challenge arrived while this was in flight, so this answer is about a code
                // the server has since deleted. Reporting it would mark the CURRENT code wrong,
                // and count an attempt against a challenge it was never submitted to.
                AuthTrace.log(
                    "verify for challenge $against DROPPED, now on ${_state.value.challengeId}",
                )
                _state.update { it.copy(verifying = false) }
                return@launch
            }
            when (result) {
                is VerifyPhoneResult.SignedIn -> {
                    _state.update { it.copy(verifying = false, lastSubmitRefused = false) }
                    AuthTrace.log("challenge $against accepted")
                    onSignedIn(result.profileComplete)
                }
                VerifyPhoneResult.Refused -> {
                    _state.update {
                        it.copy(
                            verifying = false,
                            attempts = it.attempts + 1,
                            lastSubmitRefused = true,
                        )
                    }
                    AuthTrace.log(
                        "challenge $against refused, attempt ${_state.value.attempts}",
                    )
                }
                // The server says the cap is reached, so the count goes TO the cap rather than up
                // by one -- the two can disagree if a request was lost, and the server wins.
                VerifyPhoneResult.TooManyAttempts -> {
                    _state.update {
                        it.copy(
                            verifying = false,
                            attempts = MAX_VERIFY_ATTEMPTS,
                            lastSubmitRefused = true,
                        )
                    }
                    AuthTrace.log("challenge $against hit the attempt cap")
                }
                is VerifyPhoneResult.Failed ->
                    _state.update { it.copy(verifying = false, transportFailed = true) }
            }
        }
    }

    /** A refused code releases the cooldown, per SHOWUP-143. */
    fun releaseCooldown() = _state.update { it.copy(cooldownSeconds = 0) }

    /**
     * Editing a digit clears the refusal, so the row stops being red while the user corrects it.
     *
     * Only the flag — the attempt COUNT stays, because the server counted those attempts and
     * they are what the cap is measured against.
     */
    fun clearRefusal() = _state.update {
        it.copy(lastSubmitRefused = false, resendRejected = null)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val left = cooldownRemaining(now(), _state.value.resendAvailableAt)
                _state.update { it.copy(cooldownSeconds = left) }
                if (left <= 0) return@launch
                delay(1000)
            }
        }
    }
}
