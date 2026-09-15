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
        assertEquals("first_date", topicFor("first_date")?.id)
        assertEquals("On a first date, I usually…", topicText("first_date"))
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
        assertEquals(2, suggestionsFor(used = listOf("first_date"), count = 2).size)
    }

    @Test
    fun `a used topic is never suggested again`() {
        val used = listOf("first_date", "weird_habit")
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

    // ── prompt_id ───────────────────────────────────────────────────────────

    @Test
    fun `prompt_id is the topic id plus the slot, numbered from one`() {
        // §5's own prose and its own example: "prompt_id is topic_id plus the slot it occupies --
        // match_me_if_you__slot2 -- so a topic moved between slots stays traceable".
        assertEquals("first_date__slot1", promptId("first_date", 0))
        assertEquals("hot_take__slot3", promptId("hot_take", 2))
    }

    @Test
    fun `no prompt payload can carry an answer`() {
        val answer = "Talk about anything real."
        val (_, payload) = ProfileAnalytics.promptAnswered(
            promptId = promptId("first_date", 0),
            charCount = answer.length,
            atCharLimit = false,
        )
        val rendered = payload.values.joinToString(" ")
        assertFalse("the answer reached a payload: $rendered", rendered.contains("anything real"))
        assertEquals(answer.length, payload["char_count"])
    }

    // ── the state the screen is a function of ───────────────────────────────

    @Test
    fun `a draft belongs to a topic, so dismissing and reopening restores it`() {
        val state = PromptsState(
            sheet = PromptSheet.Write("first_date"),
            drafts = mapOf("first_date" to "half a sentence"),
        )
        val dismissed = state.copy(sheet = null)
        assertEquals("half a sentence", dismissed.draftFor("first_date"))
        val reopened = dismissed.copy(sheet = PromptSheet.Write("first_date"))
        assertEquals("half a sentence", reopened.draftFor("first_date"))
        // And a different topic starts empty.
        assertEquals("", reopened.draftFor("hot_take"))
    }

    @Test
    fun `one prompt is enough to continue and none is not`() {
        assertFalse(PromptsState().canContinue)
        assertTrue(PromptsState(prompts = listOf(SavedPrompt("first_date", "x"))).canContinue)
    }

    @Test
    fun `the saved state survives a round trip through its encoding`() {
        val state = PromptsState(
            prompts = listOf(SavedPrompt("first_date", "an answer")),
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
