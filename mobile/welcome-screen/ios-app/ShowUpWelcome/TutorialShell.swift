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

struct NextButton: View {
    let label: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(label)
                    .font(F.manrope(17, .bold))
                    .foregroundColor(.liqFg)
                ZStack {
                    Circle().fill(Color.liqOrange).frame(width: 56, height: 56)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                }
                .shadow(color: .liqOrange.opacity(0.5), radius: 16, y: 10)
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
                    NextButton(label: nextLabel, action: onNext)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)                      // gutter 24
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}
