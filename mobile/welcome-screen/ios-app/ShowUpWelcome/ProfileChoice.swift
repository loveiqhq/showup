//
//  ProfileChoice.swift
//  ShowUp · the five single-select steps of "Share some details"
//    Profile 15 — Gender (SHOWUP-168)       Profile 16 — Orientation (SHOWUP-169)
//    Profile 18 — Education (SHOWUP-171)    Profile 19 — Religion (SHOWUP-172)
//    Profile 20 — Politics (SHOWUP-173)
//
//  The Swift twin of `ProfileChoiceScreen.kt`. ONE SCREEN, FIVE PAYLOADS: the differences are data
//  on `DetailStep`. The footer follows the step — mandatory steps have no SkipLink and refuse an
//  empty Continue; the skippable three treat an empty Continue exactly like Skip.
//

import SwiftUI

struct ProfileChoiceView: View {
    let step: DetailStep
    var state = DetailsUiState()
    var onTap: (String) -> Void = { _ in }
    var onToggleVisibility: () -> Void = {}
    var onContinue: () -> Void = {}
    var onSkip: () -> Void = {}
    var onBack: () -> Void = {}

    var body: some View {
        DetailsScaffold(
            step: step,
            onBack: onBack,
            hidden: Binding(get: { state.draft.isHidden(step) }, set: { _ in onToggleVisibility() }),
            // Flash where rows can hide below the fold; education's ticket says not to.
            flashIndicator: step == .orientation || step == .religion || step == .politics,
            footer: {
                DetailsFooter(onSkip: step.mandatory ? nil : onSkip, onContinue: onContinue,
                              toastVisible: state.toast != nil,
                              toastMessage: DetailsCopy.toast(step, state.toast),
                              toastTick: state.toastTick)
            }
        ) {
            DetailsOptionList(step: step, selected: state.draft.selection(step),
                              prefill: state.prefill, onTap: onTap)
        }
    }
}

#Preview("Gender · A") { ProfileChoiceView(step: .gender) }
#Preview("Gender · C") { ProfileChoiceView(step: .gender, state: DetailsUiState(toast: .refusal, toastTick: 1)) }
#Preview("Orientation · B") {
    ProfileChoiceView(step: .orientation, state: DetailsUiState(draft: DetailDraft(orientation: "bisexual")))
}
#Preview("Education · B") {
    ProfileChoiceView(step: .education, state: DetailsUiState(draft: DetailDraft(education: "university_degree")))
}
#Preview("Religion · B") {
    ProfileChoiceView(step: .religion, state: DetailsUiState(draft: DetailDraft(religion: "buddhist")))
}
#Preview("Politics · B") {
    ProfileChoiceView(step: .politics, state: DetailsUiState(draft: DetailDraft(politics: "middle")))
}
