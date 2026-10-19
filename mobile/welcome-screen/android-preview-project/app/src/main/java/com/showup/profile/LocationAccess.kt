/*
 * LocationAccess.kt
 * ShowUp · the location status, and the arrival matrix that decides what screen 12 shows (SHOWUP-165)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO FACTS, NOT ONE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The permission is §24's four values, the vocabulary every permission in the product shares --
 * [PermissionStatus], the same type the notification ask reads. The phone's Location SWITCH is a
 * second, separate fact: "the device switch is not a permission status; it is the reason C exists."
 * With it off, the OS dialog cannot help whatever the permission says, so it is checked FIRST.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE MATRIX IS DECIDED BEFORE THE SCREEN IS PUSHED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *     switch off, any permission    C
 *     not determined                A   (also the relaunch after a kill mid-dialog)
 *     granted                       skipped silently -- straight to Embrace 2
 *     denied / restricted           B
 *
 * [locationArrival] is the whole decision and a pure function, so the host takes it before the
 * screen exists ("no flash, no toast") and a test takes it with no device. The same function runs
 * on every foreground while the screen is up, because the classic bug is a user who granted access
 * in Settings coming back to the denied screen.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ANDROID: "NEVER ASKED" AND "REFUSED" LOOK THE SAME
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `checkSelfPermission` answers granted or not, and not-granted covers both a permission never
 * requested and one the user refused. [PermissionAskLog] -- shared with the photo, media and
 * notification steps -- is the proxy, recorded when the dialog is ANSWERED, never before.
 *
 * FINE OR COARSE COUNTS AS GRANTED. Android 12+ lets the user pick "Approximate", which grants
 * COARSE alone; "precise and approximate are both accepted", so either permission is a grant.
 */
package com.showup.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** What screen 12 can show. One component, three states (`state` in the reference). */
enum class LocationState {
    /** Ask -- not determined, switch on. One CTA: `Allow location access`. */
    Ask,

    /** Denied or restricted. `Open Settings` + `Not now — ask me when I search`. */
    Denied,

    /** The phone's Location switch is off, any permission. Same layout as [Denied]. */
    ServicesOff,
}

/**
 * `enums.json` §26 `location_unavailable_reason` -- why B or C is showing.
 *
 * NEW in registry 1.4.7. `not_determined` is in the set too, for the app-wide location gate; screen
 * 12 never reports it, because its not-determined state is the ask, not a recovery.
 */
enum class LocationUnavailableReason(val trackingValue: String) {
    Denied("denied"),
    ServicesOff("services_off"),
}

/** The two facts the matrix reads. */
data class LocationStatus(
    val permission: PermissionStatus,
    /** The device-wide Location switch -- `LocationManager.isLocationEnabled`. */
    val servicesOn: Boolean,
)

/** What the matrix answers: a state to show, or a silent skip to Embrace 2. */
sealed interface LocationArrival {
    data class Show(val state: LocationState) : LocationArrival
    data object Skip : LocationArrival
}

/** The arrival matrix, and the foreground reconciler -- the same rule for both. */
fun locationArrival(status: LocationStatus): LocationArrival = when {
    !status.servicesOn -> LocationArrival.Show(LocationState.ServicesOff)
    status.permission == PermissionStatus.Granted -> LocationArrival.Skip
    status.permission == PermissionStatus.NotDetermined -> LocationArrival.Show(LocationState.Ask)
    // Restricted is treated as denied, per the ticket and §24.
    else -> LocationArrival.Show(LocationState.Denied)
}

/** The §26 reason a state reports, or null for the ask. */
val LocationState.unavailableReason: LocationUnavailableReason?
    get() = when (this) {
        LocationState.Ask -> null
        LocationState.Denied -> LocationUnavailableReason.Denied
        LocationState.ServicesOff -> LocationUnavailableReason.ServicesOff
    }

/**
 * The permission, from the facts Android can report. Pure, for the same reason as
 * [notificationPermissionFor]: the branch that fires in production is testable with no Context.
 */
fun locationPermissionFor(fine: Boolean, coarse: Boolean, hasAsked: Boolean): PermissionStatus =
    when {
        fine || coarse -> PermissionStatus.Granted
        hasAsked -> PermissionStatus.Denied
        else -> PermissionStatus.NotDetermined
    }

/** Reads the status. Re-read on every foreground -- never remembered. */
interface LocationAccessReader {
    fun read(): LocationStatus
}

/**
 * The two permissions the dialog asks for, together.
 *
 * "Android 12+ requires coarse alongside fine" -- asking for fine alone is refused outright on 31+.
 * WHILE-IN-USE ONLY: there is no `ACCESS_BACKGROUND_LOCATION` here or in the manifest, and the
 * ticket forbids it anywhere in this flow.
 */
val LOCATION_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** The real reader. */
class AndroidLocationAccess(private val context: Context) : LocationAccessReader {
    override fun read(): LocationStatus {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return LocationStatus(
            permission = locationPermissionFor(
                fine = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                hasAsked = PermissionAskLog.hasAsked(context, Manifest.permission.ACCESS_FINE_LOCATION),
            ),
            // The compat call, because `isLocationEnabled` is API 28 and the compat version also
            // answers sensibly on a device with no location provider at all. No manager is read as
            // "off": a phone that cannot locate itself is the services-off case in every way that
            // matters to the user.
            servicesOn = manager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false,
        )
    }
}

/** Answers from memory. Previews, and every test that is about the rules rather than the device. */
class FixedLocationAccess(
    var status: LocationStatus = LocationStatus(PermissionStatus.NotDetermined, servicesOn = true),
) : LocationAccessReader {
    override fun read(): LocationStatus = status
}
