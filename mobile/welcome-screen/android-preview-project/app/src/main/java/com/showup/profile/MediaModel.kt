/*
 * MediaModel.kt
 * ShowUp · the media step's state, with no Compose and no Android in it (SHOWUP-161)
 *
 * Plain types and pure functions, so every rule on this screen is testable without a device, a
 * camera or a microphone — which matters more here than anywhere else in the flow, because the two
 * things this screen actually does cannot be exercised in a unit test at all.
 */
package com.showup.profile

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The hard caps. 10 seconds of video, 15 of voice — the point of the 16 Sep pass. */
object MediaLimits {
    const val VIDEO_MS = 10_000
    const val VOICE_MS = 15_000

    /**
     * How much of a take survives an interruption.
     *
     * A call, an alarm or another app taking the microphone ends the take. At or above this, the
     * review screen is shown; below it the take is discarded and we return to the card. Never a
     * silent resume.
     *
     * THE ONLY NUMBER IN THE TICKET THAT IS A PROPOSAL rather than a decision, and it is named
     * here so changing it is one line. There is deliberately NO minimum on a take the user stopped
     * themselves: confirmed 17 September 2026 — any deliberate Stop lands on review and can be
     * kept, and `stop_reason` is what will show whether a floor is needed.
     */
    const val INTERRUPTION_KEEP_MS = 2_000

    fun maxMs(kind: MediaKind): Int = when (kind) {
        MediaKind.Video -> VIDEO_MS
        MediaKind.Voice -> VOICE_MS
    }
}

/**
 * Where a recording is on its way to the server.
 *
 * FOUR CLIENT STATES AND ONE SERVER STATE. Queued, in flight and failed are things only the client
 * can know; the server's answer is "the bytes are stored" or nothing at all. That asymmetry is why
 * there is no upload-status column in the database and why this enum lives here.
 *
 * [Failed] NEVER BLOCKS THE FLOW. It surfaces on the card, not as a modal, and the user can leave
 * the screen — an upload still in flight when Continue is pressed keeps going.
 */
enum class MediaUploadStatus { Queued, InFlight, Confirmed, Failed }

/** Why a take ended. §8 `stop_reason`, a closed set of two. */
enum class MediaStopReason(val trackingValue: String) {
    /** The user pressed Stop. */
    UserStop("user_stop"),

    /** The cap did. A cap that ends most takes is a cap that is too short — that is the measurement. */
    MaxLength("max_length"),
}

/** Where a retake or a delete was invoked from. §8 `from`, a closed set of two. */
enum class MediaActedFrom(val trackingValue: String) {
    /** A take not yet kept. */
    Review("review"),

    /** An artefact already on the profile. */
    MediaCard("media_card"),
}

/** How the prompt list was reached. §21 `entry_point`. */
enum class MediaEntryPoint(val trackingValue: String) {
    /** The CTA on an empty card — the intended first path. */
    SeeThePrompts("see_the_prompts"),

    /** The list reopened over something already recorded. */
    Retake("retake"),
}

/** Where the previewed prompt came from. §22 `preview_source`. */
enum class MediaPreviewSource(val trackingValue: String) {
    Ranked("ranked"),
    Fallback("fallback"),
    ;

    companion object {
        fun fromServer(value: String?): MediaPreviewSource =
            if (value == "ranked") Ranked else Fallback
    }
}

/**
 * A recording that is on the profile, or on its way there.
 *
 * [promptId] is a §20 id and never a display string. [localPath] outlives [remoteId] because the
 * card plays the local file while the upload is still in flight — the whole point of filling the
 * card optimistically.
 */
data class MediaArtefact(
    val kind: MediaKind,
    val promptId: String,
    val durationMs: Int,
    val localPath: String?,
    val remoteId: String? = null,
    val url: String? = null,
    val status: MediaUploadStatus = MediaUploadStatus.Queued,
) {
    val prompt: MediaPrompt? get() = MediaPrompts.byId(promptId)
    val isOwnPrompt: Boolean get() = promptId == MediaPrompts.OWN_IDEA
}

/** Which half of a capture sub-screen is showing. */
enum class RecordingPhase { Recording, Review }

/**
 * A take in progress, or one waiting to be kept.
 *
 * NOT AN ARTEFACT UNTIL IT IS ACCEPTED. Stopping produces one of these; only `Use this clip` turns
 * it into a [MediaArtefact]. That distinction is the whole reason the review screen exists, and
 * collapsing the two types is how a build ends up creating the artefact on Stop.
 */
data class MediaTake(
    val kind: MediaKind,
    val promptId: String,
    val phase: RecordingPhase = RecordingPhase.Recording,
    val elapsedMs: Int = 0,
    val path: String? = null,
    /** Counts takes of the same prompt, from 1, so a retake loop is readable without timestamps. */
    val attempt: Int = 1,
    val stopReason: MediaStopReason? = null,
    /** How many times the user has played it back on review. */
    val playCount: Int = 0,
) {
    val prompt: MediaPrompt? get() = MediaPrompts.byId(promptId)
    val maxMs: Int get() = MediaLimits.maxMs(kind)

    /** 0f..1f of the cap. Drives the progress bar and the live waveform's played portion. */
    val progress: Float
        get() = if (maxMs <= 0) 0f else min(1f, elapsedMs.toFloat() / maxMs.toFloat())
}

/** Which prompt list is docked, and what is picked in it. */
data class MediaSheet(
    val kind: MediaKind,
    val entryPoint: MediaEntryPoint,
    /**
     * Nothing is preselected when the sheet is opened from an empty card — not even the previewed
     * prompt. The card previews it; the list does not pick it. Opening from a filled card's
     * `Retake` DOES preselect the answered prompt, so keeping it is one tap.
     */
    val selectedId: String? = null,
    /** When it opened, for `time_on_sheet_s`. No default: [MediaViewModel.openPrompts] always
     *  knows, and a default of zero would be a legal clock reading standing in for "unset". */
    val openedAtMs: Long,
    /** How many rows the user has tapped before committing. `selections_before` on the commit. */
    val selectionsBefore: Int = 0,
)

/**
 * The whole screen.
 *
 * The four drawn states A-D are not a field: they are [video] and [voice] being present or absent,
 * which is the only representation that cannot disagree with itself.
 */
data class MediaState(
    val video: MediaArtefact? = null,
    val voice: MediaArtefact? = null,
    val previewVideoId: String? = null,
    val previewVoiceId: String? = null,
    val previewSource: MediaPreviewSource = MediaPreviewSource.Fallback,
    val access: MediaAccess = MediaAccess(),
    val sheet: MediaSheet? = null,
    val take: MediaTake? = null,
    /** What is playing, if anything. Null at rest -- playback here is never automatic. */
    val playback: MediaPlayback? = null,
    val loaded: Boolean = false,
) {
    /** How far into [kind]'s saved clip the user has listened, for the card's `0:08 / 0:14`. */
    fun playedMs(kind: MediaKind): Int =
        playback?.takeIf { it.source == PlaybackSource.Card && it.kind == kind }?.positionMs ?: 0

    /** Whether [kind]'s card is the thing currently playing. */
    fun isPlaying(kind: MediaKind): Boolean =
        playback?.let { it.source == PlaybackSource.Card && it.kind == kind } == true

    val hasVideo: Boolean get() = video != null
    val hasVoice: Boolean get() = voice != null

    fun artefact(kind: MediaKind): MediaArtefact? = when (kind) {
        MediaKind.Video -> video
        MediaKind.Voice -> voice
    }

    /** What an empty card previews. Resolved, never ranked — see [MediaPrompts.preview]. */
    fun preview(kind: MediaKind): MediaPrompt = MediaPrompts.preview(
        kind,
        when (kind) {
            MediaKind.Video -> previewVideoId
            MediaKind.Voice -> previewVoiceId
        },
    )

    fun withArtefact(artefact: MediaArtefact?, kind: MediaKind): MediaState = when (kind) {
        MediaKind.Video -> copy(video = artefact)
        MediaKind.Voice -> copy(voice = artefact)
    }
}

/**
 * The two waveforms, ported from the reference file's formulas rather than approximated.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THE EXACT ARITHMETIC MATTERS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The acceptance criterion is "48 deterministic bars whose heights do not change between renders".
 * A random waveform re-rolled on recomposition makes a static recording appear to wobble while the
 * user is looking at it, and Compose recomposes for reasons that have nothing to do with audio.
 *
 * Deterministic is the requirement; these particular curves are the design. Both are pure
 * functions of the bar index, so they are computed once and are identical on every platform.
 *
 * NEITHER IS REAL AUDIO. The filled card's waveform is decoration on a finished recording — the
 * reference calls it "purely decorative" — and the live one is the shape the design draws while
 * recording. Rendering a true amplitude envelope is a different, larger job and is not what the
 * ticket asks for; what it asks for is that the drawing is stable.
 */
object MediaWaveform {

    /** Both waveforms are 48 bars. The count is in the acceptance criteria. */
    const val BARS = 48

    /**
     * The finished recording's strip on a filled voice card.
     *
     * `min(1, |sin(i * 0.7) * 0.55 + sin(i * 0.31) * 0.4| + 0.1)`, from `RecordedVoiceCard`.
     */
    val recorded: List<Float> = List(BARS) { i ->
        val v = abs(sin(i * 0.7) * 0.55 + sin(i * 0.31) * 0.4) + 0.1
        min(1.0, v).toFloat()
    }

    /**
     * The live strip on the voice capture screen.
     *
     * Centre-anchored: heights peak in the middle and fade toward the edges, "so it reads as 'live
     * mic' rather than 'scrubbing'". From `VoiceRecordingView`.
     */
    val live: List<Float> = List(BARS) { i ->
        val center = (i - 24.0) / 24.0
        val envelope = max(0.1, 1.0 - center * center * 0.85)
        val v = (abs(sin(i * 0.93) + cos(i * 0.5) * 0.7) / 1.7) * envelope
        min(1.0, 0.15 + v * 0.85).toFloat()
    }
}

/**
 * `0:09` — a take's length, as every readout on this screen spells it.
 *
 * Seconds are TRUNCATED, not rounded, and that is deliberate: a 9.6-second take displayed as
 * `0:10` on a 10-second cap reads as though it hit the cap when it did not. The same reasoning as
 * storing milliseconds on the server.
 */
fun formatTakeLength(ms: Int): String {
    val total = max(0, ms) / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * `duration_s` for the tracking payloads.
 *
 * Seconds with one decimal, because the caps are 10 and 15 and whole seconds cannot tell a take
 * that ran into the cap from one that stopped just short of it.
 */
fun durationSeconds(ms: Int): Double = (max(0, ms) / 100) / 10.0
