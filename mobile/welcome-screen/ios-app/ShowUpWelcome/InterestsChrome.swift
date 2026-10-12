//
//  InterestsChrome.swift
//  ShowUp · what Interests builds (Profile 21, SHOWUP-174): InterestChip, InterestsCounter,
//  ShowMoreToggle, InterestGroup and CategoryTitle
//
//  The Swift twin of `profile/InterestsChrome.kt`. ONE CHIP FOR BOTH BLOCKS: it draws picked,
//  resting or faded and reports a tap; whether the tap clears, moves or is refused at the cap is
//  screen logic (`ProfileInterests.swift`).
//
//  THE CHIP DRAWS 38 AND ANSWERS AT 44 (`overflowTapTarget`), so rows stay 8 apart and nothing
//  moves; 3 + 3 of overflow never reaches the next row. The toggle draws about 26 and answers at 44.
//

import SwiftUI

/// The chip's drawn height — `height: 38`. A minimum, so Dynamic Type grows it.
private let chipHeight: CGFloat = 38

/// How a chip answers to VoiceOver: a radio in a How I live group, a toggle in What I'm into.
enum ChipRole { case radio, toggle }

/// `scale(0.97)` on press, on the brand curve — never on a faded chip ("if (!disabled)").
private struct ChipPress: ButtonStyle {
    let enabled: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && enabled && !reduceMotion ? 0.97 : 1)
            .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.fast),
                       value: configuration.isPressed)
    }
}

/// One chip.
///
/// Picked: violet fill, 1.5 violet border, white 700, `0 4 10` violet glow. Resting: white, 1px
/// `--liq-border`, 600, a `0 1 2` hairline lift. Faded (at the cap, not picked): opacity .4, NOT
/// animated — the reference's transition list does not name opacity. The colours and the glow run
/// 180 on CSS's `ease`; the press scale on the brand curve.
struct InterestChip: View {
    let label: String
    let selected: Bool
    let role: ChipRole
    /// Drawn at .4 and read as dimmed, but STILL TAPPABLE: the tap is reported and changes nothing.
    var faded = false
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // The border is inside the box, as CSS draws it, so the picked chip is a hair wider.
        let border: CGFloat = selected ? 1.5 : 1
        Button(action: action) {
            Text(label)
                .font(F.manrope(14, selected ? .bold : .semibold))
                .foregroundColor(selected ? .white : .liqFg)
                // One line on every phone at the default size; a second only for a label wider than
                // the whole column at the largest Dynamic Type, where the alternative is clipping.
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 15 + border)
                .frame(minHeight: chipHeight)
                .background(
                    Capsule()
                        .fill(selected ? Color.liqPurple : Color.liqElevated)
                        .shadow(color: selected ? .liqChipGlow : .liqChipRest,
                                radius: selected ? 5 : 1, y: selected ? 4 : 1)
                )
                .overlay(Capsule().strokeBorder(selected ? Color.liqPurple : Color.liqBorder, lineWidth: border))
                .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: selected)
                .opacity(faded ? 0.4 : 1)
                .overflowTapTarget(drawnHeight: chipHeight)
        }
        .buttonStyle(ChipPress(enabled: !faded))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
        // iOS has no radio or toggle-button trait, so the state is SPOKEN, both ways, in the words
        // OptionRow and CheckRow use; a faded chip says it is dimmed, as `aria-disabled` would.
        .accessibilityValue(faded ? "Not checked, dimmed" : (selected ? "Checked" : "Not checked"))
    }
}

/// `{N} picked · {M} left`, and `10 of 10 picked` at the cap, beside a 4-tall meter. Orange at the
/// cap (`color 180ms`); the bar's width runs 280 on the brand curve. A POLITE LIVE REGION.
struct InterestsCounter: View {
    let picked: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let full = picked >= interestsMax
        let tone: Color = full ? .liqOrange : .liqPurple
        let label = interestsCounterLabel(picked)
        HStack(spacing: Spacing.lg) {
            Text(label)
                .font(F.manrope(13.5, .bold))
                .foregroundColor(tone)
                .lineLimit(1)
                .fixedSize()
                .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: full)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.liqMeterTrack)
                    Capsule().fill(tone)
                        .frame(width: geo.size.width * interestsFill(picked))
                        .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.progress),
                                   value: picked)
                        .animation(reduceMotion ? nil : Motion.cssEase(Motion.fast), value: full)
                }
            }
            .frame(height: 4)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.updatesFrequently)
        // "aria-live polite": announced after each change, without taking focus from the chip.
        .onChange(of: picked) { _, now in
            AccessibilityNotification.Announcement(interestsCounterLabel(now)).post()
        }
    }
}

/// `Show more` / `Show less`: a 16 chevron (stroke 2.2) turning over in 220, beside a Manrope 700
/// 13.5 violet label. Drawn `padding 4 / 2`, answering at 44 without moving anything.
struct ShowMoreToggle: View {
    let expanded: Bool
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let label = expanded ? PickCopy.showLess : PickCopy.showMore
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                BrandIconView(icon: .chevronDown, size: 16, stroke: 2.2, tint: .liqPurple)
                    .rotationEffect(.degrees(expanded ? 180 : 0))
                    .animation(reduceMotion ? nil : .timingCurve(0.22, 1, 0.36, 1, duration: Motion.disclosure),
                               value: expanded)
                Text(label)
                    .font(F.manrope(13.5, .bold))
                    .foregroundColor(.liqPurple)
            }
            .padding(.vertical, Spacing.xs)
            .padding(.horizontal, 2)
            .overflowTapTarget(drawnHeight: 26)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
        // `aria-expanded`.
        .accessibilityValue(expanded ? "Expanded" : "Collapsed")
    }
}

/// A block title — `How I live`, `What I'm into`. Manrope 700 16, margin-bottom 12.
struct CategoryTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(F.manrope(16, .bold))
            .foregroundColor(.liqFg)
            .accessibilityAddTraits(.isHeader)
    }
}

/// One group: a small label, its chips wrapping 8 apart, and — on a collapsible What I'm into group
/// — the toggle. The label is the group's accessible name.
struct InterestGroup<Trailing: View>: View {
    let label: String
    /// Already cut to four for a collapsed group.
    let chips: [DetailOption]
    let role: ChipRole
    let isSelected: (String) -> Bool
    var isFaded: (String) -> Bool = { _ in false }
    let onPick: (String) -> Void
    @ViewBuilder var toggle: () -> Trailing

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(F.manrope(13, .semibold))
                .foregroundColor(.liqMuted)
                // Read once, as the group's name below, not again as a loose line above it.
                .accessibilityHidden(true)
                .padding(.bottom, Spacing.md)
            WrapLayout(spacing: Spacing.md, lineSpacing: Spacing.md) {
                ForEach(chips, id: \.value) { option in
                    InterestChip(label: option.label, selected: isSelected(option.value), role: role,
                                 faded: isFaded(option.value)) { onPick(option.value) }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(label)
            toggle()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
