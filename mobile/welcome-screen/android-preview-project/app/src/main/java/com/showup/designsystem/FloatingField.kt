package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The outlined input with a label that rides up and notches the border.
 *
 * WHY THIS IS NOT [InputField]
 *
 * [InputField] is the welcome flow's field: 56 tall, a placeholder, no label of its own, and its
 * whole reason for existing is that the control fills its box. This one is 64 tall, carries a
 * floating label that cuts the stroke, and has valid / error affordances the phone field never has.
 * They share a rounded outlined box and nothing else -- different heights, different label model,
 * different states. Folding them together would mean a variant flag on every one of those, which
 * is the "everything input" the design system has been careful to avoid.
 *
 * What they DO share is the rule the phone field was built to enforce: the text is measured to the
 * full height of the box, so the whole 64 takes a tap rather than the middle third.
 *
 * THE LABEL NOTCHES THE STROKE, IT DOES NOT BREAK IT
 *
 * The border is drawn unbroken and the label sits on top of it with a small patch of the screen
 * colour behind. In the error state the patch switches from white to [Cream], because the field's
 * own fill turns into a 4% danger wash and a white patch would then read as a hole punched in it.
 */
@Composable
fun FloatingField(
    value: String,
    onValueChange: (String) -> Unit,
    /** Rides the border. Also the accessibility name -- an unlabelled input cannot be announced. */
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    valid: Boolean = false,
    error: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    imeAction: ImeAction = ImeAction.Go,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // Lifted whenever focused OR non-empty. It only drops back to rest if the field is blurred
    // while empty -- which on these screens never happens, because they auto-focus on arrival.
    val active = focused || value.isNotEmpty()

    val borderColor = when {
        error -> Danger
        focused -> Purple
        valid -> Success.copy(alpha = 0.55f)
        else -> Border
    }
    val haloColor = when {
        error -> Danger.copy(alpha = 0.10f)
        focused -> Purple.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val labelColor = when {
        error -> DangerFg
        focused -> Purple
        valid -> SuccessFg
        else -> Faint
    }

    // 180ms cubic-bezier(.22,1,.36,1) on every affordance, per the spec sheet. The colour tweens
    // are what stop the valid -> error swap reading as a redraw.
    val motion = rememberMotion()
    val ms = if (motion.enabled) 180 else 0
    val animatedBorder by animateColorAsState(borderColor, tween(ms), label = "border")
    val animatedHalo by animateColorAsState(haloColor, tween(ms), label = "halo")
    val animatedLabelColor by animateColorAsState(labelColor, tween(ms), label = "labelColor")
    val labelTop by animateDpAsState(if (active) (-8).dp else 22.dp, tween(ms), label = "labelTop")
    val labelLeft by animateDpAsState(if (active) 14.dp else 18.dp, tween(ms), label = "labelLeft")
    val labelSize by animateFloatAsState(if (active) 12f else 17f, tween(ms), label = "labelSize")

    val shape = RoundedCornerShape(Radius.control)

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                // The halo is a 4px ring OUTSIDE the stroke. Drawn as a second border on a slightly
                // larger box rather than a shadow: Modifier.shadow is elevation, which has a light
                // source and therefore a direction, and this ring must be even on all four sides.
                .border(4.dp, animatedHalo, shape)
                .clip(shape)
                .background(if (error) Danger.copy(alpha = 0.04f) else Elevated)
                .border(1.5.dp, animatedBorder, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    // The same guarantee InputField exists to make: the text measures to the box,
                    // so the whole 64 responds rather than the middle third.
                    .fillMaxHeight()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = TextStyle(
                    color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Purple),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = capitalization,
                    imeAction = imeAction,
                    autoCorrect = keyboardType != KeyboardType.Email,
                ),
                // Go does exactly what Continue does, and inserts no newline.
                keyboardActions = KeyboardActions(onGo = { onSubmit() }, onDone = { onSubmit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        // The placeholder only shows once the label has lifted out of the way --
                        // otherwise the two sit on top of each other at rest.
                        if (value.isEmpty() && active) {
                            Text(
                                placeholder, color = Faint, fontFamily = Manrope,
                                fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )

            if (error) {
                // 13 and not the phone field's 14, which is what its own spec draws. The two are
                // the same circle at two glyph sizes; the difference is reported, not normalised.
                DangerGlyph(size = 22.dp, glyphSize = 13.dp)
            } else if (valid) {
                Box(
                    Modifier.size(22.dp).background(Success.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckGlyph(size = 13.dp, color = SuccessFg, strokeWidth = 3.dp)
                }
            }
        }

        // The label, riding the stroke. Its patch is the screen colour in the error state so it
        // still notches a box whose fill is no longer white.
        Text(
            label,
            modifier = Modifier
                .offset(x = labelLeft, y = labelTop)
                .then(
                    if (active) Modifier
                        .background(if (error) Cream else Elevated)
                        .padding(horizontal = Spacing.sm)
                    else Modifier
                ),
            color = animatedLabelColor,
            fontFamily = Manrope,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = labelSize.sp,
        )
    }
}
