/*
 * SheetButtons.kt
 * ShowUp · the date flow's sheet buttons: a sunset primary (radius 18) and a white secondary (radius 16)
 *
 * NOT `PrimaryButton`: that one is a pill, and these two are the rounded-rectangle pair the date sheets
 * draw (the arrival confirm, the Date 05 pickers). The arrival sheet held the primary privately; a second
 * private copy for the pickers would be the drift this project has paid for, so it lives here and both
 * call it.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.date.pressable
import com.showup.search.cssLinearGradient

/**
 * The sunset button, radius 18, h 56, 16.5 / 700 white, under `--liq-shadow-cta`. [enabled] false reads at
 * alpha .45 with no glow (the picker's `Pick a time`); [waiting] reads at .6 with the glow, inert, as a send
 * in flight does (the 194 End date and 195 guard waits). Neither is tappable.
 */
@Composable
fun SheetPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    waiting: Boolean = false,
    /** A mark before the label, 9 from it (Date 06's `Message {name}`: `message-circle` 20 / 2.2). */
    leading: (@Composable () -> Unit)? = null,
) {
    val live = enabled && !waiting
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (!enabled) 0.45f else if (waiting) 0.6f else 1f)
            .then(if (live) Modifier.pressable(Role.Button, onClick = onClick) else Modifier.semantics { role = Role.Button; disabled() })
            // The glow behind, then the reference's 135deg sunset clipped to the radius-18 shape.
            .then(if (enabled) Modifier.roundedShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), 18.dp, ShadowCta) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first }),
        contentAlignment = Alignment.Center,
    ) {
        if (leading == null) {
            Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
            }
        }
    }
}

/**
 * The white secondary, h 50 (a minimum, so the largest type grows it), radius 16, the soft border, 14.5 / 700 muted.
 *
 * Date 06 draws it in four more ways, all defaults here: B and H's `Cancel` (h 52, 15), `Stay for now` in the
 * ink, and the guide's end button, which [locked] greys (the raised fill, the subtle ink, inert) and may lead
 * with a mark ([leading], 7 from the label).
 */
@Composable
fun SheetSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inert: Boolean = false,
    minHeight: Dp = 50.dp,
    fontSize: TextUnit = 14.5.sp,
    textColor: Color = Muted,
    locked: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val still = inert || locked
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (still) Modifier.semantics { role = Role.Button; disabled() } else Modifier.pressable(Role.Button, onClick = onClick))
            .background(if (locked) Raised else Elevated, shape)
            .border(1.dp, BorderSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (locked) FgSubtle else textColor
        if (leading == null) {
            Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = fontSize)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                Text(label, color = ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = fontSize)
            }
        }
    }
}
