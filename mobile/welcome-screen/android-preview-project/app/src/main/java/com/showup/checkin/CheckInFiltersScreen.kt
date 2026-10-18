/*
 * CheckInFiltersScreen.kt
 * ShowUp · Check-In 05 (SHOWUP-182): Filters -- gender (mandatory), age range, distance
 *
 * `FiltersStep` from `screen-checkin-filters-reference.jsx`, inside the step shell. Three filters in
 * this order, and NO SEXUALITY FILTER: matching is two-way on gender, orientation stays on the
 * profile. Gender chips are Profile 22's [LifePill] as built; the sliders are [RangeSlider], which
 * Search's filter sheet will consume rather than rebuild.
 *
 * The filter list is the only flexible element and the only thing that scrolls ([CheckInFlexBody]).
 * The sheet says it scrolls at 375 x 667; measured, it FITS there -- the kit's frame draws a 54 status
 * bar and a home indicator an iPhone SE does not have -- with the distance row in view
 * (`CheckInFitTest` prints it). Wherever it would not fit, it scrolls inside itself.
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Purple
import com.showup.designsystem.RangeSlider
import com.showup.designsystem.Spacing
import com.showup.profile.LifePill

/** Fit-harness anchors. */
internal fun genderTag(id: String) = "gender-$id"
internal const val AGE_SLIDER_TAG = "age-slider"
internal const val DISTANCE_SLIDER_TAG = "distance-slider"
internal const val DISTANCE_ROW_TAG = "distance-row"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInFiltersScreen(
    filters: CheckInFilters,
    modifier: Modifier = Modifier,
    toast: String? = null,
    toastTick: Int = 0,
    listScroll: ScrollState = rememberScrollState(),
    onGender: (String) -> Unit = {},
    onAge: (thumb: Int, value: Int) -> Unit = { _, _ -> },
    onDistance: (Int) -> Unit = {},
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
                CheckInFilterRow(CheckInCopy.FILTER_GENDER) {
                    FlowRow(
                        Modifier.semantics { contentDescription = CheckInCopy.FILTER_GENDER },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        CheckInGender.entries.forEach { gender ->
                            LifePill(
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
                        tag = AGE_SLIDER_TAG,
                    )
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
                }
            }
        }
    }
}

/** `CheckinFilterRow` -- the label (14 / 600) and its value (13 / 600, violet), baseline-aligned, 10 above the control. */
@Composable
fun CheckInFilterRow(label: String, modifier: Modifier = Modifier, hint: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.alignByBaseline(), color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (hint != null) {
                Text(hint, Modifier.alignByBaseline(), color = Purple, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        content()
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
