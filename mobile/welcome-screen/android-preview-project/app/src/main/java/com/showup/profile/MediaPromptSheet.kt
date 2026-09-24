/*
 * MediaPromptSheet.kt
 * ShowUp · the eleven prompts, for either medium (SHOWUP-161)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE COMPONENT WITH A `kind` PROP. TWO COPIES IS A BUG.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The ticket says exactly that, and it is worth reading twice because the two sheets differ in
 * three strings and nothing else: the sub line, the commit label, and the `type` on every event
 * they fire. Video and voice open THE SAME eleven, in THE SAME order.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ROWS ARE RADIO-SELECT, NOT TAP-TO-LAUNCH
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Tapping a row never opens the camera. The user can read all eleven and change their mind before
 * anything happens, and only the commit CTA leaves the sheet. That is also why
 * `media_prompt_selected` fires on the CTA rather than on a row: a tap is a considered look, not a
 * choice, and counting looks as choices would make the per-prompt numbers meaningless.
 *
 * NOTHING IS PRESELECTED when this opens from an empty card -- not even the previewed prompt. The
 * card previews it; the list does not pick it. Opening from a filled card's `Retake` DOES
 * preselect the answered prompt, so keeping it is one tap and changing it is two.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE DISABLED CTA IS THE SECOND DELIBERATE EXCEPTION TO FLOW RULE 2c
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * With nothing selected it is the quiet ghost variant reading `Choose a prompt to continue`, and it
 * is disabled -- the same shape as `Verify code` on screen 03 and for the same reason: there is
 * nothing to validate and the label already names the requirement.
 *
 * DO NOT GENERALISE THIS TO THE SCREEN'S OWN CONTINUE, which is never disabled in any state.
 */
package com.showup.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.SheetCloseButton
import com.showup.designsystem.SheetGrabber
import com.showup.designsystem.SheetMaxHeight
import com.showup.designsystem.SheetShape
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/**
 * A dashed outline, which Compose has no border style for.
 *
 * Used by the own-idea row alone. It is the one thing that makes that row read as an escape hatch
 * rather than a twelfth prompt, and the ticket requires it to stay distinct.
 */
private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: androidx.compose.foundation.shape.CornerBasedShape,
): Modifier = this.drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val dash = 4.dp.toPx()
    val radius = 16.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(dash, dash),
            ),
        ),
    )
}

@Composable
fun MediaPromptSheet(
    kind: MediaKind,
    selectedId: String?,
    onPick: (String) -> Unit,
    onCommit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = SheetMaxHeight)
            .clip(SheetShape)
            .background(Cream),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            Box(Modifier.align(Alignment.TopCenter)) { SheetGrabber() }
            SheetCloseButton(
                onClose,
                Modifier.align(Alignment.TopEnd).padding(end = Spacing.md, top = Spacing.md),
            )
        }

        Column(
            Modifier.padding(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                top = 14.dp,
                bottom = Spacing.xl,
            ),
        ) {
            WashHeadline(
                parts = listOf(
                    MediaCopy.SHEET_HEADLINE_BEFORE to false,
                    MediaCopy.SHEET_HEADLINE_EM to true,
                    MediaCopy.SHEET_HEADLINE_AFTER to false,
                ),
                // 36 of clearance on the trailing edge so the headline never runs under the X.
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm, end = 36.dp),
                fontSize = 25.sp,
                lineHeight = (25f * 1.12f).sp,
                letterSpacing = (-0.018).em,
            )
            Text(
                MediaCopy.sheetSub(kind),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp, lineHeight = (13.5f * 1.45f).sp,
            )
        }

        Column(
            Modifier
                .weight(1f, fill = false)
                // THE SCROLL MASK, from the reference's `maskImage`: opaque to 94% then fading
                // out, so a list that continues past the fold says so instead of being guillotined
                // by the commit row's top border.
                //
                // `DstIn` needs its own layer, and `CompositingStrategy.Offscreen` is what gives it
                // one -- without it the blend applies to whatever the sheet is drawn over.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.Black,
                            0.94f to Color.Black,
                            1.00f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            MediaPrompts.ALL.forEach { prompt ->
                PromptRow(
                    prompt = prompt,
                    selected = prompt.id == selectedId,
                    onClick = { onPick(prompt.id) },
                )
            }
            Box(Modifier.size(Spacing.md))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Cream)
                .padding(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = Spacing.xl,
                    bottom = 14.dp,
                ),
        ) {
            val picked = MediaPrompts.byId(selectedId)
            PrimaryButton(
                label = if (picked == null) MediaCopy.COMMIT_EMPTY else MediaCopy.commit(kind),
                onClick = onCommit,
                variant = if (picked == null) {
                    PrimaryButtonVariant.Ghost
                } else {
                    PrimaryButtonVariant.Sunset
                },
                enabled = picked != null,
                // Three lines, for this label alone. See PrimaryButton.labelMaxLines: at 2.0x on a
                // 320 phone `Choose a prompt to continue` does not fit two.
                labelMaxLines = 3,
                leading = if (picked == null) {
                    null
                } else {
                    {
                        // The 8px white dot the reference puts ahead of the committed label -- a
                        // small "you have chosen something" marker, not an icon.
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    }
                },
            )
        }
    }
}

/**
 * One row.
 *
 * THE OWN-IDEA ROW IS ALWAYS LAST AND ALWAYS VISUALLY DISTINCT -- dashed border, sans-serif, its
 * own sub line. It never ranks and is never removed: a list of eleven is a help, not a cage.
 */
@Composable
private fun PromptRow(
    prompt: MediaPrompt,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = when {
        selected -> Purple
        prompt.isOwn -> Border
        else -> BorderSoft
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ComponentSizes.minTapTarget)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(LilacWash)
                } else {
                    Modifier.background(Color.White)
                },
            )
            .then(
                if (prompt.isOwn && !selected) {
                    Modifier.dashedBorder(1.5.dp, border, shape)
                } else {
                    Modifier.border(1.5.dp, border, shape)
                },
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(Purple)
                    } else {
                        Modifier.border(1.5.dp, Fg.copy(alpha = 0.20f), CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(BrandIcon.Check, 12.dp, tint = Color.White, strokeWidth = 3.5f)
        }
        Column(Modifier.weight(1f)) {
            Text(
                prompt.display,
                color = Fg,
                fontFamily = if (prompt.isOwn) Manrope else Lora,
                fontWeight = if (prompt.isOwn) FontWeight.Bold else FontWeight.Medium,
                fontStyle = if (prompt.isOwn) FontStyle.Normal else FontStyle.Italic,
                fontSize = if (prompt.isOwn) 14.sp else 15.sp,
                lineHeight = ((if (prompt.isOwn) 14f else 15f) * 1.3f).sp,
            )
            if (prompt.isOwn) {
                Text(
                    MediaCopy.OWN_IDEA_SUB,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 12.sp, lineHeight = (12f * 1.35f).sp,
                )
            }
        }
    }
}
