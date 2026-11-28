/*
 * PartnerEndedFitTest.kt
 * ShowUp · Date 08's four payloads on every phone at every type size (SHOWUP-199)
 *
 * Under the shared checks (nothing clipped, 44 dp touch areas) and this ticket's own: the CTA is reachable by
 * scrolling and lies below the last row at every size, never over it. Whether the CTA NEEDS a scroll on 375 x 667 is
 * measured and written to build/partner-ended-scroll.txt, not asserted: it is a fact about the copy's length.
 * One test per payload because this machine reaps a run that renders thousands of frames in one.
 */
package com.showup.fit

import com.showup.date.PartnerScreen
import com.showup.date.partnerend.PARTNER_ENDED_CTA_TAG
import com.showup.date.partnerend.PARTNER_ENDED_ROWS_TAG
import com.showup.date.partnerend.PartnerEndedScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class PartnerEndedFitTest {
    private val MEET = "7:00 PM"
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)

    private fun rules(): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        val cta = f.tagged(PARTNER_ENDED_CTA_TAG)
        val rows = f.tagged(PARTNER_ENDED_ROWS_TAG)
        if (cta == null || rows == null) found += Violation(f.device, f.screen, "the CTA or rows", "NOT DRAWN", "")
        else if (cta.top < rows.bottom - 0.75f) {
            found += Violation(f.device, f.screen, "the CTA", "OVER THE ROWS", "rows end %.1f, CTA starts %.1f".format(rows.bottom, cta.top))
        }
        f.tapTargets().forEach { (n, r, _) ->
            if (r.width < 43.25f || r.height < 43.25f) found += Violation(f.device, f.screen, n, "TAP AREA UNDER 44DP", "%.1f x %.1fdp".format(r.width, r.height))
        }
        found
    }

    private fun sweep(screen: PartnerScreen): List<String> {
        val out = mutableListOf<String>()
        for (scale in scales) for (d in DEVICES) {
            out += measureFit(d, "PartnerEnded/${screen.wire} @$scale", scale, 0, extra = rules()) {
                PartnerEndedScreen(screen, "Leonie", null, MEET) {}
            }.filterNot { it.advisory }.map { "$it" }
        }
        return out
    }

    private fun check(screen: PartnerScreen) {
        val found = sweep(screen)
        java.io.File("build/partner-ended-fit-${screen.wire}.txt").writeText(found.joinToString("\n"))
        assertTrue("Date 08 ${screen.wire} layout problems (${found.size}):\n" + found.take(40).joinToString("\n") { "  $it" }, found.isEmpty())
        val reach = mutableListOf<String>()
        for (scale in listOf(1f, 2f)) reach += unreachableByScroll(375, 667, scale, listOf(PARTNER_ENDED_CTA_TAG)) {
            PartnerEndedScreen(screen, "Leonie", null, MEET) {}
        }
        assertTrue("CTA out of reach: $reach", reach.isEmpty())
    }

    @Test fun `A is clean on every phone at every type size`() = check(PartnerScreen.YouNoShowed)
    @Test fun `B is clean on every phone at every type size`() = check(PartnerScreen.DateEndedDiscomfort)
    @Test fun `C is clean on every phone at every type size`() = check(PartnerScreen.DateClosedPositive)
    @Test fun `D is clean on every phone at every type size`() = check(PartnerScreen.DateTimedOut)

    /** Measured, not asserted (ruling R-b): the CTA's bottom against the 375 x 667 safe area at 1x type. */
    @Test
    fun `report whether the CTA needs a scroll on 375 x 667`() {
        val phone = DEVICES.first { it.width == 375 && it.height == 667 }
        val lines = PartnerScreen.entries.map { s ->
            var needs = ""
            measureFit(phone, "PartnerEnded/${s.wire} reach", 1f, 0, extra = { f ->
                val cta = f.tagged(PARTNER_ENDED_CTA_TAG)
                needs = if (cta == null) "not drawn" else "CTA bottom %.1f of %.1f usable, scroll needed: %s".format(cta.bottom, f.height, cta.bottom > f.height + 0.75f)
                emptyList()
            }) { PartnerEndedScreen(s, "Leonie", null, MEET) {} }
            "${s.wire}: $needs"
        }
        java.io.File("build/partner-ended-scroll.txt").writeText(lines.joinToString("\n"))
    }
}
