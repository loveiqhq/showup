package com.showup.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A drawn tick, on a 24 grid like every other icon in this app.
 *
 * Drawn rather than a font glyph or a vector asset, for the same reason `BrandIcon` is: a stroked
 * path scales cleanly at any size and takes its weight from a parameter, where a glyph takes it
 * from whatever the font decides. Two sizes are in use -- 13 inside the field's success pill and 16
 * on the email helper line -- and they are the same shape at two weights.
 */
@Composable
fun CheckGlyph(
    size: Dp,
    color: Color,
    strokeWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val path = Path().apply {
            moveTo(5f * s, 12f * s)
            lineTo(10f * s, 17f * s)
            lineTo(19f * s, 7f * s)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * The round danger badge with an "!" in it.
 *
 * Two sizes ship: 22 inline in a field, 18 in the error card. The glyph is Lora rather than Manrope
 * -- the serif "!" has the weight the design draws, and it is the same choice the phone screen's
 * error glyph already makes.
 */
@Composable
fun DangerGlyph(
    size: Dp,
    modifier: Modifier = Modifier,
    glyphSize: Dp = size * 0.59f,
) {
    Box(
        modifier.size(size).background(Danger, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "!",
            color = Elevated,
            fontFamily = Lora,
            fontWeight = FontWeight.Bold,
            fontSize = glyphSize.value.sp,
        )
    }
}
