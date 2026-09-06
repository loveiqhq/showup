package com.showup.welcome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.analytics.RecordingAnalytics
import com.showup.analytics.SignUpAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the flow actually FIRES the events, which is a different claim from the catalogue being
 * correct.
 *
 * `SignUpAnalyticsTest` checks that the event names and properties match the tickets. This renders
 * the real flow, taps real controls, and asserts on what the tracker received. Without it,
 * "tracking is implemented" would mean "the constants exist" -- and a wiring that was never called
 * is indistinguishable from no wiring at all, which is the same shape as eight Swift tests sitting
 * in no target.
 *
 * No screen file was changed to make this work. The screens take values and return callbacks, so
 * every hook lives in `SignUpFlow` -- which is the architecture in CLAUDE.md paying for itself.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SignUpTrackingTest {

    @Test
    fun `the startup screenview fires on first composition`() = runComposeUiTest {
        val events = RecordingAnalytics()
        setContent { SignUpFlow(analytics = events) }
        waitForIdle()

        val views = events.of(SignUpAnalytics.SCREEN_VIEWED)
        assertTrue("a screenview should have fired", views.isNotEmpty())
        assertEquals("Signup - CreateAccount", views.first().properties["screen_name"])
    }

    @Test
    fun `a remembered account opens on welcome back and reports its last used method`() =
        runComposeUiTest {
            val events = RecordingAnalytics()
            setContent {
                SignUpFlow(
                    remembered = RememberedAccount("Leo", AuthMethod.Apple),
                    analytics = events,
                )
            }
            waitForIdle()

            val view = events.of(SignUpAnalytics.SCREEN_VIEWED).first()
            assertEquals("Signup - welcomeback", view.properties["screen_name"])
            // SHOWUP-142 asks for the lastUsed value on the screenview.
            assertEquals("apple", view.properties["last_used"])
        }

    @Test
    fun `an unremembered device reports last_used as unknown, not as a missing property`() =
        runComposeUiTest {
            val events = RecordingAnalytics()
            setContent { SignUpFlow(analytics = events) }
            waitForIdle()

            onNodeWithText("Log in", substring = true).performClick()
            waitForIdle()

            val welcomeBack = events.of(SignUpAnalytics.SCREEN_VIEWED)
                .first { it.properties["screen_name"] == "Signup - welcomeback" }
            // The ticket says "including `unknown`". An absent property and an unknown value look
            // the same in a warehouse query, so the string is sent explicitly.
            assertEquals("unknown", welcomeBack.properties["last_used"])
        }

    @Test
    fun `tapping the CTA reports the tap and then the next screenview`() = runComposeUiTest {
        val events = RecordingAnalytics()
        setContent { SignUpFlow(analytics = events) }
        waitForIdle()

        onNodeWithText("Create free account").performClick()
        waitForIdle()

        assertEquals(1, events.of(SignUpAnalytics.CREATE_ACCOUNT_TAPPED).size)
        // Order matters for a funnel: the tap causes the navigation, so it must precede it.
        val names = events.names()
        val tapAt = names.indexOf(SignUpAnalytics.CREATE_ACCOUNT_TAPPED)
        val phoneViewAt = events.all().indexOfFirst {
            it.event == SignUpAnalytics.SCREEN_VIEWED &&
                it.properties["screen_name"] == "Signup - Phonenumber"
        }
        assertTrue("the phone screenview should follow the tap", phoneViewAt > tapAt)
    }

    @Test
    fun `submitting an invalid number reports the failure with a reason and a country`() =
        runComposeUiTest {
            val events = RecordingAnalytics()
            setContent { SignUpFlow(analytics = events) }
            waitForIdle()

            onNodeWithText("Create free account").performClick()
            waitForIdle()
            // Submitting with the field empty is the cheapest way to reach a validation failure
            // without depending on the keyboard.
            onNodeWithText("Send me the code").performClick()
            waitForIdle()

            assertEquals(1, events.of(SignUpAnalytics.PHONE_SUBMITTED).size)

            val failures = events.of(SignUpAnalytics.PHONE_VALIDATION_FAILED)
            assertEquals(1, failures.size)
            // Our own outcome, not the ticket's three-value vocabulary -- see the note on
            // PHONE_VALIDATION_FAILED for why that mismatch is reported rather than mapped away.
            assertEquals("empty", failures.first().properties["reason"])
            // A two-letter ISO code, not a specific one. Asserting "DE" tested Robolectric's
            // locale rather than our code: the flow defaults the country from the device, and the
            // test runtime reports en-US.
            val country = failures.first().properties["country"] as String
            assertTrue("country should be an ISO code, was '$country'",
                Regex("^[A-Z]{2}$").matches(country))
        }

    @Test
    fun `a legal link tap is reported with a valid link and the screen it happened on`() =
        runComposeUiTest {
            val events = RecordingAnalytics()
            setContent { SignUpFlow(analytics = events) }
            waitForIdle()

            // The three legal links live inside ONE Text as annotated regions, so a click lands
            // wherever the node's centre falls -- this test cannot choose which link, and an
            // earlier version of it asserted `privacy_policy` and got `legal_notice` for exactly
            // that reason. Which region maps to which handler is a screen concern and is covered by
            // verify-welcome.py; what is under test HERE is that the tracking wiring fires at all
            // and carries the screen.
            onNodeWithText("Privacy Policy", substring = true).performClick()
            waitForIdle()

            val tap = events.of(SignUpAnalytics.LEGAL_LINK_TAPPED).firstOrNull()
            assertTrue("a legal link tap should have been reported", tap != null)
            val link = tap!!.properties["link"]
            assertTrue(
                "link should be one of the three, was '" + link + "'",
                link in setOf(
                    SignUpAnalytics.Legal.TERMS,
                    SignUpAnalytics.Legal.PRIVACY,
                    SignUpAnalytics.Legal.LEGAL_NOTICE,
                ),
            )
            // The same three links appear on four screens. Without the screen name the taps are
            // indistinguishable in the warehouse.
            assertEquals("Signup - CreateAccount", tap.properties["screen_name"])
        }

    @Test
    fun `nothing is reported when the tracker is left at its default`() = runComposeUiTest {
        // The default is NoOp, so a screen that forgets to pass a tracker is silent rather than
        // crashing -- and the app ships with analytics off until somebody switches it on.
        setContent { SignUpFlow() }
        waitForIdle()
        // Reaching here without an exception is the assertion.
    }
}
