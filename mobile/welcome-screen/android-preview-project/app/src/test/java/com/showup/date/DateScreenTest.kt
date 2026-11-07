/*
 * DateScreenTest.kt
 * ShowUp · the Date screen's structure (SHOWUP-193): the order, the hoist, what must never appear
 *
 * Measured from the laid-out nodes: "C moves the same card above the time + place card" is a
 * position and a count, not a reading of the code.
 */
package com.showup.date

import androidx.compose.ui.test.performClick
import com.showup.chat.ChatReason
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
class DateScreenTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val meet = Instant.parse("2026-11-03T18:00:00Z")
    private val unlock = Instant.parse("2026-11-03T16:30:00Z")
    private val locked = Instant.parse("2026-11-03T15:00:00Z")

    private fun date(unread: DateUnread? = null, checkedIn: Boolean = false) = ActiveDate(
        id = "d1", meetAt = meet, meetDay = "today", unlockAt = unlock, coordinateOpen = false,
        place = DatePlace("Cafe Krone", "Oranienstrasse 42, Kreuzberg"),
        match = DateMatch("u2", "Leonie", 28, "Neukölln", 1, null, 4),
        motivationYou = "long_term", motivationThem = "long_term",
        sharedVibes = listOf("coffee_chat", "deep_talk"), sharedInterests = listOf("reading", "hiking"),
        unread = unread, checkedIn = checkedIn, chatTutorialSeen = false,
    )

    private fun ui(now: Instant = locked, unread: DateUnread? = null, checkedIn: Boolean = false, copied: Boolean = false) =
        DateUi(date = date(unread, checkedIn), now = now, loading = false, copied = copied)

    private fun SemanticsNodeInteraction.top() = fetchSemanticsNode().positionInRoot.y

    @Test
    fun `A is in the ticket's order, hero to Cancel this date`() = runComposeUiTest {
        // The Match Mate slot renders nothing in the app; a 1dp marker here shows WHERE the slot is.
        setContent {
            DateScreen(ui(), zone = berlin, matchMate = {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.testTag(DATE_MATCH_MATE_TAG).size(1.dp),
                )
            })
        }
        val tops = listOf(
            DATE_HERO_TAG, DATE_TIME_CARD_TAG, DATE_COORDINATE_TAG, DATE_REPORT_TAG, DATE_INTENT_TAG,
            DATE_ACTIONS_TAG, DATE_ABOUT_TAG, DATE_COMMON_TAG, DATE_WORKS_TAG, DATE_MATCH_MATE_TAG, DATE_CANCEL_TAG,
        ).map { onNodeWithTag(it, useUnmergedTree = true).top() }
        assertEquals("the order is fixed", tops.sorted(), tops)
        onNodeWithText("Messaging available from 17:30").assertExists()
    }

    @Test
    fun `C moves the one Coordinate card above the time and place card`() = runComposeUiTest {
        setContent {
            DateScreen(
                ui(now = unlock.plusSeconds(60), unread = DateUnread(1, ChatReason.RunningLate, "Running late!", unlock)),
                zone = berlin,
            )
        }
        val coordinate = onNodeWithTag(DATE_COORDINATE_TAG, useUnmergedTree = true).top()
        val time = onNodeWithTag(DATE_TIME_CARD_TAG, useUnmergedTree = true).top()
        assertTrue("C hoists Coordinate above the time card", coordinate < time)
        onAllNodesWithText("Leonie", substring = false).assertCountEquals(1)
        onNodeWithText("Read & reply").assertExists()
        onNodeWithText("NEW MESSAGE").assertExists()
        onNodeWithText("I'M RUNNING LATE").assertExists()
        onAllNodesWithText("Open chat").assertCountEquals(0)
    }

    @Test
    fun `the change line and tiles stay hidden until Date 05 ships`() = runComposeUiTest {
        setContent { DateScreen(ui(), zone = berlin) }
        assertTrue("no change chip is shown yet", CHANGE_TILES.isEmpty())
        onAllNodesWithText("Request to change the location or meet time.").assertCountEquals(0)
        onAllNodesWithText("Change meet time").assertCountEquals(0)
    }

    @Test
    fun `switched on, A shows the change line and both tiles, and a tile opens its picker`() = runComposeUiTest {
        val tapped = mutableListOf<ChatReason>()
        setContent {
            DateScreen(ui(), zone = berlin, changeTiles = listOf(ChatReason.ChangeTime, ChatReason.ChangePlace), onChangeTile = { tapped += it })
        }
        onNodeWithText("Request to change the location or meet time.").assertExists()
        onNodeWithText("Change location").assertExists()
        onNodeWithText("Change meet time").performClick()
        assertEquals(listOf(ChatReason.ChangeTime), tapped)
    }

    @Test
    fun `the tiles are A's alone -- B never shows them`() = runComposeUiTest {
        setContent { DateScreen(ui(now = unlock), zone = berlin, changeTiles = listOf(ChatReason.ChangeTime, ChatReason.ChangePlace)) }
        onAllNodesWithText("Request to change the location or meet time.").assertCountEquals(0)
        onAllNodesWithText("Change meet time").assertCountEquals(0)
    }

    @Test
    fun `B unlocks the same card with Open chat`() = runComposeUiTest {
        setContent { DateScreen(ui(now = unlock), zone = berlin) }
        onNodeWithText("Open chat").assertExists()
        onAllNodesWithText("Messaging available", substring = true).assertCountEquals(0)
        val coordinate = onNodeWithTag(DATE_COORDINATE_TAG, useUnmergedTree = true).top()
        assertTrue(coordinate > onNodeWithTag(DATE_TIME_CARD_TAG, useUnmergedTree = true).top())
    }

    @Test
    fun `never a percentage, never Order Ride, never Match Mate content, and Report in every state`() = runComposeUiTest {
        var state by mutableStateOf(ui())
        setContent { DateScreen(state, zone = berlin) }
        for (s in listOf(ui(), ui(now = unlock), ui(now = unlock, unread = DateUnread(2, ChatReason.OnMyWay, null, unlock)))) {
            state = s
            waitForIdle()
            onAllNodesWithText("%", substring = true).assertCountEquals(0)
            onAllNodesWithText("Order Ride").assertCountEquals(0)
            onAllNodesWithText("Topics").assertCountEquals(0)
            onAllNodesWithText("Ice breakers").assertCountEquals(0)
            onNodeWithText("Report a problem", substring = true).assertExists()
        }
    }

    @Test
    fun `the footer is the Check-in bar with End date before check-in, End Date after, without moving`() = runComposeUiTest {
        var state by mutableStateOf(ui())
        setContent { DateScreen(state, zone = berlin) }
        onNodeWithText("Check-in").assertExists()
        onNodeWithText("End date").assertExists()
        val tabBarBefore = onNodeWithTag(com.showup.designsystem.TAB_BAR_TAG, useUnmergedTree = true).top()
        state = ui(checkedIn = true)
        waitForIdle()
        onNodeWithText("End Date").assertExists()
        onAllNodesWithText("Check-in").assertCountEquals(0)
        assertEquals(tabBarBefore, onNodeWithTag(com.showup.designsystem.TAB_BAR_TAG, useUnmergedTree = true).top(), 0.5f)
    }

    @Test
    fun `Copy reads Copied while the view model says so`() = runComposeUiTest {
        var state by mutableStateOf(ui())
        setContent { DateScreen(state, zone = berlin) }
        onNodeWithText("Copy").assertExists()
        state = ui(copied = true)
        waitForIdle()
        onNodeWithText("Copied").assertExists()
    }

    @Test
    fun `nothing that is not shared is drawn, so no intent line with different motivations`() = runComposeUiTest {
        val different = date().copy(motivationThem = "casual", sharedVibes = emptyList(), sharedInterests = emptyList())
        setContent { DateScreen(DateUi(date = different, now = locked, loading = false), zone = berlin) }
        onAllNodesWithText("MUTUAL INTENT").assertCountEquals(0)
        onNodeWithText("LEONIE'S SHOW-UP").assertExists()
        onAllNodesWithText("YOU BOTH WANT").assertCountEquals(0)
        onAllNodesWithText("WHAT YOU HAVE IN COMMON").assertCountEquals(0)
    }
}
