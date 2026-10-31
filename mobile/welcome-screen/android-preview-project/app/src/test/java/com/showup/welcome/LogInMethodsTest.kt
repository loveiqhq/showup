package com.showup.welcome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Log in offers only what can sign somebody in (SHOWUP-191 item 9, SHOWUP-145).
 *
 * Found on a device: Apple, Google and Facebook were drawn on Log in and did nothing when tapped,
 * because none of the three has an SDK sign-in yet (SHOWUP-144's sub-tasks). SHOWUP-145 already
 * says what to do with a provider whose setup is not finished -- hide it, do not disable it -- and
 * the screen has the switch for that; the flow never set it.
 *
 * Rendered through the real flow rather than the screen alone, because the screen was right all
 * along and the defect was in what the flow passed it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LogInMethodsTest {

    @Test
    fun `log in shows the phone number and hides the providers that cannot sign in yet`() =
        runComposeUiTest {
            setContent { SignUpFlow() }
            waitForIdle()
            onNodeWithText("Log in", substring = true).performClick()
            waitForIdle()

            onNodeWithText(methodSpec(AuthMethod.Phone).label).assertExists()
            for (provider in listOf("Apple", "Google", "Facebook")) {
                onAllNodesWithText(provider, substring = true).assertCountEquals(0)
            }
        }

    @Test
    fun `a device that last signed in with a provider still gets the phone number`() =
        runComposeUiTest {
            // The hint would claim a method the screen cannot offer, so it goes with the button.
            setContent { SignUpFlow(remembered = RememberedAccount("Leo", AuthMethod.Google)) }
            waitForIdle()

            onNodeWithText(methodSpec(AuthMethod.Phone).label).assertExists()
            onAllNodesWithText("Google", substring = true).assertCountEquals(0)
        }
}
