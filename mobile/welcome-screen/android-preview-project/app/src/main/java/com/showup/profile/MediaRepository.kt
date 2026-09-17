/*
 * MediaRepository.kt
 * ShowUp · what the media step asks the backend, and what the answers mean (SHOWUP-161)
 *
 * Every call goes through the GENERATED client. Nothing here describes a URL or a request body.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE READ RETURNS THE WHOLE SCREEN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `GET /me/media` answers with both artefacts AND both previewed prompt ids AND `preview_source`,
 * in one response. That is not convenience. `media_screen_viewed` fires on every mount carrying
 * all five of those facts together, and served from two endpoints they can disagree — a view that
 * names a preview it had not yet loaded is precisely the attribution hole the tracking spec calls
 * out. One call, one snapshot.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE CLIENT NEVER RANKS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Decision 33. This file carries the two prompt ids the server sent and resolves nothing. The
 * fallback to §20 lives in [MediaPrompts.preview] and is a RESOLUTION, not a ranking: it decides
 * what to draw when the server said nothing, and it has no access to completion counts, no
 * ordering, and no memory between calls.
 *
 * `open`, like [PhotosRepository], so a test can subclass it and answer from a queue.
 */
package com.showup.profile

import com.showup.BuildConfig
import com.showup.api.ApiError
import com.showup.api.ShowUpApi
import okhttp3.MultipartBody

/** One recording as the server holds it. */
data class StoredMedia(
    val id: String,
    val kind: MediaKind,
    val url: String,
    val promptId: String,
    val durationMs: Int,
)

/**
 * Everything the screen reads in one go.
 *
 * The previews are ids as the server spelled them, NOT resolved prompts: resolving is the screen's
 * job and it is a pure function, so keeping the raw value here means the tracking payload reports
 * what the server actually said rather than what the client decided to draw.
 */
data class MediaSnapshot(
    val items: List<StoredMedia>,
    val previewVideoId: String?,
    val previewVoiceId: String?,
    val previewSource: MediaPreviewSource,
)

/** The answer to "store this take". */
sealed interface UploadMediaResult {
    data class Stored(val media: StoredMedia) : UploadMediaResult

    /**
     * The upload did not land.
     *
     * One case, not several, and for the same reason as the photo upload: the card says the upload
     * failed and offers a retry whatever went wrong, because there is nothing the user can do
     * differently for a 500 than for a dropped connection. [error] is carried for the log.
     */
    data class Failed(val error: ApiError?) : UploadMediaResult
}

/** The answer to "delete this recording". */
sealed interface RemoveMediaResult {
    data object Removed : RemoveMediaResult
    data class Failed(val error: ApiError?) : RemoveMediaResult
}

open class MediaRepository(
    private val api: ShowUpApi,
    private val offline: DevOfflineMedia? = if (BuildConfig.DEBUG) DevOfflineMedia else null,
) {

    /**
     * What the account already holds, and what the empty cards should preview.
     *
     * Returns null when nothing answered and there is no stand-in, which the caller reads as "do
     * not touch what is on screen". An empty response and an unreachable server are different
     * facts, and collapsing them would clear a card the user had just filled.
     */
    open suspend fun load(): MediaSnapshot? = runCatching {
        val response = api.profiles.getMedia()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            MediaSnapshot(
                items = body.items.mapNotNull { dto ->
                    // An unknown kind is dropped rather than guessed. The enum is closed in the
                    // contract, so this can only fire if the server grows a third medium before
                    // this client knows about it -- and drawing it as a video would be worse.
                    MediaKind.fromTrackingValue(dto.kind.value)?.let { kind ->
                        StoredMedia(
                            id = dto.id,
                            kind = kind,
                            url = dto.url,
                            promptId = dto.mediaPromptId,
                            durationMs = dto.durationMs,
                        )
                    }
                },
                previewVideoId = body.previewVideo.value,
                previewVoiceId = body.previewVoice.value,
                previewSource = MediaPreviewSource.fromServer(body.previewSource.value),
            )
        } else {
            null
        }
    }.getOrElse { offline?.load() }

    /**
     * Stores one take, replacing whatever was in that slot.
     *
     * @param onProgress called on the upload thread with 0..1 as the bytes go out. A ten-second
     *   video is by a wide margin the largest thing this app uploads, so the card shows a real
     *   fraction rather than a spinner -- the same argument, and the same [ProgressBody], as the
     *   photo grid.
     */
    open suspend fun upload(
        kind: MediaKind,
        promptId: String,
        durationMs: Int,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        onProgress: (Float) -> Unit = {},
    ): UploadMediaResult = runCatching {
        val body = ProgressBody(bytes, mimeType, onProgress)
        // `file` because that is what the server's FileInterceptor listens for, and it is in the
        // contract rather than only in the controller.
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val response = api.profiles.uploadMedia(
            durationMs = durationMs,
            file = part,
            kind = kind.trackingValue,
            mediaPromptId = promptId,
        )
        val stored = response.body()
        if (response.isSuccessful && stored != null) {
            UploadMediaResult.Stored(
                StoredMedia(
                    id = stored.id,
                    kind = kind,
                    url = stored.url,
                    promptId = stored.mediaPromptId,
                    durationMs = stored.durationMs,
                ),
            )
        } else {
            UploadMediaResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse {
        // Nothing answered. A server that replies -- with anything, including 500 -- never reaches
        // here, so this cannot hide a backend bug.
        offline?.upload(kind, promptId, durationMs, onProgress)
            ?: UploadMediaResult.Failed(null)
    }

    /** Deletes one. 204 on success. */
    open suspend fun remove(id: String): RemoveMediaResult = runCatching {
        val response = api.profiles.deleteMedia(id)
        if (response.isSuccessful) {
            RemoveMediaResult.Removed
        } else {
            RemoveMediaResult.Failed(errorOf(response.code(), response.errorBody()?.string()))
        }
    }.getOrElse { offline?.remove(id) ?: RemoveMediaResult.Failed(null) }

    private fun errorOf(code: Int, body: String?): ApiError? =
        body?.let { ApiError.parse(code, it) }
}
