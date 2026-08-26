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
    var value: String = "176 123 45 678"
    var invalid: Bool = false
    var countryCode: String = "+49"
    var onBack: () -> Void = {}
    var onSubmit: () -> Void = {}
    var onOpenCountryList: () -> Void = {}

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700
            VerificationFrame(onBack: onBack) {
                VerificationEyebrow()
                Spacer().frame(height: compact ? 4 : 14)
                WashHeadline(parts: [("What’s your ", false), ("number", true), ("?", false)],
                             fontSize: 38)
                    .frame(height: 38 * 1.05 * 2)
                Spacer().frame(height: compact ? 6 : 10)
                Text("We’ll send a 6-digit code to verify it is you.")
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .foregroundColor(.liqNeutral)
                    .frame(maxWidth: 320, alignment: .leading)

                Spacer().frame(height: compact ? 14 : 22)
                HStack(spacing: 8) {
                    // DE / +49 is the mock default only — the real default comes from device locale,
                    // and tapping opens a country list that is out of scope here.
                    Button(action: onOpenCountryList) {
                        HStack(spacing: 8) {
                            GermanFlag()
                            Text(countryCode).font(F.manrope(16, .semibold)).foregroundColor(.liqFg)
                            BrandIconView(icon: .chevronDown, size: 16, stroke: 2, tint: .liqMuted)
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 56)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqElevated))
                        .overlay(RoundedRectangle(cornerRadius: 14)
                            .strokeBorder(Color.liqBorder, lineWidth: 1.5))
                    }
                    .buttonStyle(PressScale())

                    // Same geometry as the default field, so it does not move when it fails — and
                    // the digits are preserved, never cleared.
                    HStack(spacing: 0) {
                        Text(value.isEmpty ? "176 123 45 678" : value)
                            .font(F.manrope(17, .semibold))
                            .monospacedDigit()
                            .tracking(0.3)
                            .foregroundColor(value.isEmpty ? .liqFaint : .liqFg)
                            .lineLimit(1)
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
                        .strokeBorder(invalid ? Color.liqDanger : Color.liqBorder, lineWidth: 1.5))
                    .overlay(invalid ? RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(Color.liqDanger.opacity(0.10), lineWidth: 4)
                        .padding(-2.75) : nil)
                }

                // Reserved at 20 — the error replaces the text in the same row, so nothing below
                // it moves and the CTA stays put.
                Text(invalid ? "Please enter a valid number e.g. 176 123 45 678"
                             : "Standard message rates may apply.")
                    .font(F.manrope(13, invalid ? .semibold : .medium))
                    .foregroundColor(invalid ? .liqDangerFg : .liqSubtle)
                    .frame(maxWidth: .infinity, minHeight: 20, alignment: .leading)
                    .padding(.top, 10)
                    .padding(.leading, 4)
                    .accessibilityAddTraits(.updatesFrequently)

                Spacer().frame(height: compact ? 14 : 22)
                // Validation runs on submit, not per keystroke, so the CTA is only disabled after a
                // failure and re-enables the moment the value changes.
                PillButton("Send me the code", enabled: !invalid, action: onSubmit)
            }
        }
    }
}

/// Three rects with a hairline. CLAUDE.md forbids emoji anywhere, flags included.
private struct GermanFlag: View {
    var body: some View {
        VStack(spacing: 0) {
            Color.black
            Color(hex: 0xDD0000)
            Color(hex: 0xFFCE00)
        }
        .frame(width: 22, height: 14)
        .clipShape(RoundedRectangle(cornerRadius: 2))
        .overlay(RoundedRectangle(cornerRadius: 2)
            .strokeBorder(Color.liqFg.opacity(0.35), lineWidth: 0.5))
    }
}

// MARK: - States C and D

struct VerifyCodeView: View {
    var phone: String = "+49 176 123 45 678"
    var digits: String = ""
    var mismatch: Bool = false
    var cooldownSeconds: Int = 21
    var onBack: () -> Void = {}
    var onVerify: () -> Void = {}
    var onResend: () -> Void = {}
    var onEditNumber: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shake: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700
            let slotW: CGFloat = compact ? 44 : 49
            let slotH: CGFloat = compact ? 56 : 62

            VerificationFrame(onBack: onBack) {
                VerificationEyebrow()
                Spacer().frame(height: compact ? 4 : 14)
                WashHeadline(parts: [("Enter your ", false), ("code", true), (".", false)],
                             fontSize: 38)
                    .frame(height: 38 * 1.05)
                Spacer().frame(height: compact ? 6 : 10)
                (Text("We just sent a 6-digit code to ").foregroundColor(.liqNeutral)
                 // must be the number actually submitted on A — the user's only chance to catch a
                 // typo before waiting for an SMS that will never arrive
                 + Text(phone).font(F.manrope(15, .bold)).foregroundColor(.liqFg)
                 + Text(".").foregroundColor(.liqNeutral))
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .frame(maxWidth: 320, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: compact ? 14 : 26)
                HStack(spacing: compact ? 6 : 8) {
                    ForEach(0..<6, id: \.self) { i in
                        slot(index: i, width: slotW, height: slotH)
                    }
                }
                .frame(maxWidth: .infinity)
                .offset(x: shake)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Enter your 6-digit verification code")
                .onChange(of: mismatch) { _, isBad in
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
                            Text("Code doesn’t match. Please check or request a new code.")
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
                .frame(maxWidth: .infinity, minHeight: 59, alignment: .topLeading)
                .padding(.top, compact ? 10 : 14)
                .padding(.leading, 2)
                .accessibilityAddTraits(.updatesFrequently)

                Spacer().frame(height: compact ? 12 : 16)
                // Disabled until all six digits are in. In mismatch the digits are still there, so
                // it stays enabled — the user edits one digit and resubmits.
                PillButton("Verify code", enabled: digits.count == 6, action: onVerify)

                Spacer().frame(height: compact ? 12 : 22)
                VStack(spacing: compact ? 8 : 10) {
                    // A mistyped code must not cost another 24s wait, so the mismatch state releases
                    // the cooldown to 0 and the resend becomes a live button.
                    //
                    // The question and the action are ONE target while the resend is live, so the
                    // whole block is tappable rather than just the underlined phrase. While cooling
                    // it is inert on purpose: the sheet allows no silent resend, so a tappable label
                    // during the cooldown would be either a dead control or a rule broken.
                    let resendLive = mismatch || cooldownSeconds <= 0
                    Button(action: { if resendLive { onResend() } }) {
                        VStack(spacing: compact ? 8 : 10) {
                            Text("Didn’t receive a code?")
                                .font(F.manrope(14, .medium)).foregroundColor(.liqMuted)
                            if resendLive {
                                Text("Send a new code")
                                    .font(F.manrope(14, .bold)).foregroundColor(.liqPurple).underline()
                            } else {
                                Text(String(format: "Send a new code in 0:%02d", cooldownSeconds))
                                    .font(F.manrope(14, .semibold)).monospacedDigit()
                                    .foregroundColor(.liqSubtle)
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
                        : Color.liqBorder,
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
