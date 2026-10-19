/*
 * ConfettiRainTest.kt
 * ShowUp · Embrace 2's confetti, evaluated frame by frame against the reference (SHOWUP-166)
 *
 * The keyframes are a pure function of time, so every number the ticket states is checked here
 * rather than eyeballed in a recording: the table, the stagger, the fall distance, the fade over
 * the last 20%, nothing born or killed on screen, and the layer gone by 3000 ms.
 */
package com.showup.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ConfettiRainTest {

    private val height = 844f
    private val unit = 1f

    @Test
    fun `thirty-two pieces from the fixed table, deterministic`() {
        assertEquals(32, CONFETTI_PIECES.size)
        val first = CONFETTI_PIECES.first()
        assertEquals(4f, first.leftPercent)
        assertEquals(2300, first.fallMs)
        assertEquals(ConfettiShape.Strip, first.shape)
        val last = CONFETTI_PIECES.last()
        assertEquals(85f, last.leftPercent)
        assertEquals(200, last.delayMs)
    }

    @Test
    fun `stagger 0 to 700, falls 1950 to 2600, sizes 8 to 14`() {
        assertTrue(CONFETTI_PIECES.all { it.delayMs in 0..700 })
        assertTrue(CONFETTI_PIECES.all { it.fallMs in 1950..2600 })
        assertTrue(CONFETTI_PIECES.all { it.size in 8f..14f })
        assertEquals(0, CONFETTI_PIECES.minOf { it.delayMs })
        assertEquals(700, CONFETTI_PIECES.maxOf { it.delayMs })
    }

    @Test
    fun `sway periods are 700, 820, 940 and 1060 by index`() {
        assertEquals(listOf(700, 820, 940, 1060, 700), (0..4).map { confettiSwayMs(it) })
    }

    @Test
    fun `the last piece leaves the screen by about 2930 ms, inside the 3000 ms layer`() {
        val lastLanding = CONFETTI_PIECES.maxOf { it.delayMs + it.fallMs }
        assertTrue("last landing $lastLanding", lastLanding <= 2930)
        assertTrue(lastLanding < CONFETTI_TOTAL_MS)
    }

    @Test
    fun `every piece starts 40 above the top edge and holds there through its delay`() {
        CONFETTI_PIECES.forEachIndexed { i, p ->
            val before = confettiFrame(p, i, 0L, height, unit)
            assertEquals(-40f, before.y, 0.001f)
            assertEquals(1f, before.alpha, 0.001f)
        }
    }

    @Test
    fun `every piece ends 40 below the bottom edge, fully faded -- none vanishes on screen`() {
        CONFETTI_PIECES.forEachIndexed { i, p ->
            val end = confettiFrame(p, i, (p.delayMs + p.fallMs).toLong(), height, unit)
            assertEquals(height + 40f, end.y, 0.01f)
            assertEquals(0f, end.alpha, 0.001f)
        }
    }

    @Test
    fun `opacity holds at 1 until 80 percent of the fall`() {
        val p = CONFETTI_PIECES[0]
        val at = (p.delayMs + p.fallMs * 0.79f).toLong()
        assertEquals(1f, confettiFrame(p, 0, at, height, unit).alpha, 0.0001f)
        val later = (p.delayMs + p.fallMs * 0.9f).toLong()
        val fading = confettiFrame(p, 0, later, height, unit).alpha
        assertTrue("fading at 90%: $fading", fading in 0.01f..0.99f)
    }

    @Test
    fun `the fall distance is the screen's own height, not 884`() {
        val p = CONFETTI_PIECES[3]
        val short = confettiFrame(p, 3, (p.delayMs + p.fallMs).toLong(), 667f, unit)
        val tall = confettiFrame(p, 3, (p.delayMs + p.fallMs).toLong(), 932f, unit)
        assertEquals(707f, short.y, 0.01f)
        assertEquals(972f, tall.y, 0.01f)
    }

    @Test
    fun `the same durations on every device`() {
        val p = CONFETTI_PIECES[5]
        val mid = (p.delayMs + p.fallMs / 2).toLong()
        val a = confettiFrame(p, 5, mid, 667f, unit)
        val b = confettiFrame(p, 5, mid, 932f, unit)
        // Same progress, different distance: the fraction of the fall covered is identical.
        val fa = (a.y + 40f) / (667f + 80f)
        val fb = (b.y + 40f) / (932f + 80f)
        assertEquals(fa, fb, 0.0001f)
    }

    @Test
    fun `the swing alternates and eases into each turn`() {
        val p = CONFETTI_PIECES[0] // sway 14, rot0 -18, spin 540, swing 700
        val start = confettiFrame(p, 0, p.delayMs.toLong(), height, unit)
        val turn = confettiFrame(p, 0, (p.delayMs + 700).toLong(), height, unit)
        val back = confettiFrame(p, 0, (p.delayMs + 1400).toLong(), height, unit)
        assertEquals(-14f, start.swayX, 0.01f)
        assertEquals(14f, turn.swayX, 0.01f)
        assertEquals(-14f, back.swayX, 0.01f)
        assertEquals(-18f, start.rotation, 0.01f)
        assertEquals(-18f + 540f, turn.rotation, 0.01f)
        // rotateY 0 -> 180: the flip runs from face-on, through edge-on, to face-on reversed.
        assertEquals(1f, start.flipScale, 0.001f)
        assertEquals(-1f, turn.flipScale, 0.001f)
        val quarter = confettiFrame(p, 0, (p.delayMs + 350).toLong(), height, unit)
        assertTrue("edge-on mid-swing: ${quarter.flipScale}", abs(quarter.flipScale) < 0.2f)
    }
}
