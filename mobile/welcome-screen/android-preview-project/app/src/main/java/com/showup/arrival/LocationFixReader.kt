/*
 * LocationFixReader.kt
 * ShowUp · one fresh position for the arrival check-in (SHOWUP-195)
 *
 * FRESH OR NOTHING. A check-in is evidence that flow 10 compares at 150 m, so there is no last-known
 * fallback here (unlike Find my date's): an old position would say the person was somewhere they were
 * not. The 10 s bound is the view model's `withTimeoutOrNull`, not this class's.
 *
 * Nothing here logs a coordinate, and [LocationFix.toString] must never reach a log.
 */
package com.showup.arrival

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A position. [accuracyM] is null when the platform reported none. */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Double?)

fun interface LocationFixReader {
    /** One fresh fix, or null: no permission, services off, no provider, or no answer. */
    suspend fun fix(): LocationFix?
}

class AndroidLocationFixReader(context: Context) : LocationFixReader {

    private val app = context.applicationContext

    override suspend fun fix(): LocationFix? {
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        if (!allowed) return null
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val provider = provider(manager) ?: return null
        val location = current(manager, provider) ?: return null
        // A cached answer is not where the person is now (frame C).
        if (!isFresh(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())) return null
        return LocationFix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy.toDouble() else null)
    }

    /** The provider to ask, as Find my date chooses it: fused where it exists, else network, else GPS. */
    private fun provider(manager: LocationManager): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> null
    }

    private suspend fun current(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(app)) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (_: SecurityException) {
                // Access withdrawn between the check and the ask.
                if (cont.isActive) cont.resume(null)
            } catch (_: IllegalArgumentException) {
                // The provider went away between choosing it and asking it.
                if (cont.isActive) cont.resume(null)
            }
        }
}
