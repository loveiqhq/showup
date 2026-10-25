/*
 * PremiumTag.kt
 * ShowUp · the kit's `PremiumTag` (`screens-premium.jsx`), the label a Premium user's top bar carries
 *
 * Built with the Premium grid (SHOWUP-185), which puts it in the Search top bar at `size={10}`, and
 * used again by the Likes tab (SHOWUP-186). The kit file is not in the handoff folder; its source was
 * read from the spec sheets' own bundle, where both sheets render it.
 *
 * At [size] s: padding `s/2  s  s/2  0.82s`, gap `0.55s`, the sunset, Manrope 800 at s with
 * `letter-spacing: 0.06` (React writes a number as px) and capitals; `--liq-shadow-violet` under it,
 * a 1px inner highlight along the top (`inset 0 1px 0 rgba(255,255,255,.55)`), a soft violet inner
 * shade along the bottom (`inset 0 -1px 2px rgba(129,42,236,.35)`), and the glossy top-half sheen
 * (`linear-gradient(180deg, rgba(255,255,255,.45) 0%, rgba(255,255,255,.08) 42%, transparent 60%)`).
 * The sparkles icon is `s + 2` at stroke 2.4.
 *
 * A LABEL, NOT A CONTROL (Search 02: "PremiumTag is a label, not a control"). A screen reader reads
 * the word as written, `Premium`, not the capitals it is drawn in.
 */
package com.showup.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The word, as written. Drawn in capitals (`text-transform: uppercase`). */
const val PREMIUM_TAG_LABEL = "Premium"

/** The sheen over the top half. */
private val TagSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.45f),
    0.42f to Color.White.copy(alpha = 0.08f),
    0.60f to Color.Transparent,
)

/** `inset 0 1px 0 rgba(255,255,255,.55)`. */
private val TagHighlight = Color.White.copy(alpha = 0.55f)

/** `inset 0 -1px 2px rgba(129,42,236,.35)`. */
private val TagShade = Purple.copy(alpha = 0.35f)

/** The icon's `drop-shadow(... rgba(129,42,236,.4))`. */
private val IconShadow = Purple.copy(alpha = 0.4f)

/**
 * @param size the kit's `size`: 10 in the Search top bar. The type follows it in dp-sized `sp` so
 *   the tag keeps its drawn proportions at every system font -- it is a mark, like the wordmark,
 *   and its one word is read out in full.
 */
@Composable
fun PremiumTag(size: Float = 11f, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val type = with(density) { size.dp.toSp() }
    val sunset = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())
    Row(
        modifier
            .pillShadows(sunset, ShadowViolet)
            .drawWithContent {
                val r = size.dp.toPx() * 50f
                val pill = RoundRect(0f, 0f, this.size.width, this.size.height, CornerRadius(minOf(r, this.size.height / 2f)))
                val shape = Path().apply { addRoundRect(pill) }
                clipPath(shape) {
                    drawRect(TagSheen)
                    // Each inset shadow is the pill minus itself moved by the shadow's offset: the
                    // sliver along the edge the shadow falls on.
                    val px = 1.dp.toPx()
                    val down = Path().apply { addRoundRect(pill.translate(Offset(0f, px))) }
                    drawPath(Path.combine(PathOperation.Difference, shape, down), TagHighlight)
                    val up = Path().apply { addRoundRect(pill.translate(Offset(0f, -px))) }
                    val shade = Path.combine(PathOperation.Difference, shape, up)
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { color = TagShade }
                        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(cssBlurToShadowRadius(2.dp.toPx()), BlurMaskFilter.Blur.NORMAL)
                        canvas.drawPath(shade, paint)
                    }
                }
                drawContent()
            }
            .padding(start = (size * 0.82f).dp, end = size.dp, top = (size * 0.5f).dp, bottom = (size * 0.5f).dp)
            .clearAndSetSemantics { contentDescription = PREMIUM_TAG_LABEL },
        horizontalArrangement = Arrangement.spacedBy((size * 0.55f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `filter: drop-shadow(0 1px 1px rgba(129,42,236,.4))`: the icon again, 1 lower, violet,
        // softened by its 1px blur (sigma 0.5) where the platform can blur.
        Box {
            Box(Modifier.offset(y = 1.dp).blur(0.5.dp)) {
                Icon(BrandIcon.Sparkles, (size + 2f).dp, tint = IconShadow, strokeWidth = 2.4f)
            }
            Icon(BrandIcon.Sparkles, (size + 2f).dp, tint = Color.White, strokeWidth = 2.4f)
        }
        Text(
            PREMIUM_TAG_LABEL.uppercase(),
            color = Color.White,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = type,
            lineHeight = type,
            letterSpacing = with(density) { 0.06.dp.toSp() },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.cssLineBox(type),
        )
    }
}
