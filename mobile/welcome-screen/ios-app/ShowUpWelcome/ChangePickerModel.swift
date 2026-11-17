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

    var hasSelection: Bool { slot != nil || place != nil }
}

/// The debounce on the trimmed query (ticket B, "debounced"; 300 ms is the design's).
let placesDebounceSeconds: TimeInterval = 0.3

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

    init(
        kind: ChangeKind,
        entry: ChangeEntry,
        store: any ChangeStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        newId: @escaping () -> String = { UUID().uuidString.lowercased() },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        debounce: TimeInterval = placesDebounceSeconds
    ) {
        self.kind = kind
        self.entry = entry
        self.store = store
        self.analytics = analytics
        self.clock = clock
        self.newId = newId
        self.zone = zone
        self.debounce = debounce
        self.ui = ChangePickerUi(kind: kind, entry: entry)
    }

    /// The picker is on screen: `change_picker_opened`, once however many times the host builds it, and for the
    /// place picker the nearby list. Suggest another calls this too and fires only this.
    func open(_ date: ActiveDate) {
        guard self.date == nil else { return }
        self.date = date
        analytics?.report(ChangeAnalytics.pickerOpened(dateId: date.id, kind: kind, entry: entry,
                                                       meetAt: date.meetAt, now: clock()))
        if kind == .place { fetch("", immediately: true) }
    }

    func selectSlot(_ slot: Date) {
        if ui.slot != slot { ui.sendFailed = false }
        ui.slot = slot
    }

    func selectPlace(_ place: PlaceOption) {
        if case .loaded(_, _, let forQuery) = ui.list { pickedFromSearch = !forQuery.isEmpty } else { pickedFromSearch = false }
        if ui.place != place { ui.sendFailed = false }
        ui.place = place
    }

    /// The field changed: the list follows 300 ms after the last key, on the trimmed text.
    func setQuery(_ query: String) {
        ui.query = query
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
        case .failure:
            // No selection to keep: the list it was picked from is gone, and Suggest waits for a new one.
            ui.list = .failed
            ui.place = nil
        }
    }

    /// `Suggest {value}`, and K's retry. Inert while one is in flight, and with nothing selected.
    func suggest() {
        guard let dateId = date?.id, !ui.sending, !ui.done else { return }
        let value: String
        if kind == .time {
            guard let slot = ui.slot else { return }
            value = "\(slot.timeIntervalSince1970)"
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
            case .success:
                self?.ui.sending = false
                self?.ui.done = true
            case .failure:
                self?.ui.sending = false
                self?.ui.sendFailed = true
            }
        }
    }

    /// Cancel or back: nothing was stored or sent. Ignored while a send is in flight (both buttons are inert).
    func cancel() {
        guard !ui.sending, !ui.closed, !ui.done else { return }
        if let date {
            analytics?.report(ChangeAnalytics.pickerDismissed(dateId: date.id, kind: kind, entry: entry,
                                                              hadSelection: ui.hasSelection))
        }
        ui.closed = true
    }
}
