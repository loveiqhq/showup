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

/**
 * Mirrors the server's `OTP_MAX_ATTEMPTS`.
 *
 * The client counts as well as the server so it can stop OFFERING an action the server would
 * refuse. The server remains the authority: [VerifyCodeResult.TooManyAttempts] moves the count to
 * the cap even if the client thought there was one left.
 */
const val MAX_VERIFY_ATTEMPTS = 5

class BasicsViewModel(private val repo: BasicsRepository) : ViewModel() {

    private val _state = MutableStateFlow(BasicsUiState())
    val state: StateFlow<BasicsUiState> = _state.asStateFlow()

    /** Held so a resend can cancel the previous countdown rather than race it. */
    private var ticker: Job? = null

    fun setEmail(value: String) = _state.update { it.copy(email = value, emailInUse = false) }
    fun setDigits(value: String) =
        _state.update { it.copy(codeDigits = value, lastSubmitRefused = false) }
    fun setDob(value: String) = _state.update {
        it.copy(
            dob = value,
            serverRejectedAge = false,
            // The incomplete error clears the moment the eighth digit lands.
            dobAttempted = if (dobDigits(value).length == 8) false else it.dobAttempted,
        )
    }
    fun setHideAge(value: Boolean) = _state.update { it.copy(hideAge = value) }
    fun markDobAttempted() = _state.update { it.copy(dobAttempted = true) }
    fun clearDob() = _state.update { it.copy(dob = "", dobAttempted = false) }

    /**
     * Sends a code, from the email step's Continue and from the resend link.
     *
     * The ticket is explicit that the send is triggered by leaving the email screen and NOT by
     * arriving at the code screen — which is also what stops a relaunch from silently issuing a
     * new code while the one in the user's inbox is still good.
     */
    fun sendCode(onSent: () -> Unit = {}) {
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
                        )
                    }
                    startTicker()
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
        viewModelScope.launch {
            when (repo.verifyCode(_state.value.codeDigits)) {
                VerifyCodeResult.Verified -> {
                    _state.update { it.copy(busy = false, lastSubmitRefused = false) }
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
                is VerifyCodeResult.Failed ->
                    _state.update { it.copy(busy = false, transportFailed = true) }
            }
        }
    }

    /** Writes the date and the visibility choice together. Only a valid, 18+ date gets here. */
    fun saveDateOfBirth(iso: String, onSaved: () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, transportFailed = false, serverRejectedAge = false) }
        viewModelScope.launch {
            when (repo.saveDateOfBirth(iso, _state.value.hideAge)) {
                is SaveBasicsResult.Saved -> {
                    _state.update { it.copy(busy = false) }
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
