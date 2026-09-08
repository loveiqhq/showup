//  PrimaryButton.swift
//  ShowUp · the one primary button
//
//  There were three of these. This one (as `PillButton`) lived in WelcomeShell.swift and carried
//  the whole design; `SunsetButton` in WelcomeView.swift was a copy of its sunset variant written
//  six days earlier, before this one existed, and it had drifted; `NextButton` in TutorialShell is
//  not a duplicate at all and stays there -- it is a label beside a circular arrow badge, a
//  different silhouette with its own spec and twelve checks in verify-spec.py.
//
//  A primitive living in a screen file is how the second copy happens: whoever wrote the tutorial's
//  CTA had no reason to look inside the sign-up flow's shell.

import SwiftUI

// MARK: - ④ Buttons — always pills (9999), never rounded rectangles

/// Button treatments. Six, and every one is required.
///
/// `.apple`, `.google` and `.facebook` are not style choices — each provider dictates the appearance
/// of its own sign-in button and enforces it. Google forbids recolouring or resizing the G and
/// requires a white background, making compliance a condition of app verification; Meta requires its
/// mark in white or #1877F2 and prohibits recolouring to a host brand's palette. The uniform ghost
/// treatment the design specified breaks both.
///
/// The pill silhouette, the 56 height and the Manrope label stay — those are ours.
/// `.plain` is not a provider treatment and never carries one: it is the conflict modal's
/// secondary, which has no border precisely so the pair does not read as two equal choices.
enum PrimaryButtonVariant { case sunset, ghost, plain, apple, google, facebook }

/// `Button` from components/shared.jsx at `size="lg"`: height 56, padding 0/28, radius 9999,
/// Manrope 700 16, gap 8.
///
/// Press is `scale(0.98)` over 180ms on `cubic-bezier(.22,1,.36,1)`. CLAUDE.md states it as a
/// non-negotiable, and there are no hover states because the product is mobile-first.
struct PrimaryButton<Leading: View, Trailing: View>: View {
    // Argument order mirrors the stored properties, because Swift's memberwise init is positional
    // and a call site that reorders them will not compile. See audit/check-swift-arg-order.py.
    let label: String
    var variant: PrimaryButtonVariant = .sunset
    var enabled: Bool = true
    /// 56 everywhere except the conflict modal, whose resolve CTA the reference draws at 54 and
    /// whose secondary it draws at 50. All three clear every provider's published minimum
    /// (Apple's is 44pt), so the smaller two are a layout choice rather than a compliance question.
    var height: CGFloat = 56
    /// 16 in the sign-up flow, 17 in the tutorial — two design worlds, one call site each way.
    ///
    /// A parameter rather than a variant because the tone is unchanged: the tutorial's CTA is the
    /// same sunset button set one step larger. `.plain` overrides it, being 15/600 by definition
    /// rather than by choice. Worth collapsing to one value if the design side agrees.
    var labelSize: CGFloat = 16
    var action: () -> Void
    @ViewBuilder var leading: () -> Leading
    /// Mirrors `leading`. Used once: the tutorial CTA's trailing arrow.
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        Button(action: { if enabled { action() } }) {
            HStack(spacing: Spacing.md) {
                leading()
                Text(label)
                    // The plain secondary is 600/15 — one step down from the 700 every real
                    // button carries, which is what stops the modal reading as two equal choices.
                    .font(variant == .plain ? F.manrope(15, .semibold)
                                            : F.manrope(labelSize, .bold))
                    .lineLimit(1)
                    .foregroundColor(labelColor)
                trailing()
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .padding(.horizontal, 28)
            .background(background)
            .clipShape(Capsule())
            .overlay(
                variant == .ghost ? Capsule().strokeBorder(Color.liqBorder, lineWidth: 1)
                : variant == .google ? Capsule().strokeBorder(Color(hex: 0x747775), lineWidth: 1)
                : nil
            )
            .shadow(color: variant == .sunset ? Color.liqPurple.opacity(0.28) : .clear,
                    radius: 10, y: 8)
            .opacity(enabled ? 1 : 0.45)
        }
        .buttonStyle(PressScale())
        .disabled(!enabled)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(.isButton)
    }

    private var labelColor: Color {
        switch variant {
        case .sunset, .apple, .facebook: return .white
        case .google: return Color(hex: 0x1F1F1F)     // Google's specified label colour
        case .ghost: return .liqFg
        case .plain: return .liqMuted
        }
    }

    @ViewBuilder private var background: some View {
        switch variant {
        case .sunset:
            // 135°, midpoint at 38% — not an even three-stop ramp
            LinearGradient(
                stops: [
                    .init(color: .liqOrange, location: 0.00),
                    .init(color: Color(hex: 0xD05976), location: 0.38),
                    .init(color: .liqPurple, location: 1.00),
                ],
                startPoint: .topLeading, endPoint: .bottomTrailing)
        case .ghost, .plain:
            Color.clear
        // Black is one of the three appearances Apple's guidelines allow.
        case .apple:
            Color.black
        // A white background is required, not preferred — the G may not sit on anything else.
        case .google:
            Color.white
        // Facebook Blue. Recolouring to our palette is explicitly prohibited.
        case .facebook:
            Color(hex: 0x1877F2)
        }
    }
}

// The three convenience inits, one per slot combination actually used.
//
// Each fills the unused slots with EmptyView. Call sites that DO pass a slot name it explicitly
// (`leading:` / `trailing:`) rather than using trailing-closure syntax: with two closure
// properties, an unlabelled trailing closure is ambiguous between the last two inits below, and
// the error Swift gives for that names neither.

extension PrimaryButton where Leading == EmptyView, Trailing == EmptyView {
    init(_ label: String, variant: PrimaryButtonVariant = .sunset, enabled: Bool = true,
         height: CGFloat = 56, labelSize: CGFloat = 16, action: @escaping () -> Void) {
        self.init(label: label, variant: variant, enabled: enabled, height: height,
                  labelSize: labelSize, action: action,
                  leading: { EmptyView() }, trailing: { EmptyView() })
    }
}

extension PrimaryButton where Trailing == EmptyView {
    init(label: String, variant: PrimaryButtonVariant = .sunset, enabled: Bool = true,
         height: CGFloat = 56, labelSize: CGFloat = 16, action: @escaping () -> Void,
         @ViewBuilder leading: @escaping () -> Leading) {
        self.init(label: label, variant: variant, enabled: enabled, height: height,
                  labelSize: labelSize, action: action,
                  leading: leading, trailing: { EmptyView() })
    }
}

extension PrimaryButton where Leading == EmptyView {
    init(label: String, variant: PrimaryButtonVariant = .sunset, enabled: Bool = true,
         height: CGFloat = 56, labelSize: CGFloat = 16, action: @escaping () -> Void,
         @ViewBuilder trailing: @escaping () -> Trailing) {
        self.init(label: label, variant: variant, enabled: enabled, height: height,
                  labelSize: labelSize, action: action,
                  leading: { EmptyView() }, trailing: trailing)
    }
}

/// `transform 180ms cubic-bezier(.22,1,.36,1)`, scale 0.98. Honours reduce-motion.
///
/// Lives here rather than in WelcomeShell.swift because it is the press treatment this primitive
/// defines, and four other files reach for it: the design system cannot be a screen's export.
struct PressScale: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.fast),
                       value: configuration.isPressed)
    }
}
