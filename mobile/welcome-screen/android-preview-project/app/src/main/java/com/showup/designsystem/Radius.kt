package com.showup.designsystem

import androidx.compose.ui.unit.dp

/**
 * Corner radii, all four with a specific role.
 *
 * [pill] is 28 and that is not a coincidence: it is exactly half of [ComponentSizes.controlHeight],
 * which is what makes a 56-tall button a pill rather than a rounded rectangle. If the control
 * height ever changes, this has to change with it.
 *
 * Not here: 2, used for the drawn flag rectangles in CountryPicker, which is decorative geometry
 * rather than a component corner; and 18, which appears once, on iOS only, in TutorialShell -- an
 * unexplained divergence from Android recorded rather than tokenised.
 */
object Radius {
    /** Text inputs, the six code slots, and the provider method buttons. */
    val control = 14.dp

    /** The sunset CTA. Half of ComponentSizes.controlHeight, by definition. */
    val pill = 28.dp

    /** The inline error box glued to the code slots. */
    val errorBox = 12.dp

    /** The method-list container. */
    val card = 16.dp
}
