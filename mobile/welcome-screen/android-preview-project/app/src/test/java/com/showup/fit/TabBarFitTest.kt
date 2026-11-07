/*
 * TabBarFitTest.kt
 * ShowUp · the TabBar's labels never wrap (the 4 Nov 2026 device audit)
 *
 * A tab label that wraps is a word broken in two: `Settings` read `Setting` / `s` at 200 % on a 320
 * phone, under every screen with the bar. The fit harness cannot see it -- the label's text is cleared
 * from the semantics tree (the tab carries the name) and nothing is clipped, the bar just grows -- so
 * this measures the growth: no phone's bar may be taller than the widest phone's at the same type size.
 */
package com.showup.fit

import com.showup.date.DATE_LOCKED_TABS
import com.showup.designsystem.TAB_BAR_TAG
import com.showup.designsystem.TabBar
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class TabBarFitTest {

    @Test
    fun `no tab label wraps on any phone at any font size`() {
        val widths = (DEVICES + EDGE_DEVICES).map { it.width }.distinct().sorted()
        val fresh = mutableListOf<String>()
        // Every Android font step; the bar as the Date screen draws it (three tabs locked) and plain.
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) for (locked in listOf(false, true)) {
            fun height(width: Int) = shootGeometry(width, 400, scale) {
                TabBar(active = null, onTab = {}, disabled = if (locked) DATE_LOCKED_TABS else emptySet())
            }.tag(TAB_BAR_TAG).height
            val roomy = height(widths.last())
            widths.forEach { w ->
                val h = height(w)
                if (h > roomy + 0.75f) {
                    fresh += "%d wide @ %.2f%s: %.1f tall, %.1f at %d".format(w, scale, if (locked) " locked" else "", h, roomy, widths.last())
                }
            }
        }
        assertTrue("a tab label wrapped (${fresh.size}):\n" + fresh.joinToString("\n") { "  $it" }, fresh.isEmpty())
    }
}
