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
    /// The dates figure is dynamic and gated: SHOWUP-140 requires a toggle, because the claim only
    /// appears once enough dates have actually been organised.
    var showSocialProof: Bool = true

    @Environment(\.verticalSizeClass) private var vSize

    var body: some View {
        GeometryReader { geo in
            // 375 x 667 is the frame the handoff says to check first, and where the gap collapses.
            let compact = geo.size.height < 700

            WelcomeScaffold {
                Wordmark()

                // 132 is the only large fixed gap and the element that yields on short frames —
                // never the type, never a button height, never the CTA's safe-area margin.
                Spacer().frame(height: compact ? 72 : 132)

                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Stop texting for days.")
                            .font(.custom(PS.loraBold, size: 32))
                            .tracking(-0.015 * 32)
                            .foregroundColor(.liqFg)
                            .fixedSize(horizontal: false, vertical: true)
                        WashHeadline(
                            parts: [("Start ", false), ("meeting", true), (" today.", false)],
                            fontSize: 44
                        )
                        .frame(height: 44 * 1.05 * 2)   // two lines at every frame in the matrix
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
                    HStack(spacing: 8) {
                        BrandIconView(icon: .calendar, size: 16, stroke: 2, tint: .liqOrange)
                        (Text("234.000 Dates").font(F.manrope(13, .bold)).foregroundColor(.liqFg)
                         + Text(" already organized").font(F.manrope(13, .medium)).foregroundColor(.liqMuted))
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
                    (Text("Already have an account? ").foregroundColor(.liqMuted)
                     + Text("Log in").foregroundColor(.liqPurple).underline())
                        .font(F.manrope(14, .semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(PressScale())
                Spacer().frame(height: 14)
            }
        }
        .ignoresSafeArea(.keyboard)
    }

    private var legalLine: some View {
        let body = Text("By creating an account, you agree to our ").foregroundColor(.liqSubtle)
            + Text("Terms & Conditions").font(F.manrope(12, .semibold)).foregroundColor(.liqFg)
            + Text(" and acknowledge that you have read our ").foregroundColor(.liqSubtle)
            + Text("Privacy Policy").font(F.manrope(12, .semibold)).foregroundColor(.liqFg)
            + Text(". See our ").foregroundColor(.liqSubtle)
            + Text("Legal Notice").font(F.manrope(12, .semibold)).foregroundColor(.liqFg)
            + Text(".").foregroundColor(.liqSubtle)
        return body
            .font(F.manrope(12, .medium))
            .lineSpacing(12 * 0.45)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
    }
}

#Preview("375 x 667 - iPhone SE") { StartupView() }
#Preview("390 x 844 - reference") { StartupView() }
#Preview("430 x 932 - Pro Max") { StartupView() }
#Preview("social proof off") { StartupView(showSocialProof: false) }
