/*
 * ChangeSuggestionCard.kt
 * ShowUp · the suggestion in the thread (SHOWUP-196, Date 05 C, F, H, L), `ChangeSuggestionCard` of
 * `screen-change-reference.jsx`, with its numbers
 *
 * A structured message, not a text one: mine right, theirs left, 84 % of the thread, radius 20, a 1.5 violet
 * border at 22 %, the small shadow. The header names who and what, the value is Lora 24 with the old one struck
 * beside it, and the footer is the status: waiting (with the compact binding note), the deadline box with
 * `Suggest another` and `I agree`, agreed, replaced or expired. Replaced and expired sit at .55.
 *
 * The card holds no state: [agreeing] (the request is on its way: both buttons inert, `I agree` at .6) and
 * [agreeFailed] (L: the red line, which retries) come from the chat's model, which changes nothing else until
 * the server confirms. [bindingTime] and [bindingPlace] are the date's CURRENT values -- a pending suggestion
 * moves neither.
 */
package com.showup.change

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.chat.messageTime
import com.showup.date.pressable
import com.showup.designsystem.BaselineWrap
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.ShadowSm
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.roundedShadows
import com.showup.search.cssLinearGradient
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.Instant
import java.time.ZoneId

fun changeCardTag(id: String) = "change-card-$id"
const val CHANGE_CARD_AGREE_TAG = "change-card-agree"
const val CHANGE_CARD_COUNTER_TAG = "change-card-counter"
const val CHANGE_CARD_RETRY_TAG = "change-card-retry"
const val CHANGE_CARD_VALUE_TAG = "change-card-value"
const val CHANGE_CARD_WAS_TAG = "change-card-was"

private val AmberInk = Color(0xFF78350F)
private val AmberBody = Color(0xFF92400E)
private val AmberIcon = Color(0xFFB45309)

private val HeaderText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.06.sp, color = Purple)
private val ValueText = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = (24 * 1.1).sp, color = Fg)
private val WasText = TextStyle(fontFamily = Manrope, fontSize = 13.sp, color = FgSubtle, textDecoration = TextDecoration.LineThrough)
private val TypeLine = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp, color = Muted)
private val StampText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = FgSubtle)

@Composable
fun ChangeSuggestionCard(
    suggestion: ChangeSuggestion,
    name: String,
    bindingTime: Instant,
    bindingPlace: String,
    zone: ZoneId,
    agreeing: Boolean,
    agreeFailed: Boolean,
    onAgree: () -> Unit,
    onCounter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine = suggestion.byYou
    val isTime = suggestion.kind == ChangeKind.Time
    val faded = suggestion.status == ChangeStatus.Replaced || suggestion.status == ChangeStatus.Expired
    val value = if (isTime) suggestion.time?.let { changeClock(it, zone) }.orEmpty() else suggestion.place?.name.orEmpty()
    val was = if (isTime) changeClock(suggestion.wasTime, zone) else suggestion.wasPlaceName.orEmpty()
    val deadline = changeClock(suggestion.deadline, zone)
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.84f)
                .alpha(if (faded) 0.55f else 1f)
                .testTag(changeCardTag(suggestion.id))
                .semantics(mergeDescendants = true) {}
                .roundedShadows(SolidColor(Elevated), 20.dp, ShadowSm)
                .clip(shape)
                .background(Elevated)
                .border(1.5.dp, Purple.copy(alpha = 0.22f), shape),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 13.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isTime) BrandIcon.Clock else BrandIcon.MapPin, 13.dp, tint = Purple, strokeWidth = 2.4f)
                    val who = if (mine) ChangeCopy.CARD_YOU else ChangeCopy.CARD_THEY.format(name)
                    val what = if (isTime) ChangeCopy.CARD_TIME else ChangeCopy.CARD_PLACE
                    Text("$who · $what".uppercase(), style = HeaderText)
                }
                // The reference's `align-items: baseline; flex-wrap: wrap; gap: 10`: the struck old value on the
                // value's baseline, dropping to its own line under a long name instead of squeezing.
                BaselineWrap(
                    first = { // The tag is applied after cssLineBox: its node then sits where the text is drawn, which the baseline test reads.
                    Text(value, modifier = Modifier.cssLineBox(ValueText.lineHeight).testTag(CHANGE_CARD_VALUE_TAG), style = ValueText) },
                    second = { Text(was, modifier = Modifier.testTag(CHANGE_CARD_WAS_TAG), style = WasText) },
                    modifier = Modifier.padding(top = 6.dp),
                    gap = 10.dp,
                )
                suggestion.place?.takeIf { !isTime }?.let {
                    Text("${placeTypeLabel(it.type)} · ${it.address}", modifier = Modifier.padding(top = 3.dp), style = TypeLine)
                }
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (suggestion.status) {
                    ChangeStatus.Agreed -> StatusLine(
                        BrandIcon.Check, 15, 2.8f,
                        suggestion.answeredAt?.let { ChangeCopy.AGREED_AT.format(changeClock(it, zone)) } ?: ChangeCopy.AGREED,
                        SuccessFg, 13.sp, FontWeight.Bold,
                    )
                    ChangeStatus.Replaced -> Text(
                        ChangeCopy.REPLACED,
                        style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Muted),
                    )
                    ChangeStatus.Expired -> StatusLine(BrandIcon.Clock, 13, 2.4f, ChangeCopy.EXPIRED.format(deadline), Muted, 12.5.sp, FontWeight.Bold)
                    ChangeStatus.Pending -> if (mine) {
                        StatusLine(BrandIcon.Clock, 13, 2.4f, ChangeCopy.WAITING_FOR.format(name), Muted, 12.5.sp, FontWeight.Bold)
                        BindingNote(name, changeClock(bindingTime, zone), bindingPlace, deadline, compact = true)
                    } else {
                        DeadlineBox(deadline, changeClock(bindingTime, zone), bindingPlace)
                        CardButtons(agreeing, onAgree, onCounter)
                        if (agreeFailed) ChangeRetryLine(ChangeCopy.AGREE_FAILED, onAgree, CHANGE_CARD_RETRY_TAG)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
            Text(
                messageTime(suggestion.createdAt, zone),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                style = StampText.copy(textAlign = if (mine) TextAlign.End else TextAlign.Start),
            )
        }
    }
}

@Composable
private fun StatusLine(icon: BrandIcon, size: Int, stroke: Float, text: String, ink: Color, fontSize: TextUnit, weight: FontWeight) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, size.dp, tint = ink, strokeWidth = stroke)
        Text(text, style = TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = fontSize, color = ink))
    }
}

/**
 * The amber box: `Answer by {deadline}` and what stays binding until then. Shared with the request card on
 * the Date screen, which draws the same box (the ticket: "deadline box as on the card above").
 */
@Composable
fun DeadlineBox(deadline: String, time: String, place: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = (12.5 * 1.4).sp, color = AmberBody)
    val text = buildAnnotatedString {
        append(ChangeCopy.UNTIL_YOU_AGREE)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AmberInk)) { append(ChangeCopy.UNTIL_BINDING.format(time, place)) }
        append(ChangeCopy.UNTIL_REST)
    }
    Row(
        modifier.fillMaxWidth().background(Color(0xFFF59E0B).copy(alpha = 0.09f), shape).padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.Clock, 15.dp, tint = AmberIcon, strokeWidth = 2.4f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                ChangeCopy.ANSWER_BY.format(deadline),
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = AmberInk),
            )
            Text(text, modifier = Modifier.prettyWrap(text, body).cssLineBox(body.lineHeight), style = body)
        }
    }
}

/** `Suggest another` first, `I agree` second: h 44 pills, gap 8. While [agreeing] both are inert and `I agree` is at .6. */
@Composable
private fun CardButtons(agreeing: Boolean, onAgree: () -> Unit, onCounter: () -> Unit) {
    val pill = RoundedCornerShape(percent = 50)
    // Merging, so the card (itself a merged element) keeps the inert button as a node of its own.
    val inert = Modifier.semantics(mergeDescendants = true) { role = Role.Button; disabled() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .weight(1f).height(44.dp)
                .testTag(CHANGE_CARD_COUNTER_TAG)
                .then(if (agreeing) inert else Modifier.pressable(Role.Button, onClick = onCounter))
                .background(Elevated, pill)
                .border(1.dp, Border, pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChangeCopy.SUGGEST_ANOTHER, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Fg))
        }
        Box(
            Modifier
                .weight(1f).height(44.dp)
                .alpha(if (agreeing) 0.6f else 1f)
                .testTag(CHANGE_CARD_AGREE_TAG)
                .then(if (agreeing) inert else Modifier.pressable(Role.Button, onClick = onAgree))
                .roundedShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), 22.dp, ShadowCta)
                .clip(pill)
                .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first }),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChangeCopy.I_AGREE, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White))
        }
    }
}
