/*
 * OtpPolicy.kt
 * ShowUp · the backend's OTP rules, mirrored where the client needs them
 *
 * Two numbers the server owns. The client holds them so it can stop OFFERING an action the server
 * would refuse -- a disabled resend link reads better than a 429 -- but the server remains the
 * authority, and where the two disagree the response wins.
 *
 * WHY THIS FILE EXISTS AT ALL
 *
 * Each of these was written down twice: once in `DevAuth`, back when the sign-up flow faked its
 * own verification, and once in the profile flow when email verification became real. Two spellings
 * of the same server rule is the failure this repository has already paid for elsewhere -- see the
 * three primary buttons in the Android CLAUDE.md. Raising `OTP_MAX_ATTEMPTS` to 6 should be one
 * edit here, not a search.
 *
 * Both live in `com.showup.api` because both are properties of the backend contract, and because
 * `welcome` and `profile` both already depend on this package while neither should depend on the
 * other.
 */
package com.showup.api

/**
 * Wrong guesses allowed against one code, mirroring the server's `OTP_MAX_ATTEMPTS`.
 *
 * One rule, not two: `otp.service.ts` and `email-otp.service.ts` read the same config value and
 * throw the same message, so SMS and email share this cap rather than each having their own.
 */
const val MAX_VERIFY_ATTEMPTS = 5

/**
 * Seconds the server makes a user wait before it will send another code -- `OTP_RESEND_COOLDOWN`.
 *
 * NOT what the countdown counts. That reads `resendAvailableAt` off the challenge, so a device
 * that slept through half the window wakes up with the right number and a server that changes its
 * mind is obeyed without a release. This constant survives for one thing only: turning "seconds
 * remaining" into "seconds waited" for the resend analytics property, which needs a window length
 * and has no other source for one. If the two ever disagree, that property is slightly wrong and
 * nothing about the app's behaviour changes.
 */
const val RESEND_COOLDOWN_SECONDS = 60L

/*
 * DELIBERATELY ABSENT: the code's time-to-live (`OTP_TTL`, 300s).
 *
 * The client used to hold it, to tell an expired code from a wrong one -- both answer 401. It does
 * not need to: `/auth/phone/start` and `/auth/email/start` both return `expiresAt`, which is the
 * same fact from the authority rather than a guess that drifts the moment the config changes.
 */
