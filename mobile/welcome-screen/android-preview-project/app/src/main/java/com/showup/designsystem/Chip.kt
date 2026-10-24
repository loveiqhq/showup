/*
 * Chip.kt
 * ShowUp · the kit's `Chip` (`components/shared.jsx`): a read-only pill for a vibe, an interest or a tag
 *
 * NOT [com.showup.profile.InterestChip], which is the profile flow's PICKER -- violet when picked, a
 * radio or a toggle, with a tap area and a press scale. This one shows a value: Search draws every
 * vibe and interest of someone else's profile with it, and marks the shared ones (`SharedChip`).
 * Two components because they are two jobs; one with a mode would carry the picker's touch and
 * press machinery onto a chip nobody may press.
 *
 * Sizes, tones and weights are the kit's, unchanged:
 *   xs 26 / 11.5 / 10 / 5 · sm 30 / 13 / 12 / 6 · md 36 / 14 / 14 / 8   (height / type / side / gap)
 *   orange   idle #fff + 1px --liq-border       selected rgba(254,104,57,.10) + 1px orange, ink orange
 *   lavender idle #fff + 1px --liq-border       selected violet, ink white
 *   soft     idle --liq-bg-raised, no border   selected violet, ink white
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChipTone { Orange, Lavender, Soft }

enum class ChipSize(val height: Dp, val fontSize: TextUnit, val sidePadding: Dp, val gap: Dp) {
    Xs(26.dp, 11.5.sp, 10.dp, 5.dp),
    Sm(30.dp, 13.sp, 12.dp, 6.dp),
    Md(36.dp, 14.sp, 14.dp, 8.dp),
}

/** `rgba(254,104,57,.10)` -- the orange tone's selected fill. A fill of the chip's own. */
private val OrangeChipFill = Orange.copy(alpha = 0.10f)

/**
 * @param leading drawn before the label, in the chip's ink -- `SharedChip`'s check.
 *
 * The height is a MINIMUM, not a fixed box: at the largest system font the label grows, and a fixed
 * 36 would cut it. The kit's `white-space: nowrap` holds wherever the label fits the row; a label
 * wider than the whole row -- the largest type on a narrow phone -- WRAPS onto a second line and the
 * pill grows taller, rather than being cut off with an ellipsis (the Search fit sweep, 22 Oct 2026).
 */
@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: ChipTone = ChipTone.Orange,
    size: ChipSize = ChipSize.Md,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val (fill, ink, stroke) = when {
        tone == ChipTone.Orange && selected -> Triple(OrangeChipFill, Orange, Orange)
        tone == ChipTone.Soft && !selected -> Triple(Raised, Fg, Color.Transparent)
        selected -> Triple(Purple, Elevated, Purple)
        else -> Triple(Elevated, Fg, Border)
    }
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .heightIn(min = size.height)
            .background(fill, shape)
            .border(1.dp, stroke, shape)
            // A wrapped label keeps a little air above and below; one line is centred in the 36.
            .padding(horizontal = size.sidePadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(ink)
        Text(
            label,
            color = ink, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = size.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}
