//
//  StepProgress.swift
//  ShowUp · the segmented progress bar, for every flow that has steps
//
//  WHY IT MOVED HERE, 15 SEPTEMBER 2026
//
//  It lived in `TutorialShell.swift` because the tutorial was the only thing with steps. Then
//  "The basics" used it from there, and with SHOWUP-156 and SHOWUP-158 "The real you" makes a
//  third group — at which point a shared primitive is sitting in a screen's file that two other
//  flows depend on.
//
//  That is the exact shape of the defect this project has already paid for once: `PillButton` sat
//  in the sign-up flow's shell, whoever wrote the tutorial's CTA had no reason to open it, and
//  they wrote a second button that then drifted four ways. The rule in CLAUDE.md — "a shared
//  primitive never lives in a screen file" — is that lesson, and this is it being applied rather
//  than the third group being asked to reach into the first.
//
//  Nothing about the component changed in the move. It already took `steps` and `current`, which
//  is what both profile groups need: one shell, the count and the index as props, so a group
//  changing from four segments to three is one call site rather than three screens.
//
//  Mirrors `designsystem/StepProgress.kt`.
//

import SwiftUI

/// N segments, height 5, gap 6, full content width.
struct StepProgress: View {
    let steps: Int
    let current: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<steps, id: \.self) { i in
                Capsule()
                    .fill(i < current ? Color.liqPurple : Color.liqTrack)
                    .frame(height: 5)
            }
        }
        // Advancing a step should read as progress being made, not as the bar being redrawn.
        .animation(reduceMotion ? nil : .easeInOut(duration: Motion.screen), value: current)
        // Anonymous capsules carry no text: without this VoiceOver announces nothing here.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Step \(current) of \(steps)"))
    }
}
