/*
 * PromptsTrackingTest.kt
 * ShowUp · the fourteen rows this screen has to produce, and the six it must never (SHOWUP-158)
 *
 * The prompts screen exists to answer seven questions -- which topic did they reach for first, did
 * they try to leave with nothing written, was browse-all needed, did they abandon the sheet, what
 * did they save, did they edit, and how many did they hold on Continue. Every one of those is a
 * row in the registry, and a row that is subtly wrong is worse than a missing one: a missing row
 * is a gap somebody notices, and a wrong one is a chart somebody believes.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS FILE IS LONG, AND WHY THE ASSERTIONS LOOK PEDANTIC
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Registry 1.4.2 (16 September 2026) made three changes that a reasonable implementation gets
 * wrong by default:
 *
 *   1. `selection_index` COUNTS PER VISIT TO THE STEP, NOT PER SHEET. The natural place to keep a
 *      counter is next to the sheet that uses it, and that is exactly wrong: reset it when a sheet
 *      closes and "which topic did they reach for first" silently becomes "which topic did they
 *      reach for first in this sheet", which is a question nobody asked.
 *   2. `prompt_topic_selected` MUST NOT FIRE ON AN EDIT. Reopening a saved prompt is not a fresh
 *      choice of topic; firing it there inflates the topic-demand chart with re-edits.
 *   3. `dismiss_method` IS ONE CANONICAL SET -- close, backdrop, swipe, system_back. This screen
 *      used to say `scrim` and `back`, and those spellings are now bugs.
 *
 * And the whole family changed: the four v1.0 `prompt_*` names this screen used to emit are marked
 * SUPERSEDED -- DO NOT FIRE. [noFamilyFNameIsEverEmitted] is the test that stops them coming back.
 */
package com.showup.profile

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class PromptsTrackingTest {

    /** Saves whatever it is given, so the tracking rather than the transport is what is measured. */
    private class FakeRepo : PromptsRepository(
        api = com.showup.api.ShowUpApi(
            baseUrl = "http://127.0.0.1:1/",
            tokens = com.showup.api.InMemoryTokenStore(),
        ),
        offline = null,
    ) {
        var fail = false
        override suspend fun list(): List<SavedPrompt>? = null
        override suspend fun save(topicId: String, answer: String): SavePromptResult =
            if (fail) SavePromptResult.Failed(null) else SavePromptResult.Saved(
                SavedPrompt(topicId, answer),
            )
    }

    private class Recorder : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }

        fun names() = events.map { it.first }
        fun count(name: String) = events.count { it.first == name }
        fun only(name: String) = events.single { it.first == name }.second
        fun all(name: String) = events.filter { it.first == name }.map { it.second }
        fun clear() = events.clear()
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var analytics: Recorder
    private lateinit var vm: PromptsViewModel

    /** A clock the test drives, so `time_on_*_s` is an assertion rather than a guess. */
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo()
        analytics = Recorder()
        clock = 1_000_000L
        vm = PromptsViewModel(repo, SavedStateHandle(), analytics) { clock }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Types an answer and saves it, letting the request finish.
     *
     * [topicId] is not used -- the sheet already knows which topic it is on -- and is named at
     * every call site anyway, because a test that reads `write("An answer.")` twice in a row does
     * not say which prompt it is writing.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun write(topicId: String, answer: String) {
        vm.draftChanged(answer)
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
    }

    // ── selection_index ─────────────────────────────────────────────────────

    @Test
    fun `selection_index is 1-based and counts across the whole visit to the step`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 0)
            vm.dismissSheet(SheetDismissMethod.Close)
            vm.openTopics()
            vm.pickTopic("hot_take", position = 14)

            val selections = analytics.all("prompt_topic_selected")
            assertEquals(2, selections.size)
            assertEquals(1, selections[0]["selection_index"])
            // NOT 1 AGAIN. The sheet closed in between, and the counter does not care: the
            // registry says it counts per visit to the STEP. Reset it per sheet and the answer to
            // "which topic did they reach for first" becomes an answer to a different question.
            assertEquals(2, selections[1]["selection_index"])
        }

    @Test
    fun `an edit does not increment selection_index and does not fire a selection at all`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 0)
            write("first_date_usually", "An answer.")
            analytics.clear()

            vm.editPrompt("first_date_usually")
            // THE registry change of 16 September 2026. An edit is not a fresh choice of topic.
            assertEquals(0, analytics.count("prompt_topic_selected"))
            assertEquals(1, analytics.count("prompt_editor_opened"))
            assertEquals(true, analytics.only("prompt_editor_opened")["is_edit"])
            assertEquals("edit", analytics.only("prompt_editor_opened")["entry_point"])

            // And the count it did not touch is still visible on the next real selection.
            vm.dismissSheet(SheetDismissMethod.Close)
            vm.writeSuggestion("weird_habit", position = 0)
            assertEquals(2, analytics.only("prompt_topic_selected")["selection_index"])
        }

    // ── entry_point, the measurement the revision exists to produce ──────────

    @Test
    fun `entry_point comes from the control that was tapped, not from whether a sheet was open`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 1)
            assertEquals("suggestion", analytics.only("prompt_topic_selected")["entry_point"])
            assertEquals(1, analytics.only("prompt_topic_selected")["position"])
            assertEquals("dating_me", analytics.only("prompt_topic_selected")["topic_group"])
            analytics.clear()

            vm.dismissSheet(SheetDismissMethod.Close)
            vm.openTopics()
            vm.pickTopic("talk_for_hours", position = 10)
            // The sheet was open in BOTH cases, which is exactly why it cannot be inferred.
            assertEquals("browse", analytics.only("prompt_topic_selected")["entry_point"])
            assertEquals(10, analytics.only("prompt_topic_selected")["position"])
            assertEquals("opinions", analytics.only("prompt_topic_selected")["topic_group"])
        }

    @Test
    fun `the entry point that opened the sheet is the one the save reports`() =
        runTest(dispatcher) {
            vm.openTopics()
            vm.pickTopic("hot_take", position = 14)
            write("hot_take", "A hot take.")
            // A save that started in the browse sheet stays separable from one that started at a
            // card, which is the whole point of carrying it on the sheet rather than re-deriving
            // it when Save is pressed.
            assertEquals("browse", analytics.only("prompt_saved")["entry_point"])
        }

    // ── the browse sheet's funnel closes ────────────────────────────────────

    @Test
    fun `picking a topic is a selection and never also a dismissal`() = runTest(dispatcher) {
        vm.openTopics()
        vm.pickTopic("hot_take", position = 14)
        // opened = selected + dismissed. One act, one event: the sheet resolving into the write
        // sheet is the sheet succeeding, not the user abandoning it.
        assertEquals(1, analytics.count("prompt_topic_list_opened"))
        assertEquals(1, analytics.count("prompt_topic_selected"))
        assertEquals(0, analytics.count("prompt_topic_list_dismissed"))
    }

    @Test
    fun `cancelling the browse sheet reports how, and how long it was up`() =
        runTest(dispatcher) {
            vm.openTopics()
            clock += 7_400
            vm.dismissSheet(SheetDismissMethod.Swipe)

            val payload = analytics.only("prompt_topic_list_dismissed")
            assertEquals("swipe", payload["dismiss_method"])
            assertEquals(0, payload["used_count"])
            // Whole seconds, floored. 7.4 is 7.
            assertEquals(7, payload["time_on_sheet_s"])
            assertEquals("profile_prompts", payload["screen_id"])
        }

    @Test
    fun `all four dismiss methods reach the payload as the canonical spelling`() =
        runTest(dispatcher) {
            val seen = mutableListOf<Any?>()
            SheetDismissMethod.entries.forEach { method ->
                vm.openTopics()
                vm.dismissSheet(method)
                seen += analytics.all("prompt_topic_list_dismissed").last()["dismiss_method"]
            }
            assertEquals(listOf("close", "backdrop", "swipe", "system_back"), seen)
            // The two spellings 1.4.2 replaced must not survive anywhere in the payloads.
            val rendered = analytics.events.joinToString(" ") { it.second.values.joinToString(" ") }
            listOf("scrim", "cancel").forEach {
                assertFalse("a pre-1.4.2 dismiss value survived: $rendered", rendered.contains(it))
            }
            // `back` only as part of `system_back`, never alone.
            assertFalse(rendered.split(" ").contains("back"))
        }

    // ── the write sheet's abandonment ───────────────────────────────────────

    @Test
    fun `abandoning the write sheet reports the draft's bucket and never its text`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 0)
            vm.draftChanged("half a sentence about something real")
            vm.dismissSheet(SheetDismissMethod.Backdrop)

            val payload = analytics.only("prompt_editor_dismissed")
            assertEquals(true, payload["had_draft"])
            assertEquals("1_40", payload["draft_length_bucket"])
            assertEquals("backdrop", payload["dismiss_method"])
            assertEquals("suggestion", payload["entry_point"])
            val rendered = payload.values.joinToString(" ")
            assertFalse("the draft leaked: $rendered", rendered.contains("something real"))
        }

    @Test
    fun `an untouched sheet reports no draft`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.dismissSheet(SheetDismissMethod.Close)
        val payload = analytics.only("prompt_editor_dismissed")
        // `had_draft` is what separates "changed their mind" from "could not finish".
        assertEquals(false, payload["had_draft"])
        assertEquals("0", payload["draft_length_bucket"])
    }

    @Test
    fun `whitespace alone is not a draft`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.draftChanged("   ")
        vm.dismissSheet(SheetDismissMethod.Close)
        // Whitespace-only counts as empty everywhere else on this screen; it counts as empty here.
        assertEquals(false, analytics.only("prompt_editor_dismissed")["had_draft"])
    }

    // ── saving ──────────────────────────────────────────────────────────────

    @Test
    fun `a save reports the bucket, the count after it, and never the answer`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 0)
            write("first_date_usually", "x".repeat(95))

            val payload = analytics.only("prompt_saved")
            assertEquals("first_date_usually", payload["topic_id"])
            assertEquals("dating_me", payload["topic_group"])
            assertEquals(false, payload["is_edit"])
            assertEquals("81_120", payload["length_bucket"])
            assertEquals(1, payload["prompt_count"])
        }

    @Test
    fun `an edit is honest about not consuming a slot`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        write("first_date_usually", "First answer.")
        vm.editPrompt("first_date_usually")
        write("first_date_usually", "A better answer.")

        val saves = analytics.all("prompt_saved")
        assertEquals(2, saves.size)
        assertEquals(false, saves[0]["is_edit"])
        assertEquals(true, saves[1]["is_edit"])
        // STILL ONE. An edit overwrites; a count that went to 2 here would be a profile the user
        // does not have.
        assertEquals(1, saves[1]["prompt_count"])
    }

    @Test
    fun `the minimum is reported once, on the first save, with what got them there`() =
        runTest(dispatcher) {
            vm.writeSuggestion("first_date_usually", position = 0)
            write("first_date_usually", "An answer.")

            val met = analytics.only("prompts_minimum_met")
            assertEquals(1, met["count"])
            assertEquals("first_date_usually", met["topic_id"])
            assertEquals("suggestion", met["entry_point"])

            vm.openTopics()
            vm.pickTopic("hot_take", position = 14)
            write("hot_take", "Another.")
            // Once. The second save does not re-cross anything.
            assertEquals(1, analytics.count("prompts_minimum_met"))
        }

    @Test
    fun `a failed save reports nothing`() = runTest(dispatcher) {
        repo.fail = true
        vm.writeSuggestion("first_date_usually", position = 0)
        write("first_date_usually", "An answer.")
        // The server does not have it, so nothing may say it does.
        assertEquals(0, analytics.count("prompt_saved"))
        assertEquals(0, analytics.count("prompts_minimum_met"))
    }

    @Test
    fun `an empty Save nudges and records nothing`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        analytics.clear()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        // State H is a nudge, not a validation failure. The one refusal this screen registers is
        // Continue; a second would double-count the same user against a rule that exists once.
        assertTrue(analytics.events.isEmpty())
        assertTrue(vm.state.value.nudge)
    }

    // ── the cap ─────────────────────────────────────────────────────────────

    @Test
    fun `the cap is reported once per editor session, not per keystroke`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS))
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS))
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS))
        assertEquals(1, analytics.count("prompt_char_limit_reached"))
        assertEquals("first_date_usually", analytics.only("prompt_char_limit_reached")["topic_id"])
    }

    @Test
    fun `reopening a sheet starts a new editor session for the cap`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS))
        vm.dismissSheet(SheetDismissMethod.Close)
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS))
        // Per SESSION, so twice -- two sittings at the cap are two facts.
        assertEquals(2, analytics.count("prompt_char_limit_reached"))
    }

    @Test
    fun `stopping short of the cap reports nothing`() = runTest(dispatcher) {
        vm.writeSuggestion("first_date_usually", position = 0)
        vm.draftChanged("x".repeat(PROMPT_MAX_CHARS - 1))
        assertEquals(0, analytics.count("prompt_char_limit_reached"))
    }

    // ── the example ─────────────────────────────────────────────────────────

    @Test
    fun `dismissing the example names the topic it was for`() = runTest(dispatcher) {
        vm.writeSuggestion("weird_habit", position = 1)
        vm.hideExample()
        assertEquals("weird_habit", analytics.only("prompt_example_dismissed")["topic_id"])
    }

    // ── arriving and leaving ────────────────────────────────────────────────

    @Test
    fun `arrival reports the screen and the step, with the step index from the registry`() =
        runTest(dispatcher) {
            vm.arrived(referrer = ProfileScreen.Photos)

            val viewed = analytics.only("screen_viewed")
            assertEquals("profile_prompts", viewed["screen_id"])
            // 11 says ProfilePrompts. It said "Profile - Prompts" here until 16 September 2026,
            // which is a label nobody searching the analytics tool would have found.
            assertEquals("ProfilePrompts", viewed["screen_name"])
            assertEquals("profile_photos", viewed["referrer_screen_id"])

            val step = analytics.only("profile_step_viewed")
            assertEquals("prompts", step["step_id"])
            // Photos 1, prompts 2, media 3 -- read from RealYouStep, never a literal.
            assertEquals(2, step["step_index"])
        }

    @Test
    fun `Continue at zero prompts reports the refusal, once per press`() = runTest(dispatcher) {
        vm.arrived()
        analytics.clear()

        assertFalse(vm.continuePressed())
        assertFalse(vm.continuePressed())

        // Continue is never disabled, so this is the ONLY record that the user tried to leave.
        assertEquals(2, analytics.count("form_validation_failed"))
        val payload = analytics.all("form_validation_failed").first()
        assertEquals("prompts", payload["field_id"])
        // 1.4.2 added this as the sibling of photos_below_minimum. It used to be
        // `nothing_selected`, which belongs to a chooser where nothing was ticked.
        assertEquals("prompts_below_minimum", payload["rule"])
        assertEquals("profile_prompts", payload["screen_id"])
        assertEquals("prompts", payload["step_id"])
        assertEquals(0, analytics.count("profile_step_completed"))
    }

    @Test
    fun `Continue with prompts reports the true count and the time on the step`() =
        runTest(dispatcher) {
            vm.arrived()
            vm.writeSuggestion("first_date_usually", position = 0)
            write("first_date_usually", "An answer.")
            vm.openTopics()
            vm.pickTopic("hot_take", position = 14)
            write("hot_take", "Another.")
            clock += 42_000
            analytics.clear()

            assertTrue(vm.continuePressed())
            val payload = analytics.only("profile_step_completed")
            assertEquals("prompts", payload["step_id"])
            assertEquals(42, payload["time_on_step_s"])
            // The number the funnel reads: 1 to 3, at the moment Continue was ACCEPTED.
            assertEquals(2, payload["prompt_count"])
            assertEquals(0, analytics.count("form_validation_failed"))
        }

    @Test
    fun `prompt_count is omitted rather than zeroed on a step that has no prompts`() {
        // "OPTIONAL, SCREEN-SCOPED... OMIT on every other step." An empty property is worse than
        // an absent one, because a zero is a number somebody will chart.
        val (_, payload) = ProfileAnalytics.realYouStepCompleted(RealYouStep.Photos, 12)
        assertNull(payload["prompt_count"])
        assertEquals(12, payload["time_on_step_s"])
    }

    // ── the rules that hold across every row ────────────────────────────────

    @Test
    fun noFamilyFNameIsEverEmitted() = runTest(dispatcher) {
        vm.arrived()
        vm.openTopics()
        vm.pickTopic("first_date_usually", position = 0)
        vm.draftChanged("An answer that is long enough to be interesting.")
        vm.hideExample()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()
        vm.editPrompt("first_date_usually")
        vm.dismissSheet(SheetDismissMethod.SystemBack)
        vm.continuePressed()

        // SUPERSEDED -- DO NOT FIRE. The registry keeps these four rows, emptied of their
        // payloads, precisely so the names resolve to a notice rather than being re-implemented.
        listOf(
            "prompt_topic_picker_opened",
            "prompt_answered",
            "prompt_edited",
            "prompt_removed",
        ).forEach { name ->
            assertEquals("family F name fired: $name", 0, analytics.count(name))
        }
        // And the walk above really did produce the family E set, so the assertion is not vacuous.
        assertTrue(analytics.names().containsAll(listOf(
            "screen_viewed", "profile_step_viewed", "prompt_topic_list_opened",
            "prompt_topic_selected", "prompt_editor_opened", "prompt_example_dismissed",
            "prompt_saved", "prompts_minimum_met", "prompt_editor_dismissed",
            "profile_step_completed",
        )))
    }

    @Test
    fun `every payload is stamped with the registry the values came from`() =
        runTest(dispatcher) {
            vm.arrived()
            vm.writeSuggestion("first_date_usually", position = 0)
            write("first_date_usually", "An answer.")
            assertTrue(analytics.events.isNotEmpty())
            analytics.events.forEach { (name, payload) ->
                assertEquals(
                    "$name was stamped with the wrong registry",
                    "1.4.2",
                    payload["field_registry_version"],
                )
            }
        }

    @Test
    fun `no event anywhere on this screen carries answer text or draft text`() =
        runTest(dispatcher) {
            val secret = "the specific sentence a person wrote about themselves"
            vm.arrived()
            vm.openTopics()
            vm.pickTopic("first_date_usually", position = 0)
            vm.draftChanged(secret)
            vm.save()
            dispatcher.scheduler.advanceUntilIdle()
            vm.editPrompt("first_date_usually")
            vm.draftChanged(secret)
            vm.dismissSheet(SheetDismissMethod.Backdrop)
            vm.continuePressed()

            val rendered = analytics.events.joinToString(" ") { (n, p) ->
                n + " " + p.entries.joinToString(" ") { "${it.key}=${it.value}" }
            }
            listOf("specific sentence", "wrote about themselves", secret).forEach {
                assertFalse("text reached the pipeline: $rendered", rendered.contains(it))
            }
        }
}
