/*
 * PickRulesTest.kt
 * ShowUp · every rule of Interests, Life and Future that is not a pixel (SHOWUP-174 to SHOWUP-176)
 *
 * Plain functions, no Compose: the cap, the single-select groups, the exclusive answers, list order
 * and the arrival reset. The view model test drives the same rules through the events and the PATCH.
 */
package com.showup.profile

import com.showup.api.generated.model.FlowPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickRulesTest {

    // ── the steps ──────────────────────────────────────────────────────────────

    @Test
    fun `the three steps are 8, 9 and 10 of the bar, with their registry ids`() {
        assertEquals(listOf(8, 9, 10), PickStep.entries.map { it.stepIndex })
        assertEquals(listOf("interests", "life_now", "life_ahead"), PickStep.entries.map { it.stepId })
        assertEquals(
            listOf(FlowPosition.interests, FlowPosition.life_now, FlowPosition.life_ahead),
            PickStep.entries.map { it.position },
        )
        assertEquals(
            listOf("profile_interests", "profile_life_now", "profile_life_ahead"),
            PickStep.entries.map { it.screen.screenId },
        )
        assertEquals(
            listOf("ProfileInterests", "ProfileLifeNow", "ProfileLifeAhead"),
            PickStep.entries.map { it.screen.screenName },
        )
        assertEquals(10, SHARE_STEPS_TOTAL)
    }

    // ── Interests: the lists ───────────────────────────────────────────────────

    @Test
    fun `How I live is the ticket's six groups, labels and options in order`() {
        assertEquals(
            listOf("Activity level", "Diet", "Alcohol", "Smoking", "Rhythm", "Social battery"),
            LifestyleField.entries.map { it.label },
        )
        assertEquals(
            listOf("Couch-lover", "Moderately active", "Active", "Sport is my life"),
            LifestyleField.Activity.options.map { it.label },
        )
        assertEquals(listOf("No", "Sometimes", "Yes"), LifestyleField.Smoking.options.map { it.label })
        assertEquals(listOf("Cozy at home", "Balanced", "Always out"), LifestyleField.Social.options.map { it.label })
    }

    @Test
    fun `How I live stores the registry values, with alcohol and smoking at class 2`() {
        assertEquals(
            listOf("couch_lover", "moderately_active", "active", "sport_is_my_life"),
            LifestyleField.Activity.options.map { it.value },
        )
        assertEquals(listOf("early_bird", "night_owl"), LifestyleField.Rhythm.options.map { it.value })
        assertEquals(
            mapOf("activity" to 1, "diet" to 1, "alcohol" to 2, "smoking" to 2, "rhythm" to 0, "social" to 0),
            LifestyleField.entries.associate { it.fieldId to it.sensitivityClass },
        )
    }

    @Test
    fun `What I'm into is the reference's six groups and 51 chips, in order`() {
        assertEquals(
            listOf("Move", "Food & Drink", "Culture", "Cozy at home", "Going out", "Travel style"),
            InterestSection.entries.map { it.label },
        )
        assertEquals(listOf("move", "food", "culture", "home", "out", "travel"), InterestSection.entries.map { it.sectionId })
        assertEquals(listOf(12, 9, 8, 6, 7, 9), InterestSection.entries.map { it.options.size })
        assertEquals(51, INTEREST_ORDER.size)
        assertEquals(51, INTEREST_ORDER.toSet().size)
        assertEquals("Hyrox/CrossFit", InterestSection.Move.options[2].label)
        assertEquals("hyrox_crossfit", InterestSection.Move.options[2].value)
        assertEquals("street_food_markets", InterestSection.Food.options[4].value)
    }

    @Test
    fun `every group is collapsible, showing four, and reveals the rest`() {
        assertTrue(InterestSection.entries.all { it.collapsible })
        assertEquals(listOf(8, 5, 4, 2, 3, 5), InterestSection.entries.map { it.hiddenCount })
        assertEquals(4, visibleOptions(InterestSection.Move, expanded = false).size)
        assertEquals(12, visibleOptions(InterestSection.Move, expanded = true).size)
        assertEquals(listOf("Gym", "Running", "Hyrox/CrossFit", "Yoga/Pilates"), visibleOptions(InterestSection.Move, false).map { it.label })
    }

    // ── Interests: the taps ────────────────────────────────────────────────────

    @Test
    fun `a How I live tap moves the answer, and the chosen chip clears it`() {
        assertEquals("active", pickLifestyle(null, "active"))
        assertEquals("vegan", pickLifestyle("vegetarian", "vegan"))
        assertEquals(null, pickLifestyle("vegan", "vegan"))
    }

    @Test
    fun `a What I'm into tap picks and unpicks with no limit below ten`() {
        assertEquals(InterestTap.Selected(setOf("gym")), tapInterest(emptySet(), "gym"))
        assertEquals(InterestTap.Deselected(emptySet()), tapInterest(setOf("gym"), "gym"))
    }

    @Test
    fun `at ten an unpicked chip does nothing, and a picked one still unpicks`() {
        val ten = INTEREST_ORDER.take(10).toSet()
        assertEquals(InterestTap.CapReached, tapInterest(ten, INTEREST_ORDER[10]))
        assertEquals(InterestTap.Deselected(ten - INTEREST_ORDER[3]), tapInterest(ten, INTEREST_ORDER[3]))
        assertEquals(InterestTap.Selected(INTEREST_ORDER.take(10).toSet()), tapInterest(INTEREST_ORDER.take(9).toSet(), INTEREST_ORDER[9]))
    }

    @Test
    fun `the counter reads N picked M left, and 10 of 10 at the cap`() {
        assertEquals("0 picked · 10 left", interestsCounterLabel(0))
        assertEquals("6 picked · 4 left", interestsCounterLabel(6))
        assertEquals("9 picked · 1 left", interestsCounterLabel(9))
        assertEquals("10 of 10 picked", interestsCounterLabel(10))
        assertEquals(0f, interestsFill(0))
        assertEquals(0.6f, interestsFill(6), 0.0001f)
        assertEquals(1f, interestsFill(10))
    }

    @Test
    fun `interests save in list order whatever order they were tapped`() {
        assertEquals(listOf("gym", "reading", "cruises"), interestsInListOrder(setOf("cruises", "gym", "reading")))
    }

    @Test
    fun `How I live reports in list order, unanswered groups sending nothing`() {
        val draft = InterestsDraft(lifestyle = mapOf("social" to "balanced", "activity" to "active"))
        assertEquals(
            listOf(LifestyleField.Activity to "active", LifestyleField.Social to "balanced"),
            answeredLifestyle(draft),
        )
    }

    @Test
    fun `anything means any How I live answer or any interest`() {
        assertFalse(InterestsDraft().anything)
        assertFalse(InterestsDraft(expanded = setOf("move")).anything)
        assertTrue(InterestsDraft(lifestyle = mapOf("diet" to "vegan")).anything)
        assertTrue(InterestsDraft(interests = setOf("gym")).anything)
    }

    @Test
    fun `arrival pre-fills what this build knows, every group collapsed`() {
        val saved = SavedDetails(
            lifestyle = mapOf("activity" to "active", "diet" to "carnivore"),
            interests = listOf("gym", "skydiving", "wine"),
        )
        val draft = interestsOnArrival(saved)
        assertEquals(mapOf("activity" to "active"), draft.lifestyle)
        assertEquals(setOf("gym", "wine"), draft.interests)
        assertTrue(draft.expanded.isEmpty())
    }

    // ── Life and Future: the lists ─────────────────────────────────────────────

    @Test
    fun `Life asks Partnership, Family, Living with the ticket's labels and icons`() {
        val life = PickStep.LifeNow.questions
        assertEquals(listOf("Partnership situation", "Family situation", "Living situation"), life.map { it.label })
        assertEquals(listOf("partnership", "family", "living"), life.map { it.questionId })
        assertEquals(listOf(2, 1, 1), life.map { it.sensitivityClass })
        assertEquals(
            listOf("No children", "I have children", "Single parent", "Co-parenting", "Empty nester"),
            LifeQuestion.Family.options.map { it.label },
        )
        assertEquals(
            listOf("living_alone", "living_with_roommates", "living_with_family", "living_with_children"),
            LifeQuestion.Living.options.map { it.value },
        )
    }

    @Test
    fun `Future asks Family first, then Partnership, then Living`() {
        val future = PickStep.LifeAhead.questions
        assertEquals(listOf("Family vision", "Partnership vision", "Living style vision"), future.map { it.label })
        assertEquals(listOf("family_ahead", "partnership_ahead", "living_ahead"), future.map { it.questionId })
        assertEquals(listOf(1, 2, 1), future.map { it.sensitivityClass })
        assertEquals(
            listOf("I want kids", "I don't want kids", "I want to find out together"),
            LifeQuestion.FamilyAhead.options.map { it.label },
        )
        assertEquals(listOf("Stay local", "Expat life", "Move abroad", "I want to find out together"), LifeQuestion.LivingAhead.options.map { it.label })
        assertTrue(PickStep.Interests.questions.isEmpty())
    }

    // ── toggleLifePick ─────────────────────────────────────────────────────────

    @Test
    fun `Life pills combine, and a picked one unpicks`() {
        var picked = toggleLifePick(LifeQuestion.Partnership, emptySet(), "single")
        picked = toggleLifePick(LifeQuestion.Partnership, picked, "divorced")
        assertEquals(setOf("single", "divorced"), picked)
        assertEquals(setOf("divorced"), toggleLifePick(LifeQuestion.Partnership, picked, "single"))
    }

    @Test
    fun `No children replaces every Family pick, and any other pick clears it`() {
        val parent = setOf("has_children", "co_parenting", "single_parent")
        assertEquals(setOf("no_children"), toggleLifePick(LifeQuestion.Family, parent, "no_children"))
        assertEquals(setOf("empty_nester"), toggleLifePick(LifeQuestion.Family, setOf("no_children"), "empty_nester"))
        assertEquals(emptySet<String>(), toggleLifePick(LifeQuestion.Family, setOf("no_children"), "no_children"))
    }

    @Test
    fun `Partnership and Living on Life have no exclusive answer`() {
        assertTrue(LifeQuestion.Partnership.exclusive.isEmpty())
        assertTrue(LifeQuestion.Living.exclusive.isEmpty())
        val all = LifeQuestion.Living.options.map { it.value }
            .fold(emptySet<String>()) { p, v -> toggleLifePick(LifeQuestion.Living, p, v) }
        assertEquals(4, all.size)
    }

    @Test
    fun `Family vision holds one answer at most, and the picked one clears`() {
        var picked = toggleLifePick(LifeQuestion.FamilyAhead, emptySet(), "want_kids")
        picked = toggleLifePick(LifeQuestion.FamilyAhead, picked, "dont_want_kids")
        assertEquals(setOf("dont_want_kids"), picked)
        picked = toggleLifePick(LifeQuestion.FamilyAhead, picked, "find_out_together")
        assertEquals(setOf("find_out_together"), picked)
        assertEquals(emptySet<String>(), toggleLifePick(LifeQuestion.FamilyAhead, picked, "find_out_together"))
    }

    @Test
    fun `find out together stands alone in Partnership and Living vision, the rest combine`() {
        val both = setOf("monogamous_relationship", "open_relationship")
        assertEquals(both, toggleLifePick(LifeQuestion.PartnershipAhead, setOf("monogamous_relationship"), "open_relationship"))
        assertEquals(setOf("find_out_together"), toggleLifePick(LifeQuestion.PartnershipAhead, both, "find_out_together"))
        assertEquals(setOf("expat_life"), toggleLifePick(LifeQuestion.LivingAhead, setOf("find_out_together"), "expat_life"))
        assertEquals(setOf("stay_local", "move_abroad"), toggleLifePick(LifeQuestion.LivingAhead, setOf("stay_local"), "move_abroad"))
    }

    @Test
    fun `a tap touches only its own question`() {
        val draft = LifeDraft(mapOf("partnership" to setOf("single"), "family" to setOf("no_children")))
        val after = draft.with(LifeQuestion.Living, toggleLifePick(LifeQuestion.Living, draft.picked(LifeQuestion.Living), "living_alone"))
        assertEquals(setOf("single"), after.picked(LifeQuestion.Partnership))
        assertEquals(setOf("no_children"), after.picked(LifeQuestion.Family))
        assertEquals(setOf("living_alone"), after.picked(LifeQuestion.Living))
    }

    // ── list order, value_bucketed, arrival ────────────────────────────────────

    @Test
    fun `answers save and report in list order, comma-joined with no spaces`() {
        assertEquals(listOf("single", "divorced"), lifeInListOrder(LifeQuestion.Partnership, setOf("divorced", "single")))
        assertEquals("single,divorced", lifeBucketed(LifeQuestion.Partnership, setOf("divorced", "single")))
        assertEquals("stay_local,move_abroad", lifeBucketed(LifeQuestion.LivingAhead, setOf("move_abroad", "stay_local")))
        assertEquals("", lifeBucketed(LifeQuestion.Living, emptySet()))
    }

    @Test
    fun `anything on a life step means a pick in one of its own questions`() {
        val lifeOnly = LifeDraft(mapOf("family" to setOf("no_children")))
        assertTrue(lifeOnly.anything(PickStep.LifeNow))
        assertFalse(lifeOnly.anything(PickStep.LifeAhead))
        assertFalse(LifeDraft(mapOf("family" to emptySet())).anything(PickStep.LifeNow))
    }

    @Test
    fun `arrival resets only the step's own questions, from what this build knows`() {
        val current = LifeDraft(mapOf("partnership" to setOf("single"), "family_ahead" to setOf("want_kids")))
        val saved = SavedDetails(life = mapOf("family_ahead" to listOf("dont_want_kids"), "living_ahead" to listOf("mars")))
        val draft = lifeOnArrival(PickStep.LifeAhead, current, saved)
        assertEquals(setOf("single"), draft.picked(LifeQuestion.Partnership))
        assertEquals(setOf("dont_want_kids"), draft.picked(LifeQuestion.FamilyAhead))
        assertTrue(draft.picked(LifeQuestion.LivingAhead).isEmpty())
        assertTrue(draft.picked(LifeQuestion.PartnershipAhead).isEmpty())
    }

    @Test
    fun `a saved set that breaks its own exclusive rule arrives as one the screen could draw`() {
        val saved = SavedDetails(life = mapOf("family" to listOf("single_parent", "no_children")))
        assertEquals(setOf("no_children"), lifeOnArrival(PickStep.LifeNow, LifeDraft(), saved).picked(LifeQuestion.Family))
    }
}
