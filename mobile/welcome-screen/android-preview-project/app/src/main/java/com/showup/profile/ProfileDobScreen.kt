/*
 * ProfileDobScreen.kt
 * ShowUp · Profile creation 04 — Date of birth (SHOWUP-154)
 *
 * THE ONLY SCREEN IN THE FLOW THAT WRITES A VALUE THE USER CANNOT CHANGE
 *
 * Age is locked after this step. That is why the confirmation is INLINE and leads with the
 * computed age rather than the typed date: a user re-reading "03/22/1998" checks their own input,
 * a user reading "28" checks the thing the app will actually use. Off-by-one-year typos are caught
 * here or never.
 *
 * THE RESERVED REGION IS 84 AND MUST NOT SHRINK
 *
 * 84 is the height of the age card, the tallest of the four bodies. Holding it in every state is
 * what keeps the visibility band, the CTA and the keyboard at the identical Y whichever body is
 * showing. Dropping it to fit a failure state is the one change that would undo this screen.
 *
 * TWO RULES THAT CAME FROM THE PRODUCT SIDE, NOT THE TICKET
 *
 * Age is computed in UTC and the field order follows the device locale. Both override what the
 * ticket says; both are explained in `DateOfBirth.kt`, which is also where they are tested.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.CheckGlyph
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FloatingField
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.LilacStops
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.minTapTarget
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle
import com.showup.designsystem.rememberMotion
import com.showup.tutorial.NextButton
import com.showup.tutorial.StepProgress
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/** Final strings, quoted from the ticket. The mono chip is generated from the locale's order. */
object DobCopy {
    const val SECTION = "The basics"
    const val SUB = "Be honest — it helps us find the right matches. You must be 18 or older."
    const val LABEL = "Date of birth"
    const val CTA = "Continue"
    const val LOCK_NOTE = "Locked after this step."
    const val EDIT = "Edit"
    const val VISIBILITY = "Don't display on my profile"

    /** "age" sits inline at 700 — what is published is the age, not the date. */
    const val HELPER_LEAD = "Your "
    const val HELPER_BOLD = "age"
    const val HELPER_TAIL = " appears on your profile — not your date of birth."

    const val UNDER_18 =
        "You must be at least 18 to use Show Up. Please check the date you entered."

    fun ageQuestion(age: Int) = "You're $age. Look right?"

    /** Both format errors name the order the user is actually being asked for. */
    fun incomplete(order: DateOrder) =
        "Enter your full date of birth to continue — ${order.pattern}."

    fun invalid(order: DateOrder) =
        "That date doesn't exist. Please check the format — ${order.pattern}."
}

/**
 * @param value the display string, e.g. "03/22/1998". Slashes are inserted, never typed.
 * @param attempted whether Continue has been pressed. The incomplete error appears only after a
 *   press — never mid-typing — which is why it is a separate input from [value].
 */
@Composable
fun ProfileDobScreen(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    order: DateOrder = DateOrder.MonthFirst,
    hideAge: Boolean = false,
    onHideAgeChange: (Boolean) -> Unit = {},
    attempted: Boolean = false,
    onContinue: (DobResult.Valid) -> Unit = {},
    onRefused: (DobResult) -> Unit = {},
    onEdit: () -> Unit = {},
    onBack: () -> Unit = {},
    /** Previews seed this; the flow drives it from its own counter. */
    previewShakeKey: Int = 0,
) {
    val motion = rememberMotion()
    var shakeKey by remember { mutableIntStateOf(previewShakeKey) }

    val parsed = parseDob(value, order)
    val underAge = parsed is DobResult.Valid && parsed.age < MINIMUM_AGE
    val isError = when {
        parsed is DobResult.Impossible -> true
        underAge -> true
        attempted && parsed is DobResult.Incomplete -> true
        else -> false
    }
    val isValid = parsed is DobResult.Valid && !underAge

    BackHandler(enabled = true) { onBack() }

    val submit: () -> Unit = {
        if (parsed is DobResult.Valid && !underAge) {
            onContinue(parsed)
        } else {
            shakeKey += 1
            onRefused(parsed)
        }
    }

    BasicsScaffold(
        title = DobCopy.SECTION,
        leading = HeaderLeading.Back,
        onBack = onBack,
        cta = {
            Column(Modifier.fillMaxWidth()) {
                ProfileVisibilityRow(hidden = hideAge, onToggle = { onHideAgeChange(!hideAge) })
                Spacer(Modifier.height(26.dp))
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Orange, not sunset — a routine step. Same silhouette as name and email.
                    NextButton(DobCopy.CTA, submit, arrowSize = 22.dp)
                }
            }
        },
        content = {
            // 3 of 3 — the only screen in the group where every segment is filled.
            StepProgress(steps = 3, current = BasicsStep.Dob.progressSegment)
            Spacer(Modifier.height(18.dp))

            WashHeadline(
                parts = listOf("What's your " to false, "date of birth" to true, "?" to false),
                fontSize = 32.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                DobCopy.SUB,
                modifier = Modifier.widthIn(max = 320.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, lineHeight = 20.3.sp,
            )
            Spacer(Modifier.height(18.dp))

            FloatingField(
                value = value,
                onValueChange = { raw ->
                    // Digits only, re-formatted every time. Backspace therefore deletes a DIGIT
                    // and the slashes look after themselves — the user never deletes a slash.
                    onValueChange(formatDob(dobDigits(raw)))
                },
                label = DobCopy.LABEL,
                placeholder = order.pattern,
                modifier = Modifier.shakeOnce(shakeKey, isError && motion.enabled),
                valid = isValid,
                error = isError,
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go,
                onSubmit = submit,
            )

            // ⑨ reserved at 84 — the age card's height, the tallest of the four bodies. heightIn
            // rather than height so a two-line error at 320 can still grow rather than clip; the
            // FLOOR is what holds the band and the CTA still.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 84.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                when {
                    parsed is DobResult.Impossible -> InlineErrorCard(DobCopy.invalid(order))
                    underAge -> InlineErrorCard(DobCopy.UNDER_18)
                    attempted && parsed is DobResult.Incomplete ->
                        InlineErrorCard(DobCopy.incomplete(order))
                    parsed is DobResult.Valid -> AgeCard(age = parsed.age, onEdit = onEdit)
                    else -> DobHelper()
                }
            }
        },
    )
}

/** State A's helper. The reason the field is not scary: it says what is published and what is not. */
@Composable
private fun DobHelper() {
    Text(
        buildAnnotatedString {
            append(DobCopy.HELPER_LEAD)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(DobCopy.HELPER_BOLD) }
            append(DobCopy.HELPER_TAIL)
        },
        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.2.sp,
    )
}

/**
 * State B. The confirmation, inline.
 *
 * **No age numeral appears in state D**, which is why this is only reachable from a date that
 * already passed the gate — printing "You're 15" back at a 15-year-old adds nothing they do not
 * know and hands a rejection a number.
 */
@Composable
private fun AgeCard(age: Int, onEdit: () -> Unit) {
    val motion = rememberMotion()
    // su-confirm-in: opacity 0->1 with a 6dp rise, once, on entering the state. Keyed on the age
    // so correcting the year re-plays it rather than sliding a new number into an old card.
    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (motion.enabled) Motion.CONFIRM else 0),
        label = "confirmIn",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(enter)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(LilacStops.map { it.second }))
            .border(1.dp, Purple.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = DobCopy.ageQuestion(age) },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            age.toString(),
            color = Purple, fontFamily = Lora, fontWeight = FontWeight.Bold,
            fontSize = 44.sp, lineHeight = 44.sp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                DobCopy.ageQuestion(age),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, lineHeight = 18.2.sp,
            )
            Text(
                DobCopy.LOCK_NOTE,
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp, lineHeight = 16.25.sp,
            )
        }
        Text(
            DobCopy.EDIT,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onEdit)
                .minTapTarget(),
            color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, textDecoration = TextDecoration.Underline,
        )
    }
}

/**
 * The visibility band (callout ⑪).
 *
 * NOT [MarketingOptIn], and the five differences are why: this is transparent under a hairline
 * divider rather than a raised card, the box is on the LEFT, the hit area is capped to the switch
 * and its label instead of the full row, it carries an eye-off mark, and its label is 700/14
 * against that row's 500/13. Forcing them together would need a flag for each.
 *
 * **Checked means the age is not DISPLAYED. It is still used for matching.** The choice hides a
 * value; it never excludes the user. That distinction is the whole reason this control exists on
 * this screen rather than in the later detail steps.
 */
@Composable
internal fun ProfileVisibilityRow(
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
        Row(
            Modifier
                // Capped to the switch and its label, NOT the full row: an edge-to-edge target
                // above the CTA invites a mis-tap on the control the user actually meant.
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(vertical = 10.dp)
                .semantics {
                    stateDescription = if (hidden) "Hidden" else "Shown"
                    contentDescription = DobCopy.VISIBILITY
                },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hidden) Purple else Elevated)
                    .border(1.5.dp, if (hidden) Purple else Border, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (hidden) CheckGlyph(size = 13.dp, color = Elevated, strokeWidth = 3.dp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    BrandIcon.EyeOff,
                    size = 14.dp,
                    tint = if (hidden) Purple else Subtle,
                    strokeWidth = 2.1.dp,
                )
                Text(
                    DobCopy.VISIBILITY,
                    color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, lineHeight = 18.2.sp,
                )
            }
        }
    }
}

// ── previews: four states x three frames ────────────────────────────────────

@Preview(name = "A · empty · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PD_A375() { ProfileDobScreen() }

@Preview(name = "A · empty · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_A390() { ProfileDobScreen() }

@Preview(name = "A · empty · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PD_A430() { ProfileDobScreen() }

@Preview(name = "B · confirm · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_B390() { ProfileDobScreen(value = "03/22/1998") }

@Preview(name = "B · confirm · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PD_B375() { ProfileDobScreen(value = "03/22/1998") }

@Preview(name = "C · impossible · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_C390() { ProfileDobScreen(value = "02/30/1990") }

@Preview(name = "D · under 18 · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_D390() { ProfileDobScreen(value = "05/19/2015") }

@Preview(name = "A + press · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_E390() { ProfileDobScreen(value = "03/22", attempted = true) }

@Preview(name = "B · age hidden · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_F390() { ProfileDobScreen(value = "03/22/1998", hideAge = true) }

@Preview(name = "B · day-first locale · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PD_G390() {
    ProfileDobScreen(value = "22/03/1998", order = DateOrder.DayFirst)
}
