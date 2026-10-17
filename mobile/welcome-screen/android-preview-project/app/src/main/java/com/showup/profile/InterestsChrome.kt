/*
 * InterestsChrome.kt
 * ShowUp · what Interests builds (Profile 21, SHOWUP-174): InterestChip, InterestsCounter,
 * ShowMoreToggle, InterestGroup and CategoryTitle
 *
 * Numbers from `screen-interests-reference.jsx`, which "wins on numbers". ONE CHIP FOR BOTH BLOCKS:
 * the chip draws picked, resting or faded and reports a tap. Whether a tap clears, moves or is
 * refused at the cap is screen logic (`ProfileInterests.kt`), never a chip prop.
 *
 * THE CHIP DRAWS 38 AND ANSWERS AT 44. The reference's chip is 38 tall in rows 8 apart, and the
 * project's floor for anything tappable is 44. Growing the chip would open every row by 6 and move
 * every chip below the first row; instead the layout slot stays 38 and the touch area overflows it
 * symmetrically by 3 (`OverflowTapTarget`). With rows 8 apart, 3 + 3 never reaches the next row's
 * area, so no tap is ever ambiguous. The toggle is drawn about 26 tall and answers at 44 the same way.
 */
package com.showup.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.ChipGlow
import com.showup.designsystem.ChipRest
import com.showup.designsystem.CssEase
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.MeterTrack
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.Purple
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Spacing
import com.showup.designsystem.pillShadow
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The chip's drawn height -- `height: 38`. A floor, so a larger system font grows it. */
private val CHIP_HEIGHT = 38.dp

/** `padding: 0 15px` inside a CSS border-box, so the border is inside this width too. */
private val CHIP_PAD = 15.dp

/** The picked border -- the wider of the two, reserved in both states (see [InterestChip]). */
private val CHIP_BORDER_PICKED = 1.5.dp

/** Fit-harness anchor for one chip, by its stored value. */
internal fun interestChipTag(value: String) = "interest-chip-$value"

/** Fit-harness anchor for a group's toggle, by its §3 section. */
internal fun showMoreTag(sectionId: String) = "show-more-$sectionId"

/** How a chip answers to TalkBack: a radio in a How I live group, a toggle in What I'm into. */
enum class ChipRole { Radio, Toggle }

/**
 * One chip.
 *
 * Picked: violet fill, 1.5 violet border, white 700, `0 4 10` violet glow. Resting: white, 1px
 * `--liq-border`, 600, a `0 1 2` hairline lift. Faded (at the cap, not picked): opacity .4 --
 * NOT animated, because the reference's transition list does not name opacity.
 *
 * `transition: background 180ms, color 180ms, border-color 180ms, transform 180ms
 * cubic-bezier(.22,1,.36,1), box-shadow 180ms` -- so the colours and the glow run on CSS's `ease`
 * and only the press scale runs on the brand curve.
 *
 * ALWAYS AS WIDE AS IT IS PICKED. "No element changes Y on selection" (ticket 21), and the picked
 * weight and border make a chip up to 5 wider, which re-wrapped a row on 14 of 17 phones (measured
 * 13 October 2026, `PickFitTest`). So the chip lays out an invisible 700 copy of its label under the
 * visible one and reserves the picked border's width: the drawn chip is the picked size in both
 * states, and a pick moves nothing. Still sized by its own label -- not a fixed box.
 *
 * @param faded drawn at .4 and spoken as dimmed, but STILL TAPPABLE: a tap on a faded chip is
 *   reported (`interest_cap_reached`) and changes nothing. The caller decides that, not the chip.
 */
@Composable
fun InterestChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: ChipRole,
    modifier: Modifier = Modifier,
    faded: Boolean = false,
) {
    val motion = rememberMotion()
    val ms = if (motion.enabled) Motion.FAST else 0
    val ease = tween<androidx.compose.ui.graphics.Color>(ms, easing = CssEase)
    val fill by animateColorAsState(if (selected) Purple else Elevated, ease, label = "chipFill")
    val ink by animateColorAsState(if (selected) Elevated else Fg, ease, label = "chipInk")
    val stroke by animateColorAsState(if (selected) Purple else Border, ease, label = "chipStroke")
    val glow by animateColorAsState(if (selected) ChipGlow else ChipRest, ease, label = "chipGlow")
    val glowY by animateDpAsState(if (selected) 4.dp else 1.dp, tween(ms, easing = CssEase), label = "chipGlowY")
    val glowBlur by animateDpAsState(if (selected) 10.dp else 2.dp, tween(ms, easing = CssEase), label = "chipBlur")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // `scale(0.97)` on press, never on a faded chip ("if (!disabled)"), and not at all when motion
    // is reduced -- a scale that only stops animating still jumps, which NextButton and the iOS
    // twin both avoid.
    val scale by animateFloatAsState(
        if (pressed && !faded && motion.enabled) 0.97f else 1f,
        tween(ms, easing = ShowUpEasing),
        label = "chipPress",
    )
    // The border is inside the box, as CSS draws it: 1.5 picked, 1 resting. Not animated -- the
    // reference transitions the colour, not the width. The padding reserves the picked one.
    val borderWidth = if (selected) CHIP_BORDER_PICKED else 1.dp
    val shape = RoundedCornerShape(percent = 50)

    // THE SLOT IS THE DRAWING; the touch area is laid over it and overflows. See the file header.
    OverflowTapTarget(
        modifier = modifier,
        touch = Modifier
            .then(
                when (role) {
                    // A How I live chip is a radio, and activating the checked one clears it.
                    ChipRole.Radio -> Modifier.selectable(
                        selected = selected,
                        interactionSource = interaction,
                        // NO RIPPLE: the fill change is the feedback, and a Material ripple over it
                        // would be a second, undesigned one.
                        indication = null,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                    // A What I'm into chip is a toggle (`aria-pressed`): read as checked or not.
                    ChipRole.Toggle -> Modifier.toggleable(
                        value = selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                },
            )
            .semantics {
                contentDescription = label
                // `aria-disabled`, but STILL TAPPABLE: the tap is reported (`interest_cap_reached`)
                // and changes nothing. SPOKEN, not `disabled()`: TalkBack will not activate a disabled
                // node, so the cap event could never fire for its users while VoiceOver's did -- the
                // web's `aria-disabled` announces without blocking. The iOS twin's words.
                if (faded) stateDescription = "Not checked, dimmed"
            },
    ) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .heightIn(min = CHIP_HEIGHT)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (faded) 0.4f else 1f
                }
                .pillShadow(fill = fill, shadow = glow, offsetY = glowY, blur = glowBlur)
                .border(borderWidth, stroke, shape)
                // `xs` above and below keeps a label that grew past 38 (a second line, or the largest
                // font) off the stroke. Inside the 38 until about 1.6x type, so it changes nothing
                // at the default size or the common larger ones; `sm` grew the chip from 1.36x.
                .padding(horizontal = CHIP_PAD + CHIP_BORDER_PICKED, vertical = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            // `white-space: nowrap` in practice: every label is one line on every phone at the default
            // font, and the row wraps around whole chips. A SECOND LINE IS ALLOWED only for a label
            // wider than the whole column -- the largest system font on a 320 phone -- because the
            // alternative there is clipped text; centred, and ellipsised past two. Two, not one, for
            // `ControlPip`'s reason: FlowRow measures twice, and `maxLines = 1` latches
            // `didExceedMaxLines` on the first, tighter pass.
            //
            // The picked width, reserved in both states: drawn in no colour, read by nobody (the Box
            // clears its semantics).
            Text(
                label,
                color = Color.Transparent,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                color = ink,
                fontFamily = Manrope,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `{N} picked · {M} left`, and `10 of 10 picked` at the cap, beside a 4-tall meter.
 *
 * At the cap the label and the bar turn orange (`color 180ms`); the bar's width runs 280 on the
 * brand curve. A POLITE LIVE REGION: TalkBack reads the new count after each tap, without
 * interrupting the chip it is on.
 */
@Composable
fun InterestsCounter(picked: Int, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    val full = picked >= INTERESTS_MAX
    val tone by animateColorAsState(
        if (full) Orange else Purple,
        tween(if (motion.enabled) Motion.FAST else 0, easing = CssEase),
        label = "counterTone",
    )
    val fill by animateFloatAsState(
        interestsFill(picked),
        // The step bar's own duration and the same act -- a fill growing -- on the brand curve.
        tween(if (motion.enabled) Motion.PROGRESS else 0, easing = ShowUpEasing),
        label = "counterFill",
    )
    val label = interestsCounterLabel(picked)
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = label
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = tone,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MeterTrack),
        ) {
            // A plain bar the track clips (`overflow: hidden`): its leading edge is square, as drawn.
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(4.dp)
                    .background(tone),
            )
        }
    }
}

/**
 * `Show more` / `Show less` under a collapsible group: a 16 chevron (stroke 2.2) that turns over in
 * 220, beside a Manrope 700 13.5 violet label.
 *
 * DRAWN `padding 4 / 2` (about 26 tall), ANSWERS AT 44, overflowing its slot by 9 above and below
 * like the chip. The 10 above it and the group's 16 below leave room without reaching a chip.
 */
@Composable
fun ShowMoreToggle(expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    val turn by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(if (motion.enabled) Motion.DISCLOSURE else 0, easing = ShowUpEasing),
        label = "showMoreTurn",
    )
    val label = if (expanded) PickCopy.SHOW_LESS else PickCopy.SHOW_MORE
    OverflowTapTarget(
        modifier = modifier,
        touch = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // `aria-expanded`: TalkBack reads the state and offers the matching action.
            .semantics {
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                if (expanded) collapse { onClick(); true } else expand { onClick(); true }
            },
    ) {
        Row(
            Modifier
                .clearAndSetSemantics {}
                .padding(vertical = Spacing.xs, horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.rotate(turn)) {
                Icon(BrandIcon.ChevronDown, 16.dp, tint = Purple, strokeWidth = 2.2f)
            }
            Text(
                label,
                color = Purple,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            )
        }
    }
}

/** A block title -- `How I live`, `What I'm into`. Manrope 700 16, margin-bottom 12. */
@Composable
fun CategoryTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.semantics { heading() },
        color = Fg,
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}

/**
 * One group: a small label, its chips wrapping 8 apart, and -- on a collapsible What I'm into group
 * -- the toggle. The label is the group's accessible name; a How I live group is a radio group.
 *
 * @param chips the chips to draw, already cut to four for a collapsed group.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestGroup(
    label: String,
    chips: List<DetailOption>,
    isSelected: (String) -> Boolean,
    onPick: (String) -> Unit,
    role: ChipRole,
    modifier: Modifier = Modifier,
    isFaded: (String) -> Boolean = { false },
    toggle: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        // Read ONCE, as the group's name below, not again as a loose line of text above it.
        Text(
            label,
            modifier = Modifier.clearAndSetSemantics {},
            color = Muted,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(Spacing.md))
        FlowRow(
            Modifier
                .fillMaxWidth()
                .then(if (role == ChipRole.Radio) Modifier.selectableGroup() else Modifier)
                .semantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            chips.forEach { option ->
                InterestChip(
                    label = option.label,
                    selected = isSelected(option.value),
                    onClick = { onPick(option.value) },
                    role = role,
                    faded = isFaded(option.value),
                    modifier = Modifier.testTag(interestChipTag(option.value)),
                )
            }
        }
        if (toggle != null) {
            Spacer(Modifier.height(Spacing.lg))
            toggle()
        }
    }
}
