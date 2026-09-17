package com.showup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.showup.designsystem.Motion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
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
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.showup.api.EncryptedTokenStore
import com.showup.api.ShowUpApi
import com.showup.profile.AndroidPhotoAccess
import com.showup.profile.BasicsRepository
import com.showup.profile.CameraAccess
import com.showup.profile.CameraAskLog
import com.showup.profile.PickedBytes
import com.showup.profile.PhotoSource
import com.showup.profile.PhotosRepository
import com.showup.profile.PhotosViewModel
import com.showup.profile.ProfilePhotosScreen
import com.showup.profile.ProfilePromptsScreen
import com.showup.profile.ProfileProgressRepository
import com.showup.profile.PromptsRepository
import com.showup.profile.ResumePoint
import com.showup.profile.resumePoint
import com.showup.profile.ProfileScreen
import com.showup.profile.PromptsViewModel
import com.showup.profile.UPLOAD_JPEG_QUALITY
import com.showup.profile.uploadTargetSize
import com.showup.welcome.PhoneAuthRepository
import com.showup.welcome.PhoneAuthViewModel
import com.showup.profile.BasicsViewModel
import com.showup.profile.EmailCopy
import com.showup.profile.ProfileDobScreen
import com.showup.profile.ProfileEmbraceScreen
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Manrope
import com.showup.designsystem.Spacing
import com.showup.profile.DevOfflineBasics

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

            // ── "The real you" ──────────────────────────────────────────────
            //
            // Its own ViewModel for the same reason as the one above, and a sharper one: this
            // screen holds several uploads at once, each of which has to be cancellable on its
            // own. `readBytes` is a lambda so the ViewModel knows nothing about ContentResolver
            // and can be driven from a JVM test.
            val photos: PhotosViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        PhotosViewModel(
                            repo = PhotosRepository(api),
                            access = AndroidPhotoAccess(context),
                            readBytes = { uri -> readPickedImage(context, uri) },
                        )
                    }
                },
            )
            val photosState by photos.state.collectAsStateWithLifecycle()

            // SHOWUP-158 has a ViewModel now, and the comment that used to sit here said when it
            // would: "it becomes a ViewModel the day persistence lands." `/me/prompts` exists, so
            // saving a prompt is a request that has to outlive a redraw and be cancellable.
            //
            // The saved prompts come from the server; the half-written draft lives in the
            // SavedStateHandle, because a draft is not a prompt and there is nothing to send.
            // One call, made once, and only when there is a session to make it with. The
            // repository answers null for a 401, which routes to the flow's own entry point.
            val progressRepo = remember(api) { ProfileProgressRepository(api) }

            val prompts: PromptsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        PromptsViewModel(
                            repo = PromptsRepository(api),
                            saved = createSavedStateHandle(),
                        )
                    }
                },
            )
            val promptsState by prompts.state.collectAsStateWithLifecycle()

            // ── resuming a half-finished profile (flow rule 4a) ─────────────
            //
            // "On launch, an account with an incomplete profile routes straight to its last
            // incomplete step, with everything already entered still present."
            //
            // RESUMING IS SILENT. No prompt, no toast, no "welcome back" -- the user lands on the
            // step, and a resumed step behaves like a freshly reached one, which is why nothing
            // here sets an error or an attempted flag.
            //
            // Asked ONCE per launch, not on every recomposition, and only while the screen is
            // still the flow's entry point: a user who has already walked somewhere must not be
            // yanked back by a late answer.
            var resumeChecked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (resumeChecked) return@LaunchedEffect
                resumeChecked = true
                val progress = progressRepo.fetch() ?: return@LaunchedEffect
                if (screen != FlowScreen.SignUp) return@LaunchedEffect
                // Everything already entered, still present.
                firstName = progress.displayName.orEmpty()
                email = progress.email.orEmpty()
                basics.setEmail(progress.email.orEmpty())
                screen = when (resumePoint(progress)) {
                    ResumePoint.Name -> FlowScreen.ProfileName
                    ResumePoint.Email -> FlowScreen.ProfileEmail
                    ResumePoint.VerifyEmail -> FlowScreen.ProfileVerifyEmail
                    ResumePoint.Dob -> FlowScreen.ProfileDob
                    // NEVER the bridge. SHOWUP-155: "relaunching lands on photos and not on this
                    // bridge" -- it is a beat on the forward walk, not a place to return to.
                    ResumePoint.Photos -> FlowScreen.ProfilePhotos
                    ResumePoint.Prompts -> FlowScreen.ProfilePrompts
                    ResumePoint.Done -> FlowScreen.Home
                }
                if (screen == FlowScreen.ProfilePrompts) prompts.load()
            }

            // RE-READ THE PERMISSION STATUS ON EVERY FOREGROUND. The most common bug on the photo
            // screen is a user who granted access in Settings returning to the blocked card, and
            // ON_RESUME is the only moment that can be noticed.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) photos.refreshAccess()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // THE SYSTEM PICKER. No permission is requested and none is declared: it runs out of
            // process, shows the user their whole library, and returns only what they chose.
            val pickImage = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri -> if (uri != null) photos.picked(uri.toString(), PhotoSource.Library) }

            // The camera writes into a file we create, shared through the FileProvider for the
            // life of the intent. The URI has to exist before the intent is sent, which is why it
            // is remembered across the launch rather than produced by the result.
            var cameraTarget by remember { mutableStateOf<Uri?>(null) }
            val takePhoto = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { saved ->
                val target = cameraTarget
                if (saved && target != null) photos.picked(target.toString(), PhotoSource.Camera)
                cameraTarget = null
            }
            val launchCamera = {
                val target = newCameraTarget(context)
                cameraTarget = target
                takePhoto.launch(target)
            }
            val askCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                photos.refreshAccess()
                if (granted) launchCamera()
            }
            val motion = rememberMotion()

            // The system back gesture mirrors the on-screen Back, so hardware back never drops
            // someone out of the tutorial from the middle of it. On card 1 it is left alone, so
            // back exits as usual, and the range stops at 6 so back cannot walk out of the app
            // and into the last tutorial card.
            BackHandler(enabled = screen.hasSystemBack) {
                screen = FlowScreen.entries[screen.ordinal - 1]
            }

            Box(Modifier.fillMaxSize()) {
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
                            basics.saveDateOfBirth(
                                valid.iso,
                                onSaved = { screen = FlowScreen.ProfileEmbrace },
                            )
                        },
                        onRefused = { basics.markDobAttempted() },
                        onEdit = { basics.clearDob() },
                        onBack = { screen = FlowScreen.ProfileVerifyEmail },
                    )
                    // SHOWUP-155. The bridge out of "The basics". No header, no progress bar,
                    // no back -- the absences are the design, and the screen swallows the system
                    // gesture itself. Its one exit is forward.
                    FlowScreen.ProfileEmbrace -> ProfileEmbraceScreen(
                        firstName = firstName,
                        onContinue = { screen = FlowScreen.ProfilePhotos },
                    )

                    // SHOWUP-156. Every state on this screen is real: uploads run, report their
                    // own progress and can fail, and the count only moves when one is confirmed.
                    // The picker, the camera UI and the permission alert are the OS's and none of
                    // them is drawn here.
                    FlowScreen.ProfilePhotos -> ProfilePhotosScreen(
                        state = photosState.grid,
                        library = photosState.library,
                        camera = photosState.camera,
                        sheetOpen = photosState.sheetOpen,
                        onBack = { screen = FlowScreen.ProfileEmbrace },
                        onSlotTap = photos::tapSlot,
                        onRemove = photos::remove,
                        onRetry = photos::retry,
                        onRevealOptional = photos::revealOptional,
                        onReorder = photos::reorder,
                        onChooseLibrary = {
                            pickImage.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onChooseCamera = {
                            if (photosState.camera == CameraAccess.Granted) {
                                launchCamera()
                            } else {
                                // Recorded BEFORE the prompt, because the record is what tells a
                                // later "no rationale" apart from a first run -- see CameraAskLog.
                                CameraAskLog.recordAsked(context)
                                askCamera.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        onCameraSettings = { openAppSettings(context) },
                        onDismissSheet = photos::dismissSheet,
                        onOpenSettings = { openAppSettings(context) },
                        onContinue = { screen = FlowScreen.ProfilePrompts },
                    )

                    // SHOWUP-158. Two sheets, one screen, and every transition between them is a
                    // change to the one value the ViewModel owns.
                    FlowScreen.ProfilePrompts -> ProfilePromptsScreen(
                        state = promptsState,
                        onBack = { screen = FlowScreen.ProfilePhotos },
                        onOpenTopics = prompts::openTopics,
                        onWriteTopic = prompts::writeSuggestion,
                        onPickTopic = prompts::pickTopic,
                        onEditPrompt = prompts::editPrompt,
                        onDraftChange = prompts::draftChanged,
                        onHideExample = prompts::hideExample,
                        onSave = prompts::save,
                        onDismissSheet = prompts::dismissSheet,
                        // The ViewModel decides and records; the host only routes. Continue is
                        // never disabled, so the refused press is a real press with a real event
                        // behind it rather than a button that did nothing.
                        onContinue = { if (prompts.continuePressed()) screen = FlowScreen.Home },
                        // The SAME call on the refused press, which is what makes the two
                        // mutually exclusive: the screen picks a branch, the ViewModel re-checks
                        // and records whichever one it was. Wiring only the accepted branch would
                        // leave the refusal -- the one record that a user tried to leave -- with
                        // nothing to fire it.
                        onRefused = { prompts.continuePressed() },
                    ).also {
                        // Reads what the account already holds, and reports the arrival. Both are
                        // idempotent -- the ViewModel keeps the answer and holds the step's start
                        // time -- and arriving from photos or from a resume both land here.
                        LaunchedEffect(Unit) {
                            prompts.arrived(referrer = ProfileScreen.Photos)
                            prompts.load()
                        }
                    }

                    FlowScreen.Home ->
                        HomePlaceholderScreen(outcome, onStartOver = { screen = FlowScreen.SignUp })
                }
            }

            // The email code, on screen, in a debug build only.
            //
            // The phone flow has had this since the day it stopped faking its code; the email
            // flow never did, because `/auth/email/start` answers 204 with no body and the
            // challenge carries no `devCode` to show. With no backend running that left the
            // verification screen unwalkable: a code was required and nothing anywhere could
            // tell you what it was.
            //
            // Three conditions, each closing a different way this could leak: BuildConfig.DEBUG
            // keeps it out of any release build, the null check keeps it absent when the code
            // came from a real server rather than the offline stand-in, and the screen check
            // keeps it off every other screen.
            val offlineEmailCode = DevOfflineBasics.lastIssued
            if (BuildConfig.DEBUG &&
                offlineEmailCode != null &&
                screen == FlowScreen.ProfileVerifyEmail
            ) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = Spacing.xs)
                        .background(Color(0xE61D1129), RoundedCornerShape(50))
                        .padding(horizontal = Spacing.xl, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "TEST BUILD", color = Color(0xFFFFAE8F), fontFamily = Manrope,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.7.sp,
                    )
                    Text(
                        "OFFLINE · no server · the code is $offlineEmailCode",
                        color = Color.White, fontFamily = Manrope,
                        fontWeight = FontWeight.Medium, fontSize = 11.sp,
                    )
                }
            }
            }
        }
    }
}

// ── the platform edges the photo step needs ─────────────────────────────────
//
// Three small functions rather than three lines inside a composable, because each one is a
// PLATFORM contract with a reason attached, and none of them is about layout.

/**
 * Reads a picked image into memory.
 *
 * ON [Dispatchers.IO], because a content URI can point at a file on a network share, a cloud
 * provider or a slow SD card -- `openInputStream` is I/O whatever the URI looks like.
 *
 * WHOLE-FILE, AND THAT IS A REAL LIMIT. A modern phone photo is 3-8 MB, which is fine; a 50 MB
 * panorama is not, and would be held twice over while the multipart body copies it. Streaming
 * straight from the resolver into the request body would fix that and is the right shape once
 * `PhotosRepository` needs to retry -- a stream can only be read once, so a retry would need the
 * URI rather than the bytes. Written down rather than pre-built.
 *
 * Returns null when the URI cannot be read at all: a revoked grant, or a file deleted between
 * picking and reading. The caller shows the failed slot, which is what a user can act on.
 */
/**
 * Reads a picked image and normalises it to something the server will actually accept.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO REAL FAILURES THIS FIXES, BOTH OF WHICH LOOKED IDENTICAL TO THE USER
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This used to send the picked file's bytes untouched, with whatever MIME type the content
 * resolver reported. On a modern Android phone that is `image/heif` or `image/heic`, and the
 * server's `ALLOWED_TYPES` carries jpeg, png and webp -- so the upload came back 415. A full
 * resolution photo also runs past the server's 8 MB cap and came back 413.
 *
 * Both surfaced on screen as `Upload failed` with a `Retry` that could never succeed, because
 * retrying re-sent exactly the same bytes. The slot's one failure state is the right design -- a
 * user can do nothing different about a 500 than about a dropped connection -- but it is only
 * honest when the failure is actually transient, and neither of these was.
 *
 * DECODED DOWNSAMPLED, NOT DECODED AND THEN SHRUNK. `ImageDecoder` applies the target size while
 * it reads, so a 12 MP photo never exists in memory at 12 MP. Decoding first and scaling after
 * would allocate roughly 48 MB for an image we are about to throw away, which on a device already
 * short of memory is the difference between a slow screen and a dead one.
 *
 * It also APPLIES EXIF ORIENTATION for us, which matters because we re-encode: `BitmapFactory`
 * would have handed back the raw pixels and dropped the rotation tag with them, so every photo
 * taken in portrait would have uploaded on its side.
 *
 * `ALLOCATOR_SOFTWARE` because a hardware bitmap has no pixels in application memory and cannot be
 * compressed; the default allocator would make `compress` fail on exactly the devices that support
 * it.
 */
private suspend fun readPickedImage(context: Context, uri: String): PickedBytes? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // The arithmetic is in `uploadTargetSize`, where a JVM test can reach it. Null
                // means the photo is already small enough and the decoder is left alone.
                uploadTargetSize(info.size.width, info.size.height)?.let { (w, h) ->
                    decoder.setTargetSize(w, h)
                }
            }
            val out = ByteArrayOutputStream()
            // CHECKED, NOT ASSUMED. `compress` returns false when it could not encode -- a
            // bitmap in a config JPEG cannot represent, or a device that has just run out of
            // memory, which is exactly the condition this whole function exists to be careful
            // about. Ignoring it would upload an empty body, and an empty body is a 400 that
            // reads on screen as the same `Upload failed` as everything else.
            val encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, out)
            bitmap.recycle()
            if (!encoded || out.size() == 0) return@runCatching null
            // ALWAYS JPEG, whatever came in. The server names three types it accepts and this is
            // the one every path can produce; the extension matches so a person reading a log sees
            // the truth. NEVER the library's own display name -- that is the user's filename and
            // is not ours to send.
            PickedBytes(bytes = out.toByteArray(), mimeType = "image/jpeg", fileName = "photo.jpg")
        }.getOrNull()
    }

/**
 * A file for the camera to write into, shared through the FileProvider.
 *
 * In the cache, because a capture is read once and uploaded. `file_paths.xml` exposes this one
 * directory and nothing else.
 */
private fun newCameraTarget(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture-" + System.currentTimeMillis() + ".jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".photos", file)
}

/**
 * Opens this app's settings page.
 *
 * THE APP'S PAGE, NOT A PERMISSION TOGGLE. Neither platform can deep-link to a single row, which
 * is exactly why the blocked copy NAMES the row the user has to find -- the copy and this function
 * are two halves of one decision.
 */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
