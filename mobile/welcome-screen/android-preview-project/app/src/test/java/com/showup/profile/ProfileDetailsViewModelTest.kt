/*
 * ProfileDetailsViewModelTest.kt
 * ShowUp · Embrace 2 and the seven detail steps, driven with no device (SHOWUP-166 to SHOWUP-173)
 *
 * Every tracking row in every ticket, the order they fire in, "persist before navigating", what a
 * skip sends and does not send, and the two failure modes the view model exists to stop: a save
 * that half-happens, and a `hidden_fields` replace built on a guess.
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class ProfileDetailsViewModelTest {

    /** A store that remembers every body and can be made to fail either call. */
    private class FakeStore(
        var held: SavedDetails = SavedDetails(hiddenFields = setOf("age")),
        var loads: Boolean = true,
        var saves: Boolean = true,
    ) : ProfileDetailsStore {
        val bodies = mutableListOf<UpsertProfileDto>()
        var loadCalls = 0
        /** Milliseconds each successive read takes; a read with no entry is instant. */
        val readDelays = ArrayDeque<Long>()

        override suspend fun load(): SavedDetails? {
            loadCalls++
            // What the server holds WHEN THE READ IS MADE -- a read in flight does not see a save
            // that lands after it started.
            val snapshot = held
            val ok = loads
            readDelays.removeFirstOrNull()?.let { delay(it) }
            return if (ok) snapshot else null
        }

        override suspend fun save(body: UpsertProfileDto): SavedDetails? {
            bodies += body
            if (!saves) return null
            held = held.copy(
                heightCm = body.heightCm ?: held.heightCm,
                gender = body.gender?.value ?: held.gender,
                orientation = body.orientation?.value ?: held.orientation,
                datingLanguages = body.datingLanguages?.map { it.value } ?: held.datingLanguages,
                education = body.education?.value ?: held.education,
                religion = body.religion?.value ?: held.religion,
                politics = body.politics?.value ?: held.politics,
                hiddenFields = body.hiddenFields?.toSet() ?: held.hiddenFields,
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
    private var clock = 0L
    private lateinit var model: ProfileDetailsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeStore()
        positions = Positions()
        analytics = RecordingAnalytics()
        clock = 1_000L
        model = ProfileDetailsViewModel(store, positions, SavedStateHandle(), analytics) { clock }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.arrive(step: DetailStep, referrer: ProfileScreen? = null) {
        model.arrived(step, referrer)
        advanceUntilIdle()
    }

    // ── Embrace 2 ──────────────────────────────────────────────────────────────

    @Test
    fun `embrace 2 fires screen_viewed and embrace_bridge_viewed add_details on a push`() = runTest(dispatcher) {
        model.embraceArrived(ProfileScreen.Location, pop = false)
        assertEquals(listOf("screen_viewed", "embrace_bridge_viewed"), analytics.names())
        assertEquals("profile_embrace_details", analytics.of("screen_viewed").single().properties["screen_id"])
        assertEquals("profile_location", analytics.of("screen_viewed").single().properties["referrer_screen_id"])
        assertEquals("add_details", analytics.of("embrace_bridge_viewed").single().properties["variant"])
    }

    @Test
    fun `a back-pop onto embrace 2 fires screen_viewed there and nothing else`() = runTest(dispatcher) {
        model.embraceArrived(ProfileScreen.Height, pop = true)
        assertEquals(listOf("screen_viewed"), analytics.names())
    }

    @Test
    fun `embrace 2 fires no step event and nothing for the confetti`() = runTest(dispatcher) {
        model.embraceArrived(ProfileScreen.Location, pop = false)
        var advanced = false
        model.embraceContinue { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertFalse(analytics.names().any { it.startsWith("profile_step") })
        assertEquals(2, analytics.all().size)
    }

    @Test
    fun `embrace 2 starts the read so height can pre-fill`() = runTest(dispatcher) {
        model.embraceArrived(ProfileScreen.Location, pop = false)
        advanceUntilIdle()
        assertEquals(1, store.loadCalls)
    }

    // ── arrival ────────────────────────────────────────────────────────────────

    @Test
    fun `a step fires screen_viewed with its referrer, then profile_step_viewed`() = runTest(dispatcher) {
        arrive(DetailStep.Height, ProfileScreen.EmbraceDetails)
        assertEquals(listOf("screen_viewed", "profile_step_viewed"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("profile_height", view["screen_id"])
        assertEquals("ProfileHeight", view["screen_name"])
        assertEquals("profile_embrace_details", view["referrer_screen_id"])
        val step = analytics.of("profile_step_viewed").single().properties
        assertEquals("height", step["step_id"])
        assertEquals(1, step["step_index"])
    }

    @Test
    fun `a recomposition of the same step is not a second arrival`() = runTest(dispatcher) {
        arrive(DetailStep.Gender)
        model.optionTapped(DetailStep.Gender, "man")
        model.arrived(DetailStep.Gender, null)
        advanceUntilIdle()
        assertEquals(1, analytics.of("screen_viewed").size)
        // And the choice survives -- a dark-mode switch must not throw it away.
        assertEquals("man", model.state.value.draft.gender)
    }

    @Test
    fun `a resume pre-fills the saved value and visibility, with no toast`() = runTest(dispatcher) {
        store.held = SavedDetails(heightCm = 181, hiddenFields = setOf("height"))
        arrive(DetailStep.Height)
        assertEquals("181", model.state.value.draft.heightText)
        assertTrue(model.state.value.draft.isHidden(DetailStep.Height))
        assertNull(model.state.value.toast)
    }

    @Test
    fun `a slow read lands on an untouched step, and never over a choice already made`() = runTest(dispatcher) {
        store.held = SavedDetails(gender = "woman")
        model.arrived(DetailStep.Gender, null)
        // The user picks before the read finishes.
        model.optionTapped(DetailStep.Gender, "other")
        advanceUntilIdle()
        assertEquals("other", model.state.value.draft.gender)
    }

    @Test
    fun `typing during the save cannot change the answer that was accepted`() = runTest(dispatcher) {
        // The account read is still to happen when Continue is pressed, so the save waits on it --
        // and height's field still takes typing meanwhile.
        store.loads = false
        arrive(DetailStep.Height)
        store.loads = true
        model.heightChanged("175")
        var advanced = false
        model.continuePressed(DetailStep.Height) { advanced = true }
        model.heightChanged("")
        advanceUntilIdle()
        assertTrue(advanced)
        assertEquals(175, store.bodies.single().heightCm)
        assertEquals("175_179", analytics.of("detail_answered").single().properties["value_bucketed"])
    }

    // ── process death ──────────────────────────────────────────────────────────
    //
    // A new view model on the SAME handle is what Android builds after it killed the process: the
    // handle comes back, everything else starts again -- including the account read.

    private fun restored(handle: SavedStateHandle) =
        ProfileDetailsViewModel(store, positions, handle, analytics) { clock }

    @Test
    fun `after process death the read does not land on top of what was picked`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        model = restored(handle)
        store.held = SavedDetails(religion = "catholic")
        arrive(DetailStep.Religion)
        model.optionTapped(DetailStep.Religion, "hindu")

        model = restored(handle)
        model.arrived(DetailStep.Religion, null)
        advanceUntilIdle()
        assertEquals("hindu", model.state.value.draft.religion)
    }

    @Test
    fun `after process death an untouched step still takes the late read`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        model = restored(handle)
        store.loads = false
        arrive(DetailStep.Religion)

        store.loads = true
        store.held = SavedDetails(religion = "catholic")
        model = restored(handle)
        model.arrived(DetailStep.Religion, null)
        advanceUntilIdle()
        assertEquals("catholic", model.state.value.draft.religion)
    }

    @Test
    fun `a restore onto a step or the bridge is not a second showing of it`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        model = restored(handle)
        model.embraceArrived(ProfileScreen.Location, pop = false)
        model = restored(handle)
        model.embraceArrived(ProfileScreen.Location, pop = false)
        assertEquals(1, analytics.of("embrace_bridge_viewed").size)

        model.embraceContinue {}
        arrive(DetailStep.Height)
        model = restored(handle)
        model.arrived(DetailStep.Height, null)
        advanceUntilIdle()
        assertEquals(1, analytics.of("profile_step_viewed").size)
    }

    @Test
    fun `back discards an unsaved selection`() = runTest(dispatcher) {
        arrive(DetailStep.Religion)
        model.optionTapped(DetailStep.Religion, "hindu")
        var back = false
        model.backPressed(DetailStep.Religion) { back = true }
        assertTrue(back)
        arrive(DetailStep.Religion)
        assertNull(model.state.value.draft.religion)
        assertTrue(store.bodies.isEmpty())
    }

    // ── height ────────────────────────────────────────────────────────────────

    @Test
    fun `an empty Continue on height is refused with required_missing, no save, no navigation`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        analytics.clear()
        var advanced = false
        model.continuePressed(DetailStep.Height) { advanced = true }
        advanceUntilIdle()
        assertFalse(advanced)
        assertTrue(store.bodies.isEmpty())
        val fail = analytics.of("form_validation_failed").single().properties
        assertEquals("height", fail["field_id"])
        assertEquals("required_missing", fail["rule"])
        assertEquals("profile_height", fail["screen_id"])
        assertEquals("height", fail["step_id"])
    }

    @Test
    fun `out of range is impossible`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        model.heightChanged("99")
        analytics.clear()
        model.continuePressed(DetailStep.Height) {}
        assertEquals("impossible", analytics.of("form_validation_failed").single().properties["rule"])
    }

    @Test
    fun `the toast shows for 2600 ms, a second press restarts it, and a valid value hides it`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        model.continuePressed(DetailStep.Height) {}
        assertEquals(DetailToast.Refusal, model.state.value.toast)
        advanceTimeBy(2_000)
        model.continuePressed(DetailStep.Height) {}
        advanceTimeBy(2_000)
        assertEquals("restarted, so still up 4 s after the first press", DetailToast.Refusal, model.state.value.toast)
        advanceTimeBy(700)
        assertNull(model.state.value.toast)

        model.continuePressed(DetailStep.Height) {}
        model.heightChanged("175")
        assertNull("hides as soon as the value is valid", model.state.value.toast)
    }

    @Test
    fun `typing is filtered to three digits`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        model.heightChanged("1x8;0 5")
        assertEquals("180", model.state.value.draft.heightText)
    }

    @Test
    fun `a valid Continue saves height, visibility and position in ONE request, then reports, then navigates`() =
        runTest(dispatcher) {
            arrive(DetailStep.Height)
            model.heightChanged("175")
            model.visibilityToggled(DetailStep.Height)
            clock += 12_400
            analytics.clear()
            val order = mutableListOf<String>()
            model.continuePressed(DetailStep.Height) { order += "navigate:" + analytics.names().joinToString() }
            advanceUntilIdle()

            val body = store.bodies.single()
            assertEquals(175, body.heightCm)
            assertEquals(FlowPosition.height, body.flowPosition)
            // The age hidden on the date-of-birth step is carried, not dropped.
            assertEquals(listOf("age", "height"), body.hiddenFields)

            assertEquals(listOf("navigate:detail_answered, profile_step_completed"), order)
            val answered = analytics.of("detail_answered").single().properties
            assertEquals("height", answered["field_id"])
            assertEquals("175_179", answered["value_bucketed"])
            assertEquals(0, answered["sensitivity_class"])
            assertEquals(12, analytics.of("profile_step_completed").single().properties["time_on_step_s"])
        }

    @Test
    fun `skip writes no height even with a valid value typed, and keeps the saved one`() = runTest(dispatcher) {
        store.held = SavedDetails(heightCm = 160)
        arrive(DetailStep.Height)
        model.heightChanged("190")
        analytics.clear()
        var advanced = false
        model.skipPressed(DetailStep.Height) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertTrue("a skip saves nothing", store.bodies.isEmpty())
        assertEquals(160, store.held.heightCm)
        assertEquals(listOf(FlowPosition.height), positions.reported)
        assertEquals(listOf("detail_skipped", "profile_step_skipped"), analytics.names())
        assertEquals("height", analytics.of("detail_skipped").single().properties["field_id"])
        assertEquals("profile_height", analytics.of("profile_step_skipped").single().properties["screen_id"])
    }

    @Test
    fun `the visibility box reports on tick only`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        analytics.clear()
        model.visibilityToggled(DetailStep.Height)
        model.visibilityToggled(DetailStep.Height)
        model.visibilityToggled(DetailStep.Height)
        assertEquals(2, analytics.of("field_display_opted_out").size)
        assertEquals(listOf("field_display_opted_out", "field_display_opted_out"), analytics.names())
    }

    // ── gender and orientation ────────────────────────────────────────────────

    @Test
    fun `gender refuses an empty Continue and has no skip at all`() = runTest(dispatcher) {
        arrive(DetailStep.Gender)
        analytics.clear()
        var advanced = false
        model.continuePressed(DetailStep.Gender) { advanced = true }
        model.skipPressed(DetailStep.Gender) { advanced = true }
        advanceUntilIdle()
        assertFalse(advanced)
        assertEquals(listOf("form_validation_failed"), analytics.names())
        assertTrue(positions.reported.isEmpty())
    }

    @Test
    fun `tapping the selected gender keeps it, and picking does not navigate`() = runTest(dispatcher) {
        arrive(DetailStep.Gender)
        model.optionTapped(DetailStep.Gender, "non_binary")
        model.optionTapped(DetailStep.Gender, "non_binary")
        assertEquals("non_binary", model.state.value.draft.gender)
        assertTrue(store.bodies.isEmpty())
    }

    @Test
    fun `gender saves the section 1 value, class 2, with its visibility`() = runTest(dispatcher) {
        arrive(DetailStep.Gender)
        model.optionTapped(DetailStep.Gender, "non_binary")
        analytics.clear()
        model.continuePressed(DetailStep.Gender) {}
        advanceUntilIdle()
        assertEquals("non_binary", store.bodies.single().gender?.value)
        assertEquals(FlowPosition.gender, store.bodies.single().flowPosition)
        val answered = analytics.of("detail_answered").single().properties
        assertEquals("non_binary", answered["value_bucketed"])
        assertEquals(2, answered["sensitivity_class"])
    }

    @Test
    fun `a pick hides the refusal toast`() = runTest(dispatcher) {
        arrive(DetailStep.Orientation)
        model.continuePressed(DetailStep.Orientation) {}
        assertEquals(DetailToast.Refusal, model.state.value.toast)
        model.optionTapped(DetailStep.Orientation, "lesbian")
        assertNull(model.state.value.toast)
    }

    // ── dating language ───────────────────────────────────────────────────────

    @Test
    fun `an empty Continue on dating language is a skip -- never a refusal`() = runTest(dispatcher) {
        arrive(DetailStep.DatingLanguage)
        analytics.clear()
        var advanced = false
        model.continuePressed(DetailStep.DatingLanguage) { advanced = true }
        advanceUntilIdle()
        assertTrue(advanced)
        assertTrue(store.bodies.isEmpty())
        assertEquals(listOf("detail_skipped", "profile_step_skipped"), analytics.names())
        assertNull(model.state.value.toast)
    }

    @Test
    fun `languages save and report in list order, comma-joined, once`() = runTest(dispatcher) {
        arrive(DetailStep.DatingLanguage)
        model.languageTapped("spanish")
        model.languageTapped("german")
        model.languageTapped("arabic")
        model.languageTapped("arabic")
        analytics.clear()
        model.continuePressed(DetailStep.DatingLanguage) {}
        advanceUntilIdle()
        assertEquals(listOf("german", "spanish"), store.bodies.single().datingLanguages?.map { it.value })
        assertEquals(1, analytics.of("detail_answered").size)
        assertEquals("german,spanish", analytics.of("detail_answered").single().properties["value_bucketed"])
        assertEquals(1, analytics.of("detail_answered").single().properties["sensitivity_class"])
    }

    // ── the skippable single-selects ──────────────────────────────────────────

    @Test
    fun `education clears on a second tap, and the clear sends nothing`() = runTest(dispatcher) {
        arrive(DetailStep.Education)
        analytics.clear()
        model.optionTapped(DetailStep.Education, "phd")
        model.optionTapped(DetailStep.Education, "phd")
        assertNull(model.state.value.draft.education)
        assertTrue(analytics.all().isEmpty())
    }

    @Test
    fun `selecting then clearing then Continue is a skip that keeps the earlier value`() = runTest(dispatcher) {
        store.held = SavedDetails(religion = "catholic")
        arrive(DetailStep.Religion)
        assertEquals("catholic", model.state.value.draft.religion)
        model.optionTapped(DetailStep.Religion, "catholic")
        model.continuePressed(DetailStep.Religion) {}
        advanceUntilIdle()
        assertTrue(store.bodies.isEmpty())
        assertEquals("catholic", store.held.religion)
        assertEquals(listOf(FlowPosition.religion), positions.reported)
    }

    @Test
    fun `politics answers with the section 1 value and class 2`() = runTest(dispatcher) {
        arrive(DetailStep.Politics)
        model.optionTapped(DetailStep.Politics, "mid_right")
        analytics.clear()
        model.continuePressed(DetailStep.Politics) {}
        advanceUntilIdle()
        assertEquals("mid_right", store.bodies.single().politics?.value)
        assertEquals(2, analytics.of("detail_answered").single().properties["sensitivity_class"])
        assertEquals("1.4.15", analytics.of("detail_answered").single().properties["field_registry_version"])
    }

    // ── failures ──────────────────────────────────────────────────────────────

    @Test
    fun `a failed save keeps the user on the step with the answer, says so, and reports nothing`() =
        runTest(dispatcher) {
            store.saves = false
            arrive(DetailStep.Gender)
            model.optionTapped(DetailStep.Gender, "woman")
            analytics.clear()
            var advanced = false
            model.continuePressed(DetailStep.Gender) { advanced = true }
            // runCurrent, not advanceUntilIdle: the latter would also run the toast's 2600 ms hide.
            runCurrent()
            assertFalse(advanced)
            assertEquals("woman", model.state.value.draft.gender)
            assertEquals(DetailToast.SaveFailed, model.state.value.toast)
            assertFalse(model.state.value.saving)
            assertTrue(analytics.all().isEmpty())

            // And the CTA works again.
            store.saves = true
            model.continuePressed(DetailStep.Gender) { advanced = true }
            advanceUntilIdle()
            assertTrue(advanced)
        }

    @Test
    fun `hidden_fields is never sent on a guess -- no read, no save`() = runTest(dispatcher) {
        store.loads = false
        arrive(DetailStep.Height)
        model.heightChanged("175")
        model.continuePressed(DetailStep.Height) {}
        runCurrent()
        assertTrue("a replace built without knowing the set would un-hide the age", store.bodies.isEmpty())
        assertEquals(DetailToast.SaveFailed, model.state.value.toast)
    }

    @Test
    fun `Continue cannot be pressed twice while a save is in flight`() = runTest(dispatcher) {
        arrive(DetailStep.Gender)
        model.optionTapped(DetailStep.Gender, "man")
        var advanced = 0
        model.continuePressed(DetailStep.Gender) { advanced++ }
        model.continuePressed(DetailStep.Gender) { advanced++ }
        advanceUntilIdle()
        assertEquals(1, advanced)
        assertEquals(1, store.bodies.size)
    }

    // ── the account copy is never stale where it matters ──────────────────────────

    @Test
    fun `every save reads the account fresh, so a field hidden since the first read stays hidden`() =
        runTest(dispatcher) {
            // The read taken on arrival knows no hidden fields. Before Continue, the age is hidden
            // (on the date-of-birth step, or on another device). The PATCH must not send it back.
            store.held = SavedDetails()
            arrive(DetailStep.Height)
            store.held = store.held.copy(hiddenFields = setOf("age"))
            model.heightChanged("175")
            model.continuePressed(DetailStep.Height) {}
            advanceUntilIdle()
            assertEquals(listOf("age"), store.bodies.single().hiddenFields)
        }

    @Test
    fun `a slow first read cannot overwrite a save that landed before it`() = runTest(dispatcher) {
        store.held = SavedDetails()
        store.readDelays.addAll(listOf(5_000L, 0L))
        model.arrived(DetailStep.Height, null)
        model.heightChanged("181")
        model.continuePressed(DetailStep.Height) {}
        advanceUntilIdle()
        assertEquals(181, store.bodies.single().heightCm)
        // Back at height: what shows is the saved 181, not the empty account the slow read saw.
        model.arrived(DetailStep.Gender, null)
        model.backPressed(DetailStep.Gender) {}
        arrive(DetailStep.Height)
        assertEquals("181", model.state.value.draft.heightText)
    }

    @Test
    fun `leaving a step puts its draft back to the saved value at once`() = runTest(dispatcher) {
        // So the next arrival draws the right row on its very first frame, before it resets.
        store.held = SavedDetails(religion = "catholic")
        arrive(DetailStep.Religion)
        model.optionTapped(DetailStep.Religion, "hindu")
        model.backPressed(DetailStep.Religion) {}
        assertEquals("catholic", model.state.value.draft.religion)
    }

    @Test
    fun `the first read fills the steps that are not on screen too`() = runTest(dispatcher) {
        store.held = SavedDetails(politics = "middle")
        model.embraceArrived(ProfileScreen.Location, pop = false)
        advanceUntilIdle()
        assertEquals("middle", model.state.value.draft.politics)
    }

    // ── the outgoing screen during its exit animation ───────────────────────────

    @Test
    fun `taps on a step that is no longer on screen do nothing`() = runTest(dispatcher) {
        arrive(DetailStep.Education)
        var advances = 0
        model.skipPressed(DetailStep.Education) { advances++ }
        model.skipPressed(DetailStep.Education) { advances++ }
        model.continuePressed(DetailStep.Education) { advances++ }
        model.backPressed(DetailStep.Education) { advances++ }
        advanceUntilIdle()
        assertEquals(1, advances)
        assertEquals(1, analytics.of("detail_skipped").size)
        assertEquals(listOf(FlowPosition.education), positions.reported)
    }

    @Test
    fun `inputs on a step that is no longer on screen do nothing`() = runTest(dispatcher) {
        arrive(DetailStep.Height)
        model.heightChanged("181")
        model.backPressed(DetailStep.Height) {}
        arrive(DetailStep.Gender)
        analytics.clear()
        // Taps on the outgoing height screen, and on lists that are not the one on screen.
        model.heightChanged("170")
        model.visibilityToggled(DetailStep.Height)
        model.optionTapped(DetailStep.Religion, "hindu")
        model.languageTapped("german")
        assertEquals("", model.state.value.draft.heightText)
        assertNull(model.state.value.draft.religion)
        assertTrue(model.state.value.draft.languages.isEmpty())
        assertTrue(analytics.all().isEmpty())
    }

    @Test
    fun `the pre-fill counter survives process death with the draft`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        model = ProfileDetailsViewModel(store, positions, handle, analytics) { clock }
        arrive(DetailStep.Religion)
        val before = model.state.value.prefill
        model = ProfileDetailsViewModel(store, positions, handle, analytics) { clock }
        assertEquals(before, model.state.value.prefill)
    }

    @Test
    fun `a second tap on Embrace 2 does not navigate again`() = runTest(dispatcher) {
        model.embraceArrived(ProfileScreen.Location, pop = false)
        var advances = 0
        model.embraceContinue { advances++ }
        model.embraceContinue { advances++ }
        advanceUntilIdle()
        assertEquals(1, advances)
        assertEquals(listOf(FlowPosition.location), positions.reported)
    }

    @Test
    fun `the step timer survives process death`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        model = ProfileDetailsViewModel(store, positions, handle, analytics) { clock }
        arrive(DetailStep.Gender)
        model.optionTapped(DetailStep.Gender, "man")
        clock += 12_000
        model = ProfileDetailsViewModel(store, positions, handle, analytics) { clock }
        model.arrived(DetailStep.Gender, null)
        model.continuePressed(DetailStep.Gender) {}
        advanceUntilIdle()
        assertEquals(12, analytics.of("profile_step_completed").single().properties["time_on_step_s"])
    }

    @Test
    fun `no event names outside the registry ever leave this view model`() = runTest(dispatcher) {
        val allowed = setOf(
            "screen_viewed", "profile_step_viewed", "profile_step_completed", "profile_step_skipped",
            "detail_answered", "detail_skipped", "field_display_opted_out", "form_validation_failed",
            "embrace_bridge_viewed",
        )
        model.embraceArrived(ProfileScreen.Location, pop = false)
        DetailStep.entries.forEach { step ->
            arrive(step)
            model.visibilityToggled(step)
            model.continuePressed(step) {}
            advanceUntilIdle()
            model.skipPressed(step) {}
            advanceUntilIdle()
        }
        assertTrue(analytics.names().toSet().all { it in allowed })
    }
}
