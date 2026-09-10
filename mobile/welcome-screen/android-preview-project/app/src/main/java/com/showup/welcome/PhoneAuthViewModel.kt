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
    val cooldownSeconds: Int = 0,
    val attempts: Int = 0,
    val lastSubmitRefused: Boolean = false,
    val expiresAt: OffsetDateTime? = null,
    val resendAvailableAt: OffsetDateTime? = null,
    val busy: Boolean = false,
    val transportFailed: Boolean = false,
    /**
     * The code, straight from the server, shown ONLY in a debug build.
     *
     * Present because `AUTH_EXPOSE_OTP` is on outside production and `LogSmsSender` is the only
     * sender the backend has. Without it, testing a signup means reading server logs.
     */
    val devCode: String? = null,
) {
    fun locked(): Boolean = attempts >= MAX_VERIFY_ATTEMPTS
}

class PhoneAuthViewModel(private val repo: PhoneAuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(PhoneAuthState())
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** Overridable in tests so a countdown does not need a real second to pass. */
    internal var now: () -> OffsetDateTime = { OffsetDateTime.now() }

    /** Sends a code. Used by the phone screen's CTA and by the code screen's resend. */
    fun start(phoneE164: String, onSent: () -> Unit = {}) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, transportFailed = false) }
        viewModelScope.launch {
            when (val result = repo.start(phoneE164)) {
                is StartAuthResult.Sent -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            attempts = 0,
                            lastSubmitRefused = false,
                            expiresAt = result.expiresAt,
                            resendAvailableAt = result.resendAvailableAt,
                            devCode = result.devCode,
                        )
                    }
                    startTicker()
                    onSent()
                }
                // The server's own cooldown, or the route's 5-per-minute throttle. Either way the
                // client's countdown was the optimistic one, so let the ticker re-read the
                // server's timestamp rather than arguing with it.
                StartAuthResult.TooSoon -> _state.update { it.copy(busy = false) }
                is StartAuthResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
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
        if (s.busy || s.locked() || code.length != 6) return
        _state.update { it.copy(busy = true, transportFailed = false) }
        viewModelScope.launch {
            when (val result = repo.verify(phoneE164, code)) {
                is VerifyPhoneResult.SignedIn -> {
                    _state.update { it.copy(busy = false, lastSubmitRefused = false) }
                    onSignedIn(result.profileComplete)
                }
                VerifyPhoneResult.Refused -> _state.update {
                    it.copy(busy = false, attempts = it.attempts + 1, lastSubmitRefused = true)
                }
                // The server says the cap is reached, so the count goes TO the cap rather than up
                // by one -- the two can disagree if a request was lost, and the server wins.
                VerifyPhoneResult.TooManyAttempts -> _state.update {
                    it.copy(busy = false, attempts = MAX_VERIFY_ATTEMPTS, lastSubmitRefused = true)
                }
                is VerifyPhoneResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
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
    fun clearRefusal() = _state.update { it.copy(lastSubmitRefused = false) }

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
}
