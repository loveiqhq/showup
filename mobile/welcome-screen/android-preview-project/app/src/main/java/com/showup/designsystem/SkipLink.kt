/*
 * SkipLink.kt
 * ShowUp · the underlined "skip this step" control (SHOWUP-161)
 *
 * From `components/shared.jsx`'s `SkipLink`: Manrope 700 / 13.5, `--liq-primary-500`, underlined
 * with a 1.5 thickness at a 3 offset. Its canonical label is `Skip for now`, which is the default
 * here for the same reason it is the default there -- a skip control that says something different
 * on each screen is four skip controls.
 *
 * SHOWUP-161 is its first user, so this is a new primitive rather than an extraction. It lives in
 * the design system rather than in the media screen because the ticket calls it "the shared
 * SkipLink" and because the next optional step will want it -- and the rule this project already
 * paid for is that the second caller never finds a component hidden in another screen's file.
 *
 * THE RULE IS DRAWN, NOT `TextDecoration.Underline`. That draws the font's own underline -- about
 * 0.34 thick, 0.7 under the baseline -- a hairline hugging the text, where the reference's is 1.5
 * thick and starts 3 below the baseline (`text-underline-offset` is measured from the alphabetic
 * baseline). Found 13 October 2026; until then this header described a rule the code did not draw.
 * One rule per laid-out line, so a label the largest font wraps is underlined on both lines.
 *
 * PRESS: a 0.98 scale on the brand curve, as `PressScale` on iOS and [PrimaryButton] here -- and no
 * overlay. `clickable` with no `indication` drew Compose's default, a grey rectangle over the whole
 * padded box, which neither the reference nor the iOS twin has.
 *
 * IT IS NOT A [PrimaryButton] VARIANT. That is a 48-or-56-tall filled pill; this is underlined text
 * with no box at all. A variant that dropped the height, the fill, the border, the shadow and the
 * press scale would be a different component wearing the same name.
 */
package com.showup.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** `textDecorationThickness: 1.5`. */
private val RULE_THICKNESS = 1.5.dp

/** `textUnderlineOffset: 3` -- from the baseline to the rule's top edge. */
private val RULE_OFFSET = 3.dp

@Composable
fun SkipLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Skip for now",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion.enabled) 0.98f else 1f,
        animationSpec = tween(if (motion.enabled) Motion.FAST else 0, easing = ShowUpEasing),
        label = "skipPress",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(
        modifier
            // The text is about 17dp tall and the rule is 48. Padding rather than `requiredSize`
            // here: this sits at the START of a footer row with a large circular button at the
            // other end, so there is nothing for an overflowing hit area to collide with, and
            // padding keeps the touch area and the layout honest about each other.
            .defaultMinSize(minHeight = ComponentSizes.minTapTarget)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(top = Spacing.xl, bottom = Spacing.xl, end = Spacing.xl),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    val text = layout ?: return@drawBehind
                    val thickness = RULE_THICKNESS.toPx()
                    for (line in 0 until text.lineCount) {
                        val y = text.getLineBaseline(line) + RULE_OFFSET.toPx() + thickness / 2
                        drawLine(
                            color = Purple,
                            start = Offset(text.getLineLeft(line), y),
                            end = Offset(text.getLineRight(line), y),
                            strokeWidth = thickness,
                        )
                    }
                },
            color = Purple,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            onTextLayout = { layout = it },
        )
    }
}
