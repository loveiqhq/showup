/*
 * ClipPlayback.kt
 * ShowUp · one profile clip at a time, for every screen that shows a Search profile
 *
 * Search's own profile and the match's profile opened read-only from the Date screen (SHOWUP-193)
 * play voice notes and videos the same way: tap to play, one at a time, a readout that moves, an end
 * that is noticed. It was Search's view model's own code; the Date screen needed the same thing with
 * none of Search's events, so the playing moved here and the REPORTING stayed with each owner -- this
 * class knows nothing about analytics.
 */
package com.showup.search

import com.showup.profile.MediaKind
import com.showup.profile.MediaPlayerFactory
import com.showup.profile.MediaPlayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ClipPlayback(
    private val scope: CoroutineScope,
    private val players: MediaPlayerFactory?,
    /** Applies a change to the playback the owner draws. */
    private val update: ((SearchPlayback?) -> SearchPlayback?) -> Unit,
    /** The clip ran out (or past its length and the grace): the owner stops it, as it stops any other. */
    private val ended: () -> Unit,
) {
    private var session: MediaPlayerSession? = null
    private var ticker: Job? = null
    /** Every stop moves it on, so a play still opening when it moves knows it was superseded. */
    private var generation = 0

    /** Opens [media] and, once it plays, draws it from 0 and keeps the readout moving. */
    fun start(kind: MediaKind, media: SearchMedia) {
        val factory = players ?: return
        val mine = generation
        scope.launch {
            val next = factory.create(kind)
            val started = next.start(media.url)
            // SUPERSEDED while the clip was opening -- a pass, a like, a sheet, the app leaving. Without
            // this, a voice note tapped just before `Not now` played over the next profile.
            if (mine != generation) {
                next.stop()
                return@launch
            }
            if (!started) return@launch
            session = next
            update { SearchPlayback(kind, 0, media.durationMs) }
            // BOUNDED by the clip's own length and a grace, as iOS's clock is: a player that never
            // reports an ending cannot keep it alive.
            ticker = launch {
                var waited = 0L
                while (isActive && waited < media.durationMs + PLAY_CLOCK_GRACE_MS) {
                    delay(TICK_MS)
                    waited += TICK_MS
                    val position = next.positionMs()
                    if (next.hasFinished()) {
                        update { it?.copy(positionMs = it.durationMs) }
                        ended()
                        return@launch
                    }
                    update { it?.copy(positionMs = position) }
                }
                if (isActive) ended()
            }
        }
    }

    /** Stops the player. Clearing (and reporting) what was drawn is the owner's. */
    fun stop() {
        generation++
        ticker?.cancel()
        ticker = null
        val ending = session
        session = null
        if (ending != null) scope.launch { ending.stop() }
    }

    private companion object {
        /** How often a playing card's readout moves. */
        const val TICK_MS = 100L
        /** How long the clock keeps polling past a clip's stated length: a backstop, not a timing rule. */
        const val PLAY_CLOCK_GRACE_MS = 1_000L
    }
}
