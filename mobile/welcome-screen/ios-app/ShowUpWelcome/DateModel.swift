// DateModel.swift
// ShowUp · the Date screen's asynchronous work (SHOWUP-193)
//
// The screen loads, re-reads on every foreground and changes state with the clock, which is the
// trigger CLAUDE.md names for an `@Observable` owner. The screen itself stays a function from
// `DateUi` to pixels. Android's `DateViewModel.kt`, rule for rule:
//
//   · A VISIT IS REPORTED ONCE, WHEN THE DATE IS KNOWN. `screen_viewed` and `date_screen_viewed` need
//     the date's id and state, so `arrived` marks a visit and the first read that lands reports it.
//   · THE UNLOCK IS THE CLOCK'S, AND IT IS SILENT. `tick` moves `now`; the state is derived from it, so
//     A becomes B at meet − 1:30 in place, with no reload and no event.
//   · EVERY FOREGROUND RE-READS, so an agreed change lands in place, and no date on the re-read means
//     it ended elsewhere: `left` tells the host to leave. A FAILED re-read keeps what is on screen.

import Foundation

/// Everything the Date screen renders, in one value.
struct DateUi: Equatable, Sendable {
    var date: ActiveDate?
    var now: Date
    var loading = true
    /// Why the last read failed, while nothing is on screen; nil once a date is shown.
    var failed: CallFailure?
    /// The Copy pill reads `Copied`.
    var copied = false
    /// The date is gone (ended or cancelled elsewhere): the host leaves the screen.
    var left = false
    /// The date ended in the chat and the viewer has not seen it (SHOWUP-194): the host shows G or H.
    /// `date` stays as it was, for the match's face on that screen.
    var ended: EndedInChat?

    var state: DateState? {
        date.map { dateState(now: now, unlockAt: $0.unlockAt, unread: $0.unread, coordinateOpen: $0.coordinateOpen) }
    }
}

@MainActor
@Observable
final class DateModel {
    private(set) var state: DateUi

    private let store: any DateStoring
    private let analytics: (any AnalyticsTracking)?
    private let clock: () -> Date
    private let zone: () -> TimeZone
    /// How long `Copied` shows: `Motion.copied`. Short in tests.
    private let copiedFor: TimeInterval

    /// A visit waiting for its first read, and where it came from.
    private var pendingVisit: Visit?
    private var copiedTask: Task<Void, Never>?
    @ObservationIgnored private var poll: Task<Void, Never>?
    /// How often the screen re-reads while it is up: `datePollSeconds`. Short in tests.
    private let pollEvery: TimeInterval

    private struct Visit { let referrer: ProfileScreen? }

    init(
        store: any DateStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        copiedFor: TimeInterval = Motion.copied,
        pollEvery: TimeInterval = datePollSeconds
    ) {
        self.store = store
        self.analytics = analytics
        self.clock = clock
        self.zone = zone
        self.copiedFor = copiedFor
        self.pollEvery = pollEvery
        self.state = DateUi(now: clock())
    }

    /// The screen is on screen (from launch, a match, or back from a pushed screen): one visit.
    func arrived(referrer: ProfileScreen?) {
        // An arrival is never a leave: whether the date is gone is the read's to say. Without this a
        // second date in the same process arrived with the last one's `left` and was bounced off.
        state.left = false
        if pendingVisit != nil { return }
        pendingVisit = Visit(referrer: referrer)
        read()
    }

    /// The app came back to the foreground: re-read, report nothing.
    func foreground() { read() }

    /// The clock moved. The state follows it; nothing is reported.
    func tick() { state.now = clock() }

    /// The screen is on screen, or not. While it is, the date is read again every `datePollSeconds`, so the
    /// match's End date reaches this phone within seconds. Reads report nothing. The poll holds the model
    /// weakly and lets go of it before each sleep, so it ends with the model too.
    func onScreen(_ visible: Bool) {
        poll?.cancel()
        poll = nil
        guard visible else { return }
        let every = pollEvery
        poll = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(every * 1_000_000_000))
                guard !Task.isCancelled else { return }
                do {
                    guard let model = self else { return }
                    model.read()
                }
            }
        }
    }

    /// `Open the chat` stamped the tutorial seen on the server; the held date says so at once.
    func chatTutorialSeen() { state.date?.chatTutorialSeen = true }

    /// G or H has been seen and left: no date is held, so Search, Likes and Instant open again.
    func outcomeShown() {
        state.date = nil
        state.ended = nil
        state.left = false
    }

    /// The launch read already found `date`: draw it at once rather than a blank frame while the
    /// screen reads it again. Reports nothing -- the visit is `arrived`'s, from its own read.
    func seed(_ date: ActiveDate) {
        // Replaces a stale date (the one before, after it went away): the seed is the newer read.
        state.left = false
        state.date = date
        state.now = clock()
        state.loading = false
        state.failed = nil
    }

    /// `Try again` on the failed state: read again, the visit still waiting for it.
    func retry() {
        // The message stays up while it reads, and a second tap meanwhile reads nothing (9 Nov 2026).
        guard !state.loading else { return }
        state.loading = true
        read()
    }

    /// A change tile on the locked card (5 Nov 2026): `action_chip_used` with `surface: coordinate_card`, the
    /// same event as the chat's chips -- never `date_quick_action_tapped`. The Date 05 picker opens after it.
    func changeTile(_ reason: ChatReason) {
        guard let date = state.date else { return }
        analytics?.report(ChatAnalytics.actionChipUsed(date, reason: reason, surface: .coordinateCard, now: clock()))
    }

    func quickAction(_ action: QuickAction) {
        guard let date = state.date else { return }
        analytics?.report(DateAnalytics.quickActionTapped(date, action: action, now: clock()))
    }

    /// Copy was tapped: `Copied` for `Motion.copied`, restarted by another tap.
    func copied() {
        copiedTask?.cancel()
        state.copied = true
        let wait = copiedFor
        copiedTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.state.copied = false
        }
    }

    private func read() {
        let store = self.store
        let zone = self.zone().identifier
        Task { [weak self] in
            let answer = await store.active(zone: zone)
            guard let self else { return }
            let now = self.clock()
            self.state.now = now
            self.state.loading = false
            switch answer {
            case .success(.active(let date)):
                self.state.date = date
                self.state.failed = nil
                // A new date after one that went away is shown, not left.
                self.state.left = false
                self.state.ended = nil
                self.reportVisit(date, now: now)
            case .success(.ended(let outcome)):
                self.state.ended = outcome
            case .success(.none):
                self.state.left = true
            case .failure(let why):
                self.state.failed = why
            }
        }
    }

    private func reportVisit(_ date: ActiveDate, now: Date) {
        guard let visit = pendingVisit else { return }
        pendingVisit = nil
        analytics?.report(ProfileAnalytics.screenViewed(.dateScreen, referrer: visit.referrer))
        analytics?.report(DateAnalytics.dateScreenViewed(
            date, state: dateState(now: now, unlockAt: date.unlockAt, unread: date.unread,
                                   coordinateOpen: date.coordinateOpen), now: now))
    }
}
