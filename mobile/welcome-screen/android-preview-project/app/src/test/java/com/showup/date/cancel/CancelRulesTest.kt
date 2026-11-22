/*
 * CancelRulesTest.kt
 * ShowUp · Date 07's plain rules: Date 05's gate read for the guard sheet, `Message {name} instead`, the sent label,
 * the link's visibility and where an ended cancel lands (SHOWUP-198, R12)
 */
package com.showup.date.cancel

import com.showup.change.ChangeKind
import com.showup.change.DateChange
import com.showup.change.changeKindAvailable
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CancelRulesTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-21T18:00:00Z")
    private val cutoff = meet.minusSeconds(30 * 60)

    private fun change(allowed: Boolean = true, slots: List<Instant> = listOf(meet.plusSeconds(900))) =
        DateChange(cutoffAt = cutoff, allowed = allowed, slots = slots, timeWas = null, placeWas = null, pending = emptyList())

    private fun date(checkedIn: Boolean = false, endsEnabled: Boolean = true, chatOpen: Boolean = false, place: DatePlace? = DatePlace("Cafe Krone", null)) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = place, match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null, endsEnabled = endsEnabled,
    )

    // ── changeClosed: Date 05's gate, the same function ─────────────────────────────────────────

    @Test
    fun `changeClosed is false before the cutoff with no check-in`() {
        assertFalse(changeClosed(change(), cutoff.minusSeconds(1)))
    }

    @Test
    fun `changeClosed is true from the cutoff, to the second`() {
        assertTrue(changeClosed(change(), cutoff))
        assertTrue(changeClosed(change(), meet.plusSeconds(60)))
    }

    @Test
    fun `changeClosed is true once either person has checked in, before the cutoff`() {
        assertTrue(changeClosed(change(allowed = false), cutoff.minusSeconds(3600)))
    }

    @Test
    fun `changeClosed is false when the server sent no change block, and there are no tiles`() {
        // Nothing was ever offered, so I (and its Message button) must not appear: the chat may be locked.
        assertFalse(changeClosed(null, cutoff.minusSeconds(3600)))
        assertEquals(emptyList<ChangeKind>(), cancelTiles(null, cutoff.minusSeconds(3600)))
        assertFalse(showMessage(chatOpen = false, changeClosed = changeClosed(null, cutoff)))
        assertTrue(showMessage(chatOpen = true, changeClosed = changeClosed(null, cutoff)))
    }

    @Test
    fun `the body line needs a tile or a sent request, and never shows in I`() {
        assertTrue(solutionBodyShown(listOf(ChangeKind.Place), sent = false, changeClosed = false))
        assertTrue(solutionBodyShown(emptyList(), sent = true, changeClosed = false))
        assertFalse(solutionBodyShown(emptyList(), sent = false, changeClosed = false))
        assertFalse(solutionBodyShown(listOf(ChangeKind.Place), sent = true, changeClosed = true))
    }

    @Test
    fun `changeClosed always agrees with Date 05's tiles - both are hidden exactly when it is true`() {
        val times = listOf(cutoff.minusSeconds(7200), cutoff.minusSeconds(1), cutoff, meet)
        for (allowed in listOf(true, false)) for (now in times) {
            val c = change(allowed)
            val anyTile = changeKindAvailable(c, ChangeKind.Time, now) || changeKindAvailable(c, ChangeKind.Place, now)
            assertEquals("allowed=$allowed now=$now", !anyTile, changeClosed(c, now))
        }
    }

    // ── the tiles ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `both tiles show while the window is open and a later slot exists`() {
        assertEquals(listOf(ChangeKind.Time, ChangeKind.Place), cancelTiles(change(), cutoff.minusSeconds(60)))
    }

    @Test
    fun `Change meet time hides with no later slot and Change location stays`() {
        assertEquals(listOf(ChangeKind.Place), cancelTiles(change(slots = emptyList()), cutoff.minusSeconds(60)))
    }

    @Test
    fun `no tile shows once the change is closed`() {
        assertEquals(emptyList<ChangeKind>(), cancelTiles(change(), cutoff))
    }

    // ── Message instead ────────────────────────────────────────────────────────────────────

    @Test
    fun `showMessage is chat open or change closed`() {
        assertFalse(showMessage(chatOpen = false, changeClosed = false))
        assertTrue(showMessage(chatOpen = true, changeClosed = false))
        assertTrue(showMessage(chatOpen = false, changeClosed = true))
        assertTrue(showMessage(chatOpen = true, changeClosed = true))
    }

    @Test
    fun `the match having checked in before meet minus 1h30 shows C, the chat being open`() {
        // The server opens the chat on either check-in, and Date 05 closes the change: C, not B.
        val d = date(chatOpen = true).copy(change = change(allowed = false))
        val closed = changeClosed(d.change, meet.minusSeconds(95 * 60))
        assertTrue(showMessage(d.coordinateOpen, closed))
    }

    // ── sentLabel ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `sentLabel for a time is the clock time on the phone, for a place its name`() {
        assertEquals("20:00", sentLabel(ChangeKind.Time, Instant.parse("2026-11-21T19:00:00Z"), null, berlin))
        assertEquals("Bar Lenz", sentLabel(ChangeKind.Place, null, "Bar Lenz", berlin))
    }

    @Test
    fun `sentLabel is empty when nothing was chosen`() {
        assertEquals("", sentLabel(ChangeKind.Time, null, null, berlin))
        assertEquals("", sentLabel(ChangeKind.Place, null, null, berlin))
    }

    // ── the link ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the link shows while active, not checked in and the ends are on`() {
        assertTrue(cancelLinkShown(date()))
    }

    @Test
    fun `the link is gone after this user's own check-in`() {
        assertFalse(cancelLinkShown(date(checkedIn = true)))
    }

    @Test
    fun `the match's check-in does not hide the link`() {
        val d = date().copy(arrival = com.showup.date.DateArrival(meet.minusSeconds(600)))
        assertTrue(cancelLinkShown(d))
    }

    @Test
    fun `the link is hidden while the end routes are off`() {
        assertFalse(cancelLinkShown(date(endsEnabled = false)))
    }

    // ── the pill ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pill is the chat header's line - day, time and place`() {
        assertEquals("Today · 19:00 · Cafe Krone", guardPill(date(), berlin))
        assertEquals("Today · 19:00", guardPill(date(place = null), berlin))
    }

    // ── where a cancel lands ─────────────────────────────────────────────────────────────────

    @Test
    fun `where an end routes - the chat's G, the other's cancel H, everything else lands`() {
        fun o(reason: OutcomeReason, byYou: Boolean) = EndedOutcome("d1", "Leonie", byYou, reason = reason)
        assertEquals(EndedRoute.DateEnded, endedRoute(o(OutcomeReason.Chat, byYou = false)))
        assertEquals(EndedRoute.DateEnded, endedRoute(o(OutcomeReason.Chat, byYou = true)))
        assertEquals(EndedRoute.PartnerCancelled, endedRoute(o(OutcomeReason.Cancelled, byYou = false)))
        assertEquals(EndedRoute.Land, endedRoute(o(OutcomeReason.Cancelled, byYou = true)))
        // A Date 06 end reaching the person through the chat never shows Date 02's screen.
        for (reason in listOf(OutcomeReason.Met, OutcomeReason.PartnerNoShow, OutcomeReason.Uncomfortable)) {
            assertEquals(EndedRoute.Land, endedRoute(o(reason, byYou = false)))
            assertEquals(EndedRoute.Land, endedRoute(o(reason, byYou = true)))
        }
    }

    @Test
    fun `a cancel by the other person is H, one by you or any other reason is not`() {
        fun o(reason: OutcomeReason, byYou: Boolean) = EndedOutcome("d1", "Leonie", byYou, reason = reason)
        assertTrue(cancelledOnYou(o(OutcomeReason.Cancelled, byYou = false)))
        assertFalse(cancelledOnYou(o(OutcomeReason.Cancelled, byYou = true)))
        assertFalse(cancelledOnYou(o(OutcomeReason.Chat, byYou = false)))
        assertFalse(cancelledOnYou(o(OutcomeReason.Met, byYou = false)))
    }
}
