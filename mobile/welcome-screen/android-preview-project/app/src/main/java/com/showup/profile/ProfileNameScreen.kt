/*
 * ProfileNameScreen.kt
 * ShowUp · Profile creation 01 — Name (SHOWUP-150)
 *
 * Three states, one component, one layout column:
 *   A · empty, field focused        ProfileNameScreen()
 *   B · a value entered             ProfileNameScreen(value = "Leo")
 *   C · empty submit                reached only by a real press
 *
 * Nothing moves between them. That is the point of the reserved status region below the field, and
 * it is what makes three artboards read as one screen rather than three.
 *
 * THE CTA IS NEVER DISABLED, AND THAT IS THE WHOLE INTERACTION
 *
 * Pressing Continue on an empty field is what teaches the requirement. A dead button cannot explain
 * itself, and a disabled CTA means the error string is never seen at all. The press sets
 * `attempted`, which surfaces the card and shakes the field once.
 *
 * NO BACK, AND THE OS BACK IS SUPPRESSED TOO
 *
 * The design reference passes `leading="back"` here, which contradicts its own header comment, the
 * ticket's acceptance criteria and the spec sheet -- all three of which say the slot is EMPTY and
 * the flow is mandatory. The ticket wins on behaviour, so this screen has no chevron and swallows
 * the system back. Reported to the design side.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.FloatingField
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Neutral
import com.showup.designsystem.Fg
import com.showup.designsystem.rememberMotion
import com.showup.tutorial.NextButton
import com.showup.tutorial.StepProgress
import com.showup.welcome.WashHeadline

/** Copy — final strings. Every one of these is asserted by the conformance checker. */
internal object NameCopy {
    const val SECTION = "The basics"
    const val SUB = "Your name will be shown on your profile."
    const val LABEL = "Enter first name"
    const val PLACEHOLDER = "First name"
    const val CTA = "Continue"
    const val EMPTY_ERROR = "Enter your name to continue."
}

@Composable
fun ProfileNameScreen(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onContinue: (String) -> Unit = {},
    /** Fires on the refused press. Hoisted so the screen stays a function of values. */
    onEmptySubmit: () -> Unit = {},
) {
    // Not restored on arrival: a resumed step behaves like a freshly-reached one, and an error is
    // the product of a press rather than a position. rememberSaveable so a rotation mid-error does
    // not silently clear it.
    var attempted by rememberSaveable { mutableStateOf(false) }
    var shakeKey by remember { mutableIntStateOf(0) }

    val filled = value.trim().isNotEmpty()
    val isError = attempted && !filled

    // Once something is typed, a later clear must not re-fire the error until Continue is pressed
    // again. Keyed on `filled` so it runs on the transition, not every recomposition.
    LaunchedEffect(filled) { if (filled) attempted = false }

    val focus = remember { FocusRequester() }
    // Focused on arrival, keyboard already open -- the user types without tapping. runCatching
    // because a focus request throws whenever the field is not yet attached to a window, and a
    // convenience must never be fatal.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // The flow is mandatory: there is nothing behind step 1. A header with no chevron that the OS
    // can still dismiss is worse than no rule at all, so the gesture is swallowed here.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    val submit = {
        if (filled) {
            onContinue(value.trim())
        } else {
            attempted = true
            shakeKey += 1
            onEmptySubmit()
        }
    }

    BasicsScaffold(
        title = NameCopy.SECTION,
        leading = HeaderLeading.None,
        cta = {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Orange, not sunset -- these are routine screens. Arrow 22, which is why
                // NextButton grew an arrowSize parameter.
                NextButton(NameCopy.CTA, submit, arrowSize = 22.dp)
            }
        },
    ) {
        StepProgress(steps = 3, current = BasicsStep.Name.progressSegment)
        Box(Modifier.height(28.dp))

        // Lora 700 / 34 / 1.1 / -0.015em. WashHeadline's defaults are 1.05 and -0.02em -- the
        // welcome flow's values -- so both are passed explicitly rather than inherited. 34 and not
        // the welcome flow's 38: the keyboard is always open here, so the headline is one step
        // smaller. The apostrophe is the ticket's straight one; the welcome flow uses a curly one
        // and the design reference is itself inconsistent. Raised with design.
        WashHeadline(
            parts = listOf("What's your " to false, "name" to true, "?" to false),
            fontSize = 34.sp,
            lineHeight = (34f * 1.1f).sp,
            letterSpacing = (-0.015).em,
        )
        Box(Modifier.height(10.dp))

        Text(
            NameCopy.SUB,
            modifier = Modifier.widthIn(max = 320.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, lineHeight = (15f * 1.45f).sp,
        )

        val motion = rememberMotion()
        Box(
            Modifier
                .padding(top = 28.dp)
                .shakeOnce(shakeKey, isError && motion.enabled),
        ) {
            FloatingField(
                value = value,
                onValueChange = onValueChange,
                label = NameCopy.LABEL,
                placeholder = NameCopy.PLACEHOLDER,
                error = isError,
                keyboardType = KeyboardType.Text,
                // A hint, not an enforcement. Forcing the first letter breaks names that are
                // deliberately lower-case, and presentation is the wrong layer for it.
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Go,
                onSubmit = { submit() },
                focusRequester = focus,
            )
        }

        // RESERVED, not conditional. 44 holds the space in A and B so the CTA and the keyboard sit
        // at the identical Y in all three states. Rendering it conditionally is exactly how three
        // states stop reading as one screen.
        Box(
            Modifier
                .padding(top = 10.dp, start = 2.dp)
                .defaultMinSize(minHeight = 44.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (isError) InlineErrorCard(NameCopy.EMPTY_ERROR)
        }
    }
}

/**
 * The one-shot 480ms failure shake.
 *
 * Re-keyed per failure so a second refused press shakes again -- without the key the animation has
 * already run and a repeat press produces nothing, which reads as the button having died.
 */
@Composable
internal fun Modifier.shakeOnce(key: Int, enabled: Boolean): Modifier {
    if (!enabled) return this
    val progress = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(Motion.SHAKE))
    }
    return this.graphicsLayer {
        val t = progress.value
        // Matches the reference keyframes: -2, +3, -6, +6, decaying to nothing.
        translationX = if (t == 0f || t == 1f) 0f
        else (kotlin.math.sin(t * 3f * 2f * Math.PI).toFloat() * 6.dp.toPx() * (1f - t))
    }
}
