/*
 * ProfileEmbraceDetailsScreen.kt
 * ShowUp · Profile 13 — Embrace 2: add profile details (SHOWUP-166)
 *
 * The second bridge. It celebrates what the user has just finished, with a one-shot confetti rain,
 * then asks for more with one button. ONE STATE; the only branch is whether a first name exists.
 *
 * NOT A STEP: no AppHeader, no StepProgress, no back, no skip -- "adding an AppHeader or
 * StepProgress 'for consistency' is the most likely mistake here". The shell is screen 05's,
 * extracted into [EmbraceBridgeShell] for exactly this screen; this file is the payload: the
 * headline (no em, no wash), the lead, the centred violet glow, and the confetti.
 *
 * NOTHING WAITS FOR THE CONFETTI. The CTA is live from the first frame; a tap mid-fall navigates at
 * once, and the rain leaves with the screen.
 */
package com.showup.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral

/**
 * Copy -- final strings, "confirmed 5 Oct 2026, do not reword". The CTA label carries no arrow
 * character: the arrow is the shell's trailing SVG icon.
 */
internal object EmbraceDetailsCopy {
    fun headline(name: String) = "You are doing great, $name."
    const val HEADLINE_ANONYMOUS = "You are doing great."
    const val LEAD =
        "You'll see on others' profiles exactly what you choose to share on yours. Let's add a few " +
            "more details!"
    const val CTA = "Add profile details"
}

@Composable
fun ProfileEmbraceDetailsScreen(
    /** The name from screen 01, trimmed. Empty or whitespace-only gives the no-name headline. */
    firstName: String = "",
    onContinue: () -> Unit = {},
    /**
     * Whether this showing is a PUSH. False on a back-pop from height, which "does not replay" the
     * confetti -- and on the previews and the fit harness, which photograph the screen at rest.
     */
    playConfetti: Boolean = false,
    /**
     * The push transition has settled. "t = 0 when the push transition has settled -- not on mount,
     * where it would play behind the incoming slide."
     */
    transitionSettled: Boolean = true,
) {
    val clean = firstName.trim()
    val headline =
        if (clean.isEmpty()) EmbraceDetailsCopy.HEADLINE_ANONYMOUS else EmbraceDetailsCopy.headline(clean)

    EmbraceBridgeShell(
        // One run, not italic: "no em in the headline and no orange wash -- unlike screen 05".
        headline = listOf(headline to false),
        cta = EmbraceDetailsCopy.CTA,
        onContinue = onContinue,
        backdrop = BridgeBackdrop.Centred,
        balanceHeadline = true,
        behindContent = { ConfettiRain(play = playConfetti, start = transitionSettled) },
    ) {
        // Body block `padding: 20px 28px 0`. Manrope 500 / 16 / 1.55, max-width 330.
        Text(
            EmbraceDetailsCopy.LEAD,
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 330.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 16.sp, lineHeight = (16f * 1.55f).sp,
        )
    }
}

// ── previews: the screen AT REST at the ticket's three frames, plus the no-name branch ────────

@Preview(name = "Embrace 2 · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE2_375() { ProfileEmbraceDetailsScreen(firstName = "Leo") }

@Preview(name = "Embrace 2 · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE2_390() { ProfileEmbraceDetailsScreen(firstName = "Leo") }

@Preview(name = "Embrace 2 · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE2_430() { ProfileEmbraceDetailsScreen(firstName = "Leo") }

@Preview(name = "Embrace 2 · no name · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE2_Anon() { ProfileEmbraceDetailsScreen(firstName = "  ") }

@Preview(name = "Embrace 2 · long name · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PE2_Long() { ProfileEmbraceDetailsScreen(firstName = "Maximiliana-Rose") }
