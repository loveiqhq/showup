/*
 * PromptTopicsTest.kt
 * ShowUp · the fifteen topics, and the rules about answering them (SHOWUP-158)
 *
 * The content, because it was copied wholesale from the reference and a silent edit to any of it
 * orphans saved answers; and the four rules the conversion pass is about, because each of them
 * replaces something an earlier mock did and each is a one-character change away from doing the
 * old thing again.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTopicsTest {

    // ── the content ─────────────────────────────────────────────────────────

    @Test
    fun `fifteen topics, in three groups of five`() {
        assertEquals(3, TOPIC_GROUPS.size)
        TOPIC_GROUPS.forEach { assertEquals(5, it.topics.size) }
        assertEquals(15, PROMPT_TOPICS.size)
        // The control the screen offers says "Browse all 15 topics"; a sixteenth topic would make
        // that string a lie, which is why the copy and the count are asserted together.
        assertTrue(PromptsCopy.BROWSE_ALL.contains(PROMPT_TOPICS.size.toString()))
    }

    @Test
    fun `the groups are in the reference's order and carry its labels`() {
        assertEquals(
            listOf("Dating me", "Me in real life", "Opinions & obsessions"),
            TOPIC_GROUPS.map { it.label },
        )
    }

    @Test
    fun `every topic id is unique and stable`() {
        val ids = PROMPT_TOPICS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // ASSIGNED, NOT DERIVED. A slug computed from the display string would change with a copy
        // edit and orphan every answer saved under the old one. These two are spot-checked because
        // a derived slug would produce something quite different.
        assertEquals("first_date_usually", topicFor("first_date_usually")?.id)
        assertEquals("On a first date, I usually…", topicText("first_date_usually"))
    }

    @Test
    fun `every topic has a worked example and every example is short`() {
        PROMPT_TOPICS.forEach { topic ->
            assertNotNull("no example for ${topic.id}", PROMPT_EXAMPLES[topic.id])
        }
        // "Written short on purpose: every example is under 120 characters, so the example itself
        // says this length is fine. An example that filled the box would undo the floor framing."
        PROMPT_EXAMPLES.forEach { (id, example) ->
            assertTrue(
                "example for $id is ${example.length} characters, over the 120 the ticket sets",
                example.length < 120,
            )
        }
    }

    @Test
    fun `the three suggestions are one from each group`() {
        val groupOf = { id: String -> TOPIC_GROUPS.indexOfFirst { g -> g.topics.any { it.id == id } } }
        assertEquals(listOf(0, 1, 2), SUGGESTED_TOPIC_IDS.map(groupOf))
    }

    // ── suggestions ─────────────────────────────────────────────────────────

    @Test
    fun `three are offered at zero saved and two after that`() {
        assertEquals(3, suggestionsFor(used = emptyList(), count = 3).size)
        assertEquals(2, suggestionsFor(used = listOf("first_date_usually"), count = 2).size)
    }

    @Test
    fun `a used topic is never suggested again`() {
        val used = listOf("first_date_usually", "weird_habit")
        val offered = suggestionsFor(used, count = 2).map { it.id }
        assertTrue(offered.none { it in used })
    }

    @Test
    fun `with all three suggestions used it falls through to group order`() {
        // Otherwise the "Add another · optional" block would be an empty section label, which is
        // worse than a topic the user did not expect.
        val offered = suggestionsFor(SUGGESTED_TOPIC_IDS, count = 2)
        assertEquals(2, offered.size)
        assertEquals("out_the_door", offered.first().id)
    }

    // ── the cap, the floor and the counter ──────────────────────────────────

    @Test
    fun `the cap is 160 and slicing is silent`() {
        assertEquals(160, PROMPT_MAX_CHARS)
        val long = "x".repeat(400)
        assertEquals(160, cappedAnswer(long).length)
        // Nothing to assert about an alert, because there is none -- a paste over the limit is
        // truncated and not mentioned.
    }

    @Test
    fun `the at-cap sample is actually at the cap`() {
        // State G is "at the cap", and a sample one character short is state F wearing its name.
        // The fit sweep and the evidence screenshots both carried a shortened paraphrase once, and
        // the image filed as G showed a grey 130/160 -- the calm state -- rather than the amber it
        // exists to demonstrate.
        assertEquals(PROMPT_MAX_CHARS, PROMPT_SAMPLE_AT_CAP.length)
        // And the mid-draft has to be under the counter's threshold, or state F shows a counter.
        assertTrue(PROMPT_SAMPLE_MID.length < PROMPT_COUNTER_FROM)
    }

    @Test
    fun `the counter starts at one hundred, not at zero`() {
        // The single change this revision is most about: below 100 a numeral is not information,
        // it is a target.
        assertEquals(100, PROMPT_COUNTER_FROM)
        assertTrue(PROMPT_COUNTER_FROM in 1 until PROMPT_MAX_CHARS)
    }

    @Test
    fun `whitespace-only counts as empty`() {
        assertTrue(promptAnswerIsEmpty(""))
        assertTrue(promptAnswerIsEmpty("   "))
        assertTrue(promptAnswerIsEmpty("\n\t "))
        assertFalse(promptAnswerIsEmpty("a"))
    }

    @Test
    fun `one prompt is enough and three is the most`() {
        assertEquals(1, PROMPTS_REQUIRED)
        assertEquals(3, PROMPTS_MAX)
    }

    // ── the counter copy ────────────────────────────────────────────────────

    @Test
    fun `the counter says enough to continue only once something is saved`() {
        assertEquals("0/3 prompts", PromptsCopy.counter(0))
        assertEquals("1/3 prompts · enough to continue", PromptsCopy.counter(1))
        assertEquals("3/3 prompts · enough to continue", PromptsCopy.counter(3))
    }

    // ── the ids are the registry's ──────────────────────────────────────────

    @Test
    fun `every topic id is the one the registry dictates`() {
        // enums.json 17, verbatim and in order. THE POINT OF THE TEST is that these ids are not
        // ours to choose: they are what the analytics warehouse joins on and what the
        // `profile_prompts` rows store, so a drift here orphans data on both sides at once. Six
        // of these were invented against registry 1.3.0, which had no 17, and six were wrong.
        assertEquals(
            listOf(
                "first_date_usually", "out_the_door", "ideal_30_min", "cross_town_for",
                "spontaneous_plan",
                "thirty_min_feels", "in_real_life_more", "unsexy_truth", "sunday_energy",
                "weird_habit",
                "talk_for_hours", "know_too_much", "hill_to_die_on", "green_flag", "hot_take",
            ),
            PROMPT_TOPICS.map { it.id },
        )
    }

    @Test
    fun `every group id is the one the registry dictates`() {
        assertEquals(listOf("dating_me", "real_life", "opinions"), TOPIC_GROUPS.map { it.id })
    }

    @Test
    fun `a topic reports the group it is actually listed under`() {
        assertEquals("dating_me", topicGroupFor("first_date_usually"))
        assertEquals("real_life", topicGroupFor("weird_habit"))
        assertEquals("opinions", topicGroupFor("hot_take"))
        // An id nobody registered reports nothing rather than guessing a group.
        assertEquals("", topicGroupFor("not_a_topic"))
    }

    @Test
    fun `every example is written against a real topic id`() {
        // The examples are keyed by id, so a renamed topic with an unrenamed example key is a
        // silent fallback to the generic line rather than an error.
        val ids = PROMPT_TOPICS.map { it.id }.toSet()
        assertEquals(emptySet<String>(), PROMPT_EXAMPLES.keys - ids)
        assertEquals(emptySet<String>(), ids - PROMPT_EXAMPLES.keys)
    }

    @Test
    fun `the three suggestions are real topics, one from each group`() {
        val groups = SUGGESTED_TOPIC_IDS.map(::topicGroupFor)
        assertEquals(listOf("dating_me", "real_life", "opinions"), groups)
    }

    // ── length buckets, which exist so the text never travels ────────────────

    @Test
    fun `a length falls in the bucket 19 says it does`() {
        assertEquals("0", promptLengthBucket(0))
        assertEquals("1_40", promptLengthBucket(1))
        assertEquals("1_40", promptLengthBucket(40))
        assertEquals("41_80", promptLengthBucket(41))
        assertEquals("41_80", promptLengthBucket(80))
        assertEquals("81_120", promptLengthBucket(81))
        assertEquals("81_120", promptLengthBucket(120))
        assertEquals("121_160", promptLengthBucket(121))
        // The cap is 160 and the top bucket is open-ended, so a value that somehow got past the
        // slice still lands somewhere rather than falling out of the set.
        assertEquals("121_160", promptLengthBucket(PROMPT_MAX_CHARS))
        assertEquals("121_160", promptLengthBucket(400))
    }

    @Test
    fun `no prompt payload can carry an answer or a draft`() {
        // STRUCTURAL, NOT A CONVENTION. Every builder takes a LENGTH, so there is no signature
        // that could carry the text even if a caller wanted it to. This asserts the outcome.
        val answer = "Talk about anything real, not the safe thing."
        val payloads = listOf(
            ProfileAnalytics.promptSaved(
                topicId = "first_date_usually",
                isEdit = false,
                answerLength = answer.length,
                promptCount = 1,
            ).second,
            ProfileAnalytics.promptEditorDismissed(
                topicId = "first_date_usually",
                isEdit = false,
                draftLength = answer.length,
                method = SheetDismissMethod.Backdrop,
            ).second,
        )
        payloads.forEach { payload ->
            val rendered = payload.values.joinToString(" ")
            assertFalse("text reached a payload: $rendered", rendered.contains("anything real"))
            assertFalse("text reached a payload: $rendered", rendered.contains("safe thing"))
        }
        assertEquals("41_80", payloads[0]["length_bucket"])
        assertEquals("41_80", payloads[1]["draft_length_bucket"])
    }

    // ── the state the screen is a function of ───────────────────────────────

    @Test
    fun `a draft belongs to a topic, so dismissing and reopening restores it`() {
        val state = PromptsState(
            sheet = PromptSheet.Write("first_date_usually"),
            drafts = mapOf("first_date_usually" to "half a sentence"),
        )
        val dismissed = state.copy(sheet = null)
        assertEquals("half a sentence", dismissed.draftFor("first_date_usually"))
        val reopened = dismissed.copy(sheet = PromptSheet.Write("first_date_usually"))
        assertEquals("half a sentence", reopened.draftFor("first_date_usually"))
        // And a different topic starts empty.
        assertEquals("", reopened.draftFor("hot_take"))
    }

    @Test
    fun `one prompt is enough to continue and none is not`() {
        assertFalse(PromptsState().canContinue)
        assertTrue(PromptsState(prompts = listOf(SavedPrompt("first_date_usually", "x"))).canContinue)
    }

    @Test
    fun `the saved state survives a round trip through its encoding`() {
        val state = PromptsState(
            prompts = listOf(SavedPrompt("first_date_usually", "an answer")),
            sheet = PromptSheet.Write("hot_take", editing = true),
            drafts = mapOf("hot_take" to "half written"),
            nudge = true,
            exampleHiddenFor = "hot_take",
        )
        assertEquals(state, PromptsState.decode(PromptsState.encode(state)))
    }

    @Test
    fun `an empty state round-trips too, and rubbish decodes to one`() {
        // The case a saver usually gets wrong: no sheet, no drafts, nothing hidden.
        assertEquals(PromptsState(), PromptsState.decode(PromptsState.encode(PromptsState())))
        // And a stored value from an older build is an empty screen rather than a crash.
        assertEquals(PromptsState(), PromptsState.decode("not json at all"))
        assertEquals(PromptsState(), PromptsState.decode(""))
    }
}
