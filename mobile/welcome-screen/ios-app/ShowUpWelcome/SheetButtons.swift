// SheetButtons.swift
// ShowUp · the date flow's sheet buttons: a sunset primary (radius 18) and a white secondary (radius 16)
//
// NOT `PrimaryButton`: that one is a pill, and these two are the rounded-rectangle pair the date sheets draw (the
// arrival confirm, the Date 05 pickers). The arrival sheet held the primary privately; a second private copy for the
// pickers would be the drift this project has paid for, so it lives here and both call it. Android's
// `SheetButtons.kt`.

import SwiftUI

/// The sunset button, radius 18, h 56, 16.5 / 700 white, under `--liq-shadow-cta`. `enabled` false reads at alpha
/// .45 with no glow (the picker's `Pick a time`); `waiting` reads at .6 with the glow, inert, as a send in flight
/// does (the 194 End date and 195 guard waits). Neither is tappable.
struct SheetPrimaryButton: View {
    let label: String
    var enabled = true
    var waiting = false
    /// The fit tests' name for this button's frame, when one is measured.
    var anchor: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(F.manrope(16.5, .bold)).foregroundColor(.white)
                .frame(maxWidth: .infinity, minHeight: 56)
                // The glow behind, then the reference's 135deg sunset clipped to the radius-18 shape.
                .background(SunsetFill(shape: RoundedRectangle(cornerRadius: 18), shadows: enabled ? shadowCta : []))
                .opacity(enabled ? (waiting ? 0.6 : 1) : 0.45)
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(ifGiven: anchor)
        .disabled(!enabled || waiting)
    }
}

/// The white secondary, h 50 (a minimum, so the largest type grows it), radius 16, the soft border, 14.5 / 700 muted.
struct SheetSecondaryButton: View {
    let label: String
    var inert = false
    /// The fit tests' name for this button's frame, when one is measured.
    var anchor: String? = nil
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16)
        Button(action: action) {
            Text(label).font(F.manrope(14.5, .bold)).foregroundColor(.liqMuted)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(shape.fill(Color.liqElevated))
                .overlay(shape.strokeBorder(Color.liqBorderSoft, lineWidth: 1))
        }
        .buttonStyle(PressScale())
        .fitTapArea()
        .fitAnchor(ifGiven: anchor)
        .disabled(inert)
    }
}
