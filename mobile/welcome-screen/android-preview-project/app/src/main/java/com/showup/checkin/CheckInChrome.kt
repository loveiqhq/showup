/*
 * CheckInChrome.kt
 * ShowUp · what every Check-In screen shares (SHOWUP-178 to SHOWUP-183)
 *
 * BUILT ONCE, CONSUMED BY ALL -- "a second copy of any of these is a bug" (epic):
 *   · CheckInCloseButton, CheckInArrow, NumberedRow ............. Check-In 01
 *   · the step shell, CheckInFooter (+ its toast) ................ Check-In 02
 *   · CoachNote .................................................. Check-In 03
 *   · CheckInFooter's `loading` and `error`, CheckInSpinner ...... Check-In 06 (a variant, not a fork)
 *
 * Numbers from `design_handoff_showup/checkin/screen-checkin-*-reference.jsx`, which win on numbers.
 * The error card is the profile flow's shared [InlineErrorCard] -- story 06: "reuse it if the build
 * already has it shared".
 */
package com.showup.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.CheckInToastFill
import com.showup.designsystem.CheckInToastShadow
import com.showup.designsystem.CoachLilacFill
import com.showup.designsystem.CoachLilacInk
import com.showup.designsystem.CoachWarmFill
import com.showup.designsystem.CoachWarmInk
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Spacing
import com.showup.designsystem.StepProgress
import com.showup.designsystem.boxShadow
import com.showup.designsystem.rememberMotion
import com.showup.profile.AppHeader
import com.showup.profile.HeaderLeading
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop

/** Fit-harness anchors. */
internal const val CHECK_IN_CTA_TAG = "check-in-cta"
internal const val CHECK_IN_BODY_TAG = "check-in-body"
internal const val CHECK_IN_HEADLINE_TAG = "check-in-headline"
internal const val CHECK_IN_FOOTER_TAG = "check-in-footer"
internal const val CHECK_IN_SCROLL_TAG = "check-in-scroll"

/**
 * `CheckInArrow` -- every Check-In CTA's trailing arrow: the design system's own `arrow-right`, 18 at
 * stroke 2, "the same SVG as Embrace's CTA". Never a `→` text glyph (Check-In 01, ⑪).
 */
@Composable
fun CheckInArrow(tint: Color = Color.White) {
    Icon(BrandIcon.ArrowRight, 18.dp, tint = tint, strokeWidth = 2f)
}

/**
 * `CheckInCloseButton` -- the ✕ on every Check-In screen: `x` 22 at stroke 2 in a 36 slot, leading on
 * the intro and trailing on the steps. Answers at the 44 floor like the header's back, overflowing
 * its 36 symmetrically so nothing beside it moves.
 */
@Composable
fun CheckInCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(ComponentSizes.minTapTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = CheckInCopy.CLOSE_LABEL },
            contentAlignment = Alignment.Center,
        ) {
            Icon(BrandIcon.Close, 22.dp, tint = Fg, strokeWidth = 2f)
        }
    }
}

/**
 * `NumberedRow` -- the intro's list row: a 26 orange disc with a white numeral, an element and not a
 * list-style, beside Manrope 600 / 15 / 1.4.
 */
@Composable
fun NumberedRow(n: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The disc is a fixed 26 element and `aria-hidden` in the reference: the numeral is drawn at
        // 13 whatever the system font -- a 2x numeral does not fit a 26 disc -- and TalkBack reads
        // the row's text alone.
        val numeral = with(LocalDensity.current) { 13.dp.toSp() }
        Box(
            Modifier
                .size(26.dp)
                // `0 2px 6px rgba(254,104,57,.28)` -- a tint of the disc's own orange.
                .boxShadow(Orange, 13.dp, Orange.copy(alpha = 0.28f), offsetY = 2.dp, blur = 6.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$n",
                color = Color.White,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = numeral,
                lineHeight = numeral,
            )
        }
        Text(
            text,
            color = Fg,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = (15f * 1.4f).sp,
        )
    }
}

/** A step headline: Lora 700 / 30 / 1.15 / -0.015em, one em with the wash. "30 is the step size." */
@Composable
fun CheckInStepHeadline(parts: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    WashHeadline(
        parts = parts,
        fontSize = 30.sp,
        modifier = modifier
            .testTag(CHECK_IN_HEADLINE_TAG)
            .semantics { heading() },
        lineHeight = (30f * 1.15f).sp,
        letterSpacing = (-0.015).em,
        // `textWrap: 'pretty'` on every step headline, and the CSS line box: see WashHeadline.
        pretty = true,
        cssLineBox = true,
    )
}

/**
 * `CoachNote` -- the guidance box. Warm on Motivation, Vibe and Filters; lilac on Uplift (and the
 * colours How it works' panel shares). 10 / 14 padding, radius 12, Manrope 500 / 12.5 / 1.45.
 */
@Composable
fun CoachNote(text: String, warm: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (warm) CoachWarmFill else CoachLilacFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (warm) CoachWarmInk else CoachLilacInk,
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = (12.5f * 1.45f).sp,
    )
}

/**
 * The step shell -- built by Check-In 02, consumed by 03 to 06 with content props only:
 * AppHeader 52 (`Check-in`, back leading, ✕ trailing) → content (flex 1, padding 4 / 24 / 0:
 * StepProgress `steps={6}` mb 22 → [body] flex 1 → [footer]).
 *
 * Status bar and gesture bar are the platform's (`safeDrawing`); the keyboard pushes the footer up
 * (`imePadding`), so `Find my date` sits directly above it. Android back is the chevron.
 *
 * @param ambient Uplift's backdrop -- the one step with it, "the flow's moment to shine".
 */
@Composable
fun CheckInStepShell(
    step: CheckInStep,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(modifier.fillMaxSize().background(Cream)) {
        if (ambient) {
            // Orange .30 520² top −15% right −25% · violet .26 600² bottom −20% left −30% · peach wash.
            WelcomeBackdrop(placement = OrbPlacement.Startup, orangeAlpha = 0.30f, violetAlpha = 0.26f)
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            AppHeader(
                title = CheckInCopy.HEADER,
                leading = HeaderLeading.Back,
                onBack = onBack,
                trailing = { CheckInCloseButton(onClose) },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xs),
            ) {
                StepProgress(steps = CHECK_IN_STEPS_TOTAL, current = step.progress ?: 0)
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag(CHECK_IN_BODY_TAG),
                ) { body() }
                footer()
            }
        }
    }
}

/**
 * `CheckInFooter` -- the full-width sunset CTA with [CheckInArrow], and above it the toast or the
 * save-failed card. Margin-bottom 22.
 *
 * DIMMED IS NOT DISABLED. A step that cannot continue dims the button (opacity .45, saturate .4,
 * 180 ms) and still takes the tap, which raises the toast naming what is missing -- "never a dead
 * button". [PrimaryButton]'s own `enabled = false` would refuse the tap, so the dim is drawn here
 * around an enabled button.
 *
 * @param toast the toast's text, or null. [toastTick] restarts its entry on a repeat tap.
 * @param loading Check-In 06's saving state: the spinner replaces the arrow, the label stays, the
 *   button is inert.
 * @param error Check-In 06's save-failed card, shown until the next tap or edit. Never with a toast.
 */
@Composable
fun CheckInFooter(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    toast: String? = null,
    toastTick: Int = 0,
    loading: Boolean = false,
    error: String? = null,
) {
    val motion = rememberMotion()
    val dim by animateFloatAsState(
        if (dimmed) 1f else 0f,
        tween(if (motion.enabled) Motion.FAST else 0),
        label = "footerDim",
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .testTag(CHECK_IN_FOOTER_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (toast != null) {
            key(toastTick) { CheckInToast(toast) }
            Spacer(Modifier.height(Spacing.xl))
        } else if (error != null) {
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) { InlineErrorCard(error) }
            Spacer(Modifier.height(Spacing.xl))
        }
        PrimaryButton(
            label = label,
            onClick = { if (!loading) onClick() },
            modifier = Modifier
                .testTag(CHECK_IN_CTA_TAG)
                .dimmed(dim),
            variant = PrimaryButtonVariant.Sunset,
            trailing = { if (loading) CheckInSpinner() else CheckInArrow() },
        )
    }
}

/** How far the glow under a sunset CTA reaches: 8 down plus 20 of blur, and a margin. */
private val DIM_REACH = 32.dp

/**
 * Opacity 1 → .45 and saturation 1 → .4 at [amount] 0 → 1, on every API level (no RenderEffect).
 *
 * The layer reaches [DIM_REACH] past the button on every side: CSS's `filter` on the wrapper takes
 * in the button's `--liq-shadow-violet` glow, which falls 28 below it (8 down, 20 of blur). A layer
 * bounded to the button would cut the glow off at its edge instead of dimming it.
 */
private fun Modifier.dimmed(amount: Float): Modifier = if (amount <= 0f) this else drawWithContent {
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - 0.6f * amount) })
        alpha = 1f - 0.55f * amount
    }
    val reach = DIM_REACH.toPx()
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(-reach, -reach, size.width + reach, size.height + reach), paint)
        drawContent()
        canvas.restore()
    }
}

/**
 * The footer's toast: rgba(29,17,41,.92), radius 10, padding 8 / 14, max 280, Manrope 600 / 12.5 /
 * 1.35, centred; `0 6px 20px` at .18. Enters with `su-toast-in` -- 200 ms, up 6, on the brand curve.
 * A polite live region.
 */
@Composable
fun CheckInToast(text: String) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.REVEAL else 0
    val rise = with(LocalDensity.current) { 6.dp.roundToPx() }
    // One entry per toast: the caller keys this on its tick, so a repeat tap starts it again.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(ms, easing = ShowUpEasing)) + slideInVertically(tween(ms, easing = ShowUpEasing)) { rise },
        exit = fadeOut(tween(0)),
    ) {
        val message = remember(text) { AnnotatedString(text) }
        val style = TextStyle(
            color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp, lineHeight = (12.5f * 1.35f).sp, textAlign = TextAlign.Center,
        )
        Text(
            message,
            Modifier
                .widthIn(max = 280.dp)
                .boxShadow(CheckInToastFill, 10.dp, CheckInToastShadow, offsetY = 6.dp, blur = 20.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .prettyWrap(message, style, centered = true)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = style,
        )
    }
}

/**
 * `CheckInSpinner` -- the commit's saving state, in place of the arrow: an 18 ring, 2 wide, white at
 * .35 with a white top quarter, one turn per 800 ms, linear. Still under reduced motion: a frozen
 * ring reads as busy without movement.
 */
@Composable
fun CheckInSpinner() {
    val motion = rememberMotion()
    val turn = if (motion.enabled) {
        val transition = rememberInfiniteTransition(label = "spinner")
        transition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(Motion.SPIN, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        ).value
    } else {
        0f
    }
    Canvas(Modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        val inset = w / 2
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(Color.White.copy(alpha = 0.35f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        rotate(turn) {
            // `border-top-color` on a round box: the top quarter, from -135 to -45 degrees.
            drawArc(Color.White, -135f, 90f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
        }
    }
}
