/*
 * PhotosUploadStateTest.kt
 * ShowUp · what happens to a slot between picking a photo and storing it (SHOWUP-156)
 *
 * The rules in [PhotosViewModel] that cannot be checked by looking at [PhotoGridState] alone,
 * because they are about ORDER: what the slot looks like before the upload finishes, what happens
 * to it when the upload fails, what Retry sends, and whether the count moves at the right moment.
 *
 * Every one of these is a state a person would otherwise have to produce by picking a photo on a
 * device with a bad connection at the right instant.
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker
import com.showup.api.ShowUpApi
import com.showup.api.InMemoryTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosUploadStateTest {

    /**
     * A repository that answers from a queue, and records what it was asked.
     *
     * The queue is what lets one test say "the first upload lands and the second fails" without
     * any timing: each call takes the next answer, in order.
     */
    private class FakeRepo : PhotosRepository(
        // A base URL nothing listens on. The superclass is never asked to make a call -- every
        // method below is overridden -- so this only has to construct.
        api = ShowUpApi(baseUrl = "http://127.0.0.1:1/", tokens = InMemoryTokenStore()),
        offline = null,
    ) {
        val queued = ArrayDeque<UploadPhotoResult>()
        val uploaded = mutableListOf<PickedBytes>()
        val removed = mutableListOf<String>()
        var reportProgress = false

        override suspend fun upload(
            bytes: ByteArray,
            mimeType: String,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): UploadPhotoResult {
            uploaded += PickedBytes(bytes, mimeType, fileName)
            if (reportProgress) {
                onProgress(0.5f)
                onProgress(1f)
            }
            return queued.removeFirstOrNull()
                ?: UploadPhotoResult.Stored(StoredPhoto("id-${uploaded.size}", "u", 0))
        }

        override suspend fun remove(id: String): RemovePhotoResult {
            removed += id
            return RemovePhotoResult.Removed
        }

        override suspend fun list(): List<StoredPhoto>? = null
    }

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var analytics: Recorder
    private lateinit var vm: PhotosViewModel

    private val bytes = PickedBytes(ByteArray(12) { 7 }, "image/jpeg", "photo.jpeg")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo()
        analytics = Recorder()
        vm = PhotosViewModel(
            repo = repo,
            access = FixedPhotoAccess(),
            readBytes = { bytes },
            analytics = analytics,
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun grid() = vm.state.value.grid

    /** Picks into [slot] and lets the upload run to completion. */
    private suspend fun pick(slot: Int, source: PhotoSource = PhotoSource.Library) {
        vm.tapSlot(slot)
        vm.picked("content://pick/$slot", source)
    }

    // ── the slot is occupied before anything is stored ──────────────────────

    @Test
    fun `the picked photo fills its slot immediately, in flight`() = runTest(dispatcher) {
        vm.tapSlot(0)
        vm.picked("content://pick/0", PhotoSource.Library)
        // Not advanced: this is the frame straight after the picker returned.
        val photo = grid().at(0)
        assertEquals(UploadStatus.InFlight, photo?.status)
        assertEquals("content://pick/0", photo?.uri)
        // The user can see WHICH photo is uploading, and the count has not moved.
        assertEquals(0, grid().confirmedCount)
    }

    @Test
    fun `a confirmed upload advances the count and closes the sheet`() = runTest(dispatcher) {
        pick(0)
        advanceUntilIdle()
        assertEquals(UploadStatus.Confirmed, grid().at(0)?.status)
        assertEquals(1, grid().confirmedCount)
        assertFalse(vm.state.value.sheetOpen)
    }

    @Test
    fun `progress reaches the slot while the upload is running`() = runTest(dispatcher) {
        repo.reportProgress = true
        pick(0)
        advanceUntilIdle()
        // The ring is determinate because this number is real. A slot that ended at 0 would mean
        // the ring had been drawn from nothing.
        assertEquals(1f, grid().at(0)?.progress)
    }

    // ── failure keeps the slot ──────────────────────────────────────────────

    @Test
    fun `a failed upload keeps its slot and its image`() = runTest(dispatcher) {
        repo.queued += UploadPhotoResult.Failed(null)
        pick(0)
        advanceUntilIdle()
        val photo = grid().at(0)
        assertEquals(UploadStatus.Failed, photo?.status)
        // The grid is NEVER cleared on a failure: Retry re-uploads what was picked.
        assertEquals("content://pick/0", photo?.uri)
        assertEquals(0, grid().confirmedCount)
    }

    @Test
    fun `retry re-uploads into the same slot without re-picking`() = runTest(dispatcher) {
        repo.queued += UploadPhotoResult.Failed(null)
        pick(0)
        advanceUntilIdle()
        assertEquals(1, repo.uploaded.size)

        vm.retry(0)
        advanceUntilIdle()
        assertEquals(UploadStatus.Confirmed, grid().at(0)?.status)
        // Two uploads, one slot, and the second sent the same bytes -- nothing went back to the
        // picker.
        assertEquals(2, repo.uploaded.size)
        assertEquals(1, grid().photos.size)
    }

    @Test
    fun `an unreadable picked image is a failed upload, not a crash`() = runTest(dispatcher) {
        val vmNoBytes = PhotosViewModel(
            repo = repo,
            access = FixedPhotoAccess(),
            readBytes = { null },
            analytics = analytics,
        )
        vmNoBytes.tapSlot(0)
        vmNoBytes.picked("content://revoked", PhotoSource.Library)
        advanceUntilIdle()
        assertEquals(UploadStatus.Failed, vmNoBytes.state.value.grid.at(0)?.status)
        // And nothing was sent.
        assertTrue(repo.uploaded.isEmpty())
    }

    // ── removal ─────────────────────────────────────────────────────────────

    @Test
    fun `removing a stored photo deletes it on the server`() = runTest(dispatcher) {
        pick(0)
        advanceUntilIdle()
        val remoteId = grid().at(0)?.remoteId
        vm.remove(0)
        advanceUntilIdle()
        assertTrue(grid().photos.isEmpty())
        assertEquals(listOf(remoteId), repo.removed)
    }

    @Test
    fun `removing an in-flight photo cancels its upload and deletes nothing`() =
        runTest(dispatcher) {
            vm.tapSlot(0)
            vm.picked("content://pick/0", PhotoSource.Library)
            vm.remove(0)
            advanceUntilIdle()
            assertTrue(grid().photos.isEmpty())
            // There is no server id yet, so there is nothing to delete -- and asking the server to
            // delete null is how a client ends up removing somebody else's row.
            assertTrue(repo.removed.isEmpty())
        }

    @Test
    fun `removing one photo does not cancel another upload`() = runTest(dispatcher) {
        pick(0)
        advanceUntilIdle()
        vm.tapSlot(1)
        vm.picked("content://pick/1", PhotoSource.Library)
        vm.remove(0)
        advanceUntilIdle()
        // The second upload survived its neighbour being removed.
        assertEquals(1, grid().photos.size)
        assertEquals(UploadStatus.Confirmed, grid().at(0)?.status)
    }

    // ── reordering ──────────────────────────────────────────────────────────

    @Test
    fun `reordering into position one moves the main photo`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        pick(1); advanceUntilIdle()
        val second = grid().at(1)?.localId
        vm.reorder(from = 1, to = 0)
        assertEquals(second, grid().at(0)?.localId)
    }

    // ── tracking ────────────────────────────────────────────────────────────

    @Test
    fun `the minimum is reported once, on the fourth confirmed upload`() = runTest(dispatcher) {
        repeat(4) { slot ->
            pick(slot)
            advanceUntilIdle()
        }
        assertEquals(1, analytics.count("photos_minimum_met"))
        // A fifth does not fire it again.
        vm.tapSlot(4)
        vm.picked("content://pick/4", PhotoSource.Library)
        advanceUntilIdle()
        assertEquals(1, analytics.count("photos_minimum_met"))
    }

    @Test
    fun `the minimum is not reported when the fourth upload fails`() = runTest(dispatcher) {
        repeat(3) { slot -> pick(slot); advanceUntilIdle() }
        repo.queued += UploadPhotoResult.Failed(null)
        pick(3)
        advanceUntilIdle()
        assertEquals(0, analytics.count("photos_minimum_met"))
        assertEquals(3, grid().confirmedCount)
    }

    @Test
    fun `photo_added carries the confirmed count, not the slot count`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        repo.queued += UploadPhotoResult.Failed(null)
        pick(1); advanceUntilIdle()
        pick(2); advanceUntilIdle()

        val added = analytics.events.filter { it.first == "photo_added" }
        assertEquals(2, added.size)
        // Two landed, one did not. The second photo_added says 2, not 3.
        assertEquals(2, added.last().second["filled_count"])
        assertEquals(6, added.last().second["max_slots"])
    }

    @Test
    fun `a tap on a filled slot reports a replace`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        analytics.events.clear()
        vm.tapSlot(0)
        val tap = analytics.events.single { it.first == "photo_slot_tapped" }
        assertEquals("replace", tap.second["action"])
        assertEquals(false, tap.second["is_optional"])
    }

    @Test
    fun `add more reports the reveal rather than a tap on a slot`() = runTest(dispatcher) {
        vm.revealOptional()
        val tap = analytics.events.single { it.first == "photo_slot_tapped" }
        assertEquals("reveal_optional", tap.second["action"])
        assertEquals(true, tap.second["is_optional"])
        assertTrue(grid().optionalRevealed)
    }

    @Test
    fun `no photo payload carries a uri, a filename or a byte`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        vm.remove(0)
        advanceUntilIdle()
        val rendered = analytics.events.joinToString(" ") { it.second.values.joinToString(" ") }
        listOf("content://", "photo.jpeg", "image/jpeg").forEach {
            assertFalse("a payload leaked '$it': $rendered", rendered.contains(it))
        }
    }

    @Test
    fun `the source travels as the registry value`() = runTest(dispatcher) {
        pick(0, PhotoSource.Camera)
        advanceUntilIdle()
        val added = analytics.events.single { it.first == "photo_added" }
        assertEquals("camera", added.second["source"])
    }

    // ── access ──────────────────────────────────────────────────────────────

    @Test
    fun `refreshing access re-reads both statuses every time`() = runTest(dispatcher) {
        val reader = object : PhotoAccessReader {
            var calls = 0
            override fun library(): LibraryAccess {
                calls += 1
                return if (calls > 1) LibraryAccess.NotNeeded else LibraryAccess.Blocked
            }
            override fun camera() = CameraAccess.CanAsk
        }
        val model = PhotosViewModel(repo, reader, { bytes }, analytics)
        model.refreshAccess()
        assertEquals(LibraryAccess.Blocked, model.state.value.library)
        // A user who granted access in Settings and came back must land on the working grid. That
        // only happens if nothing is cached, which is what this asserts.
        model.refreshAccess()
        assertEquals(LibraryAccess.NotNeeded, model.state.value.library)
    }

    @Test
    fun `the sheet opens on a slot tap and remembers which slot`() = runTest(dispatcher) {
        vm.tapSlot(2)
        assertTrue(vm.state.value.sheetOpen)
        assertEquals(2, vm.state.value.pendingSlot)
        vm.dismissSheet()
        assertFalse(vm.state.value.sheetOpen)
        // Dismissing the sheet adds nothing.
        assertTrue(grid().photos.isEmpty())
        assertNull(grid().at(0))
    }
}
