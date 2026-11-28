/*
 * PartnerEndedScreen.kt
 * ShowUp · Date 08, what the person who did NOT end the date sees: one full screen, three payloads (SHOWUP-199)
 *
 * Why this is not `DateEndedScreen` (spec R4): the two differ in nearly every number (avatar 76 vs 72, badge 30 vs 28,
 * headline 27 vs 26, tile 36 / 12 vs 34 / 11, row text 13.5 / 1.45 vs 13 / 1.42, hero padding 30 24 vs 26 24 24) and in
 * the CTA, which here scrolls as the last item where Date 02 / 07's is pinned. Parametrising every number would be a
 * number with extra steps, and editing that screen puts Date 02 and 07 at risk. This one is built from the same
 * primitives instead. The reference is `screen-partner-end-reference.jsx`.
 */
package com.showup.date.partnerend

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.IconTile
import com.showup.date.MatchPhoto
import com.showup.date.PartnerScreen
import com.showup.date.cancel.runsToText
import com.showup.designsystem.BlushWash
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.ShadowInk
import com.showup.designsystem.ShadowMd
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop

const val PARTNER_ENDED_CTA_TAG = "partnerEnded:cta"
const val PARTNER_ENDED_ROW_TAG = "partnerEnded:row"
const val PARTNER_ENDED_HERO_TAG = "partnerEnded:hero"

/** The rows' column, so the fit test can ask that the CTA lies below the last of them. */
const val PARTNER_ENDED_ROWS_TAG = "partnerEnded:rows"

private val SubText = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.55).sp, color = Muted, textAlign = TextAlign.Center)
private val RowText = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.45).sp, color = Fg)

/**
 * The hero atmosphere (0.26 / 0.22, as Date 02's), column pad 8 20 0 under the system bars. One scroll whose content is
 * at least the viewport tall: hero, rows, a flexible space of at least 14, then the CTA (6 below). Measured with
 * `BoxWithConstraints` because `weight` does nothing under an unbounded scroll. A tall phone sees the CTA at the foot;
 * a short one, or the largest type, reaches it as the last item by scrolling.
 */
@Composable
fun PartnerEndedScreen(
    screen: PartnerScreen,
    name: String,
    photoUrl: String?,
    /** Hoisted only so the evidence harness can drive the column to its end; the screen owns it otherwise. */
    scroll: ScrollState = rememberScrollState(),
    onProceed: () -> Unit,
) {
    val payload = partnerEndedPayload(screen)
    Box(Modifier.fillMaxSize().background(Cream)) {
        WelcomeBackdrop(peachWash = false, placement = OrbPlacement.Atmosphere, orangeAlpha = 0.26f, violetAlpha = 0.22f)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = maxHeight)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Hero(screen, payload, name, photoUrl)
                    Spacer(Modifier.height(payload.rowsTop))
                    Rows(payload)
                    Spacer(Modifier.height(14.dp))
                }
                PrimaryButton(
                    PartnerEndedCopy.CTA,
                    onProceed,
                    modifier = Modifier.padding(bottom = 6.dp).testTag(PARTNER_ENDED_CTA_TAG),
                )
            }
        }
    }
}

@Composable
private fun Hero(screen: PartnerScreen, p: PartnerEndedPayload, name: String, photoUrl: String?) {
    val shape = RoundedCornerShape(PartnerEndedLayout.HeroRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(PARTNER_ENDED_HERO_TAG)
            .background(if (p.tone == HeroTone.Lilac) LilacWash else BlushWash, shape)
            .padding(horizontal = p.heroPadH, vertical = p.heroPadV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(PartnerEndedLayout.AvatarSize)) {
            if (p.usesPhoto) {
                MatchPhoto(name, photoUrl, PartnerEndedLayout.AvatarSize)
            } else {
                Box(
                    Modifier
                        .size(PartnerEndedLayout.AvatarSize)
                        .pillShadows(SolidColor(Color.White.copy(alpha = 0.72f)), listOf(CssShadow(ShadowInk.copy(alpha = 0.08f), offsetY = 2.dp, blur = 10.dp)))
                        .border(1.dp, Purple.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                    // The reference's `<Icon name="heart-filled" size={34}/>` passes no stroke, so the kit's `<svg>` strokes
                    // the fill at its default 1.7: 2.4 dp wider than a plain fill.
                ) { Icon(BrandIcon.HeartFilled, 34.dp, tint = Purple, fillAndStroke = true) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(PartnerEndedLayout.BadgeSize)
                    .pillShadows(SolidColor(Color.White), ShadowMd)
                    // The reference's 3 px border sits inside its 30 box.
                    .padding(3.dp)
                    .background(if (p.badgeSuccess) SuccessFg else Orange, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(p.badgeIcon, p.badgeIconSize, tint = Color.White, strokeWidth = p.badgeStroke) }
        }
        Spacer(Modifier.height(p.avatarGap))
        WashHeadline(
            parts = when (screen) {
                PartnerScreen.YouNoShowed -> listOf(PartnerEndedCopy.A_HEADLINE_LEAD.format(name) to false, PartnerEndedCopy.A_HEADLINE_ITALIC to true)
                PartnerScreen.DateEndedDiscomfort -> listOf(
                    PartnerEndedCopy.B_HEADLINE_LEAD.format(name) to false,
                    PartnerEndedCopy.B_HEADLINE_ITALIC to true,
                    PartnerEndedCopy.B_HEADLINE_REST to false,
                )
                PartnerScreen.DateClosedPositive -> listOf(PartnerEndedCopy.C_HEADLINE_LEAD.format(name) to false, PartnerEndedCopy.C_HEADLINE_ITALIC to true)
            },
            fontSize = 27.sp,
            lineHeight = (27 * 1.15).sp,
            letterSpacing = (-0.01).em,
            balance = true,
            textAlign = TextAlign.Center,
            // As iOS: the CSS line box and the CSS wash anchor, and a heading for TalkBack's heading navigation.
            cssLineBox = true,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        val sub = when (screen) {
            PartnerScreen.YouNoShowed -> runsToText(PartnerEndedCopy.aSub(name))
            PartnerScreen.DateEndedDiscomfort -> runsToText(listOf(com.showup.date.cancel.Run(PartnerEndedCopy.B_SUB)))
            PartnerScreen.DateClosedPositive -> runsToText(listOf(com.showup.date.cancel.Run(PartnerEndedCopy.C_SUB)))
        }
        Text(sub, modifier = Modifier.prettyWrap(sub, SubText, centered = true).cssLineBox(SubText.lineHeight), style = SubText)
    }
}

@Composable
private fun Rows(p: PartnerEndedPayload) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (p.accent == RowAccent.Orange) Orange else Purple
    Column(Modifier.fillMaxWidth().testTag(PARTNER_ENDED_ROWS_TAG), verticalArrangement = Arrangement.spacedBy(PartnerEndedLayout.RowGap)) {
        p.rows.forEachIndexed { i, runs ->
            val text = runsToText(runs)
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag(PARTNER_ENDED_ROW_TAG)
                    .background(Elevated, shape)
                    .border(1.dp, BorderSoft, shape)
                    // The reference's 14 inside its 1 px border; CSS counts the border in the box.
                    .padding(15.dp),
                // No `alignItems` in the reference: the tile tops the row and only the text is centred on it.
                verticalAlignment = Alignment.Top,
            ) {
                IconTile(PartnerEndedLayout.TileSize, PartnerEndedLayout.TileRadius, SolidColor(accent.copy(alpha = 0.12f)), p.rowIcons[i], 18.dp, accent, 2f)
                Spacer(Modifier.width(12.dp))
                Text(
                    text,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .prettyWrap(text, RowText)
                        .cssLineBox(RowText.lineHeight),
                    style = RowText,
                )
            }
        }
    }
}
