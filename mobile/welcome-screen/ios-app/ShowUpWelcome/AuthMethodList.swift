//  AuthMethodList.swift
//  ShowUp · the shared sign-in method list (SHOWUP-144 and SHOWUP-145)
//
//  Built once and used by both screens, which is what both tickets require:
//    "The method list uses the same component and the same ordered data source as welcome 04."
//    "This screen adds phone. Welcome 04 omits it."
//
//  Connect (144) offers Apple, Google and Facebook — the user has just verified their phone, so
//  re-offering it there would be a no-op. Welcome back (145) adds phone, because it is a genuine
//  way back in.
//
//  Provider styling follows each provider's own published spec, not our gradient. Both tickets
//  carry the same overruling note: "provider spec wins, equal prominence, never restyle a mark,
//  gradient reserved for our own CTAs … if our layout conflicts with a guideline the guideline
//  ships", and it explicitly outranks the ticket body, the spec sheet and the zip.

import SwiftUI

/// Canonical order. Connect drops phone; Welcome back keeps it. Never reordered beyond the lift.
let CONNECT_METHODS: [AuthMethod] = [.apple, .google, .facebook]
let LOGIN_METHODS: [AuthMethod] = [.phone, .apple, .google, .facebook]

/// One row per method: the button label, the mark, the hint Welcome back shows above the CTA, and
/// the short name interpolated into the error and linking copy.
struct MethodSpec {
    let label: String
    let short: String
    let icon: BrandIcon
    let hint: String
}

func methodSpec(_ m: AuthMethod) -> MethodSpec {
    switch m {
    case .phone:
        return .init(label: "Continue with phone number", short: "phone", icon: .phone,
                     hint: "Last login was via phone")
    case .apple:
        return .init(label: "Continue with Apple", short: "Apple", icon: .apple,
                     hint: "Last login was via Apple")
    case .google:
        return .init(label: "Continue with Google", short: "Google", icon: .google,
                     hint: "Last login was via Google")
    case .facebook:
        return .init(label: "Continue with Facebook", short: "Facebook", icon: .facebook,
                     hint: "Last login was via Facebook")
    // Never rendered: unknown is a lastUsed value, not a method anyone can pick.
    case .unknown:
        return .init(label: "Continue with phone number", short: "phone", icon: .phone, hint: "")
    }
}

/// Phone is ours to style; the other three are each governed by their provider.
///
/// This is where the brand decision becomes visible. SHOWUP-145 asks for "one sunset, three ghost",
/// and the note that overrules it reserves the gradient for our own CTAs. Phone is our own method,
/// so it keeps the sunset pill **when it is the promoted one** and drops to ghost otherwise. A
/// provider never takes the gradient in any position — which means "promoted to primary" is carried
/// by position and by the hint row, never by a fill swap.
func providerVariant(_ m: AuthMethod, isPrimary: Bool = false) -> PillVariant {
    switch m {
    case .apple: return .apple
    case .google: return .google
    case .facebook: return .facebook
    case .phone, .unknown: return isPrimary ? .sunset : .ghost
    }
}

/// The Google G ignores this — it is drawn in its own four colours.
func providerTint(_ m: AuthMethod, isPrimary: Bool = false) -> Color {
    switch m {
    case .apple, .facebook: return .white
    case .google: return .liqFg
    // Phone rides our own pill, so its mark follows the pill rather than a provider rule.
    case .phone, .unknown: return isPrimary ? .white : .liqFg
    }
}

/// A provider whose credential setup is not finished is **hidden, not disabled** — SHOWUP-145 says
/// so outright, and the remaining methods still fill the stack without a gap.
///
/// That is the right call rather than a greyed-out button: a disabled provider invites the tap,
/// then refuses it. Facebook in particular may not ship in v1 at all — 144 lists that as an open
/// decision — so the layout has to work with two, three or four buttons, not only the four the
/// mock happens to draw. With everything configured the default is still exactly four.
func availableMethods(_ all: [AuthMethod], configured: Set<AuthMethod>) -> [AuthMethod] {
    all.filter { configured.contains($0) }
}

/// Provider button titles come from each provider's own permitted list rather than from the
/// ticket's error / in-flight strings, which none of the three allow. See the note in `MethodRow`.
let PROVIDER_COMPLIANT_LABELS = true

/// The list. One component, variant-driven, two hosts.
struct AuthMethodList<Notice: View>: View {
    let methods: [AuthMethod]
    /// The provider currently in flight. Its button spins and disables; the others dim to 0.45.
    var loading: AuthMethod? = nil
    /// The provider that just failed. It keeps first position — the list must not shuffle between
    /// in-flight, cancel and error.
    var errorFor: AuthMethod? = nil
    var errorMessage: String? = nil
    /// Which method sits first. Set to the tapped one so it holds its place.
    var suggested: AuthMethod? = nil
    let onSelect: (AuthMethod) -> Void
    /// nil on Welcome back, which has no way past sign-in.
    var onSkip: (() -> Void)? = nil
    @ViewBuilder var notice: () -> Notice

    private var primary: AuthMethod {
        if let s = suggested, methods.contains(s) { return s }
        if let l = loading, methods.contains(l) { return l }
        return methods.first ?? .phone
    }
    private var rest: [AuthMethod] { methods.filter { $0 != primary } }
    private var anyLoading: Bool { loading != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // The banner is INSERTED ABOVE the list, never overlaid, so the buttons do not move
            // between idle, in-flight, cancelled and error.
            notice()
            if errorFor != nil, let msg = errorMessage {
                ErrorBanner(message: msg)
            }

            VStack(spacing: Spacing.lg) {
                MethodRow(method: primary, isPrimary: true, loading: loading,
                          anyLoading: anyLoading, onSelect: onSelect)
                ForEach(rest, id: \.self) { m in
                    MethodRow(method: m, isPrimary: false, loading: loading,
                              anyLoading: anyLoading, onSelect: onSelect)
                }
                if let onSkip { SkipRow(anyLoading: anyLoading, onSkip: onSkip) }
            }
            .padding(.bottom, Spacing.xl)
        }
    }
}

extension AuthMethodList where Notice == EmptyView {
    init(methods: [AuthMethod], loading: AuthMethod? = nil, errorFor: AuthMethod? = nil,
         errorMessage: String? = nil, suggested: AuthMethod? = nil,
         onSelect: @escaping (AuthMethod) -> Void, onSkip: (() -> Void)? = nil) {
        self.init(methods: methods, loading: loading, errorFor: errorFor,
                  errorMessage: errorMessage, suggested: suggested, onSelect: onSelect,
                  onSkip: onSkip, notice: { EmptyView() })
    }
}

private struct MethodRow: View {
    let method: AuthMethod
    let isPrimary: Bool
    let loading: AuthMethod?
    let anyLoading: Bool
    let onSelect: (AuthMethod) -> Void

    private var isLoading: Bool { loading == method }

    var body: some View {
        let spec = methodSpec(method)
        let variant = providerVariant(method, isPrimary: isPrimary)

        // The label. Two of the ticket's strings cannot be used on a provider button, and this is
        // the one place the conflict shows up, so the reasoning lives here.
        //
        // SHOWUP-144 asks for "Try Apple again" in the error state and "Connecting to Apple…" in
        // flight. Apple, Google and Meta each publish a CLOSED list of permitted button titles —
        // Apple allows only "Sign in with Apple" / "Sign up with Apple" / "Continue with Apple",
        // and the other two are equally restrictive. Neither replacement is on any of those lists.
        //
        // Both tickets settle this against themselves: "if our layout conflicts with a guideline
        // the guideline ships", and "the note has to overrule any other information". So the
        // permitted title stays put in every state, and the two things the ticket wants said are
        // said where they are allowed to be said:
        //   in flight      — the spinner replaces the mark, and the button is disabled
        //   after a failure — the banner above the list carries "try again"
        // The instruction it displaces ("loses its gradient") was written for the gradient design
        // that the same ticket supersedes; a provider button has no gradient left to lose.
        let label = PROVIDER_COMPLIANT_LABELS
            ? spec.label
            : (isLoading ? "Connecting to \(spec.short)…" : spec.label)

        PillButton(
            label: label,
            variant: variant,
            // Disabled is a state every provider permits, so it carries the in-flight signal.
            enabled: !anyLoading,
            action: { onSelect(method) }
        ) {
            if isLoading {
                Spinner(tint: providerTint(method, isPrimary: isPrimary))
            } else {
                BrandIconView(icon: spec.icon, size: 18, tint: providerTint(method, isPrimary: isPrimary))
            }
        }
        // Siblings dim while one is in flight; the tapped one stays at full opacity.
        .opacity(anyLoading && !isLoading ? 0.45 : 1)
        .animation(.easeOut(duration: Motion.fast), value: anyLoading)
    }
}

/// The escape hatch. Deliberately a different shape and weight from the three connect buttons so it
/// never reads as a fourth provider, and **never disabled or dimmed in any state** — SHOWUP-144
/// says so four separate times, because it is the way out of a provider request that has hung.
private struct SkipRow: View {
    let anyLoading: Bool
    let onSkip: () -> Void

    var body: some View {
        Button(action: onSkip) {
            HStack(spacing: Spacing.md) {
                Text("Skip and continue to profile")
                    .font(F.manrope(15, .semibold))
                BrandIconView(icon: .arrowRight, size: 17, stroke: 2.2,
                              tint: anyLoading ? .liqFg : .liqMuted)
            }
            // Darkens rather than dims while a provider is in flight: it is the only live control
            // on the screen at that moment, so it gets more prominent, not less.
            .foregroundColor(anyLoading ? .liqFg : .liqMuted)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .padding(.horizontal, 20)
            .background(Color.liqElevated)
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .overlay(
                // .liqBorder (ink 12%), as the design system draws it. It was briefly
                // .liqSubtle (ink 46%) for WCAG 1.4.11, which wants 3:1 for the boundary that
                // identifies a control -- see audit/AUDIT-connect-144-145.md finding 7. Reverted
                // on request: this is the designed look, the label and arrow carry the control's
                // identity at 5.03:1, and the deviation is recorded rather than made silently.
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .strokeBorder(Color.liqBorder,
                                  style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
            )
            .padding(.top, Spacing.xs)
        }
        .buttonStyle(PressScale())
    }
}

/// Danger banner — the failure states. Sits above the list; the buttons do not move.
struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            Text("!")
                .font(F.lora(13, bold: true))
                .foregroundColor(.white)
                .frame(width: IconSizes.sm, height: IconSizes.sm)
                .background(Color.liqDanger)
                .clipShape(Circle())
                .padding(.top, 1)
            Text(message)
                .font(F.manrope(13.5, .medium))
                .lineSpacing(13.5 * 0.4)
                .foregroundColor(.liqDangerFg)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, Spacing.xl)
        .background(Color.liqDanger.opacity(0.07))
        .clipShape(RoundedRectangle(cornerRadius: Radius.control, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.control, style: .continuous)
                .strokeBorder(Color.liqDanger.opacity(0.18), lineWidth: 1)
        )
        .padding(.bottom, 14)
        .accessibilityAddTraits(.isStaticText)
    }
}

/// Cancelled notice — neutral, never danger.
///
/// SHOWUP-144: "Cancel is neutral, with --liq-bg-raised, no danger colour, and no shake." Closing a
/// provider sheet is a choice, not a failure, and colouring it red would say otherwise.
struct CancelledNotice: View {
    let provider: AuthMethod

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            BrandIconView(icon: .close, size: 12, stroke: 2.4, tint: .liqFg)
                .frame(width: IconSizes.sm, height: IconSizes.sm)
                .background(Color.liqFg.opacity(0.10))
                .clipShape(Circle())
            (Text("Sign-in cancelled.").font(F.manrope(13.5, .semibold)).foregroundColor(.liqFg)
             + Text(" ")
             + Text("You closed the \(methodSpec(provider).short) sheet before we could finish.")
                .font(F.manrope(13.5, .medium)).foregroundColor(.liqMuted))
                .lineSpacing(13.5 * 0.4)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, Spacing.xl)
        .background(Color.liqRaised)
        .clipShape(RoundedRectangle(cornerRadius: Radius.control, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radius.control, style: .continuous)
                .strokeBorder(Color.liqBorderSoft, lineWidth: 1)
        )
        .padding(.bottom, Spacing.xl)
    }
}

/// 18pt ring, 2pt stroke, one turn every 0.9s. Held still when the device asks for no motion.
struct Spinner: View {
    var tint: Color = .white
    var size: CGFloat = 18
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var spinning = false

    var body: some View {
        Circle()
            .trim(from: 0, to: 0.28)
            .stroke(tint, style: StrokeStyle(lineWidth: 2, lineCap: .round))
            .background(Circle().stroke(tint.opacity(0.30), lineWidth: 2))
            .frame(width: size, height: size)
            .rotationEffect(.degrees(spinning ? 360 : 0))
            .animation(reduceMotion ? nil : .linear(duration: Motion.pulseSlow).repeatForever(autoreverses: false),
                       value: spinning)
            .onAppear { if !reduceMotion { spinning = true } }
            .accessibilityHidden(true)
    }
}
