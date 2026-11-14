/*
 * ArrivalRules.kt
 * ShowUp · the arrival check-in's plain rules (SHOWUP-195)
 */
package com.showup.arrival

import java.time.Instant

/** `enums.json` §41 `arrival_entry`: where the person tapped. `report` is reserved for Date 06. */
enum class ArrivalEntry(val wire: String) {
    DateScreen("date_screen"),
    ChatChip("chat_chip"),
    Report("report"),
}

/** How long one location read may take before the person is told, in ms. */
const val LOCATION_FIX_TIMEOUT_MS = 10_000L

/** The oldest a location fix may be, in ms: the 150 m comparison needs where the person is now. */
const val MAX_FIX_AGE_MS = 30_000L

/**
 * Whether a fix is recent enough to compare. getCurrentLocation may answer from a recent cache, so the
 * fix's own clock is checked. Inclusive at 30 s; a stamp ahead of now counts as fresh (as on iOS).
 */
fun isFresh(fixElapsedNanos: Long, nowElapsedNanos: Long): Boolean =
    nowElapsedNanos - fixElapsedNanos <= MAX_FIX_AGE_MS * 1_000_000L

/**
 * Whole minutes from the latest agreed meet time to [now]: negative before, truncated toward zero --
 * the server's `minutes_vs_start`, so both sides' events agree.
 */
fun minutesVsStart(meetAt: Instant, now: Instant): Int =
    ((now.epochSecond - meetAt.epochSecond) / 60).toInt()

/** The wait before retry number [attempt] (0-based) of an undo or commit: 2 s, doubling, held at 30 s. */
fun retryDelayMs(attempt: Int): Long {
    val doubled = 2_000L shl attempt.coerceIn(0, 10)
    return doubled.coerceAtMost(30_000L)
}
