/*
 * ChangeRulesTest.kt
 * ShowUp · Date 05's pure rules (SHOWUP-196): the distance line, the deadline, and when the tiles and chips show
 */
package com.showup.change

import com.showup.AppRoute
import com.showup.chat.ChatReason
import com.showup.chat.ChipSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ChangeRulesTest {
    private val meet = Instant.parse("2026-11-17T18:00:00Z")
    private val cutoff = meet.minusSeconds(30 * 60)
    private val slot = Instant.parse("2026-11-17T18:30:00Z")

    private fun change(allowed: Boolean = true, slots: List<Instant> = listOf(slot), cutoffAt: Instant = cutoff) =
        DateChange(cutoffAt = cutoffAt, allowed = allowed, slots = slots, timeWas = null, placeWas = null, pending = emptyList())

    @Test
    fun `distance rounds to the nearest 50 m under a kilometre and to one decimal from it`() {
        assertEquals("350 m", formatDistance(349))
        assertEquals("350 m", formatDistance(350))
        assertEquals("350 m", formatDistance(374))
        assertEquals("400 m", formatDistance(375))
        assertEquals("600 m", formatDistance(600))
        // 999 rounds up to a full kilometre: it reads as one, not as "1000 m".
        assertEquals("1.0 km", formatDistance(999))
        assertEquals("1.0 km", formatDistance(1000))
        assertEquals("1.0 km", formatDistance(1049))
        assertEquals("1.1 km", formatDistance(1050))
        assertEquals("1.4 km", formatDistance(1400))
        assertEquals("12.3 km", formatDistance(12_300))
    }

    @Test
    fun `the place types read Cafe, Bar and Restaurant, the row tag in capitals`() {
        assertEquals("Café", placeTypeLabel(PlaceType.Cafe))
        assertEquals("Bar", placeTypeLabel(PlaceType.Bar))
        assertEquals("Restaurant", placeTypeLabel(PlaceType.Restaurant))
        assertEquals("CAFÉ", placeTypeTag(PlaceType.Cafe))
        assertEquals("BAR", placeTypeTag(PlaceType.Bar))
        assertEquals("RESTAURANT", placeTypeTag(PlaceType.Restaurant))
    }

    @Test
    fun `the deadline is the binding time less half an hour, from the binding time`() {
        assertEquals(cutoff, changeDeadline(meet))
        // 18:00Z is 19:00 in Berlin: the answer is due 17:30Z (18:30 there), counted from the binding time.
        assertEquals(Instant.parse("2026-11-17T17:30:00Z"), changeDeadline(Instant.parse("2026-11-17T18:00:00Z")))
    }

    @Test
    fun `the latest slot is the last the server sent, and none when it sent none`() {
        val slots = listOf(slot, slot.plusSeconds(900), slot.plusSeconds(1800))
        assertEquals(slot.plusSeconds(1800), latestSlot(slots))
        assertNull(latestSlot(emptyList()))
    }

    @Test
    fun `both change entries show while allowed, before the cutoff, with a later slot`() {
        val now = cutoff.minusSeconds(60)
        assertTrue(changeKindAvailable(change(), ChangeKind.Time, now))
        assertTrue(changeKindAvailable(change(), ChangeKind.Place, now))
    }

    @Test
    fun `from the cutoff on both are hidden, in place as the clock ticks`() {
        assertFalse(changeKindAvailable(change(), ChangeKind.Time, cutoff))
        assertFalse(changeKindAvailable(change(), ChangeKind.Place, cutoff))
        assertFalse(changeKindAvailable(change(), ChangeKind.Place, cutoff.plusSeconds(1)))
    }

    @Test
    fun `once either person has checked in both are hidden`() {
        val now = cutoff.minusSeconds(3600)
        assertFalse(changeKindAvailable(change(allowed = false), ChangeKind.Time, now))
        assertFalse(changeKindAvailable(change(allowed = false), ChangeKind.Place, now))
    }

    @Test
    fun `no later slot hides the time entry only`() {
        val now = cutoff.minusSeconds(3600)
        assertFalse(changeKindAvailable(change(slots = emptyList()), ChangeKind.Time, now))
        assertTrue(changeKindAvailable(change(slots = emptyList()), ChangeKind.Place, now))
    }

    @Test
    fun `an older server with no change block shows neither`() {
        assertFalse(changeKindAvailable(null, ChangeKind.Time, cutoff.minusSeconds(3600)))
        assertFalse(changeKindAvailable(null, ChangeKind.Place, cutoff.minusSeconds(3600)))
    }

    @Test
    fun `the locked card's tiles and the chat's chips are one check`() {
        val now = cutoff.minusSeconds(3600)
        assertEquals(listOf(ChatReason.ChangeTime, ChatReason.ChangePlace), changeTilesFor(change(), now))
        assertEquals(listOf(ChatReason.ChangePlace), changeTilesFor(change(slots = emptyList()), now))
        assertEquals(emptyList<ChatReason>(), changeTilesFor(change(), cutoff))

        val all = visibleReasons(change(), now)
        assertEquals(
            listOf(
                ChatReason.OnMyWay, ChatReason.Arrived, ChatReason.CantFind, ChatReason.WhenArrive,
                ChatReason.ChangeTime, ChatReason.ChangePlace, ChatReason.RunningLate, ChatReason.CantMakeIt,
            ),
            all,
        )
        assertEquals(
            listOf(ChatReason.OnMyWay, ChatReason.Arrived, ChatReason.CantFind, ChatReason.WhenArrive, ChatReason.RunningLate, ChatReason.CantMakeIt),
            visibleReasons(change(), cutoff),
        )
    }

    @Test
    fun `a chip or tile opens the picker of its kind with the entry that names where it was tapped`() {
        assertEquals(AppRoute.ChangePicker("time", "chat_chip"), changePickerRoute(ChatReason.ChangeTime, ChipSurface.Entry))
        assertEquals(AppRoute.ChangePicker("place", "chat_chip"), changePickerRoute(ChatReason.ChangePlace, ChipSurface.Dock))
        assertEquals(AppRoute.ChangePicker("time", "coordinate_tile"), changePickerRoute(ChatReason.ChangeTime, ChipSurface.CoordinateCard))
        assertNull(changePickerRoute(ChatReason.RunningLate, ChipSurface.Dock))
    }

    @Test
    fun `the sub box and note times are local clock times`() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals("19:00", changeClock(meet, berlin))
        assertEquals("18:30", changeClock(cutoff, berlin))
    }

    @Test
    fun `Suggest another in the thread opens the same kind with the counter_thread entry`() {
        assertEquals(AppRoute.ChangePicker("time", "counter_thread"), counterThreadRoute(ChangeKind.Time))
        assertEquals(AppRoute.ChangePicker("place", "counter_thread"), counterThreadRoute(ChangeKind.Place))
    }
}
