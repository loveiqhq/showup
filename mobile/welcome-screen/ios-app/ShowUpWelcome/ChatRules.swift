// ChatRules.swift
// ShowUp · the coordinate chat's rules (SHOWUP-194)
//
// Plain functions, tested with no UI: what the end card, the locked field and the Send button follow, the
// header's countdown, and the two values family L derives from the thread. The model reads them to decide
// and the screen reads them to draw, so the two can never disagree. Android's `ChatRules.kt`.

import Foundation

/// The server's limit on one message: `SendChatMessageDto.body`, 1 to 1000 characters after trimming.
let messageMax = 1000

/// How often the open chat re-reads the thread and the date. New messages arrive by polling -- there is no
/// socket -- and 5 s is the design's call (§3): quick enough to coordinate a meeting, light enough to leave
/// running while the chat is on screen, and only then.
let chatPollSeconds: TimeInterval = 5

/// The cancel guide: not shown, shown with the field locked, or acknowledged and collapsed to one line.
enum Guide: Equatable, Sendable { case none, locked, acked }

/// The End date card: only once an "I can't make it today" message is in the thread, from either person,
/// and not while that chip is selected -- the guide takes its place at the foot of the thread.
func endCardVisible(messages: [ChatMessage], selected: ChatReason?) -> Bool {
    selected != .cantMakeIt && cantMakeItFrom(messages) != nil
}

/// The field and Send wait for `Got it` after "I can't make it today" is picked, and only then.
func fieldLocked(selected: ChatReason?, guide: Guide) -> Bool {
    selected == .cantMakeIt && guide == .locked
}

/// Send is enabled whenever the field has text that is not only spaces, and is not locked.
func canSend(text: String, locked: Bool) -> Bool {
    !locked && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
}

/// `in 1h 18m` to the latest agreed meet time, by whole minutes; nil at and after it, when the header leaves
/// it out (the chat stays open until the date ends, and the ticket gives no copy for after).
func countdown(now: Date, meetAt: Date) -> String? {
    guard now < meetAt else { return nil }
    let minutes = minutesBeforeStart(meetAt: meetAt, now: now)
    return ChatCopy.countdown(hours: minutes / 60, minutes: minutes % 60)
}

/// `message_sent.prefill_edited`: whether the text sent differs from the chip's draft, spaces at either end
/// aside (the server trims them too). Nil for a chip with no draft and for no chip.
func prefillEdited(reason: ChatReason?, sent: String) -> Bool? {
    guard let draft = reason?.draft else { return nil }
    return sent.trimmingCharacters(in: .whitespacesAndNewlines) != draft.trimmingCharacters(in: .whitespacesAndNewlines)
}

/// `cant_make_it_from`: who sent the latest "I can't make it today" message, seen from this phone -- `self`
/// or `match` -- or nil when the thread has none. The server decides it the same way.
func cantMakeItFrom(_ messages: [ChatMessage]) -> String? {
    // A message that did not send never reached the match (Date 02 J): it opens nothing.
    messages.last { $0.reason == .cantMakeIt && !$0.failed }.map { $0.fromYou ? "self" : "match" }
}

/// The header's line before the countdown, from the latest AGREED values: `Today · 19:00 · Cafe Krone`. A
/// date with no place stops at the time.
func headerLine(_ date: ActiveDate, zone: TimeZone) -> String {
    let day = date.meetDay == "tomorrow" ? ChatCopy.tomorrow : ChatCopy.today
    return [day, meetTime(date.meetAt, zone: zone), date.place?.name].compactMap { $0 }.joined(separator: " · ")
}

/// A bubble's time: the clock time it was sent, in the date's zone.
func messageTime(_ sentAt: Date, zone: TimeZone) -> String { meetTime(sentAt, zone: zone) }

/// A message's length as `char_count` counts it on both apps: UTF-16 units, as the server's 1000 limit does.
func charCount(_ text: String) -> Int { text.utf16.count }

/// `text` cut to the server's limit, `messageMax` UTF-16 units as Android's `take` counts them, and never
/// inside a character: a pasted text that runs past it keeps what fits.
func cappedMessage(_ text: String) -> String {
    guard text.utf16.count > messageMax else { return text }
    var out = ""
    for character in text {
        if out.utf16.count + String(character).utf16.count > messageMax { break }
        out.append(character)
    }
    return out
}
