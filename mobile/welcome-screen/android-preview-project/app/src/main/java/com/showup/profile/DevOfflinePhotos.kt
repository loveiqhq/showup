/*
 * DevOfflinePhotos.kt
 * ShowUp · walking the photo step with no backend at all (SHOWUP-156)
 *
 * The third of these, after `welcome/DevOfflineAuth.kt` and `profile/DevOfflineBasics.kt`. Read
 * the first for the argument; the three rules are identical and so are the reasons.
 *
 *   1. DEBUG ONLY -- injected as a constructor default computed from `BuildConfig.DEBUG`, so R8
 *      removes it from a release build entirely.
 *   2. ONLY WHEN NOTHING ANSWERED -- it sits in the transport-failure branch. A server that
 *      answers 400, 401, 429 or 500 is a server that is working, and its answer reaches the app
 *      untouched.
 *   3. IT SAYS SO -- an offline upload carries an `offline-` id and an `offline://` url, so
 *      nothing downstream can mistake it for something that is stored.
 *
 * IT NEVER FAILS ON ITS OWN. It used to fail every third upload, so `Upload failed` and `Retry`
 * could be walked without a backend. On a phone with no server to reach, that read as a real bug
 * (SHOWUP-156, 31 Oct 2026): the third photo failed every time and Retry fixed it, and nothing on
 * screen said the failure was staged. The failed slot is covered by `PhotosUploadStateTest`, the
 * previews and the evidence captures, which render it directly.
 */
package com.showup.profile

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/** The stand-in for `/me/photos`. */
object DevOfflinePhotos {

    private val counter = AtomicInteger(0)
    private val stored = mutableListOf<StoredPhoto>()

    /** How long a pretend upload takes. Long enough to see the ring fill, short enough to demo. */
    private const val UPLOAD_MILLIS = 1_400L

    /** How many steps the ring takes. The real one reports per 16 KiB chunk. */
    private const val STEPS = 20

    suspend fun upload(onProgress: (Float) -> Unit): UploadPhotoResult {
        val n = counter.incrementAndGet()
        repeat(STEPS) { step ->
            delay(UPLOAD_MILLIS / STEPS)
            onProgress((step + 1).toFloat() / STEPS)
        }
        val photo = StoredPhoto(
            id = "offline-$n",
            // Not a URL anything can fetch, and named so nobody mistakes it for one.
            url = "offline://photo/$n",
            position = stored.size,
        )
        stored += photo
        return UploadPhotoResult.Stored(photo)
    }

    /** What the stand-in is holding. Empty on a fresh launch -- nothing is persisted. */
    fun list(): List<StoredPhoto> = stored.toList()

    /**
     * Applies an order, refusing an incomplete list exactly as the server does.
     *
     * The refusal is the point. A stand-in that accepted anything would make the one path worth
     * walking here -- a drag whose write is rejected, and the re-read that follows -- unreachable
     * without a backend.
     */
    fun reorder(ids: List<String>): ReorderPhotosResult {
        val byId = stored.associateBy { it.id }
        if (ids.size != stored.size || ids.toSet().size != ids.size ||
            !ids.all { byId.containsKey(it) }
        ) {
            return ReorderPhotosResult.Failed(null)
        }
        val reordered = ids.mapIndexed { index, id -> byId.getValue(id).copy(position = index) }
        stored.clear()
        stored += reordered
        return ReorderPhotosResult.Stored(reordered)
    }

    fun remove(id: String): RemovePhotoResult {
        stored.removeAll { it.id == id }
        return RemovePhotoResult.Removed
    }

    /** Forgets everything. For tests, so one case cannot leak into the next. */
    fun reset() {
        counter.set(0)
        stored.clear()
    }
}
