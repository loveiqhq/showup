/*
 * ReasonBadge.kt
 * ShowUp · the reference's `ReasonBadge`, built once (SHOWUP-194, Date 02)
 *
 * The Date screen's unread card and the chat's bubbles both draw it. `onLight` is the reference's own
 * switch: the tone's tint on a light panel, white over the tone's own fill (a sent bubble).
 */
package com.showup.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Manrope
import com.showup.welcome.Icon

/** Uppercase reason label with its icon, in the tone's tint: pad 3 9 3 7, gap 5, Manrope 800 10.5. */
@Composable
fun ReasonBadge(reason: ChatReason, onLight: Boolean) {
    val tone = reason.tone
    Row(
        Modifier
            .background(if (onLight) tone.bg else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(reason.icon, 11.dp, tint = if (onLight) tone.fg else Color.White, strokeWidth = 2.5f)
        Spacer(Modifier.width(5.dp))
        Text(
            reason.label.uppercase(),
            color = if (onLight) tone.fg else Color.White,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp,
            letterSpacing = 0.05.sp,
            maxLines = 1,
        )
    }
}
