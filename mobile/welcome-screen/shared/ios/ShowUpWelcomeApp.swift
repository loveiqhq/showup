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
                // Welcome & sign-up (SHOWUP-140/142/143) runs before the tutorial, which is the
                // real order: you sign up, then you are shown how the product works.
                case -4: StartupView(onCreateAccount: { go(to: -2) }, onLogin: { go(to: -3) })
                case -3: WelcomeBackView(
                    onContinue: { m in go(to: m == .phone ? -2 : 1) },
                    onUseDifferentAccount: { go(to: -4) })
                case -2: PhoneNumberView(onBack: { go(to: -4) }, onSubmit: { go(to: -1) })
                case -1: VerifyCodeView(onBack: { go(to: -2) }, onVerify: { go(to: 1) })
                case 1: WelcomeView(onContinue: { go(to: 2) })
                case 2: MeetInRealLifeView(onNext: { go(to: 3) })
                case 3: MatchOnAvailabilityView(onNext: { go(to: 4) }, onBack: { go(to: 2) })
                case 4: MatchMeansMeetView(onNext: { go(to: 5) }, onBack: { go(to: 3) })
                case 5: ThirtyMinutesView(onNext: { go(to: 6) }, onBack: { go(to: 4) })
                default: ShowUpEveryTimeView(
                    onFinish: { go(to: 1) },   // real destination TBC — restarts the tour for now
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
