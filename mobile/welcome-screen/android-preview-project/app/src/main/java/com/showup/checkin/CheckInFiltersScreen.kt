/*
 * CheckInFiltersScreen.kt
 * ShowUp · Check-In 05 (SHOWUP-182): Filters -- gender (mandatory), age range, distance
 *          Check-In 05b (SHOWUP-189): gender checkboxes, and a Dealbreaker switch on Age and Distance
 *
 * `FiltersStep` from `screen-checkin-filters-reference.jsx`, inside the step shell. Three filters in
 * this order, and NO SEXUALITY FILTER: matching is two-way on gender, orientation stays on the
 * profile. The sliders are [RangeSlider], which Search's filter sheet consumes rather than rebuilds --
 * this IS that sheet: one screen, one store.
 *
 * 05b, decided 26 Oct 2026: the gender options are [GenderCheckPill] -- Profile 22's pill values plus a
 * checkbox square, "the platform cue for pick several" -- under a muted `Pick all that apply`; and a
 * [DealbreakerRow] sits under the Age and Distance sliders, Profile 10's [ConsentSwitch] consumed. The
 * switches ship hidden behind the server's remote flag `filters_dealbreakers` ([dealbreakers]):
 * off, they are not drawn at all and the layout is Check-In 05's plus the gender change.
 *
 * The filter list is the only flexible element and the only thing that scrolls ([CheckInFlexBody]).
 * The two Dealbreaker rows add about 110; it still fits at 390 x 844, and at 375 x 667 it scrolls
 * inside itself. The sliders, chips, switches and CTA never shrink to avoid that.
 */
package com.showup.checkin

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.ConsentSwitch
import com.showup.designsystem.CssEase
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.PillGlow
import com.showup.designsystem.Purple
import com.showup.designsystem.RangeSlider
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Spacing
import com.showup.designsystem.pillShadow
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** Fit-harness anchors. */
internal fun genderTag(id: String) = "gender-$id"
internal const val AGE_SLIDER_TAG = "age-slider"
internal const val DISTANCE_SLIDER_TAG = "distance-slider"
internal const val DISTANCE_ROW_TAG = "distance-row"
/** The age slider's own 44 box -- the Dealbreaker row sits 12 under it. */
internal const val AGE_SLIDER_BOX_TAG = "age-slider-box"
internal const val AGE_DEALBREAKER_TAG = "age-dealbreaker"
internal const val DISTANCE_DEALBREAKER_TAG = "distance-dealbreaker"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInFiltersScreen(
    filters: CheckInFilters,
    modifier: Modifier = Modifier,
    toast: String? = null,
    toastTick: Int = 0,
    listScroll: ScrollState = rememberScrollState(),
    /** The remote flag `filters_dealbreakers` (05b): off, no [DealbreakerRow] is drawn. */
    dealbreakers: Boolean = false,
    onGender: (String) -> Unit = {},
    onAge: (thumb: Int, value: Int) -> Unit = { _, _ -> },
    onDistance: (Int) -> Unit = {},
    onAgeDealbreaker: (Boolean) -> Unit = {},
    onDistanceDealbreaker: (Boolean) -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    CheckInStepShell(
        step = CheckInStep.Filters,
        onBack = onBack,
        onClose = onClose,
        modifier = modifier,
        footer = {
            CheckInFooter(
                label = CheckInCopy.CONTINUE,
                onClick = onContinue,
                dimmed = filters.genders.isEmpty(),
                toast = toast,
                toastTick = toastTick,
            )
        },
    ) {
        CheckInFlexBody(
            Modifier.fillMaxSize(),
            header = {
                Spacer(Modifier.height(Spacing.md))
                CheckInStepHeadline(CheckInCopy.FILTERS_HEADLINE)
                CoachNote(CheckInCopy.FILTERS_NOTE, warm = true, modifier = Modifier.padding(top = 14.dp))
            },
        ) {

            // THE FILTER LIST -- the only flexible element. Rows 22 apart.
            Column(
                Modifier
                    .padding(top = 18.dp)
                    .fillMaxSize()
                    .verticalScroll(listScroll)
                    .padding(bottom = Spacing.xl)
                    .testTag(CHECK_IN_SCROLL_TAG),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                // ① the hint in the existing slot, muted: the value hints below keep their violet.
                CheckInFilterRow(CheckInCopy.FILTER_GENDER, hint = CheckInCopy.FILTER_GENDER_HINT, hintMuted = true) {
                    FlowRow(
                        // A group of checkboxes, named for what it asks.
                        Modifier.semantics { contentDescription = CheckInCopy.FILTER_GENDER_GROUP },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CheckInGender.entries.forEach { gender ->
                            GenderCheckPill(
                                label = gender.label,
                                selected = gender.id in filters.genders,
                                onClick = { onGender(gender.id) },
                                modifier = Modifier.testTag(genderTag(gender.id)),
                            )
                        }
                    }
                }
                CheckInFilterRow(CheckInCopy.FILTER_AGE, hint = ageLabel(filters.minAge, filters.maxAge)) {
                    RangeSlider(
                        values = listOf(filters.minAge, filters.maxAge),
                        min = AGE_MIN,
                        max = AGE_MAX,
                        labels = listOf(CheckInCopy.SLIDER_MIN_AGE, CheckInCopy.SLIDER_MAX_AGE),
                        onChange = onAge,
                        modifier = Modifier.testTag(AGE_SLIDER_BOX_TAG),
                        tag = AGE_SLIDER_TAG,
                    )
                    if (dealbreakers) {
                        DealbreakerRow(
                            on = filters.ageDealbreaker,
                            onLine = CheckInCopy.DEALBREAKER_ON_AGE,
                            label = CheckInCopy.DEALBREAKER_AGE_LABEL,
                            onChange = onAgeDealbreaker,
                            modifier = Modifier.testTag(AGE_DEALBREAKER_TAG),
                        )
                    }
                }
                CheckInFilterRow(
                    CheckInCopy.FILTER_DISTANCE,
                    hint = distanceLabel(filters.distanceKm),
                    modifier = Modifier.testTag(DISTANCE_ROW_TAG),
                ) {
                    RangeSlider(
                        values = listOf(filters.distanceKm),
                        min = DISTANCE_MIN,
                        max = DISTANCE_MAX,
                        labels = listOf(CheckInCopy.SLIDER_DISTANCE),
                        onChange = { _, v -> onDistance(v) },
                        tag = DISTANCE_SLIDER_TAG,
                    )
                    if (dealbreakers) {
                        DealbreakerRow(
                            on = filters.distanceDealbreaker,
                            onLine = CheckInCopy.DEALBREAKER_ON_DISTANCE,
                            label = CheckInCopy.DEALBREAKER_DISTANCE_LABEL,
                            onChange = onDistanceDealbreaker,
                            modifier = Modifier.testTag(DISTANCE_DEALBREAKER_TAG),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `CheckinFilterRow` -- the label (14 / 600) and its hint, baseline-aligned, 10 above the control.
 * The hint is the value in violet (13 / 600), or with [hintMuted] an instruction in the muted ink
 * (12 / 500) -- 05b's `Pick all that apply`.
 */
@Composable
fun CheckInFilterRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    hintMuted: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.alignByBaseline(), color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (hint != null) {
                Text(
                    hint,
                    Modifier
                        .alignByBaseline()
                        // An instruction is read with its group's name; a value is read with its slider.
                        .then(if (hintMuted) Modifier.clearAndSetSemantics {} else Modifier),
                    color = if (hintMuted) Muted else Purple,
                    fontFamily = Manrope,
                    fontWeight = if (hintMuted) FontWeight.Medium else FontWeight.SemiBold,
                    fontSize = if (hintMuted) 12.sp else 13.sp,
                )
            }
        }
        content()
    }
}

/** The pill: Profile 22's `LifePill` values -- 34 tall, 13.5, pill radius, 1 stroke. */
private val PILL_HEIGHT = 34.dp

/** The square's side and corner, and the gap to the label: `16 · r 5 · gap 8`. */
private val CHECK_SIDE = 16.dp
private val CHECK_GAP = Spacing.md

/** `rgba(29,17,41,.32)`, the empty square's 1.5 stroke -- the ink at the reference's alpha. */
private val CheckStroke = Fg.copy(alpha = 0.32f)

/**
 * `GenderCheckPill` (05b ② ③): LifePill's values plus a 16 checkbox square -- empty when off, a
 * violet tick on white when on. A CHECKBOX to TalkBack, its state announced from the checkbox; the
 * square is decorative. Padding `0 14 0 10` around the 1 stroke.
 *
 * Not LifePill with a slot: LifePill stays unchanged on Vibe and Profile 22 (out of scope here), and
 * the square changes its left padding. The colours, timing and press treatment are LifePill's.
 */
@Composable
internal fun GenderCheckPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val fill by animateColorAsState(if (selected) Purple else Elevated, tween(ms, easing = ShowUpEasing), label = "genderFill")
    val ink by animateColorAsState(if (selected) Elevated else Fg, tween(ms, easing = CssEase), label = "genderInk")
    val stroke by animateColorAsState(if (selected) Purple else Border, tween(ms, easing = CssEase), label = "genderStroke")
    val squareFill by animateColorAsState(if (selected) Elevated else Color.Transparent, tween(ms, easing = CssEase), label = "squareFill")
    val squareStroke by animateColorAsState(if (selected) Color.Transparent else CheckStroke, tween(ms, easing = CssEase), label = "squareStroke")
    val shape = RoundedCornerShape(percent = 50)
    val square = RoundedCornerShape(5.dp)

    OverflowTapTarget(
        modifier = modifier,
        touch = Modifier
            .toggleable(
                value = selected,
                interactionSource = remember { MutableInteractionSource() },
                // NO RIPPLE: the fill is the feedback, as on LifePill.
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .semantics { contentDescription = label },
    ) {
        Row(
            Modifier
                .clearAndSetSemantics {}
                .heightIn(min = PILL_HEIGHT)
                .pillShadow(fill = fill, shadow = if (selected) PillGlow else Color.Transparent, offsetY = 2.dp, blur = 8.dp)
                .border(1.dp, stroke, shape)
                .padding(start = 10.dp + 1.dp, end = 14.dp + 1.dp, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CHECK_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(CHECK_SIDE)
                    .background(squareFill, square)
                    .border(1.5.dp, squareStroke, square),
                contentAlignment = Alignment.Center,
            ) {
                // The tick fades with the fill; laid out always, so the pill never changes width.
                Box(Modifier.alpha(if (selected) 1f else 0f)) {
                    Icon(BrandIcon.Check, 12.dp, tint = Purple, strokeWidth = 3f)
                }
            }
            Text(
                label,
                color = ink,
                fontFamily = Manrope,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `DealbreakerRow` (05b ④ ⑤ ⑥), under the Age and Distance sliders: margin-top 12, the text column
 * `flex: 1` and the switch `flex: none`, gap 12. `Dealbreaker` (600 / 13) over its line (12 / 1.4,
 * muted, mt 2): the off line until the switch is on, then the strict one. Profile 10's
 * [ConsentSwitch], consumed -- a second switch component is a bug.
 *
 * READ AS ONE THING, IN THE TICKET'S ORDER: the switch -- "Age range is a dealbreaker", its state --
 * then the line. The visible `Dealbreaker` is the switch's name already, so it is not read twice.
 */
@Composable
internal fun DealbreakerRow(on: Boolean, onLine: String, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        // The 12 above it is the row's margin, outside whatever the caller tags.
        Modifier
            .padding(top = 12.dp)
            .then(modifier)
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                CheckInCopy.DEALBREAKER,
                modifier = Modifier.clearAndSetSemantics {},
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            )
            Text(
                if (on) onLine else CheckInCopy.DEALBREAKER_OFF,
                modifier = Modifier.padding(top = 2.dp).semantics { traversalIndex = 1f },
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Normal,
                fontSize = 12.sp, lineHeight = (12f * 1.4f).sp,
            )
        }
        ConsentSwitch(on = on, label = label, onChange = onChange, modifier = Modifier.semantics { traversalIndex = 0f })
    }
}

@Preview(name = "Check-In 05 · A pre-filled · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewFiltersA() = CheckInFiltersScreen(filters = CheckInFilters(genders = setOf("woman")))

@Preview(name = "Check-In 05 · B no gender + toast · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewFiltersB() = CheckInFiltersScreen(
    filters = CheckInFilters(genders = emptySet()),
    toast = CheckInCopy.FILTERS_TOAST,
)

@Preview(name = "Check-In 05 · C narrowed · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewFiltersC() = CheckInFiltersScreen(
    filters = CheckInFilters(genders = setOf("woman", "non_binary"), minAge = 25, maxAge = 35, distanceKm = 15),
)

// ── 05b, with the remote flag on: the sheet's three frames ───────────────────────────────────────

@Preview(name = "Check-In 05b · A both off · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewFilters05bA() = CheckInFiltersScreen(
    filters = CheckInFilters(genders = setOf("woman")), dealbreakers = true,
)

@Preview(name = "Check-In 05b · B two genders, age strict · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewFilters05bB() = CheckInFiltersScreen(
    filters = CheckInFilters(genders = setOf("woman", "non_binary"), minAge = 28, maxAge = 35, ageDealbreaker = true),
    dealbreakers = true,
)

@Preview(name = "Check-In 05b · C both strict · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewFilters05bC() = CheckInFiltersScreen(
    filters = CheckInFilters(
        genders = setOf("woman", "man", "non_binary"), minAge = 25, maxAge = 40, distanceKm = 15,
        ageDealbreaker = true, distanceDealbreaker = true,
    ),
    dealbreakers = true,
)
