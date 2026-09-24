/*
 * MediaAccess.kt
 * ShowUp · camera and microphone status for the media step (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * VIDEO NEEDS CAMERA *AND* MICROPHONE. VOICE NEEDS MICROPHONE ONLY.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * That single sentence is the whole shape of this file, and it is why a blocked microphone is the
 * only status that blocks the entire step. A blocked camera blocks one card; the voice card stays
 * fully functional, "exactly as the library row stayed functional on screen 06".
 *
 * So the treatment is SCALED TO WHAT IS ACTUALLY BLOCKED. Never a screen replacement, never a card
 * over the voice slot, and never a gate on Continue — which is optional in every state.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY FOUR STATES HERE WHERE [CameraAccess] HAS THREE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This looks like a duplicate of the photo step's enum and is not, and the difference is the one
 * thing a reader will otherwise "fix".
 *
 * On screen 06 the camera row IS the control you tap, and tapping it asks. "Never asked" and
 * "refused once, can ask again" therefore behave identically there, so three states is the honest
 * count. Here the OS alert fires on the COMMIT CTA — not on entry, not on `See the prompts` — and
 * the permission row is an ERROR STATE that must not exist before the first refusal. A user who
 * has never been asked must see two clean cards.
 *
 * Collapsing [NotDetermined] into [CanAsk] would put a "we need access" row in front of somebody
 * who has not yet been asked for anything, on the screen where a refusal costs the most.
 *
 * What IS shared is [PermissionAskLog], and sharing it is not optional: if the photo step already
 * asked for the camera and was refused, this screen has to know, or it will tell a user to go and
 * fix a toggle they were never offered.
 */
package com.showup.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Whether a capture permission can be used, and if not, how to recover.
 *
 * The app ALWAYS knows which of these it is in. It NEVER knows which toggle the user will land on
 * — neither platform deep-links to a single permission row — which is why [CanAsk] and [Blocked]
 * are two behaviours with two different buttons and never one label.
 */
enum class MediaPermission {
    Granted,

    /**
     * Never asked. No row is shown; the OS alert fires on the commit CTA.
     *
     * The state every first-time user is in, and the reason this enum has four entries.
     */
    NotDetermined,

    /**
     * Refused once, and the system will still show the prompt.
     *
     * The row RE-PROMPTS IN APP. No Settings trip, and the copy names no toggle, because the user
     * never has to go and find one.
     */
    CanAsk,

    /**
     * Permanently denied. The system will not prompt again.
     *
     * The row opens our own app's details page, and the copy names the platform's own label for
     * the row to look for — because the user arrives on a list and has to find it.
     */
    Blocked,
    ;

    /** Whether a permission row is drawn at all. Nothing is drawn before the first refusal. */
    val needsRow: Boolean get() = this == CanAsk || this == Blocked
}

/** Both statuses, read together, because every decision on this screen needs both. */
data class MediaAccess(
    val camera: MediaPermission = MediaPermission.NotDetermined,
    val microphone: MediaPermission = MediaPermission.NotDetermined,
) {
    /**
     * What stands between the user and a take of this kind, or null when nothing does.
     *
     * MICROPHONE IS CHECKED FIRST FOR VIDEO, and the order is the point rather than a detail. A
     * video needs both; if both are refused the user can only be told about one at a time, and the
     * microphone is the one worth naming — it blocks the voice card too, so fixing it recovers
     * more of the screen. Reporting the camera first would send someone to Settings, have them fix
     * the camera, and return to a video card still blocked.
     */
    fun blockerFor(kind: MediaKind): MediaBlocker? {
        if (microphone.needsRow) return MediaBlocker(MediaCapability.Microphone, microphone)
        if (kind == MediaKind.Video && camera.needsRow) {
            return MediaBlocker(MediaCapability.Camera, camera)
        }
        return null
    }

    /** Whether a take of this kind can start without asking for anything. */
    fun isReady(kind: MediaKind): Boolean = when (kind) {
        MediaKind.Voice -> microphone == MediaPermission.Granted
        MediaKind.Video ->
            microphone == MediaPermission.Granted && camera == MediaPermission.Granted
    }

    /** What still has to be requested before a take of this kind can start. */
    fun missingFor(kind: MediaKind): List<MediaCapability> = buildList {
        if (kind == MediaKind.Video && camera != MediaPermission.Granted) {
            add(MediaCapability.Camera)
        }
        if (microphone != MediaPermission.Granted) add(MediaCapability.Microphone)
    }
}

/** Which device capability a row is about. */
enum class MediaCapability(val permission: String) {
    Camera(Manifest.permission.CAMERA),
    Microphone(Manifest.permission.RECORD_AUDIO),
}

/** A permission row's subject and mode — everything its copy and its button need. */
data class MediaBlocker(
    val capability: MediaCapability,
    val status: MediaPermission,
)

/**
 * Reads both statuses from the platform.
 *
 * An interface so the screen can be driven from a test and a preview with no device, and so the
 * one real decision lives in [AndroidMediaAccess] rather than scattered through a composable.
 */
interface MediaAccessReader {
    fun read(): MediaAccess

    /**
     * The platform's own label for a permission row, for the blocked copy.
     *
     * The ticket forbids hard-coding `Camera` and `Microphone`: "Row labels in the blocked strings
     * are replaced by the platform's own label — never hard-code Camera or Microphone if the OS
     * calls it something else." Localised builds and OEM skins both rename these.
     */
    fun platformLabel(capability: MediaCapability): String
}

/**
 * The real reader.
 *
 * NOTHING IS CACHED. Every call asks the platform, so "re-read both statuses on every foreground"
 * is a call site rather than an invalidation problem. A user who granted access in Settings and
 * came back to a blocked row will not try twice.
 */
class AndroidMediaAccess(private val context: Context) : MediaAccessReader {

    override fun read(): MediaAccess = MediaAccess(
        camera = status(MediaCapability.Camera),
        microphone = status(MediaCapability.Microphone),
    )

    private fun status(capability: MediaCapability): MediaPermission {
        val granted = ContextCompat.checkSelfPermission(context, capability.permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return MediaPermission.Granted

        // The rationale flag is how Android distinguishes "refused once" from "do not ask again",
        // and it is the only way -- no API reports the difference directly. It reads false BOTH
        // before the first request and after a permanent denial, so the ask log is what tells
        // those two apart. Without it a fresh install shows "blocked, go to Settings" to somebody
        // who has never been asked.
        val activity = context as? Activity ?: return MediaPermission.NotDetermined
        val rationale =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, capability.permission)
        return when {
            rationale -> MediaPermission.CanAsk
            PermissionAskLog.hasAsked(context, capability.permission) -> MediaPermission.Blocked
            else -> MediaPermission.NotDetermined
        }
    }

    /**
     * The permission GROUP's label, not the permission's own.
     *
     * `PermissionInfo.loadLabel` for CAMERA returns a sentence -- "take pictures and record video"
     * -- which is a description of what the app may do, not the name of the row in Settings. The
     * group label is the row: "Camera", "Microphone", translated by the platform.
     */
    override fun platformLabel(capability: MediaCapability): String {
        val fallback = when (capability) {
            MediaCapability.Camera -> "Camera"
            MediaCapability.Microphone -> "Microphone"
        }
        return runCatching {
            val pm = context.packageManager
            val group = pm.getPermissionInfo(capability.permission, 0).group ?: return fallback
            pm.getPermissionGroupInfo(group, 0).loadLabel(pm).toString()
                .replaceFirstChar { it.uppercase() }
        }.getOrDefault(fallback)
    }
}

/**
 * Whether a permission prompt has ever been shown, per permission.
 *
 * SHARED WITH THE PHOTO STEP ON PURPOSE, and it is the reason this is not a private detail of
 * either screen. `shouldShowRequestPermissionRationale` returns false in two completely different
 * situations -- before the first request, and after "don't ask again" -- and the platform offers
 * nothing to tell them apart. If screen 06 asked for the camera and was refused, this screen has
 * to inherit that fact, or it shows a user a Settings row for a toggle they were never offered.
 *
 * The camera key is spelled exactly as [CameraAskLog] spelled it, so existing installs keep the
 * record they already wrote.
 */
object PermissionAskLog {
    private const val PREFS = "showup.permissions"

    private fun key(permission: String): String = when (permission) {
        Manifest.permission.CAMERA -> "asked_camera"
        Manifest.permission.RECORD_AUDIO -> "asked_microphone"
        else -> "asked_${permission.substringAfterLast('.').lowercase()}"
    }

    fun hasAsked(context: Context, permission: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(permission), false)

    fun recordAsked(context: Context, permission: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(permission), true)
            .apply()
    }
}

/** A reader that answers whatever a preview or a test needs. */
class FixedMediaAccess(
    private val access: MediaAccess = MediaAccess(),
    private val labels: Map<MediaCapability, String> = emptyMap(),
) : MediaAccessReader {
    override fun read(): MediaAccess = access
    override fun platformLabel(capability: MediaCapability): String =
        labels[capability] ?: when (capability) {
            MediaCapability.Camera -> "Camera"
            MediaCapability.Microphone -> "Microphone"
        }
}
