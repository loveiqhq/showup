package com.showup.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one primary button. `Button` from components/shared.jsx at `size="lg"`: height 56,
 * padding 0/28, radius 9999, Manrope 700 16, gap 8.
 *
 * Press is `scale(0.98)` over 180ms on `cubic-bezier(.22,1,.36,1)` -- CLAUDE.md states it as a
 * non-negotiable, and there are no hover states because the product is mobile-first.
 *
 * WHY THIS FILE EXISTS
 *
 * There were three of these. This one (as `PillButton`) lived in WelcomeShell.kt and carried the
 * whole design; `SunsetButton` in WelcomeScreen.kt was a copy of its sunset variant written six
 * days earlier, before this one existed, and it had drifted; `NextButton` in TutorialShell.kt is
 * not a duplicate at all and stays where it is -- it is a label beside a circular arrow badge, a
 * different silhouette with its own spec.
 *
 * A primitive in a screen file is how the second copy happens: the author of the tutorial's CTA
 * had no reason to look inside the sign-up flow's shell.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PrimaryButtonVariant = PrimaryButtonVariant.Sunset,
    enabled: Boolean = true,
    /**
     * 56 everywhere except the conflict modal, whose resolve CTA the reference draws at 54 and
     * whose secondary it draws at 50. All three clear every provider's published minimum (Apple's
     * is 44pt), so the smaller two are a layout choice rather than a compliance question.
     */
    height: Dp = ComponentSizes.controlHeight,
    /**
     * 16 in the sign-up flow, 17 in the tutorial -- two design worlds, one call site each way.
     *
     * A parameter rather than a variant because the tone is unchanged: the tutorial's CTA is the
     * same sunset button, set one step larger. [PrimaryButtonVariant.Plain] overrides this, being
     * 15/600 by definition rather than by choice.
     *
     * Worth collapsing to a single value if the design side agrees; then this goes away.
     */
    labelSize: TextUnit = 16.sp,
    leading: (@Composable () -> Unit)? = null,
    /** Mirrors [leading]. Used once: the tutorial CTA's trailing arrow. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && motion.enabled) 0.98f else 1f,
        animationSpec = tween(
            durationMillis = if (motion.enabled) Motion.FAST else 0, easing = ShowUpEasing),
        label = "press",
    )
    // Percent, not Radius.pill: at height 56 the two are the same 28, but the conflict modal draws
    // this at 54 and 50, where only the percent stays a true pill.
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .then(
                when (variant) {
                    // --liq-shadow-violet on the sunset CTA.
                    //
                    // FLAGGED, not fixed: Modifier.shadow uses Android's elevation system, whose
                    // light sits at the top-centre of the window, so the direction depends on
                    // where the button sits on screen. verify-spec.py forbids exactly this in
                    // TutorialShell.kt, where the Next circle draws its glow instead. Correcting
                    // it here changes how eight buttons render and is its own task.
                    PrimaryButtonVariant.Sunset -> Modifier
                        // 12 is an elevation, and there is no elevation token; Spacing.xl happens
                        // to be 12 and would be a spacing token telling a lie about itself.
                        .shadow(12.dp, shape, ambientColor = Purple, spotColor = Purple)
                        .clip(shape)
                        .background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()))
                    PrimaryButtonVariant.Ghost -> Modifier
                        .clip(shape)
                        .border(1.dp, Border, shape)
                    PrimaryButtonVariant.Plain -> Modifier.clip(shape)
                    // Black is one of the three appearances Apple's guidelines allow.
                    PrimaryButtonVariant.Apple -> Modifier.clip(shape).background(Color.Black)
                    // White background is required, not preferred -- the G may not sit on anything
                    // else. #747775 is the border colour from Google's own button.
                    PrimaryButtonVariant.Google -> Modifier
                        .clip(shape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFF747775), shape)
                    // Facebook Blue. Recolouring to our palette is explicitly prohibited.
                    PrimaryButtonVariant.Facebook -> Modifier.clip(shape)
                        .background(Color(0xFF1877F2))
                }
            )
            .clickable(
                interactionSource = interaction, indication = null,
                enabled = enabled, role = Role.Button, onClick = onClick,
            )
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            label,
            color = when (variant) {
                PrimaryButtonVariant.Sunset, PrimaryButtonVariant.Apple,
                PrimaryButtonVariant.Facebook -> Color.White
                PrimaryButtonVariant.Google -> Color(0xFF1F1F1F)  // Google's specified label colour
                PrimaryButtonVariant.Ghost -> Fg
                PrimaryButtonVariant.Plain -> Muted
            },
            fontFamily = Manrope,
            // The plain secondary is 600/15 -- one step down from the 700 every real button
            // carries, which is what stops the modal reading as two equal choices.
            fontWeight = if (variant == PrimaryButtonVariant.Plain) FontWeight.SemiBold else FontWeight.Bold,
            fontSize = if (variant == PrimaryButtonVariant.Plain) 15.sp else labelSize,
            maxLines = 1,
        )
        trailing?.invoke()
    }
}

/**
 * Six, and every one is required.
 *
 * [Apple], [Google] and [Facebook] are not style choices -- each provider dictates the appearance
 * of its own sign-in button and enforces it. Google forbids recolouring or resizing the G and
 * requires a white background, making compliance a condition of app verification; Meta requires
 * its mark in white or #1877F2 and prohibits recolouring to a host brand's palette. The uniform
 * ghost treatment the design specified breaks both.
 *
 * The pill silhouette, the 56 height and the Manrope label stay -- those are ours.
 *
 * [Plain] is not a provider treatment and never carries one: it is the conflict modal's secondary,
 * which has no border precisely so the pair does not read as two equal choices.
 */
enum class PrimaryButtonVariant { Sunset, Ghost, Plain, Apple, Google, Facebook }
