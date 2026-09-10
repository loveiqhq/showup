//  CodeSlotRow.swift
//  ShowUp · the six-box code row, shared by phone and email verification
//
//  Mirrors `designsystem/CodeSlotRow.kt`.
//
//  WHAT MAKES THIS ONE COMPONENT AND NOT TWO
//
//  Both screens draw six 49 x 62 boxes at radius 14 with one digit each, and on both the row is
//  decoration for a SINGLE text field behind it rather than six fields. That last part is the
//  reason it is worth sharing: six fields would mean six focus targets, and then backspace, paste
//  and one-time-code autofill each have to be taught to hop between them, which is where per-digit
//  code inputs usually break.
//
//  WHAT THE TWO SCREENS GENUINELY DIFFER ON
//
//  Two things, both from their own spec sheets rather than drift:
//
//   - `halo`. SHOWUP-153's sheet draws a 4pt ring outside the stroke on the active and error
//     slots. SHOWUP-143's does not. Off by default so the older screen keeps what was approved.
//   - `caretBlinks`. 153's sheet specifies a blinking caret and says why: "A static bar reads as a
//     filled slot at a glance — the one thing this row must never be ambiguous about." 143 shipped
//     a static bar. Left as a parameter rather than changed on both, because altering a screen in
//     PO Acceptance is a product call; the concern is recorded here so it can be taken.

import SwiftUI

struct CodeSlotRow: View {
    let digits: String
    /// The whole row turns danger, and no slot is active — the error is the row, not a position.
    var error: Bool = false
    var slotWidth: CGFloat = 49
    var slotHeight: CGFloat = 62
    var gap: CGFloat = 8
    var halo: Bool = false
    var caretBlinks: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // A TimelineView, not a repeating animation and not a Timer.
        //
        // The spec is `su-caret-blink 1s steps(2, end)` — a HARD on/off, which is not what an
        // interpolating animation produces: `.repeatForever(autoreverses:)` fades, and a fading
        // caret reads as a dimmed digit rather than a cursor. A Timer would work but has to be
        // owned, cancelled and kept off the main actor's way, and `DispatchQueue` is banned by
        // audit/check-swift-concurrency.py. TimelineView is the declarative version: the schedule
        // drives the redraw and the phase is computed, so there is nothing to leak.
        //
        // Paused entirely under Reduce Motion — a blinking element is exactly what that setting
        // is for.
        Group {
            if caretBlinks && !reduceMotion {
                TimelineView(.periodic(from: .now, by: 0.5)) { timeline in
                    row(caretOn: Int(timeline.date.timeIntervalSinceReferenceDate * 2) % 2 == 0)
                }
            } else {
                row(caretOn: true)
            }
        }
    }

    private func row(caretOn: Bool) -> some View {
        HStack(spacing: gap) {
            ForEach(0..<6, id: \.self) { i in
                slot(index: i, caretOn: caretOn)
            }
        }
    }

    private func slot(index i: Int, caretOn: Bool) -> some View {
        let ch: Character? = i < digits.count ? Array(digits)[i] : nil
        let active = !error && i == digits.count && digits.count < 6

        return ZStack {
            RoundedRectangle(cornerRadius: Radius.control)
                .fill(error ? Color.liqDanger.opacity(0.04) : Color.liqElevated)
            RoundedRectangle(cornerRadius: Radius.control)
                .strokeBorder(
                    error ? Color.liqDanger
                        : active ? Color.liqPurple
                        : ch != nil ? Color.liqFg.opacity(0.32)
                        : Color.liqSubtle,
                    lineWidth: 1.5)
            if let ch {
                Text(String(ch))
                    .font(.custom(PS.loraBold, size: 30))
                    .monospacedDigit()
                    .foregroundColor(error ? .liqDangerDigit : .liqFg)
            } else if active {
                Rectangle()
                    .fill(Color.liqPurple)
                    .frame(width: 2, height: halo ? 26 : 28)
                    .opacity(caretOn ? 1 : 0)
            }
        }
        .frame(width: slotWidth, height: slotHeight)
        // The ring is OUTSIDE the stroke, so it is an overlay on a slightly larger shape rather
        // than a shadow: a shadow has a light source and would not be even on all four sides.
        .overlay(
            RoundedRectangle(cornerRadius: Radius.control)
                .strokeBorder(
                    halo ? (error ? Color.liqDanger.opacity(0.10)
                                  : active ? Color.liqPurple.opacity(0.10) : .clear)
                         : .clear,
                    lineWidth: 4)
                .padding(-4)
        )
        .shadow(color: Color(hex: 0x2E0147).opacity(0.04), radius: 2, y: 1)
        .animation(.easeOut(duration: Motion.fast), value: error)
        .animation(.easeOut(duration: Motion.fast), value: digits.count)
    }
}
