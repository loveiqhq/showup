/*
 * DateFitTest.kt
 * ShowUp · the Date screen on every phone at every type size (SHOWUP-193)
 *
 * The sheet's three states, plus the inputs most likely to break a column of cards: a long venue
 * name, a long address and a long first name at 320 wide and 2x type (they must wrap, never
 * truncate); a date with no venue, no photo, no age, no district and no distance (nothing missing is
 * drawn as a placeholder); tomorrow; and after check-in (the End Date bar).
 */
package com.showup.fit

import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.DateUnread
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
@Config(sdk = [34], qualifiers = "w600dp-h1200dp-xhdpi")
class DateFitTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val stateA = Instant.parse("2026-11-03T15:00:00Z")
    private val scales = listOf(1f, 1.3f, 2f)

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
    )

    @Test
    fun `every state of the Date screen is clean on every phone at every type size`() {
        val fresh = mutableListOf<String>()
        for ((name, ui) in frames) for (scale in scales) DEVICES.forEach { d ->
            measureFit(d, "Date/$name @$scale", fontScale = scale) { DateScreen(ui, zone = berlin) }
                .filterNot { it.advisory }
                .map { "$it" }
                // "Only the C message preview is clamped (2 lines)" -- the ticket's own exception.
                .filterNot { it.contains("“Running about") && it.contains("over its maxLines") }
                .forEach { fresh += it }
        }
        java.io.File("build/date-fit-findings.txt").writeText(fresh.joinToString("\n"))
        assertTrue("Date screen layout problems (${fresh.size}):\n" + fresh.take(60).joinToString("\n") { "  $it" }, fresh.isEmpty())
    }
}
