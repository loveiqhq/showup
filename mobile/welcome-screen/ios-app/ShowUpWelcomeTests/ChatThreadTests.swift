// ChatThreadTests.swift
// ShowUp · the thread's order, the system lines' copy, and the chat model's Date 05 half: the suggestions read
// with the thread, and `I agree` in it (SHOWUP-196 C, F, H, L). Android's `ChatThreadTest.kt` and
// `ChatThreadModelTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

/// The Date 05 suggestions the thread tests share: 18:00 in Berlin is 17:00Z on 2026-11-03.
enum ThreadSamples {
    static let berlin = TimeZone(identifier: "Europe/Berlin")!

    /// `hhmm` is Berlin time (CET, one hour ahead of UTC).
    static func at(_ hour: Int, _ minute: Int) -> Date {
        Date(timeIntervalSince1970: 1_793_664_000 + Double((hour - 1) * 3600 + minute * 60)) // 2026-11-03T00:00:00Z
    }

    /// Leonie's pending time suggestion: 18:30, answer by 17:30, sent 17:10.
    static let theirsTime = ChangeSuggestion(
        id: "t1", kind: .time, byYou: false, time: at(18, 30), place: nil,
        wasTime: at(18, 0), wasPlaceName: nil, deadline: at(17, 30), createdAt: at(17, 10))

    /// My pending place suggestion, sent 17:12.
    static let minePlace = ChangeSuggestion(
        id: "p1", kind: .place, byYou: true, time: nil,
        place: ChangePlace(placeId: "pl1", name: "Bar Lenz", type: .bar, address: "Reichenberger Str. 9"),
        wasTime: at(18, 0), wasPlaceName: "Cafe Krone", deadline: at(17, 30), createdAt: at(17, 12))

    static var agreedTime: ChangeSuggestion {
        var s = theirsTime
        s.status = .agreed
        s.answeredAt = at(17, 20)
        return s
    }

    static var agreedPlace: ChangeSuggestion {
        var s = minePlace
        s.id = "p2"
        s.status = .agreed
        s.answeredAt = at(17, 25)
        return s
    }

    static var replaced: ChangeSuggestion {
        var s = theirsTime
        s.id = "t0"
        s.status = .replaced
        s.createdAt = at(17, 5)
        return s
    }

    static var expired: ChangeSuggestion {
        var s = theirsTime
        s.id = "t2"
        s.status = .expired
        s.expiredAt = at(17, 30)
        s.expiredBindingTime = at(18, 0)
        s.expiredBindingPlaceName = "Cafe Krone"
        return s
    }

    static func message(_ id: String, _ hour: Int, _ minute: Int, fromYou: Bool) -> ChatMessage {
        ChatMessage(id: id, reason: nil, body: "m\(id)", fromYou: fromYou, sentAt: at(hour, minute),
                    read: fromYou ? false : nil)
    }
}

final class ChatThreadTests: XCTestCase {
    private let berlin = ThreadSamples.berlin
    private typealias S = ThreadSamples

    private func ids(_ items: [ThreadItem]) -> [String] { items.map { $0.id } }

    func testMessagesCardsAndSystemLinesInterleaveByTimeOldestFirst() {
        var agreed = S.theirsTime
        agreed.status = .agreed
        agreed.answeredAt = S.at(17, 20)
        let items = buildThread(
            messages: [S.message("a", 17, 0, fromYou: false), S.message("b", 17, 15, fromYou: true),
                       S.message("c", 17, 40, fromYou: false)],
            suggestions: [S.replaced, agreed], zone: berlin)
        XCTAssertEqual(ids(items), ["message:a", "card:t0", "card:t1", "message:b", "system:t1", "message:c"])
    }

    func testAnAgreedLineSitsAtAnsweredAtAndAnExpiredOneAtExpiredAtTheCardKeepsCreatedAt() {
        let items = buildThread(messages: [S.message("x", 17, 32, fromYou: false)],
                                suggestions: [S.expired, S.agreedPlace], zone: berlin)
        XCTAssertEqual(ids(items), ["card:t2", "card:p2", "system:p2", "system:t2", "message:x"])
    }

    func testAReplacedCardStaysInPlaceAndLeavesNoSystemLine() {
        let items = buildThread(messages: [], suggestions: [S.replaced, S.theirsTime], zone: berlin)
        XCTAssertEqual(ids(items), ["card:t0", "card:t1"])
        XCTAssertNil(systemLine(for: S.replaced, zone: berlin))
        XCTAssertNil(systemLine(for: S.theirsTime, zone: berlin))
    }

    func testMessagesThatFailedToSendStayAtTheFootAfterEveryCard() {
        var failed = S.message("f", 17, 0, fromYou: true)
        failed.failed = true
        let items = buildThread(messages: [failed, S.message("a", 17, 1, fromYou: false)],
                                suggestions: [S.theirsTime], zone: berlin)
        XCTAssertEqual(ids(items), ["message:a", "card:t1", "message:f"])
    }

    func testAnAgreedTimeReadsMeetTimeIsNowFromTheSuggestedTimeInLocalHHmm() {
        guard case .system(_, let text, let agreed, _)? = systemLine(for: S.agreedTime, zone: berlin) else {
            return XCTFail("expected a line")
        }
        XCTAssertEqual(text, "Meet time is now 18:30")
        XCTAssertTrue(agreed)
    }

    func testAnAgreedPlaceReadsMeetingPlaceIsNowWithTheVenue() {
        guard case .system(_, let text, _, _)? = systemLine(for: S.agreedPlace, zone: berlin) else {
            return XCTFail("expected a line")
        }
        XCTAssertEqual(text, "Meeting place is now Bar Lenz")
    }

    func testAnExpiryNamesTheDeadlineAndTheSnapshotThatStaysInTheMutedTone() {
        guard case .system(_, let text, let agreed, _)? = systemLine(for: S.expired, zone: berlin) else {
            return XCTFail("expected a line")
        }
        XCTAssertEqual(text, "No answer by 17:30 \u{2014} 18:00 at Cafe Krone stays.")
        XCTAssertFalse(agreed)
    }

    func testAnExpiryWithoutItsSnapshotPostsNoLineRatherThanAWrongOne() {
        var s = S.expired
        s.expiredBindingPlaceName = nil
        XCTAssertNil(systemLine(for: s, zone: berlin))
    }

    func testTheCardCopyIsTheTicketsWordForWord() {
        XCTAssertEqual(ChangeCopy.cardYou, "You suggested")
        XCTAssertEqual(ChangeCopy.cardThey("Leonie"), "Leonie suggested")
        XCTAssertEqual(ChangeCopy.waitingFor("Leonie"), "Waiting for Leonie")
        XCTAssertEqual(ChangeCopy.agreedAt("17:20"), "Agreed · 17:20")
        XCTAssertEqual(ChangeCopy.replaced, "Replaced by a new suggestion")
        XCTAssertEqual(ChangeCopy.expired("17:30"), "Expired · not agreed by 17:30")
        XCTAssertEqual(ChangeCopy.answerBy("17:30"), "Answer by 17:30")
        XCTAssertEqual(ChangeCopy.untilYouAgree + ChangeCopy.untilBinding(time: "18:00", place: "Cafe Krone") + ChangeCopy.untilRest,
                       "Until you agree, 18:00 at Cafe Krone stays binding. Not being there counts as a no-show.")
        XCTAssertEqual(ChangeCopy.agreeFailed, "Couldn't agree. Check your connection and try again.")
    }

    func testSuggestAnotherOpensThePickerOfTheSameKindWithEntryCounterThread() {
        XCTAssertEqual(counterThreadRoute(.time), AppRoute.changePicker(kind: .time, entry: .counterThread))
        XCTAssertEqual(counterThreadRoute(.place), AppRoute.changePicker(kind: .place, entry: .counterThread))
    }
}

@MainActor
final class ChatThreadModelTests: XCTestCase {
    private typealias S = ThreadSamples
    private let meet = ThreadSamples.at(18, 0)
    private let now = ThreadSamples.at(17, 42)
    private var models: [ChatModel] = []

    private var date: ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: meet.addingTimeInterval(-90 * 60), coordinateOpen: true,
                   place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term",
                   sharedVibes: [], sharedInterests: [], unread: nil, checkedIn: false, chatTutorialSeen: true)
    }

    override func tearDown() async throws {
        models.forEach { $0.onScreen(false) }
        models = []
    }

    private func settle(_ seconds: Double = 0.08) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func opened(_ store: FakeChatStore, _ dates: FakeDateStore, _ changes: FakeChangeStore,
                        _ analytics: RecordingAnalytics) async -> ChatModel {
        await dates.setRead(.success(.active(date)))
        let model = ChatModel(store: store, dates: dates, analytics: analytics, clock: { [now] in now },
                              zone: { TimeZone(identifier: "Europe/Berlin")! }, pollEvery: 5, received: nil,
                              changes: changes)
        models.append(model)
        model.open(date, entry: .openChat)
        await settle()
        return model
    }

    func testTheSuggestionsAreReadWithTheThreadAndHeldBesideTheMessagesNotAmongThem() async {
        let store = FakeChatStore(), dates = FakeDateStore(), changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.theirsTime]))
        await store.setThread(.success([S.message("a", 17, 0, fromYou: false)]))
        let model = await opened(store, dates, changes, RecordingAnalytics())
        XCTAssertEqual(model.ui.suggestions, [S.theirsTime])
        XCTAssertEqual(model.ui.messages.count, 1)
        let reads = await changes.suggestionReads
        XCTAssertEqual(reads, 1)
    }

    func testASuggestionIsNotAMessageSoItFiresNoMessageEvent() async {
        let store = FakeChatStore(), dates = FakeDateStore(), changes = FakeChangeStore()
        let analytics = RecordingAnalytics()
        await changes.setSuggestions(.success([S.theirsTime, S.minePlace]))
        let model = await opened(store, dates, changes, analytics)
        XCTAssertEqual(model.ui.suggestions.count, 2)
        XCTAssertTrue(analytics.entries.filter { $0.event == "message_received" || $0.event == "message_sent" }.isEmpty)
    }

    func testACardWithNoMessageIsNotTheStartGrid() async {
        let changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.minePlace]))
        let model = await opened(FakeChatStore(), FakeDateStore(), changes, RecordingAnalytics())
        XCTAssertFalse(model.ui.start)
    }

    func testIAgreeSendsTheThreadSurfaceThenReadsTheDateTheThreadAndTheSuggestionsAgain() async {
        let store = FakeChatStore(), dates = FakeDateStore(), changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.theirsTime]))
        let model = await opened(store, dates, changes, RecordingAnalytics())
        let dateReads = await dates.reads
        let threadReads = await store.calls.filter { $0 == "thread" }.count
        await changes.setSuggestions(.success([S.agreedTime]))
        model.agree("t1")
        await settle()
        let agrees = await changes.agrees
        XCTAssertEqual(agrees, ["d1/t1/thread"])
        let datesAfter = await dates.reads
        XCTAssertEqual(datesAfter, dateReads + 1)
        let threadAfter = await store.calls.filter { $0 == "thread" }.count
        XCTAssertEqual(threadAfter, threadReads + 1)
        XCTAssertEqual(model.ui.suggestions.first?.status, ChangeStatus.agreed)
        XCTAssertTrue(model.ui.agreeing.isEmpty)
        XCTAssertTrue(model.ui.agreeFailed.isEmpty)
    }

    func testWhileIAgreeIsOnItsWayBothButtonsAreInertAndASecondTapSendsNothing() async {
        let changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.theirsTime]))
        let model = await opened(FakeChatStore(), FakeDateStore(), changes, RecordingAnalytics())
        await changes.setAgreeDelay(0.2)
        model.agree("t1")
        await settle(0.05)
        XCTAssertEqual(model.ui.agreeing, ["t1"])
        model.agree("t1")
        await settle(0.05)
        let agrees = await changes.agrees
        XCTAssertEqual(agrees.count, 1)
        await settle(0.3)
        XCTAssertTrue(model.ui.agreeing.isEmpty)
    }

    func testAFailureShowsTheLineAndChangesNothingElseAndARetryIsTheSameCall() async {
        let changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.theirsTime]))
        let model = await opened(FakeChatStore(), FakeDateStore(), changes, RecordingAnalytics())
        let before = model.ui
        await changes.setAgree(.failed(.network))
        model.agree("t1")
        await settle()
        XCTAssertEqual(model.ui.agreeFailed, ["t1"])
        XCTAssertEqual(model.ui.suggestions, before.suggestions)
        XCTAssertEqual(model.ui.date, before.date)
        XCTAssertEqual(model.ui.messages, before.messages)
        await changes.setAgree(.agreed(S.agreedTime))
        await changes.setSuggestions(.success([S.agreedTime]))
        model.agree("t1")
        await settle()
        let agrees = await changes.agrees
        XCTAssertEqual(agrees.count, 2)
        XCTAssertTrue(model.ui.agreeFailed.isEmpty)
        XCTAssertEqual(model.ui.suggestions.first?.status, ChangeStatus.agreed)
    }

    func testA409ReadsAgainShowsNoErrorAndFiresNoEvent() async {
        let changes = FakeChangeStore()
        let analytics = RecordingAnalytics()
        await changes.setSuggestions(.success([S.theirsTime]))
        let model = await opened(FakeChatStore(), FakeDateStore(), changes, analytics)
        let before = analytics.entries.count
        let reads = await changes.suggestionReads
        var expired = S.expired
        expired.id = "t1"
        await changes.setAgree(.stale)
        await changes.setSuggestions(.success([expired]))
        model.agree("t1")
        await settle()
        XCTAssertTrue(model.ui.agreeFailed.isEmpty)
        let after = await changes.suggestionReads
        XCTAssertEqual(after, reads + 1)
        XCTAssertEqual(model.ui.suggestions.first?.status, ChangeStatus.expired)
        XCTAssertEqual(analytics.entries.count, before)
    }

    func testMyOwnCardAndACardThatIsNoLongerPendingCannotBeAgreed() async {
        let changes = FakeChangeStore()
        await changes.setSuggestions(.success([S.minePlace, S.replaced]))
        let model = await opened(FakeChatStore(), FakeDateStore(), changes, RecordingAnalytics())
        model.agree("p1")
        model.agree("t0")
        model.agree("unknown")
        await settle()
        let agrees = await changes.agrees
        XCTAssertTrue(agrees.isEmpty)
    }
}
