/*
 * DateGate.kt
 * ShowUp · the location gate's DATE context: the date card and its countdown pill (SHOWUP-195 G)
 *
 * The kit's `DateGateCard` (docs/superpowers/specs/kit/2026-11-10-date-gate-kit-excerpt.jsx): the date on
 * the gate, in the purple of the Date screen's time + place card, so the person recognises what they are
 * locked out of. The layout around it is [ProfileLocationScreen]'s own date context -- one location screen,
 * three contexts, never a copy.
 */
package com.showup.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.date.MatchPhoto
import com.showup.designsystem.Danger
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacCard
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.balanceWrap
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.fitWordsOf
import com.showup.search.meetTime
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.Instant
import java.time.ZoneId

internal const val DATE_GATE_CARD_TAG = "date-gate:card"
internal const val DATE_GATE_CTA_TAG = "date-gate:cta"

/** The pill under the date card: counting down before the meet time, up after it. */
sealed interface GatePill {
    data class Starts(val hours: Int, val minutes: Int) : GatePill

    data class Started(val minutes: Int) : GatePill
}

/**
 * `Starts in {h}h {m}m` today before the meet time, `Started {n} min ago` after it, nothing for tomorrow.
 * Counting down is rounded up to the minute; counting up is whole minutes elapsed.
 */
fun gatePill(meetDay: String, meetAt: Instant, now: Instant): GatePill? {
    val seconds = meetAt.epochSecond - now.epochSecond
    // Counting down rounds UP, so the last minute reads 0h 1m and never 0h 0m.
    val delta = Math.floorDiv(seconds + 59, 60L)
    return when {
        !now.isBefore(meetAt) -> GatePill.Started(((-seconds) / 60).toInt())
        meetDay == "today" -> GatePill.Starts((delta / 60).toInt(), (delta % 60).toInt())
        else -> null
    }
}

private val CardTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = (19 * 1.2).sp, color = Fg,
)

/**
 * Padding 14, radius 20, `#E8DCF5`, gap 12. Row one: the match's avatar (40, radius 13), gap 10, `You and
 * {name} are meeting {day}` in Lora 700 19 / 1.2, balanced, the day in violet italic. Row two: `clock` and
 * `map-pin` 17 / 2.2 violet beside Lora 700 19 values, gap 14, wrapping. Row three, when there is a pill: h 30,
 * pad 0 12, pill, `clock` 14 / 2.4, Manrope 700 13 white -- orange `Starts in`, red `Started`, followed after
 * the meet time by `{name} may be waiting` in red 13 / 700.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateGateCard(
    name: String,
    meetDay: String,
    meetAt: Instant,
    place: String?,
    photoUrl: String?,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val pill = gatePill(meetDay, meetAt, now)
    Column(
        modifier
            .fillMaxWidth()
            .testTag(DATE_GATE_CARD_TAG)
            .background(LilacCard, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchPhoto(name, photoUrl, 40.dp, shape = RoundedCornerShape(13.dp))
            val plain = LocationCopy.Date.CARD_LEAD + name + LocationCopy.Date.CARD_MID + meetDay
            val title = buildAnnotatedString {
                append(LocationCopy.Date.CARD_LEAD + name + LocationCopy.Date.CARD_MID)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Purple)) { append(meetDay) }
            }
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .fitWordsOf(plain, CardTitle)
                    .balanceWrap(title, CardTitle)
                    .cssLineBox(CardTitle.lineHeight),
                style = CardTitle,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardValue(BrandIcon.Clock, meetTime(meetAt, zone), Modifier.align(Alignment.CenterVertically))
            if (place != null) CardValue(BrandIcon.MapPin, place, Modifier.align(Alignment.CenterVertically))
        }
        if (pill != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val late = pill is GatePill.Started
                Row(
                    Modifier
                        .align(Alignment.CenterVertically)
                        .heightIn(min = 30.dp)
                        .background(if (late) Danger else Orange, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.clearAndSetSemantics {}) { Icon(BrandIcon.Clock, 14.dp, tint = Color.White, strokeWidth = 2.4f) }
                    Text(
                        when (pill) {
                            is GatePill.Starts -> LocationCopy.Date.STARTS_IN.format(pill.hours, pill.minutes)
                            is GatePill.Started -> LocationCopy.Date.STARTED.format(pill.minutes)
                        },
                        color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                if (late) {
                    Text(
                        LocationCopy.Date.MAY_BE_WAITING.format(name),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = Danger, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** An icon (17 / 2.2, violet) and a Lora 700 19 value, gap 6. */
@Composable
private fun CardValue(icon: BrandIcon, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clearAndSetSemantics {}) { Icon(icon, 17.dp, tint = Purple, strokeWidth = 2.2f) }
        Box(Modifier.width(6.dp))
        Text(value, style = CardTitle.copy(lineHeight = TextUnit.Unspecified))
    }
}
