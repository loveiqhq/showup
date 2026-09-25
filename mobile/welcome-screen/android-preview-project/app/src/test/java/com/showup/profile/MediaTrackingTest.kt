/*
 * MediaTrackingTest.kt
 * ShowUp · the rows this screen has to produce, and the ones it must never (SHOWUP-161)
 *
 * The tracking specification for this screen is the ticket's FIRST COMMENT, not its description --
 * Jira caps a description at 32,767 characters and the ticket is over it. The comment carries the
 * same weight, and its event table IS the specification. This file is that table, executable.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THE ASSERTIONS ARE PEDANTIC
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A missing row is a gap somebody notices. A wrong row is a chart somebody believes. Five of these
 * are wrong by default in a reasonable implementation:
 *
 *   1. `type` IS REQUIRED ON EVERY EVENT except the two screen views. One screen carries two media,
 *      so without it a tap on the voice card and a tap on the video card are the same row.
 *   2. `*_prompt_recorded` FIRES ON ACCEPT, NEVER ON STOP. Stopping produces a take; only keeping
 *      produces an artefact. Firing on Stop reports a completion for every abandoned take and makes
 *      the per-prompt completion rate -- what the server ranking is built on -- measure nothing.
 *   3. `was_previewed` IS WHAT KEEPS THE RANKING HONEST. The previewed prompt is far more visible
 *      than the other ten, so the job counts only takes where this is false.
 *   4. `dismiss_method`, NOT `method`, and from registry 1.4.2's section 23 rather than section 8 --
 *      `method` already carries `phone · apple · google` for auth.
 *   5. `media_prompt_id` IS NOT `prompt_id`. The written prompts on screen 07 are a different
 *      registry; the recording events carried `prompt_id` until v1.4 and were renamed for exactly
 *      this collision.
 *
 * And two events are registered but UNREACHABLE: `caption_added` and `caption_skipped` survive in
 * the registry so the profile-editing flow cannot invent a second name for the same act, and
 * nothing on this screen may fire them. [noCaptionEventIsEverEmitted] is what stops that.
 */
package com.showup.profile

import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaTrackingTest {

    private class FakeRepo(
        var snapshot: MediaSnapshot? = MediaSnapshot(
            emptyList(), null, null, MediaPreviewSource.Fallback,
        ),
    ) : MediaRepository(
        api = com.showup.api.ShowUpApi(
            baseUrl = "http://127.0.0.1:1/",
            tokens = com.showup.api.InMemoryTokenStore(),
        ),
        offline = null,
    ) {
        override suspend fun load(): MediaSnapshot? = snapshot
        override suspend fun upload(
            kind: MediaKind,
            promptId: String,
            durationMs: Int,
            bytes: ByteArray,
            mimeType: String,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): UploadMediaResult = UploadMediaResult.Stored(
            StoredMedia("remote", kind, "u", promptId, durationMs),
        )

        override suspend fun remove(id: String): RemoveMediaResult = RemoveMediaResult.Removed
    }

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
        fun only(name: String) = events.single { it.first == name }.second
        fun first(name: String) = events.first { it.first == name }.second
        fun last(name: String) = events.last { it.first == name }.second
        fun has(name: String) = events.any { it.first == name }
        fun clear() = events.clear()
    }

    private class TestViewModel(
        repo: MediaRepository,
        access: MediaAccessReader,
        capture: MediaCaptureFactory,
        analytics: AnalyticsTracker?,
        now: () -> Long,
        /**
         * VIRTUAL TIME, so the recording clock measures the same thing here as on a device.
         *
         * The view model reads elapsed time from a monotonic clock rather than counting its own
         * ticks -- counting is what made a ten-second video take fourteen on a real phone. Under
         * `runTest` a `delay` advances the scheduler by exactly its argument, so this is a clock
         * that moves in step with the ticks and the suite measures what it always did.
         */
        elapsedRealtimeMs: () -> Long,
    ) : MediaViewModel(
        repo, access, capture,
        // This suite is about what is reported, not about sound: the fake plays instantly and
        // never finishes on its own, so nothing here has to think about a playhead.
        FakeMediaPlayer(now = now),
        analytics, now, tickMs = 10L, elapsedRealtimeMs = elapsedRealtimeMs,
    ) {
        override fun readTake(path: String?): ByteArray? = ByteArray(8)
        override fun deleteTake(path: String) = Unit
    }

    private val dispatcher = StandardTestDispatcher()
    private var clock = 0L
    private lateinit var repo: FakeRepo
    private lateinit var capture: FakeMediaCapture
    private lateinit var events: Recorder

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = 0L
        repo = FakeRepo()
        capture = FakeMediaCapture()
        events = Recorder()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun build(access: MediaAccess = GRANTED) = TestViewModel(
        repo, FixedMediaAccess(access), capture, events, { clock },
        elapsedRealtimeMs = { dispatcher.scheduler.currentTime },
    ).also { it.refreshAccess() }

    private companion object {
        val GRANTED = MediaAccess(MediaPermission.Granted, MediaPermission.Granted)
        const val PROMPT = "comfort_snack"
        const val PREVIEWED_VIDEO = "relaxed_and_happy"
    }

    // ── arriving ─────────────────────────────────────────────────────────────

    @Test
    fun `arriving reports the screen, both steps, and the entry state`() = runTest(dispatcher) {
        val vm = build()
        vm.arrived()
        advanceUntilIdle()

        assertEquals(1, events.count(ProfileAnalytics.SCREEN_VIEWED))
        val view = events.only(ProfileAnalytics.SCREEN_VIEWED)
        assertEquals("profile_media", view["screen_id"])
        assertEquals("ProfileMedia", view["screen_name"])
        assertEquals("profile_prompts", view["referrer_screen_id"])

        // ONE SCREEN, TWO STEPS. Section 2 gives media_video and media_voice the same step_index.
        assertEquals(2, events.count(ProfileAnalytics.PROFILE_STEP_VIEWED))
        val steps = events.events
            .filter { it.first == ProfileAnalytics.PROFILE_STEP_VIEWED }
            .map { it.second["step_id"] }
        assertEquals(listOf<Any?>("media_video", "media_voice"), steps)
        events.events
            .filter { it.first == ProfileAnalytics.PROFILE_STEP_VIEWED }
            .forEach { assertEquals(3, it.second["step_index"]) }
    }

    @Test
    fun `media_screen_viewed names both previewed prompts and the source`() =
        runTest(dispatcher) {
            repo.snapshot = MediaSnapshot(
                items = emptyList(),
                previewVideoId = "made_me_smile",
                previewVoiceId = "song_makes_me_move",
                previewSource = MediaPreviewSource.Ranked,
            )
            val vm = build()
            vm.arrived()
            advanceUntilIdle()

            val e = events.only(ProfileAnalytics.MEDIA_SCREEN_VIEWED)
            assertEquals("profile_media", e["screen_id"])
            assertEquals(false, e["has_video"])
            assertEquals(false, e["has_voice"])
            // The ranking MOVES, so a view that does not record what it showed cannot be
            // attributed afterwards and a drop in recording rate cannot be told from a bad prompt.
            assertEquals("made_me_smile", e["preview_video_prompt_id"])
            assertEquals("song_makes_me_move", e["preview_voice_prompt_id"])
            assertEquals("ranked", e["preview_source"])
            // It describes the SCREEN, not a medium -- the one exception to the type rule.
            assertFalse(e.containsKey("type"))
        }

    @Test
    fun `media_screen_viewed fires again on the return from an accepted take`() =
        runTest(dispatcher) {
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            assertEquals(1, events.count(ProfileAnalytics.MEDIA_SCREEN_VIEWED))

            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            vm.acceptTake()
            advanceUntilIdle()

            assertEquals(2, events.count(ProfileAnalytics.MEDIA_SCREEN_VIEWED))
            val e = events.last(ProfileAnalytics.MEDIA_SCREEN_VIEWED)
            assertEquals(true, e["has_video"])
            assertEquals(false, e["has_voice"])
        }

    // ── the prompt list ──────────────────────────────────────────────────────

    @Test
    fun `opening the list carries the medium, the entry point and whether one exists`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            val e = events.only(ProfileAnalytics.MEDIA_PROMPT_LIST_OPENED)
            assertEquals("voice", e["type"])
            assertEquals("see_the_prompts", e["entry_point"])
            assertEquals(false, e["has_existing"])
            assertEquals("profile_media", e["screen_id"])
        }

    @Test
    fun `entry_point is intent and has_existing is state, and they are not the same field`() =
        runTest(dispatcher) {
            // The registry note is explicit: entry_point is "never inferred from whether an
            // artefact exists". Retake over an EMPTY card is a real case -- discarding a take and
            // reopening the list -- and it must still read `retake`.
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.Retake)
            val e = events.only(ProfileAnalytics.MEDIA_PROMPT_LIST_OPENED)
            assertEquals("retake", e["entry_point"])
            assertEquals(false, e["has_existing"])
        }

    @Test
    fun `no row tap ever emits a selection`() = runTest(dispatcher) {
        // Rows are radio-select. A tap is a considered look, not a choice.
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.pickPrompt("best_weather")
        vm.pickPrompt("made_me_smile")
        assertFalse(events.has(ProfileAnalytics.MEDIA_PROMPT_SELECTED))
    }

    @Test
    fun `the commit CTA emits the selection, with position and the rows tried before it`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt("best_weather")
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()

            val e = events.only(ProfileAnalytics.MEDIA_PROMPT_SELECTED)
            assertEquals("video", e["type"])
            // The stable id, never the display string.
            assertEquals(PROMPT, e["media_prompt_id"])
            assertEquals(3, e["position"])
            assertEquals(false, e["is_own_prompt"])
            assertEquals(2, e["selections_before"])
            assertEquals(false, e["was_previewed"])
        }

    @Test
    fun `was_previewed is true only for the prompt that was on the card`() =
        runTest(dispatcher) {
            // THE SINGLE RULE THAT KEEPS THE RANKING HONEST. Nothing published, so the video card
            // previews the section-20 cold start, and choosing it must say so.
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PREVIEWED_VIDEO)
            vm.commitPrompt()
            advanceUntilIdle()
            assertEquals(true, events.only(ProfileAnalytics.MEDIA_PROMPT_SELECTED)["was_previewed"])
        }

    @Test
    fun `own_idea carries is_own_prompt`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(MediaPrompts.OWN_IDEA)
        vm.commitPrompt()
        advanceUntilIdle()
        val e = events.only(ProfileAnalytics.MEDIA_PROMPT_SELECTED)
        assertEquals("own_idea", e["media_prompt_id"])
        assertEquals(true, e["is_own_prompt"])
    }

    @Test
    fun `dismissing carries the canonical method, whether one was picked, and the time`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            clock += 7_400
            vm.dismissPrompts(SheetDismissMethod.Swipe)

            val e = events.only(ProfileAnalytics.MEDIA_PROMPT_LIST_DISMISSED)
            assertEquals("video", e["type"])
            // `dismiss_method`, NOT `method` -- section 23, and section 8's `method` already means
            // phone / apple / google.
            assertEquals("swipe", e["dismiss_method"])
            assertFalse("`method` must never appear on a sheet close", e.containsKey("method"))
            assertEquals(true, e["had_selection"])
            assertEquals(7, e["time_on_sheet_s"])
        }

    @Test
    fun `had_selection is false when the list was read and left`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.dismissPrompts(SheetDismissMethod.Backdrop)
        assertEquals(false, events.only(ProfileAnalytics.MEDIA_PROMPT_LIST_DISMISSED)["had_selection"])
    }

    @Test
    fun `every sheet close value is one of the four canonical ones`() = runTest(dispatcher) {
        for (method in SheetDismissMethod.entries) {
            val vm = build()
            events.clear()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.dismissPrompts(method)
            assertTrue(
                events.only(ProfileAnalytics.MEDIA_PROMPT_LIST_DISMISSED)["dismiss_method"]
                    in listOf("close", "backdrop", "swipe", "system_back"),
            )
        }
    }

    // ── recording ────────────────────────────────────────────────────────────

    @Test
    fun `the recording event is named for its medium and carries the attempt`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()

            assertTrue(events.has(ProfileAnalytics.VOICE_RECORDING_STARTED))
            assertFalse(events.has(ProfileAnalytics.VIDEO_RECORDING_STARTED))
            val e = events.only(ProfileAnalytics.VOICE_RECORDING_STARTED)
            assertEquals("voice", e["type"])
            assertEquals(PROMPT, e["media_prompt_id"])
            assertEquals(1, e["attempt"])
            assertEquals(false, e["is_retake"])
        }

    @Test
    fun `the capture and review screens fire their own screen views`() = runTest(dispatcher) {
        // Otherwise the two most abandonable views in the whole flow are invisible.
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()

        val views = events.events
            .filter { it.first == ProfileAnalytics.SCREEN_VIEWED }
            .map { it.second["screen_id"] to it.second["referrer_screen_id"] }
        assertTrue(views.contains("profile_media_record" to "profile_media"))
        assertTrue(views.contains("profile_media_review" to "profile_media_record"))
    }

    @Test
    fun `the review event carries the stop reason and the take's real length`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            kotlinx.coroutines.delay(6_010)
            vm.stopPressed()
            advanceUntilIdle()

            val e = events.only(ProfileAnalytics.MEDIA_REVIEW_SHOWN)
            assertEquals("video", e["type"])
            assertEquals(PROMPT, e["media_prompt_id"])
            assertEquals(6.0, e["duration_s"])
            assertEquals(1, e["attempt"])
            assertEquals("user_stop", e["stop_reason"])
        }

    @Test
    fun `hitting the cap reports max_length, which is how the cap is judged`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            val e = events.only(ProfileAnalytics.MEDIA_REVIEW_SHOWN)
            assertEquals("max_length", e["stop_reason"])
            assertEquals(10.0, e["duration_s"])
        }

    @Test
    fun `playing on review counts up and the count travels with every play`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            // `runCurrent` after each: the event now fires when the player actually STARTS, not
            // on the tap. That is the whole point of the change -- it used to fire for a play that
            // never happened, because there was no player -- and it means a press has to be given
            // the chance to become a play before it is counted.
            vm.playPressed()
            runCurrent()
            vm.playPressed()
            runCurrent()
            vm.playPressed()
            runCurrent()

            assertEquals(3, events.count(ProfileAnalytics.MEDIA_PREVIEW_PLAYED))
            assertEquals(1, events.first(ProfileAnalytics.MEDIA_PREVIEW_PLAYED)["play_count"])
            assertEquals(3, events.last(ProfileAnalytics.MEDIA_PREVIEW_PLAYED)["play_count"])
            assertEquals("voice", events.last(ProfileAnalytics.MEDIA_PREVIEW_PLAYED)["type"])
        }

    // ── keeping ──────────────────────────────────────────────────────────────

    @Test
    fun `stopping never emits a recorded event and accepting does`() = runTest(dispatcher) {
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        kotlinx.coroutines.delay(5_000)
        vm.stopPressed()
        advanceUntilIdle()
        assertFalse(
            "a take that reached review is not a recording",
            events.has(ProfileAnalytics.VIDEO_PROMPT_RECORDED),
        )

        vm.acceptTake()
        advanceUntilIdle()
        assertTrue(events.has(ProfileAnalytics.VIDEO_PROMPT_RECORDED))
    }

    @Test
    fun `the recorded event carries the retake count and the plays before it`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            vm.retakeFromReview()
            advanceUntilIdle()
            // See above: a press becomes a play asynchronously now.
            vm.playPressed()
            runCurrent()
            vm.playPressed()
            runCurrent()
            vm.acceptTake()
            advanceUntilIdle()

            val e = events.only(ProfileAnalytics.VOICE_PROMPT_RECORDED)
            assertEquals("voice", e["type"])
            assertEquals(PROMPT, e["media_prompt_id"])
            assertEquals(15.0, e["duration_s"])
            // attempt counts from 1, so two attempts is ONE retake.
            assertEquals(1, e["retakes"])
            assertEquals(2, e["plays_before_accept"])
        }

    @Test
    fun `a retake from review says so, and one from a card says the other thing`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            kotlinx.coroutines.delay(4_010)
            vm.stopPressed()
            advanceUntilIdle()
            vm.retakeFromReview()
            advanceUntilIdle()

            val fromReview = events.only(ProfileAnalytics.MEDIA_RETAKEN)
            assertEquals("review", fromReview["from"])
            assertEquals(1, fromReview["attempt"])
            assertEquals(4.0, fromReview["prior_duration_s"])
            assertEquals(false, fromReview["had_video"])

            vm.acceptTake()
            advanceUntilIdle()
            events.clear()
            vm.retakeFromCard(MediaKind.Video)

            val fromCard = events.only(ProfileAnalytics.MEDIA_RETAKEN)
            // `review` is a take not yet kept; `media_card` is an artefact already on the profile.
            // One number for both means neither.
            assertEquals("media_card", fromCard["from"])
            assertEquals(true, fromCard["had_video"])
        }

    @Test
    fun `deleting carries the state BEFORE the deletion and is never a retake`() =
        runTest(dispatcher) {
            val vm = build()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            vm.acceptTake()
            advanceUntilIdle()
            events.clear()

            vm.delete(MediaKind.Video)
            val e = events.only(ProfileAnalytics.MEDIA_DELETED)
            assertEquals("video", e["type"])
            assertEquals(PROMPT, e["media_prompt_id"])
            assertEquals(10.0, e["duration_s"])
            // BEFORE: the user was looking at a filled card when they decided.
            assertEquals(true, e["had_video"])
            assertFalse(
                "deleting is not retaking -- there is no replacement take",
                events.has(ProfileAnalytics.MEDIA_RETAKEN),
            )
        }

    // ── leaving ──────────────────────────────────────────────────────────────

    @Test
    fun `an empty Continue is a skip that the user did not call one`() = runTest(dispatcher) {
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        clock += 31_000
        events.clear()
        vm.continuePressed()

        assertEquals(2, events.count(ProfileAnalytics.PROFILE_STEP_SKIPPED))
        assertEquals(0, events.count(ProfileAnalytics.PROFILE_STEP_COMPLETED))
        val e = events.first(ProfileAnalytics.PROFILE_STEP_SKIPPED)
        assertEquals("media_video", e["step_id"])
        // NEWLY REQUIRED AT v1.4 and missing from the build until this ticket.
        assertEquals("profile_media", e["screen_id"])
        assertEquals(false, e["has_video"])
        assertEquals(false, e["has_voice"])
    }

    @Test
    fun `Continue completes the medium that was recorded and skips the one that was not`() =
        runTest(dispatcher) {
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            vm.acceptTake()
            advanceUntilIdle()
            clock += 45_000
            events.clear()

            vm.continuePressed()
            assertEquals(1, events.count(ProfileAnalytics.PROFILE_STEP_COMPLETED))
            assertEquals(1, events.count(ProfileAnalytics.PROFILE_STEP_SKIPPED))
            assertEquals("media_video", events.only(ProfileAnalytics.PROFILE_STEP_COMPLETED)["step_id"])
            assertEquals(45, events.only(ProfileAnalytics.PROFILE_STEP_COMPLETED)["time_on_step_s"])
            assertEquals("media_voice", events.only(ProfileAnalytics.PROFILE_STEP_SKIPPED)["step_id"])
        }

    @Test
    fun `Continue with nothing fires no validation event, because there is no rule to fail`() =
        runTest(dispatcher) {
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            vm.continuePressed()
            assertFalse(events.has(ProfileAnalytics.FORM_VALIDATION_FAILED))
        }

    @Test
    fun `Skip records a skip for both media whatever is on the cards`() = runTest(dispatcher) {
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        vm.openPrompts(MediaKind.Voice, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        vm.acceptTake()
        advanceUntilIdle()
        events.clear()

        vm.skipPressed()
        assertEquals(2, events.count(ProfileAnalytics.PROFILE_STEP_SKIPPED))
        // The event describes the ACT; has_voice carries the state alongside it.
        assertEquals(true, events.last(ProfileAnalytics.PROFILE_STEP_SKIPPED)["has_voice"])
    }

    // ── the rules that hold across every event ───────────────────────────────

    @Test
    fun `every media event except the screen views carries type`() = runTest(dispatcher) {
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        vm.playPressed()
        vm.acceptTake()
        advanceUntilIdle()
        vm.retakeFromCard(MediaKind.Video)
        vm.dismissPrompts(SheetDismissMethod.Close)
        vm.delete(MediaKind.Video)
        advanceUntilIdle()

        val exempt = setOf(
            // Describe a screen or a step, not a medium.
            ProfileAnalytics.SCREEN_VIEWED,
            ProfileAnalytics.MEDIA_SCREEN_VIEWED,
            ProfileAnalytics.PROFILE_STEP_VIEWED,
            ProfileAnalytics.PROFILE_STEP_COMPLETED,
            ProfileAnalytics.PROFILE_STEP_SKIPPED,
        )
        val offenders = events.events
            .filter { it.first !in exempt && !it.second.containsKey("type") }
            .map { it.first }
        assertEquals("every media event must carry type", emptyList<String>(), offenders)
    }

    @Test
    fun `every event is stamped with the registry version the values came from`() =
        runTest(dispatcher) {
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
            vm.pickPrompt(PROMPT)
            vm.commitPrompt()
            advanceUntilIdle()
            vm.acceptTake()
            advanceUntilIdle()

            assertTrue(events.events.isNotEmpty())
            events.events.forEach { (name, payload) ->
                assertEquals(
                    "$name is unstamped",
                    Stamp.FIELD_REGISTRY_VERSION,
                    payload["field_registry_version"],
                )
                assertTrue("$name has no sensitivity class", payload.containsKey("sensitivity_class"))
            }
        }

    @Test
    fun `no payload ever carries the recording itself`() = runTest(dispatcher) {
        // Media of a user's face and voice is the most sensitive artefact in profile creation and
        // none of it belongs in the analytics pipeline. `duration_s` and the prompt id are the
        // whole payload.
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        vm.acceptTake()
        advanceUntilIdle()

        val forbidden = listOf(
            "path", "file", "uri", "url", "frame", "thumbnail", "transcript",
            "waveform", "bytes", "data", "content",
        )
        events.events.forEach { (name, payload) ->
            payload.keys.forEach { key ->
                forbidden.forEach { bad ->
                    assertFalse("$name carries `$key`", key.lowercase().contains(bad))
                }
            }
        }
    }

    @Test
    fun `no caption event is ever emitted`() = runTest(dispatcher) {
        // `caption_added` and `caption_skipped` stay in the registry so the profile-editing flow
        // cannot invent a second name for the same act. Caption authoring was removed from THIS
        // flow on 16 September 2026 and nothing here may fire them.
        val vm = build()
        vm.arrived()
        advanceUntilIdle()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        vm.acceptTake()
        advanceUntilIdle()
        vm.continuePressed()

        assertFalse(events.names().any { it.startsWith("caption_") })
    }

    @Test
    fun `no event uses the written-prompt registry's key`() = runTest(dispatcher) {
        // `media_prompt_id` IS NOT `prompt_id`. The written profile prompts on screen 07 are a
        // different registry for a different screen; these events carried `prompt_id` until
        // taxonomy 1.4 and were renamed for exactly this collision.
        val vm = build()
        vm.openPrompts(MediaKind.Video, MediaEntryPoint.SeeThePrompts)
        vm.pickPrompt(PROMPT)
        vm.commitPrompt()
        advanceUntilIdle()
        vm.acceptTake()
        advanceUntilIdle()

        events.events.forEach { (name, payload) ->
            assertFalse("$name carries prompt_id", payload.containsKey("prompt_id"))
            assertFalse("$name carries topic_id", payload.containsKey("topic_id"))
        }
    }

    @Test
    fun `the screen emits no ranking event -- that ships with the server job`() =
        runTest(dispatcher) {
            val vm = build()
            vm.arrived()
            advanceUntilIdle()
            assertFalse(events.names().any { it == "media_prompt_ranking_published" })
        }
}
