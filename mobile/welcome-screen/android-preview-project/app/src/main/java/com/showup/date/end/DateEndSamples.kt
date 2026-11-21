/*
 * DateEndSamples.kt
 * ShowUp · SHOWUP-197's frames A to J drawn still, with nothing wired -- the End Date bar, the wrap sheet's menu and
 * its steps, the report sheet, Date 03's gate with the note, and the failed end. One copy for the previews
 * ([DateEndPreviews]), the debug States page and the evidence harness, as [com.showup.change.ChangeSamples] is for 196.
 *
 * The values are the kit's (`screen-wrap-report-reference.jsx`): Leonie, 19:00 at Cafe Krone on the Date screen's
 * sample day (3 Nov 2026, 19:00 Berlin = 18:00Z). The sheet's clock is 17:42 before the meet time (E and the menus)
 * and 19:14 after it (F, G and J).
 */
package com.showup.date.end

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.showup.designsystem.LocalSheetScroll
import com.showup.arrival.ArrivalSamples
import com.showup.date.ActiveDate
import com.showup.date.DateSamples
import com.showup.date.DateScreen
import com.showup.date.DateUi
import java.time.Instant

object DateEndSamples {
    private val ZONE = DateSamples.ZONE

    /** The sheet's clock once the meet time has passed: 19:14 Berlin. */
    val PAST_NOW: Instant = Instant.parse("2026-11-03T18:14:00Z")

    /** The date these frames draw: the chat open, the end routes on, and (by default) this person checked in. */
    private fun date(checkedIn: Boolean = true): ActiveDate =
        DateSamples.DATE.copy(coordinateOpen = true, chatTutorialSeen = true, endsEnabled = true, checkedIn = checkedIn)

    private fun ui(
        step: EndStep,
        route: EndRoute = EndRoute.Wrap,
        now: Instant = DateSamples.B_NOW,
        failed: Boolean = false,
        checkedIn: Boolean = true,
    ) = DateEndUi(route = route, step = step, date = date(checkedIn), now = now, failed = failed, checkedIn = checkedIn)

    /** The Date screen the sheet is drawn over: the End Date bar once checked in, the Check-in bar before. */
    @Composable
    private fun dateScreen(checkedIn: Boolean = true, now: Instant = DateSamples.B_NOW) =
        DateScreen(
            DateUi(date(checkedIn), now, loading = false), ZONE,
            checkInBar = ArrivalSamples.bar,
            endDateBar = { EndDateBar(DateSamples.DATE.match.name, onEnd = {}) },
        )

    @Composable
    private fun sheet(ui: DateEndUi, scroll: ScrollState?) =
        CompositionLocalProvider(LocalSheetScroll provides scroll) {
            DateEndOver(
                visible = true,
                sheets = { DateEndSheetHost(ui, DateSamples.DATE.match.name, ZONE, EndActions()) },
                content = { dateScreen(ui.checkedIn, ui.now) },
            )
        }

    /**
     * Frame [id] ("A" to "J"), drawn as the ticket's kit props draw it. [scroll] is the sheet's, which the evidence
     * harness drives to its end where what tells the frame apart (E, F and I's buttons) is below a short phone's fold.
     */
    @Composable
    fun frame(id: String, scroll: ScrollState? = null) {
        when (id) {
            "A" -> dateScreen()
            "B" -> sheet(ui(EndStep.Menu), scroll)
            "C" -> sheet(ui(EndStep.Met), scroll)
            // Date 03's sheets, consumed: the gate with the note, over the Date screen before a check-in.
            "D" -> ArrivalSamples.over(ArrivalSamples.B_NOTE) { dateScreen(checkedIn = false) }
            "E" -> sheet(ui(EndStep.Guide), scroll)
            "F" -> sheet(ui(EndStep.Guide, now = PAST_NOW), scroll)
            "G" -> sheet(ui(EndStep.NoShow, now = PAST_NOW), scroll)
            "H" -> sheet(ui(EndStep.Menu, route = EndRoute.Report), scroll)
            "I" -> sheet(ui(EndStep.Uncomfortable, route = EndRoute.Report), scroll)
            "J" -> sheet(ui(EndStep.NoShow, now = PAST_NOW, failed = true), scroll)
            else -> Text("Unknown end frame $id")
        }
    }

    /** The ticket's frame letters, in order. */
    val LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
}
