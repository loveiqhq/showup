//  InputField.swift
//  ShowUp · the one text input, and the chrome every field-shaped control wears
//
//  WHY THE WHOLE BOX BEING TAPPABLE IS THE POINT OF THIS FILE
//
//  Android shipped a number field that measured 23dp inside a 56dp row. The row was 56, the border
//  was 56, the fill was 56; the text field inside it measured to the height of its own text and the
//  row centred it. So the control looked 56 tall and only its middle third took a tap. It was
//  invisible in the source and invisible in a screenshot, and measurement found it on 15 of 18
//  phone sizes.
//
//  iOS turned out NOT to have it — `TapTargetTests` measured the field at a full 56pt at every
//  size on 8 September 2026, because a `UIViewRepresentable` with no `sizeThatFits` accepts the
//  height SwiftUI proposes. I had predicted the opposite. The guarantee is asserted rather than
//  assumed on both platforms now, because it is one modifier away from being false on either.

import SwiftUI

// MARK: - The chrome

/// One control tall, `Radius.control` corners, an elevated fill and a 1.5pt outline.
///
/// Separate from `InputField` because two controls wear it and only one of them is an input: the
/// country pill beside the number field is a Button with identical chrome. The alternative to
/// sharing it is what was there before — the same four modifiers written twice, a few lines apart,
/// already differing in their horizontal padding.
///
/// **1.5pt and `liqSubtle`, not `liqBorder`.** The outline is the only thing identifying the field,
/// and Border at 12% measures 1.28:1 against a 3:1 requirement. Audit finding 7.
struct FieldChrome: ViewModifier {
    var outline: Color = .liqSubtle
    var height: CGFloat = ComponentSizes.controlHeight
    var horizontalPadding: CGFloat = 18
    /// The soft 4pt danger ring outside the outline.
    ///
    /// **iOS only, and flagged rather than copied.** Android's invalid field has the red outline and
    /// the glyph and no ring. Adding it to Android or dropping it here both change a screen that is
    /// in PO Acceptance, so both platforms keep what they render today and the design side decides
    /// which is right. Recorded so it is a known divergence and not silent drift.
    var dangerRing: Bool = false

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, horizontalPadding)
            .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.control).fill(Color.liqElevated))
            .overlay(RoundedRectangle(cornerRadius: Radius.control)
                .strokeBorder(outline, lineWidth: 1.5))
            .overlay(dangerRing ? RoundedRectangle(cornerRadius: Radius.control)
                .strokeBorder(Color.liqDanger.opacity(0.10), lineWidth: 4)
                .padding(-2.75) : nil)
    }
}

extension View {
    /// See `FieldChrome`.
    ///
    /// Takes the modifier rather than restating its parameters. The first version of this spelled
    /// out every default a second time -- `height: CGFloat = ComponentSizes.controlHeight`,
    /// `horizontalPadding: CGFloat = 18` -- and an injection test caught what that costs: changing
    /// the struct's height left the extension's copy still satisfying the check that guards it.
    /// One definition per value, which is the rule this whole task exists to apply.
    func fieldChrome(_ chrome: FieldChrome = FieldChrome()) -> some View {
        modifier(chrome)
    }
}

// MARK: - The input

/// The one text input: a single line inside `fieldChrome`, with an optional trailing adornment.
///
/// The field itself is passed in rather than built here, because the number field is a
/// `UIViewRepresentable` for a reason that has nothing to do with chrome — as-you-type grouping
/// needs caret control that SwiftUI's `TextField` does not expose. See `PhoneNumberField.swift`.
/// Wrapping it in a generic slot keeps that argument where it belongs and lets a future plain
/// `TextField` use the same box.
///
/// `label` is not optional. An input with no accessibility label is a control VoiceOver cannot name,
/// and this one had none on Android until 8 September 2026.
struct InputField<Field: View, Trailing: View>: View {
    let label: String
    var invalid: Bool = false
    @ViewBuilder var field: () -> Field
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 0) {
            field()
                // The counterpart of Android's fillMaxHeight. Not a parameter, so it cannot be
                // omitted -- see the note at the top of this file.
                .frame(maxHeight: .infinity)
            trailing()
        }
        .fieldChrome(FieldChrome(outline: invalid ? .liqDanger : .liqSubtle,
                                 dangerRing: invalid))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(label))
    }
}

extension InputField where Trailing == EmptyView {
    init(label: String, invalid: Bool = false, @ViewBuilder field: @escaping () -> Field) {
        self.init(label: label, invalid: invalid, field: field, trailing: { EmptyView() })
    }
}

/// The error glyph the number field shows when validation rejects the number.
///
/// Its own view because both platforms draw the same 22pt circle with a Lora "!" in it, and it was
/// written inline on each.
///
/// `Spacer(minLength: 0)` is copied verbatim from what this screen already renders, and not
/// "improved" to a fixed 8 to match Android's spacer. The two expressions distribute the row's
/// slack differently, this screen is in PO Acceptance, and I have already been wrong once today
/// about how SwiftUI resolves a flexible row. Preserving the expression preserves the pixels.
struct FieldErrorGlyph: View {
    var body: some View {
        Spacer(minLength: 0)
        ZStack {
            Circle().fill(Color.liqDanger).frame(width: 22, height: 22)
            Text("!").font(.custom(PS.loraBold, size: 14)).foregroundColor(.white)
        }
    }
}
