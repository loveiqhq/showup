/*
 * CancelGuide.kt
 * ShowUp · the two thread items at the foot of the chat (SHOWUP-194): `CancelGuide` (D) and
 * `EndDateCard` (E), from `screen-chat-reference.jsx`, with its numbers
 *
 * Neither is a message: the guide is local to this phone and never sent, and the card is drawn for
 * both people once an "I can't make it today" message is in the thread. [ThreadScroll] places them at
 * the foot and scrolls them into view.
 */
package com.showup.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.date.DrawnSmall
import com.showup.date.IconTile
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The guide's and the card's paragraphs: 12.5 / 1.38, muted, wrapped as `textWrap: pretty`. */
private val ItemText = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp, lineHeight = (12.5 * 1.38).sp, color = Muted)

/**
 * D. Waiting for `Got it`: the dark-red 9% card with its 28% border 1.5, radius 16, pad 10 12, gap 5 --
 * the frown tile 26 / 8, the title 14.5 / 700 in dark red, the two paragraphs with their bold runs, and
 * `Got it` full width, at least 40 (answering at 48), 3 below the text. After it: one centred line,
 * `check` 12 / 2.6 and the reminder, 12 / 600 subtle.
 */
@Composable
internal fun CancelGuide(name: String, acked: Boolean, onAck: () -> Unit) {
    val tone = ReasonTone.DarkRed
    if (acked) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(BrandIcon.Check, 12.dp, tint = FgSubtle, strokeWidth = 2.6f)
            Spacer(Modifier.width(6.dp))
            Text(
                ChatCopy.GUIDE_ACKED,
                color = FgSubtle,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(tone.bg, shape)
            .border(1.5.dp, tone.chipBorder, shape)
            // Pad 10 12 plus the 1.5 border, which CSS counts in the box.
            .padding(horizontal = 13.5.dp, vertical = 11.5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(26.dp, 8.dp, SolidColor(Elevated), BrandIcon.Frown, 14.dp, tone.fg, 2.3f)
            Spacer(Modifier.width(8.dp))
            Text(ChatCopy.GUIDE_TITLE, color = tone.fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
        }
        Paragraph(withBold(ChatCopy.GUIDE_1.format(name), ChatCopy.GUIDE_1_BOLD))
        Paragraph(withBold(ChatCopy.GUIDE_2, ChatCopy.GUIDE_2_BOLD))
        DrawnSmall(ChatCopy.GOT_IT, onAck, Modifier.padding(top = 3.dp).fillMaxWidth()) { press ->
            Box(
                press
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .background(tone.fg, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ChatCopy.GOT_IT, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
        }
    }
}

/**
 * E. `Not meeting today?` and the line beside `End date`: ink 4% fill, radius 16, pad 11 13, gap 10;
 * the button white with the dark-red chip border 1.5, at least 40 (answering at 48), pad 0 14, 13.5 / 700.
 */
@Composable
internal fun EndDateCard(onEnd: () -> Unit) {
    val tone = ReasonTone.DarkRed
    Row(
        Modifier
            .fillMaxWidth()
            .background(Fg.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append(ChatCopy.CARD_LEAD) }
            append(" ")
            append(ChatCopy.CARD_BODY)
        }
        Text(
            text,
            modifier = Modifier.weight(1f).prettyWrap(text, ItemText).cssLineBox(ItemText.lineHeight),
            style = ItemText,
        )
        Spacer(Modifier.width(10.dp))
        DrawnSmall(ChatCopy.END_DATE, onEnd, touch = Modifier.testTag(CHAT_END_CARD_BUTTON_TAG)) { press ->
            val pill = RoundedCornerShape(50)
            Box(
                press
                    .heightIn(min = 40.dp)
                    .background(Elevated, pill)
                    .border(1.5.dp, tone.chipBorder, pill)
                    .padding(horizontal = 15.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(ChatCopy.END_DATE, color = tone.fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Paragraph(text: androidx.compose.ui.text.AnnotatedString) {
    Text(text, modifier = Modifier.prettyWrap(text, ItemText).cssLineBox(ItemText.lineHeight), style = ItemText)
}
