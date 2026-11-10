/*
 * CheckRow.kt
 * ShowUp · the multi-select answer row (SHOWUP-170)
 *
 * Built by Profile 17 (dating language) and "its own component, not a variant of OptionRow" --
 * decided 5 October 2026. It differs in two numbers and one mark: padding 12 / 4 against
 * OptionRow's 16 / 4, and an 11 white tick in the round indicator where the radio has a dot.
 * Everything else is shared on purpose -- the wash, the divider, the radius, the label and the
 * 180 ms transitions -- "so the two lists read as a pair", which is why those live in
 * [answerRowChrome], [AnswerRowLabel] and [RoundIndicator] rather than in either row.
 */
package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One multi-select answer. A tap toggles it; other rows are unaffected.
 *
 * @param index 0-based position and [count] the list length, for "German, checkbox, not checked,
 *   1 of 8" (Profile 17, accessibility).
 */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    last: Boolean = index == count - 1,
) {
    val motion = rememberMotion()
    val wash by animateColorAsState(
        if (checked) SelectedRowWash else Color.Transparent,
        tween(if (motion.enabled) Motion.FAST else 0, easing = CssEase),
        label = "checkWash",
    )
    Row(
        modifier
            .fillMaxWidth()
            // The whole row toggles, with the Checkbox role so the state is announced in the
            // platform's words. No ripple, for the reason OptionRow gives.
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
            .answerRowChrome(wash = wash, divider = !last)
            // The 44 floor, as on iOS (settled 9 Nov 2026, see TapTarget.kt). 12 / 4 makes a row
            // 12 + 20.8 + 12 = 44.8 at the default font, already over it, so the row is the
            // reference's own height; the floor holds only if a smaller font ever shrank it. It
            // was 48 until then, 3.2 taller than the reference.
            .minTapTarget()
            .padding(vertical = Spacing.xl, horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnswerRowLabel(label, chosen = checked, modifier = Modifier.weight(1f))
        RoundIndicator(chosen = checked) {
            CheckGlyph(size = 11.dp, color = Elevated, stroke = 3.5f)
        }
    }
}
