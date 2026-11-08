/*
 * ModalShell.kt
 * ShowUp · the kit's `ModalShell` surface: Search's like sheets and the chat's End the date sheet
 *
 * MOVED HERE FROM `SearchSheets.kt` with the coordinate chat (SHOWUP-194), the second flow to draw it.
 * It was Search's private `SearchSheet`; a second copy in the chat would have been the start of the
 * drift the primary button went through. The behaviour -- scrim, back, swipe-down, rising, insets -- is
 * [SheetScaffold]'s; this is the surface the kit draws on it.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.showup.profile.SheetDismissMethod

/**
 * `ModalShell`'s `backdrop-filter: blur(8px)`: sigma 8 as the radius `Modifier.blur` takes,
 * (8 - 0.5) / 0.57735, the conversion `Shadows.kt` documents.
 *
 * NOT `blurBehindSheet`, which is the profile flow's 1.5 -- "a softening" by its own spec. The design
 * draws two sheet surfaces with two backdrops; borrowing that one put this sheet over a screen that
 * was barely softened (the 184 evidence review, 24 Oct 2026). Robolectric does not render a
 * RenderEffect, so no screenshot here could have shown it. A no-op on Android 11, where the 42%
 * scrim alone separates the sheet, as with every blur in this app.
 */
private val ModalShellBlur = 12.99.dp

/** The blur behind a [ModalShell], [fraction] 0 to 1 so it arrives with the sheet rather than snapping on. */
fun Modifier.blurBehindModalShell(fraction: Float): Modifier =
    if (fraction > 0f) blur(ModalShellBlur * fraction) else this

/** `0 -8px 32px rgba(46,1,71,.18)` -- the sheet's shadow, cast upward over the screen. */
private val SheetLift = listOf(CssShadow(ShadowInk.copy(alpha = 0.18f), offsetY = (-8).dp, blur = 32.dp))

/**
 * The kit's `ModalShell` surface inside the app's one sheet behaviour: cream, top corners 28, the
 * grabber 36 x 4, at most 88% of the frame and scrolling past it.
 *
 * @param endOnKeyboard while the keyboard is up, keep the sheet scrolled to its end. On a short
 *   phone the message sheet outgrows the space above the keys, and its two buttons would sit behind
 *   them until the user thought to scroll; held at the end, the field, the counter and both buttons
 *   show together. Pinned again only when the scroll range changes -- the keyboard rising, the field
 *   growing a line -- so a user who scrolls up to read keeps their place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModalShell(
    label: String,
    onDismiss: (SheetDismissMethod) -> Unit,
    endOnKeyboard: Boolean = false,
    /** The content's padding: 24 all round for Search's like sheets, `20 24 24` for its sorry sheet. */
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val keyboard = endOnKeyboard && WindowInsets.isImeVisible
    LaunchedEffect(keyboard) {
        if (keyboard) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }
    SheetScaffold(onDismiss = onDismiss) {
        // The scaffold keeps the sheet above the gesture bar (or the keyboard); the kit's sheet runs
        // to the bottom edge, so its colour is painted down into that inset too -- otherwise a strip
        // of scrim shows under it on every gesture-navigation phone.
        val inset = WindowInsets.safeDrawing.getBottom(LocalDensity.current).toFloat()
        BoxWithConstraints(
            Modifier.drawBehind {
                drawRect(Cream, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, inset + 1f))
            },
        ) {
            // `max-height: 88%` of the frame the sheet is docked in; past it the sheet scrolls.
            val cap = maxHeight * 0.88f
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = cap)
                    .roundedShadows(SolidColor(Cream), 28.dp, SheetLift)
                    // Square bottom corners: docked to the edge, so only the top two round.
                    .background(Cream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .verticalScroll(scroll)
                    .semantics {
                        dialog()
                        paneTitle = label
                    },
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    SheetGrabber(width = 36.dp, color = Faint)
                }
                Column(Modifier.padding(contentPadding), content = content)
            }
        }
    }
}
