/*
 * ProfileLifeScreen.kt
 * ShowUp · the two matching steps of "Share some details"
 *   Profile 22 — Life (SHOWUP-175), step 9 of 10
 *   Profile 23 — Future (SHOWUP-176), step 10 of 10, the last step of profile creation
 *
 * ONE SCREEN, TWO STEPS. Future is "the Life column from Profile 22, unchanged" with its own
 * questions and exclusive values, both of which are data on the step (`ProfileLife.kt`). Each ticket
 * gets a named entry point so a reader can find its screen; each is [LifeScreen] with its step.
 */
package com.showup.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Profile 22 (SHOWUP-175). Partnership · Family · Living; `No children` stands alone. */
@Composable
fun ProfileLifeNowScreen(
    state: PickUiState = PickUiState(),
    onTap: (LifeQuestion, String) -> Unit = { _, _ -> },
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) = LifeScreen(PickStep.LifeNow, state, onTap, onContinue, onSkip, onBack, scrollState)

/** Profile 23 (SHOWUP-176). Family first; one family answer at most; "find out together" alone. */
@Composable
fun ProfileLifeAheadScreen(
    state: PickUiState = PickUiState(),
    onTap: (LifeQuestion, String) -> Unit = { _, _ -> },
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) = LifeScreen(PickStep.LifeAhead, state, onTap, onContinue, onSkip, onBack, scrollState)

// ── previews: every state each ticket names, 375 first ──────────────────────────────────────

/** Life state B: all three answered, as the sheet draws it. */
internal val LIFE_B = PickUiState(
    life = LifeDraft(
        mapOf(
            "partnership" to setOf("single", "divorced"),
            "family" to setOf("has_children", "co_parenting"),
            "living" to setOf("living_alone"),
        ),
    ),
)

/** Future state B: all three answered, as the sheet draws it. */
internal val FUTURE_B = PickUiState(
    life = LifeDraft(
        mapOf(
            "family_ahead" to setOf("want_kids"),
            "partnership_ahead" to setOf("monogamous_relationship"),
            "living_ahead" to setOf("stay_local", "move_abroad"),
        ),
    ),
)

@Preview(name = "Life · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PL_A375() { ProfileLifeNowScreen() }

@Preview(name = "Life · B · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PL_B390() { ProfileLifeNowScreen(LIFE_B) }

@Preview(name = "Life · B · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PL_B320() { ProfileLifeNowScreen(LIFE_B) }

@Preview(name = "Future · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PF_A375() { ProfileLifeAheadScreen() }

@Preview(name = "Future · B · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PF_B430() { ProfileLifeAheadScreen(FUTURE_B) }

@Preview(name = "Future · B · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PF_B320() { ProfileLifeAheadScreen(FUTURE_B) }
