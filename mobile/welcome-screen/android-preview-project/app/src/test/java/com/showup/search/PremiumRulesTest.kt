/*
 * PremiumRulesTest.kt
 * ShowUp · the Premium grid's and the Likes tab's rules, with no UI (SHOWUP-185, 186)
 */
package com.showup.search

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumRulesTest {

    @Test
    fun `each reason carries the tickets' exact lines, with no gendered pronoun`() {
        val m = CloseReason.Matched
        assertEquals("Too late", m.pill)
        assertEquals("Has a date with someone else.", m.gridLine)
        assertEquals("Too late — Anna has a date with someone else.", m.likesHead("Anna"))
        assertEquals("A like closes as soon as either of you matches. Confirm sooner next time.", m.likesBody)
        assertEquals("Anna. Too late — Anna has a date with someone else.", m.gridLabel("Anna"))
        assertEquals("We're really sorry, Anna confirmed a date ", m.sorryHeadLead("Anna"))
        assertEquals("just before you did.", m.sorryEmphasis)
        assertEquals("A like closes the moment either of you matches, and this time Anna's match came first.", m.sorryBody("Anna"))

        val n = CloseReason.NoOverlap
        assertEquals("Out of time", n.pill)
        assertEquals("No time left to meet.", n.gridLine)
        assertEquals("Out of time — you and Anna have no time left to meet.", n.likesHead("Anna"))
        assertEquals("A like closes when your Check-Ins no longer overlap. Confirm sooner next time.", n.likesBody)
        assertEquals("Anna. Out of time — you and Anna have no time left to meet.", n.gridLabel("Anna"))
        assertEquals("Sorry, you and Anna ", n.sorryHeadLead("Anna"))
        assertEquals("ran out of time.", n.sorryEmphasis)
        assertEquals(
            "We're sorry this one didn't work out. Your Check-In and Anna's no longer share any time, so the like has closed.",
            n.sorryBody("Anna"),
        )
        for (r in CloseReason.entries) {
            val all = listOf(r.likesHead("X"), r.likesBody, r.sorryBody("X"), r.gridLine).joinToString(" ")
            for (pronoun in listOf(" he ", " she ", " his ", " her ", " him ")) {
                assertEquals("no '$pronoun' in ${r.name}", false, all.lowercase().contains(pronoun))
            }
        }
    }

    @Test
    fun `a reason this app does not know is no reason`() {
        assertEquals(CloseReason.Matched, CloseReason.of("matched"))
        assertEquals(CloseReason.NoOverlap, CloseReason.of("no_overlap"))
        assertNull(CloseReason.of("expired"))
        assertNull(CloseReason.of(null))
    }

    @Test
    fun `a card's accessible name carries its mark, never colour alone`() {
        assertEquals("Open Leonie's profile", gridCardLabel("Leonie", notNow = false, leaving = null))
        assertEquals("Open Leonie's profile, liked", gridCardLabel("Leonie", notNow = false, leaving = Leave.Liked))
        assertEquals("Open Leonie's profile, not now", gridCardLabel("Leonie", notNow = true, leaving = null))
        assertEquals("Open Leonie's profile, not interested", gridCardLabel("Leonie", notNow = false, leaving = Leave.NotInterested))
        assertEquals("Open Anna's profile", likesPhotoLabel("Anna", notNow = false))
        assertEquals("Open Anna's profile, not now", likesPhotoLabel("Anna", notNow = true))
    }

    @Test
    fun `the grid card's meta line is the meet time and the rounded distance, never an exact one`() {
        val p = SearchSamples.GRID[0].profile
        assertEquals("19:00 today · ~1 km", gridMetaLine(p, SearchSamples.ZONE))
        assertEquals("18:00 tomorrow · < 1 km", gridMetaLine(SearchSamples.GRID[2].profile, SearchSamples.ZONE))
        assertEquals("· ~4 km", likesDistance(4))
        assertEquals("· < 1 km", likesDistance(0))
    }

    @Test
    fun `the message is quoted in straight double quotes, as written`() {
        assertEquals("\"Coffee at Bonanza?\"", quotedMessage("Coffee at Bonanza?"))
        assertEquals("Anna's like declined.", undoLine("Anna"))
    }

    @Test
    fun `the fly-out lands at the visible list's centre x and 72 percent of its height`() {
        // A 100 x 200 card at (20, 300) in a list 0..400 wide, 100..900 tall.
        val (dx, dy) = flyOutTarget(20f, 300f, 100f, 200f, 0f, 100f, 400f, 800f)
        assertEquals(200f - 70f, dx, 0.001f)
        assertEquals(100f + 800f * 0.72f - 400f, dy, 0.001f)
        assertEquals(0.12f, FLY_OUT_SCALE)
    }

    @Test
    fun `a re-check status is read tolerantly -- unknown or incomplete is no change`() {
        val at = Instant.parse("2026-10-22T17:00:00Z")
        assertEquals(CardStatus.Available(at, "today"), readStatus("available", at, "today"))
        assertNull(readStatus("available", null, "today"))
        assertNull(readStatus("available", at, null))
        assertEquals(CardStatus.Closed(CloseReason.Matched), readStatus("matched", null, null))
        assertEquals(CardStatus.Closed(CloseReason.NoOverlap), readStatus("no_overlap", null, null))
        assertNull(readStatus("paused", null, null))
    }

    @Test
    fun `a confirm's answer -- a match wins, anything else is refused, never claiming a match that did not happen`() {
        assertEquals(ConfirmAnswer.Won, confirmAnswer(matched = true, closed = null))
        assertEquals(ConfirmAnswer.Refused(CloseReason.Matched), confirmAnswer(matched = false, closed = "matched"))
        assertEquals(ConfirmAnswer.Refused(CloseReason.NoOverlap), confirmAnswer(matched = false, closed = "no_overlap"))
        assertEquals(ConfirmAnswer.Refused(CloseReason.NoOverlap), confirmAnswer(matched = false, closed = null))
    }
}
