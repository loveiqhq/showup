/*
 * DateEndRulesTest.kt
 * ShowUp · Date 06's plain rules (SHOWUP-197, R9, R11)
 */
package com.showup.date.end

import com.showup.arrival.ArrivalEntry
import com.showup.arrival.minutesVsStart
import com.showup.date.EndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DateEndRulesTest {
    private val meet = Instant.parse("2026-11-21T18:00:00Z")

    @Test
    fun `pastMeet is false one second before the agreed time and true at it`() {
        assertFalse(pastMeet(meet, meet.minusSeconds(1)))
        assertTrue(pastMeet(meet, meet))
        assertTrue(pastMeet(meet, meet.plusSeconds(1)))
    }

    @Test
    fun `minutes vs start is negative before the meet time and positive after`() {
        assertEquals(-10, minutesVsStart(meet, meet.minusSeconds(600)))
        assertEquals(0, minutesVsStart(meet, meet))
        assertEquals(12, minutesVsStart(meet, meet.plusSeconds(12 * 60 + 5)))
    }

    @Test
    fun `the gate leads no-show to the guide and uncomfortable to its step, and We met to C`() {
        assertEquals(EndStep.Guide, endStepAfterGate(EndOption.NoShow))
        assertEquals(EndStep.Uncomfortable, endStepAfterGate(EndOption.Uncomfortable))
        assertEquals(EndStep.Met, endStepAfterGate(EndOption.Met))
    }

    @Test
    fun `only the two problem routes need a check-in`() {
        assertFalse(optionNeedsCheckIn(EndOption.Met))
        assertTrue(optionNeedsCheckIn(EndOption.NoShow))
        assertTrue(optionNeedsCheckIn(EndOption.Uncomfortable))
    }

    @Test
    fun `each ending step ends the date with its reason, the menus and the guide end nothing`() {
        assertEquals(EndReason.Met, endReasonFor(EndStep.Met))
        assertEquals(EndReason.PartnerNoShow, endReasonFor(EndStep.NoShow))
        assertEquals(EndReason.Uncomfortable, endReasonFor(EndStep.Uncomfortable))
        assertNull(endReasonFor(EndStep.Menu))
        assertNull(endReasonFor(EndStep.Guide))
    }

    @Test
    fun `the guide's end button is disabled and names the time before the meet time, live after it`() {
        val before = guideEnd(pastMeet = false, meetTime = "19:00")
        assertFalse(before.enabled)
        assertEquals("You can end the date from 19:00", before.label)
        assertTrue(before.clock)
        val after = guideEnd(pastMeet = true, meetTime = "19:00")
        assertTrue(after.enabled)
        assertEquals("It’s been 10+ minutes — end date", after.label)
        assertFalse(after.clock)
    }

    @Test
    fun `the scrim closes the sheet on a menu and on C, and steps back on the guide, G and I`() {
        assertTrue(scrimClosesSheet(EndStep.Menu))
        assertTrue(scrimClosesSheet(EndStep.Met))
        assertFalse(scrimClosesSheet(EndStep.Guide))
        assertFalse(scrimClosesSheet(EndStep.NoShow))
        assertFalse(scrimClosesSheet(EndStep.Uncomfortable))
    }

    @Test
    fun `landing runs once per ended date`() {
        val once = LandedOnce()
        assertTrue(once.first("d1"))
        assertFalse(once.first("d1"))
        assertTrue(once.first("d2"))
    }

    @Test
    fun `Message is offered only while the chat is open`() {
        assertTrue(messageShown(chatOpen = true))
        assertFalse(messageShown(chatOpen = false))
    }

    @Test
    fun `the gate's entry is wrapup from the wrap-up sheet and report from the report sheet`() {
        assertEquals(ArrivalEntry.Wrapup, gateEntryFor(EndRoute.Wrap))
        assertEquals(ArrivalEntry.Report, gateEntryFor(EndRoute.Report))
        assertEquals("wrapup", ArrivalEntry.Wrapup.wire)
    }
}
