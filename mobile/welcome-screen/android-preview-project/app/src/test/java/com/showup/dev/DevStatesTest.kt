/*
 * DevStatesTest.kt
 * ShowUp · the developer menu's States page opens a real frame for every entry (debug only)
 *
 * The page lists [DEV_STATES]; the gallery draws them. A state added to one and not the other would be
 * a menu row that opens nothing -- the gallery says "Unknown state" for it, and this fails.
 */
package com.showup.dev

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import com.showup.welcome.FlowScreen
import com.showup.profile.LocationArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class DevStatesTest {

    @Test
    fun `every States entry opens a frame of its own`() {
        DEV_STATES.forEach { state ->
            runComposeUiTest {
                setContent { CheckInGalleryFrame(state.id) }
                waitForIdle()
                assertEquals("${state.id} has no frame", 0, onAllNodesWithText("Unknown state", substring = true).fetchSemanticsNodes().size)
            }
        }
    }

    @Test
    fun `the States page lists every frame the six tickets name, once`() {
        assertEquals(DEV_STATES.size, DEV_STATES.map { it.id }.toSet().size)
        val perTicket = DEV_STATES.groupingBy { it.id.take(3) }.eachCount()
        assertEquals(mapOf("178" to 2, "179" to 5, "180" to 3, "181" to 5, "182" to 3, "183" to 5), perTicket)
    }

    @Test
    fun `signed out, a Check-In row opens on the test server and other account screens still refuse`() {
        val off = LocationArrival.Skip
        assertTrue(devJump(FlowScreen.CheckInToday, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off) is DevJump.TestServer)
        assertTrue(devJump(FlowScreen.CheckInGate, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off) is DevJump.TestServer)
        assertEquals(DevJump.NeedsLogIn, devJump(FlowScreen.ProfileHeight, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off))
        assertEquals(DevJump.Lands(FlowScreen.CheckInToday), devJump(FlowScreen.CheckInToday, signedIn = true, notificationsAsk = true, oldAndroid = false, location = off))
    }
}
