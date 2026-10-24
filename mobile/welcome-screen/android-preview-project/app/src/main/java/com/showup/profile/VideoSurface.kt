/*
 * VideoSurface.kt
 * ShowUp · frames, drawn by the player rather than decoded into a bitmap
 *
 * ONE COMPONENT, three users: the video review screen, the filled video card (SHOWUP-161), and
 * Search's read-only video card (SHOWUP-184). It was two identical private copies, in
 * `MediaCaptureScreen.kt` and `MediaCards.kt`, until Search needed a third -- which is the moment
 * the rule says it moves.
 *
 * A PLAYER WITH NOWHERE TO DRAW PLAYS THE AUDIO AND NOTHING ELSE, which on a video is worse than not
 * playing at all: a press of play on a picture, and a voice from a still. `useController = false`
 * because the transport controls are ours on every screen, and ExoPlayer's own bar is none of them.
 */
package com.showup.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
internal fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
