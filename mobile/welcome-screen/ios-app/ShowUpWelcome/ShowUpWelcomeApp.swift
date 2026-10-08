//  ShowUpWelcomeApp.swift
//  ShowUp · Tutorial — app entry point
//
//  This target exists only to render the tutorial screens on a simulator or device. It is not the
//  ShowUp app; when the real iOS app exists these views move there and this goes away.
//
//  The navigation is a placeholder: real routing arrives with the rest of the app. It exists so the
//  flow can be walked end to end in the simulator.

import ShowUpAPI
import SwiftUI

@main
struct ShowUpWelcomeApp: App {

    /// Crash reporting starts in the initialiser, which is the earliest point this target owns.
    ///
    /// Starting it inside a view's `onAppear` instead would miss every crash that happens before
    /// the first frame — the ones that are hardest to reproduce and most likely to hit every user
    /// at once. Returns false and does nothing at all unless a DSN is configured for this build.
    init() {
        Crashes.start(
            enabled: CrashReporting.enabled,
            dsn: CrashReporting.dsn,
            environment: Self.isDebugBuild ? "development" : "production",
            release: "org.loveiq.showup@\(Self.version)"
        )
    }

    var body: some Scene {
        WindowGroup { SessionScope() }
    }

    // `static let`, not a computed `static var`. Both would compile, and audit/
    // check-swift-concurrency.py rejects any `static var` on sight -- a deliberately blunt rule,
    // because telling a computed property from stored mutable state by pattern matching is
    // unreliable and stored mutable global state is what Swift 6 actually rejects. `let` is the
    // better code here regardless: each of these is evaluated once rather than on every read.
    #if DEBUG
    private static let isDebugBuild = true
    #else
    private static let isDebugBuild = false
    #endif

    private static let version: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
}

/// Slide-and-fade, matching the Android host.
///
/// Deliberately not `.move(edge:)`: that slides a full screen width, which reads as a page being
/// thrown rather than advanced. 64pt is roughly the sixth-of-a-width Android uses, so the two
/// platforms feel like the same product.
private struct SlideFade: ViewModifier {
    let x: CGFloat
    let opacity: Double
    func body(content: Content) -> some View {
        content.offset(x: x).opacity(opacity)
    }
}

/// The scene's root: everything the signed-in session holds lives under it, and goes with it.
///
/// WHEN THE SERVER ENDS THE SESSION the auth layer has already cleared the tokens; what is left is
/// everything the scene still holds for the person who was signed in — a dozen models, the flow
/// position, half-typed answers. Resetting each by hand is a list that goes stale the day a screen
/// is added, so the whole flow starts again instead, as Android's restart does: the epoch moves on,
/// `.id` rebuilds `TutorialFlow` with fresh `@State` models, and every scene key is a new, empty
/// slot (see `SceneKey`). The user lands on Startup, and the notice says why.
private struct SessionScope: View {
    /// The ONE literal scene key: the others are derived from it.
    @SceneStorage("session.epoch") private var epoch = 0
    /// Not scene-restored: a notice that came back after the app was killed would describe a
    /// moment that is long over.
    @State private var sessionNotice = false

    var body: some View {
        // The epoch this flow was built for. A flow torn down BY the rebuild that raised the notice
        // dismisses it on its way out (Startup's `onDisappear`), and that must not take down the
        // notice its replacement is about to show -- so a dismissal from an older flow is ignored.
        let builtFor = epoch
        TutorialFlow(epoch: epoch, sessionNotice: sessionNotice, onSessionNoticeDismissed: {
            if epoch == builtFor { sessionNotice = false }
        })
            .id(epoch)
            // `initial: true`, so news that landed before this view first drew — during the very
            // first request of a launch — is acted on, not missed.
            .onChange(of: SessionEndedSignal.shared.pending, initial: true) { _, pending in
                guard pending, SessionEndedSignal.shared.consume() else { return }
                epoch += 1
                sessionNotice = true
            }
    }
}

private struct TutorialFlow: View {
    /// Which signed-in session this flow belongs to — every scene key below is scoped to it.
    let epoch: Int
    /// The "please log in again" notice, owned by `SessionScope` so it survives the rebuild that
    /// put the user here. Startup shows it; see `SessionEndedNotice`.
    let sessionNotice: Bool
    let onSessionNoticeDismissed: () -> Void

    init(epoch: Int, sessionNotice: Bool, onSessionNoticeDismissed: @escaping () -> Void) {
        self.epoch = epoch
        self.sessionNotice = sessionNotice
        self.onSessionNoticeDismissed = onSessionNoticeDismissed
        _screenRaw = SceneStorage(wrappedValue: FlowScreen.signUp.rawValue,
                                  SceneKey.scoped("flow.screen", epoch: epoch))
        _outcomeRaw = SceneStorage(wrappedValue: SignUpOutcome.newAccount.storageKey,
                                   SceneKey.scoped("flow.outcome", epoch: epoch))
        _firstName = SceneStorage(wrappedValue: "",
                                  SceneKey.scoped("basics.firstName", epoch: epoch))
        _email = SceneStorage(wrappedValue: "", SceneKey.scoped("basics.email", epoch: epoch))
        _marketingConsent = SceneStorage(wrappedValue: false,
                                         SceneKey.scoped("basics.marketingConsent", epoch: epoch))
        _promptsStored = SceneStorage(wrappedValue: "",
                                      SceneKey.scoped("profile.prompts", epoch: epoch))
        _detailsStored = SceneStorage(wrappedValue: "",
                                      SceneKey.scoped("profile.details", epoch: epoch))
        _confettiPlayed = SceneStorage(wrappedValue: false,
                                       SceneKey.scoped("embrace2.confettiPlayed", epoch: epoch))
        _locationStartRaw = SceneStorage(wrappedValue: LocationState.ask.rawValue,
                                         SceneKey.scoped("location.start", epoch: epoch))
        _cameFromRaw = SceneStorage(wrappedValue: "",
                                    SceneKey.scoped("flow.cameFrom", epoch: epoch))
        _dobStored = SceneStorage(wrappedValue: "", SceneKey.scoped("basics.dob", epoch: epoch))
        _hideAgeStored = SceneStorage(wrappedValue: false,
                                      SceneKey.scoped("basics.hideAge", epoch: epoch))
    }

    // SceneStorage, not State: a process death mid-tutorial should not silently drop the user back
    // to card 1. Android has survived this since it was written, because rememberSaveable is the
    // default idiom there; iOS had no restoration at all.
    //
    // Scene-scoped and NOT @AppStorage on purpose. This is where the user is right now, not a
    // preference -- @AppStorage would still be holding a half-finished tutorial position weeks
    // later, and would restore it into a scene that had been properly closed.
    @SceneStorage private var screenRaw: String
    private var screen: FlowScreen { FlowScreen(rawValue: screenRaw) ?? .signUp }

    // Transition direction only. Deliberately NOT restored: there is no animation on a relaunch,
    // so the value it would restore describes a movement that is not happening.
    @State private var forward = true

    // Kept only so the placeholder home screen can name the rule that sent the user there, which
    // is what makes SHOWUP-146 demonstrable. Not product state, but it has to survive with the
    // screen or Home restores describing the wrong route.
    @SceneStorage private var outcomeRaw: String
    private var outcome: SignUpOutcome { SignUpOutcome(storageKey: outcomeRaw) ?? .newAccount }

    // Demo state for "The basics". Scene-scoped like the rest of the flow, but in-memory only:
    // the real flow persists per completed step and resumes onto the last incomplete one, which
    // depends on a profile-progress store that does not exist yet. Walkable, not shipped.
    @SceneStorage private var firstName: String
    @SceneStorage private var email: String
    @SceneStorage private var marketingConsent: Bool

    // ── "The basics" now talks to the backend ──────────────────────────────
    //
    // The code screen sends, waits, counts down against a SERVER timestamp and retries, which is
    // the moment the iOS CLAUDE.md names for @Observable. The @SceneStorage values that used to
    // live here could not own a request in flight.
    //
    // Built once for the scene, from `APIAccess` — one client and one Keychain store for the
    // whole app. This used to construct its own `ShowUpAPI(tokens: KeychainTokenStore())`, which
    // was a second client deciding for itself which environment to talk to.
    @State private var basics = BasicsModel(
        repo: BasicsRepository(api: APIAccess.client)
    )

    // ── Signing in ─────────────────────────────────────────────────────────
    //
    // The other half of the same session. It shares `APIAccess.tokens` with `basics` deliberately
    // and not incidentally: this model WRITES the tokens that model then sends. Two stores would
    // both read the same Keychain items and so would probably work, and would fail confusingly
    // the first time one of them cached anything.
    @State private var phoneAuth = PhoneAuthModel(
        repo: PhoneAuthRepository(api: APIAccess.client, tokens: APIAccess.tokens)
    )

    // The date and the visibility choice stay scene-scoped as well as living on the model, so a
    // rotation mid-typing does not lose them. The model is the source of truth while the screen
    // is alive; these are what survive it.
    // ── "The real you" ─────────────────────────────────────────────────────
    //
    // Its own model for the same reason as the two above, and a sharper one: this screen holds
    // several uploads at once, each of which has to be cancellable on its own.
    @State private var photos = PhotosModel(
        repo: PhotosRepository(api: APIAccess.client),
        access: SystemPhotoAccess()
    )

    /// Which OS surface is up, if any. Never both, and never one without the source sheet having
    /// asked first — a slot tap opens the sheet, and the sheet opens one of these.
    @State private var pickerSource: PhotoSource?

    /// The prompts screen has a model now, and the comment that used to sit here said when it
    /// would: "it becomes an `@Observable` the day persistence lands." `/me/prompts` exists.
    ///
    /// The scene storage is still here and still does the same job: the SAVED prompts come from
    /// the server, and this holds the half-written DRAFT, which is not a prompt and has nothing to
    /// send. The model writes through to it on every change.
    @SceneStorage private var promptsStored: String

    @State private var prompts = PromptsModel(repo: PromptsRepository(api: APIAccess.client))

    /// The media step (SHOWUP-161).
    ///
    /// THE CAMERA IS NOT A SECOND `@State` HERE. It belongs to the capture factory, which the model
    /// holds, and the view reads it back through `media.cameraSession`. Holding it separately meant
    /// two `CameraSession` instances — SwiftUI's `@State` initialisers cannot reference each other,
    /// so the model built its own — and a viewfinder previewing one session while the recorder
    /// wrote from another is a live preview over a black recording.
    /// Reads the OS status. Separate from the model because the host needs it BEFORE the model
    /// exists — the skip is decided on the way out of media.
    private let notificationAccess = UNNotificationAccess()

    @State private var notifications = NotificationsModel()

    @State private var reachability = ReachabilityModel(
        // No endpoint for the push consent yet -- see ReachabilityRepository.
        consent: NoConsentBackend()
    )

    /// Held rather than built in `onAppear`: a new host every redraw would be a new object for
    /// the model's weak reference to point at, and the one built during a suspended
    /// `requestNotificationPermission` would be the one that goes away.
    private let reachabilityHost = AppReachabilityHost()

    @State private var media = MediaModel(
        repo: MediaRepository(api: APIAccess.client),
        access: AVMediaAccess(),
        capture: AVMediaCaptureFactory(),
        player: sharedMediaPlayer
    )

    /// Read once per launch to decide where a half-finished profile picks up (flow rule 4a).
    private let progressRepo = ProfileProgressRepository(api: APIAccess.client)
    @State private var resumeChecked = false

    // ── the saved flow position, Location and "Share some details" (SHOWUP-165 to 173) ──────
    //
    // ONE model for Embrace 2 and the seven steps: they share one answer sheet, and each step's
    // back has to show the previous step's saved value. Its draft lives in scene storage, so a
    // scene restore mid-step keeps what was picked.
    @State private var details = DetailsModel(
        store: ProfileDetailsRepository(api: APIAccess.client),
        positions: FlowPositionReporter(api: APIAccess.client)
    )
    @SceneStorage private var detailsStored: String
    /// Whether Embrace 2's confetti has started for this showing — so a scene restore onto the
    /// bridge does not replay it. Cleared by the push, never by the way out.
    @SceneStorage private var confettiPlayed: Bool

    @State private var location = LocationModel(positions: FlowPositionReporter(api: APIAccess.client))
    /// Held rather than built in `onAppear`, for the reason `reachabilityHost` gives.
    private let locationHost = AppLocationHost()
    /// Which state 12 opens in — decided by the arrival matrix BEFORE the push.
    @SceneStorage private var locationStartRaw: String

    /// Where the user came from, for `referrer_screen_id`: "set by the navigation, never hard-coded
    /// per screen". Scene-scoped like the screen itself, so a restore keeps it.
    @SceneStorage private var cameFromRaw: String
    private var cameFrom: FlowScreen? { FlowScreen(rawValue: cameFromRaw) }

    @SceneStorage private var dobStored: String
    @SceneStorage private var hideAgeStored: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Becoming active again is the only moment a permission granted in Settings can be noticed.
    @Environment(\.scenePhase) private var scenePhase

    /// Every navigation goes through here so the transition direction is always set before the
    /// state change that triggers it.
    private func go(to next: FlowScreen) {
        cameFromRaw = screen.rawValue
        forward = next > screen
        if reduceMotion {
            screenRaw = next.rawValue
        } else {
            withAnimation(.easeInOut(duration: Motion.screen)) { screenRaw = next.rawValue }
        }
    }

    private var transition: AnyTransition {
        if reduceMotion { return .identity }
        let dx: CGFloat = forward ? 64 : -64
        return .asymmetric(
            insertion: .modifier(active: SlideFade(x: dx, opacity: 0),
                                 identity: SlideFade(x: 0, opacity: 1)),
            removal: .modifier(active: SlideFade(x: -dx, opacity: 0),
                               identity: SlideFade(x: 0, opacity: 1))
        )
    }

    /// SHOWUP-158, as its own property.
    ///
    /// Every closure is one call on the model, which owns the state and the requests. It was eight
    /// inline closures rewriting a scene-storage string until `/me/prompts` existed.
    private var promptsScreen: some View {
        ProfilePromptsView(
            state: prompts.state,
            onBack: { go(to: .profilePhotos) },
            onOpenTopics: { prompts.openTopics() },
            // THE SAME FUNCTION TWICE, and that is the point: a suggestion card and a row of
            // the browse sheet are the same act now. Registry 1.4.5 retired the property that
            // told them apart.
            onWriteTopic: { prompts.chooseTopic($0) },
            onPickTopic: { prompts.chooseTopic($0) },
            onEditPrompt: { prompts.editPrompt($0) },
            onDraftChange: { prompts.draftChanged($0) },
            onHideExample: { prompts.hideExample() },
            onSave: { prompts.save() },
            onDismissSheet: { prompts.dismissSheet($0) },
            // The model decides and records; the host only routes. Continue is never disabled, so
            // the refused press is a real press with a real event behind it rather than a button
            // that did nothing.
            // Into the media step, which is where "The real you" actually ends.
            onContinue: { if prompts.continuePressed() { go(to: .profileMedia) } },
            // The SAME call on the refused press, which is what makes the two mutually exclusive:
            // the screen picks a branch, the model re-checks and records whichever one it was.
            onRefused: { _ = prompts.continuePressed() })
            // Reads what the account already holds, and reports the arrival. Both are idempotent —
            // the model keeps the answer and holds the step's start time — and arriving from
            // photos and arriving from a resume both land here.
            .onAppear {
                let stored = $promptsStored
                prompts.attach(stored: stored.wrappedValue) { stored.wrappedValue = $0 }
                prompts.arrived(referrer: .photos)
                prompts.load()
            }
    }

    /// SHOWUP-161, as its own property, for the same reason the prompts screen is one.
    private var mediaScreen: some View {
        // ARRIVAL IS KEYED TO THE STEP, NOT TO THE SURFACE. The `.task` used to sit on the media
        // view inside the `else`, so it re-ran every time a take ended -- the view disappeared
        // while the viewfinder was up and reappeared on the way back. That fired a second
        // `screen_viewed`, a second PAIR of `profile_step_viewed`, and reset the step's start time.
        // On the Group it belongs to the position in the flow, which is what `arrived` means.
        Group { mediaSurface }
            .task { await media.arrived() }
            // RE-READ BOTH STATUSES ON EVERY FOREGROUND. "Returning from Settings with access
            // granted lands on the working card, never on the blocked row."
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { media.refreshAccess() }
                // AND STOP PLAYING WHEN THE APP GOES AWAY.
                //
                // iOS suspends the audio on its own here, because this app declares no
                // background-audio capability -- but the MODEL would not know: its ticker would
                // keep polling a playhead that has stopped moving and the card would show a frozen
                // `0:03 / 0:14` until the clock ran out. Stopping is the honest state, and it
                // matches what the Android side has to do for real, where nothing suspends it.
                if phase != .active { media.stopPlayback() }
            }
    }

    /// Where media's Continue and Skip both land.
    ///
    /// THE STATUS IS READ BEFORE THE SCREEN IS PUSHED, NEVER AFTER IT MOUNTS. The skip case is
    /// decided here, so the user never sees the ask mount and navigate away — no toast, no
    /// confirmation, no flash.
    ///
    /// 09 IS STILL SKIPPED ON A DETERMINED STATUS, AND 10 NEVER IS.
    ///
    /// SHOWUP-163 leaves the guard alone — "unchanged on 09: the Android <= 12 / already-determined
    /// skip guard" — so a user whose permission is already settled goes straight past the explainer
    /// to Stay reachable, which is the screen that can actually act on it.
    private func afterMedia() async -> FlowScreen {
        let status = await notificationAccess.read()
        guard shouldShowAsk(status) else {
            // NO REGISTRATION HERE ANY MORE. Stay reachable is the next screen either way and it
            // registers on Save — see NotificationsModel, where `skipped` used to be.
            return .profileReachability
        }
        return .profileNotifications
    }

    @ViewBuilder private var notificationsScreen: some View {
        ProfileNotificationsView(
            onEnable: {
                // The CTA only navigates now. False on a second press, and nothing is reported
                // for either — see NotificationsModel.continuePressed.
                if notifications.continuePressed() {
                    details.record(.notifications)
                    go(to: .profileReachability)
                }
            },
            busy: notifications.sheetUp
        )
        // `screen_viewed` only. SHOWUP-163 moved `permission_prompted` and the two OS-dialog
        // events to Stay reachable, which is where the dialog is raised now.
        .onAppear { notifications.arrived() }
        // NO FOREGROUND RE-READ AND NO SELF-ADVANCE ANY MORE. Both existed because this screen's
        // only button raised a dialog the OS shows once per install, so a status that became
        // determined while it was mounted left a dead CTA. It raises nothing now.
    }

    /// Stay reachable (SHOWUP-163).
    ///
    /// THE HOST DOES THE TWO THINGS ONLY AN APP CAN: raise the dialog and leave for Settings.
    /// Everything else is in the model, which is what lets every rule on this screen be tested
    /// with no device.
    @ViewBuilder private var reachabilityScreen: some View {
        ProfileReachabilityView(
            state: reachability.state,
            onPushChange: { on in Task { await reachability.pushChanged(on) } },
            onInterestToggle: { reachability.interestToggled($0) },
            onKeepActive: { Task { await reachability.keepActive() } },
            onOpenSettings: { reachability.openSettings() },
            // THE SAME DESTINATION AS `onSave`, because on state D this IS the save finishing —
            // see ReachabilityModel.confirmDeactivation. On state C the closure is never reached.
            onConfirmDeactivate: {
                Task {
                    var advanced = false
                    await reachability.confirmDeactivation { advanced = true }
                    if advanced { await leaveReachability() }
                }
            },
            onPrivacy: {
                reachability.privacyTapped()
                // REPORTS AND GOES NOWHERE, which is what the welcome flow's three legal links
                // already do: `SignUpFlow.onOpenLegal` defaults to a no-op and this host supplies
                // none, because the documents are not hosted yet. Where they live is one ticket
                // for all seven links, not a decision to take on this screen.
            },
            onSave: {
                // Into Location (12), through its arrival matrix (SHOWUP-165).
                Task {
                    var advanced = false
                    await reachability.savePressed { advanced = true }
                    if advanced { await leaveReachability() }
                }
            }
        )
        .onAppear {
            reachability.attach(reachabilityHost)
            Task { await reachability.arrived() }
        }
        // The status is re-read on every foreground, so returning from Settings with notifications
        // allowed shows the toggle on. NOTHING AUTO-ADVANCES: the user taps Save preferences
        // again, and no dialog is raised.
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await reachability.foregrounded() }
        }
    }

    @ViewBuilder private var mediaSurface: some View {
        if let take = media.state.take {
            MediaCaptureView(
                take: take,
                camera: media.cameraSession,
                onCancel: { Task { await media.cancelTake() } },
                onStop: { Task { await media.stopPressed() } },
                onPlay: { media.playPressed() },
                onRetake: { Task { await media.retakeFromReview() } },
                onAccept: { media.acceptTake() },
                playback: media.state.playback,
                player: sharedMediaPlayer.surface
            )
            // The camera only runs while a take is on screen. Leaving it running behind the media
            // screen would hold the hardware, warm the phone and light the OS recording indicator
            // for a user who is reading a list of prompts.
            .onAppear { if take.kind == .video { media.cameraSession?.startIfNeeded() } }
            .onDisappear { media.cameraSession?.stop() }
        } else {
            ProfileMediaView(
                state: media.state,
                onBack: { go(to: .profilePrompts) },
                onOpenPrompts: { media.openPrompts($0, entryPoint: $1) },
                onPickPrompt: { media.pickPrompt($0) },
                onCommitPrompt: {
                    // The model records the selection and answers with what still has to be
                    // requested. Asking happens HERE, on the commit CTA -- not on entry, and not on
                    // `See the prompts`.
                    Task {
                        guard let sheet = media.state.sheet,
                              let promptId = sheet.selectedId else { return }
                        let missing = await media.commitPrompt()
                        if !missing.isEmpty {
                            await media.requestAndBegin(sheet.kind, promptId: promptId,
                                                        capabilities: missing)
                        }
                    }
                },
                onDismissSheet: { media.dismissPrompts($0) },
                // WIRED TO THE CARD'S OWN FUNCTION. This was `playPressed`, which guards on
                // `state.take` and so returned immediately on this screen -- the play control the
                // design draws on every filled card did nothing at all.
                onPlay: { media.cardPlayPressed($0) },
                onRetake: { media.retakeFromCard($0) },
                onDelete: { media.delete($0) },
                onRetryUpload: { media.retryUpload($0) },
                onPermissionAction: { capability, status in
                    if status == .canAsk {
                        // Android only — iOS shows each alert once. Kept so the two platforms share
                        // one state machine; see MediaAccess.swift.
                        Task { _ = await AVMediaAccess().request(capability); media.refreshAccess() }
                    } else {
                        // Permanently denied. Neither platform deep-links to a single permission
                        // row, so this opens our app's own page and the copy names the row to look
                        // for.
                        openAppSettings()
                    }
                },
                platformLabel: { media.platformLabel($0) },
                // Sound does not follow the user off the screen.
                onSkip: {
                    media.stopPlayback(); media.skipPressed()
                    // Past media, whichever way: the saved flow position.
                    details.record(.media_video)
                    Task { go(to: await afterMedia()) }
                },
                onContinue: {
                    media.stopPlayback(); media.continuePressed()
                    details.record(.media_video)
                    Task { go(to: await afterMedia()) }
                }
            )
        }
    }

    // MARK: Location, Embrace 2 and "Share some details" (SHOWUP-165 to SHOWUP-173)

    /// Out of Stay reachable: record the position, then into location through its matrix.
    private func leaveReachability() async {
        details.record(.reachability)
        await enterLocation()
    }

    /// Into location: Location Services off -> C, not determined -> A, granted -> skipped silently
    /// to Embrace 2, denied or restricted -> B.
    private func enterLocation() async {
        switch await location.decideArrival() {
        case .skip:
            pushEmbraceDetails()
        case .show(let state):
            locationStartRaw = state.rawValue
            go(to: .profileLocation)
        }
    }

    /// Past location, by any of its exits: grant, `Not now`, or a grant noticed on return.
    private func leaveLocation() {
        location.left()
        pushEmbraceDetails()
    }

    /// A PUSH of Embrace 2 — the only thing that may play its confetti. Clearing the flag here,
    /// and not on the way out, means a pop's exit transition can never start a rain.
    private func pushEmbraceDetails() {
        confettiPlayed = false
        go(to: .profileEmbraceDetails)
    }

    /// Binds the details model to scene storage — idempotent, so every step can call it.
    private func attachDetails() {
        let stored = $detailsStored
        details.attach(stored: stored.wrappedValue) { stored.wrappedValue = $0 }
    }

    /// The §11 screen a flow position is, for `referrer_screen_id`. Nil where none is registered.
    private func profileScreen(of flow: FlowScreen?) -> ProfileScreen? {
        switch flow {
        case .profilePrompts: return .prompts
        case .profileMedia: return .media
        case .profileNotifications: return .notifications
        case .profileReachability: return .reachability
        case .profileLocation: return .location
        case .profileEmbraceDetails: return .embraceDetails
        case .profileHeight: return .height
        case .profileGender: return .gender
        case .profileOrientation: return .orientation
        case .profileDatingLanguage: return .datingLanguage
        case .profileEducation: return .education
        case .profileReligion: return .religion
        case .profilePolitics: return .politics
        default: return nil
        }
    }

    @ViewBuilder private var locationScreen: some View {
        ProfileLocationView(
            state: location.ui.state,
            requesting: location.ui.requesting,
            onAllow: { Task { await location.allowPressed { leaveLocation() } } },
            onOpenSettings: { location.openSettingsPressed() },
            onNotNow: { location.notNowPressed { leaveLocation() } }
        )
        .onAppear {
            location.attach(locationHost)
            // The stored start is only the first frame; `arrived` reads the status again — a scene
            // restored after the system ended the app comes back already active, so the phase
            // change below would never fire for a grant made in Settings meanwhile.
            let start = LocationState(rawValue: locationStartRaw) ?? .ask
            let referrer = profileScreen(of: cameFrom)
            Task { await location.arrived(initial: start, referrer: referrer) { leaveLocation() } }
        }
        // RE-READ ON EVERY FOREGROUND: Location Services turned on in C shows A; a grant in Settings
        // from B or C advances silently. Nothing while our own dialog is up.
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await location.foregrounded { leaveLocation() } }
        }
    }

    @ViewBuilder private var embraceDetailsScreen: some View {
        // A back-pop from height is not a push: no `embrace_bridge_viewed`, no confetti replay.
        let pop = cameFrom == .profileHeight
        ProfileEmbraceDetailsView(
            firstName: firstName,
            onContinue: { details.embraceContinue { go(to: .profileHeight) } },
            playConfetti: !pop,
            confettiPlayed: $confettiPlayed
        )
        .onAppear {
            // Attached here too: the bridge's "announced" flag is restored from the same storage.
            attachDetails()
            details.embraceArrived(referrer: profileScreen(of: cameFrom), pop: pop)
        }
    }

    @ViewBuilder private var heightScreen: some View {
        ProfileHeightView(
            state: details.state,
            onHeightChange: { details.heightChanged($0) },
            onToggleVisibility: { details.visibilityToggled(.height) },
            onContinue: { Task { await details.continuePressed(.height) { go(to: .profileGender) } } },
            onSkip: { details.skipPressed(.height) { go(to: .profileGender) } },
            onBack: { details.backPressed(.height) { go(to: .profileEmbraceDetails) } }
        )
        .onAppear {
            attachDetails()
            details.arrived(.height, referrer: profileScreen(of: cameFrom))
        }
    }

    @ViewBuilder private var datingLanguageScreen: some View {
        ProfileDatingLanguageView(
            state: details.state,
            onTap: { details.languageTapped($0) },
            onToggleVisibility: { details.visibilityToggled(.datingLanguage) },
            onContinue: {
                Task { await details.continuePressed(.datingLanguage) { go(to: .profileEducation) } }
            },
            onSkip: { details.skipPressed(.datingLanguage) { go(to: .profileEducation) } },
            onBack: { details.backPressed(.datingLanguage) { go(to: .profileOrientation) } }
        )
        .onAppear {
            attachDetails()
            details.arrived(.datingLanguage, referrer: profileScreen(of: cameFrom))
        }
    }

    /// One single-select step: five share it, because they share every rule.
    private func choiceScreen(_ step: DetailStep, next: FlowScreen, previous: FlowScreen) -> some View {
        ProfileChoiceView(
            step: step,
            state: details.state,
            onTap: { details.optionTapped(step, $0) },
            onToggleVisibility: { details.visibilityToggled(step) },
            onContinue: { Task { await details.continuePressed(step) { go(to: next) } } },
            onSkip: { details.skipPressed(step) { go(to: next) } },
            onBack: { details.backPressed(step) { go(to: previous) } }
        )
        .onAppear {
            attachDetails()
            details.arrived(step, referrer: profileScreen(of: cameFrom))
        }
    }

    /// False in any release build.
    ///
    /// A computed constant rather than an `#if` wrapped around the ViewBuilder branch: a
    /// conditional-compilation block inside a view body changes what the body RETURNS between
    /// configurations, which is how a debug-only view ends up altering release layout. This way
    /// both builds compile the same view and one of them never shows it. The same shape as
    /// `SignUpFlow.showDevStrip`, for the same reason.
    private var showDevStrip: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    /// The email verification code, on screen, in a debug build only.
    ///
    /// THE PHONE FLOW HAS HAD THIS AND THE EMAIL FLOW NEVER DID, which made the verification
    /// screen unwalkable against a real backend: a code was required and nothing on screen could
    /// tell you what it was, so the only way through was reading the server log. The challenge
    /// carries the code whenever `AUTH_EXPOSE_OTP` is on, which it is by default outside
    /// production.
    ///
    /// THREE conditions, each closing a different leak: the nil check keeps it absent when a
    /// server chooses not to expose it, the screen check keeps it off every other screen, and
    /// `showDevStrip` is false in any release build.
    @ViewBuilder private var emailDevStrip: some View {
        if showDevStrip, let devCode = basics.devCode, screen == .profileVerifyEmail {
            HStack(spacing: Spacing.sm) {
                Text("TEST BUILD")
                    .font(F.manrope(9, .bold))
                    .tracking(0.7)
                    .foregroundColor(Color(hex: 0xFFAE8F))
                Text("the code is \(devCode)")
                    .font(F.manrope(11, .medium))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.vertical, 5)
            .background(Capsule().fill(Color(hex: 0x1D1129).opacity(0.90)))
            .padding(.top, Spacing.xs)
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            Group {
                // Exhaustive on purpose. ShowUpEveryTime used to be the `default:` branch, which
                // meant any unexpected value rendered card 6; every screen is named now and the
                // compiler fails if one is added and not handled here.
                switch screen {
                // Welcome & sign-up (SHOWUP-140/142/143/144) runs before the tutorial,
                // which is the real order: you sign up, then you are shown how it works.
                // SignUpFlowView owns every step and every piece of state inside it.
                // SHOWUP-146, the whole ticket in one line: the tutorial for a new account,
                // straight into the app for a returning member.
                case .signUp: SignUpFlowView(onFinished: { o in
                    outcomeRaw = o.storageKey
                    go(to: showsTutorial(o) ? .tutorialWelcome : .home)
                }, auth: phoneAuth, epoch: epoch, sessionEnded: sessionNotice,
                   onSessionNoticeDismissed: onSessionNoticeDismissed)
                case .tutorialWelcome: WelcomeView(onContinue: { go(to: .meetInRealLife) })
                case .meetInRealLife: MeetInRealLifeView(onNext: { go(to: .matchOnAvailability) })
                case .matchOnAvailability:
                    MatchOnAvailabilityView(onNext: { go(to: .matchMeansMeet) },
                                            onBack: { go(to: .meetInRealLife) })
                case .matchMeansMeet:
                    MatchMeansMeetView(onNext: { go(to: .thirtyMinutes) },
                                       onBack: { go(to: .matchOnAvailability) })
                case .thirtyMinutes:
                    ThirtyMinutesView(onNext: { go(to: .showUpEveryTime) },
                                      onBack: { go(to: .matchMeansMeet) })
                case .showUpEveryTime: ShowUpEveryTimeView(
                    // SHOWUP-146 sent the tutorial's far end straight to the app. Profile creation
                    // now sits between the two, which is the real order.
                    onFinish: { go(to: .profileName) },
                    onBack: { go(to: .thirtyMinutes) })
                // SHOWUP-150. No back: profile creation is mandatory once entered.
                case .profileName:
                    ProfileNameView(value: $firstName, onContinue: { _ in go(to: .profileEmail) })
                case .profileEmail:
                    // Continue SENDS the code and only advances once the server has accepted it.
                    // Navigating first would put the user on a screen waiting for a code that was
                    // never dispatched.
                    // Argument order follows the declaration, which Swift requires and
                    // audit/check-swift-arg-order.py enforces ahead of the compiler.
                    ProfileEmailView(value: $email,
                                     consent: $marketingConsent,
                                     onContinue: { address in
                                         basics.email = address
                                         basics.sendCode { go(to: .profileVerifyEmail) }
                                     },
                                     onBack: { go(to: .profileName) },
                                     serverError: basics.emailInUse
                                        ? EmailCopy.alreadyInUseProposed
                                        : (basics.transportFailed ? EmailCopy.sendFailedProposed : nil))

                case .profileVerifyEmail:
                    // Every number on this screen is the server's: the cooldown counts down to
                    // `resendAvailableAt`, expiry compares against `expiresAt`, and the attempt
                    // cap is raised to the cap by a 401 that says so.
                    ProfileVerifyEmailView(
                        email: basics.email.isEmpty ? email : basics.email,
                        digits: Binding(get: { basics.codeDigits },
                                        set: { basics.codeDigits = $0 }),
                        state: basics.failure,
                        cooldownSeconds: basics.cooldownSeconds,
                        busy: basics.busy,
                        onVerify: { basics.verify { go(to: .profileDob) } },
                        onResend: { basics.sendCode() },
                        // Both exits are the same journey: back to the email step, address kept.
                        onChangeEmail: { go(to: .profileEmail) },
                        onBack: { go(to: .profileEmail) })

                case .profileDob:
                    // Continue writes the date AND the visibility choice in one PATCH and only
                    // advances when the server has stored them. Back does not re-send or
                    // re-verify anything — the code screen is already satisfied.
                    ProfileDobView(
                        value: Binding(get: { basics.dob },
                                       set: { basics.dob = $0; dobStored = $0 }),
                        hideAge: Binding(get: { basics.hideAge },
                                         set: { basics.hideAge = $0; hideAgeStored = $0 }),
                        attempted: basics.dobAttempted,
                        busy: basics.busy,
                        serverRejectedAge: basics.serverRejectedAge,
                        onContinue: { _, iso in
                            basics.saveDateOfBirth(iso: iso) { go(to: .profileEmbrace) }
                        },
                        onRefused: { basics.dobAttempted = true },
                        onEdit: { basics.dob = ""; basics.dobAttempted = false; dobStored = "" },
                        onBack: { go(to: .profileVerifyEmail) })
                    // Restore what a rotation would otherwise have dropped.
                    .onAppear {
                        if basics.dob.isEmpty { basics.dob = dobStored }
                        basics.hideAge = hideAgeStored
                    }
                case .profileEmbrace:
                    // SHOWUP-155. The bridge out of "The basics". No header, no progress bar, no
                    // back — the absences are the design. Its one exit is forward.
                    ProfileEmbraceView(
                        firstName: firstName,
                        onContinue: { go(to: .profilePhotos) })

                case .profilePhotos:
                    // SHOWUP-156. Every state here is real: uploads run, report their own progress
                    // and can fail, and the count only moves when one is confirmed. The picker,
                    // the camera UI and the permission alert are the OS's, and none of them is
                    // drawn here.
                    ProfilePhotosView(
                        state: photos.grid,
                        library: photos.library,
                        camera: photos.camera,
                        sheetOpen: photos.sheetOpen,
                        onBack: { go(to: .profileEmbrace) },
                        onSlotTap: { photos.tapSlot($0) },
                        onRemove: { photos.remove($0) },
                        onRetry: { photos.retry($0) },
                        onRevealOptional: { photos.revealOptional() },
                        onReorder: { from, to in photos.reorder(from: from, to: to) },
                        onChooseLibrary: { pickerSource = .library; photos.dismissSheet() },
                        onChooseCamera: { pickerSource = .camera; photos.dismissSheet() },
                        onCameraSettings: { openAppSettings() },
                        onDismissSheet: { photos.dismissSheet() },
                        onOpenSettings: { openAppSettings() },
                        onContinue: { go(to: .profilePrompts) })
                        // RE-READ THE PERMISSION STATUS ON EVERY FOREGROUND. The most common bug
                        // on this screen is a user who granted access in Settings returning to the
                        // blocked card, and becoming active again is the only moment to notice.
                        .onAppear {
                            photos.refreshAccess()
                            // Reads what the account already holds. Without it the grid started
                            // empty on every launch, and an account at the server's six-photo
                            // limit answered the next upload with a 400 the slot could only
                            // render as `Upload failed`.
                            photos.load()
                        }
                        .onChange(of: scenePhase) { _, phase in
                            if phase == .active { photos.refreshAccess() }
                        }
                        .sheet(isPresented: Binding(
                            get: { pickerSource != nil },
                            set: { if !$0 { pickerSource = nil } }
                        )) {
                            if pickerSource == .camera {
                                SystemCameraPicker { picked in
                                    if let picked { photos.picked(picked, source: .camera) }
                                    pickerSource = nil
                                }
                                .ignoresSafeArea()
                            } else {
                                SystemPhotoPicker { picked in
                                    if let picked { photos.picked(picked, source: .library) }
                                    pickerSource = nil
                                }
                                .ignoresSafeArea()
                            }
                        }

                case .profilePrompts:
                    // SHOWUP-158. Two sheets, one screen, and every transition between them is a
                    // change to the one stored value.
                    promptsScreen

                case .profileNotifications:
                    notificationsScreen

                case .profileReachability:
                    // SHOWUP-163. The deactivation confirm is NOT its own FlowScreen -- §11 says
                    // it "has no entry point of its own and is not a separate screen", so it is a
                    // state of this position rather than a place the router can send anyone.
                    reachabilityScreen

                case .profileMedia:
                    // SHOWUP-161. One position in the flow, two surfaces: the media screen, and the
                    // full-bleed viewfinder that replaces it while a take is running. The
                    // viewfinder is NOT its own FlowScreen — it has no entry point of its own and
                    // no way back except Cancel, so it is a state of this step rather than a place
                    // the router can send anyone.
                    mediaScreen

                case .profileLocation:
                    // SHOWUP-165. A, B and C are states of this position, never separate screens.
                    locationScreen

                case .profileEmbraceDetails:
                    // SHOWUP-166. The second bridge: no header, no progress bar, no back.
                    embraceDetailsScreen

                // "Share some details", steps 1 to 7 (SHOWUP-167 to SHOWUP-173).
                case .profileHeight: heightScreen
                case .profileGender: choiceScreen(.gender, next: .profileOrientation, previous: .profileHeight)
                case .profileOrientation:
                    choiceScreen(.orientation, next: .profileDatingLanguage, previous: .profileGender)
                case .profileDatingLanguage: datingLanguageScreen
                case .profileEducation:
                    choiceScreen(.education, next: .profileReligion, previous: .profileDatingLanguage)
                case .profileReligion:
                    choiceScreen(.religion, next: .profilePolitics, previous: .profileEducation)
                case .profilePolitics:
                    // INTERESTS (step 8) IS NOT BUILT, so politics ends at home for now. When it is,
                    // this is the single line that changes.
                    choiceScreen(.politics, next: .home, previous: .profileReligion)

                case .home:
                    HomePlaceholderView(outcome: outcome, onStartOver: { go(to: .signUp) })
                }
            }
            // .id is what makes SwiftUI treat each card as a distinct view and therefore run the
            // insertion/removal pair. Without it the switch mutates one view in place and nothing
            // transitions.
            .id(screen.rawValue)
            .transition(transition)

            emailDevStrip
        }
        // Moving past sign-up ends the notice: it describes why the user is HERE.
        .onChange(of: screenRaw) { _, _ in
            if screen != .signUp { onSessionNoticeDismissed() }
        }
        // ── resuming a half-finished profile (flow rule 4a) ─────────────────
        //
        // "On launch, an account with an incomplete profile routes straight to its last incomplete
        // step, with everything already entered still present."
        //
        // RESUMING IS SILENT. No prompt, no toast, no "welcome back" — the user lands on the step,
        // and a resumed step behaves like a freshly reached one, which is why nothing here sets an
        // error or an attempted flag.
        //
        // Once per launch, and only while the screen is still the flow's entry point: a user who
        // has already walked somewhere must not be yanked back by a late answer.
        .task {
            guard !resumeChecked else { return }
            resumeChecked = true
            guard let progress = await progressRepo.fetch() else { return }
            guard screen == .signUp else { return }
            // Everything already entered, still present.
            firstName = progress.displayName ?? ""
            basics.email = progress.email ?? ""
            switch resumePoint(progress) {
            case .name: go(to: .profileName)
            case .email: go(to: .profileEmail)
            case .verifyEmail: go(to: .profileVerifyEmail)
            case .dob: go(to: .profileDob)
            // NEVER the bridge. SHOWUP-155: "relaunching lands on photos and not on this bridge" —
            // it is a beat on the forward walk, not a place to return to.
            case .photos: go(to: .profilePhotos)
            case .prompts: go(to: .profilePrompts)
            // PAST PROMPTS, FROM THE STORED POSITION (SHOWUP-165). Each routes exactly as the
            // forward walk does: notifications through `afterMedia`, location through its matrix.
            case .media: go(to: .profileMedia)
            case .notifications: go(to: await afterMedia())
            case .reachability: go(to: .profileReachability)
            case .location: await enterLocation()
            case .height: go(to: .profileHeight)
            case .gender: go(to: .profileGender)
            case .orientation: go(to: .profileOrientation)
            case .datingLanguage: go(to: .profileDatingLanguage)
            case .education: go(to: .profileEducation)
            case .religion: go(to: .profileReligion)
            case .politics: go(to: .profilePolitics)
            case .done: go(to: .home)
            }
            // A resume onto a detail step pre-fills the saved value: start the read now.
            details.preload()
        }
    }
}

/// Demo driver for SHOWUP-144, so the ten states can be walked without a backend.
///
/// **Scaffolding, not product.** The real screen is `ConnectAccountView`, which is pure: it takes a
/// state and renders it. This host fakes the round trip that a provider SDK and our link-identity
/// endpoint would drive, and it deliberately cycles through a different ending on each attempt —
/// success, cancel, network error, declined, conflict — so a reviewer can reach every branch by
/// tapping the same button five times instead of needing five broken accounts.
struct ConnectFlowHost: View {
    let onDone: (ConnectExit) -> Void
    /// Opens a legal document.
    ///
    /// This screen carries "By continuing you agree to our Terms and Privacy Policy", and both are
    /// real links -- SHOWUP-144 lists a click event for each. Without this they fell back to the
    /// view's empty defaults: tappable, doing nothing, reporting nothing.
    var onOpenLegal: (String) -> Void = { _ in }
    /// SHOWUP-144's twelve events are reported from here rather than from the view, because this
    /// owns the state transitions -- and several of the events ARE transitions rather than taps:
    /// link succeeded, link failed, linking timeout, conflict raised.
    ///
    /// This host is scaffolding for provider SDKs that do not exist yet. When they arrive the
    /// transitions move with them and these calls move too; the names and properties do not.
    var analytics: any AnalyticsTracking = NoOpAnalytics()

    @State private var state: ConnectState = .idle
    @State private var provider: AuthMethod = .apple
    @State private var kind: ErrorKind = .network
    @State private var attempt = 0
    /// "repeat conflicts in one session" is a named event in SHOWUP-144, so the count is kept: the
    /// second conflict is a different signal from the first.
    @State private var conflicts = 0

    private func track(_ pair: (String, [String: any Sendable])) {
        analytics.track(pair.0, properties: pair.1)
    }
    private var providerName: String { String(describing: provider) }

    var body: some View {
        ConnectAccountView(
            state: state,
            provider: provider,
            kind: kind,
            onSelect: { m in
                track(SignUpAnalytics.provider(
                    SignUpAnalytics.providerTapped, String(describing: m)))
                provider = m
                Task { await run() }
            },
            // SHOWUP-146 needs to tell these three apart, so the host reports which one
            // happened rather than collapsing them into a bare "done".
            onSkip: {
                analytics.track(SignUpAnalytics.skipTapped, properties: [:])
                onDone(.skipped)
            },
            onContinue: { onDone(.connected) },
            // The 8s cap firing is a real transition, not a demo shortcut.
            onLinkingTimeout: {
                // The 8-second cap in SHOWUP-144. Reported separately from link_failed even though
                // it lands on the same state: a timeout and a refusal are different problems.
                track(SignUpAnalytics.provider(SignUpAnalytics.linkingTimeout, providerName))
                kind = .network
                state = .error
            },
            onResolveConflict: { _ in
                track(SignUpAnalytics.provider(
                    SignUpAnalytics.conflictResolveTapped, providerName))
                onDone(.resolvedConflict)
            },
            onUseDifferentAccount: {
                analytics.track(
                    SignUpAnalytics.conflictDifferentAccountTapped, properties: [:])
                state = .idle
            },
            onTerms: {
                track(SignUpAnalytics.legalLinkTapped(
                    SignUpAnalytics.Legal.terms, screenId: SignUpAnalytics.ScreenId.connectSSO))
                onOpenLegal("Terms & Conditions")
            },
            onPrivacy: {
                track(SignUpAnalytics.legalLinkTapped(
                    SignUpAnalytics.Legal.privacy, screenId: SignUpAnalytics.ScreenId.connectSSO))
                onOpenLegal("Privacy Policy")
            }
        )
    }

    private func run() async {
        state = .tapped
        try? await Task.sleep(nanoseconds: 500_000_000)
        state = .handoff
        try? await Task.sleep(nanoseconds: 1_400_000_000)

        defer { attempt += 1 }
        switch attempt % 5 {
        case 0:
            state = .linking
            try? await Task.sleep(nanoseconds: 1_600_000_000)
            state = .success
            track(SignUpAnalytics.provider(SignUpAnalytics.linkSucceeded, providerName))
        case 1:
            // The user closed the provider sheet before it finished. Distinct from an error: no
            // failure happened, they changed their mind.
            state = .cancelled
            track(SignUpAnalytics.provider(SignUpAnalytics.sheetDismissed, providerName))
        case 2:
            kind = .network
            state = .error
            track(SignUpAnalytics.linkFailed(provider: providerName, kind: "network"))
        case 3:
            kind = .declined
            state = .error
            track(SignUpAnalytics.linkFailed(provider: providerName, kind: "declined"))
        default:
            state = .linking
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            state = .conflict
            conflicts += 1
            track(SignUpAnalytics.provider(SignUpAnalytics.conflictRaised, providerName))
            // Reported IN ADDITION to conflict_raised, not instead of it, so the plain count of
            // conflicts stays correct.
            if conflicts > 1 {
                track(SignUpAnalytics.conflictRepeated(
                    count: conflicts, provider: providerName))
            }
        }
    }
}
