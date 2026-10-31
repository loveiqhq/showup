//
//  CheckInFilters.swift
//  ShowUp · Check-In 05 (SHOWUP-182): Filters -- gender (mandatory), age range, distance
//           Check-In 05b (SHOWUP-189): gender checkboxes, and a Dealbreaker switch on Age and Distance
//
//  The Swift twin of `checkin/CheckInFiltersScreen.kt`. `FiltersStep` from
//  `screen-checkin-filters-reference.jsx`, inside the step shell. Three filters in this order, and
//  NO SEXUALITY FILTER: matching is two-way on gender, orientation stays on the profile. The sliders
//  are `RangeSlider`, which Search's filter sheet consumes -- this IS that sheet: one screen, one store.
//
//  05b, decided 26 Oct 2026: the gender options are `GenderCheckPill` -- Profile 22's pill values plus
//  a checkbox square -- under a muted `Pick all that apply`; and a `DealbreakerRow` sits under the Age
//  and Distance sliders, Profile 10's `ConsentSwitch` consumed. The switches ship hidden behind the
//  server's remote flag `filters_dealbreakers` (`dealbreakers`): off, they are not drawn at all.
//
//  The filter list is the only flexible element and the only thing that scrolls; with both switches it
//  still fits at 390 x 844, and at 375 x 667 it scrolls inside itself. Nothing shrinks to avoid that.
//

import SwiftUI

struct CheckInFiltersView: View {
    let filters: CheckInFilters
    var toast: String? = nil
    var toastTick: Int = 0
    var onGender: (String) -> Void = { _ in }
    var onAge: (_ thumb: Int, _ value: Int) -> Void = { _, _ in }
    var onDistance: (Int) -> Void = { _ in }
    /// The remote flag `filters_dealbreakers` (05b): off, no `DealbreakerRow` is drawn.
    var dealbreakers: Bool = false
    var onAgeDealbreaker: (Bool) -> Void = { _ in }
    var onDistanceDealbreaker: (Bool) -> Void = { _ in }
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
                        // ① the hint in the existing slot, muted: the value hints below keep their violet.
                        CheckInFilterRow(label: CheckInCopy.filterGender, hint: CheckInCopy.filterGenderHint,
                                         hintMuted: true) {
                            WrapLayout(spacing: Spacing.md, lineSpacing: Spacing.md) {
                                ForEach(CheckInGender.allCases, id: \.self) { gender in
                                    GenderCheckPill(label: gender.label, selected: filters.genders.contains(gender.id),
                                                    action: { onGender(gender.id) })
                                }
                            }
                            // A group of checkboxes, named for what it asks.
                            .accessibilityElement(children: .contain)
                            .accessibilityLabel(Text(CheckInCopy.filterGenderGroup))
                        }
                        CheckInFilterRow(label: CheckInCopy.filterAge, hint: ageLabel(filters.minAge, filters.maxAge)) {
                            RangeSlider(values: [filters.minAge, filters.maxAge], bounds: ageMin...ageMax,
                                        labels: [CheckInCopy.sliderMinAge, CheckInCopy.sliderMaxAge],
                                        onChange: onAge)
                            if dealbreakers {
                                DealbreakerRow(on: filters.ageDealbreaker, onLine: CheckInCopy.dealbreakerOnAge,
                                               label: CheckInCopy.dealbreakerAgeLabel, onChange: onAgeDealbreaker)
                            }
                        }
                        CheckInFilterRow(label: CheckInCopy.filterDistance, hint: distanceLabel(filters.distanceKm)) {
                            RangeSlider(values: [filters.distanceKm], bounds: distanceMin...distanceMax,
                                        labels: [CheckInCopy.sliderDistance],
                                        onChange: { _, km in onDistance(km) })
                            if dealbreakers {
                                DealbreakerRow(on: filters.distanceDealbreaker,
                                               onLine: CheckInCopy.dealbreakerOnDistance,
                                               label: CheckInCopy.dealbreakerDistanceLabel,
                                               onChange: onDistanceDealbreaker)
                            }
                        }
                    }
                    .padding(.bottom, Spacing.xl)
                }
                .padding(.top, 18)
            })
        }
    }
}

/// `CheckinFilterRow` -- the label (14 / 600) and its hint, baseline-aligned, 10 above the control.
/// The hint is the value in violet (13 / 600), or with `hintMuted` an instruction in the muted ink
/// (12 / 500) -- 05b's `Pick all that apply`, read as part of its group's name, not on its own.
struct CheckInFilterRow<Content: View>: View {
    let label: String
    let hint: String?
    var hintMuted: Bool = false
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
                        .font(F.manrope(hintMuted ? 12 : 13, hintMuted ? .medium : .semibold))
                        .foregroundColor(hintMuted ? .liqMuted : .liqPurple)
                        .accessibilityHidden(hintMuted)
                }
            }
            .padding(.bottom, Spacing.lg)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The pill: Profile 22's `LifePill` values -- 34 tall, 13.5, pill radius, 1 stroke.
private let genderPillHeight: CGFloat = 34

/// `GenderCheckPill` (05b ② ③): LifePill's values plus a 16 checkbox square -- empty when off, a violet
/// tick on white when on. A checkbox to VoiceOver ("Women, checked"); the square is decorative.
/// Padding `0 14 0 10` around the 1 stroke. Android's `GenderCheckPill`.
///
/// Not LifePill with a slot: LifePill stays unchanged on Vibe and Profile 22, and the square changes
/// its leading padding. The colours, timing and press treatment are LifePill's.
struct GenderCheckPill: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let ease = reduceMotion ? nil : Motion.cssEase(Motion.fast)
        let brand = reduceMotion ? nil : Animation.timingCurve(0.22, 1, 0.36, 1, duration: Motion.fast)
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                ZStack {
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .animation(ease) { $0.foregroundStyle(selected ? Color.liqElevated : Color.clear) }
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        // `1.5 rgba(29,17,41,.32)`, drawn inside the 16 as CSS's border-box does.
                        .strokeBorder(Color.liqFg.opacity(selected ? 0 : 0.32), lineWidth: 1.5)
                        .animation(ease, value: selected)
                    BrandIconView(icon: .check, size: 12, stroke: 3, tint: .liqPurple)
                        .opacity(selected ? 1 : 0)
                        .animation(ease, value: selected)
                }
                .frame(width: 16, height: 16)
                Text(label)
                    .font(F.manrope(13.5, selected ? .bold : .medium))
                    .animation(ease) { $0.foregroundStyle(selected ? Color.white : Color.liqFg) }
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // `padding: 0 14px 0 10px` plus the 1px border CSS draws inside the same border-box.
            .padding(.leading, 11)
            .padding(.trailing, 15)
            .padding(.vertical, Spacing.xs)
            .frame(minHeight: genderPillHeight)
            .background(
                Capsule()
                    .animation(brand) { $0.foregroundStyle(selected ? Color.liqPurple : Color.liqElevated) }
                    .shadow(color: selected ? .liqPillGlow : .clear, radius: 4, y: 2)
            )
            .overlay(
                Capsule()
                    .strokeBorder(lineWidth: 1)
                    .animation(ease) { $0.foregroundStyle(selected ? Color.liqPurple : Color.liqBorder) }
            )
            .overflowTapTarget()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(selected ? "Checked" : "Not checked")
    }
}

/// `DealbreakerRow` (05b ④ ⑤ ⑥), under the Age and Distance sliders: margin-top 12, the text column
/// flexible and the switch fixed, gap 12. `Dealbreaker` (600 / 13) over its line (12 / 1.4, muted,
/// mt 2): the off line until the switch is on, then the strict one. Profile 10's `ConsentSwitch`,
/// consumed. READ IN THE TICKET'S ORDER: the switch -- its name and state -- then the line; the
/// visible `Dealbreaker` is the switch's name already, so it is not read twice.
struct DealbreakerRow: View {
    let on: Bool
    let onLine: String
    let label: String
    let onChange: (Bool) -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(CheckInCopy.dealbreaker)
                    .font(F.manrope(13, .semibold))
                    .foregroundColor(.liqFg)
                    .accessibilityHidden(true)
                Text(on ? onLine : CheckInCopy.dealbreakerOff)
                    .font(F.manrope(12, .regular))
                    .lineSpacing(12 * 0.4)
                    .foregroundColor(.liqMuted)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilitySortPriority(0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ConsentSwitch(on: on, label: label, onChange: onChange)
                .accessibilitySortPriority(1)
        }
        .padding(.top, 12)
        .accessibilityElement(children: .contain)
    }
}

#Preview("Check-In 05 · A pre-filled") { CheckInFiltersView(filters: CheckInFilters(genders: ["woman"])) }
#Preview("Check-In 05 · B no gender + toast") {
    CheckInFiltersView(filters: CheckInFilters(genders: []), toast: CheckInCopy.filtersToast)
}
#Preview("Check-In 05 · C narrowed") {
    CheckInFiltersView(filters: CheckInFilters(genders: ["woman", "non_binary"], minAge: 25, maxAge: 35, distanceKm: 15))
}
#Preview("Check-In 05b · B two genders, age strict") {
    CheckInFiltersView(filters: CheckInFilters(genders: ["woman", "non_binary"], minAge: 28, maxAge: 35, ageDealbreaker: true),
                       dealbreakers: true)
}
#Preview("Check-In 05b · C both strict") {
    CheckInFiltersView(filters: CheckInFilters(genders: ["woman", "man", "non_binary"], minAge: 25, maxAge: 40, distanceKm: 15,
                                               ageDealbreaker: true, distanceDealbreaker: true),
                       dealbreakers: true)
}
