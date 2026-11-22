// ChangePickerModel.swift
// ShowUp · the time and place picker's asynchronous work (SHOWUP-196, Date 05 A, B, K, M, N)
//
// The place picker loads, searches with a debounce and sends, which is when a screen gets an `@Observable`
// (ios-app/CLAUDE.md). The sheets stay functions from `ChangePickerUi` to pixels. Android's
// `ChangePickerViewModel.kt`, rule for rule:
//
//  - NOTHING CHANGES UNTIL THE SERVER CONFIRMS. A send that fails (K) leaves the picker open with the selection
//    kept; `done` is set only on the server's answer, and the host closes and re-reads on it.
//  - A RETRY IS THE SAME SEND. The clientId is made on the first Suggest and reused while the selected value is
//    the same, so a suggestion the server stored whose answer was lost is answered with that copy, never stored
//    twice (R14). Another value is another suggestion: a new id.
//  - ONE SELECTION SURVIVES A LIST CHANGE only while the selected place is still listed (R18).
//  - A 409 IS NOT K. The server refuses a suggestion that can no longer be made, and a retry cannot change that:
//    `ui.stale` closes the picker and the host reads the date again. No error, no event.
//  - THE SELECTION, THE QUERY AND THE SEND'S ID ARE HANDED TO THE HOST AS ONE STRING (`ChangePickerDraft`), which
//    keeps them in `@SceneStorage` scoped to the session epoch. iOS does not restore a pushed destination after the
//    process dies (the stack is `@State`), so what survives is this: opening the SAME picker again -- the same
//    date and kind -- finds the selection, the field and the retry's id where they were. A restored place is
//    kept only if the reloaded list still has it (R18). The draft is cleared when the picker ends.

import Foundation

/// What the place list area shows.
enum PlacesView: Equatable, Sendable {
    /// The first read is in flight: the area stays empty and says so to a screen reader only (R17).
    case loading
    /// M: the request failed. The message and Try again take the list's place.
    case failed
    /// `forQuery` is the trimmed query the list answers: empty is "nearby", otherwise "Results" (or N when empty).
    case loaded(places: [PlaceOption], google: Bool, forQuery: String)
}

/// Everything a picker renders.
struct ChangePickerUi: Equatable, Sendable {
    var kind: ChangeKind
    var entry: ChangeEntry
    var slot: Date?
    var place: PlaceOption?
    /// The field as typed, untrimmed.
    var query = ""
    var list: PlacesView = .loading
    /// A send is in flight: both buttons are inert and the primary sits at alpha .6.
    var sending = false
    /// K: the last send got no server OK.
    var sendFailed = false
    /// The server confirmed: the host closes the picker and re-reads.
    var done = false
    /// Cancel or back: the host closes the picker.
    var closed = false
    /// 409: the suggestion can no longer be made. The host closes the picker and reads the date again; no K.
    var stale = false

    var hasSelection: Bool { slot != nil || place != nil }
}

/// The debounce on the trimmed query (ticket B, "debounced"; 300 ms is the design's).
let placesDebounceSeconds: TimeInterval = 0.3

/// The places route's `q` allows 100 characters; the field stops there so a long query never 400s into M.
let placesQueryMax = 100

/// What must outlive the process for one picker: the selection, the field and the id of the send in progress.
/// One JSON string, because `@SceneStorage` takes primitives (as the profile drafts do). Nothing here goes to
/// analytics: it is the phone's own scene storage.
struct ChangePickerDraft: Codable, Equatable, Sendable {
    var dateId: String
    var kind: String
    /// The selected slot, seconds since 1970.
    var slot: Double?
    var place: PlaceOption?
    var query: String
    var fromSearch: Bool
    var attemptValue: String?
    var attemptId: String?

    var encoded: String {
        guard let data = try? JSONEncoder().encode(self) else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    /// Anything that does not decode is no draft: a value from another build is a shape this one never saw.
    static func decode(_ raw: String) -> ChangePickerDraft? {
        guard !raw.isEmpty, let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(ChangePickerDraft.self, from: data)
    }
}

@MainActor
@Observable
final class ChangePickerModel {
    private(set) var ui: ChangePickerUi

    @ObservationIgnored private let kind: ChangeKind
    @ObservationIgnored private let entry: ChangeEntry
    @ObservationIgnored private let store: any ChangeStoring
    @ObservationIgnored private let analytics: (any AnalyticsTracking)?
    @ObservationIgnored private let clock: () -> Date
    @ObservationIgnored private let newId: () -> String
    @ObservationIgnored private let zone: () -> TimeZone
    /// Short in tests.
    @ObservationIgnored private let debounce: TimeInterval

    @ObservationIgnored private var date: ActiveDate?
    @ObservationIgnored private var loadTask: Task<Void, Never>?
    /// The trimmed query the last read asked for.
    @ObservationIgnored private var requested: String?
    /// The selected place was picked while a non-empty query was showing.
    @ObservationIgnored private var pickedFromSearch = false
    /// The value last sent and the id it went with, kept for the retry.
    @ObservationIgnored private var attempt: (value: String, id: String)?
    /// Hands the host the draft as a string, or "" when the picker has ended.
    @ObservationIgnored private let persistDraft: (String) -> Void

    init(
        kind: ChangeKind,
        entry: ChangeEntry,
        store: any ChangeStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        newId: @escaping () -> String = { UUID().uuidString.lowercased() },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        debounce: TimeInterval = placesDebounceSeconds,
        restoring: ChangePickerDraft? = nil,
        persist: @escaping (String) -> Void = { _ in }
    ) {
        self.kind = kind
        self.entry = entry
        self.store = store
        self.analytics = analytics
        self.clock = clock
        self.newId = newId
        self.zone = zone
        self.debounce = debounce
        self.persistDraft = persist
        var start = ChangePickerUi(kind: kind, entry: entry)
        if let draft = restoring, draft.kind == kind.rawValue {
            start.slot = draft.slot.map { Date(timeIntervalSince1970: $0) }
            start.place = draft.place
            start.query = draft.query
        }
        self.ui = start
        if let draft = restoring, draft.kind == kind.rawValue {
            pickedFromSearch = draft.fromSearch
            if let value = draft.attemptValue, let id = draft.attemptId { attempt = (value, id) }
        }
    }

    /// A picker that has ended writes nothing more: a late answer must not bring a finished send's draft back.
    private var ended: Bool { ui.closed || ui.done || ui.stale }

    private func save() {
        guard let date, !ended else { return }
        persistDraft(ChangePickerDraft(
            dateId: date.id, kind: kind.rawValue, slot: ui.slot?.timeIntervalSince1970, place: ui.place, query: ui.query,
            fromSearch: pickedFromSearch, attemptValue: attempt?.value, attemptId: attempt?.id).encoded)
    }

    /// The picker is on screen: `change_picker_opened`, once however many times the host builds it, and for the
    /// place picker the nearby list. Suggest another calls this too and fires only this.
    func open(_ date: ActiveDate) {
        guard self.date == nil else { return }
        self.date = date
        // A restored slot is only as good as the list the server offers now: it may have been taken, or the cutoff may
        // have moved it out. Not offered: dropped, so the primary reads `Pick a time` and no slot is lit (R18's time
        // half).
        if let restored = ui.slot, !(date.change?.slots ?? []).contains(restored) { ui.slot = nil }
        analytics?.report(ChangeAnalytics.pickerOpened(dateId: date.id, kind: kind, entry: entry,
                                                       meetAt: date.meetAt, now: clock()))
        // After process death the field comes back with its text: the list answers THAT, not the nearby default.
        if kind == .place { fetch(ui.query.trimmingCharacters(in: .whitespacesAndNewlines), immediately: true) }
        save()
    }

    func selectSlot(_ slot: Date) {
        if ui.slot != slot { ui.sendFailed = false }
        ui.slot = slot
        save()
    }

    func selectPlace(_ place: PlaceOption) {
        if case .loaded(_, _, let forQuery) = ui.list { pickedFromSearch = !forQuery.isEmpty } else { pickedFromSearch = false }
        if ui.place != place { ui.sendFailed = false }
        ui.place = place
        save()
    }

    /// The field changed: the list follows 300 ms after the last key, on the trimmed text.
    func setQuery(_ raw: String) {
        let query = String(raw.prefix(placesQueryMax))
        ui.query = query
        save()
        guard kind == .place else { return }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        // Same read as the last one, and it did not fail: nothing to ask. A failure is retried by typing.
        if trimmed == requested, ui.list != .failed {
            loadTask?.cancel()
            return
        }
        fetch(trimmed, immediately: false)
    }

    /// M's Try again: the same read again, for what is typed.
    func retryPlaces() {
        ui.list = .loading
        fetch(ui.query.trimmingCharacters(in: .whitespacesAndNewlines), immediately: true)
    }

    private func fetch(_ trimmed: String, immediately: Bool) {
        guard let dateId = date?.id else { return }
        loadTask?.cancel()
        // An ended picker starts no read: it would only write state nobody is looking at.
        guard !ended else { return }
        let wait = debounce
        loadTask = Task { [weak self, store] in
            if !immediately {
                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                if Task.isCancelled { return }
            }
            self?.requested = trimmed
            let answer = await store.places(dateId: dateId, query: trimmed.isEmpty ? nil : trimmed)
            if Task.isCancelled { return }
            self?.land(answer, for: trimmed)
        }
    }

    private func land(_ answer: Result<PlacesList, CallFailure>, for trimmed: String) {
        switch answer {
        case .success(let list):
            ui.list = .loaded(places: list.places, google: list.google, forQuery: trimmed)
            if let chosen = ui.place, !list.places.contains(where: { $0.placeId == chosen.placeId }) { ui.place = nil }
            save()
        case .failure:
            // No selection to keep: the list it was picked from is gone, and Suggest waits for a new one.
            ui.list = .failed
            ui.place = nil
            save()
        }
    }

    /// `Suggest {value}`, and K's retry. Inert while one is in flight, and with nothing selected.
    func suggest() {
        guard let dateId = date?.id, !ui.sending, !ui.done, !ui.stale else { return }
        let value: String
        if kind == .time {
            guard let slot = ui.slot else { return }
            value = "\(Int(slot.timeIntervalSince1970))"
        } else {
            guard let place = ui.place else { return }
            value = place.placeId
        }
        let id: String
        if let attempt, attempt.value == value {
            id = attempt.id
        } else {
            id = newId()
            attempt = (value, id)
        }
        save()
        ui.sending = true
        ui.sendFailed = false
        let request = SuggestRequest(
            kind: kind,
            time: kind == .time ? ui.slot : nil,
            placeId: kind == .place ? ui.place?.placeId : nil,
            entry: entry,
            fromSearch: kind == .place ? pickedFromSearch : nil,
            clientId: id,
            tz: zone().identifier)
        Task { [weak self, store] in
            let answer = await store.suggest(dateId: dateId, request: request)
            switch answer {
            case .sent:
                self?.loadTask?.cancel()
                self?.ui.sending = false
                self?.ui.done = true
                self?.persistDraft("")
            case .stale:
                // 409: nothing a retry can fix. No K, no event: the host closes and reads the date again.
                self?.loadTask?.cancel()
                self?.ui.sending = false
                self?.ui.stale = true
                self?.persistDraft("")
            case .failed:
                self?.ui.sending = false
                self?.ui.sendFailed = true
            }
        }
    }

    /// The other person ended the date (Date 08, SHOWUP-199): the picker ends with nothing sent, and its draft goes, so
    /// opening it again after a process death finds nothing. Not the person's Cancel: no `change_picker_dismissed`. A send
    /// already on its way finishes on its own; an ended picker writes no draft again.
    func discardForPartnerEnd() {
        loadTask?.cancel()
        ui.closed = true
        persistDraft("")
    }

    /// Cancel or back: nothing was stored or sent. Ignored while a send is in flight (both buttons are inert).
    func cancel() {
        guard !ui.sending, !ui.closed, !ui.done, !ui.stale else { return }
        if let date {
            analytics?.report(ChangeAnalytics.pickerDismissed(dateId: date.id, kind: kind, entry: entry,
                                                              hadSelection: ui.hasSelection))
        }
        // The picker is over: a places answer still in flight (or a debounced search not yet sent) is dropped.
        loadTask?.cancel()
        ui.closed = true
        persistDraft("")
    }
}
