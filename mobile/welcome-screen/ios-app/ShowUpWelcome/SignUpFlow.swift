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

/// What the device remembers about the last person to sign in on it.
///
/// Nil means nobody — a fresh install, or after "Use a different account" cleared it. That is a
/// real state, not a missing value, and it decides two things: which screen launch opens, and
/// whether Welcome back can greet anyone by name.
struct RememberedAccount {
    let name: String
    let lastUsed: AuthMethod
}

struct SignUpFlowView: View {
    /// Nil on a device that has never been signed in on, which is where a new install starts.
    ///
    /// SHOWUP-140: "Renders on first launch only. If a device already has an account, the app
    /// opens Welcome back instead." This is that condition.
    var remembered: RememberedAccount? = nil
    /// Where the flow leaves the user. SHOWUP-146: `.newAccount` is shown the tutorial,
    /// `.returningMember` goes straight into the app.
    var onFinished: (SignUpOutcome) -> Void = { _ in }
    var onOpenLegal: (String) -> Void = { _ in }

    @State private var step: Step
    @State private var country: Country = DEFAULT_COUNTRY
    @State private var phoneDigits = ""
    @State private var phoneError: PhoneError?
    @State private var showCountrySheet = false

    @State private var codeDigits = ""
    @State private var codeMismatch = false
    @State private var cooldown = DevAuth.resendCooldown

    @State private var account: RememberedAccount?

    /// SHOWUP-146. Which door the user came through decides, at the far end, whether they are
    /// shown the tutorial. A launch straight onto Welcome back is a log-in by definition -- the
    /// device would not remember anyone otherwise.
    @State private var entry: Entry

    init(remembered: RememberedAccount? = nil,
         onFinished: @escaping (SignUpOutcome) -> Void = { _ in },
         onOpenLegal: @escaping (String) -> Void = { _ in }) {
        self.remembered = remembered
        self.onFinished = onFinished
        self.onOpenLegal = onOpenLegal
        _step = State(initialValue: remembered != nil ? .welcomeBack : .startup)
        _account = State(initialValue: remembered)
        _entry = State(initialValue: remembered != nil ? .logIn : .createAccount)
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
                    onCreateAccount: { entry = .createAccount; step = .phone },
                    onLogin: { entry = .logIn; step = .welcomeBack },
                    onTerms: { onOpenLegal("Terms & Conditions") },
                    onPrivacy: { onOpenLegal("Privacy Policy") },
                    onLegalNotice: { onOpenLegal("Legal Notice") },
                    // The dates figure is hidden until the number is worth showing — the minimum
                    // is still to be decided, so the toggle is off rather than the figure invented.
                    showSocialProof: false
                )

            case .welcomeBack:
                WelcomeBackView(
                    // Empty name and .unknown when the device remembers nobody — which is exactly
                    // what tapping "Log in" on Startup means: someone who has an account but not
                    // on THIS device. The view already handles it: the headline drops to a plain
                    // "Welcome back" with no name, and the "last login was via…" hint disappears
                    // rather than claiming a method that never happened here.
                    name: account?.name ?? "",
                    lastUsed: account?.lastUsed ?? .unknown,
                    onContinue: { method in
                        // Phone is the one method that goes anywhere: it is ours, and 143 is built.
                        // The three providers are live targets with nothing behind them yet — their
                        // SDK work is the sub-tasks on SHOWUP-144.
                        if method == .phone {
                            // Reaching this screen at all means logging in, whether the user
                            // tapped "Log in" on Startup or the app opened here on a remembered
                            // device.
                            entry = .logIn
                            phoneError = nil
                            step = .phone
                        }
                    },
                    onGetHelp: { onOpenLegal("Get help") },
                    // Clears the remembered account and returns to Startup, per SHOWUP-142.
                    // Clearing it is the point -- coming back here afterwards must not still know
                    // the old name.
                    onUseDifferentAccount: { account = nil; step = .startup },
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
                            // SHOWUP-146. Connect (SHOWUP-144) belongs to account creation: it is
                            // where a brand-new account is offered a provider to link. Someone
                            // signing back in has been past it already, so they skip both it and
                            // the tutorial and land in the app.
                            if entry == .logIn {
                                onFinished(outcomeOf(entry, nil))
                            } else {
                                step = .connect
                            }
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

            // SHOWUP-144. Its own host drives the ten states. Every one of its exits leaves the
            // sign-up flow; SHOWUP-146 decides which of the two destinations it leaves for.
            case .connect:
                ConnectFlowHost(onDone: { onFinished(outcomeOf(entry, $0)) })
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
