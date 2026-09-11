/*
 * DevOfflineBasics.kt
 * ShowUp · walking profile creation with no backend at all
 *
 * The email half of `welcome/DevOfflineAuth.kt`. Read that file's header for the argument; the
 * three rules are identical and so are the reasons.
 *
 *   1. DEBUG ONLY -- injected as a constructor default computed from `BuildConfig.DEBUG`, so R8
 *      removes it from a release build entirely.
 *   2. ONLY WHEN NOTHING ANSWERED -- it sits in the transport-failure branch. A server that
 *      answers 400, 401, 429 or 500 is a server that is working, and its answer reaches the app
 *      untouched.
 *   3. IT SAYS SO -- the verify screen's dev strip reads "OFFLINE" rather than "logged, not
 *      sent", so nobody can demo this believing a backend was involved.
 *
 * WHY THIS EXISTS SEPARATELY FROM DevOfflineAuth
 *
 * They model different endpoints with different rules. Phone verification mints a session; email
 * verification happens INSIDE one and returns 204 with no body. Sharing one object would mean a
 * single code and a single attempt counter across two independent challenges, so verifying an
 * email would clear the phone lockout. Two challenges, two objects.
 *
 * The cap and the cooldown are not duplicated, though: both read `com.showup.api.OtpPolicy`, the
 * same values the real endpoints enforce.
 */
package com.showup.profile

import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.RESEND_COOLDOWN_SECONDS
import java.time.OffsetDateTime
import kotlin.random.Random

/** The stand-in for `/auth/email/start` and `/auth/email/verify`. */
object DevOfflineBasics {

    @Volatile
    private var code: String? = null

    @Volatile
    private var expiresAt: OffsetDateTime? = null

    @Volatile
    private var attempts = 0

    /** How long a code lives, matching the server's `OTP_TTL`. */
    private const val TTL_SECONDS = 300L

    /**
     * The code most recently issued, for the debug strip on the verify screen.
     *
     * The real `/auth/email/start` returns `devCode` on the challenge and this one cannot, because
     * [SendCodeResult.Sent] has no field for it -- the email challenge never carried one, since
     * the screen that needed it did not exist when the contract was written. Reading it back from
     * here keeps the DTO honest rather than growing a field the server does not send.
     */
    @Volatile
    var lastIssued: String? = null
        private set

    fun start(now: OffsetDateTime): SendCodeResult.Sent {
        val issued = "%06d".format(Random.nextInt(100_000, 1_000_000))
        code = issued
        lastIssued = issued
        expiresAt = now.plusSeconds(TTL_SECONDS)
        attempts = 0
        return SendCodeResult.Sent(
            expiresAt = now.plusSeconds(TTL_SECONDS),
            resendAvailableAt = now.plusSeconds(RESEND_COOLDOWN_SECONDS),
        )
    }

    /**
     * Checks a code the way `/auth/email/verify` would, including the attempt cap and expiry.
     *
     * Models the failures, not only the success. A stand-in that always accepts would leave the
     * mismatch card, the expiry copy and the lockout state unreachable -- and those three are
     * most of what SHOWUP-153 is about.
     */
    fun verify(submitted: String, now: OffsetDateTime): VerifyCodeResult {
        val issued = code ?: return VerifyCodeResult.Refused
        if (attempts >= MAX_VERIFY_ATTEMPTS) return VerifyCodeResult.TooManyAttempts
        val deadline = expiresAt
        if (deadline != null && now.isAfter(deadline)) return VerifyCodeResult.Refused
        if (submitted != issued) {
            attempts += 1
            return if (attempts >= MAX_VERIFY_ATTEMPTS) {
                VerifyCodeResult.TooManyAttempts
            } else {
                VerifyCodeResult.Refused
            }
        }
        code = null
        attempts = 0
        return VerifyCodeResult.Verified
    }

    /** Accepts the date of birth and derives the age the way the server would. */
    fun save(dateOfBirth: String?, now: OffsetDateTime): SaveBasicsResult {
        val age = dateOfBirth?.let { runCatching { ageFrom(it, now) }.getOrNull() }
        // The server refuses under 18 and so does this, or the DoB screen's rejection state
        // could never be reached without a backend.
        if (age != null && age < 18) return SaveBasicsResult.UnderAge
        return SaveBasicsResult.Saved(age)
    }

    private fun ageFrom(iso: String, now: OffsetDateTime): Int {
        val parts = iso.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        var age = now.year - year
        if (now.monthValue < month || (now.monthValue == month && now.dayOfMonth < day)) age -= 1
        return age
    }

    /** Forgets everything. For tests, so one case cannot leak into the next. */
    fun reset() {
        code = null
        lastIssued = null
        expiresAt = null
        attempts = 0
    }
}
