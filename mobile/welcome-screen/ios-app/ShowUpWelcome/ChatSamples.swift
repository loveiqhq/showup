// ChatSamples.swift
// ShowUp · the spec sheet's frames as values (SHOWUP-194), for previews, the fit sweep and the evidence
//
// The date is the Date screen's sample (Leonie, Cafe Krone, 18:00 Berlin) with the chat open; the clock is
// the sheet's 17:42. C to F are `ScreenChatOpen` as the sheet renders it: C with the running-late chip and
// its draft, D with "I can't make it today" and the guide waiting, E and F with the one message the sheet
// seeds. Android's `ChatSamples.kt`.

import Foundation

enum ChatSamples {
    static let date: ActiveDate = {
        var date = DateSamples.date
        date.coordinateOpen = true
        date.chatTutorialSeen = true
        return date
    }()
    static let now = DateSamples.bNow

    /// The sheet's seed: my "I can't make it today" message at 17:38, read.
    static let cancelSent = ChatMessage(
        id: "c1",
        reason: .cantMakeIt,
        body: "So sorry Leonie, something came up at work and I can't make it. Could we do Saturday instead?",
        fromYou: true,
        sentAt: DateSamples.meet.addingTimeInterval(-82 * 60),
        read: true)

    /// B: no message yet.
    static let b = ChatUi(date: date, now: now, loading: false)

    /// C: `I'm running late` picked from B, its draft in the field.
    static let c: ChatUi = {
        var ui = b
        ui.selected = .runningLate
        ui.draft = ChatReason.runningLate.draft ?? ""
        return ui
    }()

    /// D: `I can't make it today` picked; the field empty and locked behind the guide.
    static let d: ChatUi = {
        var ui = b
        ui.selected = .cantMakeIt
        ui.guide = .locked
        return ui
    }()

    /// E: the cancel message is in the thread, so the End date card follows it.
    static let e: ChatUi = {
        var ui = b
        ui.messages = [cancelSent]
        return ui
    }()

    /// F: E with the End the date sheet open.
    static let f: ChatUi = {
        var ui = e
        ui.endSheet = true
        return ui
    }()

    /// G: you ended it.
    static let g = EndedInChat(dateId: date.id, name: date.match.name, endedByYou: true)

    /// H: the match ended it.
    static let h = EndedInChat(dateId: date.id, name: date.match.name, endedByYou: false)

    /// What the developer menu's "Leonie can't make it" sends, with the cancel chip.
    static let matchCantMakeIt = "So sorry, something came up at work and I can't make it today. Could we do Saturday instead?"
}
