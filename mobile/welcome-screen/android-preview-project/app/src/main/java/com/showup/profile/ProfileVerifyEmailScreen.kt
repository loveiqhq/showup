/*
 * ProfileVerifyEmailScreen.kt
 * ShowUp · Profile creation 03 — Verify email (SHOWUP-153)
 *
 * STILL STEP 2 OF 3
 *
 * The progress bar reads current=2, identical to the email screen's. The user is on the email step
 * until the code is confirmed; verification is not a fourth thing they did. Do not advance it here.
 *
 * THE ENTERED DIGITS SURVIVE A FAILURE
 *
 * The ticket calls this the one behaviour to review if only one gets reviewed, and it is right:
 * the user almost always mistyped a single digit, and clearing the row makes them re-read the whole
 * code out of their inbox. Backspace then corrects from the right, as normal.
 *
 * BOTTOM-SLACK, NOT BOTTOM-ANCHORED
 *
 * Unlike steps 1 and 2, the single flexible spacer sits at the BOTTOM of the column rather than
 * above the CTA. So the CTA sits directly under the slots it belongs to instead of floating at the
 * base of the screen. `BasicsScaffold` anchors its CTA, which is why this screen builds its own
 * column instead of using the scaffold's cta slot.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.CodeSlotRow
import com.showup.designsystem.Fg
import com.showup.designsystem.FieldContent
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.Purple
import com.showup.designsystem.rememberMotion
import com.showup.designsystem.Subtle
import com.showup.designsystem.autofill
import com.showup.designsystem.minTapTarget
import com.showup.tutorial.StepProgress
import com.showup.welcome.WashHeadline

/** Final strings. Every one is quoted from the ticket; `verify-profile.py` checks them. */
object VerifyEmailCopy {
    const val SECTION = "The basics"
    const val SUB_LEAD = "We sent a 6-digit code to "
    const val CTA = "Verify code"
    const val RESEND_QUESTION = "Didn’t receive a code?"
    const val RESEND_AVAILABLE = "Send a new code"
    const val CHANGE_ADDRESS = "Change email address"
    const val SLOT_ROW_LABEL = "Enter your 6-digit verification code"

    /** Named both recoveries, because at that moment the user does not know which they need. */
    const val MISMATCH = "That code doesn’t match. Check your inbox or request a new one."

    /** Approved by the product side, 10 September 2026. Points at resend only — re-reading the
     *  inbox cannot help a code that has died of age. */
    const val EXPIRED = "That code has expired. Send a new one to try again."

    /**
     * PROPOSED — not yet approved.
     *
     * No ticket has ever specified this string; 153 lists the state as undesigned. Written to the
     * group's rules and kept to one line so the reserved region holds. Mirrors the phone screen's
     * `VerifyCopy.LOCKED_OUT`, which is the same state on the same kind of screen.
     */
    const val LOCKED_OUT = "Too many tries. Send a new code."

    /** The countdown. Minutes are computed — the cooldown is 60, so the first tick is 1:00. */
    fun resendIn(seconds: Int) = "Send a new code in %d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * @param email echoed back verbatim, never truncated — the whole point of the sub copy is that the
 *   user can catch their own typo before waiting for mail that will never arrive.
 * @param state which failure, if any. See [verifyState] for the precedence.
 */
@Composable
fun ProfileVerifyEmailScreen(
    email: String = "leo@hey.com",
    digits: String = "",
    onDigitsChange: (String) -> Unit = {},
    state: VerifyState = VerifyState.Calm,
    /** Bumped by the host on every refused submit, so a second failure shakes again. */
    shakeKey: Int = 0,
    cooldownSeconds: Int = 60,
    onVerify: () -> Unit = {},
    onResend: () -> Unit = {},
    onChangeEmail: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val motion = rememberMotion()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Back and "Change email address" are the same journey: return to the email step with the
    // address intact. The system gesture must not do something different from the chevron.
    BackHandler(enabled = true) { onBack() }

    BasicsScaffold(
        title = VerifyEmailCopy.SECTION,
        leading = HeaderLeading.Back,
        onBack = onBack,
        cta = {},
        content = {
            // current = 2, IDENTICAL to the email screen. The bar does not advance here.
            StepProgress(steps = 3, current = BasicsStep.EmailVerify.progressSegment)
            Spacer(Modifier.height(18.dp))

            // "email" is the single italic em carrying the orange wash. Ends in a FULL STOP --
            // the only headline in the group that does, because the user has nothing to decide
            // here, only something to do.
            WashHeadline(
                parts = listOf("Please verify your " to false, "email" to true, "." to false),
                fontSize = 32.sp,
            )
            Spacer(Modifier.height(8.dp))

            Text(
                buildAnnotatedString {
                    append(VerifyEmailCopy.SUB_LEAD)
                    // Manrope 700 in --liq-fg, never truncated, wraps if long. This is the line
                    // that makes "I never got the code" answerable by the user themselves.
                    withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append(email) }
                    append(".")
                },
                modifier = Modifier.widthIn(max = 320.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, lineHeight = 20.3.sp,
            )

            Spacer(Modifier.height(22.dp))

            val isError = state != VerifyState.Calm
            // One field behind six slots, so paste and one-time-code autofill work without six
            // focus targets handing off to each other. Typed and autofilled codes take the same
            // path -- a second copy of this filter is a second chance to forget the digit strip.
            val accept: (String) -> Unit = { raw -> onDigitsChange(raw.filter { it.isDigit() }.take(6)) }
            BasicTextField(
                value = digits,
                onValueChange = accept,
                modifier = Modifier
                    .fillMaxWidth()
                    .autofill(FieldContent.SmsCode, accept)
                    .focusRequester(focus)
                    // Re-keyed per failure, so a second wrong code shakes again.
                    .shakeOnce(shakeKey, isError && motion.enabled)
                    .semantics { contentDescription = VerifyEmailCopy.SLOT_ROW_LABEL },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSubmitCode(digits, state)) onVerify() },
                ),
                singleLine = true,
                // The slots paint the digits, so the field draws nothing of its own.
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                decorationBox = {
                    CodeSlotRow(
                        digits = digits,
                        error = isError,
                        // 153's sheet, unlike 143's: a 4dp ring and a caret that blinks.
                        halo = true,
                        caretBlinks = true,
                    )
                },
            )

            // ⑭ reserved at 30 so the CTA and BOTH secondary rows sit at the same Y in either
            // state. heightIn, not height: the mismatch string wraps to two lines at 320 and the
            // card is then taller than 30 -- the floor is what stops the CALM state collapsing.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 2.dp)
                    .heightIn(min = 30.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                val message = when (state) {
                    VerifyState.Calm -> null
                    VerifyState.Mismatch -> VerifyEmailCopy.MISMATCH
                    VerifyState.Expired -> VerifyEmailCopy.EXPIRED
                    VerifyState.LockedOut -> VerifyEmailCopy.LOCKED_OUT
                }
                if (message != null) InlineErrorCard(message)
            }

            Spacer(Modifier.height(14.dp))
            // The group's ONE disabled CTA, and a settled exception rather than drift: a partial
            // code has nothing to validate, and the six boxes already say "six digits".
            PrimaryButton(
                VerifyEmailCopy.CTA,
                onVerify,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmitCode(digits, state),
            )

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // One baseline row: the question and the timer/link sit together.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        VerifyEmailCopy.RESEND_QUESTION,
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, lineHeight = 18.2.sp,
                    )
                    if (canResend(cooldownSeconds, state)) {
                        Text(
                            VerifyEmailCopy.RESEND_AVAILABLE,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClick = onResend)
                                .minTapTarget(),
                            color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, lineHeight = 18.2.sp,
                            textDecoration = TextDecoration.Underline,
                        )
                    } else {
                        Text(
                            // tabular-nums so the countdown does not jitter as digits change width
                            VerifyEmailCopy.resendIn(cooldownSeconds),
                            color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, lineHeight = 18.2.sp,
                        )
                    }
                }
                Text(
                    VerifyEmailCopy.CHANGE_ADDRESS,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onChangeEmail)
                        .minTapTarget(),
                    color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp, lineHeight = 17.55.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }

            // No spacer here on purpose. BasicsScaffold already puts its single weight(1f)
            // between content and the cta slot, and this screen leaves that slot EMPTY -- so the
            // scaffold's own spacer lands at the bottom of the column, which is exactly the
            // bottom-slack layout the ticket asks for. Adding one here would make two.
        },
    )
}

// ── previews: every state at the ends of the matrix ─────────────────────────
//
// A and the three failures seed the SAME six digits on purpose: the states should read as one row
// failing rather than four screens. Compare the CTA's Y between them and expect it not to move.

@Preview(name = "A · arrival · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PV_A375() { ProfileVerifyEmailScreen() }

@Preview(name = "A · arrival · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PV_A390() { ProfileVerifyEmailScreen() }

@Preview(name = "A · arrival · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PV_A430() { ProfileVerifyEmailScreen() }

@Preview(name = "A · typed · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PV_typed() { ProfileVerifyEmailScreen(digits = "4821") }

@Preview(name = "B · mismatch · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PV_B390() {
    ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Mismatch, cooldownSeconds = 0)
}

@Preview(name = "B · mismatch · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PV_B375() {
    ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Mismatch, cooldownSeconds = 0)
}

@Preview(name = "C · expired · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PV_expired() {
    ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.Expired, cooldownSeconds = 0)
}

@Preview(name = "D · locked out · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PV_locked() {
    ProfileVerifyEmailScreen(digits = "482170", state = VerifyState.LockedOut, cooldownSeconds = 0)
}

@Preview(name = "A · long address · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PV_long() {
    // Never truncated, wraps to two lines. The address is the whole point of the sub copy.
    ProfileVerifyEmailScreen(email = "leonardo.buonarroti@a-very-long-domain.example")
}
