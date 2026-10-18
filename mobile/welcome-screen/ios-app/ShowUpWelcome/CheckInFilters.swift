//
//  CheckInFilters.swift
//  ShowUp · Check-In 05 (SHOWUP-182): Filters -- gender (mandatory), age range, distance
//
//  The Swift twin of `checkin/CheckInFiltersScreen.kt`. `FiltersStep` from
//  `screen-checkin-filters-reference.jsx`, inside the step shell. Three filters in this order, and
//  NO SEXUALITY FILTER: matching is two-way on gender, orientation stays on the profile. Gender chips
//  are Profile 22's `LifePill` as built; the sliders are `RangeSlider`, which Search's filter sheet
//  will consume rather than rebuild.
//
//  The filter list is the only flexible element and the only thing that scrolls; at 390 x 844 the
//  three filters fit, at 375 x 667 the list scrolls inside itself.
//

import SwiftUI

struct CheckInFiltersView: View {
    let filters: CheckInFilters
    var toast: String? = nil
    var toastTick: Int = 0
    var onGender: (String) -> Void = { _ in }
    var onAge: (_ thumb: Int, _ value: Int) -> Void = { _, _ in }
    var onDistance: (Int) -> Void = { _ in }
    var onContinue: () -> Void = {}
    var onBack: () -> Void = {}
    var onClose: () -> Void = {}

    var body: some View {
        CheckInStepShell(step: .filters, onBack: onBack, onClose: onClose, footer: {
            CheckInFooter(label: CheckInCopy.continueLabel, onClick: onContinue, dimmed: filters.genders.isEmpty,
                          toast: toast, toastTick: toastTick)
        }) {
            CheckInFlexBody(header: {
                Color.clear.frame(height: Spacing.md)
                CheckInStepHeadline(parts: CheckInCopy.filtersHeadline)
                CoachNote(text: CheckInCopy.filtersNote, warm: true)
                    .padding(.top, 14)
            }, region: {
                // THE FILTER LIST -- the only flexible element. Rows 22 apart.
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 22) {
                        CheckInFilterRow(label: CheckInCopy.filterGender, hint: nil) {
                            WrapLayout(spacing: Spacing.md, lineSpacing: Spacing.md) {
                                ForEach(CheckInGender.allCases, id: \.self) { gender in
                                    LifePill(label: gender.label, selected: filters.genders.contains(gender.id),
                                             action: { onGender(gender.id) })
                                }
                            }
                            .accessibilityElement(children: .contain)
                            .accessibilityLabel(Text(CheckInCopy.filterGender))
                        }
                        CheckInFilterRow(label: CheckInCopy.filterAge, hint: ageLabel(filters.minAge, filters.maxAge)) {
                            RangeSlider(values: [filters.minAge, filters.maxAge], bounds: ageMin...ageMax,
                                        labels: [CheckInCopy.sliderMinAge, CheckInCopy.sliderMaxAge],
                                        onChange: onAge)
                        }
                        CheckInFilterRow(label: CheckInCopy.filterDistance, hint: distanceLabel(filters.distanceKm)) {
                            RangeSlider(values: [filters.distanceKm], bounds: distanceMin...distanceMax,
                                        labels: [CheckInCopy.sliderDistance],
                                        onChange: { _, km in onDistance(km) })
                        }
                    }
                    .padding(.bottom, Spacing.xl)
                }
                .padding(.top, 18)
            })
        }
    }
}

/// `CheckinFilterRow` -- the label (14 / 600) and its value (13 / 600, violet), baseline-aligned,
/// 10 above the control.
struct CheckInFilterRow<Content: View>: View {
    let label: String
    let hint: String?
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.md) {
                Text(label)
                    .font(F.manrope(14, .semibold))
                    .foregroundColor(.liqFg)
                Spacer(minLength: 0)
                if let hint {
                    Text(hint)
                        .font(F.manrope(13, .semibold))
                        .foregroundColor(.liqPurple)
                }
            }
            .padding(.bottom, Spacing.lg)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("Check-In 05 · A pre-filled") { CheckInFiltersView(filters: CheckInFilters(genders: ["woman"])) }
#Preview("Check-In 05 · B no gender + toast") {
    CheckInFiltersView(filters: CheckInFilters(genders: []), toast: CheckInCopy.filtersToast)
}
#Preview("Check-In 05 · C narrowed") {
    CheckInFiltersView(filters: CheckInFilters(genders: ["woman", "non_binary"], minAge: 25, maxAge: 35, distanceKm: 15))
}
