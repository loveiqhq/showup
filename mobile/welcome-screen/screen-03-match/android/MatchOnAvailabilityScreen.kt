/*
 * MatchOnAvailabilityScreen.kt
 * ShowUp · Tutorial screen 3 — "Match on availability" (SHOWUP-136)
 *
 * Consumes the shell built for screen 2. Content only: no progress bar, no nav row, no headline
 * styling lives here. If something in the chrome needs to change, change it in TutorialShell and
 * re-verify every screen.
 *
 * Built to 02-match-on-availability-spec-sheet.
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
fun MatchOnAvailabilityScreen(
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.match)
    }

    TutorialShell(
        step = 2,
        totalSteps = 5,
        eyebrow = "Match on availability",
            // accent under "free to date"
            underlineWidth = 210.dp,
        nextLabel = "Next",
        showBack = true,
        onNext = {
            analytics.track(TutorialAnalytics.CTA_TAPPED, TutorialAnalytics.match)
            onNext()
        },
        onBack = {
            analytics.track(TutorialAnalytics.BACK_TAPPED, TutorialAnalytics.match)
            onBack()
        },
        headline = {
            Text(
                buildAnnotatedString {
                    append("Match people who are ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("free to date") }
                    append(" when you are.")
                },
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // ⑤ rule list — gap 12, rows wrap. No body paragraphs on this screen: the slot is
            // simply not used, so it reserves no height.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleRow("Visible only when you’re free to date", "check in and state your available times to meet")
                RuleRow("Synchronised schedules", "we only show you people to date who are available when you are")
                RuleRow("Different day, different vibe", "match on what you’re in the mood for right now, not a static bio")
            }
        },
        art = { IllustrationPlaceholder() },
    )
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun MatchOnAvailabilityPreviewSmall() { MatchOnAvailabilityScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MatchOnAvailabilityPreviewReference() { MatchOnAvailabilityScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun MatchOnAvailabilityPreviewLarge() { MatchOnAvailabilityScreen() }
