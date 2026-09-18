/*
 * MediaPlayback.kt
 * ShowUp · the seam between "it played back" and how it played (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS AT ALL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket asks for two things that were drawn and never built. `Playback on review is on
 * demand, repeatable, and the CTA row does not move between plays` is an acceptance criterion, and
 * the filled voice card is specced with a `0:08 / 0:14` readout -- a played position out of a
 * total, which only means anything if the card plays.
 *
 * What shipped instead was a flag. `playPressed` set `isPlaying = true`, fired
 * `media_preview_played`, and played nothing; `playbackFinished` was called by nobody, so the flag
 * never came back down; the card's button reached a function that returned early because there was
 * no take on that screen; and `playedMs` was the literal `0`, so the readout every user saw was
 * `0:00 / 0:14` forever.
 *
 * The analytics half of that is the part worth naming. `media_preview_played` was firing for a
 * play that did not happen, into the same dataset the ticket says will decide whether 10 and 15
 * seconds are the right caps. A silent feature is a gap; a feature that reports itself working is
 * a lie in the data.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY IT IS AN INTERFACE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Same seam, same reason, and deliberately the same shape as [MediaCaptureSession]: ExoPlayer
 * needs a device and a looper, and everything ELSE here -- which source wins, what a second tap
 * does, when the event fires, when the readout resets -- is a product rule that belongs in
 * [MediaViewModel] where it can be tested with nothing attached.
 */
package com.showup.profile

/** Where a playback request came from, which is also what it is allowed to interrupt. */
enum class PlaybackSource {
    /** The 56px button on a filled video card, or the 44px pip on a filled voice card. */
    Card,

    /** The 88px glass button on video review, or the 64px sunset pip on voice review. */
    Review,
}

/**
 * What is playing, and how far in.
 *
 * Null when nothing is playing, which is the resting state of every screen in this flow -- the
 * ticket is explicit that playback is on demand and never automatic.
 */
data class MediaPlayback(
    val kind: MediaKind,
    val source: PlaybackSource,
    /** The playhead, in ms. What the card's `0:08` half of `0:08 / 0:14` reads. */
    val positionMs: Int = 0,
    /** The clip's length. The `0:14` half. */
    val durationMs: Int,
) {
    /** 0f..1f. Drives the played portion of the card's waveform. */
    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** One playing clip. Created per play, discarded when it ends. */
interface MediaPlayerSession {
    /**
     * Begins playing [path]. Returns false when there is nothing playable.
     *
     * A missing file, an unsupported container, a URL that will not open. False is a normal
     * outcome and not an error: an artefact that has been uploaded and whose local copy has been
     * cleaned up is exactly this case, and the honest response is to do nothing -- no playhead, no
     * tracking event -- rather than to show a position that never moves.
     *
     * IT DOES NOT REPORT A DURATION, deliberately. The player does not know one until it has read
     * the container, so asking here would either block the tap or return `TIME_UNSET`. The caller
     * already knows: every artefact and every take carries its own `durationMs`, measured when it
     * was recorded. That is the `0:14` in `0:08 / 0:14`.
     */
    suspend fun start(path: String): Boolean

    /** The playhead now, in ms. Polled by the view model's ticker rather than pushed. */
    fun positionMs(): Int

    /** True once the clip has reached its own end, as opposed to being stopped. */
    fun hasFinished(): Boolean

    /** Stops and releases. Safe to call twice. */
    suspend fun stop()
}

/** Makes a session. The real one holds a player; the fake holds a number. */
interface MediaPlayerFactory {
    fun create(kind: MediaKind): MediaPlayerSession
}

/**
 * A player with no player in it.
 *
 * Used by previews, by `DevOfflineMedia`, and by every unit test: it reports a duration and then
 * advances its playhead from the clock it is given, so the view model's ticker, the readout, the
 * finish transition and the tracking event are all exercised with no device and no file.
 *
 * [failFor] is how a test reaches the null branch above without needing a broken file.
 */
class FakeMediaPlayer(
    private val now: () -> Long = System::currentTimeMillis,
    private val durationMs: Int = 9_400,
    private val failFor: Set<String> = emptySet(),
) : MediaPlayerFactory {

    /** Every session this factory has made, newest last. Tests assert against it. */
    val sessions = mutableListOf<Session>()

    override fun create(kind: MediaKind): MediaPlayerSession = Session().also { sessions += it }

    inner class Session : MediaPlayerSession {
        private var startedAt: Long? = null
        private var stopped = false
        private var length = 0

        /** True between [start] and [stop]. */
        val running: Boolean get() = startedAt != null && !stopped

        override suspend fun start(path: String): Boolean {
            if (path in failFor) return false
            startedAt = now()
            length = durationMs
            return true
        }

        override fun positionMs(): Int {
            val began = startedAt ?: return 0
            return ((now() - began).toInt()).coerceIn(0, length)
        }

        override fun hasFinished(): Boolean = startedAt != null && positionMs() >= length

        override suspend fun stop() {
            stopped = true
            startedAt = null
        }
    }
}
