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

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant

import com.showup.designsystem.Spacing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.remember
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

/**
 * The flag for a country, from the bundled artwork.
 *
 * Drawn flags were replaced by real artwork once the list went from 35 countries to every country.
 * Hand-drawing did not scale and was not honest about it: nine were drawn by hand and two came out
 * wrong — Canada as a spiky asterisk, Portugal as a logo. At 250 that rate means dozens wrong, and
 * a wrong national flag is not a cosmetic bug.
 *
 * These are the flag-icons set (MIT, see assets/flags/LICENSE-flag-icons.txt), rasterised to 96x72
 * — enough for a 22dp mark at four times density. 257 files, 479 KB in total, about 1.9 KB each.
 *
 * A country with no artwork falls back to its ISO code rather than an empty box, so a gap looks
 * deliberate instead of broken.
 */
@Composable
fun Flag(country: Country, width: Dp = 22.dp) {
    val height = width * 14f / 22f
    val shape = RoundedCornerShape(2.dp)
    val context = LocalContext.current

    // Decoded once per country and kept: the picker scrolls through hundreds of rows, and decoding
    // a bitmap on every frame of a fling is exactly how a list starts to stutter.
    val bitmap = remember(country.iso) { loadFlag(context, country.iso) }

    val box = Modifier
        .size(width = width, height = height)
        .clip(shape)
        .border(0.5.dp, Color(0x591D1129), shape)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            // Decorative: the country's name sits next to it in the list, and the dial code is on
            // the pill, so a screen reader gains nothing from "flag of Germany".
            contentDescription = null,
            modifier = box,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(box.background(Raised), contentAlignment = Alignment.Center) {
            Text(
                country.iso, color = Muted, fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 0.5.sp,
            )
        }
    }
}

/** Null when the country has no bundled flag, which the caller renders as the ISO code. */
private fun loadFlag(context: android.content.Context, iso: String): ImageBitmap? = try {
    context.assets.open("flags/" + iso.uppercase() + ".png").use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
} catch (e: java.io.IOException) {
    null
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
            Box(Modifier.fillMaxWidth().padding(top = Spacing.lg), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50)).background(Border))
            }
            Text(
                "Choose your country",
                Modifier.padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.xxl, bottom = Spacing.xl),
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
                            .padding(horizontal = Spacing.screenGutter, vertical = 13.dp),
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
                        else Spacer(Modifier.width(Spacing.xxl))
                    }
                }
            }
            // Clear of the gesture bar without the sheet needing to know about insets.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xl)
            ) {
                PrimaryButton("Close", onDismiss, variant = PrimaryButtonVariant.Ghost)
            }
        }
    }
}
