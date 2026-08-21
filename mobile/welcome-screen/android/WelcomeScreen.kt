/*
 * WelcomeScreen.kt
 * ShowUp · Onboarding 00 — Welcome screen (SHOWUP-117)
 *
 * Faithful implementation of 00-welcome-spec-sheet with Jetpack Compose.
 * Drop into the Android app, open the @Preview in Android Studio (see the folder README).
 *
 * Fonts: Lora + Manrope ship in ../fonts/ and must be copied into app/src/main/res/font/ before
 * this builds — see the mapping above the FontFamily declarations. The heart is drawn in code,
 * so there is no asset to export.
 */
package com.showup.onboarding

// Adjust if the app's R class lives elsewhere (it follows the applicationId).
import com.showup.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ── ① Design tokens (from the spec sheet) ───────────────────────────────
private val Cream   = Color(0xFFFFFBF7)
private val Orange  = Color(0xFFFE6839)
private val Purple  = Color(0xFF812AEC)
private val Fg      = Color(0xFF1D1129)
private val Neutral = Color(0xFF4B3B5A)
private val Subtle  = Color(0x751D1129)   // rgba(29,17,41,.46)

// ── Fonts ────────────────────────────────────────────────────────────────
//
// The .ttf files ship in ../fonts/. Copy them into app/src/main/res/font/ under these exact
// names — Android resource names allow only lowercase and underscores:
//
//     Lora-Regular.ttf      ->  lora_regular.ttf
//     Lora-Bold.ttf         ->  lora_bold.ttf
//     Lora-Italic.ttf       ->  lora_italic.ttf
//     Lora-BoldItalic.ttf   ->  lora_bold_italic.ttf
//     Manrope-Medium.ttf    ->  manrope_medium.ttf
//     Manrope-SemiBold.ttf  ->  manrope_semibold.ttf
//     Manrope-Bold.ttf      ->  manrope_bold.ttf
//
// This references them directly rather than falling back to FontFamily.Serif/SansSerif. The
// fallback compiled fine and rendered the wrong typeface with no warning, which is how a screen
// ships in the wrong font. If the files are missing, this now fails to build — loudly.
private val Lora = FontFamily(
    Font(R.font.lora_regular,     FontWeight.Normal),
    Font(R.font.lora_bold,        FontWeight.Bold),
    Font(R.font.lora_italic,      FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lora_bold_italic, FontWeight.Bold,   FontStyle.Italic),
)
private val Manrope = FontFamily(
    Font(R.font.manrope_medium,   FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold,     FontWeight.Bold),
)

// ── ⑧ Reusable "sunset / lg" button ─────────────────────────────────────
@Composable
fun SunsetButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)                                   // size lg
            .clip(RoundedCornerShape(28.dp))                 // pill
            .background(Brush.horizontalGradient(listOf(Orange, Purple)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = Color.White, fontFamily = Manrope,
                 fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                 tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

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
    // Curve fitted to the spec sheet's own silhouette, measured rather than eyeballed:
    // aspect 1.035, widest 30% down, cleft 12.5% deep, and — the one that matters — the two lobe
    // peaks sitting 65.8% of the width apart. The earlier path had them only 39.6% apart, which
    // is what made the top read flat instead of as two lobes.
    return Path().apply {
        moveTo(x(100f), y(172.6f))
        cubicTo(x(86.6f), y(157.3f), x(18.9f), y(115.2f), x(20f), y(64.4f))
        cubicTo(x(19.8f), y(45f), x(41.3f), y(18f), x(47.3f), y(18f))
        cubicTo(x(79f), y(18.9f), x(98.2f), y(25.6f), x(100f), y(37.3f))
        cubicTo(x(101.8f), y(25.6f), x(121f), y(18.9f), x(152.7f), y(18f))
        cubicTo(x(158.7f), y(18f), x(180.2f), y(45f), x(180f), y(64.4f))
        cubicTo(x(181.1f), y(115.2f), x(113.4f), y(157.3f), x(100f), y(172.6f))
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
            rotate(degrees = -18f, pivot = p(70f, 55f)) {
                drawOval(
                    color = Color.White.copy(alpha = 0.17f),
                    topLeft = p(45f, 40f),
                    size = Size(50f * s, 30f * s),
                )
            }

            // floating accents — moved outward: the silhouette is wider than before
            drawCircle(Color(0xFFA877E6), radius = 4.5f * s, center = p(15f, 32f))
            drawCircle(Orange.copy(alpha = 0.7f), radius = 3.5f * s, center = p(172f, 118f))

            // four-point sparkle — Material has no star of this shape
            drawPath(
                Path().apply {
                    moveTo(p(179f, 20f).x, p(179f, 20f).y)
                    lineTo(p(182.4f, 29.6f).x, p(182.4f, 29.6f).y)
                    lineTo(p(192f, 33f).x, p(192f, 33f).y)
                    lineTo(p(182.4f, 36.4f).x, p(182.4f, 36.4f).y)
                    lineTo(p(179f, 46f).x, p(179f, 46f).y)
                    lineTo(p(175.6f, 36.4f).x, p(175.6f, 36.4f).y)
                    lineTo(p(166f, 33f).x, p(166f, 33f).y)
                    lineTo(p(175.6f, 29.6f).x, p(175.6f, 29.6f).y)
                    close()
                },
                Color(0xFFFBBF4B),
            )
        }
    }
}

// ── The screen ───────────────────────────────────────────────────────────
@Composable
fun WelcomeScreen(onContinue: () -> Unit = {}) {
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
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), // 20 / 24 / 0(+20)
        ) {
            // ③ Wordmark
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Fg)) { append("Show ") }
                    withStyle(SpanStyle(color = Purple, fontStyle = FontStyle.Italic)) { append("Up") }
                    withStyle(SpanStyle(color = Orange)) { append(".") }
                },
                fontFamily = Lora, fontSize = 26.sp, lineHeight = 26.sp,
            )

            Spacer(Modifier.weight(1f))                          // flex:1

            // ④⑤⑥⑦ cluster
            HeroHeart(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
            // ⑤ Headline + the underline accent the spec calls for. No blur here either — the
            //    gradient's own alpha falloff carries the softness on every API level.
            Box {
                Text(
                    buildAnnotatedString {
                        append("Welcome\nto ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Show Up.") }
                    },
                    color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                    fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = 6.dp)
                        .size(width = 210.dp, height = 12.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.00f to Color.Transparent,
                                0.26f to Orange.copy(alpha = 0.45f),
                                0.62f to Color(0xFFE0567A).copy(alpha = 0.35f),
                                1.00f to Color.Transparent,
                            ),
                            RoundedCornerShape(50),
                        )
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("We're happy to see you", color = Fg, fontFamily = Manrope,
                     fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Orange,
                     modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Let us quickly explain how Show Up works.", color = Neutral, fontFamily = Manrope,
                 fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp,
                 modifier = Modifier.widthIn(max = 320.dp))

            Spacer(Modifier.weight(1f))                          // flex:1

            // ⑧⑨ button + caption
            SunsetButton(title = "Show me how", onClick = {
                // Analytics.track("cta_click", mapOf("screen" to "onboarding_welcome"))
                onContinue()
            })
            Spacer(Modifier.height(10.dp))
            Text("Takes less than a minute", color = Subtle, fontFamily = Manrope,
                 fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                 modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
    // LaunchedEffect(Unit) { Analytics.track("screen_view", mapOf("screen" to "onboarding_welcome")) }
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
