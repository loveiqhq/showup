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

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<steps, id: \.self) { i in
                Capsule()
                    .fill(i < current ? Color.liqPurple : Color.liqTrack)
                    .frame(height: 5)
            }
        }
    }
}

// MARK: - ③ Eyebrow pill — Manrope 700 / 11 / uppercase, tracking .08, padding 5 / 10
//
// Placed by the caller inside an HStack with a trailing Spacer, so it hugs its text rather than
// stretching to the content width. The reference render shows the stretched version; the design
// system component is hug-width.

struct EyebrowPill: View {
    let text: String

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(Color.liqOrange).frame(width: 5, height: 5)
            Text(text.uppercased())
                .font(F.manrope(11, .bold))
                .tracking(0.08 * 11)
                .foregroundColor(.liqPurple)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(Color.liqEyebrowBg))
    }
}

// MARK: - ⑨ Next — label plus a 56pt circular arrow, gap 14

/// Which circle the nav row's forward action wears.
///
/// A variant on the shared button, not a forked nav row — screen 6 is the only terminal screen, and
/// forking would mean its progress bar and Back slot drift from the other five.
enum NextVariant { case orange, sunset }

struct NextButton: View {
    let label: String
    var variant: NextVariant = .orange
    var action: () -> Void

    private var circleFill: AnyShapeStyle {
        switch variant {
        case .orange: return AnyShapeStyle(Color.liqOrange)
        case .sunset: return AnyShapeStyle(LinearGradient(
            colors: [.liqOrange, Color(hex: 0xD05976), .liqPurple],
            startPoint: .topLeading, endPoint: .bottomTrailing))
        }
    }
    private var glow: Color { variant == .sunset ? .liqPurple : .liqOrange }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(label)
                    .font(F.manrope(17, .bold))
                    .foregroundColor(.liqFg)
                ZStack {
                    Circle().fill(circleFill).frame(width: 56, height: 56)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                }
                .shadow(color: glow.opacity(0.5), radius: 16, y: 10)
            }
        }
        .buttonStyle(.plain)
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
                    .padding(.top, 8)                      // ① pad-top 8 below the safe-area inset

                Spacer().frame(height: 24)                 // progress -> eyebrow

                HStack(spacing: 0) {                       // hug-width, not a full-width band
                    EyebrowPill(text: eyebrow)
                    Spacer(minLength: 0)
                }

                Spacer().frame(height: 14)                 // eyebrow -> headline
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
                    Text("Back")
                        .font(F.manrope(14, .semibold))
                        .foregroundColor(showBack ? .liqSubtle : .clear)
                        .allowsHitTesting(showBack)
                        .onTapGesture { if showBack { onBack() } }
                    Spacer()
                    NextButton(label: nextLabel, variant: nextVariant, action: onNext)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)                      // gutter 24
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
                .padding(.top, 6)              // sits on the first line's optical centre
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
                    .init(color: .liqOrange.opacity(0.13), location: 0.26),
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
    }
}


// MARK: - Screen 6 only: a plain statement row, single colour
//
// Deliberately NOT the two-tone rule/consequence pattern — that sells a benefit, this states
// policy. 14.5 / 1.42, 7pt dot at offset 7, gap 12 to the text.

struct StatementRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
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
