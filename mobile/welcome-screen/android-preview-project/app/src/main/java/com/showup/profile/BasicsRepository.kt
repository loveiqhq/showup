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

import com.showup.BuildConfig
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
        /**
         * The code itself, when the server chose to send it back.
         *
         * `AUTH_EXPOSE_OTP` defaults to on outside production, so a development backend answers
         * `/auth/email/start` with the six digits it just mailed. The client used to drop them on
         * the floor and the screen below said, in a comment, that the challenge "carries no
         * devCode to show" -- true when it was written, and not true since the route started
         * returning `OtpChallengeResponseDto`.
         *
         * What that cost: against a REAL backend the verification screen was unwalkable unless
         * somebody read the server log, because the only code on screen came from the offline
         * stand-in, which by definition is not running when a server answers.
         *
         * NULL IN PRODUCTION, always: the server omits it, and nothing here invents one.
         */
        val devCode: String? = null,
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

    /**
     * The code was wrong. NOT expired, and not the cap -- the server distinguishes all three now.
     *
     * It used to mean "wrong, expired, or superseded, and the server does not say which", and the
     * screen rendered that as "That code doesn't match. Check your inbox." So a user holding a
     * correct code that had simply aged out was told to look in their inbox for the code they had
     * just typed. Reported twice from a device before the reason existed to tell them apart.
     */
    data object Refused : VerifyCodeResult

    /**
     * The code aged out, or was superseded by a newer send.
     *
     * A DIFFERENT PROBLEM WITH A DIFFERENT ANSWER: retyping cannot fix it and the user needs the
     * resend, so the screen says so and releases the link rather than blaming the typing.
     */
    data object Expired : VerifyCodeResult

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
/**
 * `open`, for the same reason `MediaRepository` is: a view-model test needs to answer a call
 * without a socket.
 *
 * The alternative was measured and is not acceptable. Driving `BasicsViewModel` through
 * MockWebServer means the model suspends on REAL OkHttp I/O on a real thread, which a test
 * scheduler cannot wait for -- so the fixture has to alternate virtual time with real sleeps, and
 * a suite that did exactly that took NINE HOURS to run once. A repository that can simply answer
 * takes milliseconds and asserts the same rules.
 *
 * `BasicsRepositoryTest` still uses a real server, and should: what IT tests is the wire mapping,
 * where a real request and a real status code are the whole point.
 */
open class BasicsRepository(
    private val api: ShowUpApi,
    /**
     * The stand-in used when NOTHING ANSWERED, or null to let that failure be a failure.
     *
     * Injected rather than an inlined `BuildConfig.DEBUG` check, for the reason the phone flow
     * learned the hard way: unit tests build DEBUG, so a guard inside the catch block would mean
     * the release behaviour of this path could never be asserted at all.
     */
    private val offline: DevOfflineBasics? = if (BuildConfig.DEBUG) DevOfflineBasics else null,
) {

    /** Sends a code to [email]. Authenticated: the server takes the user from the bearer token. */
    open suspend fun sendCode(email: String): SendCodeResult = runCatching {
        val response = api.auth.startEmailVerification(RequestEmailDto(email = email))
        val body = response.body()
        when {
            response.isSuccessful && body != null ->
                SendCodeResult.Sent(
                    expiresAt = body.expiresAt,
                    resendAvailableAt = body.resendAvailableAt,
                    devCode = body.devCode,
                )
            response.code() == 429 -> SendCodeResult.TooSoon
            // The server's only 400 on this route is the uniqueness check.
            response.code() == 400 -> SendCodeResult.EmailInUse
            else -> SendCodeResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse {
        // Nothing answered. A server that replies -- with anything, including 500 -- never
        // reaches here, so this cannot hide a backend bug.
        offline?.start(OffsetDateTime.now()) ?: SendCodeResult.Failed(null)
    }

    /** Confirms [code]. 204 on success — the route returns no body. */
    open suspend fun verifyCode(code: String): VerifyCodeResult = runCatching {
        val response = api.auth.verifyEmail(VerifyEmailDto(code = code))
        if (response.isSuccessful) return VerifyCodeResult.Verified

        val error = errorOf(response.code(), response.errorBody()?.string())
        when {
            response.code() != 401 -> VerifyCodeResult.Failed(error)
            // THE SERVER'S OWN REASON FIRST. `error` is the domain-code slot and the route fills
            // it with one of three values; matching on it is exact where the message-text match
            // below is a guess.
            error?.error == OTP_EXPIRED -> VerifyCodeResult.Expired
            error?.error == OTP_TOO_MANY -> VerifyCodeResult.TooManyAttempts
            error?.error == OTP_MISMATCH -> VerifyCodeResult.Refused
            // THE TEXT MATCH IS THE FALLBACK, not the rule, and it stays for one reason: a client
            // can be newer than the server it is talking to. An app that assumed the reason was
            // always present would report every refusal as a mismatch against any backend that
            // had not shipped it yet -- which is the bug this whole change is about.
            error?.messages.orEmpty().any { it.contains(TOO_MANY, ignoreCase = true) } ->
                VerifyCodeResult.TooManyAttempts
            else -> VerifyCodeResult.Refused
        }
    }.getOrElse {
        offline?.verify(code, OffsetDateTime.now()) ?: VerifyCodeResult.Failed(null)
    }

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
    }.getOrElse {
        offline?.save(iso, OffsetDateTime.now()) ?: SaveBasicsResult.Failed(null)
    }

    private fun errorOf(code: Int, body: String?): ApiError? =
        body?.let { ApiError.parse(code, it) }

    private companion object {
        /** The server's sentence for the cap. Matched, not parsed — see the file header. */
        const val TOO_MANY = "Too many attempts"

        /**
         * The domain codes `/auth/email/verify` answers with. Must match `email-otp.service.ts`.
         *
         * MATCHED ON `error`, NOT ON THE SENTENCE. The route returns the same human message for a
         * wrong code and an aged-out one -- deliberately, since both are "invalid or expired" to
         * a reader -- so the sentence cannot separate them and this is what does.
         */
        const val OTP_EXPIRED = "otp_expired"
        const val OTP_TOO_MANY = "otp_too_many"
        const val OTP_MISMATCH = "otp_mismatch"

        /** The registry `field_id`, and the only value `HIDEABLE_FIELDS` accepts today. */
        const val HIDDEN_FIELD_AGE = "age"
    }
}
