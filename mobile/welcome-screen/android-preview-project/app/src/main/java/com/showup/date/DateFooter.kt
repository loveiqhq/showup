/*
 * DateFooter.kt
 * ShowUp · the Date screen's sticky footer: the Check-in bar or the End Date bar, over the TabBar
 * (SHOWUP-193 ⑯ ⑰)
 *
 * ONE CONTAINER, swapped on `checkedIn`: the Check-in bar (with its `End date` link) from the match
 * until check-in, the End Date bar after. The bars are Date 03's and Date 06's -- this ticket's
 * acceptance criteria draw them here now, and those tickets add what their buttons do. The TabBar is
 * the shared one, in its disabled state.
 */
package com.showup.date

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacCard
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.cssBlurToShadowRadius
import com.showup.designsystem.minTapTarget
import com.showup.designsystem.pillShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

const val DATE_FOOTER_BAR_TAG = "date:footer-bar"

/** `rgba(29,17,41,.86)`: the Check-in bar's deep-ink glass. */
private val BarInk = Fg.copy(alpha = 0.86f)

/** `0 -6px 24px rgba(29,17,41,.22)`: the Check-in bar's shadow, cast upward over the scroll. */
private val BarShadow = Fg.copy(alpha = 0.22f)

/**
 * The bar's upward shadow, OUTSIDE its rounded top only, as CSS draws an outer `box-shadow`: the
 * shape is drawn far off-canvas with its shadow offset back into place, so only the shadow lands,
 * and the bar's own area and everything below its top edge are clipped out.
 */
private fun Modifier.barShadow(radius: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    val far = 100_000f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(cssBlurToShadowRadius(24.dp.toPx()), -far, -6.dp.toPx(), BarShadow.toArgb())
    }
    val shape = android.graphics.Path().apply {
        addRoundRect(0f, 0f, size.width, size.height, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), android.graphics.Path.Direction.CW)
    }
    drawIntoCanvas { canvas ->
        val n = canvas.nativeCanvas
        n.save()
        n.clipRect(-far, -far, size.width + far, r)
        n.clipOutPath(shape)
        n.drawRoundRect(far, 0f, far + size.width, size.height + r, r, r, paint)
        n.restore()
    }
}

/**
 * The Check-in bar (Date 03's, from the reference's `CheckInBar`): deep ink, top corners 22, a map pin
 * tile, `Your date with {name}`, the sunset `Check-in` pill, and the `End date` link under it -- which
 * opens the same wrap-up sheet as the End Date bar (2 Nov 2026), never the cancel flow.
 */
@Composable
internal fun CheckInBar(name: String, onCheckIn: () -> Unit, onEndDate: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .barShadow(22.dp)
            .background(BarInk, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
            .testTag(DATE_FOOTER_BAR_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(46.dp, 14.dp, SolidColor(Color.White.copy(alpha = 0.12f)), BrandIcon.MapPin, 22.dp, Color.White, 2.2f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    DateCopy.BAR_TITLE.format(name),
                    color = Color.White,
                    fontFamily = Lora,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = (18 * 1.15).sp,
                )
                Text(
                    DateCopy.CHECK_IN_BAR_SUB,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color.White,
                    fontFamily = Manrope,
                    fontSize = 12.5.sp,
                    lineHeight = (12.5 * 1.4).sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .height(46.dp)
                    .pressable(Role.Button, onClick = onCheckIn)
                    .pillShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), ShadowCta)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DateCopy.CHECK_IN,
                    color = Color.White,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            DateCopy.END_DATE_LINK,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .minTapTarget()
                .pressable(Role.Button, onClick = onEndDate)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = LilacCard,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

/**
 * The End Date bar (Date 06's, the reference's `DateStatusBar`, not complete): cream glass, a 1 px
 * border-soft top rule, a calendar tile, and the outlined `End Date` pill.
 */
@Composable
internal fun DateStatusBar(name: String, onEndDate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cream.copy(alpha = 0.96f))
            .drawBehind { drawRect(BorderSoft, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(DATE_FOOTER_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(38.dp, 12.dp, SolidColor(Purple.copy(alpha = 0.10f)), BrandIcon.Calendar, 19.dp, Purple, 2.1f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                DateCopy.BAR_TITLE.format(name),
                color = Fg,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateCopy.END_BAR_SUB,
                color = Muted,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = (11.5 * 1.35).sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .height(40.dp)
                .minTapTarget()
                .pressable(Role.Button, onClick = onEndDate)
                .background(Elevated, RoundedCornerShape(50))
                .border(1.5.dp, Purple, RoundedCornerShape(50))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                DateCopy.END_DATE,
                color = Purple,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** ⑯ + ⑰: one sticky container. The bar swaps on [checkedIn]; the TabBar stays where it is. */
@Composable
internal fun DateFooter(
    checkedIn: Boolean,
    name: String,
    onCheckIn: () -> Unit,
    onEndDate: () -> Unit,
    tabBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (checkedIn) DateStatusBar(name, onEndDate) else CheckInBar(name, onCheckIn, onEndDate)
        tabBar()
    }
}
