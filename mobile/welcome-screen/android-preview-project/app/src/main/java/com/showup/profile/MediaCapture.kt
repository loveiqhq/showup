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

import kotlinx.coroutines.delay

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

    /**
     * Why the last `finish` produced nothing, in the platform's own words, or null.
     *
     * DIAGNOSTIC ONLY, AND SHOWN ONLY IN A DEBUG BUILD. `finish` returning null says a take
     * produced no file; it cannot say whether the encoder refused, the disk was full or the
     * camera went away mid-take, and those are different problems with different fixes. CameraX
     * reports exactly that in its Finalize event and this carries it out rather than dropping it
     * on the floor -- which is what happened for three rounds of "it does not work", each one
     * indistinguishable from the last.
     *
     * Defaulted to null so a fake, a preview and the voice recorder need say nothing.
     */
    fun failureDetail(): String? = null
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
    /**
     * Whether a take that STARTED goes on to produce a file.
     *
     * THE FAILURE THE REAL RECORDER HAS AND THIS FAKE COULD NOT EXPRESS. Until 27 September 2026
     * `finish` returned null only when `start` had already failed, so the one shape that actually
     * shipped -- a recording that runs, shows a counter for ten seconds, and writes nothing --
     * had no test anywhere. On a device that is the user filming, pressing stop, and landing back
     * on the card with no review screen; in the suite it was unreachable.
     *
     * It is a parameter rather than a per-kind setting because the model must treat both media
     * the same way, and a fake that could only fail for video would bake the asymmetry it is
     * meant to detect into the test.
     */
    private val finishSucceeds: Boolean = true,
    /**
     * How long `start` takes to answer, in virtual milliseconds.
     *
     * THE GAP BETWEEN ASKING AND RECORDING, which the real sessions have and this fake did not.
     * CameraX reports a [MediaCaptureSession] as started only once `VideoRecordEvent.Start`
     * arrives, and AVFoundation only once `didStartRecordingTo` fires; measured at three seconds
     * on a Pixel 7 emulator and a fraction of that on a phone. Zero here modelled a recorder that
     * begins instantly, which nothing does, and left the whole start window untested -- including
     * a Stop arriving inside it, which destroyed the review screen it had just produced.
     */
    private val startDelayMs: Long = 0L,
    private val pathFor: (MediaKind) -> String = { "/dev/null/${it.trackingValue}.take" },
) : MediaCaptureFactory {

    /** Every session this factory has made, in order, for a test to inspect. */
    val sessions = mutableListOf<FakeSession>()

    override fun create(kind: MediaKind): MediaCaptureSession =
        FakeSession(kind, startSucceeds, finishSucceeds, startDelayMs, pathFor(kind))
            .also { sessions += it }

    class FakeSession(
        val kind: MediaKind,
        private val startSucceeds: Boolean,
        private val finishSucceeds: Boolean,
        private val startDelayMs: Long,
        private val path: String,
    ) : MediaCaptureSession {
        var started = false
            private set
        var discarded = false
            private set
        private var requested = false
        private var finished = false
        private var onEnded: ((CaptureFailure) -> Unit)? = null

        override suspend fun start(onEnded: (CaptureFailure) -> Unit): Boolean {
            this.onEnded = onEnded
            requested = true
            if (startDelayMs > 0) delay(startDelayMs)
            // MIRRORS THE REAL SESSIONS. `finish` is reachable while `start` is still waiting for
            // the recorder to roll, because the session exists from the moment the take does.
            // When it gets there first it owns the take, and `start` must report nothing rather
            // than a failure the caller would write over the outcome already chosen.
            if (finished) return false
            started = startSucceeds
            return startSucceeds
        }

        override suspend fun finish(elapsedMs: Int): CaptureTake? {
            if (finished) return null
            finished = true
            // `requested`, NOT `started`: a take stopped inside the start gap has been asked for
            // but has not yet begun, and the real sessions wait for the first frame and then end
            // it rather than throwing it away.
            if (!requested || !finishSucceeds) return null
            return CaptureTake(
                path = path,
                durationMs = elapsedMs,
                mimeType = mimeTypeFor(kind),
                fileName = fileNameFor(kind),
            )
        }

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
