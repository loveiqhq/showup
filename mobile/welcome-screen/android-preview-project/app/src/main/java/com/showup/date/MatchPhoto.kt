/*
 * MatchPhoto.kt
 * ShowUp · the kit's `Avatar`: the match's photo in a circle, or their initial on the placeholder
 *
 * Taken out of the Date screen's unread card with the coordinate chat (SHOWUP-194), whose header,
 * bubbles and outcome screens draw the same face at 40, 26 and 72. One drawing, so the initial's
 * size, weight and ink cannot drift between them.
 */
package com.showup.date

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.showup.designsystem.Lora
import com.showup.search.PhotoBackdrop

/**
 * [size] square, clipped round: the photo when there is one, otherwise the kit's placeholder with the
 * initial -- Lora 600 at 0.42 of the size, white at 92%. Decorative: the name is always beside it.
 */
@Composable
internal fun MatchPhoto(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(modifier.size(size).clip(shape).clearAndSetSemantics {}) {
        PhotoBackdrop(photoUrl.orEmpty(), 0, Modifier.fillMaxSize())
        if (photoUrl == null) {
            Text(
                name.take(1),
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.92f),
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                // A fixed-size mark: it scales with the circle, never with the user's type size, which ran it
                // out of the 26 bubble avatar at 2x (the 194 fit sweep).
                fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() },
            )
        }
    }
}
