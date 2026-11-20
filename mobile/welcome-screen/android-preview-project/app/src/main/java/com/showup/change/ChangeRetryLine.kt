/*
 * ChangeRetryLine.kt
 * ShowUp · the red line that is also the retry button (Date 02 J, Date 05 L)
 *
 * One component for the failed message's `Couldn't send.` and the thread card's `Couldn't agree.`; Date 05's
 * request card on the Date screen reuses it for the same line. `alert-circle` 14 / 2.4, gap 6, 12 / 600 / 1.35
 * in the danger ink, the sentence at most 280 wide, the line at least 44 high (drawn smaller, answering at the
 * floor). It announces itself as `role="alert"` does, and a tap anywhere on it retries.
 */
package com.showup.change

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Manrope
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** [end]: the text aligns right, under a right-aligned bubble; false under a card's buttons. */
@Composable
fun ChangeRetryLine(message: String, onRetry: () -> Unit, tag: String, modifier: Modifier = Modifier, end: Boolean = false) {
    val style = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = (12 * 1.35).sp,
        color = DangerFg, textAlign = if (end) TextAlign.End else TextAlign.Start,
    )
    OverflowTapTarget(
        modifier = modifier,
        touch = Modifier
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onRetry)
            .semantics {
                contentDescription = message
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Row(
            Modifier.widthIn(max = 280.dp).heightIn(min = 44.dp).clearAndSetSemantics {}.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BrandIcon.AlertCircle, 14.dp, tint = DangerFg, strokeWidth = 2.4f)
            val line = AnnotatedString(message)
            Text(line, modifier = Modifier.prettyWrap(line, style).cssLineBox(style.lineHeight), style = style)
        }
    }
}
