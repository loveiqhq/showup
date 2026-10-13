//
//  LifeChrome.swift
//  ShowUp · what Life builds and Future consumes (Profiles 22 and 23, SHOWUP-175 / 176): LifePill,
//  CategoryHeader, and the three-question column both screens draw
//
//  The Swift twin of `profile/LifeChrome.kt`. "Build LifePill, CategoryHeader and toggleLifePick"
//  (Profile 22) and "consume them — no copy, no fork" (Profile 23): both screens are `LifeView`
//  below with their own step.
//
//  THE PILL DRAWS 34 AND ANSWERS AT 44 (`overflowTapTarget`). Rows are 8 apart, so two stacked rows'
//  touch areas share a 2-wide band in the middle of the gap. Growing the pill instead would open
//  every row by 10, which is the whole of Life's "fits at 390" budget.
//

import SwiftUI

/// The pill's drawn height — `height: 34`. A minimum, so Dynamic Type grows it.
private let pillHeight: CGFloat = 34

/// One pill — a checkbox: several can be picked in a question.
///
/// Picked: violet fill and border, white 700, `0 2 8` violet glow. Resting: white, 1px
/// `--liq-border`, 500. `transition: background 180ms cubic-bezier(.22,1,.36,1), color 180ms,
/// border-color 180ms` — the fill on the brand curve, ink and border on `ease`, and the glow NOT AT
/// ALL: `box-shadow` is not in the list, so it arrives with the tap, as on the web. Nor the weight:
/// 500 to 700 changes at once, as in CSS, so only the three colours are inside an animation.
struct LifePill: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let ease = reduceMotion ? nil : Motion.cssEase(Motion.fast)
        let brand = reduceMotion ? nil : Animation.timingCurve(0.22, 1, 0.36, 1, duration: Motion.fast)
        Button(action: action) {
            Text(label)
                .font(F.manrope(13.5, selected ? .bold : .medium))
                .animation(ease) { $0.foregroundStyle(selected ? Color.white : Color.liqFg) }
                // One line on every phone at the default size; a second only past the column's width
                // at the largest Dynamic Type — centred, at the font's own line pitch (CSS never wraps
                // a pill, so `line-height: 1` has no two-line meaning), and kept off the stroke by
                // `sm` above and below once it grows past 34.
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                // `padding: 0 14px` plus the 1px border CSS draws inside the same border-box.
                .padding(.horizontal, 15)
                .padding(.vertical, Spacing.sm)
                .frame(minHeight: pillHeight)
                .background(
                    Capsule()
                        .animation(brand) { $0.foregroundStyle(selected ? Color.liqPurple : Color.liqElevated) }
                        .shadow(color: selected ? .liqPillGlow : .clear, radius: 4, y: 2)
                )
                .overlay(
                    Capsule()
                        .strokeBorder(lineWidth: 1)
                        .animation(ease) { $0.foregroundStyle(selected ? Color.liqPurple : Color.liqBorder) }
                )
                .overflowTapTarget()
        }
        // No press scale: the reference's pill transitions `transform` and never sets one.
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        // A checkbox, as CheckRow is: "Single, checked".
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(selected ? "Checked" : "Not checked")
    }
}

/// A question's header: the icon in a 28 lavender medallion (lavender-400 at 16%, the eyebrow's
/// tint), then the label in Manrope 700 15. DECORATIVE: hidden from VoiceOver, because the label is
/// read as the question group's name.
struct CategoryHeader: View {
    let icon: BrandIcon
    let label: String

    var body: some View {
        HStack(spacing: Spacing.lg) {
            BrandIconView(icon: icon, size: 16, stroke: 1.9, tint: .liqPurple)
                .frame(width: 28, height: 28)
                .background(Circle().fill(Color.liqEyebrowBg))
            Text(label)
                .font(F.manrope(15, .bold))
                .tracking(15 * 0.005)
                .foregroundColor(.liqFg)
        }
        .accessibilityHidden(true)
    }
}

/// The questions of one step, `gap: 22`, each a group labelled by its category.
struct LifeQuestions: View {
    let step: PickStep
    let draft: LifeDraft
    let onTap: (LifeQuestion, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(step.questions, id: \.self) { question in
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    CategoryHeader(icon: question.icon, label: question.label)
                    WrapLayout(spacing: Spacing.md, lineSpacing: Spacing.md) {
                        ForEach(question.options, id: \.value) { option in
                            LifePill(label: option.label,
                                     selected: draft.picked(question).contains(option.value)) {
                                onTap(question, option.value)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(question.label)
            }
        }
    }
}

/// Life or Future: the column, the sub, the three questions, the footer. Headline margin 8, the sub
/// (Manrope 500 14.5 / 1.45, max-width 320), the region at 22; the footer 16 above, 22 below.
struct LifeView: View {
    let step: PickStep
    var state = PickUiState()
    var onTap: (LifeQuestion, String) -> Void = { _, _ in }
    var onContinue: () -> Void = {}
    var onSkip: () -> Void = {}
    var onBack: () -> Void = {}

    var body: some View {
        PickScaffold(
            step: step,
            onBack: onBack,
            headlineGap: Spacing.md,
            above: {
                Text(PickCopy.lifeSub)
                    .font(F.manrope(14.5, .medium))
                    // 14.5 / 1.45: lines 21.0 apart, as Android's `lineHeight` sets them.
                    .lineSpacing(TypeMetrics.lineSpacing(PS.manropeMedium, size: 14.5, multiple: 1.45))
                    .foregroundColor(.liqNeutral)
                    .frame(maxWidth: 320, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                    // The region's `margin-top: 22`, outside its scroll.
                    .padding(.bottom, 22)
            },
            footer: {
                PickFooter(onSkip: onSkip, onContinue: onContinue, saveFailed: state.saveFailed,
                           toastTick: state.toastTick, divided: false)
                    .padding(.top, Spacing.xxl)
                    .padding(.bottom, 22)
            }
        ) {
            LifeQuestions(step: step, draft: state.life, onTap: onTap)
                // A PRE-FILL IS NOT A TAP: new pills per pre-fill start at their value, so only a
                // tap runs the 180 ms change.
                .id(state.prefill)
        }
    }
}
