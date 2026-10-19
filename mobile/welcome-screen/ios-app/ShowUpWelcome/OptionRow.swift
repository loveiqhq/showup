//
//  OptionRow.swift
//  ShowUp · the single-select answer row (SHOWUP-168), and the chrome it shares with CheckRow
//
//  The Swift twin of `designsystem/OptionRow.kt`. Built by Profile 15 and consumed by orientation,
//  education, religion and politics — "build it once here as a separate component". Pad 16 / 4,
//  radius 10, Manrope 500 / 16 / 1.3 (700 when selected), a 22 radio that fills violet with an 8
//  white dot.
//
//  NO "clears on reselect" PROPERTY: what a tap on the selected row does is the caller's
//  (`pickSingle`). The row only reports the tap.
//

import SwiftUI

/// The rows' corner radius — also the radius the selected wash and the divider follow.
private let rowRadius: CGFloat = 10

/// The divider CSS draws for `border-bottom: 1px` on a box with `border-radius: 10`.
///
/// NOT A STRAIGHT LINE: a bottom border on a rounded box bends up along each bottom corner and thins
/// to nothing where the arc meets the zero-width side. That region is the outer rounded rectangle
/// minus the inner padding edge, whose bottom corners are 10 wide and 9 tall — drawn here as one
/// even-odd shape, exactly the Kotlin twin's `PathOperation.Difference`.
struct AnswerRowDivider: Shape {
    func path(in rect: CGRect) -> Path {
        // CIRCULAR corners on both, as CSS draws `border-radius` — SwiftUI's default is the
        // continuous squircle, which would bend the line on a different curve.
        var outer = Path(roundedRect: rect, cornerRadius: rowRadius, style: .circular)
        let inner = UnevenRoundedRectangle(
            topLeadingRadius: rowRadius,
            bottomLeadingRadius: rowRadius - 1,
            bottomTrailingRadius: rowRadius - 1,
            topTrailingRadius: rowRadius,
            style: .circular
        ).path(in: CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height - 1))
        outer.addPath(inner)
        return outer
    }
}

/// The wash and the divider both answer rows draw, behind their content.
struct AnswerRowChrome: ViewModifier {
    let chosen: Bool
    let divider: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: rowRadius, style: .circular)
                        .fill(chosen ? Color.liqSelectedRowWash : Color.clear)
                    if divider {
                        AnswerRowDivider().fill(Color.liqBorderSoft, style: FillStyle(eoFill: true))
                    }
                }
                // `transition: background 180ms`.
                .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: chosen)
            )
    }
}

/// The label both rows set: Manrope 16 / 1.3, 500 at rest and 700 when chosen.
struct AnswerRowLabel: View {
    let text: String
    let chosen: Bool

    var body: some View {
        Text(text)
            .font(F.manrope(16, chosen ? .bold : .medium))
            // line-height 1.3, in this project's spelling of a CSS line-height.
            .lineSpacing(16 * 0.3)
            .foregroundColor(.liqFg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The 22 round indicator: white with a 1.5 border at rest, violet when chosen.
struct RoundIndicator<Mark: View>: View {
    let chosen: Bool
    @ViewBuilder let mark: () -> Mark
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Circle().fill(chosen ? Color.liqPurple : Color.liqElevated)
            Circle().strokeBorder(chosen ? Color.liqPurple : Color.liqBorder, lineWidth: 1.5)
            if chosen { mark() }
        }
        .frame(width: 22, height: 22)
        .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: chosen)
    }
}

/// One single-select answer. The WHOLE ROW is the hit area.
///
/// - Parameters:
///   - index: 0-based position, and `count` the list length — so VoiceOver reads "Woman, 1 of 4".
///   - last: no divider under the final row.
struct OptionRow: View {
    let label: String
    let selected: Bool
    let index: Int
    let count: Int
    var last: Bool? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.xl) {
                AnswerRowLabel(text: label, chosen: selected)
                RoundIndicator(chosen: selected) {
                    Circle().fill(Color.liqElevated).frame(width: 8, height: 8)
                }
            }
            .padding(.vertical, Spacing.xxl)
            .padding(.horizontal, Spacing.xs)
            .contentShape(Rectangle())
            .modifier(AnswerRowChrome(chosen: selected, divider: !(last ?? (index == count - 1))))
        }
        // No press feedback beyond the selection itself: "the change is immediate, with the 180 ms
        // background and indicator transition. Nothing else on the screen changes."
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        // iOS has no radio trait, so the state is SPOKEN, both ways: "Woman, checked, 1 of 4" and,
        // after a second tap clears a skippable step's row, "not checked" -- as the tickets ask
        // ("each row is a radio with aria-checked"). The same words CheckRow uses.
        .accessibilityAddTraits(.isButton)
        .accessibilityValue("\(selected ? "Checked" : "Not checked"), \(index + 1) of \(count)")
    }
}
