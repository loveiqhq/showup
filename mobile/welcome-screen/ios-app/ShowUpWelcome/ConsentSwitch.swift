//
//  ConsentSwitch.swift
//  ShowUpWelcome · the consent switch (SHOWUP-163)
//
//  ───────────────────────────────────────────────────────────────────────────
//  THE THUMB ANIMATES, AND THAT IS THE WHOLE REASON THIS FILE EXISTS
//  ───────────────────────────────────────────────────────────────────────────
//
//  The kit's `ConsentToggle` moves its thumb with `justify-content: flex-start | flex-end` and then
//  declares `transition: transform 220ms` on it. Those two never meet: flex realigns the child
//  instantly and there is no transform to interpolate, so the transition is dead code and the thumb
//  jumps. The reference file's own header names it as a KNOWN KIT SHORTCUT and says not to copy it:
//
//      "ConsentToggle moves its thumb with justify-content, so the transform transition never runs.
//       Build a real switch whose thumb animates."
//
//  The Profile 10 ticket then makes it an acceptance criterion — "The switch thumb **animates**
//  (the kit's `justify-content` shortcut is not copied)" — so the thumb here is placed by an
//  ANIMATED OFFSET and the track colour crossfades on the same curve.
//
//  ───────────────────────────────────────────────────────────────────────────
//  NOT `Toggle`, AND NOT `.switch` STYLE
//  ───────────────────────────────────────────────────────────────────────────
//
//  SwiftUI has a switch. It is the wrong one twice over. Its size, corner and thumb are the
//  system's and change between OS versions, so the 51 × 31 the reference specifies would be a
//  coincidence rather than a value; and `Toggle` writes through a `Binding` the moment it is
//  touched, which is exactly what this control must not do — switching OFF here opens a
//  confirmation and the state does not move until that is confirmed.
//
//  So the value is one-way: the view takes `on` and reports what the user ASKED for. The caller
//  decides what happens, which is the difference between a toggle and a request.
//
//  ───────────────────────────────────────────────────────────────────────────
//  51 × 31 DRAWN, 44 × 44 TOUCHED
//  ───────────────────────────────────────────────────────────────────────────
//
//  The second shortcut the reference names: "ConsentToggle is 51x31. Its hit target must be >= 44x44
//  (pad it)." 51 × 31 is the iOS system switch's size and is what makes it read as a real permission
//  control rather than a brand widget, so the drawing keeps it and the touch area is padded around
//  it. This project has shipped a 23pt target inside a 56pt row before; the padding is not
//  decoration.
//

import SwiftUI

/// The reference's own numbers. `--liq-success` on, a 14%-of-foreground track off.
private enum SwitchMetrics {
    static let trackWidth: CGFloat = 51
    static let trackHeight: CGFloat = 31
    static let thumb: CGFloat = 27
    static let trackPadding: CGFloat = 2

    /// 51 − 27 − 2 − 2. THE ANIMATED VALUE IS A POSITION, not an alignment.
    ///
    /// `let`, not a computed `var`: `check-swift-concurrency.py` fails on any `static var` because
    /// Swift 6 rejects unisolated mutable global state, and a computed one is indistinguishable
    /// from a stored one at the grep level. A stored static is lazily initialised, so deriving it
    /// from the three above costs nothing and keeps the arithmetic visible.
    static let travel: CGFloat = trackWidth - thumb - trackPadding - trackPadding
}

struct ConsentSwitch: View {
    /// What the switch SHOWS. Not a binding — see the file header.
    let on: Bool

    /// The visible label this control belongs to, read by VoiceOver in place of a redundant one.
    let label: String

    /// What the user asked for, which is `!on`. The caller decides whether it happens.
    var onChange: (Bool) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// `cubic-bezier(.22, 1, .36, 1)` — the kit's own easing, at `Motion.confirm`.
    ///
    /// Nil under Reduce Motion, which is the setting's whole point: the thumb still moves, it just
    /// arrives rather than travels.
    private var curve: Animation? {
        reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.confirm)
    }

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(on ? Color.liqSuccess : Color.liqFg.opacity(0.14))
                .frame(width: SwitchMetrics.trackWidth, height: SwitchMetrics.trackHeight)

            Circle()
                .fill(Color.white)
                // `0 2px 5px rgba(20,12,28,0.28)`.
                .shadow(color: Color(hex: 0x140C1C).opacity(0.28), radius: 2.5, x: 0, y: 2)
                .frame(width: SwitchMetrics.thumb, height: SwitchMetrics.thumb)
                .offset(x: SwitchMetrics.trackPadding + (on ? SwitchMetrics.travel : 0))
        }
        .animation(curve, value: on)
        // THE TAP TARGET, not the drawing. The track is 31 tall and the floor is 44, so the
        // switch grows around it rather than the drawing growing. `contentShape` is what makes
        // the padded area actually receive the touch — without it the frame is only layout and
        // the hit area stays the capsule's, which is the 23-inside-56 bug this project has
        // already shipped once.
        .frame(
            width: max(SwitchMetrics.trackWidth, ComponentSizes.minTapTarget),
            height: ComponentSizes.minTapTarget
        )
        .contentShape(Rectangle())
        .onTapGesture { onChange(!on) }
        // ONE CONTROL TO VOICEOVER, announcing its state. `.isButton` would say nothing about
        // whether it is on, which for a consent switch is the entire content of the control.
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isToggle)
        .accessibilityLabel(label)
        .accessibilityValue(on ? "On" : "Off")
        .accessibilityAction { onChange(!on) }
    }
}

#Preview("Consent switch") {
    VStack(spacing: 24) {
        ConsentSwitch(on: true, label: "Push notifications")
        ConsentSwitch(on: false, label: "Push notifications")
    }
    .padding(40)
}
