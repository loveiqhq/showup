//  ShowUpWelcomeApp.swift
//  ShowUp · Tutorial — app entry point
//
//  This target exists only to render the tutorial screens on a simulator or device. It is not the
//  ShowUp app; when the real iOS app exists these views move there and this goes away.
//
//  The navigation here is a placeholder: real routing arrives with the rest of the flow. It exists
//  so "Show me how" actually goes somewhere in the simulator.

import SwiftUI

@main
struct ShowUpWelcomeApp: App {
    var body: some Scene {
        WindowGroup {
            TutorialFlow()
        }
    }
}

private struct TutorialFlow: View {
    @State private var screen = 1

    var body: some View {
        switch screen {
        case 1:  WelcomeView(onContinue: { screen = 2 })
        default: MeetInRealLifeView(onNext: { /* tutorial screen 3 — not built yet */ })
        }
    }
}
