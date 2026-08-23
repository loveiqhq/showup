package com.showup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import com.showup.tutorial.WelcomeScreen
import com.showup.tutorial.rememberMotion

/**
 * Host for the six tutorial screens. Edge-to-edge so each screen's own safe-area handling is what
 * positions the content — which is the thing worth checking on a device.
 *
 * The navigation here is a placeholder: real routing arrives with the rest of the app. It exists so
 * the flow can be walked end to end in the emulator.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // rememberSaveable, not remember: a rotation or a process death mid-tutorial should not
            // silently drop the user back to card 1.
            var screen by rememberSaveable { mutableIntStateOf(1) }
            val motion = rememberMotion()

            // The system back gesture mirrors the on-screen Back, so hardware back never drops
            // someone out of the tutorial from the middle of it. On card 1 it is left alone, so
            // back exits as usual.
            BackHandler(enabled = screen > 1) { screen -= 1 }

            AnimatedContent(
                targetState = screen,
                label = "tutorialCard",
                transitionSpec = {
                    if (!motion.enabled) {
                        // The device asked for no animation: cut, do not crossfade slowly.
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        // Forward travels in from the right, Back from the left, so the motion
                        // carries the direction of travel. A sixth of the width is enough to read
                        // as movement without the screens appearing to fly.
                        val direction = if (targetState > initialState) 1 else -1
                        (
                            slideInHorizontally(tween(320)) { w -> direction * w / 6 } +
                                fadeIn(tween(220))
                            ) togetherWith (
                            slideOutHorizontally(tween(320)) { w -> -direction * w / 6 } +
                                fadeOut(tween(180))
                            )
                    }
                },
            ) { current ->
                when (current) {
                    1 -> WelcomeScreen(onContinue = { screen = 2 })
                    2 -> MeetInRealLifeScreen(onNext = { screen = 3 })
                    3 -> MatchOnAvailabilityScreen(onNext = { screen = 4 }, onBack = { screen = 2 })
                    4 -> MatchMeansMeetScreen(onNext = { screen = 5 }, onBack = { screen = 3 })
                    5 -> ThirtyMinutesScreen(onNext = { screen = 6 }, onBack = { screen = 4 })
                    else -> ShowUpEveryTimeScreen(
                        onFinish = { screen = 1 },   // real destination TBC — restarts the tour
                        onBack = { screen = 5 },
                    )
                }
            }
        }
    }
}
