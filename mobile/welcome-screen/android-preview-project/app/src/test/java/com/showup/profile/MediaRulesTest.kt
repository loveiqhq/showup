/*
 * MediaRulesTest.kt
 * ShowUp · every rule on the media step, with no camera and no microphone (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THIS CAN AND CANNOT PROVE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * It proves the DECISIONS: the caps, the interruption threshold, the attempt counter, the retake
 * loop, the difference between stopping and keeping, the permission matrix, and which slot ends up
 * holding what. All of that lives in [MediaViewModel] precisely so that it can be proved here.
 *
 * It proves NOTHING about the recorders. [FakeMediaCapture] writes no bytes and opens no hardware,
 * so "CameraX produces a playable file" and "MediaRecorder survives a real phone call" are claims
 * only a device can settle, and they are reported as unverified rather than implied by a green bar.
 *
 * The clock is injected and the tick is 10ms of VIRTUAL time, so a ten-second cap is reached in a
 * thousand loop iterations and no wall-clock time at all. Every delay below is a whole multiple of
 * the tick, so the elapsed values stay exact rather than landing near the number asserted.
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRulesTest {

    /** Answers from memory, so the rules rather than the transport are what is measured. */
    private class FakeRepo(
        var snapshot: MediaSnapshot? = MediaSnapshot(emptyList(), null, null, MediaPreviewSource.Fallback),
        var uploadFails: Boolean = false,
    ) : MediaRepository(
        api = com.showup.api.ShowUpApi(
            baseUrl = "http://127.0.0.1:1/",
            tokens = com.showup.api.InMemoryTokenStore(),
        ),
        offline = null,
    ) {
        val removed = mutableListOf<String>()
        val uploaded = mutableListOf<Triple<MediaKind, String, Int>>()

        override suspend fun load(): MediaSnapshot? = snapshot

        override suspend fun upload(
            kind: MediaKind,
            promptId: String,
            durationMs: Int,
            bytes: ByteArray,
            mimeType: String,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): UploadMediaResult {
            uploaded += Triple(kind, promptId, durationMs)
            return if (uploadFails) {
                UploadMediaResult.Failed(null)
            } else {
                UploadMediaResult.Stored(
                    StoredMedia("remote-${uploaded.size}", kind, "u", promptId, durationMs),
                )
            }
        }

        override suspend fun remove(id: String): RemoveMediaResult {
            removed += id
            return RemoveMediaResult.Removed
        }
    }

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
        fun only(name: String) = events.single { it.first == name }.second
        fun last(name: String) = events.last { it.first == name }.second
        fun has(name: String) = events.any { it.first == name }
    }

    /** Reads no file and deletes none: the paths here are labels, not places. */
    private class TestViewModel(
        repo: MediaRepository,
        access: MediaAccessReader,
        capture: MediaCaptureFactory,
        analytics: AnalyticsTracker?,
        now: () -> Long,
    ) : MediaViewModel(repo, access, capture, analytics, now, tickMs = 10L) {
        val deleted = mutableListOf<String>()
        override fun readTake(path: String?): ByteArray? = ByteArray(8)
        override fun deleteTake(path: String) {
            deleted += path
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private var clock = 0L

    private lateinit var repo: FakeRepo
    private lateinit var capture: FakeMediaCapture
    private lateinit var analytics: Recorder

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = 0L
        repo = FakeRepo()
        capture = FakeMediaCapture()
        analytics = Recorder()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build(access: MediaAccess = GRANTED) = TestViewModel(
        repo = repo,
        access = FixedMediaAccess(access),
        capture = capture,
        analytics = analytics,
        now = { clock },
    ).also {
        // The view model starts at NotDetermined for both and only learns otherwise by asking, so
        // a fixture that skipped this would have every test blocked at the permission gate -- which
        // is exactly what happened the first time this file ran, and is the gate working. `arrived`
        // does the same thing on a real screen; this is the one line of it these tests need.
        it.refreshAccess()
    }

    private companion object {
        val GRANTED = MediaAccess(MediaPermission.Granted, MediaPermission.Granted)
        const val PROMPT = "comfort_snack"
    }

    /** Opens the list, picks a row, commits, stops, keeps. The whole happy path in one helper. */
    private suspend fun kotlinx.coroutines.test.TestScope.record(
        vm: MediaViewModel,
        kind: MediaKind,
        promptId: String = PROMPT,
        stopAfterMs: Int? = null,
    ) {
        vm.openPrompts(kind, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(promptId)
        vm.commitPrompt()
        advanceUntilIdle()
        if (stopAfterMs != null) {
            // The clock ticks 1ms at a time, so N virtual milliseconds is N ticks.
            kotlinx.coroutines.delay(stopAfterMs.toLong())
            vm.stopPressed()
        } else {
            vm.stopPressed()
        }
        advanceUntilIdle()
        vm.acceptTake()
        advanceUntilIdle()
    }

    // ── the caps ─────────────────────────────────────────────────────────────

    @Test
    fun `the video cap stops the take and shows review, with no alert and no truncated save`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()

            assertEquals(RecordingPhase.Review, vm.state.value.take?.phase)
            assertEquals(MediaLimits.VIDEO_MS, vm.state.value.take?.elapsedMs)
            assertEquals(MediaStopReason.MaxLength, vm.state.value.take?.stopReason)
            // REVIEW, not an artefact. Stopping produces a take; only accepting produces one.
            assertNull(vm.state.value.video)
        }

    @Test
    fun `the voice cap is fifteen seconds, not ten`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        assertEquals(MediaLimits.VOICE_MS, vm.state.value.take?.elapsedMs)
    }

    @Test
    fun `a user stop before the cap reports user_stop`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        kotlinx.coroutines.delay(3_000)
        vm.stopPressed()
        advanceUntilIdle()
        assertEquals(MediaStopReason.UserStop, vm.state.value.take?.stopReason)
        assertEquals(RecordingPhase.Review, vm.state.value.take?.phase)
    }

    // ── stop versus keep ─────────────────────────────────────────────────────

    @Test
    fun `stopping creates no artefact and accepting does`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        kotlinx.coroutines.delay(4_000)
        vm.stopPressed()
        advanceUntilIdle()
        assertNull("Stop must not fill the card", vm.state.value.video)

        vm.acceptTake()
        advanceUntilIdle()
        assertNotNull(vm.state.value.video)
        assertNull("The take is spent once kept", vm.state.value.take)
        assertEquals(PROMPT, vm.state.value.video?.promptId)
    }

    @Test
    fun `cancel in the viewfinder saves nothing and discards the file`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        kotlinx.coroutines.delay(2_000)
        vm.cancelTake()
        advanceUntilIdle()
        assertNull(vm.state.value.take)
        assertNull(vm.state.value.voice)
        assertTrue(capture.sessions.single().discarded)
    }

    // ── interruptions ────────────────────────────────────────────────────────

    @Test
    fun `an interruption at or past two seconds shows review`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        // One tick PAST the threshold, and the premise is asserted rather than assumed: the test
        // body and the view model's clock share one virtual timeline, so a delay landing on exactly
        // the threshold resumes in the same instant as the tick that would reach it, and the order
        // of those two is undefined. Asserting what was captured before interrupting is what makes
        // this a test of the rule rather than of the scheduler.
        kotlinx.coroutines.delay(MediaLimits.INTERRUPTION_KEEP_MS.toLong() + 10L)
        assertTrue(
            "the take must have passed the keep threshold before it is interrupted",
            (vm.state.value.take?.elapsedMs ?: 0) >= MediaLimits.INTERRUPTION_KEEP_MS,
        )
        capture.sessions.single().interrupt()
        advanceUntilIdle()
        assertEquals(RecordingPhase.Review, vm.state.value.take?.phase)
    }

    @Test
    fun `an interruption under two seconds discards and returns to the card`() =
        runTest(dispatcher) {
            // NEVER A SILENT RESUME either way -- the take ends, and the only question is whether
            // there is enough of it to be worth showing.
            val vm = build()
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            kotlinx.coroutines.delay(500)
            capture.sessions.single().interrupt()
            advanceUntilIdle()
            assertNull(vm.state.value.take)
            assertNull(vm.state.value.voice)
            assertTrue(capture.sessions.single().discarded)
        }

    @Test
    fun `a recorder that will not start returns to the card rather than to an empty review`() =
        runTest(dispatcher) {
            val vm = TestViewModel(repo, FixedMediaAccess(GRANTED), FakeMediaCapture(startSucceeds = false), analytics) { clock }
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            assertNull(vm.state.value.take)
        }

    // ── the retake loop ──────────────────────────────────────────────────────

    @Test
    fun `retake from review returns to the viewfinder on the same prompt and counts up`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            kotlinx.coroutines.delay(3_000)
            vm.stopPressed()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.take?.attempt)

            vm.retakeFromReview()
            advanceUntilIdle()
            val take = vm.state.value.take
            assertEquals("The same prompt -- it does NOT reopen the list", PROMPT, take?.promptId)
            assertEquals(2, take?.attempt)
            assertNull("A retake is not a sheet", vm.state.value.sheet)
        }

    @Test
    fun `retake from a filled card reopens the list with the answered prompt selected`() =
        runTest(dispatcher) {
            val vm = build()
            record(vm, MediaKind.Video)
            vm.retakeFromCard(MediaKind.Video)
            assertEquals(PROMPT, vm.state.value.sheet?.selectedId)
            assertEquals(MediaEntryPoint.Retake, vm.state.value.sheet?.entryPoint)
            assertNotNull("The artefact stays until a new take is accepted", vm.state.value.video)
        }

    @Test
    fun `the attempt counter resets when the prompt changes`() = runTest(dispatcher) {
        // Attempt 3 of a prompt the user has just switched to is not a third attempt at anything.
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        kotlinx.coroutines.delay(2_000)
        vm.stopPressed()
        advanceUntilIdle()
        vm.retakeFromReview()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.take?.attempt)

        vm.cancelTake()
        advanceUntilIdle()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt("best_weather")
        vm.commitPrompt()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.take?.attempt)
    }

    // ── the two slots ────────────────────────────────────────────────────────

    @Test
    fun `the two slots are independent`() = runTest(dispatcher) {
        val vm = build()
        record(vm, MediaKind.Video, "relaxed_and_happy")
        record(vm, MediaKind.Voice, "relaxing_sound")
        assertEquals("relaxed_and_happy", vm.state.value.video?.promptId)
        assertEquals("relaxing_sound", vm.state.value.voice?.promptId)
    }

    @Test
    fun `delete is immediate and returns the card to its previewed prompt`() =
        runTest(dispatcher) {
            val vm = build()
            record(vm, MediaKind.Video)
            assertNotNull(vm.state.value.video)

            vm.delete(MediaKind.Video)
            // NO CONFIRMATION STEP: the state changes on the call, not after an answer.
            assertNull(vm.state.value.video)
            advanceUntilIdle()
            assertEquals(listOf("remote-1"), repo.removed)
            assertEquals("relaxed_and_happy", vm.state.value.preview(MediaKind.Video).id)
        }

    @Test
    fun `deleting one slot leaves the other alone`() = runTest(dispatcher) {
        val vm = build()
        record(vm, MediaKind.Video)
        record(vm, MediaKind.Voice)
        vm.delete(MediaKind.Video)
        advanceUntilIdle()
        assertNull(vm.state.value.video)
        assertNotNull(vm.state.value.voice)
    }

    // ── uploading ────────────────────────────────────────────────────────────

    @Test
    fun `the card fills optimistically and confirms when the upload lands`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            kotlinx.coroutines.delay(5_000)
            vm.stopPressed()
            advanceUntilIdle()
            vm.acceptTake()
            // Filled before anything has been sent -- that is what optimistic means.
            assertNotNull(vm.state.value.video)
            assertEquals(MediaUploadStatus.Queued, vm.state.value.video?.status)

            advanceUntilIdle()
            assertEquals(MediaUploadStatus.Confirmed, vm.state.value.video?.status)
            assertEquals("remote-1", vm.state.value.video?.remoteId)
        }

    @Test
    fun `a failed upload surfaces on the card and keeps the artefact`() = runTest(dispatcher) {
        repo.uploadFails = true
        val vm = build()
        record(vm, MediaKind.Voice)
        assertEquals(MediaUploadStatus.Failed, vm.state.value.voice?.status)
        assertNotNull("A failure must not empty the card", vm.state.value.voice)
    }

    @Test
    fun `retry re-sends a failed upload`() = runTest(dispatcher) {
        repo.uploadFails = true
        val vm = build()
        record(vm, MediaKind.Voice)
        repo.uploadFails = false
        vm.retryUpload(MediaKind.Voice)
        advanceUntilIdle()
        assertEquals(MediaUploadStatus.Confirmed, vm.state.value.voice?.status)
        assertEquals(2, repo.uploaded.size)
    }

    @Test
    fun `a server read never replaces a take that is still uploading`() = runTest(dispatcher) {
        // The same defect the photo grid had to be fixed for: a read landing mid-flight would
        // otherwise replace the card the user is watching with the server's older answer.
        repo.uploadFails = true
        val vm = build()
        record(vm, MediaKind.Voice, "made_me_smile")
        repo.snapshot = MediaSnapshot(
            items = listOf(StoredMedia("old", MediaKind.Voice, "u", "best_weather", 3_000)),
            previewVideoId = null, previewVoiceId = null,
            previewSource = MediaPreviewSource.Fallback,
        )
        vm.arrived()
        advanceUntilIdle()
        assertEquals("made_me_smile", vm.state.value.voice?.promptId)
    }

    // ── permissions ──────────────────────────────────────────────────────────

    @Test
    fun `a blocked microphone blocks both cards`() {
        val access = MediaAccess(MediaPermission.Granted, MediaPermission.Blocked)
        assertEquals(MediaCapability.Microphone, access.blockerFor(MediaKind.Video)?.capability)
        assertEquals(MediaCapability.Microphone, access.blockerFor(MediaKind.Voice)?.capability)
        assertFalse(access.isReady(MediaKind.Video))
        assertFalse(access.isReady(MediaKind.Voice))
    }

    @Test
    fun `a blocked camera blocks the video card and leaves voice fully functional`() {
        // The one that matters most: scaling the treatment to what is actually blocked.
        val access = MediaAccess(MediaPermission.Blocked, MediaPermission.Granted)
        assertEquals(MediaCapability.Camera, access.blockerFor(MediaKind.Video)?.capability)
        assertNull(access.blockerFor(MediaKind.Voice))
        assertFalse(access.isReady(MediaKind.Video))
        assertTrue(access.isReady(MediaKind.Voice))
    }

    @Test
    fun `nothing is drawn before the first refusal`() {
        // A user who has never been asked sees two clean cards, and the OS alert fires on the
        // commit CTA instead.
        val access = MediaAccess(MediaPermission.NotDetermined, MediaPermission.NotDetermined)
        assertNull(access.blockerFor(MediaKind.Video))
        assertNull(access.blockerFor(MediaKind.Voice))
        assertFalse(MediaPermission.NotDetermined.needsRow)
        assertTrue(MediaPermission.CanAsk.needsRow)
        assertTrue(MediaPermission.Blocked.needsRow)
    }

    @Test
    fun `video asks for both permissions and voice asks for one`() {
        val none = MediaAccess(MediaPermission.NotDetermined, MediaPermission.NotDetermined)
        assertEquals(
            listOf(MediaCapability.Camera, MediaCapability.Microphone),
            none.missingFor(MediaKind.Video),
        )
        assertEquals(listOf(MediaCapability.Microphone), none.missingFor(MediaKind.Voice))
    }

    @Test
    fun `the microphone is named first when both are refused`() {
        // Reporting the camera first would send somebody to Settings, have them fix the camera,
        // and return to a video card still blocked.
        val access = MediaAccess(MediaPermission.Blocked, MediaPermission.Blocked)
        assertEquals(MediaCapability.Microphone, access.blockerFor(MediaKind.Video)?.capability)
    }

    @Test
    fun `the commit CTA reports what has to be requested and starts no take`() =
        runTest(dispatcher) {
            val vm = build(MediaAccess(MediaPermission.NotDetermined, MediaPermission.NotDetermined))
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            val missing = vm.commitPrompt()
            advanceUntilIdle()
            assertEquals(listOf(MediaCapability.Microphone), missing)
            assertNull("No take until the OS has answered", vm.state.value.take)
            assertNotNull("The sheet stays open behind the alert", vm.state.value.sheet)
        }

    // ── the sheet ────────────────────────────────────────────────────────────

    @Test
    fun `nothing is preselected from an empty card`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        assertNull(vm.state.value.sheet?.selectedId)
    }

    @Test
    fun `dismissing the sheet keeps nothing`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.dismissPrompts(SheetDismissMethod.Backdrop)
        assertNull(vm.state.value.sheet)
        assertNull("The card is unchanged", vm.state.value.video)
    }

    @Test
    fun `a commit with no selection does nothing at all`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        val missing = vm.commitPrompt()
        advanceUntilIdle()
        assertTrue(missing.isEmpty())
        assertNull(vm.state.value.take)
        assertFalse(analytics.has(ProfileAnalytics.MEDIA_PROMPT_SELECTED))
    }

    @Test
    fun `picking the same row twice counts one selection`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.pickPrompt(PROMPT)
        vm.pickPrompt("best_weather")
        assertEquals(2, vm.state.value.sheet?.selectionsBefore)
    }

    // ── the preview ──────────────────────────────────────────────────────────

    @Test
    fun `the client renders what the server sent and never ranks`() = runTest(dispatcher) {
        repo.snapshot = MediaSnapshot(
            items = emptyList(),
            previewVideoId = "made_me_smile",
            previewVoiceId = "song_makes_me_move",
            previewSource = MediaPreviewSource.Ranked,
        )
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        assertEquals("made_me_smile", vm.state.value.preview(MediaKind.Video).id)
        assertEquals("song_makes_me_move", vm.state.value.preview(MediaKind.Voice).id)
        assertEquals(MediaPreviewSource.Ranked, vm.state.value.previewSource)
    }

    @Test
    fun `an unreachable server still previews something`() = runTest(dispatcher) {
        repo.snapshot = null
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        assertEquals("relaxed_and_happy", vm.state.value.preview(MediaKind.Video).id)
        assertEquals("relaxing_sound", vm.state.value.preview(MediaKind.Voice).id)
    }

    @Test
    fun `own_idea is never previewed, however it arrives`() {
        assertEquals(
            "relaxed_and_happy",
            MediaPrompts.preview(MediaKind.Video, MediaPrompts.OWN_IDEA).id,
        )
        assertEquals("relaxing_sound", MediaPrompts.preview(MediaKind.Voice, "nonsense").id)
    }
}
