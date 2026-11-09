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
//   · A SEND THAT FAILS STAYS IN THE THREAD (Date 02 J, 9 Nov 2026), unsent, at its foot, and the field is
//     free for the next message. Tapping its line resends it with the same reason and the same id, which the
//     server answers with the first copy if it already has it -- the match receives it once. A poll that
//     finds it sent anyway takes it out of the outbox. `message_sent` fires once, when it is sent.
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
    /// Oldest first: the server's thread, plus anything sent that a read has not returned yet, then the
    /// messages whose send failed (`ChatMessage.failed`), at the foot.
    var messages: [ChatMessage] = []
    /// The thread's first read has not landed.
    var loading = true
    /// Why the first read failed, while there is no thread to show; nil once one is.
    var failed: CallFailure?
    var selected: ChatReason?
    var draft = ""
    var guide: Guide = .none
    var sending = false
    /// The failed messages being resent, by `ChatMessage.clientId`: their line waits.
    var resending: Set<String> = []
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
    /// The match's messages already reported, by id, in this launch and earlier ones: once per message.
    private let received: any ReceivedLedger

    @ObservationIgnored private var poll: Task<Void, Never>?
    /// The last read of the thread.
    @ObservationIgnored private var lastRead: [ChatMessage] = []
    /// Sent this session and not yet in a read: a poll that left before the send must not drop them.
    @ObservationIgnored private var unconfirmed: [ChatMessage] = []
    /// Failed sends, oldest first, until one is resent or a read finds it sent after all.
    @ObservationIgnored private var outbox: [Unsent] = []
    @ObservationIgnored private var visit: Visit?
    @ObservationIgnored private var guideShownAt: Date?

    private struct Visit {
        let start: Date
        let entry: ChatEntry
        var sent = 0
        /// `chat_opened` has fired: once per visit, once the chat has loaded.
        var opened = false
    }

    /// A message whose send failed, with what `message_sent` will need once it is sent.
    private struct Unsent {
        let clientId: String
        let reason: ChatReason?
        let body: String
        let prefillEdited: Bool?
        let at: Date

        var shown: ChatMessage {
            ChatMessage(id: "unsent:\(clientId)", reason: reason, body: body, fromYou: true, sentAt: at, read: nil,
                        clientId: clientId, failed: true)
        }
    }

    init(
        store: any ChatStoring,
        dates: any DateStoring,
        analytics: (any AnalyticsTracking)? = nil,
        clock: @escaping () -> Date = { Date() },
        zone: @escaping () -> TimeZone = { TimeZone.current },
        pollEvery: TimeInterval = chatPollSeconds,
        received: (any ReceivedLedger)? = nil
    ) {
        self.store = store
        self.dates = dates
        self.analytics = analytics
        self.clock = clock
        self.zone = zone
        self.pollEvery = pollEvery
        // Made here rather than as the default: the in-memory ledger is the main actor's, as this is.
        self.received = received ?? InMemoryReceivedLedger()
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
        visit = Visit(start: now, entry: entry)
        // A thread already on screen from an earlier visit is loaded; otherwise the first read reports it.
        if !ui.loading && ui.failed == nil { reportOpened() }
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
                    // The countdown moves with the clock whether or not the reads land: offline near the
                    // meet time it kept `in 0h 3m` after it (the SHOWUP-194 review).
                    model.ui.now = model.clock()
                    await model.refresh(id)
                }
                try? await Task.sleep(nanoseconds: UInt64(every * 1_000_000_000))
            }
        }
    }

    /// `Try again` on a failed first read. The failure stays on screen until the read answers.
    func retry() {
        guard let id = ui.date?.id, !ui.loading else { return }
        ui.loading = true
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
        let isFirst = sentNone()
        // Lower case, as Postgres hands a uuid back: a read matches it against the outbox.
        let clientId = UUID().uuidString.lowercased()
        let edited = prefillEdited(reason: reason, sent: body)
        ui.sending = true
        let store = self.store
        Task { [weak self] in
            let result = await store.send(dateId: date.id, reason: reason, body: body, clientId: clientId)
            guard let self else { return }
            var landed = false
            switch result {
            case .success(let sent):
                self.unconfirmed.append(sent)
                landed = true
            case .failure:
                // Refused because the date ended meanwhile: the date read shows the outcome at once, not
                // `Couldn't send` until the next poll. Still on, it was the send that failed: it stays in the
                // thread, and the field is free for the next message -- as after a send that landed. Unless a
                // poll meanwhile brought it: the answer was lost, the message was not, and it shows once.
                guard await self.readDate(date.id) else { return }
                landed = self.lastRead.contains { $0.clientId == clientId }
                if !landed {
                    self.outbox.append(Unsent(clientId: clientId, reason: reason, body: body, prefillEdited: edited, at: self.clock()))
                }
            }
            // Text typed while it was sending is the person's: only what was sent is cleared.
            let untouched = self.ui.draft == draft && self.ui.selected == reason
            self.ui.messages = self.shown()
            self.ui.sending = false
            if untouched {
                self.ui.selected = nil
                self.ui.draft = ""
                self.ui.guide = .none
            }
            if landed { self.reportSent(date, reason: reason, body: body, edited: edited, isFirst: isFirst) }
            if case .success = result { await self.refresh(date.id) }
        }
    }

    /// A tap on a failed message's line: sends it again with the same reason and id. Once at a time -- a second
    /// tap while it is on its way sends nothing.
    func resend(_ clientId: String) {
        guard let date = ui.date,
              let unsent = outbox.first(where: { $0.clientId == clientId }),
              !ui.resending.contains(clientId) else { return }
        let isFirst = sentNone()
        ui.resending.insert(clientId)
        let store = self.store
        Task { [weak self] in
            let result = await store.send(dateId: date.id, reason: unsent.reason, body: unsent.body, clientId: clientId)
            guard let self else { return }
            if case .failure = result {
                let still = await self.readDate(date.id)
                guard still else { return }
            }
            // Sent -- or failed, but a poll meanwhile brought it (see `send`).
            var sent: ChatMessage?
            if case .success(let message) = result { sent = message }
            let landed = sent != nil || self.lastRead.contains { $0.clientId == clientId }
            if landed, let index = self.outbox.firstIndex(where: { $0.clientId == clientId }) {
                self.outbox.remove(at: index)
                if let sent { self.unconfirmed.append(sent) }
                self.reportSent(date, reason: unsent.reason, body: unsent.body, edited: unsent.prefillEdited, isFirst: isFirst)
            }
            self.ui.messages = self.shown()
            self.ui.resending.remove(clientId)
            if case .success = result { await self.refresh(date.id) }
        }
    }

    /// `End date` on the card: the sheet opens. Only with the card, so only after a cant_make_it message.
    func openEndSheet() {
        guard let date = ui.date, ui.endCardShown, ui.outcome == nil, let from = cantMakeItFrom(ui.messages) else { return }
        ui.endSheet = true
        ui.endFailed = nil
        analytics?.report(ChatAnalytics.endDateSheetOpened(date, cantMakeItFrom: from, now: clock()))
    }

    /// `Keep the date` or the scrim: the sheet closes and nothing changes. No event. Not while the end is on its
    /// way: the sheet closed, the end landed anyway and the outcome replaced the chat.
    func keepDate() {
        guard !ui.ending else { return }
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
            lastRead = read
            unconfirmed.removeAll { sent in read.contains { $0.id == sent.id } }
            // A failed send the server has after all: its answer was lost, the message was not.
            let landed = outbox.filter { unsent in read.contains { $0.clientId == unsent.clientId } }
            outbox.removeAll { unsent in landed.contains { $0.clientId == unsent.clientId } }
            let messages = shown()
            // Not yet reported on this phone, in an earlier launch either: once per message.
            let reported = received.reported(id)
            let arrived = messages.filter { !$0.fromYou && !reported.contains($0.id) }
            ui.messages = messages
            ui.loading = false
            ui.failed = nil
            ui.now = clock()
            reportOpened()
            if let date = ui.date {
                for unsent in landed {
                    reportSent(date, reason: unsent.reason, body: unsent.body, edited: unsent.prefillEdited, isFirst: nil)
                }
            }
            for message in arrived {
                analytics?.report(ChatAnalytics.messageReceived(dateId: id, reason: message.reason, charCount: charCount(message.body)))
            }
            received.record(id, arrived.map(\.id))
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
        ui.sending = false
    }

    /// What the thread shows: the last read, what was sent since, then the failed sends.
    private func shown() -> [ChatMessage] {
        let read = lastRead
        return read + unconfirmed.filter { sent in !read.contains { $0.id == sent.id } } + outbox.map(\.shown)
    }

    /// No message of this person's has been sent yet: `is_first` for the next.
    private func sentNone() -> Bool { !ui.messages.contains { $0.fromYou && !$0.failed } }

    /// `message_sent`, once per message, when it was actually sent. `isFirst` nil: worked out now, for a failed
    /// send a read found landed -- the first of this person's if it is the only one.
    private func reportSent(_ date: ActiveDate, reason: ChatReason?, body: String, edited: Bool?, isFirst: Bool?) {
        visit?.sent += 1
        let first = isFirst ?? (lastRead.filter(\.fromYou).count <= 1)
        analytics?.report(ChatAnalytics.messageSent(
            ui.date ?? date, reason: reason, charCount: charCount(body), prefillEdited: edited, isFirst: first, now: clock()))
    }

    /// `chat_opened`, once per visit, once the chat has loaded -- never for a failed first load.
    private func reportOpened() {
        guard var visit, let date = ui.date, !visit.opened else { return }
        visit.opened = true
        self.visit = visit
        // The tutorial comes before the first open of each date's chat, so arriving from it is the first.
        analytics?.report(ChatAnalytics.chatOpened(date, entry: visit.entry, isFirst: visit.entry == .tutorial, now: clock()))
    }

    private func reset() {
        poll?.cancel()
        poll = nil
        lastRead = []
        unconfirmed.removeAll()
        outbox.removeAll()
        visit = nil
        guideShownAt = nil
        ui = ChatUi(now: clock())
    }

    private func seconds(from: Date, to: Date) -> Int { max(0, Int(to.timeIntervalSince(from))) }
}
