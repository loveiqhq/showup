/*
 * CheckInUpliftScreen.kt
 * ShowUp · Check-In 06 (SHOWUP-183): Uplift + the commit
 *
 * `UpliftStep` from `screen-checkin-uplift-reference.jsx`, inside the step shell with the ambient
 * backdrop -- the one step that has it. Five states: A on entry (always empty), B typing with the
 * keyboard open, C contact info blocked, D saving, E save failed. D and E are [CheckInFooter]'s
 * `loading` and `error`, added for this story to the 02 component -- a variant, not a fork.
 *
 * THE WHOLE STEP BODY IS ONE SCROLL CONTAINER, the headline included: "with the keyboard open there
 * is no room to pin it". `Find my date` is pinned below it and rides directly above the keyboard
 * (the shell's `imePadding`); the platform keyboard is used, never the kit's mock, with return
 * inserting a new line and sentence capitalisation. A tap outside the field closes the keyboard.
 */
package com.showup.checkin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.Danger
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.designsystem.halo

/** The field's geometry from `UpliftStep` -- local to this field, which no other screen draws. */
private val FIELD_MIN_HEIGHT = 116.dp
private val FIELD_PADDING = 14.dp
private val FIELD_BORDER = 1.5.dp
private val FIELD_RADIUS = 16.dp

/** Fit-harness anchors. */
internal const val UPLIFT_FIELD_TAG = "uplift-field"
internal const val UPLIFT_COUNTER_TAG = "uplift-counter"
internal const val UPLIFT_INSPIRATIONS_TAG = "uplift-inspirations"

@Composable
fun CheckInUpliftScreen(
    text: String,
    modifier: Modifier = Modifier,
    blocked: Boolean = false,
    saving: Boolean = false,
    saveFailed: Boolean = false,
    toast: String? = null,
    toastTick: Int = 0,
    bodyScroll: ScrollState = rememberScrollState(),
    onText: (String) -> Unit = {},
    onFindMyDate: () -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    // Where the field sits inside the body, so a tap on the body can tell "outside the field" apart.
    var bodyBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var fieldBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    CheckInStepShell(
        step = CheckInStep.Uplift,
        onBack = onBack,
        onClose = onClose,
        modifier = modifier,
        ambient = true,
        footer = {
            CheckInFooter(
                label = CheckInCopy.FIND_MY_DATE,
                onClick = onFindMyDate,
                dimmed = blocked,
                toast = toast,
                toastTick = toastTick,
                loading = saving,
                error = if (saveFailed && toast == null) CheckInCopy.SAVE_ERROR else null,
            )
        },
    ) {
        // THE STEP BODY -- one scroll container, the only flexible element. A tap anywhere in it
        // outside the field closes the keyboard. Measured against the field's own bounds rather
        // than left to event consumption: the parent hears a tap on the field too, and clearing
        // focus there would shut the keyboard the user had just opened.
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { bodyBox = it }
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        val body = bodyBox
                        val field = fieldBox
                        val inField = body != null && field != null && field.isAttached &&
                            body.localBoundingBoxOf(field).contains(at)
                        if (!inField) focus.clearFocus()
                    }
                }
                .verticalScroll(bodyScroll)
                .padding(bottom = Spacing.xl)
                .testTag(CHECK_IN_SCROLL_TAG),
        ) {
            Spacer(Modifier.height(Spacing.md))
            CheckInStepHeadline(CheckInCopy.UPLIFT_HEADLINE)
            CoachNote(CheckInCopy.UPLIFT_NOTE, warm = false, modifier = Modifier.padding(top = 14.dp))

            UpliftField(
                text,
                blocked,
                readOnly = saving,
                onText = onText,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .onGloballyPositioned { fieldBox = it },
            )

            Text(
                upliftCounterLabel(text),
                Modifier
                    .padding(top = Spacing.sm)
                    .testTag(UPLIFT_COUNTER_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = FgSubtle,
                fontFamily = Manrope,
                fontSize = 12.sp,
            )
            if (blocked) {
                // `role="alert"`.
                Box(
                    Modifier
                        .padding(top = Spacing.lg)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                ) { InlineErrorCard(CheckInCopy.CONTACT_ERROR) }
            }

            // Inspirations -- plain text, read as a list. Not tappable, not buttons, never copied in.
            Column(
                Modifier
                    .padding(top = 22.dp)
                    .testTag(UPLIFT_INSPIRATIONS_TAG),
            ) {
                Text(
                    CheckInCopy.INSPIRATIONS_LABEL,
                    Modifier.padding(bottom = Spacing.lg),
                    color = Fg,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    val style = TextStyle(
                        color = Muted, fontFamily = Manrope, fontStyle = FontStyle.Italic,
                        fontSize = 14.sp, lineHeight = (14f * 1.45f).sp,
                    )
                    CheckInCopy.INSPIRATIONS.forEach { line ->
                        // `textWrap: 'pretty'`, as the reference sets on each line.
                        val quoted = remember(line) { AnnotatedString("“$line”") }
                        Text(quoted, Modifier.prettyWrap(quoted, style), style = style)
                    }
                }
            }
        }
    }
}

/**
 * The field: min 116, padding 14, radius 16, Manrope 14 / 1.5, growing with the text. A 1.5 border
 * that is violet when focused and danger while it holds contact info -- with a 4 ring of the same
 * at .10 and a danger tint at .04 -- and `--liq-border` otherwise. At .7 while the commit is in
 * flight, read-only.
 *
 * BORDER-BOX, as the kit lays every element out (`.su-app * { box-sizing: border-box }`): the 116
 * includes the 1.5 border and the 14 padding, so the text starts 15.5 in. Compose's `border` draws
 * over the padding rather than outside it, hence the inset written as the sum.
 */
@Composable
private fun UpliftField(
    text: String,
    blocked: Boolean,
    readOnly: Boolean,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(FIELD_RADIUS)
    val stroke = when {
        blocked -> Danger
        focused -> Purple
        else -> Border
    }
    val ring = when {
        blocked -> Danger.copy(alpha = 0.10f)
        focused -> Purple.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Box(
        modifier
            .fillMaxWidth()
            .alpha(if (readOnly) 0.7f else 1f)
            // Before the clip: the halo is drawn outside the box.
            .halo(ring, FIELD_RADIUS)
            .clip(shape)
            .background(if (blocked) Danger.copy(alpha = 0.04f) else Elevated)
            .border(FIELD_BORDER, stroke, shape)
            .padding(FIELD_PADDING + FIELD_BORDER),
    ) {
        if (text.isEmpty()) {
            Text(
                CheckInCopy.UPLIFT_PLACEHOLDER,
                color = Subtle,
                fontFamily = Manrope,
                fontSize = 14.sp,
                lineHeight = (14f * 1.5f).sp,
            )
        }
        BasicTextField(
            value = text,
            // Cut at 100 graphemes in the view model: input past it is not accepted, a paste is cut.
            onValueChange = onText,
            readOnly = readOnly,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FIELD_MIN_HEIGHT - (FIELD_PADDING + FIELD_BORDER) * 2)
                .onFocusChanged { focused = it.isFocused }
                .testTag(UPLIFT_FIELD_TAG)
                .semantics {
                    contentDescription = CheckInCopy.UPLIFT_FIELD_LABEL
                    if (blocked) error(CheckInCopy.CONTACT_ERROR)
                },
            textStyle = TextStyle(color = Fg, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14f * 1.5f).sp),
            cursorBrush = SolidColor(Purple),
            // Return inserts a new line: no Done key. Sentence capitalisation, as requested of the OS.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            singleLine = false,
        )
    }
}

@Preview(name = "Check-In 06 · A empty · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewUpliftEmpty() = CheckInUpliftScreen(text = "")

@Preview(name = "Check-In 06 · C blocked · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewUpliftBlocked() = CheckInUpliftScreen(
    text = "Text me on 0176 2345 6789 :)",
    blocked = true,
    toast = CheckInCopy.CONTACT_ERROR,
)

@Preview(name = "Check-In 06 · D saving · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewUpliftSaving() = CheckInUpliftScreen(
    text = "will take you out on my boat on Wannsee",
    saving = true,
)

@Preview(name = "Check-In 06 · E failed · 390", widthDp = 390, heightDp = 844)
@Composable private fun PreviewUpliftFailed() = CheckInUpliftScreen(
    text = "will take you out on my boat on Wannsee",
    saveFailed = true,
)
