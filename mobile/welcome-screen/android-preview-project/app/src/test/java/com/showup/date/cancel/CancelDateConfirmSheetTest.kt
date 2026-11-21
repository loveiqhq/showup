/*
 * CancelDateConfirmSheetTest.kt
 * ShowUp · Date 07's frames: the ticket's copy exactly and in order for B, C, D, E and I, the cost box following the
 * value it is given, the error box between the cost box and the choices, the link at the end of the Date screen's
 * scroll and gone after this user's check-in, and H on Date 02's component (SHOWUP-198)
 */
package com.showup.date.cancel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.showup.change.ChangeKind
import com.showup.change.DateChange
import com.showup.chat.CHAT_CTA_TAG
import com.showup.chat.DateEndedScreen
import com.showup.chat.OUTCOME_ROW_TAG
import com.showup.date.ActiveDate
import com.showup.date.DATE_CANCEL_TAG
import com.showup.date.DATE_MATCH_MATE_TAG
import com.showup.date.DateMatch
import com.showup.date.DatePlace
import com.showup.date.DateScreen
import com.showup.date.DateUi
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.end.END_ERROR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class CancelDateConfirmSheetTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-21T18:00:00Z") // 19:00 in Berlin
    private val early = meet.minusSeconds(3 * 3600)

    private fun date(chatOpen: Boolean = false, penalty: Int = 10, slots: Int = 1, checkedIn: Boolean = false) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = checkedIn, chatTutorialSeen = true, arrival = null, endsEnabled = true,
        change = DateChange(meet.minusSeconds(30 * 60), true, List(slots) { meet.plusSeconds(900L * (it + 1)) }, null, null, emptyList()),
        cancelPenaltyPoints = penalty,
    )

    private fun ui(
        chatOpen: Boolean = false,
        now: Instant = early,
        sent: SentRequest? = null,
        failed: Boolean = false,
        cancelling: Boolean = false,
        penalty: Int = 10,
        slots: Int = 1,
    ) = CancelUi(open = true, sent = sent, failed = failed, cancelling = cancelling, date = date(chatOpen, penalty, slots), now = now)

    private fun androidx.compose.ui.test.ComposeUiTest.show(ui: CancelUi, actions: CancelActions = CancelActions()) =
        setContent { CancelDateSheetHost(ui, berlin, actions) }

    private fun androidx.compose.ui.test.ComposeUiTest.assertText(vararg texts: String) = texts.forEach { onNodeWithText(it, substring = true).assertExists() }

    /** The node's laid-out box, not clipped to what the scrolling sheet shows: the sheet is taller than the phone. */
    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    /** The sheet scrolls inside the shell, so a choice below the fold is reached by scrolling to it, then tapped. */
    private fun SemanticsNodeInteraction.tap() {
        performScrollTo()
        performClick()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.gone(tag: String) = onAllNodesWithTag(tag).assertCountEquals(0)

    // ── B ────────────────────────────────────────────────────────────────────

    @Test
    fun `B reads the ticket's copy in the ticket's order`() = runComposeUiTest {
        show(ui())
        assertText(
            "Today · 19:00 · Cafe Krone", "Leonie is counting on you.",
            "Are you really ready to disappoint her?",
            "This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting you. Cancelling lands on her side of the screen.",
            "Almost every date leaves you glad you went. The hard part is only the front door.",
            "She chose you out of everyone. That's rare — showing up honours it.",
            "You'll go to sleep proud you stayed real and kept your word.",
            "Sometimes life happens — find a solution with Leonie!",
            "Would a later time or another place work? Request it — if Leonie agrees, your date goes ahead.",
            "Change meet time", "Change location",
            "Cancelling this date counts as a no-show and lowers your Show-up Rate by 10 — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely.",
            "Don't cancel my date", "Cancel anyway and let her down",
            "This can't be undone — your date is off and you'll never match Leonie again.",
        )
        onAllNodesWithText("Message Leonie instead").assertCountEquals(0)
        onAllNodesWithTag(CANCEL_REASON_TAG).assertCountEquals(3)
        val order = listOf(
            CANCEL_PORTRAIT_TAG, CANCEL_HEADLINE_TAG, CANCEL_SOLUTION_TAG, CANCEL_COST_TAG, CANCEL_KEEP_TAG, CANCEL_CONFIRM_TAG, CANCEL_WARNING_TAG,
        ).map { onNodeWithTag(it).bounds().top }
        assertEquals(order.sorted(), order)
        val reasons = onAllNodesWithTag(CANCEL_REASON_TAG).fetchSemanticsNodes().map { it.positionInRoot.y }
        assertTrue(onNodeWithTag(CANCEL_HEADLINE_TAG).bounds().top < reasons.first() && reasons.last() < onNodeWithTag(CANCEL_SOLUTION_TAG).bounds().top)
    }

    @Test
    fun `the portrait is 244 tall`() = runComposeUiTest {
        show(ui())
        val h = onNodeWithTag(CANCEL_PORTRAIT_TAG).bounds().height
        assertEquals(with(density) { 244.dp.toPx() }, h, 1f)
    }

    @Test
    fun `B's tiles and the buttons send their taps`() = runComposeUiTest {
        val tiles = mutableListOf<ChangeKind>()
        var keep = 0
        var cancel = 0
        show(ui(), CancelActions(onKeep = { keep++ }, onTile = { tiles += it }, onCancel = { cancel++ }))
        onNodeWithTag(CANCEL_TILE_TIME_TAG).tap()
        onNodeWithTag(CANCEL_TILE_PLACE_TAG).tap()
        onNodeWithTag(CANCEL_KEEP_TAG).tap()
        onNodeWithTag(CANCEL_CONFIRM_TAG).tap()
        assertEquals(listOf(ChangeKind.Time, ChangeKind.Place), tiles)
        assertEquals(1, keep)
        assertEquals(1, cancel)
    }

    @Test
    fun `Change meet time hides with no later slot`() = runComposeUiTest {
        show(ui(slots = 0))
        gone(CANCEL_TILE_TIME_TAG)
        onNodeWithTag(CANCEL_TILE_PLACE_TAG).assertExists()
    }

    // ── C ────────────────────────────────────────────────────────────────────

    @Test
    fun `C adds Message instead, after the tiles, and it opens the chat`() = runComposeUiTest {
        var message = 0
        show(ui(chatOpen = true), CancelActions(onMessage = { message++ }))
        assertText("Message Leonie instead")
        onNodeWithTag(CANCEL_MESSAGE_TAG).tap()
        assertEquals(1, message)
        assertTrue(onNodeWithTag(CANCEL_TILE_PLACE_TAG).bounds().bottom <= onNodeWithTag(CANCEL_MESSAGE_TAG).bounds().top)
        assertTrue(onNodeWithTag(CANCEL_MESSAGE_TAG).bounds().bottom <= onNodeWithTag(CANCEL_COST_TAG).bounds().top)
    }

    // ── D ────────────────────────────────────────────────────────────────────

    @Test
    fun `D shows the confirmation line in place of the tiles, and the choices are unchanged`() = runComposeUiTest {
        show(ui(chatOpen = true, sent = SentRequest(ChangeKind.Time, "20:00")))
        assertText("20:00 requested. Waiting for Leonie to agree — until then your date stays as planned.")
        gone(CANCEL_TILE_TIME_TAG)
        gone(CANCEL_TILE_PLACE_TAG)
        assertText("Would a later time or another place work?", "Don't cancel my date", "Cancel anyway and let her down")
    }

    // ── E ────────────────────────────────────────────────────────────────────

    @Test
    fun `E puts the box between the cost box and the choices, with the buttons unchanged`() = runComposeUiTest {
        show(ui(failed = true))
        assertText("Couldn't cancel the date. Check your connection and try again.")
        val cost = onNodeWithTag(CANCEL_COST_TAG).bounds()
        val box = onNodeWithTag(END_ERROR_TAG).bounds()
        val keep = onNodeWithTag(CANCEL_KEEP_TAG).bounds()
        assertTrue(cost.bottom <= box.top && box.bottom <= keep.top)
        assertText("Don't cancel my date", "Cancel anyway and let her down")
    }

    @Test
    fun `a calm sheet has no error box`() = runComposeUiTest {
        show(ui())
        gone(END_ERROR_TAG)
    }

    @Test
    fun `both buttons are inert while the cancel is in flight`() = runComposeUiTest {
        var keep = 0
        var cancel = 0
        show(ui(cancelling = true), CancelActions(onKeep = { keep++ }, onCancel = { cancel++ }))
        onNodeWithTag(CANCEL_KEEP_TAG).tap()
        onNodeWithTag(CANCEL_CONFIRM_TAG).tap()
        assertEquals(0, keep)
        assertEquals(0, cancel)
        assertText("Don't cancel my date", "Cancel anyway and let her down")
    }

    // ── I ────────────────────────────────────────────────────────────────────

    @Test
    fun `I keeps the title and Message instead and drops the body line and both tiles`() = runComposeUiTest {
        show(ui(chatOpen = false, now = meet.minusSeconds(30 * 60)))
        assertText("Sometimes life happens — find a solution with Leonie!", "Message Leonie instead")
        gone(CANCEL_SOLUTION_BODY_TAG)
        gone(CANCEL_TILE_TIME_TAG)
        gone(CANCEL_TILE_PLACE_TAG)
        gone(CANCEL_SENT_TAG)
        assertText("Don't cancel my date", "Cancel anyway and let her down", "Show-up Rate by 10")
    }

    @Test
    fun `a request already sent does not show in I`() = runComposeUiTest {
        show(ui(now = meet.minusSeconds(10 * 60), sent = SentRequest(ChangeKind.Time, "20:00")))
        gone(CANCEL_SENT_TAG)
    }

    // ── the cost follows the value ──────────────────────────────────────────────────────────

    @Test
    fun `the cost box reads the value it is given, 10 and then 7`() = runComposeUiTest {
        show(ui(penalty = 7))
        assertText("lowers your Show-up Rate by 7 —")
        onAllNodesWithText("by 10", substring = true).assertCountEquals(0)
    }

    // ── 44 ────────────────────────────────────────────────────────────────────

    @Test
    fun `every target is at least 44 dp each way`() = runComposeUiTest {
        show(ui(chatOpen = true, failed = true))
        val floor = with(density) { 44.dp.toPx() } - 1f
        val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(nodes.size >= 5)
        nodes.forEach { n ->
            assertTrue("target is ${n.size.width} x ${n.size.height}", n.size.width >= floor && n.size.height >= floor)
        }
    }

    // ── A: the link ─────────────────────────────────────────────────────────

    private fun dateUi(checkedIn: Boolean) = DateUi(date = date(checkedIn = checkedIn), now = early, loading = false)

    @Test
    fun `A is the last item of the scroll, below Match Mate`() = runComposeUiTest {
        setContent {
            DateScreen(dateUi(false), zone = berlin, showCancel = true, matchMate = {
                Box(Modifier.testTag(DATE_MATCH_MATE_TAG).size(1.dp))
            })
        }
        val mate = onNodeWithTag(DATE_MATCH_MATE_TAG, useUnmergedTree = true).bounds().bottom
        val link = onNodeWithTag(DATE_CANCEL_TAG, useUnmergedTree = true).bounds().top
        // 24 above it, as the reference's flex column (gap 8) draws it: the 24 spacer plus a gap each side. The
        // link's own 10 padding is inside its box.
        assertEquals(with(density) { (8 + 24 + 8).dp.toPx() }, link - mate, 2f)
    }

    @Test
    fun `A is gone after this user's check-in`() = runComposeUiTest {
        setContent { DateScreen(dateUi(true), zone = berlin, showCancel = false) }
        onAllNodesWithTag(DATE_CANCEL_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    // ── H ────────────────────────────────────────────────────────────────────

    private val cancelled = EndedOutcome("d1", "Leonie", endedByYou = false, reason = OutcomeReason.Cancelled)

    @Test
    fun `H reads the ticket's copy with three rows and the Search CTA`() = runComposeUiTest {
        var found = 0
        setContent { DateEndedScreen(cancelled, photoUrl = null, onFindNext = { found++ }) }
        assertText(
            "Leonie had to cancel — we're truly sorry.",
            "You made room in your day and you were ready to show up. Don't be discouraged - someone true as you is just a like away from meeting you.",
            "Your Show-up Rate stays untouched. A date someone else cancels will never count against you. You did everything right.",
            "Show Up is built on reliability and treasuring each other's time — real, warm, in-person connection. This isn't how our community behaves.",
            "People who repeatedly cancel or don't show up become less visible, and eventually lose access. Leonie carries that — not you.",
            "Find someone who shows up",
        )
        onAllNodesWithTag(OUTCOME_ROW_TAG).assertCountEquals(3)
        onNodeWithTag(CHAT_CTA_TAG).performClick()
        assertEquals(1, found)
        // No back, no review, no "paused".
        onAllNodesWithText("Back").assertCountEquals(0)
        onAllNodesWithText("paused", substring = true).assertCountEquals(0)
        onAllNodesWithText("review", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the chat's own G and H are unchanged, one row and Find your next date`() = runComposeUiTest {
        setContent { DateEndedScreen(cancelled.copy(reason = OutcomeReason.Chat), photoUrl = null, onFindNext = {}) }
        onAllNodesWithTag(OUTCOME_ROW_TAG).assertCountEquals(1)
        assertText("Find your next date")
        onAllNodesWithText("Find someone who shows up").assertCountEquals(0)
    }

    @Test
    fun `a cancel by you is not H`() = runComposeUiTest {
        setContent { DateEndedScreen(cancelled.copy(endedByYou = true), photoUrl = null, onFindNext = {}) }
        onAllNodesWithText("Find someone who shows up").assertCountEquals(0)
    }
}
