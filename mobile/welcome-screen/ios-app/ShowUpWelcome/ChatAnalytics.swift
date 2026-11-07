// ChatAnalytics.swift
// ShowUp · the coordinate chat's events (SHOWUP-194; family L, registry 1.4.33 / taxonomy 1.4.26)
//
// Builders only: each returns the name and the payload, and the model or the host reports them. Names and
// values are the registry's, never typed at a call site. Every payload carries `date_id`; none carries the
// text of a message, a draft, the guide or the place -- lengths and the chip only. Android's
// `ChatAnalytics.kt`.
//
// NOT FROM HERE: `screen_viewed` (the host's, as every screen's), `date_ended_in_chat` (the server's), and
// the events this ticket retires -- `coordinate_viewed`, `date_status_sent`, `date_status_undone`.

import Foundation

/// `enums.json` §39 `chat_entry`: how the chat was reached.
enum ChatEntry: String, CaseIterable, Sendable {
    case openChat = "open_chat"
    case readReply = "read_reply"
    case tutorial = "tutorial"
    case notification = "notification"

    var wire: String { rawValue }

    /// The entry a wire value names (a route's argument), or nil for one this build does not know.
    static func fromWire(_ wire: String) -> ChatEntry? { ChatEntry(rawValue: wire) }
}

/// `action_chip_used.surface`: the start grid (B) or the dock under the thread (C to E).
enum ChipSurface: String, Sendable {
    case entry = "entry"
    case dock = "dock"

    var wire: String { rawValue }
}

enum ChatAnalytics {
    static let chatTutorialShownName = "chat_tutorial_shown"
    static let chatTutorialAcknowledgedName = "chat_tutorial_acknowledged"
    static let chatOpenedName = "chat_opened"
    static let actionChipUsedName = "action_chip_used"
    static let messageSentName = "message_sent"
    static let messageReceivedName = "message_received"
    static let cancelGuideAcknowledgedName = "cancel_guide_acknowledged"
    static let endDateSheetOpenedName = "end_date_sheet_opened"
    static let chatClosedName = "chat_closed"

    typealias Event = (String, [String: any Sendable])

    /// The tutorial (A) is shown: before the first open of each date's chat, per user.
    static func chatTutorialShown(dateId: String) -> Event {
        (chatTutorialShownName, ["date_id": dateId].merging(Stamp.of(0)) { a, _ in a })
    }

    /// `Open the chat` tapped: `readTimeS` from the tutorial showing.
    static func chatTutorialAcknowledged(dateId: String, readTimeS: Int) -> Event {
        (chatTutorialAcknowledgedName, ["date_id": dateId, "read_time_s": readTimeS].merging(Stamp.of(0)) { a, _ in a })
    }

    /// The chat is shown (B or the thread), once per visit. `isFirst`: the first open of this date's chat.
    static func chatOpened(_ date: ActiveDate, entry: ChatEntry, isFirst: Bool, now: Date) -> Event {
        (chatOpenedName, [
            "date_id": date.id,
            "entry": entry.wire,
            "is_first": isFirst,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Every chip tap, whatever it then opens.
    static func actionChipUsed(_ date: ActiveDate, reason: ChatReason, surface: ChipSurface, now: Date) -> Event {
        (actionChipUsedName, [
            "date_id": date.id,
            "reason": reason.rawValue,
            "surface": surface.wire,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// Send tapped and the message accepted. `reason` and `prefillEdited` are JSON null for a message with
    /// no chip; `prefillEdited` is null for a chip with no draft. `isFirst`: this person's first message
    /// for this date.
    static func messageSent(
        _ date: ActiveDate, reason: ChatReason?, charCount: Int, prefillEdited: Bool?, isFirst: Bool, now: Date
    ) -> Event {
        let reasonValue: any Sendable = reason?.rawValue ?? JSONNull()
        let editedValue: any Sendable = prefillEdited ?? JSONNull()
        return (messageSentName, [
            "date_id": date.id,
            "reason": reasonValue,
            "char_count": charCount,
            "prefill_edited": editedValue,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
            "is_first": isFirst,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// One of the match's messages is shown in the thread, once per message.
    static func messageReceived(dateId: String, reason: ChatReason?, charCount: Int) -> Event {
        let reasonValue: any Sendable = reason?.rawValue ?? JSONNull()
        return (messageReceivedName, [
            "date_id": dateId,
            "reason": reasonValue,
            "char_count": charCount,
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// `Got it` on the cancel guide: `readTimeS` from the guide showing.
    static func cancelGuideAcknowledged(_ date: ActiveDate, readTimeS: Int, now: Date) -> Event {
        (cancelGuideAcknowledgedName, [
            "date_id": date.id,
            "read_time_s": readTimeS,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// `End date` on the card: the sheet opens. Keeping the date is this with no `date_ended_in_chat` after it.
    static func endDateSheetOpened(_ date: ActiveDate, cantMakeItFrom: String, now: Date) -> Event {
        (endDateSheetOpenedName, [
            "date_id": date.id,
            "cant_make_it_from": cantMakeItFrom,
            "minutes_before_start": minutesBeforeStart(meetAt: date.meetAt, now: now),
        ].merging(Stamp.of(0)) { a, _ in a })
    }

    /// The person leaves the chat (back, or a hand-off that replaces it): this visit's messages and length.
    static func chatClosed(dateId: String, messagesSent: Int, durationS: Int) -> Event {
        (chatClosedName, [
            "date_id": dateId,
            "messages_sent": messagesSent,
            "duration_s": durationS,
        ].merging(Stamp.of(0)) { a, _ in a })
    }
}
