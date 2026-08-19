/*
 * WelcomeScreen.kt
 * ShowUp · Onboarding 00 — Welcome screen (SHOWUP-117)
 *
 * Faithful implementation of 00-welcome-spec-sheet with Jetpack Compose.
 * Drop into the Android app, open the @Preview in Android Studio (see the folder README).
 * Fonts: replace FontFamily.Serif / SansSerif below with bundled Lora / Manrope when available.
 */
package com.showup.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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

// Swap for bundled fonts: FontFamily(Font(R.font.lora_bold, FontWeight.Bold), …)
private val Lora    = FontFamily.Serif
private val Manrope = FontFamily.SansSerif

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
@Composable
private fun HeroHeart(modifier: Modifier = Modifier) {
    Box(modifier.size(width = 200.dp, height = 190.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Heart",
            tint = Color.Black,                              // opaque base; gradient painted over it
            modifier = Modifier
                .size(176.dp)
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = listOf(Orange, Color(0xFFE0567A), Purple),
                        start = Offset(size.width * 0.2f, 0f),
                        end = Offset(size.width * 0.6f, size.height),
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush, blendMode = BlendMode.SrcAtop)
                    }
                },
        )
        Box(Modifier.size(9.dp).offset(x = (-76).dp, y = (-58).dp)
            .background(Color(0xFFA877E6), CircleShape))
        Box(Modifier.size(7.dp).offset(x = 64.dp, y = 40.dp)
            .background(Orange.copy(alpha = 0.7f), CircleShape))

        // Sparkle — iOS had one, Android did not. Drawn rather than taken from Material icons,
        // which has no four-point sparkle of this shape.
        Canvas(Modifier.size(16.dp).offset(x = 78.dp, y = (-56).dp)) {
            val w = size.width
            val h = size.height
            val star = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w * 0.58f, h * 0.42f); lineTo(w, h / 2f); lineTo(w * 0.58f, h * 0.58f)
                lineTo(w / 2f, h)
                lineTo(w * 0.42f, h * 0.58f); lineTo(0f, h / 2f); lineTo(w * 0.42f, h * 0.42f)
                close()
            }
            drawPath(star, Color(0xFFF6C96A))
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WelcomeScreenPreview() {
    WelcomeScreen()
}
