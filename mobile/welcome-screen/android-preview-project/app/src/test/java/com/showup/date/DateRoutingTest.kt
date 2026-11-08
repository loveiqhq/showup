/*
 * DateRoutingTest.kt
 * ShowUp · where a finished profile opens while a date is active (SHOWUP-193)
 *
 * "Signed in → profile complete → active date? → Date screen → otherwise the existing Check-In /
 * Search resume." The date wins over the Check-In gate, because an active date blocks Search anyway;
 * a read that failed is no date, so the old rule stands.
 */
package com.showup.date

import com.showup.api.CallFailure
import com.showup.api.Outcome
import com.showup.welcome.FlowScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DateRoutingTest {
    private val date = ActiveDate(
        id = "d1", meetAt = Instant.parse("2026-11-03T18:00:00Z"), meetDay = "today",
        unlockAt = Instant.parse("2026-11-03T16:30:00Z"), coordinateOpen = false,
        place = null, match = DateMatch("u2", "Leonie", 28, null, null, null, 0),
        motivationYou = null, motivationThem = null,
        sharedVibes = emptyList(), sharedInterests = emptyList(), unread = null, checkedIn = false, chatTutorialSeen = false,
    )
    private val active: Outcome<DateRead> = Outcome.Ok(DateRead.Active(date))
    private val none: Outcome<DateRead> = Outcome.Ok(DateRead.None)
    private val failed: Outcome<DateRead> = Outcome.Failed(CallFailure.Network)

    @Test
    fun `an active date opens the Date screen before any Check-In target`() {
        assertEquals(FlowScreen.Date, launchTarget(active, checkInActive = true))
        assertEquals(FlowScreen.Date, launchTarget(active, checkInActive = false))
        assertEquals(FlowScreen.Date, launchTarget(active, checkInActive = null))
    }

    @Test
    fun `no date keeps the Search tab's rule`() {
        assertEquals(FlowScreen.CheckInSearch, launchTarget(none, checkInActive = true))
        assertEquals(FlowScreen.CheckInGate, launchTarget(none, checkInActive = false))
        assertEquals("an unknown Check-In is the gate", FlowScreen.CheckInGate, launchTarget(none, checkInActive = null))
    }

    @Test
    fun `a date ended in the chat and not yet seen opens on the Date screen, which shows the outcome`() {
        val ended: Outcome<DateRead> = Outcome.Ok(DateRead.Ended(EndedInChat("d1", "Leonie", endedByYou = false)))
        assertEquals(FlowScreen.Date, launchTarget(ended, checkInActive = true))
        assertEquals(FlowScreen.Date, launchTarget(ended, checkInActive = null))
        assertEquals(EndedInChat("d1", "Leonie", endedByYou = false), ended.endedOutcome())
        assertEquals(null, active.endedOutcome())
    }

    @Test
    fun `a failed date read is no date`() {
        assertEquals(FlowScreen.CheckInSearch, launchTarget(failed, checkInActive = true))
        assertEquals(FlowScreen.CheckInGate, launchTarget(failed, checkInActive = null))
    }

    @Test
    fun `the Date screen is not a Check-In screen and has no system back`() {
        assertFalse("the Check-In foreground re-read must not cover it", FlowScreen.Date.isCheckIn)
        assertFalse(FlowScreen.Date.hasSystemBack)
        assertTrue("a match from Search reads forward", FlowScreen.Date > FlowScreen.CheckInSearch)
    }
}
