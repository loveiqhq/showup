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
    /// Where events go. NoOp by default, so nothing is sent and the flow behaves identically
    /// whether or not analytics is switched on -- which is also what makes it testable.
    var analytics: any AnalyticsTracking = NoOpAnalytics()

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

    /// Tracking-only state. SHOWUP-143 wants the attempt number on a failed verify and whether a
    /// resend followed a mismatch; neither is derivable from the view's own state, because the
    /// mismatch flag clears the moment the user edits a digit.
    @State private var verifyAttempts = 0
    @State private var lastVerifyFailed = false

    /// Reports an event built by the SignUpAnalytics catalogue.
    private func track(_ pair: (String, [String: any Sendable])) {
        analytics.track(pair.0, properties: pair.1)
    }

    /// The screenview for a step. SHOWUP-142 asks for the lastUsed value on Welcome back,
    /// including `unknown`; SHOWUP-144 asks for one screenview with a `state`, not ten.
    private func trackScreen(for step: Step) {
        switch step {
        case .startup:
            track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.createAccount))
        case .welcomeBack:
            track(SignUpAnalytics.screenViewed(
                SignUpAnalytics.Screen.welcomeBack,
                ["last_used": account.map { String(describing: $0.lastUsed) } ?? "unknown"]
            ))
        case .phone:
            track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.phoneNumber))
        case .code:
            track(SignUpAnalytics.screenViewed(SignUpAnalytics.Screen.codeEntry))
        case .connect:
            track(SignUpAnalytics.screenViewed(
                SignUpAnalytics.Screen.connectSSO, ["state": "idle"]
            ))
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            switch step {
            case .startup:
                StartupView(
                    onCreateAccount: {
                        analytics.track(SignUpAnalytics.createAccountTapped, properties: [:])
                        entry = .createAccount
                        step = .phone
                    },
                    onLogin: {
                        analytics.track(SignUpAnalytics.logInTapped, properties: [:])
                        entry = .logIn
                        step = .welcomeBack
                    },
                    onTerms: {
                        track(SignUpAnalytics.legalLinkTapped(
                            SignUpAnalytics.Legal.terms,
                            screen: SignUpAnalytics.Screen.createAccount))
                        onOpenLegal("Terms & Conditions")
                    },
                    onPrivacy: {
                        track(SignUpAnalytics.legalLinkTapped(
                            SignUpAnalytics.Legal.privacy,
                            screen: SignUpAnalytics.Screen.createAccount))
                        onOpenLegal("Privacy Policy")
                    },
                    onLegalNotice: {
                        track(SignUpAnalytics.legalLinkTapped(
                            SignUpAnalytics.Legal.legalNotice,
                            screen: SignUpAnalytics.Screen.createAccount))
                        onOpenLegal("Legal Notice")
                    },
                    // On, because this target is the preview the spec sheet is reviewed against and
                    // the sheet draws the row. The figure itself is the sheet's own placeholder --
                    // "234.000" is not a measured number, and the sheet says so. The toggle exists
                    // for exactly that reason: in the real app it stays OFF until the count is real,
                    // because a fabricated statistic on a first-run screen is a claim, not a mock.
                    showSocialProof: true
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
                        // SHOWUP-142 wants `method` and `is_last_used` on every auth-method tap,
                        // including the three providers that go nowhere yet -- the intent to use
                        // them is exactly what the funnel needs to know.
                        track(SignUpAnalytics.authMethodTapped(
                            method: String(describing: method),
                            isLastUsed: account?.lastUsed == method))
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
                    onGetHelp: {
                        analytics.track(SignUpAnalytics.getHelpTapped, properties: [:])
                        onOpenLegal("Get help")
                    },
                    // Clears the remembered account and returns to Startup, per SHOWUP-142.
                    // Clearing it is the point -- coming back here afterwards must not still know
                    // the old name.
                    onUseDifferentAccount: {
                        analytics.track(SignUpAnalytics.useDifferentAccountTapped, properties: [:])
                        account = nil
                        step = .startup
                    },
                    onLegal: {
                        track(SignUpAnalytics.legalLinkTapped(
                            SignUpAnalytics.Legal.legalNotice,
                            screen: SignUpAnalytics.Screen.welcomeBack))
                        onOpenLegal("Legal Notice")
                    },
                    onPrivacy: {
                        track(SignUpAnalytics.legalLinkTapped(
                            SignUpAnalytics.Legal.privacy,
                            screen: SignUpAnalytics.Screen.welcomeBack))
                        onOpenLegal("Privacy Policy")
                    }
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
                        analytics.track(SignUpAnalytics.phoneSubmitted, properties: [:])
                        let problem = validate(phoneDigits, country)
                        phoneError = problem
                        if let problem {
                            // Our outcome, not the ticket's three-value vocabulary -- see the note
                            // on phoneValidationFailed. Reporting a reason the code cannot produce
                            // would describe something that did not happen.
                            track(SignUpAnalytics.phoneValidationFailed(
                                reason: String(describing: problem),
                                country: country.iso))
                        }
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
                        analytics.track(SignUpAnalytics.codeSubmitted, properties: [:])
                        verifyAttempts += 1
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
                            lastVerifyFailed = true
                            track(SignUpAnalytics.codeVerifyFailed(attempt: verifyAttempts))
                            // A mistyped code must not cost another wait — the ticket says the
                            // mismatch releases the cooldown, so the resend is live immediately.
                            cooldown = 0
                        }
                    },
                    onResend: {
                        // SHOWUP-143 wants how long the user waited and whether this followed a
                        // mismatch. The cooldown counts DOWN, so the time actually waited is the
                        // difference -- and after a mismatch it is released to 0, which would
                        // otherwise read as a full wait.
                        track(SignUpAnalytics.resendRequested(
                            secondsWaited: DevAuth.resendCooldown - cooldown,
                            afterMismatch: lastVerifyFailed))
                        lastVerifyFailed = false
                        codeDigits = ""
                        codeMismatch = false
                        cooldown = DevAuth.resendCooldown
                    },
                    onEditNumber: {
                        analytics.track(SignUpAnalytics.editPhoneTapped, properties: [:])
                        step = .phone
                    }
                )

            // SHOWUP-144. Its own host drives the ten states. Every one of its exits leaves the
            // sign-up flow; SHOWUP-146 decides which of the two destinations it leaves for.
            case .connect:
                ConnectFlowHost(
                    onDone: { onFinished(outcomeOf(entry, $0)) },
                    onOpenLegal: onOpenLegal,
                    analytics: analytics
                )
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
            // The first screenview. onChange does not fire for the initial value, so without this
            // the funnel would be missing its entry step -- and a funnel missing its first step
            // reads as though nobody ever started.
            trackScreen(for: step)
        }
        // Two-parameter onChange: the single-parameter form is deprecated from iOS 17, which is
        // this app's minimum. See CLAUDE.md on availability.
        .onChange(of: step) { _, newStep in
            trackScreen(for: newStep)
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
