/*
 * PickChrome.kt
 * ShowUp · the column Interests, Life and Future render in (SHOWUP-174 to SHOWUP-176), its footer,
 * and the copy the three share
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * NOT `DetailsScaffold`, AND NOT A FORK OF IT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * All three tickets say it: "the scaffold carries the visibility band and a single answer list; this
 * screen has neither. It is its own column built from the shared chrome. Do not fork the scaffold to
 * fit it." So this is a second, smaller column -- the same header, bar, headline, backdrop and
 * insets the scaffold uses, through the same components, and none of the scaffold itself:
 *
 *     AppHeader                52, "Share some details", back chevron, trailing slot empty
 *     content                  flex 1, padding 4 / 24 / 0
 *       StepProgress           steps = SHARE_STEPS_TOTAL, margin-bottom 22
 *       headline               Lora 700 / 30 / 1.12 / -0.015em, balanced
 *       [above]                Interests: the counter · Life and Future: the sub
 *       answer region          flex 1, THE ONLY THING THAT SCROLLS
 *       [footer]               Interests: hairline + 12 / 22 · Life and Future: 16 / 22
 *
 * The header, the bar, the headline, the counter or sub, and the footer never move -- not between
 * states, not while the region scrolls, not when a group opens. Every device difference lands in
 * the region, which is the column's one flexible element.
 *
 * THE FLOOR UNDER THE REGION is the detail group's, for its reason: at the largest system font on a
 * small phone the fixed part alone outgrows the screen, and a region squeezed to nothing would hide
 * every chip. Below the floor the top part scrolls as one instead, while the footer stays pinned.
 * At the default font on every phone in the matrix the floor never engages.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Spacing
import com.showup.designsystem.StepProgress
import com.showup.designsystem.scrollIndicator
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop

/** Fit-harness anchors, measured as boxes rather than read as text. */
internal const val PICK_CTA_TAG = "pick-cta"
internal const val PICK_REGION_TAG = "pick-region"
internal const val PICK_HEADLINE_TAG = "pick-headline"
internal const val PICK_FOOTER_TAG = "pick-footer"
internal const val PICK_ABOVE_TAG = "pick-above"

/**
 * Every string the three screens share or headline with, verbatim from the tickets.
 * `audit/verify-profile.py` quotes each back against the ticket text.
 *
 * EVERY APOSTROPHE IS THE TICKET'S STRAIGHT ONE, the headline's `you'd` included. The detail group's
 * "typographic apostrophe if the platform font renders one" is written into Profiles 16 to 18 only;
 * Profile 21 says "copy matches the strings above exactly", and its Jira text, spec sheet and
 * reference all have `you'd`. Until 13 October 2026 this headline had `you’d`.
 */
internal object PickCopy {
    const val HOW_I_LIVE = "How I live"
    const val WHAT_IM_INTO = "What I'm into"
    const val SHOW_MORE = "Show more"
    const val SHOW_LESS = "Show less"

    /** Life and Future share it, word for word. */
    const val LIFE_SUB = "Select all that apply. Never shown on your profile. Used to find the right matches."

    /** The headline as runs: text, and whether it is the one italic em with the orange wash. */
    fun headline(step: PickStep): List<Pair<String, Boolean>> = when (step) {
        PickStep.Interests ->
            listOf("Show how you live and what you love, so others know if you'd " to false, "click" to true)
        PickStep.LifeNow -> listOf("What fits your current " to false, "life" to true, "?" to false)
        PickStep.LifeAhead ->
            listOf("What do you imagine for your future " to false, "life" to true, "?" to false)
    }
}

/**
 * The column.
 *
 * @param headlineGap the headline's margin-bottom: 10 on Interests, 8 on Life and Future.
 * @param above what sits between the headline and the region, with its own spacing.
 * @param indicatorGutter how far the region reaches into the right gutter, so the scroll indicator
 *   draws beside the chips rather than over the last one -- Interests' `padding-right: 4;
 *   margin-right: -4`. Life and Future set none, so theirs is 0.
 * @param regionBottomInset padding inside the scroll, after the last chip: Interests' 12.
 * @param flashIndicator flash the scroll indicator once on arrival. It only ever shows when the
 *   region really overflows, so a screen that fits never flashes.
 */
@Composable
fun PickScaffold(
    step: PickStep,
    onBack: () -> Unit,
    headlineGap: Dp,
    above: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    flashIndicator: Boolean = true,
    indicatorGutter: Dp = 0.dp,
    regionBottomInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Back pops to the previous step -- the chevron, the gesture and the hardware key are one act.
    BackHandler(enabled = true, onBack = onBack)

    Box(modifier.fillMaxSize().background(Cream)) {
        // `<Atmosphere variant="form"/>`, the detail group's: 0.13 / 0.11, no peach wash. The only
        // absolutely positioned element on these screens, and nothing in the column depends on it.
        WelcomeBackdrop(
            peachWash = false,
            placement = OrbPlacement.Atmosphere,
            orangeAlpha = 0.13f,
            violetAlpha = 0.11f,
        )

        // The platform's own status bar and gesture bar, never the reference's mocks: safeDrawing
        // anchors the column to both.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AppHeader(title = DetailsCopy.SECTION, leading = HeaderLeading.Back, onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xs),
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val top = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FlexWithFloor(
                            available = top,
                            floor = ANSWER_REGION_FLOOR,
                            fixed = {
                                Column {
                                    StepProgress(steps = SHARE_STEPS_TOTAL, current = step.stepIndex)
                                    Spacer(Modifier.height(22.dp))
                                    WashHeadline(
                                        parts = PickCopy.headline(step),
                                        fontSize = 30.sp,
                                        lineHeight = (30f * 1.12f).sp,
                                        letterSpacing = (-0.015).em,
                                        balance = true,
                                        modifier = Modifier.testTag(PICK_HEADLINE_TAG),
                                    )
                                    Spacer(Modifier.height(headlineGap))
                                    Box(Modifier.testTag(PICK_ABOVE_TAG)) { above() }
                                }
                            },
                        ) {
                            AnswerRegion(scrollState, flashIndicator, indicatorGutter, regionBottomInset, content)
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().testTag(PICK_FOOTER_TAG)) { footer() }
            }
        }
    }
}

/**
 * The answer region: `flex: 1; min-height: 0; overflow-y: auto`, and the platform's scroll
 * indicator on its trailing edge.
 */
@Composable
private fun AnswerRegion(
    scrollState: ScrollState,
    flashIndicator: Boolean,
    gutter: Dp,
    bottomInset: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // `margin-right: -4`: the region is laid out [gutter] wider than the column and placed at
            // the column's start, so it overhangs into the right gutter by exactly that much. The
            // content takes the overhang back as padding, so the chips wrap at the column's width.
            .layout { measurable, constraints ->
                val extra = gutter.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minWidth + extra,
                        maxWidth = constraints.maxWidth + extra,
                    ),
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
            }
            .testTag(PICK_REGION_TAG)
            .scrollIndicator(scrollState, flashOnArrival = flashIndicator)
            .verticalScroll(scrollState),
    ) {
        Column(Modifier.fillMaxWidth().padding(end = gutter, bottom = bottomInset)) { content() }
    }
}

/**
 * `Skip for now` on the left, `Continue` on the right, and the failed-save toast riding above.
 *
 * CONTINUE IS NEVER DISABLED, on all three screens: with nothing picked it does exactly what Skip
 * does. It stops answering while a save is in flight (the view model ignores the press) and never
 * greys out.
 *
 * THE TOAST IS FOR A FAILED SAVE ONLY. These screens refuse nothing -- "no toast, no shake" -- but a
 * save can still fail, and the user must be told and kept on the step with their picks. The sentence
 * is the PROPOSED one the whole flow uses for that ([DetailsCopy.SAVE_FAILED_PROPOSED]).
 *
 * @param divided Interests' `border-top: 1px --liq-border-soft` with 12 above the row. Life and
 *   Future have no rule.
 */
@Composable
fun PickFooter(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    saveFailed: Boolean,
    divided: Boolean,
) {
    // The sentence never changes, but the slot is the detail footer's: the toast fades, and a
    // remembered message keeps the fade from swapping text mid-way if one ever does.
    val message = remember { DetailsCopy.SAVE_FAILED_PROPOSED }
    Box(Modifier.fillMaxWidth()) {
        RefusalToast(visible = saveFailed, icon = null, message = message, lift = Spacing.lg)
        Column(Modifier.fillMaxWidth()) {
            if (divided) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
                Spacer(Modifier.height(Spacing.xl))
            }
            // Side by side when the two fit, stacked when they do not (SkipContinueRow).
            SkipContinueRow(
                skipLabel = DetailsCopy.SKIP,
                ctaLabel = DetailsCopy.CTA,
                onSkip = onSkip,
                onContinue = onContinue,
                ctaModifier = Modifier.testTag(PICK_CTA_TAG),
            )
        }
    }
}
