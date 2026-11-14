// ArrivalGateModel.swift
// ShowUp · the arrival check-in's confirm / store / close / undo (SHOWUP-195). Android's
// `ArrivalGateViewModel.kt`, rule for rule.
//
// One per date for the session, held by the app and shared by the Date screen and the chat. The sheets take
// `GateUi` and return pixels.
//
//   · NOTHING IS STORED WITHOUT A FRESH FIX. No fix inside `locationFixTimeoutMs` is frame C and the store is not
//     called; a fix that arrives after the bound is dropped with the read.
//   · A FAILED STORE IS FRAME C TOO (R5). The person retries with `Try again`, which reads and stores again under a
//     new client id.
//   · CLOSE COMMITS, UNDO DELETES, AND NEITHER CAN BECOME THE OTHER (R4, R8). Both are optimistic -- the sheet
//     closes at once -- and retried on `retryDelayMs` until the server answers. An undo is never followed by a
//     commit of that record, and a new `Yes` cancels a delete still retrying.
//   · CHECKED IN IS THE SERVER'S WORD OR THIS SESSION'S COMMIT. A date read that still says `checkedIn = false`
//     while the commit is in flight must not bring the Check-in bar back.
//   · A CHECK-IN STORED WHILE THE APP IS AWAY COMMITS AT ONCE. The person tapped Yes and pocketed the phone during the
//     read; a done sheet nobody sees must not hold the match's push back, so it is Close (as the app leaving D is).
//     Back in the foreground before the store lands clears that, and D shows as normal.
//   · NO COORDINATE IN ANY EVENT. The fix goes to the store and nowhere else; nothing here logs.
//
// COMMIT AND UNDO OUTLIVE THE SCREEN AND THIS MODEL: their retry `Task`s hold the store and the date id, never
// `self`, so an app that went to the background while offline still tells the match once the network is back
// (Android's `work` scope). They end on success, on a bind to another date, or when the next act replaces them. A
// `@MainActor` deinit cannot reach a task (see `ChatModel`), so there is no cancel-on-deinit.

import Foundation

/// The sheet in front of the person: none, B / C (one sheet, `locError` says which), or D.
enum GatePhase: Equatable, Sendable {
    case idle
    case confirm(entry: ArrivalEntry, locError: Bool, busy: Bool, note: String?)
    case done(entry: ArrivalEntry)
}

struct GateUi: Equatable, Sendable {
    var phase: GatePhase = .idle
    /// This session has committed (or found committed) the bound date's check-in.
    var committed = false
    /// An undo was tapped and not yet consumed: the chat's host leaves for the Date screen.
    var undone = false
}

@MainActor
@Observable
final class ArrivalGateModel {
    private(set) var ui = GateUi()

    private let store: any ArrivalStoring
    private let reader: any LocationFixReading
    private let analytics: (any AnalyticsTracking)?
    private let clock: () -> Date
    private let newId: () -> String
    /// Waits `ms`. Injected so a test sees the retry schedule without waiting it out.
    private let sleep: @Sendable (Int) async -> Void
    private let fixTimeoutMs: Int
    /// Gives a commit or undo request iOS's background time, so one that starts as the app leaves is not suspended.
    private let background: any BackgroundTaskRunning

    @ObservationIgnored private var date: ActiveDate?
    @ObservationIgnored private var yesTask: Task<Void, Never>?
    @ObservationIgnored private var commitTask: Task<Void, Never>?
    @ObservationIgnored private var undoTask: Task<Void, Never>?
    /// The app left while a read was busy and has not come back: a store that lands now commits.
    @ObservationIgnored private var away = false

    init(
        store: any ArrivalStoring,
        reader: any LocationFixReading,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        newId: @escaping () -> String = { UUID().uuidString.lowercased() },
        sleep: @escaping @Sendable (Int) async -> Void = { ms in
            try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
        },
        fixTimeoutMs: Int = locationFixTimeoutMs,
        background: any BackgroundTaskRunning = UIKitBackgroundRunner()
    ) {
        self.store = store
        self.reader = reader
        self.analytics = analytics
        self.clock = clock
        self.newId = newId
        self.sleep = sleep
        self.fixTimeoutMs = fixTimeoutMs
        self.background = background
    }

    /// Every date read goes through here. Another date resets everything.
    func bind(_ date: ActiveDate) {
        if self.date?.id != date.id {
            cancelAll()
            away = false
            ui = GateUi()
        }
        self.date = date
    }

    /// The server's word or this session's commit, for the bound date.
    func checkedIn() -> Bool { date?.checkedIn == true || ui.committed }

    /// Shows B. False when there is nothing to check in to (already checked in, or no date bound); true when a
    /// sheet is up, whether just opened or already there.
    @discardableResult
    func open(_ entry: ArrivalEntry, note: String? = nil) -> Bool {
        guard let date else { return false }
        if checkedIn() { return false }
        if ui.phase != .idle { return true }
        ui.phase = .confirm(entry: entry, locError: false, busy: false, note: note)
        analytics?.report(ArrivalAnalytics.confirmShown(
            dateId: date.id, entry: entry, minutesVsStart: minutesVsStart(date.meetAt, clock())))
        return true
    }

    /// `Not yet`, the scrim or a swipe on B or C. Inert while busy (R7).
    func notYet() {
        guard case .confirm(_, _, let busy, _) = ui.phase, !busy else { return }
        ui.phase = .idle
    }

    /// `Yes, I've arrived` and `Try again`: read one fresh fix, store it.
    func yes() {
        guard let date, case .confirm(let entry, let locError, let busy, let note) = ui.phase, !busy else { return }
        analytics?.report(ArrivalAnalytics.confirmSubmitted(
            dateId: date.id, entry: entry, minutesVsStart: minutesVsStart(date.meetAt, clock())))
        away = false
        ui.phase = .confirm(entry: entry, locError: locError, busy: true, note: note)
        // The new store replaces the server's uncommitted row; a delete still retrying must not land after it.
        undoTask?.cancel()
        let clientId = newId()
        let reader = self.reader
        let store = self.store
        let timeout = fixTimeoutMs
        let id = date.id
        yesTask = Task { [weak self] in
            let fix = await Self.fixWithin(reader, ms: timeout)
            guard !Task.isCancelled else { return }
            guard let fix else {
                self?.failed(entry: entry, note: note)
                return
            }
            let result = await store.checkIn(dateId: id, fix: fix, entry: entry, clientId: clientId)
            guard !Task.isCancelled, let self else { return }
            switch result {
            case .stored:
                if self.away {
                    self.commitNow(dateId: id)
                } else {
                    self.ui.phase = .done(entry: entry)
                }
            case .alreadyCheckedIn:
                self.ui.phase = .idle
                self.ui.committed = true
            case .failed: self.failed(entry: entry, note: note)
            }
        }
    }

    /// `Close`, the scrim or a swipe on D: the match is told.
    func close() {
        guard let date, case .done = ui.phase else { return }
        commitNow(dateId: date.id)
    }

    /// The sheet is gone and the match is told, retried until the server answers.
    private func commitNow(dateId id: String) {
        ui.phase = .idle
        ui.committed = true
        ui.undone = false
        commitTask?.cancel()
        let store = self.store
        commitTask = retried(named: "arrival-commit") { await store.commit(dateId: id) }
    }

    /// `Undo check-in` on D: the record is deleted, never committed.
    func undo() {
        guard let date, case .done = ui.phase else { return }
        commitTask?.cancel()
        ui.phase = .idle
        ui.committed = false
        ui.undone = true
        undoTask?.cancel()
        let store = self.store
        let id = date.id
        undoTask = retried(named: "arrival-undo") { await store.undo(dateId: id) }
    }

    /// The app went to the background: with D up that is Close; with a read busy, a store that lands before
    /// `foregrounded()` commits instead of showing D.
    func leftApp() {
        switch ui.phase {
        case .done: close()
        case .confirm(_, _, let busy, _): if busy { away = true }
        case .idle: break
        }
    }

    /// The scene is active again: a store that lands now is D as normal.
    func foregrounded() { away = false }

    /// The host has acted on `undone`.
    func consumeUndone() { ui.undone = false }

    private func failed(entry: ArrivalEntry, note: String?) {
        ui.phase = .confirm(entry: entry, locError: true, busy: false, note: note)
    }

    /// `call` until it answers, waiting `retryDelayMs` between tries. Holds no model, so it outlives it. Each try
    /// runs under its own background task (begin before, end after, never across the wait).
    private func retried(named name: String,
                         _ call: @escaping @Sendable () async -> Result<Void, CallFailure>) -> Task<Void, Never> {
        let sleep = self.sleep
        let background = self.background
        return Task {
            var attempt = 0
            while !Task.isCancelled {
                if case .success = await background.run(named: name, call) { return }
                await sleep(retryDelayMs(attempt))
                attempt += 1
            }
        }
    }

    private func cancelAll() {
        yesTask?.cancel()
        commitTask?.cancel()
        undoTask?.cancel()
    }

    /// The reader's fix, or nil when none came within `ms`. The first of the two to finish wins; the other is
    /// cancelled.
    private nonisolated static func fixWithin(_ reader: any LocationFixReading, ms: Int) async -> LocationFix? {
        await withTaskGroup(of: LocationFix?.self) { group in
            group.addTask { await reader.fix() }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }
}
