/*
 * ProfileEmbraceScreen.kt
 * ShowUp · Profile creation 05 — Embrace: build your profile (SHOWUP-155)
 *
 * ONE STATE, ONE PROP. The only branch on this screen is whether a first name exists:
 *
 *     with a name     "Nice to see you, Leo."
 *     without a name  "Glad you're here."
 *
 * The second sentence is identical either way. There is no error state, no empty state and no
 * loading state, because nothing on this screen can fail -- it takes no input and calls nothing.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THIS IS NOT A STEP, AND THE ABSENCES ARE THE DESIGN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * It is a bridge between "The basics" (name, email, date of birth) and "The real you" (photos,
 * prompts, media). It belongs to neither progress bar, so:
 *
 *   · NO [AppHeader] -- no section title, no back chevron, no skip, no close
 *   · NO StepProgress -- "The basics" is finished and "The real you" has not started
 *   · nothing to fill in, so nothing to validate
 *
 * The reference file names adding either of those "the single most likely mistake on this screen".
 * `audit/verify-profile.py` asserts both absences so a later tidy-up cannot reintroduce them.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO NAMED EXCEPTIONS TO THE FLOW RULES, AND BOTH ARE THIS SCREEN'S
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * RULE 5 SAYS NO AMBIENT BACKDROP. Every other profile screen is flat [Cream] because its keyboard
 * owns the bottom half and a glow behind the keys reads as noise. This screen has no keyboard and
 * no input, so it carries the first-run Startup screen's atmosphere instead -- the orange orb, the
 * violet orb and the 360-tall peach wash -- which is what makes the bridge read as a beat rather
 * than another form. Decided 9 September 2026, and scoped to the two bridge screens only.
 *
 * That is also why this uses [WelcomeScaffold] rather than [BasicsScaffold]: the backdrop already
 * exists there, as one component, drawn full-bleed behind the status bar and the gesture bar. The
 * flow README asks for exactly that ("extract the backdrop as one component: Startup, screen 05
 * and the sibling bridge all use it") and it was extracted before this screen existed.
 *
 * RULE 7 SAYS THE CTA IS ORANGE, NOT SUNSET. Sunset is reserved for commitment beats, and agreeing
 * to build a profile is one -- so this screen's CTA is the FULL-WIDTH sunset [PrimaryButton], not
 * the round orange NextButton the basics use. Same scope: the two bridges only.
 *
 * ONE EXIT, AND IT IS FORWARD. The CTA goes to photos. There is no back -- profile creation is
 * mandatory once entered (group rule 4b) -- no skip, and no dismissable chrome, so the platform
 * back gesture is swallowed here exactly as it is on screen 01.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeScaffold

/**
 * Copy — final strings. Every one of these is quoted by `audit/verify-profile.py`.
 *
 * The headline is two sentences with a hard break between them, not one wrapped run: the reference
 * puts a literal `<br/>` there, so the greeting always owns its own line whatever the name's
 * length. [GREETING_NAMED] takes the name; [GREETING_ANONYMOUS] is what an empty or whitespace-only
 * name falls back to.
 */
internal object EmbraceCopy {
    const val GREETING_ANONYMOUS = "Glad you're here."
    fun greeting(name: String) = "Nice to see you, $name."

    const val HEADLINE_LEAD = "Time to show the person "
    const val HEADLINE_EM = "behind"
    const val HEADLINE_TAIL = " your profile."

    const val LEAD =
        "You are wonderful as you are. Share what makes you unique so others get a real feel " +
            "for who they'll meet."
    const val BULLET_PHOTOS = "Upload meaningful photos."
    const val BULLET_PROMPT = "Record a voice or video prompt."
    const val CLOSING = "More of you means better matches — and more real-life connections."
    const val CTA = "Upload my photos"
}

/**
 * A list row with a small orange dot where a bullet glyph would be.
 *
 * THE DOT IS AN ELEMENT, NOT A CHARACTER. No "•", no emoji, no list marker -- a brand rule the
 * reference states outright, and one the em-dash-only house style already implies. It is 7 round
 * in [Orange], offset 9 from the top so it sits on the first line's optical centre rather than its
 * top edge; text sits on a baseline and a circle does not, so without the offset the dot floats.
 *
 * `clearAndSetSemantics {}` rather than a content description: the dot carries no meaning a screen
 * reader needs, and an unlabelled Canvas would otherwise be announced as an unnamed element
 * between the two sentences.
 */
@Composable
private fun BulletRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(Orange)
                .clearAndSetSemantics {},
        )
        Text(
            text,
            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, lineHeight = (15f * 1.45f).sp,
        )
    }
}

@Composable
fun ProfileEmbraceScreen(
    /** The first name from step 1. Empty or whitespace-only falls back to the anonymous greeting. */
    firstName: String = "",
    onContinue: () -> Unit = {},
) {
    // Mandatory once entered: there is nothing behind this screen to return to, and a screen with
    // no chevron that the OS can still dismiss is worse than no rule at all. Same handler, same
    // reason, as ProfileNameScreen.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    val clean = firstName.trim()
    val greeting =
        if (clean.isEmpty()) EmbraceCopy.GREETING_ANONYMOUS else EmbraceCopy.greeting(clean)

    // topPadding 64 and gutter 28 come straight from the reference's headline block,
    // `padding: '64px 28px 0'`. The 64 is what replaces the chrome this screen does not have:
    // on every other profile screen the header and the progress bar occupy that space.
    WelcomeScaffold(topPadding = 64.dp, gutter = 28.dp) {
        // Lora 700 / 34 / 1.1 / -0.015em, with ONE italic em. The hard break keeps the greeting on
        // its own line: `textWrap: balance` in the reference is a wrapping hint for the second
        // sentence, not permission for the two to run together.
        WashHeadline(
            parts = listOf(
                (greeting + "\n" + EmbraceCopy.HEADLINE_LEAD) to false,
                EmbraceCopy.HEADLINE_EM to true,
                EmbraceCopy.HEADLINE_TAIL to false,
            ),
            fontSize = 34.sp,
            lineHeight = (34f * 1.1f).sp,
            letterSpacing = (-0.015).em,
        )

        // The body block's own 28 top padding. The headline block has no bottom padding, so this
        // single value is the whole gap between them.
        Box(Modifier.padding(top = 28.dp)) {
            Text(
                EmbraceCopy.LEAD,
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 15.5.sp, lineHeight = (15.5f * 1.55f).sp,
            )
        }

        Column(
            Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BulletRow(EmbraceCopy.BULLET_PHOTOS)
            BulletRow(EmbraceCopy.BULLET_PROMPT)
        }

        Text(
            EmbraceCopy.CLOSING,
            modifier = Modifier.padding(top = 14.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.5.sp, lineHeight = (15.5f * 1.55f).sp,
        )

        // THE ONLY FLEXIBLE ELEMENT. It absorbs every device difference: about 240 at 390 x 844,
        // about 65 at 375 x 667. Never a hard-coded Y, and never a second spacer.
        Box(Modifier.weight(1f))

        // Sunset and full width -- rule 7's named exception. The trailing arrow is the design
        // system's own `arrow-right` from components/shared.jsx (line 5,12 -> 19,12 with the head
        // at 12,5 / 19,12 / 12,19), which is what BrandIcon.ArrowRight already draws. The
        // reference file inlines a slightly different path of its own; the shared icon set is the
        // design system's and wins over one screen's inline copy.
        PrimaryButton(
            label = EmbraceCopy.CTA,
            onClick = onContinue,
            modifier = Modifier.padding(bottom = 22.dp),
            variant = PrimaryButtonVariant.Sunset,
            trailing = { Icon(BrandIcon.ArrowRight, 18.dp, tint = Color.White, strokeWidth = 2.dp) },
        )
    }
}

// ── previews: one state x three frames, plus the anonymous fallback ─────────
//
// The device matrix from the ticket. 375 x 667 is where the spacer nearly collapses and is checked
// first; 430 x 932 is where it takes the surplus. No keyboard is drawn on any profile preview
// because the platform keyboard is not ours -- and on this screen there is none at all, which is
// the one place that is literally true rather than a preview limitation.

@Preview(name = "Embrace · named · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_N375() { ProfileEmbraceScreen(firstName = "Leo") }

@Preview(name = "Embrace · named · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_N390() { ProfileEmbraceScreen(firstName = "Leo") }

@Preview(name = "Embrace · named · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE_N430() { ProfileEmbraceScreen(firstName = "Leo") }

@Preview(name = "Embrace · no name · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PE_A375() { ProfileEmbraceScreen() }

@Preview(name = "Embrace · no name · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PE_A390() { ProfileEmbraceScreen() }

@Preview(name = "Embrace · no name · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PE_A430() { ProfileEmbraceScreen() }

// A long name is the one thing that can reflow this headline. Checked at the narrowest frame.
@Preview(name = "Embrace · long name · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PE_L320() { ProfileEmbraceScreen(firstName = "Maximiliana-Rose") }
