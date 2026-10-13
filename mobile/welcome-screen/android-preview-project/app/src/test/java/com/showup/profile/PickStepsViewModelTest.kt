/*
 * PickStepsViewModelTest.kt
 * ShowUp · Interests, Life and Future, driven with no device (SHOWUP-174 to SHOWUP-176)
 *
 * Every row of the three tracking tables and the order they fire in, every event that must NOT
 * fire, what Continue saves and what Skip does not, "persist before navigating", and the arrival
 * rules: back discards, a resume pre-fills, a recomposition is not an arrival -- and that no
 * Continue ever saves over answers the screen has not shown (a slow or failed first read).
 */
package com.showup.profile

import androidx.lifecycle.SavedStateHandle
import com.showup.analytics.RecordingAnalytics
import com.showup.api.generated.model.FlowPosition
import com.showup.api.generated.model.UpsertProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class PickStepsViewModelTest {

    /** A store that remembers every body, applies it the way the server does, and can fail. */
    private class FakeStore(
        var held: SavedDetails = SavedDetails(),
        var saves: Boolean = true,
        var reads: Boolean = true,
    ) : ProfileDetailsStore {
        val bodies = mutableListOf<UpsertProfileDto>()
        val readDelays = ArrayDeque<Long>()
        val saveDelays = ArrayDeque<Long>()
        var loads = 0

        override suspend fun load(): SavedDetails? {
            loads++
            val snapshot = held
            val ok = reads
            readDelays.removeFirstOrNull()?.let { delay(it) }
            return snapshot.takeIf { ok }
        }

        override suspend fun save(body: UpsertProfileDto): SavedDetails? {
            bodies += body
            saveDelays.removeFirstOrNull()?.let { delay(it) }
            if (!saves) return null
            val lifestyle = held.lifestyle.toMutableMap()
            body.activity?.let { lifestyle["activity"] = it.value }
            body.diet?.let { lifestyle["diet"] = it.value }
            body.alcohol?.let { lifestyle["alcohol"] = it.value }
            body.smoking?.let { lifestyle["smoking"] = it.value }
            body.rhythm?.let { lifestyle["rhythm"] = it.value }
            body.social?.let { lifestyle["social"] = it.value }
            val life = held.life.toMutableMap()
            body.partnership?.let { life["partnership"] = it.map { v -> v.value } }
            body.family?.let { life["family"] = it.map { v -> v.value } }
            body.living?.let { life["living"] = it.map { v -> v.value } }
            body.familyAhead?.let { life["family_ahead"] = it.map { v -> v.value } }
            body.partnershipAhead?.let { life["partnership_ahead"] = it.map { v -> v.value } }
            body.livingAhead?.let { life["living_ahead"] = it.map { v -> v.value } }
            held = held.copy(
                lifestyle = lifestyle,
                interests = body.interests?.map { it.value } ?: held.interests,
                life = life,
            )
            return held
        }
    }

    private class Positions : FlowPositionSink {
        val reported = mutableListOf<FlowPosition>()
        override suspend fun report(position: FlowPosition): Boolean {
            reported += position
            return true
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeStore
    private lateinit var positions: Positions
    private lateinit var analytics: RecordingAnalytics
    private lateinit var handle: SavedStateHandle
    private var clock = 0L
    private lateinit var model: PickStepsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
        positions = Positions()
        analytics = RecordingAnalytics()
        handle = SavedStateHandle()
        clock = 1_000L
        model = PickStepsViewModel(store, positions, handle, analytics) { clock }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.arrive(step: PickStep, referrer: ProfileScreen? = null) {
        model.arrived(step, referrer)
        advanceUntilIdle()
    }

    private val state get() = model.state.value

    // ── arriving ───────────────────────────────────────────────────────────────

    @Test
    fun `arriving on Interests reports the screen with its referrer and step 8`() = runTest(dispatcher) {
        arrive(PickStep.Interests, ProfileScreen.Politics)
        assertEquals(listOf("screen_viewed", "profile_step_viewed"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("profile_interests", view["screen_id"])
        assertEquals("ProfileInterests", view["screen_name"])
        assertEquals("profile_politics", view["referrer_screen_id"])
        val step = analytics.of("profile_step_viewed").single().properties
        assertEquals("interests", step["step_id"])
        assertEquals(8, step["step_index"])
        assertEquals(0, step["sensitivity_class"])
        assertEquals("1.4.18", step["field_registry_version"])
    }

    @Test
    fun `Life and Future report steps 9 and 10, Future with Life as referrer`() = runTest(dispatcher) {
        arrive(PickStep.LifeNow, ProfileScreen.Interests)
        arrive(PickStep.LifeAhead, ProfileScreen.LifeNow)
        val views = analytics.of("screen_viewed").map { it.properties }
        assertEquals(listOf("profile_life_now", "profile_life_ahead"), views.map { it["screen_id"] })
        assertEquals(listOf("ProfileLifeNow", "ProfileLifeAhead"), views.map { it["screen_name"] })
        assertEquals(listOf("profile_interests", "profile_life_now"), views.map { it["referrer_screen_id"] })
        val steps = analytics.of("profile_step_viewed").map { it.properties }
        assertEquals(listOf("life_now", "life_ahead"), steps.map { it["step_id"] })
        assertEquals(listOf(9, 10), steps.map { it["step_index"] })
    }

    @Test
    fun `a recomposition of the step on screen is not a second arrival`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Diet, "vegan")
        arrive(PickStep.Interests)
        assertEquals(1, analytics.of("screen_viewed").size)
        assertEquals(mapOf("diet" to "vegan"), state.interests.lifestyle)
    }

    @Test
    fun `a resume pre-fills the saved answers, every group collapsed`() = runTest(dispatcher) {
        store.held = SavedDetails(lifestyle = mapOf("rhythm" to "night_owl"), interests = listOf("wine", "gym"))
        arrive(PickStep.Interests)
        assertEquals(mapOf("rhythm" to "night_owl"), state.interests.lifestyle)
        assertEquals(setOf("wine", "gym"), state.interests.interests)
        assertTrue(state.interests.expanded.isEmpty())
        assertTrue(state.prefill > 0)
    }

    @Test
    fun `a slow read never overwrites a pick made before it lands`() = runTest(dispatcher) {
        store.held = SavedDetails(lifestyle = mapOf("diet" to "vegan"))
        store.readDelays += 5_000L
        model.arrived(PickStep.Interests, null)
        model.lifestyleTapped(LifestyleField.Diet, "everything")
        advanceUntilIdle()
        assertEquals(mapOf("diet" to "everything"), state.interests.lifestyle)
    }

    // ── a slow or failed first read never costs a saved answer ─────────────────
    //
    // Found by the 13 October 2026 review: a touched screen ignored a late read entirely, so one tap
    // before it landed and Continue saved `[]` over Life's untouched questions, or a one-tag list
    // over a saved ten.

    @Test
    fun `a late read fills every How I live group and interest the user has not touched`() = runTest(dispatcher) {
        store.held = SavedDetails(
            lifestyle = mapOf("diet" to "vegan", "rhythm" to "night_owl"),
            interests = listOf("wine", "gym"),
        )
        store.readDelays += 5_000L
        model.arrived(PickStep.Interests, null)
        model.lifestyleTapped(LifestyleField.Diet, "everything")
        model.interestTapped("cooking")
        advanceUntilIdle()
        assertEquals(mapOf("diet" to "everything", "rhythm" to "night_owl"), state.interests.lifestyle)
        assertEquals(setOf("cooking", "wine", "gym"), state.interests.interests)
        model.continuePressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertEquals(
            listOf("gym", "cooking", "wine"),
            store.bodies.single().interests?.map { it.value },
        )
    }

    @Test
    fun `a late read that runs the list past ten keeps the user's own picks first`() = runTest(dispatcher) {
        val order = INTEREST_ORDER
        store.held = SavedDetails(interests = order.take(10))
        store.readDelays += 5_000L
        model.arrived(PickStep.Interests, null)
        model.interestTapped(order[20])
        model.interestTapped(order[21])
        advanceUntilIdle()
        assertEquals(setOf(order[20], order[21]) + order.take(8), state.interests.interests)
    }

    @Test
    fun `a late read leaves a tag the user unpicked unpicked, and open groups open`() = runTest(dispatcher) {
        store.held = SavedDetails(interests = listOf("gym", "wine"))
        store.readDelays += 5_000L
        model.arrived(PickStep.Interests, null)
        model.interestTapped("gym")
        model.interestTapped("gym")
        model.showMoreTapped(InterestSection.Culture)
        advanceUntilIdle()
        assertEquals(setOf("wine"), state.interests.interests)
        assertEquals(setOf("culture"), state.interests.expanded)
    }

    @Test
    fun `a late read on an untouched Interests keeps the groups the user opened`() = runTest(dispatcher) {
        store.held = SavedDetails(interests = listOf("wine"))
        store.readDelays += 5_000L
        model.arrived(PickStep.Interests, null)
        model.showMoreTapped(InterestSection.Food)
        advanceUntilIdle()
        assertEquals(setOf("wine"), state.interests.interests)
        assertEquals(setOf("food"), state.interests.expanded)
    }

    @Test
    fun `a late read on Life fills the untouched questions, so Continue keeps them`() = runTest(dispatcher) {
        store.held = SavedDetails(life = mapOf("family" to listOf("has_children"), "living" to listOf("living_alone")))
        store.readDelays += 5_000L
        model.arrived(PickStep.LifeNow, null)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "single")
        advanceUntilIdle()
        model.continuePressed(PickStep.LifeNow) {}
        advanceUntilIdle()
        val body = store.bodies.single()
        assertEquals(listOf("single"), body.partnership?.map { it.value })
        assertEquals(listOf("has_children"), body.family?.map { it.value })
        assertEquals(listOf("living_alone"), body.living?.map { it.value })
    }

    @Test
    fun `Continue with no read yet reads first and saves what the screen would have shown`() = runTest(dispatcher) {
        store.reads = false
        store.held = SavedDetails(life = mapOf("family" to listOf("has_children")))
        arrive(PickStep.LifeNow)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "single")
        store.reads = true
        var advanced = false
        model.continuePressed(PickStep.LifeNow) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        val body = store.bodies.single()
        assertEquals(listOf("single"), body.partnership?.map { it.value })
        assertEquals(listOf("has_children"), body.family?.map { it.value })
    }

    @Test
    fun `a Continue whose first read cuts a saved interest stops, so the next one saves what was shown`() =
        runTest(dispatcher) {
            val order = INTEREST_ORDER
            store.reads = false
            store.held = SavedDetails(interests = order.take(10))
            arrive(PickStep.Interests)
            model.interestTapped(order[20])
            store.reads = true
            var advanced = false
            model.continuePressed(PickStep.Interests) { advanced = true }
            advanceUntilIdle()
            // Nothing saved: the tenth saved tag would have been deleted without ever being shown.
            assertFalse(advanced)
            assertTrue(store.bodies.isEmpty())
            assertFalse(state.saving)
            assertFalse(state.saveFailed)
            assertEquals(setOf(order[20]) + order.take(9), state.interests.interests)
            // Now on screen, the merged ten save on the next Continue.
            model.continuePressed(PickStep.Interests) { advanced = true }
            advanceUntilIdle()
            assertTrue(advanced)
            assertEquals(
                interestsInListOrder(setOf(order[20]) + order.take(9)),
                store.bodies.single().interests?.map { it.value },
            )
        }

    // ── values this build cannot draw, and a jump mid-save ────────────────────────
    //
    // Found by the second audit round (13 October 2026). A newer server may add a value; the read
    // keeps it, the screen cannot show it, and re-sending an untouched part would delete it.

    @Test
    fun `a life question holding a value this build cannot draw is not sent unless touched`() = runTest(dispatcher) {
        store.held = SavedDetails(
            life = mapOf("partnership" to listOf("widowed"), "living" to listOf("living_alone", "van_life")),
        )
        arrive(PickStep.LifeNow)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "no_children")
        model.continuePressed(PickStep.LifeNow) {}
        advanceUntilIdle()
        val body = store.bodies.single()
        assertNull(body.partnership)
        assertNull(body.living)
        assertEquals(listOf("no_children"), body.family?.map { it.value })
        assertEquals(listOf("widowed"), store.held.life["partnership"])
        assertEquals(listOf("living_alone", "van_life"), store.held.life["living"])
    }

    @Test
    fun `an untouched interest list holding an unknown tag is not sent, a touched one is`() = runTest(dispatcher) {
        store.held = SavedDetails(interests = listOf("gym", "skydiving"))
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Rhythm, "early_bird")
        model.continuePressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertNull(store.bodies.single().interests)
        assertEquals(listOf("gym", "skydiving"), store.held.interests)
        // Touched, the user's list is their answer, as any answer is.
        arrive(PickStep.Interests)
        model.interestTapped("wine")
        model.continuePressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertEquals(listOf("gym", "wine"), store.bodies.last().interests?.map { it.value })
    }

    @Test
    fun `a save that lands after a developer-menu jump does not move the flow`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        store.saveDelays += 5_000L
        var advanced = false
        model.continuePressed(PickStep.Interests) { advanced = true }
        dispatcher.scheduler.runCurrent()
        model.leftElsewhere()
        advanceUntilIdle()
        assertFalse(advanced)
        assertFalse(state.saving)
        assertEquals(listOf("gym"), store.held.interests)
    }

    @Test
    fun `Continue whose read fails is a failed save -- nothing written, the picks kept, the toast`() = runTest(dispatcher) {
        store.reads = false
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        analytics.clear()
        var advanced = false
        model.continuePressed(PickStep.Interests) { advanced = true }
        dispatcher.scheduler.runCurrent()
        assertTrue(state.saveFailed)
        advanceUntilIdle()
        assertFalse(advanced)
        assertTrue(store.bodies.isEmpty())
        assertEquals(setOf("gym"), state.interests.interests)
        assertFalse(state.saving)
        assertTrue(analytics.names().isEmpty())
        // Once the account can be read, the same Continue goes through.
        store.reads = true
        model.continuePressed(PickStep.Interests) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertEquals(listOf("gym"), store.bodies.single().interests?.map { it.value })
    }

    // ── Interests: the taps ────────────────────────────────────────────────────

    @Test
    fun `How I live is single-select per group, clears on a second tap, and reports nothing per tap`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        analytics.clear()
        model.lifestyleTapped(LifestyleField.Activity, "active")
        model.lifestyleTapped(LifestyleField.Activity, "sport_is_my_life")
        model.lifestyleTapped(LifestyleField.Diet, "vegan")
        model.lifestyleTapped(LifestyleField.Diet, "vegan")
        assertEquals(mapOf("activity" to "sport_is_my_life"), state.interests.lifestyle)
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `What I'm into reports every tap with the running total`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        analytics.clear()
        model.interestTapped("gym")
        model.interestTapped("wine")
        model.interestTapped("gym")
        assertEquals(listOf("interest_selected", "interest_selected", "interest_deselected"), analytics.names())
        assertEquals(listOf(1, 2, 1), analytics.all().map { it.properties["total_selected"] })
        assertEquals(listOf("gym", "wine", "gym"), analytics.all().map { it.properties["tag_id"] })
        assertTrue(analytics.all().all { it.properties["sensitivity_class"] == 0 })
    }

    @Test
    fun `How I live never counts toward the ten`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        LifestyleField.entries.forEach { model.lifestyleTapped(it, it.options.first().value) }
        INTEREST_ORDER.take(9).forEach { model.interestTapped(it) }
        analytics.clear()
        model.interestTapped(INTEREST_ORDER[9])
        assertEquals(10, analytics.of("interest_selected").single().properties["total_selected"])
    }

    @Test
    fun `at ten a faded tap changes nothing and reports the cap, every time`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        INTEREST_ORDER.take(10).forEach { model.interestTapped(it) }
        analytics.clear()
        model.interestTapped(INTEREST_ORDER[20])
        model.interestTapped(INTEREST_ORDER[30])
        assertEquals(listOf("interest_cap_reached", "interest_cap_reached"), analytics.names())
        assertEquals(10, analytics.all().first().properties["cap"])
        assertEquals(INTEREST_ORDER.take(10).toSet(), state.interests.interests)
        // A picked chip still unpicks, which un-fades the rest.
        model.interestTapped(INTEREST_ORDER[0])
        assertEquals(9, state.interests.interests.size)
        assertFalse(state.interests.capReached)
    }

    @Test
    fun `Show more reports on expand only, with the section and what it revealed`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        analytics.clear()
        model.showMoreTapped(InterestSection.Food)
        model.showMoreTapped(InterestSection.Food)
        model.showMoreTapped(InterestSection.Move)
        assertEquals(listOf("show_more_expanded", "show_more_expanded"), analytics.names())
        val first = analytics.all()[0].properties
        assertEquals("profile_interests", first["screen_id"])
        assertEquals("food", first["section_id"])
        assertEquals(5, first["revealed_count"])
        assertEquals(8, analytics.all()[1].properties["revealed_count"])
        assertEquals(setOf("move"), state.interests.expanded)
    }

    // ── Interests: Continue, Skip, Back ────────────────────────────────────────

    @Test
    fun `Continue saves the answered groups and the interests in list order, with the position`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Social, "balanced")
        model.lifestyleTapped(LifestyleField.Alcohol, "socially")
        model.interestTapped("cruises")
        model.interestTapped("gym")
        var advanced = false
        model.continuePressed(PickStep.Interests) { advanced = true }
        advanceUntilIdle()
        val body = store.bodies.single()
        assertEquals(FlowPosition.interests, body.flowPosition)
        assertEquals("socially", body.alcohol?.value)
        assertEquals("balanced", body.social?.value)
        assertNull(body.activity)
        assertNull(body.diet)
        assertEquals(listOf("gym", "cruises"), body.interests?.map { it.value })
        assertNull(body.partnership)
        assertTrue(advanced)
    }

    @Test
    fun `an accepted Continue reports each answered group in list order, then the step, before navigating`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Social, "balanced")
        model.lifestyleTapped(LifestyleField.Smoking, "no")
        model.lifestyleTapped(LifestyleField.Activity, "active")
        analytics.clear()
        clock += 12_400
        var namesAtNavigation: List<String> = emptyList()
        model.continuePressed(PickStep.Interests) { namesAtNavigation = analytics.names() }
        advanceUntilIdle()
        assertEquals(listOf("detail_answered", "detail_answered", "detail_answered", "profile_step_completed"), namesAtNavigation)
        val answered = analytics.of("detail_answered").map { it.properties }
        assertEquals(listOf("activity", "smoking", "social"), answered.map { it["field_id"] })
        assertEquals(listOf("active", "no", "balanced"), answered.map { it["value_bucketed"] })
        assertEquals(listOf(1, 2, 0), answered.map { it["sensitivity_class"] })
        val done = analytics.of("profile_step_completed").single().properties
        assertEquals("interests", done["step_id"])
        assertEquals(12, done["time_on_step_s"])
    }

    @Test
    fun `interests alone save with no lifestyle field and report only the step`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.interestTapped("reading")
        analytics.clear()
        model.continuePressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertEquals(listOf("profile_step_completed"), analytics.names())
        assertEquals(listOf("reading"), store.bodies.single().interests?.map { it.value })
    }

    @Test
    fun `a lifestyle answer alone sends no interest list, so saved interests are kept`() = runTest(dispatcher) {
        store.held = SavedDetails(interests = listOf("gym"))
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        model.lifestyleTapped(LifestyleField.Diet, "vegan")
        model.continuePressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertNull(store.bodies.single().interests)
        assertEquals(listOf("gym"), store.held.interests)
    }

    @Test
    fun `Continue with nothing picked is exactly Skip -- no save, the skip event, the position`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.showMoreTapped(InterestSection.Move)
        analytics.clear()
        var advanced = false
        model.continuePressed(PickStep.Interests) { advanced = true }
        advanceUntilIdle()
        assertTrue(store.bodies.isEmpty())
        assertEquals(listOf("profile_step_skipped"), analytics.names())
        val skipped = analytics.all().single().properties
        assertEquals("interests", skipped["step_id"])
        assertEquals("profile_interests", skipped["screen_id"])
        assertEquals(listOf(FlowPosition.interests), positions.reported)
        assertTrue(advanced)
    }

    @Test
    fun `Skip saves nothing, keeps earlier values, and never fires the events that must not fire`() = runTest(dispatcher) {
        store.held = SavedDetails(lifestyle = mapOf("diet" to "vegan"))
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Diet, "everything")
        analytics.clear()
        model.skipPressed(PickStep.Interests) {}
        advanceUntilIdle()
        assertTrue(store.bodies.isEmpty())
        assertEquals(listOf("profile_step_skipped"), analytics.names())
        assertEquals(mapOf("diet" to "vegan"), store.held.lifestyle)
        for (never in listOf("detail_skipped", "form_validation_failed", "field_display_opted_out", "habit_answered", "profile_build_started")) {
            assertTrue(never, analytics.of(never).isEmpty())
        }
    }

    @Test
    fun `Back discards unsaved picks, and coming back shows the saved ones collapsed`() = runTest(dispatcher) {
        store.held = SavedDetails(interests = listOf("wine"))
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        model.showMoreTapped(InterestSection.Move)
        var back = false
        model.backPressed(PickStep.Interests) { back = true }
        assertTrue(back)
        assertEquals(setOf("wine"), state.interests.interests)
        assertTrue(state.interests.expanded.isEmpty())
        assertTrue(store.bodies.isEmpty())
    }

    @Test
    fun `a failed save keeps the step, its picks and the toast, and reports nothing`() = runTest(dispatcher) {
        store.saves = false
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        analytics.clear()
        var advanced = false
        model.continuePressed(PickStep.Interests) { advanced = true }
        advanceUntilIdle()
        assertFalse(advanced)
        assertEquals(setOf("gym"), state.interests.interests)
        assertFalse(state.saving)
        assertTrue(analytics.names().isEmpty())
        // The CTA works again once the save is possible.
        store.saves = true
        model.continuePressed(PickStep.Interests) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
    }

    @Test
    fun `the failed-save toast shows and then goes`() = runTest(dispatcher) {
        store.saves = false
        arrive(PickStep.Interests)
        model.interestTapped("gym")
        model.continuePressed(PickStep.Interests) {}
        dispatcher.scheduler.runCurrent()
        assertTrue(state.saveFailed)
        advanceUntilIdle()
        assertFalse(state.saveFailed)
    }

    @Test
    fun `taps on a step that is not on screen are ignored`() = runTest(dispatcher) {
        arrive(PickStep.LifeNow)
        model.interestTapped("gym")
        model.lifestyleTapped(LifestyleField.Diet, "vegan")
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.FamilyAhead, "want_kids")
        assertTrue(state.interests.interests.isEmpty())
        assertTrue(state.interests.lifestyle.isEmpty())
        assertTrue(state.life.picked(LifeQuestion.FamilyAhead).isEmpty())
    }

    // ── Life ───────────────────────────────────────────────────────────────────

    @Test
    fun `Life reports nothing per tap and applies No children's rule`() = runTest(dispatcher) {
        arrive(PickStep.LifeNow)
        analytics.clear()
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "single_parent")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "co_parenting")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "no_children")
        assertEquals(setOf("no_children"), state.life.picked(LifeQuestion.Family))
        assertTrue(analytics.names().isEmpty())
    }

    @Test
    fun `Life's Continue saves all three in list order, an unanswered question as empty`() = runTest(dispatcher) {
        arrive(PickStep.LifeNow)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "divorced")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "single")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Living, "living_alone")
        model.continuePressed(PickStep.LifeNow) {}
        advanceUntilIdle()
        val body = store.bodies.single()
        assertEquals(FlowPosition.life_now, body.flowPosition)
        assertEquals(listOf("single", "divorced"), body.partnership?.map { it.value })
        assertEquals(emptyList<String>(), body.family?.map { it.value })
        assertEquals(listOf("living_alone"), body.living?.map { it.value })
        assertNull(body.familyAhead)
        assertNull(body.interests)
        assertNull(body.activity)
    }

    @Test
    fun `Life reports one event per answered question, in screen order, then the step`() = runTest(dispatcher) {
        arrive(PickStep.LifeNow)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Living, "living_with_children")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "divorced")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "single")
        analytics.clear()
        model.continuePressed(PickStep.LifeNow) {}
        advanceUntilIdle()
        assertEquals(listOf("life_question_answered", "life_question_answered", "profile_step_completed"), analytics.names())
        val answered = analytics.of("life_question_answered").map { it.properties }
        assertEquals(listOf("partnership", "living"), answered.map { it["question_id"] })
        assertEquals(listOf("single,divorced", "living_with_children"), answered.map { it["value_bucketed"] })
        assertEquals(listOf(2, 1), answered.map { it["sensitivity_class"] })
        assertTrue(answered.none { "answers_complete" in it })
        assertEquals("life_now", analytics.of("profile_step_completed").single().properties["step_id"])
    }

    @Test
    fun `Life with nothing picked skips, and back from Future shows Life's saved picks`() = runTest(dispatcher) {
        store.held = SavedDetails(life = mapOf("family" to listOf("has_children", "co_parenting")))
        arrive(PickStep.LifeNow)
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "has_children")
        model.pillTapped(PickStep.LifeNow, LifeQuestion.Family, "co_parenting")
        analytics.clear()
        model.continuePressed(PickStep.LifeNow) {}
        advanceUntilIdle()
        assertEquals(listOf("profile_step_skipped"), analytics.names())
        assertTrue(store.bodies.isEmpty())
        arrive(PickStep.LifeAhead)
        model.backPressed(PickStep.LifeAhead) {}
        arrive(PickStep.LifeNow)
        assertEquals(setOf("has_children", "co_parenting"), state.life.picked(LifeQuestion.Family))
    }

    // ── Future ─────────────────────────────────────────────────────────────────

    @Test
    fun `Future's Continue saves the vision and the profile-complete position`() = runTest(dispatcher) {
        arrive(PickStep.LifeAhead)
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.FamilyAhead, "want_kids")
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.LivingAhead, "move_abroad")
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.LivingAhead, "stay_local")
        model.continuePressed(PickStep.LifeAhead) {}
        advanceUntilIdle()
        val body = store.bodies.single()
        assertEquals(FlowPosition.life_ahead, body.flowPosition)
        assertEquals(listOf("want_kids"), body.familyAhead?.map { it.value })
        // "Persist each answered question": an unanswered one is not sent, so it keeps its value.
        assertNull(body.partnershipAhead)
        assertEquals(listOf("stay_local", "move_abroad"), body.livingAhead?.map { it.value })
        assertNull(body.partnership)
    }

    @Test
    fun `Future keeps a saved answer the user left unanswered`() = runTest(dispatcher) {
        store.held = SavedDetails(life = mapOf("partnership_ahead" to listOf("monogamous_relationship")))
        arrive(PickStep.LifeAhead)
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.PartnershipAhead, "monogamous_relationship")
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.FamilyAhead, "want_kids")
        model.continuePressed(PickStep.LifeAhead) {}
        advanceUntilIdle()
        assertNull(store.bodies.single().partnershipAhead)
        assertEquals(listOf("monogamous_relationship"), store.held.life["partnership_ahead"])
    }

    @Test
    fun `Future reports future_question_answered with _ahead ids and per-question classes`() = runTest(dispatcher) {
        arrive(PickStep.LifeAhead)
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.PartnershipAhead, "find_out_together")
        model.pillTapped(PickStep.LifeAhead, LifeQuestion.FamilyAhead, "dont_want_kids")
        analytics.clear()
        model.continuePressed(PickStep.LifeAhead) {}
        advanceUntilIdle()
        assertEquals(listOf("future_question_answered", "future_question_answered", "profile_step_completed"), analytics.names())
        val answered = analytics.of("future_question_answered").map { it.properties }
        assertEquals(listOf("family_ahead", "partnership_ahead"), answered.map { it["question_id"] })
        assertEquals(listOf("dont_want_kids", "find_out_together"), answered.map { it["value_bucketed"] })
        assertEquals(listOf(1, 2), answered.map { it["sensitivity_class"] })
        assertTrue(analytics.of("life_question_answered").isEmpty())
        assertTrue(analytics.of("detail_answered").isEmpty())
    }

    @Test
    fun `Future's Skip marks profile creation complete and saves nothing`() = runTest(dispatcher) {
        arrive(PickStep.LifeAhead)
        analytics.clear()
        var advanced = false
        model.skipPressed(PickStep.LifeAhead) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertTrue(store.bodies.isEmpty())
        assertEquals(listOf(FlowPosition.life_ahead), positions.reported)
        val skipped = analytics.all().single()
        assertEquals("profile_step_skipped", skipped.event)
        assertEquals("life_ahead", skipped.properties["step_id"])
        assertEquals("profile_life_ahead", skipped.properties["screen_id"])
    }

    // ── process death ──────────────────────────────────────────────────────────

    @Test
    fun `a process death keeps the step, its picks and the open groups`() = runTest(dispatcher) {
        arrive(PickStep.Interests)
        model.lifestyleTapped(LifestyleField.Rhythm, "early_bird")
        model.interestTapped("gym")
        model.showMoreTapped(InterestSection.Culture)
        val revived = PickStepsViewModel(store, positions, handle, analytics) { clock }
        analytics.clear()
        revived.arrived(PickStep.Interests, null)
        advanceUntilIdle()
        assertTrue(analytics.names().isEmpty())
        assertEquals(mapOf("rhythm" to "early_bird"), revived.state.value.interests.lifestyle)
        assertEquals(setOf("gym"), revived.state.value.interests.interests)
        assertEquals(setOf("culture"), revived.state.value.interests.expanded)
    }

    @Test
    fun `a process death keeps which questions were touched, so the read that follows fills only the rest`() =
        runTest(dispatcher) {
            store.held = SavedDetails(life = mapOf("partnership" to listOf("divorced"), "family" to listOf("has_children")))
            arrive(PickStep.LifeNow)
            model.pillTapped(PickStep.LifeNow, LifeQuestion.Partnership, "divorced")
            // The revived model has never read the account; its first read must not undo the clear.
            val revived = PickStepsViewModel(store, positions, handle, analytics) { clock }
            revived.arrived(PickStep.LifeNow, null)
            advanceUntilIdle()
            assertTrue(revived.state.value.life.picked(LifeQuestion.Partnership).isEmpty())
            assertEquals(setOf("has_children"), revived.state.value.life.picked(LifeQuestion.Family))
        }
}
