/*
 * MeetClockTest.kt
 * ShowUp · Date 09: the meet time in the device's 12 / 24 h clock, in the viewer's zone (SHOWUP-200, R9)
 */
package com.showup.date.partnerend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MeetClockTest {
    private val utc = ZoneId.of("UTC")
    private fun at(h: Int, m: Int, zone: ZoneId = utc): Instant = ZonedDateTime.of(2026, 11, 28, h, m, 0, 0, zone).toInstant()

    @Test fun `19 00 reads 19 00 in 24 h and 7 00 PM in 12 h`() {
        assertEquals("19:00", meetClock(at(19, 0), utc, is24h = true))
        assertEquals("7:00 PM", meetClock(at(19, 0), utc, is24h = false))
    }

    @Test fun `just after midnight`() {
        assertEquals("00:05", meetClock(at(0, 5), utc, is24h = true))
        assertEquals("12:05 AM", meetClock(at(0, 5), utc, is24h = false))
    }

    @Test fun `noon`() {
        assertEquals("12:00", meetClock(at(12, 0), utc, is24h = true))
        assertEquals("12:00 PM", meetClock(at(12, 0), utc, is24h = false))
    }

    @Test fun `a zone other than UTC is honoured`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val newYork = ZoneId.of("America/New_York")
        val instant = at(19, 0, berlin)
        assertEquals("19:00", meetClock(instant, berlin, is24h = true))
        assertEquals("13:00", meetClock(instant, newYork, is24h = true))
        assertEquals("1:00 PM", meetClock(instant, newYork, is24h = false))
    }

    @Test fun `AM and PM stay English whatever the default locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("7:00 PM", meetClock(at(19, 0), utc, is24h = false))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
