/*
 * PhoneVerificationScreen.kt
 * ShowUp · Phone number -> verify code (SHOWUP-143)
 *
 * Four states, two components. A (enter number) and B (invalid number) are one screen; C (enter
 * code) and D (code mismatch) are the other. The failure state is a STATE, not a second screen —
 * which is what makes the "CTA does not move" requirement expressible at all.
 *
 * The helper regions are reserved rather than conditional: 20 on A/B, and on C/D the height the
 * specified error copy actually needs. See audit/AUDIT-welcome-140-142-143.md finding 1 — the sheet
 * says 42, the specified string wraps to two lines at every width, and reserving 42 would let the
 * CTA jump 17px, which the ticket forbids outright.
 *
 * The numeric keypad drawn on the spec sheet is a MOCK. The sheet says so, and says not to build it:
 * the platform keyboard ships instead. That is why nothing here draws keys.
 */
package com.showup.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
// FieldOutline: the input and slot borders take Subtle (ink 46%), not Border (ink 12%).
// WCAG 1.4.11 wants 3:1 for the boundary that identifies a control, and the field's white fill is
// 1.03:1 against the canvas -- the outline is doing all the work. Border is 1.28:1 and fails;
// Subtle is 3.04 against the page and 3.14 against the fill. See audit finding 7.
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerDigit
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.EyebrowBg
import com.showup.designsystem.Faint
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle
import com.showup.tutorial.rememberMotion

// ─────────────────────────────────────────────────────────────────────────────
// shared chrome for both halves of the flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VerificationFrame(
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // C and D drop the backdrop to .22 / .20 — the code screen is deliberately calmer than the
    // entry screen, and the keyboard owns the bottom half so nothing should glow behind it.
    WelcomeScaffold(
        peachWash = false, topWeighted = true,
        orangeAlpha = 0.26f, violetAlpha = 0.22f,
        topPadding = 4.dp,          // content pad-top 4 on this screen, not the launch screens' 20
        scrollWhenTight = true,     // the keyboard shares this frame; the CTA must stay reachable
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(BrandIcon.ArrowLeft, 22.dp, tint = Fg, strokeWidth = 2.dp)
        }
        content()
        // One flex:1 spacer at the bottom — unlike Startup and Welcome back, everything here is
        // top-anchored and the keyboard closes the frame. Nothing floats.
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ColumnScope.Eyebrow() {
    Row(
        Modifier
            .align(Alignment.Start)
            .clip(RoundedCornerShape(50))
            .background(EyebrowBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(Orange, CircleShape))
        Text(
            "PHONE VERIFICATION", color = Purple, fontFamily = Manrope,
            fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.88.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// States A and B — enter number / invalid number
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PhoneNumberScreen(
    /** Digits only, no spaces and no dial code -- the pill carries that. */
    value: String = "",
    onValueChange: (String) -> Unit = {},
    country: Country = DEFAULT_COUNTRY,
    error: PhoneError? = null,
    onBack: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onOpenCountryList: () -> Unit = {},
) {
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val focus = remember { FocusRequester() }
    val invalid = error != null

    // The keyboard is why the user is here, so it opens with the screen rather than after a tap.
    LaunchedEffect(Unit) { focus.requestFocus() }

    VerificationFrame(onBack) {
        Eyebrow()
        Spacer(Modifier.height(if (compact) 2.dp else 14.dp))
        WashHeadline(
            parts = listOf("What’s your " to false, "number" to true, "?" to false),
            fontSize = 38.sp,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
        Text(
            "We’ll send a 6-digit code to verify it is you.",
            modifier = Modifier.widthIn(max = 320.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, lineHeight = 21.75.sp,
        )

        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Country pill -- opens the list. The default comes from device locale; see SignUpFlow.
            Row(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Elevated)
                    .border(1.5.dp, Subtle, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onOpenCountryList)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Flag(country)
                Text(country.dial, color = Fg, fontFamily = Manrope,
                     fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Icon(BrandIcon.ChevronDown, 16.dp, tint = Muted, strokeWidth = 2.dp)
            }

            // The invalid field keeps the same geometry as the default one, so it does not move
            // when it fails -- and the digits are preserved, never cleared.
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Elevated)
                    .border(1.5.dp, if (invalid) Danger else Subtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    // Digits only at the source, so nothing downstream has to strip characters
                    // that were never allowed in.
                    //
                    // The cap is the country's maximum PLUS an allowance, never the maximum itself:
                    // capping exactly at the limit silently swallows the extra keystrokes, so a
                    // too-long number cannot be typed and the error that exists for it can never
                    // fire. See OVERTYPE_ALLOWANCE.
                    onValueChange = { raw ->
                        onValueChange(
                            raw.filter { it.isDigit() }.take(country.nsnMax + OVERTYPE_ALLOWANCE)
                        )
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    textStyle = TextStyle(
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp, letterSpacing = 0.3.sp,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Purple),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done,
                    ),
                    // Enter submits, so the user never has to dismiss the keyboard to reach the CTA.
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    visualTransformation = remember(country) { GroupedDigits(country) },
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                country.sample, color = Faint, fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                                letterSpacing = 0.3.sp, maxLines = 1,
                            )
                        }
                        inner()
                    },
                )
                if (invalid) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(22.dp).background(Danger, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontFamily = Lora,
                             fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // The helper row is RESERVED at 20 -- the error replaces the text in the same row so
        // nothing below it moves. The messages name the country now, so the reserve is a minimum
        // rather than a fixed height and a two-line message grows downward into the spacer.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .padding(top = 10.dp, start = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                error?.message(country) ?: "Standard message rates may apply.",
                // Muted, not Subtle -- helper text has to be readable. See audit finding 7.
                color = if (invalid) DangerFg else Muted,
                fontFamily = Manrope,
                fontWeight = if (invalid) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp, lineHeight = 17.55.sp,
            )
        }

        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
        // Validation runs on submit, not per keystroke. The button stays live so the user can ask
        // for the check -- what changes on failure is the message, not the availability of the
        // action. A disabled CTA cannot explain itself, which the ticket lists as an open concern.
        PillButton("Send me the code", onSubmit)
    }
}

/**
 * Groups the digits the way that country writes them, without changing the stored value.
 *
 * A VisualTransformation rather than reformatting the state on every keystroke: the field holds
 * plain digits, the spaces are painted on, and the OffsetMapping keeps the caret where the user
 * put it. Reformatting the value itself is what makes a phone field jump the cursor to the end.
 */
private class GroupedDigits(private val country: Country) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = formatNational(digits, country)

        // digit index -> index in the painted string, plus one entry for one-past-the-end
        val toShown = IntArray(digits.length + 1)
        var d = 0
        shown.forEachIndexed { i, c -> if (c.isDigit()) { toShown[d] = i; d++ } }
        toShown[digits.length] = shown.length

        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) =
                    toShown[offset.coerceIn(0, digits.length)]

                override fun transformedToOriginal(offset: Int) =
                    shown.take(offset.coerceIn(0, shown.length)).count { it.isDigit() }
            },
        )
    }
}

// The flag moved to CountryPicker.kt as `Flag`, which draws any of them from the country table
// rather than hard-coding Germany. Still rects, never emoji.

// ─────────────────────────────────────────────────────────────────────────────
// States C and D — enter code / code mismatch
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VerifyCodeScreen(
    phone: String = "+49 176 123 45 678",
    digits: String = "",
    onDigitsChange: (String) -> Unit = {},
    mismatch: Boolean = false,
    cooldownSeconds: Int = 21,
    onBack: () -> Unit = {},
    onVerify: () -> Unit = {},
    onResend: () -> Unit = {},
    onEditNumber: () -> Unit = {},
) {
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val motion = rememberMotion()
    val focus = remember { FocusRequester() }

    // Same reasoning as state A: this screen exists to be typed into, so the keyboard opens with
    // it rather than waiting for the user to discover that the slots are tappable.
    LaunchedEffect(Unit) { focus.requestFocus() }

    // ⑯ one-shot shake, 480ms, on entering the mismatch state — then still. There is no looping
    // animation anywhere in this flow.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(mismatch) {
        if (mismatch && motion.enabled) {
            shake.snapTo(0f)
            shake.animateTo(1f, androidx.compose.animation.core.tween(480, easing = ShowUpEasing))
        } else {
            shake.snapTo(0f)
        }
    }

    VerificationFrame(onBack) {
        Eyebrow()
        Spacer(Modifier.height(if (compact) 2.dp else 14.dp))
        WashHeadline(
            parts = listOf("Enter your " to false, "code" to true, "." to false),
            fontSize = 38.sp,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
        Text(
            buildAnnotatedString {
                append("We just sent a 6-digit code to ")
                // must be the number actually submitted on A — the user's only chance to catch a
                // typo before waiting for an SMS that will never arrive
                withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append(phone) }
                append(".")
            },
            modifier = Modifier.widthIn(max = 320.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, lineHeight = 21.75.sp,
        )

        // ⑬ six slots. 49 x 62 at 390 and above; 44 x 56 with gap 6 on the short frame, which is
        // the shrink the sheet names.
        //
        // ONE text field behind all six, not six fields. Six would mean six focus targets, and
        // then backspace, paste and SMS autofill each have to be taught to hop between them —
        // which is exactly where per-digit OTP inputs usually break. Here the field holds the
        // whole code and the slots are its decoration, so paste and autofill work for free.
        val slotW = if (compact) 44.dp else 49.dp
        val slotH = if (compact) 56.dp else 62.dp
        Spacer(Modifier.height(if (compact) 8.dp else 26.dp))
        BasicTextField(
            value = digits,
            onValueChange = { raw -> onDigitsChange(raw.filter { it.isDigit() }.take(6)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .graphicsLayer {
                    // +/- 6px, three cycles, decaying to nothing
                    val t = shake.value
                    translationX = if (t == 0f || t == 1f) 0f
                    else (kotlin.math.sin(t * 3f * 2f * Math.PI).toFloat() * 6.dp.toPx() * (1f - t))
                }
                .semantics { contentDescription = "Enter your 6-digit verification code" },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (digits.length == 6) onVerify() }),
            singleLine = true,
            // The glyphs are painted by the slots, so the field itself draws nothing — no text and
            // no system caret. The violet caret in the active slot is ours.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(6) { i ->
                        val ch = digits.getOrNull(i)
                        val active = !mismatch && i == digits.length && digits.length < 6
                        Box(
                            Modifier
                                .size(width = slotW, height = slotH)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (mismatch) Danger.copy(alpha = 0.04f) else Elevated)
                                .border(
                                    1.5.dp,
                                    when {
                                        mismatch -> Danger
                                        active -> Purple
                                        ch != null -> Fg.copy(alpha = 0.32f)
                                        else -> Border
                                    },
                                    RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ch != null) {
                                Text(
                                    ch.toString(),
                                    color = if (mismatch) DangerDigit else Fg,
                                    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                                )
                            } else if (active) {
                                // 2 x 28 violet caret. No caret at all while in error.
                                Box(Modifier.size(width = 2.dp, height = 28.dp).background(Purple))
                            }
                        }
                    }
                }
            },
        )

        // ⑭/⑰ reserved helper region — see the file header and the audit note.
        Box(
            Modifier
                .fillMaxWidth()
                // 42, and it agrees with the reference rather than contradicting it.
                //
                // screen-phone-reference.jsx reserves 62 here and says why: "62 because the verify
                // error box is 60". So the rule is `reserve = error box + 2`, and the box height
                // follows from the copy:
                //
                //   reference copy  "Code doesn't match. Please check or request a new code."
                //                   wraps to two lines -> 10 + 18.9x2 + 10 + 2 borders = 60 -> 62
                //   our copy        "That code didn't match. Try again."
                //                   one line at every width -> 10 + 18.9 + 10 + 2      = 41 -> 42
                //
                // Same rule, different string. Reserving 62 for a 41 box would pad 21dp of dead
                // space into every state and push the CTA down for no reason — the reserve exists
                // so the CTA does not move, not to hit a particular number. If the longer string
                // is ever restored, this goes back to 62 with it; the two move together.
                .heightIn(min = 42.dp)
                .padding(top = if (compact) 6.dp else 14.dp, start = 2.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (mismatch) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Danger.copy(alpha = 0.07f))
                        .border(1.dp, Danger.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(18.dp).background(Danger, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    // informative, never "Wrong" / "Failed" / "Error"
                    Text(
                        "That code didn’t match. Try again.",
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp, lineHeight = 18.9.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        // Disabled until all six digits are in. In mismatch the digits are still there, so it stays
        // enabled — the user edits one digit and resubmits.
        PillButton("Verify code", onVerify, enabled = digits.length == 6)

        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        // A mistyped code must not cost another 24s wait, so the mismatch state releases the
        // cooldown to 0 and the resend becomes a live button.
        val resendLive = mismatch || cooldownSeconds <= 0
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            // The question and the action are ONE target while the resend is live, so the whole
            // block is tappable rather than just the underlined phrase. While cooling it is inert
            // on purpose: the sheet allows no silent resend, so a tappable label during the
            // cooldown would be either a dead control or a rule broken.
            Column(
                Modifier
                    .then(
                        if (resendLive) Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onResend)
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            ) {
                Text("Didn’t receive a code?", color = Muted, fontFamily = Manrope,
                     fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (resendLive) {
                    Text(
                        "Send a new code",
                        color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, textDecoration = TextDecoration.Underline,
                    )
                } else {
                    Text(
                        "Send a new code in 0:%02d".format(cooldownSeconds),
                        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onEditNumber)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(BrandIcon.Pencil, 13.dp, tint = Muted, strokeWidth = 1.8.dp)
                Text("Edit phone number", color = Muted, fontFamily = Manrope,
                     fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

// ── previews: four states x three frames ────────────────────────────────────

@Preview(name = "A · number · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun VA375() { PhoneNumberScreen() }

@Preview(name = "A · number · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun VA390() { PhoneNumberScreen() }

@Preview(name = "A · number · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun VA430() { PhoneNumberScreen() }

@Preview(name = "B · invalid · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun VB375() { PhoneNumberScreen(value = "01512", error = PhoneError.LeadingZero) }

@Preview(name = "B · invalid · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun VB390() { PhoneNumberScreen(value = "01512", error = PhoneError.LeadingZero) }

@Preview(name = "B · invalid · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun VB430() { PhoneNumberScreen(value = "01512", error = PhoneError.LeadingZero) }

@Preview(name = "C · code · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun VC375() { VerifyCodeScreen() }

@Preview(name = "C · code · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun VC390() { VerifyCodeScreen() }

@Preview(name = "C · code · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun VC430() { VerifyCodeScreen() }

@Preview(name = "D · mismatch · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun VD375() { VerifyCodeScreen(digits = "482170", mismatch = true) }

@Preview(name = "D · mismatch · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun VD390() { VerifyCodeScreen(digits = "482170", mismatch = true) }

@Preview(name = "D · mismatch · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun VD430() { VerifyCodeScreen(digits = "482170", mismatch = true) }

// The twelve previews above render each state on its own: no window, so WindowInsets.safeDrawing
// is zero and the content sits higher than it will on a phone. They answer "does it fit".
//
// These four draw the real status and navigation bars, which also makes the insets real. It matters
// more on this screen than on any other in the flow, because the keyboard defines how much room is
// left and the sheet's own budget assumes a real one rather than the artboard's 214pt mock.
@Preview(name = "A · with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable private fun VASystemUi() { PhoneNumberScreen() }

@Preview(name = "B · with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable private fun VBSystemUi() { PhoneNumberScreen(value = "01512", error = PhoneError.LeadingZero) }

@Preview(name = "C · with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable private fun VCSystemUi() { VerifyCodeScreen() }

@Preview(name = "D · with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable private fun VDSystemUi() { VerifyCodeScreen(digits = "482170", mismatch = true) }
