/*
 * PhoneAuthRepository.kt
 * ShowUp · signing in with a phone number, for real
 *
 * Both routes go through the GENERATED client, and both are `@Public` on the server -- this is
 * the one part of the app that runs without a token, because it is what produces one.
 *
 * NO PAID PROVIDER IS INVOLVED, AND NONE NEEDS DISABLING
 *
 * `LogSmsSender` is the only SMS sender the backend has and it is hardwired in `auth.module.ts`;
 * there is no Twilio integration to switch off. It writes the code to the server log, and
 * `AUTH_EXPOSE_OTP` -- which defaults to true unless NODE_ENV is production -- also returns it as
 * `devCode` on the challenge. So the whole flow is testable locally and in CI for nothing.
 *
 * `devCode` is carried through this layer deliberately rather than being dropped: the screen
 * decides whether to show it, and it only does so in a debug build. Dropping it here would mean
 * reading server logs to test a signup.
 */
package com.showup.welcome

import com.showup.api.ApiError
import com.showup.api.ShowUpApi
import com.showup.api.TokenStore
import com.showup.api.generated.model.RequestOtpDto
import com.showup.api.generated.model.VerifyOtpDto
import java.time.OffsetDateTime

/** The answer to "text this number a code". */
sealed interface StartAuthResult {
    data class Sent(
        val expiresAt: OffsetDateTime,
        val resendAvailableAt: OffsetDateTime,
        /** Present only when the server is exposing it. Never shown outside a debug build. */
        val devCode: String?,
    ) : StartAuthResult

    /** 429. Either the per-challenge cooldown or the route's 5-per-minute throttle. */
    data object TooSoon : StartAuthResult

    data class Failed(val error: ApiError?) : StartAuthResult
}

/** The answer to "is this the code". */
sealed interface VerifyPhoneResult {
    /**
     * Signed in, and the tokens are already in the store.
     *
     * [profileComplete] is how the flow decides where to go next. It is read from
     * `/me/profile` rather than from a flag on the auth response, because the server does not
     * send one -- and because completeness survives an interrupted signup, where "was this
     * account new" would send a half-registered user straight to Home.
     */
    data class SignedIn(val profileComplete: Boolean) : VerifyPhoneResult

    /** Wrong, expired or superseded — the server answers 401 for all three. */
    data object Refused : VerifyPhoneResult

    /** The cap. Recognised by message text, and it fails safe — see the profile repository. */
    data object TooManyAttempts : VerifyPhoneResult

    data class Failed(val error: ApiError?) : VerifyPhoneResult
}

/**
 * @param tokens passed in rather than reached through [api], so the fact that this class WRITES
 *   credentials is visible in its signature instead of buried in one line of a method.
 */
class PhoneAuthRepository(
    private val api: ShowUpApi,
    private val tokens: TokenStore,
) {

    suspend fun start(phoneE164: String): StartAuthResult = runCatching {
        val response = api.auth.startPhoneVerification(RequestOtpDto(phone = phoneE164))
        val body = response.body()
        when {
            response.isSuccessful && body != null -> StartAuthResult.Sent(
                expiresAt = body.expiresAt,
                resendAvailableAt = body.resendAvailableAt,
                devCode = body.devCode,
            )
            response.code() == 429 -> StartAuthResult.TooSoon
            else -> StartAuthResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { StartAuthResult.Failed(null) }

    /**
     * Confirms the code and STORES THE TOKENS.
     *
     * The save happens here rather than at the call site because it is not optional: a caller
     * that forgot it would leave the app holding a session it cannot prove, and every later
     * request would 401 for a reason nothing on screen could explain.
     */
    suspend fun verify(phoneE164: String, code: String): VerifyPhoneResult = runCatching {
        // The route records the user-agent against the session so a person can later see where
        // they are signed in, which is why the generated signature requires it.
        val response = api.auth.verifyPhone(
            userAgent = ShowUpApi.USER_AGENT,
            verifyOtpDto = VerifyOtpDto(phone = phoneE164, code = code),
        )
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            val error = errorOf(response.code(), response.errorBody()?.string())
            return when {
                response.code() != 401 -> VerifyPhoneResult.Failed(error)
                error?.messages.orEmpty().any { it.contains("Too many attempts", true) } ->
                    VerifyPhoneResult.TooManyAttempts
                else -> VerifyPhoneResult.Refused
            }
        }

        tokens.save(accessToken = body.accessToken, refreshToken = body.refreshToken)
        VerifyPhoneResult.SignedIn(profileComplete = readProfileComplete())
    }.getOrElse { VerifyPhoneResult.Failed(null) }

    /**
     * Whether the signed-in user already has a usable profile.
     *
     * A failure here is treated as INCOMPLETE, not as an error. Sending someone through
     * onboarding they have already done is a mild annoyance; skipping them past profile creation
     * because a request timed out leaves an account that cannot be matched.
     */
    private suspend fun readProfileComplete(): Boolean = runCatching {
        val profile = api.profiles.getProfile()
        profile.body()?.isComplete == true
    }.getOrElse { false }

    private fun errorOf(code: Int, body: String?): ApiError? = body?.let { ApiError.parse(code, it) }

}
