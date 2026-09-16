//  EmailVerification.swift
//  ShowUp · what the code screen is looking at (SHOWUP-153)
//
//  Mirrors `profile/EmailVerification.kt`. The screen renders one of four things under the slots,
//  and which one is a decision with three inputs and an order of precedence — so it lives here
//  where it can be tested rather than inside a view where it cannot.
//
//  WHY THE CLIENT CAN TELL "EXPIRED" FROM "WRONG" WITHOUT A BACKEND CHANGE
//
//  `/auth/email/verify` answers 401 "Invalid or expired code" for both, so the RESPONSE cannot
//  distinguish them. But `/auth/email/start` already returns `expiresAt`, so the client knows when
//  the code dies without asking. That comparison is the whole mechanism.
//
//  Its one weakness, stated plainly: it trusts the device clock. A badly wrong clock shows the
//  other message. Both point at the same recovery — send a new code — so the cost is a less apt
//  sentence, never a dead end.

import Foundation

/// What the user is being told under the slots.
enum VerifyState {
    /// Nothing to say. Fewer than six digits, or six that have not been submitted yet.
    case calm
    /// Six digits, submitted, refused. The code is still live and still worth correcting.
    case mismatch
    /// The code died of age. Re-reading the inbox will not help; only a new code will.
    case expired
    /// The server has stopped accepting attempts against this code. Same cap as SMS.
    case lockedOut
}

/// Which failure the screen is in.
///
/// PRECEDENCE, AND WHY IT IS THIS WAY ROUND
///
/// Expired outranks locked out, which outranks mismatch. All three end at the same button, so the
/// ordering is not about what the user must do — it is about telling them the truth. A code that
/// expired on the second attempt did not fail because of too many tries, and saying so would
/// invite them to believe they had used up something they had not. Locked out then outranks
/// mismatch for the same reason: reaching the cap means the last submit was also a mismatch, and
/// "try again" would be an instruction that cannot be followed.
func verifyState(
    attempts: Int,
    maxAttempts: Int,
    expired: Bool,
    lastSubmitRefused: Bool
) -> VerifyState {
    if expired { return .expired }
    if attempts >= maxAttempts { return .lockedOut }
    if lastSubmitRefused { return .mismatch }
    return .calm
}

/// Whether a submission should even be attempted.
///
/// Both blocking states are refusals the server would make anyway, so asking is a round trip that
/// can only fail. Six digits is the screen's own rule — a short code is not an error, it is a CTA
/// with nothing to submit.
func canSubmitCode(_ digits: String, state: VerifyState) -> Bool {
    digits.count == 6 && state != .lockedOut && state != .expired
}

/// Whether the resend link is live.
///
/// Any failure releases it. A user who can neither submit nor resend has no move left, and the
/// cooldown is a courtesy to the mail server rather than a punishment.
func canResend(cooldownSeconds: Int, state: VerifyState) -> Bool {
    state != .calm || cooldownSeconds <= 0
}
