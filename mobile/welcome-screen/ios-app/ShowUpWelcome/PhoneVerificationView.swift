//  PhoneVerificationView.swift
//  ShowUp · Phone number -> verify code (SHOWUP-143)
//
//  Four states, two views. A (enter number) and B (invalid number) are one screen; C (enter code)
//  and D (code mismatch) are the other. The failure state is a STATE, not a second screen — which
//  is what makes the "CTA does not move" requirement expressible at all.
//
//  The helper regions are reserved rather than conditional: 20 on A/B, and on C/D the height the
//  specified error copy actually needs. See audit/AUDIT-welcome-140-142-143.md finding 1 — the sheet
//  says 42, the specified string wraps to two lines at every width, and reserving 42 would let the
//  CTA jump 17pt, which the ticket forbids outright.
//
//  The numeric keypad drawn on the sheet is a MOCK. The sheet says so, and says not to build it:
//  the platform keyboard ships instead. That is why nothing here draws keys.

import SwiftUI

// MARK: - shared chrome

private struct VerificationFrame<Content: View>: View {
    let onBack: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        // C and D drop the backdrop to .22 / .20 — the code screen is deliberately calmer, and the
        // keyboard owns the bottom half so nothing should glow behind it.
        //
        // SwiftUI's automatic keyboard avoidance only guarantees the *focused field* is visible —
        // it says nothing about the CTA below it. scrollWhenTight makes the whole column reachable
        // instead, so the button cannot end up stranded under the keyboard.
        WelcomeScaffold(peachWash: false, topWeighted: true,
                        orangeAlpha: 0.26, violetAlpha: 0.22,
                        topPadding: 4,          // pad-top 4 here, not the launch screens' 20
                        scrollWhenTight: true) {
            Button(action: onBack) {
                BrandIconView(icon: .arrowLeft, size: 22, stroke: 2, tint: .liqFg)
                    .frame(width: 44, height: 44, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressScale())
            .accessibilityLabel("Back")

            content()

            // One flex:1 spacer at the bottom — unlike Startup and Welcome back, everything here is
            // top-anchored and the keyboard closes the frame. Nothing floats.
            Spacer(minLength: 0)
        }
    }
}

private struct VerificationEyebrow: View {
    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(Color.liqOrange).frame(width: 5, height: 5)
            Text("PHONE VERIFICATION")
                .font(F.manrope(11, .bold))
                .tracking(0.08 * 11)
                .foregroundColor(.liqPurple)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(Color.liqEyebrowBg))
    }
}

// MARK: - States A and B

struct PhoneNumberView: View {
    /// Digits only, no spaces and no dial code -- the pill carries that.
    @Binding var value: String
    var country: Country = DEFAULT_COUNTRY
    var error: PhoneError? = nil
    var onBack: () -> Void = {}
    var onSubmit: () -> Void = {}
    var onOpenCountryList: () -> Void = {}

    @FocusState private var focused: Bool
    private var invalid: Bool { error != nil }

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700
            VerificationFrame(onBack: onBack) {
                VerificationEyebrow()
                Spacer().frame(height: compact ? 2 : 14)
                WashHeadline(parts: [("What’s your ", false), ("number", true), ("?", false)],
                             fontSize: 38)
                    .frame(height: 38 * 1.05 * 2)
                Spacer().frame(height: compact ? 4 : 10)
                Text("We’ll send a 6-digit code to verify it is you.")
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .foregroundColor(.liqNeutral)
                    .frame(maxWidth: 320, alignment: .leading)

                Spacer().frame(height: compact ? 12 : 22)
                HStack(spacing: 8) {
                    // The default comes from device locale; see SignUpFlowView.
                    Button(action: onOpenCountryList) {
                        HStack(spacing: 8) {
                            FlagView(country: country)
                            Text(country.dial).font(F.manrope(16, .semibold)).foregroundColor(.liqFg)
                            BrandIconView(icon: .chevronDown, size: 16, stroke: 2, tint: .liqMuted)
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 56)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqElevated))
                        .overlay(RoundedRectangle(cornerRadius: 14)
                            // Subtle (46%), not Border (12%) — the outline is the only thing identifying the
                            // field, and Border is 1.28:1 against a 3:1 rule. Audit finding 7.
                            .strokeBorder(Color.liqSubtle, lineWidth: 1.5))
                    }
                    .buttonStyle(PressScale())

                    // Same geometry as the default field, so it does not move when it fails — and
                    // the digits are preserved, never cleared.
                    HStack(spacing: 0) {
                        // Bound to the raw digits; the grouping is applied on every change so
                        // the field always displays the country's own habits. Assigning back only
                        // when the value actually differs stops the binding looping.
                        TextField(country.sample, text: Binding(
                            get: { formatNational(value, country) },
                            set: { typed in
                                // maximum PLUS an allowance, never the maximum itself -- see
                                // OVERTYPE_ALLOWANCE. Capping exactly at the limit makes the
                                // too-long error unreachable.
                                let digits = String(typed.filter(\.isNumber)
                                    .prefix(country.nsnMax + OVERTYPE_ALLOWANCE))
                                if digits != value { value = digits }
                            }
                        ))
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .focused($focused)
                        .font(F.manrope(17, .semibold))
                        .monospacedDigit()
                        .tracking(0.3)
                        .foregroundColor(.liqFg)
                        .tint(.liqPurple)
                        .lineLimit(1)
                        .submitLabel(.done)
                        .onSubmit(onSubmit)
                        if invalid {
                            Spacer(minLength: 0)
                            ZStack {
                                Circle().fill(Color.liqDanger).frame(width: 22, height: 22)
                                Text("!").font(.custom(PS.loraBold, size: 14)).foregroundColor(.white)
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .frame(maxWidth: .infinity, minHeight: 56, maxHeight: 56, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqElevated))
                    .overlay(RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(invalid ? Color.liqDanger : Color.liqSubtle, lineWidth: 1.5))
                    .overlay(invalid ? RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(Color.liqDanger.opacity(0.10), lineWidth: 4)
                        .padding(-2.75) : nil)
                }

                // Reserved at 20 — the error replaces the text in the same row, so nothing below
                // it moves and the CTA stays put.
                Text(error?.message(country) ?? "Standard message rates may apply.")
                    .font(F.manrope(13, invalid ? .semibold : .medium))
                    // Muted, not Subtle — helper text has to be readable. Audit finding 7.
                    .foregroundColor(invalid ? .liqDangerFg : .liqMuted)
                    .frame(maxWidth: .infinity, minHeight: 20, alignment: .leading)
                    .padding(.top, 10)
                    .padding(.leading, 4)
                    .accessibilityAddTraits(.updatesFrequently)

                Spacer().frame(height: compact ? 12 : 22)
                // Validation runs on submit, not per keystroke. The button stays live so the
                // user can ask for the check -- what changes on failure is the message, not the
                // availability of the action.
                PillButton("Send me the code", action: onSubmit)
            }
            // The keyboard is why the user is here, so it opens with the screen.
            .onAppear { focused = true }
        }
    }
}

// The flag moved to CountryPicker.swift as `FlagView`, which draws any of them from the
// country table rather than hard-coding Germany. Still rects, never emoji.

// MARK: - States C and D

struct VerifyCodeView: View {
    var phone: String = "+49 176 123 45 678"
    @Binding var digits: String
    var mismatch: Bool = false
    var cooldownSeconds: Int = 21
    var onBack: () -> Void = {}
    var onVerify: () -> Void = {}
    var onResend: () -> Void = {}
    var onEditNumber: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shake: CGFloat = 0
    @FocusState private var focused: Bool

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700
            let slotW: CGFloat = compact ? 44 : 49
            let slotH: CGFloat = compact ? 56 : 62

            VerificationFrame(onBack: onBack) {
                VerificationEyebrow()
                Spacer().frame(height: compact ? 2 : 14)
                WashHeadline(parts: [("Enter your ", false), ("code", true), (".", false)],
                             fontSize: 38)
                    .frame(height: 38 * 1.05)
                Spacer().frame(height: compact ? 4 : 10)
                (Text("We just sent a 6-digit code to ").foregroundColor(.liqNeutral)
                 // must be the number actually submitted on A — the user's only chance to catch a
                 // typo before waiting for an SMS that will never arrive
                 + Text(phone).font(F.manrope(15, .bold)).foregroundColor(.liqFg)
                 + Text(".").foregroundColor(.liqNeutral))
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .frame(maxWidth: 320, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: compact ? 8 : 26)
                // ONE text field behind all six slots, not six fields. Six would mean six focus
                // targets, and then backspace, paste and SMS autofill each have to be taught to
                // hop between them — which is exactly where per-digit OTP inputs usually break.
                // Here the field holds the whole code and the slots are its decoration, so paste
                // and iOS's own one-time-code autofill work for free.
                ZStack {
                    TextField("", text: Binding(
                        get: { digits },
                        set: { digits = String($0.filter(\.isNumber).prefix(6)) }
                    ))
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focused)
                    // The glyphs are painted by the slots, so the field itself draws nothing.
                    .foregroundColor(.clear)
                    .tint(.clear)
                    .accentColor(.clear)
                    .frame(maxWidth: .infinity, minHeight: slotH)

                    HStack(spacing: compact ? 6 : 8) {
                        ForEach(0..<6, id: \.self) { i in
                            slot(index: i, width: slotW, height: slotH)
                        }
                    }
                    .allowsHitTesting(false)
                }
                .contentShape(Rectangle())
                .onTapGesture { focused = true }
                .onAppear { focused = true }
                .frame(maxWidth: .infinity)
                .offset(x: shake)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Enter your 6-digit verification code")
                // One parameter, not two. The two-parameter closure — `{ old, new in }` — is the
                // iOS 17 overload, and this target deploys to 16.0. Same trap as
                // scrollBounceBehavior: valid Swift that needs a newer OS than we claim to support.
                .onChange(of: mismatch) { isBad in
                    // One shot, 480ms, then still. There is no looping animation in this flow.
                    guard isBad, !reduceMotion else { shake = 0; return }
                    withAnimation(.linear(duration: 0.48)) { shake = 0 }
                    let steps: [CGFloat] = [6, -6, 5, -5, 3, -3, 0]
                    for (n, dx) in steps.enumerated() {
                        DispatchQueue.main.asyncAfter(deadline: .now() + Double(n) * 0.068) {
                            withAnimation(.easeInOut(duration: 0.068)) { shake = dx }
                        }
                    }
                }

                // Reserved region — see the file header and the audit note.
                Group {
                    if mismatch {
                        HStack(alignment: .top, spacing: 10) {
                            ZStack {
                                Circle().fill(Color.liqDanger).frame(width: 18, height: 18)
                                Text("!").font(.custom(PS.loraBold, size: 12)).foregroundColor(.white)
                            }
                            // informative, never "Wrong" / "Failed" / "Error"
                            Text("That code didn’t match. Try again.")
                                .font(F.manrope(13.5, .medium))
                                .lineSpacing(13.5 * 0.4)
                                .foregroundColor(.liqDangerFg)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.liqDanger.opacity(0.07)))
                        .overlay(RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(Color.liqDanger.opacity(0.18), lineWidth: 1))
                    }
                }
                // Back to the sheet's 42 — the shortened message is one line at every width.
                .frame(maxWidth: .infinity, minHeight: 42, alignment: .topLeading)
                .padding(.top, compact ? 6 : 14)
                .padding(.leading, 2)
                .accessibilityAddTraits(.updatesFrequently)

                Spacer().frame(height: compact ? 8 : 16)
                // Disabled until all six digits are in. In mismatch the digits are still there, so
                // it stays enabled — the user edits one digit and resubmits.
                PillButton("Verify code", enabled: digits.count == 6, action: onVerify)

                Spacer().frame(height: compact ? 8 : 14)
                VStack(spacing: compact ? 4 : 6) {
                    // A mistyped code must not cost another 24s wait, so the mismatch state releases
                    // the cooldown to 0 and the resend becomes a live button.
                    //
                    // The question and the action are ONE target while the resend is live, so the
                    // whole block is tappable rather than just the underlined phrase. While cooling
                    // it is inert on purpose: the sheet allows no silent resend, so a tappable label
                    // during the cooldown would be either a dead control or a rule broken.
                    let resendLive = mismatch || cooldownSeconds <= 0
                    Button(action: { if resendLive { onResend() } }) {
                        VStack(spacing: compact ? 4 : 6) {
                            Text("Didn’t receive a code?")
                                .font(F.manrope(14, .medium)).foregroundColor(.liqMuted)
                            if resendLive {
                                Text("Send a new code")
                                    .font(F.manrope(14, .bold)).foregroundColor(.liqPurple).underline()
                            } else {
                                Text(String(format: "Send a new code in 0:%02d", cooldownSeconds))
                                    .font(F.manrope(14, .semibold)).monospacedDigit()
                                    .foregroundColor(.liqMuted)
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressScale())
                    .disabled(!resendLive)
                    .accessibilityLabel(Text(resendLive ? "Send a new code" : "Send a new code, waiting"))
                    Button(action: onEditNumber) {
                        HStack(spacing: 6) {
                            BrandIconView(icon: .pencil, size: 13, stroke: 1.8, tint: .liqMuted)
                            Text("Edit phone number")
                                .font(F.manrope(14, .semibold)).foregroundColor(.liqMuted)
                        }
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressScale())
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    @ViewBuilder
    private func slot(index i: Int, width: CGFloat, height: CGFloat) -> some View {
        let ch: Character? = i < digits.count ? Array(digits)[i] : nil
        let active = !mismatch && i == digits.count && digits.count < 6
        ZStack {
            RoundedRectangle(cornerRadius: 14)
                .fill(mismatch ? Color.liqDanger.opacity(0.04) : Color.liqElevated)
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(
                    mismatch ? Color.liqDanger
                        : active ? Color.liqPurple
                        : ch != nil ? Color.liqFg.opacity(0.32)
                        : Color.liqSubtle,
                    lineWidth: 1.5)
            if let ch {
                Text(String(ch))
                    .font(.custom(PS.loraBold, size: 30))
                    .monospacedDigit()
                    .foregroundColor(mismatch ? .liqDangerDigit : .liqFg)
            } else if active {
                // 2 x 28 violet caret. No caret at all while in error.
                Rectangle().fill(Color.liqPurple).frame(width: 2, height: 28)
            }
        }
        .frame(width: width, height: height)
        .shadow(color: Color(hex: 0x2E0147).opacity(0.04), radius: 2, y: 1)
    }
}

#Preview("A · number · 375") { PhoneNumberView() }
#Preview("A · number · 390") { PhoneNumberView() }
#Preview("B · invalid · 390") { PhoneNumberView(value: "0151 2", invalid: true) }
#Preview("C · code · 390") { VerifyCodeView() }
#Preview("D · mismatch · 390") { VerifyCodeView(digits: "482170", mismatch: true) }
