//  TutorialShell.swift
//  ShowUp · the shared chrome for every tutorial screen (SHOWUP-135)
//
//  Screen 2 ("Meet in real life") is the reference implementation of this chrome; screens 3-6 reuse
//  it unchanged and pass content only. Nothing screen-specific belongs in this file.
//
//  The layout rule that matters: this is ONE top-anchored column with a SINGLE flexible region
//  between the illustration and the nav row. That region is the only thing that absorbs height
//  differences between screens and between devices. No Y position is ever hard-coded — offsets that
//  look right at 844 tall break on every other frame.
//
//  The illustration is the only flexible element. On short frames it shrinks; the type, the nav row
//  and the safe-area margins never do, and the screen never scrolls.

import SwiftUI
import UIKit

// MARK: - ② Step progress — 5 segments, height 5, gap 6, full content width

struct StepProgress: View {
    let steps: Int
    let current: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<steps, id: \.self) { i in
                Capsule()
                    .fill(i < current ? Color.liqPurple : Color.liqTrack)
                    .frame(height: 5)
            }
        }
        // Advancing a card should read as progress being made, not as the bar being redrawn.
        .animation(reduceMotion ? nil : .easeInOut(duration: Motion.screen), value: current)
        // Five anonymous capsules carry no text: without this VoiceOver announces nothing here.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Step \(current) of \(steps)"))
    }
}

// MARK: - ③ Eyebrow pill — Manrope 700 / 11 / uppercase, tracking .08, padding 5 / 10
//
// Placed by the caller inside an HStack with a trailing Spacer, so it hugs its text rather than
// stretching to the content width. The reference render shows the stretched version; the design
// system component is hug-width.

// MARK: - ⑨ Next — label plus a 56pt circular arrow, gap 14

/// Which circle the nav row's forward action wears.
///
/// A variant on the shared button, not a forked nav row — screen 6 is the only terminal screen, and
/// forking would mean its progress bar and Back slot drift from the other five.
enum NextVariant { case orange, sunset }

/// The spec's arrow: a 2pt stroke with round caps, not a filled glyph.
///
/// `Image(systemName: "arrow.right")` renders at the system weight with a different silhouette;
/// at 56pt against a saturated circle that difference shows. Drawing it also keeps iOS and Android
/// pixel-identical, which an SF Symbol never can.
struct ArrowRight: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let s = r.width
        let midY = r.midY
        p.move(to: CGPoint(x: s * 0.10, y: midY))
        p.addLine(to: CGPoint(x: s * 0.84, y: midY))
        p.move(to: CGPoint(x: s * 0.56, y: midY - s * 0.25))
        p.addLine(to: CGPoint(x: s * 0.86, y: midY))
        p.addLine(to: CGPoint(x: s * 0.56, y: midY + s * 0.25))
        return p
    }
}

/// Publishes the button's pressed state down the environment so only the circle scales and the
/// label stays put — matching Android, where the press transform sits on the circle alone.
private struct IsPressedKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
    var nextIsPressed: Bool {
        get { self[IsPressedKey.self] }
        set { self[IsPressedKey.self] = newValue }
    }
}
private struct PressReporter: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.environment(\.nextIsPressed, configuration.isPressed)
    }
}

private struct NextCircle: View {
    let variant: NextVariant
    @Environment(\.nextIsPressed) private var isPressed
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var fill: AnyShapeStyle {
        switch variant {
        case .orange:
            return AnyShapeStyle(Color.liqOrange)
        case .sunset:
            // 135 degrees, midpoint at 38% - deliberately not an even three-stop ramp
            return AnyShapeStyle(LinearGradient(
                stops: [
                    .init(color: .liqOrange, location: 0.00),
                    .init(color: Color(hex: 0xD05976), location: 0.38),
                    .init(color: .liqPurple, location: 1.00),
                ],
                startPoint: .topLeading, endPoint: .bottomTrailing))
        }
    }
    private var glow: Color { variant == .sunset ? .liqPurple : .liqOrange }
    /// Card 05's arrow is 22; cards 01-04 use 20.
    private var arrowSize: CGFloat { variant == .sunset ? 22 : 20 }

    var body: some View {
        ZStack {
            Circle().fill(fill).frame(width: IconSizes.badge, height: IconSizes.badge)
            ArrowRight()
                .stroke(Color.white,
                        style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                .frame(width: arrowSize, height: arrowSize)
        }
        // --liq-shadow-cta, both layers rather than one approximation of them:
        //     0 8px 20px rgba(c,.32)  and  0 2px 6px rgba(c,.20)
        // SwiftUI's `radius` is about half a CSS blur, hence 10 and 3. Two stacked shadows are how
        // SwiftUI expresses a two-layer token, and the pair reads tighter under the circle than the
        // single soft 0.5 that was here.
        .shadow(color: glow.opacity(0.32), radius: 10, y: 8)
        .shadow(color: glow.opacity(0.20), radius: 3, y: 2)
        // 0.94 reads as a press without the circle appearing to shrink away from the finger.
        .scaleEffect(isPressed && !reduceMotion ? 0.94 : 1)
        .animation(.spring(response: 0.24, dampingFraction: 0.55), value: isPressed)
    }
}

struct NextButton: View {
    let label: String
    var variant: NextVariant = .orange
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(label)
                    .font(F.manrope(17, .bold))
                    .foregroundColor(.liqFg)
                NextCircle(variant: variant)
            }
        }
        .buttonStyle(PressReporter())
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - The shell

/// Everything here is identical across tutorial screens 2-6.
///
/// - Parameters:
///   - step: which segment of the progress bar is filled (1-based)
///   - totalSteps: how many segments — 5 for the tour
///   - showBack: false on the first screen of the tour. The slot stays, reserving layout width,
///     but is invisible and non-interactive.
///   - headline: passed in so each screen styles its own italic run
///   - content: ⑤⑥⑦ the per-screen body — rule rows, paragraphs
///   - art: ⑧ the illustration block, the only element allowed to shrink
struct TutorialShell<Headline: View, Content: View, Art: View>: View {
    let step: Int
    let totalSteps: Int
    let eyebrow: String
    // Declared here, before nextLabel, because that is where every call site passes it.
    // Swift's memberwise init requires arguments in declaration order — unlike Kotlin,
    // where named arguments may appear in any order. That difference is why Android
    // compiled this happily while Xcode rejected it.
    let nextLabel: String
    var nextVariant: NextVariant = .orange
    var showBack: Bool = true
    var onNext: () -> Void = {}
    var onBack: () -> Void = {}
    @ViewBuilder let headline: () -> Headline
    @ViewBuilder let content: () -> Content
    @ViewBuilder let art: () -> Art

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 0) {
                StepProgress(steps: totalSteps, current: step)
                    .padding(.top, Spacing.md)                      // ① pad-top 8 below the safe-area inset

                Spacer().frame(height: 24)                 // progress -> eyebrow

                HStack(spacing: 0) {                       // hug-width, not a full-width band
                    StatusBadge(label: eyebrow, tone: .lavender)
                    Spacer(minLength: 0)
                }

                Spacer().frame(height: 14)                 // eyebrow -> headline

                // ④ headline. The orange wash belongs to the italic run, and WashHeadline
                // measures where that run actually landed before drawing it. This was a
                // fixed-width bar pinned to the bottom-leading corner of the whole headline: a
                // fixed width cannot know where the words are, so it sat under the last line
                // instead of under the emphasised phrase, and was wrong by a different amount at
                // every screen size.
                headline()

                Spacer().frame(height: 28)                 // headline -> content

                content()

                Spacer().frame(height: 24)                 // content -> illustration

                // The single flexible region. Everything above is fixed; the art takes what is
                // left, capped at its natural height, and the surplus below it pushes the nav row
                // to the bottom.
                art()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

                // ⑨ nav row — bottom-anchored, 24 above the content floor
                HStack {
                    // 44pt is Apple's minimum touch target; the label alone is ~17pt tall. The
                    // frame carries the target and the text sits at its leading edge - identical
                    // pixels, a control that passes an accessibility audit. While the slot is
                    // reserved but invisible (card 01) it is hidden from VoiceOver too, so nothing
                    // announces a "Back" that cannot be pressed.
                    Button(action: { if showBack { onBack() } }) {
                        Text("Back")
                            .font(F.manrope(14, .semibold))
                            .foregroundColor(showBack ? .liqSubtle : .clear)
                            .minTapTarget()
                    }
                    .buttonStyle(.plain)
                    .disabled(!showBack)
                    .accessibilityHidden(!showBack)

                    Spacer()
                    NextButton(label: nextLabel, variant: nextVariant, action: onNext)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, Spacing.screenGutter)                      // gutter 24
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

// MARK: - ⑤ Rule row — shared by screens 2-5: dot · rule · faint dash · purple consequence

struct RuleRow: View {
    let rule: String
    let consequence: String
    var wraps: Bool = true

    var body: some View {
        HStack(alignment: .top, spacing: 9) {
            Circle()
                .fill(Color.liqOrange)
                .frame(width: 6, height: 6)
                .padding(.top, Spacing.sm)              // sits on the first line's optical centre
            Text(TypeMetrics.attributed(
                colouredRuns: [
                    (rule, TypeMetrics.uiFont(PS.manropeSemi, 13,
                                              fallback: .systemFont(ofSize: 13, weight: .semibold)),
                     UIColor(Color.liqFg)),
                    (" — ", TypeMetrics.uiFont(PS.manropeMedium, 13,
                                               fallback: .systemFont(ofSize: 13, weight: .medium)),
                     UIColor(Color.liqFaint)),
                    (consequence, TypeMetrics.uiFont(PS.manropeSemi, 13,
                                                     fallback: .systemFont(ofSize: 13, weight: .semibold)),
                     UIColor(Color.liqPurple)),
                ],
                size: 13, multiple: 1.4, trackingEm: -0.01
            ))
            .lineLimit(wraps ? nil : 1)          // screen 2 is specced nowrap; 3-5 wrap
            .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

// MARK: - ⑧ Placeholder for the artwork design will supply
//
// It holds the exact block the real asset gets — 248 x 210, centred, shrinking with the frame — so
// dropping the image in later changes nothing about the layout. Replace the placeholder with
// Image(...).resizable().scaledToFit() and the surrounding code is unchanged.

struct IllustrationPlaceholder: View {
    /// Screen 6 draws at 0.62 — its text block is the tallest of the set.
    var scale: CGFloat = 1

    var body: some View {
        ZStack {
            // radial glow — kept so the "no banding" criterion stays testable
            RadialGradient(
                gradient: Gradient(stops: [
                    .init(color: .liqOrange.opacity(0.16), location: 0.00),
                    .init(color: .liqPurple.opacity(0.10), location: 0.48),
                    .init(color: .liqPurple.opacity(0.00), location: 0.70),
                ]),
                center: .center, startRadius: 0, endRadius: 120
            )
            .blur(radius: 6)

            GeometryReader { geo in
                let side = min(geo.size.height, 230 * scale)
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(
                        Color.liqPurple.opacity(0.38),
                        style: StrokeStyle(lineWidth: 1.5, dash: [7, 6])
                    )
                    .overlay(
                        VStack(spacing: 3) {
                            Image(systemName: "photo")
                                .font(.system(size: 24, weight: .light))
                                .foregroundColor(.liqPurple.opacity(0.5))
                            Text("ILLUSTRATION")
                                .font(F.manrope(11, .bold))
                                .tracking(0.07 * 11)
                                .foregroundColor(.liqPurple.opacity(0.75))
                            Text("248 × 210")
                                .font(F.manrope(10.5, .semibold))
                                .foregroundColor(.liqSubtle)
                        }
                    )
                    .frame(width: side * (248.0 / 210.0), height: side)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
        .frame(maxHeight: 230 * scale)
        // Decorative: it carries no information the copy does not already state, so VoiceOver
        // skips it rather than being given a label it would have to read past.
        .accessibilityHidden(true)
    }
}


// MARK: - Screen 6 only: a plain statement row, single colour
//
// Deliberately NOT the two-tone rule/consequence pattern — that sells a benefit, this states
// policy. 14.5 / 1.42, 7pt dot at offset 7, gap 12 to the text.

struct StatementRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.xl) {
            Circle().fill(Color.liqOrange).frame(width: 7, height: 7).padding(.top, 7)
            Text(TypeMetrics.attributed(
                runs: [(text, TypeMetrics.uiFont(PS.manropeSemi, 14.5,
                        fallback: .systemFont(ofSize: 14.5, weight: .semibold)))],
                size: 14.5, multiple: 1.42, color: UIColor(Color.liqFg)))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}
