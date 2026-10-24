/*
 * SearchCoach.kt
 * ShowUp · the actions coach, "Three ways to decide" (Search 01, frame F, callouts 24 and 25)
 *
 * The card only. Where it sits is the screen's: inside the action bar's own column, above the three
 * buttons with a 14 gap, so it follows the bar on every phone and never takes a fixed Y. The scrim,
 * the inert buttons and the dismissals are the screen's and the view model's.
 *
 * A dialog named `What the three buttons do`; focus lands on `Got it`. The rows wear the buttons'
 * own icons and tones -- danger outline, muted outline, sunset -- so each reads as the button below.
 */
package com.showup.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import com.showup.designsystem.cssLineBox
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.ButtonPair
import com.showup.designsystem.PAIR_BUTTON_PADDING
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Danger
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.roundedShadows
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/** `0 16px 40px rgba(29,17,41,.28)` -- the coach card lifted over the scrim. */
private val CoachLift = listOf(CssShadow(Color(0x471D1129), offsetY = 16.dp, blur = 40.dp))

/** Test anchors for the coach's two buttons. */
const val COACH_GOT_IT_TAG = "coach-got-it"
const val COACH_DONT_SHOW_TAG = "coach-dont-show"

/** `rgba(251,50,59,.4)` -- the Not interested outline, the button's and its coach row's. */
internal val DangerOutline = Color(0x66FB323B)

@Composable
fun SearchActionsCoach(onDone: (dontShowAgain: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val gotIt = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { gotIt.requestFocus() } }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .roundedShadows(SolidColor(Elevated), 24.dp, CoachLift)
                // Scrolls inside the card only when the bar's column cannot give it its height.
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp)
                .semantics {
                    dialog()
                    paneTitle = SearchCopy.COACH_LABEL
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WashHeadline(
                parts = listOf(SearchCopy.COACH_TITLE_LEAD to false, SearchCopy.COACH_TITLE_EMPHASIS to true),
                fontSize = 22.sp,
                lineHeight = (22 * 1.15).sp,
                letterSpacing = (-0.01).em,
                cssLineBox = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoachRow.entries.forEach { row -> CoachRowView(row) }
            }
            ButtonPair(
                first = {
                    PrimaryButton(
                        SearchCopy.COACH_DONT_SHOW, { onDone(true) }, Modifier.testTag(COACH_DONT_SHOW_TAG),
                        variant = PrimaryButtonVariant.Ghost, height = 48.dp, labelSize = 15.sp,
                        horizontalPadding = PAIR_BUTTON_PADDING,
                    )
                },
                second = {
                    PrimaryButton(
                        SearchCopy.COACH_GOT_IT, { onDone(false) }, Modifier.focusRequester(gotIt).testTag(COACH_GOT_IT_TAG),
                        variant = PrimaryButtonVariant.Violet, height = 48.dp, labelSize = 15.sp,
                        horizontalPadding = PAIR_BUTTON_PADDING,
                    )
                },
            )
        }
        // The caret: a 14 square turned 45 degrees, half below the card, pointing at the buttons.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 7.dp)
                .size(14.dp)
                .rotate(45f)
                .background(Elevated, RoundedCornerShape(3.dp))
                .clearAndSetSemantics {},
        )
    }
}

@Composable
private fun CoachRowView(row: CoachRow) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val disc = Modifier.size(38.dp)
        Box(
            when (row.tone) {
                ActionTone.Danger -> disc.background(Elevated, CircleShape).border(1.5.dp, DangerOutline, CircleShape)
                ActionTone.Muted -> disc.background(Elevated, CircleShape).border(1.5.dp, Border, CircleShape)
                ActionTone.Primary -> disc.background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            val ink = when (row.tone) {
                ActionTone.Danger -> Danger
                ActionTone.Muted -> Muted
                ActionTone.Primary -> Color.White
            }
            Icon(row.icon, 18.dp, tint = ink, strokeWidth = if (row.tone == ActionTone.Primary) 2.4f else 2f)
        }
        Column {
            // CSS line boxes (`CssLineBox.kt`): at 15 / 1.2 Compose kept the title's natural 20.5, and
            // the three rows came out 11 taller than the sheet's card (the 184 evidence review).
            Text(
                row.title, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                lineHeight = (15 * 1.2).sp, modifier = Modifier.cssLineBox((15 * 1.2).sp),
            )
            // `text-wrap: pretty`, as the reference sets on each row's line.
            val body = AnnotatedString(row.body)
            val bodyStyle = TextStyle(
                color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = (13.5 * 1.4).sp,
            )
            Text(body, Modifier.prettyWrap(body, bodyStyle).cssLineBox(bodyStyle.lineHeight), style = bodyStyle)
        }
    }
}
