//
//  CheckInChrome.swift
//  ShowUp · what every Check-In screen shares (SHOWUP-178 to SHOWUP-183)
//
//  The Swift twin of `checkin/CheckInChrome.kt` and `checkin/CheckInFlexBody.kt`. BUILT ONCE,
//  CONSUMED BY ALL -- "a second copy of any of these is a bug" (epic):
//    · CheckInCloseButton, CheckInArrow, NumberedRow ............. Check-In 01
//    · the step shell, CheckInFooter (+ its toast), the flex body . Check-In 02
//    · CoachNote .................................................. Check-In 03
//    · CheckInFooter's `loading` and `error`, CheckInSpinner ...... Check-In 06 (a variant, not a fork)
//
//  Numbers from `design_handoff_showup/checkin/screen-checkin-*-reference.jsx`, which win on
//  numbers. The error card is the profile flow's shared `InlineErrorCard` -- story 06: "reuse it if
//  the build already has it shared".
//

import SwiftUI

/// `CheckInArrow` -- every Check-In CTA's trailing arrow: the design system's own `arrow-right`, 18
/// at stroke 2, "the same SVG as Embrace's CTA". Never a text glyph (Check-In 01).
struct CheckInArrow: View {
    var tint: Color = .white

    var body: some View {
        BrandIconView(icon: .arrowRight, size: 18, stroke: 2, tint: tint)
            .accessibilityHidden(true)
    }
}

/// `CheckInCloseButton` -- the close on every Check-In screen: `x` 22 at stroke 2 in a 36 slot,
/// leading on the intro and trailing on the steps. Answers at the 44 floor like the header's back,
/// overflowing its 36 symmetrically so nothing beside it moves.
struct CheckInCloseButton: View {
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            BrandIconView(icon: .close, size: 22, stroke: 2, tint: .liqFg)
                .frame(width: ComponentSizes.minTapTarget, height: ComponentSizes.minTapTarget)
                .contentShape(Circle())
        }
        .buttonStyle(PressScale())
        .frame(width: 36, height: 36)
        .accessibilityLabel(Text(CheckInCopy.closeLabel))
    }
}

/// `NumberedRow` -- the intro's list row: a 26 orange disc with a white numeral, an element and not
/// a list-style, beside Manrope 600 / 15 / 1.4.
struct NumberedRow: View {
    let n: Int
    let text: String

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            // The disc is a fixed 26 element and `aria-hidden` in the reference: the numeral is drawn
            // at 13 whatever the type size -- a 2x numeral does not fit a 26 disc -- and VoiceOver
            // reads the row's text alone.
            Text("\(n)")
                .font(.custom(PS.manropeBold, fixedSize: 13))
                .foregroundColor(.white)
                .frame(width: 26, height: 26)
                .background(
                    Circle()
                        .fill(Color.liqOrange)
                        // `0 2px 6px rgba(254,104,57,.28)` -- a tint of the disc's own orange.
                        .shadow(color: Color.liqOrange.opacity(0.28), radius: 3, y: 2)
                )
                .accessibilityHidden(true)
            Text(text)
                .font(F.manrope(15, .semibold))
                .foregroundColor(.liqFg)
                .lineSpacing(TypeMetrics.lineSpacing(PS.manropeSemi, size: 15, multiple: 1.4))
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// A step headline: Lora 700 / 30 / 1.15 / -0.015em, one em with the wash, `textWrap: 'pretty'` and
/// the CSS line box. "30 is the step size."
struct CheckInStepHeadline: View {
    let parts: [(String, Bool)]

    var body: some View {
        WashHeadline(parts: parts, fontSize: 30, lineHeightMultiple: 1.15, trackingEm: -0.015,
                     pretty: true, cssLineBox: true, heading: true)
    }
}

/// `CoachNote` -- the guidance box. Warm on Motivation, Vibe and Filters; lilac on Uplift (and the
/// colours How it works' panel shares). 10 / 14 padding, radius 12, Manrope 500 / 12.5 / 1.45.
struct CoachNote: View {
    let text: String
    let warm: Bool

    var body: some View {
        Text(text)
            .font(F.manrope(12.5, .medium))
            .foregroundColor(warm ? .liqCoachWarmInk : .liqCoachLilacInk)
            .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 12.5, multiple: 1.45))
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, Spacing.lg)
            .background(
                RoundedRectangle(cornerRadius: Radius.errorBox)
                    .fill(warm ? Color.liqCoachWarmFill : Color.liqCoachLilacFill)
            )
    }
}

/// The step shell -- built by Check-In 02, consumed by 03 to 06 with content props only:
/// AppHeader 52 (`Check-in`, back leading, close trailing) → content (padding 4 / 24 / 0:
/// StepProgress `steps={6}` mb 22 → `body` flexible → `footer`).
///
/// The keyboard pushes the footer up (SwiftUI's keyboard safe area), so `Find my date` sits
/// directly above it. The edge swipe is the chevron. `ambient` is Uplift's backdrop -- the one step
/// with it, "the flow's moment to shine".
struct CheckInStepShell<Footer: View, Content: View>: View {
    let step: CheckInStep
    let onBack: () -> Void
    let onClose: () -> Void
    var ambient: Bool = false
    @ViewBuilder let footer: () -> Footer
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            if ambient {
                // Orange .30 520² top −15% right −25% · violet .26 600² bottom −20% left −30% · peach wash.
                WelcomeBackdrop(placement: .startup, orangeAlpha: 0.30, violetAlpha: 0.26)
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
            }
            VStack(spacing: 0) {
                AppHeader(title: CheckInCopy.header, leading: .back, onBack: onBack,
                          trailing: AnyView(CheckInCloseButton(onClick: onClose)))
                VStack(alignment: .leading, spacing: 0) {
                    StepProgress(steps: checkInStepsTotal, current: step.progress ?? 0)
                    Color.clear.frame(height: 22)
                    content()
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    footer()
                }
                .padding(.horizontal, Spacing.screenGutter)
                .padding(.top, Spacing.xs)
            }
        }
        .modifier(EdgeSwipeBack(onBack: onBack))
    }
}

/// The least the flexible region is ever given: two slot rows, or a motivation row and a half.
/// Android's `REGION_FLOOR`.
let checkInRegionFloor: CGFloat = 80

/// A Check-In step body: a header block over the ONE flexible region, with a floor -- Android's
/// `CheckInFlexBody`; its header has the argument.
///
/// Every step from 02 to 05 is the same column: a header block (`flex: none`) over one region that
/// is `flex: 1, min-height: 0, overflow-y: auto`. The region takes whatever height is left and
/// scrolls inside itself; the header never moves. When the header leaves less than the floor -- How
/// it works open on a 375 x 667, or the largest type on a 320 -- the region keeps the floor and the
/// header and the region scroll TOGETHER, and what sits below this body stays where it is.
struct CheckInFlexBody<Header: View, Region: View>: View {
    @ViewBuilder let header: () -> Header
    @ViewBuilder let region: () -> Region

    var body: some View {
        // The viewport, read OUTSIDE the scroll: inside it the height is unbounded.
        GeometryReader { geo in
            ScrollView(.vertical, showsIndicators: false) {
                FlexBodyLayout(viewport: geo.size.height, floor: checkInRegionFloor) {
                    VStack(alignment: .leading, spacing: 0) { header() }
                    // One subview whatever the region holds: the layout places exactly two, and a
                    // region of several views would flatten into more and draw nothing. Android's
                    // `Box { region() }`.
                    VStack(alignment: .leading, spacing: 0) { region() }
                }
                .frame(width: geo.size.width)
            }
            // A body that fits does not rubber-band: it reads as a fixed header over a list.
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

/// The header at its natural height, the region given exactly what is left -- never less than the
/// floor.
private struct FlexBodyLayout: Layout {
    let viewport: CGFloat
    let floor: CGFloat

    private func heights(_ width: CGFloat, _ subviews: Subviews) -> (header: CGFloat, region: CGFloat) {
        let head = subviews.first?.sizeThatFits(ProposedViewSize(width: width, height: nil)).height ?? 0
        return (head, max(floor, viewport - head))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let h = heights(width, subviews)
        return CGSize(width: width, height: h.header + h.region)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let h = heights(bounds.width, subviews)
        subviews[0].place(at: bounds.origin, anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: h.header))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + h.header), anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: h.region))
    }
}

/// `CheckInFooter` -- the full-width sunset CTA with `CheckInArrow`, and above it the toast or the
/// save-failed card. Margin-bottom 22.
///
/// DIMMED IS NOT DISABLED. A step that cannot continue dims the button (opacity .45, saturate .4,
/// 180 ms) and still takes the tap, which raises the toast naming what is missing -- "never a dead
/// button". `PrimaryButton`'s own `enabled: false` would refuse the tap, so the dim is drawn here
/// around an enabled button, over the button AND its glow, as CSS's `filter` on the wrapper is.
///
/// `loading` is Check-In 06's saving state: the spinner replaces the arrow, the label stays, the
/// button is inert. `error` is its save-failed card, shown until the next tap or edit, never with a
/// toast. `toastTick` restarts the toast's entry on a repeat tap.
struct CheckInFooter: View {
    let label: String
    let onClick: () -> Void
    var dimmed: Bool = false
    var toast: String? = nil
    var toastTick: Int = 0
    var loading: Bool = false
    var error: String? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            if let toast {
                CheckInToast(text: toast)
                    .id(toastTick)
                Color.clear.frame(height: Spacing.xl)
            } else if let error {
                InlineErrorCard(message: Text(error))
                    // `role="alert"`: announced the moment it appears, ahead of anything queued --
                    // Android's assertive live region. The card itself announces nothing.
                    .onAppear {
                        var alert = AttributedString(error)
                        alert.accessibilitySpeechAnnouncementPriority = .high
                        AccessibilityNotification.Announcement(alert).post()
                    }
                Color.clear.frame(height: Spacing.xl)
            } else {
                EmptyView()
            }
            PrimaryButton(label: label, variant: .sunset, action: { if !loading { onClick() } },
                          trailing: {
                              if loading { CheckInSpinner() } else { CheckInArrow() }
                          })
                .saturation(dimmed ? 0.4 : 1)
                .opacity(dimmed ? 0.45 : 1)
                .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: dimmed)
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 22)
    }
}

/// `su-toast-in` for an element that appears: 200 ms, up 6, on the brand curve. Android's `Reveal`.
struct CheckInReveal: ViewModifier {
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 6)
            .onAppear {
                if reduceMotion {
                    shown = true
                } else {
                    withAnimation(Motion.brand(Motion.reveal)) { shown = true }
                }
            }
    }
}

/// The footer's toast: rgba(29,17,41,.92), radius 10, padding 8 / 14, max 280, Manrope 600 / 12.5 /
/// 1.35, centred and `text-wrap: pretty`; `0 6px 20px` at .18. Enters with `su-toast-in`. Announced
/// politely, once per showing.
struct CheckInToast: View {
    let text: String

    var body: some View {
        PrettyText(text,
                   font: TypeMetrics.uiFont(PS.manropeSemi, 12.5, fallback: .systemFont(ofSize: 12.5, weight: .semibold)),
                   lineHeight: 12.5 * 1.35, color: .white, centered: true, hugs: true)
            .padding(.horizontal, 14)
            .padding(.vertical, Spacing.md)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.liqCheckInToastFill)
                    .shadow(color: .liqCheckInToastShadow, radius: 10, y: 6)
            )
            .frame(maxWidth: 280)
            .modifier(CheckInReveal())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(text))
            .onAppear {
                var announcement = AttributedString(text)
                announcement.accessibilitySpeechAnnouncementPriority = .default
                AccessibilityNotification.Announcement(announcement).post()
            }
    }
}

/// `CheckInSpinner` -- the commit's saving state, in place of the arrow: an 18 ring, 2 wide, white
/// at .35 with a white top quarter, one turn per 800 ms, linear. Still under reduce motion: a frozen
/// ring reads as busy without movement.
struct CheckInSpinner: View {
    @State private var turning = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(0.35), lineWidth: 2)
            // `border-top-color` on a round box: the top quarter, from -135 to -45 degrees.
            Circle()
                .trim(from: 0.625, to: 0.875)
                .stroke(Color.white, style: StrokeStyle(lineWidth: 2, lineCap: .butt))
                .rotationEffect(.degrees(turning ? 360 : 0))
                .animation(reduceMotion ? nil : .linear(duration: Motion.spin).repeatForever(autoreverses: false),
                           value: turning)
        }
        .padding(1)
        .frame(width: 18, height: 18)
        .onAppear { if !reduceMotion { turning = true } }
        .accessibilityHidden(true)
    }
}
