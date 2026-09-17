/*
 * PhotoUploadTest.kt
 * ShowUp · the size a picked photo is decoded at (SHOWUP-156)
 *
 * The decode itself needs a device. The arithmetic does not, and the arithmetic is where the bug
 * was: a photo sent at full resolution came back 413, and one sent as HEIC came back 415, and both
 * rendered as `Upload failed` with a Retry that re-sent the same bytes and failed the same way.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoUploadTest {

    @Test
    fun `a photo already within the cap is left alone`() {
        // Null rather than the original size, so the caller does not ask the decoder to resize to
        // exactly what it was about to produce anyway.
        assertNull(uploadTargetSize(1600, 1200))
        assertNull(uploadTargetSize(UPLOAD_MAX_EDGE, UPLOAD_MAX_EDGE))
        assertNull(uploadTargetSize(100, 100))
    }

    @Test
    fun `a photo over the cap is scaled so its longest edge is the cap`() {
        // A 12 megapixel phone photo, which is what actually broke this.
        val (width, height) = uploadTargetSize(4032, 3024)!!
        assertEquals(UPLOAD_MAX_EDGE, width)
        assertEquals(1536, height)
    }

    @Test
    fun `portrait scales on its height, not its width`() {
        val (width, height) = uploadTargetSize(3024, 4032)!!
        assertEquals(UPLOAD_MAX_EDGE, height)
        assertEquals(1536, width)
        // The bug this guards against is scaling on `width` because it is written first: a
        // portrait photo would come out 2048 wide and 2731 tall, which is BIGGER than it started.
    }

    @Test
    fun `the aspect ratio survives`() {
        val (width, height) = uploadTargetSize(4000, 2000)!!
        assertEquals(2.0, width.toDouble() / height, 0.01)
    }

    @Test
    fun `an absurdly wide panorama keeps a short edge of at least one pixel`() {
        // 8000 x 1 scales to 2048 x 0.00025, and a zero-height bitmap is one the encoder refuses.
        // Absurd as a profile photo, and still not a crash.
        val (width, height) = uploadTargetSize(8000, 1)!!
        assertEquals(UPLOAD_MAX_EDGE, width)
        assertEquals(1, height)
    }

    @Test
    fun `a photo with no pixels is nothing to resize`() {
        // What a decoder reports for a file it could not read the header of.
        assertNull(uploadTargetSize(0, 0))
        assertNull(uploadTargetSize(-1, 100))
    }

    @Test
    fun `the cap is the same number the iOS side uses`() {
        // Stated here so a change to one platform fails a test rather than quietly making the two
        // apps produce photos of different quality. `verify-profile.py` checks the pair.
        assertEquals(2048, UPLOAD_MAX_EDGE)
        assertEquals(90, UPLOAD_JPEG_QUALITY)
    }

    @Test
    fun `the result always fits inside the cap on both edges`() {
        // A property rather than an example: whatever goes in, neither edge comes out over the cap.
        listOf(
            4032 to 3024, 3024 to 4032, 8000 to 6000, 2049 to 2049, 5000 to 100, 100 to 5000,
        ).forEach { (w, h) ->
            val (outW, outH) = uploadTargetSize(w, h)!!
            assert(outW <= UPLOAD_MAX_EDGE) { "$w x $h produced a width of $outW" }
            assert(outH <= UPLOAD_MAX_EDGE) { "$w x $h produced a height of $outH" }
        }
    }
}
