package com.showup.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The chat's rules (Date 02), each a plain function the model and the screen both read. */
class ChatRulesTest {
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val at = Instant.parse("2026-11-03T16:38:00Z")

    private fun msg(id: String, reason: ChatReason?, fromYou: Boolean) =
        ChatMessage(id, reason, "text $id", fromYou, at, if (fromYou) false else null)

    @Test
    fun `the end card waits for an I can't make it today message, from either side`() {
        assertFalse(endCardVisible(emptyList(), selected = null))
        assertFalse(endCardVisible(listOf(msg("1", ChatReason.RunningLate, true), msg("2", null, false)), null))
        assertTrue(endCardVisible(listOf(msg("1", ChatReason.CantMakeIt, true)), null))
        assertTrue(endCardVisible(listOf(msg("1", ChatReason.CantMakeIt, false)), ChatReason.OnMyWay))
    }

    @Test
    fun `a message that did not send is not in the thread for the end card or cant_make_it_from (J)`() {
        val unsent = msg("1", ChatReason.CantMakeIt, true).copy(failed = true)
        assertFalse(endCardVisible(listOf(unsent), null))
        assertNull(cantMakeItFrom(listOf(unsent)))
        assertEquals("match", cantMakeItFrom(listOf(msg("0", ChatReason.CantMakeIt, false), unsent)))
    }

    @Test
    fun `the end card steps aside while I can't make it today is the selected chip`() {
        assertFalse(endCardVisible(listOf(msg("1", ChatReason.CantMakeIt, false)), ChatReason.CantMakeIt))
    }

    @Test
    fun `the field is locked only while the cancel guide waits for Got it`() {
        assertTrue(fieldLocked(ChatReason.CantMakeIt, Guide.Locked))
        assertFalse(fieldLocked(ChatReason.CantMakeIt, Guide.Acked))
        assertFalse(fieldLocked(ChatReason.RunningLate, Guide.None))
        assertFalse(fieldLocked(null, Guide.None))
    }

    @Test
    fun `send is enabled whenever the field has text and is not locked`() {
        assertTrue(canSend("Hi", locked = false))
        assertFalse(canSend("", locked = false))
        assertFalse(canSend("   \n ", locked = false))
        assertFalse(canSend("Hi", locked = true))
    }

    @Test
    fun `the countdown reads in h and m, 0h under an hour, and is gone from the meet time on`() {
        assertEquals("in 1h 18m", countdown(meet.minusSeconds(78 * 60), meet))
        assertEquals("in 0h 45m", countdown(meet.minusSeconds(45 * 60), meet))
        assertEquals("in 0h 0m", countdown(meet.minusSeconds(30), meet))
        assertNull(countdown(meet, meet))
        assertNull(countdown(meet.plusSeconds(60), meet))
    }

    @Test
    fun `prefill_edited compares the sent text with the chip's draft, and is null with no draft`() {
        val draft = ChatReason.RunningLate.draft!!
        assertEquals(false, prefillEdited(ChatReason.RunningLate, draft))
        assertEquals(false, prefillEdited(ChatReason.RunningLate, "  $draft "))
        assertEquals(true, prefillEdited(ChatReason.RunningLate, "Running 20 min late, sorry!"))
        assertNull(prefillEdited(ChatReason.CantMakeIt, "So sorry, I can't make it."))
        assertNull(prefillEdited(null, "Hi"))
    }

    @Test
    fun `cant_make_it_from is who sent the latest I can't make it today message, or none`() {
        assertEquals("self", cantMakeItFrom(listOf(msg("1", ChatReason.CantMakeIt, true))))
        assertEquals("match", cantMakeItFrom(listOf(msg("1", ChatReason.CantMakeIt, false), msg("2", null, true))))
        assertEquals(
            "match",
            cantMakeItFrom(listOf(msg("1", ChatReason.CantMakeIt, true), msg("2", ChatReason.CantMakeIt, false))),
        )
        assertNull(cantMakeItFrom(listOf(msg("1", ChatReason.RunningLate, true))))
    }
}
