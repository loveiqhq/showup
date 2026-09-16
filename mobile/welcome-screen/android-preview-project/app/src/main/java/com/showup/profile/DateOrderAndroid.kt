/*
 * DateOrderAndroid.kt
 * ShowUp · reading the device's date order (SHOWUP-154)
 *
 * Kept apart from `DateOfBirth.kt` so that file stays free of Android types and can be tested as
 * plain Kotlin. This is the only place the platform is asked anything.
 */
package com.showup.profile

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this device writes the month or the day first.
 *
 * The product side ruled on 10 September 2026 that the field follows the device rather than
 * always asking US-first. The reason is in the ticket's own open questions: 03/04 is a valid date
 * in BOTH readings, so a German user typing day-first into a month-first field gets a wrong date
 * that no validation can catch -- and the age is locked afterwards.
 *
 * `getDateFormatOrder` returns the three components in the order the locale writes them, e.g.
 * ['M','d','y'] or ['d','M','y']. Only the first matters here: the year is last in both orders
 * this screen supports, and a locale that leads with the year (ja, ko) is read as month-first
 * rather than silently mis-parsed -- recorded as a gap rather than guessed at.
 */
@Composable
fun rememberDateOrder(): DateOrder {
    val context = LocalContext.current
    // Keyed on the configuration so a locale change while the app is alive is picked up.
    val config = LocalConfiguration.current
    return remember(config) {
        val order = runCatching { DateFormat.getDateFormatOrder(context) }.getOrNull()
        if (order?.firstOrNull() == 'd') DateOrder.DayFirst else DateOrder.MonthFirst
    }
}
