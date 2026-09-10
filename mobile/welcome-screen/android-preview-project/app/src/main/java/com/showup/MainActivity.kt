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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.showup.api.EncryptedTokenStore
import com.showup.api.ShowUpApi
import com.showup.profile.BasicsRepository
import com.showup.welcome.PhoneAuthRepository
import com.showup.welcome.PhoneAuthViewModel
import com.showup.profile.BasicsViewModel
import com.showup.profile.EmailCopy
import com.showup.profile.ProfileDobScreen
import java.time.OffsetDateTime
import com.showup.profile.ProfileEmailScreen
import com.showup.profile.ProfileNameScreen
import com.showup.profile.ProfileVerifyEmailScreen
import com.showup.profile.rememberDateOrder
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

            // ── "The basics" now talks to the backend ───────────────────────
            //
            // The code screen sends, waits, counts down against a SERVER timestamp and retries,
            // which is the moment the Android CLAUDE.md names for introducing a ViewModel. The
            // hand-rolled saveable state that used to live here could not own a request in
            // flight; viewModelScope can, and cancels it with the screen.
            //
            // The api is built once per Activity, not per recomposition, and its tokens come from
            // the encrypted store -- a refresh token is a durable credential.
            val context = LocalContext.current
            // One store, two models: the sign-up flow WRITES the tokens and the profile flow
            // reads them through the interceptor. Sharing the instance is what makes that work.
            val tokenStore = remember(context) { EncryptedTokenStore(context) }
            val api = remember(tokenStore) { ShowUpApi(tokens = tokenStore) }

            val phoneAuth: PhoneAuthViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PhoneAuthViewModel(PhoneAuthRepository(api, tokenStore)) }
                },
            )

            val basics: BasicsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        BasicsViewModel(BasicsRepository(api))
                    }
                },
            )
            // collectAsStateWithLifecycle, never bare collectAsState: the latter keeps collecting
            // while the app is backgrounded, which for a countdown means burning a wakelock to
            // update a screen nobody is looking at.
            val basicsState by basics.state.collectAsStateWithLifecycle()
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
                    FlowScreen.SignUp -> SignUpFlow(auth = phoneAuth, onFinished = {
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
                    FlowScreen.ProfileEmail -> ProfileEmailScreen(
                        value = email,
                        onValueChange = {
                            email = it
                            basics.setEmail(it)
                        },
                        consent = marketingConsent,
                        onConsentChange = { marketingConsent = it },
                        // Continue SENDS the code and only advances once the server has accepted
                        // it. Navigating first would put the user on a screen waiting for a code
                        // that was never dispatched.
                        onContinue = {
                            email = it
                            basics.setEmail(it)
                            basics.sendCode(onSent = { screen = FlowScreen.ProfileVerifyEmail })
                        },
                        serverError = if (basicsState.emailInUse) {
                            EmailCopy.ALREADY_IN_USE_PROPOSED
                        } else if (basicsState.transportFailed) {
                            EmailCopy.SEND_FAILED_PROPOSED
                        } else {
                            null
                        },
                        onBack = { screen = FlowScreen.ProfileName },
                    )

                    // SHOWUP-153. Every number on this screen is the server's: the cooldown
                    // counts down to `resendAvailableAt`, the expiry compares against
                    // `expiresAt`, and the attempt cap is raised to the cap by a 401 that says
                    // so. Nothing here is simulated any more.
                    FlowScreen.ProfileVerifyEmail -> ProfileVerifyEmailScreen(
                        email = basicsState.email.ifEmpty { email },
                        digits = basicsState.codeDigits,
                        onDigitsChange = basics::setDigits,
                        state = basicsState.failure(OffsetDateTime.now()),
                        shakeKey = basicsState.shakeKey,
                        cooldownSeconds = basicsState.cooldownSeconds,
                        busy = basicsState.busy,
                        onVerify = { basics.verify(onVerified = { screen = FlowScreen.ProfileDob }) },
                        onResend = { basics.sendCode() },
                        // Both exits are the same journey: back to the email step, address kept.
                        onChangeEmail = { screen = FlowScreen.ProfileEmail },
                        onBack = { screen = FlowScreen.ProfileEmail },
                    )

                    // SHOWUP-154. Continue writes the date AND the visibility choice in one
                    // PATCH and only advances when the server has stored them. Back does not
                    // re-send or re-verify anything -- the code screen is already satisfied.
                    FlowScreen.ProfileDob -> ProfileDobScreen(
                        value = basicsState.dob,
                        onValueChange = basics::setDob,
                        order = rememberDateOrder(),
                        hideAge = basicsState.hideAge,
                        onHideAgeChange = basics::setHideAge,
                        attempted = basicsState.dobAttempted,
                        busy = basicsState.busy,
                        serverRejectedAge = basicsState.serverRejectedAge,
                        onContinue = { valid ->
                            basics.saveDateOfBirth(valid.iso, onSaved = { screen = FlowScreen.Home })
                        },
                        onRefused = { basics.markDobAttempted() },
                        onEdit = { basics.clearDob() },
                        onBack = { screen = FlowScreen.ProfileVerifyEmail },
                    )
                    FlowScreen.Home ->
                        HomePlaceholderScreen(outcome, onStartOver = { screen = FlowScreen.SignUp })
                }
            }
        }
    }
}
