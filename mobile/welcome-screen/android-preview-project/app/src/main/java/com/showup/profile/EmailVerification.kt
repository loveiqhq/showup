/*
 * EmailVerification.kt
 * ShowUp · what the code screen is looking at (SHOWUP-153)
 *
 * The screen renders one of four things under the slots. Which one is a decision with three
 * inputs and an order of precedence, so it lives here where it can be tested rather than inside a
 * composable where it cannot.
 *
 * WHY THE CLIENT CAN TELL "EXPIRED" FROM "WRONG" WITHOUT A BACKEND CHANGE
 *
 * `/auth/email/verify` answers 401 "Invalid or expired code" for both, so the RESPONSE cannot
 * distinguish them. But `/auth/email/start` already returns `expiresAt`, so the client knows when
 * the code dies without asking. Comparing the clock to that timestamp is the whole mechanism, and
 * it is why this needed no server work.
 *
 * Its one weakness, stated plainly: it trusts the device clock. A badly wrong clock shows the
 * other message. Both messages point at the same recovery — send a new code — so the cost is a
 * less apt sentence, never a dead end.
 */
package com.showup.profile

/** What the user is being told under the slots. */
enum class VerifyState {
    /** Nothing to say. Fewer than six digits, or six that have not been submitted yet. */
    Calm,

    /** Six digits, submitted, refused. The code is still live and still worth correcting. */
    Mismatch,

    /** The code died of age. Re-reading the inbox will not help; only a new code will. */
    Expired,

    /** The server has stopped accepting attempts against this code. Same cap as SMS. */
    LockedOut,
}

/**
 * Which failure the screen is in.
 *
 * PRECEDENCE, AND WHY IT IS THIS WAY ROUND
 *
 * Expired outranks locked out, which outranks mismatch. All three end at the same button, so the
 * ordering is not about what the user must do — it is about telling them the truth. A code that
 * expired on the second attempt did not fail because of too many tries, and saying so would
 * invite them to believe they had used up something they had not. Locked out then outranks
 * mismatch for the same reason: reaching the cap means the last submit was also a mismatch, and
 * "try again" would be an instruction that cannot be followed.
 *
 * @param attempts submissions made against the current code
 * @param maxAttempts the server's cap, mirrored — see `DevAuth.MAX_VERIFY_ATTEMPTS`
 * @param expired whether the clock has passed the challenge's `expiresAt`
 * @param lastSubmitRefused whether the most recent submission came back wrong
 */
fun verifyState(
    attempts: Int,
    maxAttempts: Int,
    expired: Boolean,
    lastSubmitRefused: Boolean,
): VerifyState = when {
    expired -> VerifyState.Expired
    attempts >= maxAttempts -> VerifyState.LockedOut
    lastSubmitRefused -> VerifyState.Mismatch
    else -> VerifyState.Calm
}

/**
 * Whether a submission should even be attempted.
 *
 * Both blocking states are refusals the server would make anyway, so asking is a round trip that
 * can only fail. Six digits is the screen's own rule — [VerifyState.Calm] with a short code is
 * not an error, it is a CTA that has nothing to submit.
 */
fun canSubmitCode(digits: String, state: VerifyState): Boolean =
    digits.length == 6 && state != VerifyState.LockedOut && state != VerifyState.Expired

/**
 * Whether the resend link is live.
 *
 * Any failure releases it. A user who cannot submit and cannot resend has no move left, and the
 * cooldown is a courtesy to the mail server, not a punishment — the ticket says a wrong code must
 * not cost another wait, and the same reasoning covers the other two.
 */
fun canResend(cooldownSeconds: Int, state: VerifyState): Boolean =
    state != VerifyState.Calm || cooldownSeconds <= 0
