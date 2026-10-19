/*
 * IconStrokeTest.kt
 * ShowUp · how thick a drawn icon's line actually is, in pixels
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE BUG THIS EXISTS FOR, AND WHY NOTHING ELSE COULD SEE IT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * `Icon` authors every path on a 24 grid and scales it with `withTransform { scale(s, s) }`. The
 * stroke width was converted to pixels OUTSIDE that transform and drawn INSIDE it, so it was
 * multiplied by the scale a second time:
 *
 *     rendered = strokeWidth x size x density^2 / 24      (wrong)
 *     correct  = strokeWidth x size x density   / 24
 *
 * The error factor is exactly the DENSITY. At 1.0 it is invisible, which is why every preview
 * looked right; on a 2.625x phone every icon in the app was 2.6x too heavy, and the arrow on the
 * embrace CTA was fat enough that the two strokes of its head merged into a solid triangle.
 *
 * `ScreenFitTest` could never have caught it: that harness measures the SPACE a control occupies,
 * and the control's bounds were always correct. This is a bug in the ink inside the box, and the
 * only way to see it is to count pixels.
 *
 * So this renders one icon and measures its shaft. Robolectric with `GraphicsMode.NATIVE` draws
 * real pixels into a bitmap -- the same mechanism `EvidenceScreenshots` uses, and for the same
 * reason: `captureToImage()` needs a window and there is none.
 */
package com.showup.fit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// `sdk = [34]` because the project targets 36 and Robolectric 4.14.1 ships images up to 35; the
// same pin the evidence harness carries, for the same reason. Nothing here is version-sensitive --
// it is arithmetic on a Canvas.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconStrokeTest {

    /**
     * Draws one icon at a chosen density and returns the bitmap.
     *
     * The density is the whole point of the test, so it is provided rather than inherited: the bug
     * was invisible at 1.0 and severe at 2.625, and a test that ran at whatever the host reported
     * would have been a coin toss.
     */
    private fun render(
        icon: BrandIcon,
        sizeDp: Int,
        strokeWidth: Float,
        density: Float,
    ): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity)
        activity.setContentView(view)
        var px = 0
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
                px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
                Box(Modifier) {
                    Icon(icon, sizeDp.dp, tint = Color.Black, strokeWidth = strokeWidth)
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, px, px)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /**
     * How many rows of a column carry ink.
     *
     * Measured well to the LEFT of the arrowhead, so it is the shaft being measured and not the
     * point where three strokes meet. Any pixel that is not fully transparent counts: an
     * antialiased edge is still ink, and ignoring it would under-report every measurement by the
     * same amount in both the broken and the fixed case, which is the one thing that would make
     * this test agree with itself while being wrong.
     */
    private fun inkHeightAt(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).count { y -> Bitmap.createBitmap(bitmap, x, y, 1, 1).getPixel(0, 0) != 0 }

    @Test
    fun `the shaft is as thick as the design draws it, not thicker`() {
        // 18dp at 2.625x is 47px, so the 24-grid scales by 1.97. A stroke of 2 grid units is
        // 2 x 1.97 = 3.9px. Before the fix it was 2.dp.toPx() x 1.97 = 10.3px -- 2.6x heavier,
        // which is the density, and the head's strokes merged into a solid triangle.
        val bitmap = render(BrandIcon.ArrowRight, sizeDp = 18, strokeWidth = 2f, density = 2.625f)
        val shaft = inkHeightAt(bitmap, x = bitmap.width / 5)
        // Antialiasing spreads a 3.9px line over about 5 rows; a 10.3px one cannot fit in that.
        assertTrue("the shaft measured ${shaft}px, expected about 4", shaft in 3..6)
    }

    @Test
    fun `the stroke scales with the icon, not with the density squared`() {
        // THE PROPERTY THE BUG BROKE. Doubling the density doubles every dimension, so the stroke
        // in PIXELS doubles -- and no more. Under the bug it quadrupled, because the density was
        // applied twice.
        val small = inkHeightAt(
            render(BrandIcon.ArrowRight, 18, 2f, density = 1f), 18 / 5,
        )
        val large = render(BrandIcon.ArrowRight, 18, 2f, density = 2f)
        val big = inkHeightAt(large, large.width / 5)
        assertTrue("density 1 gave ${small}px and density 2 gave ${big}px", big <= small * 3)
    }

    @Test
    fun `a heavier stroke is heavier, so the parameter still does something`() {
        // The guard against "fixing" this by ignoring the parameter altogether.
        val thin = render(BrandIcon.ArrowRight, 24, 1f, density = 2f)
        val thick = render(BrandIcon.ArrowRight, 24, 4f, density = 2f)
        assertTrue(
            "1f gave ${inkHeightAt(thin, thin.width / 5)}px and " +
                "4f gave ${inkHeightAt(thick, thick.width / 5)}px",
            inkHeightAt(thick, thick.width / 5) > inkHeightAt(thin, thin.width / 5),
        )
    }

    @Test
    fun `the icon fills the box it is given`() {
        // A sanity check on the harness itself: if the bitmap were blank every assertion above
        // would pass for the wrong reason.
        val bitmap = render(BrandIcon.ArrowRight, 24, 2f, density = 2f)
        assertEquals(48, bitmap.width)
        assertTrue("nothing was drawn", (0 until bitmap.width).any { inkHeightAt(bitmap, it) > 0 })
    }
}
