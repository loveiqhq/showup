//
//  CheckInVibe.swift
//  ShowUp · Check-In 04 (SHOWUP-181): Vibe -- one to five of twenty, in three groups
//
//  The Swift twin of `checkin/CheckInVibeScreen.kt`. `VibeStep` from
//  `screen-checkin-vibe-reference.jsx`, inside the step shell. Chips are Profile 22's `LifePill`, AS
//  BUILT: no new chip component, no new props, no locked or dimmed state -- "chips are never
//  locked". At 5/5 a tap on an idle chip selects nothing and raises the max toast (the model); the
//  counter turning violet is the only visual change.
//
//  The chip groups are the only flexible element and scroll on EVERY device. Headline, note,
//  counter and CTA never move.
//

import SwiftUI

struct CheckInVibeView: View {
    let picked: Set<String>
    var toast: String? = nil
    var toastTick: Int = 0
    /// Open with the chip groups scrolled to their end -- the developer menu's `181D-end` frame,
    /// which Android reaches by handing in a scroll state.
    var groupsStartAtEnd: Bool = false
    var onTap: (String) -> Void = { _ in }
    var onContinue: () -> Void = {}
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}

    var body: some View {
        CheckInStepShell(step: .vibe, onBack: onBack, onClose: onClose, footer: {
            CheckInFooter(label: CheckInCopy.continueLabel, onClick: onContinue, dimmed: picked.isEmpty,
                          toast: toast, toastTick: toastTick)
        }) {
            CheckInFlexBody(header: {
                Color.clear.frame(height: Spacing.md)
                CheckInStepHeadline(parts: CheckInCopy.vibeHeadline)
                CoachNote(text: CheckInCopy.vibeNote, warm: true)
                    .padding(.top, 14)
                counter
                    .padding(.top, 14)
            }, region: {
                // THE CHIP GROUPS -- the only flexible element; they scroll on every device.
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        ForEach(VibeGroup.allCases, id: \.self) { group in
                            groupView(group)
                        }
                    }
                    .padding(.bottom, Spacing.xl)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .defaultScrollAnchor(groupsStartAtEnd ? .bottom : .top)
                .padding(.top, Spacing.xl)
            })
        }
    }

    /// `Pick up to 5 — mix and match` · `n/5`, baseline-aligned, the count turning violet at 5/5,
    /// re-read as it changes.
    private var counter: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            Text(CheckInCopy.vibeCounterLead)
                .font(F.manrope(13, .semibold))
                .foregroundColor(.liqFg)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: Spacing.lg)
            Text("\(picked.count)/\(vibeMax)")
                .font(F.manrope(12, .semibold))
                .foregroundColor(picked.count >= vibeMax ? .liqPurple : .liqSubtle)
                .accessibilityAddTraits(.updatesFrequently)
        }
    }

    private func groupView(_ group: VibeGroup) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.md) {
                Text(group.label)
                    .font(F.manrope(13, .bold))
                    .foregroundColor(.liqFg)
                    .accessibilityAddTraits(.isHeader)
                Text(group.hint.uppercased())
                    .font(F.manrope(10.5, .bold))
                    .tracking(10.5 * 0.08)
                    .foregroundColor(.liqSubtle)
                    .accessibilityHidden(true)
            }
            .padding(.bottom, Spacing.lg)
            WrapLayout(spacing: Spacing.md, lineSpacing: Spacing.md) {
                ForEach(group.vibes, id: \.id) { vibe in
                    LifePill(label: vibe.label, selected: picked.contains(vibe.id), action: { onTap(vibe.id) })
                }
            }
        }
        .accessibilityElement(children: .contain)
    }
}

#Preview("Check-In 04 · A empty") { CheckInVibeView(picked: []) }
#Preview("Check-In 04 · C picked") { CheckInVibeView(picked: ["coffee_chat", "playful", "no_rush"]) }
#Preview("Check-In 04 · D five + toast") {
    CheckInVibeView(picked: ["coffee_chat", "sunset", "calm", "playful", "no_rush"], toast: CheckInCopy.vibeToastMax)
}
