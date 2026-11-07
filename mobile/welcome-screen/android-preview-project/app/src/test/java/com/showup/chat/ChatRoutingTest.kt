/*
 * ChatRoutingTest.kt
 * ShowUp · where the Date screen's entry points lead, and what the host reports on the way (SHOWUP-194)
 */
package com.showup.chat

import com.showup.AppRoute
import com.showup.analytics.RecordingAnalytics
import com.showup.date.DateUnread
import com.showup.date.EndedInChat
import com.showup.profile.ProfileScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ChatRoutingTest {
    private val date = ChatSamples.DATE
    private var now = Instant.parse("2026-11-03T16:42:00Z")

    @Test
    fun `the tutorial comes first until Open the chat has been tapped for this date`() {
        assertEquals(AppRoute.ChatTutorial, chatRoute(date.copy(chatTutorialSeen = false), ChatEntry.OpenChat))
        assertEquals(AppRoute.Chat(ChatEntry.OpenChat.wire), chatRoute(date.copy(chatTutorialSeen = true), ChatEntry.OpenChat))
    }

    @Test
    fun `Read and reply is the entry when a message waits, Open chat otherwise`() {
        assertEquals(ChatEntry.OpenChat, chatEntryFor(date))
        assertEquals(ChatEntry.ReadReply, chatEntryFor(date.copy(unread = DateUnread(1, ChatReason.RunningLate, "Late", now))))
        assertEquals(AppRoute.Chat("read_reply"), chatRoute(date, chatEntryFor(date.copy(unread = DateUnread(1, null, "Hi", now)))))
    }

    @Test
    fun `an entry reads back from its wire value, an unknown one as none`() {
        assertEquals(ChatEntry.Tutorial, ChatEntry.fromWire("tutorial"))
        assertEquals(ChatEntry.Notification, ChatEntry.fromWire("notification"))
        assertNull(ChatEntry.fromWire("somewhere"))
    }

    @Test
    fun `an outcome routes to the outcome screen with everything it draws`() {
        assertEquals(
            AppRoute.DateEnded("d1", "Leonie", endedByYou = false, photoUrl = "https://x/p.jpg"),
            outcomeRoute(EndedInChat("d1", "Leonie", endedByYou = false), "https://x/p.jpg"),
        )
    }

    @Test
    fun `the host reports the tutorial's view and shown, then its acknowledgement with the read time`() {
        val analytics = RecordingAnalytics()
        val events = ChatHostEvents(analytics, clock = { now })
        events.tutorialShown(date)
        now = now.plusSeconds(9)
        events.tutorialAcknowledged(date)
        assertEquals(listOf("screen_viewed", "chat_tutorial_shown", "chat_tutorial_acknowledged"), analytics.names())
        val view = analytics.of("screen_viewed").single().properties
        assertEquals("date_chat_tutorial", view["screen_id"])
        assertEquals("Date - Chat tutorial", view["screen_name"])
        assertEquals("date_screen", view["referrer_screen_id"])
        assertEquals(date.id, analytics.of("chat_tutorial_shown").single().properties["date_id"])
        val ack = analytics.of("chat_tutorial_acknowledged").single().properties
        assertEquals(date.id, ack["date_id"])
        assertEquals(9, ack["read_time_s"])
    }

    @Test
    fun `the chat is one screen view per open, the outcome one for G and H alike`() {
        val analytics = RecordingAnalytics()
        val events = ChatHostEvents(analytics, clock = { now })
        events.chatShown(referrer = ProfileScreen.DateChatTutorial)
        events.chatShown(referrer = ProfileScreen.DateScreen)
        events.outcomeShown(referrer = ProfileScreen.DateChat)
        val views = analytics.of("screen_viewed").map { it.properties }
        assertEquals(listOf("date_chat", "date_chat", "date_ended_in_chat"), views.map { it["screen_id"] })
        assertEquals(listOf("Date - Coordinate chat", "Date - Coordinate chat", "Date - Ended in chat"), views.map { it["screen_name"] })
        assertEquals(listOf("date_chat_tutorial", "date_screen", "date_chat"), views.map { it["referrer_screen_id"] })
    }
}
