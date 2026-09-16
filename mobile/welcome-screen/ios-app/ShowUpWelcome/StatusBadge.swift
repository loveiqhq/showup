//  StatusBadge.swift
//  ShowUp · the small pill above a headline
//
//  WHAT THIS REPLACED
//
//  Three implementations per platform, six in all, drawing the same pill:
//
//    * `Eyebrow` in ConnectAccountView        — orange, and the only one that did not use a token
//    * `EyebrowPill` in TutorialShell         — lavender
//    * `VerificationEyebrow` in PhoneVerificationView — orange, label hardcoded
//
//  They agreed on the 5pt dot, the spacing, the padding and the 11pt bold label, and had drifted in
//  the fill: Connect painted `Color.liqOrange.opacity(0.12)` while `liqEyebrowOrangeBg` is defined
//  as exactly `Color(hex: 0xFE6839).opacity(0.12)`. The same pixels reached by two routes, one of
//  them a token and one of them a value re-derived at a call site.
//
//  IT DOES NOT POSITION ITSELF
//
//  Deliberately no alignment of its own. Connect's badge is CENTRED — it sits in a plain
//  `VStack`, whose default alignment is `.center` — while the tutorial's is pushed leading by an
//  `HStack` with a trailing `Spacer`, and the phone screens' by their frame. A pill that insisted
//  on its own alignment is what stopped these three from being one to begin with.

import SwiftUI

/// Which tone a `StatusBadge` wears.
///
/// Two, because the design uses two: the sign-up flow's badges are orange and the tutorial's are
/// lavender. `CLAUDE.md` names this case — "a component's tone is per screen, and both tones
/// stay" — so it is a variant rather than a token one screen could redefine and break the other.
///
/// It controls the fill and the label colour, and deliberately NOT the dot, which is `liqOrange` in
/// all three implementations this replaced, the lavender one included.
enum BadgeTone {
    /// Sign-up flow: the phone screens and Connect.
    case orange
    /// The tutorial cards.
    case lavender

    var fill: Color {
        switch self {
        case .orange: return .liqEyebrowOrangeBg
        case .lavender: return .liqEyebrowBg
        }
    }

    var label: Color {
        switch self {
        case .orange: return .liqOrange
        case .lavender: return .liqPurple
        }
    }
}

/// A 5pt dot and a short uppercase label in a tinted capsule.
struct StatusBadge: View {
    /// Shown uppercased. All three implementations uppercased, two by call and one by literal.
    let label: String
    var tone: BadgeTone = .orange

    var body: some View {
        HStack(spacing: Spacing.sm) {
            // Orange in every tone, lavender included. All three drew it this way, so it is
            // preserved rather than tidied into the variant.
            Circle().fill(Color.liqOrange).frame(width: 5, height: 5)
            Text(label.uppercased())
                .font(F.manrope(11, .bold))
                .tracking(0.08 * 11)
                .foregroundColor(tone.label)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 5)
        // `background(Capsule().fill(...))`, which is what two of the three used. The third wrote
        // `.background(colour).clipShape(Capsule())`; for a solid fill with nothing overflowing
        // the two are the same drawing, so they collapse to one.
        .background(Capsule().fill(tone.fill))
    }
}
