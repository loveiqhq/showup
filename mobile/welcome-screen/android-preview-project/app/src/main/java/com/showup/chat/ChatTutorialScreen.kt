/*
 * ChatTutorialScreen.kt
 * ShowUp · A, "Must read — how messaging works" (SHOWUP-194), from `screen-chat-reference.jsx`
 *
 * Before the first open of each date's chat, once per person. Two steps and no third. Header, one
 * scroll, and `Open the chat` pinned under it -- the only way forward; back returns to the Date screen
 * and the tutorial shows again next time.
 */
package com.showup.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.IconTile
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

private val IntroText = TextStyle(fontFamily = Manrope, fontSize = 14.5.sp, lineHeight = (14.5 * 1.5).sp, color = Muted)
private val StepText = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.5).sp, color = Muted)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatTutorialScreen(name: String, onOpen: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cream)) {
        SunsetWash()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            // Back and the context line: pad 4 12 8, gap 8; 13 / 700 muted.
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChatBackButton(onBack)
                Spacer(Modifier.width(8.dp))
                Text(ChatCopy.TUTORIAL_HEADER.format(name), color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            ) {
                // The hero: Lora 700 30, −0.02em, 1.12, `Must read` washed; 4 above, 8 below.
                WashHeadline(
                    parts = listOf(ChatCopy.TUTORIAL_HEADLINE_ITALIC to true, ChatCopy.TUTORIAL_HEADLINE_REST to false),
                    fontSize = 30.sp,
                    lineHeight = (30 * 1.12).sp,
                    letterSpacing = (-0.02).em,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                // The intro box: violet 8% with a 16% border, radius 14, pad 12 14, 22 below.
                val intro = withBold(ChatCopy.TUTORIAL_INTRO, ChatCopy.TUTORIAL_INTRO_BOLD)
                val introShape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Purple.copy(alpha = 0.08f), introShape)
                        .border(1.dp, Purple.copy(alpha = 0.16f), introShape)
                        // Pad 12 14 plus the 1 px border, which CSS counts in the box.
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                ) {
                    Text(intro, modifier = Modifier.prettyWrap(intro, IntroText).cssLineBox(IntroText.lineHeight), style = IntroText)
                }
                Spacer(Modifier.height(22.dp))
                TutorialStep(
                    n = 1, icon = BrandIcon.Navigation, tint = Orange.copy(alpha = 0.12f), color = Orange,
                    title = ChatCopy.STEP_1_TITLE, body = withBold(ChatCopy.STEP_1_BODY, ChatCopy.STEP_1_BOLD),
                )
                // The two example chips, one line where it fits: 20 below the step less 6, 53 in (the tile and
                // its gap), gap 8. On a 375 phone the line is wider than the column; it wraps rather than cut
                // `Running late` to `Running`.
                FlowRow(
                    Modifier.padding(start = 53.dp, top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        ChatCopy.EXAMPLES,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                    )
                    ExampleChip(ChatCopy.EXAMPLE_ARRIVED, BrandIcon.Navigation)
                    ExampleChip(ChatCopy.EXAMPLE_LATE, BrandIcon.Hourglass)
                }
                Spacer(Modifier.height(20.dp))
                TutorialStep(
                    n = 2, icon = BrandIcon.EyeOff, tint = Purple.copy(alpha = 0.10f), color = Purple,
                    title = ChatCopy.STEP_2_TITLE, body = withBold(ChatCopy.STEP_2_BODY, ChatCopy.STEP_2_BOLD),
                )
                Spacer(Modifier.height(10.dp))
            }
            // The pinned CTA: pad 12 16 16 on the glass, `Open the chat` h 54 with the orange glow, and the
            // privacy line 10 below.
            Column(
                Modifier
                    .fillMaxWidth()
                    .pinnedGlass()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            ) {
                PrimaryButton(
                    ChatCopy.OPEN_THE_CHAT,
                    onOpen,
                    height = 54.dp,
                    labelSize = 16.sp,
                    shadows = ShadowCta,
                    leading = { Icon(BrandIcon.MessageCircle, 19.dp, tint = Color.White, strokeWidth = 2.2f) },
                )
                LockLine(ChatCopy.TUTORIAL_PRIVATE, Modifier.padding(top = 10.dp))
            }
        }
    }
}

/**
 * The tile 40 / 13 with its icon 20 / 2.2 and the step number on a 20 white disc, 1.5 ringed, 6 out
 * at the top right; beside it (13 away) the title, Lora 700 17 / 1.2, and the line, 13.5 / 1.5 muted.
 */
@Composable
private fun TutorialStep(n: Int, icon: BrandIcon, tint: Color, color: Color, title: String, body: AnnotatedString) {
    Row {
        Box(Modifier.size(40.dp)) {
            IconTile(40.dp, 13.dp, SolidColor(tint), icon, 20.dp, color, 2.2f)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .background(Elevated, CircleShape)
                    .border(1.5.dp, color, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                // A fixed-size mark on a fixed-size disc: the step is read from the title, not the digit.
                Text(
                    n.toString(),
                    color = color,
                    fontFamily = Lora,
                    fontWeight = FontWeight.Bold,
                    fontSize = with(LocalDensity.current) { 12.dp.toSp() },
                    lineHeight = with(LocalDensity.current) { 12.dp.toSp() },
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(top = 1.dp)) {
            Text(title, color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = (17 * 1.2).sp)
            Spacer(Modifier.height(4.dp))
            Text(body, modifier = Modifier.prettyWrap(body, StepText).cssLineBox(StepText.lineHeight), style = StepText)
        }
    }
}

/** An example chip, not a button: pad 7 12 7 10, white, the orange chip border, 12.5 / 700 orange, icon 14 / 2.4. */
@Composable
private fun ExampleChip(label: String, icon: BrandIcon) {
    val tone = ReasonTone.Orange
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .background(Elevated, pill)
            .border(1.dp, tone.chipBorder, pill)
            .padding(start = 11.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 14.dp, tint = tone.fg, strokeWidth = 2.4f)
        Spacer(Modifier.width(6.dp))
        Text(label, color = tone.fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
    }
}

/**
 * The sunset wash at the top: the reference's 260 band at 16% blurred by 40. A blur is a no-op before
 * Android 12, so the band is drawn at once soft-edged instead -- the sunset at 16%, faded out down a
 * longer box the way the blur spreads it -- the same on every Android this app runs on.
 */
@Composable
private fun SunsetWash(height: Dp = 300.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = 0.16f; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawRect(SunsetBrush)
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}
