package com.showup.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.showup.api.SessionEnded
import com.showup.designsystem.Motion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "please log in again" notice, and the flag that brings it up.
 *
 * The restart itself is a platform call (a cleared task) and is not driven here; what is pinned is
 * everything either side of it -- that the news is taken exactly once, and that the sentence shows,
 * goes when Startup goes, and goes on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionEndedNoticeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * [SessionEnded] is process-wide, and a test that fails before it consumes the flag would leave
     * it raised for whichever test runs next.
     */
    @Before
    fun clearTheFlag() {
        SessionEnded.consume()
    }

    @Test
    fun `the news is taken once, however many hands reach for it`() {
        SessionEnded.signal()
        SessionEnded.signal()

        assertTrue("the first taker gets it", SessionEnded.consume())
        assertFalse("nobody gets it twice -- the app restarts once", SessionEnded.consume())
        assertFalse(SessionEnded.isPending.value)
    }

    @Test
    fun `nothing to take when nothing happened`() {
        assertFalse(SessionEnded.consume())
    }

    @Test
    fun `Startup says why, and leaving Startup ends it`() {
        var shown by mutableStateOf(true)
        var onStartup by mutableStateOf(true)
        compose.setContent {
            if (onStartup) {
                StartupScreen(sessionEnded = shown, onSessionNoticeDismissed = { shown = false })
            }
        }

        compose.onNodeWithText(SESSION_ENDED_MESSAGE).assertIsDisplayed()

        // To the phone step, say: the notice goes with the screen, and does not come back with a
        // fresh clock when the user returns.
        onStartup = false
        compose.runOnIdle { assertFalse("left Startup", shown) }
    }

    @Test
    fun `it goes on its own after the notice duration`() {
        compose.mainClock.autoAdvance = false
        var shown by mutableStateOf(true)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                SessionEndedNotice(visible = shown, onDismiss = { shown = false })
            }
        }

        compose.mainClock.advanceTimeBy(Motion.NOTICE - 500L)
        assertTrue("still up just before the duration", shown)

        compose.mainClock.advanceTimeBy(1_000L)
        assertFalse("gone after it", shown)
    }
}
