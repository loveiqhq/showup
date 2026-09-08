//  ConnectAccountView.swift
//  ShowUp · Connect an account — the whole first-time connection flow (SHOWUP-144)
//
//  Ten states out of one view, driven by `state` + `provider` + `kind`, exactly as the ticket
//  requires: no per-provider screen, no per-state route, and the conflict as a modal over the
//  mounted screen rather than a route of its own.
//
//    A  idle          three connect buttons and the skip
//    B  tapped        spinner on the tapped one, siblings dimmed, skip still live
//    C  handoff       Apple's sheet — the system owns the foreground
//    D  handoff       Google's sheet — likewise
//    E  linking       token returned, our backend is linking; capped at 8s
//    F  success       linked, forward into profile creation
//    G  cancelled     the user closed the sheet — neutral, never an error
//    H  error         network or declined
//    I  conflict      the identity belongs to an existing account
//    J  resolve       the owning provider's sheet, in sign-in mode — not ours
//
//  C, D and J are drawn by the OS or the provider SDK. Nothing in this file reproduces them, and
//  nothing assumes their height: they differ per provider, per OS version and per account count.

import SwiftUI

/// The eight view states. The two OS handoffs share one of them; the provider tells them apart.
enum ConnectState { case idle, tapped, handoff, linking, success, cancelled, error, conflict }

/// The two failures the ticket recognises. Anything else is a bug in our handling, not a state.
enum ErrorKind { case network, declined }

/// "Linking must not become a dead end. It has no exit by design, so cap it — 8s — and fall out to
/// error · network rather than spinning on." The ticket flags the number itself as a guess to be
/// instrumented, which is why it is named rather than buried in the task.
let LINKING_TIMEOUT_SECONDS: Double = 8

/// Whether the platform dims the screen for us during an OS handoff.
///
/// On iOS the system draws its own dim behind `ASAuthorizationController` and behind the Google
/// sheet, so ours would be the second layer — and the ticket calls a double scrim a visible bug.
/// The scrim under the **conflict modal is ours** on both platforms, because that modal is ours.
/// That is what the acceptance criterion is really asking: exactly one dim layer in each of the
/// two states, not zero, and not the same owner in both.
let PLATFORM_DIMS_HANDOFF = true

struct ConnectAccountView: View {
    var state: ConnectState = .idle
    var provider: AuthMethod = .apple
    var kind: ErrorKind = .network
    /// From the provider. Nil or blank is a real case — Apple often shares nothing.
    var firstName: String? = "Leo"
    /// A provider whose credential sub-task is not done ships hidden, not disabled.
    var configured: Set<AuthMethod> = Set(CONNECT_METHODS)
    /// Nil when the backend cannot return one; the copy has a separate string for that.
    var conflictEmail: String? = "leo@gmail.com"
    /// The provider that OWNS the existing account — not the one the user just tapped.
    var conflictOwner: AuthMethod = .google
    var onSelect: (AuthMethod) -> Void = { _ in }
    var onSkip: () -> Void = {}
    var onContinue: () -> Void = {}
    var onLinkingTimeout: () -> Void = {}
    var onResolveConflict: (AuthMethod) -> Void = { _ in }
    var onUseDifferentAccount: () -> Void = {}
    var onTerms: () -> Void = {}
    var onPrivacy: () -> Void = {}

    // Idempotency. "Double-tapping a provider creates exactly one link request." The list already
    // disables its buttons once something is in flight, but a second tap can land in the frame
    // before that state arrives, so the latch closes on the first tap and reopens only when the
    // state actually moves.
    @State private var dispatched = false

    private var methods: [AuthMethod] { availableMethods(CONNECT_METHODS, configured: configured) }

    var body: some View {
        ZStack {
            switch state {
            case .linking: LinkingHero(provider: provider)
            case .success: SuccessHero(provider: provider, firstName: firstName, onContinue: onContinue)
            default:
                MethodListLayout(
                    state: state, provider: provider, kind: kind, methods: methods,
                    onSelect: { m in if !dispatched { dispatched = true; onSelect(m) } },
                    onSkip: onSkip, onTerms: onTerms, onPrivacy: onPrivacy
                )
            }

            // Ours only where the platform draws none. During an OS handoff on iOS it does.
            if state == .handoff && !PLATFORM_DIMS_HANDOFF { Scrim() }

            if state == .conflict {
                Scrim()
                ConflictSheet(
                    email: conflictEmail, owner: conflictOwner,
                    onResolve: { onResolveConflict(conflictOwner) },
                    onUseDifferent: onUseDifferentAccount
                )
            }
        }
        .onChange(of: state) { _, _ in dispatched = false }
        // E is the one state with no exit of its own, so it gets a clock.
        .task(id: stateKey) {
            guard state == .linking else { return }
            try? await Task.sleep(nanoseconds: UInt64(LINKING_TIMEOUT_SECONDS * 1_000_000_000))
            guard !Task.isCancelled else { return }
            onLinkingTimeout()
        }
    }

    /// `task(id:)` restarts whenever this changes, which is what cancels a pending timeout as soon
    /// as the flow leaves the linking state or switches provider.
    private var stateKey: String { "\(state)-\(provider)" }
}

/// rgba(20,12,30,0.42), and deliberately not tappable.
///
/// No gesture attached, so taps fall through to nothing — an accidental dismiss would drop the user
/// into a list where every provider they own raises this same modal again.
private struct Scrim: View {
    var body: some View {
        Color(red: 20 / 255, green: 12 / 255, blue: 30 / 255).opacity(0.42)
            .ignoresSafeArea()
            .allowsHitTesting(false)
    }
}

// ─────────────────────────────────────────────────────────────
// A / B / C / D / G / H / I — one column, one method list
// ─────────────────────────────────────────────────────────────
private struct MethodListLayout: View {
    let state: ConnectState
    let provider: AuthMethod
    let kind: ErrorKind
    let methods: [AuthMethod]
    let onSelect: (AuthMethod) -> Void
    let onSkip: () -> Void
    let onTerms: () -> Void
    let onPrivacy: () -> Void

    private var inFlight: Bool { state == .tapped || state == .handoff }
    private var errored: Bool { state == .error }
    private var short: String { methodSpec(provider).short }

    private var errorMessage: String? {
        guard errored else { return nil }
        switch kind {
        case .network:  return "We couldn't reach \(short). Check your connection and try again."
        case .declined: return "\(short) didn't return a valid sign-in. Try again, or use a different method."
        }
    }

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700

            WelcomeScaffold {
                Wordmark()

                // "At 375 x 667 the wordmark -> headline 96 collapses first, then the headline
                // steps 40 -> 34." Those are the only two things allowed to move.
                Spacer().frame(height: compact ? 44 : 96)

                VStack(alignment: .leading, spacing: Spacing.xxl) {
                    // The heart trails the headline INSIDE the text, on the baseline of whatever
                    // line the text ends on -- see WashHeadline's `trailing`. It used to be a
                    // sibling in an HStack, which reserved its width against every line: the
                    // headline wrapped badly and the heart was pushed off the right edge.
                    WashHeadline(
                        parts: [("Welcome to ", false), ("Show Up", true)],
                        fontSize: compact ? 34 : 40,
                        trailing: .heart
                    )

                    // A hard break, not a wrap: the ticket specifies two lines and names the break.
                    Text("Connect an account for easier future sign-ins.\nOr continue and start creating your profile.")
                        .font(F.manrope(16, .medium))
                        .lineSpacing(16 * 0.45)
                        .foregroundColor(.liqNeutral)
                        .frame(maxWidth: 310, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                // ONE spacer, per the reference. The banner and the notice are inserted above the
                // list inside the bottom group, so they eat into this and the buttons keep their
                // distance from the bottom across A, B, G and H.
                Spacer(minLength: 0)

                AuthMethodList(
                    methods: methods,
                    loading: inFlight ? provider : nil,
                    errorFor: errored ? provider : nil,
                    errorMessage: errorMessage,
                    // The tapped provider holds first position through in-flight, cancel and
                    // error, so "try again" is always the same target under the thumb. Idle and
                    // the conflict sit in canonical order — the conflict must not reorder.
                    suggested: (state == .idle || state == .conflict) ? nil : provider,
                    onSelect: onSelect,
                    onSkip: onSkip,
                    notice: {
                        if state == .cancelled { CancelledNotice(provider: provider) }
                    }
                )

                LegalLine(onTerms: onTerms, onPrivacy: onPrivacy)
            }
        }
    }
}

/// "By continuing you agree to our **Terms** and **Privacy Policy**." Both are real links.
private struct LegalLine: View {
    let onTerms: () -> Void
    let onPrivacy: () -> Void

    var body: some View {
        Text(attributed)
            .font(F.manrope(12, .medium))
            .multilineTextAlignment(.center)
            // fg-subtle, as the design system draws it -- see the note in AuthMethodList.
            .foregroundColor(.liqSubtle)
            .tint(.liqFg)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, Spacing.md)
            .environment(\.openURL, OpenURLAction { url in
                if url.absoluteString == "showup://terms" { onTerms() } else { onPrivacy() }
                return .handled
            })
    }

    private var attributed: AttributedString {
        var s = AttributedString("By continuing you agree to our ")
        var terms = AttributedString("Terms")
        terms.link = URL(string: "showup://terms")
        terms.font = F.manrope(12, .semibold)
        terms.foregroundColor = .liqFg
        var mid = AttributedString(" and ")
        mid.foregroundColor = .liqMuted
        var privacy = AttributedString("Privacy Policy")
        privacy.link = URL(string: "showup://privacy")
        privacy.font = F.manrope(12, .semibold)
        privacy.foregroundColor = .liqFg
        s.append(terms); s.append(mid); s.append(privacy); s.append(AttributedString("."))
        return s
    }
}

// ─────────────────────────────────────────────────────────────
// E — linking
// ─────────────────────────────────────────────────────────────
private struct LinkingHero: View {
    let provider: AuthMethod

    var body: some View {
        WelcomeScaffold {
            Wordmark()
            Spacer(minLength: 0)
            VStack(spacing: 24) {
                ZStack {
                    GradientRing()
                    Circle()
                        .fill(Color.liqElevated)
                        .frame(width: 84, height: 84)
                        .shadow(color: Color.liqPurple.opacity(0.10), radius: 6, y: 4)
                        .overlay(
                            BrandIconView(icon: methodSpec(provider).icon, size: 42,
                                          tint: provider == .apple ? .black : .liqFg,
                                          opticalCentre: true)
                        )
                }
                .frame(width: 120, height: 120)

                VStack(spacing: Spacing.md) {
                    WashHeadline(parts: [("Signing you ", false), ("in", true), ("…", false)],
                                 fontSize: 28, lineHeightMultiple: 1.15, trackingEm: -0.015)
                    Text("Verifying your \(linkingSubject(provider)) and setting things up. This takes a second.")
                        .font(F.manrope(15, .medium))
                        .lineSpacing(15 * 0.45)
                        .foregroundColor(.liqMuted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 32)
            }
            .frame(maxWidth: .infinity)
            Spacer(minLength: 0)
            Text("Don't close the app.")
                .font(F.manrope(12, .medium))
                .foregroundColor(.liqMuted)
                .frame(maxWidth: .infinity)
                .padding(.bottom, Spacing.md)
        }
        .accessibilityElement(children: .combine)
    }
}

/// "Verifying your Apple ID" / "your Google account" — the ticket names both forms.
private func linkingSubject(_ m: AuthMethod) -> String {
    switch m {
    case .apple: return "Apple ID"
    case .google: return "Google account"
    case .facebook: return "Facebook account"
    default: return "account"
    }
}

/// 120 box, r 52, 4pt stroke, a 28% arc on the sunset ramp, one turn every 1.4s.
private struct GradientRing: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var spinning = false

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.liqFg.opacity(0.06), lineWidth: 4)
                .frame(width: 104, height: 104)
            Circle()
                .trim(from: 0, to: 0.28)
                .stroke(
                    LinearGradient(colors: [.liqOrange, .liqPurple],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    style: StrokeStyle(lineWidth: 4, lineCap: .round)
                )
                .frame(width: 104, height: 104)
                .rotationEffect(.degrees(spinning ? 360 : 0))
                .animation(reduceMotion ? nil : .linear(duration: Motion.pulseLong).repeatForever(autoreverses: false),
                           value: spinning)
        }
        .onAppear { if !reduceMotion { spinning = true } }
        .accessibilityHidden(true)
    }
}

// ─────────────────────────────────────────────────────────────
// F — success
// ─────────────────────────────────────────────────────────────
private struct SuccessHero: View {
    let provider: AuthMethod
    let firstName: String?
    let onContinue: () -> Void

    var body: some View {
        WelcomeScaffold {
            Wordmark()
            Spacer(minLength: 0)
            VStack(spacing: 28) {
                ZStack {
                    Circle()
                        .fill(RadialGradient(
                            colors: [Color.liqSuccess.opacity(0.22), Color.liqSuccess.opacity(0)],
                            center: .center, startRadius: 0, endRadius: 60))
                        .frame(width: 120, height: 120)
                    Circle()
                        .fill(Color.liqElevated)
                        .frame(width: 88, height: 88)
                        .shadow(color: Color.liqSuccess.opacity(0.18), radius: 12, y: 8)
                        .overlay(
                            BrandIconView(icon: methodSpec(provider).icon, size: 44,
                                          tint: provider == .apple ? .black : .liqFg,
                                          opticalCentre: true)
                        )
                    // The 3pt ring is the page background, not white — it has to disappear into
                    // the canvas rather than read as a second badge outline.
                    Circle()
                        .fill(Color.liqSuccess)
                        .frame(width: 30, height: 30)
                        .overlay(BrandIconView(icon: .check, size: 18, stroke: 2.4, tint: .white))
                        .padding(3)
                        .background(Circle().fill(Color.liqCream))
                        // right 4 / bottom 4 inside the 120 box, per the reference — not flush.
                        // 60 (centre) + 38 = 98, + 18 (half the badge) = 116, i.e. 4 from 120.
                        .offset(x: 38, y: 38)
                }
                .frame(width: 120, height: 120)

                VStack(spacing: Spacing.lg) {
                    StatusBadge(label: "\(methodSpec(provider).short) connected")
                    // No name from the provider is a real case, not a defensive default: Apple's
                    // private-relay users often share nothing. The italic run still has to be the
                    // emphasis, so the whole sentence changes shape rather than the name being
                    // replaced by an empty span — "You're in, null." is the bug this prevents.
                    WashHeadline(parts: headlineParts, fontSize: 32,
                                 lineHeightMultiple: 1.1, trackingEm: -0.015)
                    Text("We'll never post or message anyone on your behalf. Let's finish your profile in 90 seconds.")
                        .font(F.manrope(15, .medium))
                        .lineSpacing(15 * 0.45)
                        .foregroundColor(.liqMuted)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 280)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.screenGutter)
            Spacer(minLength: 0)
            HStack {
                Spacer()
                NextButton(label: "Continue", variant: .sunset, action: onContinue)
            }
            .padding(.bottom, 18)
        }
    }

    private var headlineParts: [(String, Bool)] {
        let name = (firstName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { return [("You're ", false), ("in", true), (".", false)] }
        return [("You're ", false), ("in", true), (", \(name).", false)]
    }
}

/// Orange tone: bg orange 12%, orange text, a 5pt dot, Manrope 700 11 uppercase, tracking .08.
// ─────────────────────────────────────────────────────────────
// I — the conflict modal
// ─────────────────────────────────────────────────────────────
/// Bottom-anchored and content-sized. Never given a height and never centred, so a long address
/// grows it upward instead of clipping, scrolling or truncating.
///
/// It renders over the mounted screen rather than replacing it, which is what keeps the method list
/// behind it identical before and after a dismiss: nothing re-runs, because nothing unmounted, and
/// no navigation entry is created.
private struct ConflictSheet: View {
    let email: String?
    let owner: AuthMethod
    let onResolve: () -> Void
    let onUseDifferent: () -> Void

    private var short: String { methodSpec(owner).short }

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                // Orange, not danger. Nothing failed here — the account simply exists.
                BrandIconView(icon: .shield, size: 26, stroke: 1.8, tint: .liqOrange)
                    .frame(width: IconSizes.badge, height: IconSizes.badge)
                    .background(Color.liqOrange.opacity(0.12))
                    .clipShape(Circle())
                    .padding(.bottom, Spacing.xxl)

                // One plain run, so no wash: there is no emphasis phrase in this headline, and an
                // empty em would paint a glow under nothing.
                WashHeadline(parts: [("You already have an account.", false)], fontSize: 26,
                             lineHeightMultiple: 1.15, trackingEm: -0.015)
                    .padding(.bottom, Spacing.lg)

                Text(bodyAttributed)
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.45)
                    .foregroundColor(.liqNeutral)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, Spacing.md)

                // The product reason, not boilerplate — it is why no second account is offered.
                // Verbatim, per the ticket.
                Text("One person, one account. Show-up Rates only work if you can't start over.")
                    .font(F.manrope(13, .medium))
                    .lineSpacing(13 * 0.4)
                    .foregroundColor(.liqMuted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 22)

                // Names the OWNING provider, not the one the user tapped: tapping Apple on a
                // Google-owned account offers Continue with Google. Getting this backwards sends
                // the user round a loop. It wears that provider's own button, because the ticket
                // lists this CTA alongside the three on the method list.
                PrimaryButton(
                    label: methodSpec(owner).label,
                    variant: providerVariant(owner),
                    height: 54,
                    action: onResolve,
                    leading: {
                        BrandIconView(icon: methodSpec(owner).icon, size: 18,
                                      tint: providerTint(owner))
                    }
                )
                .padding(.bottom, Spacing.md)

                // No border, so the pair never reads as two equal choices. This is the only
                // dismiss: there is no close icon and the scrim above does not accept taps.
                PrimaryButton("Use a different account", variant: .plain, height: 50,
                           action: onUseDifferent)
            }
            .padding(.horizontal, Spacing.screenGutter)
            .padding(.top, 26)
            .padding(.bottom, 22)
            .background(Color.liqElevated)
            .clipShape(RoundedRectangle(cornerRadius: Radius.pill, style: .continuous))
            .shadow(color: Color.liqPurple.opacity(0.28), radius: 30, y: 20)
        }
        .padding(8)
    }

    private var bodyAttributed: AttributedString {
        // Both values are interpolated. With no address the sentence changes shape rather than
        // rendering an empty bold span or a masked placeholder — naming the account is the modal's
        // whole job, so a blank there is worse than a different sentence.
        guard let email, !email.isEmpty else {
            return AttributedString(
                "That account is already on Show Up — you signed up with \(short). "
                + "Continue with \(short) to pick up where you left off.")
        }
        var addr = AttributedString(email)
        addr.font = F.manrope(15, .bold)
        addr.foregroundColor = .liqFg
        var s = AttributedString("")
        s.append(addr)
        s.append(AttributedString(
            " is already on Show Up — you signed up with \(short). "
            + "Continue with \(short) to pick up where you left off."))
        return s
    }
}

// ─────────────────────────────────────────────────────────────
// Previews — every state the ticket wants evidence for.
//
// The flow is also walkable end to end in the simulator via ConnectFlowHost in ShowUpWelcomeApp,
// which cycles through a different ending on each attempt.
// ─────────────────────────────────────────────────────────────
#Preview("A idle") { ConnectAccountView() }
#Preview("B tapped - Apple") { ConnectAccountView(state: .tapped) }
#Preview("B tapped - Google") { ConnectAccountView(state: .tapped, provider: .google) }
#Preview("E linking") { ConnectAccountView(state: .linking) }
#Preview("F success") { ConnectAccountView(state: .success) }
#Preview("F success - no name") { ConnectAccountView(state: .success, firstName: nil) }
#Preview("G cancelled") { ConnectAccountView(state: .cancelled) }
#Preview("H error - network") { ConnectAccountView(state: .error) }
#Preview("H error - declined") { ConnectAccountView(state: .error, provider: .google, kind: .declined) }
#Preview("I conflict") { ConnectAccountView(state: .conflict) }
#Preview("I conflict - 38-char address") {
    ConnectAccountView(state: .conflict, conflictEmail: "leonhard.schwarzkopf@studio-mantis.com")
}
#Preview("I conflict - no address") { ConnectAccountView(state: .conflict, conflictEmail: nil) }
#Preview("A idle - Facebook cut from v1") { ConnectAccountView(configured: [.apple, .google]) }
