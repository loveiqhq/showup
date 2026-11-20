// ChangeSheetsTests.swift
// ShowUp · the pickers, one test per frame (SHOWUP-196, Date 05 A, B, K, M, N). Android's `ChangeSheetsTest.kt`.
//
// Each frame asserts the ticket's copy EXACTLY, as the screen builds it from `ChangeCopy` -- the strings are the
// contract, and `audit/verify-change.py` quotes the same ones from the ticket -- and lays the frame out in a
// hosting controller at 375 x 667, the evidence size, to prove it builds and takes a size in every state.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ChangeSheetsTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_794_938_400) // 19:00 in Berlin
    private var slots: [Date] { [900, 1800, 2700, 3600, 10_800].map { meet.addingTimeInterval($0) } }
    private var ctx: ChangeSheetContext {
        ChangeSheetContext(name: "Leonie", bindingTime: meet, bindingPlace: "Cafe Krone", slots: slots, zone: berlin)
    }

    private func ui(_ kind: ChangeKind, _ edit: (inout ChangePickerUi) -> Void = { _ in }) -> ChangePickerUi {
        var ui = ChangePickerUi(kind: kind, entry: .coordinateTile)
        edit(&ui)
        return ui
    }

    /// The frame laid out at 375 x 667: it must take a size, with no crash, in every state.
    private func lay(_ view: some View, file: StaticString = #filePath, line: UInt = #line) {
        let host = UIHostingController(rootView: view)
        host.view.frame = CGRect(x: 0, y: 0, width: 375, height: 667)
        host.view.layoutIfNeeded()
        let fitted = host.sizeThatFits(in: CGSize(width: 375, height: 667))
        XCTAssertGreaterThan(fitted.height, 0, file: file, line: line)
    }

    private let note = "19:00 at Cafe Krone stays binding unless Leonie agrees by 18:30. Not showing up there counts as a no-show."

    func testAReadsTheTicketsCopyTheSlotsAsLocalTimesAndAPickATimeThatIsDisabled() {
        XCTAssertEqual(ChangeCopy.timeTitle, "Suggest a new time")
        XCTAssertEqual(ChangeCopy.timeCurrent + ctx.time, "Your current meet time: 19:00")
        XCTAssertEqual(ChangeCopy.timeChoose(name: "Leonie", latest: changeClock(latestSlot(slots)!, zone: berlin)),
                       "Choose a time within Leonie's availability. Latest possible meet time: 22:00.")
        XCTAssertEqual(ChangeCopy.agreeBy(name: "Leonie", deadline: ctx.deadline, binding: ctx.time),
                       "Leonie has to agree by 18:30, so you both still arrive on time. If not, 19:00 stays binding.")
        XCTAssertEqual(slots.map { changeClock($0, zone: berlin) }, ["19:15", "19:30", "19:45", "20:00", "22:00"])
        XCTAssertEqual(ChangeCopy.pickTime, "Pick a time")
        XCTAssertEqual(ChangeCopy.cancel, "Cancel")
        XCTAssertFalse(ui(.time).hasSelection)
        lay(TimeChangeSheet(ctx: ctx, ui: ui(.time), onSelect: { _ in }, onSuggest: {}, onCancel: {}))
    }

    func testAWithASlotSelectedOffersSuggestAndThatSlot() {
        let chosen = ui(.time) { $0.slot = slots[1] }
        XCTAssertTrue(chosen.hasSelection)
        XCTAssertEqual(ChangeCopy.suggest(changeClock(slots[1], zone: berlin)), "Suggest 19:30")
        lay(TimeChangeSheet(ctx: ctx, ui: chosen, onSelect: { _ in }, onSuggest: {}, onCancel: {}))
    }

    func testTheBindingNoteReadsTheTicketsSentenceBoldThenTheRest() {
        XCTAssertEqual(
            ChangeCopy.bindingBold(time: "19:00", place: "Cafe Krone", name: "Leonie", deadline: "18:30") + ChangeCopy.bindingRest,
            note)
        XCTAssertEqual(ChangeCopy.bindingBoldYou(time: "19:00", place: "Cafe Krone", deadline: "18:30"),
                       "19:00 at Cafe Krone stays binding unless you agree by 18:30.")
        lay(BindingNote(name: "Leonie", time: "19:00", place: "Cafe Krone", deadline: "18:30", compact: true))
    }

    func testKKeepsTheSelectionReplacesTheBindingNoteWithTheErrorAndSuggestRetries() {
        let failed = ui(.time) { $0.slot = slots[1]; $0.sendFailed = true }
        XCTAssertEqual(ChangeCopy.sendFailed, "Couldn't send your suggestion. Check your connection and try again.")
        XCTAssertTrue(failed.sendFailed)
        XCTAssertEqual(failed.slot, slots[1])
        lay(TimeChangeSheet(ctx: ctx, ui: failed, onSelect: { _ in }, onSuggest: {}, onCancel: {}))
    }

    func testBReadsTheTicketsCopyTheNearbyListWithItsRowsAndTheGoogleAttribution() {
        let listed = PlacesView.loaded(places: nearbyPlaces + [PlaceOption(placeId: "p-kanal", name: "Kanal Bar", type: .bar,
                                                                           address: "Paul-Lincke-Ufer 21, Kreuzberg", distanceMeters: 1100)],
                                       google: true, forQuery: "")
        XCTAssertEqual(ChangeCopy.placeTitle, "Suggest a new place")
        XCTAssertEqual(ChangeCopy.placeCurrent + "Cafe Krone", "Your current meet place: Cafe Krone")
        XCTAssertEqual(ChangeCopy.placeChoose, "Choose a public place: café, bar or restaurant.")
        XCTAssertEqual(ChangeCopy.agreeBy(name: "Leonie", deadline: "18:30", binding: "Cafe Krone"),
                       "Leonie has to agree by 18:30, so you both still arrive on time. If not, Cafe Krone stays binding.")
        XCTAssertEqual(ChangeCopy.searchPlaceholder, "Search cafés, bars, restaurants")
        XCTAssertEqual(ChangeCopy.nearby, "Nearby public places")
        XCTAssertEqual(ChangeCopy.pickPlace, "Pick a place")
        XCTAssertEqual(ChangeCopy.googleMaps, "Google Maps")
        XCTAssertEqual(placeTypeTag(.bar), "BAR")
        XCTAssertEqual("\(nearbyPlaces[0].address) · \(formatDistance(nearbyPlaces[0].distanceMeters))", "Lausitzer Platz 9, Kreuzberg · 350 m")
        lay(PlaceChangeSheet(ctx: ctx, ui: ui(.place) { $0.list = listed }, onQuery: { _ in }, onSelect: { _ in },
                             onRetry: {}, onSuggest: {}, onCancel: {}))
    }

    func testBWithARowSelectedReadsSuggestAndItsVenue() {
        let picked = ui(.place) {
            $0.list = .loaded(places: nearbyPlaces, google: false, forQuery: "")
            $0.place = nearbyPlaces[0]
        }
        XCTAssertEqual(ChangeCopy.suggest(picked.place!.name), "Suggest Bar Lenz")
        lay(PlaceChangeSheet(ctx: ctx, ui: picked, onQuery: { _ in }, onSelect: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {}))
    }

    func testBWhileTheListLoadsIsEmptyWithASpokenDescriptionAndNoVisibleText() {
        XCTAssertEqual(ChangeCopy.loadingPlaces, "Loading places")
        lay(PlaceChangeSheet(ctx: ctx, ui: ui(.place), onQuery: { _ in }, onSelect: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {}))
    }

    func testBATypedQuerysListReadsResults() {
        XCTAssertEqual(ChangeCopy.results, "Results")
        lay(PlaceChangeSheet(ctx: ctx,
                             ui: ui(.place) { $0.query = "bar"; $0.list = .loaded(places: nearbyPlaces, google: true, forQuery: "bar") },
                             onQuery: { _ in }, onSelect: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {}))
    }

    func testMReplacesTheListWithTheMessageAndTryAgainTheSearchStaysAndSuggestIsDisabled() {
        XCTAssertEqual(ChangeCopy.placesFailed, "Couldn't load places. Check your connection and try again.")
        XCTAssertEqual(ChangeCopy.tryAgain, "Try again")
        let failed = ui(.place) { $0.list = .failed }
        XCTAssertFalse(failed.hasSelection)
        lay(PlaceChangeSheet(ctx: ctx, ui: failed, onQuery: { _ in }, onSelect: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {}))
    }

    func testNSaysNothingWasFoundForTheQueryWithNoTryAgain() {
        XCTAssertEqual(ChangeCopy.noPlaces("sushi"), "No cafés, bars or restaurants found for “sushi”.")
        XCTAssertEqual(ChangeCopy.tryAnother, "Try another name.")
        lay(PlaceChangeSheet(ctx: ctx,
                             ui: ui(.place) { $0.query = "sushi"; $0.list = .loaded(places: [], google: true, forQuery: "sushi") },
                             onQuery: { _ in }, onSelect: { _ in }, onRetry: {}, onSuggest: {}, onCancel: {}))
    }
}
