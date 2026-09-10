/*
 * BasicsRepository.kt
 * ShowUp · what "The basics" asks the backend, and what the answers mean
 *
 * Every call here goes through the GENERATED client. Nothing in this file describes a request
 * body or a URL -- those come from openapi.json, and hand-writing one would be a second contract
 * that drifts from the first.
 *
 * WHAT THIS FILE IS REALLY FOR: TRANSLATING 401
 *
 * `/auth/email/verify` answers `401 Invalid or expired code` for a wrong code, an expired one, a
 * superseded one AND a missing challenge, and `401 Too many attempts; request a new code` once
 * the cap is reached. One status, five causes, and the screen has a different sentence for three
 * of them. So the mapping lives here, in one place, where its weaknesses can be written down
 * rather than rediscovered:
 *
 *   - "too many attempts" is told apart by MATCHING THE MESSAGE TEXT. That is fragile -- it is
 *     English, and it is a sentence rather than a code -- so it fails SAFE: an unrecognised 401
 *     is reported as a refusal, which is the message that invites a retry the cap would block
 *     one attempt later. Worth replacing with a domain error code on the server; noted for the
 *     backend rather than worked around harder here.
 *
 *   - EXPIRY IS NOT DECIDED HERE AT ALL. It is decided by the caller, from the `expiresAt` the
 *     start call already returned, because the response genuinely cannot distinguish it. See
 *     `EmailVerification.kt`.
 */
package com.showup.profile

import com.showup.api.ApiError
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.RequestEmailDto
import com.showup.api.generated.model.UpsertProfileDto
import com.showup.api.generated.model.VerifyEmailDto
import java.time.OffsetDateTime

/** The answer to "send this address a code". */
sealed interface SendCodeResult {
    /**
     * Both timestamps come from the server, and both are used rather than assumed.
     *
     * [resendAvailableAt] is why the client no longer holds a cooldown constant: the number was
     * 24 in the design, 30 in the app and 60 on the server, and the only one that can refuse a
     * resend is the server's.
     */
    data class Sent(
        val expiresAt: OffsetDateTime,
        val resendAvailableAt: OffsetDateTime,
    ) : SendCodeResult

    /** 429. The previous code's cooldown has not elapsed. */
    data object TooSoon : SendCodeResult

    /** 400 — the address belongs to another account. Placement decided, copy is not. */
    data object EmailInUse : SendCodeResult

    /** Anything else, including no network. */
    data class Failed(val error: ApiError?) : SendCodeResult
}

/** The answer to "is this the code". */
sealed interface VerifyCodeResult {
    data object Verified : VerifyCodeResult

    /** Wrong, expired, or superseded — the server does not say which. */
    data object Refused : VerifyCodeResult

    /** The cap. Recognised by message text; see the file header. */
    data object TooManyAttempts : VerifyCodeResult

    data class Failed(val error: ApiError?) : VerifyCodeResult
}

/** The answer to "store this date of birth". */
sealed interface SaveBasicsResult {
    /** [age] is the server's own derivation, not the client's — see below. */
    data class Saved(val age: Int?) : SaveBasicsResult

    /** 400 from `isAtLeast18`. The client checks too, so reaching this means the two disagreed. */
    data object UnderAge : SaveBasicsResult

    data class Failed(val error: ApiError?) : SaveBasicsResult
}

/**
 * The one place the profile flow talks to the backend.
 *
 * Takes a [ShowUpApi] rather than building one, so a test can hand it a client pointed at a
 * MockWebServer — which is how every mapping below is verified without a backend.
 */
class BasicsRepository(private val api: ShowUpApi) {

    /** Sends a code to [email]. Authenticated: the server takes the user from the bearer token. */
    suspend fun sendCode(email: String): SendCodeResult = runCatching {
        val response = api.auth.startEmailVerification(RequestEmailDto(email = email))
        val body = response.body()
        when {
            response.isSuccessful && body != null ->
                SendCodeResult.Sent(
                    expiresAt = body.expiresAt,
                    resendAvailableAt = body.resendAvailableAt,
                )
            response.code() == 429 -> SendCodeResult.TooSoon
            // The server's only 400 on this route is the uniqueness check.
            response.code() == 400 -> SendCodeResult.EmailInUse
            else -> SendCodeResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { SendCodeResult.Failed(null) }

    /** Confirms [code]. 204 on success — the route returns no body. */
    suspend fun verifyCode(code: String): VerifyCodeResult = runCatching {
        val response = api.auth.verifyEmail(VerifyEmailDto(code = code))
        if (response.isSuccessful) return VerifyCodeResult.Verified

        val error = errorOf(response.code(), response.errorBody()?.string())
        when {
            response.code() != 401 -> VerifyCodeResult.Failed(error)
            // Fragile on purpose, and it fails safe: see the file header.
            error?.messages.orEmpty().any { it.contains(TOO_MANY, ignoreCase = true) } ->
                VerifyCodeResult.TooManyAttempts
            else -> VerifyCodeResult.Refused
        }
    }.getOrElse { VerifyCodeResult.Failed(null) }

    /**
     * Writes the date of birth and the age-visibility choice.
     *
     * One PATCH, not two. They are set on the same screen by the same press, and splitting them
     * would let the date land while the visibility choice failed — leaving the user with an age
     * displayed that they asked to hide.
     *
     * `hiddenFields` REPLACES the whole set, which is safe here because this is profile creation
     * and the set starts empty. A later screen editing one field has to read-modify-write.
     */
    suspend fun saveDateOfBirth(iso: String, hideAge: Boolean): SaveBasicsResult = runCatching {
        val response = api.profiles.updateProfile(
            UpsertProfileDto(
                dateOfBirth = iso,
                // Never isVisible. That flag means "appears in discovery at all", and the product
                // decision is explicit that hiding an age must not affect matching.
                hiddenFields = if (hideAge) listOf(HIDDEN_FIELD_AGE) else emptyList(),
            ),
        )
        val body = response.body()
        when {
            response.isSuccessful && body != null -> SaveBasicsResult.Saved(body.age)
            // The server re-derives age and refuses under 18. The client already checked, so this
            // only fires when the two disagree -- which is possible around a birthday if a clock
            // is wrong, and is exactly why the client was moved onto the server's UTC basis.
            response.code() == 400 -> SaveBasicsResult.UnderAge
            else -> SaveBasicsResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { SaveBasicsResult.Failed(null) }

    private fun errorOf(code: Int, body: String?): ApiError? =
        body?.let { ApiError.parse(code, it) }

    private companion object {
        /** The server's sentence for the cap. Matched, not parsed — see the file header. */
        const val TOO_MANY = "Too many attempts"

        /** The registry `field_id`, and the only value `HIDEABLE_FIELDS` accepts today. */
        const val HIDDEN_FIELD_AGE = "age"
    }
}
