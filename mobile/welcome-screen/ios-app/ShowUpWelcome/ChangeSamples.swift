// ChangeSamples.swift
// ShowUp · SHOWUP-196's frames A to N drawn still, with nothing wired -- the two pickers, the thread's suggestion
// cards, the Date screen's waiting tags, request card and tiles, and the failures. One copy for the previews, the
// debug States page, the fit sweep and the evidence. Android's `ChangeSamples.kt`.
//
// The values are the kit's: Leonie, 19:00 at Cafe Krone, Oranienstrasse 42, Kreuzberg; the slots 19:15 to 21:30; the
// five fixture places. Times are the Date screen's sample day (3 Nov 2026, 19:00 Berlin), the answer-by is 18:30
// (binding time - 0:30) and the clock is the sheet's 17:42 (chat open) or 15:55 (chat locked).

import SwiftUI

enum ChangeSamples {
    static let zone = DateSamples.zone
    private static let meet = DateSamples.meet
    private static let deadline = meet.addingTimeInterval(-30 * 60)

    /// A Berlin wall-clock time of the sample day as a Date: the sample's 19:00 is `meet`.
    private static func berlin(_ hour: Int, _ minute: Int) -> Date {
        meet.addingTimeInterval(TimeInterval(((hour - 19) * 60 + minute) * 60))
    }

    /// The kit's slot list, 19:15 to 21:30 in 15-minute steps.
    static let slots: [Date] = (1...10).map { meet.addingTimeInterval(TimeInterval($0 * 15 * 60)) }

    /// The kit's five places, with their distances from Cafe Krone.
    static let places: [PlaceOption] = [
        PlaceOption(placeId: "p-lenz", name: "Bar Lenz", type: .bar, address: "Lausitzer Platz 9, Kreuzberg", distanceMeters: 350),
        PlaceOption(placeId: "p-nove", name: "Osteria Nove", type: .restaurant, address: "Wiener Str. 14, Kreuzberg", distanceMeters: 600),
        PlaceOption(placeId: "p-fraeu", name: "Café Fräulein", type: .cafe, address: "Manteuffelstr. 30, Kreuzberg", distanceMeters: 800),
        PlaceOption(placeId: "p-kanal", name: "Kanal Bar", type: .bar, address: "Paul-Lincke-Ufer 21, Kreuzberg", distanceMeters: 1100),
        PlaceOption(placeId: "p-linde", name: "Lindenblatt", type: .restaurant, address: "Skalitzer Str. 82, Kreuzberg", distanceMeters: 1400),
    ]

    static let ctx = ChangeSheetContext(name: "Leonie", bindingTime: meet, bindingPlace: "Cafe Krone", slots: slots, zone: zone)

    /// My 19:30 suggestion, sent 17:41.
    static let mineTime = ChangeSuggestion(
        id: "s-mine-time", kind: .time, byYou: true, time: berlin(19, 30), place: nil,
        wasTime: meet, wasPlaceName: nil, deadline: deadline, createdAt: berlin(17, 41))

    /// My Bar Lenz suggestion, sent 17:41.
    static let minePlace = ChangeSuggestion(
        id: "s-mine-place", kind: .place, byYou: true, time: nil,
        place: ChangePlace(placeId: places[0].placeId, name: places[0].name, type: places[0].type, address: places[0].address),
        wasTime: meet, wasPlaceName: "Cafe Krone", deadline: deadline, createdAt: berlin(17, 41))

    /// Leonie's 19:30 suggestion, sent 17:38.
    static let theirsTime: ChangeSuggestion = {
        var s = mineTime
        s.id = "s-theirs-time"
        s.byYou = false
        s.createdAt = berlin(17, 38)
        return s
    }()

    /// H: mine, not agreed by 18:30 -- the server's snapshot of the binding values then.
    static let expiredTime: ChangeSuggestion = {
        var s = mineTime
        s.id = "s-expired"
        s.status = .expired
        s.expiredAt = deadline
        s.expiredBindingTime = meet
        s.expiredBindingPlaceName = "Cafe Krone"
        return s
    }()

    private static let block = DateChange(cutoffAt: deadline, allowed: true, slots: slots, timeWas: nil, placeWas: nil, pending: [])

    /// The date with the Date 05 block a server of this version sends: tiles and chips allowed, nothing open.
    static let date: ActiveDate = {
        var d = DateSamples.date
        d.change = block
        return d
    }()

    private static func withPending(_ open: [ChangeSuggestion]) -> ActiveDate {
        var d = date
        d.change = DateChange(cutoffAt: deadline, allowed: true, slots: slots, timeWas: nil, placeWas: nil, pending: open)
        return d
    }

    /// Leonie's earlier message, which the thread frames seed (the kit's `CHANGE_SEED`).
    static let seed = ChatMessage(id: "seed", reason: .onMyWay, body: "Looking forward to tonight!", fromYou: false,
                                  sentAt: berlin(17, 20), read: nil)

    private static let chatDate: ActiveDate = {
        var d = date
        d.coordinateOpen = true
        d.chatTutorialSeen = true
        return d
    }()

    /// A thread over `date` holding `suggestions`.
    static func chatUi(_ suggestions: [ChangeSuggestion], failed: Set<String> = []) -> ChatUi {
        var ui = ChatSamples.b
        ui.date = chatDate
        ui.messages = [seed]
        ui.suggestions = suggestions
        ui.agreeFailed = failed
        return ui
    }

    /// The chat's start grid with a change block: all eight chips, two of them the change chips. A payload with no
    /// `change` block (an older server) fits six; the fit sweep needs this one to see the eight-chip grid and dock.
    static let chatWithChange: ChatUi = {
        var ui = ChatSamples.b
        ui.date = chatDate
        return ui
    }()

    /// The Date screen at `now` (locked before 16:30Z, open after).
    static func dateUi(_ date: ActiveDate, now: Date = DateSamples.bNow, agreeFailed: Bool = false) -> DateUi {
        DateUi(date: date, now: now, loading: false, agreeFailed: agreeFailed)
    }

    /// A: the time picker with 19:30 picked.
    static let a = ChangePickerUi(kind: .time, entry: .coordinateTile, slot: slots[1])

    /// B: the place picker with Bar Lenz picked.
    static let b = ChangePickerUi(kind: .place, entry: .chatChip, place: places[0],
                                  list: PlacesView.loaded(places: places, google: true, forQuery: ""))

    /// K: A with the send error.
    static let k: ChangePickerUi = {
        var ui = a
        ui.sendFailed = true
        return ui
    }()

    /// M: the Places request failed.
    static let m = ChangePickerUi(kind: .place, entry: .chatChip, list: PlacesView.failed)

    /// N: a search with no result.
    static let n = ChangePickerUi(kind: .place, entry: .chatChip, query: "sushi",
                                  list: PlacesView.loaded(places: [], google: true, forQuery: "sushi"))

    /// D: one time and one place of mine, both open.
    static let dDate = withPending([mineTime, minePlace])

    /// E, J, L: Leonie's time request, with her unread message under it.
    static let eDate: ActiveDate = {
        var d = withPending([theirsTime])
        d.changeRequest = theirsTime
        d.unread = DateSamples.unread
        return d
    }()

    /// G: the time agreed, 19:30 now binding, `Changed · was 19:00`.
    static let gDate: ActiveDate = {
        var d = date
        d.meetAt = slots[1]
        d.change = DateChange(cutoffAt: deadline, allowed: true, slots: slots, timeWas: meet, placeWas: nil, pending: [])
        return d
    }()

    /// Frame `id` ("A" to "N", and "L-thread"), drawn as the ticket's kit props draw it.
    @ViewBuilder
    static func frame(_ id: String) -> some View {
        switch id {
        case "A": picker(a)
        case "B": picker(b)
        case "C": chat(chatUi([mineTime]))
        case "D": dateScreen(dateUi(dDate))
        case "E": dateScreen(dateUi(eDate))
        case "F": chat(chatUi([theirsTime]))
        case "G": dateScreen(dateUi(gDate))
        case "H": chat(chatUi([expiredTime]))
        case "I": dateScreen(dateUi(date, now: DateSamples.aNow))
        case "J": dateScreen(dateUi(eDate, now: DateSamples.aNow))
        case "K": picker(k)
        case "L": dateScreen(dateUi(eDate, agreeFailed: true))
        case "L-thread": chat(chatUi([theirsTime], failed: [theirsTime.id]))
        case "M": picker(m)
        case "N": picker(n)
        default: Text("Unknown change frame \(id)")
        }
    }

    private static func picker(_ ui: ChangePickerUi) -> some View {
        ChangePickerScreen(ctx: ctx, ui: ui, onSelectSlot: { _ in }, onSelectPlace: { _ in }, onQuery: { _ in },
                           onRetry: {}, onSuggest: {}, onCancel: {})
    }

    private static func chat(_ ui: ChatUi) -> some View {
        ChatScreen(ui: ui, zone: zone)
    }

    private static func dateScreen(_ ui: DateUi) -> some View {
        DateScreen(ui: ui, zone: zone)
    }

    /// The ticket's frame letters, in order.
    static let letters = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"]
}
