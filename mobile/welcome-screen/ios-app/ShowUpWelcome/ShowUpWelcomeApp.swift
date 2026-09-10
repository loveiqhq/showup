//  ShowUpWelcomeApp.swift
//  ShowUp · Tutorial — app entry point
//
//  This target exists only to render the tutorial screens on a simulator or device. It is not the
//  ShowUp app; when the real iOS app exists these views move there and this goes away.
//
//  The navigation is a placeholder: real routing arrives with the rest of the app. It exists so the
//  flow can be walked end to end in the simulator.

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

    // ── "The basics" step 3 and the code screen ─────────────────────────────
    //
    // Scene-scoped like everything else in this flow: a rotation or a background keeps them, a
    // properly closed scene does not resurrect a half-finished signup.
    @SceneStorage("basics.codeDigits") private var codeDigits: String = ""
    @SceneStorage("basics.codeAttempts") private var codeAttempts: Int = 0
    @SceneStorage("basics.codeRefused") private var codeRefused: Bool = false
    @SceneStorage("basics.resendCooldown") private var resendCooldown: Int = DevAuth.resendCooldown
    /// Epoch seconds at which the current code dies. The whole mechanism that lets the client
    /// tell "expired" from "wrong": /auth/email/start returns expiresAt, and the 401 for a bad
    /// code and an expired one are identical, so the response cannot.
    @SceneStorage("basics.codeExpiresAt") private var codeExpiresAt: Double = 0
    @SceneStorage("basics.dob") private var dob: String = ""
    @SceneStorage("basics.hideAge") private var hideAge: Bool = false
    @SceneStorage("basics.dobAttempted") private var dobAttempted: Bool = false

    @State private var nowSeconds: Double = Date().timeIntervalSince1970

    private var codeExpired: Bool { codeExpiresAt > 0 && nowSeconds >= codeExpiresAt }

    private var codeState: VerifyState {
        verifyState(attempts: codeAttempts,
                    maxAttempts: DevAuth.maxVerifyAttempts,
                    expired: codeExpired,
                    lastSubmitRefused: codeRefused)
    }

    /// Sending a code is one act with one set of consequences, so it is written once and called
    /// from both the arrival and the resend rather than copied into each.
    private func sendCode() {
        codeDigits = ""
        codeAttempts = 0
        codeRefused = false
        resendCooldown = DevAuth.resendCooldown
        nowSeconds = Date().timeIntervalSince1970
        codeExpiresAt = nowSeconds + Double(DevAuth.codeTTLSeconds)
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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

    var body: some View {
        ZStack {
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
                })
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
                    // Continue reaches Verify email, and SENDS the code on the way — the ticket
                    // is explicit that the send is triggered here rather than on arrival, which
                    // is also what keeps a relaunch onto the code screen from issuing a new one.
                    ProfileEmailView(value: $email,
                                     consent: $marketingConsent,
                                     onContinue: { _ in
                                         sendCode()
                                         go(to: .profileVerifyEmail)
                                     },
                                     onBack: { go(to: .profileName) })

                case .profileVerifyEmail:
                    ProfileVerifyEmailView(
                        email: email,
                        digits: Binding(get: { codeDigits },
                                        set: { codeDigits = $0; codeRefused = false }),
                        state: codeState,
                        cooldownSeconds: resendCooldown,
                        onVerify: {
                            codeAttempts += 1
                            if codeDigits == DevAuth.testCode {
                                codeRefused = false
                                go(to: .profileDob)
                            } else {
                                codeRefused = true
                            }
                        },
                        onResend: { sendCode() },
                        // Both exits are the same journey: back to the email step, address kept.
                        onChangeEmail: { go(to: .profileEmail) },
                        onBack: { go(to: .profileEmail) })
                    // One ticker drives the countdown AND the expiry check, so they can never
                    // disagree about what time it is.
                    .task {
                        while !Task.isCancelled {
                            try? await Task.sleep(for: .seconds(1))
                            nowSeconds = Date().timeIntervalSince1970
                            if resendCooldown > 0 { resendCooldown -= 1 }
                        }
                    }

                case .profileDob:
                    // Back must NOT re-send or re-verify anything — the code screen is already
                    // satisfied, so this only moves the position.
                    ProfileDobView(
                        value: Binding(get: { dob }, set: {
                            dob = $0
                            // The incomplete error clears the moment the eighth digit lands.
                            if dobDigits($0).count == 8 { dobAttempted = false }
                        }),
                        hideAge: $hideAge,
                        attempted: dobAttempted,
                        onContinue: { _, _ in go(to: .home) },
                        onRefused: { dobAttempted = true },
                        onEdit: {
                            // A clear, not a cursor placement: a wrong date is nearly always
                            // wrong in the year.
                            dob = ""
                            dobAttempted = false
                        },
                        onBack: { go(to: .profileVerifyEmail) })
                case .home:
                    HomePlaceholderView(outcome: outcome, onStartOver: { go(to: .signUp) })
                }
            }
            // .id is what makes SwiftUI treat each card as a distinct view and therefore run the
            // insertion/removal pair. Without it the switch mutates one view in place and nothing
            // transitions.
            .id(screen.rawValue)
            .transition(transition)
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
