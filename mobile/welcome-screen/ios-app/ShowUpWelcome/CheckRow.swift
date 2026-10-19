//
//  CheckRow.swift
//  ShowUp · the multi-select answer row (SHOWUP-170)
//
//  The Swift twin of `designsystem/CheckRow.kt`. "Its own component, not a variant of OptionRow":
//  padding 12 / 4 against 16 / 4, and an 11 white tick in the round indicator where the radio has a
//  dot. The wash, divider, radius, label and transitions are shared — "so the two lists read as a
//  pair" — through `AnswerRowChrome`, `AnswerRowLabel` and `RoundIndicator`.
//
//  12 / 4 IS 44.8 TALL AT THE DEFAULT SIZE — iOS's 44pt floor, met as drawn. (Android's floor is 48,
//  and its twin carries it; the padding is the reference's on both.)
//

import SwiftUI

struct CheckRow: View {
    let label: String
    let checked: Bool
    let index: Int
    let count: Int
    var last: Bool? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xl) {
                AnswerRowLabel(text: label, chosen: checked)
                RoundIndicator(chosen: checked) {
                    CheckGlyph(size: 11, color: .liqElevated, stroke: 3.5)
                }
            }
            .padding(.vertical, Spacing.xl)
            .padding(.horizontal, Spacing.xs)
            .frame(minHeight: ComponentSizes.minTapTarget)
            .contentShape(Rectangle())
            .modifier(AnswerRowChrome(chosen: checked, divider: !(last ?? (index == count - 1))))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        // A checkbox announces its state: "German, checked, 1 of 8".
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue("\(checked ? "Checked" : "Not checked"), \(index + 1) of \(count)")
    }
}
