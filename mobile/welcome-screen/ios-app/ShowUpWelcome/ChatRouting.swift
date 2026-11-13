// ChatRouting.swift
// ShowUp · where the Date screen's chat entry points lead, and what the app reports on the way (SHOWUP-194)
//
// Plain functions and one small reporter, so the app only wires them: which screen `Open chat` and `Read &
// reply` open, the entry each is, where an ended date goes, and the three screen views and two tutorial
// events no model owns. Android's `ChatRouting.kt`.

import Foundation

/// The tutorial until `Open the chat` has been tapped once for this date, then the chat itself.
func chatRoute(_ date: ActiveDate, entry: ChatEntry) -> AppRoute {
    date.chatTutorialSeen ? .chat(entry) : .chatTutorial
}

/// Which button opened the chat. The Date screen shows `Open chat` (B) or `Read & reply` (C), never both: C
/// is B with an unread message, so the waiting message says which it was. The match's arrival card (Date 03) says
/// `Open chat` whatever else waits.
func chatEntryFor(_ date: ActiveDate) -> ChatEntry {
    date.arrival == nil && date.unread != nil ? .readReply : .openChat
}

/// G or H, with everything they draw. The outcome's own photo first: at the next launch no date is held, and
/// `photoURL` -- the held date's -- is nil, so H drew an initial (the SHOWUP-194 review).
func outcomeRoute(_ outcome: EndedInChat, photoURL: URL?) -> AppRoute {
    .dateEnded(outcome, photoURL: outcome.photoUrl ?? photoURL)
}

/// The events the app reports for the chat's destinations: `screen_viewed` for the tutorial, the chat (B and
/// C to E are one screen, one view per open) and the outcome (G and H are one); the tutorial's
/// `chat_tutorial_shown` and `chat_tutorial_acknowledged`, the second carrying how long it was read.
@MainActor
final class ChatHostEvents {
    private let analytics: (any AnalyticsTracking)?
    private let clock: () -> Date
    private var tutorialAt: Date?

    init(analytics: (any AnalyticsTracking)?, clock: @escaping () -> Date = { Date() }) {
        self.analytics = analytics
        self.clock = clock
    }

    /// The tutorial is on screen, over the Date screen.
    func tutorialShown(_ date: ActiveDate) {
        tutorialAt = clock()
        analytics?.report(ProfileAnalytics.screenViewed(.dateChatTutorial, referrer: .dateScreen))
        analytics?.report(ChatAnalytics.chatTutorialShown(dateId: date.id))
    }

    /// `Open the chat`.
    func tutorialAcknowledged(_ date: ActiveDate) {
        let now = clock()
        let seconds = max(0, Int(now.timeIntervalSince(tutorialAt ?? now)))
        analytics?.report(ChatAnalytics.chatTutorialAcknowledged(dateId: date.id, readTimeS: seconds))
    }

    /// The chat is shown, from the Date screen or the tutorial.
    func chatShown(referrer: ProfileScreen) {
        analytics?.report(ProfileAnalytics.screenViewed(.dateChat, referrer: referrer))
    }

    /// G or H is shown, from the chat or the Date screen.
    func outcomeShown(referrer: ProfileScreen) {
        analytics?.report(ProfileAnalytics.screenViewed(.dateEndedInChat, referrer: referrer))
    }
}
