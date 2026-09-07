package com.showup.designsystem

import androidx.compose.ui.unit.dp

/**
 * Icon and badge dimensions that recur across screens.
 *
 * Only two, because most fixed sizes in this codebase are illustration geometry rather than icons:
 * 84, 104 and 120 are all Connect-screen art, and 120's six uses are six references to the same
 * spinner canvas on one screen. Tokenising those would move a drawing's coordinates into the design
 * system and make them look reusable.
 *
 * [badge] is 56, the same number as ComponentSizes.controlHeight, and they are separate tokens on
 * purpose: one is how tall a control is, the other is how wide a circular badge is. They agree
 * today by coincidence, not by rule.
 */
object IconSizes {
    /** Provider marks inside method buttons, and the small status glyphs. */
    val sm = 20.dp

    /** The round status/eyebrow badge on the tutorial and Connect screens. */
    val badge = 56.dp
}
