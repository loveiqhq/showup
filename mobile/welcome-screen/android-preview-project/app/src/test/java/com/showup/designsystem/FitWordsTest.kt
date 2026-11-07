/*
 * FitWordsTest.kt
 * ShowUp · a word shrunk to fit its column is never cut by the height it was shrunk into
 *
 * `fitWords` lays a long word's text out wider and draws it scaled down. A parent that fixes the
 * height -- a grid row whose two tiles are one height, `IntrinsicSize.Min` and `fillMaxHeight` --
 * hands it the height of the SCALED drawing; the text laid out at full size is taller than that, and
 * was cut to it. The chat's start tiles lost up to 70 px of their labels that way at 150 % (the
 * SHOWUP-194 fit sweep in CI, 7 Nov 2026). The check is the fit harness's own: the paragraph is no
 * taller than the text was laid out in.
 */
package com.showup.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class FitWordsTest {

    @Test
    fun `a shrunk word's text is not cut by the fixed height of its row`() {
        val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = (15 * 1.16).sp)
        val text = "When will you arrive?"
        var laid: TextLayoutResult? = null
        runComposeUiTest {
            setContent {
                // 44 wide: `arrive?` at 15 / 700 is wider, so it is drawn shrunk, as in a tile at 320.
                Row(Modifier.width(44.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Text(text, Modifier.fitWordsOf(text, style), style = style, onTextLayout = { laid = it })
                    }
                }
            }
            waitForIdle()
        }
        val layout = checkNotNull(laid)
        val over = layout.multiParagraph.height - layout.size.height
        assertTrue("the text was cut by %.0f px".format(over), over <= 1f)
    }
}
