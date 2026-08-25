//  WelcomeBackView.swift
//  ShowUp · Welcome back — re-login (SHOWUP-142)
//
//  Shown on launch whenever an account already exists on the device; a device with no account opens
//  Startup instead. The two are one visual space — same backdrop values, same wordmark at 26, same
//  position — which is why both take them from WelcomeShell rather than owning a copy.

import SwiftUI

/// Which method this device signed in with last. Device state, never a user setting.
///
/// `.unknown` is a real case, not a defensive default: an unrecognised value falls back to phone as
/// the primary button and hides the hint row entirely, because telling someone they last used a
/// method we are guessing at would be a lie.
enum AuthMethod: CaseIterable { case phone, apple, google, facebook, unknown }

private struct MethodSpec {
    let label: String
    let icon: BrandIcon
    let hint: String
}

/// Canonical order — phone, Apple, Google, Facebook. Never reordered beyond lifting the primary.
private let CANONICAL: [AuthMethod] = [.phone, .apple, .google, .facebook]

private let METHODS: [AuthMethod: MethodSpec] = [
    .phone:    .init(label: "Continue with phone number", icon: .phone,    hint: "Last login was via phone"),
    .apple:    .init(label: "Continue with Apple",        icon: .apple,    hint: "Last login was via Apple"),
    .google:   .init(label: "Continue with Google",       icon: .google,   hint: "Last login was via Google"),
    .facebook: .init(label: "Continue with Facebook",     icon: .facebook, hint: "Last login was via Facebook"),
]

struct WelcomeBackView: View {
    var name: String = "Leo"
    var lastUsed: AuthMethod = .phone
    var onContinue: (AuthMethod) -> Void = { _ in }
    var onGetHelp: () -> Void = {}
    var onUseDifferentAccount: () -> Void = {}
    var onLegal: () -> Void = {}
    var onPrivacy: () -> Void = {}

    private var known: Bool { lastUsed != .unknown }
    private var primary: AuthMethod { known ? lastUsed : .phone }
    private var rest: [AuthMethod] { CANONICAL.filter { $0 != primary } }

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 700

            WelcomeScaffold {
                Wordmark()

                // 120 here against Startup's 132 — same role: the element that yields first.
                Spacer().frame(height: compact ? 60 : 120)

                VStack(alignment: .leading, spacing: 18) {
                    // With no name the headline is "Welcome back" and the italic span is omitted —
                    // an empty em would still paint a wash under nothing.
                    Group {
                        if name.isEmpty {
                            WashHeadline(parts: [("Welcome back", false)], fontSize: 44)
                        } else {
                            WashHeadline(parts: [("Welcome back ", false), (name, true)], fontSize: 44)
                        }
                    }
                    .frame(height: 44 * 1.05 * 2)

                    Text("Sign back in to check your availability and see who’s free today.")
                        .font(F.manrope(17, .medium))
                        .lineSpacing(17 * 0.45)
                        .foregroundColor(.liqNeutral)
                        .frame(maxWidth: 280, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                // The stack floats between two equal spacers — centred in the lower band.
                VStack(spacing: 10) {
                    if known {
                        HStack(spacing: 6) {
                            // 6pt dot with a 3pt ring — the ring sits outside the dot rather than
                            // growing it, so the dot stays 6.
                            Circle().fill(Color.liqOrange).frame(width: 6, height: 6)
                                .overlay(Circle().stroke(Color.liqOrange.opacity(0.18), lineWidth: 3))
                            Text(METHODS[primary]!.hint)
                                .font(F.manrope(12, .semibold))
                                .foregroundColor(.liqMuted)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 2)
                    }

                    // Exactly four buttons, always. Only the last-used one is sunset.
                    PillButton(label: METHODS[primary]!.label, variant: .sunset,
                               action: { onContinue(primary) }) {
                        BrandIconView(icon: METHODS[primary]!.icon, size: 18, tint: .white)
                    }
                    ForEach(rest, id: \.self) { m in
                        PillButton(label: METHODS[m]!.label, variant: .ghost,
                                   action: { onContinue(m) }) {
                            BrandIconView(icon: METHODS[m]!.icon, size: 18, tint: .liqFg)
                        }
                    }
                }
                .padding(.bottom, 12)

                Spacer(minLength: 0)

                // One wrapping paragraph with real links, not an HStack of Buttons — an HStack
                // cannot wrap mid-sentence, so a narrow frame would clip it instead of flowing.
                Text(helpAttributed)
                    .font(F.manrope(12, .medium))
                    .multilineTextAlignment(.center)
                    .tint(.liqFg)
                    .frame(maxWidth: .infinity)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 8)

                // No Terms & Conditions here — consent was given at sign-up.
                Text(legalAttributed)
                    .font(F.manrope(11.5, .medium))
                    .multilineTextAlignment(.center)
                    .tint(.liqMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 8)
            }
        }
        .ignoresSafeArea(.keyboard)
        .environment(\.openURL, OpenURLAction { url in
            switch url.host {
            case "help":    onGetHelp()
            case "switch":  onUseDifferentAccount()
            case "legal":   onLegal()
            case "privacy": onPrivacy()
            default:        break
            }
            return .handled
        })
    }

    private var helpAttributed: AttributedString {
        func plain(_ t: String) -> AttributedString {
            var a = AttributedString(t)
            a.foregroundColor = .liqSubtle
            return a
        }
        func link(_ t: String, _ target: String) -> AttributedString {
            var a = AttributedString(t)
            a.link = URL(string: "showup-legal://" + target)
            a.foregroundColor = .liqFg
            a.font = F.manrope(12, .semibold)
            return a
        }
        var out = plain("Trouble signing in? ")
        out.append(link("Get help", "help"))
        out.append(plain(" or "))
        out.append(link("Use a different account", "switch"))
        return out
    }

    private var legalAttributed: AttributedString {
        func link(_ t: String, _ target: String) -> AttributedString {
            var a = AttributedString(t)
            a.link = URL(string: "showup-legal://" + target)
            a.foregroundColor = .liqMuted
            a.font = F.manrope(11.5, .semibold)
            a.underlineStyle = .single
            return a
        }
        var out = link("Legal Notice", "legal")
        var dot = AttributedString(" · ")
        dot.foregroundColor = .liqSubtle
        out.append(dot)
        out.append(link("Privacy Policy", "privacy"))
        return out
    }
}

#Preview("375 x 667 - iPhone SE") { WelcomeBackView() }
#Preview("390 x 844 - reference") { WelcomeBackView() }
#Preview("430 x 932 - Pro Max") { WelcomeBackView() }
#Preview("lastUsed = Apple") { WelcomeBackView(lastUsed: .apple) }
#Preview("lastUsed = Google") { WelcomeBackView(lastUsed: .google) }
#Preview("lastUsed = Facebook") { WelcomeBackView(lastUsed: .facebook) }
#Preview("lastUsed = unknown") { WelcomeBackView(lastUsed: .unknown) }
#Preview("no name") { WelcomeBackView(name: "") }
#Preview("24-character name") { WelcomeBackView(name: "Maximiliana Konstantina") }
