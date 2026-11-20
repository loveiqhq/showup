// DateChangeTests.swift
// ShowUp · Date 05 on the Date screen without a view: the rules, the model's I agree, the dev actions (SHOWUP-196)
//
// Android's `DateChangeLogicTest.kt`, the same cases and payloads. The views are covered on Android by
// `DateChangeScreenTest`; Swift has no view test harness, so what a view decides lives in these functions.

import XCTest
@testable import ShowUpWelcome

@MainActor
final class DateChangeTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z
    private var unlock: Date { meet.addingTimeInterval(-90 * 60) }
    private var later: Date { meet.addingTimeInterval(30 * 60) }
    private var deadline: Date { meet.addingTimeInterval(-30 * 60) }
    private var now: Date { meet.addingTimeInterval(-3 * 3600) }
    private var sent: Date { meet.addingTimeInterval(-4 * 3600) }

    private func time(byYou: Bool) -> ChangeSuggestion {
        ChangeSuggestion(id: "t1", kind: .time, byYou: byYou, time: later, place: nil, wasTime: meet, wasPlaceName: nil,
                         deadline: deadline, createdAt: sent)
    }

    private func place(byYou: Bool) -> ChangeSuggestion {
        ChangeSuggestion(id: "p1", kind: .place, byYou: byYou, time: nil,
                         place: ChangePlace(placeId: "pl", name: "Bar Lenz", type: .bar, address: "Reichenberger Str. 9"),
                         wasTime: meet, wasPlaceName: "Cafe Krone", deadline: deadline, createdAt: sent)
    }

    private func date(request: ChangeSuggestion? = nil, pending: [ChangeSuggestion] = [], timeWas: Date? = nil,
                      arrival: DateArrival? = nil) -> ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: unlock, coordinateOpen: false,
                   place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term", sharedVibes: [], sharedInterests: [],
                   unread: nil, checkedIn: false, chatTutorialSeen: true, arrival: arrival,
                   change: DateChange(cutoffAt: meet.addingTimeInterval(-1800), allowed: true, slots: [later],
                                      timeWas: timeWas, placeWas: nil, pending: pending),
                   changeRequest: request)
    }

    // MARK: rules

    func testHoistingOrderIsTheArrivalThenTheRequestThenAnUnreadMessage() {
        let arrival = DateArrival(checkedInAt: now)
        XCTAssertTrue(hoisted(DateState.locked, nil, time(byYou: false)))
        XCTAssertTrue(hoisted(DateState.available, arrival, time(byYou: false)))
        XCTAssertTrue(hoisted(DateState.unread, nil, nil))
        XCTAssertFalse(hoisted(DateState.available, nil, nil))
        XCTAssertFalse(requestShown(date(request: time(byYou: false), arrival: arrival)))
        XCTAssertTrue(requestShown(date(request: time(byYou: false))))
    }

    func testAnOwnPendingTimeGivesAWaitingTagTheMatchsGivesNoneAnAgreedOneGivesChangedWas() {
        let waiting = changeTagFor(date(pending: [time(byYou: true)]), kind: .time, now: now, zone: berlin)
        XCTAssertEqual(waiting, ChangeTagText.waiting("19:30"))
        XCTAssertNil(changeTagFor(date(pending: [time(byYou: false)]), kind: .time, now: now, zone: berlin))
        XCTAssertEqual(changeTagFor(date(pending: [place(byYou: true)]), kind: .place, now: now, zone: berlin),
                       ChangeTagText.waiting("Bar Lenz"))
        let agreed = date(timeWas: meet.addingTimeInterval(-1800))
        XCTAssertEqual(changeTagFor(agreed, kind: .time, now: now, zone: berlin), ChangeTagText.changed("18:30"))
        XCTAssertNil(changeTagFor(agreed, kind: .time, now: meet, zone: berlin), "hidden after the meet time")
        XCTAssertNil(changeTagFor(agreed, kind: .place, now: now, zone: berlin))
    }

    func testOneBindingNoteForAnyOwnPendingWithTheCurrentBindingValuesAndDeadline() {
        XCTAssertNil(bindingNoteFor(date(pending: [time(byYou: false)]), zone: berlin))
        XCTAssertEqual(bindingNoteFor(date(pending: [time(byYou: true), place(byYou: true)]), zone: berlin),
                       BindingNoteValues(time: "19:00", place: "Cafe Krone", deadline: "18:30"))
    }

    func testTheRequestCardTextCarriesEveryTicketValue() {
        let t = requestCardText(date(request: place(byYou: false)), request: place(byYou: false), now: now, zone: berlin)
        XCTAssertEqual(t.reason, ChatReason.changePlace)
        XCTAssertEqual(t.needs, "needs your answer \u{00B7} 15:00")
        XCTAssertEqual(t.value, "Bar Lenz")
        XCTAssertEqual(t.was, "Cafe Krone")
        XCTAssertEqual(t.placeLine, "Bar \u{00B7} Reichenberger Str. 9")
        XCTAssertEqual(t.deadline, "18:30")
        XCTAssertEqual(t.bindingTime, "19:00")
        XCTAssertEqual(t.opensLine, "Messaging opens at 17:30")
        XCTAssertNil(requestCardText(date(), request: time(byYou: false), now: now, zone: berlin).placeLine)
    }

    func testDateScreenViewedHasUnreadIsTrueWhileARequestShows() {
        let with = DateAnalytics.dateScreenViewed(date(request: time(byYou: false)), state: DateState.locked, now: now).1
        XCTAssertEqual(with["has_unread"] as? Bool, true)
        let without = DateAnalytics.dateScreenViewed(date(), state: DateState.locked, now: now).1
        XCTAssertEqual(without["has_unread"] as? Bool, false)
    }

    func testOpenChatOnTheRequestCardIsReadReplyAndSuggestAnotherOpensTheCounterCardPicker() {
        XCTAssertEqual(chatEntryFor(date(request: time(byYou: false))), ChatEntry.readReply)
        XCTAssertEqual(chatEntryFor(date(request: time(byYou: false), arrival: DateArrival(checkedInAt: now))), ChatEntry.openChat)
        XCTAssertEqual(counterCardRoute(.place), AppRoute.changePicker(kind: .place, entry: .counterCard))
    }

    // MARK: the model's I agree

    private func settle(_ seconds: Double = 0.08) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func shown(_ store: FakeDateStore, _ changes: FakeChangeStore) async -> DateModel {
        await store.setRead(.success(.active(date(request: time(byYou: false)))))
        let clockNow = now
        let zone = berlin
        let m = DateModel(store: store, analytics: RecordingAnalytics(), clock: { clockNow }, zone: { zone },
                          changes: changes)
        m.arrived(referrer: nil)
        await settle()
        return m
    }

    func testAgreeSendsSurfaceCoordinateCardThenReadsTheDateAgainAndClearsNothingItself() async {
        let store = FakeDateStore(), changes = FakeChangeStore()
        let m = await shown(store, changes)
        let before = await store.reads
        await store.setRead(.success(.active(date(request: nil, timeWas: meet))))
        m.agreeRequest()
        await settle()
        let agrees = await changes.agrees
        XCTAssertEqual(agrees, ["d1/t1/coordinate_card"])
        let after = await store.reads
        XCTAssertEqual(after, before + 1)
        XCTAssertNil(m.state.date?.changeRequest)
        XCTAssertFalse(m.state.agreeing)
        XCTAssertFalse(m.state.agreeFailed)
    }

    func testA409ReadsOnlyWithNoError() async {
        let store = FakeDateStore(), changes = FakeChangeStore()
        let m = await shown(store, changes)
        await changes.setAgree(.stale)
        let before = await store.reads
        m.agreeRequest()
        await settle()
        let after = await store.reads
        XCTAssertEqual(after, before + 1)
        XCTAssertFalse(m.state.agreeFailed)
        XCTAssertFalse(m.state.agreeing)
    }

    func testAFailureShowsTheLineAndChangesNothingAndARetryThatSucceedsClearsIt() async {
        let store = FakeDateStore(), changes = FakeChangeStore()
        let m = await shown(store, changes)
        await changes.setAgree(.failed(CallFailure.network))
        let before = await store.reads
        m.agreeRequest()
        await settle()
        XCTAssertTrue(m.state.agreeFailed)
        let unchanged = await store.reads
        XCTAssertEqual(unchanged, before, "no re-read, the card stays")
        XCTAssertEqual(m.state.date?.changeRequest?.id, "t1")
        await changes.setAgree(.agreed(time(byYou: false)))
        await store.setRead(.success(.active(date(request: nil))))
        m.agreeRequest()
        await settle()
        XCTAssertFalse(m.state.agreeFailed)
        let agrees = await changes.agrees
        XCTAssertEqual(agrees.count, 2)
    }

    func testWhileInFlightASecondTapSendsNothingAndTheStateSaysAgreeing() async {
        let store = FakeDateStore(), changes = FakeChangeStore()
        let m = await shown(store, changes)
        await changes.setAgreeDelay(0.2)
        m.agreeRequest()
        await settle(0.05)
        XCTAssertTrue(m.state.agreeing)
        m.agreeRequest()
        await settle(0.05)
        let agrees = await changes.agrees
        XCTAssertEqual(agrees.count, 1)
        await settle(0.3)
        XCTAssertFalse(m.state.agreeing)
    }

    func testAgreeWithNoRequestDoesNothing() async {
        let store = FakeDateStore(), changes = FakeChangeStore()
        await store.setRead(.success(.active(date())))
        let clockNow = now
        let zone = berlin
        let m = DateModel(store: store, analytics: RecordingAnalytics(), clock: { clockNow }, zone: { zone },
                          changes: changes)
        m.arrived(referrer: nil)
        await settle()
        m.agreeRequest()
        await settle()
        let agrees = await changes.agrees
        XCTAssertEqual(agrees.count, 0)
    }

    // MARK: the dev actions

    func testTheDevActionsAreTheFourTask3RoutesAndHaveTheirLabels() {
        XCTAssertTrue(DevDateAction.allCases.contains(DevDateAction.matchSuggestsTime))
        XCTAssertEqual(DevDateAction.matchSuggestsTime.label, "Leonie suggests a later time (SHOWUP-196 E / J)")
        XCTAssertEqual(DevDateAction.matchSuggestsPlace.label, "Leonie suggests another place (SHOWUP-196 E / J)")
        XCTAssertEqual(DevDateAction.matchAgrees.label, "Leonie agrees to my suggestion (SHOWUP-196 G)")
        XCTAssertEqual(DevDateAction.expireSuggestions.label, "Expire suggestions now (SHOWUP-196 H)")
    }
}
