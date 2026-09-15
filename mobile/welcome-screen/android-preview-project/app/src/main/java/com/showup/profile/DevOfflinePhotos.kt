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
 *   3. IT SAYS SO -- an offline upload is marked, and the screen's debug strip says the photos are
 *      not stored anywhere.
 *
 * WHY THIS ONE ALSO FAILS
 *
 * The other two stand-ins always succeed on the happy path, because the states they gate are
 * reachable by typing something wrong. This one is different: `Upload failed` and `Retry` cannot
 * be reached by doing anything wrong -- they need an upload to fail -- so a stand-in that always
 * succeeded would leave two of the seven states unwalkable without unplugging a router at exactly
 * the right moment.
 *
 * So EVERY THIRD upload fails. Deterministic rather than random, because a state you cannot
 * reproduce on demand is not one you can demonstrate, and a flaky stand-in is worse than none.
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

    /** Every third upload fails, so the failed slot and Retry are reachable. See the header. */
    private const val FAIL_EVERY = 3

    suspend fun upload(onProgress: (Float) -> Unit): UploadPhotoResult {
        val n = counter.incrementAndGet()
        repeat(STEPS) { step ->
            delay(UPLOAD_MILLIS / STEPS)
            onProgress((step + 1).toFloat() / STEPS)
        }
        if (n % FAIL_EVERY == 0) return UploadPhotoResult.Failed(null)
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
