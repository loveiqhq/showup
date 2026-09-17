/*
 * ControlPip.kt
 * ShowUp · the small pill-shaped secondary action (SHOWUP-161)
 *
 * `Retake` and `Delete` on both filled media cards. One component with two tones, because the
 * acceptance criteria require it in as many words -- "`Retake` and `Delete` are the shared 32px
 * ControlPips -- neutral and danger tones -- IDENTICAL ON BOTH CARDS" -- and because a second copy
 * is how the video card's Delete and the voice card's Delete drift apart.
 *
 * It lives here rather than in a screen file for the reason `PillButton` taught this project the
 * hard way. See `SheetScaffold.kt`, which was extracted the same afternoon.
 *
 * NOT A [PrimaryButton] VARIANT. That component is a 56-tall full-width CTA with six variants and
 * two slots; this is a 32-tall bordered pill that sits in a row of two. They share no geometry, no
 * type and no role, and forcing one into the other would mean a variant that ignores most of its
 * own parameters.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** Which of the two pills this is. Delete is destructive and says so in red. */
enum class ControlPipTone { Neutral, Danger }

/** The drawn height. The touch area is [ComponentSizes.minTapTarget] -- see below. */
private val PIP_HEIGHT = 32.dp

@Composable
fun ControlPip(
    icon: BrandIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ControlPipTone = ControlPipTone.Neutral,
) {
    val content = if (tone == ControlPipTone.Danger) DangerFg else Fg
    val outline = if (tone == ControlPipTone.Danger) Danger.copy(alpha = 0.30f) else Border

    // DRAWS 32 AND ANSWERS AT 44, structured exactly like [SheetCloseButton]: the OUTER node
    // fixes the layout slot at the drawn height, and the inner `requiredHeight` overflows it
    // symmetrically, so the touch area grows and nothing visible moves.
    //
    // It grows only VERTICALLY, which is the one difference from the close X. The two pips sit in
    // a row 8dp apart; overflowing 6dp horizontally as well would make their hit areas overlap by
    // 4dp, and a tap near Retake's right edge would delete the recording. The pill is already far
    // wider than 44, so height is the only axis that needs anything.
    Box(modifier.height(PIP_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredHeight(ComponentSizes.minTapTarget)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .height(PIP_HEIGHT)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White)
                    .border(1.dp, outline, RoundedCornerShape(percent = 50))
                    .padding(horizontal = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, 13.dp, tint = content, strokeWidth = 2f)
                Text(
                    label,
                    color = content,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.01.sp,
                    // A PILL'S LABEL NEVER WRAPS. Squeezed into a narrow card the layout would
                    // rather break "Delete" over six lines than let the pill overflow, which the
                    // fit harness caught at 320 wide: 146px of text below the cut. The pill keeps
                    // its intrinsic width and the row around it is what yields -- see
                    // RecordedControls, where the descriptive label is the weighted child.
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
