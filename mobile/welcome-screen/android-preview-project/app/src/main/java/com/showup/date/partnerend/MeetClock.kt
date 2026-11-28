/*
 * MeetClock.kt
 * ShowUp · Date 09's `{time}`: the latest agreed meet time in the device's 12 / 24 h setting (SHOWUP-200, R9)
 */
package com.showup.date.partnerend

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `19:00` on a 24 h clock, `7:00 PM` on a 12 h one. Pure: the host passes the zone and the device's setting
 * (`DateFormat.is24HourFormat`). The locale is pinned to English because the copy is English; the default locale
 * would write "19:00 Uhr" or "PM" as "nachm." around an English sentence.
 */
fun meetClock(meetAt: Instant, zone: ZoneId, is24h: Boolean): String =
    DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.ENGLISH).withZone(zone).format(meetAt)

/**
 * [meetClock] for the route's ISO string: null when there is none or it does not parse, so Date 09 drops ` ({time})`
 * rather than show a placeholder (R9).
 */
fun meetClockOf(iso: String?, zone: ZoneId, is24h: Boolean): String? =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { meetClock(it, zone, is24h) }
