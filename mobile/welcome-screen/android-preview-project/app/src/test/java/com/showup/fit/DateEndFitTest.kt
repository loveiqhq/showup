/*
 * DateEndFitTest.kt
 * ShowUp · Date 06, end the date and report a problem, on every phone at every type size (SHOWUP-197)
 *
 * The ticket's frames A to C and E to J (D is Date 03's) on every phone of [DEVICES] and [EDGE_DEVICES], all
 * seven Android font steps and every Android from 11 to 15, under the shared checks (every touch area 44 dp both
 * ways and none on another, no word broken between two letters, nothing clipped or off the screen) and this
 * ticket's own:
 *
 *  - the sheet's CTA (or, on the guide, its end button) is drawn on every frame -- the sheet scrolls inside the
 *    shell where it is taller than the phone, and the buttons stay reachable by that scroll;
 *  - J draws its box above the CTA on every phone;
 *  - the End Date bar lies wholly inside the screen width.
 *
 * Split into several tests: this machine reaps a run that renders thousands of frames in one.
 */
package com.showup.fit

import androidx.compose.runtime.Composable
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateSamples
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.end.DateEndSheetHost
import com.showup.date.end.DateEndUi
import com.showup.date.end.END_BAR_TAG
import com.showup.date.end.END_CTA_TAG
import com.showup.date.end.END_ERROR_TAG
import com.showup.date.end.END_GUIDE_END_TAG
import com.showup.date.end.END_PICK_NO_SHOW_TAG
import com.showup.date.end.EndActions
import com.showup.date.end.EndDateBar
import com.showup.date.end.EndRoute
import com.showup.date.end.EndStep
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// w1000: the unfolded Fold 7 row is 984 wide; the frame is sized per device either way.
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class DateEndFitTest {
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES
    private val zone = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-21T18:00:00Z")

    private class Frame(val label: String, val tag: String?, val content: @Composable () -> Unit)

    private fun date(chatOpen: Boolean = true) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = true, chatTutorialSeen = true, arrival = null, endsEnabled = true,
    )

    private fun sheet(
        label: String,
        tag: String?,
        route: EndRoute = EndRoute.Wrap,
        step: EndStep = EndStep.Menu,
        past: Boolean = true,
        chatOpen: Boolean = true,
        failed: Boolean = false,
    ): Frame {
        val ui = DateEndUi(
            route = route, step = step, date = date(chatOpen), failed = failed, checkedIn = true,
            now = if (past) meet.plusSeconds(11 * 60) else meet.minusSeconds(5 * 60),
        )
        return Frame(label, tag) { DateEndSheetHost(ui, "Leonie", zone, EndActions()) }
    }

    /** A inside the Date screen's footer slot, above the TabBar, as the app draws it. */
    private val barFrame = {
        Frame("A bar", END_BAR_TAG) {
            DateScreen(
                DateUi(DateSamples.DATE.copy(checkedIn = true, endsEnabled = true), DateSamples.A_NOW, loading = false),
                zone,
                endDateBar = { EndDateBar("Leonie", onEnd = {}) },
            )
        }
    }

    private val menuFrames = listOf(
        barFrame,
        { sheet("B how did it go", END_PICK_NO_SHOW_TAG) },
        { sheet("C we met", END_CTA_TAG, step = EndStep.Met) },
        { sheet("H report a problem", END_PICK_NO_SHOW_TAG, route = EndRoute.Report) },
    )

    private val guideFrames = listOf(
        { sheet("E not here yet, before", END_GUIDE_END_TAG, step = EndStep.Guide, past = false) },
        { sheet("F not here yet, after", END_GUIDE_END_TAG, step = EndStep.Guide) },
        { sheet("E chat locked", END_GUIDE_END_TAG, step = EndStep.Guide, past = false, chatOpen = false) },
        { sheet("G thanks for waiting", END_CTA_TAG, step = EndStep.NoShow) },
        { sheet("I uncomfortable", END_CTA_TAG, route = EndRoute.Report, step = EndStep.Uncomfortable) },
    )

    private val failedFrames = listOf(
        { sheet("J on G", END_CTA_TAG, step = EndStep.NoShow, failed = true) },
        { sheet("J on C", END_CTA_TAG, step = EndStep.Met, failed = true) },
        { sheet("J on I", END_CTA_TAG, route = EndRoute.Report, step = EndStep.Uncomfortable, failed = true) },
    )

    private fun rules(frame: Frame): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        val anchor = frame.tag?.let { f.tagged(it) }
        if (frame.tag != null && anchor == null) fail(frame.tag, "NOT DRAWN", frame.label)
        if (frame.label.startsWith("J")) {
            val box = f.tagged(END_ERROR_TAG)
            if (box == null || anchor == null) fail("the error box", "NOT DRAWN", frame.label)
            else if (box.bottom > anchor.top + SLACK) fail("the error box", "NOT ABOVE THE CTA", "box ends %.1f, CTA starts %.1f".format(box.bottom, anchor.top))
        }
        if (frame.label.startsWith("A") && anchor != null) {
            if (anchor.left < -SLACK || anchor.right > f.device.width + SLACK) fail("the bar", "WIDER THAN THE SCREEN", "%.1f..%.1f".format(anchor.left, anchor.right))
        }
        f.tapTargets().forEach { (n, r, _) ->
            if (r.width < TAP_DP - SLACK || r.height < TAP_DP - SLACK) fail(n, "TAP AREA UNDER 44DP", "%.1f x %.1fdp".format(r.width, r.height))
        }
        found
    }

    private fun run(device: Device, frame: Frame, scale: Float): List<String> =
        measureFit(device, "End/${frame.label} @$scale", scale, 0, extra = rules(frame), content = frame.content)
            .filterNot { it.advisory }
            .map { "$it" }

    private fun sweep(frames: List<() -> Frame>, scales: List<Float>): List<String> {
        val fresh = mutableListOf<String>()
        for (make in frames) for (scale in scales) phones.forEach { d -> fresh += run(d, make(), scale) }
        return fresh
    }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue("Date 06 layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" }, findings.isEmpty())
    }

    @Test
    fun `A, B, C and H are clean on every phone at every type size`() {
        report(sweep(menuFrames, scales), "end-fit-menus.txt")
    }

    @Test
    fun `E, F, G and I are clean on every phone at every type size`() {
        report(sweep(guideFrames, scales), "end-fit-steps.txt")
    }

    @Test
    fun `J is clean on every phone at every type size`() {
        report(sweep(failedFrames, scales), "end-fit-failed.txt")
    }

    @Test
    @Config(sdk = [30, 31, 32, 33, 35])
    fun `the Date 06 frames are clean on every Android from 11 to 15`() {
        report(sweep(menuFrames + guideFrames + failedFrames, listOf(1f, 2f)), "end-fit-sdk.txt")
    }

    /**
     * "The CTAs and choices are reachable by scrolling" (the ticket, for E, F and I at 375 x 667): the sheet scrolls
     * inside its shell where it is taller than the phone, so each frame's last control must come inside the frame
     * once scrolled to -- driven with `performScrollTo`, not read off the layout. At the largest type as well.
     */
    @Test
    fun `every sheet's CTA is reachable by scrolling at 375 x 667`() {
        val found = mutableListOf<String>()
        val frames = menuFrames.drop(1) + guideFrames + failedFrames
        for (make in frames) {
            val frame = make()
            for (scale in listOf(1f, 2f)) {
                found += unreachableByScroll(375, 667, scale, listOfNotNull(frame.tag), frame.content).map { "${frame.label}: $it" }
            }
        }
        assertTrue("Date 06 controls out of reach (${found.size}):\n" + found.joinToString("\n") { "  $it" }, found.isEmpty())
    }

    private companion object {
        const val TAP_DP = 44f
        const val SLACK = 0.75f
    }
}
