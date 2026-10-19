/*
 * ProfileVisibility.kt
 * ShowUp · "Don't display on my profile" -- the ONE profile-visibility control
 *
 * `ProfileVisibility` in `components/shared.jsx`: "Used on every profile-creation step that asks
 * for an attribute we may or may not publish ... one control with one wording appears on every
 * step." It lived in the date-of-birth screen's file as `ProfileVisibilityRow` while that was its
 * only caller. The seven "Share some details" steps (SHOWUP-167 to SHOWUP-173) are the second
 * group to use it, and a shared primitive never lives in a screen file -- the tutorial's own copy
 * of the primary button is what that rule cost the first time.
 *
 * WHAT IT IS NOT
 *
 * Not the marketing opt-in on the email step, and the five differences are why: this sits under a
 * hairline divider rather than on a raised card, the box is on the LEFT, the hit area is capped to
 * the box and its label instead of the full row, it carries an eye-off mark, and its label is
 * 700 / 14 against that row's 500 / 13.
 *
 * CHECKED MEANS NOT DISPLAYED -- STILL USED FOR MATCHING. It hides a value; it never excludes the
 * user, and it never touches `isVisible`.
 */
package com.showup.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The one wording, on every step that carries the band. */
const val PROFILE_VISIBILITY_LABEL = "Don't display on my profile"

@Composable
fun ProfileVisibility(
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = PROFILE_VISIBILITY_LABEL,
) {
    // `transition: background 180ms, border-color 180ms` on the box. A swap with no transition
    // reads as a redraw rather than a box being ticked.
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val fill by animateColorAsState(if (hidden) Purple else Elevated, tween(ms, easing = CssEase), label = "visFill")
    val stroke by animateColorAsState(if (hidden) Purple else Border, tween(ms, easing = CssEase), label = "visStroke")
    val mark by animateColorAsState(if (hidden) Purple else Subtle, tween(ms, easing = CssEase), label = "visMark")

    Column(modifier.fillMaxWidth()) {
        // `borderTop: 1px solid var(--liq-border-soft)` -- the hairline that makes this a band.
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
        Row(
            Modifier
                // CAPPED TO THE BOX AND ITS LABEL, NOT THE FULL ROW. An edge-to-edge target above
                // a bottom-right Continue invites a thumb reaching for one to flip the other --
                // a privacy setting changed by accident. `inline-flex` in the reference.
                //
                // `toggleable` with the Checkbox role, so TalkBack announces "checked" / "not
                // checked" in the platform's own words -- the reference's `role="checkbox"
                // aria-checked`.
                .toggleable(
                    value = hidden,
                    interactionSource = remember { MutableInteractionSource() },
                    // No ripple: the box's own 180 ms fill is the feedback, as the reference draws.
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .semantics { contentDescription = label }
                // minHeight 56, padding 10 / 0.
                .heightIn(min = ComponentSizes.controlHeight)
                .padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fill)
                    .border(1.5.dp, stroke, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (hidden) CheckGlyph(size = 13.dp, color = Elevated, stroke = 3f)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.EyeOff, size = 14.dp, tint = mark, strokeWidth = 2.1f)
                Text(
                    label,
                    color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, lineHeight = 18.2.sp,
                    // `whiteSpace: nowrap` in the reference, and at the default size it is one line
                    // on every phone this ships to -- about 252 of the 272 the Galaxy Fold cover
                    // leaves. NOT FORCED, though: at 1.3x type on that cover the forced line was
                    // truncated mid-word (ScreenFitTest), and a privacy control whose label is cut
                    // off is one nobody can be sure of. Wrapping is the honest failure.
                )
            }
        }
    }
}
