/*
 * CheckInVibeScreen.kt
 * ShowUp · Check-In 04 (SHOWUP-181): Vibe -- one to five of twenty, in three groups
 *
 * `VibeStep` from `screen-checkin-vibe-reference.jsx`, inside the step shell. Chips are Profile 22's
 * [LifePill], AS BUILT: no new chip component, no new props, no locked or dimmed state -- "chips are
 * never locked". At 5/5 a tap on an idle chip selects nothing and raises the max toast (the view
 * model); the counter turning violet is the only visual change.
 *
 * The chip groups are the only flexible element and scroll on EVERY device (about twice the space
 * they get at 390 x 844). Headline, note, counter and CTA never move -- except at the largest system
 * fonts, where the header leaves the groups less than [REGION_FLOOR] and the two scroll together.
 */
package com.showup.checkin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.profile.LifePill

/** Fit-harness anchors. */
internal fun vibeTag(id: String) = "vibe-$id"
internal const val VIBE_COUNTER_TAG = "vibe-counter"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInVibeScreen(
    picked: Set<String>,
    modifier: Modifier = Modifier,
    toast: String? = null,
    toastTick: Int = 0,
    groupsScroll: ScrollState = rememberScrollState(),
    onTap: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    CheckInStepShell(
        step = CheckInStep.Vibe,
        onBack = onBack,
        onClose = onClose,
        modifier = modifier,
        footer = {
            CheckInFooter(
                label = CheckInCopy.CONTINUE,
                onClick = onContinue,
                dimmed = picked.isEmpty(),
                toast = toast,
                toastTick = toastTick,
            )
        },
    ) {
        CheckInFlexBody(
            Modifier.fillMaxSize(),
            header = {
                Spacer(Modifier.height(Spacing.md))
                CheckInStepHeadline(CheckInCopy.VIBE_HEADLINE)
                CoachNote(CheckInCopy.VIBE_NOTE, warm = true, modifier = Modifier.padding(top = 14.dp))

                // The counter row, baseline-aligned: `Pick up to 5 — mix and match` · `n/5`, the count
                // turning violet at 5/5. A polite live region.
                Row(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        CheckInCopy.VIBE_COUNTER_LEAD,
                        Modifier.alignByBaseline().weight(1f, fill = false),
                        color = Fg,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        "${picked.size}/$VIBE_MAX",
                        Modifier
                            .alignByBaseline()
                            .padding(start = Spacing.lg)
                            .testTag(VIBE_COUNTER_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (picked.size >= VIBE_MAX) Purple else FgSubtle,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            },
        ) {
            // THE CHIP GROUPS -- the only flexible element; they scroll on every device.
            Column(
                Modifier
                    .padding(top = Spacing.xl)
                    .fillMaxSize()
                    .verticalScroll(groupsScroll)
                    .padding(bottom = Spacing.xl)
                    .testTag(CHECK_IN_SCROLL_TAG),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                VibeGroup.entries.forEach { group ->
                    Column(Modifier.semantics { contentDescription = group.label }) {
                        Row(Modifier.padding(bottom = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text(
                                group.label,
                                Modifier.alignByBaseline(),
                                color = Fg,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Text(
                                group.hint.uppercase(),
                                Modifier.alignByBaseline(),
                                color = FgSubtle,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.08.em,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            group.vibes.forEach { vibe ->
                                LifePill(
                                    label = vibe.label,
                                    selected = vibe.id in picked,
                                    onClick = { onTap(vibe.id) },
                                    modifier = Modifier.testTag(vibeTag(vibe.id)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Check-In 04 · A empty · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewVibeEmpty() = CheckInVibeScreen(picked = emptySet())

@Preview(name = "Check-In 04 · C picked · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewVibePicked() = CheckInVibeScreen(picked = setOf("coffee_chat", "playful", "no_rush"))

@Preview(name = "Check-In 04 · D five + toast · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewVibeMax() = CheckInVibeScreen(
    picked = setOf("coffee_chat", "sunset", "calm", "playful", "no_rush"),
    toast = CheckInCopy.VIBE_TOAST_MAX,
)
