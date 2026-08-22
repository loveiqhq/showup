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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Faint
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple

/** ⑤ One rule row: dot · rule · faint dash · purple consequence. */
@Composable
private fun RuleRow(rule: String, consequence: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        // 6px dot, offset 6 from the top so it sits on the first line's optical centre
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(Orange, CircleShape))
        Text(
            buildAnnotatedString {
                append(rule)
                withStyle(SpanStyle(color = Faint, fontWeight = FontWeight.Medium)) { append(" — ") }
                withStyle(SpanStyle(color = Purple)) { append(consequence) }
            },
            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = 18.2.sp, letterSpacing = (-0.01).em,
            maxLines = 1, softWrap = false,
        )
    }
}

/**
 * ⑧ Placeholder for the artwork design will supply.
 *
 * It holds the exact block the real asset gets — 248 x 210, centred, shrinking with the frame — so
 * dropping the image in later changes nothing about the layout. Replace the Canvas with an
 * Image(painterResource(...), contentScale = ContentScale.Fit) and the surrounding code is unchanged.
 */
@Composable
private fun IllustrationPlaceholder() {
    Box(contentAlignment = Alignment.Center) {
        // radial glow, 220 x 190 — kept so the "no banding" criterion stays testable
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Orange.copy(alpha = 0.16f),
                    0.26f to Orange.copy(alpha = 0.13f),
                    0.48f to Purple.copy(alpha = 0.10f),
                    0.70f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                radius = size.minDimension * 0.62f,
            )
        }
        Box(
            Modifier.fillMaxHeight().heightIn(max = 230.dp).aspectRatio(248f / 210f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = Purple.copy(alpha = 0.38f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f,
                        ),
                    ),
                )
            }
            Text(
                "ILLUSTRATION\n248 × 210",
                color = Purple.copy(alpha = 0.75f), fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp,
                letterSpacing = 0.07.em,
            )
        }
    }
}

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
            Text(
                buildAnnotatedString {
                    append("We want you to ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("actually") }
                    append(" meet.")
                },
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.015).em,
            )
        },
        content = {
            // ⑤ rule list — 3 rows, gap 8, block gap below 16
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleRow("No texting for weeks", "date in real life instead")
                RuleRow("No ghosting", "we penalize unreliability")
                RuleRow("No collecting matches", "you meet who you match")
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
