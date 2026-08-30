/*
 * HarnessSmokeTest.kt
 * ShowUp · proves the layout harness measures TEXT before anything is asserted with it
 *
 * A measuring instrument that has not been checked against a known quantity measures nothing.
 *
 * The specific danger: Robolectric's default graphics mode stubs out font metrics, so every string
 * measures to roughly nothing. Under that mode a "does it fit" suite cannot fail -- text takes no
 * room, so nothing ever overflows, and a page of green ticks would mean nothing at all.
 *
 * Two calibrations, because the obvious one is not enough. Measuring a button tells you the
 * button's width, which is set by its parent and would look correct even with no font at all. Only
 * the text node itself, and only text that has to WRAP, depends on real glyph advances.
 */
package com.showup.fit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SENTENCE =
    "The quick brown fox jumps over the lazy dog and keeps running until it finds a wall."

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class HarnessSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `glyph advances are real, so text wraps when the box narrows`() {
        // The same sentence in two boxes of different widths. With real metrics the narrow one is
        // taller, because it takes more lines. With stubbed metrics both collapse to the same
        // nothing, and the assertion below fails -- which is exactly what it is for.
        compose.setContent {
            Box(Modifier.width(300.dp)) { Text(SENTENCE, fontSize = 14.sp) }
            Box(Modifier.width(120.dp)) { Text(SENTENCE + " ", fontSize = 14.sp) }
        }

        val wide = compose.onNodeWithText(SENTENCE, substring = false).getUnclippedBoundsInRoot()
        val narrow = compose.onNodeWithText(SENTENCE + " ").getUnclippedBoundsInRoot()
        val wideH = (wide.bottom - wide.top).value
        val narrowH = (narrow.bottom - narrow.top).value
        val wideW = (wide.right - wide.left).value

        println("=== CALIBRATION ===")
        println("in a 300dp box: ${wideW} x ${wideH} dp")
        println("in a 120dp box: ${(narrow.right - narrow.left).value} x ${narrowH} dp")

        assertTrue("text in a 300dp box measured ${wideH}dp tall -- metrics look stubbed",
            wideH > 14f)
        assertTrue("narrowing the box did not add lines ($wideH -> $narrowH) -- text is not " +
            "really being measured, so no fit assertion built on this harness would mean anything",
            narrowH > wideH * 1.5f)
    }
}
