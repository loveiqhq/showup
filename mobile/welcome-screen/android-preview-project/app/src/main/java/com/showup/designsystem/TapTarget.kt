package com.showup.designsystem

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Gives a control at least [ComponentSizes.minTapTarget] in both directions.
 *
 * WHY A MODIFIER AND NOT A COMPONENT
 *
 * The two back controls in this app are not the same control and were deliberately not merged into
 * one. The phone screens draw a `chevron-left` icon at the top of the screen; the tutorial draws the
 * WORD "Back" in the bottom nav row beside `NextButton`, at a different colour, with a rounded clip
 * and a `showBack` flag that hides it and clears it from the semantics tree on card 01. They share
 * an `onBack` callback and nothing that is drawn.
 *
 * A `BackControl` covering both would have been a component whose two variants share only a
 * lambda -- a flag choosing between two unrelated renderings. What they genuinely share is the
 * RULE: a control has to be big enough to hit. So the rule is what got extracted.
 *
 * THE FLOOR CANNOT BE LOWERED, ONLY RAISED
 *
 * [min] is clamped up to [ComponentSizes.minTapTarget]. A caller asking for 48 gets 48; a caller
 * asking for 20 gets 44. That is the difference between a default and a guarantee: a plain default
 * parameter would let somebody pass a smaller number and quietly reintroduce the defect this whole
 * area exists to prevent -- the 23dp field inside a 56dp row that looked right and only responded
 * in its middle third.
 *
 * `defaultMinSize`, not `size`: this sets a floor and lets the content be larger, which is what the
 * tutorial's label needs. It does not centre or align anything -- the call site owns that, because
 * one of these controls is at the top of a screen and the other is in a row at the bottom.
 *
 * ## 44 or 48 on Android
 *
 * The default is [ComponentSizes.minTapTarget], which is **44 on both platforms** today. Android's
 * own convention is 48 (Material) against Apple's 44 (HIG), and both `CLAUDE.md` files already state
 * the rule that way -- "44pt / 48dp minimum for anything tappable".
 *
 * The token does not implement that split, and neither does the fit harness, which flags below 44 on
 * Android too. The call sites are correspondingly mixed: the tutorial's back control passes 48, the
 * phone screens' takes the 44 default, and both were hardcoded numbers before this file existed.
 *
 * That mismatch is real and is NOT resolved here, because resolving it means changing a rendered hit
 * area. It is written up in `docs/design-system.md` with a recommendation. What this modifier does is
 * make the number visible and shared instead of typed in four places.
 */
fun Modifier.minTapTarget(min: Dp = ComponentSizes.minTapTarget): Modifier {
    val floor = if (min > ComponentSizes.minTapTarget) min else ComponentSizes.minTapTarget
    return defaultMinSize(minWidth = floor, minHeight = floor)
}
