package com.showup.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The chat's reason vocabulary (Date 02): the ticket's chip table, built once, read by Date 01 too. */
class ChatReasonsTest {

    @Test
    fun `six chips are shown, in the grid order, until Date 05 ships`() {
        assertEquals(
            listOf(ChatReason.OnMyWay, ChatReason.Arrived, ChatReason.CantFind, ChatReason.WhenArrive, ChatReason.RunningLate, ChatReason.CantMakeIt),
            SHOWN_REASONS,
        )
    }

    @Test
    fun `every chip is the registry's wire value with the ticket's label and draft`() {
        val table = ChatReason.entries.map { Triple(it.wire, it.label, it.draft) }
        assertEquals(
            listOf(
                Triple("on_my_way", "I'm on my way", "On my way now — see you in a few!"),
                Triple("arrived", "I arrived at the spot", "I'm here at the meeting spot — see you soon!"),
                Triple("cant_find", "I can't spot you", "I'm here but can't spot you — where are you sitting?"),
                Triple("when_arrive", "When will you arrive?", "When do you think you'll get here?"),
                Triple("change_time", "Change meet time", null),
                Triple("change_place", "Change location", null),
                Triple("running_late", "I'm running late", "Running about 10 min late — sorry! Almost there."),
                Triple("cant_make_it", "I can't make it today", null),
            ),
            table,
        )
    }

    @Test
    fun `the tones are the reference's -- violet for the two changes, dark red for cancelling, orange otherwise`() {
        assertEquals(ReasonTone.Violet, ChatReason.ChangeTime.tone)
        assertEquals(ReasonTone.Violet, ChatReason.ChangePlace.tone)
        assertEquals(ReasonTone.DarkRed, ChatReason.CantMakeIt.tone)
        assertEquals(ReasonTone.Orange, ChatReason.RunningLate.tone)
    }

    @Test
    fun `a wire value reads back to its chip, and an old, unknown or missing one to none`() {
        assertEquals(ChatReason.CantMakeIt, ChatReason.fromWire("cant_make_it"))
        assertEquals(ChatReason.WhenArrive, ChatReason.fromWire("when_arrive"))
        assertNull(ChatReason.fromWire("cant_make_it_today"))
        assertNull(ChatReason.fromWire("something_new"))
        assertNull(ChatReason.fromWire(null))
    }
}
