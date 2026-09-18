/*
 * AndroidMediaPlayer.kt
 * ShowUp · the real player (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE PLAYER, NOT ONE PER CLIP
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * ExoPlayer holds a codec, an audio track and a renderer thread. Two of them alive at once on a
 * screen with a video card and a voice card is two decoders competing for the same hardware, and
 * on the low end of `minSdk = 30` that is where playback starts failing rather than stuttering.
 * There is also never a reason for two: the ticket's rule that playback is on demand means exactly
 * one thing is ever playing, and starting a second play stops the first.
 *
 * So this factory owns one instance, hands every session the same one, and releases it with the
 * screen. `media3-exoplayer` was already a declared dependency when this was written -- added in
 * anticipation and referenced by nothing, so release builds shipped a player library that no code
 * called. This is the code that was missing.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * NOTHING HERE DECIDES ANYTHING
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Same rule as the recorders next door. No product decision lives in this file: not which source
 * wins, not what a second tap does, not when the tracking event fires. It turns a path into sound
 * and pictures, and reports where the playhead is.
 */
package com.showup.profile

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real player, behind [MediaPlayerFactory].
 *
 * [surface] is how the video card and the video review screen show FRAMES rather than playing a
 * clip's audio underneath a still thumbnail -- which is what a player with nowhere to draw does,
 * and is worse than not playing at all. The screen attaches a `PlayerView` to it, exactly as the
 * viewfinder attaches a `PreviewView` to the camera controller.
 */
class AndroidMediaPlayer(private val context: Context) : MediaPlayerFactory {

    /**
     * The one instance, created on first use.
     *
     * Main thread only, which every caller is: composition and the view model's ticker both run
     * there. Null before anything has played and after [release].
     */
    var surface: ExoPlayer? = null
        private set

    /**
     * Set by the listener when a clip reaches its own end.
     *
     * On the factory rather than the session because there is one player: a session that has been
     * superseded must not be able to report the new clip's ending as its own.
     */
    private var endedAt: Long = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) endedAt = System.nanoTime()
        }
    }

    override fun create(kind: MediaKind): MediaPlayerSession = Session()

    /** Releases the player. The host calls this when the media screens leave composition. */
    fun release() {
        surface?.removeListener(listener)
        surface?.release()
        surface = null
    }

    private inner class Session : MediaPlayerSession {
        /**
         * When this session started, so it can tell its own ending from a later clip's.
         *
         * Without it, a session stopped and replaced would still see `endedAt` from the clip that
         * replaced it and report `hasFinished` -- which the view model reads as "this play ran to
         * the end", resetting a playhead that belongs to something else.
         */
        private var startedAt: Long = 0L

        override suspend fun start(path: String): Boolean = withContext(Dispatchers.Main) {
            val player = surface
                ?: ExoPlayer.Builder(context).build().also {
                    it.addListener(listener)
                    surface = it
                }
            startedAt = System.nanoTime()
            runCatching {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(path)))
                player.prepare()
                player.playWhenReady = true
                true
            }.getOrDefault(false)
        }

        override fun positionMs(): Int = surface?.currentPosition?.toInt() ?: 0

        override fun hasFinished(): Boolean = endedAt > startedAt

        override suspend fun stop() {
            withContext(Dispatchers.Main) {
                surface?.run {
                    playWhenReady = false
                    stop()
                    clearMediaItems()
                }
            }
        }
    }
}
