// CancelRules.swift
// ShowUp · Date 07's plain rules (SHOWUP-198). Android's `CancelRules.kt`.
//
// The guard sheet follows Date 05's gate and never a copy of it (R12): `changeClosed` reads `changeWindowOpen`, the
// first half of `changeKindAvailable`, and `cancelTiles` filters through `changeKindAvailable` itself.
// `minutesBeforeStart` is the Date screen's (`DateRules.swift`), so this sheet's events agree with Date 05's.

import Foundation

/// I: from Date 05's cutoff, or once either person has checked in, a new time or place can no longer be agreed.
/// A server that sends no `change` block (before Date 05) is not "closed": nothing was ever offered, so there are no
/// tiles and no body line, and `Message {name} instead` follows the chat alone -- it must not open a locked chat.
func changeClosed(_ change: DateChange?, now: Date) -> Bool {
    guard let change else { return false }
    return !changeWindowOpen(change, now: now)
}

/// The tiles that still show, in the chips' order: Date 05's own gate for each kind.
func cancelTiles(_ change: DateChange?, now: Date) -> [ChangeKind] {
    [ChangeKind.time, ChangeKind.place].filter { changeKindAvailable(change, kind: $0, now: now) }
}

/// The body line shows only with something under it: a tile Date 05 still offers, or D's confirmation.
func solutionBodyShown(tiles: [ChangeKind], sent: Bool, changeClosed: Bool) -> Bool {
    !changeClosed && (!tiles.isEmpty || sent)
}

/// `Message {name} instead`: whenever the chat is open (C), and always from the cutoff (I) -- it is open by then.
func showMessage(chatOpen: Bool, changeClosed: Bool) -> Bool { chatOpen || changeClosed }

/// D's `{value}`: the requested time on the phone's clock, or the requested place's name. Empty when none was chosen.
func sentLabel(kind: ChangeKind, slot: Date?, placeName: String?, zone: TimeZone) -> String {
    switch kind {
    case .time: return slot.map { changeClock($0, zone: zone) } ?? ""
    case .place: return placeName ?? ""
    }
}

/// The red link shows while the date is active (a date is only passed here while it is), THIS user has not checked in
/// -- the match's check-in does not hide it -- and the end routes are on (R1: a cancel with nowhere to land stays
/// hidden in production).
func cancelLinkShown(_ date: ActiveDate) -> Bool { date.endsEnabled && !date.checkedIn }

/// The portrait's pill: the chat header's line, `Today · 19:00 · Cafe Krone`, from the binding values (R10).
func guardPill(_ date: ActiveDate, zone: TimeZone) -> String { headerLine(date, zone: zone) }

/// H: the other person called the date off. The canceller's own landing is `landAfterEnd`.
func cancelledOnYou(_ outcome: EndedOutcome) -> Bool { outcome.reason == .cancelled && !outcome.endedByYou }

/// Where an ended date goes for the person who finds it ended: G, H, Date 08's screen (with the payload the server
/// picked), or the landing after an end. Android's `EndedRoute`; the screen rides in the case, so no caller unwraps it.
enum EndedRoute: Equatable { case dateEnded, partnerCancelled, partnerEnded(PartnerScreen), land }

/// ONE decision for every way an end reaches a person (the Date screen's read, the chat's poll and its 409 re-read):
/// the chat's own end is G, a cancel by the other person is H, a Date 06 end by the other person is Date 08 (the server
/// sends `EndedOutcome.partnerScreen` only to the match; an older server sends none, so that end lands), everything
/// else -- the canceller's own cancel and Date 06's own end -- lands (flow 10 plugs in there).
func endedRoute(_ outcome: EndedOutcome) -> EndedRoute {
    if outcome.reason == .chat { return .dateEnded }
    if cancelledOnYou(outcome) { return .partnerCancelled }
    if let screen = outcome.partnerScreen, !outcome.endedByYou { return .partnerEnded(screen) }
    return .land
}

/// Date 08's once-per-ended-date gate, in one place for the Date screen's hook. The hook runs on appear (an end a
/// Settings read stored before the Date screen existed) and on every change, so it sees one end several times: the
/// screen to show the first time, nil after.
func partnerEndedToShow(_ outcome: EndedOutcome, once: inout LandedOnce) -> PartnerScreen? {
    guard case .partnerEnded(let screen) = endedRoute(outcome), once.first(outcome.dateId) else { return nil }
    return screen
}

// `CancelUi` has members named `changeClosed` and `showMessage`, which hide the free functions inside the type (as
// `pastMeet` does for `DateEndUi`); it calls these.
func cancelChangeClosed(_ change: DateChange?, now: Date) -> Bool { changeClosed(change, now: now) }
func cancelShowMessage(chatOpen: Bool, changeClosed closed: Bool) -> Bool { showMessage(chatOpen: chatOpen, changeClosed: closed) }
func cancelSolutionBodyShown(tiles: [ChangeKind], sent: Bool, changeClosed closed: Bool) -> Bool {
    solutionBodyShown(tiles: tiles, sent: sent, changeClosed: closed)
}
