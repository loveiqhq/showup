/*
 * DevOfflineMedia.kt
 * ShowUp · walking the media step with no backend at all (SHOWUP-161)
 *
 * The fourth of these, after `welcome/DevOfflineAuth.kt`, `profile/DevOfflineBasics.kt` and
 * `profile/DevOfflinePhotos.kt`. Read the first for the argument; the three rules are identical
 * and so are the reasons.
 *
 *   1. DEBUG ONLY -- injected as a constructor default computed from `BuildConfig.DEBUG`, so R8
 *      removes it from a release build entirely.
 *   2. ONLY WHEN NOTHING ANSWERED -- it sits in the transport-failure branch. A server that
 *      answers 400, 401, 429 or 500 is a server that is working, and its answer reaches the app
 *      untouched.
 *   3. IT SAYS SO -- an offline recording carries an `offline-` id, so nothing downstream can
 *      mistake it for something that is stored.
 *
 * IT NEVER FAILS ON ITS OWN, like the photo stand-in -- see `DevOfflinePhotos.kt` for why it
 * used to fail every third upload and why that was taken out (SHOWUP-156, 31 Oct 2026). The
 * card's failed state is covered by the unit tests and previews, which render it directly.
 *
 * THE PREVIEW ALWAYS READS `fallback` HERE, and that is honest rather than lazy: there is no
 * ranking job in this process, so there is nothing that could have ranked. Serving `ranked` would
 * make the one field that exists to tell those apart lie in the one build where it is easiest to
 * look at.
 */
package com.showup.profile

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** The stand-in for `/me/media`. */
object DevOfflineMedia {

    private val counter = AtomicInteger(0)
    private val stored = mutableMapOf<MediaKind, StoredMedia>()

    /** Long enough to watch the card's progress fill, short enough to demo. */
    private const val UPLOAD_MILLIS = 1_600L

    /** How many steps the progress takes. The real one reports per 16 KiB chunk. */
    private const val STEPS = 20

    fun load(): MediaSnapshot = MediaSnapshot(
        items = stored.values.toList(),
        // Nothing published, because nothing here could have published it.
        previewVideoId = null,
        previewVoiceId = null,
        previewSource = MediaPreviewSource.Fallback,
    )

    suspend fun upload(
        kind: MediaKind,
        promptId: String,
        durationMs: Int,
        onProgress: (Float) -> Unit,
    ): UploadMediaResult {
        val n = counter.incrementAndGet()
        repeat(STEPS) { step ->
            delay(UPLOAD_MILLIS / STEPS)
            onProgress((step + 1).toFloat() / STEPS)
        }
        val media = StoredMedia(
            id = "offline-$n",
            kind = kind,
            // No bytes were sent anywhere, so there is no URL that could serve them. The card
            // plays the LOCAL file while an upload is in flight and keeps doing so here, which is
            // the same path a real optimistic fill takes before the server answers.
            url = "",
            promptId = promptId,
            durationMs = durationMs,
        )
        // Replace, exactly as the server's unique index does -- one artefact per kind.
        stored[kind] = media
        return UploadMediaResult.Stored(media)
    }

    fun remove(id: String): RemoveMediaResult {
        stored.entries.removeAll { it.value.id == id }
        return RemoveMediaResult.Removed
    }
}
