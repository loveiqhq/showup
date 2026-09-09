//  FloatingField.swift
//  ShowUp · the outlined input whose label rides up and notches the border
//
//  WHY THIS IS NOT InputField
//
//  `InputField` is the welcome flow's field: 56 tall, a placeholder, no label of its own, and its
//  whole reason for existing is that the control fills its box. This one is 64 tall, carries a
//  floating label that cuts the stroke, and has valid / error affordances the phone field never
//  has. They share a rounded outlined box and nothing else. Folding them together would mean a
//  variant flag on every one of those differences, which is the "everything input" the design
//  system has been careful to avoid.
//
//  What they DO share is the rule the phone field was built to enforce: the whole box takes a tap.
//
//  THE LABEL NOTCHES THE STROKE, IT DOES NOT BREAK IT
//
//  The border is drawn unbroken and the label sits on top of it with a patch of the screen colour
//  behind. In the error state that patch switches from white to `liqCream`, because the field's own
//  fill becomes a 4% danger wash and a white patch would then read as a hole punched in it.

import SwiftUI

struct FloatingField: View {
    @Binding var value: String
    /// Rides the border. Also the accessibility name — an unlabelled input cannot be announced.
    let label: String
    let placeholder: String
    var valid: Bool = false
    var error: Bool = false
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .words
    var contentType: UITextContentType? = nil
    var onSubmit: () -> Void = {}

    @FocusState private var focused: Bool

    /// Lifted whenever focused OR non-empty. It only drops back to rest if the field is blurred
    /// while empty — which on these screens never happens, because they auto-focus on arrival.
    private var active: Bool { focused || !value.isEmpty }

    private var borderColor: Color {
        if error { return .liqDanger }
        if focused { return .liqPurple }
        if valid { return Color.liqSuccess.opacity(0.55) }
        return .liqBorder
    }
    private var haloColor: Color {
        if error { return Color.liqDanger.opacity(0.10) }
        if focused { return Color.liqPurple.opacity(0.10) }
        return .clear
    }
    private var labelColor: Color {
        if error { return .liqDangerFg }
        if focused { return .liqPurple }
        if valid { return .liqSuccessFg }
        return .liqFaint
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: Spacing.md) {
                TextField("", text: $value, prompt: active ? Text(placeholder)
                    .foregroundColor(.liqFaint) : nil)
                    .focused($focused)
                    .font(F.manrope(17, .medium))
                    .foregroundColor(.liqFg)
                    .tint(.liqPurple)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled(keyboard == .emailAddress)
                    .textContentType(contentType)
                    .submitLabel(.go)
                    .onSubmit(onSubmit)
                    .accessibilityLabel(Text(label))
                    // The counterpart of Android's fillMaxHeight: the whole 64 takes the tap, not
                    // the line box the text happens to occupy.
                    .frame(maxHeight: .infinity)

                if error {
                    DangerGlyph(size: 22, glyphSize: 13)
                } else if valid {
                    ZStack {
                        Circle().fill(Color.liqSuccess.opacity(0.14))
                        CheckGlyph(size: 13, color: .liqSuccessFg, lineWidth: 3)
                    }
                    .frame(width: 22, height: 22)
                }
            }
            .padding(.horizontal, 18)
            .frame(height: 64)
            .background(
                RoundedRectangle(cornerRadius: Radius.control)
                    .fill(error ? Color.liqDanger.opacity(0.04) : Color.liqElevated)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Radius.control)
                    .strokeBorder(borderColor, lineWidth: 1.5)
            )
            // The halo is a 4pt ring OUTSIDE the stroke, drawn rather than shadowed: a shadow has a
            // light source and therefore a direction, and this ring must be even on all four sides.
            .overlay(
                RoundedRectangle(cornerRadius: Radius.control)
                    .strokeBorder(haloColor, lineWidth: 4)
                    .padding(-2.75)
            )
            .animation(.easeOut(duration: Motion.fast), value: error)
            .animation(.easeOut(duration: Motion.fast), value: valid)
            .animation(.easeOut(duration: Motion.fast), value: focused)

            // The label, riding the stroke. Its patch is the screen colour in the error state so it
            // still notches a box whose fill is no longer white.
            Text(label)
                .font(F.manrope(active ? 12 : 17, active ? .semibold : .medium))
                .foregroundColor(active ? labelColor : .liqFaint)
                .padding(.horizontal, active ? Spacing.sm : 0)
                .background(active ? (error ? Color.liqCream : Color.liqElevated) : .clear)
                .offset(x: active ? 14 : 18, y: active ? -8 : 22)
                .allowsHitTesting(false)
                .animation(.easeOut(duration: Motion.fast), value: active)
                .animation(.easeOut(duration: Motion.fast), value: labelColor)
                .accessibilityHidden(true)
        }
    }

    /// Focus the field. Called by the screens on appear — the keyboard is open before the first tap.
    func focus() { focused = true }
}

// MARK: - Glyphs

/// A drawn tick on a 24 grid, like every other icon here.
struct CheckGlyph: View {
    let size: CGFloat
    let color: Color
    var lineWidth: CGFloat = 2

    var body: some View {
        Path { p in
            let s = size / 24
            p.move(to: CGPoint(x: 5 * s, y: 12 * s))
            p.addLine(to: CGPoint(x: 10 * s, y: 17 * s))
            p.addLine(to: CGPoint(x: 19 * s, y: 7 * s))
        }
        .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        .frame(width: size, height: size)
    }
}

/// The round danger badge with an "!" in it. Lora rather than Manrope — the serif "!" has the
/// weight the design draws, and it is the same choice the phone screen's error glyph makes.
struct DangerGlyph: View {
    let size: CGFloat
    var glyphSize: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(Color.liqDanger)
            Text("!")
                .font(.custom(PS.loraBold, size: glyphSize))
                .foregroundColor(.white)
        }
        .frame(width: size, height: size)
    }
}

/// The inline failure card — ONE component across name, email and date of birth.
///
/// The epic names it as built-once and the name ticket says it twice: "the same component the DoB
/// screen uses, not a re-style". Every value here is shared. It deliberately does not own the
/// region it sits in: name reserves a fixed height around it and email does not, and a card that
/// reserved its own space would take that choice away from the screens.
struct InlineErrorCard: View {
    let message: Text

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.lg) {
            DangerGlyph(size: 18, glyphSize: 12)
                .padding(.top, 1)
            message
                .font(F.manrope(13.5, .medium))
                .foregroundColor(.liqDangerFg)
                .lineSpacing(13.5 * 0.4)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: Radius.errorBox)
                .fill(Color.liqDanger.opacity(0.07))
        )
        .overlay(
            RoundedRectangle(cornerRadius: Radius.errorBox)
                .strokeBorder(Color.liqDanger.opacity(0.18), lineWidth: 1)
        )
    }
}
