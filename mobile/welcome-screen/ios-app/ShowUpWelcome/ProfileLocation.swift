//
//  ProfileLocation.swift
//  ShowUp · Profile 12 — Location permission ask (SHOWUP-165)
//
//  The Swift twin of `ProfileLocationScreen.kt`; its header has the full argument. Three states,
//  one component: A (ask), B (denied), C (the phone's Location Services off). 09's two named
//  exceptions — the ambient backdrop and the full-width sunset CTA — and nothing new.
//
//  THE CTA SITS AT THE SAME Y IN ALL THREE STATES: the footer is pinned at the bottom of the
//  scaffold in every state, and the content above it is top-anchored with one flexible spacer.
//
//  THE TICKET'S ORDER OF SACRIFICE, as `ViewThatFits`: the full design, then the 120 radar, then the
//  10 row gap as well — the first that fits is drawn — and past the last, a scroll under the pinned
//  CTA, 09's recorded precedent (E20), so the button is never pushed off screen.
//

import SwiftUI

/// Every string on the screen, verbatim from the ticket — the iOS column of its platform table.
enum LocationCopy {
    static let aLead = "Find people ", aEm = "nearby", aTail = "."
    static let bLead = "We can't find dates ", bEm = "without", bTail = " location."
    static let cLead = "Your phone has location ", cEm = "switched off", cTail = "."

    static let row1 = "Set a search radius around your location"
    static let row2 = "We pick a fair halfway venue for you both"
    static let row2Sub = "No planning, no home advantage — a busy public spot that's neutral ground for both of you."
    static let row3 = "Walking directions on the day"

    static let privacy = "Your exact location is never shown on your profile. Other people only see the city "
        + "you're in and an approximate distance from themselves e.g. 1.5km."

    static let allow = "Allow location access"
    static let openSettings = "Open Settings"
    static let notNow = "Not now — ask me when I search"

    static let bBody = "Location access is off for Show Up. Without it we can't show you anyone nearby — "
        + "every date happens in the real world. "
    static let bPathLead = "Turn it on in "
    static let bPath = "Settings › Show Up › Location"
    static let pathTail = ", or finish your profile first and we'll ask again when you start searching."

    static let cBody = "Location Services is off for every app on this phone, not just Show Up. "
    static let cPath = "Settings › Privacy & Security › Location Services"
}

/// The ticket's order of sacrifice, as levels.
private enum LocationFit: CaseIterable {
    case full, compactRadar, tight
    var radar: CGFloat { self == .full ? 160 : 120 }
    var rowGap: CGFloat { self == .tight ? 10 : 14 }
}

private struct LocationBenefit: Identifiable {
    let icon: BrandIcon
    let title: String
    var sub: String? = nil
    var id: String { title }
}

private let locationBenefits = [
    LocationBenefit(icon: .compass, title: LocationCopy.row1),
    LocationBenefit(icon: .mapPin, title: LocationCopy.row2, sub: LocationCopy.row2Sub),
    LocationBenefit(icon: .navigation, title: LocationCopy.row3),
]

struct ProfileLocationView: View {
    var state: LocationState = .ask
    var requesting: Bool = false
    var onAllow: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var onNotNow: () -> Void = {}

    var body: some View {
        WelcomeScaffold(
            topPadding: 0,
            gutter: 28,
            footer: {
                VStack(spacing: Spacing.lg) {
                    if state == .ask {
                        PrimaryButton(LocationCopy.allow, variant: .sunset,
                                      action: { if !requesting { onAllow() } })
                    } else {
                        PrimaryButton(LocationCopy.openSettings, variant: .sunset, action: onOpenSettings)
                        // The longest label on any button: a third line at the largest type on the
                        // narrowest phone beats a clipped sentence.
                        PrimaryButton(LocationCopy.notNow, variant: .ghost, labelLineLimit: 3,
                                      action: onNotNow)
                    }
                }
                .padding(.bottom, Spacing.sm)
            }
        ) {
            ViewThatFits(in: .vertical) {
                column(.full)
                column(.compactRadar)
                column(.tight)
                ScrollView(.vertical, showsIndicators: false) { column(.tight) }
            }
        }
        // No chevron, no swipe-back — as on 09 and 10.
        .navigationBarBackButtonHidden(true)
    }

    /// The content at one sacrifice level, ending in the one flexible spacer (min 12).
    private func column(_ fit: LocationFit) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if state == .ask {
                // The radar block: `padding: 16px 28px 0`, then the hero's own 8 top margin.
                LocationRadar(height: fit.radar)
                    .padding(.top, Spacing.xxl + Spacing.md)
            }
            WashHeadline(parts: headlineParts, fontSize: 32, lineHeightMultiple: 1.1, trackingEm: -0.015,
                         balance: true)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, state == .ask ? Spacing.xl : 56)

            if state == .ask {
                VStack(alignment: .leading, spacing: fit.rowGap) {
                    ForEach(locationBenefits) { LocationBenefitRow(benefit: $0) }
                }
                .padding(.top, Spacing.xs + 22)
                PrivacyNote().padding(.top, 22)
            } else {
                recoveryBody
                    .font(F.manrope(15, .medium))
                    .lineSpacing(15 * 0.55)
                    .foregroundColor(.liqNeutral)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.xxl)
            }
            Spacer(minLength: Spacing.xl)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var headlineParts: [(String, Bool)] {
        switch state {
        case .ask: return [(LocationCopy.aLead, false), (LocationCopy.aEm, true), (LocationCopy.aTail, false)]
        case .denied: return [(LocationCopy.bLead, false), (LocationCopy.bEm, true), (LocationCopy.bTail, false)]
        case .servicesOff:
            return [(LocationCopy.cLead, false), (LocationCopy.cEm, true), (LocationCopy.cTail, false)]
        }
    }

    /// B's and C's body, the Settings path in bold — read inline, not as a link.
    private var recoveryBody: Text {
        let bold = F.manrope(15, .bold)
        if state == .servicesOff {
            return Text(LocationCopy.cBody + LocationCopy.bPathLead)
                + Text(LocationCopy.cPath).font(bold)
                + Text(LocationCopy.pathTail)
        }
        return Text(LocationCopy.bBody + LocationCopy.bPathLead)
            + Text(LocationCopy.bPath).font(bold)
            + Text(LocationCopy.pathTail)
    }
}

/// One benefit row, centre-aligned as the reference sets it. One group for VoiceOver.
private struct LocationBenefitRow: View {
    let benefit: LocationBenefit

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Gradients.lilac())
                BrandIconView(icon: benefit.icon, size: IconSizes.sm, stroke: 1.8, tint: .liqPurple)
            }
            .frame(width: 40, height: 40)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 0) {
                // Lora 700 / 17 / 1.25, -0.005em, `text-wrap: balance`.
                WashHeadline(parts: [(benefit.title, false)], fontSize: 17, lineHeightMultiple: 1.25,
                             trackingEm: -0.005, balance: true)
                if let sub = benefit.sub {
                    Text(sub)
                        // 400 in the reference; the bundle carries no Regular cut, so this resolves
                        // to Medium, as Compose's nearest-weight match does on Android.
                        .font(F.manrope(13.5, .regular))
                        .lineSpacing(13.5 * 0.35)
                        // `rgba(0,0,0,0.62)` in the reference, carried as the warm ink at the same
                        // weight — "no pure black" is a brand rule.
                        .foregroundColor(.liqMuted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 3)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// The privacy note: a lavender-washed card with a 16 lock.
private struct PrivacyNote: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            BrandIconView(icon: .lock, size: 16, stroke: 1.8, tint: .liqPurple)
                .padding(.top, 1)
                .accessibilityHidden(true)
            Text(LocationCopy.privacy)
                .font(F.manrope(12.5, .medium))
                .lineSpacing(12.5 * 0.45)
                .foregroundColor(.liqNeutral)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, Spacing.xl)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .circular).fill(Color.liqPrivacyNoteWash))
    }
}

/// The radar: three rings, four nearby dots and the sunset pin, on the reference's 240 x 160 canvas,
/// scaled as a picture when the first sacrifice shrinks it to 120. Static. Decorative.
private struct LocationRadar: View {
    let height: CGFloat

    var body: some View {
        Canvas { ctx, size in
            let u = size.height / 160
            let origin = CGPoint(x: size.width / 2 - 120 * u, y: 0)
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: origin.x + x * u, y: origin.y + y * u) }
            func circle(_ c: CGPoint, _ r: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: c.x - r * u, y: c.y - r * u, width: 2 * r * u, height: 2 * r * u))
            }
            let ring = 1.25 * u
            let dash = StrokeStyle(lineWidth: ring, dash: [2 * u, 4 * u])
            ctx.stroke(circle(p(120, 80), 24), with: .color(Color.liqPurple.opacity(0.45)), lineWidth: ring)
            ctx.stroke(circle(p(120, 80), 56), with: .color(Color.liqPurple.opacity(0.30)), style: dash)
            ctx.stroke(circle(p(120, 80), 96), with: .color(Color.liqPurple.opacity(0.18)), style: dash)

            for c in [p(148, 62), p(74, 108), p(198, 118)] {
                ctx.fill(circle(c, 4.5), with: .color(.liqCream))
                ctx.stroke(circle(c, 4.5), with: .color(.liqPurple), lineWidth: 1.5 * u)
            }
            ctx.fill(circle(p(44, 50), 3.5), with: .color(.liqCream))
            ctx.stroke(circle(p(44, 50), 3.5), with: .color(Color.liqLavender.opacity(0.7)), lineWidth: 1.25 * u)

            ctx.fill(circle(p(120, 80), 18), with: .color(Color.liqOrange.opacity(0.18)))

            // The pin: the 24-grid map-pin at translate(108, 64) scale 1.25, sunset top to bottom.
            var g = ctx
            g.translateBy(x: origin.x + 108 * u, y: origin.y + 64 * u)
            g.scaleBy(x: 1.25 * u, y: 1.25 * u)
            var pin = Path()
            pin.move(to: CGPoint(x: 21, y: 10))
            pin.addCurve(to: CGPoint(x: 12, y: 23), control1: CGPoint(x: 21, y: 17), control2: CGPoint(x: 12, y: 23))
            pin.addCurve(to: CGPoint(x: 3, y: 10), control1: CGPoint(x: 12, y: 23), control2: CGPoint(x: 3, y: 17))
            pin.addArc(center: CGPoint(x: 12, y: 10), radius: 9,
                       startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            pin.closeSubpath()
            // #FE6839 -> #D05976: the sunset gradient's first two stops, read from the token rather
            // than retyped.
            g.fill(pin, with: .linearGradient(
                Gradient(colors: [Color.liqOrange, Color.suGradSunsetStops[1].color]),
                startPoint: CGPoint(x: 12, y: 1), endPoint: CGPoint(x: 12, y: 23)))
            g.stroke(pin, with: .color(.white), lineWidth: 1.6)
            g.fill(Path(ellipseIn: CGRect(x: 9, y: 7, width: 6, height: 6)), with: .color(.white))
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview("Location · A") { ProfileLocationView(state: .ask) }
#Preview("Location · B") { ProfileLocationView(state: .denied) }
#Preview("Location · C") { ProfileLocationView(state: .servicesOff) }
