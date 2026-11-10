/*
 * ArrivalRulesTest.kt
 * ShowUp · the arrival check-in's plain rules (SHOWUP-195)
 */
package com.showup.arrival

import com.showup.chat.ChatReason
import com.showup.chat.SHOWN_REASONS
import com.showup.date.DateArrival
import com.showup.date.DateState
import com.showup.date.hoisted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ArrivalRulesTest {
    private val meet = Instant.parse("2026-11-03T18:00:00Z")

    @Test
    fun `minutes vs start is whole minutes from the meet time, negative before, truncated toward zero`() {
        assertEquals(-30, minutesVsStart(meet, meet.minusSeconds(30 * 60)))
        assertEquals(0, minutesVsStart(meet, meet))
        assertEquals(7, minutesVsStart(meet, meet.plusSeconds(7 * 60 + 59)))
        assertEquals(0, minutesVsStart(meet, meet.minusSeconds(59)))
        assertEquals(-1, minutesVsStart(meet, meet.minusSeconds(60)))
    }

    @Test
    fun `the retry schedule doubles from 2 s and holds at 30 s`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..5).map(::retryDelayMs))
        assertEquals(30_000L, retryDelayMs(100))
    }

    @Test
    fun `the location read is bounded at 10 s`() {
        assertEquals(10_000L, LOCATION_FIX_TIMEOUT_MS)
    }

    @Test
    fun `an arrival or an unread message hoists a card`() {
        val arrival = DateArrival(meet)
        assertTrue(hoisted(DateState.Locked, arrival))
        assertFalse(hoisted(DateState.Available, null))
        assertTrue(hoisted(DateState.Unread, null))
        assertFalse(hoisted(DateState.Locked, null))
    }

    @Test
    fun `six chips are shown, arrived among them`() {
        assertEquals(
            listOf(ChatReason.OnMyWay, ChatReason.Arrived, ChatReason.CantFind, ChatReason.WhenArrive, ChatReason.RunningLate, ChatReason.CantMakeIt),
            SHOWN_REASONS,
        )
    }

    @Test
    fun `the entry wire values are the registry's`() {
        assertEquals(listOf("date_screen", "chat_chip", "report"), ArrivalEntry.entries.map { it.wire })
    }
}
