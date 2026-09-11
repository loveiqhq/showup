/*
 * DevOfflineAuth.kt
 * ShowUp · walking the sign-up flow with no backend at all
 *
 * WHAT THIS IS, AND WHAT IT IS CAREFUL NOT TO BE
 *
 * The app talks to a real NestJS backend over real HTTP. That is not negotiable and this file
 * does not change it: every request is still made, and whatever the server answers is what the
 * app believes. What this adds is one narrow case -- the request could not be made AT ALL,
 * because nothing is listening -- where a debug build carries on locally instead of stopping.
 *
 * It exists because the alternative is worse for the people who need it most. Reviewing a design
 * or walking a flow should not require Docker, Postgres, Redis and a Nest server, and on
 * 10 September 2026 it did: with no backend running, "Send me the code" showed a red card and the
 * flow ended there. Correct behaviour, useless afternoon.
 *
 * THE THREE RULES THAT KEEP IT HONEST
 *
 *   1. DEBUG ONLY. Every entry point is behind `BuildConfig.DEBUG`, which is a compile-time
 *      constant, so R8 removes this from a release build entirely. `verify-welcome.py` fails if
 *      a call site loses its guard.
 *
 *   2. ONLY WHEN NOTHING ANSWERED. It engages on a thrown IOException -- connection refused, DNS
 *      failure, cleartext blocked by policy. A server that answers 400, 401, 429 or 500 is a
 *      server that is working, and its answer is passed through untouched. This cannot paper
 *      over a backend bug, because a backend that responds is never reached by this code.
 *
 *   3. IT SAYS SO, LOUDLY. [StartAuthResult.Sent.offline] reaches the screen and the dev strip
 *      reads "OFFLINE - no server" rather than the usual "logged, not sent". Nobody should be
 *      able to demo this and believe they were talking to a backend. That mistake is exactly how
 *      the cleartext bug survived: a failure that looked like something else.
 *
 * NO PAID SERVICE IS INVOLVED HERE OR ANYWHERE NEAR IT. The backend's only SMS sender is
 * LogSmsSender, which writes to a log; this file does not even reach the backend. Twilio has
 * never been connected.
 */
package com.showup.welcome

import com.showup.api.MAX_VERIFY_ATTEMPTS
import com.showup.api.RESEND_COOLDOWN_SECONDS
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * The stand-in backend, holding exactly what a real challenge holds.
 *
 * An object rather than an injected dependency because it has to survive the repository being
 * rebuilt, which happens on every configuration change. Its whole lifetime is one debug session.
 */
object DevOfflineAuth {

    /** The code currently "sent", or null when nothing has been. */
    @Volatile
    private var code: String? = null

    @Volatile
    private var expiresAt: OffsetDateTime? = null

    @Volatile
    private var attempts = 0

    /** How long a code lives, matching the server's `OTP_TTL`. */
    private const val TTL_SECONDS = 300L

    /**
     * Issues a code, the way `/auth/phone/start` would.
     *
     * Six digits and never 123456 or 000000: the first is what everyone tries by accident and
     * the second is what a broken generator returns, and either would hide a real mismatch from
     * whoever is testing the mismatch state.
     */
    fun start(now: OffsetDateTime): StartAuthResult.Sent {
        val issued = "%06d".format(Random.nextInt(100_000, 1_000_000))
        code = issued
        expiresAt = now.plusSeconds(TTL_SECONDS)
        attempts = 0
        return StartAuthResult.Sent(
            expiresAt = now.plusSeconds(TTL_SECONDS),
            resendAvailableAt = now.plusSeconds(RESEND_COOLDOWN_SECONDS),
            devCode = issued,
            offline = true,
        )
    }

    /**
     * Checks a code the way `/auth/phone/verify` would, including the attempt cap.
     *
     * Deliberately models the failures too. A stand-in that only ever succeeds would let the
     * mismatch card, the lockout card and the expiry copy rot unseen, which is most of what
     * anyone walking this flow needs to look at.
     */
    fun verify(submitted: String, now: OffsetDateTime): VerifyPhoneResult {
        val issued = code ?: return VerifyPhoneResult.Refused
        if (attempts >= MAX_VERIFY_ATTEMPTS) return VerifyPhoneResult.TooManyAttempts
        val deadline = expiresAt
        if (deadline != null && now.isAfter(deadline)) return VerifyPhoneResult.Refused
        if (submitted != issued) {
            attempts += 1
            return if (attempts >= MAX_VERIFY_ATTEMPTS) {
                VerifyPhoneResult.TooManyAttempts
            } else {
                VerifyPhoneResult.Refused
            }
        }
        code = null
        attempts = 0
        // Incomplete, always. There is no profile offline, and the routing rule is that a profile
        // which cannot be read counts as incomplete -- so the flow continues into onboarding,
        // which is the half anyone walking this wants to see.
        return VerifyPhoneResult.SignedIn(profileComplete = false)
    }

    /** Forgets everything. For tests, so one case cannot leak into the next. */
    fun reset() {
        code = null
        expiresAt = null
        attempts = 0
    }
}
