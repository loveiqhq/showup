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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink

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

/**
 * A request body that reports how much of itself has been written.
 *
 * OkHttp writes a body by handing it a sink and asking it to fill it, and it never asks how far
 * along that got. Counting here is the only place the number exists.
 *
 * WRITTEN IN CHUNKS RATHER THAN IN ONE CALL, which is the whole point: `sink.write(bytes)` would
 * report 0 and then 1 with nothing in between, so the ring would jump rather than fill. 16 KiB is
 * OkHttp's own segment size, so the chunking costs nothing beyond the loop.
 *
 * `contentLength` is exact, which matters twice: the server gets a `Content-Length` rather than a
 * chunked upload, and the fraction below has a real denominator.
 */
private class ProgressBody(
    private val bytes: ByteArray,
    private val mimeType: String,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType() = mimeType.toMediaType()

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        if (total == 0) {
            onProgress(1f)
            return
        }
        var written = 0
        while (written < total) {
            val chunk = minOf(CHUNK, total - written)
            sink.write(bytes, written, chunk)
            written += chunk
            onProgress(written.toFloat() / total)
        }
    }

    private companion object {
        /** OkHttp's segment size. Smaller would mean more callbacks for no more information. */
        const val CHUNK = 16 * 1024
    }
}
