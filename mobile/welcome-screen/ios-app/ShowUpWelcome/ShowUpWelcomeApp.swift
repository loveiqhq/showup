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
    var body: some Scene {
        WindowGroup { TutorialFlow() }
    }
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

    @State private var state: ConnectState = .idle
    @State private var provider: AuthMethod = .apple
    @State private var kind: ErrorKind = .network
    @State private var attempt = 0

    var body: some View {
        ConnectAccountView(
            state: state,
            provider: provider,
            kind: kind,
            onSelect: { m in
                provider = m
                Task { await run() }
            },
            // SHOWUP-146 needs to tell these three apart, so the host reports which one
            // happened rather than collapsing them into a bare "done".
            onSkip: { onDone(.skipped) },
            onContinue: { onDone(.connected) },
            // The 8s cap firing is a real transition, not a demo shortcut.
            onLinkingTimeout: { kind = .network; state = .error },
            onResolveConflict: { _ in onDone(.resolvedConflict) },
            onUseDifferentAccount: { state = .idle }
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
        case 1:
            state = .cancelled
        case 2:
            kind = .network
            state = .error
        case 3:
            kind = .declined
            state = .error
        default:
            state = .linking
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            state = .conflict
        }
    }
}
