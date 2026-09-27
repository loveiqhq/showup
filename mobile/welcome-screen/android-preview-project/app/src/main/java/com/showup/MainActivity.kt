package com.showup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.showup.designsystem.Motion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.Manifest
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.showup.tutorial.MatchMeansMeetScreen
import com.showup.tutorial.MatchOnAvailabilityScreen
import com.showup.tutorial.MeetInRealLifeScreen
import com.showup.tutorial.ShowUpEveryTimeScreen
import com.showup.tutorial.ThirtyMinutesScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.showup.api.EncryptedTokenStore
import com.showup.api.ShowUpApi
import com.showup.profile.NoConsentBackend
import com.showup.profile.ProfileReachabilityScreen
import com.showup.profile.ReachabilityHost
import com.showup.profile.ReachabilityViewModel
import android.os.Build
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import com.showup.profile.AndroidMediaAccess
import com.showup.profile.AndroidMediaCaptureFactory
import com.showup.profile.AndroidMediaPlayer
import com.showup.profile.AndroidPhotoAccess
import com.showup.profile.BasicsRepository
import com.showup.profile.CameraAccess
import com.showup.profile.CameraAskLog
import com.showup.profile.MediaCapability
import com.showup.profile.MediaCaptureScreen
import com.showup.profile.MediaEntryPoint
import com.showup.profile.MediaKind
import com.showup.profile.MediaPermission
import com.showup.profile.MediaRepository
import com.showup.profile.MediaViewModel
import com.showup.profile.PermissionAskLog
import com.showup.profile.PickedBytes
import com.showup.profile.PhotoSource
import com.showup.profile.PhotosRepository
import com.showup.profile.PhotosViewModel
import com.showup.profile.AndroidNotificationAccess
import com.showup.profile.ProfileNotificationsScreen
import com.showup.profile.PushRegistration
import com.showup.profile.shouldShowAsk
import com.showup.profile.NotificationsViewModel
import com.showup.profile.ProfileMediaScreen
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

            // ── the media step (SHOWUP-161) ─────────────────────────────────
            //
            // CameraX binds to a LIFECYCLE, so the controller is created here rather than inside
            // the capture screen: a controller rebuilt on every recomposition would tear the
            // camera session down and put it back up mid-take. It is bound once and handed to the
            // viewfinder, and `AndroidMediaCaptureFactory` reads it through a lambda so a session
            // created before the first bind still finds it.
            val cameraController = remember(context) { LifecycleCameraController(context) }
            val captureLifecycle = LocalLifecycleOwner.current

            /**
             * Whether the camera may be opened at all.
             *
             * STATE, AND KEYED INTO THE BIND BELOW, because this starts FALSE on a real install
             * and becomes true in the middle of the flow -- and nothing was re-binding when it
             * did. See the effect.
             */
            var cameraGranted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            // Re-read on every foreground: the permission can also be granted in Settings and
            // revoked there, and a controller bound against a revoked camera is the same dead
            // viewfinder in the other direction.
            DisposableEffect(captureLifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        cameraGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                }
                captureLifecycle.lifecycle.addObserver(observer)
                onDispose { captureLifecycle.lifecycle.removeObserver(observer) }
            }

            DisposableEffect(captureLifecycle, cameraGranted) {
                // VIDEO_CAPTURE IS ENABLED HERE, AT BIND TIME, AND NOWHERE ELSE.
                //
                // `CameraController`'s default is IMAGE_CAPTURE | IMAGE_ANALYSIS -- video is NOT
                // in it. Enabling it inside `startRecording`'s caller, which is what this used to
                // do, asks CameraX to REBIND the camera session and then starts a recording on
                // the use case that rebind is still creating. The recording attaches to nothing,
                // the encoder never writes, and `finish` finds a zero-byte file and returns null.
                //
                // From the user's side that is: record a video, press stop, land back on the card
                // with no take and no review screen -- which is exactly what a real device did.
                // Voice was unaffected because `MediaRecorder` has nothing to do with CameraX.
                //
                // Enabled before `bindToLifecycle`, the use case is part of the first bind and
                // there is no second one to race.
                //
                // ── AND ONLY WHEN THE PERMISSION IS HELD ─────────────────────
                //
                // `bindToLifecycle` is `@RequiresPermission(CAMERA)`. This used to run once, at
                // composition, keyed on the lifecycle alone -- which on a REAL INSTALL is before
                // the user has granted anything. The camera could not open, and nothing bound
                // again when the grant arrived seconds later, so the controller spent the rest of
                // the session bound to a camera that was never opened.
                //
                // `isVideoCaptureEnabled` cannot see that: it describes the CONFIGURED USE CASES,
                // not whether a camera is open, so the guard in `AndroidVideoSession.start` read
                // true and the recording attached to nothing. The encoder wrote no bytes, `finish`
                // found a zero-length file and returned null, and the user was returned to the
                // card with no review screen and no explanation -- after filming for ten seconds.
                // Voice was unaffected throughout because `MediaRecorder` is not CameraX.
                //
                // Keying the effect on the grant is the whole fix: the moment it flips, this
                // disposes and binds again, with permission.
                //
                // IT IS ALSO THE RIGHT SCOPE FOR A SECOND REASON. Binding at composition opened
                // the front camera for the entire app session, which on Android 12 and above
                // lights the green camera indicator the whole time somebody is editing their
                // profile. A camera that is open when nothing is filming is a privacy defect
                // whatever else it does.
                if (cameraGranted) {
                    cameraController.setEnabledUseCases(CameraController.VIDEO_CAPTURE)
                    cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraController.bindToLifecycle(captureLifecycle)
                }
                onDispose { if (cameraGranted) cameraController.unbind() }
            }

            // ── the notification ask (SHOWUP-162) ────────────────────────────────────
            //
            // THE STATUS IS READ BEFORE THE SCREEN IS PUSHED, NEVER AFTER IT MOUNTS. Both skip
            // cases -- Android 12 and below, and a status that is somehow already determined --
            // are decided here, so the user never sees the screen mount and navigate away. No
            // toast, no confirmation, no flash: `shouldShowAsk` is a pure function and the
            // navigation either goes through the ask or does not.
            val notifications = remember(context) { AndroidNotificationAccess(context) }

            val reachModel: ReachabilityViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ReachabilityViewModel(
                            access = notifications,
                            push = PushRegistration(api),
                            // No endpoint for the push consent yet -- see ReachabilityRepository.
                            // No sink is wired in this host yet: every profile view model
                            // here reports to nothing, which is brief Step 2 and its own ticket.
                            consent = NoConsentBackend,
                        )
                    }
                },
            )

            val notifyModel: NotificationsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        NotificationsViewModel(
                            access = notifications,
                            push = PushRegistration(api),
                        )
                    }
                },
            )

            /**
             * Where media's Continue and Skip both land.
             *
             * 09 IS STILL SKIPPED ON A DETERMINED STATUS, and 10 never is. SHOWUP-163 leaves the
             * guard alone -- "unchanged on 09: the Android <= 12 / already-determined skip guard"
             * -- so a user whose permission is already settled goes straight past the explainer
             * to Stay reachable, which is the screen that can actually act on it.
             */
            fun afterMedia(): FlowScreen {
                val status = notifications.read()
                if (shouldShowAsk(status)) return FlowScreen.ProfileNotifications
                // THE SKIPPED USER STILL NEEDS A TOKEN. Below API 33 notifications are on with
                // nothing to ask for, and the only thing that registered for push was a callback
                // on the screen those users never see. See NotificationsViewModel.skipped.
                // NO SKIP-PATH REGISTRATION ANY MORE, and that is SHOWUP-163 undoing a fix
                // from earlier the same day rather than a regression.
                //
                // The old problem was that an Android <= 12 user never saw 09 and so never hit
                // the grant callback that registered for push. Every user reaches Stay reachable
                // now, and IT registers -- as part of the save, after the consent, which is the
                // better place: registration and consent land together or not at all.
                // Registering here as well would simply do it twice.
                return FlowScreen.ProfileReachability
            }

            // ── the OS notification dialog, now raised by Stay reachable (SHOWUP-163) ──
            //
            // IT ANSWERS A SUSPEND FUNCTION. `ReachabilityViewModel.savePressed` has to WAIT for
            // the answer -- the ticket's order is "raise the OS dialog and wait", then register,
            // then commit, then advance -- and a launcher callback cannot be awaited. So the
            // callback completes a deferred the suspend side is sitting on.
            //
            // The launcher is declared here, above the `when`, so its callback survives the
            // navigation it triggers. That was already the rule when 09 owned it.
            var permissionAnswer by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                // RECORDED WHEN IT IS ANSWERED, never before the launch. Android cannot tell
                // "never asked" from "refused" -- both read as not-granted -- so `PermissionAskLog`
                // is the proxy, and recording it before the dialog is answered makes a liar of it:
                // a user who raises the sheet and backgrounds the app without answering would come
                // back recorded as denied while the OS status is still not determined.
                PermissionAskLog.recordAsked(context, Manifest.permission.POST_NOTIFICATIONS)
                permissionAnswer?.complete(granted)
                permissionAnswer = null
            }

            // The player, held here for the same reason the camera controller is: it owns a
            // hardware resource that outlives a recomposition and has to be released when the flow
            // leaves, and the video surfaces attach to it.
            val mediaPlayer = remember(context) { AndroidMediaPlayer(context) }
            DisposableEffect(mediaPlayer) { onDispose { mediaPlayer.release() } }

            val media: MediaViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MediaViewModel(
                            repo = MediaRepository(api),
                            access = AndroidMediaAccess(context),
                            capture = AndroidMediaCaptureFactory(context) { cameraController },
                            player = mediaPlayer,
                        )
                    }
                },
            )
            val mediaState by media.state.collectAsStateWithLifecycle()

            // Which prompt the user committed to, held across the permission round trip: the OS
            // alert takes the app out of the foreground, and the answer comes back with no memory
            // of what it was asked for.
            var pendingTake by remember { mutableStateOf<Pair<MediaKind, String>?>(null) }
            val askCapture = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { _ ->
                // THE GRANT IS READ BACK BEFORE THE TAKE BEGINS. `permissionResult` goes straight
                // on to `beginTake`, so this is the moment the bind above has to learn that the
                // camera may now be opened -- and `AndroidVideoSession.start` waits for it to
                // actually be open rather than assuming this recomposition has landed.
                cameraGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                val pending = pendingTake
                pendingTake = null
                if (pending != null) media.permissionResult(pending.first, pending.second)
            }

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
            //
            // BOTH SCREENS, one observer. SHOWUP-161 requires the same of camera and microphone --
            // "returning from Settings with access granted lands on the working card, never on the
            // blocked row" -- and a second observer for the second screen would be a second place
            // to forget. Neither call touches the network and both are cheap.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        photos.refreshAccess()
                        media.refreshAccess()
                    }
                    // AND STOP PLAYING WHEN THE SCREEN GOES AWAY.
                    //
                    // Nothing else does. `onDispose` releases the player when the composable
                    // leaves, and backgrounding the app does not dispose anything -- the process
                    // lives, ExoPlayer keeps its renderer thread, and a voice note the user
                    // started plays on over whatever they switched to. iOS is saved from the same
                    // bug only by not declaring the background-audio capability.
                    //
                    // ON_STOP rather than ON_PAUSE: a permission alert over the activity pauses it
                    // and is not the user leaving, and this screen can raise one.
                    if (event == Lifecycle.Event.ON_STOP) {
                        media.stopPlayback()
                    }
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
                    ).also {
                        // Reads what the account already holds. Without it the grid started empty
                        // on every launch, and an account already at the server's six-photo limit
                        // answered the next upload with a 400 the slot could only render as
                        // `Upload failed` -- with a Retry that re-sent the same bytes to the same
                        // full account.
                        LaunchedEffect(Unit) { photos.load() }
                    }

                    // SHOWUP-158. Two sheets, one screen, and every transition between them is a
                    // change to the one value the ViewModel owns.
                    FlowScreen.ProfilePrompts -> ProfilePromptsScreen(
                        state = promptsState,
                        onBack = { screen = FlowScreen.ProfilePhotos },
                        onOpenTopics = prompts::openTopics,
                        // THE SAME FUNCTION TWICE, and that is the point: a suggestion card
                        // and a row of the browse sheet are the same act now. Registry 1.4.5
                        // retired the property that told them apart.
                        onWriteTopic = prompts::chooseTopic,
                        onPickTopic = prompts::chooseTopic,
                        onEditPrompt = prompts::editPrompt,
                        onDraftChange = prompts::draftChanged,
                        onHideExample = prompts::hideExample,
                        onSave = prompts::save,
                        onDismissSheet = prompts::dismissSheet,
                        // The ViewModel decides and records; the host only routes. Continue is
                        // never disabled, so the refused press is a real press with a real event
                        // behind it rather than a button that did nothing.
                        // Into the media step, which is where "The real you" actually ends.
                        onContinue = {
                            if (prompts.continuePressed()) screen = FlowScreen.ProfileMedia
                        },
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

                    // SHOWUP-161. One position in the flow, two surfaces: the media screen,
                    // and the full-bleed viewfinder that replaces it while a take is running.
                    // The viewfinder is NOT its own FlowScreen -- it has no entry point of its own
                    // and no way back except Cancel, so it is a state of this step rather than a
                    // place the router can send anyone.
                    FlowScreen.ProfileMedia -> {
                        // ARRIVAL IS KEYED TO THE STEP, NOT TO THE SURFACE.
                        //
                        // This used to sit on the media screen inside the `else` below, which made
                        // it re-run every time a take ended: the branch was removed while the
                        // viewfinder was up and composed again on the way back, restarting the
                        // effect. That fired a second `screen_viewed`, a second PAIR of
                        // `profile_step_viewed`, and reset the step's start time -- so
                        // `time_on_step_s` measured from the last take rather than from arrival,
                        // and the step funnel counted one entry per recording.
                        //
                        // Up here the effect belongs to the position in the flow. Returning from a
                        // take does not re-enter the step, and `acceptTake` already reports the new
                        // entry state itself.
                        LaunchedEffect(Unit) { media.arrived() }
                        val take = mediaState.take
                        if (take != null) {
                            MediaCaptureScreen(
                                take = take,
                                onCancel = media::cancelTake,
                                onStop = media::stopPressed,
                                onPlay = media::playPressed,
                                onRetake = media::retakeFromReview,
                                onAccept = media::acceptTake,
                                cameraController = cameraController,
                                playback = mediaState.playback,
                                player = mediaPlayer.surface,
                            )
                        } else {
                            ProfileMediaScreen(
                                state = mediaState,
                                onBack = {
                                    media.stopPlayback()
                                    screen = FlowScreen.ProfilePrompts
                                },
                                onOpenPrompts = media::openPrompts,
                                onPickPrompt = media::pickPrompt,
                                onCommitPrompt = {
                                    // The view model records the selection and answers with what
                                    // still has to be requested. Asking happens HERE, on the commit
                                    // CTA -- not on entry, and not on `See the prompts`.
                                    val sheet = mediaState.sheet
                                    val missing = media.commitPrompt()
                                    if (missing.isNotEmpty() && sheet?.selectedId != null) {
                                        pendingTake = sheet.kind to sheet.selectedId!!
                                        missing.forEach {
                                            PermissionAskLog.recordAsked(context, it.permission)
                                        }
                                        askCapture.launch(missing.map { it.permission }.toTypedArray())
                                    }
                                },
                                onDismissSheet = media::dismissPrompts,
                                // WIRED. This was not passed at all, so the play control the design
                                // draws on every filled card fell through to the default no-op.
                                onPlay = media::cardPlayPressed,
                                player = mediaPlayer.surface,
                                onRetake = media::retakeFromCard,
                                onDelete = media::delete,
                                onRetryUpload = media::retryUpload,
                                onPermissionAction = { capability, status ->
                                    if (status == MediaPermission.CanAsk) {
                                        // Re-prompts IN APP and names no toggle: the user never
                                        // has to go and find one.
                                        PermissionAskLog.recordAsked(context, capability.permission)
                                        askCapture.launch(arrayOf(capability.permission))
                                    } else {
                                        // Permanently denied. Neither platform deep-links to a
                                        // single permission row, so this opens our app's own page
                                        // and the copy names the row to look for.
                                        openAppSettings(context)
                                    }
                                },
                                platformLabel = media::platformLabel,
                                onSkip = {
                                    // Sound does not follow the user off the screen.
                                    media.stopPlayback()
                                    media.skipPressed()
                                    screen = afterMedia()
                                },
                                onContinue = {
                                    media.stopPlayback()
                                    media.continuePressed()
                                    screen = afterMedia()
                                },
                            )
                        }
                    }

                    FlowScreen.ProfileNotifications -> {
                        // `screen_viewed` only. SHOWUP-163 moved `permission_prompted` and the
                        // two OS-dialog events to Stay reachable, which is where the dialog is
                        // raised now -- see NotificationsViewModel.arrived.
                        LaunchedEffect(Unit) { notifyModel.arrived() }

                        // NO FOREGROUND RE-READ AND NO SELF-ADVANCE ANY MORE.
                        //
                        // Both existed because this screen's only button raised a dialog that the
                        // OS shows once per install: a status that became determined while it was
                        // mounted left a dead CTA, so it had to advance by itself. It raises
                        // nothing now, so `Continue` always works and there is no dead state to
                        // escape. The re-read that matters moved to 10, where it drives a toggle.
                        val sheetUp by notifyModel.sheetUp.collectAsStateWithLifecycle()

                        ProfileNotificationsScreen(
                            busy = sheetUp,
                            onEnable = {
                                // False on a second press, and nothing is reported for it.
                                if (notifyModel.continuePressed()) {
                                    screen = FlowScreen.ProfileReachability
                                }
                            },
                        )
                    }

                    // ── Stay reachable (SHOWUP-163) ─────────────────────────
                    FlowScreen.ProfileReachability -> {
                        val reachState by reachModel.state.collectAsStateWithLifecycle()

                        // THE HOST DOES THE TWO THINGS ONLY AN ACTIVITY CAN: raise the dialog and
                        // leave for Settings. Everything else is in the view model, which is what
                        // lets every rule on this screen be tested with no device.
                        val reachScope = rememberCoroutineScope()
                        DisposableEffect(reachModel) {
                            reachModel.attach(
                                object : ReachabilityHost {
                                    override suspend fun requestNotificationPermission(): Boolean {
                                        // Below API 33 there is no runtime permission to ask for
                                        // and notifications are already on.
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                            return true
                                        }
                                        val answer = CompletableDeferred<Boolean>()
                                        permissionAnswer = answer
                                        askNotifications.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                        return answer.await()
                                    }

                                    override fun openSettingsOrReprompt() {
                                        // ANDROID CAN SOMETIMES STILL ASK, and when it can, an
                                        // in-app dialog is a far shorter path than Settings. The
                                        // ticket allows either and the event is the same.
                                        val canAsk = Build.VERSION.SDK_INT >=
                                            Build.VERSION_CODES.TIRAMISU &&
                                            !PermissionAskLog.hasAsked(
                                                context, Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        if (canAsk) {
                                            reachScope.launch {
                                                val answer = CompletableDeferred<Boolean>()
                                                permissionAnswer = answer
                                                askNotifications.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                                answer.await()
                                                reachModel.foregrounded()
                                            }
                                        } else {
                                            openAppSettings(context)
                                        }
                                    }
                                },
                            )
                            onDispose { }
                        }

                        LaunchedEffect(Unit) { reachModel.arrived() }

                        // The status is re-read on every foreground, so returning from Settings
                        // with notifications allowed shows the toggle on. NOTHING AUTO-ADVANCES:
                        // the user taps Save preferences again and no dialog is raised.
                        val reachLifecycle = LocalLifecycleOwner.current
                        DisposableEffect(reachLifecycle) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) reachModel.foregrounded()
                            }
                            reachLifecycle.lifecycle.addObserver(observer)
                            onDispose { reachLifecycle.lifecycle.removeObserver(observer) }
                        }

                        ProfileReachabilityScreen(
                            state = reachState,
                            onPushChange = reachModel::pushChanged,
                            onInterestToggle = reachModel::interestToggled,
                            onKeepActive = reachModel::keepActive,
                            onOpenSettings = reachModel::openSettings,
                            // THE SAME DESTINATION AS `onSave`, because on state D this IS
                            // the save finishing -- see ReachabilityViewModel.confirmDeactivation.
                            // On state C the lambda is never reached.
                            onConfirmDeactivate = {
                                reachModel.confirmDeactivation { screen = FlowScreen.Home }
                            },
                            onPrivacy = {
                                reachModel.privacyTapped()
                                // REPORTS AND GOES NOWHERE, which is what the welcome flow's
                                // three legal links already do: `SignUpFlow.onOpenLegal` defaults
                                // to a no-op and this host supplies none, because the documents
                                // are not hosted yet. Where they live is one ticket for all seven
                                // links, not a decision to take on this screen.
                            },
                            onSave = {
                                // LOCATION (12) DOES NOT EXIST YET, so the one exit ends at Home.
                                // When 12 is built this is the single line that changes.
                                reachModel.savePressed { screen = FlowScreen.Home }
                            },
                        )
                    }

                    FlowScreen.Home ->
                        HomePlaceholderScreen(outcome, onStartOver = { screen = FlowScreen.SignUp })
                }
            }

            // The email code, on screen, in a debug build only.
            //
            // TWO SOURCES, BECAUSE THERE ARE TWO WAYS TO BE TESTING. The offline stand-in issues
            // a code when nothing answers; a development server sends the real one back in the
            // challenge, because `AUTH_EXPOSE_OTP` defaults to on outside production.
            //
            // The server's is preferred when both exist: if a server answered, its code is the
            // one that will verify, and the stand-in's is a leftover from before it came up.
            //
            // THIS USED TO SHOW ONLY THE STAND-IN'S, and the comment here asserted that
            // `/auth/email/start` "answers 204 with no body". That was true when it was written
            // and stopped being true when the route started returning `OtpChallengeResponseDto`.
            // The cost was exactly backwards from the intent: the screen was walkable with NO
            // backend and unwalkable with a REAL one, because the only code on screen came from
            // the stand-in, which is not running when a server answers.
            //
            // Three conditions, each closing a different way this could leak: BuildConfig.DEBUG
            // keeps it out of any release build, the null check keeps it absent when neither
            // source produced a code, and the screen check keeps it off every other screen.
            val serverEmailCode = basicsState.devCode
            val offlineEmailCode = DevOfflineBasics.lastIssued
            val emailCode = serverEmailCode ?: offlineEmailCode
            if (BuildConfig.DEBUG &&
                emailCode != null &&
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
                        if (serverEmailCode != null) {
                            "the code is $emailCode"
                        } else {
                            "OFFLINE · no server · the code is $emailCode"
                        },
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
