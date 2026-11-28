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
    fun `the States page lists every frame the tickets name, once`() {
        assertEquals(DEV_STATES.size, DEV_STATES.map { it.id }.toSet().size)
        val perTicket = DEV_STATES.groupingBy { it.id.take(3) }.eachCount()
        // 193: the sheet's A, B, C and D's end, plus the two states it adds (the read-only profile
        // and a first read that failed).
        // 194: the sheet's A to K (I, J and K, the three failures, 9 Nov 2026).
        // 195: A to H plus C2 (16 Nov) -- the bar, the guard, no fix, check-in not saved, done, after Close, the chat chip, the date gate, the arrival card.
        // 196: A to N, the ticket's fourteen frames (L's thread variant is a preview and fit frame only).
        // 197: A to J (D is Date 03's gate with the note). 198: A to F, H and I -- G is flow 10's and has no row. 199: A, B, C and the chat variant of A.
        assertEquals(mapOf("178" to 2, "179" to 5, "180" to 3, "181" to 5, "182" to 3, "183" to 5, "184" to 10, "193" to 6, "194" to 11, "195" to 9, "196" to 14, "197" to 10, "198" to 8, "199" to 4), perTicket)
    }

    @Test
    fun `signed out, a Check-In row opens on the test server and other account screens still refuse`() {
        val off = LocationArrival.Skip
        assertTrue(devJump(FlowScreen.CheckInToday, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off) is DevJump.TestServer)
        assertTrue(devJump(FlowScreen.CheckInGate, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off) is DevJump.TestServer)
        assertEquals(DevJump.NeedsLogIn, devJump(FlowScreen.ProfileHeight, signedIn = false, notificationsAsk = true, oldAndroid = false, location = off))
        assertEquals(DevJump.TestServer(FlowScreen.CheckInToday), devJump(FlowScreen.CheckInToday, signedIn = true, notificationsAsk = true, oldAndroid = false, location = off))
    }
}
