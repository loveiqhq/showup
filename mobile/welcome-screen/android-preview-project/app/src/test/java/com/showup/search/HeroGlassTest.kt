/*
 * HeroGlassTest.kt
 * ShowUp · the hero pills' glass: the photo behind a pill, lined up and blurred (SHOWUP-184 ⑤ ⑦)
 *
 * The photo stand-in is black on its left half and white on its right, and a pill straddles the edge
 * at x = 150. The glass is drawn alone, with no photo under the pill, so every pixel inside the pill is
 * the glass's own: it must be the photo's black up to x = 150 and its white from there -- the photo
 * drawn again at its own place, which is what a backdrop filter blurs. A glass drawn from the photo's
 * corner would be all black here, and one that drew nothing would be transparent.
 *
 * WHAT THIS CANNOT SEE: the blur itself. Robolectric draws into a software Canvas, which ignores
 * RenderEffect -- a plain `Modifier.blur` comes out sharp here too (measured 25 Oct 2026) -- so the
 * blur is Android's to apply and is checked on a device. This test holds the part that is ours.
 *
 * Drawn into a bitmap the way `IconStrokeTest` and the evidence harness draw: `captureToImage()`
 * needs a window, and Robolectric has none.
 */
package com.showup.search

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HeroGlassTest {

    private fun grey(bitmap: Bitmap, x: Int, y: Int): Int {
        val c = bitmap.getPixel(x, y)
        return ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
    }

    /** A 300 x 200 photo, black then white, with a 120 x 40 pill at (90, 80) over its edge at x = 150. */
    private fun render(photoUnder: Boolean = true): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity)
        activity.setContentView(view)
        val backdrop = HeroBackdrop { m ->
            Box(
                m.drawBehind {
                    drawRect(Color.Black, size = Size(size.width / 2, size.height))
                    drawRect(Color.White, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                },
            )
        }
        view.setContent {
            // One dp is one pixel, so the numbers below are pixels.
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
                Box(Modifier.size(300.dp, 200.dp)) {
                    Box(Modifier.fillMaxSize().onGloballyPositioned { backdrop.photo = it }) {
                        if (photoUnder) backdrop.content(Modifier.fillMaxSize())
                    }
                    Box(Modifier.offset(90.dp, 80.dp).size(120.dp, 40.dp).clip(RoundedCornerShape(percent = 50))) {
                        HeroGlass(backdrop, 19.92.dp, Modifier.fillMaxSize())
                    }
                }
            }
        }
        // Twice: the glass learns where it sits from the first layout and draws in the second.
        repeat(2) {
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 300, 200)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `the glass draws the photo behind the pill at the photo's own place`() {
        val image = render(photoUnder = false)
        val y = 100
        val inside = (100..200 step 10).map { x -> x to image.getPixel(x, y) }
        for ((x, c) in inside) {
            assertTrue("the glass covers the pill at x $x (alpha ${c ushr 24})", c ushr 24 == 255)
            val g = grey(image, x, y)
            if (x < 150) assertTrue("left of the photo's edge the glass is the photo's black (x $x: $g)", g < 10)
            else assertTrue("from the photo's edge the glass is the photo's white (x $x: $g)", g > 245)
        }
        assertTrue("outside the pill nothing is drawn", image.getPixel(60, y) ushr 24 == 0 && image.getPixel(240, y) ushr 24 == 0)
    }

    @Test
    fun `with the photo under it the pill shows the same photo, so nothing shifts`() {
        val image = render(photoUnder = true)
        for (x in listOf(60, 120, 145, 150, 155, 180, 240)) {
            val g = grey(image, x, 100)
            if (x < 150) assertTrue("x $x is the photo's black ($g)", g < 10) else assertTrue("x $x is the photo's white ($g)", g > 245)
        }
    }
}
