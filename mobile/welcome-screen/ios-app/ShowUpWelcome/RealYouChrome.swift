//
//  RealYouChrome.swift
//  ShowUp · the shell every screen in "The real you" shares (SHOWUP-156, SHOWUP-158)
//
//  The Swift port of `profile/RealYouChrome.kt`. Read that file's header for the argument; the
//  design decisions are identical and are not restated here. What follows is only what differs
//  because this is SwiftUI.
//

import SwiftUI

/// Copy shared by every screen in the group.
enum RealYouCopy {
    static let section = "The real you"
}

/// A step of "The real you".
///
/// `media` is declared and not built. It is here because `count` has to be 3 rather than 2 — the
/// progress bar shows a step the user has not reached yet, which is the whole point of a progress
/// bar — and because declaring it is how the count and the last segment stay in agreement.
///
/// CONFIRMED BY THE PRODUCT SIDE ON 16 SEPTEMBER 2026: media is planned and arrives in a later
/// ticket. The third segment is not speculative and must not be dropped to make the bar match the
/// screens that exist today — see `audit/CONFLICTS-2026-08-27.md` E8.
enum RealYouStep: String, CaseIterable {
    case photos
    case prompts
    /// Voice and video. Not built; see SHOWUP-158's "out of scope".
    case media

    /// Which segment is filled.
    var progressSegment: Int { (Self.allCases.firstIndex(of: self) ?? 0) + 1 }

    /// `step_id` from the taxonomy's §2 vocabulary.
    ///
    /// `media` is NOT a registry value: §2 carries `media_voice` and `media_video` as two separate
    /// steps. Which of them a single media screen would report is a question for that ticket, so
    /// this returns the id the group's own tickets use and the decision is recorded rather than
    /// guessed. Nothing emits it yet.
    var stepId: String { rawValue }

    /// `step_index` for the funnel.
    ///
    /// TAKEN FROM THE TICKETS, AND §2 DISAGREES ON ONE OF THEM. Registry 1.3.0 gives `photos`
    /// step_index 1 with the note "The real you · step 1 of 4" — right index, stale count — and
    /// gives `prompts` step_index **10**, under "Share some details · step 10", which is where
    /// prompts sat before it moved into this group. SHOWUP-158 says the corrected indices "must be
    /// added before the ticket is picked up"; they have not been. The tickets are authoritative on
    /// behaviour, so the position in THIS group ships and the mismatch is recorded.
    var stepIndex: Int { progressSegment }

    /// Where the backdrop's two orbs sit on this screen.
    ///
    /// Photos puts both at the top because its footer carries a gradient mask a low orb would
    /// muddy; prompts splits them because its footer is a bare CTA row.
    var orbPlacement: OrbPlacement { self == .prompts ? .realYouSplit : .realYouTop }

    /// How many segments the group's bar has.
    ///
    /// THREE, not four. `ScreenProfileVerify` was dropped from the MVP with SHOWUP-158 and gets no
    /// segment. If it returns, this constant changes and nothing else does.
    ///
    /// And three, not two: media is a later ticket, confirmed 16 September 2026. Nothing routes to
    /// `.media` and `resumePoint` has no case for it, so the unbuilt step costs a segment and
    /// nothing else.
    static let count = 3
}

/// The group's scaffold: four fixed regions and one scrolling one.
///
///     status bar        safe-area inset
///     AppHeader         52, fixed
///     StepProgress      fixed on photos, part of the scroll on prompts — see `progressFixed`
///     content           THE ONLY SCROLLING REGION
///     footer            fixed, the screen's own padding and background
///     home indicator    safe-area inset
///
/// NO ABSOLUTE Y ANYWHERE, and it breaks harder here than elsewhere in the flow: a fixed Y inside
/// the scrolling region is wrong the moment the user drags.
///
/// THE BACKDROP IS A SIBLING OF THE COLUMN, NOT ITS BACKGROUND, so the orbs run full-bleed behind
/// the status bar and the home indicator with no seam — the same arrangement, and the same reason,
/// as `WelcomeScaffold`.
///
/// - Parameter progressFixed: whether the bar sits above the scroll. True on photos, false on
///   prompts, and that is a design decision rather than an oversight: "this screen carries more
///   above the fold and the bar is not worth the 26px. Do not 'fix' it into a third fixed row."
///   When false, the screen renders `StepProgress` itself as the first thing in `content`.
struct RealYouScaffold<Content: View, Footer: View>: View {
    let step: RealYouStep
    var onBack: () -> Void = {}
    var progressFixed: Bool = true
    @ViewBuilder var content: () -> Content
    @ViewBuilder var footer: () -> Footer

    var body: some View {
        ZStack {
            Color.liqCream.ignoresSafeArea()
            // Lower intensity than the welcome flow's 0.32 / 0.28: this group has content all the
            // way down rather than a hero and a band, so the orbs are atmosphere behind it rather
            // than the subject. No peach wash — that is the bridge screens' and Startup's, and it
            // would sit straight over the headline here.
            WelcomeBackdrop(peachWash: false, placement: step.orbPlacement,
                            orangeAlpha: 0.18, violetAlpha: 0.16)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                AppHeader(title: RealYouCopy.section, leading: .back, onBack: onBack)

                if progressFixed {
                    // padding '4px 24px 14px' from the reference's progress wrapper.
                    StepProgress(steps: RealYouStep.count, current: step.progressSegment)
                        .padding(.init(top: Spacing.xs, leading: Spacing.screenGutter,
                                       bottom: 14, trailing: Spacing.screenGutter))
                }

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) { content() }
                        // padding '4 24 0'. The floor is 0 because each screen ends its own
                        // content with a tail spacer sized to clear its footer.
                        .padding(.init(top: Spacing.xs, leading: Spacing.screenGutter,
                                       bottom: 0, trailing: Spacing.screenGutter))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                footer()
            }
        }
    }
}

/// A refusal toast, drawn ABOVE the footer and occupying none of it.
///
/// Both tickets specify `bottom: 100%` of the footer and both say why: "so the footer keeps
/// identical geometry when it is idle and nothing reflows when the toast appears". A toast that
/// took part in the footer's layout would push the CTA down by its own height every time the user
/// pressed a button that refused — the CTA moving in response to being pressed, on the one press
/// where the user most needs it to stay put.
///
/// SwiftUI expresses that as an `overlay` aligned to the footer's top edge with an alignment guide
/// that puts the toast's BOTTOM there: an overlay takes no part in its host's layout, so the
/// footer measures identically whether the toast is up or not.
///
/// VISIBILITY, NOT REMOVAL. The view stays in the hierarchy and fades, so the exit animation runs;
/// removing it would cut instead.
struct RefusalToast: View {
    let visible: Bool
    let icon: BrandIcon
    let message: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: Spacing.md) {
            BrandIconView(icon: icon, size: 15, stroke: 2.2, tint: .white)
            Text(message)
                .font(F.manrope(13, .semibold))
                .foregroundColor(.white)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, Spacing.lg)
        .frame(maxWidth: 300)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.liqFg))
        .shadow(color: Color(hex: 0x2E0147).opacity(0.22), radius: 11, y: 8)
        .opacity(visible ? 1 : 0)
        // 6 up on the way in, from the reference's `translateY(6px)` resting state.
        .offset(y: visible ? 0 : 6)
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: visible)
        // Announced when up, and absent from the tree when not. A toast that stays readable while
        // invisible is a control VoiceOver can land on and a sighted user cannot see.
        .accessibilityHidden(!visible)
        .accessibilityAddTraits(.updatesFrequently)
        .allowsHitTesting(false)
    }
}

/// Places a `RefusalToast` immediately above the view it is attached to, taking no space in it.
extension View {
    func refusalToast(_ toast: RefusalToast) -> some View {
        overlay(alignment: .top) {
            toast.alignmentGuide(.top) { $0[.bottom] }
        }
    }
}

/// When a refusal toast is up.
///
/// A holder rather than a raw boolean because the AUTO-DISMISS is the part that is easy to get
/// wrong: a second refused press while the first toast is still up has to restart the 2.6 seconds
/// rather than be swallowed by the timer already running. Cancelling and replacing the task is
/// what makes that work.
@MainActor
@Observable
final class RefusalToastState {
    private(set) var visible = false
    private var timer: Task<Void, Never>?

    func show() {
        visible = true
        timer?.cancel()
        timer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Motion.toast * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.visible = false
        }
    }

    // No deinit: the task captures self weakly, so a state that goes away while a toast is up
    // simply has nothing left to update. A deinit cannot touch main-actor state under strict
    // concurrency anyway.
}
