/*
 * CheckInMotivationScreen.kt
 * ShowUp · Check-In 03 (SHOWUP-180): Motivation -- one of five, single-select
 *
 * `IntentStep` from `screen-checkin-motivation-reference.jsx`, inside the step shell (02) with
 * content props only. The option list is the only flexible element and the only thing that scrolls
 * ([CheckInFlexBody], with its floor at the largest fonts); at 390 x 844 the five rows fit, at
 * 375 x 667 the list scrolls inside itself (`CheckInFitTest`).
 *
 * SINGLE-SELECT, radio semantics: a tap selects, a tap on another row replaces it, a tap on the
 * selected row does nothing. Selected is the violet border and a filled icon tile -- no checkmark.
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.CssEase
import com.showup.designsystem.Elevated
import com.showup.designsystem.EyebrowBg
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.rememberMotion
import com.showup.welcome.Icon

/** The row's 1.5 border -- also part of its inset, see [MotivationRow]. */
private val ROW_BORDER = 1.5.dp

/** Fit-harness anchor for one motivation row. */
internal fun motivationTag(id: String) = "motivation-$id"

@Composable
fun CheckInMotivationScreen(
    selected: String?,
    modifier: Modifier = Modifier,
    toast: String? = null,
    toastTick: Int = 0,
    listScroll: ScrollState = rememberScrollState(),
    onPick: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    CheckInStepShell(
        step = CheckInStep.Motivation,
        onBack = onBack,
        onClose = onClose,
        modifier = modifier,
        footer = {
            CheckInFooter(
                label = CheckInCopy.CONTINUE,
                onClick = onContinue,
                dimmed = selected == null,
                toast = toast,
                toastTick = toastTick,
            )
        },
    ) {
        CheckInFlexBody(
            Modifier.fillMaxSize(),
            header = {
                Spacer(Modifier.height(Spacing.md))
                CheckInStepHeadline(CheckInCopy.MOTIVATION_HEADLINE)
                CoachNote(CheckInCopy.MOTIVATION_NOTE, warm = true, modifier = Modifier.padding(top = 14.dp))
            },
        ) {

            // THE LIST -- the only flexible element. A radio group named "Motivation to date".
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxSize()
                    .verticalScroll(listScroll)
                    .padding(bottom = Spacing.xl)
                    .selectableGroup()
                    .semantics { contentDescription = CheckInCopy.MOTIVATION_GROUP }
                    .testTag(CHECK_IN_SCROLL_TAG),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                MotivationOption.entries.forEach { option ->
                    MotivationRow(option, option.id == selected, onClick = { onPick(option.id) })
                }
            }
        }
    }
}

/**
 * One row: padding 14 / 16, radius 16, white, a 1.5 border (violet when selected), a 36 icon tile
 * (radius 12; violet with a white glyph when selected, lavender .16 with a violet glyph otherwise),
 * and the title (15 / 700) over the subline (13, subtle). `all 180ms`.
 */
@Composable
private fun MotivationRow(option: MotivationOption, selected: Boolean, onClick: () -> Unit) {
    val motion = rememberMotion()
    val ease = tween<Color>(if (motion.enabled) Motion.FAST else 0, easing = CssEase)
    val stroke by animateColorAsState(if (selected) Purple else Border, ease, label = "rowStroke")
    val tile by animateColorAsState(if (selected) Purple else EyebrowBg, ease, label = "rowTile")
    val glyph by animateColorAsState(if (selected) Color.White else Purple, ease, label = "rowGlyph")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Elevated)
            .border(ROW_BORDER, stroke, shape)
            // Radio semantics: the row is the radio, read with its title, subline and state. A tap
            // on the selected row changes nothing (the view model keeps it selected).
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // BORDER-BOX, as the kit lays out every element: the 1.5 border sits OUTSIDE the
            // 14 / 16 padding, and Compose's `border` draws over the padding instead -- so the
            // inset is their sum. Without it each row was 3pt shorter than the sheet's and the
            // list 14pt shorter over five rows.
            .padding(horizontal = 16.dp + ROW_BORDER, vertical = 14.dp + ROW_BORDER)
            .testTag(motivationTag(option.id)),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(option.icon, 18.dp, tint = glyph, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(option.title, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(option.subline, color = FgSubtle, fontFamily = Manrope, fontSize = 13.sp)
        }
    }
}

@Preview(name = "Check-In 03 · A empty · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewMotivationEmpty() = CheckInMotivationScreen(selected = null)

@Preview(name = "Check-In 03 · B toast · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewMotivationToast() = CheckInMotivationScreen(selected = null, toast = CheckInCopy.MOTIVATION_TOAST)

@Preview(name = "Check-In 03 · C picked · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewMotivationPicked() = CheckInMotivationScreen(selected = "long_term")
