/*
 * CheckInAvailabilityScreen.kt
 * ShowUp · Check-In 02 (SHOWUP-179): Today and Tomorrow -- one `AvailabilityStep`, two days
 *
 * "Two screens, one component, one story. Splitting them is how a grid gets built twice."
 * [AvailabilityStep] takes the day's grid as a value; Today and Tomorrow differ only in that grid,
 * their copy, and whether an empty Continue confirms (Today) or toasts (Tomorrow).
 *
 * LAYOUT, from the reference: header block (headline mt 8 → subline mt 10 → How it works mt 12) →
 * THE GRID (flex 1, the only thing that scrolls, mt 13) → summary (mt 8) → legend → CheckInFooter.
 * Opening How it works takes its height from the grid; the summary, legend and CTA never move
 * (`CheckInFitTest`). Slots, the CTA and the headline are never shrunk to fit. Where the header
 * leaves the grid less than [REGION_FLOOR] -- the panel open on a small phone, the largest system
 * fonts -- the header and the grid scroll together above the summary ([CheckInFlexBody]).
 */
package com.showup.checkin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.showup.designsystem.Motion
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.blurBehindDialog
import com.showup.designsystem.rememberMotion
import androidx.compose.ui.Modifier
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One availability step -- Today ([CheckInStep.Today]) or Tomorrow. Takes values, returns taps.
 *
 * @param picked this grid's picks, by hour.
 */
@Composable
fun CheckInAvailabilityScreen(
    grid: AvailabilityGrid,
    picked: Map<Int, SlotTier>,
    howItWorksOpen: Boolean,
    modifier: Modifier = Modifier,
    toast: String? = null,
    toastTick: Int = 0,
    dialogOpen: Boolean = false,
    gridScroll: ScrollState = rememberScrollState(),
    onSlot: (Int) -> Unit = {},
    onClearAll: () -> Unit = {},
    onToggleHowItWorks: () -> Unit = {},
    onContinue: () -> Unit = {},
    onConfirmNoToday: () -> Unit = {},
    onCancelNoToday: () -> Unit = {},
    onDismissNoToday: () -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val today = grid.day == CheckInStep.Today
    Box(modifier.fillMaxSize()) {
        // While the confirm is up, the screen behind is out of TalkBack's reach: focus is trapped
        // in the card, and returns here when it closes.
        val motion = rememberMotion()
        val scrim by animateFloatAsState(
            if (dialogOpen) 1f else 0f,
            tween(if (motion.enabled) Motion.REVEAL else 0, easing = ShowUpEasing),
            label = "dialogBlur",
        )
        val behind = (if (dialogOpen) Modifier.clearAndSetSemantics {} else Modifier).blurBehindDialog(scrim)
        CheckInStepShell(
            step = grid.day,
            onBack = onBack,
            onClose = onClose,
            modifier = behind,
            footer = {
                CheckInFooter(
                    label = CheckInCopy.CONTINUE,
                    onClick = onContinue,
                    // Today's Continue is never disabled; Tomorrow's dims until an hour is picked.
                    dimmed = !today && picked.isEmpty(),
                    toast = toast,
                    toastTick = toastTick,
                )
            },
        ) {
            AvailabilityStep(grid, picked, howItWorksOpen, gridScroll, onSlot, onClearAll, onToggleHowItWorks)
        }
        if (dialogOpen) {
            NoHoursTodayDialog(onConfirm = onConfirmNoToday, onCancel = onCancelNoToday, onDismiss = onDismissNoToday)
        }
    }
}

/** `AvailabilityStep` -- the one grid component for both days. */
@Composable
fun AvailabilityStep(
    grid: AvailabilityGrid,
    picked: Map<Int, SlotTier>,
    howItWorksOpen: Boolean,
    gridScroll: ScrollState,
    onSlot: (Int) -> Unit,
    onClearAll: () -> Unit,
    onToggleHowItWorks: () -> Unit,
) {
    val today = grid.day == CheckInStep.Today
    Column(Modifier.fillMaxSize()) {
        // The header block (flex none) over the grid (flex 1) -- with the floor, see CheckInFlexBody:
        // How it works open on a small phone scrolls the two together instead of crushing what follows.
        CheckInFlexBody(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            header = {
                Spacer(Modifier.height(Spacing.md))
                CheckInStepHeadline(if (today) CheckInCopy.TODAY_HEADLINE else CheckInCopy.TOMORROW_HEADLINE)
                val sub = remember(today) {
                    buildAnnotatedString {
                        if (today) {
                            // `How it works` in primary 700 -- styled text, not a link. The disclosure below
                            // is the control.
                            CheckInCopy.TODAY_SUB.forEach { (t, strong) ->
                                if (strong) withStyle(SpanStyle(color = Purple, fontWeight = FontWeight.Bold)) { append(t) }
                                else append(t)
                            }
                        } else {
                            append(CheckInCopy.TOMORROW_SUB)
                        }
                    }
                }
                val subStyle = TextStyle(color = FgSubtle, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14f * 1.45f).sp)
                // `textWrap: 'pretty'`: the sheet's `… See How it works for` / `more info.`, not `info.` alone.
                Text(sub, Modifier.padding(top = Spacing.lg).prettyWrap(sub, subStyle), style = subStyle)
                HowItWorks(
                    open = howItWorksOpen,
                    onToggle = onToggleHowItWorks,
                    modifier = Modifier.padding(top = Spacing.xl),
                    action = { if (picked.isNotEmpty()) ClearAll(onClearAll) },
                )
            },
        ) {
            // THE GRID -- the only flexible element and the only thing that scrolls.
            Box(
                Modifier
                    .padding(top = 13.dp)
                    .fillMaxSize()
                    .testTag(GRID_TAG),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(gridScroll)
                        .padding(bottom = 2.dp)
                        .testTag(CHECK_IN_SCROLL_TAG),
                ) {
                    AvailabilityGridRows(grid, picked, onSlot)
                }
            }
        }

        AvailabilitySummary(grid, picked, Modifier.padding(top = Spacing.md))
        AvailabilityLegend()
    }
}

// ── previews: the four frames the ticket names, at the kit's 4:20pm ───────────────────────────────

private val PREVIEW_DAYS = checkInDays(LocalDateTime.of(LocalDate.of(2026, 10, 13), java.time.LocalTime.of(16, 20)))

@Preview(name = "Check-In 02 · A Today picked · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewTodayPicked() = CheckInAvailabilityScreen(
    grid = gridOf(CheckInStep.Today, PREVIEW_DAYS),
    picked = mapOf(18 to SlotTier.Preferred, 19 to SlotTier.Preferred, 20 to SlotTier.Available),
    howItWorksOpen = false,
)

@Preview(name = "Check-In 02 · B Today empty + confirm · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewTodayConfirm() = CheckInAvailabilityScreen(
    grid = gridOf(CheckInStep.Today, PREVIEW_DAYS),
    picked = emptyMap(),
    howItWorksOpen = false,
    dialogOpen = true,
)

@Preview(name = "Check-In 02 · C Tomorrow empty + toast · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewTomorrowToast() = CheckInAvailabilityScreen(
    grid = gridOf(CheckInStep.Tomorrow, PREVIEW_DAYS),
    picked = emptyMap(),
    howItWorksOpen = false,
    toast = CheckInCopy.TOMORROW_TOAST,
)

@Preview(name = "Check-In 02 · D Tomorrow picked · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewTomorrowPicked() = CheckInAvailabilityScreen(
    grid = gridOf(CheckInStep.Tomorrow, PREVIEW_DAYS),
    picked = mapOf(12 to SlotTier.Preferred, 13 to SlotTier.Preferred, 19 to SlotTier.Available),
    howItWorksOpen = false,
)
