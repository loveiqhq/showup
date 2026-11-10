package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

private val SheetErrorText = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.5.sp,
    lineHeight = (13.5 * 1.4).sp,
    color = Fg,
)

/**
 * What a sheet's action could not do, inside the sheet above its buttons (SHOWUP-194 K, 9 Nov 2026; the
 * reference names Date 03's no-location error as the same box): the danger 8% box under a 1 px danger
 * 20% rule, radius 14, pad 11 13, `alert-circle` 17 / 2.2 in the danger ink 1 down, gap 9, and the
 * sentence 13.5 / 600 / 1.4 in the ink. It announces itself, as `role="alert"` does.
 *
 * NOT [InlineErrorCard]: the profile flow's card is 7% / 18%, radius 12, a filled glyph and the sentence
 * in the danger ink -- the date flow's sheets draw this one.
 */
@Composable
fun SheetErrorBox(message: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
            .background(Danger.copy(alpha = 0.08f), shape)
            .border(1.dp, Danger.copy(alpha = 0.20f), shape)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.AlertCircle, 17.dp, tint = DangerFg, strokeWidth = 2.2f) }
        val text = AnnotatedString(message)
        Text(
            text,
            modifier = Modifier.weight(1f).prettyWrap(text, SheetErrorText).cssLineBox(SheetErrorText.lineHeight),
            style = SheetErrorText,
        )
    }
}
