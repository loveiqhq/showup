/*
 * ProgressBody.kt
 * ShowUp · a request body that reports how much of itself has been written
 *
 * EXTRACTED FROM PhotosRepository ON 17 SEPTEMBER 2026, when the media step became its second
 * user. It sat as a private class in a repository file, and the rule that applies is the one the
 * primary button taught this project: "a shared primitive never lives in a screen file... anything
 * two flows use gets its own file". A copy in MediaRepository would have been the third `PillButton`.
 */
package com.showup.profile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

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
internal class ProgressBody(
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

    internal companion object {
        /** OkHttp's segment size. Smaller would mean more callbacks for no more information. */
        const val CHUNK = 16 * 1024
    }
}
