//
//  ProfileHeight.swift
//  ShowUp · Profile 14 — Height, step 1 of "Share some details" (SHOWUP-167)
//
//  The Swift twin of `ProfileHeightScreen.kt`. Three states on one column — A empty, B valid, C
//  refused — and nothing moves between them: the tick is inside the field and the refusal is a toast
//  that takes no space.
//
//  THE NUMBER PAD IS THE PLATFORM'S (`.numberPad`), and it has NO RETURN KEY — "Continue is the only
//  submit". Focused on arrival, so the user can type without tapping.
//

import SwiftUI

struct ProfileHeightView: View {
    var state = DetailsUiState()
    var onHeightChange: (String) -> Void = { _ in }
    var onToggleVisibility: () -> Void = {}
    var onContinue: () -> Void = {}
    var onSkip: () -> Void = {}
    var onBack: () -> Void = {}
    /// Opens the keypad on arrival. True in the app; off in previews and the fit harness.
    var autoFocus: Bool = true

    private let step = DetailStep.height

    /// THE FIELD'S OWN TEXT, FILTERED IN `onChange` — never in the binding (ios-app/CLAUDE.md).
    /// Filtering inside the setter left the bound value unchanged when a keystroke was refused, and
    /// a TextField whose binding did not change keeps showing what was typed: a fourth digit or a
    /// pasted letter stayed on screen while the model held three digits. Rewriting THIS value is a
    /// real change, so the field redraws with what is actually kept.
    @State private var text: String

    init(
        state: DetailsUiState = DetailsUiState(),
        onHeightChange: @escaping (String) -> Void = { _ in },
        onToggleVisibility: @escaping () -> Void = {},
        onContinue: @escaping () -> Void = {},
        onSkip: @escaping () -> Void = {},
        onBack: @escaping () -> Void = {},
        autoFocus: Bool = true
    ) {
        self.state = state
        self.onHeightChange = onHeightChange
        self.onToggleVisibility = onToggleVisibility
        self.onContinue = onContinue
        self.onSkip = onSkip
        self.onBack = onBack
        self.autoFocus = autoFocus
        _text = State(initialValue: state.draft.heightText)
    }

    var body: some View {
        DetailsScaffold(
            step: step,
            onBack: onBack,
            hidden: Binding(get: { state.draft.isHidden(step) }, set: { _ in onToggleVisibility() }),
            footer: {
                DetailsFooter(onSkip: onSkip, onContinue: onContinue,
                              toastVisible: state.toast != nil,
                              toastMessage: DetailsCopy.toast(step, state.toast),
                              toastTick: state.toastTick)
            }
        ) {
            FloatingField(
                value: $text,
                label: DetailsCopy.heightLabel,
                placeholder: DetailsCopy.heightPlaceholder,
                // The tick for an integer 120-230 and nothing else. Never an error state: a
                // refusal does not recolour the field.
                valid: parseHeight(state.draft.heightText) != nil,
                keyboard: .numberPad,
                capitalization: .never,
                focusOnAppear: autoFocus
            )
            .onChange(of: text) { _, typed in
                // Digits only, at most three, paste included — then the kept value goes up.
                let kept = filterHeightInput(typed)
                if kept != typed {
                    text = kept
                } else if kept != state.draft.heightText {
                    onHeightChange(kept)
                }
            }
            // A pre-fill (a resume, a back from gender, a late read) comes down the other way.
            .onChange(of: state.draft.heightText) { _, saved in
                if saved != text { text = saved }
            }
            Text(DetailsCopy.heightNote)
                .font(F.manrope(12.5, .medium))
                .lineSpacing(12.5 * 0.4)
                .foregroundColor(.liqSubtle)
                .fixedSize(horizontal: false, vertical: true)
                // `margin: 10px 2px 0`.
                .padding(.top, Spacing.lg)
                .padding(.horizontal, 2)
        }
    }
}

#Preview("Height · A") { ProfileHeightView(autoFocus: false) }
#Preview("Height · B") {
    ProfileHeightView(state: DetailsUiState(draft: DetailDraft(heightText: "175")), autoFocus: false)
}
#Preview("Height · C") {
    ProfileHeightView(state: DetailsUiState(toast: .refusal, toastTick: 1), autoFocus: false)
}
