// DateEndSamples.swift
// ShowUp · SHOWUP-197's frames A to J drawn still, with nothing wired -- the End Date bar, the wrap sheet's menu and
// its steps, the report sheet, Date 03's gate with the note, and the failed end. One copy for the previews
// (`DateEndPreviews`), the debug States page and the evidence harness, as `ChangeSamples` is for 196. Android's
// `DateEndSamples.kt`.
//
// The values are the kit's (`screen-wrap-report-reference.jsx`): Leonie, 19:00 at Cafe Krone on the Date screen's
// sample day (3 Nov 2026, 19:00 Berlin = 18:00Z). The sheet's clock is 17:42 before the meet time (E and the menus)
// and 19:14 after it (F, G and J).

import SwiftUI

enum DateEndSamples {
    static let zone = DateSamples.zone
    private static let name = DateSamples.date.match.name

    /// The sheet's clock once the meet time has passed: 19:14 Berlin.
    static let pastNow = DateSamples.meet.addingTimeInterval(14 * 60)

    /// The date these frames draw: the chat open, the end routes on, and (by default) this person checked in.
    private static func date(checkedIn: Bool = true) -> ActiveDate {
        var d = DateSamples.date
        d.coordinateOpen = true
        d.chatTutorialSeen = true
        d.endsEnabled = true
        d.checkedIn = checkedIn
        return d
    }

    private static func ui(_ step: EndStep, route: EndRoute = .wrap, now: Date = DateSamples.bNow, failed: Bool = false,
                           checkedIn: Bool = true) -> DateEndUi {
        var u = DateEndUi()
        u.route = route
        u.step = step
        u.date = date(checkedIn: checkedIn)
        u.now = now
        u.failed = failed
        u.checkedIn = checkedIn
        return u
    }

    /// The Date screen the sheet is drawn over: the End Date bar once checked in, the Check-in bar before.
    private static func dateScreen(checkedIn: Bool = true, now: Date = DateSamples.bNow) -> some View {
        DateScreen(ui: DateUi(date: date(checkedIn: checkedIn), now: now, loading: false), zone: zone,
                   checkInBar: { AnyView(CheckInBar(name: name, onCheckIn: {})) },
                   endDateBar: { AnyView(EndDateBar(name: name, onEnd: {})) })
    }

    /// `scrollToEnd` is the evidence harness's: a sheet taller than the 375 x 667 frame scrolls inside its shell, and the
    /// picture is taken at the end of that scroll where what tells the frame apart (E, F and I's buttons) sits.
    private static func sheet(_ ui: DateEndUi, scrollToEnd: Bool) -> some View {
        DateEndOver(visible: true, name: name, zone: zone, ui: ui, actions: EndActions()) {
            dateScreen(checkedIn: ui.checkedIn, now: ui.now)
        }
        .environment(\.sheetScrollsToEnd, scrollToEnd)
    }

    /// Frame `id` ("A" to "J"), drawn as the ticket's kit props draw it.
    @ViewBuilder
    static func frame(_ id: String, scrollToEnd: Bool = false) -> some View {
        switch id {
        case "A": dateScreen()
        case "B": sheet(ui(.menu), scrollToEnd: scrollToEnd)
        case "C": sheet(ui(.met), scrollToEnd: scrollToEnd)
        // Date 03's sheets, consumed: the gate with the note, over the Date screen before a check-in.
        case "D":
            ArrivalOver(ui: GateUi(phase: .confirm(entry: .report, error: nil, busy: false, note: ArrivalCopy.reportNote)),
                        name: name, onYes: {}, onNotYet: {}, onClose: {}, onUndo: {}) {
                dateScreen(checkedIn: false)
            }
        case "E": sheet(ui(.guide), scrollToEnd: scrollToEnd)
        case "F": sheet(ui(.guide, now: pastNow), scrollToEnd: scrollToEnd)
        case "G": sheet(ui(.noShow, now: pastNow), scrollToEnd: scrollToEnd)
        case "H": sheet(ui(.menu, route: .report), scrollToEnd: scrollToEnd)
        case "I": sheet(ui(.uncomfortable, route: .report), scrollToEnd: scrollToEnd)
        case "J": sheet(ui(.noShow, now: pastNow, failed: true), scrollToEnd: scrollToEnd)
        default: Text("Unknown end frame \(id)")
        }
    }

    /// The ticket's frame letters, in order.
    static let letters = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"]
}
