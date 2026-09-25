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
private class AndroidVideoSession(
    private val context: Context,
    private val controller: () -> LifecycleCameraController?,
    private val output: File,
) : MediaCaptureSession {

    private var recording: Recording? = null
    private var finished = false

    /** Completed by the Finalize event, which is the only place CameraX reports a real duration. */
    private val finalized = CompletableDeferred<Int?>()

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
        return runCatching {
            recording = camera.startRecording(
                FileOutputOptions.Builder(output).build(),
                // Audio on: a ten-second clip of a silent face is not what was asked for.
                AudioConfig.create(true),
                ContextCompat.getMainExecutor(context),
            ) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val nanos = event.recordingStats.recordedDurationNanos
                    val ms = (nanos / 1_000_000L).toInt()
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
    }

    override suspend fun finish(elapsedMs: Int): CaptureTake? {
        if (finished) return null
        finished = true
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
        const val FINALIZE_TIMEOUT_MS = 4_000L
    }
}
