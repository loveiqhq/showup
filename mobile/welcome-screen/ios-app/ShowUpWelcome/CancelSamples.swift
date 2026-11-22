// CancelSamples.swift
// ShowUp · SHOWUP-198's frames A to F, H and I drawn still, with nothing wired -- the red link at the end of the Date
// screen, the guard sheet in its states, the Date screen once checked in, and what the match sees. G is flow 10's and
// has no frame here. One copy for the previews (`CancelPreviews`), the debug States page and the evidence harness.
// Android's `CancelSamples.kt`.
//
// The values are the kit's (`screen-cancel-confirm-reference.jsx`): Leonie, 19:00 at Cafe Krone on the Date screen's
// sample day (3 Nov 2026). The sheet's clock is 15:55 while the chat is locked (B), 17:42 with it open (C, D, E) and
// 18:35 Berlin (17:35Z), five minutes past Date 05's 18:30 cutoff, for I.

import SwiftUI

enum CancelSamples {
    static let zone = DateSamples.zone
    private static let name = DateSamples.date.match.name

    /// I: five minutes past Date 05's cutoff (meet minus 0:30), the chat open.
    static let closedNow = DateSamples.meet.addingTimeInterval(-25 * 60)

    /// The date, not checked in, the cancel link on, a cancel costing 10 points, and Date 05's block of a current server.
    private static func date(chatOpen: Bool, checkedIn: Bool = false) -> ActiveDate {
        var d = ChangeSamples.date
        d.coordinateOpen = chatOpen
        d.chatTutorialSeen = true
        d.endsEnabled = true
        d.checkedIn = checkedIn
        d.cancelPenaltyPoints = 10
        return d
    }

    private static func ui(now: Date, chatOpen: Bool, sent: SentRequest? = nil, failed: Bool = false) -> CancelUi {
        var u = CancelUi()
        u.open = true
        u.sent = sent
        u.changeRequested = sent != nil
        u.failed = failed
        u.date = date(chatOpen: chatOpen)
        u.now = now
        return u
    }

    /// The Date screen, which comes to rest at the end of its scroll when `toEnd` (A and F: the red link, or its
    /// absence, sits last).
    private static func dateScreen(_ date: ActiveDate, now: Date, showCancel: Bool, toEnd: Bool = false) -> some View {
        // Date 03's bar before check-in, Date 06's after: F (checked in) draws the End Date bar, as the sheet does.
        DateScreen(ui: DateUi(date: date, now: now, loading: false), zone: zone, showCancel: showCancel,
                   checkInBar: { AnyView(CheckInBar(name: name, onCheckIn: {})) },
                   endDateBar: { AnyView(EndDateBar(name: name, onEnd: {})) })
            .environment(\.dateScrollsToEnd, toEnd)
    }

    /// `scrollToEnd` is the evidence harness's: the guard sheet is taller than the 375 x 667 frame, and its solution box,
    /// cost box, choices and error sit below the fold.
    private static func sheet(_ ui: CancelUi, scrollToEnd: Bool) -> some View {
        DateEndOver(visible: true, name: name, zone: zone, ui: DateEndUi(), actions: EndActions(), cancelUi: ui,
                    cancelActions: CancelActions()) {
            dateScreen(ui.date ?? date(chatOpen: ui.chatOpen), now: ui.now, showCancel: true)
        }
        .environment(\.sheetScrollsToEnd, scrollToEnd)
    }

    /// Frame `id` ("A" to "F", "H", "I"). `scrollToEnd` drives the guard sheet's scroll to its end (B to E and I); A and F
    /// always come to rest at the Date screen's end.
    @ViewBuilder
    static func frame(_ id: String, scrollToEnd: Bool = false) -> some View {
        switch id {
        case "A": dateScreen(date(chatOpen: false), now: DateSamples.aNow, showCancel: true, toEnd: true)
        case "B": sheet(ui(now: DateSamples.aNow, chatOpen: false), scrollToEnd: scrollToEnd)
        case "C": sheet(ui(now: DateSamples.bNow, chatOpen: true), scrollToEnd: scrollToEnd)
        case "D":
            sheet(ui(now: DateSamples.bNow, chatOpen: true, sent: SentRequest(kind: .time, label: "20:00")),
                  scrollToEnd: scrollToEnd)
        case "E": sheet(ui(now: DateSamples.bNow, chatOpen: true, failed: true), scrollToEnd: scrollToEnd)
        case "F": dateScreen(date(chatOpen: true, checkedIn: true), now: DateSamples.bNow, showCancel: false, toEnd: true)
        case "H":
            DateEndedScreen(outcome: EndedOutcome(dateId: "sample-date", name: name, endedByYou: false, reason: .cancelled),
                            photoURL: nil, onFindNext: {})
        case "I": sheet(ui(now: closedNow, chatOpen: true), scrollToEnd: scrollToEnd)
        default: Text("Unknown cancel frame \(id)")
        }
    }

    /// The ticket's frame letters, in order; G is flow 10's.
    static let letters = ["A", "B", "C", "D", "E", "F", "H", "I"]
}
