/*
 * DevOfflineUploadsTest.kt
 * ShowUp · the debug upload stand-ins never fail on their own (SHOWUP-156 / SHOWUP-161)
 *
 * Both stand-ins used to fail every third upload on purpose, so `Upload failed` and `Retry` could
 * be walked without a backend. Device testing read that as a real bug: on a phone with no server
 * to reach, the third photo failed every time and Retry fixed it. Nothing on screen said the
 * failure was staged. The failed states are covered by unit tests, previews and evidence
 * captures, so the stand-in now does what its name says and stands in for a working server.
 *
 * THREE IN A ROW, from wherever the counter is: any three consecutive uploads include one whose
 * number is a multiple of three, so this fails against the old stand-ins whatever ran before it.
 */
package com.showup.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOfflineUploadsTest {

    @Test
    fun `the photo stand-in stores every upload, the third included`() = runTest {
        DevOfflinePhotos.reset()
        repeat(6) { n ->
            val result = DevOfflinePhotos.upload {}
            assertTrue("upload ${n + 1} was $result", result is UploadPhotoResult.Stored)
        }
        DevOfflinePhotos.reset()
    }

    @Test
    fun `the media stand-in stores every upload, the third included`() = runTest {
        repeat(3) { n ->
            val result = DevOfflineMedia.upload(
                kind = MediaKind.Video,
                promptId = "relaxed_and_happy",
                durationMs = 9_400,
                onProgress = {},
            )
            assertTrue("upload ${n + 1} was $result", result is UploadMediaResult.Stored)
        }
    }
}
