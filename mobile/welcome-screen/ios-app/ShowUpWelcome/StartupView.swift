//  StartupView.swift
//  ShowUp · Startup — first run (SHOWUP-140)
//
//  The first screen a new visitor sees. Once an account exists on the device, launch opens Welcome
//  back instead; that routing decision belongs to the flow, not to either screen.
//
//  Built to welcome/screen-startup-reference.jsx (numbers) and SHOWUP-140 (behaviour, copy).
//  TWO flex:1 spacers, not one — the social-proof row floats optically centred between them.

import SwiftUI

struct StartupView: View {
    var onCreateAccount: () -> Void = {}
    var onLogin: () -> Void = {}
    var onTerms: () -> Void = {}
    var onPrivacy: () -> Void = {}
    var onLegalNotice: () -> Void = {}
    /// The dates figure is dynamic and gated: SHOWUP-140 requires a toggle, because the claim only
    /// appears once enough dates have actually been organised. It defaults OFF -- the minimum has
    /// not been decided, and shipping a number nobody has agreed to would be inventing it.
    var showSocialProof: Bool = false

    @Environment(\.verticalSizeClass) private var vSize

    var body: some View {
        WelcomeScaffold {
            Wordmark()

            // 132 is the only large fixed gap and the element that yields on short frames — never
            // the type, never a button height, never the CTA's safe-area margin.
            //
            // A RANGE, not a height behind a device-size test. It was `compact ? 72 : 132` keyed on
            // `geo.size.height < 700`, and that was wrong in both directions: it yielded at 375x667,
            // where the handoff says 132 still holds and the two spacers collapse to about 20 each,
            // and it is a hard-coded Y decision of exactly the kind the spec's last line forbids.
            // Given a range the layout resolves it: the gap takes 132 wherever there is room and
            // gives it back, toward 64, where there is not. Measured across the device matrix in
            // ScreenFitTests rather than reasoned about -- 320x686 and 360x640 are the two frames
            // that actually need it, and both overflowed at a fixed 132.
            Spacer().frame(minHeight: 64, maxHeight: 132)

            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Stop texting for days.")
                        .font(.custom(PS.loraBold, size: 32))
                        .tracking(-0.015 * 32)
                        .foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                    WashHeadline(
                        parts: [("Start ", false), ("meeting", true), (" today.", false)],
                        fontSize: 44
                    )
                }
                Text("Your availability. Your intent. Your date — today or tomorrow.")
                    .font(F.manrope(19, .semibold))
                    .lineSpacing(19 * 0.4)
                    .foregroundColor(.liqFg)
                    .frame(maxWidth: 320, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)

            if showSocialProof {
                HStack(spacing: Spacing.md) {
                    BrandIconView(icon: .calendar, size: 16, stroke: 2, tint: .liqOrange)
                    (Text("234.000 Dates").font(F.manrope(13, .bold)).foregroundColor(.liqFg)
                     + Text(" already organized").font(F.manrope(13, .medium)).foregroundColor(.liqSubtle))
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 14)
            }

            Spacer(minLength: 0)

            // The three phrases ship as real links with their own hit areas.
            legalLine
                .padding(.bottom, 14)

            PillButton("Create free account", action: onCreateAccount)
            Spacer().frame(height: 10)

            // The reference's own hit area is ~31; the ticket requires at least 44 without
            // changing the 14pt type, so the frame carries the target.
            Button(action: onLogin) {
                (Text("Already have an account? ").foregroundColor(.liqSubtle)
                 + Text("Log in").foregroundColor(.liqPurple).underline())
                    .font(F.manrope(14, .semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressScale())
            Spacer().frame(height: 14)
        }
        .ignoresSafeArea(.keyboard)
    }

    /// Three real tappable links, each with its own hit area (SHOWUP-140).
    ///
    /// AttributedString links rather than three Buttons in an HStack: the sentence has to wrap as
    /// one paragraph, and an HStack cannot wrap mid-sentence. `openURL` intercepts the taps, so
    /// these stay in-app actions — the scheme is deliberately never registered with the system.
    private var legalLine: some View {
        Text(legalAttributed)
            .font(F.manrope(12, .medium))
            .lineSpacing(12 * 0.45)
            .multilineTextAlignment(.center)
            .tint(.liqFg)                 // link colour; SwiftUI would use accentColor otherwise
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
            .environment(\.openURL, OpenURLAction { url in
                switch url.host {
                case "terms":   onTerms()
                case "privacy": onPrivacy()
                case "legal":   onLegalNotice()
                default:        break
                }
                return .handled
            })
    }

    private var legalAttributed: AttributedString {
        func plain(_ t: String) -> AttributedString {
            var a = AttributedString(t)
            // Muted (62%, 5.03:1), not Subtle (46%, 3.04:1) — Subtle fails WCAG 2.1 AA and this
            // is the sentence where the user accepts the Terms. See audit finding 7.
            a.foregroundColor = .liqMuted
            return a
        }
        func link(_ t: String, _ target: String) -> AttributedString {
            var a = AttributedString(t)
            a.link = URL(string: "showup-legal://" + target)
            a.foregroundColor = .liqFg
            a.font = F.manrope(12, .semibold)
            return a
        }
        var out = plain("By creating an account, you agree to our ")
        out.append(link("Terms & Conditions", "terms"))
        out.append(plain(" and acknowledge that you have read our "))
        out.append(link("Privacy Policy", "privacy"))
        out.append(plain(". See our "))
        out.append(link("Legal Notice", "legal"))
        out.append(plain("."))
        return out
    }
}

#Preview("375 x 667 - iPhone SE") { StartupView() }
#Preview("390 x 844 - reference") { StartupView() }
#Preview("430 x 932 - Pro Max") { StartupView() }
#Preview("social proof off") { StartupView(showSocialProof: false) }
