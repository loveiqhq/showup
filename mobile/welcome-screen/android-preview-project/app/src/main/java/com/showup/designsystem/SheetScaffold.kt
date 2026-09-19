/*
 * SheetScaffold.kt
 * ShowUp · the chrome every bottom sheet in the product shares
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * EXTRACTED FROM ProfilePromptsScreen ON 17 SEPTEMBER 2026
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * These three lived as private functions inside a screen file, which is the shape of the mistake
 * this project already paid for once: `PillButton` sat in `WelcomeShell.kt`, whoever wrote the
 * tutorial's CTA had no reason to open the sign-up flow's shell, and so they wrote their own — and
 * the copy nobody was looking at drifted four ways over three weeks.
 *
 * SHOWUP-161 is the second user. Its acceptance criteria describe this chrome as already shared —
 * "Sheet chrome is the shared one — radius 32 32 0 0, 40 x 4 grabber, 36px close X, sheet-rise
 * 360ms, max-height 660 with the scroll mask" — so the only way to satisfy it was to make that
 * true. Nothing about the behaviour changed in the move.
 *
 * The dismissal vocabulary is the registry's §23 and the reason it is one set: `close` is the X,
 * `backdrop` is the scrim, `swipe` is the drag, and `system_back` is the Android gesture, which is
 * NOT the same act as the X and must never be folded into it (decision 32).
 */
package com.showup.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.showup.profile.SheetDismissMethod
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** Copy shared by every sheet. */
object SheetCopy {
    const val DISMISS = "Dismiss"
}

/**
 * How far the sheet must be dragged before it counts as dismissed.
 *
 * A threshold rather than any downward movement, so a small nudge springs back rather than
 * closing a sheet the user was only inspecting.
 */
private const val SWIPE_DISMISS_PX = 120f

/** The sheet's top corners. `32 32 0 0` — it is docked to the bottom edge and has no lower ones. */
val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * The tallest a sheet may be, from the reference's `max-height: 660`.
 *
 * Past this the list inside scrolls, under the fade mask, rather than the sheet growing into the
 * screen it is docked over.
 */
val SheetMaxHeight = 660.dp

/** The drag grabber. Decorative: a sheet is dismissed by the X, the scrim or the drag, not by this. */
@Composable
fun SheetGrabber() {
    Box(
        Modifier
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Fg.copy(alpha = 0.18f))
            .clearAndSetSemantics {},
    )
}

/**
 * The close X. Placed by the caller, because sheets pad differently.
 *
 * DRAWS 36 AND ANSWERS AT 44. The reference specifies a 36 control and the fit harness flagged it
 * at all seventeen sizes the first time it was built that way -- "36.0dp, unusable below 44" --
 * which is the rule every tappable thing in this app is held to. `requiredSize` ignores the 36
 * layout slot and overflows it symmetrically, so the hit area grows and nothing visible moves.
 * Same trick, same reason, as the back chevron in AppHeader and the photo grid's remove pip.
 */
@Composable
fun SheetCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(ComponentSizes.minTapTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = SheetCopy.DISMISS },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Fg.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Close, 20.dp, tint = Fg, strokeWidth = 1.8f)
            }
        }
    }
}

/**
 * The scrim and the rising surface every sheet sits in.
 *
 * `sheet-rise` is 28 up and 0.85 -> 1 opacity over [Motion.SHEET] -- a shared keyframe, not a
 * per-sheet animation, and skipped entirely when the device asks for no motion.
 */
@Composable
fun SheetScaffold(
    onDismiss: (SheetDismissMethod) -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = rememberMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 28f,
        animationSpec = tween(
            durationMillis = if (motion.enabled) Motion.SHEET else 0,
            easing = ShowUpEasing,
        ),
        label = "sheetRise",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = tween(durationMillis = if (motion.enabled) Motion.SHEET else 0),
        label = "sheetFade",
    )

    // THE ANDROID BACK GESTURE CLOSES THE SHEET, and reports itself as what it is.
    //
    // Without this the gesture fell through to the host and left the step entirely, which is a
    // different act with a different outcome, and the sheet the user was trying to close was
    // still open when they got back. `system_back` is Android-only and the registry is explicit
    // that it is NOT the same as the X.
    BackHandler { onDismiss(SheetDismissMethod.SystemBack) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Fg.copy(alpha = 0.42f))
                // THE SCRIM IS `backdrop`, NOT `close`. Four values, four different acts:
                // the registry unified them on 16 September 2026 precisely because three
                // spellings of the same four acts had drifted apart, and folding two of them
                // together here would put the drift back inside one screen.
                .clickable(role = Role.Button) { onDismiss(SheetDismissMethod.Backdrop) }
                .semantics { contentDescription = SheetCopy.DISMISS },
        )
        // SWIPE-DOWN. A bottom sheet that cannot be pushed down is wrong on a phone regardless of
        // what any ticket says; the drag is tracked so the finger stays on the sheet, and it only
        // counts as a dismissal past a threshold, so a small nudge springs back.
        var drag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = rise * density + drag
                    alpha = fade
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag > SWIPE_DISMISS_PX) onDismiss(SheetDismissMethod.Swipe)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, delta ->
                        // Downwards only. Dragging a bottom sheet UP would detach it from the
                        // edge it is docked to, and no sheet in this product is positioned by a
                        // top offset.
                        drag = (drag + delta).coerceAtLeast(0f)
                    }
                }
                // The keyboard is not ours and its height is not knowable, and neither is the
                // gesture bar's. `safeDrawing` bottom is BOTH -- it reports the keyboard when one
                // is up and the navigation inset when one is not, taking whichever is larger, so
                // a sheet's CTA clears the keys while typing and the gesture bar while not.
                //
                // `imePadding()` alone was wrong here: with the keyboard down it reserves nothing,
                // and a sheet's own bottom padding on a gesture-navigation device puts its CTA
                // underneath the bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            content()
        }
    }
}
