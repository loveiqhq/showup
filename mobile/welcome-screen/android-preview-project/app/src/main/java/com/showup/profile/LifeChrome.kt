/*
 * LifeChrome.kt
 * ShowUp · what Life builds and Future consumes (Profiles 22 and 23, SHOWUP-175 / 176): LifePill,
 * CategoryHeader, and the three-question column both screens draw
 *
 * Profile 22: "Build `LifePill`, `CategoryHeader` and `toggleLifePick` from this file." Profile 23's
 * first criterion: "The screen consumes LifePill, CategoryHeader and toggleLifePick from Profile 22
 * -- no copy, no fork." So both screens render [LifeScreen] below with their own step, and nothing
 * here knows which one it is drawing. The toggle is in `ProfileLife.kt`, as a plain function.
 *
 * THE PILL DRAWS 34 AND ANSWERS AT 44, overflowing its slot by 5 above and below
 * (`OverflowTapTarget`, the chip's pattern). Rows are 8 apart, so the touch areas of two stacked rows share a 2-wide band in the
 * middle of the gap -- the lower pill takes a tap there, being drawn later. Growing the pill instead
 * would open every row by 10, which the design spends its whole "fits at 390" budget on.
 */
package com.showup.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.CssEase
import com.showup.designsystem.Elevated
import com.showup.designsystem.EyebrowBg
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Neutral
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.PillGlow
import com.showup.designsystem.Purple
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Spacing
import com.showup.designsystem.pillShadow
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The pill's drawn height -- `height: 34`. A floor, so a larger system font grows it. */
private val PILL_HEIGHT = 34.dp

/** `padding: 0 14px`, plus the 1px border CSS draws inside the same border-box. */
private val PILL_PAD = 14.dp + 1.dp

/** Fit-harness anchor for one pill, by question and value -- `find_out_together` is in three. */
internal fun lifePillTag(question: LifeQuestion, value: String) = "life-pill-${question.questionId}-$value"

/** Fit-harness anchor for one question's section. */
internal fun lifeSectionTag(question: LifeQuestion) = "life-section-${question.questionId}"

/**
 * One pill. A checkbox: several can be picked in a question.
 *
 * Picked: violet fill and border, white 700, `0 2 8` violet glow. Resting: white, 1px
 * `--liq-border`, 500. `transition: background 180ms cubic-bezier(.22,1,.36,1), color 180ms,
 * border-color 180ms` -- the fill on the brand curve, the ink and the border on CSS's `ease`, and the
 * glow NOT AT ALL: `box-shadow` is not in the list, so it arrives with the tap, as on the web.
 */
@Composable
fun LifePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val fill by animateColorAsState(if (selected) Purple else Elevated, tween(ms, easing = ShowUpEasing), label = "pillFill")
    val ink by animateColorAsState(if (selected) Elevated else Fg, tween(ms, easing = CssEase), label = "pillInk")
    val stroke by animateColorAsState(if (selected) Purple else Border, tween(ms, easing = CssEase), label = "pillStroke")
    val shape = RoundedCornerShape(percent = 50)

    OverflowTapTarget(
        modifier = modifier,
        touch = Modifier
            .toggleable(
                value = selected,
                interactionSource = remember { MutableInteractionSource() },
                // NO RIPPLE: the fill is the feedback, and a ripple over it would be undesigned.
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .heightIn(min = PILL_HEIGHT)
                .pillShadow(
                    fill = fill,
                    shadow = if (selected) PillGlow else Color.Transparent,
                    offsetY = 2.dp,
                    blur = 8.dp,
                )
                .border(1.dp, stroke, shape)
                // `sm` above and below keeps a label that grew past 34 off the stroke; at the default
                // font it is inside the 34 and changes nothing.
                .padding(horizontal = PILL_PAD, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = ink,
                fontFamily = Manrope,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.5.sp,
                // NO `lineHeight`. The reference's `line-height: 1` only centres one line in the 34,
                // and one line centres identically at the font's own height (the half-leading is
                // symmetric). CSS never wraps a pill, so for the second line the largest font can
                // force, the font's own pitch is used -- on both platforms -- where 1.0 em is cramped.
                textAlign = TextAlign.Center,
                // One line on every phone at the default font; a second only for a label wider than
                // the column at the largest font -- see the interest chip.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A question's header: the icon in a 28 lavender medallion, then the label in Manrope 700 15.
 *
 * The medallion is lavender-400 at 16% -- [EyebrowBg], the same tint the lavender eyebrow pill and
 * status badge fill with -- and the icon is primary violet at 16, stroke 1.9. DECORATIVE: the icon is
 * hidden from TalkBack, and the label is read as the question group's name, not again here.
 */
@Composable
fun CategoryHeader(icon: BrandIcon, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(EyebrowBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, 16.dp, tint = Purple, strokeWidth = 1.9f)
        }
        Text(
            label,
            color = Fg,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.005.em,
        )
    }
}

/**
 * The questions of one step, `gap: 22`, each a group labelled by its category: the header, 12, then
 * its pills wrapping 8 apart. The row count is whatever the widths make it -- never fixed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifeQuestions(
    step: PickStep,
    draft: LifeDraft,
    onTap: (LifeQuestion, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        step.questions.forEach { question ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag(lifeSectionTag(question))
                    .semantics { contentDescription = question.label },
            ) {
                CategoryHeader(question.icon, question.label)
                Spacer(Modifier.height(Spacing.xl))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    question.options.forEach { option ->
                        LifePill(
                            label = option.label,
                            selected = option.value in draft.picked(question),
                            onClick = { onTap(question, option.value) },
                            modifier = Modifier.testTag(lifePillTag(question, option.value)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Life or Future: the column, the sub, the three questions, the footer. The two screens are this
 * with their own step -- "same column, pill and toggle as Life; nothing else differs".
 *
 * Headline margin 8, then the sub (Manrope 500 14.5 / 1.45, max-width 320), then the region at 22.
 * The footer has no rule: 16 above it, 22 below.
 */
@Composable
fun LifeScreen(
    step: PickStep,
    state: PickUiState,
    onTap: (LifeQuestion, String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    require(step != PickStep.Interests) { "Interests is not a life step" }
    PickScaffold(
        step = step,
        onBack = onBack,
        headlineGap = Spacing.md,
        scrollState = scrollState,
        // Flashes only where the region overflows -- Life at 375, Future at 390 and 375 -- because
        // the indicator itself never shows over content that fits.
        flashIndicator = true,
        above = {
            Column {
                Text(
                    PickCopy.LIFE_SUB,
                    modifier = Modifier.widthIn(max = 320.dp),
                    color = Neutral,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp,
                    lineHeight = (14.5f * 1.45f).sp,
                )
                // The region's `margin-top: 22`, outside its scroll.
                Spacer(Modifier.height(22.dp))
            }
        },
        footer = {
            Box(Modifier.padding(top = Spacing.xxl, bottom = 22.dp)) {
                PickFooter(onContinue = onContinue, onSkip = onSkip, saveFailed = state.saveFailed, divided = false)
            }
        },
    ) {
        // A PRE-FILL IS NOT A TAP: new pills per pre-fill start at their value, so only a tap runs
        // the 180 ms change ("pre-fill the saved answers without animation").
        key(state.prefill) {
            LifeQuestions(step = step, draft = state.life, onTap = onTap)
        }
    }
}
