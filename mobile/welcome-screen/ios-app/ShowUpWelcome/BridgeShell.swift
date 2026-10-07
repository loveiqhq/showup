//
//  BridgeShell.swift
//  ShowUp · the shell both bridges render through — screen 05 (SHOWUP-155) and Embrace 2
//  (SHOWUP-166)
//
//  The Swift twin of `profile/BridgeShell.kt`. Profile 13: "This screen reuses 05's shell — do not
//  fork it. If 05's shell does not take those as props yet, add the props; do not copy the screen."
//
//  THE SHELL'S, AND SO IDENTICAL ON BOTH: no header, no progress bar, no back; the backdrop and the
//  full-width sunset CTA; the headline block at 64 / 28 in Lora 700 / 34 / 1.1 / −0.015em — THE SIZE
//  IS NOT A PROP, so the two bridges cannot drift again; one flexible spacer; the trailing 18 arrow.
//
//  THE PAYLOAD'S: the headline runs, the body, the backdrop recipe, and the layer between the
//  backdrop and the content — Embrace 2's confetti.
//

import SwiftUI

/// Which backdrop a bridge draws. Two recipes of the one backdrop component.
enum BridgeBackdrop {
    /// Screen 05: Startup's orbs, 0.32 / 0.28.
    case corner
    /// Embrace 2: orange 480 top-right at 0.30, a centred violet glow at 0.30.
    case centred

    var placement: OrbPlacement { self == .corner ? .startup : .embraceDetails }
    var orangeAlpha: Double { self == .corner ? 0.32 : 0.30 }
    var violetAlpha: Double { self == .corner ? 0.28 : 0.30 }
}

struct EmbraceBridgeShell<Content: View>: View {
    let headline: [(String, Bool)]
    let cta: String
    let onContinue: () -> Void
    let backdrop: BridgeBackdrop
    /// `text-wrap: balance`. Embrace 2's criteria name it; 05 keeps its hard break.
    var balanceHeadline: Bool = false
    /// Drawn above the backdrop and below the content. Embrace 2's confetti; nothing on 05.
    var behindContent: AnyView? = nil
    @ViewBuilder let content: () -> Content

    var body: some View {
        WelcomeScaffold(
            placement: backdrop.placement,
            orangeAlpha: backdrop.orangeAlpha,
            violetAlpha: backdrop.violetAlpha,
            topPadding: 64,
            gutter: 28,
            behindContent: behindContent
        ) {
            WashHeadline(parts: headline, fontSize: 34, lineHeightMultiple: 1.1, trackingEm: -0.015,
                         balance: balanceHeadline)
                .accessibilityAddTraits(.isHeader)

            content()

            // THE ONLY FLEXIBLE ELEMENT. `minLength: 0` so it may collapse on the shortest frame
            // rather than force the CTA off the bottom.
            Spacer(minLength: 0)

            PrimaryButton(
                label: cta,
                variant: .sunset,
                action: onContinue,
                trailing: { BrandIconView(icon: .arrowRight, size: 18, stroke: 2, tint: .white) }
            )
            .padding(.bottom, 22)
        }
        // No back of any kind: there is nothing behind a bridge to return to.
        .navigationBarBackButtonHidden(true)
    }
}
