/*
 * ArrivalFitTest.kt
 * ShowUp · the arrival check-in on every phone at every type size (SHOWUP-195)
 *
 * A (the Date screen with the Check-in bar, and with the End date link switched on), B, B with a note, C,
 * D, E (after Close: the footer empty), F (the chat's start grid with B over it), H (the arrival card
 * hoisted, the chat open and still locked) and G (the date gate: not determined, denied, services off;
 * today with the pill, after the meet time with the late pill, tomorrow with none) -- on every phone of
 * [DEVICES] and [EDGE_DEVICES], all seven Android font steps, and every Android from 11 to 15.
 *
 * The rules are the 193 / 194 sweeps': every touch area 44 dp both ways (never 48) and none partly on
 * another, no word broken between two letters, nothing clipped or off the screen (the shared checks) and the
 * end of the Date screen's scroll resting above the footer. And this ticket's own:
 *
 *  - the bar (R12): every word whole at every size, the button inside the bar, the bar on the TabBar --
 *    or, once bar + TabBar pass 60 % of the screen, in the scroll above it;
 *  - the sheets (R9): at 375 x 667 at the default type `Not yet`, `Yes, I've arrived` / `Try again` and
 *    C's error box lie wholly inside the sheet's visible part -- under the 88 % cap's scroll edge and off
 *    the bottom of the screen alike counts as not visible (the ticket's evidence line);
 *  - the hoisted arrival card sits in the gutters and never on the next card.
 *
 * Split into several tests, as the 193 / 194 sweeps' memory lesson says: this machine reaps a run that
 * renders thousands of frames in one.
 */
package com.showup.fit

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import com.showup.arrival.ARRIVAL_BAR_TAG
import com.showup.arrival.ARRIVAL_CHECK_IN_TAG
import com.showup.arrival.ARRIVAL_CIRCLE_TAG
import com.showup.arrival.ARRIVAL_CLOSE_TAG
import com.showup.arrival.ARRIVAL_ERROR_TAG
import com.showup.arrival.ARRIVAL_NOT_YET_TAG
import com.showup.arrival.ARRIVAL_SHEET_TAG
import com.showup.arrival.ARRIVAL_UNDO_TAG
import com.showup.arrival.ARRIVAL_YES_TAG
import com.showup.arrival.ArrivalCopy
import com.showup.arrival.ArrivalEntry
import com.showup.arrival.ArrivalOver
import com.showup.arrival.CheckInBar
import com.showup.arrival.GatePhase
import com.showup.arrival.GateUi
import com.showup.chat.ChatSamples
import com.showup.chat.ChatScreen
import com.showup.date.ActiveDate
import com.showup.date.DATE_ABOUT_TAG
import com.showup.date.DATE_ACTIONS_TAG
import com.showup.date.DATE_ARRIVAL_TAG
import com.showup.date.DATE_CANCEL_TAG
import com.showup.date.DATE_COMMON_TAG
import com.showup.date.DATE_COORDINATE_TAG
import com.showup.date.DATE_FOOTER_BAR_TAG
import com.showup.date.DATE_HERO_TAG
import com.showup.date.DATE_INTENT_TAG
import com.showup.date.DATE_REPORT_TAG
import com.showup.date.DATE_TIME_CARD_TAG
import com.showup.date.DATE_WORKS_TAG
import com.showup.date.DateArrival
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.designsystem.TAB_BAR_TAG
import com.showup.profile.DATE_GATE_CARD_TAG
import com.showup.profile.DATE_GATE_CTA_TAG
import com.showup.profile.LocationScreenContext
import com.showup.profile.LocationState
import com.showup.profile.ProfileLocationScreen
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
class ArrivalFitTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val stateA = Instant.parse("2026-11-03T15:00:00Z")
    private val name = "Leonie"

    /** Every Android font size step, 85 % to Android 14's 200 %. */
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES

    private val date = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", name, 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = listOf("coffee_chat", "nature_walk", "playful", "deep_talk"),
        sharedInterests = listOf("reading", "hiking", "brunch"),
        unread = null, checkedIn = false, chatTutorialSeen = false,
    )

    /** A: the chat open (unlocked), the person not yet checked in -- the kit's `coordOpen checkInFlow`. */
    private val open = DateUi(date, unlock, loading = false)
    private val locked = DateUi(date, stateA, loading = false)
    private val checkedIn = DateUi(date.copy(checkedIn = true), unlock, loading = false)
    private fun arrived(ui: DateUi) =
        ui.copy(date = ui.date!!.copy(arrival = DateArrival(ui.now.minusSeconds(10))))

    private fun confirm(locError: Boolean = false, busy: Boolean = false, note: String? = null) =
        GateUi(phase = GatePhase.Confirm(ArrivalEntry.DateScreen, locError, busy, note))

    private val done = GateUi(phase = GatePhase.Done(ArrivalEntry.DateScreen))

    /** What one render is: a name, its content, and the scroll the Date screen's end-of-scroll rule drives. */
    private class Frame(val label: String, val scroll: ScrollState?, val content: @Composable () -> Unit)

    private fun dateWithBar(ui: DateUi, scroll: ScrollState, link: Boolean = false): @Composable () -> Unit = {
        DateScreen(
            ui, zone = berlin, scroll = scroll,
            checkInBar = { CheckInBar(name, onCheckIn = {}, onEnd = if (link) ({}) else null) },
        )
    }

    private fun sheet(label: String, gate: GateUi): Frame {
        val scroll = ScrollState(0)
        return Frame(label, null) {
            ArrivalOver(gate, name, onYes = {}, onNotYet = {}, onClose = {}, onUndo = {}, content = dateWithBar(open, scroll))
        }
    }

    private fun barFrame(label: String, ui: DateUi, link: Boolean = false): Frame {
        val scroll = ScrollState(0)
        return Frame(label, scroll, dateWithBar(ui, scroll, link))
    }

    private val barFrames = listOf(
        { barFrame("A bar", open) },
        { barFrame("A bar, End date link", open, link = true) },
        { barFrame("A bar, locked", locked) },
        { barFrame("E after Close", checkedIn) },
        { barFrame("H arrival card", arrived(open)) },
        { barFrame("H arrival card, locked", arrived(locked)) },
    )

    private val sheetFrames = listOf(
        { sheet("B guard", confirm()) },
        { sheet("B guard + note", confirm(note = ArrivalCopy.REPORT_NOTE)) },
        { sheet("B guard, busy", confirm(busy = true)) },
        { sheet("C no location fix", confirm(locError = true)) },
        { sheet("C no location fix + note", confirm(locError = true, note = ArrivalCopy.REPORT_NOTE)) },
        { sheet("D done", done) },
    )

    private val chatFrames = listOf(
        {
            Frame("F chat start + B", null) {
                ArrivalOver(
                    GateUi(phase = GatePhase.Confirm(ArrivalEntry.ChatChip, locError = false, busy = false, note = null)),
                    name, onYes = {}, onNotYet = {}, onClose = {}, onUndo = {},
                ) { ChatScreen(ChatSamples.B, berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}) }
            }
        },
        {
            Frame("F chat start + C", null) {
                ArrivalOver(
                    GateUi(phase = GatePhase.Confirm(ArrivalEntry.ChatChip, locError = true, busy = false, note = null)),
                    name, onYes = {}, onNotYet = {}, onClose = {}, onUndo = {},
                ) { ChatScreen(ChatSamples.B, berlin, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}) }
            }
        },
    )

    private fun gate(label: String, state: LocationState, now: Instant, day: String = "today", place: String? = "Cafe Krone"): Frame =
        Frame(label, null) {
            ProfileLocationScreen(
                state = state,
                context = LocationScreenContext.Date(
                    name = name, meetDay = day, meetAt = meet, place = place, photoUrl = null,
                    now = now, zone = berlin, onTab = {},
                ),
            )
        }

    private val gateFrames = listOf(
        { gate("G not determined, today", LocationState.Ask, meet.minusSeconds(78 * 60)) },
        { gate("G denied, today", LocationState.Denied, meet.minusSeconds(78 * 60)) },
        { gate("G services off, today", LocationState.ServicesOff, meet.minusSeconds(78 * 60)) },
        { gate("G denied, after the meet", LocationState.Denied, meet.plusSeconds(10 * 60)) },
        { gate("G denied, tomorrow", LocationState.Denied, Instant.parse("2026-11-02T22:00:00Z"), day = "tomorrow") },
        { gate("G denied, no place", LocationState.Denied, meet.minusSeconds(78 * 60), place = null) },
    )

    // ── the rules ───────────────────────────────────────────────────────────────────────────────

    private fun isSheetTap(n: String) = SHEET_TAGS.any { n.contains(it) }

    private fun rules(frame: Frame, scale: Float): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        fun contains(a: FitRect, b: FitRect) =
            a.left <= b.left + SLACK && a.right >= b.right - SLACK && a.top <= b.top + SLACK && a.bottom >= b.bottom - SLACK

        // 1. Every touch area 44 dp both ways, and none partly on another. With a sheet up, nothing behind
        // it answers: only the sheet's own controls are compared. Otherwise a control in the scroll passing
        // under the pinned footer is not an overlap (the footer is on top and the control scrolls clear).
        val taps = f.tapTargets()
        taps.forEach { (n, r, _) ->
            if (r.width < TAP_DP - SLACK || r.height < TAP_DP - SLACK) {
                fail(n, "TAP AREA UNDER 44DP", "%.1f x %.1fdp".format(r.width, r.height))
            }
        }
        val sheetUp = f.tagged(ARRIVAL_SHEET_TAG) != null
        val compared = if (sheetUp) taps.filter { isSheetTap(it.name) } else taps
        for (i in compared.indices) for (j in i + 1 until compared.size) {
            val (a, ra, sa) = compared[i]
            val (b, rb, sb) = compared[j]
            if (sa != sb || contains(ra, rb) || contains(rb, ra)) continue
            val w = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
            val h = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
            if (w > SLACK && h > SLACK) fail("$a / $b", "TAP AREAS OVERLAP", "%.1f x %.1fdp".format(w, h))
        }

        // 2. No word broken between two letters (R12 for the bar; the sheets' titles and body too).
        f.lineBreaks().forEach { (text, ends) ->
            ends.filter { it in 1 until text.length && text[it - 1].isLetterOrDigit() && text[it].isLetterOrDigit() }
                .forEach { end ->
                    val from = text.lastIndexOf(' ', end - 1) + 1
                    val to = text.indexOf(' ', end).let { if (it < 0) text.length else it }
                    fail("\"${text.take(40)}\"", "WORD BROKEN", "`${text.substring(from, end)}` / `${text.substring(end, to)}`")
                }
        }

        // 3. The bar: inside the screen, the button inside the bar, and on the TabBar -- or in the scroll
        // above it once the two take more than 60 % of the screen (193's rule).
        val bar = f.tagged(ARRIVAL_BAR_TAG)
        val tab = f.tagged(TAB_BAR_TAG)
        val footer = f.tagged(DATE_FOOTER_BAR_TAG)
        if (bar != null) {
            if (bar.left < -SLACK || bar.right > f.device.width + SLACK) {
                fail("the bar", "OUTSIDE THE SCREEN", "%.1f..%.1f of %d".format(bar.left, bar.right, f.device.width))
            }
            f.tagged(ARRIVAL_CHECK_IN_TAG)?.let { b ->
                if (!contains(bar, b)) fail("Check-in", "OUTSIDE THE BAR", "button %s, bar %s".format(b, bar))
                if (b.right > bar.right - 16f + SLACK) fail("Check-in", "PAST THE BAR'S 16 PADDING", "%.1fdp".format(b.right - (bar.right - 16f)))
            }
            if (tab != null) {
                val shouldPin = bar.height + tab.height <= f.height * 0.6f + SLACK
                val pinned = kotlin.math.abs(bar.bottom - tab.top) < SLACK
                if (shouldPin && !pinned) fail("the bar", "NOT ON THE TABBAR", "bar bottom %.1f, TabBar top %.1f".format(bar.bottom, tab.top))
                if (!shouldPin && pinned) fail("the bar", "PINNED THOUGH IT TAKES OVER 60 %", "%.1f + %.1f of %.1f".format(bar.height, tab.height, f.height))
            }
        }

        // 4. Scrolled to the very end, the last thing in the scroll rests above the footer.
        val scroll = frame.scroll
        if (scroll != null && f.tagged(DATE_CANCEL_TAG) != null) {
            val barPinned = tab != null && footer != null && kotlin.math.abs(footer.bottom - tab.top) < SLACK
            val footerTop = when {
                barPinned -> footer!!.top
                tab != null -> tab.top
                else -> f.height
            }
            f.settle { scroll.dispatchRawDelta(1e7f) }
            val cancel = f.tagged(DATE_CANCEL_TAG)!!
            if (cancel.bottom > footerTop + SLACK) {
                fail("Cancel this date", "HIDDEN BEHIND THE FOOTER", "%.1fdp under it at the end of the scroll".format(cancel.bottom - footerTop))
            }
            val tabNow = f.tagged(TAB_BAR_TAG)
            val barNow = f.tagged(ARRIVAL_BAR_TAG)
            if (!barPinned && barNow != null && tabNow != null && barNow.bottom > tabNow.top + SLACK) {
                fail("the bar, in the scroll", "HIDDEN BEHIND THE TABBAR", "%.1fdp".format(barNow.bottom - tabNow.top))
            }
        }

        // 5. The cards: a column, every one inside the 16 gutters, none on the next (H's card included).
        if (frame.scroll != null) {
            val cards = CARD_TAGS.mapNotNull { t -> f.tagged(t)?.let { t to it } }.sortedBy { it.second.top }
            cards.forEach { (t, r) ->
                if (r.left < 16f - SLACK || r.right > f.device.width - 16f + SLACK) {
                    fail(t, "OUTSIDE THE 16 GUTTERS", "%.1f..%.1f of %d".format(r.left, r.right, f.device.width))
                }
            }
            cards.zipWithNext().forEach { (a, b) ->
                if (b.second.top < a.second.bottom - SLACK) fail("${a.first} / ${b.first}", "CARDS OVERLAP", "%.1fdp".format(a.second.bottom - b.second.top))
            }
        }

        // 6. The sheets: the circle inside the sheet's visible part, and -- the ticket's evidence line --
        // at 375 x 667 at the default type, `Not yet`, the primary and C's error box wholly inside it.
        // The visible part is under the 88 % cap's top and above the bottom of the frame.
        if (sheetUp) {
            // The visible part starts at the LOWER of the 88 % cap's top and the sheet content's own top.
            val capTop = maxOf(f.height * (1f - 0.88f), f.tagged(ARRIVAL_SHEET_TAG)?.top ?: 0f)
            val whole = f.device.width == 375 && f.device.height == 667 && scale == 1f
            listOf(ARRIVAL_CIRCLE_TAG, ARRIVAL_NOT_YET_TAG, ARRIVAL_YES_TAG, ARRIVAL_CLOSE_TAG, ARRIVAL_UNDO_TAG, ARRIVAL_ERROR_TAG).forEach { t ->
                val r = f.tagged(t) ?: return@forEach
                if (r.left < -SLACK || r.right > f.device.width + SLACK) fail(t, "OFF THE SIDE", "%.1f..%.1f".format(r.left, r.right))
                if (whole && (r.top < capTop - SLACK || r.bottom > f.height + SLACK)) {
                    fail(t, "NOT FULLY INSIDE THE SHEET'S VISIBLE PART at 375 x 667", "%.1f..%.1f of %.1f..%.1f".format(r.top, r.bottom, capTop, f.height))
                }
            }
        }

        // 6b. A frame that draws nothing it is meant to would pass every rule above: say so.
        val l = f.label()
        if (l.startsWith("A bar") && bar == null) fail("the bar", "NOT DRAWN", l)
        if ((l.startsWith("B") || l.startsWith("C") || l.startsWith("F")) && f.tagged(ARRIVAL_NOT_YET_TAG) == null) fail("Not yet", "NOT DRAWN", l)
        if (l.startsWith("C") || l.endsWith("+ C")) { if (f.tagged(ARRIVAL_ERROR_TAG) == null) fail("C's error box", "NOT DRAWN", l) }
        if (l.startsWith("D") && f.tagged(ARRIVAL_CLOSE_TAG) == null) fail("Close", "NOT DRAWN", l)
        if (l.startsWith("H") && f.tagged(DATE_ARRIVAL_TAG) == null) fail("the arrival card", "NOT DRAWN", l)
        if (l.startsWith("G") && f.tagged(DATE_GATE_CTA_TAG) == null) fail("the gate's CTA", "NOT DRAWN", l)

        // 7. The gate (G): its date card and CTA whole on the screen, the CTA above the TabBar.
        f.tagged(DATE_GATE_CTA_TAG)?.let { cta ->
            if (cta.bottom > f.height + SLACK) fail("the gate's CTA", "OFF THE BOTTOM", "%.1fdp".format(cta.bottom - f.height))
            if (tab != null && cta.bottom > tab.top + SLACK) fail("the gate's CTA", "BEHIND THE TABBAR", "%.1fdp".format(cta.bottom - tab.top))
        }
        f.tagged(DATE_GATE_CARD_TAG)?.let { card ->
            if (card.left < 16f - SLACK || card.right > f.device.width - 16f + SLACK) fail("the date card", "OUTSIDE THE GUTTERS", "%.1f..%.1f".format(card.left, card.right))
        }
        found
    }

    private fun FitFrame.label() = screen.substringAfter("Arrival/").substringBefore(" @")

    private fun run(device: Device, frame: Frame, scale: Float): List<String> =
        measureFit(device, "Arrival/${frame.label} @$scale", scale, 0, extra = rules(frame, scale), content = frame.content)
            .filterNot { it.advisory }
            .map { "$it" }

    private fun sweep(frames: List<() -> Frame>, scales: List<Float>): List<String> {
        val fresh = mutableListOf<String>()
        for (make in frames) for (scale in scales) phones.forEach { d -> fresh += run(d, make(), scale) }
        return fresh
    }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue(
            "Arrival layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `A, E and H, the Date screen with the bar, are clean on every phone at every type size`() {
        report(sweep(barFrames, scales), "arrival-fit-bar.txt")
    }

    @Test
    fun `B and C over the Date screen are clean on every phone at every type size`() {
        report(sweep(sheetFrames.take(5), scales), "arrival-fit-confirm.txt")
    }

    @Test
    fun `D over the Date screen and F over the chat are clean on every phone at every type size`() {
        report(sweep(listOf(sheetFrames.last()) + chatFrames, scales), "arrival-fit-done-chat.txt")
    }

    @Test
    fun `G, the date gate, is clean on every phone at every type size`() {
        report(sweep(gateFrames, scales), "arrival-fit-gate.txt")
    }

    /** The ticket's own check, on the phone it names, at the default type size (as the ticket asks): B, B with a
     *  note, C, C with a note, D (Close and Undo) and F over the chat. */
    @Test
    fun `at 375 x 667 the guard's buttons and C's error are fully visible`() {
        val phone = DEVICES.first { it.width == 375 && it.height == 667 }
        val fresh = mutableListOf<String>()
        for (make in listOf(sheetFrames[0], sheetFrames[1], sheetFrames[3], sheetFrames[4], sheetFrames[5]) + chatFrames) {
            fresh += run(phone, make(), 1f)
        }
        report(fresh, "arrival-fit-375.txt")
    }

    /**
     * The guard's title breaks as the spec sheet draws it (Chrome with the bundled Lora): at 390 wide, default
     * type, for `Leonie`. Pins `ConfirmTitleMax`, which is tuned to this one string.
     */
    @Test
    fun `B's title breaks into three lines as the spec sheet draws it`() {
        val phone = DEVICES.first { it.width == 390 && it.height == 844 }
        val want = listOf("Have you arrived at the", "meeting location for", "your date with Leonie?")
        val found = measureFit(phone, "Arrival/B title", 1f, 0, extra = { f ->
            val title = ArrivalCopy.CONFIRM_TITLE.format(name)
            val ends = f.lineBreaks().firstOrNull { it.first == title }?.second
            val got = ends?.let { e -> (listOf(0) + e + title.length).zipWithNext { a, b -> title.substring(a, b).trim() } }
            if (got == want) emptyList()
            else listOf(Violation(f.device, f.screen, "B's title", "TITLE BREAKS DIFFER", "$got, want $want"))
        }) { sheet("B", confirm()).content() }.filterNot { it.advisory }
        assertTrue(found.joinToString(System.lineSeparator()), found.isEmpty())
    }

    /** Every Android the app installs on that Robolectric draws -- 11, 12, 12L, 13, and 15 beside the 14
     *  above -- at the default and the largest type: text and drawing differ between versions, layouts must not. */
    @Test
    @Config(sdk = [30, 31, 32, 33, 35])
    fun `the arrival frames are clean on every Android from 11 to 15`() {
        report(sweep(barFrames + sheetFrames + chatFrames + gateFrames, listOf(1f, 2f)), "arrival-fit-sdk.txt")
    }

    private companion object {
        /** The touch floor: 44 on both platforms (9 Nov 2026), as `android-preview-project/CLAUDE.md` states it. */
        const val TAP_DP = 44f
        const val SLACK = 0.75f
        val SHEET_TAGS = listOf(ARRIVAL_NOT_YET_TAG, ARRIVAL_YES_TAG, ARRIVAL_CLOSE_TAG, ARRIVAL_UNDO_TAG)
        val CARD_TAGS = listOf(
            DATE_HERO_TAG, DATE_ARRIVAL_TAG, DATE_TIME_CARD_TAG, DATE_COORDINATE_TAG, DATE_REPORT_TAG, DATE_INTENT_TAG,
            DATE_ACTIONS_TAG, DATE_ABOUT_TAG, DATE_COMMON_TAG, DATE_WORKS_TAG,
        )
    }
}
