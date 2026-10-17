/*
 * Shadows.kt
 * ShowUp · a CSS `box-shadow` on a rounded rectangle, drawn rather than cast
 *
 * WHY NOT `Modifier.shadow`
 *
 * It is elevation: Android lights it from a virtual source at the top-centre of the WINDOW, so the
 * shadow's direction depends on where the element sits on screen, and its colour comes from the
 * theme rather than the token. The design's shadows are violet-tinted glows pushed straight down
 * ("shadows are violet- or orange-tinted, never grey"), which elevation cannot draw. The round CTA
 * already learned this -- see `ctaGlow` in WelcomeShell.kt, which does the same for circles.
 *
 * THE BLUR IS CONVERTED, NOT GUESSED
 *
 * CSS's blur radius B is a Gaussian with sigma B / 2. Android's shadow layer takes a radius r that
 * Skia turns into sigma = 0.57735 r + 0.5. Solving for r keeps the softness the reference draws on
 * every density instead of whatever looked close on one device.
 */
package com.showup.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fills this node with [fill] as a rounded rectangle of [cornerRadius], with a `box-shadow` of
 * [shadow] offset [offsetY] down and blurred by [blur] -- the CSS values, unchanged.
 *
 * THE FILL IS PART OF IT, deliberately: a shadow layer on a transparent paint is not drawn on every
 * API level, so the shape and its shadow are one call. Use it in place of `background`, not with it.
 */
fun Modifier.boxShadow(
    fill: Color,
    cornerRadius: Dp,
    shadow: Color,
    offsetY: Dp,
    blur: Dp,
): Modifier = drawBehind { drawShadowed(fill, cornerRadius.toPx(), shadow, offsetY, blur) }

/**
 * [boxShadow] on a fully round pill -- CSS `border-radius: 9999px` -- whatever height the node
 * turns out to be.
 *
 * A fixed radius is right only at the height it was chosen for: a chip that grows at the largest
 * system font would keep a 19 radius on a taller box while its border, drawn with a 50% shape,
 * stayed round, and the glow and the outline would disagree. Half the measured height cannot.
 */
fun Modifier.pillShadow(
    fill: Color,
    shadow: Color,
    offsetY: Dp,
    blur: Dp,
): Modifier = drawBehind { drawShadowed(fill, size.height / 2f, shadow, offsetY, blur) }

/**
 * `box-shadow: 0 0 0 4px <colour>` -- a flat ring OUTSIDE the border box, the focus and error halo
 * of the multi-line fields (the prompt editor, Check-In 06's uplift).
 *
 * Drawn outside the node's own bounds rather than inside, so a field does not shrink when it gains
 * focus. THAT IS ALSO WHY IT MUST COME BEFORE ANY `clip` in the chain: a clip earlier in the chain
 * cuts everything outside the shape away, the halo with it. The prompt editor shipped exactly that
 * order, and its focus ring never appeared on Android.
 */
fun Modifier.halo(color: Color, radius: Dp, spread: Dp = 4.dp): Modifier = drawBehind {
    val s = spread.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-s, -s),
        size = Size(size.width + 2 * s, size.height + 2 * s),
        cornerRadius = CornerRadius(radius.toPx() + s),
    )
}

/** One layer of a CSS `box-shadow` with no x offset and no spread: its colour, its y and its blur. */
data class CssShadow(val color: Color, val offsetY: Dp, val blur: Dp)

/**
 * `--liq-shadow-violet`, the sunset and violet CTAs' glow, as the token file writes it:
 * `0 8px 20px rgba(129,42,236,.28), 0 2px 6px rgba(129,42,236,.18)`.
 */
val ShadowViolet = listOf(
    CssShadow(Purple.copy(alpha = 0.28f), offsetY = 8.dp, blur = 20.dp),
    CssShadow(Purple.copy(alpha = 0.18f), offsetY = 2.dp, blur = 6.dp),
)

/**
 * A full pill filled with [fill] -- a gradient or a colour -- under a MULTI-LAYER `box-shadow`.
 *
 * Each layer is the pill drawn again with its own shadow, the last-listed first so CSS's first
 * layer ends on top. The fill is the real fill every time rather than a stand-in colour, so the
 * antialiased edge of the shape carries no fringe of some other colour; the last pass is the one
 * that shows. Use it in place of `background`, like [boxShadow].
 */
fun Modifier.pillShadows(fill: Brush, shadows: List<CssShadow>): Modifier = drawBehind {
    val r = size.height / 2f
    shadows.asReversed().forEach { layer ->
        val sigma = layer.blur.toPx() / 2f
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            when (fill) {
                is SolidColor -> color = fill.value.toArgb()
                is ShaderBrush -> { color = android.graphics.Color.BLACK; shader = fill.createShader(size) }
                else -> color = android.graphics.Color.BLACK
            }
            setShadowLayer(radius, 0f, layer.offsetY.toPx(), layer.color.toArgb())
        }
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShadowed(
    fill: Color,
    r: Float,
    shadow: Color,
    offsetY: Dp,
    blur: Dp,
) {
    val sigma = blur.toPx() / 2f
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = fill.toArgb()
        setShadowLayer(radius, 0f, offsetY.toPx(), shadow.toArgb())
    }
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}
