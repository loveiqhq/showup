/*
 * AnimatedContentReuseTest.kt
 * ShowUp · why every arrival in MainActivity keys on "is this the screen being shown"
 *
 * A back pressed during the 320 ms transition returns to a screen that is still composed, sliding
 * away -- and AnimatedContent REUSES that composition instead of building it again. An arrival in a
 * `LaunchedEffect(Unit)` therefore runs once, on the first showing, and never on the return; the
 * detail steps' view model, which had been told the step was left, then ignored every press on it,
 * and Embrace 2 -- which has no back -- could only be escaped by killing the app.
 *
 * This pins the platform behaviour the fix rests on, so a Compose upgrade that changed it shows here
 * first: across A -> B -> back to A mid-transition, the `Unit`-keyed effect runs once and the
 * `onTop`-keyed one runs on both showings.
 *
 * THE CLOCK IS MANUAL, so a state write from test code is delivered explicitly
 * (`Snapshot.sendApplyNotifications`) and frames are stepped by hand; and it is handed back in a
 * `finally`, because a paused clock left behind by a failing assertion hangs every Compose test that
 * runs after this one in the same JVM -- which is how this file first announced itself.
 */
package com.showup.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimatedContentReuseTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a back during the transition reuses the outgoing screen, so only an onTop arrival re-runs`() {
        var screen by mutableStateOf("A")
        val log = mutableListOf<String>()

        fun show(next: String) {
            rule.runOnUiThread {
                screen = next
                Snapshot.sendApplyNotifications()
            }
            repeat(6) { rule.mainClock.advanceTimeByFrame() }
        }

        rule.mainClock.autoAdvance = false
        try {
            rule.setContent {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    label = "reuse",
                ) { current ->
                    val onTop = current == screen
                    SideEffect { log += "compose $current onTop=$onTop" }
                    if (current == "A") {
                        LaunchedEffect(Unit) { log += "unit arrival A" }
                        LaunchedEffect(onTop) { if (onTop) log += "onTop arrival A" }
                    }
                    Text(current)
                }
            }
            rule.mainClock.advanceTimeBy(500)
            show("B")
            // A is still composed, leaving -- which is what makes the return a reuse.
            assertTrue(log.toString(), "compose A onTop=false" in log)
            show("A")
            rule.mainClock.advanceTimeBy(1_000)

            assertEquals(log.toString(), 1, log.count { it == "unit arrival A" })
            assertEquals(log.toString(), 2, log.count { it == "onTop arrival A" })
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }
}
