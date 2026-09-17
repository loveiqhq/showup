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
import kotlinx.coroutines.yield
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

        /** Every order this repository was asked to store, in the order it was asked. */
        val orders = mutableListOf<List<String>>()

        /** What the next reorder answers. Null means "stored, in the order it was given". */
        var reorderAnswer: ReorderPhotosResult? = null

        /** What a re-read returns. Null is "nothing answered", which is the default here. */
        var listAnswer: List<StoredPhoto>? = null

        /**
         * Makes a re-read suspend once before answering.
         *
         * The only way to reproduce a real race on a test dispatcher: it lets an upload queued
         * AFTER the re-read finish BEFORE the answer arrives, which is what a network read does
         * every time and an instant fake never does.
         */
        var slowList = false

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

        override suspend fun reorder(remoteIds: List<String>): ReorderPhotosResult {
            orders += remoteIds
            return reorderAnswer ?: ReorderPhotosResult.Stored(
                remoteIds.mapIndexed { index, id -> StoredPhoto(id, "u", index) },
            )
        }

        override suspend fun list(): List<StoredPhoto>? {
            if (slowList) yield()
            return listAnswer
        }
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
        // The second upload survived its neighbour being removed -- AND STAYED IN ITS OWN BOX.
        // It used to slide into slot 0, which is what made deleting the second photo look like
        // deleting the last one.
        assertEquals(1, grid().photos.size)
        assertNull(grid().at(0))
        assertEquals(UploadStatus.Confirmed, grid().at(1)?.status)
    }

    // ── what the account already holds ──────────────────────────────────────

    @Test
    fun `arriving reads the photos the account already has`() = runTest(dispatcher) {
        repo.listAnswer = listOf(
            StoredPhoto("a", "https://cdn/a.jpg", 0),
            StoredPhoto("b", "https://cdn/b.jpg", 1),
        )
        vm.load()
        advanceUntilIdle()
        // Without this the grid started empty on every launch, and an account already at the
        // server's six-photo limit answered the next upload with a 400 that the slot could only
        // render as `Upload failed` -- with a Retry that re-sent the same bytes to the same full
        // account.
        assertEquals(listOf("a", "b"), grid().photos.mapNotNull { it.remoteId })
        assertEquals(2, grid().confirmedCount)
    }

    @Test
    fun `a read does not throw away a photo still uploading`() = runTest(dispatcher) {
        vm.tapSlot(0)
        vm.picked("content://pick/0", PhotoSource.Library)
        repo.listAnswer = listOf(StoredPhoto("a", "https://cdn/a.jpg", 0))
        vm.load()
        advanceUntilIdle()
        // The in-flight one exists only here; a read cannot know about it and must not delete it.
        assertEquals(2, grid().photos.size)
    }

    @Test
    fun `photos already on the account are not a fresh crossing of the minimum`() =
        runTest(dispatcher) {
            repo.listAnswer = (0 until 4).map { StoredPhoto("id-$it", "u", it) }
            vm.load()
            advanceUntilIdle()
            // `photos_minimum_met` fires when the count FIRST reaches four. An account that
            // already held four did not reach it just now, and reporting it here would put a
            // threshold event on every relaunch.
            assertEquals(0, analytics.count("photos_minimum_met"))
            assertEquals(4, grid().confirmedCount)
        }

    @Test
    fun `six stored photos open the optional block that two of them sit in`() =
        runTest(dispatcher) {
            repo.listAnswer = (0 until 6).map { StoredPhoto("id-$it", "u", it) }
            vm.load()
            advanceUntilIdle()
            // Slots 5 and 6 are behind `Add more`; with six stored, two photos would otherwise
            // have nowhere to be.
            assertTrue(grid().optionalRevealed)
            assertEquals(6, grid().photos.size)
        }

    @Test
    fun `a read that answers nothing leaves the grid alone`() = runTest(dispatcher) {
        vm.tapSlot(0)
        vm.picked("content://pick/0", PhotoSource.Library)
        advanceUntilIdle()
        repo.listAnswer = null
        vm.load()
        advanceUntilIdle()
        // An empty list and an unreachable server are different facts; collapsing them would wipe
        // a grid the user had just filled.
        assertEquals(1, grid().photos.size)
    }

    // ── a box emptied stays empty ───────────────────────────────────────────

    @Test
    fun `deleting the second photo leaves the others exactly where they were`() =
        runTest(dispatcher) {
            repeat(4) { slot -> pick(slot); advanceUntilIdle() }
            val ids = (0..3).map { grid().at(it)?.remoteId }

            vm.remove(1)
            advanceUntilIdle()

            // THE BUG THIS EXISTS FOR, reported from a device: the list used to close up, so
            // deleting the second photo slid the third and fourth one box left and the empty box
            // appeared at the END. It read as "the last one was deleted" and there was no way to
            // put a new photo back where the old one had been.
            assertEquals(ids[0], grid().at(0)?.remoteId)
            assertNull(grid().at(1))
            assertEquals(ids[2], grid().at(2)?.remoteId)
            assertEquals(ids[3], grid().at(3)?.remoteId)
            assertEquals(3, grid().confirmedCount)
        }

    @Test
    fun `a new photo goes into the box that was emptied`() = runTest(dispatcher) {
        repeat(4) { slot -> pick(slot); advanceUntilIdle() }
        vm.remove(1)
        advanceUntilIdle()

        pick(1)
        advanceUntilIdle()
        // Back in the second box, not appended to the end.
        assertEquals(UploadStatus.Confirmed, grid().at(1)?.status)
        assertEquals(4, grid().confirmedCount)
        assertEquals(4, grid().photos.size)
    }

    @Test
    fun `the first free box is the one Add more fills`() = runTest(dispatcher) {
        repeat(4) { slot -> pick(slot); advanceUntilIdle() }
        assertEquals(4, grid().firstFreeSlot())
        vm.remove(2)
        advanceUntilIdle()
        // The gap, not the end: a hole in the middle is the first place a photo should land.
        assertEquals(2, grid().firstFreeSlot())
    }

    // ── reordering ──────────────────────────────────────────────────────────

    @Test
    fun `reordering into position one moves the main photo`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        pick(1); advanceUntilIdle()
        val second = grid().at(1)?.localId
        vm.reorder(from = 1, to = 0)
        // Before anything is advanced: the tile has already moved. A grid that waited for the
        // round trip would read as the drag having failed.
        assertEquals(second, grid().at(0)?.localId)
    }

    @Test
    fun `a drag sends the whole order, not the pair that moved`() = runTest(dispatcher) {
        repeat(3) { slot -> pick(slot); advanceUntilIdle() }
        val ids = grid().photos.mapNotNull { it.remoteId }
        vm.reorder(from = 2, to = 0)
        advanceUntilIdle()
        // One call, carrying the complete list. Two drags racing as two diffs is exactly what
        // sending the whole order avoids.
        assertEquals(1, repo.orders.size)
        // A SWAP, not a shuffle. The grid is six fixed boxes and any of them may be empty, so
        // "take it out and push everything along" has nothing to mean -- there is nothing to push
        // into an empty box. Slot 0 and slot 2 trade places and slot 1 does not move.
        assertEquals(listOf(ids[2], ids[1], ids[0]), repo.orders.single())
    }

    @Test
    fun `a drag that changes nothing sends nothing`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        pick(1); advanceUntilIdle()
        vm.reorder(from = 1, to = 1)
        advanceUntilIdle()
        assertTrue(repo.orders.isEmpty())
    }

    @Test
    fun `a drag made during an upload is sent once the upload lands`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        pick(1); advanceUntilIdle()
        // A third photo, still going up.
        vm.tapSlot(2)
        vm.picked("content://pick/2", PhotoSource.Library)
        vm.reorder(from = 1, to = 0)
        // Nothing yet: the route takes the complete set of STORED photos and one of these is not.
        assertTrue(repo.orders.isEmpty())

        advanceUntilIdle()
        // The upload landed, and the drag the user made while it was in flight was not lost --
        // which is the whole reason it is remembered rather than dropped.
        assertEquals(1, repo.orders.size)
        assertEquals(grid().photos.mapNotNull { it.remoteId }, repo.orders.single())
    }

    @Test
    fun `an order is not sent while a slot is still failed`() = runTest(dispatcher) {
        pick(0); advanceUntilIdle()
        repo.queued += UploadPhotoResult.Failed(null)
        pick(1); advanceUntilIdle()
        vm.reorder(from = 1, to = 0)
        advanceUntilIdle()
        // A failed slot has no server id and never had one, so there is no complete list to send.
        assertTrue(repo.orders.isEmpty())

        vm.retry(0)
        advanceUntilIdle()
        // Retrying completes the grid, and the pending drag goes out with it.
        assertEquals(1, repo.orders.size)
    }

    @Test
    fun `a refused order is replaced by the one the server reports`() = runTest(dispatcher) {
        repeat(3) { slot -> pick(slot); advanceUntilIdle() }
        val ids = grid().photos.mapNotNull { it.remoteId }
        repo.reorderAnswer = ReorderPhotosResult.Failed(null)
        // The server no longer holds the middle photo -- removed on another device, say.
        repo.listAnswer = listOf(
            StoredPhoto(ids[2], "u", 0),
            StoredPhoto(ids[0], "u", 1),
        )

        vm.reorder(from = 2, to = 0)
        advanceUntilIdle()
        // The grid adopts what the server actually holds rather than keeping a photo that is gone.
        assertEquals(listOf(ids[2], ids[0]), grid().photos.mapNotNull { it.remoteId })
    }

    @Test
    fun `a refused order that cannot be re-read leaves the grid alone`() = runTest(dispatcher) {
        repeat(2) { slot -> pick(slot); advanceUntilIdle() }
        repo.reorderAnswer = ReorderPhotosResult.Failed(null)
        repo.listAnswer = null
        vm.reorder(from = 1, to = 0)
        advanceUntilIdle()
        // Nothing answered, so there is nothing to adopt. Emptying a grid the user just filled
        // because the network dropped would be the worse bug.
        assertEquals(2, grid().photos.size)
    }

    @Test
    fun `adopting the server order keeps a photo that is still uploading`() = runTest(dispatcher) {
        repeat(2) { slot -> pick(slot); advanceUntilIdle() }
        val ids = grid().photos.mapNotNull { it.remoteId }
        repo.reorderAnswer = ReorderPhotosResult.Failed(null)
        repo.listAnswer = listOf(StoredPhoto(ids[1], "u", 0), StoredPhoto(ids[0], "u", 1))
        vm.reorder(from = 1, to = 0)
        // A new photo starts uploading before the refusal comes back.
        vm.tapSlot(2)
        vm.picked("content://pick/2", PhotoSource.Library)
        advanceUntilIdle()
        // Three slots still: the in-flight one exists only here, so a read cannot know about it
        // and must not delete it.
        assertEquals(3, grid().photos.size)
    }

    @Test
    fun `an upload that lands during the re-read is not dropped by it`() = runTest(dispatcher) {
        repeat(2) { slot -> pick(slot); advanceUntilIdle() }
        val ids = grid().photos.mapNotNull { it.remoteId }
        repo.reorderAnswer = ReorderPhotosResult.Failed(null)
        // What the server held when the read went out: the third photo had not arrived yet.
        repo.listAnswer = listOf(StoredPhoto(ids[1], "u", 0), StoredPhoto(ids[0], "u", 1))
        repo.slowList = true

        vm.reorder(from = 1, to = 0)
        vm.tapSlot(2)
        vm.picked("content://pick/2", PhotoSource.Library)
        advanceUntilIdle()

        // The third upload CONFIRMED while the read was in the air, so it has a server id the
        // answer does not mention. That silence is not evidence it is gone -- it was never in the
        // order that was sent -- and dropping it here would delete a photo the user watched land.
        assertEquals(3, grid().photos.size)
        assertEquals(listOf(ids[1], ids[0]), grid().photos.take(2).mapNotNull { it.remoteId })
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
