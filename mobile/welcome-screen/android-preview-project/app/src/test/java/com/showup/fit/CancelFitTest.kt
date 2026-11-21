/*
 * CancelFitTest.kt
 * ShowUp · Date 07, the guard sheet and what the match sees, on every phone at every type size (SHOWUP-198)
 *
 * Frames B, C, D, E and I (one sheet, parts replaced in place) and H on the shared outcome screen, on every phone
 * of [DEVICES] and [EDGE_DEVICES], all seven Android font steps and every Android from 11 to 15, under the shared
 * checks (every touch area 44 dp both ways and none on another, no word broken between two letters, nothing clipped
 * or off the screen) and this ticket's own:
 *
 *  - the choices are drawn on every sheet frame: the sheet scrolls inside its shell where it is taller than the
 *    phone, and `Don't cancel my date` and `Cancel anyway` stay reachable by that scroll;
 *  - E draws its box between the cost box and the choices;
 *  - I draws no body line and no tile;
 *  - H's Search CTA is drawn on every phone.
 *
 * Split into several tests: this machine reaps a run that renders thousands of frames in one.
 */
package com.showup.fit

import androidx.compose.runtime.Composable
import com.showup.change.ChangeKind
import com.showup.change.DateChange
import com.showup.chat.CHAT_CTA_TAG
import com.showup.chat.DateEndedScreen
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.cancel.CANCEL_CONFIRM_TAG
import com.showup.date.cancel.CANCEL_COST_TAG
import com.showup.date.cancel.CANCEL_KEEP_TAG
import com.showup.date.cancel.CANCEL_MESSAGE_TAG
import com.showup.date.cancel.CANCEL_SOLUTION_BODY_TAG
import com.showup.date.cancel.CANCEL_TILE_PLACE_TAG
import com.showup.date.cancel.CANCEL_TILE_TIME_TAG
import com.showup.date.cancel.CancelActions
import com.showup.date.cancel.CancelDateSheetHost
import com.showup.date.cancel.CancelUi
import com.showup.date.cancel.SentRequest
import com.showup.date.end.END_ERROR_TAG
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
class CancelFitTest {
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES
    private val zone = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-21T18:00:00Z")

    private class Frame(val label: String, val tag: String?, val content: @Composable () -> Unit)

    private fun date(chatOpen: Boolean) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = false, chatTutorialSeen = true, arrival = null, endsEnabled = true,
        change = DateChange(meet.minusSeconds(30 * 60), true, listOf(meet.plusSeconds(900)), null, null, emptyList()),
        cancelPenaltyPoints = 10,
    )

    private fun sheet(
        label: String,
        chatOpen: Boolean = false,
        closed: Boolean = false,
        sent: SentRequest? = null,
        failed: Boolean = false,
    ): Frame {
        val ui = CancelUi(
            open = true, sent = sent, failed = failed, date = date(chatOpen),
            now = if (closed) meet.minusSeconds(10 * 60) else meet.minusSeconds(3 * 3600),
        )
        return Frame(label, CANCEL_KEEP_TAG) { CancelDateSheetHost(ui, zone, CancelActions()) }
    }

    private fun partnerCancelled() = Frame("H partner cancelled", CHAT_CTA_TAG) {
        DateEndedScreen(EndedOutcome("d1", "Leonie", endedByYou = false, reason = OutcomeReason.Cancelled), photoUrl = null, onFindNext = {})
    }

    private val sheetFrames = listOf(
        { sheet("B guard, chat locked") },
        { sheet("C guard, chat open", chatOpen = true) },
        { sheet("D request sent", chatOpen = true, sent = SentRequest(ChangeKind.Place, "Osteria Nove")) },
        { sheet("E cancel failed", chatOpen = true, failed = true) },
        { sheet("I from meet minus 0:30", closed = true) },
    )

    private val longRequests = listOf(
        { sheet("D time request", sent = SentRequest(ChangeKind.Time, "20:00")) },
        { sheet("D long place request", chatOpen = true, sent = SentRequest(ChangeKind.Place, "Café Fräulein am Landwehrkanal")) },
    )

    private fun rules(frame: Frame): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        val anchor = frame.tag?.let { f.tagged(it) }
        if (frame.tag != null && anchor == null) fail(frame.tag, "NOT DRAWN", frame.label)
        if (frame.label.startsWith("E")) {
            val box = f.tagged(END_ERROR_TAG)
            val cost = f.tagged(CANCEL_COST_TAG)
            if (box == null || anchor == null || cost == null) {
                fail("the error box", "NOT DRAWN", frame.label)
            } else {
                if (box.bottom > anchor.top + SLACK) fail("the error box", "NOT ABOVE THE CHOICES", "box ends %.1f, keep starts %.1f".format(box.bottom, anchor.top))
                if (box.top < cost.bottom - SLACK) fail("the error box", "NOT BELOW THE COST BOX", "box starts %.1f, cost ends %.1f".format(box.top, cost.bottom))
            }
        }
        if (frame.label.startsWith("I")) {
            listOf(CANCEL_SOLUTION_BODY_TAG, CANCEL_TILE_TIME_TAG, CANCEL_TILE_PLACE_TAG).forEach {
                if (f.tagged(it) != null) fail(it, "DRAWN IN I", frame.label)
            }
            if (f.tagged(CANCEL_MESSAGE_TAG) == null) fail(CANCEL_MESSAGE_TAG, "NOT DRAWN", frame.label)
        }
        f.tapTargets().forEach { (n, r, _) ->
            if (r.width < TAP_DP - SLACK || r.height < TAP_DP - SLACK) fail(n, "TAP AREA UNDER 44DP", "%.1f x %.1fdp".format(r.width, r.height))
        }
        found
    }

    private fun run(device: Device, frame: Frame, scale: Float): List<String> =
        measureFit(device, "Cancel/${frame.label} @$scale", scale, 0, extra = rules(frame), content = frame.content)
            .filterNot { it.advisory }
            .map { "$it" }

    private fun sweep(frames: List<() -> Frame>, scales: List<Float>): List<String> {
        val fresh = mutableListOf<String>()
        for (make in frames) for (scale in scales) phones.forEach { d -> fresh += run(d, make(), scale) }
        return fresh
    }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue("Date 07 layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" }, findings.isEmpty())
    }

    @Test
    fun `B, C and D are clean on every phone at every type size`() {
        report(sweep(sheetFrames.take(3) + longRequests, scales), "cancel-fit-guard.txt")
    }

    @Test
    fun `E and I are clean on every phone at every type size`() {
        report(sweep(sheetFrames.drop(3), scales), "cancel-fit-states.txt")
    }

    @Test
    fun `H is clean on every phone at every type size`() {
        report(sweep(listOf({ partnerCancelled() }), scales), "cancel-fit-partner.txt")
    }

    @Test
    @Config(sdk = [30, 31, 32, 33, 35])
    fun `the Date 07 frames are clean on every Android from 11 to 15`() {
        report(sweep(sheetFrames + { partnerCancelled() }, listOf(1f, 2f)), "cancel-fit-sdk.txt")
    }

    /**
     * "The choices are reachable by scrolling" (the ticket, for the tall guard sheet at 375 x 667): `Don't cancel my
     * date` and `Cancel anyway` (and, in I, the one `Message` button) must come inside the frame once scrolled to --
     * driven with `performScrollTo`, not read off the layout. At the largest type as well.
     */
    @Test
    fun `the guard sheet's choices are reachable by scrolling at 375 x 667`() {
        val found = mutableListOf<String>()
        for (make in sheetFrames + longRequests) {
            val frame = make()
            val tags = if (frame.label.startsWith("I")) listOf(CANCEL_MESSAGE_TAG, CANCEL_KEEP_TAG) else listOf(CANCEL_KEEP_TAG, CANCEL_CONFIRM_TAG)
            for (scale in listOf(1f, 2f)) {
                found += unreachableByScroll(375, 667, scale, tags, frame.content).map { "${frame.label}: $it" }
            }
        }
        assertTrue("Date 07 choices out of reach (${found.size}):\n" + found.joinToString("\n") { "  $it" }, found.isEmpty())
    }

    private companion object {
        const val TAP_DP = 44f
        const val SLACK = 0.75f
    }
}
