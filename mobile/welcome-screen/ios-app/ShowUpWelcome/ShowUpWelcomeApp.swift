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
        WindowGroup { TutorialFlow() }
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

private struct TutorialFlow: View {
    // SceneStorage, not State: a process death mid-tutorial should not silently drop the user back
    // to card 1. Android has survived this since it was written, because rememberSaveable is the
    // default idiom there; iOS had no restoration at all.
    //
    // Scene-scoped and NOT @AppStorage on purpose. This is where the user is right now, not a
    // preference -- @AppStorage would still be holding a half-finished tutorial position weeks
    // later, and would restore it into a scene that had been properly closed.
    @SceneStorage("flow.screen") private var screenRaw: String = FlowScreen.signUp.rawValue
    private var screen: FlowScreen { FlowScreen(rawValue: screenRaw) ?? .signUp }

    // Transition direction only. Deliberately NOT restored: there is no animation on a relaunch,
    // so the value it would restore describes a movement that is not happening.
    @State private var forward = true

    // Kept only so the placeholder home screen can name the rule that sent the user there, which
    // is what makes SHOWUP-146 demonstrable. Not product state, but it has to survive with the
    // screen or Home restores describing the wrong route.
    @SceneStorage("flow.outcome") private var outcomeRaw: String = SignUpOutcome.newAccount.storageKey
    private var outcome: SignUpOutcome { SignUpOutcome(storageKey: outcomeRaw) ?? .newAccount }

    // Demo state for "The basics". Scene-scoped like the rest of the flow, but in-memory only:
    // the real flow persists per completed step and resumes onto the last incomplete one, which
    // depends on a profile-progress store that does not exist yet. Walkable, not shipped.
    @SceneStorage("basics.firstName") private var firstName: String = ""
    @SceneStorage("basics.email") private var email: String = ""
    @SceneStorage("basics.marketingConsent") private var marketingConsent: Bool = false

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
    @SceneStorage("profile.prompts") private var promptsStored: String = ""

    @State private var prompts = PromptsModel(repo: PromptsRepository(api: APIAccess.client))

    /// The media step (SHOWUP-161).
    ///
    /// THE CAMERA IS NOT A SECOND `@State` HERE. It belongs to the capture factory, which the model
    /// holds, and the view reads it back through `media.cameraSession`. Holding it separately meant
    /// two `CameraSession` instances — SwiftUI's `@State` initialisers cannot reference each other,
    /// so the model built its own — and a viewfinder previewing one session while the recorder
    /// wrote from another is a live preview over a black recording.
    @State private var media = MediaModel(
        repo: MediaRepository(api: APIAccess.client),
        access: AVMediaAccess(),
        capture: AVMediaCaptureFactory(),
        player: sharedMediaPlayer
    )

    /// Read once per launch to decide where a half-finished profile picks up (flow rule 4a).
    private let progressRepo = ProfileProgressRepository(api: APIAccess.client)
    @State private var resumeChecked = false

    @SceneStorage("basics.dob") private var dobStored: String = ""
    @SceneStorage("basics.hideAge") private var hideAgeStored: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Becoming active again is the only moment a permission granted in Settings can be noticed.
    @Environment(\.scenePhase) private var scenePhase

    /// Every navigation goes through here so the transition direction is always set before the
    /// state change that triggers it.
    private func go(to next: FlowScreen) {
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
            onWriteTopic: { prompts.writeSuggestion($0, position: $1) },
            onPickTopic: { prompts.pickTopic($0, position: $1) },
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
                onSkip: { media.stopPlayback(); media.skipPressed(); go(to: .home) },
                onContinue: { media.stopPlayback(); media.continuePressed(); go(to: .home) }
            )
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
                }, auth: phoneAuth)
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

                case .profileMedia:
                    // SHOWUP-161. One position in the flow, two surfaces: the media screen, and the
                    // full-bleed viewfinder that replaces it while a take is running. The
                    // viewfinder is NOT its own FlowScreen — it has no entry point of its own and
                    // no way back except Cancel, so it is a state of this step rather than a place
                    // the router can send anyone.
                    mediaScreen

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
            case .done: go(to: .home)
            }
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
                    SignUpAnalytics.Legal.terms, screen: SignUpAnalytics.Screen.connectSSO))
                onOpenLegal("Terms & Conditions")
            },
            onPrivacy: {
                track(SignUpAnalytics.legalLinkTapped(
                    SignUpAnalytics.Legal.privacy, screen: SignUpAnalytics.Screen.connectSSO))
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
