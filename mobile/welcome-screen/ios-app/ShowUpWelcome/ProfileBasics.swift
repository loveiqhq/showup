//  ProfileBasics.swift
//  ShowUp · Profile creation 01 Name (SHOWUP-150) and 02 Email (SHOWUP-152)
//
//  Both screens live here because they are one shell with two questions, and the shell is the part
//  that must not be copied. Mirrors `profile/ProfileNameScreen.kt` and `ProfileEmailScreen.kt`.
//
//  THE CTA IS NEVER DISABLED, AND THAT IS THE WHOLE INTERACTION
//
//  Pressing Continue on an empty or malformed field is what teaches the requirement. A dead button
//  cannot explain itself, and a disabled CTA means the specific error strings are never seen at all.
//
//  NO BACK ON STEP 1
//
//  The design reference passes `leading="back"` on the name screen, contradicting its own header
//  comment, the ticket's acceptance criteria and the spec sheet — all three of which say the slot is
//  EMPTY and the flow is mandatory. The ticket wins on behaviour. Reported to the design side.

import SwiftUI

// MARK: - Copy — final strings

enum NameCopy {
    static let section = "The basics"
    static let sub = "Your name will be shown on your profile."
    static let label = "Enter first name"
    static let placeholder = "First name"
    static let cta = "Continue"
    static let emptyError = "Enter your name to continue."
}

enum EmailCopy {
    static let section = "The basics"
    static let sub = "Never shown on your profile. Used for password reset, receipts and support."
    static let label = "Email"
    static let placeholder = "you@example.com"
    static let helper = "We'll send a 6-digit code to confirm it's really you."
    static let consent = "Receive curated tips, local event invites, and special offers. "
        + "No spam, you can opt out whenever you like."
    static let cta = "Continue"

    static let emptyError = "Enter your email to continue."
    static let noAtLead = "Add an "
    static let noAtChip = "@"
    static let noAtTail = " \u{2014} e.g. you@example.com"
    static let noDotLead = "Looks like the domain is missing "
    static let noDotChip = ".com"
    static let noDotTail = " (or similar)."
    static let genericError = "That email doesn\u{2019}t look quite right. Please check it."
}

// MARK: - Validation

/// Format only — `something@something.something`.
///
/// Drives the tick and the helper line and **does not gate the CTA**. Deliberately not
/// RFC-complete: the screen's job is to catch a typo before a code is sent to nobody, and a
/// stricter check rejects addresses that genuinely work.
func isEmailFormatValid(_ raw: String) -> Bool {
    let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    return value.range(of: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", options: .regularExpression) != nil
}

/// Which of the four messages the input earns.
///
/// "Please enter a valid email" is banned: it tells the user nothing they do not already know.
enum EmailErrorKind: Equatable { case empty, missingAt, missingDot, generic }

func emailErrorKind(_ raw: String) -> EmailErrorKind {
    let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if value.isEmpty { return .empty }
    if !value.contains("@") { return .missingAt }
    let after = value.split(separator: "@", maxSplits: 1, omittingEmptySubsequences: false)
    if after.count < 2 || !after[1].contains(".") { return .missingDot }
    return .generic
}

/// The message, with the named fragment as a mono chip so the fix is one keystroke.
func emailErrorText(_ raw: String) -> Text {
    func chip(_ s: String) -> Text {
        Text(s)
            .font(.system(size: 13, weight: .bold, design: .monospaced))
    }
    switch emailErrorKind(raw) {
    case .empty:      return Text(EmailCopy.emptyError)
    case .missingAt:  return Text(EmailCopy.noAtLead) + chip(EmailCopy.noAtChip) + Text(EmailCopy.noAtTail)
    case .missingDot: return Text(EmailCopy.noDotLead) + chip(EmailCopy.noDotChip) + Text(EmailCopy.noDotTail)
    case .generic:    return Text(EmailCopy.genericError)
    }
}

// MARK: - 01 · Name

struct ProfileNameView: View {
    @Binding var value: String
    var onContinue: (String) -> Void = { _ in }
    /// Fires on the refused press only, never on render.
    var onEmptySubmit: () -> Void = {}

    @State private var attempted = false
    @State private var shakeKey = 0
    @FocusState private var focused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var filled: Bool {
        !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    private var isError: Bool { attempted && !filled }

    private func submit() {
        if filled {
            onContinue(value.trimmingCharacters(in: .whitespacesAndNewlines))
        } else {
            attempted = true
            shakeKey += 1
            onEmptySubmit()
        }
    }

    var body: some View {
        BasicsScaffold(title: NameCopy.section, leading: .none) {
            StepProgress(steps: 3, current: BasicsStep.name.progressSegment)
                .padding(.bottom, 28)

            // Lora 700 / 34 / 1.1 / -0.015em. WashHeadline defaults to the welcome flow's 1.05 and
            // -0.02em, so both are passed. 34 and not 38: the keyboard is always open here, so the
            // headline is one step smaller.
            WashHeadline(parts: [("What's your ", false), ("name", true), ("?", false)],
                         fontSize: 34, lineHeightMultiple: 1.1, trackingEm: -0.015)
                .padding(.bottom, 10)

            Text(NameCopy.sub)
                .font(F.manrope(15, .medium))
                .foregroundColor(.liqNeutral)
                .lineSpacing(15 * 0.45)
                .frame(maxWidth: 320, alignment: .leading)

            FloatingField(
                value: $value,
                label: NameCopy.label,
                placeholder: NameCopy.placeholder,
                error: isError,
                capitalization: .words,
                onSubmit: submit
            )
            .padding(.top, 28)
            .modifier(ShakeOnce(key: shakeKey, active: isError && !reduceMotion))

            // RESERVED, not conditional. 44 holds the space so the CTA and the keyboard sit at the
            // identical Y in all three states. Rendering it conditionally is exactly how three
            // states stop reading as one screen.
            ZStack(alignment: .topLeading) {
                Color.clear.frame(minHeight: 44)
                if isError { InlineErrorCard(message: Text(NameCopy.emptyError)) }
            }
            .frame(minHeight: 44, alignment: .topLeading)
            .padding(.top, 10)
            .padding(.leading, 2)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.updatesFrequently)
        } cta: {
            HStack {
                Spacer(minLength: 0)
                // Orange, not sunset — these are routine screens. Arrow 22.
                NextButton(label: NameCopy.cta, arrowSize: 22, action: submit)
            }
            .padding(.bottom, 18)
        }
        .onAppear { focused = true }
    }
}

// MARK: - 02 · Email

struct ProfileEmailView: View {
    @Binding var value: String
    @Binding var consent: Bool
    var onContinue: (String) -> Void = { _ in }
    var onBack: () -> Void = {}
    var onSubmitRefused: (Bool) -> Void = { _ in }

    @State private var attempted = false
    @State private var shakeKey = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var valid: Bool { isEmailFormatValid(value) }
    private var isError: Bool { attempted && !valid }

    private func submit() {
        if valid {
            onContinue(value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        } else {
            attempted = true
            shakeKey += 1
            onSubmitRefused(emailErrorKind(value) == .empty)
        }
    }

    var body: some View {
        BasicsScaffold(title: EmailCopy.section, leading: .back, onBack: onBack) {
            StepProgress(steps: 3, current: BasicsStep.email.progressSegment)
                .padding(.bottom, 18)

            WashHeadline(parts: [("What's your ", false), ("email", true), ("?", false)],
                         fontSize: 32, lineHeightMultiple: 1.1, trackingEm: -0.015)
                .padding(.bottom, 8)

            // Leads with what the email is NOT: it is the field users most expect to be spammed
            // from, so the reassurance goes first and the three real uses second — and it is the
            // answer to the consent row below it.
            Text(EmailCopy.sub)
                .font(F.manrope(14.5, .medium))
                .foregroundColor(.liqNeutral)
                .lineSpacing(14.5 * 0.4)
                .frame(maxWidth: 320, alignment: .leading)

            FloatingField(
                value: $value,
                label: EmailCopy.label,
                placeholder: EmailCopy.placeholder,
                valid: valid,
                error: isError,
                keyboard: .emailAddress,
                capitalization: .never,
                contentType: .emailAddress,
                onSubmit: submit
            )
            .padding(.top, 16)
            .modifier(ShakeOnce(key: shakeKey, active: isError && !reduceMotion))

            // NOT height-reserved. This screen is the group's densest and reserving the taller
            // error height in the calm state would push the CTA into the keys, so the consent row
            // and CTA sit ~18 lower in the error state. What must hold instead is that the CTA
            // stays fully visible above the keyboard in BOTH states, at every size.
            Group {
                if isError {
                    InlineErrorCard(message: emailErrorText(value))
                } else {
                    HStack(alignment: .top, spacing: Spacing.md) {
                        CheckGlyph(size: 16, color: .liqSuccessFg, lineWidth: 2)
                            .padding(.top, 1)
                        // Muted, NOT success green — the tick carries the colour. The only place in
                        // the flow the user is told a code screen is coming.
                        Text(EmailCopy.helper)
                            .font(F.manrope(13, .medium))
                            .foregroundColor(.liqMuted)
                            .lineSpacing(13 * 0.4)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 2)
                    .padding(.vertical, 4)
                }
            }
            .padding(.top, 8)
            .padding(.leading, 2)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.updatesFrequently)

            MarketingOptIn(checked: $consent)
                .padding(.top, 10)
        } cta: {
            HStack {
                Spacer(minLength: 0)
                NextButton(label: EmailCopy.cta, arrowSize: 22, action: submit)
            }
            // 10, not name's 18 — the tight margin is what keeps the CTA clear of the keys in the
            // error state, where everything below the field sits ~18 lower.
            .padding(.bottom, 10)
        }
    }
}

/// The marketing consent row.
///
/// **Opt-IN.** Unchecked by default, never pre-ticked, never gates Continue, never styled as an
/// error. The whole row is the hit area. Transactional email — the code, password reset, receipts —
/// is unaffected and needs no consent; this row governs marketing only.
struct MarketingOptIn: View {
    @Binding var checked: Bool

    var body: some View {
        Button {
            checked.toggle()
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Text(EmailCopy.consent)
                    .font(F.manrope(13, .medium))
                    .foregroundColor(.liqFg)
                    .lineSpacing(13 * 0.3)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(checked ? Color.liqPurple : Color.liqElevated)
                    RoundedRectangle(cornerRadius: 6)
                        .strokeBorder(checked ? Color.liqPurple : Color.liqBorder, lineWidth: 1.5)
                    if checked { CheckGlyph(size: 13, color: .white, lineWidth: 3) }
                }
                .frame(width: 22, height: 22)
                .animation(.easeOut(duration: Motion.fast), value: checked)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: Radius.errorBox).fill(Color.liqRaised))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.errorBox)
                    .strokeBorder(Color.liqBorderSoft, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(EmailCopy.consent))
        .accessibilityAddTraits(checked ? [.isButton, .isSelected] : .isButton)
    }
}

/// The one-shot 480ms failure shake, re-keyed per failure so a second refused press shakes again.
///
/// Without the key the animation has already run and a repeat press produces nothing, which reads
/// as the button having died.
///
/// `keyframeAnimator` rather than a chain of delayed closures: the first version used
/// `DispatchQueue.main.asyncAfter`, which `check-swift-concurrency.py` rejects on sight — a queue
/// hop leaves the isolation the compiler tracks, and this project bans the escape hatches that
/// would silence that. The keyframes are also a truer reading of the design: one timeline the
/// system owns, not ten timers racing it.
///
/// The amplitude is scaled to zero rather than the track being conditional, because a keyframe
/// builder has to produce the same shape every time. Reduce Motion arrives here as `active: false`.
struct ShakeOnce: ViewModifier {
    var key: Int
    var active: Bool

    func body(content: Content) -> some View {
        let amp: CGFloat = active ? 1 : 0
        let step = Motion.shake * 0.1
        return content.keyframeAnimator(initialValue: CGFloat.zero, trigger: key) { view, dx in
            view.offset(x: dx)
        } keyframes: { _ in
            KeyframeTrack {
                // -2, +3, -6, +6, decaying to nothing — the reference's su-shake curve.
                CubicKeyframe(-2 * amp, duration: step)
                CubicKeyframe(3 * amp, duration: step)
                CubicKeyframe(-6 * amp, duration: step)
                CubicKeyframe(6 * amp, duration: step)
                CubicKeyframe(-6 * amp, duration: step)
                CubicKeyframe(6 * amp, duration: step)
                CubicKeyframe(-6 * amp, duration: step)
                CubicKeyframe(3 * amp, duration: step)
                CubicKeyframe(-2 * amp, duration: step)
                CubicKeyframe(0, duration: step)
            }
        }
    }
}
