/*
 * ThirtyMinutesScreen.kt
 * ShowUp · Tutorial screen 5 — "30 minutes, no pressure" (SHOWUP-138)
 *
 * Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
 * styling lives here. If something in the chrome needs to change, change it in TutorialShell and
 * re-verify every screen.
 *
 * Built to 04-thirty-minutes-spec-sheet.
 */
package com.showup.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora

@Composable
fun ThirtyMinutesScreen(
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.thirty)
    }

    TutorialShell(
        step = 4,
        totalSteps = 5,
        eyebrow = "30 minutes, no pressure",
            // accent under "thirty minutes"
            underlineWidth = 150.dp,
        nextLabel = "Next",
        showBack = true,
        onNext = {
            analytics.track(TutorialAnalytics.CTA_TAPPED, TutorialAnalytics.thirty)
            onNext()
        },
        onBack = {
            analytics.track(TutorialAnalytics.BACK_TAPPED, TutorialAnalytics.thirty)
            onBack()
        },
        headline = {
            Text(
                buildAnnotatedString {
                    append("Just ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("thirty minutes") }
                    append(".")
                },
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // ⑤ rule list — gap 12, rows wrap. No body paragraphs on this screen: the slot is
            // simply not used, so it reserves no height.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleRow("Low-pressure 30-minute dates", "quick, relaxed meetups to see if you click in real life")
                RuleRow("30 minutes up", "stay if you’re vibing, or leave with a smile — no hard feelings")
                RuleRow("Built-in icebreakers", "fun, easy prompts to keep the conversation flowing")
            }
        },
        art = { IllustrationPlaceholder() },
    )
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun ThirtyMinutesPreviewSmall() { ThirtyMinutesScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ThirtyMinutesPreviewReference() { ThirtyMinutesScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun ThirtyMinutesPreviewLarge() { ThirtyMinutesScreen() }
