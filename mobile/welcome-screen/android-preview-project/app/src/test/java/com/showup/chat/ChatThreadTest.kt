/*
 * ChatThreadTest.kt
 * ShowUp · the thread's order and the system lines' copy (SHOWUP-196, Date 05 R9)
 */
package com.showup.chat

import com.showup.change.ChangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ChatThreadTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val s = ThreadSamples

    private fun keys(items: List<ThreadItem>) = items.map { it.key }

    @Test
    fun `messages, cards and system lines interleave by time, oldest first`() {
        val items = buildThread(
            messages = listOf(s.message("a", "17:00", false), s.message("b", "17:15", true), s.message("c", "17:40", false)),
            suggestions = listOf(s.REPLACED, s.THEIRS_TIME.copy(status = ChangeStatus.Agreed, answeredAt = s.AGREED_TIME.answeredAt)),
            zone = berlin,
        )
        // card t0 at 17:05, card t1 at 17:10, message b 17:15, its line at 17:20, message c 17:40.
        assertEquals(listOf("message:a", "card:t0", "card:t1", "message:b", "system:t1", "message:c"), keys(items))
    }

    @Test
    fun `an agreed line sits at answeredAt and an expired one at expiredAt, the card keeps createdAt`() {
        val items = buildThread(listOf(s.message("x", "17:32", false)), listOf(s.EXPIRED, s.AGREED_PLACE), berlin)
        // card t2 17:10, card p2 17:12, line p2 17:25, line t2 17:30, message 17:32.
        assertEquals(listOf("card:t2", "card:p2", "system:p2", "system:t2", "message:x"), keys(items))
    }

    @Test
    fun `a replaced card stays in place and leaves no system line`() {
        val items = buildThread(emptyList(), listOf(s.REPLACED, s.THEIRS_TIME), berlin)
        assertEquals(listOf("card:t0", "card:t1"), keys(items))
        assertNull(systemLineFor(s.REPLACED, berlin))
        assertNull(systemLineFor(s.THEIRS_TIME, berlin))
    }

    @Test
    fun `messages that failed to send stay at the foot, after every card`() {
        val failed = s.message("f", "17:00", true).copy(failed = true)
        val items = buildThread(listOf(failed, s.message("a", "17:01", false)), listOf(s.THEIRS_TIME), berlin)
        assertEquals(listOf("message:a", "card:t1", "message:f"), keys(items))
    }

    @Test
    fun `an agreed time reads Meet time is now, from the suggested time in local HH mm`() {
        val line = systemLineFor(s.AGREED_TIME, berlin)!!
        assertEquals("Meet time is now 18:30", line.text)
        assertEquals(true, line.agreed)
    }

    @Test
    fun `an agreed place reads Meeting place is now with the venue`() {
        assertEquals("Meeting place is now Bar Lenz", systemLineFor(s.AGREED_PLACE, berlin)!!.text)
    }

    @Test
    fun `an expiry names the deadline and the snapshot that stays, in the muted tone`() {
        val line = systemLineFor(s.EXPIRED, berlin)!!
        assertEquals("No answer by 17:30 — 18:00 at Cafe Krone stays.", line.text)
        assertEquals(false, line.agreed)
    }

    @Test
    fun `an expiry without its snapshot posts no line rather than a wrong one`() {
        assertNull(systemLineFor(s.EXPIRED.copy(expiredBindingPlaceName = null), berlin))
    }
}
