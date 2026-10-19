/*
 * FlowNavigatorTest.kt
 * ShowUp · the flow's position, held where an Activity rebuild cannot orphan it
 *
 * Two halves. A rebuild keeps the SAME view model, so a navigation written by a save that finishes
 * after the rebuild lands in the state the new composition reads -- that is the instance surviving,
 * which is what a view model is. Process death keeps only the BUNDLE, so the second test round-trips
 * the handle's saved state the way the system does and builds a fresh navigator from it.
 */
package com.showup.welcome

import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowNavigatorTest {

    /**
     * DRAIN THE MAIN LOOPER. The navigator's state is Compose snapshot state, written here with no
     * composition running: Compose answers a write like that by posting its apply-notification task
     * to the main looper, and Robolectric clears the looper between tests. A task dropped that way
     * leaves Compose's UI dispatcher believing a flush is still pending, so the NEXT Compose test in
     * this JVM never goes idle -- FlowRestorationTest timed out at 60 s exactly when it ran after
     * this class.
     */
    @After
    fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a navigation records where it came from`() {
        val nav = FlowNavigator(SavedStateHandle())
        assertEquals(FlowScreen.SignUp, nav.screen)
        assertNull(nav.cameFrom)
        nav.goTo(FlowScreen.ProfileHeight)
        nav.goTo(FlowScreen.ProfileGender)
        assertEquals(FlowScreen.ProfileGender, nav.screen)
        assertEquals(FlowScreen.ProfileHeight, nav.cameFrom)
    }

    @Test
    fun `going to the screen already shown changes nothing -- not even the referrer`() {
        val nav = FlowNavigator(SavedStateHandle())
        nav.goTo(FlowScreen.ProfileLocation)
        nav.goTo(FlowScreen.ProfileEmbraceDetails)
        nav.goTo(FlowScreen.ProfileEmbraceDetails)
        assertEquals(FlowScreen.ProfileEmbraceDetails, nav.screen)
        assertEquals(FlowScreen.ProfileLocation, nav.cameFrom)
    }

    @Test
    fun `the position survives process death`() {
        val handle = SavedStateHandle()
        val nav = FlowNavigator(handle)
        // Deliberately not the default: restoring to SignUp would be indistinguishable from
        // restoring nothing at all.
        nav.goTo(FlowScreen.ProfileReligion)
        nav.goTo(FlowScreen.ProfilePolitics)

        @Suppress("RestrictedApi") // the system's own save path, which is what is under test
        val saved: Bundle = handle.savedStateProvider().saveState()
        val restored = FlowNavigator(SavedStateHandle.createHandle(saved, null))

        assertEquals(FlowScreen.ProfilePolitics, restored.screen)
        assertEquals(FlowScreen.ProfileReligion, restored.cameFrom)
    }
}
