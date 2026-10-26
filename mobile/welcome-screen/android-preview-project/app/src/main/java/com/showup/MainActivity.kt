package com.showup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalAccessibilityManager
import android.app.ActivityOptions
import com.showup.analytics.AppAnalytics
import com.showup.checkin.AndroidPlaceReader
import java.time.ZoneId
import com.showup.dev.DevSearchStore
import com.showup.dev.DEV_SEARCH_STARTS
import com.showup.search.filtersActive
import com.showup.search.LikesReceivedScreen
import com.showup.search.PremiumSurface
import com.showup.search.SearchAnalytics
import com.showup.search.SearchGridScreen
import com.showup.search.SearchProfileScreen
import com.showup.search.SearchRepository
import com.showup.search.SearchUiState
import com.showup.search.SearchViewModel
import com.showup.analytics.NavAnalytics
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.showup.api.EncryptedTokenStore
import com.showup.dev.DevMenu
import com.showup.dev.DevMenuFacts
import com.showup.dev.DevSection
import com.showup.dev.DevTab
import com.showup.dev.devSection
import com.showup.profile.locationArrival
import com.showup.api.SessionEnded
import com.showup.api.ShowUpApi
import com.showup.profile.NoConsentBackend
import com.showup.profile.ProfileReachabilityScreen
import com.showup.profile.ReachabilityHost
import com.showup.profile.ReachabilityViewModel
import android.os.Build
import androidx.compose.runtime.rememberCoroutineScope
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
import com.showup.profile.ApiPermissionStatusSync
import com.showup.profile.PermissionReconciler
import com.showup.profile.PushRegistrationSync
import com.showup.profile.SharedPrefsPermissionLedger
import com.showup.profile.permissionSnapshot
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
import com.showup.checkin.CheckInAnalytics
import com.showup.checkin.CheckInAvailabilityScreen
import com.showup.checkin.CheckInFilters
import com.showup.checkin.CheckInFiltersScreen
import com.showup.checkin.CheckInGateScreen
import com.showup.checkin.CheckInIntroScreen
import com.showup.checkin.CheckInMotivationScreen
import com.showup.checkin.CheckInRepository
import com.showup.checkin.CheckInStep
import com.showup.checkin.CheckInUpliftScreen
import com.showup.checkin.CheckInVibeScreen
import com.showup.checkin.CheckInViewModel
import com.showup.checkin.SettingsPlaceholderScreen
import com.showup.dev.DEV_GALLERY_ACTIVITY
import com.showup.dev.DevCheckInLaunch
import com.showup.dev.DevState
import com.showup.dev.DevCheckInStore
import com.showup.designsystem.Tab
import com.showup.profile.resumePoint
import com.showup.profile.ProfileScreen
import com.showup.profile.PromptsViewModel
import com.showup.profile.UPLOAD_JPEG_QUALITY
import com.showup.profile.uploadTargetSize
import com.showup.api.generated.model.FlowPosition
import com.showup.profile.AndroidLocationAccess
import com.showup.profile.DetailStep
import com.showup.profile.DetailsUiState
import com.showup.profile.FlowPositionReporter
import com.showup.profile.LOCATION_PERMISSIONS
import com.showup.profile.LocationArrival
import com.showup.profile.LocationHost
import com.showup.profile.LocationState
import com.showup.profile.LocationViewModel
import com.showup.profile.ProfileChoiceScreen
import com.showup.profile.ProfileDatingLanguageScreen
import com.showup.profile.ProfileDetailsRepository
import com.showup.profile.ProfileDetailsViewModel
import com.showup.profile.PickStep
import com.showup.profile.PickStepsViewModel
import com.showup.profile.ProfileInterestsScreen
import com.showup.profile.ProfileLifeAheadScreen
import com.showup.profile.ProfileLifeNowScreen
import com.showup.profile.ProfileEmbraceDetailsScreen
import com.showup.profile.ProfileHeightScreen
import com.showup.profile.ProfileLocationScreen
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import com.showup.welcome.PhoneAuthRepository
import com.showup.welcome.PhoneAuthViewModel
import com.showup.profile.BasicsStep
import com.showup.profile.BasicsViewModel
import com.showup.profile.EmailCopy
import com.showup.profile.ProfileDobScreen
import com.showup.profile.ProfileEmbraceScreen
import java.time.OffsetDateTime
import com.showup.profile.ProfileEmailScreen
import com.showup.profile.ProfileNameScreen
import com.showup.profile.ProfileVerifyEmailScreen
import com.showup.profile.rememberDateOrder
import com.showup.welcome.FlowNavigator
import com.showup.welcome.FlowScreen
import com.showup.welcome.isProfileCreation
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
            // IN A VIEW MODEL, not rememberSaveable -- see FlowNavigator. A rotation or a process
            // death mid-tutorial still does not drop the user back to card 1 (the handle saves
            // it), and now a save that finishes after an Activity rebuild navigates the screen the
            // user is looking at rather than a state object nobody reads any more. The demo opens
            // where a real first run opens: Startup.
            val nav: FlowNavigator = viewModel(
                factory = viewModelFactory { initializer { FlowNavigator(createSavedStateHandle()) } },
            )
            var screen by nav::screen

            // WHICH ACCOUNT THE PROFILE SCREENS ARE HOLDING, as a generation number.
            //
            // Every view model below that caches what an account has saved -- the answers, the
            // photos, the prompts, the media, the drafts in their SavedStateHandles -- is KEYED on
            // it, and a completed sign-up or log-in bumps it (SignUpFlow's `onFinished`). Until 13
            // October 2026 they were built once per Activity and read the account once, so "Start
            // over" or the developer menu's Startup followed by a NEW account showed the previous
            // account's answers on its first visit to each screen -- and Continue saved them into
            // the new account. A new generation is a new instance with nothing in it; the old ones
            // hold no references anything still reads. Saveable, so a process death keeps the
            // generation and therefore the drafts that belong to it.
            var account by rememberSaveable { mutableIntStateOf(0) }

            // The NavHost's back stack (AppRoute.kt) -- not `nav`, the FlowNavigator above, which
            // still decides the screen inside the flow destination. Saved with the activity.
            val navController = rememberNavController()

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
            val api = remember(tokenStore) {
                ShowUpApi(tokens = tokenStore, onSessionEnded = SessionEnded::signal)
            }

            // ── the server ended the session ─────────────────────────────────
            //
            // The auth layer has already cleared the tokens; what is left is everything the app
            // still holds for the person who was signed in -- a dozen view models, the flow
            // position, half-typed answers. Resetting each one by hand is a list that goes stale
            // the day a screen is added, so the whole Activity starts again instead: a cleared
            // task means new view models, an empty SavedStateHandle and Startup, exactly a first
            // launch, plus the one extra that says why.
            //
            // Collected while STARTED only. News that lands while the app is in the background
            // waits, and is acted on when the user comes back to it.
            val sessionEnded by SessionEnded.isPending.collectAsStateWithLifecycle()
            LaunchedEffect(sessionEnded) {
                if (sessionEnded && SessionEnded.consume()) restartSignedOut()
            }
            // Read from the intent ONCE; after that it is screen state. Saveable, so a rebuild of
            // this Activity does not bring back a notice that has already gone.
            var sessionNotice by rememberSaveable {
                mutableStateOf(intent.getBooleanExtra(EXTRA_SESSION_ENDED, false))
            }

            val phoneAuth: PhoneAuthViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { PhoneAuthViewModel(PhoneAuthRepository(api, tokenStore)) }
                },
            )

            val basics: BasicsViewModel = viewModel(
                key = "basics-$account",
                factory = viewModelFactory {
                    initializer {
                        BasicsViewModel(BasicsRepository(api), analytics = AppAnalytics)
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
                key = "photos-$account",
                factory = viewModelFactory {
                    initializer {
                        PhotosViewModel(
                            repo = PhotosRepository(api),
                            access = AndroidPhotoAccess(context),
                            readBytes = { uri -> readPickedImage(context, uri) },
                            analytics = AppAnalytics,
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

            // ── the saved flow position, Location and "Share some details" (SHOWUP-165 to 173) ──
            //
            // One reporter, shared: media, 09, Stay reachable and location advance the position
            // through it, and the detail steps send theirs inside the answer's own PATCH.
            val positions = remember(api) { FlowPositionReporter(api) }

            // ONE view model for Embrace 2 and the seven steps: they share one answer sheet, and
            // each step's back has to show the previous step's saved value. Its draft lives in the
            // SavedStateHandle, so a process death mid-step keeps what was picked.
            val details: ProfileDetailsViewModel = viewModel(
                key = "details-$account",
                factory = viewModelFactory {
                    initializer {
                        ProfileDetailsViewModel(
                            store = ProfileDetailsRepository(api),
                            positions = positions,
                            handle = createSavedStateHandle(),
                            analytics = AppAnalytics,
                        )
                    }
                },
            )
            val detailsState by details.state.collectAsStateWithLifecycle()

            // Interests, Life and Future (SHOWUP-174 to 176): one view model for the three, for the
            // detail group's reason -- one answer sheet, and each step's back shows the previous
            // step's saved picks. Same store, same position sink, drafts in the SavedStateHandle.
            val picks: PickStepsViewModel = viewModel(
                key = "picks-$account",
                factory = viewModelFactory {
                    initializer {
                        PickStepsViewModel(
                            store = ProfileDetailsRepository(api),
                            positions = positions,
                            handle = createSavedStateHandle(),
                            analytics = AppAnalytics,
                        )
                    }
                },
            )
            val picksState by picks.state.collectAsStateWithLifecycle()

            val locationAccess = remember(context) { AndroidLocationAccess(context) }

            // THE SHARED PERMISSION RECONCILER (epic SHOWUP-151). On launch and on every return to
            // the foreground, for the life of the app -- not only while a permission screen is up:
            // a permission turned off in Settings next week must still reach the server and the
            // analytics. Built on the same readers the screens use; see PermissionReconciler.kt.
            val permissions = remember(context) {
                PermissionReconciler(
                    snapshot = permissionSnapshot(
                        notifications = AndroidNotificationAccess(context),
                        location = locationAccess,
                        media = AndroidMediaAccess(context),
                    ),
                    ledger = SharedPrefsPermissionLedger(context),
                    push = PushRegistrationSync(PushRegistration(api)),
                    server = ApiPermissionStatusSync(api, tokenStore),
                    analytics = AppAnalytics,
                    scope = lifecycleScope,
                )
            }
            val reconcilerScreen by rememberUpdatedState(screen)
            val reconcilerLifecycle = LocalLifecycleOwner.current
            DisposableEffect(reconcilerLifecycle, permissions) {
                // ON_START, not ON_RESUME: our own permission dialogs pause the activity without
                // stopping it, so they never look like a trip outside the app -- and their answers
                // are absorbed where they land, below. Added while already started, the observer
                // is brought up to date at once, which is how the launch itself is read.
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        permissions.onStart(inFlow = reconcilerScreen.isProfileCreation)
                    }
                }
                reconcilerLifecycle.lifecycle.addObserver(observer)
                onDispose { reconcilerLifecycle.lifecycle.removeObserver(observer) }
            }
            val location: LocationViewModel = viewModel(
                key = "location-$account",
                factory = viewModelFactory {
                    initializer {
                        LocationViewModel(access = locationAccess, positions = positions, analytics = AppAnalytics)
                    }
                },
            )
            val locationState by location.state.collectAsStateWithLifecycle()

            // ── the 48hrs Check-In (SHOWUP-178 to SHOWUP-183) ───────────────
            //
            // One owner for the whole flow, built before its steps (epic, "Built once"): the step
            // order, back and ✕, the gate's reopen rule and every event of family H. Keyed on the
            // account like the others: a new sign-in must not inherit the last account's answers.
            // DEBUG ONLY: a launch with `dev_checkin` walks the Check-In on an in-memory server, so
            // every screen and failure state can be walked and recorded on a phone with no backend
            // (dev/DevCheckInStore.kt). A fresh launch only; never in a release build.
            val devCheckIn = remember {
                if (BuildConfig.DEBUG && savedInstanceState == null) DevCheckInLaunch.from(intent) else null
            }
            val checkIn: CheckInViewModel = viewModel(
                key = "checkin-$account",
                factory = viewModelFactory {
                    initializer {
                        val store = devCheckIn?.let { DevCheckInStore(it.latencyMs, it.commit, it.introPassed, active = it.start in DEV_SEARCH_STARTS) }
                            ?: CheckInRepository(api)
                        CheckInViewModel(
                            store,
                            analytics = AppAnalytics,
                            // Where Search measures from. The dev server ignores it, so it asks no fix.
                            places = if (devCheckIn == null) AndroidPlaceReader(context) else null,
                        )
                    }
                },
            )
            val checkInState by checkIn.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                val dev = devCheckIn ?: return@LaunchedEffect
                val target = FlowScreen.entries.firstOrNull { it.name == dev.start } ?: FlowScreen.CheckInIntro
                if (target.isCheckInStep) checkIn.beginFlow()
                screen = target
            }

            // WHERE THE USER CAME FROM, for `referrer_screen_id` -- held beside the screen, for the
            // same reason (FlowNavigator).
            val cameFrom by nav::cameFrom

            /** Every navigation into and within the new screens goes through here. */
            fun goTo(next: FlowScreen) = nav.goTo(next)

            /**
             * Into location, through the arrival matrix: switch off -> C, not determined -> A,
             * granted -> skipped silently to Embrace 2, denied or restricted -> B.
             */
            fun enterLocation() {
                when (location.decideArrival()) {
                    LocationArrival.Skip -> goTo(FlowScreen.ProfileEmbraceDetails)
                    is LocationArrival.Show -> goTo(FlowScreen.ProfileLocation)
                }
            }

            /** Past location, by any of its exits: grant, `Not now`, or a grant noticed on return. */
            fun leaveLocation() {
                location.left()
                goTo(FlowScreen.ProfileEmbraceDetails)
            }

            // THE LOCATION DIALOG. Fine and coarse in ONE request -- Android 12+ requires coarse
            // alongside fine -- and either one granted is a grant: "precise and approximate are
            // both accepted". The answer goes to the view model, not to a deferred, so a rebuild
            // while the dialog is up cannot strand it (see LocationHost).
            val askLocation = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                // AN EMPTY RESULT IS NO ANSWER: the system cancelled the request. Not a denial --
                // nothing recorded, and A keeps a working button.
                if (result.isEmpty()) {
                    location.permissionCancelled()
                    return@rememberLauncherForActivityResult
                }
                // Recorded when ANSWERED, never before the launch -- the same rule as the
                // notification dialog, for the same reason.
                PermissionAskLog.recordAsked(context, Manifest.permission.ACCESS_FINE_LOCATION)
                // Our own sheet: permission_result's act, never an outside change.
                permissions.absorbOwnAnswer()
                location.permissionAnswered(result.values.any { it }) { leaveLocation() }
            }

            val prompts: PromptsViewModel = viewModel(
                key = "prompts-$account",
                factory = viewModelFactory {
                    initializer {
                        PromptsViewModel(
                            repo = PromptsRepository(api),
                            saved = createSavedStateHandle(),
                            analytics = AppAnalytics,
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
                    // FRONT BY DEFAULT, AND WHATEVER EXISTS OTHERWISE.
                    //
                    // The ticket asks for the front camera and draws no flip control: a selfie is
                    // the obvious intent for a face clip. Every real phone has one, so on a device
                    // this is the front camera and nothing else ever happens.
                    //
                    // An EMULATOR is where it is not: an AVD can be configured with no front
                    // camera at all, and binding a selector the device cannot satisfy throws
                    // rather than falling back -- which reaches a user as a viewfinder that never
                    // opens and a take that produces nothing. This is not a stand-in for real
                    // capture and does not fake anything: the same session, the same recorder and
                    // the same state machine run either way, pointed at whichever lens exists.
                    cameraController.cameraSelector = runCatching {
                        if (cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    }.getOrDefault(CameraSelector.DEFAULT_FRONT_CAMERA)
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
                key = "reachModel-$account",
                factory = viewModelFactory {
                    initializer {
                        ReachabilityViewModel(
                            access = notifications,
                            push = PushRegistration(api),
                            // No endpoint for the push consent yet -- see ReachabilityRepository.
                            consent = NoConsentBackend,
                            analytics = AppAnalytics,
                        )
                    }
                },
            )

            val notifyModel: NotificationsViewModel = viewModel(
                key = "notifyModel-$account",
                factory = viewModelFactory {
                    initializer {
                        NotificationsViewModel(
                            access = notifications,
                            push = PushRegistration(api),
                            analytics = AppAnalytics,
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
            // callback completes a deferred the save is sitting on, HELD BY THE VIEW MODEL so an
            // Activity rebuild while the dialog is up cannot orphan it (see
            // `ReachabilityViewModel.awaitNotificationAnswer`).
            //
            // The launcher is declared here, above the `when`, so its callback survives the
            // navigation it triggers. That was already the rule when 09 owned it.
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                // RECORDED WHEN IT IS ANSWERED, never before the launch. Android cannot tell
                // "never asked" from "refused" -- both read as not-granted -- so `PermissionAskLog`
                // is the proxy, and recording it before the dialog is answered makes a liar of it:
                // a user who raises the sheet and backgrounds the app without answering would come
                // back recorded as denied while the OS status is still not determined.
                PermissionAskLog.recordAsked(context, Manifest.permission.POST_NOTIFICATIONS)
                permissions.absorbOwnAnswer()
                reachModel.notificationAnswered(granted)
            }

            // The player, held here for the same reason the camera controller is: it owns a
            // hardware resource that outlives a recomposition and has to be released when the flow
            // leaves, and the video surfaces attach to it.
            val mediaPlayer = remember(context) { AndroidMediaPlayer(context) }
            DisposableEffect(mediaPlayer) { onDispose { mediaPlayer.release() } }

            val media: MediaViewModel = viewModel(
                key = "media-$account",
                factory = viewModelFactory {
                    initializer {
                        MediaViewModel(
                            repo = MediaRepository(api),
                            access = AndroidMediaAccess(context),
                            capture = AndroidMediaCaptureFactory(context) { cameraController },
                            player = mediaPlayer,
                            analytics = AppAnalytics,
                        )
                    }
                },
            )
            val mediaState by media.state.collectAsStateWithLifecycle()

            // ── date search (SHOWUP-184) ─────────────────────────────────────────
            //
            // One owner for the queue, the like flow, the coach and their events, keyed on the account
            // like every other. It plays through the app's one player -- the media step and Search are
            // never on screen together -- and a debug launch walks it on samples (dev/DevSearchStore.kt).
            val search: SearchViewModel = viewModel(
                key = "search-$account",
                factory = viewModelFactory {
                    initializer {
                        SearchViewModel(
                            store = devCheckIn?.let { DevSearchStore(it.latencyMs) } ?: SearchRepository(api),
                            analytics = AppAnalytics,
                            players = mediaPlayer,
                            // An open like sheet and its message survive a process death.
                            saved = createSavedStateHandle(),
                        )
                    }
                },
            )
            val searchState by search.state.collectAsStateWithLifecycle()

            // THE UNDO WINDOW AS THE PHONE'S ACCESSIBILITY SETTINGS WANT IT (SHOWUP-186: "while focus
            // is on it, the 6 s does not run out"). TalkBack's focus is not a focus the app can see, so
            // while TalkBack is on the window holds until the list is left; otherwise it is the user's
            // "time to take action", never less than 6 s. Followed live: TalkBack can be switched on
            // with the app open.
            val accessibility = LocalAccessibilityManager.current
            DisposableEffect(search, accessibility) {
                val platform = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                fun apply() {
                    val recommended = accessibility?.calculateRecommendedTimeoutMillis(
                        Motion.LIKES_UNDO.toLong(), containsIcons = false, containsText = true, containsControls = true,
                    ) ?: Motion.LIKES_UNDO.toLong()
                    search.setUndoWindow(if (platform?.isTouchExplorationEnabled == true) Long.MAX_VALUE else recommended)
                }
                apply()
                val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { apply() }
                platform?.addTouchExplorationStateChangeListener(listener)
                onDispose { platform?.removeTouchExplorationStateChangeListener(listener) }
            }

            // THE LIKES BADGE: open received likes, the one number the Likes tab and the Search likes
            // chip both show (SHOWUP-187 rule 10) -- on the gate's and Settings' bars too, so it is
            // read on every foreground of a tab screen, not only by a Search visit.
            val likesOpen = searchState.likesBadge

            // THE PROFILE OPENED FROM A LIST IS A DESTINATION OF ITS OWN (AppRoute.SearchProfile),
            // pushed over the flow and popped back to it. The view model says whether one is open and
            // the back stack follows -- one source of truth, so a process death that restores the
            // destination with nothing open pops it again rather than drawing an empty profile.
            val profileOpen = searchState.opened != null
            LaunchedEffect(profileOpen) {
                val onProfile = navController.currentBackStackEntry?.destination?.hasRoute<AppRoute.SearchProfile>() == true
                when {
                    profileOpen && !onProfile -> navController.navigate(AppRoute.SearchProfile) { launchSingleTop = true }
                    !profileOpen && onProfile -> navController.popBackStack()
                }
            }

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
                permissions.absorbOwnAnswer()
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
                // MARKED ASKED ONCE THE ANSWER IS BACK, not before: a rebuild or a process death
                // during the fetch cancels it, and a flag already set would forbid the retry and
                // leave a returning member on Startup.
                val progress = progressRepo.fetch()
                resumeChecked = true
                if (progress == null) return@LaunchedEffect
                if (screen != FlowScreen.SignUp) return@LaunchedEffect
                // A FINISHED PROFILE OPENS ON THE SEARCH TAB (Check-In 01): the gate until a
                // Check-In is active, unlocked Search after. Read before deciding -- and an
                // unreachable server is the gate, because date search must never open on a guess.
                val finished = if (resumePoint(progress) == ResumePoint.Done) {
                    if (checkIn.read()?.active == true) FlowScreen.CheckInSearch else FlowScreen.CheckInGate
                } else {
                    null
                }
                // The read took time: a user who has walked somewhere meanwhile stays there.
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
                    // PAST PROMPTS, FROM THE STORED POSITION (SHOWUP-165). Each routes exactly as
                    // the forward walk does, so resuming and walking land in the same place:
                    // notifications through `afterMedia`, which owns its skip guard.
                    ResumePoint.Media -> FlowScreen.ProfileMedia
                    ResumePoint.Notifications -> afterMedia()
                    ResumePoint.Reachability -> FlowScreen.ProfileReachability
                    // Through the arrival matrix, exactly as the forward walk: a kill mid-dialog
                    // with no answer lands on A, a grant is skipped, a denial shows B.
                    ResumePoint.Location -> when (location.decideArrival()) {
                        LocationArrival.Skip -> FlowScreen.ProfileEmbraceDetails
                        is LocationArrival.Show -> FlowScreen.ProfileLocation
                    }
                    ResumePoint.Height -> FlowScreen.ProfileHeight
                    ResumePoint.Gender -> FlowScreen.ProfileGender
                    ResumePoint.Orientation -> FlowScreen.ProfileOrientation
                    ResumePoint.DatingLanguage -> FlowScreen.ProfileDatingLanguage
                    ResumePoint.Education -> FlowScreen.ProfileEducation
                    ResumePoint.Religion -> FlowScreen.ProfileReligion
                    ResumePoint.Politics -> FlowScreen.ProfilePolitics
                    ResumePoint.Interests -> FlowScreen.ProfileInterests
                    ResumePoint.LifeNow -> FlowScreen.ProfileLifeNow
                    ResumePoint.LifeAhead -> FlowScreen.ProfileLifeAhead
                    ResumePoint.Done -> finished ?: FlowScreen.CheckInGate
                }
                if (screen == FlowScreen.ProfilePrompts) prompts.load()
                // A resume onto a detail step pre-fills the saved value: start the read now,
                // before the step's arrival asks for it. Only there -- nothing else reads it.
                if (DetailStep.entries.any { it.screen == profileScreenOf(screen) }) details.preload()
                if (PickStep.entries.any { it.screen == profileScreenOf(screen) }) picks.preload()
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
            // Keyed on the two models too: after a new sign-in they are new instances, and an
            // observer that captured the old ones would refresh models nothing draws any more.
            DisposableEffect(lifecycleOwner, photos, media) {
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

            // THE CHECK-IN IS RE-READ ON EVERY FOREGROUND, and its clock re-evaluated: "read on every
            // foreground", and "a slot that has started disappears from the grid and from the
            // selection, silently". Only while a Check-In screen is shown -- elsewhere nothing
            // reads it, and a signed-out launch must not send an authenticated request.
            DisposableEffect(lifecycleOwner, checkIn) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && screen.isCheckIn) {
                        checkIn.evaluateClock()
                        checkIn.refresh()
                        // The plan as well as the count: it decides where the Likes tab goes.
                        search.foregrounded()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // SEARCH, LEFT FOR ANOTHER APP AND COME BACK TO. Backgrounding is the profile leaving the
            // screen: its card view reported with the dwell it had, its voice note or video stopped
            // ("pauses when scrolled out of view" -- the app going away is the furthest out). The
            // return starts a new dwell. ON_STOP / ON_START for the reason the media screen gives:
            // a permission alert pauses the activity and is not the user leaving.
            DisposableEffect(lifecycleOwner, search) {
                val observer = LifecycleEventObserver { _, event ->
                    // The Likes tab too: going to the background sends a decline waiting out its Undo.
                    if (screen != FlowScreen.CheckInSearch && screen != FlowScreen.LikesReceived) return@LifecycleEventObserver
                    when (event) {
                        Lifecycle.Event.ON_STOP -> search.leave()
                        Lifecycle.Event.ON_START -> search.resume()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // THE SEARCH TAB FOLLOWS THE SERVER. "B is the Search tab whenever the user has no
            // completed Check-In": a Check-In committed elsewhere unlocks it, and a window that
            // closed while the app was away locks it again. Only between these two -- never out of
            // the middle of a Check-In.
            val checkInActive = checkInState.server?.active
            LaunchedEffect(checkInActive, screen) {
                when {
                    screen == FlowScreen.CheckInGate && checkInActive == true -> goTo(FlowScreen.CheckInSearch)
                    screen == FlowScreen.CheckInSearch && checkInActive == false -> goTo(FlowScreen.CheckInGate)
                }
            }

            /** The Check-In screen for a step. */
            fun CheckInStep.flowScreen(): FlowScreen = when (this) {
                CheckInStep.Intro -> FlowScreen.CheckInIntro
                CheckInStep.Today -> FlowScreen.CheckInToday
                CheckInStep.Tomorrow -> FlowScreen.CheckInTomorrow
                CheckInStep.Motivation -> FlowScreen.CheckInMotivation
                CheckInStep.Vibe -> FlowScreen.CheckInVibe
                CheckInStep.Filters -> FlowScreen.CheckInFilters
                CheckInStep.Uplift -> FlowScreen.CheckInUplift
            }

            /**
             * A Check-In step is on screen. IF ITS OWNER DID NOT SURVIVE -- a process death; the
             * screen is saved, the view model is not -- the Check-In restarts at Today, empty, and
             * Motivation, Vibe and Filters pre-fill from the server again (story 02: only ✕ "or
             * leaving the app long enough for the screen to be destroyed" clears the picks). The
             * intro holds nothing and is simply shown.
             */
            fun checkInArrived(step: CheckInStep) {
                if (step != CheckInStep.Intro && !checkIn.flowAlive) {
                    checkIn.beginFlow()
                    if (step != CheckInStep.Today) {
                        // Not a navigation the user made: no referrer, as on a launch.
                        nav.cameFrom = null
                        screen = FlowScreen.CheckInToday
                        return
                    }
                }
                checkIn.arrived(step, step.screen, profileScreenOf(cameFrom))
            }

            /** Every step's ✕: the flow is discarded and the gate shown. */
            fun closeCheckIn(step: CheckInStep) = checkIn.closePressed(step) { goTo(FlowScreen.CheckInGate) }

            /** The Search tab: Search with a committed Check-In, the gate without one (story 01). */
            fun searchTab(): FlowScreen =
                if (checkInState.server?.active == true) FlowScreen.CheckInSearch else FlowScreen.CheckInGate

            /** A TabBar tap while [active] shows: `tab_switched` for any other tab -- the nav act alone. */
            fun tabTapped(active: Tab, tapped: Tab) {
                NavAnalytics.tabSwitched(active, tapped)?.let { (event, properties) -> AppAnalytics.track(event, properties) }
            }

            /** The gate's CTA, Search tab or Instant tab: the intro until it has been passed, Today after. */
            fun reopenCheckIn(target: CheckInAnalytics.GateTarget) =
                checkIn.gateTapped(target) { step -> goTo(step.flowScreen()) }

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
                permissions.absorbOwnAnswer()
                photos.refreshAccess()
                if (granted) launchCamera()
            }
            val motion = rememberMotion()

            // The system back gesture mirrors the on-screen Back, so hardware back never drops
            // someone out of the tutorial from the middle of it. On card 1 it is left alone, so
            // back exits as usual, and the range stops at 6 so back cannot walk out of the app
            // and into the last tutorial card.
            BackHandler(enabled = screen.hasSystemBack) {
                // Through goTo, so the screen arrived at knows where it came from.
                goTo(FlowScreen.entries[screen.ordinal - 1])
            }

            // ── the developer menu (debug builds only) ──────────────────────
            //
            // Jump to any screen, and watch every event as it fires (dev/DevMenu.kt). Everything
            // that touches it sits behind `BuildConfig.DEBUG`, a compile-time constant, so a
            // release build carries none of it.
            var devMenuOpen by rememberSaveable { mutableStateOf(false) }
            // For this launch only: hidden so a screenshot for design review is clean.
            var devTabHidden by remember { mutableStateOf(false) }
            var devSignedIn by remember { mutableStateOf<Boolean?>(null) }
            val devScope = rememberCoroutineScope()
            if (BuildConfig.DEBUG) {
                // Read each time the menu opens: a sign-in or a session end since last time
                // changes which screens can be reached.
                LaunchedEffect(devMenuOpen) {
                    if (devMenuOpen) devSignedIn = tokenStore.refreshToken() != null
                }
            }

            /**
             * A jump, through the SAME ways in the walk and the resume use -- "behave like real":
             * notifications through `afterMedia`'s skip guard, location through its arrival
             * matrix, prompts loading what the account holds.
             */
            fun devJumpTo(target: FlowScreen) {
                // Leave whatever is on screen the way the walk would, so the next showing of any
                // screen is a new view: a jump back onto the same step must arrive again.
                location.left()
                details.leftElsewhere()
                picks.leftElsewhere()
                notifyModel.left()
                reachModel.left()
                checkIn.leftElsewhere()
                // A jump INTO a Check-In step begins a flow there, as the walk would have: pre-filled
                // from the server, availability and the uplift empty.
                if (target.isCheckInStep) checkIn.beginFlow()
                when (target) {
                    FlowScreen.ProfileNotifications -> goTo(afterMedia())
                    FlowScreen.ProfileLocation -> enterLocation()
                    else -> goTo(target)
                }
                if (screen == FlowScreen.ProfilePrompts) prompts.load()
                // "Everything already entered, still present" -- the resume's rule, for a jump
                // into the basics: the name and email come from the account, as on a relaunch.
                if (target.devSection() == DevSection.Basics) {
                    devScope.launch {
                        val progress = progressRepo.fetch() ?: return@launch
                        if (firstName.isEmpty()) firstName = progress.displayName.orEmpty()
                        if (email.isEmpty()) {
                            email = progress.email.orEmpty()
                            basics.setEmail(email)
                        }
                    }
                }
            }

            // Moving past sign-up ends the notice: it describes why the user is HERE.
            LaunchedEffect(screen) { if (screen != FlowScreen.SignUp) sessionNotice = false }

            Box(Modifier.fillMaxSize()) {
            // THE NAVHOST: the existing flow is one destination (see AppRoute.kt). No transition of
            // its own -- the flow animates its screens itself, and a push from it brings its own.
            NavHost(
                navController = navController,
                startDestination = AppRoute.Flow,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
            composable<AppRoute.Flow> {
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
                // WHETHER THIS IS THE SCREEN BEING SHOWN, or one sliding away. A back pressed
                // during the 320 ms transition returns to a screen that is still composed, and
                // AnimatedContent REUSES that composition rather than building it again -- so an
                // arrival keyed on `Unit` never runs a second time, and a step whose view model
                // thinks it was left would ignore every press. Arrivals key on this instead, and
                // run again the moment the screen is the target once more.
                val onTop = current == screen
                // Exhaustive on purpose. ShowUpEveryTime used to be the `else` branch, which meant
                // any unexpected value rendered card 6; every screen is now named and the compiler
                // fails if one is added and not handled here.
                when (current) {
                    // Welcome & sign-up (SHOWUP-140/142/143/144) runs before the tutorial,
                    // which is the real order: you sign up, then you are shown how it works.
                    // SignUpFlow owns every step and every piece of state inside it.
                    // SHOWUP-146, the whole ticket in one line: the tutorial for a new
                    // account, straight into the app for a returning member.
                    FlowScreen.SignUp -> SignUpFlow(
                        auth = phoneAuth,
                        analytics = AppAnalytics,
                        onFinished = {
                            // A SESSION BEGAN, possibly a different account's: nothing the
                            // screens hold may carry over. A fresh generation of every
                            // account-scoped view model, and the host's own copy of the name, the
                            // address and the consent box.
                            account += 1
                            firstName = ""
                            email = ""
                            marketingConsent = false
                            // And the server hears this account's permission statuses.
                            permissions.accountChanged()
                            outcome = it
                            screen = if (showsTutorial(it)) FlowScreen.TutorialWelcome else FlowScreen.Home
                        },
                        sessionEnded = sessionNotice,
                        onSessionNoticeDismissed = { sessionNotice = false },
                    )
                    FlowScreen.TutorialWelcome ->
                        WelcomeScreen(onContinue = { screen = FlowScreen.MeetInRealLife }, analytics = AppAnalytics)
                    FlowScreen.MeetInRealLife ->
                        MeetInRealLifeScreen(onNext = { screen = FlowScreen.MatchOnAvailability }, analytics = AppAnalytics)
                    FlowScreen.MatchOnAvailability -> MatchOnAvailabilityScreen(
                        onNext = { screen = FlowScreen.MatchMeansMeet },
                        onBack = { screen = FlowScreen.MeetInRealLife },
                        analytics = AppAnalytics,
                    )
                    FlowScreen.MatchMeansMeet -> MatchMeansMeetScreen(
                        onNext = { screen = FlowScreen.ThirtyMinutes },
                        onBack = { screen = FlowScreen.MatchOnAvailability },
                        analytics = AppAnalytics,
                    )
                    FlowScreen.ThirtyMinutes -> ThirtyMinutesScreen(
                        onNext = { screen = FlowScreen.ShowUpEveryTime },
                        onBack = { screen = FlowScreen.MatchMeansMeet },
                        analytics = AppAnalytics,
                    )
                    FlowScreen.ShowUpEveryTime -> ShowUpEveryTimeScreen(
                        // SHOWUP-146 sent the tutorial's far end straight to the app. Profile
                        // creation now sits between the two, which is the real order.
                        onFinish = { goTo(FlowScreen.ProfileName) },
                        onBack = { screen = FlowScreen.ThirtyMinutes },
                        analytics = AppAnalytics,
                    )
                    // SHOWUP-150. No back: profile creation is mandatory once entered, and the
                    // screen swallows the system gesture itself.
                    FlowScreen.ProfileName -> ProfileNameScreen(
                        value = firstName,
                        onValueChange = { firstName = it },
                        onContinue = {
                            firstName = it
                            basics.nameAccepted(it)
                            goTo(FlowScreen.ProfileEmail)
                        },
                        onEmptySubmit = basics::nameRefused,
                    ).also {
                        // Each arrival is a showing; keyed on onTop for the reason above.
                        LaunchedEffect(onTop) { if (onTop) basics.arrived(BasicsStep.Name, profileScreenOf(cameFrom)) }
                    }
                    FlowScreen.ProfileEmail -> ProfileEmailScreen(
                        value = email,
                        onValueChange = {
                            email = it
                            basics.setEmail(it)
                        },
                        consent = marketingConsent,
                        onConsentChange = {
                            marketingConsent = it
                            basics.marketingConsentChanged(it)
                        },
                        // Continue SENDS the code and only advances once the server has accepted
                        // it. Navigating first would put the user on a screen waiting for a code
                        // that was never dispatched.
                        onContinue = {
                            email = it
                            basics.setEmail(it)
                            basics.sendCode(onSent = { goTo(FlowScreen.ProfileVerifyEmail) })
                        },
                        onSubmitRefused = basics::emailRefused,
                        serverError = if (basicsState.emailInUse) {
                            EmailCopy.ALREADY_IN_USE_PROPOSED
                        } else if (basicsState.transportFailed) {
                            EmailCopy.SEND_FAILED_PROPOSED
                        } else {
                            null
                        },
                        onBack = { goTo(FlowScreen.ProfileName) },
                    ).also {
                        LaunchedEffect(onTop) { if (onTop) basics.arrived(BasicsStep.Email, profileScreenOf(cameFrom)) }
                    }

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
                        onVerify = { basics.verify(onVerified = { goTo(FlowScreen.ProfileDob) }) },
                        onResend = basics::resendCode,
                        // Both exits are the same journey: back to the email step, address kept.
                        onChangeEmail = { goTo(FlowScreen.ProfileEmail) },
                        onBack = { goTo(FlowScreen.ProfileEmail) },
                    ).also {
                        LaunchedEffect(onTop) { if (onTop) basics.arrived(BasicsStep.EmailVerify, profileScreenOf(cameFrom)) }
                    }

                    // SHOWUP-154. Continue writes the date AND the visibility choice in one
                    // PATCH and only advances when the server has stored them. Back does not
                    // re-send or re-verify anything -- the code screen is already satisfied.
                    FlowScreen.ProfileDob -> {
                      // The order the field reads digits in, handed to the view model too, so the
                      // states it reports are the states the screen shows.
                      val dobOrder = rememberDateOrder()
                      ProfileDobScreen(
                        value = basicsState.dob,
                        onValueChange = { basics.setDob(it, dobOrder) },
                        order = dobOrder,
                        hideAge = basicsState.hideAge,
                        onHideAgeChange = basics::setHideAge,
                        attempted = basicsState.dobAttempted,
                        busy = basicsState.busy,
                        serverRejectedAge = basicsState.serverRejectedAge,
                        onContinue = { valid ->
                            basics.saveDateOfBirth(
                                valid.iso,
                                valid.age,
                                onSaved = { goTo(FlowScreen.ProfileEmbrace) },
                            )
                        },
                        onRefused = basics::dobRefused,
                        onEdit = { basics.clearDob() },
                        onBack = { goTo(FlowScreen.ProfileVerifyEmail) },
                      )
                      LaunchedEffect(onTop) { if (onTop) basics.arrived(BasicsStep.Dob, profileScreenOf(cameFrom)) }
                    }
                    // SHOWUP-155. The bridge out of "The basics". No header, no progress bar,
                    // no back -- the absences are the design, and the screen swallows the system
                    // gesture itself. Its one exit is forward.
                    FlowScreen.ProfileEmbrace -> ProfileEmbraceScreen(
                        firstName = firstName,
                        onContinue = { goTo(FlowScreen.ProfilePhotos) },
                    ).also {
                        LaunchedEffect(onTop) { if (onTop) basics.embraceArrived(profileScreenOf(cameFrom)) }
                    }

                    // SHOWUP-156. Every state on this screen is real: uploads run, report their
                    // own progress and can fail, and the count only moves when one is confirmed.
                    // The picker, the camera UI and the permission alert are the OS's and none of
                    // them is drawn here.
                    FlowScreen.ProfilePhotos -> ProfilePhotosScreen(
                        state = photosState.grid,
                        library = photosState.library,
                        camera = photosState.camera,
                        sheetOpen = photosState.sheetOpen,
                        onBack = { goTo(FlowScreen.ProfileEmbrace) },
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
                        onContinue = { goTo(FlowScreen.ProfilePrompts) },
                        onRefused = photos::continueRefused,
                    ).also {
                        // Reads what the account already holds. Without it the grid started empty
                        // on every launch, and an account already at the server's six-photo limit
                        // answered the next upload with a 400 the slot could only render as
                        // `Upload failed` -- with a Retry that re-sent the same bytes to the same
                        // full account.
                        LaunchedEffect(Unit) { photos.load() }
                        LaunchedEffect(onTop) { if (onTop) photos.arrived(profileScreenOf(cameFrom)) }
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
                        // Keyed on `onTop`, like every arrival -- see the note where it is defined.
                        LaunchedEffect(onTop) {
                            if (onTop) {
                                prompts.arrived(referrer = ProfileScreen.Photos)
                                prompts.load()
                            }
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
                        LaunchedEffect(onTop) { if (onTop) media.arrived() }
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
                                    // Past media, whichever way: the saved flow position.
                                    details.record(FlowPosition.media_video)
                                    screen = afterMedia()
                                },
                                onContinue = {
                                    media.stopPlayback()
                                    media.continuePressed()
                                    details.record(FlowPosition.media_video)
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
                                    details.record(FlowPosition.notifications)
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
                            val host = object : ReachabilityHost {
                                override suspend fun requestNotificationPermission(): Boolean {
                                    // Below API 33 there is no runtime permission to ask for
                                    // and notifications are already on.
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        return true
                                    }
                                    return reachModel.awaitNotificationAnswer {
                                        askNotifications.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    }
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
                                            reachModel.awaitNotificationAnswer {
                                                askNotifications.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                            }
                                            reachModel.foregrounded()
                                        }
                                    } else {
                                        openAppSettings(context)
                                    }
                                }
                            }
                            reachModel.attach(host)
                            onDispose { reachModel.detach(host) }
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
                                reachModel.confirmDeactivation {
                                    details.record(FlowPosition.reachability)
                                    enterLocation()
                                }
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
                                // Into Location (12), through its arrival matrix (SHOWUP-165).
                                reachModel.savePressed {
                                    details.record(FlowPosition.reachability)
                                    enterLocation()
                                }
                            },
                        )
                    }

                    // ── Location (SHOWUP-165) ───────────────────────────────
                    FlowScreen.ProfileLocation -> {
                        // THE HOST DOES ONLY WHAT AN ACTIVITY CAN: raise the dialog and open the
                        // Settings page the platform table names. Every rule is in the view model.
                        DisposableEffect(location) {
                            val host = object : LocationHost {
                                override fun requestLocationPermission() {
                                    askLocation.launch(LOCATION_PERMISSIONS)
                                }

                                override fun openSettings(state: LocationState) {
                                    // B: our app page. C: the system Location page itself --
                                    // Android can open it, which is why C's Android copy
                                    // drops the path sentence.
                                    if (state == LocationState.ServicesOff) {
                                        openLocationSettings(context)
                                    } else {
                                        openAppSettings(context)
                                    }
                                }
                            }
                            location.attach(host)
                            onDispose { location.detach(host) }
                        }
                        LaunchedEffect(onTop) {
                            if (onTop) location.arrived(profileScreenOf(cameFrom)) { leaveLocation() }
                        }

                        // RE-READ ON EVERY FOREGROUND: Location turned on in C shows A; a grant in
                        // Settings from B or C advances silently. Nothing while our dialog is up.
                        val locLifecycle = LocalLifecycleOwner.current
                        DisposableEffect(locLifecycle) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    location.foregrounded { leaveLocation() }
                                }
                            }
                            locLifecycle.lifecycle.addObserver(observer)
                            onDispose { locLifecycle.lifecycle.removeObserver(observer) }
                        }

                        ProfileLocationScreen(
                            state = locationState.state,
                            requesting = locationState.requesting,
                            onAllow = location::allowPressed,
                            onOpenSettings = location::openSettingsPressed,
                            onNotNow = { location.notNowPressed { leaveLocation() } },
                        )
                    }

                    // ── Embrace 2 (SHOWUP-166) ──────────────────────────────
                    FlowScreen.ProfileEmbraceDetails -> {
                        // A back-pop from height is not a push: no `embrace_bridge_viewed`, and
                        // the confetti does not replay.
                        // Decided as the screen is built: the Continue that leaves it changes
                        // `cameFrom`, and the outgoing bridge must not start a rain on its way out.
                        val pushed = remember { cameFrom != FlowScreen.ProfileHeight }
                        LaunchedEffect(onTop) {
                            if (onTop) {
                                details.embraceArrived(
                                    profileScreenOf(cameFrom),
                                    pop = cameFrom == FlowScreen.ProfileHeight,
                                )
                            }
                        }
                        ProfileEmbraceDetailsScreen(
                            firstName = firstName,
                            onContinue = {
                                details.embraceContinue { goTo(FlowScreen.ProfileHeight) }
                            },
                            // `pushed` as built, and never on a pop -- even onto a reused screen whose
                            // rain had not begun when it was left.
                            playConfetti = pushed && cameFrom != FlowScreen.ProfileHeight,
                            // t = 0 is when THIS screen's push transition has settled, not mount --
                            // and never on the way out, whose first frame also reads as "settled".
                            transitionSettled = transition.targetState == EnterExitState.Visible &&
                                transition.currentState == EnterExitState.Visible &&
                                !transition.isRunning,
                        )
                    }

                    // ── "Share some details", steps 1 to 7 (SHOWUP-167 to SHOWUP-173) ─────────
                    FlowScreen.ProfileHeight -> {
                        LaunchedEffect(onTop) {
                            if (onTop) details.arrived(DetailStep.Height, profileScreenOf(cameFrom))
                        }
                        ProfileHeightScreen(
                            state = heldWhileLeaving(detailsState, onTop),
                            // Re-armed on every showing: a back that lands mid-transition reuses the
                            // screen, and the keypad must open again without a tap.
                            autoFocus = onTop,
                            onHeightChange = details::heightChanged,
                            onToggleVisibility = { details.visibilityToggled(DetailStep.Height) },
                            onContinue = {
                                details.continuePressed(DetailStep.Height) {
                                    goTo(FlowScreen.ProfileGender)
                                }
                            },
                            onSkip = {
                                details.skipPressed(DetailStep.Height) {
                                    goTo(FlowScreen.ProfileGender)
                                }
                            },
                            onBack = {
                                details.backPressed(DetailStep.Height) { goTo(FlowScreen.ProfileEmbraceDetails) }
                            },
                        )
                    }
                    FlowScreen.ProfileGender -> DetailChoiceHost(
                        DetailStep.Gender, details, detailsState, profileScreenOf(cameFrom), onTop,
                        onNext = { goTo(FlowScreen.ProfileOrientation) },
                        onPrevious = { goTo(FlowScreen.ProfileHeight) },
                    )
                    FlowScreen.ProfileOrientation -> DetailChoiceHost(
                        DetailStep.Orientation, details, detailsState, profileScreenOf(cameFrom), onTop,
                        onNext = { goTo(FlowScreen.ProfileDatingLanguage) },
                        onPrevious = { goTo(FlowScreen.ProfileGender) },
                    )
                    FlowScreen.ProfileDatingLanguage -> {
                        LaunchedEffect(onTop) {
                            if (onTop) {
                                details.arrived(DetailStep.DatingLanguage, profileScreenOf(cameFrom))
                            }
                        }
                        ProfileDatingLanguageScreen(
                            state = heldWhileLeaving(detailsState, onTop),
                            onTap = details::languageTapped,
                            onToggleVisibility = {
                                details.visibilityToggled(DetailStep.DatingLanguage)
                            },
                            onContinue = {
                                details.continuePressed(DetailStep.DatingLanguage) {
                                    goTo(FlowScreen.ProfileEducation)
                                }
                            },
                            onSkip = {
                                details.skipPressed(DetailStep.DatingLanguage) {
                                    goTo(FlowScreen.ProfileEducation)
                                }
                            },
                            onBack = {
                                details.backPressed(DetailStep.DatingLanguage) {
                                    goTo(FlowScreen.ProfileOrientation)
                                }
                            },
                        )
                    }
                    FlowScreen.ProfileEducation -> DetailChoiceHost(
                        DetailStep.Education, details, detailsState, profileScreenOf(cameFrom), onTop,
                        onNext = { goTo(FlowScreen.ProfileReligion) },
                        onPrevious = { goTo(FlowScreen.ProfileDatingLanguage) },
                    )
                    FlowScreen.ProfileReligion -> DetailChoiceHost(
                        DetailStep.Religion, details, detailsState, profileScreenOf(cameFrom), onTop,
                        onNext = { goTo(FlowScreen.ProfilePolitics) },
                        onPrevious = { goTo(FlowScreen.ProfileEducation) },
                    )
                    FlowScreen.ProfilePolitics -> DetailChoiceHost(
                        DetailStep.Politics, details, detailsState, profileScreenOf(cameFrom), onTop,
                        onNext = { goTo(FlowScreen.ProfileInterests) },
                        onPrevious = { goTo(FlowScreen.ProfileReligion) },
                    )

                    // ── steps 8 to 10 (SHOWUP-174 to SHOWUP-176) ─────────────────────
                    FlowScreen.ProfileInterests -> {
                        LaunchedEffect(onTop) {
                            if (onTop) picks.arrived(PickStep.Interests, profileScreenOf(cameFrom))
                        }
                        ProfileInterestsScreen(
                            state = heldWhileLeaving(picksState, onTop),
                            onLifestyle = picks::lifestyleTapped,
                            onInterest = picks::interestTapped,
                            onShowMore = picks::showMoreTapped,
                            onContinue = {
                                picks.continuePressed(PickStep.Interests) { goTo(FlowScreen.ProfileLifeNow) }
                            },
                            onSkip = { picks.skipPressed(PickStep.Interests) { goTo(FlowScreen.ProfileLifeNow) } },
                            onBack = { picks.backPressed(PickStep.Interests) { goTo(FlowScreen.ProfilePolitics) } },
                        )
                    }
                    FlowScreen.ProfileLifeNow -> {
                        LaunchedEffect(onTop) {
                            if (onTop) picks.arrived(PickStep.LifeNow, profileScreenOf(cameFrom))
                        }
                        ProfileLifeNowScreen(
                            state = heldWhileLeaving(picksState, onTop),
                            onTap = { question, value -> picks.pillTapped(PickStep.LifeNow, question, value) },
                            onContinue = {
                                picks.continuePressed(PickStep.LifeNow) { goTo(FlowScreen.ProfileLifeAhead) }
                            },
                            onSkip = { picks.skipPressed(PickStep.LifeNow) { goTo(FlowScreen.ProfileLifeAhead) } },
                            onBack = { picks.backPressed(PickStep.LifeNow) { goTo(FlowScreen.ProfileInterests) } },
                        )
                    }
                    FlowScreen.ProfileLifeAhead -> {
                        LaunchedEffect(onTop) {
                            if (onTop) picks.arrived(PickStep.LifeAhead, profileScreenOf(cameFrom))
                        }
                        ProfileLifeAheadScreen(
                            state = heldWhileLeaving(picksState, onTop),
                            onTap = { question, value -> picks.pillTapped(PickStep.LifeAhead, question, value) },
                            // THE END OF PROFILE CREATION, by either exit: the 48hrs Check-In intro
                            // (Check-In 01), with a new flow begun behind it.
                            onContinue = {
                                picks.continuePressed(PickStep.LifeAhead) {
                                    checkIn.beginFlow()
                                    goTo(FlowScreen.CheckInIntro)
                                }
                            },
                            onSkip = {
                                picks.skipPressed(PickStep.LifeAhead) {
                                    checkIn.beginFlow()
                                    goTo(FlowScreen.CheckInIntro)
                                }
                            },
                            onBack = { picks.backPressed(PickStep.LifeAhead) { goTo(FlowScreen.ProfileLifeNow) } },
                        )
                    }

                    // ── the 48hrs Check-In (SHOWUP-178 to SHOWUP-183) ──────────────────
                    //
                    // Every screen draws `heldWhileLeaving`: a ✕ discards the flow, and without it
                    // the outgoing step would visibly empty during its exit.
                    FlowScreen.CheckInGate -> {
                        LaunchedEffect(onTop) {
                            if (onTop) checkIn.arrived(null, ProfileScreen.CheckinSearchUnavailable, profileScreenOf(cameFrom, searchState.layout))
                        }
                        CheckInGateScreen(
                            onCheckIn = { target ->
                                if (target == CheckInAnalytics.GateTarget.InstantTab) tabTapped(Tab.Search, Tab.Instant)
                                reopenCheckIn(target)
                            },
                            onSettings = {
                                tabTapped(Tab.Search, Tab.Settings)
                                goTo(FlowScreen.CheckInSettings)
                            },
                            // Premium: Likes received (SHOWUP-186); Freemium: the likes upsell, its own
                            // story, not built -- so the tap is the nav act alone, and never opens
                            // Likes received for Freemium.
                            onLikes = {
                                tabTapped(Tab.Search, Tab.Likes)
                                if (searchState.premium) goTo(FlowScreen.LikesReceived)
                            },
                            likes = likesOpen,
                        )
                    }
                    FlowScreen.CheckInIntro -> {
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(CheckInStep.Intro) }
                        CheckInIntroScreen(
                            onStart = { checkIn.introStarted { goTo(FlowScreen.CheckInToday) } },
                            // Android back is the ✕ here: the intro has no back (story 01).
                            onClose = { closeCheckIn(CheckInStep.Intro) },
                        )
                    }
                    FlowScreen.CheckInToday, FlowScreen.CheckInTomorrow -> {
                        val step = if (current == FlowScreen.CheckInToday) CheckInStep.Today else CheckInStep.Tomorrow
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(step) }
                        val held = heldWhileLeaving(checkInState, onTop)
                        val grid = if (step == CheckInStep.Today) held.todayGrid else held.tomorrowGrid
                        if (grid != null) {
                            CheckInAvailabilityScreen(
                                grid = grid,
                                picked = grid.picked(held.picks),
                                howItWorksOpen = held.howItWorksOpen,
                                toast = held.toast,
                                toastTick = held.toastTick,
                                dialogOpen = held.noHoursDialog,
                                onSlot = { hour ->
                                    val at = grid.hours.indexOf(hour)
                                    if (at >= 0) checkIn.slotTapped(step, grid.starts[at])
                                },
                                onClearAll = { checkIn.clearAll(step) },
                                onToggleHowItWorks = checkIn::toggleHowItWorks,
                                onContinue = {
                                    checkIn.availabilityContinue(step) {
                                        goTo(if (step == CheckInStep.Today) FlowScreen.CheckInTomorrow else FlowScreen.CheckInMotivation)
                                    }
                                },
                                onConfirmNoToday = { checkIn.noHoursConfirmed { goTo(FlowScreen.CheckInTomorrow) } },
                                onCancelNoToday = { checkIn.noHoursCancelled(dismissed = false) },
                                onDismissNoToday = { checkIn.noHoursCancelled(dismissed = true) },
                                // Today's back is the intro, even after a gate reopen skipped it
                                // (story 02, Open: "Default: back -> intro").
                                onBack = {
                                    checkIn.backPressed(step) {
                                        goTo(if (step == CheckInStep.Today) FlowScreen.CheckInIntro else FlowScreen.CheckInToday)
                                    }
                                },
                                onClose = { closeCheckIn(step) },
                            )
                        }
                    }
                    FlowScreen.CheckInMotivation -> {
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(CheckInStep.Motivation) }
                        val held = heldWhileLeaving(checkInState, onTop)
                        CheckInMotivationScreen(
                            selected = held.motivation,
                            toast = held.toast,
                            toastTick = held.toastTick,
                            onPick = checkIn::motivationTapped,
                            onContinue = { checkIn.motivationContinue { goTo(FlowScreen.CheckInVibe) } },
                            onBack = { checkIn.backPressed(CheckInStep.Motivation) { goTo(FlowScreen.CheckInTomorrow) } },
                            onClose = { closeCheckIn(CheckInStep.Motivation) },
                        )
                    }
                    FlowScreen.CheckInVibe -> {
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(CheckInStep.Vibe) }
                        val held = heldWhileLeaving(checkInState, onTop)
                        CheckInVibeScreen(
                            picked = held.vibes,
                            toast = held.toast,
                            toastTick = held.toastTick,
                            onTap = checkIn::vibeTapped,
                            onContinue = { checkIn.vibeContinue { goTo(FlowScreen.CheckInFilters) } },
                            onBack = { checkIn.backPressed(CheckInStep.Vibe) { goTo(FlowScreen.CheckInMotivation) } },
                            onClose = { closeCheckIn(CheckInStep.Vibe) },
                        )
                    }
                    FlowScreen.CheckInFilters -> {
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(CheckInStep.Filters) }
                        val held = heldWhileLeaving(checkInState, onTop)
                        CheckInFiltersScreen(
                            // Null only for the instant before the arrival seeds the stand-in.
                            filters = held.filters ?: CheckInFilters(genders = emptySet()),
                            toast = held.toast,
                            toastTick = held.toastTick,
                            onGender = checkIn::genderTapped,
                            onAge = checkIn::ageMoved,
                            onDistance = checkIn::distanceMoved,
                            onContinue = { checkIn.filtersContinue { goTo(FlowScreen.CheckInUplift) } },
                            onBack = { checkIn.backPressed(CheckInStep.Filters) { goTo(FlowScreen.CheckInVibe) } },
                            onClose = { closeCheckIn(CheckInStep.Filters) },
                        )
                    }
                    FlowScreen.CheckInUplift -> {
                        LaunchedEffect(onTop) { if (onTop) checkInArrived(CheckInStep.Uplift) }
                        val held = heldWhileLeaving(checkInState, onTop)
                        CheckInUpliftScreen(
                            text = held.uplift,
                            blocked = held.upliftBlocked,
                            saving = held.saving,
                            saveFailed = held.saveFailed,
                            toast = held.toast,
                            toastTick = held.toastTick,
                            onText = checkIn::upliftChanged,
                            // Saved -> straight to Search, date search unlocked. No toast.
                            onFindMyDate = { checkIn.findMyDate { goTo(FlowScreen.CheckInSearch) } },
                            onBack = { checkIn.backPressed(CheckInStep.Uplift) { goTo(FlowScreen.CheckInFilters) } },
                            onClose = { closeCheckIn(CheckInStep.Uplift) },
                        )
                    }
                    FlowScreen.CheckInSearch -> {
                        val zone = remember { ZoneId.systemDefault() }
                        // ARRIVED HERE ALREADY: the screen coming back with this visit still in the
                        // view model is not arriving -- the pop of a profile pushed over it (which
                        // fired the grid's screen_viewed and started its re-check itself), or the
                        // activity recreated by a system change (font size, dark mode). Saved with
                        // the destination, so both bring it back; a new view model after a process
                        // death has no visit, so that one arrives. Cleared when the screen slides
                        // away, so every other way in is an arrival.
                        var arrivedHere by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(onTop) {
                            if (onTop) {
                                if (arrivedHere && search.hasVisited) return@LaunchedEffect
                                arrivedHere = true
                                val filters = checkInState.server?.filters
                                search.arrived(zone.id, filters?.let(::filtersActive).orEmpty(), profileScreenOf(cameFrom, searchState.layout))
                            } else {
                                arrivedHere = false
                                // Sliding away -- to Filters or another tab: the profile has left the screen.
                                search.leave()
                            }
                        }
                        if (searchState.layout == SearchAnalytics.Layout.Grid) {
                            SearchGridScreen(
                                state = searchState,
                                zone = zone,
                                settled = onTop && settledHere(),
                                onOpen = { id -> search.openCard(PremiumSurface.Grid, id) },
                                onLeft = { id -> search.cardLeft(PremiumSurface.Grid, id) },
                                onUndo = search::undo,
                                onUndoFocus = search::undoFocus,
                                onFilters = { goTo(FlowScreen.SearchFilters) },
                                // Premium: the likes chip opens the Likes tab (Search 02, restored 21 Oct).
                                onLikesChip = { goTo(FlowScreen.LikesReceived) },
                                onTab = { tab ->
                                    tabTapped(Tab.Search, tab)
                                    when (tab) {
                                        Tab.Settings -> goTo(FlowScreen.CheckInSettings)
                                        Tab.Likes -> goTo(FlowScreen.LikesReceived)
                                        // Instant is its own story: the tap is the nav act alone.
                                        Tab.Search, Tab.Instant -> Unit
                                    }
                                },
                                onRetry = { search.retry(zone.id) },
                            )
                        } else SearchProfileScreen(
                            state = searchState,
                            zone = zone,
                            player = mediaPlayer.surface,
                            onLike = search::like,
                            onPass = search::pass,
                            onCoachDone = search::dismissCoach,
                            onCancelConfirm = search::cancelConfirm,
                            onContinue = search::continueToMessage,
                            onDraft = search::editDraft,
                            onDismissMessage = search::dismissMessage,
                            onSkipMessage = search::skipMessage,
                            onSendMessage = search::sendMessage,
                            onOpenPhoto = search::openPhoto,
                            onViewerPage = search::viewerPage,
                            onCloseViewer = search::closeViewer,
                            onTogglePlay = search::togglePlay,
                            onScrolledOut = search::scrolledOut,
                            onSectionSeen = search::sectionSeen,
                            onFilters = { goTo(FlowScreen.SearchFilters) },
                            // Freemium: the likes upsell, its own story -- never Likes received.
                            onLikesChip = {},
                            onTab = { tab ->
                                tabTapped(Tab.Search, tab)
                                when (tab) {
                                    Tab.Settings -> goTo(FlowScreen.CheckInSettings)
                                    // The upsell (Freemium) and Instant are their own stories: the tap
                                    // is the nav act alone, as on the gate.
                                    Tab.Search, Tab.Likes, Tab.Instant -> Unit
                                }
                            },
                            onRetry = { search.retry(zone.id) },
                        )
                    }
                    FlowScreen.LikesReceived -> {
                        val zone = remember { ZoneId.systemDefault() }
                        // As on Search: the pop of a profile or a recreated activity is not an
                        // arrival while the view model still holds the list.
                        var arrivedHere by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(onTop) {
                            if (onTop) {
                                if (arrivedHere && !searchState.likes.loading) return@LaunchedEffect
                                arrivedHere = true
                                search.likesArrived(zone.id, profileScreenOf(cameFrom, searchState.layout))
                            } else {
                                arrivedHere = false
                                // Another tab: a decline waiting out its Undo is sent now.
                                search.leave()
                            }
                        }
                        // A tab root: no back button, and back returns to Search, as from Settings.
                        BackHandler { goTo(searchTab()) }
                        LikesReceivedScreen(
                            state = searchState,
                            zone = zone,
                            settled = onTop && settledHere(),
                            onOpen = { id -> search.openCard(PremiumSurface.Likes, id) },
                            onLeft = { id -> search.cardLeft(PremiumSurface.Likes, id) },
                            onUndo = search::undo,
                            onUndoFocus = search::undoFocus,
                            onBackToSearch = { goTo(searchTab()) },
                            onTab = { tab ->
                                tabTapped(Tab.Likes, tab)
                                when (tab) {
                                    Tab.Search -> goTo(searchTab())
                                    Tab.Settings -> goTo(FlowScreen.CheckInSettings)
                                    Tab.Instant -> if (checkInState.server?.active != true) {
                                        reopenCheckIn(CheckInAnalytics.GateTarget.InstantTab)
                                    }
                                    Tab.Likes -> Unit
                                }
                            },
                            onRetry = { search.likesRetry(zone.id) },
                            scrollStart = search.likesScroll,
                            onScrolled = search::likesScrolled,
                        )
                    }
                    FlowScreen.SearchFilters -> {
                        LaunchedEffect(onTop) { if (onTop) checkIn.searchFiltersOpened() }
                        val held = heldWhileLeaving(checkInState, onTop)
                        val back = { checkIn.searchFiltersLeft { goTo(FlowScreen.CheckInSearch) } }
                        CheckInFiltersScreen(
                            filters = held.filters ?: CheckInFilters(genders = emptySet()),
                            toast = held.toast,
                            toastTick = held.toastTick,
                            onGender = checkIn::genderTapped,
                            onAge = checkIn::ageMoved,
                            onDistance = checkIn::distanceMoved,
                            onContinue = {
                                checkIn.searchFiltersContinue {
                                    search.filtersSaved()
                                    goTo(FlowScreen.CheckInSearch)
                                }
                            },
                            onBack = back,
                            // Never the Check-In's ✕ here: a committed Check-In is not discarded from Search.
                            onClose = back,
                        )
                    }
                    FlowScreen.CheckInSettings -> {
                        LaunchedEffect(onTop) { if (onTop) checkIn.atTabs() }
                        // Back returns to Search -- the gate without a Check-In, Search with one.
                        BackHandler { goTo(searchTab()) }
                        SettingsPlaceholderScreen(
                            likes = likesOpen,
                            onTab = { tab ->
                                tabTapped(Tab.Settings, tab)
                                when (tab) {
                                    // Search with no Check-In IS the gate (story 01); with one, Search.
                                    Tab.Search -> goTo(searchTab())
                                    // Premium: Likes received. Freemium: the upsell, not built.
                                    Tab.Likes -> if (searchState.premium) goTo(FlowScreen.LikesReceived)
                                    // Instant is gated without a Check-In -- it opens one, as on the gate --
                                    // and its own story with one.
                                    Tab.Instant -> if (checkInState.server?.active != true) {
                                        reopenCheckIn(CheckInAnalytics.GateTarget.InstantTab)
                                    }
                                    Tab.Settings -> Unit
                                }
                            },
                        )
                    }

                    FlowScreen.Home ->
                        HomePlaceholderScreen(outcome, onStartOver = { screen = FlowScreen.SignUp })
                }
            }
            }

            // THE PROFILE, OPENED FROM THE GRID OR THE LIKES LIST (SHOWUP-185, 186): Search 01's
            // screen, extended. It slides in over the list and back off it; the list keeps its scroll
            // position underneath, saved with the flow destination.
            composable<AppRoute.SearchProfile>(
                enterTransition = {
                    val ms = if (motion.enabled) Motion.SCREEN else 0
                    slideInHorizontally(tween(ms)) { w -> w / 6 } + fadeIn(tween(if (motion.enabled) 220 else 0))
                },
                popExitTransition = {
                    val ms = if (motion.enabled) Motion.SCREEN else 0
                    slideOutHorizontally(tween(ms)) { w -> w / 6 } + fadeOut(tween(if (motion.enabled) Motion.FAST else 0))
                },
            ) {
                val zone = remember { ZoneId.systemDefault() }
                // AS IT LEAVES, THE PROFILE IT WAS: the last state with the profile open, its sheets,
                // viewer and coach closed. Drawn live, the popped screen slid away showing the empty
                // queue under the grid -- "No results", the wrong tab, no back pill.
                val open = searchState.opened != null
                val shown = heldWhileLeaving(searchState, open).let { held ->
                    if (open) held else held.copy(sheet = null, viewerAt = null, coach = false, confirming = false)
                }
                val fromLikes = shown.opened?.from == PremiumSurface.Likes
                val here = if (fromLikes) Tab.Likes else Tab.Search
                SearchProfileScreen(
                    state = shown,
                    zone = zone,
                    player = mediaPlayer.surface,
                    onLike = search::like,
                    onPass = search::pass,
                    onCoachDone = search::dismissCoach,
                    onCancelConfirm = search::cancelConfirm,
                    onContinue = search::continueToMessage,
                    onDraft = search::editDraft,
                    onDismissMessage = search::dismissMessage,
                    onSkipMessage = search::skipMessage,
                    onSendMessage = search::sendMessage,
                    onOpenPhoto = search::openPhoto,
                    onViewerPage = search::viewerPage,
                    onCloseViewer = search::closeViewer,
                    onTogglePlay = search::togglePlay,
                    onScrolledOut = search::scrolledOut,
                    onSectionSeen = search::sectionSeen,
                    onBack = search::backFromProfile,
                    onConfirmDate = search::confirmDate,
                    onRefusedDone = search::refusedDone,
                    onFilters = {
                        search.closeProfileForElsewhere()
                        goTo(FlowScreen.SearchFilters)
                    },
                    onLikesChip = {
                        search.closeProfileForElsewhere()
                        goTo(FlowScreen.LikesReceived)
                    },
                    onTab = { tab ->
                        if (tab != here) {
                            tabTapped(here, tab)
                            when (tab) {
                                Tab.Search -> {
                                    search.closeProfileForElsewhere()
                                    goTo(searchTab())
                                }
                                Tab.Likes -> {
                                    search.closeProfileForElsewhere()
                                    goTo(FlowScreen.LikesReceived)
                                }
                                Tab.Settings -> {
                                    search.closeProfileForElsewhere()
                                    goTo(FlowScreen.CheckInSettings)
                                }
                                // As on the list under it: the nav act alone with a Check-In -- the
                                // profile stays -- and without one it opens one.
                                Tab.Instant -> if (checkInState.server?.active != true) {
                                    search.closeProfileForElsewhere()
                                    reopenCheckIn(CheckInAnalytics.GateTarget.InstantTab)
                                }
                            }
                        }
                    },
                )
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
                        // IT SAYS WHEN THE CODE IS DEAD, and that is the whole point of the
                        // change. This strip showed the last code it was handed with nothing to
                        // say whether it still worked, so a tester read a correct-looking code,
                        // typed it, and was told it did not match -- twice, because the strip
                        // was the most trustworthy thing on the screen and it was wrong.
                        //
                        // `expired` is the same derivation the screen itself uses, so the strip
                        // cannot disagree with the error card under it.
                        when {
                            basicsState.expired(java.time.OffsetDateTime.now()) ->
                                "this code has EXPIRED · send a new one"
                            serverEmailCode != null -> "the code is $emailCode"
                            else -> "OFFLINE · no server · the code is $emailCode"
                        },
                        color = Color.White, fontFamily = Manrope,
                        fontWeight = FontWeight.Medium, fontSize = 11.sp,
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                if (!devTabHidden && !devMenuOpen) DevTab(onOpen = { devMenuOpen = true })
                if (devMenuOpen) {
                    val devEvents by AppAnalytics.log.entries.collectAsStateWithLifecycle()
                    DevMenu(
                        facts = DevMenuFacts(
                            current = screen,
                            signedIn = devSignedIn,
                            notificationsAsk = shouldShowAsk(notifications.read()),
                            oldAndroid = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
                            location = locationArrival(locationAccess.read()),
                            premium = searchState.premium,
                        ),
                        events = devEvents,
                        onJump = {
                            devMenuOpen = false
                            devJumpTo(it)
                        },
                        onClearEvents = AppAnalytics.log::clear,
                        onHideTab = {
                            devTabHidden = true
                            devMenuOpen = false
                        },
                        onClose = { devMenuOpen = false },
                        onTestServer = {
                            devMenuOpen = false
                            restartOnDevCheckIn(it)
                        },
                        // The menu stays open behind the frame: back returns to the States list, so
                        // frame after frame can be looked at without reopening it.
                        onOpenState = { state -> openDevState(state) },
                        onPremium = search::setPremium,
                    )
                    // Back closes the menu, never the screen under it.
                    BackHandler { devMenuOpen = false }
                }
            }
            }
        }
    }

    /**
     * Starts the app again from Startup, as a first launch would, saying why.
     *
     * NEW_TASK | CLEAR_TASK finishes this Activity and everything under it, so nothing of the
     * signed-in session survives in memory: not a view model, not the flow position. No animation
     * -- the notice is the explanation, and a slide would read as the user having gone somewhere.
     */
    /**
     * DEBUG ONLY: start again on the Check-In's in-memory test server at [screen] -- the developer
     * menu's way into the Check-In with no account. A fresh task, so the real server's state and the
     * test server's never mix in one set of view models.
     */
    private fun restartOnDevCheckIn(screen: FlowScreen) {
        if (!BuildConfig.DEBUG) return
        val fresh = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(DevCheckInLaunch.EXTRA_ON, true)
            .putExtra(DevCheckInLaunch.EXTRA_START, screen.name)
            // Past the intro, so the gate reopens at Today as a returning user's would; the intro row
            // itself still opens the intro.
            .putExtra(DevCheckInLaunch.EXTRA_INTRO_PASSED, screen != FlowScreen.CheckInIntro)
        startActivity(fresh)
    }

    /** DEBUG ONLY: one ticket frame in its exact state, over the app; back returns to it. */
    private fun openDevState(state: DevState) {
        if (!BuildConfig.DEBUG) return
        val open = Intent().setClassName(packageName, DEV_GALLERY_ACTIVITY).putExtra("state", state.id)
        runCatching { startActivity(open) }
    }

    private fun restartSignedOut() {
        val fresh = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_SESSION_ENDED, true)
        startActivity(fresh, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    companion object {
        /** Set on the restart [restartSignedOut] makes, and only there. */
        const val EXTRA_SESSION_ENDED = "com.showup.SESSION_ENDED"
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
 * The system Location page -- C's Android destination ("Android opens the Location page directly
 * and needs no path"). Falls back to the app page on a device that has no such screen.
 */
private fun openLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

/** The §11 screen a flow position is, for `referrer_screen_id`. Null where none is registered. */
private fun profileScreenOf(
    screen: FlowScreen?,
    /** The Search tab's layout: the grid is `search_grid`, the queue `search_profile`. */
    searchLayout: SearchAnalytics.Layout = SearchAnalytics.Layout.OneUp,
): ProfileScreen? = when (screen) {
    FlowScreen.ProfileName -> ProfileScreen.Name
    FlowScreen.ProfileEmail -> ProfileScreen.Email
    FlowScreen.ProfileVerifyEmail -> ProfileScreen.EmailVerification
    FlowScreen.ProfileDob -> ProfileScreen.Dob
    FlowScreen.ProfileEmbrace -> ProfileScreen.EmbraceBuild
    FlowScreen.ProfilePhotos -> ProfileScreen.Photos
    FlowScreen.ProfilePrompts -> ProfileScreen.Prompts
    FlowScreen.ProfileMedia -> ProfileScreen.Media
    FlowScreen.ProfileNotifications -> ProfileScreen.Notifications
    FlowScreen.ProfileReachability -> ProfileScreen.Reachability
    FlowScreen.ProfileLocation -> ProfileScreen.Location
    FlowScreen.ProfileEmbraceDetails -> ProfileScreen.EmbraceDetails
    FlowScreen.ProfileHeight -> ProfileScreen.Height
    FlowScreen.ProfileGender -> ProfileScreen.Gender
    FlowScreen.ProfileOrientation -> ProfileScreen.Orientation
    FlowScreen.ProfileDatingLanguage -> ProfileScreen.DatingLanguage
    FlowScreen.ProfileEducation -> ProfileScreen.Education
    FlowScreen.ProfileReligion -> ProfileScreen.Religion
    FlowScreen.ProfilePolitics -> ProfileScreen.Politics
    FlowScreen.ProfileInterests -> ProfileScreen.Interests
    FlowScreen.ProfileLifeNow -> ProfileScreen.LifeNow
    FlowScreen.ProfileLifeAhead -> ProfileScreen.LifeAhead
    FlowScreen.CheckInGate -> ProfileScreen.CheckinSearchUnavailable
    FlowScreen.CheckInIntro -> ProfileScreen.CheckinIntro
    FlowScreen.CheckInToday -> ProfileScreen.CheckinToday
    FlowScreen.CheckInTomorrow -> ProfileScreen.CheckinTomorrow
    FlowScreen.CheckInMotivation -> ProfileScreen.CheckinMotivation
    FlowScreen.CheckInVibe -> ProfileScreen.CheckinVibe
    FlowScreen.CheckInFilters -> ProfileScreen.CheckinFilters
    FlowScreen.CheckInUplift -> ProfileScreen.CheckinUplift
    FlowScreen.CheckInSearch ->
        if (searchLayout == SearchAnalytics.Layout.Grid) ProfileScreen.SearchGrid else ProfileScreen.SearchProfile
    FlowScreen.SearchFilters -> ProfileScreen.CheckinFilters
    FlowScreen.LikesReceived -> ProfileScreen.LikesReceived
    else -> null
}

/**
 * Whether the screen this is called from has SETTLED: its destination's lifecycle is resumed, which
 * Navigation does only once a push or a pop transition has finished. The fly-out waits for it -- "t =
 * 0 when the back transition has settled".
 */
@Composable
private fun settledHere(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * What a screen draws: the live [value] while it is the one being shown, and the last value it had
 * once it starts sliding away.
 *
 * The detail steps share one state, and leaving a step puts its part back to the saved value -- so
 * without this, Back or Skip would visibly clear the height, or the picked row, on the outgoing
 * screen during its exit. Frozen, the outgoing screen leaves looking as the user left it, and the
 * incoming one is drawn right from its first frame. A plain remembered slot, not state: it only
 * caches what the live value already drew.
 */
@Composable
private fun <T> heldWhileLeaving(value: T, onTop: Boolean): T {
    val held = remember { Held(value) }
    if (onTop) held.value = value
    return held.value
}

private class Held<T>(var value: T)

/**
 * One single-select detail step, hosted: its arrival, and every press routed to the view model.
 * Five steps share it because they share every rule -- the differences are on [DetailStep].
 */
@Composable
private fun DetailChoiceHost(
    step: DetailStep,
    details: ProfileDetailsViewModel,
    state: DetailsUiState,
    referrer: ProfileScreen?,
    onTop: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    LaunchedEffect(onTop) { if (onTop) details.arrived(step, referrer) }
    ProfileChoiceScreen(
        step = step,
        state = heldWhileLeaving(state, onTop),
        onTap = { details.optionTapped(step, it) },
        onToggleVisibility = { details.visibilityToggled(step) },
        onContinue = { details.continuePressed(step, onNext) },
        onSkip = { details.skipPressed(step, onNext) },
        onBack = { details.backPressed(step, onPrevious) },
    )
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
