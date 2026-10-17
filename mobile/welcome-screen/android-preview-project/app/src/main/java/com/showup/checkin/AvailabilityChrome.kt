/*
 * AvailabilityChrome.kt
 * ShowUp · Check-In 02's parts (SHOWUP-179): HowItWorks, AvailabilitySlot, AvailabilitySummary,
 * LegendPill, NoHoursTodayDialog
 *
 * Numbers from `screen-checkin-availability-reference.jsx`. `HowItWorks` is built here once and is
 * the explainer every later step would reuse; the dialog is the only absolutely-positioned layer in
 * the Check-In (scrim + centred card).
 */
package com.showup.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.showup.designsystem.shrinkToFit
import androidx.compose.ui.layout.Layout
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Border
import com.showup.designsystem.CoachLilacFill
import com.showup.designsystem.CoachLilacInk
import com.showup.designsystem.CssEase
import com.showup.designsystem.DialogScrim
import com.showup.designsystem.DialogShadow
import com.showup.designsystem.Elevated
import com.showup.designsystem.Faint
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Primary600
import com.showup.designsystem.Purple
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Spacing
import com.showup.designsystem.TierAvailableBorder
import com.showup.designsystem.TierAvailableFill
import com.showup.designsystem.TierAvailableInk
import com.showup.designsystem.boxShadow
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/** Fit-harness anchors. */
internal const val GRID_TAG = "check-in-grid"
internal const val SUMMARY_TAG = "check-in-summary"
internal const val LEGEND_TAG = "check-in-legend"
internal const val HOW_IT_WORKS_TAG = "check-in-how-it-works"
internal const val NO_HOURS_DIALOG_TAG = "check-in-no-hours"
internal fun slotTag(hour: Int) = "slot-$hour"

/** A slot's three looks, and the legend's: fill, ink, border. */
private data class TierLook(val fill: Color, val ink: Color, val border: Color)

private fun lookOf(tier: SlotTier?) = when (tier) {
    null -> TierLook(Elevated, Fg, Border)
    SlotTier.Preferred -> TierLook(Purple, Color.White, Purple)
    SlotTier.Available -> TierLook(TierAvailableFill, TierAvailableInk, TierAvailableBorder)
}

/**
 * `HowItWorks` -- the disclosure row and its panel. The row: `alert-circle` 15 + `How it works` (13 /
 * 700, primary) + a chevron that turns over in 200 ms; [action] sits at the trailing end (Clear all).
 * The panel opens inline, below, 10 down: lilac, padding 14 / 16, radius 14, 13 / 1.5, paragraphs
 * 8 apart, entering with `su-toast-in`. Collapsed on every entry (the view model resets it).
 */
@Composable
fun HowItWorks(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    val motion = rememberMotion()
    val turn by animateFloatAsState(
        if (open) 180f else 0f,
        tween(if (motion.enabled) Motion.REVEAL else 0, easing = CssEase),
        label = "howTurn",
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The disclosure: a button with its state spoken, answering at 44 without moving anything.
            OverflowTapTarget(
                touch = Modifier
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics {
                        contentDescription = CheckInCopy.HOW_IT_WORKS
                        stateDescription = if (open) "Expanded" else "Collapsed"
                    },
                modifier = Modifier.testTag(HOW_IT_WORKS_TAG),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(BrandIcon.AlertCircle, 15.dp, tint = Purple, strokeWidth = 2f)
                    Text(
                        CheckInCopy.HOW_IT_WORKS,
                        color = Purple,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Box(Modifier.rotate(turn)) { Icon(BrandIcon.ChevronDown, 15.dp, tint = Purple, strokeWidth = 2.4f) }
                }
            }
            action()
        }
        if (open) {
            Spacer(Modifier.height(Spacing.lg))
            Reveal {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CoachLilacFill)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CheckInCopy.HOW_IT_WORKS_PANEL.forEach { runs ->
                        Text(
                            buildAnnotatedString {
                                runs.forEach { (t, bold) ->
                                    if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t)
                                }
                            },
                            color = CoachLilacInk,
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            lineHeight = (13f * 1.5f).sp,
                        )
                    }
                }
            }
        }
    }
}

/** `Clear all` -- `x` 14 + 13 / 700 in `--liq-fg-subtle`, gap 5. Shown only with a selection. */
@Composable
fun ClearAll(onClick: () -> Unit) {
    OverflowTapTarget(touch = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(BrandIcon.Close, 14.dp, tint = FgSubtle, strokeWidth = 2.2f)
            Text(CheckInCopy.CLEAR_ALL, color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** `su-toast-in` for an element that appears: 200 ms, up 6, on the brand curve. */
@Composable
internal fun Reveal(content: @Composable () -> Unit) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.REVEAL else 0
    val rise = with(LocalDensity.current) { 6.dp.roundToPx() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(ms, easing = ShowUpEasing)) + slideInVertically(tween(ms, easing = ShowUpEasing)) { rise },
    ) { content() }
}

/**
 * `AvailabilitySlot` -- one hour: 36 tall, a full pill, 13 / 600, colours per tier, `all 180ms`.
 * DRAWN 36, ANSWERS AT 44 ([OverflowTapTarget]); rows are 5 apart, so two rows' touch areas share a
 * 3-wide band in the gap, where the lower slot wins -- the chips' and pills' compromise.
 * Announced as `5pm, not selected` / `preferred` / `available` (story 02, accessibility).
 */
@Composable
fun AvailabilitySlot(hour: Int, tier: SlotTier?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val look = lookOf(tier)
    val fill by animateColorAsState(look.fill, tween(ms, easing = CssEase), label = "slotFill")
    val ink by animateColorAsState(look.ink, tween(ms, easing = CssEase), label = "slotInk")
    val stroke by animateColorAsState(look.border, tween(ms, easing = CssEase), label = "slotStroke")
    val label = formatHour(hour)
    OverflowTapTarget(
        touch = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                stateDescription = when (tier) {
                    null -> "not selected"
                    SlotTier.Preferred -> "preferred"
                    SlotTier.Available -> "available"
                }
            },
        modifier = modifier.testTag(slotTag(hour)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(fill)
                .border(1.dp, stroke, RoundedCornerShape(percent = 50))
                .padding(horizontal = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            // Shrinks to the pill rather than truncating: at 2x on a 320 phone `noon` is wider than its
            // 64-wide slot, and `no...` is no label at all.
            Text(
                label,
                Modifier.shrinkToFit(minScale = 0.5f),
                color = ink,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The grid: four columns, 5 apart both ways. Every slot of the day, in order. */
@Composable
fun AvailabilityGridRows(grid: AvailabilityGrid, picked: Map<Int, SlotTier>, onTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        grid.hours.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { h ->
                    AvailabilitySlot(h, picked[h], onClick = { onTap(h) }, modifier = Modifier.weight(1f))
                }
                // A short last row keeps its cells the width of the full rows above.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * `AvailabilitySummary` -- what the taps add up to: `Your availability` beside the ranges, lilac once
 * anything is picked, white before. At the default type it is ONE line that never wraps -- too long,
 * the ranges shrink to 0.78 at most, and past four runs they already read `+N more`. Only the largest
 * system fonts get past that floor, and then the ranges wrap rather than lose their end.
 */
@Composable
fun AvailabilitySummary(grid: AvailabilityGrid, picked: Map<Int, SlotTier>, modifier: Modifier = Modifier) {
    val summary = availabilitySummary(grid.hours, picked)
    val any = summary != null
    val shape = RoundedCornerShape(12.dp)
    val labelStyle = TextStyle(
        color = if (any) TierAvailableInk else FgSubtle,
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
    )
    val style = TextStyle(
        fontFamily = Manrope,
        fontWeight = if (any) FontWeight.ExtraBold else FontWeight.SemiBold,
        fontSize = 13.5.sp,
        color = if (any) Primary600 else Faint,
    )
    val text = summary ?: CheckInCopy.SUMMARY_EMPTY
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (any) TierAvailableFill else Elevated)
            .border(1.dp, if (any) TierAvailableBorder else BorderSoft, shape)
            // Border-box: the 1 border is outside the 8 / 14 padding, so the inset is the sum.
            .padding(horizontal = 14.dp + 1.dp, vertical = Spacing.md + 1.dp)
            .testTag(SUMMARY_TAG),
    ) {
        // THREE LAYOUTS, chosen by what fits, and at the default type always the first: the ranges on
        // the label's line, shrunk to 0.78 at most -- "one line, never wraps". Past that floor, which
        // only the largest system fonts reach, the ranges WRAP rather than lose their end ("never an
        // incomplete truth"); and when even the label needs the whole line, they go under it.
        val measurer = rememberTextMeasurer()
        val gap = Spacing.md
        val width = constraints.maxWidth
        val label = remember(labelStyle) { measurer.measure(CheckInCopy.SUMMARY_LABEL, labelStyle, maxLines = 1, softWrap = false).size.width }
        val need = remember(text, style) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width }
        val gapPx = with(LocalDensity.current) { gap.roundToPx() }
        val room = width - label - gapPx
        when {
            need * SUMMARY_MIN_SCALE <= room -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Text(CheckInCopy.SUMMARY_LABEL, Modifier.alignByBaseline(), style = labelStyle, maxLines = 1, softWrap = false)
                Text(
                    text,
                    Modifier
                        .weight(1f)
                        .alignByBaseline()
                        .shrinkToFit(SUMMARY_MIN_SCALE, origin = 0f),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            room >= with(LocalDensity.current) { SUMMARY_MIN_VALUE.roundToPx() } -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Text(CheckInCopy.SUMMARY_LABEL, Modifier.alignByBaseline(), style = labelStyle, maxLines = 1, softWrap = false)
                Text(text, Modifier.weight(1f).alignByBaseline(), style = style)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(CheckInCopy.SUMMARY_LABEL, Modifier.shrinkToFit(SUMMARY_MIN_SCALE, origin = 0f), style = labelStyle, maxLines = 1, softWrap = false)
                Text(text, style = style)
            }
        }
    }
}

/** "Auto-shrinks to 0.78" -- the summary's floor before it wraps instead. */
private const val SUMMARY_MIN_SCALE = 0.78f

/** The least room beside the label the ranges wrap in before they move under it. */
private val SUMMARY_MIN_VALUE = 80.dp

/** `LegendPill` -- padding 5 / 10, a full pill, 11.5 / 700, in a tier's colours; `clear` is idle's. */
@Composable
fun LegendPill(label: String, tier: SlotTier?) {
    val look = lookOf(tier)
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(look.fill)
            .border(1.dp, look.border, RoundedCornerShape(percent = 50))
            // Border-box: the 1 border is outside the 5 / 10 padding, so the inset is the sum.
            .padding(horizontal = Spacing.lg + 1.dp, vertical = 5.dp + 1.dp),
        color = look.ink,
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The legend, pinned above the CTA: a hairline, then `Tap an hour to cycle through:` (11 / 700,
 * muted) over the three pills 7 apart. mt 6, pt 9, mb 8; its two lines 5 apart.
 */
@Composable
fun AvailabilityLegend(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.md)
            .testTag(LEGEND_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
        Spacer(Modifier.height(9.dp))
        // `white-space: nowrap` in the reference -- one line at the default type, centred, and allowed to
        // wrap at the largest system fonts rather than run off the screen.
        Text(
            CheckInCopy.LEGEND_LEAD,
            color = Muted,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = (11f * 1.1f).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        LegendRow {
            LegendPill(CheckInCopy.LEGEND_PREFERRED, SlotTier.Preferred)
            LegendPill(CheckInCopy.LEGEND_AVAILABLE, SlotTier.Available)
            LegendPill(CheckInCopy.LEGEND_CLEAR, null)
        }
    }
}

/**
 * The three legend pills, 7 apart, centred on ONE row -- as the reference lays them out, which does
 * not fit every phone: Chrome measures the row at 338 (114 + 117 + 93 and two gaps), so on a 375
 * phone (327 to give) it spills about 6 into each side gutter, and on a 320 it would run off the
 * screen. So: one centred row while it fits the column OR spills no more than [LEGEND_SPILL] into each
 * gutter -- exactly the reference at 375 -- and centred rows, 5 apart, past that (the 320 phone, the
 * largest system fonts), never a truncated pill.
 */
@Composable
private fun LegendRow(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 7.dp.roundToPx()
        val rowGap = 5.dp.roundToPx()
        val pills = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val total = pills.sumOf { it.width } + gap * (pills.size - 1)
        val rows: List<List<androidx.compose.ui.layout.Placeable>> =
            if (total <= width + 2 * LEGEND_SPILL.roundToPx()) {
                listOf(pills)
            } else {
                val out = mutableListOf(mutableListOf<androidx.compose.ui.layout.Placeable>())
                var used = 0
                pills.forEach { p ->
                    val next = if (out.last().isEmpty()) p.width else used + gap + p.width
                    if (out.last().isNotEmpty() && next > width) {
                        out += mutableListOf(p)
                        used = p.width
                    } else {
                        out.last() += p
                        used = next
                    }
                }
                out
            }
        val heights = rows.map { row -> row.maxOf { it.height } }
        layout(width, heights.sum() + rowGap * (rows.size - 1)) {
            var y = 0
            rows.forEachIndexed { i, row ->
                val rowWidth = row.sumOf { it.width } + gap * (row.size - 1)
                var x = (width - rowWidth) / 2
                row.forEach { p ->
                    p.place(x, y + (heights[i] - p.height) / 2)
                    x += p.width + gap
                }
                y += heights[i] + rowGap
            }
        }
    }
}

/** How far the legend's one row may spill into each side gutter (24) before it wraps: 8 stays clear. */
private val LEGEND_SPILL = 16.dp

/**
 * `NoHoursTodayDialog` -- modal, the Check-In's only absolutely-positioned layer: a scrim
 * (rgba(29,17,41,.44), fading in 200 ms) and a centred card (max 320, radius 26, padding 26 / 24 /
 * 20, popping in 260 ms on the brand curve). No ✕. The scrim, `Cancel, pick hours today` and
 * Android back all close it and stay on Today. The kit blurs what is behind the scrim 3px: so does
 * this, on Android 12 and later (`blurBehindDialog`, RenderEffect); on Android 11 it is the tint alone.
 *
 * MODAL FOR TALKBACK TOO: the screen behind clears its semantics while this is up (the caller), so
 * focus cannot leave the card; the card is a dialog pane named as the reference names it.
 */
@Composable
fun NoHoursTodayDialog(onConfirm: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val motion = rememberMotion()
    // ONE STATE PER TRANSITION. The scrim and the card were driven by a single shared state, and
    // two transitions seeking one `MutableTransitionState` leave each other stuck: with animations
    // off they jump to the end and look right, but on a real phone the card and the scrim never
    // appeared at all -- only the blur behind them. Found on the emulator, invisible to Robolectric.
    val scrimIn = remember { MutableTransitionState(false).apply { targetState = true } }
    val cardIn = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize().testTag(NO_HOURS_DIALOG_TAG), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visibleState = scrimIn,
            enter = fadeIn(tween(if (motion.enabled) Motion.REVEAL else 0, easing = CssEase)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DialogScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = CheckInCopy.NO_HOURS_CANCEL,
                        onClick = onDismiss,
                    ),
            )
        }
        val pop = if (motion.enabled) Motion.POP else 0
        val rise = with(LocalDensity.current) { 10.dp.roundToPx() }
        // The card never runs off the screen: at the largest system font on a small phone it is taller
        // than the frame, and its two buttons were crushed below the 44 floor. Capped 24 short of each
        // edge, its content scrolls inside it instead.
        BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        val cardMax = maxHeight
        AnimatedVisibility(
            visibleState = cardIn,
            enter = fadeIn(tween(pop, easing = ShowUpEasing)) +
                slideInVertically(tween(pop, easing = ShowUpEasing)) { rise } +
                scaleIn(tween(pop, easing = ShowUpEasing), initialScale = 0.97f),
            modifier = Modifier.padding(horizontal = 26.dp),
        ) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .heightIn(max = cardMax)
                    .boxShadow(Elevated, 26.dp, DialogShadow, offsetY = 24.dp, blur = 60.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 20.dp)
                    .semantics {
                        dialog()
                        paneTitle = CheckInCopy.NO_HOURS_DIALOG_LABEL
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LilacWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BrandIcon.Calendar, 24.dp, tint = Purple, strokeWidth = 1.9f)
                }
                Spacer(Modifier.height(Spacing.xxl))
                // A plain `<em>`: italic in the title's own 700, no wash.
                WashHeadline(
                    parts = CheckInCopy.NO_HOURS_TITLE,
                    fontSize = 23.sp,
                    lineHeight = (23f * 1.15f).sp,
                    cssLineBox = true,
                    letterSpacing = (-0.01).em,
                    textAlign = TextAlign.Center,
                    wash = false,
                )
                val body = remember { AnnotatedString(CheckInCopy.NO_HOURS_BODY) }
                val bodyStyle = TextStyle(
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, lineHeight = (14f * 1.5f).sp, textAlign = TextAlign.Center,
                )
                Text(
                    body,
                    Modifier.padding(top = Spacing.lg).prettyWrap(body, bodyStyle, centered = true),
                    style = bodyStyle,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    label = CheckInCopy.NO_HOURS_CONFIRM,
                    onClick = onConfirm,
                    variant = PrimaryButtonVariant.Sunset,
                    trailing = { CheckInArrow() },
                )
                Spacer(Modifier.height(Spacing.lg))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(role = Role.Button, onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        CheckInCopy.NO_HOURS_CANCEL,
                        color = Purple,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        }
    }
}
