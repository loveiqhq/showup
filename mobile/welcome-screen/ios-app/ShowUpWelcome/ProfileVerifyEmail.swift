//  ProfileVerifyEmail.swift
//  ShowUp · Profile creation 03 — Verify email (SHOWUP-153)
//
//  Mirrors `profile/ProfileVerifyEmailScreen.kt`.
//
//  STILL STEP 2 OF 3
//
//  The progress bar reads current=2, identical to the email screen's. The user is on the email
//  step until the code is confirmed; verification is not a fourth thing they did.
//
//  THE ENTERED DIGITS SURVIVE A FAILURE
//
//  The ticket calls this the one behaviour to review if only one gets reviewed, and it is right:
//  the user almost always mistyped a single digit, and clearing the row makes them re-read the
//  whole code out of their inbox.
//
//  BOTTOM-SLACK, NOT BOTTOM-ANCHORED
//
//  Unlike steps 1 and 2, the single flexible spacer sits at the BOTTOM of the column. Passing an
//  EMPTY cta to BasicsScaffold achieves exactly that: the scaffold's own spacer lands after this
//  content and before nothing.

import SwiftUI

/// Final strings. Every one is quoted from the ticket.
enum VerifyEmailCopy {
    static let section = "The basics"
    static let subLead = "We sent a 6-digit code to "
    static let cta = "Verify code"
    static let resendQuestion = "Didn’t receive a code?"
    static let resendAvailable = "Send a new code"
    static let changeAddress = "Change email address"
    static let slotRowLabel = "Enter your 6-digit verification code"

    /// Names both recoveries, because at that moment the user does not know which they need.
    static let mismatch = "That code doesn’t match. Check your inbox or request a new one."

    /// Approved by the product side, 10 September 2026. Points at resend only — re-reading the
    /// inbox cannot help a code that has died of age.
    static let expired = "That code has expired. Send a new one to try again."

    /// PROPOSED — not yet approved. No ticket specifies this string; 153 lists the state as
    /// undesigned. Kept to one line so the reserved region holds.
    static let lockedOut = "Too many tries. Send a new code."

    /// Minutes are computed — the cooldown is 60, so the first tick is 1:00.
    static func resendIn(_ seconds: Int) -> String {
        String(format: "Send a new code in %d:%02d", seconds / 60, seconds % 60)
    }
}

struct ProfileVerifyEmailView: View {
    /// Echoed back verbatim, never truncated — the whole point of the sub copy is that the user
    /// can catch their own typo before waiting for mail that will never arrive.
    var email: String = "leo@hey.com"
    @Binding var digits: String
    var state: VerifyState = .calm
    var cooldownSeconds: Int = 60
    /// A request is in flight.
    ///
    /// The CTA goes inert rather than growing a spinner: PrimaryButton has no loading variant,
    /// and adding one would change a control two shipped screens already draw. Inert is honest
    /// and costs no layout — a measured spinner is a separate, deliberate change.
    var busy: Bool = false
    var onVerify: () -> Void = {}
    var onResend: () -> Void = {}
    var onChangeEmail: () -> Void = {}
    var onBack: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shake: CGFloat = 0
    @FocusState private var focused: Bool

    private var isError: Bool { state != .calm }

    private var message: String? {
        switch state {
        case .calm: return nil
        case .mismatch: return VerifyEmailCopy.mismatch
        case .expired: return VerifyEmailCopy.expired
        case .lockedOut: return VerifyEmailCopy.lockedOut
        }
    }

    var body: some View {
        // Empty cta: the scaffold's single spacer then lands at the bottom, which IS the
        // bottom-slack layout. Adding a spacer here as well would make two.
        BasicsScaffold(title: VerifyEmailCopy.section, leading: .back, onBack: onBack, content: {
            // current = 2, IDENTICAL to the email screen. The bar does not advance here.
            StepProgress(steps: 3, current: BasicsStep.emailVerify.progressSegment)
                .padding(.bottom, 18)

            // "email" is the single italic em carrying the orange wash. Ends in a FULL STOP —
            // the only headline in the group that does, because the user has nothing to decide
            // here, only something to do.
            WashHeadline(parts: [("Please verify your ", false), ("email", true), (".", false)],
                         fontSize: 32, lineHeightMultiple: 1.1)
                .padding(.bottom, 8)

            (Text(VerifyEmailCopy.subLead)
                .font(F.manrope(14.5, .medium))
                .foregroundColor(.liqNeutral)
             + Text(email)
                .font(F.manrope(14.5, .bold))
                .foregroundColor(.liqFg)
             + Text(".")
                .font(F.manrope(14.5, .medium))
                .foregroundColor(.liqNeutral))
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: 320, alignment: .leading)

            codeRow.padding(.top, 22)

            // ⑭ A RESERVE, not a floor, and that is the whole point of it.
            //
            // The card is ALWAYS present and hidden with opacity — a ViewBuilder branch that
            // evaluates to nil reserves nothing, which shipped a 56pt CTA jump on this flow.
            // That much was right. What was wrong was `minHeight: 30`: a minimum lets the region
            // grow the moment the copy wraps, and everything below it moves with it. Reported
            // from an Android device on 11 September, where the CTA, the resend row and the
            // change-address link all slid down when the mismatch card arrived; this file had
            // the same defect and the same number.
            //
            // Fixed at 80, matching ProfileVerifyEmailScreen.kt and the phone screen's helper
            // region: the tallest message this screen can show, wrapped at the narrowest width
            // in the matrix. The quiet space under the slots in the calm state is what the
            // guarantee costs, and SHOWUP-143 is explicit that it is worth paying.
            ZStack(alignment: .topLeading) {
                InlineErrorCard(message: Text(message ?? VerifyEmailCopy.mismatch))
                    .opacity(isError ? 1 : 0)
            }
            .frame(maxWidth: .infinity, height: 80, alignment: .topLeading)
            .padding(.top, 12)
            .padding(.leading, 2)
            .accessibilityAddTraits(.updatesFrequently)

            // The group's ONE disabled CTA, and a settled exception rather than drift: a partial
            // code has nothing to validate, and the six boxes already say "six digits".
            PrimaryButton(VerifyEmailCopy.cta,
                          enabled: canSubmitCode(digits, state: state) && !busy,
                          action: onVerify)
                .padding(.top, 14)

            secondaryActions.padding(.top, 18)
        }, cta: { EmptyView() })
    }

    private var codeRow: some View {
        ZStack {
            // One field behind six slots, so paste and one-time-code autofill work without six
            // focus targets handing off to each other.
            TextField("", text: Binding(
                get: { digits },
                set: { digits = String($0.filter(\.isNumber).prefix(6)) }
            ))
            .keyboardType(.numberPad)
            .textContentType(.oneTimeCode)
            .focused($focused)
            .foregroundColor(.clear)
            .accentColor(.clear)
            .frame(maxWidth: .infinity, minHeight: 62)

            CodeSlotRow(digits: digits, error: isError, halo: true, caretBlinks: true)
                .allowsHitTesting(false)
        }
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
        .onAppear { focused = true }
        .offset(x: shake)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(VerifyEmailCopy.slotRowLabel)
        .onChange(of: state) { _, next in
            // One shot per transition INTO a failure, so a second wrong code shakes again.
            guard next != .calm, !reduceMotion else { shake = 0; return }
            withAnimation(.linear(duration: Motion.shake)) { shake = 0 }
            let steps: [CGFloat] = [6, -6, 5, -5, 3, -3, 0]
            let start = Date()
            for (i, dx) in steps.enumerated() {
                let deadline = start.addingTimeInterval(Double(i) * Motion.shake / Double(steps.count))
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(max(0, deadline.timeIntervalSinceNow)))
                    shake = dx
                }
            }
        }
    }

    private var secondaryActions: some View {
        VStack(spacing: 6) {
            // One row, ONE HEIGHT, whichever branch is showing.
            //
            // The link carries the 44pt tap floor and the countdown beside it does not, so
            // swapping between them changed this row's height — and with the row aligned to a
            // text baseline, the question moved as the link's box grew around it. A mismatch
            // RELEASES the cooldown (SHOWUP-143), so the swap happens at exactly the moment the
            // error appears, which is why it read as the error moving the links.
            //
            // The slot is a fixed height either way now, so nothing moves; centring the row
            // against it keeps the question on the link's optical line.
            HStack(alignment: .center, spacing: 6) {
                Text(VerifyEmailCopy.resendQuestion)
                    .font(F.manrope(14, .semibold))
                    .foregroundColor(.liqFg)
                ZStack {
                    if canResend(cooldownSeconds: cooldownSeconds, state: state) {
                        Button(action: onResend) {
                            Text(VerifyEmailCopy.resendAvailable)
                                .font(F.manrope(14, .bold))
                                .foregroundColor(.liqPurple)
                                .underline()
                        }
                    } else {
                        Text(VerifyEmailCopy.resendIn(cooldownSeconds))
                            .font(F.manrope(14, .semibold))
                            .foregroundColor(.liqSubtle)
                            // tabular so the countdown does not jitter as digits change width
                            .monospacedDigit()
                    }
                }
                .frame(height: ComponentSizes.minTapTarget)
            }
            Button(action: onChangeEmail) {
                Text(VerifyEmailCopy.changeAddress)
                    .font(F.manrope(13.5, .semibold))
                    .foregroundColor(.liqMuted)
                    .underline()
            }
            .frame(minHeight: ComponentSizes.minTapTarget)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview("A · arrival · 390") {
    ProfileVerifyEmailView(digits: .constant(""))
}

#Preview("A · typed · 390") {
    ProfileVerifyEmailView(digits: .constant("4821"))
}

#Preview("B · mismatch · 390") {
    ProfileVerifyEmailView(digits: .constant("482170"), state: .mismatch, cooldownSeconds: 0)
}

#Preview("C · expired · 390") {
    ProfileVerifyEmailView(digits: .constant("482170"), state: .expired, cooldownSeconds: 0)
}

#Preview("D · locked out · 390") {
    ProfileVerifyEmailView(digits: .constant("482170"), state: .lockedOut, cooldownSeconds: 0)
}

#Preview("A · long address") {
    ProfileVerifyEmailView(email: "leonardo.buonarroti@a-very-long-domain.example",
                           digits: .constant(""))
}
