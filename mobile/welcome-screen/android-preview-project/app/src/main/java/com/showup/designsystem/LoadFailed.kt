package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

private val LoadFailedText = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = (15 * 1.45).sp,
    color = Muted,
    textAlign = TextAlign.Center,
)

/**
 * A screen's first load failed (the Date screen, SHOWUP-193, and the chat, SHOWUP-194 I; 9 Nov 2026):
 * centred in what it is given, gap 14, pad 0 32 -- the violet 10% tile 56 / r18 with `refresh` 24 / 2.1,
 * the sentence 15 / 1.45 / 500 muted at most 290, and `Try again`, the kit's primary md button (flat
 * orange under the CTA glow, h 48, pad 22, 15 / 700) 4 further down, as wide as its label.
 *
 * NOT Search's `CentredStatement`, a ghost button and no tile: "the same pattern as Search 01" is the
 * behaviour -- the sentence and Try again in place of the content -- and both references draw this.
 *
 * Centred where it fits, and scrolling where it does not: in the top half of a split screen at the largest
 * type the column is taller than the room, and Try again was cut (the 9 Nov 2026 fit sweep).
 *
 * [busy]: the load is on its way again. The sentence stays and Try again does nothing until it answers
 * ("while it loads, the button is inert"); a screen that blanked meanwhile would flash.
 */
@Composable
fun LoadFailed(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clearAndSetSemantics {}.background(Purple.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(BrandIcon.Refresh, 24.dp, tint = Purple, strokeWidth = 2.1f) }
            val sentence = AnnotatedString(text)
            Text(
                sentence,
                modifier = Modifier.widthIn(max = 290.dp).prettyWrap(sentence, LoadFailedText, centered = true).cssLineBox(LoadFailedText.lineHeight),
                style = LoadFailedText,
            )
            PrimaryButton(
                action,
                onClick = { if (!busy) onAction() },
                modifier = Modifier.padding(top = 4.dp).width(IntrinsicSize.Max),
                variant = PrimaryButtonVariant.Orange,
                height = 48.dp,
                labelSize = 15.sp,
                horizontalPadding = 22.dp,
            )
        }
    }
}
