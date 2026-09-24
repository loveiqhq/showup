/*
 * PhotosRepository.kt
 * ShowUp · what the photo step asks the backend, and what the answers mean (SHOWUP-156)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE CONTRACT GAP THIS FILE WAS BLOCKED ON, AND HOW IT WAS FIXED
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `POST /me/photos` was emitted with NO REQUEST BODY. The controller carried
 * `@ApiConsumes('multipart/form-data')`, which declares the content type and nothing else, so the
 * operation had no `requestBody` and the generated client exposed `uploadPhoto()` taking no
 * arguments -- an upload endpoint that could not carry a photo, on both platforms.
 *
 * Found on 15 September 2026 while wiring this screen. Fixed at the source, with `@ApiBody` on
 * `photos.controller.ts`, rather than by hand-writing a multipart call here: a hand-written
 * request would be a second contract, and the rule against that is the reason this file has no
 * URLs in it.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THE PROGRESS IS REAL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The slot draws a DETERMINATE ring, and the ticket contrasts it with a spinner on purpose: a
 * spinner says something is happening, a ring says how much is left. A photo is the largest thing
 * this app uploads and the first place a slow connection is visible, so a ring that is not
 * measuring anything would be a lie told at exactly the moment the user is deciding whether to
 * wait.
 *
 * OkHttp reports nothing about a request body's progress, so [ProgressBody] counts the bytes as
 * they are written. That is the only honest way to get the number, and it is thirty lines.
 *
 * `open`, like `PhoneAuthRepository`, so a test can subclass it and answer from a queue. The
 * alternative -- an interface plus a real implementation plus a fake -- is three types where one
 * would do, for a class whose whole job is to map HTTP onto four result types.
 */
package com.showup.profile

import com.showup.BuildConfig
import com.showup.api.ApiError
import com.showup.api.ShowUpApi
import com.showup.api.generated.model.ReorderPhotosDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody

/** One photo as the server holds it. */
data class StoredPhoto(
    val id: String,
    val url: String,
    val position: Int,
)

/** The answer to "store this image". */
sealed interface UploadPhotoResult {
    data class Stored(val photo: StoredPhoto) : UploadPhotoResult

    /**
     * The upload did not land.
     *
     * One case, not several, and deliberately so: the slot's failed state says `Upload failed` and
     * offers `Retry` whatever went wrong, because there is nothing the user can do differently for
     * a 500 than for a dropped connection. [error] is carried for the log, not for the copy.
     */
    data class Failed(val error: ApiError?) : UploadPhotoResult
}

/**
 * The answer to "store this order".
 *
 * ONE FAILED CASE, like the upload, and for a sharper reason. The server refuses a list that is
 * not this account’s photos exactly once each, and every way of getting that wrong -- one
 * missing, one duplicated, one belonging to somebody else -- has the same cause (this client’s
 * idea of what is stored is out of date) and the same recovery (re-read and adopt what comes
 * back). Splitting them would give the caller three branches that do one thing.
 */
sealed interface ReorderPhotosResult {
    data class Stored(val photos: List<StoredPhoto>) : ReorderPhotosResult
    data class Failed(val error: ApiError?) : ReorderPhotosResult
}

/** The answer to "delete this photo". */
sealed interface RemovePhotoResult {
    data object Removed : RemovePhotoResult
    data class Failed(val error: ApiError?) : RemovePhotoResult
}

/**
 * Reads and writes the user's photos.
 *
 * Takes a [ShowUpApi] rather than building one, so a test can hand it a client pointed at a
 * MockWebServer -- which is how every mapping below is verified without a backend.
 */
open class PhotosRepository(
    private val api: ShowUpApi,
    /**
     * The stand-in used when NOTHING ANSWERED, or null to let that failure be a failure.
     *
     * Injected rather than an inlined `BuildConfig.DEBUG` check, for the reason the phone flow
     * learned the hard way: unit tests build DEBUG, so a guard inside the catch block would mean
     * the release behaviour of this path could never be asserted at all.
     */
    private val offline: DevOfflinePhotos? = if (BuildConfig.DEBUG) DevOfflinePhotos else null,
) {

    /**
     * Stores one image.
     *
     * @param onProgress called on the upload thread with 0..1 as the bytes go out. Not on the main
     *   thread: the caller hoists it onto the UI thread, because this is I/O and the callback fires
     *   as often as the socket accepts a chunk.
     */
    open suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        onProgress: (Float) -> Unit = {},
    ): UploadPhotoResult = runCatching {
        val body = ProgressBody(bytes, mimeType, onProgress)
        // The field name is `file` because that is what the server's FileInterceptor listens for,
        // and it is in the contract now rather than only in the controller.
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val response = api.profiles.uploadPhoto(part)
        val stored = response.body()
        if (response.isSuccessful && stored != null) {
            UploadPhotoResult.Stored(
                StoredPhoto(id = stored.id, url = stored.url, position = stored.position),
            )
        } else {
            UploadPhotoResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse {
        // Nothing answered. A server that replies -- with anything, including 500 -- never
        // reaches here, so this cannot hide a backend bug.
        offline?.upload(onProgress) ?: UploadPhotoResult.Failed(null)
    }

    /**
     * What the account already holds.
     *
     * Returns null when nothing answered and there is no stand-in, which the caller reads as "do
     * not touch what is on screen". An empty list and an unreachable server are different facts,
     * and collapsing them would wipe a grid the user had just filled.
     */
    open suspend fun list(): List<StoredPhoto>? = runCatching {
        val response = api.profiles.listPhotos()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body.map { StoredPhoto(id = it.id, url = it.url, position = it.position) }
        } else {
            null
        }
    }.getOrElse { offline?.list() }

    /**
     * Stores the order the user dragged the grid into.
     *
     * SENDS THE WHOLE ORDER, not the pair of indices that moved. A from/to pair is a diff against
     * an order the server has to already agree with, and two drags in quick succession on a slow
     * connection arrive as two diffs applied to a list that moved in between -- which produces an
     * order the user never made. A whole list is also idempotent, so a retry after a dropped
     * connection is safe.
     *
     * STORED PHOTOS ONLY. An in-flight photo has no server id yet and a failed one never had one,
     * so neither can appear in a list the server will accept -- and the complete set is exactly
     * what it requires. The caller is the one that knows when the grid is all stored; this states
     * what it has to hand over.
     */
    open suspend fun reorder(remoteIds: List<String>): ReorderPhotosResult = runCatching {
        val response = api.profiles.reorderPhotos(ReorderPhotosDto(ids = remoteIds))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ReorderPhotosResult.Stored(
                body.sortedBy { it.position }
                    .map { StoredPhoto(id = it.id, url = it.url, position = it.position) },
            )
        } else {
            ReorderPhotosResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { offline?.reorder(remoteIds) ?: ReorderPhotosResult.Failed(null) }

    /** Deletes one. 204 on success. */
    open suspend fun remove(id: String): RemovePhotoResult = runCatching {
        val response = api.profiles.deletePhoto(id)
        if (response.isSuccessful) {
            RemovePhotoResult.Removed
        } else {
            RemovePhotoResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { offline?.remove(id) ?: RemovePhotoResult.Failed(null) }

    private fun errorOf(code: Int, body: String?): ApiError? =
        body?.let { ApiError.parse(code, it) }
}
