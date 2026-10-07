/*
 * ScrollIndicator.kt
 * ShowUp · the scroll indicator Android Views draw and Compose does not
 *
 * Orientation, dating language, religion and politics all say the same thing (SHOWUP-169, 170,
 * 172, 173): "The scroll indicator is the platform's. We flash it once on arrival. We do not draw a
 * fade, a chevron or a 'more' hint."
 *
 * ON ANDROID THERE IS NO PLATFORM INDICATOR TO FLASH. A `ScrollView` draws one and
 * `awakenScrollBars()` flashes it; Compose's `verticalScroll` draws nothing at all, so "flash the
 * platform scroll indicator" has nothing to call. This is that indicator, drawn the way the
 * platform draws it -- a thin thumb on the trailing edge, shown while the content moves and faded
 * out after -- and timed by the platform's own [ViewConfiguration] values rather than numbers
 * chosen here, so it behaves like the one a `ScrollView` would have shown.
 *
 * It is NOT a "more" hint: it appears only when the content really overflows, and it carries no
 * arrow, no fade over the content and no copy. On iOS the real one is used, with
 * `scrollIndicatorsFlash(onAppear:)`.
 */
package com.showup.designsystem

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Draws a scroll indicator for [state] over this node's trailing edge.
 *
 * Put it BEFORE `verticalScroll` in the chain, so it draws in the viewport's coordinates rather than
 * scrolling away with the content.
 *
 * @param flashOnArrival show it once when the content first turns out to overflow -- and never when
 *   it does not ("at 430 x 932 nothing overflows and nothing flashes").
 */
fun Modifier.scrollIndicator(state: ScrollState, flashOnArrival: Boolean): Modifier = composed {
    val context = LocalContext.current
    val config = remember(context) { ViewConfiguration.get(context) }
    val motion = rememberMotion()
    val alpha = remember { Animatable(0f) }
    val fadeMs = if (motion.enabled) ViewConfiguration.getScrollBarFadeDuration() else 0
    val holdMs = ViewConfiguration.getScrollDefaultDelay().toLong()

    // ONE COROUTINE OWNS THE ALPHA. Two effects animating the same Animatable interrupt each other
    // -- the scroll collector's first "not scrolling" faded the arrival flash out early, and the
    // interrupted animation could end the indicator for the rest of the screen.
    LaunchedEffect(state, flashOnArrival) {
        // Once, on arrival, and only if there is somewhere to scroll. Layout settles a frame after
        // composition, so it waits briefly for the content to report an overflow rather than
        // reading zero and deciding there is none. The flash holds until the user scrolls.
        if (flashOnArrival) {
            val overflows = withTimeoutOrNull(1_000) { snapshotFlow { state.maxValue }.first { it > 0 } }
            if (overflows != null) {
                alpha.snapTo(1f)
                withTimeoutOrNull(holdMs * 2) { snapshotFlow { state.isScrollInProgress }.first { it } }
            }
        }
        // Then shown while the content moves, faded after -- the platform's own rhythm. A new
        // value cancels the fade in progress, so a scroll mid-fade shows the thumb at once.
        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                alpha.snapTo(1f)
            } else {
                delay(holdMs)
                alpha.animateTo(0f, tween(fadeMs))
            }
        }
    }

    drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || alpha.value <= 0f) return@drawWithContent
        val viewport = size.height
        val total = viewport + max
        val thickness = config.scaledScrollBarSize.toFloat().coerceAtMost(4.dp.toPx())
        val thumb = (viewport * viewport / total).coerceAtLeast(24.dp.toPx())
        val top = (state.value.toFloat() / max) * (viewport - thumb)
        drawRoundRect(
            color = Fg.copy(alpha = 0.32f * alpha.value),
            topLeft = Offset(size.width - thickness, top),
            size = Size(thickness, thumb),
            cornerRadius = CornerRadius(thickness / 2f, thickness / 2f),
        )
    }
}
