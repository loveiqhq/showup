// CancelModel.swift
// ShowUp · Date 07's guard sheet: open or not, the request sent from it, the cancel in flight (SHOWUP-198). Android's
// `CancelViewModel.kt`, rule for rule.
//
// The sheet stays a function from `CancelUi` to pixels; this owns what must outlive a redraw: the cancel is a server
// request, and the sheet and the request sent from it survive process death.
//
// ─────────────────────────────────────────────────────────────────────────────
// THE RULES THIS FILE ENFORCES
// ─────────────────────────────────────────────────────────────────────────────
//
// THE DATE IS NOT CANCELLED UNTIL THE SERVER CONFIRMS. A failure leaves the sheet up with `failed` set; the button
// keeps its label and retries, and `Don't cancel my date` still works. `landed` is set only on the server's answer,
// and the host lands the canceller with `landAfterEnd`. Both buttons are inert while it runs.
//
// A CANCEL AFTER A REQUEST IS STILL A CANCEL. The request does not soften the penalty; it only sets the session's
// `change_requested`, which `cancel_abandoned_at_guard` reports.
//
// TRACKING. `cancel_guard_shown` fires on every open; `cancel_abandoned_at_guard` once per close without a cancel
// (keep, the scrim or a swipe, Message instead). Opening a picker is not an exit. A close the server forced (a
// check-in on another device, an already-cancelled date, a switch turned off) is no exit either: the person did not
// choose it. `date_cancelled` is the server's and is never reported from here.
//
// A 409 IS NOT E. `checked_in` closes the sheet and the date is read again (the link goes); `date_not_active` closes
// it and reads again, which routes the person to H as the cancelled-on one. A 404 (the switch is off) closes it too.
//
// THE SHEET AND THE SENT VALUE SURVIVE PROCESS DEATH in an epoch-scoped `@SceneStorage` draft (`CancelDraft`, one
// string, as `DateEndDraft`), and are re-validated against the date read: another date, a check-in, or the switch off
// closes it. A restore is not an open: no second `cancel_guard_shown`.

import Foundation

/// What D shows: the request that was sent from the sheet, as its bold value.
struct SentRequest: Equatable, Sendable {
    var kind: ChangeKind
    var label: String
}

/// Everything the sheet renders, and the one-shot asks the host acts on.
struct CancelUi: Equatable, Sendable {
    var open = false
    /// D: a picker sent a request from this sheet.
    var sent: SentRequest? = nil
    /// A request was sent from this sheet in this session: `cancel_abandoned_at_guard.change_requested`.
    var changeRequested = false
    /// The cancel is in flight: both buttons are inert.
    var cancelling = false
    /// E: the last cancel got no server OK.
    var failed = false
    /// A tile was tapped: the host opens Date 05's picker of this kind over the sheet (then `consumePicker`).
    var openPicker: ChangeKind? = nil
    /// Message instead was tapped: the host opens the chat (then `consumeOpenChat`).
    var openChat = false
    /// The server confirmed the cancel: the host lands the canceller (then `consumeLanded`).
    var landed: EndedOutcome? = nil
    var date: ActiveDate? = nil
    var now = Date(timeIntervalSince1970: 0)

    /// I: Date 05's cutoff or a check-in has passed, evaluated with the screen's clock.
    var changeClosed: Bool { cancelChangeClosed(date?.change, now: now) }
    var chatOpen: Bool { date?.coordinateOpen == true }
    var showMessage: Bool { cancelShowMessage(chatOpen: chatOpen, changeClosed: changeClosed) }
    /// The tiles Date 05 still offers, in the chips' order.
    var tiles: [ChangeKind] { cancelTiles(date?.change, now: now) }
    /// The body line, with the tiles or D under it.
    var bodyShown: Bool { cancelSolutionBodyShown(tiles: tiles, sent: sent != nil, changeClosed: changeClosed) }
    /// The cost box's `{drop}`: the rate config's value, never a literal.
    var drop: Int { date?.cancelPenaltyPoints ?? 0 }
}

/// What must outlive the process: the open sheet and what was sent from it. One JSON string, because `@SceneStorage`
/// takes primitives (as `DateEndDraft` does). Nothing here goes to analytics.
struct CancelDraft: Codable, Equatable, Sendable {
    var sentKind: String?
    var sentLabel: String?
    var changeRequested: Bool
    var dateId: String

    var encoded: String {
        DraftEncoding.string(self)
    }

    /// Anything that does not decode is no draft: a value from another build is a shape this one never saw.
    static func decode(_ raw: String) -> CancelDraft? {
        guard !raw.isEmpty, let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(CancelDraft.self, from: data)
    }
}

@MainActor
@Observable
final class CancelModel {
    private(set) var ui = CancelUi()

    @ObservationIgnored private let store: any CancelStoring
    @ObservationIgnored private let analytics: (any AnalyticsTracking)?
    /// The date must be read again: a 409 says the screen is out of date. Set by the host, which owns the date model.
    @ObservationIgnored var reread: () -> Void
    /// Hands the host the draft as a string, or "" when the sheet is closed.
    @ObservationIgnored private var persistDraft: (String) -> Void = { _ in }

    /// The date this model has been bound to, and the one a restored sheet belongs to.
    @ObservationIgnored private var boundId: String?
    @ObservationIgnored private var savedDateId: String?
    @ObservationIgnored private var attached = false
    /// The draft last written, so an unchanged one is never written again (the clock binds every second).
    @ObservationIgnored private var lastPersisted: String?

    init(store: any CancelStoring, analytics: (any AnalyticsTracking)? = nil, reread: @escaping () -> Void = {}) {
        self.store = store
        self.analytics = analytics
        self.reread = reread
    }

    /// Binds to scene storage and reads what it holds on the first call only, so the host can call it before every
    /// bind. A restore reports nothing.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        persistDraft = persist
        guard !attached else { return }
        attached = true
        guard let draft = CancelDraft.decode(stored) else { return }
        // A date read that came first knows its id: a draft of another date is stale.
        if let bound = boundId, bound != draft.dateId { return }
        let kind = [ChangeKind.time, ChangeKind.place].first { $0.rawValue == draft.sentKind }
        ui.open = true
        ui.sent = kind.map { SentRequest(kind: $0, label: draft.sentLabel ?? "") }
        ui.changeRequested = draft.changeRequested
        savedDateId = draft.dateId
        revalidate()
    }

    private func persist() {
        let encoded = ui.open
            ? CancelDraft(sentKind: ui.sent?.kind.rawValue, sentLabel: ui.sent?.label, changeRequested: ui.changeRequested,
                          dateId: boundId ?? savedDateId ?? "").encoded
            : ""
        guard encoded != lastPersisted else { return }
        lastPersisted = encoded
        persistDraft(encoded)
    }

    private func change(_ block: (inout CancelUi) -> Void) {
        block(&ui)
        persist()
    }

    private static func closing(_ u: inout CancelUi) {
        u.open = false
        u.sent = nil
        u.changeRequested = false
        u.cancelling = false
        u.failed = false
        u.openPicker = nil
    }

    /// Every date read and every clock tick goes through here: another date resets everything.
    func bind(_ date: ActiveDate, now: Date) {
        let other: Bool
        if let previous = boundId {
            other = previous != date.id
        } else {
            other = ui.open && savedDateId != date.id
        }
        if other { ui = CancelUi() }
        boundId = date.id
        change {
            $0.date = date
            $0.now = now
        }
        revalidate()
    }

    /// The link is gone and the route may be off: a sheet over a date that no longer allows it closes, silently.
    private func revalidate() {
        guard ui.open, let date = ui.date, !date.endsEnabled || date.checkedIn else { return }
        change { Self.closing(&$0) }
    }

    /// The other person ended this date (Date 08, SHOWUP-199): the guard closes and its saved state goes with it, so it
    /// cannot restore after a process death. Not a dismissal: `cancel_abandoned_at_guard` is for the person's own exit.
    func discardForPartnerEnd() {
        guard ui.open else { return }
        change { Self.closing(&$0) }
    }

    /// The red link: B or C (the sheet reads the chat from the date).
    func open() {
        guard let date = ui.date, !ui.open, date.endsEnabled, !date.checkedIn else { return }
        let chatOpen = ui.chatOpen
        let minutes = minutesBeforeStart(meetAt: date.meetAt, now: ui.now)
        change {
            Self.closing(&$0)
            $0.open = true
        }
        analytics?.report(CancelAnalytics.guardShown(dateId: date.id, chatOpen: chatOpen,
                                                     penaltyPoints: date.cancelPenaltyPoints, minutesBeforeStart: minutes))
    }

    private func exit(_ how: CancelExit) {
        guard let date = ui.date, ui.open, !ui.cancelling else { return }
        let requested = ui.changeRequested
        change { Self.closing(&$0) }
        analytics?.report(CancelAnalytics.abandoned(dateId: date.id, exit: how, changeRequested: requested))
    }

    /// `Don't cancel my date`. Nothing is stored.
    func keep() { exit(.keep) }

    /// The scrim or a swipe-down.
    func dismiss() { exit(.dismissed) }

    /// `Message {name} instead`: the flow closes and the host opens the chat. Nothing is cancelled.
    func messageInstead() {
        guard ui.open, !ui.cancelling, ui.showMessage else { return }
        exit(.message)
        change { $0.openChat = true }
    }

    /// A tile: Date 05's picker over the sheet. Not an exit. A tile Date 05 hides asks for nothing.
    func picker(_ kind: ChangeKind) {
        guard ui.open, !ui.cancelling else { return }
        guard changeKindAvailable(ui.date?.change, kind: kind, now: ui.now) else { return }
        change { $0.openPicker = kind }
    }

    /// A picker sent from this sheet: D replaces the tiles and the sheet stays open.
    func pickerSent(kind: ChangeKind, label: String) {
        guard ui.open else { return }
        change {
            $0.sent = SentRequest(kind: kind, label: label)
            $0.changeRequested = true
        }
    }

    /// `Cancel anyway and let her down`, and E's retry. Inert while one is in flight.
    func cancel() {
        guard let date = ui.date, ui.open, !ui.cancelling else { return }
        // `failed` stays set while a retry is in flight so the box does not leave and come back; it goes with the
        // success (the sheet closes) and a second failure keeps it.
        change { $0.cancelling = true }
        let store = self.store
        let id = date.id
        Task { [weak self] in
            let result = await store.cancel(dateId: id)
            self?.cancelled(result)
        }
    }

    private func cancelled(_ result: CancelResult) {
        switch result {
        case .cancelled(let outcome):
            change {
                Self.closing(&$0)
                $0.landed = outcome
            }
        case .refused(let code):
            refused(code)
        case .failed:
            change {
                $0.cancelling = false
                $0.failed = true
            }
        }
    }

    private func refused(_ code: String) {
        switch code {
        case "checked_in", "date_not_active", refusedNotFound:
            change { Self.closing(&$0) }
            reread()
        default:
            // Unknown: the person cannot tell it from a failure, and a retry is the honest answer.
            change {
                $0.cancelling = false
                $0.failed = true
            }
        }
    }

    func consumePicker() { change { $0.openPicker = nil } }

    func consumeOpenChat() { change { $0.openChat = false } }

    func consumeLanded() { change { $0.landed = nil } }
}
