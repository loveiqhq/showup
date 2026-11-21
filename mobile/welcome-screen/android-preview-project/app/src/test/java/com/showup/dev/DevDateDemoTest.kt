/*
 * DevDateDemoTest.kt
 * ShowUp · the in-memory date server behind Date 05's live States frames (debug only)
 *
 * What the States page promises: each frame opens in the state its picture shows, at today's times, and every tap
 * then does what the real server would. The rules mirrored here are the server's; the frame seeds are ChangeSamples'.
 */
package com.showup.dev

import com.showup.api.Outcome
import com.showup.arrival.ArrivalEntry
import com.showup.change.AgreeResult
import com.showup.change.AgreeSurface
import com.showup.change.ChangeEntry
import com.showup.change.ChangeKind
import com.showup.change.ChangeSamples
import com.showup.change.ChangeStatus
import com.showup.change.SuggestRequest
import com.showup.change.SuggestResult
import com.showup.chat.ChatReason
import com.showup.date.ActiveDate
import com.showup.date.CancelResult
import com.showup.date.EndDateResult
import com.showup.date.EndEntry
import com.showup.date.EndReason
import com.showup.date.OutcomeReason
import com.showup.date.DateRead
import com.showup.date.DateSamples
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DevDateDemoTest {
    private var now: Instant = Instant.parse("2026-11-21T09:00:00Z")
    private fun demo(frame: String) = DevDateDemo(frame, clock = { now })

    private suspend fun DevDateDemo.read(): ActiveDate = ((active("Europe/Berlin") as Outcome.Ok).value as DateRead.Active).date

    private fun time(at: Instant, clientId: String = "c1") =
        SuggestRequest(ChangeKind.Time, at, null, ChangeEntry.ChatChip, null, clientId, "Europe/Berlin")

    private fun place(id: String, clientId: String = "c2") =
        SuggestRequest(ChangeKind.Place, null, id, ChangeEntry.ChatChip, false, clientId, "Europe/Berlin")

    // ── where each frame opens ──────────────────────────────────────────────────────────────

    @Test
    fun `every frame has a landing, the pickers over the chat, the thread frames in it, the rest on the Date screen`() {
        assertEquals(DemoLanding.TimePicker, demoLanding("A"))
        assertEquals(DemoLanding.TimePicker, demoLanding("K"))
        for (f in listOf("B", "M", "N")) assertEquals(DemoLanding.PlacePicker, demoLanding(f))
        for (f in listOf("C", "F", "H")) assertEquals(DemoLanding.Chat, demoLanding(f))
        for (f in listOf("D", "E", "G", "I", "J", "L")) assertEquals(DemoLanding.DateScreen, demoLanding(f))
    }

    @Test
    fun `the States page opens every Date 05 frame live and every other ticket's frame as a picture`() {
        val live = DEV_STATES.mapNotNull { state -> devLiveFrame(state)?.let { state.id to it } }
        assertEquals(ChangeSamples.LETTERS.map { "196$it" to it }, live)
        assertNull(devLiveFrame(DevState("195A", "SHOWUP-195", "A")))
        assertNull(devLiveFrame(DevState("196Z", "SHOWUP-196", "Z")))
    }

    // ── each frame's state, at today's times ────────────────────────────────────────────────

    @Test
    fun `the sheet's clock is now, so the meet time is 1h18 away with the chat open and 3h05 away while it is locked`() = runTest {
        val open = demo("A").read()
        assertEquals(Duration.ofMinutes(78), Duration.between(now, open.meetAt))
        assertTrue(open.coordinateOpen)
        assertTrue(open.change!!.allowed)
        assertEquals(10, open.change!!.slots.size)
        assertEquals(open.meetAt.plusSeconds(15 * 60), open.change!!.slots.first())

        val locked = demo("I").read()
        assertEquals(Duration.ofMinutes(185), Duration.between(now, locked.meetAt))
        assertFalse(locked.coordinateOpen)
        assertTrue(locked.change!!.allowed)
    }

    @Test
    fun `D holds one time and one place of mine, E F J L hold Leonie's time as the request`() = runTest {
        val d = demo("D").read()
        assertEquals(listOf(ChangeKind.Time, ChangeKind.Place), d.change!!.pending.map { it.kind })
        assertTrue(d.change!!.pending.all { it.byYou })
        assertNull(d.changeRequest)

        for (f in listOf("E", "F", "J", "L")) {
            val e = demo(f).read()
            assertNotNull(f, e.changeRequest)
            assertFalse(f, e.changeRequest!!.byYou)
            assertEquals(f, e.meetAt.plusSeconds(30 * 60), e.changeRequest!!.time)
        }
    }

    @Test
    fun `G has the later time binding, changed from the original, and H shows the expired card after the cutoff`() = runTest {
        val g = demo("G").read()
        assertEquals(g.change!!.timeWas!!.plusSeconds(30 * 60), g.meetAt)
        // The chat opened at the original time's unlock, before the later time was agreed.
        assertTrue(g.coordinateOpen)

        val h = demo("H")
        val read = h.read()
        assertFalse(read.change!!.allowed)
        val cards = (h.suggestions(read.id) as Outcome.Ok).value
        assertEquals(listOf(ChangeStatus.Expired), cards.map { it.status })
        assertEquals(read.meetAt, cards.single().expiredBindingTime)
    }

    @Test
    fun `E and L show Leonie's running-late message unread under her request, J's locked chat has none`() = runTest {
        for (f in listOf("E", "L")) {
            val unread = demo(f).read().unread
            assertEquals(f, 1, unread?.count)
            assertEquals(f, DateSamples.UNREAD_BODY, unread?.body)
        }
        assertNull(demo("J").read().unread)
        assertNull(demo("D").read().unread)
    }

    @Test
    fun `N's picker opens on its search for sushi, every other frame's on the nearby list`() {
        assertEquals("sushi", demoQuery("N"))
        for (f in ChangeSamples.LETTERS - "N") assertNull(f, demoQuery(f))
    }

    @Test
    fun `a test date made from the dev menu starts over, the chat locked again when it is far away`() = runTest {
        val demo = demo("E")
        assertTrue(demo.read().coordinateOpen)
        demo.createTestDate(200)
        val fresh = demo.read()
        assertFalse(fresh.coordinateOpen)
        assertNull(fresh.changeRequest)
        assertNull(fresh.unread)
    }

    @Test
    fun `the match ending the date in the chat shows its outcome again after a new test date`() = runTest {
        val demo = demo("C")
        demo.matchEndsInChat()
        assertTrue((demo.active("Europe/Berlin") as Outcome.Ok).value is DateRead.Ended)
        demo.markEndSeen("x")
        demo.createTestDate(60)
        demo.matchEndsInChat()
        assertTrue((demo.active("Europe/Berlin") as Outcome.Ok).value is DateRead.Ended)
    }

    @Test
    fun `the thread frames carry Leonie's earlier message, the locked ones none`() = runTest {
        val c = demo("C")
        assertEquals(listOf("Looking forward to tonight!"), (c.thread("x") as Outcome.Ok).value.map { it.body })
        assertEquals(1, (c.suggestions("x") as Outcome.Ok).value.size)
        assertTrue((demo("I").thread("x") as Outcome.Ok).value.isEmpty())
    }

    // ── suggesting ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a suggested slot waits for Leonie, a resend is stored once, and a second time replaces the first`() = runTest {
        val demo = demo("A")
        val slots = demo.read().change!!.slots
        val first = (demo.suggest("x", time(slots[1])) as SuggestResult.Sent).suggestion
        assertEquals(first, (demo.suggest("x", time(slots[1])) as SuggestResult.Sent).suggestion)
        assertEquals(listOf(first.id), demo.read().change!!.pending.map { it.id })

        val second = (demo.suggest("x", time(slots[3], clientId = "c9")) as SuggestResult.Sent).suggestion
        val all = (demo.suggestions("x") as Outcome.Ok).value
        assertEquals(listOf(ChangeStatus.Replaced, ChangeStatus.Pending), all.map { it.status })
        assertEquals(listOf(second.id), demo.read().change!!.pending.map { it.id })
    }

    @Test
    fun `a slot not offered and a suggestion past the cutoff are refused as stale`() = runTest {
        val demo = demo("A")
        val meet = demo.read().meetAt
        assertEquals(SuggestResult.Stale, demo.suggest("x", time(meet.plusSeconds(7 * 60))))
        now = meet.minusSeconds(29 * 60)
        assertEquals(SuggestResult.Stale, demo.suggest("x", time(meet.plusSeconds(15 * 60), clientId = "c3")))
    }

    @Test
    fun `any listed place can be suggested, and choosing another one before Suggest sends only that one`() = runTest {
        val demo = demo("B")
        val sent = (demo.suggest("x", place("p-nove")) as SuggestResult.Sent).suggestion
        assertEquals("Osteria Nove", sent.place!!.name)
        assertEquals("Cafe Krone", sent.wasPlaceName)
    }

    @Test
    fun `K fails its first Suggest and sends on the retry`() = runTest {
        val demo = demo("K")
        val at = demo.read().change!!.slots[1]
        assertTrue(demo.suggest("x", time(at)) is SuggestResult.Failed)
        assertTrue(demo.suggest("x", time(at)) is SuggestResult.Sent)
    }

    // ── places ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the nearby list is the kit's five, a search filters it, and sushi finds nothing (N)`() = runTest {
        val demo = demo("N")
        assertEquals(5, (demo.places("x", null) as Outcome.Ok).value.places.size)
        assertEquals(listOf("Bar Lenz", "Kanal Bar"), (demo.places("x", "bar") as Outcome.Ok).value.places.map { it.name })
        assertTrue((demo.places("x", "sushi") as Outcome.Ok).value.places.isEmpty())
    }

    @Test
    fun `M fails its first places read and lists them on Try again`() = runTest {
        val demo = demo("M")
        assertTrue(demo.places("x", null) is Outcome.Failed)
        assertEquals(5, (demo.places("x", null) as Outcome.Ok).value.places.size)
    }

    // ── agreeing ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `I agree makes Leonie's time binding, changed from the original, and the request card goes`() = runTest {
        val demo = demo("E")
        val before = demo.read()
        val request = before.changeRequest!!
        assertTrue(demo.agree("x", request.id, AgreeSurface.CoordinateCard) is AgreeResult.Agreed)
        val after = demo.read()
        assertEquals(request.time, after.meetAt)
        assertEquals(before.meetAt, after.change!!.timeWas)
        assertNull(after.changeRequest)
    }

    @Test
    fun `a chat once open stays open when an agreed later time moves the unlock past now`() = runTest {
        val demo = demo("E")
        val request = demo.read().changeRequest!!
        demo.agree("x", request.id, AgreeSurface.CoordinateCard)
        val after = demo.read()
        assertTrue(now.isBefore(after.unlockAt))
        assertTrue(after.coordinateOpen)
    }

    @Test
    fun `L fails its first I agree and agrees on the retry`() = runTest {
        val demo = demo("L")
        val id = demo.read().changeRequest!!.id
        assertTrue(demo.agree("x", id, AgreeSurface.CoordinateCard) is AgreeResult.Failed)
        assertTrue(demo.agree("x", id, AgreeSurface.CoordinateCard) is AgreeResult.Agreed)
    }

    @Test
    fun `my own suggestion cannot be agreed by me, Leonie agrees to it from the dev menu`() = runTest {
        val demo = demo("C")
        val mine = demo.read().change!!.pending.single()
        assertEquals(AgreeResult.Stale, demo.agree("x", mine.id, AgreeSurface.Thread))
        assertTrue(demo.matchAgrees().getOrThrow())
        assertEquals(mine.time, demo.read().meetAt)
    }

    @Test
    fun `an open suggestion expires at its deadline`() = runTest {
        val demo = demo("C")
        now = demo.read().change!!.pending.single().deadline
        val read = demo.read()
        assertTrue(read.change!!.pending.isEmpty())
        assertEquals(ChangeStatus.Expired, (demo.suggestions("x") as Outcome.Ok).value.single().status)
    }

    // ── checking in, chatting ───────────────────────────────────────────────────────────────

    @Test
    fun `a committed check-in opens the chat and closes new suggestions, undo before Close leaves nothing`() = runTest {
        val demo = demo("I")
        val fix = demo.fix()
        demo.checkIn("x", fix, ArrivalEntry.DateScreen, "k1")
        demo.undo("x")
        assertFalse(demo.read().checkedIn)

        demo.checkIn("x", fix, ArrivalEntry.DateScreen, "k2")
        demo.commit("x")
        val read = demo.read()
        assertTrue(read.checkedIn)
        assertTrue(read.coordinateOpen)
        assertFalse(read.change!!.allowed)
    }

    @Test
    fun `a sent message joins the thread once, and Leonie's unread message shows until the chat is read`() = runTest {
        val demo = demo("C")
        demo.send("x", ChatReason.OnMyWay, "On my way now", "m1")
        demo.send("x", ChatReason.OnMyWay, "On my way now", "m1")
        assertEquals(2, (demo.thread("x") as Outcome.Ok).value.size)

        now = now.plusSeconds(60)
        demo.messageFromMatch(ChatReason.RunningLate, "Running late")
        assertEquals(1, demo.read().unread!!.count)
        now = now.plusSeconds(60)
        demo.markRead("x")
        assertNull(demo.read().unread)
    }

    @Test
    fun `Leonie suggesting from the dev menu raises her request card`() = runTest {
        val demo = demo("D")
        assertTrue(demo.matchSuggests(ChangeKind.Place).getOrThrow())
        val request = demo.read().changeRequest!!
        assertEquals(ChangeKind.Place, request.kind)
        assertEquals("Bar Lenz", request.place!!.name)
    }

    // -- end and cancel (Date 06, 07): the server's refusals, then the outcome the next read reports --

    private suspend fun DevDateDemo.commitCheckIn() {
        checkIn("x", fix(), ArrivalEntry.DateScreen, "k1")
        commit("x")
    }

    @Test
    fun `an active date reads with the ends switch on and the stand-in servers penalty`() = runTest {
        val date = demo("B").read()
        assertTrue(date.endsEnabled)
        assertTrue(date.cancelPenaltyPoints > 0)
    }

    @Test
    fun `an end is refused before the meet time and without a committed check-in`() = runTest {
        val demo = demo("B")
        assertEquals(EndDateResult.Refused("before_meet"), demo.end("x", EndReason.PartnerNoShow, EndEntry.EndDateBar))
        assertEquals(EndDateResult.Refused("not_checked_in"), demo.end("x", EndReason.Uncomfortable, EndEntry.Report))
        assertTrue(demo.read().endsEnabled)
    }

    @Test
    fun `met needs no check-in, and the next read reports the outcome until it is seen`() = runTest {
        val demo = demo("B")
        val result = demo.end("x", EndReason.Met, EndEntry.EndDateBar) as EndDateResult.Ended
        assertEquals(OutcomeReason.Met, result.outcome.reason)
        val read = (demo.active("UTC") as Outcome.Ok).value as DateRead.Ended
        assertEquals(OutcomeReason.Met, read.outcome.reason)
        assertEquals(EndDateResult.Refused("date_not_active"), demo.end("x", EndReason.Met, EndEntry.EndDateBar))
        demo.markEndSeen("x")
        assertEquals(DateRead.None, (demo.active("UTC") as Outcome.Ok).value)
    }

    @Test
    fun `uncomfortable and a no-show end once checked in, a no-show only from the meet time`() = runTest {
        val demo = demo("B")
        demo.commitCheckIn()
        val meetAt = demo.read().meetAt
        assertEquals(EndDateResult.Refused("before_meet"), demo.end("x", EndReason.PartnerNoShow, EndEntry.CheckinBarLink))
        now = meetAt
        val ended = demo.end("x", EndReason.PartnerNoShow, EndEntry.CheckinBarLink) as EndDateResult.Ended
        assertEquals(OutcomeReason.PartnerNoShow, ended.outcome.reason)

        val other = demo("B")
        other.commitCheckIn()
        val uncomfortable = other.end("x", EndReason.Uncomfortable, EndEntry.Report) as EndDateResult.Ended
        assertEquals(OutcomeReason.Uncomfortable, uncomfortable.outcome.reason)
    }

    @Test
    fun `Stay for now succeeds and leaves the date active`() = runTest {
        val demo = demo("B")
        assertTrue(demo.flagDiscomfort("x").getOrThrow())
        assertNotNull(demo.read())
    }

    @Test
    fun `a cancel ends the date as cancelled, and is refused once checked in or once over`() = runTest {
        val demo = demo("B")
        val cancelled = demo.cancel("x") as CancelResult.Cancelled
        assertEquals(OutcomeReason.Cancelled, cancelled.outcome.reason)
        assertEquals(CancelResult.Refused("date_not_active"), demo.cancel("x"))
        val read = (demo.active("UTC") as Outcome.Ok).value as DateRead.Ended
        assertEquals(OutcomeReason.Cancelled, read.outcome.reason)

        val checked = demo("B")
        checked.commitCheckIn()
        assertEquals(CancelResult.Refused("checked_in"), checked.cancel("x"))
    }

    @Test
    fun `the dev menus End the date makes any later end or cancel date_not_active`() = runTest {
        val demo = demo("B")
        demo.endTestDate()
        assertEquals(EndDateResult.Refused("date_not_active"), demo.end("x", EndReason.Met, EndEntry.EndDateBar))
        assertEquals(CancelResult.Refused("date_not_active"), demo.cancel("x"))
    }
}
