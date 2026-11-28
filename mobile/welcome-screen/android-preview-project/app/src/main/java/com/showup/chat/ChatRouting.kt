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
import com.showup.date.EndedOutcome
import com.showup.date.OutcomeReason
import com.showup.date.PartnerScreen
import com.showup.date.cancel.cancelledOnYou
import com.showup.date.partnerend.partnerEndedScreenId
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
 * both: C is B with an unread message, so the waiting message says which it was. The match's change
 * request card (Date 05 E) is read-and-answer too, so its `Open chat` is `read_reply`. The match's arrival
 * card (Date 03) says `Open chat` whatever else waits.
 */
fun chatEntryFor(date: ActiveDate): ChatEntry =
    if (date.arrival == null && (date.unread != null || date.changeRequest != null)) ChatEntry.ReadReply else ChatEntry.OpenChat

/**
 * `Open chat` on the match's arrival card. The route and the entry are decided from the date AS THE
 * CARD SHOWED IT, before [arrivalSeen] clears the card: read after, the date has no arrival and an unread
 * message would make the entry `read_reply` for a button that said `Open chat`.
 */
fun openChatFromArrivalCard(date: ActiveDate, arrivalSeen: () -> Unit, open: (AppRoute) -> Unit) {
    val route = chatRoute(date, chatEntryFor(date))
    arrivalSeen()
    open(route)
}

/** G or H, with everything they draw. */
fun outcomeRoute(outcome: EndedOutcome, photoUrl: String?): AppRoute.DateEnded =
    // The outcome's own photo first: at the next launch no date is held, and [photoUrl] -- the held
    // date's -- is null, so H drew an initial (the SHOWUP-194 review).
    AppRoute.DateEnded(outcome.dateId, outcome.name, outcome.endedByYou, outcome.photoUrl ?: photoUrl, cancelled = cancelledOnYou(outcome))

/**
 * Date 08's screen: for the match of a Date 06 end (which has a `partnerScreen`, [endedRoute]) and, with Date 09, for both
 * people of a timed-out date. A timed-out outcome may carry no `partnerScreen` (an older server, or the one that sends it
 * to one person only), so Date 09's is derived from the reason. [meetAt] rides the route as an ISO string so a restored
 * route keeps Date 09's `{time}`: the outcome's own, else the held date's.
 */
fun partnerEndedRoute(outcome: EndedOutcome, photoUrl: String?, heldDate: ActiveDate? = null): AppRoute.PartnerEnded {
    val screen = outcome.partnerScreen ?: PartnerScreen.DateTimedOut.takeIf { outcome.reason == OutcomeReason.TimedOut }
    return AppRoute.PartnerEnded(
        outcome.dateId, outcome.name, checkNotNull(screen).wire,
        // The outcome's own photo first, as H does: at the next launch no date is held.
        outcome.photoUrl ?: photoUrl,
        (outcome.meetAt ?: heldDate?.meetAt)?.toString(),
    )
}

/**
 * Where the person was when Date 08 opened, for `referrer_screen_id` (R7). [from] is the chat when the chat found
 * the end. Otherwise it was a read of the Date screen: [heldDate] is what that screen was showing, and a read that
 * finds an end leaves it as it was. None held means this read is the launch's first and nothing was on screen yet.
 * `cameFrom` cannot say this: a launch sets the screen without a `goTo`, so it stays null all session, and a
 * process-death restore brings back a non-null one on what is effectively a relaunch.
 */
fun partnerEndedReferrer(heldDate: ActiveDate?, from: ProfileScreen?): ProfileScreen? =
    from ?: ProfileScreen.DateScreen.takeIf { heldDate != null }

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

    /** G or H is shown, from the chat or the Date screen; [cancelled] is Date 07's H (`date_partner_cancelled`). */
    fun outcomeShown(referrer: ProfileScreen, cancelled: Boolean = false) {
        val screen = if (cancelled) ProfileScreen.DatePartnerCancelled else ProfileScreen.DateEndedInChat
        analytics?.report(ProfileAnalytics.screenViewed(screen, referrer))
    }

    /**
     * Date 08 is shown, one view per showing. [referrer] is where the person was; null on a launch, when nothing led
     * there. Proceed to review reports nothing, and `date_ended` is the server's, never the match's phone.
     */
    fun partnerEndedShown(screen: PartnerScreen, referrer: ProfileScreen?) {
        analytics?.report(ProfileAnalytics.screenViewed(partnerEndedScreenId(screen), referrer))
    }
}
