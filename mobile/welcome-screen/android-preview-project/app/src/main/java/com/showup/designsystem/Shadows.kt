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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

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
): Modifier = drawBehind {
    val sigma = blur.toPx() / 2f
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = fill.toArgb()
        setShadowLayer(radius, 0f, offsetY.toPx(), shadow.toArgb())
    }
    val r = cornerRadius.toPx()
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint) }
}
