/*
 * ShowUpEveryTimeScreen.kt
 * ShowUp · Tutorial screen 6 — "Show up, every time" (SHOWUP-139)
 *
 * The last screen. Screens 2-5 sell the product; this one states how reliability is measured and
 * enforced, and the CTA is the moment the user accepts it.
 *
 * Three documented variations on the shell, all of them parameters rather than forks:
 *   1. Terminal CTA  — "I’m ready to show up" with the sunset-gradient circle.
 *   2. Statement list — five single-colour rows, NOT the two-tone rule/consequence pattern of
 *      screens 2-5. That pattern sells a benefit; this states policy. Do not "fix" it to match.
 *   3. A closing paragraph, the only body paragraph in screens 3-6.
 *
 * Height: this is the tallest screen in the set. The illustration is drawn at 0.62 scale and must
 * shrink further on short frames — never the type, never the nav row, never a scroll.
 *
 * Built to 05-show-up-every-time-spec-sheet.
 */
package com.showup.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral

@Composable
fun ShowUpEveryTimeScreen(
    onFinish: () -> Unit = {},
    onBack: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.showUpRate)
    }

    TutorialShell(
        step = 5,                                   // all five segments filled
        totalSteps = 5,
        eyebrow = "Show up, every time",
            // accent under "there’s a cost"
            underlineWidth = 196.dp,
        nextLabel = "I’m ready to show up",
        nextVariant = NextVariant.Sunset,           // variation 1
        showBack = true,
        onNext = {
            // completion event for the whole tutorial, not just this screen
            analytics.track(TutorialAnalytics.COMPLETED, TutorialAnalytics.showUpRate)
            onFinish()
        },
        onBack = {
            analytics.track(TutorialAnalytics.BACK_TAPPED, TutorialAnalytics.showUpRate)
            onBack()
        },
        headline = {
            Text(
                buildAnnotatedString {
                    append("If you don’t show up, ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("there’s a cost") }
                    append(".")
                },
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // variation 2 — statements, one colour, gap 11
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                StatementRow("Every profile has a Show-up Rate.")
                StatementRow("Showing up to dates is reflected positively.")
                StatementRow("Not showing up is reflected negatively.")
                StatementRow("A persistently low Show-up Rate reduces your visibility to others.")
                StatementRow("Miss a date without fair notice and you can’t search for new dates for 24 hours.")
            }
            // variation 3 — closing paragraph, gap above 5, lead clause bold
            Spacer(Modifier.height(5.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fg)) {
                        append("Show Up is for reliable people.")
                    }
                    append(" Life happens. Stay fair and show respect for each other, and your ")
                    append("Show-up Rate will reflect it.")
                },
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, lineHeight = 21.75.sp,
            )
        },
        art = { IllustrationPlaceholder(scale = 0.62f) },
    )
}

@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun ShowUpPreviewSmall() { ShowUpEveryTimeScreen() }

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ShowUpPreviewReference() { ShowUpEveryTimeScreen() }

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun ShowUpPreviewLarge() { ShowUpEveryTimeScreen() }
