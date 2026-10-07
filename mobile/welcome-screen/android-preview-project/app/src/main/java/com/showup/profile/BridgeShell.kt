/*
 * BridgeShell.kt
 * ShowUp · the shell both bridges render through -- screen 05 (SHOWUP-155) and Embrace 2 (SHOWUP-166)
 *
 * Profile 13: "Same shell as screen 05, different payload ... This screen reuses 05's shell -- do
 * not fork it. If 05's shell does not take those as props yet, add the props; do not copy the
 * screen." It did not; this is the extraction, and both screens are now payloads over it.
 *
 * WHAT IS THE SHELL'S, AND THEREFORE IDENTICAL ON BOTH:
 *   · no AppHeader, no StepProgress, no back of any kind -- "the absences are the design"
 *   · the ambient backdrop and the full-width sunset CTA, the bridges' two named exceptions
 *   · headline block `padding: 64px 28px 0`, Lora 700 / 34 / 1.1 / -0.015em -- THE SIZE IS NOT A
 *     PROP. "One headline size for the bridges: 34 ... part of the shared shell, not a prop, so the
 *     two bridges cannot drift apart again." It drifted once: the kit drew 36 here.
 *   · ONE flexible spacer between the body and the CTA, whose wrapper sits 22 above the gesture bar
 *   · the trailing 18 arrow, the design system's own `arrow-right`, never an arrow character
 *
 * WHAT IS THE PAYLOAD'S: the headline runs (05 has an em; 13 has none), the body, the backdrop
 * recipe (05's violet sits in the corner; 13's is a centred glow), and the layer between the
 * backdrop and the content -- 13's confetti.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeScaffold

/** The bridges' CTA, for the fit harness. */
internal const val BRIDGE_CTA_TAG = "bridge-cta"

/** Which backdrop a bridge draws. Two recipes of the one backdrop component. */
enum class BridgeBackdrop(
    internal val placement: OrbPlacement,
    internal val orangeAlpha: Float,
    internal val violetAlpha: Float,
) {
    /** Screen 05: Startup's orbs, orange top-right and violet bottom-left, 0.32 / 0.28. */
    Corner(OrbPlacement.Startup, 0.32f, 0.28f),

    /** Embrace 2: orange 480 top-right at 0.30, violet as a centred glow at the bottom at 0.30. */
    Centred(OrbPlacement.EmbraceDetails, 0.30f, 0.30f),
}

@Composable
internal fun EmbraceBridgeShell(
    headline: List<Pair<String, Boolean>>,
    cta: String,
    onContinue: () -> Unit,
    backdrop: BridgeBackdrop,
    /** `text-wrap: balance` on the headline. Embrace 2's criteria name it; 05 keeps its hard break. */
    balanceHeadline: Boolean = false,
    /** Drawn above the backdrop and below the content. Embrace 2's confetti; nothing on 05. */
    behindContent: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    // Mandatory once entered, and nothing behind a bridge to return to: the system back gesture
    // and key are swallowed, the same handler and the same reason as the name screen.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    WelcomeScaffold(
        topPadding = 64.dp,
        gutter = 28.dp,
        placement = backdrop.placement,
        orangeAlpha = backdrop.orangeAlpha,
        violetAlpha = backdrop.violetAlpha,
        behindContent = behindContent,
    ) {
        WashHeadline(
            parts = headline,
            fontSize = 34.sp,
            // A heading for TalkBack, as iOS marks it -- the one heading on a screen with no header.
            modifier = Modifier.semantics { heading() },
            lineHeight = (34f * 1.1f).sp,
            letterSpacing = (-0.015).em,
            balance = balanceHeadline,
        )

        body()

        // THE ONLY FLEXIBLE ELEMENT. Never a hard-coded Y, and never a second spacer.
        Box(Modifier.weight(1f))

        PrimaryButton(
            label = cta,
            onClick = onContinue,
            modifier = Modifier.padding(bottom = 22.dp).testTag(BRIDGE_CTA_TAG),
            variant = PrimaryButtonVariant.Sunset,
            trailing = { Icon(BrandIcon.ArrowRight, 18.dp, tint = Color.White, strokeWidth = 2f) },
        )
    }
}
