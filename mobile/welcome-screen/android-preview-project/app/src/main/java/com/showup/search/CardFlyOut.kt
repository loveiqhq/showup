/*
 * CardFlyOut.kt
 * ShowUp · the fly-out a card makes when it gets a final answer (SHOWUP-185 20, reused by 186)
 *
 * "One implementation for the grid and this list" (`useCardFlyOut` in the kit). t = 0 when the back
 * transition has settled: hold [Motion.GRID_LEAVE_HOLD] (the filled heart or the label is visible),
 * then the card moves to the visible list's centre x and 72% of its height, scales to .12 and fades to
 * 0 over [Motion.GRID_LEAVE] on `cubic-bezier(.55,0,.8,.2)`; then it is gone and the rest move to
 * their new slots over [Motion.GRID_REFLOW] on `cubic-bezier(.2,.7,.2,1)` -- the lazy list's item
 * placement animation, which IS a FLIP. With reduced motion: a [Motion.GRID_LEAVE_REDUCED] fade, then
 * the reflow.
 *
 * THE FLYING CARD IS DRAWN ABOVE THE WHOLE LIST, in [FlyOutLayer], not inside its own slot. A lazy
 * list draws each item in a layer of its own, sized to the item, and a card moved within it was cut
 * off at its slot's edge -- it could not pass over the row below, as the kit's `z-index: 5` lets it
 * (the 185 frame K review, 25 Oct 2026). So the slot keeps its place, drawn empty, while a copy of the
 * card flies in the layer over the list; when the flight ends the slot goes and the rest reflow.
 *
 * WHERE AND HOW FAR are read where they are drawn: the copy's box in its layout step, its transform in
 * its draw step, from the slot's measured box. Nothing waits on a recomposition after layout -- which
 * is also what lets a frozen frame (evidence K, H) be captured exactly where the motion puts it.
 *
 * UNDO MID-FLIGHT stops it: the card's leaving ends, the flight is dropped, and the card is drawn in
 * its slot again at full opacity, with no transform left behind.
 */
package com.showup.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.showup.designsystem.Motion
import com.showup.designsystem.rememberMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The fit harness's anchor for a card in flight. */
const val FLY_TAG_PREFIX = "fly-"

/** `cubic-bezier(.55,0,.8,.2)`: the fly-out accelerates away. */
val GridLeaveEasing = CubicBezierEasing(0.55f, 0f, 0.8f, 0.2f)

/** `cubic-bezier(.2,.7,.2,1)`: the reflow settles in. */
val GridReflowEasing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * The reflow, as a lazy list's item placement takes it. With reduced motion too ("a 200 ms fade, then
 * the reflow"); the system's own animation scale shortens it, to nothing when animations are off.
 */
fun gridReflowSpec(): FiniteAnimationSpec<IntOffset> = tween(Motion.GRID_REFLOW, easing = GridReflowEasing)

/** How far into a frozen flight [freezeMs] is, eased; null for a live list. */
fun frozenProgress(freezeMs: Int?): Float? =
    freezeMs?.let { GridLeaveEasing.transform((it.toFloat() / Motion.GRID_LEAVE).coerceIn(0f, 1f)) }

/**
 * What a list's cards in flight share with the layer that draws them: one per list, remembered by
 * it, read by [FlyOutLayer] and written by each card's [cardFlyOut].
 */
@Stable
class FlyOutHost {
    /** The visible list, in root coordinates: where every flight is aimed. */
    var viewport by mutableStateOf<Rect?>(null)

    /** The layer's own place, in root coordinates. */
    var origin by mutableStateOf<Rect?>(null)

    /** Each card's slot, in root coordinates, as last laid out before it took off. */
    val slots = mutableStateMapOf<String, Rect>()

    /** The cards in flight, and how far each is: 0 to 1, already eased. */
    val flights = mutableStateMapOf<String, Float>()

    /** False with reduced motion: the flight is a fade in place. */
    var shrinks by mutableStateOf(true)
}

/**
 * The fly-out, on one card's slot. While the card flies the slot draws nothing -- the copy in the
 * layer above is the card -- and keeps its place until [onLeft].
 *
 * @param leaving the card got a final answer and is on its way out.
 * @param settled the list is the screen again -- the back transition has finished.
 * @param onLeft the flight is over: the card can leave the list.
 * @param freezeMs evidence only: the card held this far into its flight, as frames K and H draw it.
 */
@Composable
fun Modifier.cardFlyOut(
    host: FlyOutHost,
    key: String,
    leaving: Boolean,
    settled: Boolean,
    onLeft: () -> Unit,
    freezeMs: Int? = null,
): Modifier {
    val motion = rememberMotion()
    val progress = remember { Animatable(0f) }
    val left by rememberUpdatedState(onLeft)
    val leavingNow by rememberUpdatedState(leaving)
    val frozen = freezeMs != null

    LaunchedEffect(leaving, settled, freezeMs) {
        if (!leaving) {
            progress.snapTo(0f)
            host.flights.remove(key)
            return@LaunchedEffect
        }
        if (!settled || frozen) return@LaunchedEffect
        delay(Motion.GRID_LEAVE_HOLD.toLong())
        // Not before both boxes are laid out: taken any earlier, the flight has nowhere to aim.
        snapshotFlow { host.slots[key] }.filterNotNull().first()
        snapshotFlow { host.viewport }.filterNotNull().first()
        host.shrinks = motion.enabled
        progress.snapTo(0f)
        host.flights[key] = 0f
        try {
            progress.animateTo(
                1f,
                // Reduced motion: the reference's fade, 200 ms on the flight's own curve.
                tween(if (motion.enabled) Motion.GRID_LEAVE else Motion.GRID_LEAVE_REDUCED, easing = GridLeaveEasing),
            ) { host.flights[key] = value }
        } catch (stopped: CancellationException) {
            // INTERRUPTED -- Undo, or the list no longer the settled screen. The copy goes with the
            // flight, never left frozen half-faded over the list; the card is drawn in its slot again
            // and, still leaving, flies again once the list settles.
            host.flights.remove(key)
            throw stopped
        }
        // LANDED. The copy stays, invisible at the end of its flight, until the card has left the
        // list: removed first, the slot was drawn again for the frame before `onLeft` landed.
        left()
    }

    // The card leaving the screen while on its way out -- scrolled away mid-flight, or the list
    // itself going -- finishes it at once, as opening another card does: no copy is left behind for
    // a slot that is no longer there.
    DisposableEffect(key) {
        onDispose {
            host.flights.remove(key)
            host.slots.remove(key)
            if (leavingNow) left()
        }
    }

    // Hidden while a copy of it flies -- live, or frozen for the evidence frame.
    val inFlight = leaving && (frozen || host.flights.containsKey(key))
    return this
        // The slot's box until its live flight starts -- then it is the flight's starting point and
        // holds still. A frozen card never starts one, so its box is always the slot's.
        //
        // The WHOLE box, not the part of it the list shows (`boundsInRoot` is cut at the list's
        // edge): a card half below the fold flew as a copy squeezed into its visible half, its
        // `View profile` crushed at the largest type on the smallest phones (fit sweep, 25 Oct
        // 2026). `getBoundingClientRect` in the kit is the whole box too.
        .onGloballyPositioned { if (!host.flights.containsKey(key)) host.slots[key] = Rect(it.positionInRoot(), it.size.toSize()) }
        .then(if (inFlight) Modifier.alpha(0f) else Modifier)
}

/**
 * The layer over a list where its cards fly: each flight is [card] drawn again at its slot, scaled
 * about its centre, moved toward its target and faded -- `transform: translate(dx, dy) scale(s);
 * opacity` -- above every other card. Drawn only, never a control: the slot keeps the card's
 * semantics.
 *
 * @param frozenKeys evidence only: cards held [frozenAt] into their flight.
 */
@Composable
fun FlyOutLayer(
    host: FlyOutHost,
    card: @Composable (key: String) -> Unit,
    modifier: Modifier = Modifier,
    frozenKeys: List<String> = emptyList(),
    frozenAt: Float? = null,
) {
    // Cut at the list's edge, as the kit's scroll container cuts its cards: a copy starting half
    // below the fold must not be drawn over the tab bar.
    Box(modifier.clipToBounds().onGloballyPositioned { host.origin = Rect(it.positionInRoot(), it.size.toSize()) }) {
        val keys = (host.flights.keys + frozenKeys).distinct()
        keys.forEach { key ->
            Box(
                Modifier
                    // THE TRANSFORM IS ON A LAYER THE OVERLAY'S SIZE, pivoting on the card's centre. A
                    // layer only the card's size is cut at its own untransformed box by some renderers
                    // (Robolectric's, which draws the evidence frames); one this big cuts nothing inside
                    // the list, and draws the same everywhere.
                    .graphicsLayer {
                        val slot = host.slots[key] ?: return@graphicsLayer
                        val view = host.viewport ?: return@graphicsLayer
                        val origin = host.origin ?: return@graphicsLayer
                        val p = host.flights[key] ?: frozenAt ?: 0f
                        alpha = 1f - p
                        if (frozenAt != null || host.shrinks) {
                            val (dx, dy) = flyOutTarget(
                                slot.left, slot.top, slot.width, slot.height,
                                view.left, view.top, view.width, view.height,
                            )
                            if (size.width > 0f && size.height > 0f) {
                                transformOrigin = TransformOrigin(
                                    (slot.center.x - origin.left) / size.width,
                                    (slot.center.y - origin.top) / size.height,
                                )
                            }
                            translationX = dx * p
                            translationY = dy * p
                            val s = 1f - (1f - FLY_OUT_SCALE) * p
                            scaleX = s
                            scaleY = s
                        }
                    }
                    // The card at its slot inside that box, read here in layout: no recomposition stands
                    // between the slot being laid out and its copy following it.
                    .layout { measurable, constraints ->
                        val slot = host.slots[key]
                        val origin = host.origin
                        if (slot == null || origin == null) return@layout layout(constraints.maxWidth, constraints.maxHeight) {}
                        val placeable = measurable.measure(Constraints.fixed(slot.width.roundToInt(), slot.height.roundToInt()))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place((slot.left - origin.left).roundToInt(), (slot.top - origin.top).roundToInt())
                        }
                    },
            ) {
                // Nothing for a screen reader -- the slot keeps the card's name -- but findable by the fit
                // harness, which checks where the flight is.
                Box(Modifier.clearAndSetSemantics { testTag = FLY_TAG_PREFIX + key }) { card(key) }
            }
        }
    }
}
