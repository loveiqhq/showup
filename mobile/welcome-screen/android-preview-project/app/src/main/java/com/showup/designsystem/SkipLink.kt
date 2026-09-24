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
 * IT IS NOT A [PrimaryButton] VARIANT. That is a 48-or-56-tall filled pill; this is underlined text
 * with no box at all. A variant that dropped the height, the fill, the border, the shadow and the
 * press scale would be a different component wearing the same name.
 */
package com.showup.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SkipLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Skip for now",
) {
    Box(
        modifier
            // The text is about 17dp tall and the rule is 48. Padding rather than `requiredSize`
            // here: this sits at the START of a footer row with a large circular button at the
            // other end, so there is nothing for an overflowing hit area to collide with, and
            // padding keeps the touch area and the layout honest about each other.
            .defaultMinSize(minHeight = ComponentSizes.minTapTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = Spacing.xl, bottom = Spacing.xl, end = Spacing.xl),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = Purple,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            textDecoration = TextDecoration.Underline,
        )
    }
}
