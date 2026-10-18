//
//  CheckInNoHoursDialog.swift
//  ShowUp · Check-In 02's "No hours today?" confirm (SHOWUP-179)
//
//  The Swift twin of `NoHoursTodayDialog` in `checkin/AvailabilityChrome.kt`. Modal, the Check-In's
//  only overlay: a scrim (rgba(29,17,41,.44), fading in 200 ms) and a centred card (max 320, radius
//  26, padding 26 / 24 / 20, popping in 260 ms on the brand curve: up 10, from .97). No close. The
//  scrim, `Cancel, pick hours today`, the edge swipe and VoiceOver's escape all close it and stay
//  on Today. The screen behind blurs 3 with the scrim (the caller, `CheckInAvailabilityView`).
//
//  ONE STATE PER TRANSITION, as on Android: the scrim and the card each have their own, because one
//  shared state driving two transitions left both stuck on a real phone there.
//
//  THE CARD NEVER RUNS OFF THE SCREEN: at the largest type on a small phone it is taller than the
//  frame and its two buttons were crushed below the 44 floor. Capped 24 short of each edge, its
//  content scrolls inside it instead.
//
//  MODAL FOR VOICEOVER TOO: the card is a modal element named as the reference names it, and the
//  screen behind is hidden from VoiceOver while it is up (the caller).
//

import SwiftUI

struct NoHoursTodayDialog: View {
    let onConfirm: () -> Void
    let onCancel: () -> Void
    let onDismiss: () -> Void
    @State private var scrimIn = false
    @State private var cardIn = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Color.liqDialogScrim
                .ignoresSafeArea()
                .opacity(scrimIn ? 1 : 0)
                .contentShape(Rectangle())
                .onTapGesture(perform: onDismiss)
                .accessibilityHidden(true)

            // Both views of the card, and the one that fits wins: the card at its own height, or --
            // only when that is taller than the frame allows -- the same card scrolling inside it.
            ViewThatFits(in: .vertical) {
                card
                ScrollView(.vertical, showsIndicators: false) { card }
                    .scrollBounceBehavior(.basedOnSize)
            }
            .frame(maxWidth: 320)
            .background(Color.liqElevated)
            .clipShape(RoundedRectangle(cornerRadius: 26))
            // `0 24px 60px` -- CSS's blur is twice SwiftUI's shadow radius. After the clip, so the
            // shadow is the card's and the clip does not cut it off.
            .compositingGroup()
            .shadow(color: .liqDialogShadow, radius: 30, y: 24)
            .padding(.horizontal, 26)
            .padding(.vertical, 24)
            .opacity(cardIn ? 1 : 0)
            .scaleEffect(cardIn ? 1 : 0.97)
            .offset(y: cardIn ? 0 : 10)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text(CheckInCopy.noHoursDialogLabel))
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape, onDismiss)
        }
        .onAppear {
            if reduceMotion {
                scrimIn = true
                cardIn = true
            } else {
                withAnimation(Motion.cssEase(Motion.reveal)) { scrimIn = true }
                withAnimation(Motion.brand(Motion.pop)) { cardIn = true }
            }
        }
    }

    private var card: some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: Radius.card).fill(Gradients.lilac())
                BrandIconView(icon: .calendar, size: 24, stroke: 1.9, tint: .liqPurple)
            }
            .frame(width: 52, height: 52)
            .accessibilityHidden(true)

            Color.clear.frame(height: Spacing.xxl)
            // A plain `<em>`: italic in the title's own 700, no wash.
            WashHeadline(parts: CheckInCopy.noHoursTitle, fontSize: 23, lineHeightMultiple: 1.15,
                         trackingEm: -0.01, centered: true, wash: false, cssLineBox: true)

            PrettyText(CheckInCopy.noHoursBody,
                       font: TypeMetrics.uiFont(PS.manropeMedium, 14, fallback: .systemFont(ofSize: 14, weight: .medium)),
                       lineHeight: 14 * 1.5, color: .liqNeutral, centered: true)
                .padding(.top, Spacing.lg)

            Color.clear.frame(height: 18)
            PrimaryButton(label: CheckInCopy.noHoursConfirm, variant: .sunset, action: onConfirm,
                          trailing: { CheckInArrow() })
            Color.clear.frame(height: Spacing.lg)
            Button(action: onCancel) {
                Text(CheckInCopy.noHoursCancel)
                    .font(F.manrope(15, .bold))
                    .foregroundColor(.liqPurple)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .contentShape(Capsule())
            }
            .buttonStyle(PressScale())
        }
        .padding(.horizontal, Spacing.screenGutter)
        .padding(.top, 26)
        .padding(.bottom, 20)
    }
}
