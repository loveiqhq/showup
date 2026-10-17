//
//  PickChrome.swift
//  ShowUp · the column Interests, Life and Future render in (SHOWUP-174 to SHOWUP-176), its footer,
//  and the copy the three share
//
//  The Swift twin of `profile/PickChrome.kt`; its header has the column and the reasons. NOT
//  `DetailsScaffold` and not a fork of it — "this screen has neither [the band nor a single answer
//  list]. It is its own column built from the shared chrome":
//
//      AppHeader 52 · StepProgress (mb 22) · headline 30/1.12 · [above] · answer region (flex 1,
//      the ONLY thing that scrolls) · [footer]
//
//  The detail group's 80 floor under the region, for its reason: at the largest Dynamic Type on a
//  small phone the fixed part outgrows the screen, and the top part then scrolls as one while the
//  footer stays pinned. At the default size the floor never engages.
//

import SwiftUI

/// Every string the three screens share or headline with, verbatim from the tickets.
///
/// EVERY APOSTROPHE IS THE TICKET'S STRAIGHT ONE, the headline's `you'd` included: Profile 21 says
/// "copy matches the strings above exactly", and only Profiles 16 to 18 carry the typographic rule
/// (the Kotlin twin's comment has the detail).
enum PickCopy {
    static let howILive = "How I live"
    static let whatImInto = "What I'm into"
    static let showMore = "Show more"
    static let showLess = "Show less"

    /// Life and Future share it, word for word.
    static let lifeSub = "Select all that apply. Never shown on your profile. Used to find the right matches."

    /// The headline as runs: text, and whether it is the one italic em with the orange wash.
    static func headline(_ step: PickStep) -> [(String, Bool)] {
        switch step {
        case .interests:
            return [("Show how you live and what you love, so others know if you'd ", false), ("click", true)]
        case .lifeNow: return [("What fits your current ", false), ("life", true), ("?", false)]
        case .lifeAhead: return [("What do you imagine for your future ", false), ("life", true), ("?", false)]
        }
    }
}

/// The column.
struct PickScaffold<Above: View, Content: View, Footer: View>: View {
    let step: PickStep
    var onBack: () -> Void = {}
    /// The headline's margin-bottom: 10 on Interests, 8 on Life and Future.
    let headlineGap: CGFloat
    /// How far the region reaches into the right gutter so the indicator draws beside the chips —
    /// Interests' `padding-right: 4; margin-right: -4`. Zero on Life and Future.
    var indicatorGutter: CGFloat = 0
    /// Padding inside the scroll after the last chip: Interests' 12.
    var regionBottomInset: CGFloat = 0
    @ViewBuilder var above: () -> Above
    @ViewBuilder var footer: () -> Footer
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            // `<Atmosphere variant="form"/>`, the detail group's: 0.13 / 0.11, no peach wash.
            WelcomeBackdrop(peachWash: false, placement: .atmosphere, orangeAlpha: 0.13, violetAlpha: 0.11)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                AppHeader(title: DetailsCopy.section, leading: .back, onBack: onBack)

                VStack(alignment: .leading, spacing: 0) {
                    GeometryReader { geo in
                        ScrollView(.vertical, showsIndicators: false) {
                            FlexWithFloorLayout(available: geo.size.height, floor: answerRegionFloor) {
                                topFixed
                                region
                            }
                        }
                        .scrollBounceBehavior(.basedOnSize)
                    }
                    // `margin-right: -4`, taken HERE, by the outer scroll: a ScrollView clips to its
                    // own bounds, so a region overhanging it lost the 4 its indicator draws in. The
                    // column above the region is held back by the same 4 (`topFixed`), and the
                    // region's content by its own padding, so nothing else moves.
                    .padding(.trailing, -indicatorGutter)

                    footer()
                }
                .padding(.init(top: Spacing.xs, leading: Spacing.screenGutter, bottom: 0,
                               trailing: Spacing.screenGutter))
            }
        }
        .modifier(EdgeSwipeBack(onBack: onBack))
    }

    private var topFixed: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepProgress(steps: shareStepsTotal, current: step.stepIndex)
                .padding(.bottom, 22)
            WashHeadline(parts: PickCopy.headline(step), fontSize: 30,
                         lineHeightMultiple: 1.12, trackingEm: -0.015, balance: true)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, headlineGap)
            above()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Back inside the column: the outer scroll reaches into the gutter only for the region.
        .padding(.trailing, indicatorGutter)
    }

    /// The answer region: `flex: 1; min-height: 0; overflow-y: auto`, with the platform's own
    /// indicator flashed once on arrival — and only where the content really overflows.
    private var region: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 0) {
                content()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.trailing, indicatorGutter)
            .padding(.bottom, regionBottomInset)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollIndicatorsFlash(onAppear: true)
    }
}

/// `Skip for now` on the left, `Continue` on the right, and the failed-save toast 10 above.
///
/// CONTINUE IS NEVER DISABLED, on all three screens: with nothing picked it does exactly what Skip
/// does. THE TOAST IS FOR A FAILED SAVE ONLY — these screens refuse nothing — and its sentence is
/// the PROPOSED one the flow uses for that failure.
struct PickFooter: View {
    let onSkip: () -> Void
    let onContinue: () -> Void
    let saveFailed: Bool
    var toastTick: Int = 0
    /// Interests' `border-top: 1px --liq-border-soft` with 12 above the row.
    let divided: Bool

    var body: some View {
        VStack(spacing: 0) {
            if divided {
                Rectangle().fill(Color.liqBorderSoft).frame(height: 1)
                Color.clear.frame(height: Spacing.xl)
            }
            // Side by side when the two fit, stacked when they do not (SkipContinueRow).
            SkipContinueRow(skipLabel: DetailsCopy.skip, ctaLabel: DetailsCopy.cta,
                            onSkip: onSkip, onContinue: onContinue)
        }
        .refusalToast(RefusalToast(visible: saveFailed, icon: nil, message: DetailsCopy.saveFailedProposed,
                                   lift: Spacing.lg, tick: toastTick))
    }
}
