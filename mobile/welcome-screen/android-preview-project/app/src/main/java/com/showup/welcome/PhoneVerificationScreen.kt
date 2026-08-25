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
    WelcomeScaffold(peachWash = false, topWeighted = true, orangeAlpha = 0.26f, violetAlpha = 0.22f) {
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
    value: String = "176 123 45 678",
    invalid: Boolean = false,
    countryCode: String = "+49",
    onBack: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onOpenCountryList: () -> Unit = {},
) {
    val compact = LocalConfiguration.current.screenHeightDp < 700
    VerificationFrame(onBack) {
        Eyebrow()
        Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
        WashHeadline(
            parts = listOf("What’s your " to false, "number" to true, "?" to false),
            fontSize = 38.sp,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            "We’ll send a 6-digit code to verify it is you.",
            modifier = Modifier.widthIn(max = 320.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, lineHeight = 21.75.sp,
        )

        Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Country pill. DE / +49 is the mock default only — the real default comes from device
            // locale, and tapping it opens a country list that is out of scope here.
            Row(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Elevated)
                    .border(1.5.dp, Border, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onOpenCountryList)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GermanFlag()
                Text(countryCode, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Icon(BrandIcon.ChevronDown, 16.dp, tint = Muted, strokeWidth = 2.dp)
            }

            // The invalid field keeps the same geometry as the default one, so it does not move
            // when it fails — and the digits are preserved, never cleared.
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Elevated)
                    .border(1.5.dp, if (invalid) Danger else Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value.ifBlank { "176 123 45 678" },
                    color = if (value.isBlank()) Faint else Fg,
                    fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    letterSpacing = 0.3.sp, maxLines = 1,
                )
                if (invalid) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(22.dp).background(Danger, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // ⑥/⑩ the helper row is RESERVED at 20 — the error replaces the text in the same row so
        // nothing below it moves.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp)
                .padding(top = 10.dp, start = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                if (invalid) "Please enter a valid number e.g. 176 123 45 678"
                else "Standard message rates may apply.",
                color = if (invalid) DangerFg else Subtle,
                fontFamily = Manrope,
                fontWeight = if (invalid) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp, lineHeight = 17.55.sp,
            )
        }

        Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
        // Validation runs on submit, not per keystroke, so the CTA is only disabled after a failure
        // and re-enables the moment the value changes.
        PillButton("Send me the code", onSubmit, enabled = !invalid)
    }
}

/** Drawn as three rects with a hairline. CLAUDE.md forbids emoji anywhere, flags included. */
@Composable
private fun GermanFlag() {
    Column(
        Modifier
            .size(width = 22.dp, height = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(0.5.dp, Color(0x591D1129), RoundedCornerShape(2.dp))
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF000000)))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFDD0000)))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFCE00)))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// States C and D — enter code / code mismatch
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VerifyCodeScreen(
    phone: String = "+49 176 123 45 678",
    digits: String = "",
    mismatch: Boolean = false,
    cooldownSeconds: Int = 21,
    onBack: () -> Unit = {},
    onVerify: () -> Unit = {},
    onResend: () -> Unit = {},
    onEditNumber: () -> Unit = {},
) {
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val motion = rememberMotion()

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
        Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
        WashHeadline(
            parts = listOf("Enter your " to false, "code" to true, "." to false),
            fontSize = 38.sp,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
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
        val slotW = if (compact) 44.dp else 49.dp
        val slotH = if (compact) 56.dp else 62.dp
        Spacer(Modifier.height(if (compact) 14.dp else 26.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // +/- 6px, three cycles, decaying to nothing
                    val t = shake.value
                    translationX = if (t == 0f || t == 1f) 0f
                    else (kotlin.math.sin(t * 3f * 2f * Math.PI).toFloat() * 6.dp.toPx() * (1f - t))
                }
                .semantics { contentDescription = "Enter your 6-digit verification code" },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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

        // ⑭/⑰ reserved helper region — see the file header and the audit note.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 59.dp)
                .padding(top = if (compact) 10.dp else 14.dp, start = 2.dp)
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
                        "Code doesn’t match. Please check or request a new code.",
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp, lineHeight = 18.9.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        // Disabled until all six digits are in. In mismatch the digits are still there, so it stays
        // enabled — the user edits one digit and resubmits.
        PillButton("Verify code", onVerify, enabled = digits.length == 6)

        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            Text("Didn’t receive a code?", color = Muted, fontFamily = Manrope,
                 fontWeight = FontWeight.Medium, fontSize = 14.sp)
            // A mistyped code must not cost another 24s wait, so the mismatch state releases the
            // cooldown to 0 and the resend becomes a live button.
            if (mismatch || cooldownSeconds <= 0) {
                Text(
                    "Send a new code",
                    modifier = Modifier.clickable(role = Role.Button, onClick = onResend),
                    color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, textDecoration = TextDecoration.Underline,
                )
            } else {
                Text(
                    "Send a new code in 0:%02d".format(cooldownSeconds),
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
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
@Composable private fun VB375() { PhoneNumberScreen(value = "0151 2", invalid = true) }

@Preview(name = "B · invalid · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun VB390() { PhoneNumberScreen(value = "0151 2", invalid = true) }

@Preview(name = "B · invalid · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun VB430() { PhoneNumberScreen(value = "0151 2", invalid = true) }

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
