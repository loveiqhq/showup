/*
 * ProfileInterestsScreen.kt
 * ShowUp · Profile 21 — Interests (SHOWUP-174), step 8 of 10 of "Share some details"
 *
 * One column, two blocks, no visibility band. Everything is drawn from [PickUiState]; every tap goes
 * out as a lambda and the view model decides what it means (`PickStepsViewModel`).
 *
 * THE SPACING IS THE REFERENCE'S, MARGIN COLLAPSING INCLUDED. Each group carries `margin-bottom: 16`
 * and the How I live block `margin-bottom: 14` -- and in CSS a block's last child's bottom margin
 * collapses into the block's own, so the gap from the last How I live chip to `What I'm into` is
 * max(16, 14) = 16, not 30. Drawn here as one 16. After the last What I'm into group the 16 sits
 * inside the region's 12 of bottom padding, for 28 under the last chip.
 */
package com.showup.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.showup.designsystem.Spacing

@Composable
fun ProfileInterestsScreen(
    state: PickUiState = PickUiState(),
    onLifestyle: (LifestyleField, String) -> Unit = { _, _ -> },
    onInterest: (String) -> Unit = {},
    onShowMore: (InterestSection) -> Unit = {},
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
) {
    val draft = state.interests
    PickScaffold(
        step = PickStep.Interests,
        onBack = onBack,
        headlineGap = Spacing.lg,
        scrollState = scrollState,
        // "Flash the platform scroll indicator once on arrival" -- the region scrolls on every phone.
        flashIndicator = true,
        indicatorGutter = Spacing.xs,
        regionBottomInset = Spacing.xl,
        above = {
            // `margin: 4px 0 18px`.
            Column {
                Spacer(Modifier.height(Spacing.xs))
                InterestsCounter(picked = draft.interests.size)
                Spacer(Modifier.height(18.dp))
            }
        },
        footer = {
            Box(Modifier.padding(bottom = 22.dp)) {
                PickFooter(onContinue = onContinue, onSkip = onSkip, saveFailed = state.saveFailed, divided = true)
            }
        },
    ) {
        // A PRE-FILL IS NOT A TAP: new chips per pre-fill start at their value, so only a tap runs
        // the 180 ms change ("pre-fill saved answers and interests without animation").
        key(state.prefill) {
            CategoryTitle(PickCopy.HOW_I_LIVE)
            Spacer(Modifier.height(Spacing.xl))
            LifestyleField.entries.forEach { field ->
                InterestGroup(
                    label = field.label,
                    chips = field.options,
                    isSelected = { draft.lifestyle[field.fieldId] == it },
                    onPick = { onLifestyle(field, it) },
                    role = ChipRole.Radio,
                )
                Spacer(Modifier.height(Spacing.xxl))
            }

            CategoryTitle(PickCopy.WHAT_IM_INTO)
            Spacer(Modifier.height(Spacing.xl))
            InterestSection.entries.forEach { section ->
                val open = section.sectionId in draft.expanded
                InterestGroup(
                    label = section.label,
                    chips = visibleOptions(section, open),
                    isSelected = { it in draft.interests },
                    // At the cap every UNPICKED chip fades; picked ones still unpick.
                    isFaded = { it !in draft.interests && draft.capReached },
                    onPick = onInterest,
                    role = ChipRole.Toggle,
                    toggle = if (section.collapsible) {
                        {
                            ShowMoreToggle(
                                expanded = open,
                                onClick = { onShowMore(section) },
                                modifier = Modifier.testTag(showMoreTag(section.sectionId)),
                            )
                        }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

// ── previews: every state the ticket names, 375 first ───────────────────────────────────────

/** State B: two How I live answers, six interests. */
internal val INTERESTS_B = PickUiState(
    interests = InterestsDraft(
        lifestyle = mapOf("activity" to "active", "diet" to "flexitarian"),
        interests = setOf("running", "cooking", "brunch", "live_music", "board_games", "city_breaks"),
    ),
)

/** State C: the cap, with every How I live group answered. */
internal val INTERESTS_C = PickUiState(
    interests = InterestsDraft(
        lifestyle = mapOf(
            "activity" to "active", "diet" to "flexitarian", "alcohol" to "socially",
            "smoking" to "no", "rhythm" to "early_bird", "social" to "balanced",
        ),
        interests = setOf(
            "running", "hiking", "cooking", "brunch", "wine", "live_music",
            "cinema", "board_games", "city_breaks", "road_trips",
        ),
    ),
)

/** Frame D: state B with Move open. */
internal val INTERESTS_D = INTERESTS_B.copy(interests = INTERESTS_B.interests.copy(expanded = setOf("move")))

@Preview(name = "Interests · A · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PI_A375() { ProfileInterestsScreen() }

@Preview(name = "Interests · B · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PI_B390() { ProfileInterestsScreen(INTERESTS_B) }

@Preview(name = "Interests · C cap · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PI_C430() { ProfileInterestsScreen(INTERESTS_C) }

@Preview(name = "Interests · D Move open · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PI_D320() { ProfileInterestsScreen(INTERESTS_D) }
