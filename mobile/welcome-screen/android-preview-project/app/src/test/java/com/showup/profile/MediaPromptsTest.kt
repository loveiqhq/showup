/*
 * MediaPromptsTest.kt
 * ShowUp · the eleven, the escape hatch, and the two waveforms (SHOWUP-161)
 *
 * The prompts are QUOTED here rather than derived from the list they check. A test that read the
 * strings back out of `MediaPrompts.ALL` would pass on any strings at all, which is the failure
 * mode the ticket warns about twice -- "do not retype them, keep the order" -- and the only way to
 * catch a retype is to have the ticket's own words sitting next to the code's.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPromptsTest {

    /** Verbatim from the reference file's `MEDIA_PROMPTS`, in its order. */
    private val quoted = listOf(
        "The little everyday thing that instantly puts me in a good mood",
        "What my ideal sunny morning looks like",
        "A sound that always makes me feel relaxed",
        "My go-to comfort snack when having a good day",
        "How my friends would describe my energy in three words",
        "The kind of weather that brings out the best in me",
        "A song that always makes me want to move",
        "What I usually look like when I'm relaxed and happy",
        "The best simple pleasure in my daily routine",
        "Something cute or funny that made me smile this week",
        "My favorite way to spend an easy 30 minutes outside",
    )

    @Test
    fun `the eleven read exactly as the reference writes them, in order`() {
        assertEquals(quoted, MediaPrompts.ALL.filter { !it.isOwn }.map { it.display })
    }

    @Test
    fun `the escape hatch is last and reads as the ticket quotes it`() {
        val last = MediaPrompts.ALL.last()
        assertEquals("Something else — my own idea", last.display)
        assertTrue(last.isOwn)
        assertEquals(MediaPrompts.OWN_IDEA, last.id)
        assertEquals(1, MediaPrompts.ALL.count { it.isOwn })
    }

    @Test
    fun `the ids are the section-20 registry's, not invented`() {
        assertEquals(
            listOf(
                "everyday_good_mood", "ideal_sunny_morning", "relaxing_sound", "comfort_snack",
                "friends_three_words", "best_weather", "song_makes_me_move", "relaxed_and_happy",
                "simple_pleasure", "made_me_smile", "easy_30_outside", "own_idea",
            ),
            MediaPrompts.ALL.map { it.id },
        )
    }

    @Test
    fun `positions are dense, from zero, and match the index`() {
        MediaPrompts.ALL.forEachIndexed { index, prompt -> assertEquals(index, prompt.position) }
    }

    @Test
    fun `the eyebrow's count is derived and excludes the escape hatch`() {
        // `One of 11 prompts` has to stay true if a twelfth is ever added, and a hard-coded 11
        // beside a list of twelve entries is the kind of drift no test notices.
        assertEquals(11, MediaPrompts.COUNT)
        assertEquals(12, MediaPrompts.ALL.size)
    }

    @Test
    fun `the cold starts differ per medium and are both real prompts`() {
        assertEquals("relaxed_and_happy", MediaPrompts.coldStart(MediaKind.Video).id)
        assertEquals("relaxing_sound", MediaPrompts.coldStart(MediaKind.Voice).id)
        // One you would show and one you would play, so the two empty cards never read as
        // duplicates of each other.
        assertNotEquals(
            MediaPrompts.coldStart(MediaKind.Video),
            MediaPrompts.coldStart(MediaKind.Voice),
        )
    }

    @Test
    fun `an unknown id resolves to nothing rather than to something plausible`() {
        assertNull(MediaPrompts.byId("first_date_usually"))
        assertNull(MediaPrompts.byId(null))
        // `first_date_usually` is a WRITTEN-prompt topic id from section 17. It must not resolve
        // here: these are two registries for two screens, and the rename at taxonomy 1.4 happened
        // because they had been confused once already.
    }

    @Test
    fun `the step ids and index come from section 2`() {
        assertEquals("media_video", MediaKind.Video.stepId)
        assertEquals("media_voice", MediaKind.Voice.stepId)
        assertEquals(3, MediaKind.STEP_INDEX)
        assertEquals("video", MediaKind.Video.trackingValue)
        assertEquals("voice", MediaKind.Voice.trackingValue)
    }
}

/**
 * The two waveforms.
 *
 * "48 deterministic bars whose heights do not change between renders" is an acceptance criterion,
 * and determinism is the part a test can actually hold: Compose recomposes for reasons that have
 * nothing to do with audio, and a waveform re-rolled on recomposition makes a finished recording
 * appear to wobble while the user is looking at it.
 */
class MediaWaveformTest {

    @Test
    fun `both waveforms are 48 bars`() {
        assertEquals(48, MediaWaveform.BARS)
        assertEquals(48, MediaWaveform.recorded.size)
        assertEquals(48, MediaWaveform.live.size)
    }

    @Test
    fun `every bar is within the drawable range`() {
        (MediaWaveform.recorded + MediaWaveform.live).forEach {
            assertTrue("bar out of range: $it", it in 0f..1f)
        }
    }

    @Test
    fun `the values do not change between reads`() {
        assertEquals(MediaWaveform.recorded, MediaWaveform.recorded.toList())
        assertEquals(MediaWaveform.live, MediaWaveform.live.toList())
    }

    @Test
    fun `the live waveform peaks in the middle and fades to the edges`() {
        // Centre-anchored, "so it reads as 'live mic' rather than 'scrubbing'". The envelope is
        // what makes that true, and a port that dropped it would still be deterministic and still
        // be wrong -- which is why this is asserted rather than left to the eye.
        val middle = MediaWaveform.live[24]
        assertTrue(middle > MediaWaveform.live.first())
        assertTrue(middle > MediaWaveform.live.last())
    }

    @Test
    fun `the two waveforms are not the same curve`() {
        assertNotEquals(MediaWaveform.recorded, MediaWaveform.live)
    }
}

/** The readouts under every take. */
class MediaDurationFormatTest {

    @Test
    fun `lengths read as the design writes them`() {
        assertEquals("0:09", formatTakeLength(9_400))
        assertEquals("0:14", formatTakeLength(14_100))
        assertEquals("0:00", formatTakeLength(0))
        assertEquals("0:10", formatTakeLength(10_000))
    }

    @Test
    fun `seconds are truncated, never rounded`() {
        // A 9.6-second take displayed as 0:10 on a 10-second cap reads as though it hit the cap
        // when it did not, which is the one thing this screen's measurement must not confuse.
        assertEquals("0:09", formatTakeLength(9_900))
    }

    @Test
    fun `a negative length cannot be produced`() {
        assertEquals("0:00", formatTakeLength(-500))
        assertEquals(0.0, durationSeconds(-500), 0.0001)
    }

    @Test
    fun `duration_s keeps one decimal, because whole seconds hide the cap`() {
        assertEquals(9.4, durationSeconds(9_400), 0.0001)
        assertEquals(10.0, durationSeconds(10_000), 0.0001)
        assertEquals(9.9, durationSeconds(9_999), 0.0001)
    }
}
