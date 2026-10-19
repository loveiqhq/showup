package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The chrome every field-shaped control wears: one control tall, [Radius.control] corners, an
 * elevated fill and a 1.5dp outline.
 *
 * Exposed separately from [InputField] because two controls wear it and only one of them is an
 * input. The country pill beside the number field is a BUTTON with identical chrome, and the
 * alternative to sharing it is what was there before: the same five modifiers written twice, a few
 * lines apart, already differing in their horizontal padding.
 *
 * [outline] is a parameter rather than a boolean because the states are not symmetrical -- the
 * pill has one outline, the input has two, and a `error: Boolean` here would have to grow a case
 * every time a control gains a state.
 *
 * **1.5dp and [Subtle], not [Border].** The outline is the only thing identifying the field, and
 * Border at 12% measures 1.28:1 against a 3:1 requirement. Audit finding 7.
 */
fun Modifier.fieldChrome(
    outline: Color = Subtle,
    height: Dp = ComponentSizes.controlHeight,
    horizontalPadding: Dp = 18.dp,
    /**
     * Taken here rather than chained on by the caller, because modifier ORDER decides the tap area
     * and only one order is right.
     *
     * `.clickable().padding(14)` makes the padding part of the control -- the whole 56dp box
     * responds. `.padding(14).clickable()` looks identical and responds only inside the padding,
     * losing 28dp of width. A caller writing `.fieldChrome(...).clickable { }` would get the
     * second one, silently, which is the same class of defect as the 23dp field this file exists
     * to prevent. So the hook is a parameter and the order is not the caller's to get wrong.
     */
    onClick: (() -> Unit)? = null,
): Modifier {
    val shape = RoundedCornerShape(Radius.control)
    return this
        .height(height)
        .clip(shape)
        .background(Elevated)
        .border(1.5.dp, outline, shape)
        .then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
            else Modifier
        )
        .padding(horizontal = horizontalPadding)
}

/**
 * The one text input. A single line inside [fieldChrome], with a placeholder, an optional trailing
 * adornment, and the whole box tappable.
 *
 * WHY THE WHOLE BOX BEING TAPPABLE IS THE POINT OF THIS FILE
 *
 * This project shipped a number field that measured **23dp inside a 56dp row**. The row was 56, the
 * border was 56, the fill was 56; the text field inside it measured to the height of its own text
 * and the row centred it. So the control looked 56dp tall and only its middle third would take a
 * tap -- touching near the top or bottom edge did nothing at all. It was invisible in the source
 * and invisible in a screenshot, and ScreenFitTest found it on 15 of 18 phone sizes.
 *
 * The fix was one modifier. That is exactly why it belongs here instead of at a call site: a
 * `fillMaxHeight()` in a screen is one careless edit from being dropped, and the next person to add
 * an input starts from nothing and can make the same mistake from scratch. Here there is no way to
 * ask for a field that does not fill its box -- the modifier is not a parameter -- and
 * `TapTargetTest` asserts the measured height on both platforms.
 *
 * Because the field fills the box, its text has to be centred by [decorationBox] rather than
 * riding the top edge.
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    /** Spoken by TalkBack. Required: an unlabelled input is a control a screen reader cannot name. */
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Red outline plus whatever the caller puts in [trailing]. */
    invalid: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Phone,
    /**
     * What the field holds, so the keyboard can offer to fill it. Null means "do not offer".
     *
     * Explicit rather than derived from [keyboardType], because the two answer different questions:
     * a phone keypad is also what a PIN and a verification code are typed on, and inferring
     * [FieldContent.PhoneNumber] from [KeyboardType.Phone] would offer somebody's phone number as a
     * PIN.
     */
    contentType: FieldContent? = null,
    onSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** The error glyph on the number field. Sits inside the box, after the text. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = TextStyle(
        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, letterSpacing = 0.3.sp,
    )
    Row(
        modifier.fieldChrome(outline = if (invalid) Danger else Subtle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // fillMaxHeight is the whole point -- see the note above. Not a parameter, so it
            // cannot be omitted.
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Before the focus requester, so the observer is above the target it watches. A
                // filled value goes through the caller's onValueChange rather than around it,
                // which is what keeps the number field's digit filter and length cap applying to
                // an autofilled number exactly as they do to a typed one.
                .then(
                    contentType?.let { type ->
                        Modifier.autofill(type) { onValueChange(it) }
                    } ?: Modifier
                )
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .semantics { contentDescription = label },
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(Purple),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType, imeAction = ImeAction.Done,
            ),
            // Enter submits, so the keyboard never has to be dismissed to reach the CTA.
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder, color = Faint, fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                            letterSpacing = 0.3.sp, maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        if (trailing != null) {
            // An explicit spacer, not spacedBy: with one child spacedBy puts no space anywhere,
            // and the gap between the digits and the error glyph is 8 in the design.
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}
