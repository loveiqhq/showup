//
//  ProfileInterestsView.swift
//  ShowUp · Profile 21 — Interests (SHOWUP-174), step 8 of 10 of "Share some details"
//
//  The Swift twin of `ProfileInterestsScreen.kt`: one column, two blocks, no visibility band, and
//  the reference's spacing — margin collapsing included, so the gap from the last How I live chip
//  to `What I'm into` is max(16, 14) = 16, not 30. After the last group its 16 sits inside the
//  region's 12 of bottom padding.
//

import SwiftUI

struct ProfileInterestsView: View {
    var state = PickUiState()
    var onLifestyle: (LifestyleField, String) -> Void = { _, _ in }
    var onInterest: (String) -> Void = { _ in }
    var onShowMore: (InterestSection) -> Void = { _ in }
    var onContinue: () -> Void = {}
    var onSkip: () -> Void = {}
    var onBack: () -> Void = {}

    var body: some View {
        let draft = state.interests
        PickScaffold(
            step: .interests,
            onBack: onBack,
            headlineGap: Spacing.lg,
            indicatorGutter: Spacing.xs,
            regionBottomInset: Spacing.xl,
            above: {
                // `margin: 4px 0 18px`.
                InterestsCounter(picked: draft.interests.count)
                    // A count filled from the server is drawn at once, never grown or announced.
                    .id(state.prefill)
                    .padding(.top, Spacing.xs)
                    .padding(.bottom, 18)
            },
            footer: {
                PickFooter(onSkip: onSkip, onContinue: onContinue, saveFailed: state.saveFailed,
                           toastTick: state.toastTick, divided: true)
                    .padding(.bottom, 22)
            }
        ) {
            VStack(alignment: .leading, spacing: 0) {
                CategoryTitle(text: PickCopy.howILive)
                    .padding(.bottom, Spacing.xl)
                ForEach(LifestyleField.allCases, id: \.self) { field in
                    InterestGroup(
                        label: field.label,
                        chips: field.options,
                        role: .radio,
                        isSelected: { draft.lifestyle[field.fieldId] == $0 },
                        onPick: { onLifestyle(field, $0) },
                        toggle: { EmptyView() }
                    )
                    .padding(.bottom, Spacing.xxl)
                }

                CategoryTitle(text: PickCopy.whatImInto)
                    .padding(.bottom, Spacing.xl)
                ForEach(InterestSection.allCases, id: \.self) { section in
                    let open = draft.expanded.contains(section.sectionId)
                    InterestGroup(
                        label: section.label,
                        chips: visibleOptions(section, expanded: open),
                        role: .toggle,
                        isSelected: { draft.interests.contains($0) },
                        // At the cap every UNPICKED chip fades; picked ones still unpick.
                        isFaded: { !draft.interests.contains($0) && draft.capReached },
                        onPick: onInterest,
                        toggle: {
                            if section.collapsible {
                                ShowMoreToggle(expanded: open) { onShowMore(section) }
                                    .padding(.top, Spacing.lg)
                            }
                        }
                    )
                    .padding(.bottom, Spacing.xxl)
                }
            }
            // A PRE-FILL IS NOT A TAP: new chips per pre-fill start at their value.
            .id(state.prefill)
        }
    }
}

// MARK: - the states the ticket names

extension PickUiState {
    /// State B: two How I live answers, six interests.
    static let interestsB = PickUiState(interests: InterestsDraft(
        lifestyle: ["activity": "active", "diet": "flexitarian"],
        interests: ["running", "cooking", "brunch", "live_music", "board_games", "city_breaks"]))

    /// State C: the cap, every How I live group answered.
    static let interestsC = PickUiState(interests: InterestsDraft(
        lifestyle: ["activity": "active", "diet": "flexitarian", "alcohol": "socially",
                    "smoking": "no", "rhythm": "early_bird", "social": "balanced"],
        interests: ["running", "hiking", "cooking", "brunch", "wine", "live_music",
                    "cinema", "board_games", "city_breaks", "road_trips"]))

    /// Frame D: state B with Move open.
    static let interestsD = PickUiState(interests: InterestsDraft(
        lifestyle: ["activity": "active", "diet": "flexitarian"],
        interests: ["running", "cooking", "brunch", "live_music", "board_games", "city_breaks"],
        expanded: ["move"]))

    /// Life state B, as the sheet draws it.
    static let lifeB = PickUiState(life: LifeDraft(answers: [
        "partnership": ["single", "divorced"],
        "family": ["has_children", "co_parenting"],
        "living": ["living_alone"],
    ]))

    /// Future state B, as the sheet draws it.
    static let futureB = PickUiState(life: LifeDraft(answers: [
        "family_ahead": ["want_kids"],
        "partnership_ahead": ["monogamous_relationship"],
        "living_ahead": ["stay_local", "move_abroad"],
    ]))
}

#Preview("Interests · A") { ProfileInterestsView() }
#Preview("Interests · B") { ProfileInterestsView(state: .interestsB) }
#Preview("Interests · C cap") { ProfileInterestsView(state: .interestsC) }
#Preview("Interests · D Move open") { ProfileInterestsView(state: .interestsD) }
#Preview("Life · A") { LifeView(step: .lifeNow) }
#Preview("Life · B") { LifeView(step: .lifeNow, state: .lifeB) }
#Preview("Future · A") { LifeView(step: .lifeAhead) }
#Preview("Future · B") { LifeView(step: .lifeAhead, state: .futureB) }
