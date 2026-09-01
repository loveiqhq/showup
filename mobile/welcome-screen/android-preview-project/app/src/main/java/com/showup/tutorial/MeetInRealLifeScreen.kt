/*
 * MeetInRealLifeScreen.kt
 * ShowUp · Tutorial screen 2 — "Meet in real life" (SHOWUP-135)
 *
 * Screen 2 of the 6-step tutorial, reached from the Welcome screen via "Show me how".
 *
 * Everything structural lives in TutorialShell — progress bar, eyebrow pill, headline slot,
 * illustration block, the flexible region and the nav row. This file is content only, which is the
 * point: screens 3-6 are the same shell with different content.
 *
 * Built to 01-meet-in-real-life-spec-sheet.
 */
package com.showup.tutorial

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.welcome.WashHeadline
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral

@Composable
fun MeetInRealLifeScreen(
    onNext: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.meet)
    }

    TutorialShell(
        step = 1,                       // first of the 5-segment tour
        totalSteps = 5,
        eyebrow = "Meet people in real life",
        nextLabel = "Next",
        showBack = false,               // first screen of the tour — slot reserved, invisible
        onNext = {
            analytics.track(TutorialAnalytics.CTA_TAPPED, TutorialAnalytics.meet)
            onNext()
        },
        headline = {
            // ④ Lora 700 / 34 / 1.1 / -0.015em, "actually" italic
            WashHeadline(
                parts = listOf(
                    "We want you to " to false,
                    "actually" to true,
                    " meet." to false,
                ),
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // ⑤ rule list — 3 rows, gap 8, block gap below 16
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleRow("No texting for weeks", "date in real life instead", wraps = false)
                RuleRow("No ghosting", "we penalize unreliability", wraps = false)
                RuleRow("No collecting matches", "you meet who you match", wraps = false)
            }
            Spacer(Modifier.height(16.dp))

            // ⑥ commitment — "will meet" italic 700 · gap below 10
            Text(
                buildAnnotatedString {
                    append("If you match here, you ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)) {
                        append("will meet")
                    }
                    append(" in real life. A match is a committed date — not a maybe.")
                },
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 16.sp, lineHeight = 24.sp,
            )
            Spacer(Modifier.height(10.dp))

            // ⑦ reliability — two bold runs, no gap below (the flexible region takes over)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Showing up to dates boosts your profile")
                    }
                    append(" by highlighting your reliability and increasing your visibility. ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Missing dates without fair notice upfront")
                    }
                    append(" reduces your visibility for others.")
                },
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 16.sp, lineHeight = 24.sp,
            )
        },
        art = { IllustrationPlaceholder() },
    )
}

// The three frames the acceptance criteria name. 375 x 667 is the one that fails first.
@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun MeetPreviewSmall() { MeetInRealLifeScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MeetPreviewReference() { MeetInRealLifeScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun MeetPreviewLarge() { MeetInRealLifeScreen() }

// The three previews above render the composable on its own. There is no window behind them, so
// WindowInsets.safeDrawing resolves to zero and the progress bar sits flush against the very top
// edge -- which is not where it lands on a phone. Those three answer "does it fit"; they are
// misleading about vertical position.
//
// This one asks the preview to draw the real status and navigation bars. That also makes the
// insets real, so it shows where the content actually sits once the system bars take their space.
// Slower to render than the others, which is why it is on the reference size only.
@Preview(
    name = "390 x 844 - with system bars",
    showSystemUi = true,
    device = "spec:width=390dp,height=844dp",
)
@Composable
private fun MeetPreviewSystemUi() {
    MeetInRealLifeScreen()
}
