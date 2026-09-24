/*
 * NotificationAccess.kt
 * ShowUp · the notification permission status, and the two cases that skip the ask (SHOWUP-162)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FOUR VALUES, ONE VOCABULARY, BOTH PLATFORMS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `enums.json` §24 defines the set and the ticket uses it verbatim: `not_determined · granted ·
 * denied · restricted`. Not spelled per-platform -- iOS's `authorizationStatus` and Android's
 * rationale flag both map onto these four -- and `limited` is deliberately absent, because it is a
 * photo-library concept that lives on `permission_result` alone.
 *
 * `denied` COVERS PERMANENT DENIAL. The distinction between "denied once" and "denied for good" is
 * the platform's rationale flag, and §24 is explicit that it is a BEHAVIOUR branch rather than a
 * fifth value. Nothing on this screen branches on it: a denial is recovered on Stay reachable (10)
 * and nowhere else.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ANDROID 12 AND BELOW REPORTS `granted`, AND THAT IS NOT A SHORTCUT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * There is no `POST_NOTIFICATIONS` before API 33: notifications are on by default and there is
 * nothing to ask for. §24 says these users are "reported as granted", so that is what this
 * returns -- not `not_determined`, which would be a lie that raises a sheet that cannot exist.
 *
 * The consequence the ticket underlines: those users still need push registration and all five
 * categories. Being skipped by the ASK is not being skipped by the FEATURE.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE SCREEN IS SKIPPED BEFORE IT IS PUSHED, NEVER AFTER IT MOUNTS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * [shouldShowAsk] is the whole decision and it is a pure function of the status, so the host can
 * answer it before the screen exists. Two cases skip:
 *
 *   Android <= 12        nothing to ask for
 *   already determined   a restore-from-backup, or the app killed while the sheet was up
 *
 * The second is a GUARD, not a designed state. The system dialog is shown once per install, so a
 * screen whose only button raises a dialog that will not appear is a dead end -- and this screen
 * has no skip, no back and no close. Silently means silently: no toast, no confirmation, and no
 * flash of the screen before it navigates.
 */
package com.showup.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The OS's answer, in `enums.json` §24's vocabulary.
 *
 * [trackingValue] is what goes on the wire; the names are never typed at a call site.
 */
enum class NotificationPermission(val trackingValue: String) {
    NotDetermined("not_determined"),
    Granted("granted"),
    Denied("denied"),

    /**
     * Parental controls or MDM.
     *
     * Android has no equivalent and never returns it; iOS does. §24 says it is "treated as denied
     * everywhere in the product", which here means it is a determined status and so it skips the
     * ask -- there is nothing a sheet could change.
     */
    Restricted("restricted"),
    ;

    /** Whether the OS has an answer. The ask exists only for the one status that does not. */
    val isDetermined: Boolean get() = this != NotDetermined
}

/**
 * Reads the status. Re-read on every foreground -- never remembered.
 *
 * §24 again: a permission "is the device's answer and is re-read on every foreground rather than
 * remembered". A cached value is wrong the moment the user changes it in Settings, and this flow
 * has a screen whose entire reason to exist depends on the answer.
 */
interface NotificationAccessReader {
    fun read(): NotificationPermission
}

/**
 * Should the ask be shown at all?
 *
 * The single decision behind both skip cases, as a pure function so the host can take it before
 * the screen is pushed and a test can take it with no device.
 */
fun shouldShowAsk(status: NotificationPermission): Boolean = !status.isDetermined

/**
 * The status, from the three facts Android can report. A pure function, so the branch that
 * actually fires in production is testable with no Context and no device.
 *
 * `hasAsked` is the only way to tell a permission never requested from one refused: Android
 * reports both as not-granted, which is why [PermissionAskLog] exists and is shared with the photo
 * and media steps rather than re-invented here.
 */
fun notificationPermissionFor(
    sdkInt: Int,
    granted: Boolean,
    hasAsked: Boolean,
): NotificationPermission = when {
    // Below 33 there is no runtime permission at all. Granted, per §24 -- and the branch the
    // ticket calls "the one that actually fires in production".
    sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationPermission.Granted
    granted -> NotificationPermission.Granted
    hasAsked -> NotificationPermission.Denied
    else -> NotificationPermission.NotDetermined
}

/** The real reader. */
class AndroidNotificationAccess(
    private val context: Context,
    /** Injected so a test can exercise the API-level branch that only fires in production. */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : NotificationAccessReader {

    override fun read(): NotificationPermission {
        // The context is not touched below 33, where the answer does not depend on it.
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return notificationPermissionFor(sdkInt, granted = false, hasAsked = false)
        }
        return notificationPermissionFor(
            sdkInt = sdkInt,
            granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
            hasAsked = PermissionAskLog.hasAsked(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }
}

/** Answers from memory. Previews, and every test that is about the rules rather than the device. */
class FixedNotificationAccess(
    private val status: NotificationPermission = NotificationPermission.NotDetermined,
) : NotificationAccessReader {
    override fun read(): NotificationPermission = status
}
