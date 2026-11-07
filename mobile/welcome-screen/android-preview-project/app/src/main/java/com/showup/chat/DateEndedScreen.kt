/*
 * DateEndedScreen.kt
 * ShowUp · G and H, after a date is ended in the chat (SHOWUP-194): the reference's
 * `ScreenPartnerCancelled` with its `viaChat` variant -- G when you ended it, H when the match did
 *
 * Date 07 extends this component for Cancel this date; it never forks it. No review follows and
 * nobody's Show-up Rate moves; `Find your next date` opens Search.
 */
package com.showup.chat

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.EndedInChat
import com.showup.date.IconTile
import com.showup.date.MatchPhoto
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.ShadowMd
import com.showup.designsystem.Success
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeBackdrop

private val HeroText = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.55).sp, color = Muted, textAlign = TextAlign.Center)
private val RowText = TextStyle(fontFamily = Manrope, fontSize = 13.sp, lineHeight = (13 * 1.42).sp, color = Fg)

/**
 * The hero atmosphere; pad 4 20 0. The lilac card (radius 28, pad 26 24 24, centred): the avatar 72
 * with the orange `x` badge 28 (white 3 ring, the md shadow) at its corner, the headline (Lora 700 26,
 * −0.01em, 1.15, balanced, the one word washed), and the line (14 / 1.55 muted). Then the one row (14
 * below): white, the soft border, radius 14, pad 13, the shield tile 34 / 11 in green, the line 13 / 1.42.
 * The flexible space, then `Find your next date` with `search` 18 / 2.4. It scrolls only when it must.
 */
@Composable
fun DateEndedScreen(outcome: EndedInChat, photoUrl: String?, onFindNext: () -> Unit) {
    val g = outcome.endedByYou
    val name = outcome.name
    Box(Modifier.fillMaxSize().background(Cream)) {
        WelcomeBackdrop(peachWash = false, placement = OrbPlacement.Atmosphere, orangeAlpha = 0.26f, violetAlpha = 0.22f)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            val frame = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .heightIn(min = frame)
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(LilacWash, RoundedCornerShape(28.dp))
                                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(72.dp)) {
                                MatchPhoto(name, photoUrl, 72.dp)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 3.dp, y = 3.dp)
                                        .size(28.dp)
                                        .pillShadows(SolidColor(Color.White), ShadowMd)
                                        .padding(3.dp)
                                        .background(Orange, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(BrandIcon.Close, 15.dp, tint = Color.White, strokeWidth = 2.6f) }
                            }
                            Spacer(Modifier.height(16.dp))
                            WashHeadline(
                                parts = if (g) {
                                    listOf(ChatCopy.G_HEADLINE_LEAD to false, ChatCopy.G_HEADLINE_ITALIC to true, ChatCopy.G_HEADLINE_REST to false)
                                } else {
                                    listOf(ChatCopy.H_HEADLINE_LEAD.format(name) to false, ChatCopy.H_HEADLINE_ITALIC to true, ChatCopy.H_HEADLINE_REST to false)
                                },
                                fontSize = 26.sp,
                                lineHeight = (26 * 1.15).sp,
                                letterSpacing = (-0.01).em,
                                balance = true,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            val body = withBold(if (g) ChatCopy.G_BODY.format(name) else ChatCopy.H_BODY, ChatCopy.OUTCOME_BOLD)
                            Text(body, modifier = Modifier.prettyWrap(body, HeroText, centered = true).cssLineBox(HeroText.lineHeight), style = HeroText)
                        }
                        Spacer(Modifier.height(14.dp))
                        val rowShape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Elevated, rowShape)
                                .border(1.dp, BorderSoft, rowShape)
                                // Pad 13 plus the 1 px border, which CSS counts in the box.
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(34.dp, 11.dp, SolidColor(Success.copy(alpha = 0.12f)), BrandIcon.ShieldCheck, 17.dp, SuccessFg, 2f)
                            Spacer(Modifier.width(12.dp))
                            val row = withBold(if (g) ChatCopy.G_ROW.format(name) else ChatCopy.H_ROW, ChatCopy.OUTCOME_BOLD)
                            Text(row, modifier = Modifier.weight(1f).prettyWrap(row, RowText).cssLineBox(RowText.lineHeight), style = RowText)
                        }
                    }
                    // `flex: 1, min-height 12`, then the CTA.
                    PrimaryButton(
                        ChatCopy.FIND_NEXT,
                        onFindNext,
                        modifier = Modifier.padding(top = 12.dp),
                        leading = { Icon(BrandIcon.Search, 18.dp, tint = Color.White, strokeWidth = 2.4f) },
                    )
                }
            }
        }
    }
}
