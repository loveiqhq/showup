/*
 * EndDateBar.kt
 * ShowUp · A, the End Date bar (SHOWUP-197), from `DateStatusBar` in `screen-wrap-report-reference.jsx`
 *
 * The footer slot of the Date screen once the person has checked in. ONE state: the kit's "Date complete ·
 * Browsing's unlocked · Rate" variant is deleted, because Search stays blocked until the review.
 *
 * NO BACKDROP BLUR. `rgba(255,251,247,.96)` + `blur(20px)` is composited here: the tint at 96 % over [Cream],
 * opaque, so what scrolls behind the bar never shows through it (the 193 rule for glass bars).
 */
package com.showup.date.end

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.date.DrawnSmall
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

const val END_BAR_TAG = "end:bar"
const val END_BAR_TILE_TAG = "end:bar-tile"
const val END_BAR_BUTTON_TAG = "end:bar-button"

/** `rgba(255,251,247,.96)` over the cream, as one opaque colour. */
private val BarFill = Color(0xF5FFFBF7).compositeOver(Cream)

private val BarTitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Fg)
private val BarLine = TextStyle(fontFamily = Manrope, fontSize = 11.5.sp, lineHeight = (11.5 * 1.35).sp, color = Muted)
private val BarButton = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Purple)

/**
 * Padding 10 14 under a 1 px `border-top` (the soft border), the row centred, gap 12: the calendar tile (38,
 * radius 12, violet 10 %, `calendar` 19 / 2.1), the text (13.5 / 700 over 11.5 / 1.35 muted, pretty) and
 * `End Date` (h 40, pad 0 16, pill, white, 1.5 violet border, 14 / 700, nowrap), drawn at 40 and answering at 44.
 */
@Composable
fun EndDateBar(name: String, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .testTag(END_BAR_TAG)
            .background(BarFill)
            .drawBehind { drawLine(BorderSoft, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            // 10 14, and the 1 px rule above the content: CSS counts a border in the box.
            .padding(start = 14.dp, top = 11.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.testTag(END_BAR_TILE_TAG).size(38.dp).background(Purple.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(BrandIcon.Calendar, 19.dp, tint = Purple, strokeWidth = 2.1f) }
        Column(Modifier.weight(1f)) {
            Text(DateEndCopy.BAR_TITLE.format(name), style = BarTitle)
            val line = AnnotatedString(DateEndCopy.BAR_LINE)
            Text(line, Modifier.prettyWrap(line, BarLine).cssLineBox(BarLine.lineHeight), style = BarLine)
        }
        DrawnSmall(
            label = DateEndCopy.BAR_BUTTON,
            onClick = onEnd,
            touch = Modifier.testTag(END_BAR_BUTTON_TAG),
        ) { press ->
            Box(
                press
                    .heightIn(min = 40.dp)
                    .background(Elevated, RoundedCornerShape(50))
                    .border(1.5.dp, Purple, RoundedCornerShape(50))
                    // Pad 0 16 plus the 1.5 border CSS counts in the width.
                    .padding(horizontal = 17.5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(DateEndCopy.BAR_BUTTON, style = BarButton, maxLines = 1, softWrap = false) }
        }
    }
}
