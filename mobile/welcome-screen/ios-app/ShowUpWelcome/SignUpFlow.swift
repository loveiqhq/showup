//  SignUpFlow.swift
//  ShowUp · the walkable sign-up flow — Startup → phone → code → connect (SHOWUP-140/142/143/144)
//
//  This is the piece that turns five rendered screens into something a person can actually walk
//  through. Every screen stays pure: it takes values and emits events. All the state lives here.
//
//  NOTHING HERE IS A STAND-IN ANY MORE. This file used to open with a scaffolding notice: the
//  code was a constant, the resend restarted a local timer, and a `DevAuth` enum held both so they
//  were one edit to remove. That edit has happened.
//
//    - The code is real. `/auth/phone/start` asks the backend to send one and
//      `/auth/phone/verify` confirms it, returning a JWT pair written to the Keychain.
//    - The resend really sends, and the countdown counts to the server's `resendAvailableAt`
//      rather than down from a number this file chose.
//
//  No SMS provider is involved and none is needed. `LogSmsSender` is the only sender the backend
//  has; it writes the code to the server log, and `AUTH_EXPOSE_OTP` — on unless NODE_ENV is
//  production — also returns it on the challenge, which is what the debug-only strip displays.
//  A complete signup is walkable for nothing, and switching a paid provider on later changes one
//  binding in `auth.module.ts` and nothing in this file.

import SwiftUI

/// Where the user is. One flat enum — this flow has no nesting and no side routes.
private enum Step: String { case startup, welcomeBack, phone, code, connect }

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
    /// The asynchronous half of this flow: sending a code, confirming it, and the countdown.
    ///
    /// Optional ONLY so previews and ScreenFitTests can render every screen without a backend.
    /// A nil model cannot sign anyone in — it renders the same screens with a default state and
    /// inert actions, which is what a preview should be. The app always passes one.
    var auth: PhoneAuthModel? = nil
    var onOpenLegal: (String) -> Void = { _ in }
    /// Where events go. NoOp by default, so nothing is sent and the flow behaves identically
    /// whether or not analytics is switched on -- which is also what makes it testable.
    var analytics: any AnalyticsTracking = NoOpAnalytics()

    // ── restored with the scene ──────────────────────────────────────────────
    //
    // SceneStorage, not State: backgrounded on the code screen and killed, this flow used to come
    // back at Startup with an empty field. Android has survived that since it was written --
    // rememberSaveable is the default idiom there -- and iOS had no restoration at all.
    //
    // Scene-scoped and NOT @AppStorage, deliberately. This is where the user is right now, not a
    // preference: @AppStorage would restore a half-typed phone number into a scene that had been
    // properly closed, weeks later.
    //
    // Keys are prefixed because SceneStorage is one flat namespace per scene, and two views
    // storing under a bare "step" would silently share a value.

    @SceneStorage("signup.step") private var stepRaw: String = ""
    @SceneStorage("signup.entry") private var entryRaw: String = ""
    @SceneStorage("signup.countryISO") private var countryISO: String = ""
    @SceneStorage("signup.phoneDigits") private var phoneDigits: String = ""
    @SceneStorage("signup.codeDigits") private var codeDigits: String = ""
    // The countdown, the attempt count and the mismatch flag belong to the model now, because
    // every one of them is decided by a server response rather than by this view. They were
    // @SceneStorage until the flow started talking to a backend; scene state that outlives the
    // challenge it describes is worse than no state, because a restored scene would show a
    // countdown against a code the server has already forgotten.
    private var cooldown: Int { auth?.cooldownSeconds ?? 0 }
    private var codeMismatch: Bool { auth?.lastSubmitRefused ?? false }
    private var verifyAttempts: Int { auth?.attempts ?? 0 }

    /// The three derived values. A `@SceneStorage` default cannot be computed, and two of these
    /// depend on `remembered` -- so the empty string means "nothing stored yet" and the fallback
    /// is exactly the initial value the initialiser used to set.
    private var step: Step {
        Step(rawValue: stepRaw) ?? (remembered != nil ? .welcomeBack : .startup)
    }

    /// SHOWUP-146. Which door the user came through decides, at the far end, whether they are
    /// shown the tutorial. A launch straight onto Welcome back is a log-in by definition -- the
    /// device would not remember anyone otherwise.
    private var entry: Entry {
        Entry(storageKey: entryRaw) ?? (remembered != nil ? .logIn : .createAccount)
    }

    /// Stored as an ISO code, which is what Android's `CountrySaver` writes too.
    private var country: Country {
        countryISO.isEmpty ? DEFAULT_COUNTRY : countryForRegion(countryISO)
    }

    /// Every step change goes through here, so the raw value is the only thing that is written and
    /// the nine call sites still read as `go(to: .phone)`.
    private func go(to next: Step) { stepRaw = next.rawValue }

    // ── not restored, on purpose ─────────────────────────────────────────────
    //
    // A stale validation error and a re-opened modal are both worse than their absence: the error
    // describes an edit the user may not have finished, and a sheet restoring itself is a screen
    // the user never chose to open. Android does save `phoneError` and `showCountrySheet`; that
    // divergence is deliberate and recorded in the architecture notes.
    @State private var phoneError: PhoneError?
    @State private var showCountrySheet = false

    @State private var account: RememberedAccount?

    init(remembered: RememberedAccount? = nil,
         onFinished: @escaping (SignUpOutcome) -> Void = { _ in },
         onOpenLegal: @escaping (String) -> Void = { _ in }) {
        self.remembered = remembered
        self.onFinished = onFinished
        self.onOpenLegal = onOpenLegal
        // step and entry are no longer seeded here: they are scene-backed, and their fallbacks
        // reproduce exactly what these two lines used to set.
        _account = State(initialValue: remembered)
    }

    /// The number as it is shown back to the user on the code screen.
    private var fullNumber: String { "\(country.dial) \(formatNational(phoneDigits, country))" }

    // No clock here any more. `PhoneAuthModel` recomputes the countdown from the server's
    // `resendAvailableAt` once a second, so a device that slept through half the window wakes up
    // with the right number rather than one that was decremented while it was asleep.

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
                        entryRaw = Entry.createAccount.storageKey
                        go(to: .phone)
                    },
                    onLogin: {
                        analytics.track(SignUpAnalytics.logInTapped, properties: [:])
                        entryRaw = Entry.logIn.storageKey
                        go(to: .welcomeBack)
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
                            entryRaw = Entry.logIn.storageKey
                            phoneError = nil
                            go(to: .phone)
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
                        go(to: .startup)
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
                    onBack: { go(to: .startup) },
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
                            // Advance only once the server has accepted the request. Moving
                            // first would put the user on a code screen waiting for an SMS
                            // that was never dispatched.
                            auth?.start(phoneE164: country.e164(phoneDigits)) { go(to: .code) }
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
                            auth?.clearRefusal()
                        }
                    ),
                    mismatch: codeMismatch,
                    lockedOut: verifyAttempts >= maxVerifyAttempts,
                    cooldownSeconds: cooldown,
                    // Back and "Edit phone number" are the same journey, so they behave
                    // identically: return to A with the number intact, per SHOWUP-143.
                    onBack: { go(to: .phone) },
                    onVerify: {
                        // Locked out: the server would refuse this, so the client does not ask.
                        // The CTA is already disabled in that state; this is the second guard,
                        // for a submit arriving from the keyboard's action key.
                        guard verifyAttempts < maxVerifyAttempts else { return }
                        analytics.track(SignUpAnalytics.codeSubmitted, properties: [:])
                        auth?.verify(phoneE164: country.e164(phoneDigits),
                                     code: codeDigits) { profileComplete in
                            // SHOWUP-146, decided by the PROFILE rather than by what the user
                            // said they were doing. Connect and the tutorial belong to building
                            // an account; someone whose profile is already complete has been
                            // past both, whichever button they tapped to get here.
                            //
                            // Completeness rather than an "is this account new" flag because it
                            // survives an interrupted signup: a user who quit halfway through
                            // profile creation is not new, but must not be sent to Home.
                            if profileComplete {
                                onFinished(outcomeOf(.logIn, nil))
                            } else {
                                go(to: .connect)
                            }
                        }
                    },
                    onResend: {
                        // SHOWUP-143 wants how long the user waited and whether this followed
                        // a mismatch. The cooldown counts DOWN, so the time actually waited is
                        // the difference.
                        //
                        // `afterMismatch` is READ FROM THE MODEL rather than from a flag this
                        // view maintains. It was a flag, and nothing ever set it to true, so the
                        // property shipped as a constant false that no test could see. The count
                        // cannot drift the same way: the server increments it and `start` resets
                        // it, so "attempts against this challenge" is true by construction.
                        track(SignUpAnalytics.resendRequested(
                            secondsWaited: resendCooldownSeconds - cooldown,
                            afterMismatch: verifyAttempts > 0))
                        codeDigits = ""
                        // A new code is a new challenge; the model resets the attempt count and
                        // adopts the server's fresh resendAvailableAt.
                        auth?.start(phoneE164: country.e164(phoneDigits))
                    },
                    onEditNumber: {
                        analytics.track(SignUpAnalytics.editPhoneTapped, properties: [:])
                        go(to: .phone)
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
                    countryISO = $0.iso
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
            //
            // Only when nothing was stored. Unguarded, this runs on a restore too and overwrites
            // the country the user picked, which would make storing it pointless -- a saved value
            // that is always immediately replaced.
            //
            // Android's LaunchedEffect(Unit) had the same shape and the same defect: CountrySaver
            // restored the country and the effect replaced it one line later. Guarded there too
            // now, with `localeDefaultApplied`, and covered by CountryRestorationTest.
            if countryISO.isEmpty {
                countryISO = countryForRegion(Locale.current.region?.identifier).iso
            }
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
    }

    // ── dev strip ────────────────────────────────────────────────────────────
    //
    // Shows the code the SERVER generated, not a constant the app invented. It exists because
    // LogSmsSender is the only sender the backend has and AUTH_EXPOSE_OTP returns the code
    // outside production — so a signup is testable with no paid provider and without reading
    // server logs.
    //
    // THREE conditions, each closing a different leak: the nil check keeps it absent when a
    // server chooses not to expose it, the step check keeps it off every other screen, and
    // `showDevStrip` is false in any release build.
    /// False in any release build.
    ///
    /// A computed constant rather than an `#if` wrapped around the ViewBuilder branch: a
    /// conditional-compilation block inside a view body changes what the body RETURNS between
    /// configurations, which is how a debug-only view ends up altering release layout. This way
    /// both builds compile the same view and one of them never shows it.
    private var showDevStrip: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    @ViewBuilder private var devStrip: some View {
        if showDevStrip, let devCode = auth?.devCode, step == .code {
            HStack(spacing: Spacing.sm) {
                Text("TEST BUILD")
                    .font(F.manrope(9, .bold))
                    .tracking(0.7)
                    .foregroundColor(Color(hex: 0xFFAE8F))
                Text("logged, not sent · the code is \(devCode)")
                    .font(F.manrope(11, .medium))
                    .foregroundColor(.white)
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.vertical, 5)
            .background(Capsule().fill(Color(hex: 0x1D1129).opacity(0.90)))
            .padding(.top, Spacing.xs)
        }
    }
}
