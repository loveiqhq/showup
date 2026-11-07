// ChatModel.swift
// ShowUp · the coordinate chat's asynchronous work (SHOWUP-194)
//
// One per date, held by the app for the session, so the thread, a typed draft, the selected chip and the
// guide survive leaving the chat and coming back. The screens take `ChatUi` and return pixels. Android's
// `ChatViewModel.kt`, rule for rule:
//
//   · THE DOCK IS THE PERSON'S. A poll replaces the thread and the date and nothing else: the draft, the
//     selected chip and the guide change only by what the person does. Text typed while a message was
//     sending is kept when it lands.
//   · NEW MESSAGES ARRIVE BY POLLING, while the chat is on screen: the date and the thread every
//     `chatPollSeconds`, and at once on showing, after a send and on retry. A read that brings the match's
//     messages marks the thread read; so does opening the chat.
//   · AN END FROM ANYWHERE SHOWS THE OUTCOME. This person's End date, the other person's (found by a poll),
//     or a second tap answered 409: the sheet closes, the draft goes, the poll stops and `outcome` carries
//     G or H. A date gone some other way sets `left`.
//   · NO TEXT IN ANY EVENT. Payloads carry lengths, the chip and whether its draft was edited.
//
// The poll is a `Task` that holds the model weakly and lets go of it before each sleep: cancelled by
// `onScreen(false)`, and ended by the model going away -- a `@MainActor` deinit cannot reach it.

import Foundation

/// Everything the chat's screens render, in one value.
struct ChatUi: Equatable, Sendable {
    /// The date, as the last read found it: the header's latest agreed time and place.
    var date: ActiveDate?
    var now: Date
    /// Oldest first: the server's thread, plus anything sent that a read has not returned yet.
    var messages: [ChatMessage] = []
    /// The thread's first read has not landed.
    var loading = true
    /// Why the first read failed, while there is no thread to show; nil once one is.
    var failed: CallFailure?
    var selected: ChatReason?
    var draft = ""
    var guide: Guide = .none
    var sending = false
    /// The last send did not land; the draft and the chip are still there. Cleared by any edit.
    var sendFailed = false
    /// The End the date sheet (F) is open.
    var endSheet = false
    /// End date was tapped and has not answered.
    var ending = false
    /// Why the last End date did not land; the sheet stays open.
    var endFailed: CallFailure?
    /// The date ended in the chat: G when `endedByYou`, H otherwise. The host shows it.
    var outcome: EndedInChat?
    /// The date is gone some other way: the host goes back to the Date screen.
    var left = false

    var locked: Bool { fieldLocked(selected: selected, guide: guide) }
    /// Android's `canSend` (the free function of that name would be hidden by a property spelled the same).
    var sendEnabled: Bool { !sending && canSend(text: draft, locked: locked) }
    /// Android's `endCard`.
    var endCardShown: Bool { endCardVisible(messages: messages, selected: selected) }
    /// B, the start grid: no message yet and nothing picked or typed. A chip tap moves to the thread.
    var start: Bool { messages.isEmpty && selected == nil && draft.isEmpty && guide == .none }
    /// Android's `countdown`.
    var countdownText: String? { date.flatMap { countdown(now: now, meetAt: $0.meetAt) } }
}

@MainActor
@Observable
final class ChatModel {
    private(set) var ui: ChatUi

    private let store: any ChatStoring
    private let dates: any DateStoring
    private let analytics: (any AnalyticsTracking)?
    private let clock: () -> Date
    private let zone: () -> TimeZone
    private let pollEvery: TimeInterval

    @ObservationIgnored private var poll: Task<Void, Never>?
    /// The match's messages already reported as received this session, by id: once per message.
    @ObservationIgnored private var received = Set<String>()
    /// Sent this session and not yet in a read: a poll that left before the send must not drop them.
    @ObservationIgnored private var unconfirmed: [ChatMessage] = []
    @ObservationIgnored private var visit: Visit?
    @ObservationIgnored private var guideShownAt: Date?

    private struct Visit {
        let start: Date
        var sent = 0
    }

    init(
        store: any ChatStoring,
        dates: any DateStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        pollEvery: TimeInterval = chatPollSeconds
    ) {
        self.store = store
        self.dates = dates
        self.analytics = analytics
        self.clock = clock
        self.zone = zone
        self.pollEvery = pollEvery
        self.ui = ChatUi(now: clock())
    }

    /// The chat is shown: a visit begins. `date` is what the opening screen read; the first poll replaces
    /// it with the latest. A different date than the one held starts afresh.
    func open(_ date: ActiveDate, entry: ChatEntry) {
        if let held = ui.date?.id, held != date.id { reset() }
        let now = clock()
        ui.date = date
        ui.now = now
        ui.left = false
        visit = Visit(start: now)
        // The tutorial comes before the first open of each date's chat, so arriving from it is the first.
        analytics?.report(ChatAnalytics.chatOpened(date, entry: entry, isFirst: entry == .tutorial, now: now))
        let store = self.store
        let id = date.id
        Task { await store.markRead(dateId: id) }
        onScreen(true)
    }

    /// The chat is on screen, or not (backgrounded, covered). The poll runs only while it is.
    func onScreen(_ visible: Bool) {
        guard visible else {
            poll?.cancel()
            poll = nil
            return
        }
        guard let id = ui.date?.id, poll == nil, ui.outcome == nil, !ui.left else { return }
        let every = pollEvery
        poll = Task { [weak self] in
            while !Task.isCancelled {
                do {
                    guard let model = self else { return }
                    await model.refresh(id)
                }
                try? await Task.sleep(nanoseconds: UInt64(every * 1_000_000_000))
            }
        }
    }

    /// `Try again` on a failed first read.
    func retry() {
        guard let id = ui.date?.id else { return }
        ui.loading = true
        ui.failed = nil
        Task { [weak self] in await self?.refresh(id) }
    }

    /// The person leaves the chat (back, or a hand-off that replaces it). The visit ends; the dock stays.
    func close() {
        onScreen(false)
        guard let visit else { return }
        self.visit = nil
        guard let id = ui.date?.id else { return }
        analytics?.report(ChatAnalytics.chatClosed(dateId: id, messagesSent: visit.sent, durationS: seconds(from: visit.start, to: clock())))
    }

    /// A chip tap, on the start grid or the dock: selects it and REPLACES the field with its draft. "I can't
    /// make it today" has none: it empties the field and locks it behind the guide.
    func pick(_ reason: ChatReason, surface: ChipSurface) {
        guard let date = ui.date else { return }
        let now = clock()
        analytics?.report(ChatAnalytics.actionChipUsed(date, reason: reason, surface: surface, now: now))
        ui.selected = reason
        ui.sendFailed = false
        if reason == .cantMakeIt {
            guideShownAt = now
            ui.draft = ""
            ui.guide = .locked
        } else {
            ui.draft = reason.draft ?? ""
            ui.guide = .none
        }
    }

    /// The field changed. A locked field takes nothing; the server's limit is the field's.
    func edit(_ text: String) {
        guard !ui.locked else { return }
        ui.draft = cappedMessage(text)
        ui.sendFailed = false
    }

    /// `Got it`: the guide collapses to one line and the field opens. The screen focuses it.
    func ackGuide() {
        guard let date = ui.date, ui.guide == .locked else { return }
        let now = clock()
        ui.guide = .acked
        analytics?.report(ChatAnalytics.cancelGuideAcknowledged(date, readTimeS: seconds(from: guideShownAt ?? now, to: now), now: now))
    }

    /// Send: the trimmed text with the selected chip's reason, or none.
    func send() {
        guard let date = ui.date, ui.sendEnabled else { return }
        let draft = ui.draft
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let reason = ui.selected
        let isFirst = !ui.messages.contains { $0.fromYou }
        ui.sending = true
        ui.sendFailed = false
        let store = self.store
        Task { [weak self] in
            let result = await store.send(dateId: date.id, reason: reason, body: body)
            guard let self else { return }
            switch result {
            case .success(let sent):
                self.unconfirmed.append(sent)
                self.visit?.sent += 1
                // Text typed while it was sending is the person's: only what was sent is cleared.
                let untouched = self.ui.draft == draft && self.ui.selected == reason
                if !self.ui.messages.contains(where: { $0.id == sent.id }) { self.ui.messages.append(sent) }
                self.ui.sending = false
                if untouched {
                    self.ui.selected = nil
                    self.ui.draft = ""
                    self.ui.guide = .none
                }
                self.analytics?.report(ChatAnalytics.messageSent(
                    self.ui.date ?? date, reason: reason, charCount: charCount(body),
                    prefillEdited: prefillEdited(reason: reason, sent: body), isFirst: isFirst, now: self.clock()))
                await self.refresh(date.id)
            case .failure:
                self.ui.sending = false
                self.ui.sendFailed = true
            }
        }
    }

    /// `End date` on the card: the sheet opens. Only with the card, so only after a cant_make_it message.
    func openEndSheet() {
        guard let date = ui.date, ui.endCardShown, ui.outcome == nil, let from = cantMakeItFrom(ui.messages) else { return }
        ui.endSheet = true
        ui.endFailed = nil
        analytics?.report(ChatAnalytics.endDateSheetOpened(date, cantMakeItFrom: from, now: clock()))
    }

    /// `Keep the date` or the scrim: the sheet closes and nothing changes. No event.
    func keepDate() {
        ui.endSheet = false
        ui.endFailed = nil
    }

    /// `End date` on the sheet: ends the date for both.
    func endDate() {
        guard let date = ui.date, ui.endSheet, !ui.ending else { return }
        ui.ending = true
        ui.endFailed = nil
        let store = self.store
        Task { [weak self] in
            let result = await store.endInChat(dateId: date.id)
            guard let self else { return }
            switch result {
            case .ended(let outcome):
                self.ended(outcome)
            case .alreadyEnded:
                // Already over: the other person ended it, or a second tap did. The date read says which.
                if await self.readDate(date.id) {
                    self.ui.ending = false
                    self.ui.endFailed = .server
                }
            case .failed(let why):
                self.ui.ending = false
                self.ui.endFailed = why
            }
        }
    }

    // MARK: - reads

    /// The date, then the thread. The dock is never touched.
    private func refresh(_ id: String) async {
        guard await readDate(id) else { return }
        switch await store.thread(dateId: id) {
        case .success(let read):
            unconfirmed.removeAll { sent in read.contains { $0.id == sent.id } }
            let messages = read + unconfirmed
            var arrived: [ChatMessage] = []
            for message in messages where !message.fromYou && received.insert(message.id).inserted {
                arrived.append(message)
            }
            ui.messages = messages
            ui.loading = false
            ui.failed = nil
            ui.now = clock()
            for message in arrived {
                analytics?.report(ChatAnalytics.messageReceived(dateId: id, reason: message.reason, charCount: charCount(message.body)))
            }
            if !arrived.isEmpty { await store.markRead(dateId: id) }
        case .failure(let why):
            // A failed re-read keeps the thread on screen: a dropped connection is not an empty chat.
            if ui.loading {
                ui.loading = false
                ui.failed = why
            }
        }
    }

    /// Reads the date: the latest agreed time and place, or its end. False once the chat is over -- ended
    /// (the outcome shows) or gone some other way (`left`). A failed read changes nothing.
    private func readDate(_ id: String) async -> Bool {
        let tz = zone().identifier
        if case .success(let found) = await dates.active(zone: tz) {
            switch found {
            case .active(let date):
                if date.id == id {
                    ui.date = date
                    ui.now = clock()
                } else {
                    leave()
                }
            case .ended(let outcome):
                if outcome.dateId == id { ended(outcome) } else { leave() }
            case .none:
                leave()
            }
        }
        return ui.outcome == nil && !ui.left
    }

    private func ended(_ outcome: EndedInChat) {
        poll?.cancel()
        poll = nil
        ui.outcome = outcome
        ui.endSheet = false
        ui.ending = false
        ui.endFailed = nil
        ui.draft = ""
        ui.selected = nil
        ui.guide = .none
        ui.sending = false
    }

    private func leave() {
        poll?.cancel()
        poll = nil
        ui.left = true
        ui.endSheet = false
        ui.ending = false
    }

    private func reset() {
        poll?.cancel()
        poll = nil
        received.removeAll()
        unconfirmed.removeAll()
        visit = nil
        guideShownAt = nil
        ui = ChatUi(now: clock())
    }

    private func seconds(from: Date, to: Date) -> Int { max(0, Int(to.timeIntervalSince(from))) }
}
