/*
 * PremiumParts.kt
 * ShowUp · what the Premium grid (SHOWUP-185) and the Likes tab (SHOWUP-186) both draw
 *
 * The `Liked you` badge (on a grid card and on the profile's hero), the white label pill (a marked or
 * closed card, the sorry sheet's reason, the list's `Not now`), the undo toast, CSS `grayscale()`,
 * and the frame both screens stand in: top bar, the one scroll region, the TabBar, and the toast 12
 * above it. Built once, here, because both stories consume them -- "one implementation".
 */
package com.showup.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.PhotoInk
import com.showup.designsystem.Purple
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.designsystem.roundedShadows
import com.showup.designsystem.shrinkToFit
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.Spinner

/** Test and evidence anchors. */
const val UNDO_TOAST_TAG = "undo-toast"
const val PREMIUM_LIST_TAG = "premium-list"

private val Sunset = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())

/** `0 2px 8px rgba(0,0,0,.18)` -- the badge, lifted off the photo it sits on. */
private val BadgeLift = listOf(CssShadow(Color.Black.copy(alpha = 0.18f), offsetY = 2.dp, blur = 8.dp))

/**
 * `PremiumLikedYouBadge`: a sunset pill, padding `4 9 4 7`, gap 4, the filled heart 11, `Liked you`
 * in Manrope 800 / 10.5 with `letter-spacing: 0.04` (px). On a grid card and on the profile's hero.
 * Read as part of its card or hero, never on its own.
 */
@Composable
fun LikedYouBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .pillShadows(Sunset, BadgeLift)
            .padding(start = 7.dp, end = 9.dp, top = 4.dp, bottom = 4.dp)
            .clearAndSetSemantics { contentDescription = PremiumCopy.LIKED_YOU },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.HeartFilled, 11.dp, tint = Color.White)
        // Shrinks rather than cuts where a large type outgrows a narrow card's corner.
        Text(
            PremiumCopy.LIKED_YOU, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp, lineHeight = 10.5.sp, letterSpacing = with(LocalDensity.current) { 0.04.dp.toSp() },
            maxLines = 1, softWrap = false, modifier = Modifier.cssLineBox(10.5.sp).shrinkToFit(0.6f, origin = 0f),
        )
    }
}

/**
 * The white label pill: `#fff`, 1px `--liq-border`, Manrope 800 / 11 in `--liq-fg-muted`, nowrap.
 * `5 10` on a grid card and the sorry sheet; `4 9` for the Likes list's `Not now` ([compact]).
 */
@Composable
fun PillLabel(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val pill = RoundedCornerShape(percent = 50)
    Text(
        text,
        color = Muted, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
        maxLines = 1, softWrap = false,
        modifier = modifier
            .background(Elevated, pill)
            .border(1.dp, Border, pill)
            .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 4.dp else 5.dp),
    )
}

/**
 * CSS `filter: grayscale(amount)`, drawn as the browser draws it: the whole layer -- photo,
 * placeholder and washes alike -- through one colour matrix. `grayscale(a)` is `saturate(1 - a)`
 * with the same luminance weights, so the platform's saturation matrix is that filter.
 */
fun Modifier.cssGrayscale(amount: Float): Modifier = drawWithContent {
    val matrix = ColorMatrix().apply { setToSaturation(1f - amount) }
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        canvas.restore()
    }
}

/**
 * [full] on one line when it fits, and [top] over [bottom] when it does not -- never cut, never run
 * out of its box. The grid card's meta line (`19:00 tomorrow · ~12 km`) is one line at 375 as the
 * ticket requires; on the narrowest phone at a large type it breaks at the dot instead of overflowing.
 */
@Composable
fun OneLineOrStacked(
    full: @Composable () -> Unit,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(full, top, bottom), modifier = modifier) { (f, t, b), constraints ->
        val loose = constraints.copy(minWidth = 0)
        val whole = f.first().measure(Constraints(maxHeight = constraints.maxHeight))
        if (whole.width <= constraints.maxWidth) {
            layout(whole.width, whole.height) { whole.place(0, 0) }
        } else {
            val first = t.first().measure(loose)
            val second = b.first().measure(loose)
            layout(maxOf(first.width, second.width), first.height + second.height) {
                first.place(0, 0)
                second.place(0, first.height)
            }
        }
    }
}

/** `0 8px 24px rgba(20,12,28,.22)` -- the toast, floating over the list. */
private val ToastLift = listOf(CssShadow(PhotoInk.copy(alpha = 0.22f), offsetY = 8.dp, blur = 24.dp))

/**
 * `LikesUndoToast` (Search 02 G, Search 03 D): min-height 52, padding `4 4 4 16`, radius 16,
 * `rgba(29,17,41,.92)`, gap 12; `{name}'s like declined.` in Manrope 600 / 14 / 1.35, white; `Undo`
 * a 44-high button, padding `0 14`, 800 / 14 in orange.
 *
 * A polite live region, so it is announced ("role=status"). Focus on `Undo` holds the window --
 * [onUndoFocus] tells the view model, which owns the clock.
 */
@Composable
fun LikesUndoToast(name: String, onUndo: () -> Unit, onUndoFocus: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            // Ahead of the padding: the node's box is the whole toast, for a screen reader's focus
            // ring and for the fit harness alike.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(UNDO_TOAST_TAG)
            .roundedShadows(androidx.compose.ui.graphics.SolidColor(Fg.copy(alpha = 0.92f)), 16.dp, ToastLift)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            undoLine(name), color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, lineHeight = (14 * 1.35).sp, modifier = Modifier.weight(1f).cssLineBox((14 * 1.35).sp),
        )
        Box(
            Modifier
                .height(44.dp)
                .onFocusChanged { onUndoFocus(it.isFocused) }
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(PremiumCopy.UNDO, color = Orange, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/**
 * A Premium screen's frame: the top bar, the region between it and the TabBar (the list, the one
 * scroll container), the TabBar at the bottom, and the toast floating 16 from each side and 12 above
 * the TabBar -- "it does not scroll with the grid". No fixed Y: everything is measured.
 */
@Composable
fun PremiumFrame(
    topBar: @Composable () -> Unit,
    region: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    toast: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(topBar, region, tabBar, toast ?: {}),
        modifier = modifier.fillMaxSize(),
    ) { (top, reg, tab, toastPart), constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val row = Constraints(minWidth = w, maxWidth = w)
        val t = top.first().measure(row)
        val b = tab.first().measure(row)
        val regionHeight = (h - t.height - b.height).coerceAtLeast(0)
        val r = reg.first().measure(Constraints.fixed(w, regionHeight))
        val side = 16.dp.roundToPx()
        val s = toastPart.firstOrNull()?.measure(Constraints(maxWidth = (w - 2 * side).coerceAtLeast(0), maxHeight = regionHeight))
        layout(w, h) {
            t.place(0, 0)
            r.place(0, t.height)
            b.place(0, h - b.height)
            s?.place(side, h - b.height - 12.dp.roundToPx() - s.height)
        }
    }
}

/** The toast's arrival: a short rise and fade, as the Check-In toast arrives ([Motion.REVEAL]). */
@Composable
fun UndoToastSlot(toast: UndoToast?, onUndo: () -> Unit, onUndoFocus: (Boolean) -> Unit) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.REVEAL else 0
    // The last toast stays drawn while it leaves, so its words do not blank mid-fade.
    val last = remember { mutableStateOf(toast) }
    if (toast != null) last.value = toast
    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn(tween(ms)) + slideInVertically(tween(ms)) { it / 6 },
        exit = fadeOut(tween(ms)) + slideOutVertically(tween(ms)) { it / 6 },
    ) {
        last.value?.let { LikesUndoToast(it.name, onUndo, onUndoFocus) }
    }
}

/** A list being read: the spinner, named for a screen reader. */
@Composable
fun ListLoading(label: String) {
    Box(Modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Spinner(tint = Purple, size = 24.dp)
    }
}

/**
 * A paragraph with CSS `text-wrap: pretty`, as the references set it on their body copy: a greedy wrap
 * that would leave one short word alone on the last line pulls one more down to it. The line box is
 * CSS's, so the paragraph is as tall as the sheet draws it.
 */
@Composable
fun PrettyText(text: String, style: TextStyle, modifier: Modifier = Modifier, centered: Boolean = false) {
    val annotated = AnnotatedString(text)
    Text(annotated, modifier.prettyWrap(annotated, style, centered).cssLineBox(style.lineHeight), style = style)
}

/** Space a list keeps clear above the toast: the Likes list's `88` bottom padding. */
val ToastClearance: Dp = 88.dp

/** A list's content padding. */
fun listPadding(top: Dp, bottom: Dp): PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom)
