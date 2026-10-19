/*
 * PhotoAccess.kt
 * ShowUp · the two permission questions the photo step can actually ask (SHOWUP-156)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * READ THIS BEFORE CHANGING ANYTHING IN HERE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The permission surface on this screen is SMALLER THAN IT LOOKS, and that is a build constraint
 * rather than an implementation detail. SHOWUP-156 states it as the decision the rest of the
 * ticket depends on:
 *
 *     Present the SYSTEM PICKER -- iOS PHPickerViewController, Android 13+ photo picker -- and do
 *     not request a library permission.
 *
 * The system picker runs out of process, lets the user browse their WHOLE library, and hands back
 * only what they chose. So:
 *
 *   · iOS needs no library access card at all, in any status.
 *   · Android 13+ needs none either.
 *   · iOS "Limited access" CANNOT HAPPEN. There is no `limited` state in this file and no
 *     partial-library banner anywhere in the build. An earlier draft had one; it was DELETED, not
 *     redesigned, because it explained a state we cannot enter.
 *
 * Build an in-app library browser instead and every one of those states comes back. Don't.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY [LibraryAccess.CanAsk] AND [LibraryAccess.Blocked] ARE BUILT AND NOT REACHED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This is the one thing in the photo step that a reader will think is a bug, so it is written down
 * rather than left to be rediscovered.
 *
 * The ticket says the library card applies on ANDROID <= 12 ONLY, because the photo picker arrived
 * in Android 13 and older versions genuinely gated the library behind `READ_EXTERNAL_STORAGE`.
 * This app's `minSdk` is 30, so API 30, 31 and 32 are in range and the states are real cases.
 *
 * Except that androidx's `PickVisualMedia` does not need that permission on those versions either.
 * Where the backported picker is present it uses it, and where it is not it falls back to
 * `ACTION_OPEN_DOCUMENT` — the Storage Access Framework, which is also out of process and also
 * permissionless. So on every API level this app supports, there is no library permission to be
 * missing, and [reader] returns [LibraryAccess.NotNeeded] every time.
 *
 * THE ANSWER IS NOT TO DELETE THE STATES, AND IT IS NOT TO REQUEST A PERMISSION. The ticket rules
 * out both in one sentence — "Do not delete it (old Android is real), and do not request a library
 * permission to make it appear" — and the second half matters more than the first: asking for
 * `READ_EXTERNAL_STORAGE` purely so that states D and E can be demonstrated would hand the app a
 * permission it has no use for, on the screen where a refusal costs the most.
 *
 * So the two states are built, rendered, swept at seventeen device sizes and unit tested, and the
 * READER is the single seam that decides whether anyone sees them. If the picker path ever stops
 * covering a supported version, that decision is one function here.
 */
package com.showup.profile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Whether the photo library can be reached, and if not, how to recover.
 *
 * TWO REFUSAL MODES, NOT ONE BOOLEAN. "Collapsing them is how a screen ends up telling a user to
 * open Settings when it could simply have asked" — so the card's button says what it will actually
 * do, and the app always knows which of the two it is in.
 */
enum class LibraryAccess {
    /**
     * No permission is involved. The grid works.
     *
     * Every platform and version this app supports, for the reasons in the file header.
     */
    NotNeeded,

    /**
     * Not granted, and the system will still show the prompt.
     *
     * The card's button RE-PROMPTS IN APP. No Settings trip, and the copy names no toggle, because
     * the user never has to go and find one.
     */
    CanAsk,

    /**
     * Permanently denied. The system will not prompt again.
     *
     * The card's button opens the app's details page, and the copy NAMES THE ROW to look for —
     * because neither platform can deep-link to a single permission toggle, so the user arrives on
     * a list and has to find it.
     */
    Blocked,
}

/**
 * Whether the camera can be used, and if not, how to recover.
 *
 * THE ONE PERMISSION THAT ALWAYS APPLIES, on both platforms, and the reason the source sheet
 * exists at all: the library path needs no permission and the camera path always does, so the two
 * cannot share one control.
 *
 * A DENIED CAMERA BLOCKS NOTHING. The library row still works and the step is still completable,
 * so [Blocked] is never a grid replacement and never a full-screen card — it is a quiet row in the
 * sheet the user just opened. The answer arrives where the question was asked.
 */
enum class CameraAccess {
    Granted,

    /** Never asked, or refused once. Tapping the row fires the OS prompt. */
    CanAsk,

    /** Permanently denied. The row goes quiet and grows a `Settings` pill. */
    Blocked,
}

/**
 * Reads the two statuses from the platform.
 *
 * An interface so the screen can be driven from a test and from a preview without a device, and so
 * the one decision in [AndroidPhotoAccess] is a single replaceable thing rather than a condition
 * scattered through a composable.
 */
interface PhotoAccessReader {
    fun library(): LibraryAccess
    fun camera(): CameraAccess
}

/**
 * The real reader.
 *
 * RE-READ ON EVERY FOREGROUND. "The most common bug on this screen is a user who granted access in
 * Settings returning to the blocked card." Nothing is cached here — every call asks the platform —
 * so the only thing the screen has to do is call it again when it resumes, which it does.
 */
class AndroidPhotoAccess(private val context: Context) : PhotoAccessReader {

    /**
     * Always [LibraryAccess.NotNeeded]. See the file header for why, at length.
     *
     * This is the seam. If a supported Android version ever needs a library permission again, it
     * is this function that changes, and the card, its two modes, its copy and its tests are all
     * already there.
     */
    override fun library(): LibraryAccess = LibraryAccess.NotNeeded

    override fun camera(): CameraAccess {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return CameraAccess.Granted

        // The rationale flag is how Android distinguishes "refused once" from "do not ask again",
        // and it is the ONLY way: there is no API that reports the difference directly. It reads
        // false both before the first ask and after a permanent denial, so a first run would look
        // permanently blocked -- which is why the app remembers whether it has asked.
        val activity = context as? Activity ?: return CameraAccess.CanAsk
        val rationale =
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        return when {
            rationale -> CameraAccess.CanAsk
            CameraAskLog.hasAsked(context) -> CameraAccess.Blocked
            else -> CameraAccess.CanAsk
        }
    }
}

/**
 * Whether the camera prompt has ever been shown.
 *
 * `shouldShowRequestPermissionRationale` returns false in two completely different situations --
 * before the first request, and after "don't ask again" -- and the platform offers nothing to tell
 * them apart. Without this record a fresh install would show the "blocked, go to Settings" row to
 * a user who has never been asked, and sending somebody to Settings to turn on a permission they
 * were never offered is worse than useless: the toggle they are told to find is already correct.
 *
 * One boolean, written the first time the prompt is launched.
 */
object CameraAskLog {
    /**
     * DELEGATES TO [PermissionAskLog], which the media step shares (SHOWUP-161).
     *
     * One record, not two. Video recording needs the same camera permission this screen asks for,
     * so if the user refuses it here the media screen must inherit that fact -- otherwise it reads
     * "no rationale, never asked" and shows two clean cards to somebody it should be offering a
     * re-prompt. The key spelling is unchanged, so installs that wrote the old record keep it.
     */
    fun hasAsked(context: Context): Boolean =
        PermissionAskLog.hasAsked(context, Manifest.permission.CAMERA)

    fun recordAsked(context: Context) {
        PermissionAskLog.recordAsked(context, Manifest.permission.CAMERA)
    }
}

/** A reader that answers whatever a preview or a test needs. */
class FixedPhotoAccess(
    private val library: LibraryAccess = LibraryAccess.NotNeeded,
    private val camera: CameraAccess = CameraAccess.CanAsk,
) : PhotoAccessReader {
    override fun library() = library
    override fun camera() = camera
}
