// ChatThread.swift
// ShowUp · what the thread shows: messages, suggestion cards and system lines in one time order (SHOWUP-196)
//
// Android's `ChatThread.kt`. The server keeps messages and suggestions apart (a suggestion is NOT a message: no
// `message_sent`, no unread), so the phone merges them for the eye only, oldest first. A card sits at `createdAt`;
// the system line an answer leaves sits at `answeredAt` (agreed) or `expiredAt` (expired) -- the card keeps its own
// place. A replaced card stays where it was and posts no line. Messages that failed to send stay at the foot, as
// before: they were never in the server's order.

import Foundation

enum ThreadItem: Equatable, Identifiable {
    case message(ChatMessage)
    case card(ChangeSuggestion)
    /// `agreed`: the success tone and `check`; otherwise the muted tone and `clock` (an expiry).
    case system(id: String, text: String, agreed: Bool, at: Date)

    var id: String {
        switch self {
        case .message(let message): return "message:\(message.id)"
        case .card(let suggestion): return "card:\(suggestion.id)"
        case .system(let id, _, _, _): return "system:\(id)"
        }
    }

    var at: Date {
        switch self {
        case .message(let message): return message.sentAt
        case .card(let suggestion): return suggestion.createdAt
        case .system(_, _, _, let at): return at
        }
    }

    /// Rank on a tie: what was said, then the card, then what the answer left.
    fileprivate var rank: Int {
        switch self {
        case .message: return 0
        case .card: return 1
        case .system: return 2
        }
    }
}

/// The line a suggestion's answer leaves, in the client's words from the server's facts; nil when it left none
/// (pending, replaced) or the facts are missing. All times are `HH:mm` in `zone`.
func systemLine(for s: ChangeSuggestion, zone: TimeZone) -> ThreadItem? {
    switch s.status {
    case .agreed:
        guard let at = s.answeredAt else { return nil }
        let text: String?
        switch s.kind {
        case .time: text = s.time.map { ChangeCopy.systemTime(changeClock($0, zone: zone)) }
        case .place: text = s.place.map { ChangeCopy.systemPlace($0.name) }
        }
        return text.map { ThreadItem.system(id: s.id, text: $0, agreed: true, at: at) }
    case .expired:
        guard let at = s.expiredAt, let time = s.expiredBindingTime, let place = s.expiredBindingPlaceName else { return nil }
        let text = ChangeCopy.systemExpired(deadline: changeClock(s.deadline, zone: zone),
                                            time: changeClock(time, zone: zone), place: place)
        return ThreadItem.system(id: s.id, text: text, agreed: false, at: at)
    case .pending, .replaced:
        return nil
    }
}

/// Messages and suggestions interleaved by time, oldest first (stable), then the messages that failed to send.
func buildThread(messages: [ChatMessage], suggestions: [ChangeSuggestion], zone: TimeZone) -> [ThreadItem] {
    var all: [ThreadItem] = messages.filter { !$0.failed }.map { ThreadItem.message($0) }
    all += suggestions.map { ThreadItem.card($0) }
    all += suggestions.compactMap { systemLine(for: $0, zone: zone) }
    // `sorted` is not stable in Swift: the offset keeps equal times in the order they were given.
    let merged = all.enumerated()
        .sorted { a, b in
            if a.element.at != b.element.at { return a.element.at < b.element.at }
            if a.element.rank != b.element.rank { return a.element.rank < b.element.rank }
            return a.offset < b.offset
        }
        .map { $0.element }
    return merged + messages.filter { $0.failed }.map { ThreadItem.message($0) }
}
