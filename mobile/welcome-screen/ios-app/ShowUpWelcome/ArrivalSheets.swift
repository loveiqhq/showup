// ArrivalSheets.swift
// ShowUp · B, C and D, the check-in sheets (SHOWUP-195), from `CheckInConfirmSheet` / `CheckInDoneSheet`.
// Android's `ArrivalSheets.kt`.
//
// ONE MODAL SHELL (R9). `ArrivalSheetHost` keeps a single `ModalShell` up while the phase is not idle and swaps B / C
// for D inside it: the content crossfades and the height animates (`Motion.progress`) on the same scrim, instead of
// one sheet dropping while another rises. C's error box grows in (`Motion.fast`). With reduced motion every change is
// instant.
//
// BUSY (R7). While the location is read and stored both buttons are inert, the primary sits at alpha .6 -- the 194
// End date sheet's waiting look -- and the scrim and a swipe do nothing.
//
// The buttons are drawn as `EndDateSheet` draws its own: radius 16 / 18, sunset through `SunsetFill` and `shadowCta`,
// never a second button component.

import SwiftUI
import UIKit

/// The guard's title is `max-width: 310` in the reference. Chrome with the bundled Lora measures `meeting location
/// for your` at 310.58, so it breaks before `your` (`meeting location for` / `your date with Leonie?`, as the spec
/// sheet draws it); a text engine that measures the same run at under 310 keeps `your` on the line. One point of room
/// (309) gives Chrome's break; the 310 lines it does not touch (measured in headless Chrome 157, 13 Nov 2026).
/// Tuned to THIS title with the sample name `Leonie`, not a global change: a wider pretty-wrap rule or max-width
/// would move reviewed wraps on other screens for a sub-point engine difference, and another name can still break a
/// word differently.
let confirmTitleMax: CGFloat = 309

/// B / C are one kind, D the other: the key the host's swap animates on.
private enum SheetKind { case confirm, done }

/// B and C while the phase is confirm, D while it is done, nothing while idle. `onNotYet` answers `Not yet` and the
/// scrim / swipe on B and C (ignored while busy); `onClose` answers `Close` and the same on D.
struct ArrivalSheetHost: View {
    let ui: GateUi
    let name: String
    let onYes: () -> Void
    let onNotYet: () -> Void
    let onClose: () -> Void
    let onUndo: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if ui.phase != .idle {
            let kind: SheetKind = { if case .done = ui.phase { return .done } else { return .confirm } }()
            ModalShell(
                label: kind == .done ? ArrivalCopy.doneTitle : ArrivalCopy.confirmTitle(name),
                onDismiss: { _ in
                    switch ui.phase {
                    case .confirm(_, _, let busy, _): if !busy { onNotYet() }
                    case .done: onClose()
                    case .idle: break
                    }
                },
                contentPadding: EdgeInsets(top: 14, leading: 24, bottom: 24, trailing: 24)
            ) {
                ZStack {
                    switch ui.phase {
                    case .confirm(_, let locError, let busy, let note):
                        CheckInConfirmSheet(name: name, note: note, locError: locError, busy: busy,
                                            onYes: onYes, onNotYet: onNotYet)
                            .transition(.opacity)
                    case .done:
                        CheckInDoneSheet(name: name, onClose: onClose, onUndo: onUndo)
                            .transition(.opacity)
                    case .idle:
                        EmptyView()
                    }
                }
                .frame(maxWidth: .infinity)
                .fitAnchor(FitAnchorID.arrivalSheet)
                .animation(reduceMotion ? nil : Motion.brand(Motion.progress), value: kind)
            }
            .fitModal()
        }
    }
}

/// The 80 circle, violet 12%, with its icon (34 / 2.2) and the 8 above it.
/// `heart-filled` is fill AND stroke here, as the kit's SVG draws it (the stroke is inherited from the `<svg>`); the stroke
/// of the map pin is its own line either way.
private struct SheetCircle: View {
    let icon: BrandIcon

    var body: some View {
        BrandIconView(icon: icon, size: 34, stroke: 2.2, tint: .liqPurple, fillAndStroke: true)
            .frame(width: 80, height: 80)
            .background(Circle().fill(Color.liqPurple.opacity(0.12)))
            .fitAnchor(FitAnchorID.arrivalCircle)
            .padding(.top, 8)
            .accessibilityHidden(true)
    }
}

/// Lora 700 26 / 1.15, -0.01em, centred, pretty, at most `maxWidth`.
private struct SheetTitle: View {
    let text: String
    let maxWidth: CGFloat

    var body: some View {
        // No word broken between two letters at any type size: where the widest word is wider than the box the title
        // is drawn at the largest smaller size where it fits (`FitWords`, never under the default size). At the default
        // size nothing changes: the first try is the title as it was, at its max width.
        FitWords(text: text, anchor: FitAnchorID.arrivalTitleWord, font: { s in F.lora(26 * s, bold: true) }) { s in
            var title = PrettyText(text, font: chatFont(PS.loraBold, 26 * s, .bold), lineHeight: 26 * 1.15 * s, color: .liqFg,
                                   centered: true)
            title.tracking = -0.01 * 26 * s
            return title
        }
        .frame(maxWidth: maxWidth)
        .fitAnchor(FitAnchorID.arrivalTitle)
        .accessibilityAddTraits(.isHeader)
    }
}

/// A sunset button, radius 18, h 56, 16.5 / 700 white, under `--liq-shadow-cta`. Waiting reads at alpha .6.
private struct SunsetSheetButton: View {
    let label: String
    let waiting: Bool
    let action: () -> Void
    let anchor: String

    var body: some View {
        Button(action: action) {
            Text(label).font(F.manrope(16.5, .bold)).foregroundColor(.white)
                .frame(maxWidth: .infinity, minHeight: 56)
                // The glow behind, then the reference's 135deg sunset clipped to the radius-18 shape.
                .background(SunsetFill(shape: RoundedRectangle(cornerRadius: 18), shadows: shadowCta))
                .opacity(waiting ? 0.6 : 1)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(anchor)
        .disabled(waiting)
    }
}

/// B and C: circle 80 over the title (Lora 700 26 / 1.15, -0.01em, 16 above, at most `confirmTitleMax` = 309, pretty), the optional
/// `note` (14 / 1.5 muted, 12 under the title), C's `SheetErrorBox` (14 under the title, 12 under a note, 16 above the
/// buttons), `Not yet` (h 50, radius 16, white, soft border, 14.5 / 700 muted) and the primary 10 below (h 56, radius
/// 18, sunset, `Yes, I've arrived` / `Try again`).
///
/// The spacing is the reference's margins summed: title 22 below less the error's -8 = 14; note -10 above and 20
/// below, so 12 and 12.
struct CheckInConfirmSheet: View {
    let name: String
    let note: String?
    let locError: Bool
    let busy: Bool
    let onYes: () -> Void
    let onNotYet: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            SheetCircle(icon: .mapPin)
            SheetTitle(text: ArrivalCopy.confirmTitle(name), maxWidth: confirmTitleMax)
                .padding(.top, 16)
            if let note {
                PrettyText(note, font: chatFont(PS.manropeRegular, 14, .regular), lineHeight: 14 * 1.5, color: .liqMuted,
                           centered: true)
                    .frame(maxWidth: 310)
                    .padding(.top, 12)
            }
            // 14 (or 12) above + 8 below the title block = the 22 (or 20) a calm sheet has; with the box, 16 below it.
            if locError {
                SheetErrorBox(message: ArrivalCopy.locError)
                    .fitAnchor(FitAnchorID.arrivalError)
                    .padding(.top, note == nil ? 14 : 12)
                    .padding(.bottom, 16)
                    .transition(.opacity)
            } else {
                Color.clear.frame(height: (note == nil ? 14 : 12) + 8)
            }
            Button(action: onNotYet) {
                Text(ArrivalCopy.notYet).font(F.manrope(14.5, .bold)).foregroundColor(.liqMuted)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.liqElevated))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.liqBorderSoft, lineWidth: 1))
            }
            .buttonStyle(PressScale())
            .fitTapArea()
            .fitAnchor(FitAnchorID.arrivalNotYet)
            .disabled(busy)
            SunsetSheetButton(label: locError ? ArrivalCopy.tryAgain : ArrivalCopy.yes, waiting: busy, action: onYes,
                              anchor: FitAnchorID.arrivalYes)
                .padding(.top, 10)
        }
        .frame(maxWidth: .infinity)
        .animation(reduceMotion ? nil : Motion.brand(Motion.fast), value: locError)
    }
}

/// D: the same shell, circle 80 with `heart-filled`, the title (16 above, 8 below, at most 300), the body (15 / 1.5
/// muted, at most 300, 22 below), `Close` (h 56, radius 18, sunset) and `Undo check-in` 6 below (min h 44, pad 0 16,
/// Manrope 600 14 muted, no fill, no border).
struct CheckInDoneSheet: View {
    let name: String
    let onClose: () -> Void
    let onUndo: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            SheetCircle(icon: .heartFilled)
            SheetTitle(text: ArrivalCopy.doneTitle, maxWidth: 300)
                .padding(.top, 16)
            // Whole words at every type size, as the title (`FitWords`); unchanged at the default size.
            FitWords(text: ArrivalCopy.doneBody(name), anchor: FitAnchorID.arrivalBodyWord,
                     font: { s in F.manrope(15 * s, .regular) }) { s in
                PrettyText(ArrivalCopy.doneBody(name), font: chatFont(PS.manropeRegular, 15 * s, .regular),
                           lineHeight: 15 * 1.5 * s, color: .liqMuted, centered: true)
            }
                .frame(maxWidth: 300)
                .fitAnchor(FitAnchorID.arrivalBody)
                .padding(.top, 8)
            SunsetSheetButton(label: ArrivalCopy.close, waiting: false, action: onClose, anchor: FitAnchorID.arrivalClose)
                .padding(.top, 22)
            Button(action: onUndo) {
                Text(ArrivalCopy.undo).font(F.manrope(14, .semibold)).foregroundColor(.liqMuted)
                    .padding(.horizontal, 16)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                    .fitTapArea()
            }
            .buttonStyle(PressScale())
            .fitAnchor(FitAnchorID.arrivalUndo)
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview("Arrival · B") {
    ArrivalSheetHost(ui: GateUi(phase: .confirm(entry: .dateScreen, locError: false, busy: false, note: nil)),
                     name: "Leonie", onYes: {}, onNotYet: {}, onClose: {}, onUndo: {})
}
#Preview("Arrival · C") {
    ArrivalSheetHost(ui: GateUi(phase: .confirm(entry: .dateScreen, locError: true, busy: false, note: nil)),
                     name: "Leonie", onYes: {}, onNotYet: {}, onClose: {}, onUndo: {})
}
#Preview("Arrival · D") {
    ArrivalSheetHost(ui: GateUi(phase: .done(entry: .dateScreen)),
                     name: "Leonie", onYes: {}, onNotYet: {}, onClose: {}, onUndo: {})
}
