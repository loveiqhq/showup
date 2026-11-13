/*
 * CheckInBar.kt
 * ShowUp · A, the Check-in bar (SHOWUP-195), from `CheckInBar` in `screen-checkin-reference.jsx`
 *
 * The footer slot of the Date screen until the person has checked in. Larger and filled, so it reads as
 * THE action on arrival.
 *
 * NO BACKDROP BLUR. The reference's `rgba(29,17,41,.86)` + `blur(20px)` is composited here: the ink at 86 %
 * over [Cream], opaque, so what scrolls behind the bar never shows through it (the 193 rule for the
 * TabBar's glass, kept).
 *
 * R12, THE EXTREME SIZES. The text column never goes below its longest word. Where pin + text + button
 * cannot share one row, the button goes under the text, full width, 10 below.
 */
package com.showup.arrival

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.date.DrawnSmall
import com.showup.date.pressable
import com.showup.designsystem.Cream
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacCard
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.roundedShadows
import com.showup.search.cssLinearGradient
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

const val ARRIVAL_BAR_TAG = "arrival:bar"
const val ARRIVAL_CHECK_IN_TAG = "arrival:check-in"
const val ARRIVAL_END_DATE_TAG = "arrival:end-date"

/**
 * R15: the `End date` link is built and switched off. It opens Date 06's wrap-up sheet, which does not
 * exist yet; the host passes `onEnd` only when this is on and that sheet ships.
 */
const val END_DATE_LINK_SHOWN = false

/** `rgba(29,17,41,.86)` over the cream, as one opaque colour. */
private val BarFill = Fg.copy(alpha = 0.86f).compositeOver(Cream)

/** `0 -6px 24px rgba(29,17,41,.22)`, cast up. */
private val BarLift = listOf(CssShadow(Fg.copy(alpha = 0.22f), offsetY = (-6).dp, blur = 24.dp))

private val BarTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = (18 * 1.15).sp, color = Color.White,
)
private val BarLine = TextStyle(
    fontFamily = Manrope, fontSize = 12.5.sp, lineHeight = (12.5 * 1.4).sp, color = Color.White,
)

/**
 * Padding 16 16 10, column gap 6. The row is centred, gap 14: the pin tile 46 (radius 14, white 12 %,
 * `map-pin` 22 / 2.2), the text (Lora 700 18 / 1.15 over Manrope 12.5 / 1.4, 3 below, pretty) and
 * `Check-in` (h 46, pad 0 20, pill, sunset, Manrope 800 15, nowrap, `--liq-shadow-cta`). Under it the
 * `End date` link when [onEnd] is given: centred, pad 8 12, Manrope 600 13 `#E8DCF5`, answering at 44.
 */
@Composable
fun CheckInBar(
    name: String,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    onEnd: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .testTag(ARRIVAL_BAR_TAG)
            // The shadow's fill is the bar's own, so the rounded bottom corners it draws are covered.
            .roundedShadows(SolidColor(BarFill), 22.dp, BarLift)
            .background(BarFill, shape)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BarRow(
            pin = {
                Box(
                    Modifier.size(46.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(BrandIcon.MapPin, 22.dp, tint = Color.White, strokeWidth = 2.2f) }
            },
            text = {
                Column {
                    val title = AnnotatedString(ArrivalCopy.BAR_TITLE.format(name))
                    Text(title, Modifier.cssLineBox(BarTitle.lineHeight), style = BarTitle)
                    val line = AnnotatedString(ArrivalCopy.BAR_LINE)
                    Text(
                        line,
                        Modifier.padding(top = 3.dp).prettyWrap(line, BarLine).cssLineBox(BarLine.lineHeight),
                        style = BarLine,
                    )
                }
            },
            button = {
                Box(
                    Modifier
                        .testTag(ARRIVAL_CHECK_IN_TAG)
                        .heightIn(min = 46.dp)
                        .pressable(Role.Button, onClick = onCheckIn)
                        // The glow behind, then the reference's 135deg sunset clipped to the pill.
                        .pillShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), ShadowCta)
                        .clip(RoundedCornerShape(50))
                        .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first })
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ArrivalCopy.BAR_BUTTON, color = Color.White, fontFamily = Manrope,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false,
                    )
                }
            },
        )
        if (onEnd != null) {
            DrawnSmall(
                label = ArrivalCopy.END_DATE,
                onClick = onEnd,
                touch = Modifier.testTag(ARRIVAL_END_DATE_TAG),
            ) { press ->
                Text(
                    ArrivalCopy.END_DATE,
                    modifier = press.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = LilacCard, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * The pin, the text and the button in one row (gap 14, centred) where the text keeps at least its
 * longest word; otherwise the pin and the text share the first row and the button runs full width
 * under them, 10 below (R12).
 */
@Composable
private fun BarRow(pin: @Composable () -> Unit, text: @Composable () -> Unit, button: @Composable () -> Unit) {
    Layout(contents = listOf(pin, text, button), modifier = Modifier.fillMaxWidth()) { (pins, texts, buttons), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 14.dp.roundToPx()
        val under = 10.dp.roundToPx()
        val width = constraints.maxWidth
        val pinP = pins.first().measure(loose)
        // Intrinsics, not a measure: a Measurable may be measured once, and the button's final width depends on the choice.
        val buttonW = buttons.first().maxIntrinsicWidth(Constraints.Infinity)
        val longestWord = texts.first().minIntrinsicWidth(Constraints.Infinity)
        val inline = pinP.width + gap + longestWord + gap + buttonW <= width
        if (inline) {
            val buttonP = buttons.first().measure(loose)
            val textP = texts.first().measure(
                Constraints(maxWidth = width - pinP.width - buttonP.width - 2 * gap, maxHeight = Constraints.Infinity),
            )
            val h = maxOf(pinP.height, textP.height, buttonP.height)
            layout(width, h) {
                pinP.place(0, (h - pinP.height) / 2)
                textP.place(pinP.width + gap, (h - textP.height) / 2)
                buttonP.place(width - buttonP.width, (h - buttonP.height) / 2)
            }
        } else {
            val textP = texts.first().measure(
                Constraints(maxWidth = (width - pinP.width - gap).coerceAtLeast(0), maxHeight = Constraints.Infinity),
            )
            val full = buttons.first().measure(Constraints(minWidth = width, maxWidth = width))
            val h = maxOf(pinP.height, textP.height)
            layout(width, h + under + full.height) {
                pinP.place(0, (h - pinP.height) / 2)
                textP.place(pinP.width + gap, (h - textP.height) / 2)
                full.place(0, h + under)
            }
        }
    }
}
