/*
 * FlowRestorationTest.kt
 * ShowUp · does the flow come back where the user left it
 *
 * WHY THIS EXISTS
 *
 * iOS shipped with no state restoration at all: backgrounded on the code screen and killed, it
 * came back at Startup with an empty field, while Android came back on the code screen with the
 * digits still there. The iOS half of that is fixed with @SceneStorage, which needs a real scene
 * and therefore cannot be tested here or in ScreenFitTests -- it is a manual check on a device.
 *
 * What IS testable is the Android half, and the shape both platforms share: that the saved values
 * survive the save/restore round trip, and that the typed [FlowScreen] that replaced the Int scheme
 * still restores. `StateRestorationTester` does exactly what the system does on process death --
 * it saves the registry, throws the composition away and rebuilds it -- so this is the real
 * mechanism rather than a stand-in for it.
 *
 * WHAT THIS DOES NOT COVER
 *
 * The iOS side. Say so rather than implying parity: the Swift equivalent of these assertions is a
 * device check, and `FlowScreenCodingTests` covers only that the raw values round-trip.
 */
package com.showup.welcome

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowRestorationTest {

    // StateRestorationTester takes the JUnit4 rule, not runComposeUiTest's ComposeUiTest -- it
    // needs the rule's saved-state registry to emulate what the system does on process death.
    @get:Rule
    val rule = createComposeRule()

    /**
     * The typed screen survives what used to kill it.
     *
     * The value under test is deliberately NOT the first one: restoring to the default is
     * indistinguishable from restoring nothing at all, which is how a restoration test passes
     * while proving nothing.
     */
    @Test
    fun theTutorialPositionSurvivesProcessDeath() {
        val restorer = StateRestorationTester(rule)
        var move: ((FlowScreen) -> Unit)? = null
        restorer.setContent {
            var screen by rememberSaveable { mutableStateOf(FlowScreen.SignUp) }
            move = { screen = it }
            Text(screen.name)
        }
        // The move is driven from OUTSIDE the composition on purpose. The first version of this
        // did it with an `if` in the body, which re-runs on every fresh composition -- so the
        // value reappeared after the restore whether or not anything had been saved, and the test
        // passed while proving nothing. The self-test below is what caught it.
        rule.runOnUiThread { move!!(FlowScreen.ThirtyMinutes) }
        rule.waitForIdle()
        rule.onNodeWithText(FlowScreen.ThirtyMinutes.name).assertExists()

        restorer.emulateSavedInstanceStateRestore()

        rule.onNodeWithText(FlowScreen.ThirtyMinutes.name).assertExists()
    }

    /**
     * The instrument has to fire.
     *
     * `remember` is what the code looked like before anyone thought about rotation. If the harness
     * cannot see THAT reset, then the assertion above is not evidence of anything.
     */
    @Test
    fun theProbeSeesStateThatDoesNotSurvive() {
        val restorer = StateRestorationTester(rule)
        var move: ((FlowScreen) -> Unit)? = null
        restorer.setContent {
            // remember, not rememberSaveable: survives recomposition, not process death.
            var screen by androidx.compose.runtime.remember { mutableStateOf(FlowScreen.SignUp) }
            move = { screen = it }
            Text(screen.name)
        }
        rule.runOnUiThread { move!!(FlowScreen.ThirtyMinutes) }
        rule.waitForIdle()
        rule.onNodeWithText(FlowScreen.ThirtyMinutes.name).assertExists()

        restorer.emulateSavedInstanceStateRestore()

        // Back to the default, because nothing was written down. If this ever passes as
        // ThirtyMinutes, the restore above is not restoring anything and the assertion in the
        // test above is a decoration. It caught exactly that on the first attempt.
        rule.onNodeWithText(FlowScreen.SignUp.name).assertExists()
    }

    // ── the ordering the transitions depend on ───────────────────────────────
    //
    // Two things read the order of these entries: AnimatedContent picks its direction by comparing
    // ordinals, and hasSystemBack was the range `screen in 2..6`. Both were Ints before, so the
    // enum has to reproduce the same answers or a screen slides the wrong way.

    @Test
    fun theOrderReproducesEveryDirectionTheIntSchemeGave() {
        // (from, to, wasForward) taken from the Int scheme: -4 signUp, 1..6 cards, 7 home.
        val cases = listOf(
            Triple(FlowScreen.SignUp, FlowScreen.TutorialWelcome, true),      // -4 -> 1
            Triple(FlowScreen.SignUp, FlowScreen.Home, true),                 // -4 -> 7
            Triple(FlowScreen.TutorialWelcome, FlowScreen.MeetInRealLife, true),
            Triple(FlowScreen.MeetInRealLife, FlowScreen.MatchOnAvailability, true),
            Triple(FlowScreen.MatchOnAvailability, FlowScreen.MatchMeansMeet, true),
            Triple(FlowScreen.MatchMeansMeet, FlowScreen.ThirtyMinutes, true),
            Triple(FlowScreen.ThirtyMinutes, FlowScreen.ShowUpEveryTime, true),
            Triple(FlowScreen.ShowUpEveryTime, FlowScreen.Home, true),        // 6 -> 7
            Triple(FlowScreen.MatchOnAvailability, FlowScreen.MeetInRealLife, false),
            Triple(FlowScreen.MatchMeansMeet, FlowScreen.MatchOnAvailability, false),
            Triple(FlowScreen.ThirtyMinutes, FlowScreen.MatchMeansMeet, false),
            Triple(FlowScreen.ShowUpEveryTime, FlowScreen.ThirtyMinutes, false),
            // "Start over" was 7 -> -4, which read as BACKWARD. It still must.
            Triple(FlowScreen.Home, FlowScreen.SignUp, false),
        )
        for ((from, to, wasForward) in cases) {
            assertEquals(
                "%s -> %s should travel %s".format(from, to, if (wasForward) "forward" else "back"),
                wasForward,
                to.ordinal > from.ordinal,
            )
        }
    }

    /** `screen in 2..6` meant cards 2 through 6 and nothing else. */
    @Test
    fun systemBackCoversExactlyTheCardsItUsedTo() {
        val withBack = FlowScreen.entries.filter { it.hasSystemBack }
        assertEquals(
            listOf(
                FlowScreen.MeetInRealLife,
                FlowScreen.MatchOnAvailability,
                FlowScreen.MatchMeansMeet,
                FlowScreen.ThirtyMinutes,
                FlowScreen.ShowUpEveryTime,
                // The three basics screens that draw a chevron. The gesture has to agree with
                // it -- a header with a back control that the OS can dismiss differently is worse
                // than no rule at all. ProfileName is absent on purpose: profile creation is
                // mandatory once entered, so back does NOTHING there.
                FlowScreen.ProfileEmail,
                FlowScreen.ProfileVerifyEmail,
                FlowScreen.ProfileDob,
            ),
            withBack,
        )
        // Card 1 is excluded so back exits the app, and neither end of the flow is in the tour.
        assertTrue(!FlowScreen.TutorialWelcome.hasSystemBack)
        assertTrue(!FlowScreen.SignUp.hasSystemBack)
        assertTrue(!FlowScreen.Home.hasSystemBack)
        // Mandatory once entered: back must do nothing here, not step backwards.
        assertTrue(!FlowScreen.ProfileName.hasSystemBack)
    }

    /**
     * Every screen is named, which is the defect the Int scheme carried: card 6 was the `else`
     * branch, so any unexpected value rendered it.
     */
    @Test
    fun everyScreenIsDistinctAndNamed() {
        // 12 since "The basics" is complete: the eight originals plus the four profile steps --
        // ProfileName, ProfileEmail, ProfileVerifyEmail and ProfileDob -- which sit between the
        // tutorial's end and Home.
        assertEquals(12, FlowScreen.entries.size)
        assertEquals(FlowScreen.entries.size, FlowScreen.entries.map { it.name }.toSet().size)
        assertNotEquals(FlowScreen.SignUp, FlowScreen.entries.last())
    }
}
