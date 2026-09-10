/*
 * CooldownTest.kt
 * ShowUp · the countdown cannot be broken by a clock
 *
 * `cooldownRemaining` subtracts the DEVICE's clock from the SERVER's timestamp, and nothing makes
 * those agree. That is not a hypothetical: on 10 September 2026 the emulator rendered "Send a new
 * code in 162024:02" against a server whose `resendAvailableAt` was further ahead than the device
 * believed. The resend link would never have come back.
 *
 * The upper clamp is what these tests are really about. The lower one has always been there.
 */
package com.showup.welcome

import com.showup.api.RESEND_COOLDOWN_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CooldownTest {

    private val now: OffsetDateTime =
        OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `no challenge means nothing to wait for`() {
        assertEquals(0, cooldownRemaining(now, null))
    }

    @Test
    fun `a window still open counts the seconds left`() {
        assertEquals(41, cooldownRemaining(now, now.plusSeconds(41)))
    }

    @Test
    fun `the whole window is reported as the whole window`() {
        assertEquals(
            RESEND_COOLDOWN_SECONDS.toInt(),
            cooldownRemaining(now, now.plusSeconds(RESEND_COOLDOWN_SECONDS)),
        )
    }

    @Test
    fun `a window already passed is zero, not a negative countdown`() {
        assertEquals(0, cooldownRemaining(now, now.minusSeconds(30)))
    }

    @Test
    fun `a device clock an hour behind does not lock the user out for an hour`() {
        // The exact shape of the bug: the server's timestamp is an hour ahead because the DEVICE
        // is an hour behind, not because anyone has to wait an hour. Unclamped this returned
        // 3600 and the screen rendered "60:00" while the resend link stayed dead.
        assertEquals(
            RESEND_COOLDOWN_SECONDS.toInt(),
            cooldownRemaining(now, now.plusHours(1)),
        )
    }

    @Test
    fun `an absurd server timestamp is still bounded by the policy window`() {
        // The value the emulator actually produced, from a stub dated 2027.
        assertEquals(
            RESEND_COOLDOWN_SECONDS.toInt(),
            cooldownRemaining(now, OffsetDateTime.of(2027, 1, 1, 10, 1, 0, 0, ZoneOffset.UTC)),
        )
    }

    @Test
    fun `the countdown always renders as minutes and seconds`() {
        // Whatever the server says, the value reaching the screen is one the "%d:%02d" format can
        // show sensibly. That format is why an unclamped 9721322 second wait read as "162022:02".
        for (offset in listOf(0L, 1L, 59L, 60L, 61L, 3600L, 9_721_322L)) {
            val seconds = cooldownRemaining(now, now.plusSeconds(offset))
            assertEquals(
                "%d:%02d".format(seconds / 60, seconds % 60),
                if (seconds == 60) "1:00" else "0:%02d".format(seconds),
            )
        }
    }
}
