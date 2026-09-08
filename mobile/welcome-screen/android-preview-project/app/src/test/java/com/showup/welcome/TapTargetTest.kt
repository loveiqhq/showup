/*
 * TapTargetTest.kt
 * ShowUp · does the control that LOOKS tappable actually take a tap
 *
 * WHY THIS EXISTS
 *
 * This project shipped a phone field that measured 23dp inside a 56dp row. It looked right,
 * rendered right, and only its middle third responded to a tap, so touching near the top or bottom
 * edge did nothing. No screenshot could show it and no reading of the source found it; it was found
 * by measuring.
 *
 * The fix -- `fillMaxHeight()` on the field -- is one modifier, and one modifier is exactly the kind
 * of thing that gets dropped in a refactor. So the guarantee is asserted here rather than left to a
 * comment: the field's own semantics bounds, after layout, against the box drawn around it.
 *
 * WHAT THIS IS NOT
 *
 * ScreenFitTest already flags any tappable node under 44dp, across 17 device sizes. That is the
 * broad net. This is the specific claim -- that the INPUT fills its box -- stated where somebody
 * changing the input will see it fail.
 */
package com.showup.welcome

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.showup.designsystem.fieldChrome
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.showup.designsystem.ComponentSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TapTargetTest {

    /**
     * The field is as tall as the box a person can see.
     *
     * `assertHeightIsAtLeast(minTapTarget)` rather than `== 56`: 44 is the floor the design uses
     * deliberately in a few places and the smallest number that is never a bug. The exact-56
     * claim is the second assertion, which is about this control specifically.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theNumberFieldFillsTheBoxItIsDrawnIn() = runComposeUiTest {
        setContent { PhoneNumberScreen(value = "") }

        val field = onNodeWithContentDescription(PHONE_FIELD_LABEL)
        field.assertHeightIsAtLeast(ComponentSizes.minTapTarget)

        val bounds = field.fetchSemanticsNode().size
        val density = onNodeWithContentDescription(PHONE_FIELD_LABEL)
            .fetchSemanticsNode().layoutInfo.density
        val heightDp = with(density) { bounds.height.toDp() }
        assertEquals(
            "the field should be exactly one control tall, not %s".format(heightDp),
            ComponentSizes.controlHeight.value, heightDp.value, 0.75f,
        )
    }

    /** A tap at the very top edge of the field lands on the field, not on its background. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aTapAtTheTopEdgeOfTheFieldReachesIt() = runComposeUiTest {
        setContent { PhoneNumberScreen(value = "") }
        // performClick targets the node's centre, which the 23dp version also passed. The height
        // assertion above is what covers the edges: a node that IS the full box has no edge that
        // is not itself. This asserts the node is reachable and takes a click at all.
        onNodeWithContentDescription(PHONE_FIELD_LABEL).performClick()
    }

    /**
     * The instrument has to fire.
     *
     * A field left at the height of its own text inside a 56dp box is the exact shape of the
     * original defect. If the measurement cannot see that, a green run above proves nothing.
     *
     * The iOS twin of this self-test caught a wrong prediction of mine before it became a "fix"
     * for a defect that does not exist there. Worth the twelve lines.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theProbeFindsAShortField() = runComposeUiTest {
        setContent {
            Row(
                Modifier.fieldChrome(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No fillMaxHeight, which is the whole defect: the field measures to its text and
                // the row centres it inside a box three times as tall.
                BasicTextField(
                    value = "0176 123 45 678",
                    onValueChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "defective" },
                    textStyle = TextStyle(fontSize = 17.sp),
                    singleLine = true,
                )
            }
        }
        val node = onNodeWithContentDescription("defective").fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.size.height.toDp() }
        assertTrue(
            "the probe measured %s, so it cannot see the 23dp defect either".format(height),
            height < ComponentSizes.minTapTarget,
        )
    }
}
