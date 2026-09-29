/*
 * AndroidMediaCapture.kt
 * ShowUp · the two real recorders (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * OUR OWN SESSION, NOT THE CAMERA APP
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This file is the deliberate opposite of the photo step. Photos hands off to the system picker
 * and is better for it; media runs capture in process, and the ticket rules the alternative out in
 * as many words: an intent to the OS camera app "is NOT an acceptable substitute -- it loses the
 * prompt, the 10-second cap and the review screen".
 *
 * All three are the screen. The prompt has to sit under the lens for the whole take because the
 * user is answering a question; the cap has to be ours because 10 seconds is the product decision
 * the 16 Sep pass was about; and the review screen has to exist because stopping produces a take
 * and only accepting produces an artefact.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * NOTHING HERE DECIDES ANYTHING
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * No cap, no minimum length, no retake counting, no tracking. This file turns a microphone and a
 * camera into a file on disk and reports when that stopped happening. Every rule lives in
 * [MediaViewModel], where it can be tested without hardware.
 */
package com.showup.profile

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Where takes are written.
 *
 * `cacheDir`, not `filesDir`. A take is a temporary artefact on its way to the server, and the one
 * that is NOT accepted must not survive: the user recorded their face, looked at it and decided
 * against it, and leaving that on disk is the wrong default in the one place it matters most. The
 * OS is also free to reclaim the directory, which is correct for something already uploaded.
 */
private fun takeFile(context: Context, kind: MediaKind): File {
    val dir = File(context.cacheDir, "media-takes").apply { mkdirs() }
    return File(dir, "${kind.trackingValue}-${System.currentTimeMillis()}.${extensionFor(kind)}")
}

private fun extensionFor(kind: MediaKind) = when (kind) {
    MediaKind.Video -> "mp4"
    MediaKind.Voice -> "m4a"
}

/**
 * Creates the real sessions.
 *
 * [cameraController] is supplied by the recording screen rather than built here, because CameraX
 * binds to a lifecycle and a factory has none. The screen that owns the viewfinder owns the
 * controller; this turns it into takes.
 */
class AndroidMediaCaptureFactory(
    private val context: Context,
    private val cameraController: () -> LifecycleCameraController?,
) : MediaCaptureFactory {

    override fun create(kind: MediaKind): MediaCaptureSession = when (kind) {
        MediaKind.Voice -> AndroidVoiceSession(context, takeFile(context, kind))
        MediaKind.Video -> AndroidVideoSession(
            context,
            cameraController,
            takeFile(context, kind),
        )
    }
}

/**
 * A voice take, through `MediaRecorder`.
 *
 * MPEG-4 / AAC, which is what the server accepts for the voice slot and what every Android device
 * can encode. 96 kbps mono at 44.1 kHz: fifteen seconds of speech is about 180 KB, comfortably
 * inside the server's 8 MB ceiling with no compression step of our own.
 */
private class AndroidVoiceSession(
    private val context: Context,
    private val output: File,
) : MediaCaptureSession {

    private var recorder: MediaRecorder? = null
    private var focusRequest: AudioFocusRequest? = null
    private var finished = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun start(onEnded: (CaptureFailure) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    // Deprecated at API 31 and the only constructor below it. minSdk is 30, so
                    // this branch is one real API level rather than dead code.
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(96_000)
                rec.setAudioSamplingRate(44_100)
                rec.setAudioChannels(1)
                rec.setOutputFile(output.absolutePath)
                // NO setMaxDuration. The cap is a product rule and lives in the view model -- see
                // MediaCapture's header. setMaxDuration reports its stop through the same info
                // callback as everything else, so the one place that knows WHY a take ended would
                // be the one place unable to say.
                rec.setOnErrorListener { _, _, _ -> onEnded(CaptureFailure.NoOutput) }
                rec.prepare()
                rec.start()
                recorder = rec
                requestFocus(onEnded)
                true
            }.getOrElse {
                releaseQuietly()
                false
            }
        }

    /**
     * Audio focus, which is how Android says "a call is arriving".
     *
     * The platform has no microphone-interruption callback; losing focus is the signal, and
     * `AUDIOFOCUS_LOSS` / `LOSS_TRANSIENT` is what an incoming call, an alarm or another recorder
     * produces. Requesting focus is also the polite half: it tells whatever is playing to stop, so
     * the take does not capture the user's own music.
     */
    private fun requestFocus(onEnded: (CaptureFailure) -> Unit) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    onEnded(CaptureFailure.Interrupted)
                }
            }
            .build()
        focusRequest = request
        manager.requestAudioFocus(request)
    }

    override suspend fun finish(elapsedMs: Int): CaptureTake? = withContext(Dispatchers.IO) {
        if (finished) return@withContext null
        finished = true
        val stopped = runCatching {
            recorder?.stop()
        }.isSuccess
        releaseQuietly()
        // `stop()` throws when the take was too short for the encoder to write a valid file. That
        // is a real outcome rather than a crash: nothing usable exists, so the caller is told
        // nothing was produced and discards it.
        if (!stopped || !output.exists() || output.length() == 0L) {
            output.delete()
            return@withContext null
        }
        CaptureTake(
            path = output.absolutePath,
            // MediaRecorder reports no duration, so the caller's clock is the only measurement.
            durationMs = elapsedMs,
            mimeType = mimeTypeFor(MediaKind.Voice),
            fileName = fileNameFor(MediaKind.Voice),
        )
    }

    override suspend fun discard() = withContext(Dispatchers.IO) {
        if (!finished) {
            finished = true
            runCatching { recorder?.stop() }
        }
        releaseQuietly()
        output.delete()
        Unit
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
        focusRequest?.let { request ->
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching { manager?.abandonAudioFocusRequest(request) }
        }
        focusRequest = null
    }
}

/**
 * A video take, through CameraX.
 *
 * The controller is bound by the viewfinder composable; this drives a [Recording] on it. Audio is
 * captured with the video, which is why the video card needs BOTH permissions and the voice card
 * needs only one -- the single fact the whole permission matrix is derived from.
 */
/**
 * CameraX's finalize error codes, named.
 *
 * `VideoRecordEvent.Finalize.ERROR_*` are plain ints and the class offers no name for them, so a
 * report would otherwise read "error 7" -- which is true and useless. The ones that actually
 * happen are worth spelling: an emulator's software encoder gives ENCODING_FAILED or
 * RECORDER_ERROR, a full device gives INSUFFICIENT_STORAGE, and a camera taken away mid-take
 * gives SOURCE_INACTIVE. Each is somebody else's problem to fix and they are not the same
 * somebody.
 */
private fun finalizeErrorName(code: Int): String = when (code) {
    VideoRecordEvent.Finalize.ERROR_NONE -> "none"
    VideoRecordEvent.Finalize.ERROR_UNKNOWN -> "unknown"
    VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "file size limit"
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "insufficient storage"
    VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "camera source inactive"
    VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "invalid output options"
    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "encoding failed"
    VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "recorder error"
    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "no valid data"
    VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "duration limit"
    else -> "code $code"
}

private class AndroidVideoSession(
    private val context: Context,
    private val controller: () -> LifecycleCameraController?,
    private val output: File,
) : MediaCaptureSession {

    private var recording: Recording? = null
    private var finished = false

    /** Completed by the Finalize event, which is the only place CameraX reports a real duration. */
    private val finalized = CompletableDeferred<Int?>()

    /**
     * What CameraX said went wrong, for a debug build to show.
     *
     * The Finalize event's error code is the ONLY place the platform explains itself, and it was
     * being read for a yes/no and then discarded. Kept here so `failureDetail` can hand it to the
     * screen when a take produces nothing.
     */
    private var finalizeError: String? = null

    /**
     * Completed when the recorder is genuinely rolling, or false if it never did.
     *
     * THE DIFFERENCE BETWEEN ASKING AND RECORDING, and the bug this field exists for.
     * `startRecording` returns a [Recording] as soon as the request is accepted -- the Recorder is
     * merely PENDING_RECORDING at that point. Frames do not exist until CameraX emits
     * [VideoRecordEvent.Start], which is when the encoder has its input surface and the camera is
     * streaming into it.
     *
     * Measured on a Pixel 7 emulator, that gap is THREE SECONDS: start requested at 05.480, Start
     * event at 08.476. On a physical phone it is closer to two hundred milliseconds. It is never
     * zero, and two real defects lived in it:
     *
     *  - a take stopped inside the gap has no frames at all, so the muxer writes nothing and
     *    CameraX finalises with ERROR_NO_VALID_DATA -- reported from the emulator as "that
     *    recording didn't save" with `no valid data (8), 0ms, 0 bytes`;
     *  - and every take that DID survive was short by the width of the gap, because the ten-second
     *    clock started when we asked rather than when the camera answered.
     *
     * The second is the interesting half: it is invisible, it happens on real hardware, and it
     * makes the counter disagree with the file it is supposedly counting.
     */
    private val started = CompletableDeferred<Boolean>()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun start(onEnded: (CaptureFailure) -> Unit): Boolean {
        val camera = controller() ?: return false
        // NOT `setEnabledUseCases` HERE. Enabling a use case rebinds the camera session, and a
        // `startRecording` issued against a rebind still in flight attaches to nothing: the
        // encoder never writes, `finish` finds a zero-byte file, and the user gets no review
        // screen. The controller is bound with VIDEO_CAPTURE already on -- see MainActivity.
        //
        // Checked rather than assumed, because the failure it prevents is silent: a recording
        // that appears to run for ten seconds and produces no file.
        if (!camera.isVideoCaptureEnabled) return false

        // AND WAIT FOR THE CAMERA TO ACTUALLY BE OPEN, which is a different question.
        //
        // `isVideoCaptureEnabled` answers "is VIDEO_CAPTURE among the configured use cases" and
        // is true whether or not a camera was ever opened -- it read true for the whole of the
        // bug it was added to catch. `cameraInfo` is null until a bind has really produced a
        // camera, so it is the readiness signal.
        //
        // The wait exists because the grant and the first take are the SAME GESTURE: the user
        // answers the OS dialog and `permissionResult` calls `beginTake` immediately, so the
        // bind that the grant triggers may still be in flight one composition later. Polling a
        // bounded number of times is not elegant and is honest -- CameraX offers no "bound"
        // signal to await, and the alternative is the race that shipped.
        val ready = withTimeoutOrNull(CAMERA_READY_TIMEOUT_MS) {
            while (camera.cameraInfo == null) delay(CAMERA_READY_POLL_MS)
            true
        }
        if (ready != true) return false
        val requested = runCatching {
            recording = camera.startRecording(
                FileOutputOptions.Builder(output).build(),
                // Audio on: a ten-second clip of a silent face is not what was asked for.
                AudioConfig.create(true),
                ContextCompat.getMainExecutor(context),
            ) { event ->
                // Idempotent by construction: `complete` on an already-completed deferred returns
                // false and changes nothing, so a Finalize arriving after a normal Start cannot
                // retroactively turn a good take into a failed start.
                if (event is VideoRecordEvent.Start) started.complete(true)
                if (event is VideoRecordEvent.Finalize) {
                    // A Finalize with no Start before it means the take never rolled. Answering
                    // the wait here rather than letting it time out turns an eight-second stall
                    // into an immediate and accurate "we could not start".
                    started.complete(false)
                    val nanos = event.recordingStats.recordedDurationNanos
                    val ms = (nanos / 1_000_000L).toInt()
                    if (event.hasError()) {
                        // The code, its name, and what the recorder thinks it wrote. A number
                        // alone sends whoever reads it to the documentation; the name is the
                        // half that makes a bug report actionable.
                        finalizeError =
                            "${finalizeErrorName(event.error)} (${event.error}), ${ms}ms, " +
                                "${output.length()} bytes"
                    }
                    // hasError covers a full disk, a revoked permission mid-take and the source
                    // becoming inactive. Some errors still leave a playable prefix, which is why
                    // the duration travels either way and the caller decides what to keep.
                    finalized.complete(if (event.hasError() && ms <= 0) null else ms)
                    if (event.hasError() && !finished) onEnded(CaptureFailure.Interrupted)
                }
            }
            true
        }.getOrElse {
            recording = null
            false
        }
        if (!requested) return false

        // AND NOW WAIT FOR THE CAMERA TO ANSWER.
        //
        // This is the whole fix. Returning here rather than above means [MediaViewModel]'s clock
        // is anchored to the first frame instead of to the request, so the ten-second cap measures
        // ten seconds of video and a Stop can never land before the recorder has rolled.
        //
        // The cost is that the viewfinder sits at 0:00 for the length of the gap -- a blink on a
        // phone, three seconds on an emulator. That is the truth, and a counter that runs while
        // nothing is being recorded is the lie it replaces.
        val rolling = withTimeoutOrNull(START_TIMEOUT_MS) { started.await() }

        // Stop arrived while we were waiting. `finish` set `finished`, owns the recording and the
        // file, and has already decided the outcome -- so this must not clean up underneath it,
        // and must not report a start failure over the top of it.
        if (finished) return false

        if (rolling != true) {
            runCatching { recording?.stop() }
            recording = null
            withContext(Dispatchers.IO) { output.delete() }
            return false
        }
        return true
    }

    override suspend fun finish(elapsedMs: Int): CaptureTake? {
        if (finished) return null
        finished = true
        // STOP ONLY WHAT HAS STARTED.
        //
        // Reachable when the user presses Stop inside the start gap, because the session exists
        // from the moment the take does. Stopping a recording that has produced no frames is
        // precisely the ERROR_NO_VALID_DATA case, so wait for the first frame before ending it --
        // bounded, because a recorder that never rolls must still let go of the screen.
        withTimeoutOrNull(START_TIMEOUT_MS) { started.await() }
        recording?.stop()
        recording = null

        // Finalisation is asynchronous, and the file is not closed until it arrives. The timeout
        // is a backstop against a callback that never comes rather than an expected path -- without
        // it a stuck encoder would hang the review screen forever.
        val reported = withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { finalized.await() }
        return withContext(Dispatchers.IO) {
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                return@withContext null
            }
            CaptureTake(
                path = output.absolutePath,
                // CameraX's own measurement wins where it has one: a wall clock also counts the
                // moment between asking to stop and the encoder closing the file.
                durationMs = reported?.takeIf { it > 0 } ?: elapsedMs,
                mimeType = mimeTypeFor(MediaKind.Video),
                fileName = fileNameFor(MediaKind.Video),
            )
        }
    }

    override fun failureDetail(): String? = finalizeError

    override suspend fun discard() {
        if (!finished) {
            finished = true
            recording?.stop()
            recording = null
            withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { finalized.await() }
        }
        withContext(Dispatchers.IO) { output.delete() }
    }

    private companion object {
        /**
         * How long to wait for CameraX to close the file.
         *
         * FIFTEEN SECONDS, NOT FOUR, and the four was a guess that cost a week. Finalisation is
         * the muxer writing the MP4's index and closing the file, and until it lands the file on
         * disk is legitimately empty -- so a wait that gives up early reads a zero-byte file and
         * reports "that recording didn't save" for a take that was about to be perfectly fine.
         *
         * On a phone it arrives in well under a second, so the extra eleven cost nothing and are
         * never spent. On an emulator, where the video encoder is software and slow, they are
         * the difference between a take and a shrug. The timeout exists for a callback that
         * never comes at all, and that is a much rarer thing than a slow one.
         */
        const val FINALIZE_TIMEOUT_MS = 15_000L

        /**
         * How long to wait for a bound camera before giving up on the take.
         *
         * Long enough for a bind triggered by the permission grant one gesture earlier, short
         * enough that a user whose camera will never open is told so rather than left watching a
         * viewfinder that is not coming.
         */
        /**
         * How long to wait for [VideoRecordEvent.Start] before calling the take a failure.
         *
         * EIGHT SECONDS, against a measured worst case of three. The emulator's software AVC
         * encoder is the slow case and it is the one that matters here, because it is where this
         * is developed; a phone's hardware encoder answers in a fraction of a second and never
         * spends any of this. Like [FINALIZE_TIMEOUT_MS], the timeout is a backstop against a
         * callback that never arrives at all, not a budget for a slow one -- so it sits well
         * clear of the slowest thing we have actually seen rather than close to it.
         */
        const val START_TIMEOUT_MS = 8_000L

        const val CAMERA_READY_TIMEOUT_MS = 3_000L
        const val CAMERA_READY_POLL_MS = 50L
    }
}
