/*
 * AndroidPlaceReader.kt
 * ShowUp · the phone's position and district at Find my date, with what the phone already has
 *
 * NO PLAY SERVICES, on purpose: `LocationManagerCompat.getCurrentLocation` asks the platform for one
 * fresh fix -- the fused provider where the phone has one (API 31+), else the network, else GPS --
 * and `Geocoder` is the phone's own reverse geocoder. Free, no account, no new dependency (decided
 * 21 Oct 2026).
 *
 * BOUNDED, both of them. `Find my date` waits on this behind its saving state, so a fix that does
 * not come in [FIX_TIMEOUT_MS] falls back to the newest last-known position younger than
 * [LAST_KNOWN_MAX_AGE_MS], and a geocoder that does not answer in [GEOCODE_TIMEOUT_MS] leaves the
 * district out -- Search then shows the distance alone. An old position would put the user in the
 * wrong place; an absent one only means nobody is offered, and the server says so the same way.
 *
 * Nothing here logs a coordinate or an address.
 */
package com.showup.checkin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class AndroidPlaceReader(context: Context) : PlaceReader {

    private val app = context.applicationContext

    override suspend fun here(): CheckInPlace? {
        if (!allowed()) return null
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) { currentFix(manager) } ?: lastKnown(manager) ?: return null
        val district = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) { districtAt(fix) }
        return CheckInPlace(fix.latitude, fix.longitude, district)
    }

    private fun allowed(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

    /** The provider to ask: fused where it exists, else whichever of network and GPS is on. */
    private fun provider(manager: LocationManager): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> null
    }

    private suspend fun currentFix(manager: LocationManager): Location? {
        val provider = provider(manager) ?: return null
        return suspendCancellableCoroutine { cont ->
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

    /** The newest position any provider still holds, if it is recent enough to be where the user is. */
    private fun lastKnown(manager: LocationManager): Location? = try {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= LAST_KNOWN_MAX_AGE_MS * 1_000_000 }
            .maxByOrNull { it.elapsedRealtimeNanos }
    } catch (_: SecurityException) {
        null
    }

    private suspend fun districtAt(fix: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(app, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    fix.latitude, fix.longitude, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }
                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        } else {
            // The blocking form is the only one below API 33; it does network I/O, so off the main thread.
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.let { districtOf(it.subLocality, it.locality) }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 4_000L
        const val GEOCODE_TIMEOUT_MS = 3_000L
        /** Two hours: still where the user is likely to be meeting, unlike yesterday's fix. */
        const val LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 60 * 1_000L
    }
}
