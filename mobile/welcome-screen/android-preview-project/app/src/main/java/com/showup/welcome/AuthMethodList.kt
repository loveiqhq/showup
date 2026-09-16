/*
 * AuthMethodList.kt
 * ShowUp · the shared sign-in method list (SHOWUP-144 and SHOWUP-145)
 *
 * Built once and used by both screens, which is what both tickets require:
 *   "The method list uses the same component and the same ordered data source as welcome 04."
 *   "This screen adds phone. Welcome 04 omits it."
 *
 * Connect (144) offers Apple, Google and Facebook — the user has just verified their phone, so
 * re-offering it there would be a no-op. Welcome back (145) adds phone, because it is a genuine
 * way back in.
 *
 * Provider styling follows each provider's own published spec, not our gradient. The reference file
 * carries the gradient treatment and says so explicitly:
 *   "Provider button styling follows Apple's / Google's / Facebook's own published specs at equal
 *    prominence — NOT the gradient treatment in this file. Decided 26 Aug 2026."
 * The gradient is reserved for our own CTAs.
 */
package com.showup.welcome

import androidx.compose.runtime.getValue
import com.showup.designsystem.Subtle

import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant

import com.showup.designsystem.IconSizes
import com.showup.designsystem.Motion
import com.showup.designsystem.Radius
import com.showup.designsystem.Spacing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerGlyph
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Raised
import com.showup.designsystem.rememberMotion

/**
 * Provider button titles are taken from each provider's own permitted list rather than from the
 * ticket's error / in-flight strings, which none of the three allow. See the long note in
 * [MethodButton]. Set to false to restore the ticket's literal wording.
 */
const val PROVIDER_COMPLIANT_LABELS = true

/** Canonical order. Connect drops phone; Welcome back keeps it. Never reordered beyond the lift. */
val CONNECT_METHODS = listOf(AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook)
val LOGIN_METHODS = listOf(AuthMethod.Phone, AuthMethod.Apple, AuthMethod.Google, AuthMethod.Facebook)

/**
 * One row per method: the button label, the mark, the hint used on Welcome back, and the short name
 * that gets interpolated into "Connecting to X…" and "Try X again".
 */
data class MethodSpec(
    val label: String,
    val short: String,
    val icon: BrandIcon,
    val hint: String,
)

fun methodSpec(m: AuthMethod): MethodSpec = when (m) {
    AuthMethod.Phone -> MethodSpec("Continue with phone number", "phone", BrandIcon.Phone, "Last login was via phone")
    AuthMethod.Apple -> MethodSpec("Continue with Apple", "Apple", BrandIcon.Apple, "Last login was via Apple")
    AuthMethod.Google -> MethodSpec("Continue with Google", "Google", BrandIcon.Google, "Last login was via Google")
    AuthMethod.Facebook -> MethodSpec("Continue with Facebook", "Facebook", BrandIcon.Facebook, "Last login was via Facebook")
    // Never rendered: unknown is a lastUsed value, not a method someone can pick.
    AuthMethod.Unknown -> MethodSpec("Continue with phone number", "phone", BrandIcon.Phone, "")
}

/**
 * Phone is ours to style; the other three are each governed by their provider.
 *
 * This is where the brand decision becomes visible. SHOWUP-145 asks for "one sunset, three ghost",
 * and the note that overrules it says the gradient is "reserved for our own CTAs". Phone is our own
 * method, so it keeps the sunset pill **when it is the promoted one** and drops to ghost otherwise.
 * A provider never takes the gradient in any position. The consequence, spelled out in the zip's
 * ticket 02: the stack has a gradient primary in the phone case and none in the other three, and
 * the hint row is what carries the suggestion in all four.
 */
fun providerVariant(m: AuthMethod, isPrimary: Boolean = false): PrimaryButtonVariant = when (m) {
    AuthMethod.Apple -> PrimaryButtonVariant.Apple
    AuthMethod.Google -> PrimaryButtonVariant.Google
    AuthMethod.Facebook -> PrimaryButtonVariant.Facebook
    AuthMethod.Phone, AuthMethod.Unknown -> if (isPrimary) PrimaryButtonVariant.Sunset else PrimaryButtonVariant.Ghost
}

/** The Google G ignores this — it is drawn in its own four colours. */
fun providerTint(m: AuthMethod, isPrimary: Boolean = false): Color = when (m) {
    AuthMethod.Apple, AuthMethod.Facebook -> Color.White
    AuthMethod.Google -> Fg
    // Phone rides our own pill, so its mark follows the pill rather than a provider rule.
    AuthMethod.Phone, AuthMethod.Unknown -> if (isPrimary) Color.White else Fg
}

/**
 * A provider whose credential setup is not finished is **hidden, not disabled** — SHOWUP-145 states
 * it outright, and the remaining methods still fill the stack without a gap.
 *
 * That is the right call rather than a greyed-out button: a disabled provider invites the tap, then
 * refuses it. Facebook in particular may not ship in v1 at all — the ticket lists that as an open
 * decision — so the layout has to work with two, three or four buttons, not only the four the mock
 * happens to draw.
 */
fun availableMethods(all: List<AuthMethod>, configured: Set<AuthMethod>): List<AuthMethod> =
    all.filter { it in configured }

/**
 * The list. One component, variant-driven.
 *
 * @param loading  the provider currently in flight, if any. Its button shows a spinner and
 *                 "Connecting to X…"; the others dim but the skip row never does.
 * @param errorFor the provider that just failed. It stays in first position — the ticket requires
 *                 the list not to shuffle between in-flight, cancel and error.
 * @param suggested which provider sits first. Set to the tapped one so it holds its place.
 * @param onSkip    null on Welcome back, which has no skip.
 */
@Composable
fun AuthMethodList(
    methods: List<AuthMethod>,
    onSelect: (AuthMethod) -> Unit,
    modifier: Modifier = Modifier,
    loading: AuthMethod? = null,
    errorFor: AuthMethod? = null,
    errorMessage: String? = null,
    suggested: AuthMethod? = null,
    notice: (@Composable () -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    if (methods.isEmpty()) return
    val primary = suggested?.takeIf { it in methods } ?: loading?.takeIf { it in methods } ?: methods.first()
    val rest = methods.filter { it != primary }

    Column(modifier) {
        // The banner is INSERTED ABOVE the list, never overlaid, so the buttons do not move
        // between idle, in-flight, cancelled and error.
        notice?.invoke()
        if (errorFor != null && errorMessage != null) ErrorBanner(errorMessage)

        Column(
            Modifier.padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            MethodButton(primary, onSelect, loading, errorFor == primary, loading != null, isPrimary = true)
            rest.forEach { m ->
                MethodButton(m, onSelect, loading, errorFor == m, loading != null, isPrimary = false)
            }
            if (onSkip != null) SkipRow(onSkip, anyLoading = loading != null)
        }
    }
}

@Composable
private fun MethodButton(
    method: AuthMethod,
    onSelect: (AuthMethod) -> Unit,
    loading: AuthMethod?,
    isErrored: Boolean,
    anyLoading: Boolean,
    isPrimary: Boolean,
) {
    val spec = methodSpec(method)
    val isLoading = loading == method
    val variant = providerVariant(method, isPrimary)

    // The label. Two of the ticket's strings cannot be used on a provider button, and this is the
    // one place the conflict shows up, so the reasoning lives here.
    //
    // SHOWUP-144 asks for "Try Apple again" in the error state and "Connecting to Apple…" in flight.
    // Apple, Google and Meta each publish a CLOSED list of permitted button titles — Apple allows
    // only "Sign in with Apple" / "Sign up with Apple" / "Continue with Apple"; Google and Meta are
    // equally restrictive. Neither replacement string is on any of those lists.
    //
    // The ticket settles this against itself twice: "Compliance wins over our visual system", and
    // "Verify against the providers' guidelines as published at build time, not against this
    // ticket." So the permitted title stays put in both states, and the two things the ticket wants
    // said are said where they are allowed to be said:
    //   in flight — the spinner replaces the mark, and the button is disabled
    //   after a failure — the banner above the list carries "try again"
    // The instruction it contradicts ("loses its gradient") was written for the gradient design
    // that the same ticket supersedes; a provider button has no gradient left to lose.
    //
    // Flagged for the boss on 27 Aug 2026. Flip PROVIDER_COMPLIANT_LABELS to restore the
    // ticket's literal strings if the providers' guidelines are re-read and disagree.
    val label = when {
        isLoading && !PROVIDER_COMPLIANT_LABELS -> "Connecting to ${spec.short}…"
        isErrored && !PROVIDER_COMPLIANT_LABELS -> "Try ${spec.short} again"
        else -> spec.label
    }
    PrimaryButton(
        label, { onSelect(method) },
        // Siblings dim to 0.45 while one is in flight; the tapped one stays at full opacity.
        modifier = Modifier.alpha(if (anyLoading && !isLoading) 0.45f else 1f),
        variant = variant,
        // Disabled is one of the states every provider permits, so it carries the in-flight signal.
        enabled = !anyLoading,
        leading = {
            if (isLoading) Spinner(tint = providerTint(method, isPrimary))
            else Icon(spec.icon, 18.dp, tint = providerTint(method, isPrimary))
        },
    )

}

/**
 * The escape hatch. Deliberately a different shape and weight from the three connect buttons so it
 * never reads as a fourth provider, and **never disabled or dimmed in any state** — SHOWUP-144 says
 * so four separate times, because it is the way out of a provider request that has hung.
 */
@Composable
private fun SkipRow(onSkip: () -> Unit, anyLoading: Boolean) {
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .height(52.dp)
            .clip(shape)
            .background(Elevated)
            // Border (ink 12%), as the design system draws it. It was briefly Subtle (ink 46%)
            // for WCAG 1.4.11, which wants 3:1 for the boundary that identifies a control -- see
            // audit/AUDIT-connect-144-145.md finding 7. Reverted on request: this is the designed
            // look, the label and arrow carry the control's identity at 5.03:1, and the deviation
            // is recorded rather than made silently.
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    color = Border,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx() - w / 2f),
                    style = Stroke(
                        width = w,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onSkip)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Skip and continue to profile",
            // Darkens rather than dims while a provider is in flight: it is the only live control
            // on the screen at that moment, so it gets more prominent, not less.
            color = if (anyLoading) Fg else Muted,
            fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        )
        Icon(BrandIcon.ArrowRight, 17.dp, tint = if (anyLoading) Fg else Muted, strokeWidth = 2.2.dp)
    }
}

/**
 * Danger banner — the failure states. Sits above the list; the buttons do not move.
 *
 * DELIBERATELY NOT [InlineErrorCard], though the two share a palette.
 *
 * The inline card is a field's error: radius 12, 14/10 padding, an 18 glyph. This is a banner above
 * a list: radius 14, 14/12 padding, a 20 glyph, full width with its own bottom margin. Three of its
 * four geometry values differ, so unifying them would mean three parameters to express one shape --
 * the "everything component" this design system keeps declining to build. The glyph is shared
 * because the glyph genuinely is the same.
 */
@Composable
fun ErrorBanner(message: String) {
    val shape = RoundedCornerShape(Radius.control)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(shape)
            .background(Danger.copy(alpha = 0.07f))
            .border(1.dp, Danger.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // The glyph is shared; the BANNER is not. See the note on ErrorBanner above.
        Box(Modifier.padding(top = 1.dp)) {
            DangerGlyph(size = IconSizes.sm, glyphSize = 13.dp)
        }
        Text(
            message, color = DangerFg, fontFamily = Manrope,
            fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.9.sp,
        )
    }
}

/**
 * Cancelled notice — neutral, never danger.
 *
 * SHOWUP-144: "Cancel is neutral, with --liq-bg-raised, no danger colour, and no shake." Closing a
 * provider sheet is a choice, not a failure, and colouring it red would say otherwise.
 */
@Composable
fun CancelledNotice(provider: AuthMethod) {
    val shape = RoundedCornerShape(Radius.control)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xl)
            .clip(shape)
            .background(Raised)
            .border(1.dp, BorderSoft, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(Modifier.size(IconSizes.sm).background(Fg.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(BrandIcon.Close, 12.dp, tint = Fg, strokeWidth = 2.4.dp)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Fg)) { append("Sign-in cancelled.") }
                append(" ")
                withStyle(SpanStyle(color = Muted)) {
                    append("You closed the ${methodSpec(provider).short} sheet before we could finish.")
                }
            },
            color = Fg, fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = 18.9.sp,
        )
    }
}

/** 18dp ring, 2dp stroke, one turn every 0.9s. Held still when the device asks for no motion. */
@Composable
fun Spinner(tint: Color = Color.White, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val motion = rememberMotion()
    val angle by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = if (motion.enabled) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE_SLOW, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(Modifier.size(size).rotate(angle)) {
        val w = 2.dp.toPx()
        drawCircle(tint.copy(alpha = 0.30f), radius = (this.size.minDimension - w) / 2f, style = Stroke(w))
        drawArc(
            color = tint, startAngle = -90f, sweepAngle = 100f, useCenter = false,
            style = Stroke(w, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f),
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
        )
    }
}
