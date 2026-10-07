/*
 * ProfileChoiceScreen.kt
 * ShowUp · the five single-select steps of "Share some details"
 *   Profile 15 — Gender (SHOWUP-168)        Profile 16 — Orientation (SHOWUP-169)
 *   Profile 18 — Education (SHOWUP-171)     Profile 19 — Religion (SHOWUP-172)
 *   Profile 20 — Politics (SHOWUP-173)
 *
 * ONE SCREEN, FIVE PAYLOADS. Orientation "is the gender screen with six options"; education,
 * religion and politics "build nothing". What differs between them is data on [DetailStep] -- the
 * options, whether the step is mandatory, whether a second tap clears -- and the copy in
 * [DetailsCopy]. Each ticket gets a named entry point below so a reader can find its screen, and
 * each named screen is exactly this one with its step.
 *
 * WHAT A TAP DOES IS NOT DECIDED HERE. The row reports it; the view model applies `pickSingle`,
 * which keeps a selection on gender and orientation ("a radio never clears") and clears it on the
 * other three (the named exception, "screen logic in the row's tap handler").
 *
 * THE FOOTER FOLLOWS THE STEP. Gender and orientation are mandatory: no SkipLink, the row is
 * `flex-end`, and an empty Continue shows the toast. The other three carry `Skip for now`, and an
 * empty Continue behaves exactly like it -- no toast, no refusal.
 */
package com.showup.profile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ProfileChoiceScreen(
    step: DetailStep,
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    require(!step.multiSelect && step != DetailStep.Height) { "$step is not a single-select step" }
    val scroll = rememberScrollState()
    DetailsScaffold(
        step = step,
        onBack = onBack,
        hidden = state.draft.isHidden(step),
        onToggleVisibility = onToggleVisibility,
        scrollState = scroll,
        // Flash where rows can be hidden below the fold. Gender's four always fit; education's
        // deficit is about 6 at 375 x 667 and its ticket says not to flash ("nothing meaningful is
        // hidden"). The flash itself only happens when the list really overflows.
        flashIndicator = step == DetailStep.Orientation ||
            step == DetailStep.Religion ||
            step == DetailStep.Politics,
        footer = {
            DetailsFooter(
                onContinue = onContinue,
                onSkip = if (step.mandatory) null else onSkip,
                toastVisible = state.toast != null,
                toastMessage = toastMessage(step, state.toast),
            )
        },
    ) {
        DetailsOptionList(
            step = step,
            selected = state.draft.selection(step),
            onTap = onTap,
            scrollState = scroll,
            prefill = state.prefill,
        )
    }
}

// ── one named screen per ticket ─────────────────────────────────────────────────────────────

/** Profile 15 (SHOWUP-168). Four options, mandatory, a radio never clears. */
@Composable
fun ProfileGenderScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
) = ProfileChoiceScreen(
    DetailStep.Gender, state, onTap, onToggleVisibility, onContinue, onSkip = {}, onBack = onBack,
)

/** Profile 16 (SHOWUP-169). Six options in a fixed order whatever gender was saved; mandatory. */
@Composable
fun ProfileOrientationScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
) = ProfileChoiceScreen(
    DetailStep.Orientation, state, onTap, onToggleVisibility, onContinue, onSkip = {}, onBack = onBack,
)

/** Profile 18 (SHOWUP-171). Four options, skippable, a second tap clears. */
@Composable
fun ProfileEducationScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) = ProfileChoiceScreen(DetailStep.Education, state, onTap, onToggleVisibility, onContinue, onSkip, onBack)

/** Profile 19 (SHOWUP-172). Nine options, skippable, scrolls on every device. */
@Composable
fun ProfileReligionScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) = ProfileChoiceScreen(DetailStep.Religion, state, onTap, onToggleVisibility, onContinue, onSkip, onBack)

/** Profile 20 (SHOWUP-173). Eight options in linear order, skippable, fits only at 430 x 932. */
@Composable
fun ProfilePoliticsScreen(
    state: DetailsUiState = DetailsUiState(),
    onTap: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) = ProfileChoiceScreen(DetailStep.Politics, state, onTap, onToggleVisibility, onContinue, onSkip, onBack)

// ── previews: every state each ticket names, at 375 first ──────────────────────────────────

private fun picked(step: DetailStep, value: String) =
    DetailsUiState(draft = DetailDraft().withSelection(step, value))

private val REFUSED = DetailsUiState(toast = DetailToast.Refusal, toastTick = 1)

@Preview(name = "Gender · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PG_A375() { ProfileGenderScreen() }

@Preview(name = "Gender · B non-binary · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PG_B390() { ProfileGenderScreen(picked(DetailStep.Gender, "non_binary")) }

@Preview(name = "Gender · C refused · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PG_C430() { ProfileGenderScreen(REFUSED) }

@Preview(name = "Orientation · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PO_A375() { ProfileOrientationScreen() }

@Preview(name = "Orientation · B bisexual · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PO_B390() { ProfileOrientationScreen(picked(DetailStep.Orientation, "bisexual")) }

@Preview(name = "Orientation · C refused · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PO_C375() { ProfileOrientationScreen(REFUSED) }

@Preview(name = "Education · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_A375() { ProfileEducationScreen() }

@Preview(name = "Education · B degree · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_B390() { ProfileEducationScreen(picked(DetailStep.Education, "university_degree")) }

@Preview(name = "Religion · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PR_A375() { ProfileReligionScreen() }

@Preview(name = "Religion · B buddhist · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_B390() { ProfileReligionScreen(picked(DetailStep.Religion, "buddhist")) }

@Preview(name = "Politics · A · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PP_A430() { ProfilePoliticsScreen() }

@Preview(name = "Politics · B middle · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PP_B375() { ProfilePoliticsScreen(picked(DetailStep.Politics, "middle")) }
