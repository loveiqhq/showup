/*
 * EndDateSheet.kt
 * ShowUp · F, the End the date sheet (SHOWUP-194), from `screen-chat-reference.jsx`, with its numbers
 *
 * On the kit's `ModalShell`. `Keep the date` comes first and `End date` second, so a quick first tap
 * never ends a date; the scrim, back and a swipe all keep it.
 */
package com.showup.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.pressable
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.ModalShell
import com.showup.designsystem.Muted
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

private val SheetTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = (26 * 1.15).sp,
    letterSpacing = (-0.01).em, color = Fg, textAlign = TextAlign.Center,
)
private val SheetBody = TextStyle(
    fontFamily = Manrope, fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Muted, textAlign = TextAlign.Center,
)

/**
 * Pad 14 24 24, centred: the dark-red 9% circle 80 with `x` 34 / 2.4, the title (Lora 700 26, 16 above,
 * 8 below, at most 310), the line (15 / 1.5 muted, at most 300, 22 below), `Keep the date` (h 50, radius
 * 16, white with the soft border, 14.5 / 700 muted) and `End date` (10 below, h 56, radius 18, dark red,
 * 16.5 / 700 white). While it ends, `End date` waits; if it fails, the sheet says so and stays.
 */
@Composable
internal fun EndDateSheet(name: String, ending: Boolean, failed: Boolean, onKeep: () -> Unit, onEnd: () -> Unit) {
    val tone = ReasonTone.DarkRed
    val title = ChatCopy.SHEET_TITLE.format(name)
    ModalShell(
        label = title,
        onDismiss = { onKeep() },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().testTag(CHAT_SHEET_TAG), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 8.dp).size(80.dp).background(tone.bg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(BrandIcon.Close, 34.dp, tint = tone.fg, strokeWidth = 2.4f) }
            Spacer(Modifier.height(16.dp))
            val heading = AnnotatedString(title)
            Text(
                heading,
                modifier = Modifier.widthIn(max = 310.dp).prettyWrap(heading, SheetTitle, centered = true).cssLineBox(SheetTitle.lineHeight),
                style = SheetTitle,
            )
            Spacer(Modifier.height(8.dp))
            val body = AnnotatedString(ChatCopy.SHEET_BODY)
            Text(
                body,
                modifier = Modifier.widthIn(max = 300.dp).prettyWrap(body, SheetBody, centered = true).cssLineBox(SheetBody.lineHeight),
                style = SheetBody,
            )
            if (failed) {
                Text(
                    ChatCopy.END_FAILED,
                    modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
                    color = DangerFg,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
            val keepShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .testTag(END_SHEET_KEEP_TAG)
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .pressable(Role.Button, onClick = onKeep)
                    .background(Elevated, keepShape)
                    .border(1.dp, BorderSoft, keepShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(ChatCopy.KEEP_THE_DATE, color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .testTag(END_SHEET_END_TAG)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .pressable(Role.Button, onClick = { if (!ending) onEnd() })
                    .background(tone.fg.copy(alpha = if (ending) 0.6f else 1f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ChatCopy.END_DATE, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
            }
        }
    }
}
