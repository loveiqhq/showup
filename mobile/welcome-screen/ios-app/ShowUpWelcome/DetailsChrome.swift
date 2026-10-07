//
//  DetailsChrome.swift
//  ShowUp · the shell every "Share some details" step renders through (SHOWUP-167), the footer,
//  and the two answer lists
//
//  The Swift twin of `profile/DetailsChrome.kt`; its header has the column, the numbers and the
//  reasons. In short:
//
//      AppHeader 52 · StepProgress (mb 26) · headline 30/1.12 (mb 8) · sub 14.5/1.45 ·
//      answer region (mt 12 + pt 10, the ONLY scrolling region) · band (mt 6) · footer (mt 26 mb 22)
//
//  The top part — bar, headline, sub and region — has an 80 floor under the region: on every
//  phone at the default size with no keyboard the floor never engages and nothing above the band
//  moves. Where it would (height's number pad on the smallest phones, very large type), the top part
//  scrolls as one so the field stays reachable while the band and Continue stay pinned.
//

import SwiftUI

/// Every string the group draws, verbatim from the seven tickets.
enum DetailsCopy {
    static let section = "Share some details"
    static let cta = "Continue"
    static let skip = "Skip for now"

    /// PROPOSED — NOT APPROVED. No detail ticket says what a failed save shows; this is the prompts
    /// screen's sentence for the same failure, so the flow says one thing for it everywhere.
    static let saveFailedProposed = PromptsCopy.saveFailedProposed

    static let heightLabel = "Enter height in cm"
    static let heightPlaceholder = "e.g. 175"
    static let heightNote = "Be honest — it helps us find the right matches."

    static let subMatches = "Used to find the right matches."
    static let subSelectAll = "Select all that apply."
    static let subSelectOne = "Select one."

    /// The headline as runs: text, and whether it is the one italic em with the orange wash.
    /// Typographic apostrophes in the three `What’s` headlines, as the tickets allow.
    static func headline(_ step: DetailStep) -> [(String, Bool)] {
        switch step {
        case .height: return [("How ", false), ("tall", true), (" are you?", false)]
        case .gender: return [("Which gender describes ", false), ("you", true), (" best?", false)]
        case .orientation: return [("What\u{2019}s your sexual ", false), ("orientation", true), ("?", false)]
        case .datingLanguage:
            return [("What\u{2019}s your preferred dating ", false), ("language", true), ("?", false)]
        case .education:
            return [("What\u{2019}s your highest level of ", false), ("education", true), ("?", false)]
        case .religion: return [("What are your ", false), ("religious", true), (" beliefs?", false)]
        case .politics: return [("What are your ", false), ("political", true), (" beliefs?", false)]
        }
    }

    /// The headline as one plain string — the list's accessible name.
    static func headlineText(_ step: DetailStep) -> String { headline(step).map(\.0).joined() }

    static func sub(_ step: DetailStep) -> String {
        switch step {
        case .height, .gender, .orientation: return subMatches
        case .datingLanguage: return subSelectAll
        case .education, .religion, .politics: return subSelectOne
        }
    }

    /// The refusal toast. Only the three steps that refuse have one.
    static func refusal(_ step: DetailStep) -> String? {
        switch step {
        case .height: return "Enter a height between 120 and 230 cm to continue"
        case .gender: return "Pick a gender to continue"
        case .orientation: return "Pick an orientation to continue"
        default: return nil
        }
    }

    /// The toast's sentence: the step's own refusal, or the proposed save-failure line.
    static func toast(_ step: DetailStep, _ toast: DetailToast?) -> String {
        if toast == .saveFailed { return saveFailedProposed }
        return refusal(step) ?? saveFailedProposed
    }
}

/// The least the answer region is ever given — height's 64 field under its 10 inset.
let answerRegionFloor: CGFloat = 80

/// A fixed part above a flexible one: the flexible part gets what `available` leaves, never less
/// than `floor`, and the layout reports its TRUE height so a surrounding scroll engages only when
/// the floor does. The Kotlin twin is `FlexWithFloor`, for the same reason: a flexible frame with a
/// minimum cannot ask its parent to grow.
private struct FlexWithFloorLayout: Layout {
    let available: CGFloat
    let floor: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let fixed = subviews.first?.sizeThatFits(ProposedViewSize(width: width, height: nil)).height ?? 0
        return CGSize(width: width, height: fixed + max(available - fixed, floor))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let width = bounds.width
        let fixed = subviews[0].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
        let flex = max(available - fixed, floor)
        subviews[0].place(at: bounds.origin, anchor: .topLeading,
                          proposal: ProposedViewSize(width: width, height: fixed))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.minY + fixed), anchor: .topLeading,
                          proposal: ProposedViewSize(width: width, height: flex))
    }
}

/// The leading-edge swipe that pops a step, as the tickets list it: "Back (chevron, iOS
/// swipe-back, Android back) pops". This flow routes with a `FlowScreen` switch rather than a
/// navigation stack, so the system gesture does not exist here; this is it, calling the same
/// `onBack` the chevron does.
private struct EdgeSwipeBack: ViewModifier {
    let onBack: () -> Void

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 20).onEnded { drag in
                guard drag.startLocation.x < 24,
                      drag.translation.width > 80,
                      abs(drag.translation.height) < 60 else { return }
                onBack()
            }
        )
    }
}

/// The shell.
struct DetailsScaffold<Content: View, Footer: View>: View {
    let step: DetailStep
    var onBack: () -> Void = {}
    @Binding var hidden: Bool
    /// Flash the scroll indicator once on arrival — orientation, dating language, religion and
    /// politics. Education says not to.
    var flashIndicator: Bool = false
    @ViewBuilder var footer: () -> Footer
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            // `<Atmosphere variant="form"/>` — 0.13 / 0.11, no peach wash.
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

                    ProfileVisibility(hidden: $hidden)
                        .padding(.top, Spacing.sm)

                    footer()
                        .padding(.top, 26)
                        .padding(.bottom, 22)
                }
                .padding(.init(top: Spacing.xs, leading: Spacing.screenGutter, bottom: 0, trailing: Spacing.screenGutter))
            }
        }
        .modifier(EdgeSwipeBack(onBack: onBack))
    }

    private var topFixed: some View {
        VStack(alignment: .leading, spacing: 0) {
            StepProgress(steps: shareStepsTotal, current: step.stepIndex)
                .padding(.bottom, 26)
            WashHeadline(parts: DetailsCopy.headline(step), fontSize: 30,
                         lineHeightMultiple: 1.12, trackingEm: -0.015, balance: true)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, Spacing.md)
            Text(DetailsCopy.sub(step))
                .font(F.manrope(14.5, .medium))
                .lineSpacing(14.5 * 0.45)
                .foregroundColor(.liqNeutral)
                .frame(maxWidth: 320, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            // The answer region's `margin-top: 12`, outside its scroll.
            Color.clear.frame(height: Spacing.xl)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// THE ANSWER REGION: padding-top 10 INSIDE the scroll (so height's notched label is not
    /// clipped), and the screen's one flexible element.
    ///
    /// ITS SCROLL IS HANDED TO THE LISTS through the environment, with an anchor ABOVE the 10 —
    /// so "opens scrolled to the top" is offset 0, not the first row with the inset scrolled away,
    /// and the reader wraps the scroll view it drives rather than sitting inside it.
    private var region: some View {
        GeometryReader { geo in
            ScrollViewReader { proxy in
                ScrollView(.vertical) {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear.frame(height: 0).id(DetailsRegionScroller.top)
                        content()
                            .padding(.top, Spacing.lg)
                        Spacer(minLength: 0)
                    }
                    .frame(minHeight: geo.size.height, alignment: .top)
                    .environment(\.detailsRegionScroller, DetailsRegionScroller(proxy: proxy))
                }
                .scrollBounceBehavior(.basedOnSize)
                // The platform's own indicator, flashed once — and only where the list overflows.
                .scrollIndicatorsFlash(onAppear: flashIndicator)
            }
        }
    }
}

/// The footer row: `Skip for now` on the left when the step can be skipped, `Continue` on the
/// right, and the group's toast 10 above it. ONE FOOTER WITH AN OPTIONAL SKIP.
struct DetailsFooter: View {
    var onSkip: (() -> Void)?
    let onContinue: () -> Void
    let toastVisible: Bool
    let toastMessage: String
    /// Bumped on every refusal, so a second press while the toast is up is announced again.
    var toastTick: Int = 0

    var body: some View {
        HStack {
            if let onSkip { SkipLink(label: DetailsCopy.skip, action: onSkip) }
            Spacer(minLength: 0)
            NextButton(label: DetailsCopy.cta, arrowSize: 22, action: onContinue)
        }
        .refusalToast(RefusalToast(visible: toastVisible, icon: nil, message: toastMessage, lift: 10,
                                   tick: toastTick))
    }
}

/// The answer region's scroll, as the lists use it.
///
/// A `@MainActor` CLASS, so it is Sendable and the environment key below can hold a STORED
/// `static let` default — the project's concurrency rule does not accept a computed `static var`
/// on trust (see `Gradients` in DesignSystem.swift), and a struct carrying `ScrollViewProxy` is not
/// Sendable. It is only ever made and used on the main actor, in a view body and its tasks.
@MainActor
final class DetailsRegionScroller {
    /// The region's very top — above its 10 inset. A constant string: no isolation needed.
    nonisolated static let top = "details.region.top"
    private let proxy: ScrollViewProxy

    init(proxy: ScrollViewProxy) { self.proxy = proxy }

    /// Scrolls with animations off: a pre-fill is "without animation".
    func scroll(to id: String, anchor: UnitPoint? = nil) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) { proxy.scrollTo(id, anchor: anchor) }
    }
}

private struct DetailsRegionScrollerKey: EnvironmentKey {
    static let defaultValue: DetailsRegionScroller? = nil
}

extension EnvironmentValues {
    var detailsRegionScroller: DetailsRegionScroller? {
        get { self[DetailsRegionScrollerKey.self] }
        set { self[DetailsRegionScrollerKey.self] = newValue }
    }
}

/// A single-select list of `OptionRow`s. On a pre-fill, a saved row below the fold is scrolled
/// fully into view — the least distance, without animation — and a tap never scrolls.
struct DetailsOptionList: View {
    let step: DetailStep
    let selected: String?
    var prefill: Int = 0
    let onTap: (String) -> Void
    @Environment(\.detailsRegionScroller) private var scroller

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(step.options.enumerated()), id: \.element.value) { i, option in
                OptionRow(label: option.label, selected: option.value == selected,
                          index: i, count: step.options.count) { onTap(option.value) }
                    .id(option.value)
            }
        }
        // A PRE-FILL IS NOT A TAP: new rows per pre-fill start at their value, so only a tap runs
        // the 180 ms change ("pre-fill the saved selection ... without animation").
        .id(prefill)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(DetailsCopy.headlineText(step))
        .onChange(of: prefill, initial: true) { _, _ in
            guard let selected, let scroller else { return }
            // One runloop later, once the push has laid the list out. A nil anchor is the minimal
            // scroll that makes the row fully visible, and none if it already is.
            Task { @MainActor in
                await Task.yield()
                scroller.scroll(to: selected)
            }
        }
    }
}

/// The multi-select list of `CheckRow`s. A pre-fill opens it SCROLLED TO THE TOP.
struct DetailsCheckList: View {
    let step: DetailStep
    let ticked: Set<String>
    var prefill: Int = 0
    let onTap: (String) -> Void

    @Environment(\.detailsRegionScroller) private var scroller

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(step.options.enumerated()), id: \.element.value) { i, option in
                CheckRow(label: option.label, checked: ticked.contains(option.value),
                         index: i, count: step.options.count) { onTap(option.value) }
                    .id(option.value)
            }
        }
        .id(prefill)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(DetailsCopy.headlineText(step))
        .onChange(of: prefill) { _, _ in
            guard let scroller else { return }
            Task { @MainActor in
                await Task.yield()
                scroller.scroll(to: DetailsRegionScroller.top, anchor: .top)
            }
        }
    }
}
