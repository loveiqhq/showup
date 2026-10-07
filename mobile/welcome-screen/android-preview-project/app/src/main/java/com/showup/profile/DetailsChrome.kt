/*
 * DetailsChrome.kt
 * ShowUp · the shell every "Share some details" step renders through (SHOWUP-167), the footer, and
 * the two answer lists the steps after it consume
 *
 * Profile 14: "This screen builds the group's shell. Every 'Share some details' step renders through
 * one scaffold -- header, progress bar, headline, sub copy, answer region, visibility band, footer.
 * Height is the first consumer, so the shell is built here and steps 2-9 reuse it. A second copy of
 * it is a bug." Its numbers are `DetailsScaffold` in `screen-height-reference.jsx`.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE COLUMN, AND WHY ONLY ONE THING IN IT MOVES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *     AppHeader                52, "Share some details", back chevron, trailing slot empty
 *     content                  flex 1, padding 4 / 24 / 0
 *       StepProgress           steps = SHARE_STEPS_TOTAL, margin-bottom 26
 *       headline               Lora 700 / 30 / 1.12 / -0.015em, margin-bottom 8
 *       sub                    Manrope 500 / 14.5 / 1.45, max-width 320
 *       answer region          margin-top 12 + padding-top 10, flex 1, THE ONLY SCROLLING REGION
 *       visibility band        margin-top 6
 *       footer                 margin-top 26, margin-bottom 22
 *
 * The header, the bar, the headline, the band and the footer never move -- not between a screen's
 * states, not when the list scrolls, not when the toast shows. Every device difference lands in the
 * answer region, which ends in the screen's one flexible spacer and scrolls only when its content
 * really does not fit (orientation, dating language and education at 375 x 667; religion and
 * politics on more). Rows are never shortened to avoid a scroll.
 *
 * THE 10 IS PADDING INSIDE THE SCROLL, NOT MARGIN OUTSIDE IT. Height's floating label rides 8 above
 * its field, and the region's overflow clipped it until the kit split the 22 into `margin-top 12 +
 * padding-top 10` on 5 October 2026. Same 22, no Y change, and the label survives.
 *
 * THE HEADLINE IS 30 HERE, CARRIED BY THE SCAFFOLD -- "one headline size per screen group", decided 5
 * October 2026. A screen passes the words, never the size.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.CheckRow
import com.showup.designsystem.Cream
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.OptionRow
import com.showup.designsystem.ProfileVisibility
import com.showup.designsystem.SkipLink
import com.showup.designsystem.Spacing
import com.showup.designsystem.StepProgress
import com.showup.designsystem.scrollIndicator
import com.showup.tutorial.NextButton
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop

/** Fit-harness anchors. Tags rather than text, so the harness measures the box and not a label. */
internal const val DETAILS_CTA_TAG = "details-cta"
internal const val DETAILS_BAND_TAG = "details-band"
internal const val DETAILS_REGION_TAG = "details-region"
internal const val DETAILS_HEADLINE_TAG = "details-headline"

/**
 * Every string the group draws, verbatim from the seven tickets. `audit/verify-profile.py` quotes
 * each one back against the ticket text.
 *
 * TYPOGRAPHIC APOSTROPHES in the three `What’s` headlines: "the headline uses a typographic
 * apostrophe if the platform font renders one" -- Lora does. Spaced em dash in height's note.
 */
internal object DetailsCopy {
    const val SECTION = "Share some details"
    const val CTA = "Continue"
    const val SKIP = "Skip for now"

    /**
     * PROPOSED -- NOT APPROVED. No detail ticket says what a failed save shows. This is the
     * sentence the prompts screen already uses for the same failure, so the flow says one thing
     * for it everywhere; it travels in the group's own toast, which every step already has.
     */
    const val SAVE_FAILED_PROPOSED = PromptsCopy.SAVE_FAILED_PROPOSED

    const val HEIGHT_LABEL = "Enter height in cm"
    const val HEIGHT_PLACEHOLDER = "e.g. 175"
    const val HEIGHT_NOTE = "Be honest — it helps us find the right matches."

    const val SUB_MATCHES = "Used to find the right matches."
    const val SUB_SELECT_ALL = "Select all that apply."
    const val SUB_SELECT_ONE = "Select one."

    /** The headline, as runs: text and whether it is the one italic em with the orange wash. */
    fun headline(step: DetailStep): List<Pair<String, Boolean>> = when (step) {
        DetailStep.Height -> listOf("How " to false, "tall" to true, " are you?" to false)
        DetailStep.Gender -> listOf("Which gender describes " to false, "you" to true, " best?" to false)
        DetailStep.Orientation ->
            listOf("What’s your sexual " to false, "orientation" to true, "?" to false)
        DetailStep.DatingLanguage ->
            listOf("What’s your preferred dating " to false, "language" to true, "?" to false)
        DetailStep.Education ->
            listOf("What’s your highest level of " to false, "education" to true, "?" to false)
        DetailStep.Religion ->
            listOf("What are your " to false, "religious" to true, " beliefs?" to false)
        DetailStep.Politics ->
            listOf("What are your " to false, "political" to true, " beliefs?" to false)
    }

    /** The headline as one plain string -- the list's accessible name ("labelled by the headline"). */
    fun headlineText(step: DetailStep): String = headline(step).joinToString("") { it.first }

    fun sub(step: DetailStep): String = when (step) {
        DetailStep.Height, DetailStep.Gender, DetailStep.Orientation -> SUB_MATCHES
        DetailStep.DatingLanguage -> SUB_SELECT_ALL
        DetailStep.Education, DetailStep.Religion, DetailStep.Politics -> SUB_SELECT_ONE
    }

    /**
     * The refusal toast. One string per refusing step; the four steps whose empty Continue is a
     * skip have none, because they never refuse.
     */
    fun refusal(step: DetailStep): String? = when (step) {
        DetailStep.Height -> "Enter a height between 120 and 230 cm to continue"
        DetailStep.Gender -> "Pick a gender to continue"
        DetailStep.Orientation -> "Pick an orientation to continue"
        else -> null
    }
}

/**
 * The shell.
 *
 * @param flashIndicator flash the scroll indicator once on arrival -- orientation, dating
 *   language, religion and politics. Education says not to: at 375 x 667 it scrolls by about 6 and
 *   "nothing meaningful is hidden".
 */
@Composable
fun DetailsScaffold(
    step: DetailStep,
    onBack: () -> Unit,
    hidden: Boolean,
    onToggleVisibility: () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    flashIndicator: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Back pops to the previous step -- the chevron, the gesture and the hardware key are the same
    // act. Unlike 12 and 13, this group has somewhere to go back to.
    BackHandler(enabled = true, onBack = onBack)

    Box(modifier.fillMaxSize().background(Cream)) {
        // `<Atmosphere variant="form"/>` -- 0.13 / 0.11, no peach wash. Decorative, full-bleed behind
        // the status bar and the gesture bar, and nothing in the column depends on it.
        WelcomeBackdrop(
            peachWash = false,
            placement = OrbPlacement.Atmosphere,
            orangeAlpha = 0.13f,
            violetAlpha = 0.11f,
        )

        // safeDrawing takes the status bar, the gesture bar AND the keyboard: on height the column
        // ends at the keypad's top edge, so the band and the footer ride above it and the answer
        // region absorbs the difference. Nothing here knows a keyboard height.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AppHeader(title = DetailsCopy.SECTION, leading = HeaderLeading.Back, onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xs),
            ) {
                // THE TOP PART -- bar, headline, sub and answer region -- with an 80 floor under the
                // region, and the band and footer pinned below it.
                //
                // On every phone at the default type and with no keyboard, the region is far taller
                // than 80, the floor never engages, and this is the layout in the header comment
                // exactly: nothing above the band moves and only the region scrolls.
                //
                // WHERE IT WOULD NOT BE: height's numeric keypad on a small Android leaves about 330
                // of a 640 screen, and the bar, the headline and the sub are fixed. Without the floor
                // the region was squeezed to NOTHING -- the CTA still showed and the field the user
                // was typing into did not (DetailsFitTest measured 0 on a 360 x 640). With it, the
                // top part scrolls as one so the focused field can be brought into view, while the
                // band and Continue stay pinned above the keypad. "The keyboard must not hide any
                // required control" -- the field is one.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val top = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FlexWithFloor(
                            available = top,
                            floor = ANSWER_REGION_FLOOR,
                            fixed = {
                                Column {
                                    StepProgress(steps = SHARE_STEPS_TOTAL, current = step.stepIndex)
                                    Spacer(Modifier.height(26.dp))

                                    WashHeadline(
                                        parts = DetailsCopy.headline(step),
                                        fontSize = 30.sp,
                                        lineHeight = (30f * 1.12f).sp,
                                        letterSpacing = (-0.015).em,
                                        balance = true,
                                        modifier = Modifier.testTag(DETAILS_HEADLINE_TAG),
                                    )
                                    Spacer(Modifier.height(Spacing.md))
                                    Text(
                                        DetailsCopy.sub(step),
                                        modifier = Modifier.widthIn(max = 320.dp),
                                        color = Neutral, fontFamily = Manrope,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.5.sp, lineHeight = (14.5f * 1.45f).sp,
                                    )
                                    // The answer region's `margin-top: 12`, outside its scroll.
                                    Spacer(Modifier.height(Spacing.xl))
                                }
                            },
                        ) {
                            // THE ANSWER REGION: padding-top 10 inside the scroll, and the screen's
                            // one flexible element.
                            BoxWithConstraints(
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(DETAILS_REGION_TAG),
                            ) {
                                val viewport = maxHeight
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .scrollIndicator(scrollState, flashOnArrival = flashIndicator)
                                        .verticalScroll(scrollState),
                                ) {
                                    // Floored at the viewport, so with room to spare the ONE spacer
                                    // below takes the slack and nothing scrolls; without it, the
                                    // column overflows and the region scrolls instead of anything
                                    // shrinking.
                                    Column(
                                        Modifier
                                            .heightIn(min = viewport)
                                            .padding(top = ANSWER_REGION_INSET),
                                    ) {
                                        content()
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                ProfileVisibility(
                    hidden = hidden,
                    onToggle = onToggleVisibility,
                    modifier = Modifier.padding(top = Spacing.sm).testTag(DETAILS_BAND_TAG),
                )

                Box(Modifier.padding(top = 26.dp, bottom = 22.dp)) { footer() }
            }
        }
    }
}

/**
 * A fixed part above a flexible one: the flexible part gets whatever [available] leaves, but never
 * less than [floor] -- and this reports its TRUE height, so inside a scroll the excess scrolls.
 *
 * NOT `weight` WITH `heightIn(min)`. A weighted child is handed a fixed height, and `heightIn` can
 * only coerce inside the constraints it is given, so the floor would never engage; `requiredHeightIn`
 * would overflow the slot symmetrically over whatever sits above it. Measuring the two parts here is
 * the one way to say "the rest, but at least this much" and have the column grow when it must.
 */
@Composable
private fun FlexWithFloor(
    available: androidx.compose.ui.unit.Dp,
    floor: androidx.compose.ui.unit.Dp,
    fixed: @Composable () -> Unit,
    flexible: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(contents = listOf(fixed, flexible)) { (fixedParts, flexParts), constraints ->
        val width = constraints.maxWidth
        val placedFixed = fixedParts.map { it.measure(androidx.compose.ui.unit.Constraints(maxWidth = width)) }
        val fixedHeight = placedFixed.sumOf { it.height }
        val flexHeight = maxOf(available.roundToPx() - fixedHeight, floor.roundToPx())
        val placedFlex = flexParts.map {
            it.measure(androidx.compose.ui.unit.Constraints.fixed(width, flexHeight))
        }
        layout(width, fixedHeight + flexHeight) {
            var y = 0
            placedFixed.forEach { it.place(0, y); y += it.height }
            placedFlex.forEach { it.place(0, y) }
        }
    }
}

/** The answer region's inner top padding, shared with the lists that scroll a row into view. */
internal val ANSWER_REGION_INSET = Spacing.lg

/**
 * The least the answer region is ever given: room for height's 64 field under its 10 inset, or
 * about one and a half rows of a list. Below it, the top part scrolls instead -- see the scaffold.
 * Not a design number: the reference never draws a region this small, because it never draws a
 * keyboard on a 640 phone.
 */
internal val ANSWER_REGION_FLOOR = 80.dp

/**
 * The footer row: `Skip for now` on the left when the step can be skipped, `Continue` on the right,
 * and the group's toast riding above it.
 *
 * ONE FOOTER WITH AN OPTIONAL SKIP, not two -- Profile 15: "`FooterContinue` is the footer without
 * its SkipLink. Build it as the Profile 14 footer with an optional skip." With no skip the row is
 * `flex-end` and its left side stays EMPTY; nothing is drawn to balance it.
 *
 * CONTINUE IS NEVER DISABLED. A refused press is a real press: the view model reports it and the
 * toast says why.
 */
@Composable
fun DetailsFooter(
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
    toastVisible: Boolean,
    toastMessage: String,
) {
    // THE SENTENCE OUTLIVES THE TOAST. The fade-out still draws it, and once the toast is gone the
    // caller's message for "no toast" is a different sentence -- after a failed save on height, the
    // refusal line would swap in mid-fade. A plain remembered slot, not state: it only caches the
    // last sentence that was actually up.
    val lastShown = remember { arrayOf(toastMessage) }
    if (toastVisible) lastShown[0] = toastMessage
    Box(Modifier.fillMaxWidth()) {
        RefusalToast(visible = toastVisible, icon = null, message = lastShown[0], lift = 10.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (onSkip != null) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSkip != null) SkipLink(onClick = onSkip, label = DetailsCopy.SKIP)
            NextButton(
                label = DetailsCopy.CTA,
                onClick = onContinue,
                modifier = Modifier.testTag(DETAILS_CTA_TAG),
                arrowSize = 22.dp,
            )
        }
    }
}

/**
 * Scrolls the row at [selectedIndex] fully into view, once per pre-fill and WITHOUT animation --
 * "if the saved row is below the fold on a short screen, the list opens scrolled so that row is
 * fully visible". Keyed on [prefill], which the view model bumps only when a step is filled from
 * the server: a TAP never scrolls the list ("nothing else on the screen changes, and the list does
 * not scroll").
 */
@Composable
private fun ScrollSavedRowIntoView(
    scrollState: ScrollState,
    prefill: Int,
    rowBottom: Float?,
) {
    // ONCE PER PRE-FILL, NOT PER COMPOSITION. An Activity rebuild runs this effect again with the
    // same pre-fill, and the user may have scrolled since; the scroll position itself is restored.
    var handled by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(prefill, rowBottom) {
        if (prefill == handled) return@LaunchedEffect
        val bottom = rowBottom ?: return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        if (bottom > scrollState.value + viewport) {
            scrollState.scrollTo((bottom - viewport).toInt())
        }
        handled = prefill
    }
}

/**
 * A single-select list of [OptionRow]s: a radiogroup labelled by the headline, each row announcing
 * its state and its position ("Woman, 1 of 4").
 */
@Composable
fun DetailsOptionList(
    step: DetailStep,
    selected: String?,
    onTap: (String) -> Unit,
    scrollState: ScrollState,
    prefill: Int,
) {
    val density = LocalDensity.current
    var savedRowBottom by remember(prefill) { mutableStateOf<Float?>(null) }
    // Captured at the pre-fill, not tracked: the row that was SAVED is the one brought into view.
    val target = remember(prefill) { selected }
    val headline = DetailsCopy.headlineText(step)
    Column(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics {
                contentDescription = headline
                collectionInfo = CollectionInfo(step.options.size, 1)
            },
    ) {
        // A PRE-FILL IS NOT A TAP: "pre-fill the saved selection ... without animation". New rows
        // per pre-fill start at their value, so only a tap runs the 180 ms change.
        key(prefill) {
            step.options.forEachIndexed { i, option ->
                OptionRow(
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { onTap(option.value) },
                    index = i,
                    count = step.options.size,
                    modifier = if (option.value == target) {
                        Modifier.onGloballyPositioned { coords ->
                            val inset = with(density) { ANSWER_REGION_INSET.toPx() }
                            val top = coords.parentLayoutCoordinates
                                ?.localPositionOf(coords, androidx.compose.ui.geometry.Offset.Zero)?.y ?: 0f
                            savedRowBottom = inset + top + coords.size.height
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
    ScrollSavedRowIntoView(scrollState, prefill, savedRowBottom)
}

/**
 * The multi-select list of [CheckRow]s: a group labelled by the headline. On a pre-fill it opens
 * SCROLLED TO THE TOP, not to a saved row -- Profile 17 says so in as many words.
 */
@Composable
fun DetailsCheckList(
    step: DetailStep,
    ticked: Set<String>,
    onTap: (String) -> Unit,
    scrollState: ScrollState,
    prefill: Int,
) {
    // Once per pre-fill, not per composition -- see ScrollSavedRowIntoView.
    var handled by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(prefill) {
        if (prefill > 0 && prefill != handled) {
            scrollState.scrollTo(0)
            handled = prefill
        }
    }
    val headline = DetailsCopy.headlineText(step)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = headline
                collectionInfo = CollectionInfo(step.options.size, 1)
            },
    ) {
        key(prefill) {
            step.options.forEachIndexed { i, option ->
                CheckRow(
                    label = option.label,
                    checked = option.value in ticked,
                    onClick = { onTap(option.value) },
                    index = i,
                    count = step.options.size,
                )
            }
        }
    }
}
