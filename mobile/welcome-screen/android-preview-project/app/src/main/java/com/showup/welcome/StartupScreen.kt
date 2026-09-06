/*
 * StartupScreen.kt
 * ShowUp · Startup — first run (SHOWUP-140)
 *
 * The first screen a new visitor sees. Once an account exists on the device, launch opens Welcome
 * back instead; the routing decision belongs to the flow, not to either screen.
 *
 * Built to welcome/screen-startup-reference.jsx, which wins on numbers, and SHOWUP-140, which wins
 * on behaviour and copy. Layout is nested flex columns with TWO flex:1 spacers — the social-proof
 * row floats optically centred between them rather than being pinned. No Y offset anywhere.
 */
package com.showup.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle

/**
 * @param showSocialProof the dates figure is dynamic and gated — SHOWUP-140 requires a toggle,
 *        because the claim only appears once enough dates have actually been organised and the
 *        threshold has not been decided.
 *
 *        **Defaults to false**, which is the production-safe value: shipping a number nobody has
 *        agreed to would be inventing a statistic on the first screen a user ever sees.
 *
 *        The preview flow (`SignUpFlow`) passes `true` deliberately, so the row can be seen and
 *        measured at every device size before the real number exists. `ScreenFitTest` sweeps both
 *        states — with and without — because the row sits between two `flex: 1` spacers and
 *        changes what has to fit.
 */
@Composable
fun StartupScreen(
    onCreateAccount: () -> Unit = {},
    onLogin: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onLegalNotice: () -> Unit = {},
    showSocialProof: Boolean = false,
) {
    WelcomeScaffold {
        // Where the gap has to yield. The handoff is explicit that 375 x 667 is NOT one of those
        // frames -- there the two spacers below collapse to about 20 each and the 132 survives --
        // so the test is 667, not the 700 it used to be, which yielded one whole frame too early.
        //
        // Width is in it because height alone gets the Fold cover screen wrong: 320 x 686 is
        // TALLER than the SE and still cannot take 132, because at 320 the headline and the legal
        // line each wrap an extra line and eat the room the extra height provided. Both frames are
        // in ScreenFitTest, which is what this predicate is answerable to.
        val config = LocalConfiguration.current
        val compact = config.screenHeightDp < 667 || config.screenWidthDp < 360
        Wordmark()

        // 132 is the only large fixed gap and the element that yields on short frames — never the
        // type, never a button height, never the CTA's safe-area margin, never a scroll.
        //
        // Chosen from the frame rather than fought for with a weight: Compose has no max-height on
        // a weighted child, so a weighted spacer would eat all the slack the two flex:1 spacers
        // below are supposed to share. The iOS twin CAN express it as a range and does --
        // `Spacer().frame(minHeight: 64, maxHeight: 132)` -- so the two platforms reach the same
        // layout by different means, and 64 is the floor on both.
        Spacer(Modifier.height(if (compact) 64.dp else 132.dp))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // ④ headline — line 1 at 32, line 2 at 44, both 1.05. "meeting" italic + wash.
            Column {
                Text(
                    "Stop texting for days.",
                    color = Fg, fontFamily = com.showup.designsystem.Lora,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 33.6.sp,
                    letterSpacing = (-0.015).em,
                )
                Spacer(Modifier.height(4.dp))
                WashHeadline(
                    parts = listOf("Start " to false, "meeting" to true, " today." to false),
                    fontSize = 44.sp,
                )
            }
            Text(
                "Your availability. Your intent. Your date — today or tomorrow.",
                modifier = Modifier.widthIn(max = 320.dp),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp, lineHeight = 26.6.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // Social proof floats between the two spacers. Hidden entirely when the figure is not yet
        // true — an unmet claim is worse than no claim.
        if (showSocialProof) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.Calendar, 16.dp, tint = Orange, strokeWidth = 2.dp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append("234.000 Dates") }
                        append(" already organized")
                    },
                    color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Three real tappable links, each with its own hit area (SHOWUP-140).
        //
        // LinkAnnotation rather than three separate Text composables in a Row: the sentence has to
        // wrap as one paragraph, and a Row of Texts cannot wrap mid-sentence. This keeps it a
        // single laid-out paragraph while giving each phrase its own touch target and its own
        // "link" role for a screen reader.
        val linkStyle = SpanStyle(color = Fg, fontWeight = FontWeight.SemiBold)
        Text(
            buildAnnotatedString {
                append("By creating an account, you agree to our ")
                withLink(LinkAnnotation.Clickable("terms") { onTerms() }) {
                    withStyle(linkStyle) { append("Terms & Conditions") }
                }
                append(" and acknowledge that you have read our ")
                withLink(LinkAnnotation.Clickable("privacy") { onPrivacy() }) {
                    withStyle(linkStyle) { append("Privacy Policy") }
                }
                append(". See our ")
                withLink(LinkAnnotation.Clickable("legal") { onLegalNotice() }) {
                    withStyle(linkStyle) { append("Legal Notice") }
                }
                append(".")
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            // Muted (62%, 5.03:1), not Subtle (46%, 3.04:1) — Subtle fails WCAG 2.1 AA and this
            // is the sentence where the user accepts the Terms. See audit finding 7.
            color = Subtle, fontFamily = Manrope, fontSize = 12.sp, lineHeight = 17.4.sp,
            textAlign = TextAlign.Center,
        )

        PillButton("Create free account", onCreateAccount)
        Spacer(Modifier.height(10.dp))

        // The reference's own hit area is ~31; the ticket requires at least 44 without changing the
        // 14px type, so the box carries the target and the text keeps its size.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onLogin),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                buildAnnotatedString {
                    append("Already have an account? ")
                    withStyle(SpanStyle(color = Purple, textDecoration = TextDecoration.Underline)) { append("Log in") }
                },
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun StartupPreviewSmall() { StartupScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun StartupPreviewReference() { StartupScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun StartupPreviewLarge() { StartupScreen() }

@Preview(name = "social proof off", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun StartupPreviewNoProof() { StartupScreen(showSocialProof = false) }

@Preview(name = "390 x 844 - with system bars", showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable
private fun StartupPreviewSystemUi() { StartupScreen() }
