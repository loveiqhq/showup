//  PhoneNumberField.swift
//  ShowUp · the number field, grouped as you type, with the caret where you left it (SHOWUP-143)
//
//  A UITextField rather than SwiftUI's TextField, and the reason is the caret.
//
//  As-you-type grouping means rewriting the string the user is editing, and SwiftUI gives no way to
//  say where the caret should land afterwards — `TextField` has no selection API at all. Every
//  SwiftUI-only version of this screen has therefore had the same fault: the moment the grouping
//  changed, the caret jumped to the end, so correcting one digit in the middle typed the rest of
//  the number at the end. Two earlier attempts are worth recording because both looked right:
//
//    · binding through a getter that reformats. The grouping never appeared at all — while the
//      field is first responder UIKit owns its text and SwiftUI will not reliably push a getter's
//      rewrite into it — and when it did land it clobbered keystrokes still in flight. Ten digits
//      typed, five arrived.
//    · reformatting in onChange. That fixed both of those, and left the caret jumping.
//
//  Here UIKit owns the text, which is the arrangement it is built for: the rewrite happens inside
//  the edit rather than a frame later, so nothing is dropped, and the selection can be put back.
//
//  This is the direct twin of what Android does with a VisualTransformation and an OffsetMapping --
//  see GroupedDigits in PhoneVerificationScreen.kt. Both keep the VALUE as plain digits and treat
//  the spaces as presentation; both map a caret position through that presentation by counting
//  digits rather than characters.

import SwiftUI
import UIKit

struct PhoneNumberField: UIViewRepresentable {
    /// Digits only, no spaces and no dial code -- the pill carries that.
    @Binding var digits: String
    var country: Country
    var onSubmit: () -> Void = {}

    func makeUIView(context: Context) -> AutoFocusTextField {
        let field = AutoFocusTextField()
        field.delegate = context.coordinator
        field.keyboardType = .phonePad
        field.textContentType = .telephoneNumber
        field.returnKeyType = .done
        field.tintColor = UIColor(Color.liqPurple)
        field.adjustsFontForContentSizeCategory = true
        // Kerning goes through defaultTextAttributes, which replaces the lot -- so the font and the
        // colour are set in the same dictionary rather than before it, where they would be lost.
        field.defaultTextAttributes = [
            .font: Self.font,
            .foregroundColor: UIColor(Color.liqFg),
            .kern: 0.3,
        ]
        field.addTarget(context.coordinator,
                        action: #selector(Coordinator.editingChanged(_:)),
                        for: .editingChanged)
        // The field is one half of an HStack and must take the space it is given, not the space its
        // current text needs.
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        sync(field, context: context)
        return field
    }

    func updateUIView(_ field: AutoFocusTextField, context: Context) {
        context.coordinator.parent = self
        sync(field, context: context)
    }

    /// Push the parent's state into the field, without disturbing an edit already in progress.
    ///
    /// The equality check is what makes this safe to call on every update: after the coordinator has
    /// formatted an edit the two already agree, so this does nothing and the caret it just placed
    /// survives. It only really fires when something OUTSIDE the field changed the value -- coming
    /// back from the code screen, or picking a different country.
    private func sync(_ field: AutoFocusTextField, context: Context) {
        field.attributedPlaceholder = NSAttributedString(
            string: country.sample,
            attributes: [.font: Self.font, .foregroundColor: UIColor(Color.liqFaint), .kern: 0.3])
        let shown = formatNational(digits, country)
        if field.text != shown {
            field.text = shown
            if let end = field.position(from: field.endOfDocument, offset: 0) {
                field.selectedTextRange = field.textRange(from: end, to: end)
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    /// Manrope SemiBold 17 with tabular figures, so the digits do not shuffle sideways as the
    /// grouping is applied. `.monospacedDigit()` is the SwiftUI spelling of the same feature.
    private static let font: UIFont = {
        let base = UIFont(name: PS.manropeSemi, size: 17)
            ?? .systemFont(ofSize: 17, weight: .semibold)
        let descriptor = base.fontDescriptor.addingAttributes([
            .featureSettings: [[
                UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector,
            ]],
        ])
        return UIFont(descriptor: descriptor, size: 17)
    }()

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: PhoneNumberField
        init(_ parent: PhoneNumberField) { self.parent = parent }

        @objc func editingChanged(_ field: UITextField) {
            let text = field.text ?? ""

            // Where the caret is, measured in DIGITS rather than characters. Characters would drift
            // every time a space appeared or vanished; digits are the thing the user is actually
            // pointing at, and they survive any regrouping.
            let caret = field.selectedTextRange?.end
            let offset = caret.map { field.offset(from: field.beginningOfDocument, to: $0) } ?? text.count
            let digitsBefore = text.prefix(offset).filter(\.isNumber).count

            // E.164's fifteen PLUS the allowance, never the maximum itself -- see
            // OVERTYPE_ALLOWANCE. Capping exactly at a limit makes the too-long error unreachable,
            // because the digits that would trigger it can never be typed. The per-country maximum
            // is not enforced here at all; that is validation's job, on submit.
            let all = String(text.filter(\.isNumber).prefix(E164_MAX_DIGITS + OVERTYPE_ALLOWANCE))
            let shown = formatNational(all, parent.country)

            field.text = shown
            let target = Coordinator.offset(afterDigits: digitsBefore, in: shown)
            if let position = field.position(from: field.beginningOfDocument, offset: target) {
                field.selectedTextRange = field.textRange(from: position, to: position)
            }
            parent.digits = all
        }

        /// The character offset just past the `n`th digit of `text` -- Android's OffsetMapping, in
        /// the one direction this field needs it.
        static func offset(afterDigits n: Int, in text: String) -> Int {
            guard n > 0 else { return 0 }
            var seen = 0
            for (index, character) in text.enumerated() where character.isNumber {
                seen += 1
                if seen == n { return index + 1 }
            }
            return text.count
        }

        func textFieldShouldReturn(_ field: UITextField) -> Bool {
            parent.onSubmit()
            return true
        }
    }
}

/// Takes the keyboard when it appears, which is why the user is on this screen.
///
/// `becomeFirstResponder()` from `makeUIView` is too early -- the field is not in a window yet and
/// the call is simply dropped. This is the callback that means "now you are".
final class AutoFocusTextField: UITextField {
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil { becomeFirstResponder() }
    }
}
