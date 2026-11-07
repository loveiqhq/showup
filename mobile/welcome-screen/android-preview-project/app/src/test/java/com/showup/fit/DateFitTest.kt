/*
 * DateFitTest.kt
 * ShowUp · the Date screen on every phone at every type size (SHOWUP-193)
 *
 * The sheet's three states, plus the inputs most likely to break a column of cards: a long venue
 * name, a long address and a long first name at 320 wide and 2x type (they must wrap, never
 * truncate); a date with no venue, no photo, no age, no district and no distance (nothing missing is
 * drawn as a placeholder); tomorrow; and after check-in (the End Date bar).
 *
 * THE 4 NOV 2026 DEVICE AUDIT widened it to every phone and every type size there is: the shared
 * list plus [EDGE_DEVICES] (Display Zoom, split screen, unfolded foldables), all seven Android font
 * steps, Android 11 and 15 beside 14 -- and added the Date screen's own rules to the shared ones:
 * every touch area 48 dp both ways and none on another, the cards in a column that never overlaps,
 * and the end of the scroll resting above the footer, scrolled there and measured.
 */
package com.showup.fit

import androidx.compose.foundation.ScrollState
import com.showup.date.ActiveDate
import com.showup.date.DATE_ABOUT_TAG
import com.showup.date.DATE_ACTIONS_TAG
import com.showup.date.DATE_CANCEL_TAG
import com.showup.date.DATE_COMMON_TAG
import com.showup.date.DATE_COORDINATE_TAG
import com.showup.date.DATE_FOOTER_BAR_TAG
import com.showup.date.DATE_HERO_TAG
import com.showup.date.DATE_INTENT_TAG
import com.showup.date.DATE_REPORT_TAG
import com.showup.date.DATE_TIME_CARD_TAG
import com.showup.date.DATE_WORKS_TAG
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.DateUnread
import com.showup.designsystem.TAB_BAR_TAG
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
class DateFitTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val stateA = Instant.parse("2026-11-03T15:00:00Z")
    /** Every Android font size step, 85 % to Android 14's 200 %. */
    private val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    private val phones = DEVICES + EDGE_DEVICES

    private val date = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = listOf("coffee_chat", "nature_walk", "playful", "deep_talk"),
        sharedInterests = listOf("reading", "hiking", "brunch"),
        unread = null, checkedIn = false,
    )
    private val unread = DateUnread(
        2, "running_late",
        "Running about 10 min late — sorry! Almost there, the tram stopped twice and I am nearly at the door.",
        unlock,
    )

    private val frames: List<Pair<String, DateUi>> = listOf(
        "A locked" to DateUi(date, stateA, loading = false),
        "B open" to DateUi(date, unlock, loading = false),
        "C unread" to DateUi(date.copy(unread = unread), unlock.plusSeconds(60), loading = false),
        "long names" to DateUi(
            date.copy(
                place = DatePlace("The Very Long Name Café and Wine Bar Berlin", "Am Kupfergraben 6a, Hinterhaus, 3. Etage, 10117 Berlin-Mitte"),
                match = date.match.copy(name = "Maximiliana-Konstantina", district = "Friedrichshain-Kreuzberg"),
            ),
            unlock.plusSeconds(60).let { it },
            loading = false,
        ),
        "sparse" to DateUi(
            date.copy(
                place = null,
                match = DateMatch("u2", "Leonie", null, null, null, null, 0),
                motivationThem = "casual", sharedVibes = emptyList(), sharedInterests = emptyList(),
            ),
            stateA,
            loading = false,
        ),
        "tomorrow" to DateUi(date.copy(meetDay = "tomorrow"), Instant.parse("2026-11-02T22:00:00Z"), loading = false),
        "checked in" to DateUi(date.copy(checkedIn = true), unlock, loading = false),
        "failed first read" to DateUi(now = stateA, loading = false, failed = com.showup.api.CallFailure.Network),
    )

    /** The Date screen's own rules, run in each render after the shared ones. */
    private fun dateRules(scroll: ScrollState?, dateOwned: Boolean = true): (FitFrame) -> List<Violation> = { f ->
        val found = mutableListOf<Violation>()
        fun fail(element: String, problem: String, detail: String) {
            found += Violation(f.device, f.screen, element, problem, detail)
        }
        // Where the pinned footer starts, read before anything scrolls: the bar when it sits on the
        // TabBar, the TabBar when the bar has moved into the scroll (large type on a short screen).
        val tab = f.tagged(TAB_BAR_TAG)
        val bar = f.tagged(DATE_FOOTER_BAR_TAG)
        val barPinned = tab != null && bar != null && kotlin.math.abs(bar.bottom - tab.top) < SLACK
        val footerTop = when {
            barPinned -> bar!!.top
            tab != null -> tab.top
            else -> f.height
        }

        // 1. Every touch area 48 dp both ways -- Android's floor (this module's CLAUDE.md), not the
        // shared harness's 44 -- and no two partly on each other, where a tap could land on the wrong
        // one. One inside another is a nested control and fine. A control in the scroll passing under
        // the pinned footer is not an overlap: the footer is on top and the control scrolls clear.
        val taps = f.tapTargets()
        if (dateOwned) taps.forEach { (name, r) ->
            if (r.width < ANDROID_TAP_DP - SLACK || r.height < ANDROID_TAP_DP - SLACK) {
                fail(name, "TAP AREA UNDER 48DP", "%.1f x %.1fdp".format(r.width, r.height))
            }
        }
        fun contains(a: FitRect, b: FitRect) =
            a.left <= b.left + SLACK && a.right >= b.right - SLACK && a.top <= b.top + SLACK && a.bottom >= b.bottom - SLACK
        for (i in taps.indices) for (j in i + 1 until taps.size) {
            val (a, ra, sa) = taps[i]
            val (b, rb, sb) = taps[j]
            if (sa != sb || contains(ra, rb) || contains(rb, ra)) continue
            val w = minOf(ra.right, rb.right) - maxOf(ra.left, rb.left)
            val h = minOf(ra.bottom, rb.bottom) - maxOf(ra.top, rb.top)
            if (w > SLACK && h > SLACK) fail("$a / $b", "TAP AREAS OVERLAP", "%.1f x %.1fdp".format(w, h))
        }

        // 1b. No word broken between two letters: Compose breaks a word wider than its line wherever it
        // must, and nothing above sees it -- the text is all drawn, none of it clipped. CSS keeps a
        // word whole; where one cannot fit, `fitWords` shrinks it (the 4 Nov 2026 device audit).
        if (dateOwned) f.lineBreaks().forEach { (text, ends) ->
            ends.filter { it in 1 until text.length && text[it - 1].isLetterOrDigit() && text[it].isLetterOrDigit() }
                .forEach { end ->
                    val from = text.lastIndexOf(' ', end - 1) + 1
                    val to = text.indexOf(' ', end).let { if (it < 0) text.length else it }
                    fail("\"${text.take(40)}\"", "WORD BROKEN", "`${text.substring(from, end)}` / `${text.substring(end, to)}`")
                }
        }

        // 2. The cards: a column, every one inside the 16 gutters, none on the next.
        val cards = CARD_TAGS.mapNotNull { t -> f.tagged(t)?.let { t to it } }.sortedBy { it.second.top }
        cards.forEach { (t, r) ->
            if (r.left < 16f - SLACK || r.right > f.device.width - 16f + SLACK) {
                fail(t, "OUTSIDE THE 16 GUTTERS", "%.1f..%.1f of %d".format(r.left, r.right, f.device.width))
            }
        }
        cards.zipWithNext().forEach { (a, b) ->
            if (b.second.top < a.second.bottom - SLACK) {
                fail("${a.first} / ${b.first}", "CARDS OVERLAP", "%.1fdp".format(a.second.bottom - b.second.top))
            }
        }

        // 3. Scrolled to the very end, the last thing in the scroll rests above the footer: nothing is
        // reachable only behind it. And a bar that moved into the scroll ends above the TabBar.
        if (scroll != null && f.tagged(DATE_CANCEL_TAG) != null) {
            f.settle { scroll.dispatchRawDelta(1e7f) }
            val cancel = f.tagged(DATE_CANCEL_TAG)!!
            if (cancel.bottom > footerTop + SLACK) {
                fail("Cancel this date", "HIDDEN BEHIND THE FOOTER",
                    "%.1fdp under it at the end of the scroll".format(cancel.bottom - footerTop))
            }
            val tabNow = f.tagged(TAB_BAR_TAG)
            val barNow = f.tagged(DATE_FOOTER_BAR_TAG)
            if (!barPinned && barNow != null && tabNow != null && barNow.bottom > tabNow.top + SLACK) {
                fail("the footer bar, in the scroll", "HIDDEN BEHIND THE TABBAR", "%.1fdp".format(barNow.bottom - tabNow.top))
            }
        }
        found
    }

    private fun date(ui: DateUi, device: Device, label: String, scale: Float): List<String> {
        val scroll = ScrollState(0)
        return measureFit(device, "Date/$label @$scale", scale, 0, extra = dateRules(scroll)) {
            DateScreen(ui, zone = berlin, scroll = scroll)
        }
            .filterNot { it.advisory }
            .map { "$it" }
            // "Only the C message preview is clamped (2 lines)" -- the ticket's own exception.
            .filterNot { it.contains("“Running about") && it.contains("over its maxLines") }
    }

    /**
     * The match's profile, read-only (12): Search's screen with the back pill and the locked bar. Held to
     * the shared rules and to "no two touch areas on each other", not to the Date screen's 48 or its
     * whole words: its back pill (44), voice button (46) and the section values that break inside a word
     * at large type (`Flexitari` / `an`, `SMOKIN` / `G`) are Search's, shared with the Search tab, and
     * Compose already widens a touch under 48 when nothing else is hit -- reported to Search's owner (the
     * 4 Nov 2026 device audit), not changed here.
     */
    private fun profile(device: Device, scale: Float): List<String> =
        measureFit(device, "Date/read-only profile @$scale", scale, 0, extra = dateRules(null, dateOwned = false)) {
            com.showup.search.SearchProfileScreen(
                state = com.showup.search.SearchUiState(loading = false, queue = listOf(com.showup.search.SearchSamples.FULL)),
                zone = berlin, readOnly = true, backLabel = com.showup.date.DateCopy.PROFILE_BACK,
                disabledTabs = com.showup.date.DATE_LOCKED_TABS, disabledHint = com.showup.date.DateCopy.TAB_HINT,
            )
        }.filterNot { it.advisory }.map { "$it" }

    private fun report(findings: List<String>, file: String) {
        java.io.File("build/$file").writeText(findings.joinToString("\n"))
        assertTrue(
            "Date screen layout problems (${findings.size}):\n" + findings.take(60).joinToString("\n") { "  $it" },
            findings.isEmpty(),
        )
    }

    @Test
    fun `every state of the Date screen is clean on every phone at every type size`() {
        val fresh = mutableListOf<String>()
        for ((name, ui) in frames) for (scale in scales) phones.forEach { d -> fresh += date(ui, d, name, scale) }
        for (scale in scales) phones.forEach { d -> fresh += profile(d, scale) }
        report(fresh, "date-fit-findings.txt")
    }

    /** The oldest Android the app installs on and the newest Robolectric draws, at the default and the
     *  largest type: text and drawing differ between Android versions, layouts must not. */
    @Test
    @Config(sdk = [30, 35])
    fun `the Date screen is clean on Android 11 and Android 15 too`() {
        val fresh = mutableListOf<String>()
        for ((name, ui) in frames) for (scale in listOf(1f, 2f)) phones.forEach { d -> fresh += date(ui, d, name, scale) }
        for (scale in listOf(1f, 2f)) phones.forEach { d -> fresh += profile(d, scale) }
        report(fresh, "date-fit-findings-sdk.txt")
    }

    private companion object {
        /** Android's touch floor: Material's 48, as `android-preview-project/CLAUDE.md` states it. */
        const val ANDROID_TAP_DP = 48f
        const val SLACK = 0.75f
        val CARD_TAGS = listOf(
            DATE_HERO_TAG, DATE_TIME_CARD_TAG, DATE_COORDINATE_TAG, DATE_REPORT_TAG, DATE_INTENT_TAG,
            DATE_ACTIONS_TAG, DATE_ABOUT_TAG, DATE_COMMON_TAG, DATE_WORKS_TAG,
        )
    }
}
