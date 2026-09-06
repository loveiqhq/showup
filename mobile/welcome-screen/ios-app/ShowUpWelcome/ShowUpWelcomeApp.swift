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
    // Negative ids are the pre-account flow, positive ones the tutorial. The demo opens
    // where a real first run opens: Startup.
    @State private var screen = -4
    @State private var forward = true
    // Kept only so the placeholder home screen can name the rule that sent the user there, which
    // is what makes SHOWUP-146 demonstrable. Not product state.
    @State private var outcome: SignUpOutcome = .newAccount
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Every navigation goes through here so the transition direction is always set before the
    /// state change that triggers it.
    private func go(to next: Int) {
        forward = next > screen
        if reduceMotion {
            screen = next
        } else {
            withAnimation(.easeInOut(duration: 0.32)) { screen = next }
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
                switch screen {
                // Welcome & sign-up (SHOWUP-140/142/143/144) runs before the tutorial,
                // which is the real order: you sign up, then you are shown how it works.
                // SignUpFlowView owns every step and every piece of state inside it.
                // SHOWUP-146, the whole ticket in one line: the tutorial for a new account,
                // straight into the app for a returning member.
                case -4: SignUpFlowView(onFinished: { o in
                    outcome = o
                    go(to: showsTutorial(o) ? 1 : 7)
                })
                case 1: WelcomeView(onContinue: { go(to: 2) })
                case 2: MeetInRealLifeView(onNext: { go(to: 3) })
                case 3: MatchOnAvailabilityView(onNext: { go(to: 4) }, onBack: { go(to: 2) })
                case 4: MatchMeansMeetView(onNext: { go(to: 5) }, onBack: { go(to: 3) })
                case 5: ThirtyMinutesView(onNext: { go(to: 6) }, onBack: { go(to: 4) })
                case 7: HomePlaceholderView(outcome: outcome, onStartOver: { go(to: -4) })
                default: ShowUpEveryTimeView(
                    // SHOWUP-146: the far end of the tutorial is the app, not the tour again.
                    onFinish: { go(to: 7) },
                    onBack: { go(to: 5) })
                }
            }
            // .id is what makes SwiftUI treat each card as a distinct view and therefore run the
            // insertion/removal pair. Without it the switch mutates one view in place and nothing
            // transitions.
            .id(screen)
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
            onTerms: {
                track(SignUpAnalytics.legalLinkTapped(
                    SignUpAnalytics.Legal.terms, screen: SignUpAnalytics.Screen.connectSSO))
                onOpenLegal("Terms & Conditions")
            },
            onPrivacy: {
                track(SignUpAnalytics.legalLinkTapped(
                    SignUpAnalytics.Legal.privacy, screen: SignUpAnalytics.Screen.connectSSO))
                onOpenLegal("Privacy Policy")
            },
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
