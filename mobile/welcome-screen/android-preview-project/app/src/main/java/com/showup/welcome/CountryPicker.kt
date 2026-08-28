/*
 * CountryPicker.kt
 * ShowUp · the flag mark and the country list sheet (SHOWUP-143)
 *
 * The ticket puts the country list itself out of scope, so this is the smallest thing that lets a
 * user actually change their dial code: a bottom sheet, one row per country, the current one
 * marked. No search — at this length the list scrolls fine, and a search field is a second input to
 * design and test for no benefit yet.
 */
package com.showup.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised

/** 22 x 14 with a hairline, so a white stripe against a white field still reads as a flag. */
@Composable
fun Flag(country: Country, width: Dp = 22.dp) {
    val height = width * 14f / 22f
    val shape = RoundedCornerShape(2.dp)
    val outline = Modifier
        .size(width = width, height = height)
        .clip(shape)
        .border(0.5.dp, Color(0x591D1129), shape)

    when (val art = country.flag) {
        is FlagArt.Bands -> {
            if (art.horizontal) {
                Column(outline) {
                    art.stripes.forEach { (c, w) ->
                        Box(Modifier.weight(w.toFloat()).fillMaxWidth().background(Color(c)))
                    }
                }
            } else {
                Row(outline) {
                    art.stripes.forEach { (c, w) ->
                        Box(Modifier.weight(w.toFloat()).fillMaxHeight().background(Color(c)))
                    }
                }
            }
        }

        is FlagArt.Cross -> {
            Box(outline.background(Color(art.bg))) {
                // The Nordic cross sits left of centre; the Swiss one is centred. Arm thickness is
                // ~2/9 of the height either way, which is close enough at 14px to read correctly.
                val arm = height * 0.22f
                val vx = if (art.centred) (width - arm) / 2 else width * 0.30f - arm / 2
                Box(Modifier.fillMaxWidth().height(arm).align(Alignment.CenterStart)
                    .background(Color(art.arm)))
                Box(Modifier.align(Alignment.TopStart).offset(x = vx).width(arm).fillMaxHeight()
                    .background(Color(art.arm)))
                if (art.inner != null) {
                    val thin = arm * 0.45f
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(thin)
                        .background(Color(art.inner)))
                    Box(Modifier.align(Alignment.TopStart).offset(x = vx + (arm - thin) / 2)
                        .width(thin).fillMaxHeight().background(Color(art.inner)))
                }
            }
        }

        // Honest fallback. See the note on FlagArt.Code.
        FlagArt.Code -> Box(
            outline.background(Raised),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                country.iso, color = Muted, fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * The list, as a bottom sheet over the screen.
 *
 * Opens scrolled to the current country rather than at the top — the user is here to change a
 * value they can already see, so showing them where they are is the first useful thing.
 */
@Composable
fun CountrySheet(
    current: Country,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = COUNTRIES.indexOfFirst { it.iso == current.iso }
        if (i > 0) listState.scrollToItem(i)
    }

    Box(Modifier.fillMaxSize()) {
        // Tappable here, unlike the conflict modal on 144: this sheet is a convenience with a
        // harmless dismiss, not a decision the user has to make.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x6B140C1E))
                .clickable(onClick = onDismiss)
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Elevated),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50)).background(Border))
            }
            Text(
                "Choose your country",
                Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(COUNTRIES, key = { it.iso }) { c ->
                    val selected = c.iso == current.iso
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(c) }
                            .background(if (selected) Orange.copy(alpha = 0.07f) else Color.Transparent)
                            .padding(horizontal = 24.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Flag(c)
                        Text(
                            c.name, Modifier.weight(1f),
                            color = Fg, fontFamily = Manrope,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                        Text(
                            c.dial,
                            color = if (selected) Purple else Muted, fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                        if (selected) Icon(BrandIcon.Check, 16.dp, tint = Purple, strokeWidth = 2.4.dp)
                        else Spacer(Modifier.width(16.dp))
                    }
                }
            }
            // Clear of the gesture bar without the sheet needing to know about insets.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                PillButton("Close", onDismiss, variant = PillVariant.Ghost)
            }
        }
    }
}
