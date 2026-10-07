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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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
    /**
     * Paints the value without changing it.
     *
     * Added for the date field, which needs slashes on screen and digits in state. Defaulted to
     * None, so every other call site is untouched and unmeasured.
     *
     * The alternative -- reformatting the value on each keystroke -- is what the phone field's
     * own comment warns against, and what the date field was doing: rewriting the string moves
     * the caret, so the next digit lands somewhere the user did not put it. Typing 03221995
     * produced 03/21/9592, reproducibly, at one digit per second.
     */
    visualTransformation: VisualTransformation = VisualTransformation.None,
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
    val ms = if (motion.enabled) Motion.FAST else 0
    val animatedBorder by animateColorAsState(borderColor, tween(ms, easing = ShowUpEasing), label = "border")
    val animatedHalo by animateColorAsState(haloColor, tween(ms, easing = ShowUpEasing), label = "halo")
    val animatedLabelColor by animateColorAsState(
        labelColor, tween(ms, easing = ShowUpEasing), label = "labelColor",
    )
    val labelTop by animateDpAsState(
        if (active) (-8).dp else 22.dp, tween(ms, easing = ShowUpEasing), label = "labelTop",
    )
    val labelLeft by animateDpAsState(
        if (active) 14.dp else 18.dp, tween(ms, easing = ShowUpEasing), label = "labelLeft",
    )
    val labelSize by animateFloatAsState(
        if (active) 12f else 17f, tween(ms, easing = ShowUpEasing), label = "labelSize",
    )

    // THE CARET LANDS AT THE END OF A VALUE THAT ARRIVES. The String overload of BasicTextField
    // starts its caret at 0, so a field pre-filled while focused -- height on a resume or a back --
    // put the caret before "175": backspace deleted nothing and typing went in front. Only a value
    // that arrives in an EMPTY field moves the caret; every other edit keeps the stock behaviour.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // The last text handed UP, reset whenever the parent's value changes -- what the stock String
    // overload compares against. Comparing with `value` instead dropped an edit when two arrived in
    // one frame and the second returned the text to it (type then delete, an autocorrect revert).
    val lastSent = remember(value) { Held(value) }
    // "Arrives in an empty field" is judged on what was SHOWN -- the parent's previous value -- as
    // well as the buffer, which can still hold a keystroke the parent cleared.
    val previous = remember { Held(value) }
    val shown = when {
        fieldValue.text == value -> fieldValue
        fieldValue.text.isEmpty() || previous.value.isEmpty() -> TextFieldValue(value, TextRange(value.length))
        else -> fieldValue.copy(text = value)
    }
    previous.value = value

    val shape = RoundedCornerShape(Radius.control)

    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                // The halo is a 4px ring OUTSIDE the stroke -- the reference's `0 0 0 4px` shadow. It
                // is drawn as a stroke centred 2 outside the box, so it neither covers the field nor
                // tints a translucent error background. Not Modifier.shadow: that is elevation, with
                // a light source and therefore a direction, and this ring is even on all four sides.
                .drawBehind {
                    val ring = 4.dp.toPx()
                    val radius = Radius.control.toPx() + ring / 2f
                    drawRoundRect(
                        color = animatedHalo,
                        topLeft = Offset(-ring / 2f, -ring / 2f),
                        size = Size(size.width + ring, size.height + ring),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = ring),
                    )
                }
                .clip(shape)
                .background(if (error) Danger.copy(alpha = 0.04f) else Elevated)
                .border(1.5.dp, animatedBorder, shape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BasicTextField(
                value = shown,
                onValueChange = { next ->
                    fieldValue = next
                    // Sent when it differs from what is shown OR from what was last sent: the first
                    // catches an edit the parent refused before (it would otherwise match the stale
                    // `lastSent` for ever), the second two edits in one frame.
                    if (next.text != value || next.text != lastSent.value) {
                        lastSent.value = next.text
                        onValueChange(next.text)
                    }
                },
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
                visualTransformation = visualTransformation,
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
                    CheckGlyph(size = 13.dp, color = SuccessFg, stroke = 3f)
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

/** A plain remembered slot: [FloatingField]'s last text sent up. */
private class Held(var value: String)
