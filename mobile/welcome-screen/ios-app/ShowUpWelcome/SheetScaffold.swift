//
//  SheetScaffold.swift
//  ShowUp · the chrome every bottom sheet in the product shares
//
//  ─────────────────────────────────────────────────────────────────────────────
//  EXTRACTED FROM ProfilePrompts ON 17 SEPTEMBER 2026
//  ─────────────────────────────────────────────────────────────────────────────
//
//  These three lived as private types inside a screen file, which is the shape of the mistake this
//  project already paid for once: `PillButton` sat in `WelcomeShell`, whoever wrote the tutorial's
//  CTA had no reason to open the sign-up flow's shell, and so they wrote their own — and the copy
//  nobody was looking at drifted four ways over three weeks.
//
//  SHOWUP-161 is the second user. Its acceptance criteria describe this chrome as already shared —
//  "Sheet chrome is the shared one — radius 32 32 0 0, 40 x 4 grabber, 36px close X, sheet-rise
//  360ms, max-height 660 with the scroll mask" — so the only way to satisfy it was to make that
//  true. Nothing about the behaviour changed in the move, and the Kotlin twin was extracted the
//  same afternoon for the same reason.
//
//  The dismissal vocabulary is the registry's §23 and the reason it is one set: `close` is the X,
//  `backdrop` is the scrim, `swipe` is the drag, and `system_back` is the Android gesture, which is
//  NOT the same act as the X and must never be folded into it (decision 32). iOS never produces
//  `system_back` — it has no such gesture — and the value stays in the shared enum anyway, because
//  a tracking vocabulary that differs per platform is two vocabularies.
//

import SwiftUI

/// Copy shared by every sheet.
enum SheetCopy {
    static let dismiss = "Dismiss"
}

/// The sheet's top corners. `32 32 0 0` — it is docked to the bottom edge and has no lower ones.
let sheetCornerRadius: CGFloat = 32

/// The tallest a sheet may be, from the reference's `max-height: 660`.
///
/// Past this the list inside scrolls, under the fade mask, rather than the sheet growing into the
/// screen it is docked over.
let sheetMaxHeight: CGFloat = 660

/// The drag grabber. Decorative: a sheet is dismissed by the X or the scrim, not by this.
struct SheetGrabber: View {
    var body: some View {
        Capsule()
            .fill(Color.liqFg.opacity(0.18))
            .frame(width: 40, height: 4)
            .accessibilityHidden(true)
    }
}

/// The close X.
///
/// DRAWS 36 AND ANSWERS AT 44. The reference specifies a 36 control; 36 fails the floor every
/// tappable thing in this app is held to, and the Android fit harness flagged it at all seventeen
/// sizes the first time it was built that way. `minTapTarget` grows the hit area and nothing
/// visible moves.
struct SheetCloseButton: View {
    let onClose: () -> Void

    var body: some View {
        Button(action: onClose) {
            ZStack {
                Circle().fill(Color.liqFg.opacity(0.04)).frame(width: 36, height: 36)
                BrandIconView(icon: .close, size: 20, stroke: 1.8, tint: .liqFg)
            }
            .minTapTarget(alignment: .center)
        }
        .buttonStyle(PressScale())
        .accessibilityLabel(Text(SheetCopy.dismiss))
    }
}

/// The scrim and the rising surface both sheets sit in.
///
/// `sheet-rise` is 28 up and 0.85 → 1 opacity over `Motion.sheet` — a shared keyframe, not a
/// per-sheet animation, and skipped entirely when the device asks for no motion.
/// How far a sheet has to be pushed down before letting go dismisses it.
///
/// A thumb's travel: far enough that a scroll inside the sheet cannot trigger it by accident, near
/// enough that the gesture does not feel resisted. Local rather than a token -- it is this
/// gesture's threshold and nothing else's, and `Spacing` holds no value meaning "a deliberate
/// drag".
///
/// AT FILE SCOPE, not inside `SheetScaffold`: that type is generic over its content, and Swift has
/// no storage for a static on a generic type.
let sheetSwipeDismiss: CGFloat = 120

struct SheetScaffold<Content: View>: View {
    let onDismiss: (SheetDismissMethod) -> Void
    @ViewBuilder let content: () -> Content

    @State private var shown = false
    /// How far the sheet has been pushed down by the finger now on it.
    ///
    /// SWIPE-DOWN, which the ticket asks for twice - "closing the sheet by X, scrim tap or swipe
    /// keeps what was typed", and again in the tracking criteria - and which nothing here
    /// implemented. A bottom sheet that cannot be pushed down is wrong on a phone regardless of
    /// the ticket. It only counts as a dismissal past a threshold, so a small nudge springs back.
    @State private var drag: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion


    var body: some View {
        ZStack(alignment: .bottom) {
            Color.liqFg.opacity(0.42)
                .ignoresSafeArea()
                // THE SCRIM IS `backdrop`, NOT `close`. Four values, four different acts:
                // the registry unified them on 16 September 2026 precisely because three
                // spellings of the same four acts had drifted apart, and folding two of them
                // together here would put the drift back inside one screen.
                .onTapGesture { onDismiss(.backdrop) }
                .accessibilityLabel(Text(SheetCopy.dismiss))
                .accessibilityAddTraits(.isButton)

            content()
                .offset(y: (shown ? 0 : 28) + drag)
                .opacity(shown ? 1 : 0.85)
                .gesture(
                    DragGesture()
                        // Downwards only. Dragging a bottom sheet UP would detach it from the
                        // edge it is docked to, and the ticket forbids positioning either sheet
                        // by a top offset.
                        .onChanged { drag = max(0, $0.translation.height) }
                        .onEnded { value in
                            if value.translation.height > sheetSwipeDismiss {
                                onDismiss(.swipe)
                            }
                            drag = 0
                        }
                )
                // The keyboard is not ours, and its height is not knowable. The safe-area inset
                // for the keyboard is what keeps the field, the status row and Save above it
                // without anybody guessing — and SwiftUI applies it by default, which is why
                // there is no modifier here and no number anywhere.
                .onAppear {
                    if reduceMotion {
                        shown = true
                    } else {
                        withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: Motion.sheet)) {
                            shown = true
                        }
                    }
                }
        }
    }
}
