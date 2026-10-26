package com.showup.dev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.showup.analytics.EventLog
import com.showup.profile.LocationArrival
import com.showup.profile.LocationState
import com.showup.welcome.FlowScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The developer menu, drawn.
 *
 * Its first version crashed the moment it opened: a section and a screen shared a list key. The
 * rules test could not see that -- it is a fact about the drawn list -- so this draws it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevMenuRenderTest {

    @Test
    fun `demo launches enable likes by default and can still request freemium`() {
        val intent = android.content.Intent().putExtra(DevCheckInLaunch.EXTRA_ON, true)
        org.junit.Assert.assertTrue(DevCheckInLaunch.from(intent)!!.premium)

        intent.putExtra(DevCheckInLaunch.EXTRA_PREMIUM, false)
        org.junit.Assert.assertFalse(DevCheckInLaunch.from(intent)!!.premium)
    }

    @Test
    fun `demo previews can open profiles and received likes without signing in`() {
        val targets = mutableListOf<FlowScreen>()
        compose.setContent {
            DevMenu(facts(signedIn = false), emptyList(), onJump = {}, onClearEvents = {},
                onHideTab = {}, onClose = {}, onTestServer = targets::add)
        }

        compose.onNodeWithText("Preview profiles").performClick()
        compose.onNodeWithText("Preview received likes").performClick()

        org.junit.Assert.assertEquals(listOf(FlowScreen.CheckInSearch, FlowScreen.LikesReceived), targets)
    }

    @get:Rule
    val compose = createComposeRule()

    private fun facts(signedIn: Boolean? = true) = DevMenuFacts(
        current = FlowScreen.SignUp,
        signedIn = signedIn,
        notificationsAsk = false,
        oldAndroid = false,
        location = LocationArrival.Show(LocationState.Ask),
    )

    @Test
    fun `it opens, lists every screen, and a tap jumps`() {
        var jumped: FlowScreen? = null
        compose.setContent {
            DevMenu(facts(), emptyList(), onJump = { jumped = it }, onClearEvents = {}, onHideTab = {}, onClose = {})
        }

        // Every screen is reachable in the list -- the last one included.
        for (screen in FlowScreen.entries) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(screen.devLabel()))
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(FlowScreen.ProfileGender.devLabel()))
        compose.onNodeWithText(FlowScreen.ProfileGender.devLabel()).performClick()

        assertEquals(FlowScreen.ProfileGender, jumped)
    }

    @Test
    fun `it names the skip the walk would make`() {
        compose.setContent {
            DevMenu(facts(), emptyList(), onJump = {}, onClearEvents = {}, onHideTab = {}, onClose = {})
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Skipped: already answered on this phone"))
        compose.onNodeWithText("Skipped: already answered on this phone").assertIsDisplayed()
    }

    @Test
    fun `signed out, a profile screen cannot be pressed`() {
        compose.setContent {
            DevMenu(facts(signedIn = false), emptyList(), onJump = {}, onClearEvents = {}, onHideTab = {}, onClose = {})
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(FlowScreen.ProfileHeight.devLabel()))
        compose.onNode(hasText(FlowScreen.ProfileHeight.devLabel()), useUnmergedTree = false)
            .assertIsNotEnabled()
    }

    @Test
    fun `the events page shows each event and what it carried, withholding personal data`() {
        val log = EventLog()
        log.record("screen_viewed", mapOf("screen_id" to "profile_height", "email" to "someone@example.com"))
        compose.setContent {
            DevMenu(facts(), log.entries.value, onJump = {}, onClearEvents = {}, onHideTab = {}, onClose = {})
        }

        compose.onNodeWithText("Events (1)").performClick()

        compose.onNodeWithText("screen_viewed").assertIsDisplayed()
        compose.onNodeWithText("screen_id: profile_height").assertIsDisplayed()
        compose.onNodeWithText("email: [withheld]").assertIsDisplayed()
    }
}
