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
