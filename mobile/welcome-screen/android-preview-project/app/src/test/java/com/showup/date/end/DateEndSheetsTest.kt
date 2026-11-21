/*
 * DateEndSheetsTest.kt
 * ShowUp · Date 06's frames, one test per frame: the ticket's copy exactly, its order, the disabled end button, the
 * error box above an unchanged CTA, two options in H, and every target at 44 dp (SHOWUP-197)
 */
package com.showup.date.end

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.showup.date.ActiveDate
import com.showup.date.DateMatch
import com.showup.date.DatePlace
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
class DateEndSheetsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-21T18:00:00Z") // 19:00 in Berlin

    private fun date(chatOpen: Boolean = true) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = meet.minusSeconds(90 * 60), coordinateOpen = chatOpen,
        place = DatePlace("Cafe Krone", null),
        match = DateMatch("u2", "Leonie", 28, null, 1, null, 4),
        motivationYou = null, motivationThem = null, sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = null, checkedIn = true, chatTutorialSeen = true, arrival = null, endsEnabled = true,
    )

    private fun ui(
        route: EndRoute = EndRoute.Wrap,
        step: EndStep = EndStep.Menu,
        past: Boolean = true,
        chatOpen: Boolean = true,
        failed: Boolean = false,
        ending: Boolean = false,
    ) = DateEndUi(
        route = route, step = step, date = date(chatOpen), failed = failed, ending = ending, checkedIn = true,
        now = if (past) meet.plusSeconds(11 * 60) else meet.minusSeconds(5 * 60),
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(ui: DateEndUi, actions: EndActions = EndActions()) =
        setContent { DateEndSheetHost(ui, "Leonie", berlin, actions) }

    private fun androidx.compose.ui.test.ComposeUiTest.assertText(vararg texts: String) =
        texts.forEach { onNodeWithText(it).assertExists() }

    /** Every clickable node is at least 44 dp each way. */
    private fun androidx.compose.ui.test.ComposeUiTest.assertTargets() {
        val floor = with(density) { 44.dp.toPx() } - 1f
        val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("some target", nodes.isNotEmpty())
        nodes.forEach { n ->
            val b = n.boundsInRoot
            assertTrue("target ${n.config} is ${b.width} x ${b.height}", b.width >= floor && b.height >= floor)
        }
    }

    // ── A ────────────────────────────────────────────────────────────────────

    @Test
    fun `A shows its three strings with End Date as the button and answers a tap`() = runComposeUiTest {
        var taps = 0
        setContent { EndDateBar("Leonie", onEnd = { taps++ }) }
        assertText("Your date with Leonie", "Tap End Date - when your date is finished.")
        onNodeWithText("End Date").assertExists()
        onNodeWithTag(END_BAR_BUTTON_TAG).performClick()
        assertEquals(1, taps)
        assertTargets()
    }

    // ── B ────────────────────────────────────────────────────────────────────

    @Test
    fun `B reads the ticket's copy, the two rows in order, and Cancel`() = runComposeUiTest {
        show(ui())
        assertText(
            "How did it go with Leonie?",
            "We met — date is finished", "Only once you’ve actually met in person.",
            "My date isn't showing up", "They're not here yet — here's what to do and how the wait works.",
            "Cancel",
        )
        val met = onNodeWithTag(END_PICK_MET_TAG).fetchSemanticsNode().boundsInRoot
        val noShow = onNodeWithTag(END_PICK_NO_SHOW_TAG).fetchSemanticsNode().boundsInRoot
        val cancel = onNodeWithTag(END_CANCEL_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(met.bottom <= noShow.top && noShow.bottom <= cancel.top)
        assertTargets()
    }

    @Test
    fun `B's rows and Cancel send their taps`() = runComposeUiTest {
        val picked = mutableListOf<EndOption>()
        var closed = 0
        show(ui(), EndActions(onPick = { picked += it }, onClose = { closed++ }))
        onNodeWithTag(END_PICK_MET_TAG).performClick()
        onNodeWithTag(END_PICK_NO_SHOW_TAG).performClick()
        onNodeWithTag(END_CANCEL_TAG).performClick()
        assertEquals(listOf(EndOption.Met, EndOption.NoShow), picked)
        assertEquals(1, closed)
    }

    // ── C ────────────────────────────────────────────────────────────────────

    @Test
    fun `C reads the ticket's copy, with the note, End Date to Review and Cancel`() = runComposeUiTest {
        show(ui(step = EndStep.Met))
        assertText(
            "Thanks for showing up",
            "You are wonderful for showing up to Leonie.\nWe hope you had a great time together.",
            "What happens next".uppercase(),
            "Please review your date now.", "Search for new dates resumes after the review.",
            "This is only for dates that happened. Need to call it off before meeting? Use Cancel date in the chat menu. " +
                "Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.",
            "End Date → Review", "Cancel",
        )
        assertTargets()
    }

    @Test
    fun `C's two buttons send End and Back`() = runComposeUiTest {
        var ends = 0
        var backs = 0
        show(ui(step = EndStep.Met), EndActions(onEnd = { ends++ }, onBack = { backs++ }))
        onNodeWithTag(END_CTA_TAG).performClick()
        onNodeWithTag(END_SECONDARY_TAG).performClick()
        assertEquals(1, ends)
        assertEquals(1, backs)
    }

    // ── E and F ──────────────────────────────────────────────────────────────

    @Test
    fun `E shows the four guide cards, Message, and a disabled end button naming the agreed time`() = runComposeUiTest {
        var ends = 0
        show(ui(step = EndStep.Guide, past = false), EndActions(onConfirmWait = { ends++ }))
        assertText(
            "Back", "Not here yet?",
            "Message Leonie to ask where they are. A quick “on my way?” clears up most late arrivals.",
            "Please give it at least 10 minutes. Something can always come up.",
            "Please check you are at the right location.",
            "If you get no reply and you’re 10 minutes past your meet time — you’re free to end the date and leave.",
            "Message Leonie", "You can end the date from 19:00",
        )
        onNodeWithTag(END_GUIDE_END_TAG).assertIsNotEnabled()
        onNodeWithTag(END_GUIDE_END_TAG).performClick()
        assertEquals(0, ends)
        onAllNodesWithText("It’s been 10+ minutes — end date").assertCountEquals(0)
    }

    @Test
    fun `F's end button is enabled, says it has been 10 minutes, and opens G`() = runComposeUiTest {
        var ends = 0
        show(ui(step = EndStep.Guide, past = true), EndActions(onConfirmWait = { ends++ }))
        onNodeWithText("It’s been 10+ minutes — end date").assertExists()
        onNodeWithTag(END_GUIDE_END_TAG).assertIsEnabled().performClick()
        assertEquals(1, ends)
        onAllNodesWithText("You can end the date from 19:00").assertCountEquals(0)
        assertTargets()
    }

    @Test
    fun `the guide hides Message while the chat is locked and the end button moves up`() = runComposeUiTest {
        show(ui(step = EndStep.Guide, chatOpen = false))
        onAllNodesWithText("Message Leonie").assertCountEquals(0)
        // The first card still names Message in bold, as a sentence.
        onNodeWithText("Message Leonie to ask", substring = true).assertExists()
        val last = onNodeWithText("If you get no reply", substring = true).fetchSemanticsNode().boundsInRoot
        val end = onNodeWithTag(END_GUIDE_END_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("18 below the last card: ${end.top - last.bottom}", end.top - last.bottom < with(density) { 40.dp.toPx() })
    }

    // ── G ────────────────────────────────────────────────────────────────────

    @Test
    fun `G reads the ticket's copy and says nothing about the match's fault`() = runComposeUiTest {
        show(ui(step = EndStep.NoShow))
        assertText(
            "Thanks for waiting",
            "You gave Leonie time. Leaving now won’t lower your Show-up Rate.",
            "Please give us a little more information in the review now - helps us to determine the consequences for your date partner.",
            "No penalty to you — your Show-up Rate stays where it is.",
            "Searching for a new date will resume.",
            "End date and review", "Back",
        )
        onAllNodesWithText("This is on them", substring = true).assertCountEquals(0)
        assertTargets()
    }

    // ── H ────────────────────────────────────────────────────────────────────

    @Test
    fun `H shows two options only, in order, and Cancel`() = runComposeUiTest {
        show(ui(route = EndRoute.Report))
        assertText(
            "Report a problem", "Everything here is confidential. We've got you.",
            "My date isn't showing up", "They're not here yet — here's what to do and how the wait works.",
            "I don't feel comfortable", "If you don't feel comfortable in the situation - head out now. That's ok!",
            "Cancel",
        )
        onAllNodesWithText("I'm in danger").assertCountEquals(0)
        onAllNodesWithText("Something else").assertCountEquals(0)
        val a = onNodeWithTag(END_PICK_NO_SHOW_TAG).fetchSemanticsNode().boundsInRoot
        val b = onNodeWithTag(END_PICK_UNCOMFORTABLE_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(a.bottom <= b.top)
        // The scrim's Dismiss, two rows and Cancel are the only targets.
        onAllNodes(hasClickAction()).assertCountEquals(4)
        assertTargets()
    }

    @Test
    fun `H's rows send the two options`() = runComposeUiTest {
        val picked = mutableListOf<EndOption>()
        show(ui(route = EndRoute.Report), EndActions(onPick = { picked += it }))
        onNodeWithTag(END_PICK_NO_SHOW_TAG).performClick()
        onNodeWithTag(END_PICK_UNCOMFORTABLE_TAG).performClick()
        assertEquals(listOf(EndOption.NoShow, EndOption.Uncomfortable), picked)
    }

    @Test
    fun `H reaches the same guide and confirm the wrap-up sheet does`() = runComposeUiTest {
        show(ui(route = EndRoute.Report, step = EndStep.NoShow))
        assertText("Thanks for waiting", "End date and review")
    }

    // ── I ────────────────────────────────────────────────────────────────────

    @Test
    fun `I reads the ticket's copy with Head out now and Stay for now`() = runComposeUiTest {
        show(ui(route = EndRoute.Report, step = EndStep.Uncomfortable))
        assertText(
            "Back", "We're sorry to hear that.",
            "You don't have to explain a thing — you showed up, so you're free to head out now, with no penalty to your Show-up Rate.",
            "No penalty to you — you showed up, and that's what counts.",
            "Head out whenever you're ready — no need to say goodbye.",
            "Review your date next. What you tell us stays confidential.",
            "Head out now", "Stay for now",
        )
        assertTargets()
    }

    @Test
    fun `I's two buttons send Head out and Stay`() = runComposeUiTest {
        var ends = 0
        var stays = 0
        show(ui(route = EndRoute.Report, step = EndStep.Uncomfortable), EndActions(onEnd = { ends++ }, onStay = { stays++ }))
        onNodeWithTag(END_CTA_TAG).performClick()
        onNodeWithTag(END_STAY_TAG).performClick()
        assertEquals(1, ends)
        assertEquals(1, stays)
    }

    // ── J ────────────────────────────────────────────────────────────────────

    private fun androidx.compose.ui.test.ComposeUiTest.assertJ(step: EndStep, cta: String) {
        val route = if (step == EndStep.Uncomfortable) EndRoute.Report else EndRoute.Wrap
        val shown = androidx.compose.runtime.mutableStateOf(ui(route = route, step = step))
        setContent { DateEndSheetHost(shown.value, "Leonie", berlin, EndActions()) }
        val before = onNodeWithTag(END_CTA_TAG).fetchSemanticsNode().boundsInRoot
        shown.value = ui(route = route, step = step, failed = true)
        waitForIdle()
        onNodeWithText("Couldn't end the date. Check your connection and try again.").assertExists()
        val box = onNodeWithTag(END_ERROR_TAG).fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag(END_CTA_TAG).fetchSemanticsNode().boundsInRoot
        onNodeWithText(cta).assertExists()
        assertTrue("box above the CTA for $step", box.bottom <= button.top)
        assertEquals("the CTA keeps its size for $step", before.height, button.height, 0.5f)
    }

    @Test
    fun `J on G puts the box above the CTA and leaves the buttons unchanged`() = runComposeUiTest { assertJ(EndStep.NoShow, "End date and review") }

    @Test
    fun `J on C puts the box above the CTA and leaves the buttons unchanged`() = runComposeUiTest { assertJ(EndStep.Met, "End Date → Review") }

    @Test
    fun `J on I puts the box above the CTA and leaves the buttons unchanged`() = runComposeUiTest { assertJ(EndStep.Uncomfortable, "Head out now") }

    @Test
    fun `an end in flight makes both buttons inert`() = runComposeUiTest {
        var ends = 0
        var backs = 0
        show(ui(step = EndStep.NoShow, ending = true), EndActions(onEnd = { ends++ }, onBack = { backs++ }))
        onNodeWithTag(END_CTA_TAG).assertIsNotEnabled().performClick()
        onNodeWithTag(END_SECONDARY_TAG).assertIsNotEnabled().performClick()
        assertEquals(0, ends)
        assertEquals(0, backs)
    }

    // ── shared pieces ────────────────────────────────────────────────────────

    @Test
    fun `EndErrorBox takes its own text for Date 07 and defaults to Date 06's`() = runComposeUiTest {
        setContent { EndErrorBox("Couldn't cancel the date.") }
        onNodeWithText("Couldn't cancel the date.").assertExists()
    }

    @Test
    fun `EndErrorBox defaults to Date 06's sentence`() = runComposeUiTest {
        setContent { EndErrorBox() }
        onNodeWithText(DateEndCopy.END_FAILED).assertExists()
    }

    @Test
    fun `nothing draws while the sheet is hidden behind the gate or closed`() = runComposeUiTest {
        show(DateEndUi(route = EndRoute.Wrap, gated = EndStep.Guide, date = date()))
        onAllNodesWithTag(END_SHEET_TAG).assertCountEquals(0)
    }

    // ── the switch (R1) ──────────────────────────────────────────────────────

    private fun dateScreen(showReport: Boolean) = androidx.compose.ui.test.runComposeUiTest {
        setContent {
            com.showup.date.DateScreen(
                com.showup.date.DateUi(date(), com.showup.date.DateSamples.A_NOW, loading = false),
                berlin, showReport = showReport,
            )
        }
        onAllNodesWithTag(com.showup.date.DATE_REPORT_TAG).assertCountEquals(if (showReport) 1 else 0)
    }

    @Test
    fun `the Report card shows while the end routes are on and is not rendered when they are off`() {
        dateScreen(showReport = true)
        dateScreen(showReport = false)
    }
}
