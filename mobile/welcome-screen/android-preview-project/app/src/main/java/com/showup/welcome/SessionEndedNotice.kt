package com.showup.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Spacing
import com.showup.designsystem.ToastShadow
import com.showup.designsystem.boxShadow
import com.showup.designsystem.rememberMotion
import kotlinx.coroutines.delay

/**
 * What the user reads when the server has ended their session.
 *
 * PROPOSED COPY, pending Philipp. "Log in", not "sign in", because "Log in" is the link on the
 * Startup screen this appears over -- the sentence names the thing to tap. iOS carries the same
 * string in `SessionEnded.swift`.
 */
const val SESSION_ENDED_MESSAGE = "Your session ended. Please log in again."

/**
 * The "please log in again" notice: a dark chip drawn ABOVE the Startup screen's legal line,
 * taking no space in it.
 *
 * WHERE, AND WHY THERE. It first hung from the top of the screen, and on a device it sat squarely
 * on the wordmark. Above the footer is where every toast in this app already appears (the refusal
 * toast on photos, prompts and the details group), it is the empty middle of Startup at every size,
 * and it is right over "Create free account" and "Log in" -- the sentence sits next to the thing it
 * tells you to do.
 *
 * The same chip as `RefusalToast` -- one family of messages, one look -- and anchored the same way:
 * measured, then placed above the anchor with a reported size of zero, so Startup measures
 * identically with and without it and nothing moves when it appears.
 *
 * It goes after [Motion.NOTICE], or the moment Startup leaves the screen. Not tappable: drawn
 * outside its anchor's bounds, a tap would never reach it on Android, and a chip that looks
 * dismissable and is not is worse than one that simply goes.
 */
@Composable
fun BoxScope.SessionEndedNotice(visible: Boolean, onDismiss: () -> Unit) {
    // The dismissal the timer calls is the latest one handed in, not the one captured when the
    // notice went up.
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        if (visible) {
            delay(Motion.NOTICE.toLong())
            dismiss()
        }
    }
    // Leaving Startup -- to the phone step, or anywhere -- ends it, so it cannot come back with a
    // fresh clock when the user returns.
    DisposableEffect(Unit) { onDispose { dismiss() } }

    val motion = rememberMotion()
    val fade = if (motion.enabled) Motion.FAST else 0
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(fade), label = "noticeAlpha")
    // 6 up on the way in, the refusal toast's `translateY(6px)` resting state.
    val rise by animateFloatAsState(if (visible) 0f else 6f, tween(fade), label = "noticeRise")

    Box(
        Modifier
            .align(Alignment.TopCenter)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Report nothing, so the legal line measures identically in both states.
                layout(0, 0) {
                    placeable.place(-placeable.width / 2, -placeable.height - Spacing.lg.roundToPx())
                }
            },
    ) {
        Text(
            SESSION_ENDED_MESSAGE,
            color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = (13f * 1.2f).sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = rise * density
                }
                .widthIn(max = 300.dp)
                .boxShadow(fill = Fg, cornerRadius = 14.dp, shadow = ToastShadow, offsetY = 8.dp, blur = 22.dp)
                .padding(horizontal = 14.dp, vertical = Spacing.lg)
                // Announced when it arrives -- TalkBack users are the ones who would otherwise
                // never know why they are back at the start -- and absent from the tree when gone.
                .then(
                    if (visible) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                ),
        )
    }
}
