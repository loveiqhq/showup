//
//  ProfileDatingLanguage.swift
//  ShowUp · Profile 17 — Dating language, step 4 of "Share some details" (SHOWUP-170)
//
//  The Swift twin of `ProfileDatingLanguageScreen.kt`. Eight languages, multi-select with no limit,
//  through `CheckRow`. NO refused state: an empty Continue is a skip, so the only toast this screen
//  can show is a failed save's.
//

import SwiftUI

struct ProfileDatingLanguageView: View {
    var state = DetailsUiState()
    var onTap: (String) -> Void = { _ in }
    var onToggleVisibility: () -> Void = {}
    var onContinue: () -> Void = {}
    var onSkip: () -> Void = {}
    var onBack: () -> Void = {}

    private let step = DetailStep.datingLanguage

    var body: some View {
        DetailsScaffold(
            step: step,
            onBack: onBack,
            hidden: Binding(get: { state.draft.isHidden(step) }, set: { _ in onToggleVisibility() }),
            flashIndicator: true,
            footer: {
                DetailsFooter(onSkip: onSkip, onContinue: onContinue,
                              toastVisible: state.toast == .saveFailed,
                              toastMessage: DetailsCopy.saveFailedProposed,
                              toastTick: state.toastTick)
            }
        ) {
            DetailsCheckList(step: step, ticked: state.draft.languages, prefill: state.prefill, onTap: onTap)
        }
    }
}

#Preview("Dating language · A") { ProfileDatingLanguageView() }
#Preview("Dating language · B") {
    ProfileDatingLanguageView(state: DetailsUiState(draft: DetailDraft(languages: ["german", "english", "spanish"])))
}
