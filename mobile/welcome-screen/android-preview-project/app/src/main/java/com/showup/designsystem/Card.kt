/*
 * Card.kt
 * ShowUp · the kit's `Card` (`components/shared.jsx`), the surface every Search section sits on
 *
 * `background: var(--liq-bg-elevated); border-radius: 20; border: 1px solid var(--liq-border-soft);
 * box-shadow: var(--liq-shadow-md)` -- and the reference overrides fill, border and padding per
 * section (the shared vibes row's 1.5 orange border, the highlight's blush gradient), which is what
 * the parameters are. Built with Date search 01 (SHOWUP-184), the first screen made of cards.
 *
 * A card FILLS ITS CONTAINER'S WIDTH, as the kit's block `div` does. Wrapping its content instead
 * looked right on every card holding something full width -- a photo, a prompt, the essentials --
 * and drew the vibe cards as narrow boxes hugging their chips (the 184 evidence frames, 24 Oct 2026).
 */
package com.showup.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * The kit card's own corner, 20. NOT [Radius.card] (16), which is the method-list container: two
 * surfaces with two radii in the design, and folding them together would move one of them.
 */
private val CARD_RADIUS = 20.dp

/**
 * @param fill `--liq-bg-elevated` by default; a section may pass a gradient (the highlight's blush).
 * @param border 1px `--liq-border-soft` by default; null draws none.
 * @param shadows `--liq-shadow-md` by default; empty draws none.
 * @param contentPadding the reference's `padding` for the section, 16 for most.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    fill: Brush = SolidColor(Elevated),
    border: BorderStroke? = BorderStroke(1.dp, BorderSoft),
    shadows: List<CssShadow> = ShadowMd,
    contentPadding: PaddingValues = PaddingValues(Spacing.xxl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    Column(
        modifier
            .fillMaxWidth()
            .roundedShadows(fill, CARD_RADIUS, shadows)
            // CSS draws the border INSIDE the box, over the fill, so it is applied after it here.
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
