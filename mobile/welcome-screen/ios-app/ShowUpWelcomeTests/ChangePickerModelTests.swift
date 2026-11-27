// ChangePickerModelTests.swift
// ShowUp · the picker's loading, search, selection and sending (SHOWUP-196, Date 05 A, B, K, M, N)
// Android's `ChangePickerViewModelTest.kt`, case for case. The clock, the id source and the store are injected and
// the debounce is short, so the 300 ms rule is a few milliseconds here.

import XCTest
@testable import ShowUpWelcome

let nearbyPlaces = [
    PlaceOption(placeId: "p-lenz", name: "Bar Lenz", type: .bar, address: "Lausitzer Platz 9, Kreuzberg", distanceMeters: 350),
    PlaceOption(placeId: "p-nove", name: "Osteria Nove", type: .restaurant, address: "Wiener Str. 14, Kreuzberg", distanceMeters: 600),
    PlaceOption(placeId: "p-fraeu", name: "Café Fräulein", type: .cafe, address: "Manteuffelstr. 30, Kreuzberg", distanceMeters: 800),
]

func sampleSuggestion() -> ChangeSuggestion {
    ChangeSuggestion(id: "s1", kind: .time, byYou: true, time: Date(timeIntervalSince1970: 1_794_940_200), place: nil,
                     wasTime: Date(timeIntervalSince1970: 1_794_938_400), wasPlaceName: nil,
                     deadline: Date(timeIntervalSince1970: 1_794_936_600), createdAt: Date(timeIntervalSince1970: 1_794_927_600))
}

/// Answers from values the test sets; records every call.
actor FakeChangeStore: ChangeStoring {
    private(set) var placeCalls: [String?] = []
    private(set) var suggests: [SuggestRequest] = []
    private(set) var agrees: [String] = []
    private var placesAnswer: @Sendable (String?) -> Result<PlacesList, CallFailure> = { _ in
        .success(PlacesList(google: true, places: nearbyPlaces))
    }
    private var suggestAnswer: SuggestResult = .sent(sampleSuggestion())
    private var agreeAnswer: AgreeResult = .agreed(sampleSuggestion())
    private var suggestionsAnswer: Result<[ChangeSuggestion], CallFailure> = .success([])
    /// How long an agree takes to answer: the request on its way.
    private var agreeDelay: Double = 0
    private(set) var suggestionReads = 0

    func setPlaces(_ answer: @escaping @Sendable (String?) -> Result<PlacesList, CallFailure>) { placesAnswer = answer }
    func setSuggest(_ answer: SuggestResult) { suggestAnswer = answer }
    func setAgree(_ answer: AgreeResult) { agreeAnswer = answer }
    func setSuggestions(_ answer: Result<[ChangeSuggestion], CallFailure>) { suggestionsAnswer = answer }
    func setAgreeDelay(_ seconds: Double) { agreeDelay = seconds }

    func places(dateId: String, query: String?) async -> Result<PlacesList, CallFailure> {
        placeCalls.append(query)
        return placesAnswer(query)
    }
    func suggest(dateId: String, request: SuggestRequest) async -> SuggestResult {
        suggests.append(request)
        return suggestAnswer
    }
    func agree(dateId: String, suggestionId: String, surface: AgreeSurface) async -> AgreeResult {
        agrees.append("\(dateId)/\(suggestionId)/\(surface.rawValue)")
        if agreeDelay > 0 { try? await Task.sleep(nanoseconds: UInt64(agreeDelay * 1_000_000_000)) }
        return agreeAnswer
    }
    func suggestions(dateId: String) async -> Result<[ChangeSuggestion], CallFailure> {
        suggestionReads += 1
        return suggestionsAnswer
    }
}

@MainActor
final class ChangePickerModelTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_794_938_400) // 2026-11-17T18:00:00Z
    private var now: Date { meet.addingTimeInterval(-3 * 3600) }
    private var slot: Date { meet.addingTimeInterval(1800) }
    private let debounce: TimeInterval = 0.03

    private func date() -> ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: "today", unlockAt: meet.addingTimeInterval(-5400), coordinateOpen: false,
                   place: DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg"),
                   match: DateMatch(userId: "u2", name: "Leonie", age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term",
                   sharedVibes: [], sharedInterests: [], unread: nil, checkedIn: false, chatTutorialSeen: false,
                   change: DateChange(cutoffAt: meet.addingTimeInterval(-1800), allowed: true,
                                      slots: [slot, slot.addingTimeInterval(900)], timeWas: nil, placeWas: nil, pending: []))
    }

    private func model(_ kind: ChangeKind, _ store: FakeChangeStore, _ analytics: RecordingAnalytics,
                       entry: ChangeEntry = .coordinateTile, saved: ChangePickerDraft? = nil,
                       persist: @escaping (String) -> Void = { _ in }) -> ChangePickerModel {
        let counter = IdCounter()
        return ChangePickerModel(kind: kind, entry: entry, store: store, analytics: analytics, clock: { self.now },
                                 newId: { counter.next() },
                                 zone: { TimeZone(identifier: "Europe/Berlin")! }, debounce: debounce,
                                 restoring: saved, persist: persist)
    }

    private final class DraftBox {
        var value = ""
    }

    private final class IdCounter {
        private var n = 0
        func next() -> String {
            n += 1
            return "id-\(n)"
        }
    }

    private func settle(_ seconds: Double = 0.12) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func names(_ analytics: RecordingAnalytics) -> [String] { analytics.entries.map(\.event) }

    func testOpeningReportsChangePickerOpenedOnceWithTheEntryAndTheMinutesToTheStart() async {
        let analytics = RecordingAnalytics()
        let m = model(.time, FakeChangeStore(), analytics, entry: .counterCard)
        m.open(date())
        m.open(date())
        await settle()
        XCTAssertEqual(names(analytics), ["change_picker_opened"])
        let p = analytics.entries[0].properties
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["kind"] as? String, "time")
        XCTAssertEqual(p["entry"] as? String, "counter_card")
        XCTAssertEqual(p["minutes_before_start"] as? Int, 180)
    }

    func testTheTimePickerAsksForNoPlaces() async {
        let store = FakeChangeStore()
        model(.time, store, RecordingAnalytics()).open(date())
        await settle()
        let calls = await store.placeCalls
        XCTAssertTrue(calls.isEmpty)
    }

    func testThePlacePickerLoadsWhatIsNearbyOnOpenWithNoQuery() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        XCTAssertEqual(m.ui.list, PlacesView.loading)
        m.open(date())
        await settle()
        let calls = await store.placeCalls
        XCTAssertEqual(calls, [nil])
        XCTAssertEqual(m.ui.list, PlacesView.loaded(places: nearbyPlaces, google: true, forQuery: ""))
    }

    func testTypingSearchesOnce300msAfterTheLastKeyOnTheTrimmedQuery() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        m.setQuery("s")
        await settle(0.01)
        m.setQuery("su  ")
        await settle(0.3)
        let calls = await store.placeCalls
        XCTAssertEqual(calls, [nil, "su"])
        XCTAssertEqual(m.ui.query, "su  ")
    }

    func testAnEmptiedQueryShowsWhatIsNearbyAgain() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        await store.setPlaces { q in .success(PlacesList(google: true, places: q == nil ? nearbyPlaces : Array(nearbyPlaces.prefix(1)))) }
        m.setQuery("lenz")
        await settle(0.3)
        m.setQuery("")
        await settle(0.3)
        let calls = await store.placeCalls
        XCTAssertEqual(calls, [nil, "lenz", nil])
        XCTAssertEqual(m.ui.list, PlacesView.loaded(places: nearbyPlaces, google: true, forQuery: ""))
    }

    func testAQueryWithNoResultIsNNotAFailure() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        await store.setPlaces { _ in .success(PlacesList(google: true, places: [])) }
        m.setQuery("sushi")
        await settle(0.3)
        XCTAssertEqual(m.ui.list, PlacesView.loaded(places: [], google: true, forQuery: "sushi"))
    }

    func testAFailedLoadIsMKeepsTheSearchUsableAndTryAgainReadsAgain() async {
        let store = FakeChangeStore()
        await store.setPlaces { _ in .failure(.network) }
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        XCTAssertEqual(m.ui.list, PlacesView.failed)
        XCTAssertNil(m.ui.place)
        await store.setPlaces { _ in .success(PlacesList(google: true, places: nearbyPlaces)) }
        m.retryPlaces()
        await settle()
        XCTAssertEqual(m.ui.list, PlacesView.loaded(places: nearbyPlaces, google: true, forQuery: ""))
        let calls = await store.placeCalls
        XCTAssertEqual(calls, [nil, nil])
    }

    func testTryAgainKeepsTheTypedQuery() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        await store.setPlaces { _ in .failure(.server) }
        m.setQuery("bar")
        await settle(0.3)
        XCTAssertEqual(m.ui.list, PlacesView.failed)
        await store.setPlaces { _ in .success(PlacesList(google: false, places: nearbyPlaces)) }
        m.retryPlaces()
        await settle()
        let calls = await store.placeCalls
        XCTAssertEqual(calls.last ?? nil, "bar")
    }

    func testASlotOrAPlaceIsSelectedAndAnotherReplacesIt() async {
        let t = model(.time, FakeChangeStore(), RecordingAnalytics())
        t.open(date())
        t.selectSlot(slot)
        XCTAssertEqual(t.ui.slot, slot)
        t.selectSlot(slot.addingTimeInterval(900))
        XCTAssertEqual(t.ui.slot, slot.addingTimeInterval(900))

        let p = model(.place, FakeChangeStore(), RecordingAnalytics())
        p.open(date())
        await settle()
        p.selectPlace(nearbyPlaces[0])
        p.selectPlace(nearbyPlaces[1])
        XCTAssertEqual(p.ui.place, nearbyPlaces[1])
    }

    func testASelectionSurvivesAListChangeOnlyWhileThePlaceIsStillListed() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        m.selectPlace(nearbyPlaces[1])
        await store.setPlaces { _ in .success(PlacesList(google: true, places: [nearbyPlaces[0], nearbyPlaces[1]])) }
        m.setQuery("o")
        await settle(0.3)
        XCTAssertEqual(m.ui.place, nearbyPlaces[1])
        await store.setPlaces { _ in .success(PlacesList(google: true, places: [nearbyPlaces[0]])) }
        m.setQuery("ol")
        await settle(0.3)
        XCTAssertNil(m.ui.place)
    }

    func testSuggestWithNothingSelectedSendsNothing() async {
        let store = FakeChangeStore()
        let m = model(.time, store, RecordingAnalytics())
        m.open(date())
        m.suggest()
        await settle()
        let sent = await store.suggests
        XCTAssertTrue(sent.isEmpty)
    }

    func testASuggestedTimeGoesWithTheEntryAFreshIdAndTheZoneAndOnSuccessThePickerIsDone() async {
        let store = FakeChangeStore()
        let analytics = RecordingAnalytics()
        let m = model(.time, store, analytics, entry: .chatChip)
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        XCTAssertTrue(m.ui.sending, "both buttons are inert while it is in flight")
        await settle()
        let sent = await store.suggests
        XCTAssertEqual(sent, [SuggestRequest(kind: .time, time: slot, placeId: nil, entry: .chatChip, fromSearch: nil,
                                             clientId: "id-1", tz: "Europe/Berlin")])
        XCTAssertTrue(m.ui.done)
        XCTAssertFalse(m.ui.sending)
        XCTAssertEqual(names(analytics), ["change_picker_opened"], "a successful send fires nothing client-side")
    }

    func testAPlacePickedFromASearchIsFlaggedFromSearchOneFromTheNearbyListIsNot() async {
        let store = FakeChangeStore()
        let nearby = model(.place, store, RecordingAnalytics())
        nearby.open(date())
        await settle()
        nearby.selectPlace(nearbyPlaces[0])
        nearby.suggest()
        await settle()
        var sent = await store.suggests
        XCTAssertEqual(sent.last?.fromSearch, false)
        XCTAssertEqual(sent.last?.placeId, "p-lenz")

        let searched = model(.place, store, RecordingAnalytics())
        searched.open(date())
        await settle()
        searched.setQuery("lenz")
        await settle(0.3)
        searched.selectPlace(nearbyPlaces[0])
        searched.suggest()
        await settle()
        sent = await store.suggests
        XCTAssertEqual(sent.last?.fromSearch, true)
    }

    func testAFailedSendIsKThePickerStaysOpenWithTheSelectionAndNothingIsDone() async {
        let store = FakeChangeStore()
        await store.setSuggest(.failed(.network))
        let m = model(.time, store, RecordingAnalytics())
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        await settle()
        XCTAssertTrue(m.ui.sendFailed)
        XCTAssertFalse(m.ui.sending)
        XCTAssertFalse(m.ui.done)
        XCTAssertEqual(m.ui.slot, slot)
    }

    func testTheRetryReusesTheSameClientIdAndADifferentValueGetsANewOne() async {
        let store = FakeChangeStore()
        await store.setSuggest(.failed(.network))
        let m = model(.time, store, RecordingAnalytics())
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        await settle()
        m.suggest()
        await settle()
        var sent = await store.suggests
        XCTAssertEqual(sent.map(\.clientId), ["id-1", "id-1"])

        m.selectSlot(slot.addingTimeInterval(900))
        m.suggest()
        await settle()
        sent = await store.suggests
        XCTAssertEqual(sent.last?.clientId, "id-2")

        await store.setSuggest(.sent(sampleSuggestion()))
        m.suggest()
        await settle()
        sent = await store.suggests
        XCTAssertEqual(sent.last?.clientId, "id-2")
        XCTAssertTrue(m.ui.done)
    }

    func testASecondTapWhileItIsInFlightSendsNothingMore() async {
        let store = FakeChangeStore()
        let m = model(.time, store, RecordingAnalytics())
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        m.suggest()
        await settle()
        let sent = await store.suggests
        XCTAssertEqual(sent.count, 1)
    }

    // MARK: 409, 503

    func testA409IsNotKThePickerIsStaleNothingIsShownAsFailedAndNoEventFires() async {
        let store = FakeChangeStore()
        await store.setSuggest(.stale)
        let analytics = RecordingAnalytics()
        let m = model(.time, store, analytics)
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        await settle()
        XCTAssertTrue(m.ui.stale, "the host closes the picker and reads the date again")
        XCTAssertFalse(m.ui.sendFailed, "no K")
        XCTAssertFalse(m.ui.sending)
        XCTAssertFalse(m.ui.done)
        XCTAssertEqual(names(analytics), ["change_picker_opened"])
        m.cancel()
        XCTAssertTrue(analytics.entries.filter { $0.event == "change_picker_dismissed" }.isEmpty,
                      "a stale picker is not dismissed")
    }

    func testA503StaysKWithTheSelectionKeptAndTheSameClientIdOnTheRetry() async {
        let store = FakeChangeStore()
        await store.setSuggest(.failed(.server))
        let m = model(.time, store, RecordingAnalytics())
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        await settle()
        XCTAssertTrue(m.ui.sendFailed)
        XCTAssertFalse(m.ui.stale)
        XCTAssertEqual(m.ui.slot, slot)
        m.suggest()
        await settle()
        let sent = await store.suggests
        XCTAssertEqual(sent.map(\.clientId), ["id-1", "id-1"])
    }

    // MARK: process death

    func testTheSelectionTheFieldAndTheRetrysIdComeBackFromTheStoredDraft() async {
        let store = FakeChangeStore()
        await store.setSuggest(.failed(.network))
        let box = DraftBox()
        let first = model(.time, store, RecordingAnalytics(), persist: { box.value = $0 })
        first.open(date())
        first.selectSlot(slot)
        first.suggest()
        await settle()
        XCTAssertFalse(box.value.isEmpty)

        // The process dies: a new model, from what the host kept.
        let restored = model(.time, store, RecordingAnalytics(), saved: ChangePickerDraft.decode(box.value))
        XCTAssertEqual(restored.ui.slot, slot)
        restored.open(date())
        restored.suggest()
        await settle()
        let sent = await store.suggests
        XCTAssertEqual(sent.map(\.clientId), ["id-1", "id-1"], "the retry is the same send")
    }

    func testARestoredPlaceAndQueryReloadTheListForThatQueryAndThePlaceStaysOnlyWhileListed() async {
        let store = FakeChangeStore()
        let box = DraftBox()
        let first = model(.place, store, RecordingAnalytics(), persist: { box.value = $0 })
        first.open(date())
        await settle()
        first.setQuery("lenz")
        await settle(0.3)
        first.selectPlace(nearbyPlaces[0])

        let restored = model(.place, store, RecordingAnalytics(), saved: ChangePickerDraft.decode(box.value))
        XCTAssertEqual(restored.ui.query, "lenz")
        XCTAssertEqual(restored.ui.place, nearbyPlaces[0])
        restored.open(date())
        await settle()
        let calls = await store.placeCalls
        XCTAssertEqual(calls.last ?? nil, "lenz", "the list answers the restored query")
        XCTAssertEqual(restored.ui.place, nearbyPlaces[0], "still listed: kept")

        await store.setPlaces { _ in .success(PlacesList(google: true, places: [nearbyPlaces[1]])) }
        let gone = model(.place, store, RecordingAnalytics(), saved: ChangePickerDraft.decode(box.value))
        gone.open(date())
        await settle()
        XCTAssertNil(gone.ui.place, "no longer listed: dropped (R18)")
    }

    func testTheDraftIsClearedWhenThePickerEndsAndADraftOfAnotherKindIsNotRestored() async {
        let box = DraftBox()
        let m = model(.time, FakeChangeStore(), RecordingAnalytics(), persist: { box.value = $0 })
        m.open(date())
        m.selectSlot(slot)
        let draft = ChangePickerDraft.decode(box.value)
        XCTAssertNotNil(draft)
        m.cancel()
        XCTAssertEqual(box.value, "")

        let other = model(.place, FakeChangeStore(), RecordingAnalytics(), saved: draft)
        XCTAssertNil(other.ui.slot)
        XCTAssertEqual(other.ui.query, "")
        XCTAssertNil(ChangePickerDraft.decode("not json"))
    }

    func testARestoredSlotTheServerNoLongerOffersIsDroppedOneStillOfferedIsKept() async {
        let box = DraftBox()
        let first = model(.time, FakeChangeStore(), RecordingAnalytics(), persist: { box.value = $0 })
        first.open(date())
        first.selectSlot(slot.addingTimeInterval(900))

        let kept = model(.time, FakeChangeStore(), RecordingAnalytics(), saved: ChangePickerDraft.decode(box.value))
        XCTAssertEqual(kept.ui.slot, slot.addingTimeInterval(900))
        kept.open(date())
        XCTAssertEqual(kept.ui.slot, slot.addingTimeInterval(900), "still offered: kept")

        // The slot list moved on while the process was gone.
        var moved = date()
        moved.change = DateChange(cutoffAt: meet.addingTimeInterval(-1800), allowed: true, slots: [slot], timeWas: nil,
                                  placeWas: nil, pending: [])
        let dropped = model(.time, FakeChangeStore(), RecordingAnalytics(), saved: ChangePickerDraft.decode(box.value))
        dropped.open(moved)
        XCTAssertNil(dropped.ui.slot, "not offered: dropped, the primary reads Pick a time")
        XCTAssertFalse(dropped.ui.hasSelection)
    }

    func testADebouncedSearchNotYetSentIsDroppedWhenThePickerIsCancelledAndTheClearedDraftStaysCleared() async {
        let store = FakeChangeStore()
        let box = DraftBox()
        let m = model(.place, store, RecordingAnalytics(), persist: { box.value = $0 })
        m.open(date())
        await settle()
        m.setQuery("a")
        m.cancel()
        await settle(0.3)
        let calls = await store.placeCalls
        XCTAssertEqual(calls, [nil], "the pending search never went out")
        XCTAssertEqual(box.value, "", "a late answer does not rewrite the cleared draft")
        m.setQuery("late")
        await settle(0.3)
        XCTAssertEqual(box.value, "")
    }

    func testTheSearchFieldStopsAtTheContractsHundredCharacters() async {
        let store = FakeChangeStore()
        let m = model(.place, store, RecordingAnalytics())
        m.open(date())
        await settle()
        m.setQuery(String(repeating: "x", count: 130))
        await settle(0.3)
        XCTAssertEqual(m.ui.query.count, 100)
        let calls = await store.placeCalls
        XCTAssertEqual((calls.last ?? nil)?.count, 100)
    }

    func testCancelOrBackReportsChangePickerDismissedWithWhetherAnythingWasSelectedAndSendsNothing() async {
        let store = FakeChangeStore()
        let analytics = RecordingAnalytics()
        let empty = model(.time, store, analytics, entry: .chatChip)
        empty.open(date())
        empty.cancel()
        let p = analytics.entries.first { $0.event == "change_picker_dismissed" }?.properties ?? [:]
        XCTAssertEqual(p["date_id"] as? String, "d1")
        XCTAssertEqual(p["kind"] as? String, "time")
        XCTAssertEqual(p["entry"] as? String, "chat_chip")
        XCTAssertEqual(p["had_selection"] as? Bool, false)
        XCTAssertTrue(empty.ui.closed)

        let chosen = model(.time, store, analytics)
        chosen.open(date())
        chosen.selectSlot(slot)
        chosen.cancel()
        let last = analytics.entries.last { $0.event == "change_picker_dismissed" }?.properties ?? [:]
        XCTAssertEqual(last["had_selection"] as? Bool, true)
        let sent = await store.suggests
        XCTAssertTrue(sent.isEmpty)
    }

    func testCancelWhileASendIsInFlightIsIgnored() async {
        let analytics = RecordingAnalytics()
        let m = model(.time, FakeChangeStore(), analytics)
        m.open(date())
        m.selectSlot(slot)
        m.suggest()
        m.cancel()
        XCTAssertFalse(m.ui.closed)
        XCTAssertTrue(analytics.entries.filter { $0.event == "change_picker_dismissed" }.isEmpty)
    }

    /// One draft must encode to one string however many times it is encoded.
    func testADraftEncodesToOneStringEveryTime() {
        let place = PlaceOption(placeId: "p1", name: "Cafe", type: .cafe, address: "1 Main St", distanceMeters: 120)
        let draft = ChangePickerDraft(dateId: "d1", kind: "place", slot: 1_800_000_000, place: place, query: "cof",
                                      fromSearch: true, attemptValue: "v", attemptId: "a1")
        XCTAssertEqual(Set((0..<50).map { _ in draft.encoded }).count, 1)
    }
}
