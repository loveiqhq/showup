//  PhoneVerificationView.swift
//  ShowUp · Phone number -> verify code (SHOWUP-143)
//
//  Four states, two views. A (enter number) and B (invalid number) are one screen; C (enter code)
//  and D (code mismatch) are the other. The failure state is a STATE, not a second screen — which
//  is what makes the "CTA does not move" requirement expressible at all.
//
//  The helper regions are reserved rather than conditional: two lines on A/B, and on C/D the height
//  the specified error copy actually needs. See audit/AUDIT-welcome-140-142-143.md finding 1 — the
//  sheet says 42, the specified string wraps to two lines at every width, and reserving 42 would
//  let the CTA jump 17pt, which the ticket forbids outright.
//
//  Both regions were reserving nothing like what they claimed, and both were found by running this
//  on a simulator for the first time on 2026-09-01: A/B held one line for copy that always takes
//  two (CTA moved 15.7pt), and C/D wrapped its card in a bare `if`, which reserves nothing at all
//  in SwiftUI (CTA moved 56pt). Both now measure 0.0pt on an iPhone 17 Pro. The fixes are at each
//  site; what they have in common is that a reserve you cannot see is a reserve nobody checked.
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
                // chevron-left at 24, stroke 2 -- the handoff's AppHeader with leading="back",
                // which welcome/screen-phone-reference.jsx uses on both of these screens. It was
                // an arrow-left at 22: the wrong icon from the same set, and visibly heavier.
                BrandIconView(icon: .chevronLeft, size: 24, stroke: 2, tint: .liqFg)
                    .frame(width: ComponentSizes.minTapTarget, height: ComponentSizes.minTapTarget, alignment: .leading)
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
        HStack(spacing: Spacing.sm) {
            Circle().fill(Color.liqOrange).frame(width: 5, height: 5)
            Text("PHONE VERIFICATION")
                .font(F.manrope(11, .bold))
                .tracking(0.08 * 11)
                // The ORANGE tone. screen-phone-reference.jsx uses <Eyebrow color="orange"> on
                // both of these screens; lavender is the tutorial's and was taken by default.
                .foregroundColor(.liqOrange)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, 5)
        .background(Capsule().fill(Color.liqEyebrowOrangeBg))
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

    private var invalid: Bool { error != nil }

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700
            VerificationFrame(onBack: onBack) {
                VerificationEyebrow()
                Spacer().frame(height: compact ? 2 : 14)
                // No fixed height. The frame was `fontSize * 1.05 * lines`, which is the LINE BOX
                // and not what the glyphs occupy: a 1.05 line height is tighter than Lora's natural
                // leading, so the ascenders and descenders overflow it, and `.frame(height:)` centres
                // rather than clips. The text therefore spilled downward into the 10pt margin below
                // and the underline wash sat on top of the sub copy. WashHeadline reports the size it
                // actually needs, and Android has always let it. Measured on the phone screen: the
                // ink gap to the sub copy went from 15pt to 27pt with the frame gone.
                WashHeadline(parts: [("What’s your ", false), ("number", true), ("?", false)],
                             fontSize: 38)
                Spacer().frame(height: compact ? 4 : 10)
                Text("We’ll send a 6-digit code to verify it is you.")
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .foregroundColor(.liqNeutral)
                    .frame(maxWidth: 320, alignment: .leading)

                Spacer().frame(height: compact ? 12 : 22)
                HStack(spacing: Spacing.md) {
                    // The default comes from device locale; see SignUpFlowView.
                    Button(action: onOpenCountryList) {
                        HStack(spacing: Spacing.md) {
                            FlagView(country: country)
                            Text(country.dial).font(F.manrope(16, .semibold)).foregroundColor(.liqFg)
                            BrandIconView(icon: .chevronDown, size: 16, stroke: 2, tint: .liqMuted)
                        }
                        // A button wearing the field's chrome, so the two cannot drift apart.
                        // 14 rather than the field's 18: the pill hugs its content, the field
                        // does not. The contrast argument for Subtle over Border lives in
                        // FieldChrome now, next to the value it defends.
                        .fieldChrome(FieldChrome(horizontalPadding: 14))
                    }
                    .buttonStyle(PressScale())

                    // Same geometry as the default field, so it does not move when it fails — and
                    // the digits are preserved, never cleared.
                    //
                    // The chrome, the 56, the outline and the danger ring are all InputField's
                    // now. What stays here is the one thing specific to this screen: the field
                    // is a UITextField and not SwiftUI's TextField, because as-you-type grouping
                    // needs caret control that TextField does not expose. The whole argument is
                    // in PhoneNumberField.swift. `value` stays plain digits; the grouping is
                    // presentation, applied inside the edit.
                    InputField(label: "Phone number", invalid: invalid) {
                        PhoneNumberField(digits: $value, country: country, onSubmit: onSubmit)
                    } trailing: {
                        if invalid { FieldErrorGlyph() }
                    }
                }

                // Reserved at TWO lines, top-aligned, because that is what the error copy needs.
                //
                // It was reserved at 20 -- one line -- which is what the calm default takes but not
                // what any error takes: "That looks too short for United States. For example 201
                // 555 0123." wraps at every width this screen ships at. Measured on an iPhone 17
                // Pro, that let the CTA drop 15.7pt the moment the number was rejected, which is
                // the jump SHOWUP-143 forbids and the one 20 was chosen to prevent.
                //
                // A fixed height rather than a minimum, so the row cannot grow either; the scale
                // factor lets a long country name shrink to fit instead of being cut off.
                Text(error?.message(country) ?? "Standard message rates may apply.")
                    .font(F.manrope(13, invalid ? .semibold : .medium))
                    // Muted, not Subtle — helper text has to be readable. Audit finding 7.
                    .foregroundColor(invalid ? .liqDangerFg : .liqMuted)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, minHeight: 36, maxHeight: 36, alignment: .topLeading)
                    .padding(.top, Spacing.lg)
                    .padding(.leading, Spacing.xs)
                    .accessibilityAddTraits(.updatesFrequently)

                Spacer().frame(height: compact ? 12 : 22)
                // Validation runs on submit, not per keystroke. The button stays live so the
                // user can ask for the check -- what changes on failure is the message, not the
                // availability of the action.
                PillButton("Send me the code", action: onSubmit)
            }
            // The keyboard is why the user is here, so it opens with the screen -- see
            // AutoFocusTextField, which takes it the moment the field reaches a window.
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
                Spacer().frame(height: compact ? 4 : 10)
                (Text("We just sent a 6-digit code to ").foregroundColor(.liqNeutral)
                 // must be the number actually submitted on A — the user's only chance to catch a
                 // typo before waiting for an SMS that will never arrive
                 //
                 // Every space inside it is made non-breaking, so the number wraps as one thing.
                 // With ordinary spaces the line broke wherever it ran out of room, leaving "+1"
                 // stranded on the first line and the digits on the second. A number split across
                 // two lines is exactly what a person is here to read back and check.
                 + Text(phone.replacingOccurrences(of: " ", with: "\u{00A0}"))
                     .font(F.manrope(15, .bold)).foregroundColor(.liqFg)
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
                // Two parameters now the target is iOS 17. The single-parameter closure is
                // deprecated from 17 onwards; it was used here only because the project deployed
                // to 16.0, where the two-parameter overload does not exist.
                .onChange(of: mismatch) { _, isBad in
                    // One shot, 480ms, then still. There is no looping animation in this flow.
                    guard isBad, !reduceMotion else { shake = 0; return }
                    withAnimation(.linear(duration: Motion.shake)) { shake = 0 }
                    let steps: [CGFloat] = [6, -6, 5, -5, 3, -3, 0]

                    // Absolute deadlines measured from one start point, NOT a chain of sleeps.
                    //
                    // The obvious rewrite -- sleep 68ms, move, sleep 68ms, move -- is wrong in a
                    // way that only shows on a device: each wait carries its own small scheduling
                    // error and they accumulate, so by the seventh step the shake visibly lags.
                    // Offsetting every step from `start` keeps the exact timing the DispatchQueue
                    // version had, which is the point: this is a mechanical swap of the scheduling
                    // mechanism, not a redesign of the animation.
                    //
                    // The mechanism had to change because Swift 6 will not allow a DispatchQueue
                    // closure to touch view state -- it cannot see that the closure runs on the
                    // main actor. A Task can be told, and is.
                    let start = ContinuousClock.now
                    Task { @MainActor in
                        for (n, dx) in steps.enumerated() {
                            try? await Task.sleep(
                                until: start + .seconds(Double(n) * 0.068),
                                clock: .continuous,
                            )
                            withAnimation(.easeInOut(duration: 0.068)) { shake = dx }
                        }
                    }
                }

                // Reserved region — see the file header and the audit note.
                //
                // The card is ALWAYS in the tree and hidden with opacity. It used to be wrapped in
                // a bare `if mismatch`, which reserved nothing: an unsatisfied `if` inside a
                // ViewBuilder produces nil, and a frame and a padding wrapped around nil both
                // collapse to zero rather than holding the row open. Measured on an iPhone 17 Pro,
                // the CTA jumped 56pt — the full 42 + 14 — the moment the code was wrong, which is
                // exactly what this region exists to prevent and what SHOWUP-143 forbids outright.
                HStack(alignment: .top, spacing: Spacing.lg) {
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
                .padding(.vertical, Spacing.lg)
                .background(RoundedRectangle(cornerRadius: Radius.errorBox).fill(Color.liqDanger.opacity(0.07)))
                .overlay(RoundedRectangle(cornerRadius: Radius.errorBox)
                    .strokeBorder(Color.liqDanger.opacity(0.18), lineWidth: 1))
                .opacity(mismatch ? 1 : 0)
                // Invisible is not the same as absent: without this VoiceOver would read an error
                // that is not being shown.
                .accessibilityHidden(!mismatch)
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
                    // "Didn't receive a code?" is a LABEL, not a control. It introduces the action
                    // below it and does nothing on its own.
                    //
                    // It was briefly merged with the action into one tap target, which meant
                    // tapping the question sent another SMS — a question that silently spends
                    // money and restarts the cooldown, with nothing on screen to suggest it would.
                    // The reference has it as plain text and the button as a button.
                    let resendLive = mismatch || cooldownSeconds <= 0
                    Text("Didn’t receive a code?")
                        .font(F.manrope(14, .medium))
                        .foregroundColor(.liqMuted)

                    if resendLive {
                        Button(action: onResend) {
                            Text("Send a new code")
                                .font(F.manrope(14, .bold))
                                .foregroundColor(.liqPurple)
                                .underline()
                                // 44pt is Apple's own minimum touch target; the text alone is ~20.
                                .frame(minHeight: 44)
                                .padding(.horizontal, Spacing.xl)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(PressScale())
                    } else {
                        // Inert while cooling, on purpose: the ticket allows no silent resend, so a
                        // tappable label during the cooldown would be either a dead control or a
                        // rule broken. Tabular figures so the countdown does not jitter.
                        Text(String(format: "Send a new code in 0:%02d", cooldownSeconds))
                            .font(F.manrope(14, .semibold))
                            .monospacedDigit()
                            .foregroundColor(.liqMuted)
                            .padding(.vertical, Spacing.xs)
                    }
                    Button(action: onEditNumber) {
                        HStack(spacing: Spacing.sm) {
                            BrandIconView(icon: .pencil, size: 13, stroke: 1.8, tint: .liqMuted)
                            Text("Edit phone number")
                                .font(F.manrope(14, .semibold)).foregroundColor(.liqMuted)
                        }
                        .padding(.horizontal, Spacing.md).padding(.vertical, Spacing.xs)
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
            RoundedRectangle(cornerRadius: Radius.control)
                .fill(mismatch ? Color.liqDanger.opacity(0.04) : Color.liqElevated)
            RoundedRectangle(cornerRadius: Radius.control)
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

#Preview("A · number · 375") { PhoneNumberView(value: .constant("")) }
#Preview("A · number · 390") { PhoneNumberView(value: .constant("17612345678")) }
#Preview("B · invalid · 390") { PhoneNumberView(value: .constant("1761"), error: .tooShort) }
#Preview("C · code · 390") { VerifyCodeView(digits: .constant("")) }
#Preview("D · mismatch · 390") { VerifyCodeView(digits: .constant("482170"), mismatch: true) }
