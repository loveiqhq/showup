//
//  ProfileEmbrace.swift
//  ShowUp · Profile creation 05 — Embrace: build your profile (SHOWUP-155)
//
//  The Swift port of `profile/ProfileEmbraceScreen.kt`. Read that file's header for the argument;
//  the design decisions are identical and are not restated here. What follows is only what differs
//  because this is SwiftUI.
//
//  ONE STATE, ONE PROP — the first name, and whether there is one.
//
//  THIS IS NOT A STEP: no AppHeader, no StepProgress, nothing to validate. The absences are the
//  design, and `audit/verify-profile.py` asserts them on this file as well as on the Kotlin.
//
//  TWO NAMED EXCEPTIONS, BOTH THIS SCREEN'S: it carries the Startup screen's ambient backdrop
//  (flow rule 5's exception) and a full-width SUNSET CTA rather than the round orange NextButton
//  (rule 7's exception). Both are scoped to the two bridge screens only.
//
//  WHY THE BACK GESTURE IS NOT SUPPRESSED HERE, AND WHY THAT IS NOT A PARITY GAP
//
//  Android swallows the system back with a `BackHandler`. There is no equivalent to write on this
//  side: the screen is not inside a `NavigationStack`, so there is no interactive pop gesture to
//  disable — `SignUpFlowView` swaps its content on a state change, exactly as `ProfileNameView`
//  already does. The rule holds on both platforms; only one of them needs code to make it hold.
//

import SwiftUI

/// Copy — final strings. Every one of these is quoted by `audit/verify-profile.py`.
enum EmbraceCopy {
    static let greetingAnonymous = "Glad you're here."
    static func greeting(_ name: String) -> String { "Nice to see you, \(name)." }

    static let headlineLead = "Time to show the person "
    static let headlineEm = "behind"
    static let headlineTail = " your profile."

    static let lead =
        "You are wonderful as you are. Share what makes you unique so others get a real feel "
        + "for who they'll meet."
    static let bulletPhotos = "Upload meaningful photos."
    static let bulletPrompt = "Record a voice or video prompt."
    static let closing = "More of you means better matches — and more real-life connections."
    static let cta = "Upload my photos"
}

/// A list row with a small orange dot where a bullet glyph would be.
///
/// THE DOT IS AN ELEMENT, NOT A CHARACTER — no "•", no emoji, no `List` marker. 7 round in
/// `.liqOrange`, offset 9 from the top so it sits on the first line's optical centre; text sits on
/// a baseline and a circle does not, so without the offset the dot floats.
///
/// `.accessibilityHidden(true)` on the dot: it carries nothing VoiceOver needs, and the row reads
/// as its sentence rather than as "image, Upload meaningful photos".
private struct BulletRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(Color.liqOrange)
                .frame(width: 7, height: 7)
                .padding(.top, 9)
                .accessibilityHidden(true)
            Text(text)
                .font(F.manrope(15, .semibold))
                .foregroundColor(.liqFg)
                .lineSpacing(15 * 0.45)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct ProfileEmbraceView: View {
    /// The first name from step 1. Empty or whitespace-only falls back to the anonymous greeting.
    var firstName: String = ""
    var onContinue: () -> Void = {}

    private var greeting: String {
        let clean = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? EmbraceCopy.greetingAnonymous : EmbraceCopy.greeting(clean)
    }

    var body: some View {
        // THE SHARED BRIDGE SHELL since SHOWUP-166 — see BridgeShell.swift. Everything the two
        // bridges share lives there; this screen is its payload. The hard break keeps the greeting
        // on its own line whatever the name's length — the reference puts a literal `<br/>` here.
        EmbraceBridgeShell(
            headline: [
                (greeting + "\n" + EmbraceCopy.headlineLead, false),
                (EmbraceCopy.headlineEm, true),
                (EmbraceCopy.headlineTail, false),
            ],
            cta: EmbraceCopy.cta,
            onContinue: onContinue,
            backdrop: .corner
        ) {
            // The body block's own 28 top padding. The headline block has no bottom padding, so
            // this single value is the whole gap between them.
            Text(EmbraceCopy.lead)
                .font(F.manrope(15.5, .medium))
                .foregroundColor(.liqNeutral)
                .lineSpacing(15.5 * 0.55)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 28)

            VStack(alignment: .leading, spacing: 8) {
                BulletRow(text: EmbraceCopy.bulletPhotos)
                BulletRow(text: EmbraceCopy.bulletPrompt)
            }
            .padding(.top, 14)

            Text(EmbraceCopy.closing)
                .font(F.manrope(15.5, .medium))
                .foregroundColor(.liqNeutral)
                .lineSpacing(15.5 * 0.55)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 14)
        }
    }
}

// MARK: - Previews
//
// The device matrix from the ticket. 375 x 667 is where the single spacer nearly collapses;
// 430 x 932 is where it takes the surplus. There is no keyboard on this screen at all, so unlike
// every other profile preview these show the real thing rather than a column missing its keys.

#Preview("Embrace · named") {
    ProfileEmbraceView(firstName: "Leo")
}

#Preview("Embrace · no name") {
    ProfileEmbraceView()
}

#Preview("Embrace · long name") {
    ProfileEmbraceView(firstName: "Maximiliana-Rose")
}
