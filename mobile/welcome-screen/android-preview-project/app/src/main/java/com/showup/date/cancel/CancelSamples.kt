/*
 * CancelSamples.kt
 * ShowUp · SHOWUP-198's frames A to F, H and I drawn still, with nothing wired -- the red link at the end of the Date
 * screen, the guard sheet in its states, the Date screen once checked in, and what the match sees. G is flow 10's
 * and has no frame here. One copy for the previews ([CancelPreviews]), the debug States page and the evidence harness.
 *
 * The values are the kit's (`screen-cancel-confirm-reference.jsx`): Leonie, 19:00 at Cafe Krone on the Date screen's
 * sample day (3 Nov 2026). The sheet's clock is 15:55 while the chat is locked (B), 17:42 with it open (C, D, E) and
 * 18:35 Berlin (17:35Z), five minutes past Date 05's 18:30 cutoff, for I.
 */
package com.showup.date.cancel

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.showup.designsystem.LocalSheetScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.showup.arrival.ArrivalSamples
import com.showup.change.ChangeKind
import com.showup.change.ChangeSamples
import com.showup.chat.DateEndedScreen
import com.showup.date.ActiveDate
import com.showup.date.DateSamples
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.end.DateEndOver
import java.time.Instant

object CancelSamples {
    private val ZONE = DateSamples.ZONE

    /** I: five minutes past Date 05's cutoff (meet minus 0:30), the chat open. */
    val CLOSED_NOW: Instant = Instant.parse("2026-11-03T17:35:00Z")

    /** The date, not checked in, the cancel link on, a cancel costing 10 points, and Date 05's block of a current server. */
    private fun date(chatOpen: Boolean, checkedIn: Boolean = false): ActiveDate =
        ChangeSamples.DATE.copy(
            coordinateOpen = chatOpen, chatTutorialSeen = true, endsEnabled = true, checkedIn = checkedIn,
            cancelPenaltyPoints = 10,
        )

    private fun ui(
        now: Instant,
        chatOpen: Boolean,
        sent: SentRequest? = null,
        failed: Boolean = false,
    ) = CancelUi(open = true, sent = sent, changeRequested = sent != null, failed = failed, date = date(chatOpen), now = now)

    @Composable
    private fun dateScreen(date: ActiveDate, now: Instant, showCancel: Boolean, scroll: ScrollState? = null) {
        val state = scroll ?: remember { ScrollState(0) }
        DateScreen(
            DateUi(date, now, loading = false), ZONE,
            // Date 03's bar before check-in, Date 06's after: F (checked in) draws the End Date bar, as the sheet does.
            checkInBar = ArrivalSamples.bar,
            endDateBar = { com.showup.date.end.EndDateBar(DateSamples.DATE.match.name, onEnd = {}) },
            showCancel = showCancel, scroll = state,
        )
    }

    @Composable
    private fun sheet(ui: CancelUi, scroll: ScrollState?) =
        CompositionLocalProvider(LocalSheetScroll provides scroll) {
            DateEndOver(
                visible = true,
                sheets = { CancelDateSheetHost(ui, ZONE, CancelActions()) },
                content = { dateScreen(ui.date!!, ui.now, showCancel = true) },
            )
        }

    /** A Date-screen frame scrolled to its end -- the harness's [scroll] or its own -- and kept there. */
    @Composable
    private fun atEnd(date: ActiveDate, now: Instant, showCancel: Boolean, scroll: ScrollState?) {
        val state = scroll ?: remember { ScrollState(0) }
        // Follows maxValue: the pinned footer is measured after the first layout and its spacer then lengthens the
        // scroll, so a single scroll to the end stopped short of the red link.
        LaunchedEffect(state.maxValue) { state.scrollTo(state.maxValue) }
        dateScreen(date, now, showCancel, state)
    }

    /**
     * Frame [id] ("A" to "F", "H", "I"). [scroll] is the one the evidence harness drives to its end: the Date screen's
     * for A and F (the red link, or its absence, sits last), the sheet's for B to E and I (the solution box, cost box,
     * choices and error sit below a short phone's fold). Without one, A and F scroll themselves once laid out.
     */
    @Composable
    fun frame(id: String, scroll: ScrollState? = null) {
        when (id) {
            "A" -> atEnd(date(chatOpen = false), DateSamples.A_NOW, showCancel = true, scroll)
            "B" -> sheet(ui(DateSamples.A_NOW, chatOpen = false), scroll)
            "C" -> sheet(ui(DateSamples.B_NOW, chatOpen = true), scroll)
            "D" -> sheet(ui(DateSamples.B_NOW, chatOpen = true, sent = SentRequest(ChangeKind.Time, "20:00")), scroll)
            "E" -> sheet(ui(DateSamples.B_NOW, chatOpen = true, failed = true), scroll)
            "F" -> atEnd(date(chatOpen = true, checkedIn = true), DateSamples.B_NOW, showCancel = false, scroll)
            "H" -> DateEndedScreen(
                EndedOutcome("sample-date", DateSamples.DATE.match.name, endedByYou = false, reason = OutcomeReason.Cancelled),
                photoUrl = null, onFindNext = {},
            )
            "I" -> sheet(ui(CLOSED_NOW, chatOpen = true), scroll)
            else -> Text("Unknown cancel frame $id")
        }
    }

    /** The ticket's frame letters, in order; G is flow 10's. */
    val LETTERS = listOf("A", "B", "C", "D", "E", "F", "H", "I")
}
