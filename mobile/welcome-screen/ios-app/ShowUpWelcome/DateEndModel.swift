// DateEndModel.swift
// ShowUp · Date 06's sheets: which is up, its step, the gate it waits on, the end in flight (SHOWUP-197). Android's
// `DateEndViewModel.kt`, rule for rule.
//
// The sheets stay functions from `DateEndUi` to pixels; this owns what must outlive a redraw: the end is a server
// request, the discomfort flag is one, and the gate's detour must remember where it was going.
//
// ─────────────────────────────────────────────────────────────────────────────
// THE RULES THIS FILE ENFORCES
// ─────────────────────────────────────────────────────────────────────────────
//
// THE DATE IS NOT ENDED UNTIL THE SERVER CONFIRMS. An end that fails (J) leaves the sheet on the same step with
// `failed` set; the CTA keeps its label and retries. `landed` is set only on the server's answer, and the host leaves
// the Date screen on it (`landAfterEnd`).
//
// THE GATE CONTINUES TO THE PICKED STEP (R9). The two problem routes need a stored check-in. Not checked in, the
// sheet hides and `openGate` asks the host for Date 03's gate with the note; this model, not the gate, remembers the
// step (`gated`): Close (commit) reopens the sheet there, Not yet reopens the menu, Undo closes everything. `We met`
// needs no check-in.
//
// TRACKING FIRES ON THE TAP. The outcome or category goes out before the gate or the confirm step. Cancel, the scrim,
// Back, `Message {name}` and a tap on the disabled end button fire nothing. `date_ended` and `problem_stay_flagged`
// are the server's and are never reported from here.
//
// A 409 IS NOT J. `date_not_active` closes the sheet and the date is read again (the end already happened, the read
// routes on it); `before_meet` returns to the guide and `not_checked_in` to the menu, each with a re-read. A 404 (the
// switch is off) closes the sheet.
//
// THE SHEET, ITS STEP AND THE GATED STEP SURVIVE PROCESS DEATH in an epoch-scoped `@SceneStorage` draft (R14), handed
// to the host as one string (`DateEndDraft`), and are re-validated against the date read: a step that needs a
// check-in goes back to the menu without one, the no-show confirm back to the guide before the meet time, and a gate
// that died with the process becomes the step it was heading for if the check-in landed, the menu if not.

import Foundation

/// Everything the sheets render, and the one-shot asks the host acts on.
struct DateEndUi: Equatable, Sendable {
    /// The sheet that is open; nil is closed.
    var route: EndRoute? = nil
    var step: EndStep = .menu
    /// Where B was opened (the bar or the link); H's end is always `report`.
    var entry: EndEntry = .endDateBar
    /// The step the gate is serving: set while Date 03's sheets are up, the sheet hidden.
    var gated: EndStep? = nil
    /// Ask the host to open the gate with this entry (then `consumeGate`).
    var openGate: ArrivalEntry? = nil
    /// An end is in flight: both buttons are inert.
    var ending = false
    /// J: the last end got no server OK.
    var failed = false
    /// `Message {name}` was tapped: the host opens the chat (then `consumeOpenChat`).
    var openChat = false
    /// The server confirmed an end: the host lands (then `consumeLanded`).
    var landed: EndedOutcome? = nil
    var date: ActiveDate? = nil
    var now = Date(timeIntervalSince1970: 0)
    var checkedIn = false

    /// The sheet is on screen: open, and not hidden behind the gate.
    var visible: Bool { route != nil && gated == nil }
    /// `pastMeet(meetAt, now)`, R11. Spelled out here because a member of this name hides the free function.
    var pastMeet: Bool { date.map { now >= $0.meetAt } ?? false }
    var chatOpen: Bool { date?.coordinateOpen == true }
}

/// The draft carries where B was opened; a raw-value enum takes `Codable` from an extension in its own module.
extension EndEntry: Codable {}

/// What must outlive the process: the open sheet, its step, where it came from and the gated step. One JSON string,
/// because `@SceneStorage` takes primitives (as `ChangePickerDraft` does). Nothing here goes to analytics.
struct DateEndDraft: Codable, Equatable, Sendable {
    var route: EndRoute
    var step: EndStep
    var entry: EndEntry
    var gated: EndStep?
    var dateId: String

    var encoded: String {
        DraftEncoding.string(self)
    }

    /// Anything that does not decode is no draft: a value from another build is a shape this one never saw.
    static func decode(_ raw: String) -> DateEndDraft? {
        guard !raw.isEmpty, let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(DateEndDraft.self, from: data)
    }
}

@MainActor
@Observable
final class DateEndModel {
    private(set) var ui = DateEndUi()

    @ObservationIgnored private let store: any DateEndStoring
    @ObservationIgnored private let analytics: (any AnalyticsTracking)?
    /// The date must be read again: a 409 says the screen is out of date. Set by the host, which owns the date model.
    @ObservationIgnored var reread: () -> Void
    /// Hands the host the draft as a string, or "" when the sheet is closed.
    @ObservationIgnored private var persistDraft: (String) -> Void = { _ in }

    /// The date this model has been bound to, and the one a restored sheet belongs to.
    @ObservationIgnored private var boundId: String?
    @ObservationIgnored private var savedDateId: String?
    /// Restored and not yet checked against a date read: the first `bind` validates it.
    @ObservationIgnored private var unvalidated = false
    /// This session committed a check-in: counts as checked in before the date read says so.
    @ObservationIgnored private var committed = false
    @ObservationIgnored private var attached = false

    init(store: any DateEndStoring, analytics: (any AnalyticsTracking)? = nil, reread: @escaping () -> Void = {}) {
        self.store = store
        self.analytics = analytics
        self.reread = reread
    }

    /// Binds to scene storage and reads what it holds on the first call only -- the shape of `SearchModel.attach`, so
    /// the host can call it before every bind. A restore reports nothing.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        persistDraft = persist
        guard !attached else { return }
        attached = true
        guard let draft = DateEndDraft.decode(stored) else { return }
        // A date read that came first knows its id: a draft of another date is stale.
        if let bound = boundId, bound != draft.dateId { return }
        ui.route = draft.route
        ui.step = draft.step
        ui.entry = draft.entry
        ui.gated = draft.gated
        savedDateId = draft.dateId
        unvalidated = true
        validate()
    }

    /// What must outlive the process: the open sheet, its step and the gated step. A closed sheet leaves nothing.
    /// The draft last written, so an unchanged one is never written again.
    @ObservationIgnored private var lastPersisted: String?

    private func persist() {
        let encoded = ui.route.map {
            DateEndDraft(route: $0, step: ui.step, entry: ui.entry, gated: ui.gated,
                         dateId: boundId ?? savedDateId ?? "").encoded
        } ?? ""
        // The clock binds every second while a sheet is open; scene storage is written only when the draft changed.
        guard encoded != lastPersisted else { return }
        lastPersisted = encoded
        persistDraft(encoded)
    }

    private func change(_ block: (inout DateEndUi) -> Void) {
        block(&ui)
        persist()
    }

    /// Every date read, and every clock tick, goes through here: another date resets everything.
    func bind(_ date: ActiveDate, now: Date) {
        let other: Bool
        if let previous = boundId {
            other = previous != date.id
        } else {
            other = ui.route != nil && savedDateId != date.id
        }
        if other {
            committed = false
            unvalidated = false
            ui = DateEndUi()
        }
        boundId = date.id
        let checkedIn = date.checkedIn || committed
        change {
            $0.date = date
            $0.now = now
            $0.checkedIn = checkedIn
        }
        validate()
    }

    /// R14: a step the date no longer supports goes back to where it is allowed.
    private func validate() {
        guard ui.route != nil, let date = ui.date else { return }
        if !date.endsEnabled {
            change {
                $0.route = nil
                $0.step = .menu
                $0.gated = nil
                $0.openGate = nil
                $0.failed = false
            }
            unvalidated = false
            return
        }
        if unvalidated {
            // A gate that died with the process: the check-in either landed or it did not.
            unvalidated = false
            if let gated = ui.gated {
                change {
                    $0.gated = nil
                    $0.step = $0.checkedIn ? gated : .menu
                }
            }
        }
        if ui.gated != nil { return }
        let needsCheckIn = ui.step == .guide || ui.step == .noShow || ui.step == .uncomfortable
        if needsCheckIn && !ui.checkedIn {
            change {
                $0.step = .menu
                $0.failed = false
            }
        } else if ui.step == .noShow && !ui.pastMeet {
            change {
                $0.step = .guide
                $0.failed = false
            }
        }
    }

    /// The End Date bar or the Check-in bar's link: B.
    func openWrap(_ entry: EndEntry) {
        guard let date = ui.date, ui.route == nil, date.endsEnabled else { return }
        let checkedIn = ui.checkedIn
        let minutes = minutesVsStart(date.meetAt, ui.now)
        change {
            $0.route = .wrap
            $0.step = .menu
            $0.entry = entry
            $0.failed = false
        }
        analytics?.report(DateEndAnalytics.wrapupOpened(dateId: date.id, entry: entry, checkedIn: checkedIn,
                                                        minutesVsStart: minutes))
    }

    /// The Report card: H.
    func openReport() {
        guard let date = ui.date, ui.route == nil, date.endsEnabled else { return }
        let checkedIn = ui.checkedIn
        let minutes = minutesVsStart(date.meetAt, ui.now)
        change {
            $0.route = .report
            $0.step = .menu
            $0.failed = false
        }
        analytics?.report(DateEndAnalytics.problemReportOpened(dateId: date.id, checkedIn: checkedIn,
                                                               minutesVsStart: minutes))
    }

    /// A menu row. The event goes out on the tap, before the gate; then the step, or the gate when the route needs a
    /// check-in this person has not made.
    func pick(_ option: EndOption) {
        guard let date = ui.date, let route = ui.route else { return }
        guard ui.visible, ui.step == .menu, !ui.ending else { return }
        let belongs: Bool
        switch route {
        case .wrap: belongs = option != .uncomfortable
        case .report: belongs = option != .met
        }
        guard belongs else { return }
        switch route {
        case .wrap:
            analytics?.report(DateEndAnalytics.wrapupOutcomeSelected(dateId: date.id, option: option,
                                                                     checkedIn: ui.checkedIn))
        case .report:
            analytics?.report(DateEndAnalytics.problemCategorySelected(dateId: date.id, option: option,
                                                                       checkedIn: ui.checkedIn))
        }
        let target = endStepAfterGate(option)
        if optionNeedsCheckIn(option) && !ui.checkedIn {
            change {
                $0.gated = target
                $0.openGate = gateEntryFor(route)
            }
        } else {
            change {
                $0.step = target
                $0.failed = false
            }
        }
    }

    /// The guide's end button: G, from the meet time. Before it the button is disabled and this is a no-op.
    func confirmWait() {
        guard ui.visible, ui.step == .guide, !ui.ending, ui.pastMeet else { return }
        change {
            $0.step = .noShow
            $0.failed = false
        }
    }

    /// `Message {name}`: the sheet closes and the host opens the chat. Hidden while the chat is locked.
    func message() {
        guard ui.visible, ui.step == .guide, !ui.ending, messageShown(chatOpen: ui.chatOpen) else { return }
        change {
            $0.route = nil
            $0.step = .menu
            $0.failed = false
            $0.openChat = true
        }
    }

    /// `Back`, Cancel on C, G's `Back` and the scrim on G, E / F and I: one step up. On a menu it is `close`.
    func back() {
        guard ui.route != nil, !ui.ending else { return }
        switch ui.step {
        case .menu:
            close()
        case .noShow:
            change {
                $0.step = .guide
                $0.failed = false
            }
        case .met, .guide, .uncomfortable:
            change {
                $0.step = .menu
                $0.failed = false
            }
        }
    }

    /// `Cancel`, the scrim or a swipe-down on B or H. Nothing is stored. Inert while an end is in flight.
    func close() {
        guard ui.route != nil, !ui.ending else { return }
        change {
            $0.route = nil
            $0.step = .menu
            $0.gated = nil
            $0.openGate = nil
            $0.failed = false
        }
    }

    /// The other person ended this date (Date 08, SHOWUP-199): the sheet closes whatever it is doing, including while an
    /// end of ours is in flight (that call finishes on its own: a 409 re-reads into the same screen), and nothing of it
    /// stays saved, so it cannot come back after a process death. Not a dismissal: nothing is reported.
    func discardForPartnerEnd() {
        guard ui.route != nil else { return }
        change {
            $0.route = nil
            $0.step = .menu
            $0.gated = nil
            $0.openGate = nil
            $0.failed = false
        }
    }

    /// The CTA of C, G or I: end the date. Inert while one is already in flight.
    func end() {
        guard let date = ui.date, let route = ui.route else { return }
        guard ui.visible, !ui.ending, let reason = endReasonFor(ui.step) else { return }
        let entry: EndEntry = route == .report ? .report : ui.entry
        // `failed` stays set while a retry is in flight: the box above the CTA does not leave and come back, and the
        // layout does not jump. It goes with the success (the sheet closes) and a second failure keeps it.
        change { $0.ending = true }
        let store = self.store
        let id = date.id
        Task { [weak self] in
            let result = await store.end(dateId: id, reason: reason, entry: entry)
            self?.ended(result)
        }
    }

    private func ended(_ result: EndDateResult) {
        switch result {
        case .ended(let outcome):
            change {
                $0.route = nil
                $0.step = .menu
                $0.gated = nil
                $0.ending = false
                $0.failed = false
                $0.landed = outcome
            }
        case .refused(let code):
            refused(code)
        case .failed:
            change {
                $0.ending = false
                $0.failed = true
            }
        }
    }

    private func refused(_ code: String) {
        switch code {
        case "before_meet":
            change {
                $0.ending = false
                $0.failed = false
                $0.step = .guide
            }
            reread()
        case "not_checked_in":
            committed = false
            change {
                $0.ending = false
                $0.failed = false
                $0.step = .menu
                $0.checkedIn = false
            }
            reread()
        case "date_not_active", refusedNotFound:
            change {
                $0.route = nil
                $0.step = .menu
                $0.gated = nil
                $0.ending = false
                $0.failed = false
            }
            reread()
        default:
            // Unknown: the person cannot tell it from a failure, and a retry is the honest answer.
            change {
                $0.ending = false
                $0.failed = true
            }
        }
    }

    /// `Stay for now` on I: an internal flag, one more each tap; the sheet closes and the date continues.
    func stay() {
        guard let date = ui.date, ui.visible, ui.step == .uncomfortable, !ui.ending else { return }
        change {
            $0.route = nil
            $0.step = .menu
            $0.failed = false
        }
        // Not awaited, and holding the store and the id rather than `self`, so it outlives this model: the person is
        // not kept waiting on evidence, and a failed flag shows nothing (the ticket).
        let store = self.store
        let id = date.id
        Task { _ = await store.flagDiscomfort(dateId: id) }
    }

    // ── the gate (R9) ────────────────────────────────────────────────────────────────────────

    /// The host has opened the gate.
    func consumeGate() { change { $0.openGate = nil } }

    /// Close on the done sheet (or the app leaving it): reopen the sheet at the step the person picked.
    func gateCommitted() {
        guard let gated = ui.gated else { return }
        committed = true
        change {
            $0.gated = nil
            $0.openGate = nil
            $0.step = gated
            $0.checkedIn = true
        }
    }

    /// Not yet: back to the menu the route came from.
    func gateNotYet() {
        guard ui.gated != nil else { return }
        change {
            $0.gated = nil
            $0.openGate = nil
            $0.step = .menu
        }
    }

    /// Undo: everything closes and the Check-in bar is back (Date 03).
    func gateUndone() {
        guard ui.gated != nil else { return }
        committed = false
        change {
            $0.route = nil
            $0.step = .menu
            $0.gated = nil
            $0.openGate = nil
            $0.checkedIn = false
        }
    }

    func consumeOpenChat() { change { $0.openChat = false } }

    func consumeLanded() { change { $0.landed = nil } }
}
