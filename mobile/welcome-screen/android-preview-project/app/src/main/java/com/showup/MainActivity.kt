package com.showup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.showup.designsystem.Motion

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import com.showup.profile.ProfileEmailScreen
import com.showup.profile.ProfileNameScreen
import com.showup.welcome.FlowScreen
import com.showup.welcome.SignUpFlow
import com.showup.welcome.SignUpOutcome
import com.showup.welcome.showsTutorial
import com.showup.tutorial.WelcomeScreen
import com.showup.designsystem.rememberMotion

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
            // silently drop the user back to card 1. A Kotlin enum is Serializable, so this needs
            // no Saver. The demo opens where a real first run opens: Startup.
            var screen by rememberSaveable { mutableStateOf(FlowScreen.SignUp) }
            // Kept only so the placeholder home screen can name the rule that sent the user
            // there, which is what makes SHOWUP-146 demonstrable. Not product state.
            var outcome by rememberSaveable { mutableStateOf(SignUpOutcome.NewAccount) }
            // Demo state for "The basics". In-memory and rememberSaveable only: the real flow
            // persists per completed step and resumes onto the last incomplete one, and that
            // depends on a profile-progress store that does not exist yet. Walkable, not shipped.
            var firstName by rememberSaveable { mutableStateOf("") }
            var email by rememberSaveable { mutableStateOf("") }
            var marketingConsent by rememberSaveable { mutableStateOf(false) }
            val motion = rememberMotion()

            // The system back gesture mirrors the on-screen Back, so hardware back never drops
            // someone out of the tutorial from the middle of it. On card 1 it is left alone, so
            // back exits as usual, and the range stops at 6 so back cannot walk out of the app
            // and into the last tutorial card.
            BackHandler(enabled = screen.hasSystemBack) {
                screen = FlowScreen.entries[screen.ordinal - 1]
            }

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
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(tween(Motion.SCREEN)) { w -> direction * w / 6 } +
                                fadeIn(tween(220))
                            ) togetherWith (
                            slideOutHorizontally(tween(Motion.SCREEN)) { w -> -direction * w / 6 } +
                                fadeOut(tween(Motion.FAST))
                            )
                    }
                },
            ) { current ->
                // Exhaustive on purpose. ShowUpEveryTime used to be the `else` branch, which meant
                // any unexpected value rendered card 6; every screen is now named and the compiler
                // fails if one is added and not handled here.
                when (current) {
                    // Welcome & sign-up (SHOWUP-140/142/143/144) runs before the tutorial,
                    // which is the real order: you sign up, then you are shown how it works.
                    // SignUpFlow owns every step and every piece of state inside it.
                    // SHOWUP-146, the whole ticket in one line: the tutorial for a new
                    // account, straight into the app for a returning member.
                    FlowScreen.SignUp -> SignUpFlow(onFinished = {
                        outcome = it
                        screen = if (showsTutorial(it)) FlowScreen.TutorialWelcome else FlowScreen.Home
                    })
                    FlowScreen.TutorialWelcome ->
                        WelcomeScreen(onContinue = { screen = FlowScreen.MeetInRealLife })
                    FlowScreen.MeetInRealLife ->
                        MeetInRealLifeScreen(onNext = { screen = FlowScreen.MatchOnAvailability })
                    FlowScreen.MatchOnAvailability -> MatchOnAvailabilityScreen(
                        onNext = { screen = FlowScreen.MatchMeansMeet },
                        onBack = { screen = FlowScreen.MeetInRealLife },
                    )
                    FlowScreen.MatchMeansMeet -> MatchMeansMeetScreen(
                        onNext = { screen = FlowScreen.ThirtyMinutes },
                        onBack = { screen = FlowScreen.MatchOnAvailability },
                    )
                    FlowScreen.ThirtyMinutes -> ThirtyMinutesScreen(
                        onNext = { screen = FlowScreen.ShowUpEveryTime },
                        onBack = { screen = FlowScreen.MatchMeansMeet },
                    )
                    FlowScreen.ShowUpEveryTime -> ShowUpEveryTimeScreen(
                        // SHOWUP-146 sent the tutorial's far end straight to the app. Profile
                        // creation now sits between the two, which is the real order.
                        onFinish = { screen = FlowScreen.ProfileName },
                        onBack = { screen = FlowScreen.ThirtyMinutes },
                    )
                    // SHOWUP-150. No back: profile creation is mandatory once entered, and the
                    // screen swallows the system gesture itself.
                    FlowScreen.ProfileName -> ProfileNameScreen(
                        value = firstName,
                        onValueChange = { firstName = it },
                        onContinue = {
                            firstName = it
                            screen = FlowScreen.ProfileEmail
                        },
                    )
                    // SHOWUP-152. Continue should reach Verify email (story 03), which is not
                    // built -- so in this demo host it lands on Home. Marked so it is not mistaken
                    // for the specified route.
                    FlowScreen.ProfileEmail -> ProfileEmailScreen(
                        value = email,
                        onValueChange = { email = it },
                        consent = marketingConsent,
                        onConsentChange = { marketingConsent = it },
                        onContinue = {
                            email = it
                            screen = FlowScreen.Home
                        },
                        onBack = { screen = FlowScreen.ProfileName },
                    )
                    FlowScreen.Home ->
                        HomePlaceholderScreen(outcome, onStartOver = { screen = FlowScreen.SignUp })
                }
            }
        }
    }
}
