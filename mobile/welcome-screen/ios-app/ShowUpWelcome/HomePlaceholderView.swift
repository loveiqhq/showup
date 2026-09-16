//  HomePlaceholderView.swift
//  ShowUp · where both endings of the sign-up flow arrive (SHOWUP-146)
//
//  **Scaffolding, not product.** The real app does not exist in this preview target, so without
//  something here the two endings SHOWUP-146 distinguishes would be indistinguishable: a returning
//  member who correctly skips the tutorial would land on a blank screen, which looks like a bug
//  rather than like the rule working.
//
//  So this view states which rule sent the user here. That makes the ticket demonstrable -- walk
//  the flow twice, screenshot both, and the acceptance evidence is the two screenshots.
//
//  Deleted when the real home screen lands.

import SwiftUI

struct HomePlaceholderView: View {
    let outcome: SignUpOutcome
    var onStartOver: () -> Void = {}

    /// The whole point of the view: name the rule that applied, in the ticket's own terms.
    private var rule: String {
        switch outcome {
        case .newAccount:
            return "New account. The tutorial was shown first, then the app."
        case .returningMember:
            return "Returning member. The tutorial was skipped, as SHOWUP-146 requires."
        }
    }

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()

            VStack(spacing: 14) {
                Text("The app starts here")
                    .font(F.lora(30))
                    .foregroundColor(.liqFg)
                    .multilineTextAlignment(.center)

                Text(rule)
                    .font(F.manrope(15, .medium))
                    .foregroundColor(.liqNeutral)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .frame(maxWidth: 280)

                Text("Placeholder screen. It goes away with the real home screen.")
                    .font(F.manrope(12, .medium))
                    .foregroundColor(.liqSubtle)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 280)
                    .padding(.top, Spacing.sm)

                Button(action: onStartOver) {
                    Text("Start over")
                        .font(F.manrope(14, .medium))
                        .foregroundColor(.liqFg)
                        .padding(.horizontal, 22)
                        .padding(.vertical, Spacing.xl)
                        .overlay(Capsule().stroke(Color.liqBorder, lineWidth: 1))
                }
                .padding(.top, Spacing.lg)
            }
            .padding(.horizontal, 32)
        }
    }
}

#Preview("home - new account") {
    HomePlaceholderView(outcome: .newAccount)
}

#Preview("home - returning member") {
    HomePlaceholderView(outcome: .returningMember)
}
