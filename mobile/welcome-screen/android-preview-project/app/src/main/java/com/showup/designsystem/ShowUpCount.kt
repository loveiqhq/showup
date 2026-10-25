/*
 * ShowUpCount.kt
 * ShowUp · the Show-up display: a count of dates attended, never a percentage (SHOWUP-184)
 *
 * NEW SHARED COMPONENT, "next to Chip" as the ticket puts it: every Show-up display moves onto this
 * one, and the percentage `ShowUpPill` stays only on screens not yet re-ticketed (epic rule 4).
 * `N dates attended` counts dates both people confirmed; 0 reads `New in the App` whether the person
 * is new or has had no confirmed date yet. A missed date is never counted, shown or recorded.
 *
 * The reference's `ShowUpCount`, unchanged: an inline pill, gap 6, Manrope 700 at line-height 1,
 * the number at 800. `New` is 10% violet on violet with `sparkles`; a count is the kit's `good` tone
 * (`showUpTone(100)`: 10% green on #0A7A47) with `shield-check`. The icon is the type size + 1.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

enum class ShowUpCountSize(val fontSize: TextUnit, val horizontal: Dp, val vertical: Dp) {
    /** `4px 9px` at 11 -- the meet card's. */
    Xs(11.sp, 9.dp, 4.dp),
    Sm(12.sp, 12.dp, 7.dp),
    Md(12.5.sp, 12.dp, 7.dp),
}

/** The zero state's words: "whether the user is new or has had no confirmed date yet". */
const val SHOW_UP_NEW = "New in the App"

/** `N dates attended` · `1 date attended` · `New in the App`. Never a percentage. */
fun showUpCountLabel(count: Int): String = when {
    count <= 0 -> SHOW_UP_NEW
    count == 1 -> "1 date attended"
    else -> "$count dates attended"
}

/** `rgba(129,42,236,.10)`, the `New in the App` fill. */
private val NewFill = Purple.copy(alpha = 0.10f)

/** `showUpTone(100).softBg`, `rgba(0,171,85,.10)` -- a count's fill. Its ink is [SuccessFg]. */
private val CountFill = Success.copy(alpha = 0.10f)

/** The dark variant's `New in the App`: `rgba(255,255,255,.92)`. */
private val DarkNewFill = Elevated.copy(alpha = 0.92f)

/**
 * @param dark `variant="dark"`, over a photo (the Premium grid card): a count on `showUpTone(100)`'s
 *   dark pill, `New in the App` on white at .92, both over a backdrop blur of 8.
 * @param glass what that blur sees -- the photo under the pill, drawn again and blurred by the
 *   caller, who knows the photo. Behind the fill, clipped to the pill.
 */
@Composable
fun ShowUpCount(
    count: Int,
    modifier: Modifier = Modifier,
    size: ShowUpCountSize = ShowUpCountSize.Md,
    dark: Boolean = false,
    glass: (@Composable (Modifier) -> Unit)? = null,
) {
    val isNew = count <= 0
    val ink = when {
        isNew -> Purple
        dark -> ShowUpDarkInk
        else -> SuccessFg
    }
    val fill = when {
        dark && isNew -> DarkNewFill
        dark -> ShowUpDarkFill
        isNew -> NewFill
        else -> CountFill
    }
    val label = showUpCountLabel(count)
    // The icon follows the type, as the reference's `fs + 1` does, so it scales with the font.
    val iconSize = with(LocalDensity.current) { (size.fontSize.value + 1).sp.toDp() }
    val pill = RoundedCornerShape(percent = 50)
    Box(modifier.clip(pill)) {
        glass?.invoke(Modifier.matchParentSize())
        ShowUpCountRow(count, isNew, ink, label, iconSize, size, Modifier.background(fill, pill))
    }
}

@Composable
private fun ShowUpCountRow(
    count: Int,
    isNew: Boolean,
    ink: Color,
    label: String,
    iconSize: Dp,
    size: ShowUpCountSize,
    modifier: Modifier,
) {
    Row(
        modifier
            .padding(horizontal = size.horizontal, vertical = size.vertical)
            // One node for a screen reader: the sentence, not an icon and two runs of text.
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (isNew) BrandIcon.Sparkles else BrandIcon.ShieldCheck, iconSize, tint = ink, strokeWidth = 2.2f)
        Text(
            if (isNew) {
                buildAnnotatedString { append(label) }
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(count.toString()) }
                    append(label.removePrefix(count.toString()))
                }
            },
            color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = size.fontSize, lineHeight = size.fontSize, maxLines = 1, softWrap = false,
            // `line-height: 1` as CSS lays it out: the pill is the icon and its padding, not
            // Manrope's natural line -- Compose's own box made every count taller than the kit's.
            modifier = Modifier.cssLineBox(size.fontSize),
        )
    }
}
