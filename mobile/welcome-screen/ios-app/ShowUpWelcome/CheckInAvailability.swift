//
//  CheckInAvailability.swift
//  ShowUp · Check-In 02 (SHOWUP-179): Today and Tomorrow -- one `AvailabilityStep`, two days
//
//  The Swift twin of `checkin/CheckInAvailabilityScreen.kt`. "Two screens, one component, one
//  story. Splitting them is how a grid gets built twice." The view takes the day's grid as a value;
//  Today and Tomorrow differ only in that grid, their copy, and whether an empty Continue confirms
//  (Today) or toasts (Tomorrow).
//
//  LAYOUT, from the reference: header block (headline mt 8 → subline mt 10 → How it works mt 12) →
//  THE GRID (flexible, the only thing that scrolls, mt 13) → summary (mt 8) → legend → footer.
//  Opening How it works takes its height from the grid; the summary, legend and CTA never move.
//  Slots, the CTA and the headline are never shrunk to fit.
//

import SwiftUI

struct CheckInAvailabilityView: View {
    let grid: AvailabilityGrid
    /// This grid's picks, by hour.
    let picked: [Int: SlotTier]
    let howItWorksOpen: Bool
    var toast: String? = nil
    var toastTick: Int = 0
    var dialogOpen: Bool = false
    var onSlot: (Int) -> Void = { _ in }
    var onClearAll: () -> Void = {}
    var onToggleHowItWorks: () -> Void = {}
    var onContinue: () -> Void = {}
    var onConfirmNoToday: () -> Void = {}
    var onCancelNoToday: () -> Void = {}
    var onDismissNoToday: () -> Void = {}
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var today: Bool { grid.day == .today }

    var body: some View {
        ZStack {
            // While the confirm is up the swipe back is its dismiss, as Android back is.
            CheckInStepShell(step: grid.day, onBack: dialogOpen ? onDismissNoToday : onBack, onClose: onClose,
                             footer: {
                                 CheckInFooter(label: CheckInCopy.continueLabel, onClick: onContinue,
                                               // Today's Continue is never dimmed; Tomorrow's dims until
                                               // an hour is picked.
                                               dimmed: !today && picked.isEmpty,
                                               toast: toast, toastTick: toastTick)
                             }) {
                VStack(spacing: 0) {
                    // The header block over the grid -- with the floor: How it works open on a small
                    // phone scrolls the two together instead of crushing what follows.
                    CheckInFlexBody(header: { header }, region: { gridRegion })
                    AvailabilitySummary(grid: grid, picked: picked)
                        .padding(.top, Spacing.md)
                    AvailabilityLegend()
                }
            }
            // `blur(3px)` behind the scrim, arriving with it.
            .blur(radius: dialogOpen ? 3 : 0)
            .animation(reduceMotion ? nil : Motion.brand(Motion.reveal), value: dialogOpen)
            // While the confirm is up, the screen behind is out of VoiceOver's reach.
            .accessibilityHidden(dialogOpen)

            if dialogOpen {
                NoHoursTodayDialog(onConfirm: onConfirmNoToday, onCancel: onCancelNoToday, onDismiss: onDismissNoToday)
            }
        }
    }

    @ViewBuilder private var header: some View {
        Color.clear.frame(height: Spacing.md)
        CheckInStepHeadline(parts: today ? CheckInCopy.todayHeadline : CheckInCopy.tomorrowHeadline)
        // `textWrap: 'pretty'`: the sheet's `… See How it works for` / `more info.`, not `info.`
        // alone. `How it works` in primary 700 -- styled text, not a link; the disclosure below is
        // the control.
        PrettyText(runs: today ? CheckInCopy.todaySub : [(CheckInCopy.tomorrowSub, false)],
                   font: TypeMetrics.uiFont(PS.manropeRegular, 14, fallback: .systemFont(ofSize: 14)),
                   lineHeight: 14 * 1.45, color: .liqSubtle,
                   emphasisFont: TypeMetrics.uiFont(PS.manropeBold, 14, fallback: .systemFont(ofSize: 14, weight: .bold)),
                   emphasisColor: .liqPurple)
            .padding(.top, Spacing.lg)
        HowItWorks(open: howItWorksOpen, onToggle: onToggleHowItWorks, action: {
            if !picked.isEmpty { ClearAll(onClick: onClearAll) }
        })
        .padding(.top, Spacing.xl)
    }

    /// THE GRID -- the only flexible element and the only thing that scrolls.
    private var gridRegion: some View {
        ScrollView(.vertical, showsIndicators: false) {
            AvailabilityGridRows(grid: grid, picked: picked, onTap: onSlot)
                .padding(.bottom, 2)
        }
        .padding(.top, 13)
    }
}

// MARK: - previews: the four frames the ticket names, at the kit's 4:20pm

private let previewDays = checkInDays(CivilDateTime(date: CivilDate(year: 2026, month: 10, day: 13), hour: 16, minute: 20))

#Preview("Check-In 02 · A Today picked") {
    CheckInAvailabilityView(grid: gridOf(.today, previewDays),
                            picked: [18: .preferred, 19: .preferred, 20: .available], howItWorksOpen: false)
}

#Preview("Check-In 02 · B Today empty + confirm") {
    CheckInAvailabilityView(grid: gridOf(.today, previewDays), picked: [:], howItWorksOpen: false, dialogOpen: true)
}

#Preview("Check-In 02 · C Tomorrow empty + toast") {
    CheckInAvailabilityView(grid: gridOf(.tomorrow, previewDays), picked: [:], howItWorksOpen: false,
                            toast: CheckInCopy.tomorrowToast)
}

#Preview("Check-In 02 · D Tomorrow picked, How it works open") {
    CheckInAvailabilityView(grid: gridOf(.tomorrow, previewDays),
                            picked: [12: .preferred, 13: .preferred, 19: .available], howItWorksOpen: true)
}
