// ChatRulesTests.swift
// ShowUp · the chat's rules and routing (SHOWUP-194), each a plain function the model and the screen read.
// Android's `ChatRulesTest.kt` and `ChatRoutingTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

@MainActor
final class ChatRulesTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private var at: Date { meet.addingTimeInterval(-82 * 60) }

    private func msg(_ id: String, _ reason: ChatReason?, fromYou: Bool) -> ChatMessage {
        ChatMessage(id: id, reason: reason, body: "text \(id)", fromYou: fromYou, sentAt: at, read: fromYou ? false : nil)
    }

    func testTheEndCardWaitsForAnICantMakeItTodayMessageFromEitherSide() {
        XCTAssertFalse(endCardVisible(messages: [], selected: nil))
        XCTAssertFalse(endCardVisible(messages: [msg("1", .runningLate, fromYou: true), msg("2", nil, fromYou: false)], selected: nil))
        XCTAssertTrue(endCardVisible(messages: [msg("1", .cantMakeIt, fromYou: true)], selected: nil))
        XCTAssertTrue(endCardVisible(messages: [msg("1", .cantMakeIt, fromYou: false)], selected: .onMyWay))
    }

    func testTheEndCardStepsAsideWhileICantMakeItTodayIsSelected() {
        XCTAssertFalse(endCardVisible(messages: [msg("1", .cantMakeIt, fromYou: false)], selected: .cantMakeIt))
    }

    func testTheFieldIsLockedOnlyWhileTheGuideWaitsForGotIt() {
        XCTAssertTrue(fieldLocked(selected: .cantMakeIt, guide: .locked))
        XCTAssertFalse(fieldLocked(selected: .cantMakeIt, guide: .acked))
        XCTAssertFalse(fieldLocked(selected: .runningLate, guide: .none))
        XCTAssertFalse(fieldLocked(selected: nil, guide: .none))
    }

    func testSendIsEnabledWheneverTheFieldHasTextAndIsNotLocked() {
        XCTAssertTrue(canSend(text: "Hi", locked: false))
        XCTAssertFalse(canSend(text: "", locked: false))
        XCTAssertFalse(canSend(text: "   \n ", locked: false))
        XCTAssertFalse(canSend(text: "Hi", locked: true))
    }

    func testTheCountdownReadsInHoursAndMinutesAndIsGoneFromTheMeetTimeOn() {
        XCTAssertEqual(countdown(now: meet.addingTimeInterval(-78 * 60), meetAt: meet), "in 1h 18m")
        XCTAssertEqual(countdown(now: meet.addingTimeInterval(-45 * 60), meetAt: meet), "in 0h 45m")
        XCTAssertEqual(countdown(now: meet.addingTimeInterval(-30), meetAt: meet), "in 0h 0m")
        XCTAssertNil(countdown(now: meet, meetAt: meet))
        XCTAssertNil(countdown(now: meet.addingTimeInterval(60), meetAt: meet))
    }

    func testPrefillEditedComparesTheSentTextWithTheDraftAndIsNilWithNoDraft() {
        let draft = ChatReason.runningLate.draft ?? ""
        XCTAssertEqual(prefillEdited(reason: .runningLate, sent: draft), false)
        XCTAssertEqual(prefillEdited(reason: .runningLate, sent: "  \(draft) "), false)
        XCTAssertEqual(prefillEdited(reason: .runningLate, sent: "Running 20 min late, sorry!"), true)
        XCTAssertNil(prefillEdited(reason: .cantMakeIt, sent: "So sorry, I can't make it."))
        XCTAssertNil(prefillEdited(reason: nil, sent: "Hi"))
    }

    func testCantMakeItFromIsWhoSentTheLatestOneOrNone() {
        XCTAssertEqual(cantMakeItFrom([msg("1", .cantMakeIt, fromYou: true)]), "self")
        XCTAssertEqual(cantMakeItFrom([msg("1", .cantMakeIt, fromYou: false), msg("2", nil, fromYou: true)]), "match")
        XCTAssertEqual(cantMakeItFrom([msg("1", .cantMakeIt, fromYou: true), msg("2", .cantMakeIt, fromYou: false)]), "match")
        XCTAssertNil(cantMakeItFrom([msg("1", .runningLate, fromYou: true)]))
    }

    func testAMessageIsCappedAtTheServersLimitWithoutSplittingACharacter() {
        XCTAssertEqual(cappedMessage(String(repeating: "a", count: 1200)).utf16.count, 1000)
        XCTAssertEqual(cappedMessage("Hi"), "Hi")
    }

    // MARK: - routing

    func testTheTutorialComesFirstUntilOpenTheChatHasBeenTapped() {
        var date = ChatSamples.date
        date.chatTutorialSeen = false
        XCTAssertEqual(chatRoute(date, entry: .openChat), .chatTutorial)
        date.chatTutorialSeen = true
        XCTAssertEqual(chatRoute(date, entry: .openChat), .chat(.openChat))
    }

    func testReadAndReplyIsTheEntryWhenAMessageWaits() {
        var date = ChatSamples.date
        XCTAssertEqual(chatEntryFor(date), .openChat)
        date.unread = DateUnread(count: 1, reason: .runningLate, body: "Late", sentAt: at)
        XCTAssertEqual(chatEntryFor(date), .readReply)
    }

    func testAnEntryReadsBackFromItsWireValue() {
        XCTAssertEqual(ChatEntry.fromWire("tutorial"), .tutorial)
        XCTAssertNil(ChatEntry.fromWire("somewhere"))
    }

    func testTheHostReportsTheTutorialAndItsAcknowledgementWithTheReadTime() {
        let analytics = RecordingAnalytics()
        var now = meet
        let events = ChatHostEvents(analytics: analytics, clock: { now })
        events.tutorialShown(ChatSamples.date)
        now = now.addingTimeInterval(9)
        events.tutorialAcknowledged(ChatSamples.date)
        XCTAssertEqual(analytics.names(), ["screen_viewed", "chat_tutorial_shown", "chat_tutorial_acknowledged"])
        let view = analytics.entries[0].properties
        XCTAssertEqual(view["screen_id"] as? String, "date_chat_tutorial")
        XCTAssertEqual(view["screen_name"] as? String, "Date - Chat tutorial")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "date_screen")
        XCTAssertEqual(analytics.entries[1].properties["date_id"] as? String, ChatSamples.date.id)
        XCTAssertEqual(analytics.entries[2].properties["read_time_s"] as? Int, 9)
    }

    func testTheChatIsOneViewPerOpenAndTheOutcomeOneForGAndH() {
        let analytics = RecordingAnalytics()
        let events = ChatHostEvents(analytics: analytics)
        events.chatShown(referrer: .dateChatTutorial)
        events.chatShown(referrer: .dateScreen)
        events.outcomeShown(referrer: .dateChat)
        XCTAssertEqual(analytics.entries.map { $0.properties["screen_id"] as? String },
                       ["date_chat", "date_chat", "date_ended_in_chat"])
        XCTAssertEqual(analytics.entries.map { $0.properties["referrer_screen_id"] as? String },
                       ["date_chat_tutorial", "date_screen", "date_chat"])
    }

    func testAnOutcomeRoutesToTheOutcomeScreenWithEverythingItDraws() {
        let outcome = EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false)
        XCTAssertEqual(outcomeRoute(outcome, photoURL: nil), .dateEnded(outcome, photoURL: nil))
    }

    func testTheLastTwoWordsStayTogetherAndNothingElseChanges() {
        XCTAssertEqual(lastWordsTogether("Read the note above, then tap Got it"), "Read the note above, then tap Got\u{00A0}it")
        XCTAssertEqual(lastWordsTogether("Alone"), "Alone")
        XCTAssertEqual(lastWordsTogether("Read the note above, then tap Got it").replacingOccurrences(of: "\u{00A0}", with: " "),
                       "Read the note above, then tap Got it")
    }

    func testTheOutcomesOwnPhotoWinsAndTheHeldDatesStandsInWhenItHasNone() {
        let held = URL(string: "https://x/held.jpg")
        let own = URL(string: "https://x/own.jpg")
        var outcome = EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false)
        XCTAssertEqual(outcomeRoute(outcome, photoURL: held), .dateEnded(outcome, photoURL: held))
        outcome.photoUrl = own
        XCTAssertEqual(outcomeRoute(outcome, photoURL: held), .dateEnded(outcome, photoURL: own))
    }

    func testAnUnseenEndedOutcomeOpensTheDateScreenAtLaunch() {
        let ended: Result<DateRead, CallFailure> = .success(.ended(EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false)))
        XCTAssertEqual(launchTarget(date: ended, checkInActive: true), .date)
        XCTAssertEqual(endedOutcome(ended), EndedInChat(dateId: "d1", name: "Leonie", endedByYou: false))
    }
}
