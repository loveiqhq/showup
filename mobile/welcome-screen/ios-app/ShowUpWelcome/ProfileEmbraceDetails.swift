//
//  ProfileEmbraceDetails.swift
//  ShowUp · Profile 13 — Embrace 2: add profile details (SHOWUP-166)
//
//  The Swift twin of `ProfileEmbraceDetailsScreen.kt`. The second bridge: it celebrates what the user
//  has just finished, with a one-shot confetti rain, then asks for more with one button. One state;
//  the only branch is the name. NOT A STEP — no header, no progress bar, no back, no skip.
//
//  NOTHING WAITS FOR THE CONFETTI: the CTA is live from the first frame, and the rain leaves with the
//  screen.
//

import SwiftUI

/// Copy — final strings, "confirmed 5 Oct 2026, do not reword".
enum EmbraceDetailsCopy {
    static func headline(_ name: String) -> String { "You are doing great, \(name)." }
    static let headlineAnonymous = "You are doing great."
    static let lead = "You'll see on others' profiles exactly what you choose to share on yours. "
        + "Let's add a few more details!"
    static let cta = "Add profile details"
}

struct ProfileEmbraceDetailsView: View {
    /// The name from screen 01, trimmed. Empty or whitespace-only gives the no-name headline.
    var firstName: String = ""
    var onContinue: () -> Void = {}
    /// Whether this showing is a PUSH. False on a back-pop from height — no replay — and in the
    /// previews and the fit harness, which photograph the screen at rest.
    var playConfetti: Bool = false
    /// Owned by the flow, in scene storage — see `ConfettiRain.played`.
    var confettiPlayed: Binding<Bool> = .constant(false)

    private var headline: String {
        let clean = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? EmbraceDetailsCopy.headlineAnonymous : EmbraceDetailsCopy.headline(clean)
    }

    var body: some View {
        EmbraceBridgeShell(
            // One run, not italic: "no em in the headline and no orange wash — unlike screen 05".
            headline: [(headline, false)],
            cta: EmbraceDetailsCopy.cta,
            onContinue: onContinue,
            backdrop: .centred,
            balanceHeadline: true,
            behindContent: AnyView(ConfettiRain(play: playConfetti, played: confettiPlayed))
        ) {
            // Body block `padding: 20px 28px 0`. Manrope 500 / 16 / 1.55, max-width 330.
            Text(EmbraceDetailsCopy.lead)
                .font(F.manrope(16, .medium))
                .foregroundColor(.liqNeutral)
                .lineSpacing(16 * 0.55)
                .frame(maxWidth: 330, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 20)
        }
    }
}

#Preview("Embrace 2 · named") { ProfileEmbraceDetailsView(firstName: "Leo") }
#Preview("Embrace 2 · no name") { ProfileEmbraceDetailsView(firstName: "  ") }
#Preview("Embrace 2 · long name") { ProfileEmbraceDetailsView(firstName: "Maximiliana-Rose") }
