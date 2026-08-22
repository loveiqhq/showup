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

private struct TutorialFlow: View {
    @State private var screen = 1

    var body: some View {
        switch screen {
        case 1: WelcomeView(onContinue: { screen = 2 })
        case 2: MeetInRealLifeView(onNext: { screen = 3 })
        case 3: MatchOnAvailabilityView(onNext: { screen = 4 }, onBack: { screen = 2 })
        case 4: MatchMeansMeetView(onNext: { screen = 5 }, onBack: { screen = 3 })
        case 5: ThirtyMinutesView(onNext: { screen = 6 }, onBack: { screen = 4 })
        default: ShowUpEveryTimeView(
            onFinish: { screen = 1 },   // real destination TBC — restarts the tour for now
            onBack: { screen = 5 })
        }
    }
}
