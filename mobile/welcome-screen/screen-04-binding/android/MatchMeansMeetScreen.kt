/*
 * MatchMeansMeetScreen.kt
 * ShowUp · Tutorial screen 4 — "Match means meet" (SHOWUP-137)
 *
 * Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
 * styling lives here. If something in the chrome needs to change, change it in TutorialShell and
 * re-verify every screen.
 *
 * Built to 03-match-means-meet-spec-sheet.
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
fun MatchMeansMeetScreen(
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.binding)
    }

    TutorialShell(
        step = 3,
        totalSteps = 5,
        eyebrow = "Match means meet",
        nextLabel = "Next",
        showBack = true,
        onNext = {
            analytics.track(TutorialAnalytics.CTA_TAPPED, TutorialAnalytics.binding)
            onNext()
        },
        onBack = {
            analytics.track(TutorialAnalytics.BACK_TAPPED, TutorialAnalytics.binding)
            onBack()
        },
        headline = {
            Text(
                buildAnnotatedString {
                    append("A match is a ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("binding") }
                    append(" date.")
                },
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // ⑤ rule list — gap 12, rows wrap. No body paragraphs on this screen: the slot is
            // simply not used, so it reserves no height.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleRow("You decide who you like", "if you match, you will meet")
                RuleRow("We suggest the time", "a date and time that works for both of your schedules")
                RuleRow("We pick the place", "a safe, public spot halfway between you")
            }
        },
        art = { IllustrationPlaceholder() },
    )
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun MatchMeansMeetPreviewSmall() { MatchMeansMeetScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MatchMeansMeetPreviewReference() { MatchMeansMeetScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun MatchMeansMeetPreviewLarge() { MatchMeansMeetScreen() }
