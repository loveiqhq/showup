/*
 * CheckInPlace.kt
 * ShowUp · where the phone is at Find my date, and the district it names (SHOWUP-184)
 *
 * Search measures every distance from the Check-In's position, on both sides: a Check-In committed
 * with no position is offered to nobody and finds nobody. So `Find my date` sends the phone's place
 * with the hours -- when location is allowed -- and the district its OWN geocoder names, which
 * Search shows as `~3 km · Kreuzberg` (decided 21 Oct 2026: the phone's built-in geocoder, free; the
 * server never geocodes; no district, the distance alone).
 *
 * The coordinates go to the server and nowhere else: never logged, never tracked, never shown -- the
 * server rounds every distance before anyone sees one.
 */
package com.showup.checkin

/** The phone's position and, when its geocoder could name one, the district around it. */
data class CheckInPlace(val latitude: Double, val longitude: Double, val district: String?)

/** Reads [CheckInPlace] at commit. Null without location access, with the switch off, or no fix in time. */
fun interface PlaceReader {
    suspend fun here(): CheckInPlace?
}

/** The server's column: `varchar(80)`, and it refuses a blank one. */
const val DISTRICT_MAX = 80

/**
 * The district line from a geocoder's answer: `{sub-locality}, {locality}` -- `Neukölln, Berlin`, as
 * the spec sheet draws it -- or whichever of the two exists, never the same name twice. Null when it
 * named neither. Cut to [DISTRICT_MAX], at a word where it can be.
 */
fun districtOf(subLocality: String?, locality: String?): String? {
    val sub = subLocality?.trim()?.takeIf { it.isNotEmpty() }
    val city = locality?.trim()?.takeIf { it.isNotEmpty() }
    val line = when {
        sub != null && city != null && !sub.equals(city, ignoreCase = true) -> "$sub, $city"
        else -> sub ?: city
    } ?: return null
    if (line.length <= DISTRICT_MAX) return line
    // Too long for the column: the district alone if it fits, else cut at the last space.
    if (sub != null && sub.length <= DISTRICT_MAX) return sub
    val cut = line.take(DISTRICT_MAX)
    return cut.substringBeforeLast(' ', cut).trimEnd(',', ' ')
}
