/*
 * ProfileEmailScreen.kt
 * ShowUp · Profile creation 02 — Email + marketing consent (SHOWUP-152)
 *
 * Three states:
 *   A · a format-valid address, success tick, calm helper line
 *   B · a malformed address submitted
 *   C · an empty submit
 *
 * THIS SCREEN DELIBERATELY DOES NOT RESERVE ITS STATUS REGION
 *
 * Steps 1 and 3 reserve theirs so nothing moves. This one is the group's densest -- field, helper,
 * consent row and CTA all clear a keyboard -- and reserving the taller error height in the calm
 * state would push the CTA into the keys. So the consent row and CTA sit ~18 lower in the error
 * state, and that is accepted. What must hold instead is the acceptance test: the CTA stays fully
 * visible above the keyboard in ALL THREE states at every size. That is why the CTA's bottom margin
 * is 10 here and 18 on name.
 *
 * EVERY GAP HERE IS TIGHTER THAN STEP 1's AND THAT IS NOT DRIFT
 *
 * progress 18 not 28 · headline 32 not 34 · sub 14.5 not 15 · field top 16 not 28 · helper 8 not 10
 * · CTA bottom 10 not 18. The density is the price of the consent row. Normalising them to match
 * step 1 is what the ticket explicitly forbids.
 */
package com.showup.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.CheckGlyph
import com.showup.designsystem.Danger
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FloatingField
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Purple
import com.showup.designsystem.Radius
import com.showup.designsystem.Raised
import com.showup.designsystem.Spacing
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.rememberMotion
import com.showup.tutorial.NextButton
import com.showup.tutorial.StepProgress
import com.showup.welcome.WashHeadline

/** Copy — final strings. */
internal object EmailCopy {
    const val SECTION = "The basics"
    const val SUB = "Never shown on your profile. Used for password reset, receipts and support."
    const val LABEL = "Email"
    const val PLACEHOLDER = "you@example.com"
    const val HELPER = "We'll send a 6-digit code to confirm it's really you."
    const val CONSENT = "Receive curated tips, local event invites, and special offers. " +
        "No spam, you can opt out whenever you like."
    const val CTA = "Continue"

    const val EMPTY_ERROR = "Enter your email to continue."
    const val NO_AT_LEAD = "Add an "
    const val NO_AT_CHIP = "@"
    const val NO_AT_TAIL = " — e.g. you@example.com"
    const val NO_DOT_LEAD = "Looks like the domain is missing "
    const val NO_DOT_CHIP = ".com"
    const val NO_DOT_TAIL = " (or similar)."
    const val GENERIC_ERROR = "That email doesn’t look quite right. Please check it."
}

/**
 * Format only -- `something@something.something`.
 *
 * It drives the tick and the helper line and **does not gate the CTA**. Deliberately not an
 * RFC-complete validator: the screen's job is to catch a typo before a code is sent to nobody, and
 * a stricter check rejects addresses that genuinely work.
 */
internal fun isEmailFormatValid(value: String): Boolean =
    Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value.trim())

/**
 * The message is chosen from what the user actually typed.
 *
 * "Please enter a valid email" is banned: it tells the user nothing they do not already know.
 * Naming the missing character makes the fix one keystroke, which is why the fragment comes back as
 * a chip rather than being buried in the sentence.
 */
internal fun emailErrorCopy(raw: String, chipStyle: SpanStyle): AnnotatedString {
    val value = raw.trim()
    return when {
        value.isEmpty() -> AnnotatedString(EmailCopy.EMPTY_ERROR)
        !value.contains('@') -> buildAnnotatedString {
            append(EmailCopy.NO_AT_LEAD)
            withStyle(chipStyle) { append(EmailCopy.NO_AT_CHIP) }
            append(EmailCopy.NO_AT_TAIL)
        }
        !value.substringAfter('@').contains('.') -> buildAnnotatedString {
            append(EmailCopy.NO_DOT_LEAD)
            withStyle(chipStyle) { append(EmailCopy.NO_DOT_CHIP) }
            append(EmailCopy.NO_DOT_TAIL)
        }
        else -> AnnotatedString(EmailCopy.GENERIC_ERROR)
    }
}

@Composable
fun ProfileEmailScreen(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    consent: Boolean = false,
    onConsentChange: (Boolean) -> Unit = {},
    onContinue: (String) -> Unit = {},
    onBack: () -> Unit = {},
    /** Fires on the refused press only. */
    onSubmitRefused: (empty: Boolean) -> Unit = {},
    /** Seeds the error state for an artboard. **Previews only** — see ProfileNameScreen. */
    previewAttempted: Boolean = false,
) {
    var attempted by rememberSaveable { mutableStateOf(previewAttempted) }
    var shakeKey by remember { mutableIntStateOf(0) }

    val valid = isEmailFormatValid(value)
    val isError = attempted && !valid

    // Clears as soon as the value becomes format-valid -- not on blur, not on re-submit. Editing a
    // valid value back to invalid does not re-fire until Continue is pressed again.
    LaunchedEffect(valid) { if (valid) attempted = false }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val submit = {
        if (valid) {
            // Trimmed and lower-cased on submit.
            onContinue(value.trim().lowercase())
        } else {
            attempted = true
            shakeKey += 1
            onSubmitRefused(value.trim().isEmpty())
        }
    }

    BasicsScaffold(
        title = EmailCopy.SECTION,
        leading = HeaderLeading.Back,
        onBack = onBack,
        cta = {
            Row(
                // 10, not name's 18 -- the tight margin is what keeps the CTA clear of the keys in
                // the error state, where everything below the field sits ~18 lower.
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NextButton(EmailCopy.CTA, submit, arrowSize = 22.dp)
            }
        },
    ) {
        StepProgress(steps = 3, current = BasicsStep.Email.progressSegment)
        Box(Modifier.height(18.dp))

        WashHeadline(
            parts = listOf("What's your " to false, "email" to true, "?" to false),
            fontSize = 32.sp,
            lineHeight = (32f * 1.1f).sp,
            letterSpacing = (-0.015).em,
        )
        Box(Modifier.height(8.dp))

        // Leads with what the email is NOT. Email is the field users most expect to be spammed
        // from, so the reassurance goes first and the three real uses second -- and it is also the
        // answer to the consent row below it.
        Text(
            EmailCopy.SUB,
            modifier = Modifier.widthIn(max = 320.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp, lineHeight = (14.5f * 1.4f).sp,
        )

        val motion = rememberMotion()
        Box(
            Modifier
                .padding(top = 16.dp)
                .shakeOnce(shakeKey, isError && motion.enabled),
        ) {
            FloatingField(
                value = value,
                onValueChange = onValueChange,
                label = EmailCopy.LABEL,
                placeholder = EmailCopy.PLACEHOLDER,
                valid = valid,
                error = isError,
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go,
                onSubmit = { submit() },
                focusRequester = focus,
            )
        }

        // NOT height-reserved -- see the file header. The error card is ~18 taller than the helper
        // line it replaces, and everything below shifts by that much. Deliberate on this screen.
        Box(
            Modifier
                .padding(top = 8.dp, start = 2.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (isError) {
                InlineErrorCard(
                    emailErrorCopy(
                        value,
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            background = Danger.copy(alpha = 0.10f),
                        ),
                    )
                )
            } else {
                Row(
                    Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 1.dp)) {
                        CheckGlyph(size = 16.dp, color = SuccessFg, strokeWidth = 2.dp)
                    }
                    // Muted, NOT success green -- the tick carries the colour. This is the only
                    // place in the flow the user is told a code screen is coming.
                    Text(
                        EmailCopy.HELPER,
                        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.sp, lineHeight = (13f * 1.4f).sp,
                    )
                }
            }
        }

        Box(Modifier.padding(top = 10.dp)) {
            MarketingOptIn(checked = consent, onToggle = { onConsentChange(!consent) })
        }
    }
}

/**
 * The marketing consent row.
 *
 * **Opt-IN.** Unchecked by default, never pre-ticked, never gates Continue, never styled as an
 * error. The whole row is the hit area, not just the box. Transactional email -- the verification
 * code, password reset, receipts -- is unaffected and needs no consent; this row governs marketing
 * only, which is what the sub copy above it already promised.
 */
@Composable
internal fun MarketingOptIn(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.errorBox)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Raised)
            .border(1.dp, BorderSoft, shape)
            // The whole row, so the target is the copy as well as the 22 box.
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            EmailCopy.CONSENT,
            modifier = Modifier.weight(1f),
            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = (13f * 1.3f).sp,
        )
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Purple else Elevated)
                .border(1.5.dp, if (checked) Purple else Border, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) CheckGlyph(size = 13.dp, color = Elevated, strokeWidth = 3.dp)
        }
    }
}

// ── previews: three states x three frames ───────────────────────────────────
//
// A and B seed the SAME address, one of them mistyped, on purpose: the states should read as one
// field failing rather than two screens. B and C are also where the ~18px shift lives — this
// screen deliberately does not reserve its status region, so compare the CTA's position between
// A and B here and expect it to move.

@Preview(name = "A · valid · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_A375() { ProfileEmailScreen(value = "leo@hey.com") }

@Preview(name = "A · valid · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_A390() { ProfileEmailScreen(value = "leo@hey.com") }

@Preview(name = "A · valid · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE_A430() { ProfileEmailScreen(value = "leo@hey.com") }

@Preview(name = "B · invalid · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_B375() { ProfileEmailScreen(value = "leo@hey", previewAttempted = true) }

@Preview(name = "B · invalid · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_B390() { ProfileEmailScreen(value = "leo@hey", previewAttempted = true) }

@Preview(name = "B · invalid · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE_B430() { ProfileEmailScreen(value = "leo@hey", previewAttempted = true) }

@Preview(name = "C · empty submit · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_C375() { ProfileEmailScreen(previewAttempted = true) }

@Preview(name = "C · empty submit · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_C390() { ProfileEmailScreen(previewAttempted = true) }

@Preview(name = "C · empty submit · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE_C430() { ProfileEmailScreen(previewAttempted = true) }

@Preview(name = "A · consent on · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_consent() { ProfileEmailScreen(value = "leo@hey.com", consent = true) }
