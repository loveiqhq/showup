/*
 * ProfileHeightScreen.kt
 * ShowUp · Profile 14 — Height, step 1 of "Share some details" (SHOWUP-167)
 *
 * Three states on one column -- A empty, B a valid value, C Continue refused -- and nothing moves
 * between them: the tick appears inside the field, and the refusal is a toast over the footer that
 * takes up no space. The shell is [DetailsScaffold], built for this ticket and consumed by the six
 * steps after it.
 *
 * THE KEYPAD IS THE PLATFORM'S. `KeyboardType.Number` is TYPE_CLASS_NUMBER, the ticket's own ask;
 * its height is never assumed, and the scaffold's safe-drawing insets keep the band and the footer
 * above it on every keyboard. Android's keypad has an action key where the iOS number pad has none;
 * here it does what Continue does -- the same rule every field in the flow follows ("Go does exactly
 * what Continue does").
 */
package com.showup.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.FloatingField
import com.showup.designsystem.Manrope
import com.showup.designsystem.Spacing

/** The field, for the fit harness and the keyboard check. */
internal const val HEIGHT_FIELD_TAG = "height-field"

@Composable
fun ProfileHeightScreen(
    state: DetailsUiState = DetailsUiState(),
    onHeightChange: (String) -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
    /**
     * Opens the keypad on arrival. True in the app -- "the user can type without tapping". Off in
     * previews and the fit harness, which have no window to show a keyboard in.
     */
    autoFocus: Boolean = true,
) {
    val step = DetailStep.Height
    val text = state.draft.heightText
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    DetailsScaffold(
        step = step,
        onBack = onBack,
        hidden = state.draft.isHidden(step),
        onToggleVisibility = onToggleVisibility,
        footer = {
            DetailsFooter(
                onContinue = onContinue,
                onSkip = onSkip,
                toastVisible = state.toast != null,
                toastMessage = toastMessage(step, state.toast),
            )
        },
    ) {
        FloatingField(
            value = text,
            onValueChange = onHeightChange,
            label = DetailsCopy.HEIGHT_LABEL,
            placeholder = DetailsCopy.HEIGHT_PLACEHOLDER,
            modifier = Modifier.testTag(HEIGHT_FIELD_TAG),
            // The tick, for an integer 120-230 inclusive and nothing else. No error state: a refusal
            // never recolours the field ("there is no shake, the field is not recoloured").
            valid = parseHeight(text) != null,
            keyboardType = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
            onSubmit = onContinue,
            focusRequester = focus,
        )
        Text(
            DetailsCopy.HEIGHT_NOTE,
            // `margin: 10px 2px 0`.
            modifier = Modifier.padding(top = Spacing.lg, start = 2.dp, end = 2.dp),
            color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp, lineHeight = (12.5f * 1.4f).sp,
        )
    }
}

/** The toast's sentence: the step's own refusal, or the proposed save-failure line. */
internal fun toastMessage(step: DetailStep, toast: DetailToast?): String = when (toast) {
    DetailToast.SaveFailed -> DetailsCopy.SAVE_FAILED_PROPOSED
    else -> DetailsCopy.refusal(step) ?: DetailsCopy.SAVE_FAILED_PROPOSED
}

// ── previews: the three states the ticket names, at its three frames ───────────────────────
//
// Keyboard closed, because a preview has none -- the keypad-open check is the fit harness's and a
// device's. 375 x 667 first: it is where the spacer runs out.

private val A = DetailsUiState()
private val B = DetailsUiState(draft = DetailDraft(heightText = "175"))
private val C = DetailsUiState(toast = DetailToast.Refusal, toastTick = 1)

@Preview(name = "Height · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PH_A375() { ProfileHeightScreen(A, autoFocus = false) }

@Preview(name = "Height · B · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PH_B375() { ProfileHeightScreen(B, autoFocus = false) }

@Preview(name = "Height · C · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PH_C375() { ProfileHeightScreen(C, autoFocus = false) }

@Preview(name = "Height · B · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PH_B390() { ProfileHeightScreen(B, autoFocus = false) }

@Preview(name = "Height · C · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PH_C430() { ProfileHeightScreen(C, autoFocus = false) }

@Preview(name = "Height · B hidden · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PH_B320() {
    ProfileHeightScreen(
        DetailsUiState(draft = DetailDraft(heightText = "175", hidden = setOf("height"))),
        autoFocus = false,
    )
}
