/*
 * CountryRestorationTest.kt
 * ShowUp · the country the user picked has to survive being killed
 *
 * THE BUG THIS EXISTS FOR
 *
 * `CountrySaver` saved the country correctly and `LaunchedEffect(Unit)` overwrote it one line
 * later. A fresh composition is exactly what a process death produces, so the effect ran AGAIN
 * after the state had been restored and replaced the user's choice with the device locale's.
 *
 * The saver worked. The restore worked. The value was then thrown away, which is why nothing
 * caught it: every part looked right on its own, and a rotation -- where the effect also re-runs
 * but the locale answer happens to equal what was saved on a fresh install -- looked fine too.
 *
 * WHY THE PICKED COUNTRY MUST DIFFER FROM THE LOCALE'S
 *
 * If the test picked whatever the locale already reports, an overwrite would produce the same
 * value and the assertion would pass against the broken code. So the country under test is chosen
 * as a NEIGHBOUR of the locale default in the sorted list: the sheet scrolls to the current
 * country when it opens, so only nearby entries are composed, and a different dial code is
 * required or an overwrite would be invisible.
 */
package com.showup.welcome

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The qualifiers are not decoration: without a real screen size Robolectric lays the flow out
// in a window too small to place the CTA, performClick lands on nothing, and the test fails
// while looking like a navigation bug. Same size SignUpTrackingTest uses.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CountryRestorationTest {

    @get:Rule
    val rule = createComposeRule()

    /** What the locale default resolves to, from the same source the screen reads. */
    private val localeCountry: Country = countryForRegion(Locale.getDefault().country)

    /**
     * A NEIGHBOUR of the locale's country in the sorted list, with a different dial code.
     *
     * Neighbour, because `CountrySheet` scrolls to the current country when it opens -- the first
     * entry in the alphabet is not composed at that scroll position and cannot be clicked. That
     * cost one failing run to learn.
     *
     * Different dial code, because the assertion is made on the pill's dial: two countries sharing
     * "+1" would make an overwrite invisible. The self-test at the bottom asserts this holds.
     */
    private val otherCountry: Country = run {
        val i = COUNTRIES.indexOfFirst { it.iso == localeCountry.iso }
        listOfNotNull(COUNTRIES.getOrNull(i + 1), COUNTRIES.getOrNull(i - 1))
            .first { it.dial != localeCountry.dial }
    }

    /** Startup -> the phone screen, where the country pill lives. */
    private fun walkToPhoneScreen() {
        rule.onNodeWithText("Create free account").performClick()
        rule.waitForIdle()
    }

    /**
     * First launch is unchanged: no saved country, so the pill takes the device locale.
     *
     * This is the half that must NOT change. The guard added for the bug below could easily have
     * been written so that the default never applies at all, which would be a worse bug than the
     * one it fixes -- silently shipping everyone Germany.
     */
    @Test
    fun withNothingSavedThePillTakesTheDeviceLocale() {
        rule.setContent { SignUpFlow() }
        walkToPhoneScreen()

        rule.onNodeWithText(localeCountry.dial).assertExists()
    }

    /**
     * A picked country survives process death.
     *
     * Before the fix this failed: the restore put the picked country back and the locale effect
     * replaced it on the same composition.
     */
    @Test
    fun aPickedCountrySurvivesProcessDeath() {
        val restorer = StateRestorationTester(rule)
        restorer.setContent { SignUpFlow() }
        walkToPhoneScreen()

        // Open the picker from the pill, which shows the current dial code.
        rule.onNodeWithText(localeCountry.dial).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(otherCountry.name).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(otherCountry.dial).assertExists()

        restorer.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        // The picked country, not the locale's. This is the whole assertion.
        rule.onNodeWithText(otherCountry.dial).assertExists()
    }

    /**
     * The instrument has to fire.
     *
     * The test above is only evidence if the two dial codes actually differ -- if the locale
     * default and the picked country resolved to the same string, an overwrite would be invisible
     * and the assertion would pass against the broken code.
     */
    @Test
    fun theTwoCountriesUnderTestAreActuallyDistinguishable() {
        assertNotEquals(localeCountry.iso, otherCountry.iso)
        assertNotEquals(
            "the locale default and the picked country share a dial code, so an overwrite would " +
                "be invisible and aPickedCountrySurvivesProcessDeath would prove nothing",
            localeCountry.dial,
            otherCountry.dial,
        )
    }
}
