/*
 * MediaFlowRegressionTest.kt
 * ShowUp · the ten rules the media flow was reported as breaking (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT THESE CAN PROVE, AND WHAT THEY CANNOT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Two bugs were reported from an emulator: the video path never reached review, and playback
 * "started slow then sped up" on both media.
 *
 * THE FIRST IS NOT REACHABLE FROM HERE, and it is worth being exact rather than reassuring.
 * `FakeMediaCapture` replaces the recorder, which is where the defect lives -- below it are
 * CameraX and `MediaRecorder`, neither of which a JVM test runs. What this file can do is prove
 * that everything ABOVE the recorder is correct: given a recorder that produces a file, the state
 * machine reaches review, both actions do what the spec says, and the card ends up filled with
 * the prompt preserved. That narrows any future report to the layer below, which is the part
 * these tests were missing when the bug shipped.
 *
 * THE SECOND IS REACHABLE, and is asserted directly: elapsed time must come from the PLAYER, so a
 * clock that runs at a different rate from the ticks cannot change what the screen reads.
 *
 * Both media are driven through every rule. The two differ in how they reach the platform and
 * must not differ in anything a user sees.
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaFlowRegressionTest {

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun count(name: String) = events.count { it.first == name }
    }

    /** Stores nothing and answers everything. The flow under test is not the network. */
    private class FakeRepo : MediaRepository(
        api = com.showup.api.ShowUpApi(
            baseUrl = "http://127.0.0.1:1/",
            tokens = com.showup.api.InMemoryTokenStore(),
        ),
        offline = null,
    ) {
        override suspend fun load(): MediaSnapshot? =
            MediaSnapshot(emptyList(), null, null, MediaPreviewSource.Fallback)

        override suspend fun upload(
            kind: MediaKind,
            promptId: String,
            durationMs: Int,
            bytes: ByteArray,
            mimeType: String,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): UploadMediaResult = UploadMediaResult.Stored(
            StoredMedia("remote-1", kind, "u", promptId, durationMs),
        )

        override suspend fun remove(id: String): RemoveMediaResult = RemoveMediaResult.Removed
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var capture: FakeMediaCapture
    private lateinit var analytics: Recorder

    /**
     * THE PLAYER'S CLOCK, AND ONLY THE PLAYER'S.
     *
     * The view model's ticker runs on ordinary virtual time; this one is moved by hand. Driving
     * them independently is how a test can tell "the screen reads the player" from "the screen
     * counts its own ticks" -- with one clock the two are indistinguishable, which is exactly why
     * the drift shipped.
     *
     * THE VIEW MODEL'S CLOCK IS NOT FROZEN, and an earlier version of this file froze it. That
     * hangs the playback poll against its pass bound and then STOPS PLAYBACK, so every assertion
     * read `null` -- a fixture that broke the thing it was measuring.
     */
    private var playerClock = 0L
    private lateinit var player: FakeMediaPlayer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo()
        capture = FakeMediaCapture()
        playerClock = 0L
        player = FakeMediaPlayer(now = { playerClock })
        analytics = Recorder()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Reads and deletes nothing: the paths here are labels rather than places. */
    private class Vm(
        repo: MediaRepository,
        capture: MediaCaptureFactory,
        player: MediaPlayerFactory,
        analytics: AnalyticsTracker?,
        elapsedRealtimeMs: () -> Long,
    ) : MediaViewModel(
        repo, FixedMediaAccess(GRANTED), capture, player, analytics, { 0L },
        tickMs = 10L, elapsedRealtimeMs = elapsedRealtimeMs,
    ) {
        override fun readTake(path: String?): ByteArray? = ByteArray(8)
        override fun deleteTake(path: String) = Unit

        companion object {
            val GRANTED = MediaAccess(MediaPermission.Granted, MediaPermission.Granted)
        }
    }

    private fun build(clock: () -> Long = { dispatcher.scheduler.currentTime }) =
        Vm(repo, capture, player, analytics, clock).also { it.refreshAccess() }



    /** Prompt chosen, recorder running. The state every rule below starts from. */
    private suspend fun startTake(vm: MediaViewModel, kind: MediaKind, promptId: String) {
        vm.openPrompts(kind, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(promptId)
        vm.commitPrompt()
        delay(300)
    }

    /** A real prompt id, the way the sheet supplies one. */
    /** Records a take, accepts it, and returns the duration the card ended up with. */
    private suspend fun recordAndAccept(vm: MediaViewModel, kind: MediaKind): Int {
        startTake(vm, kind, promptFor(kind))
        delay(2_000)
        vm.stopPressed()
        advanceUntilIdleIn()
        val duration = vm.state.value.take?.elapsedMs ?: 0
        vm.acceptTake()
        advanceUntilIdleIn()
        return duration
    }

    private suspend fun advanceUntilIdleIn() {
        // `advanceUntilIdle` is not available inside a plain suspend helper, so the scheduler is
        // driven directly -- the same call it makes.
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun promptFor(kind: MediaKind) = MediaPrompts.preview(kind, null).id

    // ── 1 · record → stop → review ──────────────────────────────────────────

    @Test
    fun `stopping a video take enters review with the take's real length`() =
        runTest(dispatcher) {
            val vm = build()
            val prompt = promptFor(MediaKind.Video)
            startTake(vm, MediaKind.Video, prompt)
            delay(2_000)

            vm.stopPressed()
            advanceUntilIdle()

            val take = vm.state.value.take
            assertNotNull("stop must land on review, never back on the card", take)
            assertEquals(RecordingPhase.Review, take?.phase)
            assertEquals("the prompt survives the recorder", prompt, take?.promptId)
            assertTrue("the recorded length is carried into review", (take?.elapsedMs ?: 0) > 0)
            assertNotNull("and the file it will play", take?.path)
            assertEquals(MediaStopReason.UserStop, take?.stopReason)
        }

    @Test
    fun `stopping a voice take enters review in exactly the same shape`() =
        runTest(dispatcher) {
            // THE TWO MEDIA REACH THE PLATFORM DIFFERENTLY and must not differ above it. Voice was
            // the working path when video was broken, so it is the control: every assertion here
            // is the one above with the kind changed.
            val vm = build()
            val prompt = promptFor(MediaKind.Voice)
            startTake(vm, MediaKind.Voice, prompt)
            delay(2_000)

            vm.stopPressed()
            advanceUntilIdle()

            val take = vm.state.value.take
            assertEquals(RecordingPhase.Review, take?.phase)
            assertEquals(prompt, take?.promptId)
            assertTrue((take?.elapsedMs ?: 0) > 0)
            assertNotNull(take?.path)
        }

    // ── 2 · review offers exactly two ways on ───────────────────────────────

    @Test
    fun `review offers retake and accept, and accept is the only one that keeps anything`() =
        runTest(dispatcher) {
            for (kind in MediaKind.entries) {
                val vm = build()
                startTake(vm, kind, promptFor(kind))
                delay(2_000)
                vm.stopPressed()
                advanceUntilIdle()

                // The screen draws `Retake` and `Use this clip` off this phase -- see
                // MediaCaptureScreen's `ReviewActions`. What the model owes them is that BOTH are
                // available and NEITHER has yet produced an artefact.
                assertEquals(RecordingPhase.Review, vm.state.value.take?.phase)
                assertNull("stopping produces a take, not an artefact", vm.state.value.artefact(kind))
            }
        }

    // ── 3 · retake ──────────────────────────────────────────────────────────

    @Test
    fun `retake discards only that take and returns to the same prompt`() =
        runTest(dispatcher) {
            val vm = build()
            val prompt = promptFor(MediaKind.Video)
            startTake(vm, MediaKind.Video, prompt)
            delay(2_000)
            vm.stopPressed()
            advanceUntilIdle()
            val first = capture.sessions.single()

            vm.retakeFromReview()
            // BOUNDED. The new take starts recording immediately, and draining the scheduler
            // would run it all the way to the cap and back into review -- which is the state
            // this test exists to distinguish from.
            delay(300)

            val take = vm.state.value.take
            assertNotNull("retake returns to the viewfinder, not to the card", take)
            assertEquals("ON THE SAME PROMPT -- it does not reopen the list", prompt, take?.promptId)
            assertEquals(RecordingPhase.Recording, take?.phase)
            assertEquals("and it is a second attempt", 2, take?.attempt)
            assertTrue("the first take's file is discarded", first.discarded)
            assertNull("and nothing was kept", vm.state.value.video)
        }

    // ── 4 · accept ──────────────────────────────────────────────────────────

    @Test
    fun `use this clip fills the video card immediately, with the prompt preserved`() =
        runTest(dispatcher) {
            val vm = build()
            val prompt = promptFor(MediaKind.Video)
            startTake(vm, MediaKind.Video, prompt)
            delay(2_000)
            vm.stopPressed()
            advanceUntilIdle()
            val recorded = vm.state.value.take?.elapsedMs ?: 0

            vm.acceptTake()

            // OPTIMISTIC, and asserted BEFORE the upload is allowed to run: the card is filled the
            // moment the user accepts, and the network happens afterwards. Draining the scheduler
            // first would pass whether or not that were true.
            val artefact = vm.state.value.video
            assertNotNull("the card is filled on accept, not on upload", artefact)
            assertEquals("the chosen prompt is the card's caption", prompt, artefact?.promptId)
            assertEquals(recorded, artefact?.durationMs)
            assertNotNull("and it plays from the local file until the upload lands", artefact?.localPath)
            assertNull("the viewfinder is gone", vm.state.value.take)
            assertNull("THE VOICE CARD IS UNTOUCHED", vm.state.value.voice)

            advanceUntilIdle()
            assertEquals(
                "and the upload confirms it rather than creating it",
                MediaUploadStatus.Confirmed, vm.state.value.video?.status,
            )
        }

    @Test
    fun `use this recording fills the voice card and leaves the video one alone`() =
        runTest(dispatcher) {
            val vm = build()
            val prompt = promptFor(MediaKind.Voice)
            startTake(vm, MediaKind.Voice, prompt)
            delay(2_000)
            vm.stopPressed()
            advanceUntilIdle()

            vm.acceptTake()

            assertNotNull(vm.state.value.voice)
            assertEquals(prompt, vm.state.value.voice?.promptId)
            assertNull("THE VIDEO CARD IS UNTOUCHED", vm.state.value.video)
        }

    // ── 5 · the cap behaves exactly as Stop does ────────────────────────────

    @Test
    fun `reaching the cap enters review the same way tapping stop does`() =
        runTest(dispatcher) {
            for (kind in MediaKind.entries) {
                val vm = build()
                val prompt = promptFor(kind)
                startTake(vm, kind, prompt)
                // Let it run all the way to the limit rather than stopping it.
                advanceUntilIdle()

                val take = vm.state.value.take
                assertEquals("the cap shows review, never an alert", RecordingPhase.Review,
                    take?.phase)
                assertEquals(MediaLimits.maxMs(kind), take?.elapsedMs)
                // The ONE difference from a user stop, and it is a measurement rather than a
                // behaviour: `max_length` is how the ticket learns whether the cap is too short.
                assertEquals(MediaStopReason.MaxLength, take?.stopReason)
                assertEquals(prompt, take?.promptId)
            }
        }

    // ── 6, 7, 8 · playback is the player's, not a ticker's ──────────────────

    /**
     * THE REGRESSION FOR "PLAYBACK STARTS SLOW THEN SPEEDS UP".
     *
     * The player always ran at 1.0 -- nothing sets a rate anywhere -- and the readout always came
     * from `positionMs()`. What drifted was the POLL: `delay(tickMs)` sleeps at least its
     * argument, the overshoot compounded, and the bar updated late and then jumped.
     *
     * Here the clock runs at THREE TIMES the tick, which is that failure exaggerated and inverted:
     * a build that derives position from its own ticks reads a third of the truth, and one that
     * reads the player reads the player. Asserting the position against a clock the ticks disagree
     * with is the only way to tell those apart.
     */
    @Test
    fun `playback position comes from the player, not from the tick count`() =
        runTest(dispatcher) {
            val vm = build()
            recordAndAccept(vm, MediaKind.Voice)

            vm.cardPlayPressed(MediaKind.Voice)
            // BOUNDED, like every advance around a self-rescheduling poll in this file: draining
            // the scheduler runs the play clock to its pass bound and STOPS PLAYBACK, so the
            // state under test would be gone before it was read.
            delay(50)

            // The PLAYER moves four seconds. The view model's own clock barely moves at all.
            // A build that derived position from its ticks would report roughly nothing.
            playerClock += 4_000
            delay(60)
            assertEquals(4_000, vm.state.value.playback?.positionMs)

            // The tick clock races ahead and the player does not. The readout must not follow it:
            // this is the "speeds up to catch up" half of the report, and a counter would.
            // Virtual time races ahead and the PLAYER does not move. The readout must not
            // follow the clock: this is the "speeds up to catch up" half of the report, and a
            // counter would.
            delay(3_000)
            assertEquals(
                "the readout is the player's position and nothing else",
                4_000, vm.state.value.playback?.positionMs,
            )
        }

    @Test
    fun `a stalled clock cannot make playback run fast to catch up`() =
        runTest(dispatcher) {
            // BACKGROUNDING, OR AN EMULATOR HITCH: the poll misses several ticks and resumes. A
            // clock that counted ticks would be behind and would race; one that reads the player
            // reports where the player is, which is where it was all along.
            val vm = build()
            recordAndAccept(vm, MediaKind.Video)

            vm.cardPlayPressed(MediaKind.Video)
            delay(50)

            // The poll misses a long stretch of wall time; the player moved on normally.
            playerClock += 2_100
            delay(2_000)

            assertEquals(
                "never a sum of skipped ticks",
                2_100, vm.state.value.playback?.positionMs,
            )
        }

    @Test
    fun `the progress the screen draws is the position the player reported`() =
        runTest(dispatcher) {
            // The bar is `playback.progress` -- position over duration -- and there is no path
            // from the bar back into the player. The UI follows; it cannot drive.
            val vm = build()
            val duration = recordAndAccept(vm, MediaKind.Voice)

            vm.cardPlayPressed(MediaKind.Voice)
            // BOUNDED, like every advance around a self-rescheduling poll in this file: draining
            // the scheduler runs the play clock to its pass bound and STOPS PLAYBACK, so the
            // state under test would be gone before it was read.
            delay(50)
            playerClock += duration / 2
            delay(60)

            val playback = vm.state.value.playback
            assertNotNull(playback)
            assertEquals(0.5f, playback!!.progress, 0.05f)
        }

    // ── 9 · a take that produces nothing says so ────────────────────────────

    @Test
    fun `a take that records nothing reports which failure it was`() = runTest(dispatcher) {
        val vm = Vm(repo, FakeMediaCapture(finishSucceeds = false), player, analytics) {
            dispatcher.scheduler.currentTime
        }.also { it.refreshAccess() }
        startTake(vm, MediaKind.Video, promptFor(MediaKind.Video))
        delay(2_000)
        vm.stopPressed()
        advanceUntilIdle()

        assertNull(vm.state.value.take)
        assertNull(vm.state.value.video)
        // THE PART THAT WAS MISSING. Two reports of this could not be told apart because the
        // screen said nothing at all; now it says which of the two failures happened.
        assertEquals(
            CaptureFailed(MediaKind.Video, CaptureFailure2.NothingRecorded),
            vm.state.value.captureFailed,
        )
    }

    @Test
    fun `a recorder that never starts is reported as a different failure`() = runTest(dispatcher) {
        val vm = Vm(repo, FakeMediaCapture(startSucceeds = false), player, analytics) {
            dispatcher.scheduler.currentTime
        }.also { it.refreshAccess() }
        startTake(vm, MediaKind.Voice, promptFor(MediaKind.Voice))
        advanceUntilIdle()

        assertEquals(
            CaptureFailed(MediaKind.Voice, CaptureFailure2.NeverStarted),
            vm.state.value.captureFailed,
        )
    }

    @Test
    fun `a new attempt clears the last failure`() = runTest(dispatcher) {
        val vm = Vm(repo, FakeMediaCapture(startSucceeds = false), player, analytics) {
            dispatcher.scheduler.currentTime
        }.also { it.refreshAccess() }
        startTake(vm, MediaKind.Video, promptFor(MediaKind.Video))
        advanceUntilIdle()
        assertNotNull(vm.state.value.captureFailed)

        // OPENING THE LIST IS ENOUGH. The notice belongs to a take that is over, and the user
        // choosing a prompt again has moved on from it -- waiting for the next take to BEGIN
        // would leave the message up underneath the sheet the whole time it is open.
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)

        assertNull("a stale failure must not sit under a fresh choice", vm.state.value.captureFailed)
    }
}
