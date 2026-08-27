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

// MethodSpec, the canonical order and the provider treatments now live in AuthMethodList.swift,
// shared with the Connect screen. SHOWUP-145 requires exactly that: "The method list uses the same
// component and the same ordered data source as welcome 04."

struct WelcomeBackView: View {
    var name: String = "Leo"
    var lastUsed: AuthMethod = .phone
    /// Which providers have finished credential setup. The rest are hidden, not greyed out.
    var configured: Set<AuthMethod> = Set(LOGIN_METHODS)
    var onContinue: (AuthMethod) -> Void = { _ in }
    var onGetHelp: () -> Void = {}
    var onUseDifferentAccount: () -> Void = {}
    var onLegal: () -> Void = {}
    var onPrivacy: () -> Void = {}

    // A provider whose credential setup is not finished is hidden, not disabled (SHOWUP-145).
    // With all four configured this is still exactly four buttons, which is the other rule.
    private var methods: [AuthMethod] { availableMethods(LOGIN_METHODS, configured: configured) }
    private var known: Bool { lastUsed != .unknown && methods.contains(lastUsed) }
    private var primary: AuthMethod { known ? lastUsed : (methods.first ?? .phone) }

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
                            Text(methodSpec(primary).hint)
                                .font(F.manrope(12, .semibold))
                                .foregroundColor(.liqMuted)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 2)
                    }

                    // The same component and the same ordered data source as Connect — SHOWUP-145.
                    // No skip here: Welcome back has no way past sign-in.
                    AuthMethodList(methods: methods, suggested: primary, onSelect: onContinue)
                }

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
            a.foregroundColor = .liqMuted      // see audit finding 7
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
        dot.foregroundColor = .liqMuted
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
