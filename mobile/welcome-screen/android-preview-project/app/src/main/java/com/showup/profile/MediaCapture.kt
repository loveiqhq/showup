/*
 * MediaCapture.kt
 * ShowUp · the seam between "a take happened" and how it happened (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS AN INTERFACE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Neither real implementation can run in a unit test: CameraX needs a camera and a lifecycle, and
 * `MediaRecorder` needs a microphone. Everything ELSE on this screen — the cap, the attempt
 * counter, the retake loop, the interruption threshold, every one of the thirteen tracking events
 * — is decided by [MediaViewModel] and has nothing to do with either. Putting the recorders behind
 * this interface is what lets all of that be tested with no device attached.
 *
 * It is the same seam as `PhotoAccessReader`, for the same reason and with the same shape: one
 * small interface, one real implementation, one fake.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IT DELIBERATELY DOES NOT DO
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * IT DOES NOT ENFORCE THE CAP. The 10 and 15 second limits are product rules and they live in the
 * view model with everything else that can be tested. A recorder that stopped itself would be a
 * second place the rule lives, and `MediaRecorder.setMaxDuration` in particular reports the stop
 * on a callback thread with no way to distinguish it from a failure — so the one place that knows
 * WHY a take ended would be the one place that could not say so.
 *
 * IT DOES NOT COUNT TIME for the UI. The elapsed value the progress bar reads is the view model's
 * own clock, so the bar advances identically in a test, in a preview and on a device.
 */
package com.showup.profile

/** A finished take: where the bytes are, and how long they ran. */
data class CaptureTake(
    val path: String,
    /**
     * The recorder's own measurement where it has one, the caller's clock where it does not.
     *
     * CameraX reports `recordedDurationNanos` on finalisation and that is the honest number — a
     * wall clock includes the moment between asking to stop and the encoder closing the file.
     * `MediaRecorder` reports nothing, so audio falls back to the elapsed value it was given.
     */
    val durationMs: Int,
    val mimeType: String,
    val fileName: String,
)

/** Why a session ended without producing a usable take. */
enum class CaptureFailure {
    /** The recorder would not start: hardware in use, no encoder, permission revoked mid-flight. */
    CouldNotStart,

    /**
     * A call, an alarm, or another app took the microphone.
     *
     * Reported separately from [CouldNotStart] because the product rule differs: an interruption
     * that captured at least [MediaLimits.INTERRUPTION_KEEP_MS] still shows the review screen.
     */
    Interrupted,

    /** The take ran but nothing usable came out of it. */
    NoOutput,
}

/** What a session produced. */
sealed interface CaptureResult {
    data class Completed(val take: CaptureTake) : CaptureResult

    /**
     * The take ended early and involuntarily.
     *
     * [take] is present when something was captured before it ended, so the caller can apply the
     * two-second rule. Never a silent resume either way.
     */
    data class Ended(val reason: CaptureFailure, val take: CaptureTake?) : CaptureResult
}

/**
 * One take, from start to file.
 *
 * Single use. A retake creates a new session, which is what makes the attempt counter meaningful
 * and keeps a half-finished recorder from being reused.
 */
interface MediaCaptureSession {

    /**
     * Begins recording. False if it could not start at all.
     *
     * @param onEnded called only when the take ends WITHOUT [finish] being called — an interruption
     *   or a hardware failure. The caller decides what that means; this only reports it.
     */
    suspend fun start(onEnded: (CaptureFailure) -> Unit): Boolean

    /** Ends the take and closes the file. Null if nothing usable was written. */
    suspend fun finish(elapsedMs: Int): CaptureTake?

    /** Abandons the take and deletes whatever was written. Safe to call twice. */
    suspend fun discard()
}

/** Creates a session per take. */
interface MediaCaptureFactory {
    fun create(kind: MediaKind): MediaCaptureSession
}

/**
 * A capture that writes nothing, for tests and previews.
 *
 * Returns a take whose length is exactly the elapsed value it was handed, so a test can assert the
 * cap, the attempt counter and the duration on every tracking payload without a camera.
 */
class FakeMediaCapture(
    private val startSucceeds: Boolean = true,
    private val pathFor: (MediaKind) -> String = { "/dev/null/${it.trackingValue}.take" },
) : MediaCaptureFactory {

    /** Every session this factory has made, in order, for a test to inspect. */
    val sessions = mutableListOf<FakeSession>()

    override fun create(kind: MediaKind): MediaCaptureSession =
        FakeSession(kind, startSucceeds, pathFor(kind)).also { sessions += it }

    class FakeSession(
        val kind: MediaKind,
        private val startSucceeds: Boolean,
        private val path: String,
    ) : MediaCaptureSession {
        var started = false
            private set
        var discarded = false
            private set
        private var onEnded: ((CaptureFailure) -> Unit)? = null

        override suspend fun start(onEnded: (CaptureFailure) -> Unit): Boolean {
            this.onEnded = onEnded
            started = startSucceeds
            return startSucceeds
        }

        override suspend fun finish(elapsedMs: Int): CaptureTake? =
            if (!started) null
            else CaptureTake(
                path = path,
                durationMs = elapsedMs,
                mimeType = mimeTypeFor(kind),
                fileName = fileNameFor(kind),
            )

        override suspend fun discard() {
            discarded = true
        }

        /** Drives the interruption path from a test. */
        fun interrupt(reason: CaptureFailure = CaptureFailure.Interrupted) {
            onEnded?.invoke(reason)
        }
    }
}

/**
 * What each medium is uploaded as.
 *
 * Both are MPEG-4 containers, which is what Android's encoder writes; the server accepts these
 * exact two spellings per slot and refuses a video in the voice slot, so getting this wrong is a
 * 415 rather than a silent mis-store.
 */
fun mimeTypeFor(kind: MediaKind): String = when (kind) {
    MediaKind.Video -> "video/mp4"
    MediaKind.Voice -> "audio/mp4"
}

fun fileNameFor(kind: MediaKind): String = when (kind) {
    MediaKind.Video -> "take.mp4"
    MediaKind.Voice -> "take.m4a"
}
