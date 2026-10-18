//
//  CheckInMotivation.swift
//  ShowUp · Check-In 03 (SHOWUP-180): Motivation -- one of five, single-select
//
//  The Swift twin of `checkin/CheckInMotivationScreen.kt`. `IntentStep` from
//  `screen-checkin-motivation-reference.jsx`, inside the step shell (02) with content props only.
//  The option list is the only flexible element and the only thing that scrolls; at 390 x 844 the
//  five rows fit, at 375 x 667 the list scrolls inside itself.
//
//  SINGLE-SELECT, radio semantics: a tap selects, a tap on another row replaces it, a tap on the
//  selected row does nothing. Selected is the violet border and a filled icon tile -- no checkmark.
//

import SwiftUI

/// The row's 1.5 border -- also part of its inset, see `MotivationRow`.
private let rowBorder: CGFloat = 1.5

struct CheckInMotivationView: View {
    let selected: String?
    var toast: String? = nil
    var toastTick: Int = 0
    var onPick: (String) -> Void = { _ in }
    var onContinue: () -> Void = {}
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}

    var body: some View {
        CheckInStepShell(step: .motivation, onBack: onBack, onClose: onClose, footer: {
            CheckInFooter(label: CheckInCopy.continueLabel, onClick: onContinue, dimmed: selected == nil,
                          toast: toast, toastTick: toastTick)
        }) {
            CheckInFlexBody(header: {
                Color.clear.frame(height: Spacing.md)
                CheckInStepHeadline(parts: CheckInCopy.motivationHeadline)
                CoachNote(text: CheckInCopy.motivationNote, warm: true)
                    .padding(.top, 14)
            }, region: {
                // THE LIST -- the only flexible element. A radio group named "Motivation to date".
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: Spacing.lg) {
                        ForEach(MotivationOption.allCases, id: \.self) { option in
                            MotivationRow(option: option, selected: option.id == selected,
                                          onClick: { onPick(option.id) })
                        }
                    }
                    .padding(.bottom, Spacing.xl)
                }
                .padding(.top, 14)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(Text(CheckInCopy.motivationGroup))
            })
        }
    }
}

/// One row: padding 14 / 16, radius 16, white, a 1.5 border (violet when selected), a 36 icon tile
/// (radius 12; violet with a white glyph when selected, lavender .16 with a violet glyph otherwise),
/// and the title (15 / 700) over the subline (13, subtle). `all 180ms`.
///
/// BORDER-BOX, as the kit lays out every element: the 1.5 border sits OUTSIDE the 14 / 16 padding,
/// so the inset is their sum -- each row 3pt taller than a border drawn over the padding.
private struct MotivationRow: View {
    let option: MotivationOption
    let selected: Bool
    let onClick: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radius.errorBox)
                        .fill(selected ? Color.liqPurple : Color.liqEyebrowBg)
                    BrandIconView(icon: option.icon, size: 18, stroke: 2, tint: selected ? .white : .liqPurple)
                }
                .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 0) {
                    Text(option.title)
                        .font(F.manrope(15, .bold))
                        .foregroundColor(.liqFg)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(option.subline)
                        .font(F.manrope(13, .regular))
                        .foregroundColor(.liqSubtle)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, Spacing.xxl + rowBorder)
            .padding(.vertical, 14 + rowBorder)
            .background(RoundedRectangle(cornerRadius: Radius.card).fill(Color.liqElevated))
            .overlay(RoundedRectangle(cornerRadius: Radius.card)
                .strokeBorder(selected ? Color.liqPurple : Color.liqBorder, lineWidth: rowBorder))
            .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: selected)
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.plain)
        // The row is the radio, read with its title, subline and state.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(option.title), \(option.subline)"))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

#Preview("Check-In 03 · A empty") { CheckInMotivationView(selected: nil) }
#Preview("Check-In 03 · B toast") { CheckInMotivationView(selected: nil, toast: CheckInCopy.motivationToast) }
#Preview("Check-In 03 · C picked") { CheckInMotivationView(selected: "long_term") }
