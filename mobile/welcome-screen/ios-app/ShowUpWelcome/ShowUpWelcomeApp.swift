//  ShowUpWelcomeApp.swift
//  ShowUp · Onboarding 00 — app entry point
//
//  This target exists only to render the welcome card on a simulator or device. It is not the
//  ShowUp app; when the real iOS app exists, WelcomeView.swift moves there and this goes away.

import SwiftUI

@main
struct ShowUpWelcomeApp: App {
    var body: some Scene {
        WindowGroup {
            WelcomeView(onContinue: {
                // onboarding card 01 — not built yet (out of scope for SHOWUP-117)
            })
        }
    }
}
