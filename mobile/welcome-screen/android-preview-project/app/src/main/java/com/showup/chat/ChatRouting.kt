/*
 * ChatRouting.kt
 * ShowUp · where the Date screen's chat entry points lead, and what the host reports on the way
 * (SHOWUP-194)
 *
 * Plain functions and one small reporter, so the host in `MainActivity` only wires them: which screen
 * `Open chat` and `Read & reply` open, the entry each is, where an ended date goes, and the three
 * screen views and two tutorial events no model owns.
 */
package com.showup.chat

import com.showup.AppRoute
import com.showup.analytics.AnalyticsTracker
import com.showup.date.ActiveDate
import com.showup.date.EndedInChat
import com.showup.profile.ProfileAnalytics
import com.showup.profile.ProfileScreen
import com.showup.profile.report
import java.time.Duration
import java.time.Instant

/** The tutorial until `Open the chat` has been tapped once for this date, then the chat itself. */
fun chatRoute(date: ActiveDate, entry: ChatEntry): AppRoute =
    if (date.chatTutorialSeen) AppRoute.Chat(entry.wire) else AppRoute.ChatTutorial

/**
 * Which button opened the chat. The Date screen shows `Open chat` (B) or `Read & reply` (C), never
 * both: C is B with an unread message, so the waiting message says which it was.
 */
fun chatEntryFor(date: ActiveDate): ChatEntry = if (date.unread != null) ChatEntry.ReadReply else ChatEntry.OpenChat

/** G or H, with everything they draw. */
fun outcomeRoute(outcome: EndedInChat, photoUrl: String?): AppRoute.DateEnded =
    AppRoute.DateEnded(outcome.dateId, outcome.name, outcome.endedByYou, photoUrl)

/**
 * The events the host reports for the chat's destinations: `screen_viewed` for the tutorial, the chat
 * (B and C to E are one screen, one view per open) and the outcome (G and H are one); the tutorial's
 * `chat_tutorial_shown` and `chat_tutorial_acknowledged`, the second carrying how long it was read.
 */
class ChatHostEvents(
    private val analytics: AnalyticsTracker?,
    private val clock: () -> Instant = Instant::now,
) {
    private var tutorialAt: Instant? = null

    /** The tutorial is on screen, over the Date screen. */
    fun tutorialShown(date: ActiveDate) {
        tutorialAt = clock()
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.DateChatTutorial, ProfileScreen.DateScreen))
        analytics?.report(ChatAnalytics.chatTutorialShown(date.id))
    }

    /** `Open the chat`. */
    fun tutorialAcknowledged(date: ActiveDate) {
        val now = clock()
        val seconds = Duration.between(tutorialAt ?: now, now).seconds.coerceAtLeast(0).toInt()
        analytics?.report(ChatAnalytics.chatTutorialAcknowledged(date.id, seconds))
    }

    /** The chat is shown, from the Date screen or the tutorial. */
    fun chatShown(referrer: ProfileScreen) {
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.DateChat, referrer))
    }

    /** G or H is shown, from the chat or the Date screen. */
    fun outcomeShown(referrer: ProfileScreen) {
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.DateEndedInChat, referrer))
    }
}
