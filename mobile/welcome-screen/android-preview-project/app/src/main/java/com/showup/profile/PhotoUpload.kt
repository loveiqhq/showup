/*
 * PhotoUpload.kt
 * ShowUp · the rules a picked photo has to satisfy before it is sent (SHOWUP-156)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS, AND WHAT IT FIXES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The photo screen used to send the picked file untouched, with whatever MIME type the content
 * resolver reported. Two things came back from the server, and both looked identical on screen:
 *
 *   415  a modern Android phone hands out `image/heif` or `image/heic`, and the server's allowed
 *        types are jpeg, png and webp
 *   413  a full-resolution photo runs past the server's 8 MB cap
 *
 * Both rendered as `Upload failed` with a `Retry` that re-sent exactly the same bytes, so it could
 * never succeed. The slot's SINGLE failure state is the right design -- there is nothing a user can
 * do differently about a 500 than about a dropped connection -- but it is only honest when the
 * failure is actually transient, and neither of these was.
 *
 * The decode itself lives at the call site, where the platform APIs are; what lives here is the
 * arithmetic, because a rule that can be checked without a device is a rule that gets checked.
 */
package com.showup.profile

/**
 * The longest edge an uploaded photo may have.
 *
 * 2048 is generous for what a profile card ever draws -- the widest phone in the fit matrix is 440
 * dp, which is 1320 physical pixels at 3x -- and it keeps a re-encode well inside the server's
 * 8 MB limit without being visibly soft if the photo is ever shown larger.
 *
 * THE SAME NUMBER ON BOTH PLATFORMS. A photo that looked fine on one and soft on the other would
 * be a difference nobody chose.
 */
const val UPLOAD_MAX_EDGE = 2048

/** Visually lossless at the sizes a profile card uses, and roughly a third of the bytes of 100. */
const val UPLOAD_JPEG_QUALITY = 90

/**
 * The size to decode a picked photo at, or null when it is already small enough.
 *
 * NULL RATHER THAN THE ORIGINAL SIZE, so the caller can skip asking the decoder to resize at all.
 * Upscaling a small photo would add bytes and no detail, which is the opposite of the point.
 *
 * ASPECT RATIO IS PRESERVED and the short edge never rounds to zero: a panorama at 8000 x 200
 * scales to 2048 x 51, and a degenerate 8000 x 1 would otherwise scale to a zero-height bitmap
 * that the encoder refuses. Both are absurd as profile photos and neither should be a crash.
 */
fun uploadTargetSize(width: Int, height: Int, maxEdge: Int = UPLOAD_MAX_EDGE): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return null
    val ratio = maxEdge.toDouble() / longest
    return Pair(
        (width * ratio).toInt().coerceAtLeast(1),
        (height * ratio).toInt().coerceAtLeast(1),
    )
}
