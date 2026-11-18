/*
 * DateChangeScreenTest.kt
 * ShowUp · Date 05 on the Date screen: D (own waiting suggestions), G (agreed), E and J (the request card), I (the
 * tiles) and L (I agree failed) (SHOWUP-196)
 *
 * Copy is read from the tree, sizes and order are measured from the laid-out nodes. The screen is rendered whole, so
 * the hoisting order and the card's wiring are the screen's own.
 */
package com.showup.date

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.showup.change.ChangeKind
import com.showup.change.ChangePlace
import com.showup.change.ChangeSuggestion
import com.showup.change.DateChange
import com.showup.change.PlaceType
import com.showup.chat.ChatReason
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
class DateChangeScreenTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // The binding meet time is 19:00 Berlin; the chat opens at 17:30; a suggestion is 19:30, answer by 18:30.
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val locked = Instant.parse("2026-11-03T15:00:00Z")
    private val open = Instant.parse("2026-11-03T16:45:00Z")
    private val laterTime = Instant.parse("2026-11-03T18:30:00Z")
    private val deadline = Instant.parse("2026-11-03T17:30:00Z")
    private val sent = Instant.parse("2026-11-03T14:00:00Z")

    private fun timeSuggestion(byYou: Boolean, id: String = "t1") = ChangeSuggestion(
        id = id, kind = ChangeKind.Time, byYou = byYou, time = laterTime, place = null,
        wasTime = meet, wasPlaceName = null, deadline = deadline, createdAt = sent,
    )

    private fun placeSuggestion(byYou: Boolean, id: String = "p1") = ChangeSuggestion(
        id = id, kind = ChangeKind.Place, byYou = byYou, time = null,
        place = ChangePlace("pl1", "Bar Lenz", PlaceType.Bar, "Reichenberger Str. 9"),
        wasTime = meet, wasPlaceName = "Cafe Krone", deadline = deadline, createdAt = sent,
    )

    private fun change(
        pending: List<ChangeSuggestion> = emptyList(),
        timeWas: Instant? = null,
        placeWas: String? = null,
    ) = DateChange(meet.minusSeconds(1800), true, listOf(laterTime), timeWas, placeWas, pending)

    private fun date(
        change: DateChange? = change(),
        request: ChangeSuggestion? = null,
        unread: DateUnread? = null,
        arrival: DateArrival? = null,
        meetAt: Instant = meet,
        placeName: String = "Cafe Krone",
    ) = ActiveDate(
        id = "d1", meetAt = meetAt, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace(placeName, "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = emptyList(), sharedInterests = emptyList(),
        unread = unread, checkedIn = false, chatTutorialSeen = true, arrival = arrival,
        change = change, changeRequest = request,
    )

    private fun ui(d: ActiveDate, now: Instant = locked, agreeing: Boolean = false, agreeFailed: Boolean = false) =
        DateUi(date = d, now = now, loading = false, agreeing = agreeing, agreeFailed = agreeFailed)

    // ── D · the viewer's own waiting suggestions ──────────────────────────────────────────────

    @Test
    fun `D two own pending suggestions show two waiting tags and exactly one compact note, the binding values stay big`() = runComposeUiTest {
        val d = date(change = change(pending = listOf(timeSuggestion(true), placeSuggestion(true))))
        setContent { DateScreen(ui(d), zone = berlin) }
        onAllNodesWithText("Waiting for Leonie to agree").assertCountEquals(2)
        onNodeWithText("You suggested 19:30").assertExists()
        onNodeWithText("You suggested Bar Lenz").assertExists()
        // The note is one, with the CURRENT binding values and the binding deadline (19:00 - 0:30).
        onAllNodesWithTag(DATE_BINDING_NOTE_TAG).assertCountEquals(1)
        onNodeWithText("19:00 at Cafe Krone stays binding unless Leonie agrees by 18:30.", substring = true).assertExists()
        // Big values are the BINDING ones: the suggested values appear only inside the tags.
        onNodeWithText("19:00").assertExists()
        onNodeWithText("Cafe Krone").assertExists()
        onAllNodesWithText("19:30").assertCountEquals(0)
        onAllNodesWithText("Bar Lenz").assertCountEquals(0)
        // The tags sit under their value, inside the card; the note is the card's last element.
        val note = onNodeWithTag(DATE_BINDING_NOTE_TAG).fetchSemanticsNode().positionInRoot.y
        assertTrue(onNodeWithTag(DATE_TIME_TAG).fetchSemanticsNode().positionInRoot.y < onNodeWithTag(DATE_PLACE_TAG).fetchSemanticsNode().positionInRoot.y)
        assertTrue(onNodeWithTag(DATE_PLACE_TAG).fetchSemanticsNode().positionInRoot.y < note)
    }

    @Test
    fun `D one own time suggestion shows one tag and the note`() = runComposeUiTest {
        setContent { DateScreen(ui(date(change = change(pending = listOf(timeSuggestion(true))))), zone = berlin) }
        onAllNodesWithText("Waiting for Leonie to agree").assertCountEquals(1)
        onAllNodesWithTag(DATE_PLACE_TAG).assertCountEquals(0)
        onAllNodesWithTag(DATE_BINDING_NOTE_TAG).assertCountEquals(1)
    }

    @Test
    fun `the match's pending suggestion never appears on the purple card`() = runComposeUiTest {
        // It is in the payload's pending list (both people's are), but not in changeRequest: the card is gone, as after a read.
        val d = date(change = change(pending = listOf(timeSuggestion(false), placeSuggestion(false))))
        setContent { DateScreen(ui(d), zone = berlin) }
        onAllNodesWithText("Waiting for Leonie to agree").assertCountEquals(0)
        onAllNodesWithText("Waiting for you to agree").assertCountEquals(0)
        onAllNodesWithTag(DATE_TIME_TAG).assertCountEquals(0)
        onAllNodesWithTag(DATE_PLACE_TAG).assertCountEquals(0)
        onAllNodesWithTag(DATE_BINDING_NOTE_TAG).assertCountEquals(0)
        onAllNodesWithText("19:30").assertCountEquals(0)
    }

    // ── G · agreed ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `G the new value is big with Changed was the original, and no Agreed pill`() = runComposeUiTest {
        val d = date(meetAt = laterTime, placeName = "Bar Lenz", change = change(timeWas = meet, placeWas = "Cafe Krone"))
        setContent { DateScreen(ui(d), zone = berlin) }
        onNodeWithText("19:30").assertExists()
        onNodeWithText("Bar Lenz").assertExists()
        onNodeWithText("Changed · was 19:00").assertExists()
        onNodeWithText("Changed · was Cafe Krone").assertExists()
        onAllNodesWithText("Agreed", substring = true).assertCountEquals(0)
        onAllNodesWithTag(DATE_BINDING_NOTE_TAG).assertCountEquals(0)
    }

    @Test
    fun `G the tag shows the ORIGINAL only and is hidden after the meet time`() = runComposeUiTest {
        val d = date(meetAt = laterTime, change = change(timeWas = meet))
        setContent { DateScreen(ui(d, now = laterTime.plusSeconds(1)), zone = berlin) }
        onAllNodesWithText("Changed · was 19:00").assertCountEquals(0)
        onAllNodesWithTag(DATE_TIME_TAG).assertCountEquals(0)
    }

    // ── E · the request card, the chat open ─────────────────────────────────────────────────

    @Test
    fun `E a time request shows the ticket's copy, Open chat first and I agree second, 48 high with a gap of 8`() = runComposeUiTest {
        val d = date(request = timeSuggestion(false), change = change(pending = listOf(timeSuggestion(false))))
        val agrees = mutableListOf<Unit>()
        val opens = mutableListOf<Unit>()
        setContent { DateScreen(ui(d, now = open), zone = berlin, onAgree = { agrees += Unit }, onOpenChat = { opens += Unit }) }
        onNodeWithText("Leonie").assertExists()
        onNodeWithText("CHANGE REQUEST").assertExists()
        onNodeWithText("needs your answer · 15:00").assertExists()
        onNodeWithText("CHANGE MEET TIME").assertExists()
        onNodeWithText("Leonie suggested").assertExists()
        onNodeWithText("19:30").assertExists()
        onNodeWithText("Answer by 18:30").assertExists()
        onNodeWithText("Until you agree, 19:00 at Cafe Krone stays binding. Not being there counts as a no-show.", substring = true).assertExists()
        onNodeWithText("1").assertExists()
        // No locked line, no Suggest another, no `Messaging opens at` while the chat is open.
        onAllNodesWithText("Messaging opens at 17:30").assertCountEquals(0)
        onAllNodesWithText("Suggest another").assertCountEquals(0)
        val first = onNodeWithTag(DATE_REQUEST_FIRST_TAG).fetchSemanticsNode()
        val agree = onNodeWithTag(DATE_REQUEST_AGREE_TAG).fetchSemanticsNode()
        assertTrue("Open chat is left of I agree", first.boundsInRoot.left < agree.boundsInRoot.left)
        val dp = first.layoutInfo.density.density
        assertEquals(48f, first.size.height / dp, 0.5f)
        assertEquals(48f, agree.size.height / dp, 0.5f)
        assertEquals("gap 8", 8f, (agree.boundsInRoot.left - first.boundsInRoot.right) / dp, 0.5f)
        onNodeWithTag(DATE_REQUEST_FIRST_TAG).performClick()
        assertEquals(1, opens.size)
        assertEquals(0, agrees.size)
        onNodeWithTag(DATE_REQUEST_AGREE_TAG).performClick()
        assertEquals(1, agrees.size)
    }

    @Test
    fun `E a place request shows the value, the old place struck and the type and address`() = runComposeUiTest {
        val d = date(request = placeSuggestion(false))
        setContent { DateScreen(ui(d, now = open), zone = berlin) }
        onNodeWithText("CHANGE LOCATION").assertExists()
        onNodeWithText("Bar Lenz").assertExists()
        onNodeWithText("Bar · Reichenberger Str. 9").assertExists()
        // The old place is the card's struck one next to the value, and the purple card's value: two.
        onAllNodesWithText("Cafe Krone").assertCountEquals(2)
    }

    @Test
    fun `E the request card is hoisted above the time and place card`() = runComposeUiTest {
        setContent { DateScreen(ui(date(request = timeSuggestion(false)), now = open), zone = berlin) }
        val coordinate = onNodeWithTag(DATE_COORDINATE_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        val time = onNodeWithTag(DATE_TIME_CARD_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        assertTrue(coordinate < time)
    }

    // ── J · the request card, the chat locked ───────────────────────────────────────────────

    @Test
    fun `J locked, Suggest another is first, I agree second and Messaging opens at shows, in place of the locked card`() = runComposeUiTest {
        val d = date(request = timeSuggestion(false), change = change(pending = listOf(timeSuggestion(false))))
        val counters = mutableListOf<ChangeKind>()
        setContent { DateScreen(ui(d, now = locked), zone = berlin, onSuggestAnother = { counters += it }) }
        onNodeWithText("Suggest another").assertExists()
        onNodeWithText("I agree").assertExists()
        onNodeWithText("Messaging opens at 17:30").assertExists()
        onAllNodesWithText("Open chat").assertCountEquals(0)
        onAllNodesWithText("Messaging available from 17:30").assertCountEquals(0)
        onAllNodesWithText("Change meet time").assertCountEquals(0)
        val first = onNodeWithTag(DATE_REQUEST_FIRST_TAG).fetchSemanticsNode()
        val agree = onNodeWithTag(DATE_REQUEST_AGREE_TAG).fetchSemanticsNode()
        assertTrue(first.boundsInRoot.left < agree.boundsInRoot.left)
        onNodeWithTag(DATE_REQUEST_FIRST_TAG).performClick()
        assertEquals(listOf(ChangeKind.Time), counters)
    }

    // ── I · the locked card's tiles ─────────────────────────────────────────────────────────

    @Test
    fun `I the tiles show from the payload and each opens its picker`() = runComposeUiTest {
        val tapped = mutableListOf<ChatReason>()
        setContent { DateScreen(ui(date()), zone = berlin, onChangeTile = { tapped += it }) }
        onNodeWithText("Request to change the location or meet time.").assertExists()
        onNodeWithText("Change meet time").performClick()
        onNodeWithText("Change location").performClick()
        assertEquals(listOf(ChatReason.ChangeTime, ChatReason.ChangePlace), tapped)
    }

    @Test
    fun `I the tiles are replaced by the request card while one is open`() = runComposeUiTest {
        setContent { DateScreen(ui(date(request = placeSuggestion(false))), zone = berlin) }
        onAllNodesWithText("Request to change the location or meet time.").assertCountEquals(0)
        onNodeWithText("Suggest another").assertExists()
    }

    // ── L · I agree failed ───────────────────────────────────────────────────────────────────

    @Test
    fun `L a failed agree shows the red line under the buttons, and a tap on it or on I agree retries`() = runComposeUiTest {
        val agrees = mutableListOf<Unit>()
        val d = date(request = timeSuggestion(false))
        setContent { DateScreen(ui(d, now = open, agreeFailed = true), zone = berlin, onAgree = { agrees += Unit }) }
        onNodeWithText("Couldn't agree. Check your connection and try again.", useUnmergedTree = true).assertExists()
        val buttons = onNodeWithTag(DATE_REQUEST_AGREE_TAG).fetchSemanticsNode().boundsInRoot.bottom
        val line = onNodeWithTag(DATE_REQUEST_RETRY_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the line is under the buttons", line.top >= buttons - 1f)
        onNodeWithTag(DATE_REQUEST_RETRY_TAG).performClick()
        onNodeWithTag(DATE_REQUEST_AGREE_TAG).performClick()
        assertEquals(2, agrees.size)
        // Nothing else changed: the purple card still shows the binding values.
        assertTrue(onAllNodesWithText("19:00").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `L no line without a failure`() = runComposeUiTest {
        setContent { DateScreen(ui(date(request = timeSuggestion(false)), now = open), zone = berlin) }
        onAllNodesWithTag(DATE_REQUEST_RETRY_TAG).assertCountEquals(0)
    }

    @Test
    fun `while I agree is on its way both buttons are inert`() = runComposeUiTest {
        val agrees = mutableListOf<Unit>()
        val opens = mutableListOf<Unit>()
        setContent {
            DateScreen(ui(date(request = timeSuggestion(false)), now = open, agreeing = true), zone = berlin, onAgree = { agrees += Unit }, onOpenChat = { opens += Unit })
        }
        onNodeWithTag(DATE_REQUEST_AGREE_TAG).assertIsNotEnabled()
        onNodeWithTag(DATE_REQUEST_FIRST_TAG).assertIsNotEnabled()
        onNodeWithTag(DATE_REQUEST_AGREE_TAG).performClick()
        onNodeWithTag(DATE_REQUEST_FIRST_TAG).performClick()
        assertEquals(0, agrees.size + opens.size)
    }

    // ── the hoisting order: arrival, request, message ───────────────────────────────────────

    @Test
    fun `the request outranks an unread message`() = runComposeUiTest {
        val unread = DateUnread(1, ChatReason.RunningLate, "Running late!", open)
        setContent { DateScreen(ui(date(request = timeSuggestion(false), unread = unread), now = open), zone = berlin) }
        onNodeWithText("CHANGE REQUEST").assertExists()
        onAllNodesWithText("Read & reply").assertCountEquals(0)
        onAllNodesWithText("NEW MESSAGE").assertCountEquals(0)
    }

    @Test
    fun `the arrival outranks the request`() = runComposeUiTest {
        setContent { DateScreen(ui(date(request = timeSuggestion(false), arrival = DateArrival(open)), now = open), zone = berlin) }
        onNodeWithText("ARRIVED").assertExists()
        onAllNodesWithText("CHANGE REQUEST").assertCountEquals(0)
    }
}
