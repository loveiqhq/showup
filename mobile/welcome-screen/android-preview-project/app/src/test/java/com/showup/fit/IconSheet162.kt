/*
 * IconSheet162.kt
 * ShowUp · renders the notification ask's five glyphs so they can be LOOKED AT (SHOWUP-162)
 *
 * Three of them are new and one was re-derived from the kit's path, and every one of those is an
 * arc or a starburst reconstructed from SVG by hand. The project's own lesson is that an icon
 * redrawn from memory is a real icon, faithfully drawn, and the wrong one -- and no assertion
 * catches that. So this draws them large, on the pip they actually sit in, and the answer is a
 * picture rather than a passing test.
 */
package com.showup.fit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Purple
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconSheet162 {

    /** The five the ask names, plus the filled heart Connect kept, at the size they are drawn. */
    private val glyphs = listOf(
        BrandIcon.Heart, BrandIcon.Sparkles, BrandIcon.MessageCircle,
        BrandIcon.Clock, BrandIcon.Close, BrandIcon.HeartFilled,
    )

    @Test
    fun `render the notification glyphs at pip size and at four times it`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity)
        activity.setContentView(view)
        var w = 0
        var h = 0
        view.setContent {
            w = with(LocalDensity.current) { 560.dp.roundToPx() }
            h = with(LocalDensity.current) { 260.dp.roundToPx() }
            Box(Modifier.size(560.dp, 260.dp).background(androidx.compose.ui.graphics.Color.White)) {
                Row(
                    Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    glyphs.forEach { g ->
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // The real thing: a 40 pip at radius 12, icon 20 at stroke 1.8.
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(LilacWash),
                                contentAlignment = Alignment.Center,
                            ) { Icon(g, 20.dp, tint = Purple, strokeWidth = 1.8f) }
                            // Four times over, where a wrong arc is obvious.
                            Icon(g, 80.dp, tint = Purple, strokeWidth = 1.8f)
                        }
                    }
                }
            }
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File("build/evidence").apply { mkdirs() }
        File(out, "ICONS_notifications.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("wrote ICONS_notifications.png")
    }
}
