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

    /// The Search location gate's strings (SHOWUP-188, `LOCATION_COPY.app`): identical on iOS and
    /// Android, and none of them names a Settings path -- the button routes instead. Apostrophes are
    /// U+2019, as the ticket's acceptance criteria and the reference render them. The privacy note
    /// and the two CTAs are Profile 12's, unchanged.
    enum App {
        static let aLead = "Allow location to see people ", aEm = "nearby", aTail = "."
        static let aBody = "We use your location to show you people within your distance, pick a fair "
            + "halfway venue and guide you there on the day."
        static let bLead = "Location is off for ", bEm = "Show Up", bTail = "."
        static let bBody = "You turned off location access for Show Up, so we can\u{2019}t show you anyone "
            + "nearby or plan a date. Open Settings and allow location \u{2014} you\u{2019}ll pick up right "
            + "where you left off."
        static let cLead = "Location is off on your ", cEm = "phone", cTail = "."
        static let cBody = "Location is switched off for every app on this phone, not just Show Up. Open "
            + "Settings and turn it on \u{2014} you\u{2019}ll pick up right where you left off."
        /// D's back pill.
        static let back = "Back"
    }
}

/// Which context the ONE location screen is drawn in (SHOWUP-188 ①, `context: "profile" | "app"`).
/// `.profile` is Profile 12 inside sign-up, unchanged; `.app` is the Search location gate. Android's
/// `LocationScreenContext`.
enum LocationScreenContext {
    case profile
    case app(LocationAppContext)
}

/// The gate's chrome: the Search top bar (no filter button) and the TabBar, and in D -- pushed over a
/// profile opened from the Likes tab -- the Premium bar with no chip and a back pill.
struct LocationAppContext {
    /// `.search` for A-C, `.likes` for D.
    let tab: AppTab
    /// The open received likes -- the chip's count and the TabBar badge.
    let likes: Int
    let onTab: (AppTab) -> Void
    let onLikes: () -> Void
    /// D only: back to the profile. Nil on the tab, which is a root with no back.
    var onBack: (() -> Void)? = nil
    var backLabel: String = LocationCopy.App.back
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
    var context: LocationScreenContext = .profile

    var body: some View {
        switch context {
        case .profile: profileBody
        case .app(let app):
            LocationGateLayout(state: state, requesting: requesting, onAllow: onAllow,
                               onOpenSettings: onOpenSettings, app: app)
        }
    }

    private var profileBody: some View {
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
                // THE GAP IS BETWEEN PARAGRAPHS, NOT BETWEEN PIPS (Philipp, 8 October 2026). The
                // pip no longer sets a row's height (see LocationBenefitRow), so the reference's gap
                // is widened by the overhang a one-line row used to add: all three paragraphs sit
                // the same distance apart. The bottom padding keeps row 3's pip clear of the note.
                VStack(alignment: .leading, spacing: fit.rowGap + LocationBenefitRow.pipOverhang) {
                    ForEach(locationBenefits) { LocationBenefitRow(benefit: $0) }
                }
                .padding(.top, Spacing.xs + 22)
                .padding(.bottom, LocationBenefitRow.pipOverhang)
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
/// One benefit row: the title and its sub-line read as one element; the pip is decorative.
///
/// THE PIP IS CENTRED ON THE TITLE, NOT ON THE ROW (Philipp, 8 October 2026). The reference sets
/// `alignItems: center` on the whole row, which on row 2 — the only one with a sub-line — put the
/// map pin halfway down the grey text, a line below the bold title it belongs to. Now the pip sits
/// level with the title on every row, and the sub-line runs beneath the title, aligned to its text.
///
/// And the pip TAKES NO HEIGHT: the title sets the row. A 40-tall pip beside a 21-tall one-line
/// title used to add invisible space above and below that row, so the paragraphs sat unevenly
/// apart. With the text setting the height, the stack's spacing is the gap the eye sees.
private struct LocationBenefitRow: View {
    let benefit: LocationBenefit

    static let pip: CGFloat = 40
    static let pipGap: CGFloat = 14
    /// How far a pip reaches past a ONE-line title, above and below: the pip is 40, a title line is
    /// 17 x 1.25. A two-line title is taller than the pip, and then nothing overhangs.
    static let pipOverhang: CGFloat = (pip - 17 * 1.25) / 2

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: Self.pipGap) {
                // A zero-height slot the stack centres on the title; the pip hangs from it at its
                // full size, overflowing the slot rather than setting the row's height.
                Color.clear
                    .frame(width: Self.pip, height: 0)
                    .overlay {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Gradients.lilac())
                            BrandIconView(icon: benefit.icon, size: IconSizes.sm, stroke: 1.8, tint: .liqPurple)
                        }
                        .frame(width: Self.pip, height: Self.pip)
                    }
                    .accessibilityHidden(true)

                // Lora 700 / 17 / 1.25, -0.005em, `text-wrap: balance`.
                WashHeadline(parts: [(benefit.title, false)], fontSize: 17, lineHeightMultiple: 1.25,
                             trackingEm: -0.005, balance: true)
            }
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
                    // Under the title's text, not under the pip.
                    .padding(.leading, Self.pip + Self.pipGap)
                    .padding(.top, 3)
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
///
/// THE ONE RADAR (SHOWUP-188 ③): `live` is Profile 12's drawing, unchanged. `live: false` is the
/// location gate's B, C and D -- slate rings at .22 / .14 / .08, no dots, no halo, a grey pin and one
/// slash. A second radar component is a bug.
private struct LocationRadar: View {
    let height: CGFloat
    var live: Bool = true

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
            let ink = live ? Color.liqPurple : Color.liqRadarSlate
            let alphas: [Double] = live ? [0.45, 0.30, 0.18] : [0.22, 0.14, 0.08]
            ctx.stroke(circle(p(120, 80), 24), with: .color(ink.opacity(alphas[0])), lineWidth: ring)
            ctx.stroke(circle(p(120, 80), 56), with: .color(ink.opacity(alphas[1])), style: dash)
            ctx.stroke(circle(p(120, 80), 96), with: .color(ink.opacity(alphas[2])), style: dash)

            if live {
                for c in [p(148, 62), p(74, 108), p(198, 118)] {
                    ctx.fill(circle(c, 4.5), with: .color(.liqCream))
                    ctx.stroke(circle(c, 4.5), with: .color(.liqPurple), lineWidth: 1.5 * u)
                }
                ctx.fill(circle(p(44, 50), 3.5), with: .color(.liqCream))
                ctx.stroke(circle(p(44, 50), 3.5), with: .color(Color.liqLavender.opacity(0.7)), lineWidth: 1.25 * u)

                ctx.fill(circle(p(120, 80), 18), with: .color(Color.liqOrange.opacity(0.18)))
            }

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
            if live {
                g.fill(pin, with: .linearGradient(
                    Gradient(colors: [Color.liqOrange, Color.suGradSunsetStops[1].color]),
                    startPoint: CGPoint(x: 12, y: 1), endPoint: CGPoint(x: 12, y: 23)))
                g.stroke(pin, with: .color(.white), lineWidth: 1.6)
                g.fill(Path(ellipseIn: CGRect(x: 9, y: 7, width: 6, height: 6)), with: .color(.white))
            } else {
                g.fill(pin, with: .color(Color.liqRadarSlate.opacity(0.55)))
                g.stroke(pin, with: .color(Color.liqRadarSlate.opacity(0.65)), lineWidth: 1.6)
                g.fill(Path(ellipseIn: CGRect(x: 9, y: 7, width: 6, height: 6)), with: .color(.liqRadarSlateDot))
                // The slash: (-24, 18) to (21, -21) from the pin's centre, 1.6, round caps.
                var slash = Path()
                slash.move(to: p(96, 98))
                slash.addLine(to: p(141, 59))
                ctx.stroke(slash, with: .color(Color.liqRadarSlate.opacity(0.55)),
                           style: StrokeStyle(lineWidth: 1.6 * u, lineCap: .round))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - the app context: the Search location gate (SHOWUP-188)

/// The gate's order of sacrifice, as levels: "spacer -> radar 170 -> 130 (D 140 -> 110) ->
/// privacy-note margin 18 -> 12. Never shrink the headline, never let it scroll."
private enum GateFit: CaseIterable {
    case full, compactRadar, tight
    var radar: CGFloat { self == .full ? 170 : 130 }
    var radarOverProfile: CGFloat { self == .full ? 140 : 110 }
    var noteGap: CGFloat { self == .tight ? 12 : 18 }
}

/// The location screen in its app context: SearchTopBar (no filter) -> [back pill, D] -> the content
/// column (radar, headline, body, [privacy note, A], ONE flexible spacer) -> the CTA -> TabBar.
/// Backdrop 0, content 1, chrome 2. Android's `LocationGateLayout`.
///
/// THE CTA SITS DIRECTLY ABOVE THE TABBAR IN EVERY STATE: a band pinned above it, 22 from it. Past the
/// last sacrifice level the content scrolls under that band rather than push the CTA off screen,
/// Profile 12's recorded precedent.
private struct LocationGateLayout: View {
    let state: LocationState
    let requesting: Bool
    let onAllow: () -> Void
    let onOpenSettings: () -> Void
    let app: LocationAppContext

    /// "A state swap in place (A -> B, C -> B) moves focus to the headline and announces it."
    @AccessibilityFocusState private var headlineFocused: Bool

    private var blocked: Bool { state != .ask }
    private var overProfile: Bool { app.onBack != nil }

    var body: some View {
        VStack(spacing: 0) {
            // No filter button: with location off, the only job is turning it on. D is the bar of the
            // profile it was opened from -- Premium, no chip.
            SearchTopBar(likes: app.likes, onLikes: app.onLikes, onFilters: {},
                         premium: overProfile, filter: false, chip: !overProfile)
                .fitAnchor(FitAnchorID.searchTopBar)
            if let back = app.onBack {
                BackPill(label: app.backLabel, onBack: back, rowBackground: .clear)
            }
            ViewThatFits(in: .vertical) {
                column(.full)
                column(.compactRadar)
                column(.tight)
                ScrollView(.vertical, showsIndicators: false) { column(.tight) }
            }
            .frame(maxHeight: .infinity, alignment: .top)

            PrimaryButton(label: blocked ? LocationCopy.openSettings : LocationCopy.allow, variant: .sunset,
                          // Beside its icon, `Allow location access` takes a third line on the Fold
                          // cover at the largest type; two would cut it mid-word.
                          labelLineLimit: 3,
                          // Never disabled: while our dialog is up it simply stops answering.
                          action: { if blocked { onOpenSettings() } else if !requesting { onAllow() } },
                          leading: {
                              BrandIconView(icon: blocked ? .settings : .mapPin, size: 18, stroke: 2.2, tint: .white)
                                  .accessibilityHidden(true)
                          },
                          trailing: { EmptyView() })
                .padding(.horizontal, 24)
                .padding(.bottom, 22)
                .fitAnchor(FitAnchorID.gateCta)

            TabBar(active: app.tab, onTab: app.onTab, likes: app.likes)
                .fitAnchor(FitAnchorID.tabBar)
        }
        .background(alignment: .top) {
            // Profile 12's three layers; dialled back on B, C and D (orbs .16 / .14, wash .28).
            WelcomeBackdrop(placement: .startup,
                            orangeAlpha: blocked ? 0.16 : 0.32,
                            violetAlpha: blocked ? 0.14 : 0.28,
                            washAlpha: blocked ? 0.28 : 0.55)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .background(Color.liqCream.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .onChange(of: state) { _, _ in headlineFocused = true }
    }

    private var headlineParts: [(String, Bool)] {
        switch state {
        case .ask: return [(LocationCopy.App.aLead, false), (LocationCopy.App.aEm, true), (LocationCopy.App.aTail, false)]
        case .denied: return [(LocationCopy.App.bLead, false), (LocationCopy.App.bEm, true), (LocationCopy.App.bTail, false)]
        case .servicesOff:
            return [(LocationCopy.App.cLead, false), (LocationCopy.App.cEm, true), (LocationCopy.App.cTail, false)]
        }
    }

    private var bodyText: String {
        switch state {
        case .ask: return LocationCopy.App.aBody
        case .denied: return LocationCopy.App.bBody
        case .servicesOff: return LocationCopy.App.cBody
        }
    }

    /// The content at one sacrifice level, ending in the one flexible spacer (min 16).
    private func column(_ fit: GateFit) -> some View {
        VStack(spacing: 0) {
            LocationRadar(height: overProfile ? fit.radarOverProfile : fit.radar, live: state == .ask)
            // Lora 700 / 30 / 1.12 / -0.015em, centred, balanced -- the Search group's size, not
            // Profile 12's 32. The screen's heading.
            WashHeadline(parts: headlineParts, fontSize: 30, lineHeightMultiple: 1.12, trackingEm: -0.015,
                         balance: true, centered: true, cssLineBox: true, heading: true)
                .accessibilityFocused($headlineFocused)
                .padding(.top, 10)
                .fitAnchor(FitAnchorID.gateHeadline)
            // Manrope 500 / 14.5 / 1.5, centred, at most 330 wide.
            PrettyText(bodyText,
                       font: TypeMetrics.uiFont(PS.manropeMedium, 14.5, fallback: .systemFont(ofSize: 14.5, weight: .medium)),
                       lineHeight: 14.5 * 1.5, color: .liqNeutral, centered: true)
                .frame(maxWidth: 330)
                .padding(.top, 12)
            // A only: the pitch's privacy note, Profile 12's own. Not on B, C, D.
            if state == .ask {
                PrivacyNote().padding(.top, fit.noteGap)
            }
            Spacer(minLength: 16)
        }
        .padding(.horizontal, 24)
        .padding(.top, 4)
        .frame(maxWidth: .infinity)
    }
}

/// The Search location gate (SHOWUP-188) -- `ScreenLocationGate`, a thin wrapper around the one
/// location screen in its app context. `onBack` is D's; nil on the Search tab, where it is a root.
struct LocationGateView: View {
    let state: LocationState
    let tab: AppTab
    let likes: Int
    let onTab: (AppTab) -> Void
    let onLikes: () -> Void
    var onAllow: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var requesting = false
    var onBack: (() -> Void)? = nil

    var body: some View {
        ProfileLocationView(state: state, requesting: requesting, onAllow: onAllow, onOpenSettings: onOpenSettings,
                            context: .app(LocationAppContext(tab: tab, likes: likes, onTab: onTab, onLikes: onLikes,
                                                             onBack: onBack)))
    }
}

#Preview("Gate · A") { LocationGateView(state: .ask, tab: .search, likes: 2, onTab: { _ in }, onLikes: {}) }
#Preview("Gate · B") { LocationGateView(state: .denied, tab: .search, likes: 2, onTab: { _ in }, onLikes: {}) }
#Preview("Gate · C") { LocationGateView(state: .servicesOff, tab: .search, likes: 2, onTab: { _ in }, onLikes: {}) }
#Preview("Gate · D") {
    LocationGateView(state: .denied, tab: .likes, likes: 2, onTab: { _ in }, onLikes: {}, onBack: {})
}
#Preview("Location · A") { ProfileLocationView(state: .ask) }
#Preview("Location · B") { ProfileLocationView(state: .denied) }
#Preview("Location · C") { ProfileLocationView(state: .servicesOff) }
