/*
 * ProfileDetailsRulesTest.kt
 * ShowUp · every rule in "Share some details" that is not a pixel (SHOWUP-167 to SHOWUP-173)
 *
 * Plain functions over plain data, so no Compose, no device and no network. The view model is
 * tested separately; this is the table it reads.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDetailsRulesTest {

    // ── the table ─────────────────────────────────────────────────────────────

    @Test
    fun `seven steps in walk order, indexed 1 to 7 as section 2 gives them`() {
        assertEquals(
            listOf("height", "gender", "orientation", "dating_language", "education", "religion", "politics"),
            DetailStep.entries.map { it.stepId },
        )
        assertEquals((1..7).toList(), DetailStep.entries.map { it.stepIndex })
    }

    @Test
    fun `the bar is ten segments, from one place`() {
        assertEquals(10, SHARE_STEPS_TOTAL)
    }

    @Test
    fun `only gender and orientation are mandatory`() {
        assertEquals(
            setOf(DetailStep.Gender, DetailStep.Orientation),
            DetailStep.entries.filter { it.mandatory }.toSet(),
        )
    }

    @Test
    fun `only the skippable single-selects clear on a second tap`() {
        assertEquals(
            setOf(DetailStep.Education, DetailStep.Religion, DetailStep.Politics),
            DetailStep.entries.filter { it.clearsOnReselect }.toSet(),
        )
    }

    @Test
    fun `every option list is the ticket's labels in the ticket's order`() {
        assertEquals(listOf("Woman", "Man", "Non-binary", "Other"), DetailStep.Gender.options.map { it.label })
        assertEquals(
            listOf("Straight", "Gay", "Lesbian", "Bisexual", "Pansexual", "Other"),
            DetailStep.Orientation.options.map { it.label },
        )
        assertEquals(
            listOf("German", "English", "Spanish", "Italian", "French", "Turkish", "Russian", "Arabic"),
            DetailStep.DatingLanguage.options.map { it.label },
        )
        assertEquals(
            listOf("A-Levels / Abitur", "Apprenticeship", "University degree", "PhD"),
            DetailStep.Education.options.map { it.label },
        )
        assertEquals(
            listOf(
                "Protestant", "Catholic", "Orthodox", "Muslim", "Jewish", "Buddhist", "Hindu",
                "Atheist", "Spiritual / other",
            ),
            DetailStep.Religion.options.map { it.label },
        )
        assertEquals(
            listOf("Left", "Mid-left", "Middle", "Mid-right", "Right", "Conservative", "Libertarian", "Apolitical"),
            DetailStep.Politics.options.map { it.label },
        )
    }

    @Test
    fun `what is stored is the section 1 value, never the label`() {
        assertEquals("non_binary", DetailStep.Gender.options[2].value)
        assertEquals("a_levels_abitur", DetailStep.Education.options[0].value)
        assertEquals("spiritual_other", DetailStep.Religion.options.last().value)
        assertEquals("mid_left", DetailStep.Politics.options[1].value)
        // Conservative AFTER right -- the 1.4.15 reorder, keyed on value.
        assertEquals(
            listOf("right", "conservative"),
            DetailStep.Politics.options.map { it.value }.subList(4, 6),
        )
    }

    @Test
    fun `classes follow section 1`() {
        assertEquals(0, DetailStep.Height.sensitivityClass)
        assertEquals(2, DetailStep.Gender.sensitivityClass)
        assertEquals(2, DetailStep.Orientation.sensitivityClass)
        assertEquals(1, DetailStep.DatingLanguage.sensitivityClass)
        assertEquals(1, DetailStep.Education.sensitivityClass)
        assertEquals(2, DetailStep.Religion.sensitivityClass)
        assertEquals(2, DetailStep.Politics.sensitivityClass)
    }

    @Test
    fun `next and previous walk the steps, with nothing past either end`() {
        assertNull(DetailStep.Height.previous)
        assertEquals(DetailStep.Gender, DetailStep.Height.next)
        assertEquals(DetailStep.Religion, DetailStep.Politics.previous)
        assertNull(DetailStep.Politics.next)
    }

    // ── height ────────────────────────────────────────────────────────────────

    @Test
    fun `the field keeps digits only, at most three, paste included`() {
        assertEquals("175", filterHeightInput("175"))
        assertEquals("175", filterHeightInput("1a7.5cm"))
        assertEquals("180", filterHeightInput("1802"))
        assertEquals("", filterHeightInput("abc"))
        assertEquals("165", filterHeightInput(" 165 cm "))
    }

    @Test
    fun `valid is a whole number from 120 to 230 inclusive`() {
        assertNull(parseHeight("119"))
        assertEquals(120, parseHeight("120"))
        assertEquals(230, parseHeight("230"))
        assertNull(parseHeight("231"))
        assertNull(parseHeight(""))
    }

    @Test
    fun `empty is required_missing and out of range is impossible`() {
        assertEquals("required_missing", heightRefusal(""))
        assertEquals("impossible", heightRefusal("99"))
        assertEquals("impossible", heightRefusal("231"))
        assertNull(heightRefusal("175"))
    }

    @Test
    fun `buckets follow section 1, tens at the ends and fives in the middle`() {
        assertEquals("<150", heightBucket(120))
        assertEquals("<150", heightBucket(149))
        assertEquals("150_159", heightBucket(150))
        assertEquals("150_159", heightBucket(159))
        assertEquals("160_164", heightBucket(160))
        assertEquals("165_169", heightBucket(169))
        assertEquals("175_179", heightBucket(175))
        assertEquals("185_189", heightBucket(189))
        assertEquals("190_199", heightBucket(190))
        assertEquals("190_199", heightBucket(199))
        assertEquals("200+", heightBucket(200))
        assertEquals("200+", heightBucket(230))
    }

    // ── taps ─────────────────────────────────────────────────────────────────

    @Test
    fun `a radio never clears on gender and orientation`() {
        assertEquals("woman", pickSingle(DetailStep.Gender, "woman", "woman"))
        assertEquals("gay", pickSingle(DetailStep.Orientation, "gay", "gay"))
    }

    @Test
    fun `a second tap clears on education, religion and politics`() {
        assertNull(pickSingle(DetailStep.Education, "phd", "phd"))
        assertNull(pickSingle(DetailStep.Religion, "muslim", "muslim"))
        assertNull(pickSingle(DetailStep.Politics, "middle", "middle"))
    }

    @Test
    fun `a different row always moves the selection`() {
        assertEquals("man", pickSingle(DetailStep.Gender, "woman", "man"))
        assertEquals("phd", pickSingle(DetailStep.Education, "apprenticeship", "phd"))
        assertEquals("left", pickSingle(DetailStep.Politics, null, "left"))
    }

    @Test
    fun `languages toggle with no limit`() {
        var set = emptySet<String>()
        DetailStep.DatingLanguage.options.forEach { set = toggleMulti(set, it.value) }
        assertEquals(8, set.size)
        set = toggleMulti(set, "german")
        assertFalse("german" in set)
    }

    @Test
    fun `languages are saved and reported in LIST order, not tap order`() {
        val ticked = toggleMulti(toggleMulti(emptySet(), "spanish"), "german")
        assertEquals(listOf("german", "spanish"), inListOrder(DetailStep.DatingLanguage, ticked))
        assertEquals("german,spanish", languagesBucketed(inListOrder(DetailStep.DatingLanguage, ticked)))
    }

    // ── what Continue resolves to ─────────────────────────────────────────────

    @Test
    fun `height refuses empty and out of range, and answers a valid value`() {
        assertEquals(
            DetailContinue.Refused("required_missing"),
            resolveContinue(DetailStep.Height, DetailDraft()),
        )
        assertEquals(
            DetailContinue.Refused("impossible"),
            resolveContinue(DetailStep.Height, DetailDraft(heightText = "300")),
        )
        assertEquals(DetailContinue.Answer, resolveContinue(DetailStep.Height, DetailDraft(heightText = "175")))
    }

    @Test
    fun `gender and orientation refuse an empty Continue -- they are mandatory`() {
        assertEquals(
            DetailContinue.Refused("required_missing"),
            resolveContinue(DetailStep.Gender, DetailDraft()),
        )
        assertEquals(
            DetailContinue.Refused("required_missing"),
            resolveContinue(DetailStep.Orientation, DetailDraft()),
        )
    }

    @Test
    fun `the four skippable lists treat an empty Continue as a skip, never a refusal`() {
        listOf(DetailStep.DatingLanguage, DetailStep.Education, DetailStep.Religion, DetailStep.Politics)
            .forEach { assertEquals("$it", DetailContinue.Skip, resolveContinue(it, DetailDraft())) }
    }

    @Test
    fun `a selection is an answer on every list`() {
        assertEquals(DetailContinue.Answer, resolveContinue(DetailStep.Gender, DetailDraft(gender = "other")))
        assertEquals(
            DetailContinue.Answer,
            resolveContinue(DetailStep.DatingLanguage, DetailDraft(languages = setOf("arabic"))),
        )
        assertEquals(DetailContinue.Answer, resolveContinue(DetailStep.Politics, DetailDraft(politics = "apolitical")))
    }

    // ── arrival and visibility ────────────────────────────────────────────────

    @Test
    fun `arrival shows the saved value and visibility for that step only`() {
        val saved = SavedDetails(heightCm = 181, gender = "man", hiddenFields = setOf("gender", "age"))
        val current = DetailDraft(orientation = "gay", hidden = setOf("orientation"))
        val draft = draftOnArrival(DetailStep.Gender, current, saved)
        assertEquals("man", draft.gender)
        assertTrue(draft.isHidden(DetailStep.Gender))
        // Orientation's part is untouched by gender's arrival.
        assertEquals("gay", draft.orientation)
        assertTrue(draft.isHidden(DetailStep.Orientation))
    }

    @Test
    fun `arrival discards an unsaved selection -- back throws it away`() {
        val draft = draftOnArrival(DetailStep.Religion, DetailDraft(religion = "hindu"), SavedDetails())
        assertNull(draft.religion)
    }

    @Test
    fun `height arrives as its saved number, or empty`() {
        assertEquals("181", draftOnArrival(DetailStep.Height, DetailDraft(), SavedDetails(heightCm = 181)).heightText)
        assertEquals("", draftOnArrival(DetailStep.Height, DetailDraft(heightText = "17"), SavedDetails()).heightText)
    }

    @Test
    fun `a saved value this build does not know selects nothing`() {
        val draft = draftOnArrival(DetailStep.Gender, DetailDraft(), SavedDetails(gender = "agender"))
        assertNull(draft.gender)
        val langs = draftOnArrival(
            DetailStep.DatingLanguage, DetailDraft(), SavedDetails(datingLanguages = listOf("german", "klingon")),
        )
        assertEquals(setOf("german"), langs.languages)
    }

    @Test
    fun `hidden_fields is a replace, so the age chosen on the date step survives`() {
        assertEquals(
            listOf("age", "height"),
            hiddenFieldsToSend(DetailStep.Height, setOf("age"), hideThis = true),
        )
        assertEquals(
            listOf("age"),
            hiddenFieldsToSend(DetailStep.Height, setOf("age", "height"), hideThis = false),
        )
        assertEquals(
            listOf("age", "gender"),
            hiddenFieldsToSend(DetailStep.Religion, setOf("age", "gender"), hideThis = false),
        )
    }
}
