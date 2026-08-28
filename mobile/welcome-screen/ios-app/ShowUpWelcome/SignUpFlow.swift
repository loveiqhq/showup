//  SignUpFlow.swift
//  ShowUp · the walkable sign-up flow — Startup → phone → code → connect (SHOWUP-140/142/143/144)
//
//  This is the piece that turns five rendered screens into something a person can actually walk
//  through. Every screen stays pure: it takes values and emits events. All the state lives here.
//
//  DEV SCAFFOLDING, CLEARLY MARKED. Two things in this file are stand-ins for services that do not
//  exist yet, and both are gathered into `DevAuth` so they are one edit to remove:
//
//    1. The verification code is fixed at `DevAuth.testCode`. Twilio is not connected, so no SMS is
//       sent and no server checks anything. The code is shown on screen in a dev strip, because a
//       test flow you cannot get through is not a test flow.
//    2. The resend "sends" nothing. It restarts the cooldown, which is the only visible behaviour.
//
//  Nothing else here is fake. The typing, the validation, the country list, the error states, the
//  cooldown timer, the routing and the back behaviour are all real and all survive Twilio landing.

import SwiftUI

/// Everything that stands in for a backend. Delete this and the compiler finds every use.
enum DevAuth {
    /// The code that "works" until Twilio is wired up.
    ///
    /// Six digits, because SHOWUP-143 specifies six and the slots are built for six. Deliberately
    /// not 123456: that is the first thing anyone tries by accident, and it would hide the mismatch
    /// state — which is a state we need to be able to demonstrate.
    static let testCode = "480726"

    /// Seconds before a resend is offered. Real cooldown, fake send.
    static let resendCooldown = 30

    /// Set false to hide the on-screen hint without removing the fixed code.
    static let showHint = true
}

/// Where the user is. One flat enum — this flow has no nesting and no side routes.
private enum Step { case startup, welcomeBack, phone, code, connect }

struct SignUpFlowView: View {
    /// Startup on a fresh device, Welcome back when an account is remembered.
    var startAtWelcomeBack: Bool = false
    var onFinished: () -> Void = {}
    var onOpenLegal: (String) -> Void = { _ in }

    @State private var step: Step
    @State private var country: Country = DEFAULT_COUNTRY
    @State private var phoneDigits = ""
    @State private var phoneError: PhoneError?
    @State private var showCountrySheet = false

    @State private var codeDigits = ""
    @State private var codeMismatch = false
    @State private var cooldown = DevAuth.resendCooldown

    init(startAtWelcomeBack: Bool = false,
         onFinished: @escaping () -> Void = {},
         onOpenLegal: @escaping (String) -> Void = { _ in }) {
        self.startAtWelcomeBack = startAtWelcomeBack
        self.onFinished = onFinished
        self.onOpenLegal = onOpenLegal
        _step = State(initialValue: startAtWelcomeBack ? .welcomeBack : .startup)
    }

    /// The number as it is shown back to the user on the code screen.
    private var fullNumber: String { "\(country.dial) \(formatNational(phoneDigits, country))" }

    /// One ticking clock for the resend, live only while the code screen is up.
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack(alignment: .top) {
            switch step {
            case .startup:
                StartupView(
                    // The dates figure is hidden until the number is worth showing — the minimum
                    // is still to be decided, so the toggle is off rather than the figure invented.
                    showSocialProof: false,
                    onCreateAccount: { step = .phone },
                    onLogin: { step = .welcomeBack },
                    onTerms: { onOpenLegal("Terms & Conditions") },
                    onPrivacy: { onOpenLegal("Privacy Policy") },
                    onLegalNotice: { onOpenLegal("Legal Notice") }
                )

            case .welcomeBack:
                WelcomeBackView(
                    onContinue: { method in
                        // Phone is the one method that goes anywhere: it is ours, and 143 is built.
                        // The three providers are live targets with nothing behind them yet — their
                        // SDK work is the sub-tasks on SHOWUP-144.
                        if method == .phone {
                            phoneError = nil
                            step = .phone
                        }
                    },
                    onGetHelp: { onOpenLegal("Get help") },
                    // Clears the remembered account and returns to Startup, per SHOWUP-142.
                    onUseDifferentAccount: { step = .startup },
                    onLegal: { onOpenLegal("Legal Notice") },
                    onPrivacy: { onOpenLegal("Privacy Policy") }
                )

            case .phone:
                PhoneNumberView(
                    value: Binding(
                        get: { phoneDigits },
                        set: { new in
                            phoneDigits = new
                            // Validation runs on submit, so a rejected number clears its error the
                            // moment the user starts fixing it rather than nagging while they type.
                            phoneError = nil
                        }
                    ),
                    country: country,
                    error: phoneError,
                    onBack: { step = .startup },
                    onSubmit: {
                        let problem = validate(phoneDigits, country)
                        phoneError = problem
                        if problem == nil {
                            codeDigits = ""
                            codeMismatch = false
                            cooldown = DevAuth.resendCooldown
                            step = .code
                        }
                    },
                    onOpenCountryList: { showCountrySheet = true }
                )

            case .code:
                VerifyCodeView(
                    phone: fullNumber,
                    digits: Binding(
                        get: { codeDigits },
                        set: { new in
                            codeDigits = new
                            codeMismatch = false
                        }
                    ),
                    mismatch: codeMismatch,
                    cooldownSeconds: cooldown,
                    // Back and "Edit phone number" are the same journey, so they behave
                    // identically: return to A with the number intact, per SHOWUP-143.
                    onBack: { step = .phone },
                    onVerify: {
                        if codeDigits == DevAuth.testCode {
                            step = .connect
                        } else {
                            codeMismatch = true
                            // A mistyped code must not cost another wait — the ticket says the
                            // mismatch releases the cooldown, so the resend is live immediately.
                            cooldown = 0
                        }
                    },
                    onResend: {
                        codeDigits = ""
                        codeMismatch = false
                        cooldown = DevAuth.resendCooldown
                    },
                    onEditNumber: { step = .phone }
                )

            // SHOWUP-144. Its own host drives the ten states; a resolved conflict or a skip both
            // leave the sign-up flow entirely.
            case .connect:
                ConnectFlowHost(onDone: onFinished)
            }

            devStrip
        }
        .sheet(isPresented: $showCountrySheet) {
            CountrySheet(
                current: country,
                onPick: {
                    country = $0
                    // The old number was validated against the old country's rules, so the verdict
                    // no longer means anything. Clearing it is honest; keeping it would show an
                    // error naming the wrong country.
                    phoneError = nil
                    showCountrySheet = false
                },
                onDismiss: { showCountrySheet = false }
            )
        }
        .onAppear {
            // The country pill defaults from device locale — SHOWUP-143 asks for exactly this, and
            // the region the platform reports is the same ISO key the table is built on.
            country = countryForRegion(Locale.current.region?.identifier)
        }
        .onReceive(tick) { _ in
            if step == .code && cooldown > 0 { cooldown -= 1 }
        }
    }

    // ── dev strip ────────────────────────────────────────────────────────────
    // Only on the code screen, only while the code is fixed. Goes away with DevAuth.
    @ViewBuilder private var devStrip: some View {
        if DevAuth.showHint && step == .code {
            HStack(spacing: 6) {
                Text("TEST BUILD")
                    .font(F.manrope(9, .bold))
                    .tracking(0.7)
                    .foregroundColor(Color(hex: 0xFFAE8F))
                Text("no SMS is sent · the code is \(DevAuth.testCode)")
                    .font(F.manrope(11, .medium))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 5)
            .background(Capsule().fill(Color(hex: 0x1D1129).opacity(0.90)))
            .padding(.top, 4)
        }
    }
}
