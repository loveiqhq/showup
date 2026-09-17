package com.showup.designsystem

import androidx.compose.ui.unit.dp

/**
 * Standard control dimensions.
 *
 * WHAT IS DELIBERATELY ABSENT
 *
 * The reserved helper regions on the phone screens are NOT here, and that was a considered
 * decision on 7 September 2026 rather than an omission.
 *
 * They look like constants -- the spec sheet names 20 for states A/B and 42 for C/D -- and they are
 * not. The A/B region is 36 in code, because an error message takes two lines; 20 was the earlier
 * value and it was a bug. The C/D region is 42 because of a RULE, `reserve = error box + 2`, where
 * the box height follows from the copy: our one-line string gives 41 + 2, the reference's two-line
 * string gave 60 + 2. PhoneVerificationScreen says so at the call site, and says the two move
 * together if the copy changes.
 *
 * A token called `helperRegionCD = 42` would freeze a number that is documented as moving, and
 * would state the spec value rather than the implemented one. The invariant that actually matters
 * -- the CTA does not move between states -- is asserted by ScreenFitTest at 17 device sizes, which
 * is a stronger guarantee than a named number.
 */
object ComponentSizes {
    /** The sunset CTA and the phone input. SHOWUP-140 fixes the CTA at 56. */
    val controlHeight = 56.dp

    /**
     * The floor for anything tappable, even where the reference draws smaller.
     *
     * `heightIn(min = 44.dp)` on the log-in link and the back control. Asserted by ScreenFitTest,
     * which found a 23dp target inside a 56dp row on 15 of 18 sizes.
     */
    /**
     * The design system's `md` button -- 48 tall, 15sp label (`shared.jsx`, the `sizes` map).
     *
     * A SECOND STANDARD HEIGHT, not a one-off. `shared.jsx` defines three: sm 36, md 48, lg 56.
     * [controlHeight] is `lg` and is what the flow's full-width CTAs use; `md` is what a button
     * INSIDE a card uses, and the media step's `See the prompts` is the first of those to be
     * built. The 8dp matters there: SHOWUP-161 requires the whole empty state -- pill, headline,
     * both cards, both CTAs -- to fit above the fold at 390x844, and two cards' CTAs at `lg`
     * spend 16 of that budget on nothing.
     *
     * Named rather than written as 48 at the call site, because the rule is that a standard
     * control height has a name; this one now has both its name and the sentence that says which
     * of the design's three it is.
     */
    val controlHeightMedium = 48.dp

    val minTapTarget = 44.dp
}
