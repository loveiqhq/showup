/*
 * PhotosReorderTest.kt
 * ShowUp · what `PATCH /me/photos/order` sends and what its answers mean (SHOWUP-156)
 *
 * The order the user drags the grid into is the order everyone else sees their photos in, and the
 * main photo is whichever one ends up first. Until this route existed the drag was local: it
 * survived until the screen was left and no further.
 *
 * Every assertion here is made against bytes that crossed a socket, served in-process. No backend,
 * no network, no emulator.
 */
package com.showup.profile

import com.showup.api.InMemoryTokenStore
import com.showup.api.ShowUpApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotosReorderTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: PhotosRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        // `offline = null`, so this suite measures what a RELEASE build does. The debug stand-in
        // has its own case at the bottom; mixing the two would mean neither was checked.
        repo = PhotosRepository(
            ShowUpApi(
                baseUrl = server.url("/").toString(),
                tokens = InMemoryTokenStore(access = "t"),
            ),
            offline = null,
        )
    }

    @After
    fun stop() = server.shutdown()

    private fun respond(code: Int, body: String = "") = server.enqueue(
        MockResponse().setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body),
    )

    private fun photo(id: String, position: Int) =
        """{"id":"$id","url":"https://cdn/$id.jpg","position":$position,""" +
            """"moderationStatus":"pending","createdAt":"2026-09-16T10:20:30Z"}"""

    // ── the request ───────────────────────────────────────────────────────────

    @Test
    fun `the whole order is sent to the contract path`() = runTest {
        respond(200, "[${photo("b", 0)},${photo("a", 1)}]")
        repo.reorder(listOf("b", "a"))

        val sent = server.takeRequest()
        assertEquals("PATCH", sent.method)
        assertEquals("/me/photos/order", sent.path)
        // Every id, in the order the grid is now in -- not the pair of indices that moved. A diff
        // against an order the server has already changed produces an order nobody chose.
        assertEquals("""{"ids":["b","a"]}""", sent.body.readUtf8())
    }

    // ── the answers ───────────────────────────────────────────────────────────

    @Test
    fun `a stored order comes back renumbered from zero`() = runTest {
        respond(200, "[${photo("c", 0)},${photo("a", 1)},${photo("b", 2)}]")
        val result = repo.reorder(listOf("c", "a", "b"))

        assertTrue("expected Stored, got $result", result is ReorderPhotosResult.Stored)
        result as ReorderPhotosResult.Stored
        assertEquals(listOf("c", "a", "b"), result.photos.map { it.id })
        assertEquals(listOf(0, 1, 2), result.photos.map { it.position })
    }

    @Test
    fun `the answer is sorted by position, not trusted to arrive sorted`() = runTest {
        // The server orders its reply; nothing in HTTP guarantees it, and a grid drawn in the
        // order bytes happened to arrive would be a drag that landed somewhere else.
        respond(200, "[${photo("a", 2)},${photo("b", 0)},${photo("c", 1)}]")
        val result = repo.reorder(listOf("b", "c", "a")) as ReorderPhotosResult.Stored
        assertEquals(listOf("b", "c", "a"), result.photos.map { it.id })
    }

    @Test
    fun `a refused order carries the server's error`() = runTest {
        respond(
            400,
            """{"statusCode":400,"message":"The order must list this account's photos exactly once each.","error":"Bad Request"}""",
        )
        val result = repo.reorder(listOf("a", "b"))

        assertTrue("expected Failed, got $result", result is ReorderPhotosResult.Failed)
        result as ReorderPhotosResult.Failed
        // Carried for the log, not for the copy: the screen's recovery is the same whatever the
        // server said, and it is to re-read rather than to explain.
        assertEquals(400, result.error?.statusCode)
    }

    @Test
    fun `nothing answering is a failure with nothing to log`() = runTest {
        server.shutdown()
        val result = repo.reorder(listOf("a"))
        assertTrue("expected Failed, got $result", result is ReorderPhotosResult.Failed)
        assertNull((result as ReorderPhotosResult.Failed).error)
    }

    // ── the debug stand-in ────────────────────────────────────────────────────

    @Test
    fun `the offline stand-in refuses a list that is not everything it holds`() = runTest {
        DevOfflinePhotos.reset()
        val first = DevOfflinePhotos.upload {} as UploadPhotoResult.Stored
        val second = DevOfflinePhotos.upload {} as UploadPhotoResult.Stored

        // It refuses exactly what the server refuses. A stand-in that accepted anything would make
        // the refusal path -- and the re-read that follows it -- unwalkable without a backend.
        val partial = DevOfflinePhotos.reorder(listOf(first.photo.id))
        assertTrue(partial is ReorderPhotosResult.Failed)

        val stored = DevOfflinePhotos.reorder(listOf(second.photo.id, first.photo.id))
        assertTrue("expected Stored, got $stored", stored is ReorderPhotosResult.Stored)
        assertEquals(
            listOf(second.photo.id, first.photo.id),
            DevOfflinePhotos.list().map { it.id },
        )
        assertEquals(listOf(0, 1), DevOfflinePhotos.list().map { it.position })
        DevOfflinePhotos.reset()
    }
}
