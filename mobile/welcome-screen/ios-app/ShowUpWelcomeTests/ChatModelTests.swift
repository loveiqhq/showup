// ChatModelTests.swift
// ShowUp · the chat's model: selection, drafts, the guide lock, the end card, polling, the end and tracking
// (SHOWUP-194). Android's `ChatViewModelTest.kt`, case for case, the five Review Focus cases among them.
//
// The poll runs every 50 ms here and the tests wait in real time, as `DateModelTests` does; each test stops
// the poll it started.

import XCTest
@testable import ShowUpWelcome

/// Answers from values the test sets; records every call by name.
actor FakeChatStore: ChatStoring {
    private(set) var thread: Result<[ChatMessage], CallFailure> = .success([])
    private(set) var sendFails: CallFailure?
    private(set) var end: EndResult = .ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: true))
    /// How long End date takes to answer: the end on its way.
    private(set) var endDelay: Double = 0
    private(set) var calls: [String] = []
    private(set) var sent: [String] = []
    private var next = 0

    func setThread(_ value: Result<[ChatMessage], CallFailure>) { thread = value }
    func setSendFails(_ value: CallFailure?) { sendFails = value }
    func setEnd(_ value: EndResult) { end = value }
    func setEndDelay(_ value: Double) { endDelay = value }

    func thread(dateId: String) async -> Result<[ChatMessage], CallFailure> {
        calls.append("thread")
        return thread
    }

    func send(dateId: String, reason: ChatReason?, body: String) async -> Result<ChatMessage, CallFailure> {
        calls.append("send")
        if let sendFails { return .failure(sendFails) }
        sent.append("\(reason?.rawValue ?? "none"):\(body)")
        next += 1
        let message = ChatMessage(id: "s\(next)", reason: reason, body: body, fromYou: true, sentAt: Date(), read: false)
        // The server has it from now on: the next read returns it.
        if case .success(let list) = thread { thread = .success(list + [message]) }
        return .success(message)
    }

    func markRead(dateId: String) async { calls.append("read") }
    func markTutorialSeen(dateId: String) async { calls.append("tutorial") }
    func endInChat(dateId: String) async -> EndResult {
        calls.append("end")
        if endDelay > 0 { try? await Task.sleep(nanoseconds: UInt64(endDelay * 1_000_000_000)) }
        return end
    }
    func markEndSeen(dateId: String) async { calls.append("end-seen") }
}

@MainActor
final class ChatModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private var now = Date(timeIntervalSince1970: 1_793_728_800 - 78 * 60) // 17:42 Berlin: 78 min to go
    private var models: [ChatModel] = []

    private var date: ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: meet.addingTimeInterval(-90 * 60), coordinateOpen: true,
                   place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term",
                   sharedVibes: [], sharedInterests: [], unread: nil, checkedIn: false, chatTutorialSeen: true)
    }

    private func theirs(_ id: String, _ reason: ChatReason?, _ body: String? = nil) -> ChatMessage {
        ChatMessage(id: id, reason: reason, body: body ?? "From Leonie \(id)", fromYou: false, sentAt: now, read: nil)
    }

    private func mine(_ id: String, _ reason: ChatReason?) -> ChatMessage {
        ChatMessage(id: id, reason: reason, body: "From me \(id)", fromYou: true, sentAt: now, read: false)
    }

    private func settle(_ seconds: Double = 0.08) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    /// Opens the chat and lets the first read land.
    private func opened(_ store: FakeChatStore, _ dates: FakeDateStore, _ analytics: RecordingAnalytics,
                        entry: ChatEntry = .openChat, ledger: InMemoryReceivedLedger? = nil) async -> ChatModel {
        await dates.setRead(.success(.active(date)))
        let model = ChatModel(store: store, dates: dates, analytics: analytics, clock: { [unowned self] in self.now },
                              zone: { TimeZone(identifier: "Europe/Berlin")! }, pollEvery: 0.05, received: ledger)
        models.append(model)
        model.open(date, entry: entry)
        await settle()
        return model
    }

    // MARK: - the final review's cases (Android's, case for case)

    func testAMessageReportedInAnEarlierLaunchIsNotReportedAgainAndANewOneIsOnce() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let analytics = RecordingAnalytics()
        let ledger = InMemoryReceivedLedger()
        await store.setThread(.success([theirs("t1", .runningLate)]))
        let first = await opened(store, dates, analytics, ledger: ledger)
        first.onScreen(false)
        XCTAssertEqual(analytics.entries.filter { $0.event == "message_received" }.count, 1)
        // The next launch: a new model on the same phone, the same thread.
        let t1 = theirs("t1", .runningLate)
        now = now.addingTimeInterval(60)
        let second = await opened(store, dates, analytics, ledger: ledger)
        XCTAssertEqual(analytics.entries.filter { $0.event == "message_received" }.count, 1, "an old message is not news")
        await store.setThread(.success([t1, theirs("t2", nil)]))
        await settle(0.15)
        XCTAssertEqual(analytics.entries.filter { $0.event == "message_received" }.count, 2)
        second.onScreen(false)
    }

    func testTheCountdownMovesWithTheClockWhileEveryReadFails() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let model = await opened(store, dates, RecordingAnalytics())
        XCTAssertEqual(model.ui.countdownText, "in 1h 18m")
        await dates.setRead(.failure(.network))
        await store.setThread(.failure(.network))
        now = meet.addingTimeInterval(60)
        await settle(0.15)
        XCTAssertNil(model.ui.countdownText, "offline too, past the meet time the countdown leaves the header")
        model.onScreen(false)
    }

    func testASendRefusedBecauseTheDateHasEndedShowsTheOutcomeNotASendFailure() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let model = await opened(store, dates, RecordingAnalytics())
        // The poll stopped, so only the send can find the end.
        model.onScreen(false)
        model.pick(.runningLate, surface: .dock)
        await store.setSendFails(.server)
        let ended = EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false)
        await dates.setRead(.success(.ended(ended)))
        model.send()
        await settle()
        XCTAssertFalse(model.ui.sendFailed)
        XCTAssertEqual(model.ui.outcome, ended)
    }

    func testKeepTheDateAndTheScrimDoNothingWhileTheEndIsOnItsWay() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        await store.setThread(.success([theirs("t1", .cantMakeIt)]))
        await store.setEndDelay(0.3)
        let model = await opened(store, dates, RecordingAnalytics())
        model.openEndSheet()
        model.endDate()
        await settle(0.05)
        XCTAssertTrue(model.ui.ending)
        model.keepDate()
        XCTAssertTrue(model.ui.endSheet, "the sheet stays while the end is on its way")
        await settle(0.4)
        XCTAssertEqual(model.ui.outcome, EndedInChat(dateId: "d1", name: "Leonie", endedByYou: true))
        model.onScreen(false)
    }

    func testTheHeaderFollowsTheLatestAgreedTimeFromTheDateRead() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let model = await opened(store, dates, RecordingAnalytics())
        var later = date
        later.meetAt = meet.addingTimeInterval(30 * 60)
        later.place = DatePlace(name: "Bar Nova", address: nil)
        await dates.setRead(.success(.active(later)))
        await settle(0.15)
        XCTAssertEqual(model.ui.date, later)
        model.onScreen(false)
    }

    func testAFirstReadThatFailsShowsTheFailureAndALaterFailedPollKeepsTheThread() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        await store.setThread(.failure(.network))
        let model = await opened(store, dates, RecordingAnalytics())
        XCTAssertEqual(model.ui.failed, .network)
        await store.setThread(.success([theirs("t1", nil)]))
        model.retry()
        await settle()
        XCTAssertNil(model.ui.failed)
        XCTAssertEqual(model.ui.messages.count, 1)
        await store.setThread(.failure(.server))
        await settle(0.15)
        XCTAssertNil(model.ui.failed)
        XCTAssertEqual(model.ui.messages.count, 1)
        model.onScreen(false)
    }

    func testNoEndSheetWithoutAnICantMakeItTodayMessage() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let analytics = RecordingAnalytics()
        await store.setThread(.success([theirs("t1", .runningLate)]))
        let model = await opened(store, dates, analytics)
        model.openEndSheet()
        XCTAssertFalse(model.ui.endSheet)
        XCTAssertTrue(analytics.entries.filter { $0.event == "end_date_sheet_opened" }.isEmpty)
        model.onScreen(false)
    }

    func testADateGoneSomeOtherWayLeavesTheChat() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        let model = await opened(store, dates, RecordingAnalytics())
        await dates.setRead(.success(DateRead.none))
        await settle(0.15)
        XCTAssertTrue(model.ui.left)
    }

    override func tearDown() async throws {
        for model in models { model.onScreen(false) }
        models.removeAll()
        try await super.tearDown()
    }

    // MARK: selection, drafts and the guide

    func testAChipSelectsItselfAndReplacesTheFieldWithItsDraft() async {
        let m = await opened(FakeChatStore(), FakeDateStore(), RecordingAnalytics())
        m.edit("Something I typed")
        m.pick(.runningLate, surface: .dock)
        XCTAssertEqual(m.ui.selected, .runningLate)
        XCTAssertEqual(m.ui.draft, "Running about 10 min late — sorry! Almost there.")
        m.pick(.onMyWay, surface: .dock)
        XCTAssertEqual(m.ui.draft, "On my way now — see you in a few!")
    }

    func testICantMakeItTodayLocksTheFieldUntilGotItAndAnotherChipRemovesTheGuide() async {
        let m = await opened(FakeChatStore(), FakeDateStore(), RecordingAnalytics())
        m.pick(.runningLate, surface: .entry)
        m.pick(.cantMakeIt, surface: .dock)
        XCTAssertEqual(m.ui.draft, "")
        XCTAssertEqual(m.ui.guide, .locked)
        XCTAssertTrue(m.ui.locked)
        XCTAssertFalse(m.ui.sendEnabled)
        m.edit("typed while locked")
        XCTAssertEqual(m.ui.draft, "", "the locked field takes nothing")
        m.ackGuide()
        XCTAssertEqual(m.ui.guide, .acked)
        m.edit("So sorry, something came up.")
        XCTAssertTrue(m.ui.sendEnabled)
        m.pick(.onMyWay, surface: .dock)
        XCTAssertEqual(m.ui.guide, .none)
        m.pick(.cantMakeIt, surface: .dock)
        XCTAssertEqual(m.ui.guide, .locked, "picking it again shows the guide again, locked")
    }

    // MARK: sending

    func testSendPostsTheReasonAndTheTrimmedTextThenClearsTheChipAndTheField() async {
        let store = FakeChatStore()
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        m.pick(.runningLate, surface: .dock)
        m.edit("Running 20 min late, sorry!  ")
        m.send()
        await settle()
        let sent = await store.sent
        XCTAssertEqual(sent, ["running_late:Running 20 min late, sorry!"])
        XCTAssertNil(m.ui.selected)
        XCTAssertEqual(m.ui.draft, "")
        XCTAssertEqual(m.ui.messages.map(\.body), ["Running 20 min late, sorry!"])
    }

    func testMessageSentCarriesTheRegistrysFieldsAndNeverTheText() async {
        let analytics = RecordingAnalytics()
        let m = await opened(FakeChatStore(), FakeDateStore(), analytics)
        m.pick(.runningLate, surface: .dock)
        m.send()
        await settle()
        let first = analytics.entries.first { $0.event == "message_sent" }!.properties
        XCTAssertEqual(first["date_id"] as? String, "d1")
        XCTAssertEqual(first["reason"] as? String, "running_late")
        XCTAssertEqual(first["char_count"] as? Int, "Running about 10 min late — sorry! Almost there.".utf16.count)
        XCTAssertEqual(first["prefill_edited"] as? Bool, false)
        XCTAssertEqual(first["minutes_before_start"] as? Int, 78)
        XCTAssertEqual(first["is_first"] as? Bool, true)
        XCTAssertEqual(Set(first.keys), ["date_id", "reason", "char_count", "prefill_edited", "minutes_before_start",
                                         "is_first", "sensitivity_class", "field_registry_version"])
        XCTAssertFalse(first.values.contains { "\($0)".contains("Running about") })
        m.edit("No chip this time")
        m.send()
        await settle()
        let second = analytics.entries.last { $0.event == "message_sent" }!.properties
        XCTAssertTrue(second["reason"] is JSONNull)
        XCTAssertTrue(second["prefill_edited"] is JSONNull)
        XCTAssertEqual(second["is_first"] as? Bool, false)
    }

    func testAFailedSendKeepsTheDraftAndTheChipAndAddsNothing() async {
        let store = FakeChatStore()
        let analytics = RecordingAnalytics()
        let m = await opened(store, FakeDateStore(), analytics)
        await store.setSendFails(.network)
        m.pick(.onMyWay, surface: .dock)
        m.send()
        await settle()
        XCTAssertEqual(m.ui.selected, .onMyWay)
        XCTAssertEqual(m.ui.draft, "On my way now — see you in a few!")
        XCTAssertTrue(m.ui.messages.isEmpty)
        XCTAssertTrue(m.ui.sendFailed)
        XCTAssertFalse(m.ui.sending)
        XCTAssertFalse(analytics.names().contains("message_sent"))
        m.edit("On my way now!")
        XCTAssertFalse(m.ui.sendFailed, "editing clears the failure")
    }

    // MARK: polling

    func testAPollNeverTouchesTheDock() async {
        let store = FakeChatStore()
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        m.pick(.cantMakeIt, surface: .dock)
        m.ackGuide()
        m.edit("So sorry Leonie")
        await store.setThread(.success([theirs("t1", .runningLate)]))
        await settle(0.15)
        XCTAssertEqual(m.ui.messages.count, 1)
        XCTAssertEqual(m.ui.selected, .cantMakeIt)
        XCTAssertEqual(m.ui.draft, "So sorry Leonie")
        XCTAssertEqual(m.ui.guide, .acked)
    }

    func testAPollThatBringsTheMatchsMessagesReportsEachOnceAndMarksRead() async {
        let store = FakeChatStore()
        let analytics = RecordingAnalytics()
        let m = await opened(store, FakeDateStore(), analytics)
        await store.setThread(.success([theirs("t1", .runningLate, "Ten min late")]))
        await settle(0.15)
        await store.setThread(.success([theirs("t1", .runningLate, "Ten min late"), theirs("t2", nil, "Here")]))
        await settle(0.2)
        let received = analytics.entries.filter { $0.event == "message_received" }.map(\.properties)
        XCTAssertEqual(received.count, 2)
        XCTAssertEqual(received[0]["reason"] as? String, "running_late")
        XCTAssertEqual(received[0]["char_count"] as? Int, 12)
        XCTAssertTrue(received[1]["reason"] is JSONNull)
        let reads = await store.calls.filter { $0 == "read" }.count
        XCTAssertEqual(reads, 3, "open, and one for each poll that brought something")
        _ = m
    }

    func testOnScreenFalseStopsThePoll() async {
        let store = FakeChatStore()
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        m.onScreen(false)
        await settle(0.06)
        let before = await store.calls.count
        await settle(0.2)
        let after = await store.calls.count
        XCTAssertEqual(before, after)
    }

    func testTheThreadTheDraftTheChipAndTheGuideSurviveLeavingAndComingBack() async {
        let store = FakeChatStore()
        await store.setThread(.success([theirs("t1", .runningLate)]))
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        m.pick(.cantMakeIt, surface: .dock)
        m.ackGuide()
        m.edit("Sorry Leonie")
        m.close()
        m.open(date, entry: .openChat)
        await settle()
        XCTAssertEqual(m.ui.selected, .cantMakeIt)
        XCTAssertEqual(m.ui.draft, "Sorry Leonie")
        XCTAssertEqual(m.ui.guide, .acked)
        XCTAssertEqual(m.ui.messages.count, 1)
    }

    // MARK: ending the date

    func testTheEndCardWaitsWhileICantMakeItTodayIsSelected() async {
        let store = FakeChatStore()
        await store.setThread(.success([theirs("t1", .cantMakeIt)]))
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        XCTAssertTrue(m.ui.endCardShown)
        m.pick(.cantMakeIt, surface: .dock)
        XCTAssertFalse(m.ui.endCardShown)
        m.pick(.onMyWay, surface: .dock)
        XCTAssertTrue(m.ui.endCardShown)
    }

    func testEndDateOpensTheSheetWithCantMakeItFromAndTheEnderGetsTheOutcome() async {
        let store = FakeChatStore()
        let analytics = RecordingAnalytics()
        await store.setThread(.success([mine("m1", .cantMakeIt)]))
        let m = await opened(store, FakeDateStore(), analytics)
        m.openEndSheet()
        XCTAssertTrue(m.ui.endSheet)
        let opened = analytics.entries.first { $0.event == "end_date_sheet_opened" }!.properties
        XCTAssertEqual(opened["cant_make_it_from"] as? String, "self")
        XCTAssertEqual(opened["minutes_before_start"] as? Int, 78)
        m.keepDate()
        XCTAssertFalse(m.ui.endSheet)
        m.openEndSheet()
        m.endDate()
        await settle()
        XCTAssertEqual(m.ui.outcome, EndedInChat(dateId: "d1", name: "Leonie", endedByYou: true))
        XCTAssertFalse(m.ui.endSheet)
    }

    func testAPollThatFindsTheDateEndedShowsTheOutcomeClosingTheSheetAndDroppingTheDraft() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        await store.setThread(.success([theirs("t1", .cantMakeIt)]))
        let m = await opened(store, dates, RecordingAnalytics())
        m.pick(.onMyWay, surface: .dock)
        m.openEndSheet()
        await dates.setRead(.success(.ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))))
        await settle(0.15)
        XCTAssertEqual(m.ui.outcome, EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))
        XCTAssertFalse(m.ui.endSheet)
        XCTAssertEqual(m.ui.draft, "")
        XCTAssertNil(m.ui.selected)
    }

    func testA409OnEndDateShowsTheOutcomeTheNextDateReadBrings() async {
        let store = FakeChatStore()
        let dates = FakeDateStore()
        await store.setThread(.success([theirs("t1", .cantMakeIt)]))
        await store.setEnd(.alreadyEnded)
        let m = await opened(store, dates, RecordingAnalytics())
        m.openEndSheet()
        await dates.setRead(.success(.ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))))
        m.endDate()
        await settle()
        XCTAssertEqual(m.ui.outcome, EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))
    }

    func testAFailedEndDateKeepsTheSheetOpenAndSaysWhy() async {
        let store = FakeChatStore()
        await store.setThread(.success([theirs("t1", .cantMakeIt)]))
        await store.setEnd(.failed(.network))
        let m = await opened(store, FakeDateStore(), RecordingAnalytics())
        m.openEndSheet()
        m.endDate()
        await settle()
        XCTAssertNil(m.ui.outcome)
        XCTAssertTrue(m.ui.endSheet)
        XCTAssertFalse(m.ui.ending)
        XCTAssertEqual(m.ui.endFailed, .network)
    }

    // MARK: the rest of family L

    func testChatOpenedCarriesEntryIsFirstAndMinutesBeforeStart() async {
        let analytics = RecordingAnalytics()
        let m = await opened(FakeChatStore(), FakeDateStore(), analytics, entry: .tutorial)
        let first = analytics.entries.first { $0.event == "chat_opened" }!.properties
        XCTAssertEqual(first["entry"] as? String, "tutorial")
        XCTAssertEqual(first["is_first"] as? Bool, true)
        XCTAssertEqual(first["minutes_before_start"] as? Int, 78)
        m.close()
        m.open(date, entry: .readReply)
        let second = analytics.entries.last { $0.event == "chat_opened" }!.properties
        XCTAssertEqual(second["entry"] as? String, "read_reply")
        XCTAssertEqual(second["is_first"] as? Bool, false)
    }

    func testActionChipUsedFiresOnEveryTapWithItsSurface() async {
        let analytics = RecordingAnalytics()
        let m = await opened(FakeChatStore(), FakeDateStore(), analytics)
        m.pick(.runningLate, surface: .entry)
        m.pick(.runningLate, surface: .dock)
        m.pick(.cantMakeIt, surface: .dock)
        let used = analytics.entries.filter { $0.event == "action_chip_used" }.map(\.properties)
        XCTAssertEqual(used.map { $0["reason"] as? String }, ["running_late", "running_late", "cant_make_it"])
        XCTAssertEqual(used.map { $0["surface"] as? String }, ["entry", "dock", "dock"])
    }

    func testGotItFiresCancelGuideAcknowledgedWithTheReadTime() async {
        let analytics = RecordingAnalytics()
        let m = await opened(FakeChatStore(), FakeDateStore(), analytics)
        m.pick(.cantMakeIt, surface: .dock)
        now = now.addingTimeInterval(12)
        m.ackGuide()
        let ack = analytics.entries.first { $0.event == "cancel_guide_acknowledged" }!.properties
        XCTAssertEqual(ack["read_time_s"] as? Int, 12)
        XCTAssertEqual(ack["minutes_before_start"] as? Int, 77)
    }

    func testChatClosedCarriesTheVisitsMessagesAndItsLength() async {
        let analytics = RecordingAnalytics()
        let m = await opened(FakeChatStore(), FakeDateStore(), analytics)
        m.pick(.onMyWay, surface: .dock)
        m.send()
        await settle()
        now = now.addingTimeInterval(95)
        m.close()
        let closed = analytics.entries.first { $0.event == "chat_closed" }!.properties
        XCTAssertEqual(closed["messages_sent"] as? Int, 1)
        XCTAssertEqual(closed["duration_s"] as? Int, 95)
    }
}
