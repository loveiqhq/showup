/*
 * WelcomeShell.kt
 * ShowUp · shared chrome for the welcome & sign-up flow (SHOWUP-140 / 142 / 143)
 *
 * Startup and Welcome back are one visual space: the same three backdrop layers, value for value,
 * and the same wordmark at 26. The handoff is explicit that building them separately makes them
 * drift, so everything shared lives here and neither screen owns a copy.
 *
 * Values come from design_handoff_showup/tokens/colors_and_type.css and components/shared.jsx,
 * which are authoritative, plus the two reference .jsx files which win on numbers.
 */
package com.showup.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.WordmarkStops
import com.showup.tutorial.rememberMotion

// ─────────────────────────────────────────────────────────────────────────────
// ① Backdrop — full-bleed, BEHIND the status bar and home indicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The ambient backdrop. Startup and Welcome back carry all three layers; phone verification carries
 * two (no peach wash) at lower intensity, because its keypad owns the bottom half of the screen and
 * nothing should glow behind it.
 *
 * Drawn on a Canvas rather than as blurred Boxes: `Modifier.blur` is a no-op below API 31, which
 * would give hard-edged circles on a third of devices. A radial gradient's own alpha falloff carries
 * the softness on every API level.
 */
@Composable
fun WelcomeBackdrop(
    modifier: Modifier = Modifier,
    peachWash: Boolean = true,
    orangeAlpha: Float = 0.32f,
    violetAlpha: Float = 0.28f,
    topWeighted: Boolean = false,
) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (topWeighted) {
            // Phone verification: orange 460 at top -18% / right -30%, violet 420 at top -10% / left -25%
            radial(Offset(w * 1.30f - 230.dp.toPx(), -0.18f * h + 230.dp.toPx()), 230.dp.toPx(), Orange, orangeAlpha)
            radial(Offset(-0.25f * w + 210.dp.toPx(), -0.10f * h + 210.dp.toPx()), 210.dp.toPx(), Purple, violetAlpha)
        } else {
            // Startup / Welcome back: orange 520 at top -15% / right -25%, violet 600 at bottom -20% / left -30%
            radial(Offset(w + 0.25f * w - 260.dp.toPx(), -0.15f * h + 260.dp.toPx()), 260.dp.toPx(), Orange, orangeAlpha)
            radial(Offset(-0.30f * w + 300.dp.toPx(), h + 0.20f * h - 300.dp.toPx()), 300.dp.toPx(), Purple, violetAlpha)
        }

        if (peachWash) {
            // linear-gradient(180deg, rgba(255,229,210,.55) 0%, rgba(255,251,247,0) 70%) · height 360
            val washH = 360.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFFFFE5D2).copy(alpha = 0.55f),
                    0.7f to Color(0x00FFFBF7),
                    1.0f to Color.Transparent,
                    startY = 0f, endY = washH,
                ),
                size = Size(w, washH),
            )
        }
    }
}

/** One orb. The stop at 65% is where the reference puts full transparency. */
private fun DrawScope.radial(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha),
            0.65f to Color.Transparent,
            1.00f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ② Wordmark — 26 on both launch screens, identical position
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `.su-wordmark` — Lora 700, tracking -0.02em, baseline-aligned with a 0.18em gap.
 *
 * "Up" is filled with `--su-grad-wordmark` (96°, violet → rose → orange) clipped to the glyphs, not
 * flat violet. Compose does this with a Brush on the TextStyle, which is the direct equivalent of
 * the CSS `background-clip: text`.
 */
@Composable
fun Wordmark(size: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    val gap = with(LocalDensity.current) { (size.toPx() * 0.18f).toDp() }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(gap)) {
        val base = TextStyle(
            fontFamily = Lora, fontWeight = FontWeight.Bold,
            fontSize = size, letterSpacing = (-0.02).em, lineHeight = size,
        )
        Text("Show", style = base, color = Fg)
        Text(
            "Up",
            style = base.copy(
                fontStyle = FontStyle.Italic,
                brush = Brush.horizontalGradient(colorStops = WordmarkStops.toTypedArray()),
            ),
        )
        Text(".", style = base, color = Orange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ③ The underline wash — the signature editorial flourish
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A headline whose italic run carries the orange wash.
 *
 * From `tokens/colors_and_type.css`, `.su-underlined em::after`:
 *   left/right -2px · bottom -0.08em · height 0.32em · z-index -1
 *   radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)
 * and the em itself is italic at **weight 500**, not the surrounding 700.
 *
 * The wash is positioned from the laid-out text rather than a hard-coded width: `getPathForRange`
 * gives the italic run's real bounds, so the wash tracks the word wherever it wraps to. A fixed
 * width is what an earlier build used and it only ever looks right at one frame size.
 *
 * Compose's radial gradients are circular, so the canvas is scaled to turn the circle into the
 * ellipse the CSS asks for.
 */
@Composable
fun WashHeadline(
    parts: List<Pair<String, Boolean>>,      // text → is this the italic run?
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = (fontSize.value * 1.05f).sp,
    letterSpacing: TextUnit = (-0.02).em,
) {
    val text = buildAnnotatedString {
        parts.forEach { (t, italic) ->
            if (italic) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)) { append(t) }
            } else {
                append(t)
            }
        }
    }
    // character range of the italic run, if there is one
    var start = -1
    var end = -1
    var cursor = 0
    parts.forEach { (t, italic) ->
        if (italic && start < 0) { start = cursor; end = cursor + t.length }
        cursor += t.length
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier = modifier.drawBehind {
            val lr = layout ?: return@drawBehind
            if (start < 0 || end <= start) return@drawBehind
            val bounds = lr.getPathForRange(start, end).getBounds()
            if (bounds.width <= 0f) return@drawBehind

            val em = fontSize.toPx()
            val left = bounds.left - 2.dp.toPx()
            val right = bounds.right + 2.dp.toPx()
            val bottom = bounds.bottom + em * 0.08f      // bottom: -0.08em
            val h = em * 0.32f                            // height: 0.32em
            val w = right - left
            val rx = w / 2f

            withTransform({
                translate(left, bottom - h)
                // ellipse at 50% 100%: horizontal radius w/2, vertical radius h
                scale(scaleX = 1f, scaleY = h / rx, pivot = Offset(rx, h))
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Orange.copy(alpha = 0.55f),
                        0.7f to Color.Transparent,
                        1.0f to Color.Transparent,
                        center = Offset(rx, h), radius = rx,
                    ),
                    radius = rx, center = Offset(rx, h),
                )
            }
        },
        onTextLayout = { layout = it },
        color = Fg,
        fontFamily = Lora,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ④ Buttons — always pills (9999), never rounded rectangles
// ─────────────────────────────────────────────────────────────────────────────

enum class PillVariant { Sunset, Ghost }

/**
 * `Button` from components/shared.jsx at `size="lg"`: height 56, padding 0/28, radius 9999,
 * Manrope 700 16, gap 8.
 *
 * Press is `scale(0.98)` over 180ms on `cubic-bezier(.22,1,.36,1)` — CLAUDE.md states it as a
 * non-negotiable, and there are no hover states because the product is mobile-first.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Sunset,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && motion.enabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = if (motion.enabled) 180 else 0, easing = ShowUpEasing),
        label = "press",
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .then(
                when (variant) {
                    // --liq-shadow-violet on the sunset CTA
                    PillVariant.Sunset -> Modifier
                        .shadow(12.dp, shape, ambientColor = Purple, spotColor = Purple)
                        .clip(shape)
                        .background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()))
                    PillVariant.Ghost -> Modifier
                        .clip(shape)
                        .border(1.dp, Border, shape)
                }
            )
            .clickable(
                interactionSource = interaction, indication = null,
                enabled = enabled, role = Role.Button, onClick = onClick,
            )
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            label,
            color = if (variant == PillVariant.Sunset) Color.White else Fg,
            fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/** CLAUDE.md: easing is `cubic-bezier(.22,1,.36,1)` · 180ms state · 280ms progress. */
val ShowUpEasing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

// ─────────────────────────────────────────────────────────────────────────────
// ⑤ Icons — Lucide geometry, 24x24, stroke 1.7, currentColor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drawn rather than imported: CLAUDE.md requires inline stroke-only SVG at Lucide geometry, and
 * forbids icon fonts, PNGs and unicode glyphs. The brand marks (Apple, Google, Facebook) are filled
 * paths in the source, so they are drawn filled here.
 */
enum class BrandIcon { Phone, Apple, Google, Facebook, Calendar, ChevronDown, ArrowLeft, Pencil }

@Composable
fun Icon(icon: BrandIcon, size: Dp, tint: Color = Fg, strokeWidth: Dp = 1.7.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f                      // all paths are authored on a 24 grid
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            when (icon) {
                BrandIcon.Calendar -> {
                    drawRoundRect(
                        color = tint, topLeft = Offset(3f, 4f), size = Size(18f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = stroke,
                    )
                    drawLine(tint, Offset(16f, 2f), Offset(16f, 6f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(8f, 2f), Offset(8f, 6f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(3f, 10f), Offset(21f, 10f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.ChevronDown -> drawPath(
                    path(listOf(6f to 9f, 12f to 15f, 18f to 9f)), tint, style = stroke,
                )
                BrandIcon.ArrowLeft -> {
                    drawLine(tint, Offset(19f, 12f), Offset(5f, 12f), stroke.width, StrokeCap.Round)
                    drawPath(path(listOf(12f to 19f, 5f to 12f, 12f to 5f)), tint, style = stroke)
                }
                BrandIcon.Pencil -> {
                    drawPath(path(listOf(11f to 4f, 4f to 4f, 2f to 6f, 2f to 20f, 4f to 22f, 18f to 22f, 20f to 20f, 20f to 13f)), tint, style = stroke)
                    drawPath(path(listOf(18.5f to 2.5f, 21.5f to 5.5f, 12f to 15f, 8f to 16f, 9f to 12f), close = true), tint, style = stroke)
                }
                // Brand marks are filled in the source rather than stroked.
                BrandIcon.Phone -> drawPath(phonePath(), tint, style = stroke)
                BrandIcon.Apple -> drawPath(applePath(), tint)
                BrandIcon.Google -> drawPath(googlePath(), tint)
                BrandIcon.Facebook -> drawPath(facebookPath(), tint)
            }
        }
    }
}

private fun path(points: List<Pair<Float, Float>>, close: Boolean = false) =
    androidx.compose.ui.graphics.Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        if (close) close()
    }

private fun phonePath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22f, 16.92f); lineTo(22f, 19.92f)
    cubicTo(22f, 21.02f, 20.92f, 21.99f, 19.82f, 21.92f)
    cubicTo(16.7f, 21.6f, 13.7f, 20.6f, 11.19f, 18.85f)
    cubicTo(8.85f, 17.25f, 6.87f, 15.27f, 5.19f, 12.85f)
    cubicTo(3.43f, 10.3f, 2.44f, 7.3f, 2.12f, 4.18f)
    cubicTo(2.05f, 3.08f, 3.01f, 2f, 4.11f, 2f)
    lineTo(7.11f, 2f)
    cubicTo(8.11f, 2f, 8.98f, 2.72f, 9.11f, 3.72f)
    cubicTo(9.24f, 4.68f, 9.48f, 5.62f, 9.83f, 6.53f)
    cubicTo(10.06f, 7.24f, 9.86f, 8.02f, 9.38f, 8.64f)
    lineTo(8.09f, 9.91f)
    cubicTo(9.53f, 12.36f, 11.64f, 14.47f, 14.09f, 15.91f)
    lineTo(15.36f, 14.64f)
    cubicTo(15.98f, 14.16f, 16.76f, 13.96f, 17.47f, 14.19f)
    cubicTo(18.38f, 14.54f, 19.32f, 14.78f, 20.28f, 14.91f)
    cubicTo(21.29f, 15.05f, 22f, 15.92f, 22f, 16.92f)
    close()
}

private fun applePath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(16f, 4f); cubicTo(16.5f, 5.5f, 15.5f, 7f, 14f, 7.5f)
    cubicTo(12f, 8f, 11f, 7f, 11f, 5.5f); cubicTo(12.5f, 4f, 14f, 3.5f, 16f, 4f); close()
    moveTo(18.4f, 13.5f); cubicTo(17.8f, 15f, 17f, 16.5f, 15.5f, 16.5f)
    cubicTo(14.1f, 16.5f, 13.6f, 15.7f, 12f, 15.7f); cubicTo(10.4f, 15.7f, 9.9f, 16.5f, 8.5f, 16.5f)
    cubicTo(7f, 16.5f, 6.1f, 15.1f, 5.4f, 13.6f); cubicTo(4f, 11f, 4.6f, 7.6f, 6.6f, 6.5f)
    cubicTo(7.9f, 5.8f, 9.1f, 6.2f, 10.1f, 6.5f); cubicTo(11.1f, 6.8f, 11.5f, 6.8f, 12.5f, 6.5f)
    cubicTo(13.6f, 6.1f, 14.7f, 5.6f, 16.1f, 6.3f); cubicTo(14.4f, 7.4f, 14f, 9.8f, 15.7f, 11.1f)
    cubicTo(16.2f, 12.2f, 16.5f, 12.7f, 18.4f, 13.5f); close()
}

private fun googlePath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22f, 12.2f); cubicTo(22f, 11.4f, 21.9f, 10.8f, 21.8f, 10.2f)
    lineTo(12f, 10.2f); lineTo(12f, 14.1f); lineTo(17.6f, 14.1f)
    cubicTo(17.4f, 15.4f, 16.6f, 16.4f, 15.6f, 17.1f); lineTo(15.6f, 19.6f); lineTo(18.9f, 19.6f)
    cubicTo(20.8f, 17.8f, 22f, 15.2f, 22f, 12.2f); close()
    moveTo(12f, 22f); cubicTo(14.7f, 22f, 17f, 21.1f, 18.7f, 19.6f); lineTo(15.4f, 17.1f)
    cubicTo(14.5f, 17.7f, 13.4f, 18.1f, 12f, 18.1f); cubicTo(9.4f, 18.1f, 7.1f, 16.3f, 6.3f, 13.9f)
    lineTo(2.9f, 13.9f); lineTo(2.9f, 16.5f); cubicTo(4.6f, 19.9f, 8f, 22f, 12f, 22f); close()
    moveTo(6.3f, 13.9f); cubicTo(6.1f, 13.3f, 6f, 12.6f, 6f, 12f); cubicTo(6f, 11.4f, 6.1f, 10.7f, 6.3f, 10.1f)
    lineTo(6.3f, 7.5f); lineTo(2.9f, 7.5f); cubicTo(2.3f, 8.9f, 2f, 10.4f, 2f, 12f)
    cubicTo(2f, 13.6f, 2.3f, 15.1f, 2.9f, 16.5f); close()
    moveTo(12f, 5.9f); cubicTo(13.5f, 5.9f, 14.8f, 6.4f, 15.9f, 7.4f); lineTo(18.8f, 4.5f)
    cubicTo(17f, 2.9f, 14.7f, 2f, 12f, 2f); cubicTo(8f, 2f, 4.6f, 4.1f, 2.9f, 7.5f)
    lineTo(6.3f, 10.1f); cubicTo(7.1f, 7.7f, 9.4f, 5.9f, 12f, 5.9f); close()
}

private fun facebookPath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22f, 12f); cubicTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); cubicTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
    cubicTo(2f, 16.84f, 5.44f, 20.87f, 10.44f, 21.88f); lineTo(10.44f, 14.89f); lineTo(7.9f, 14.89f)
    lineTo(7.9f, 12f); lineTo(10.44f, 12f); lineTo(10.44f, 9.8f)
    cubicTo(10.44f, 7.29f, 11.93f, 5.9f, 14.22f, 5.9f); cubicTo(15.32f, 5.9f, 16.46f, 6.1f, 16.46f, 6.1f)
    lineTo(16.46f, 8.56f); lineTo(15.2f, 8.56f); cubicTo(13.96f, 8.56f, 13.57f, 9.33f, 13.57f, 10.12f)
    lineTo(13.57f, 12f); lineTo(16.35f, 12f); lineTo(15.9f, 14.89f); lineTo(13.57f, 14.89f)
    lineTo(13.57f, 21.88f); cubicTo(18.56f, 20.87f, 22f, 16.84f, 22f, 12f); close()
}

// ─────────────────────────────────────────────────────────────────────────────
// ⑥ Chrome — the mock's 54 / 28 are placeholders for the real safe-area insets
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The launch-screen scaffold: backdrop at the bottom of the z-order, content above it, and the
 * safe-area insets applied to the content only — so the backdrop runs full-bleed behind the status
 * bar and home indicator with no seam, which is an acceptance criterion on both 140 and 142.
 */
@Composable
fun WelcomeScaffold(
    modifier: Modifier = Modifier,
    peachWash: Boolean = true,
    topWeighted: Boolean = false,
    orangeAlpha: Float = 0.32f,
    violetAlpha: Float = 0.28f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Cream)) {
        WelcomeBackdrop(
            peachWash = peachWash, topWeighted = topWeighted,
            orangeAlpha = orangeAlpha, violetAlpha = violetAlpha,
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            content = content,
        )
    }
}
