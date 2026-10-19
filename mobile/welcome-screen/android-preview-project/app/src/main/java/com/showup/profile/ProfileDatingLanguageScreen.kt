/*
 * ProfileDatingLanguageScreen.kt
 * ShowUp · Profile 17 — Dating language, step 4 of "Share some details" (SHOWUP-170)
 *
 * Eight languages, multi-select with no limit, through [DetailsScaffold] and the group's new
 * multi-select row, [com.showup.designsystem.CheckRow]. Two states -- nothing ticked, some ticked --
 * and NO refused state: "Continue with nothing ticked behaves exactly like Skip". So there is no
 * toast to draw on this screen except a failed save's.
 *
 * Saved and reported in LIST order, never tap order (`inListOrder`). The list opens scrolled to the
 * top on a resume, not to a ticked row.
 */
package com.showup.profile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ProfileDatingLanguageScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val step = DetailStep.DatingLanguage
    val scroll = rememberScrollState()
    DetailsScaffold(
        step = step,
        onBack = onBack,
        hidden = state.draft.isHidden(step),
        onToggleVisibility = onToggleVisibility,
        scrollState = scroll,
        flashIndicator = true,
        footer = {
            DetailsFooter(
                onContinue = onContinue,
                onSkip = onSkip,
                // Only ever a failed save here: this step refuses nothing.
                toastVisible = state.toast == DetailToast.SaveFailed,
                toastMessage = DetailsCopy.SAVE_FAILED_PROPOSED,
            )
        },
    ) {
        DetailsCheckList(
            step = step,
            ticked = state.draft.languages,
            onTap = onTap,
            scrollState = scroll,
            prefill = state.prefill,
        )
    }
}

// ── previews: both states, at the three frames; 375 is where eight rows do not fit ────────────

private val THREE = DetailsUiState(
    draft = DetailDraft(languages = setOf("german", "english", "spanish")),
)

@Preview(name = "Dating language · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PL_A375() { ProfileDatingLanguageScreen() }

@Preview(name = "Dating language · B · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PL_B375() { ProfileDatingLanguageScreen(THREE) }

@Preview(name = "Dating language · B · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PL_B390() { ProfileDatingLanguageScreen(THREE) }

@Preview(name = "Dating language · A · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PL_A430() { ProfileDatingLanguageScreen() }
