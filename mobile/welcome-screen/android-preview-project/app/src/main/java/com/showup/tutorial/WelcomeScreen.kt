/*
 * WelcomeScreen.kt
 * ShowUp · Tutorial card 1 — Welcome screen (SHOWUP-117)
 *
 * Faithful implementation of 00-welcome-spec-sheet with Jetpack Compose.
 * Drop into the Android app, open the @Preview in Android Studio (see the folder README).
 *
 * Tokens, fonts and the analytics seam now live in shared/ — two screens cannot each declare
 * their own Cream/Orange/Lora. The heart is drawn in code, so there is no asset to export.
 */
package com.showup.tutorial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.showup.designsystem.Lora

import com.showup.designsystem.PrimaryButton

import com.showup.designsystem.IconSizes
import com.showup.designsystem.Spacing

import com.showup.analytics.AnalyticsTracker
import com.showup.analytics.NoOpAnalytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.welcome.Wordmark
import com.showup.welcome.WashHeadline
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle

// ── ④ Hero heart (200 × 190, gradient orange → purple) ───────────────────
// The design's heart, drawn from its own curves rather than borrowed from Material's icon set.
// Same coordinates as the iOS HeartShape and the design SVG — a 200 x 190 space, scaled to fit —
// so both platforms render the identical shape and there is no PNG to export or go stale.
private fun heartPath(w: Float, h: Float): Path {
    val s = minOf(w / 200f, h / 190f)
    val dx = (w - 200f * s) / 2f
    val dy = (h - 190f * s) / 2f
    fun x(v: Float) = dx + v * s
    fun y(v: Float) = dy + v * s
    // Traced from the spec sheet's actual outline, not fitted to its width profile — many
    // different shapes share a width profile, and the earlier attempt produced flat-topped lobes
    // with hard corners. These control points come from a least-squares fit against the real
    // contour, and the apex tangent is horizontal on both sides so each lobe is a smooth dome.
    return Path().apply {
        moveTo(x(99.8f), y(172.9f))
        cubicTo(x(78f), y(152.1f), x(17.9f), y(111.3f), x(20f), y(63.7f))
        cubicTo(x(22.3f), y(22.2f), x(58.2f), y(16.7f), x(66.6f), y(17.1f))
        cubicTo(x(82.6f), y(17.1f), x(97f), y(27.6f), x(100f), y(36.6f))
        cubicTo(x(103f), y(27.6f), x(117.4f), y(17.1f), x(133.4f), y(17.1f))
        cubicTo(x(141.8f), y(16.7f), x(177.7f), y(22.2f), x(180f), y(63.7f))
        cubicTo(x(182.1f), y(111.3f), x(122f), y(152.1f), x(99.8f), y(172.9f))
        close()
    }
}

@Composable
private fun HeroHeart(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 200.dp, height = 190.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = minOf(size.width / 200f, size.height / 190f)
            val dx = (size.width - 200f * s) / 2f
            val dy = (size.height - 190f * s) / 2f
            fun p(x: Float, y: Float) = Offset(dx + x * s, dy + y * s)

            // soft peach glow behind the heart
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Orange.copy(alpha = 0.26f),
                    1.00f to Color.Transparent,
                    center = p(100f, 92f),
                    radius = 96f * s,
                ),
                radius = 96f * s,
                center = p(100f, 92f),
            )

            drawPath(
                heartPath(size.width, size.height),
                Brush.linearGradient(
                    colors = listOf(Orange, Color(0xFFE8565E), Color(0xFF9333D9)),
                    start = p(36f, 11f),
                    end = p(164f, 182f),
                ),
            )

            // highlight on the upper-left lobe
            rotate(degrees = -18f, pivot = p(66f, 52f)) {
                drawOval(
                    color = Color.White.copy(alpha = 0.17f),
                    topLeft = p(42f, 37f),
                    size = Size(48f * s, 30f * s),
                )
            }

            // floating accents — moved outward: the silhouette is wider than before
            drawCircle(Color(0xFFA877E6), radius = 4.5f * s, center = p(13f, 40f))
            drawCircle(Orange.copy(alpha = 0.7f), radius = 3.5f * s, center = p(162f, 130f))

            // four-point sparkle — Material has no star of this shape
            drawPath(
                Path().apply {
                    moveTo(p(186f, 17f).x, p(186f, 17f).y)
                    lineTo(p(189.2f, 25.8f).x, p(189.2f, 25.8f).y)
                    lineTo(p(198f, 29f).x, p(198f, 29f).y)
                    lineTo(p(189.2f, 32.2f).x, p(189.2f, 32.2f).y)
                    lineTo(p(186f, 41f).x, p(186f, 41f).y)
                    lineTo(p(182.8f, 32.2f).x, p(182.8f, 32.2f).y)
                    lineTo(p(174f, 29f).x, p(174f, 29f).y)
                    lineTo(p(182.8f, 25.8f).x, p(182.8f, 25.8f).y)
                    close()
                },
                Color(0xFFFBBF4B),
            )
        }
    }
}

// ── The screen ───────────────────────────────────────────────────────────
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit = {},
    analytics: AnalyticsTracker = NoOpAnalytics,
) {
    Box(Modifier.fillMaxSize().background(Cream)) {

        // ② peach top fade + two soft orbs
        //
        // NOT Modifier.blur(): that is a no-op below Android 12 (API 31), so on any older device
        // the orbs rendered as hard-edged circles — a visibly broken screen, not a subtle one.
        // A radial gradient with an alpha falloff gives the same soft orb on every API level, and
        // the extra colour stops also work against the banding the AC calls out.
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.46f)
                .background(Brush.verticalGradient(
                    0.00f to Orange.copy(alpha = 0.15f),
                    0.22f to Orange.copy(alpha = 0.11f),
                    0.48f to Orange.copy(alpha = 0.06f),
                    0.72f to Orange.copy(alpha = 0.02f),
                    1.00f to Color.Transparent,
                )))
            Box(Modifier.size(340.dp).align(Alignment.TopEnd).offset(x = 110.dp, y = (-150).dp)
                .background(Brush.radialGradient(
                    0.00f to Orange.copy(alpha = 0.32f),
                    0.34f to Orange.copy(alpha = 0.26f),
                    0.62f to Orange.copy(alpha = 0.12f),
                    0.80f to Color.Transparent,
                    1.00f to Color.Transparent,
                )))
            Box(Modifier.size(400.dp).align(Alignment.BottomStart).offset(x = (-160).dp, y = 140.dp)
                .background(Brush.radialGradient(
                    0.00f to Purple.copy(alpha = 0.28f),
                    0.34f to Purple.copy(alpha = 0.22f),
                    0.62f to Purple.copy(alpha = 0.10f),
                    0.80f to Color.Transparent,
                    1.00f to Color.Transparent,
                )))
        }

        // Nested flex columns — the two Spacer(weight = 1f) are the `flex:1` spacers.
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)   // safe area
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = 20.dp, bottom = 20.dp), // 20 / 24 / 0(+20)
        ) {
            // ③ Wordmark -- the shared one, not a local copy.
            //
            // The local copy had drifted from the token file in three ways at once: flat purple
            // instead of the wordmark gradient, no 700 weight, and no -0.02em tracking. That is
            // what a second copy of a brand mark does, and it is why there is now only one.
            Wordmark(size = 26.sp)

            Spacer(Modifier.weight(1f))                          // flex:1

            // ④⑤⑥⑦ cluster
            HeroHeart(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
            // ⑤ Headline. The orange wash belongs to the italic run, and WashHeadline measures
            //    where that run actually landed before drawing it.
            //
            //    This was a 210dp bar pinned to the bottom of the headline block. A fixed width
            //    cannot know where the words are: it sat under the whole last line instead of
            //    under "Show Up.", and it was wrong by a different amount on every screen size.
            WashHeadline(
                parts = listOf("Welcome\nto " to false, "Show Up." to true),
                fontSize = 42.sp, lineHeight = 44.sp,
            )
            Spacer(Modifier.height(Spacing.xxl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("We’re happy to see you", color = Fg, fontFamily = Manrope,
                     fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.width(Spacing.md))
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Orange,
                     modifier = Modifier.size(IconSizes.sm))
            }
            Spacer(Modifier.height(Spacing.xxl))
            Text("Let us quickly explain how Show Up works.", color = Neutral, fontFamily = Manrope,
                 fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp,
                 modifier = Modifier.widthIn(max = 320.dp))

            Spacer(Modifier.weight(1f))                          // flex:1

            // ⑧⑨ button + caption
            //
            // The shared primitive, not the copy of it that used to live in this file. That copy
            // predated PrimaryButton by six days and had drifted four ways: a two-stop gradient
            // instead of SunsetStops, no violet shadow (iOS had one), no press feedback, and a
            // Material glyph for the arrow where CLAUDE.md requires a drawn 2px stroke. All four
            // are the same defects the 23 Aug audit fixed on cards 02-06, in the one file it did
            // not look at.
            //
            // 17 is the tutorial's CTA size -- NextButton is 17 too -- against 16 in the sign-up
            // flow. The arrow is the tour's own, so it is the same one the Next circle draws.
            PrimaryButton(
                label = "Show me how",
                onClick = {
                    analytics.track(TutorialAnalytics.CTA_TAPPED, TutorialAnalytics.welcome)
                    onContinue()
                },
                labelSize = 17.sp,
                trailing = { ArrowRight(size = IconSizes.sm) },
            )
            Spacer(Modifier.height(Spacing.lg))
            Text("Takes less than a minute", color = Subtle, fontFamily = Manrope,
                 fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                 modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
    LaunchedEffect(Unit) {
        analytics.track(TutorialAnalytics.CARD_VIEWED, TutorialAnalytics.welcome)
    }
}

// The three frames the acceptance criteria name. All content must be visible on each, with
// nothing clipped, no scroll, and the CTA reachable. 375 x 667 is the one that fails first —
// check it before the others.
@Preview(name = "375 x 667 - iPhone SE", showBackground = true, widthDp = 375, heightDp = 667)
@Composable
private fun WelcomeScreenPreviewSmall() {
    WelcomeScreen()
}

@Preview(name = "390 x 844 - reference", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeScreenPreviewReference() {
    WelcomeScreen()
}

@Preview(name = "430 x 932 - Pro Max", showBackground = true, widthDp = 430, heightDp = 932)
@Composable
private fun WelcomeScreenPreviewLarge() {
    WelcomeScreen()
}

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
private fun WelcomeScreenPreviewSystemUi() {
    WelcomeScreen()
}
