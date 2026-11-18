// ChangeRulesTests.swift
// ShowUp · Date 05's pure rules (SHOWUP-196): the distance line, the deadline, and when the tiles and chips show.
// Android's `ChangeRulesTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

final class ChangeRulesTests: XCTestCase {
    private let meet = Date(timeIntervalSince1970: 1_794_938_400) // 2026-11-17T18:00:00Z, 19:00 in Berlin
    private var cutoff: Date { meet.addingTimeInterval(-30 * 60) }
    private var slot: Date { meet.addingTimeInterval(30 * 60) }
    private let berlin = TimeZone(identifier: "Europe/Berlin")!

    private func change(allowed: Bool = true, slots: [Date]? = nil, cutoffAt: Date? = nil) -> DateChange {
        DateChange(cutoffAt: cutoffAt ?? cutoff, allowed: allowed, slots: slots ?? [slot], timeWas: nil, placeWas: nil,
                   pending: [])
    }

    func testDistanceRoundsToTheNearest50UnderAKilometreAndToOneDecimalFromIt() {
        XCTAssertEqual(formatDistance(349), "350 m")
        XCTAssertEqual(formatDistance(350), "350 m")
        XCTAssertEqual(formatDistance(374), "350 m")
        XCTAssertEqual(formatDistance(375), "400 m")
        XCTAssertEqual(formatDistance(600), "600 m")
        // 999 rounds up to a full kilometre: it reads as one, not as "1000 m".
        XCTAssertEqual(formatDistance(999), "1.0 km")
        XCTAssertEqual(formatDistance(1000), "1.0 km")
        XCTAssertEqual(formatDistance(1049), "1.0 km")
        XCTAssertEqual(formatDistance(1050), "1.1 km")
        XCTAssertEqual(formatDistance(1400), "1.4 km")
        XCTAssertEqual(formatDistance(12_300), "12.3 km")
    }

    func testThePlaceTypesReadCafeBarAndRestaurantTheRowTagInCapitals() {
        XCTAssertEqual(placeTypeLabel(.cafe), "Café")
        XCTAssertEqual(placeTypeLabel(.bar), "Bar")
        XCTAssertEqual(placeTypeLabel(.restaurant), "Restaurant")
        XCTAssertEqual(placeTypeTag(.cafe), "CAFÉ")
        XCTAssertEqual(placeTypeTag(.bar), "BAR")
        XCTAssertEqual(placeTypeTag(.restaurant), "RESTAURANT")
    }

    func testTheDeadlineIsTheBindingTimeLessHalfAnHourFromTheBindingTime() {
        XCTAssertEqual(changeDeadline(binding: meet), cutoff)
        XCTAssertEqual(changeClock(meet, zone: berlin), "19:00")
        XCTAssertEqual(changeClock(cutoff, zone: berlin), "18:30")
    }

    func testTheLatestSlotIsTheLastTheServerSentAndNilWhenItSentNone() {
        let slots = [slot, slot.addingTimeInterval(900), slot.addingTimeInterval(1800)]
        XCTAssertEqual(latestSlot(slots), slot.addingTimeInterval(1800))
        XCTAssertNil(latestSlot([]))
    }

    func testBothEntriesShowWhileAllowedBeforeTheCutoffWithALaterSlot() {
        let now = cutoff.addingTimeInterval(-60)
        XCTAssertTrue(changeKindAvailable(change(), kind: .time, now: now))
        XCTAssertTrue(changeKindAvailable(change(), kind: .place, now: now))
    }

    func testFromTheCutoffOnBothAreHiddenInPlaceAsTheClockTicks() {
        XCTAssertFalse(changeKindAvailable(change(), kind: .time, now: cutoff))
        XCTAssertFalse(changeKindAvailable(change(), kind: .place, now: cutoff))
        XCTAssertFalse(changeKindAvailable(change(), kind: .place, now: cutoff.addingTimeInterval(1)))
    }

    func testOnceEitherPersonHasCheckedInBothAreHidden() {
        let now = cutoff.addingTimeInterval(-3600)
        XCTAssertFalse(changeKindAvailable(change(allowed: false), kind: .time, now: now))
        XCTAssertFalse(changeKindAvailable(change(allowed: false), kind: .place, now: now))
    }

    func testNoLaterSlotHidesTheTimeEntryOnly() {
        let now = cutoff.addingTimeInterval(-3600)
        XCTAssertFalse(changeKindAvailable(change(slots: []), kind: .time, now: now))
        XCTAssertTrue(changeKindAvailable(change(slots: []), kind: .place, now: now))
    }

    func testAnOlderServerWithNoChangeBlockShowsNeither() {
        XCTAssertFalse(changeKindAvailable(nil, kind: .time, now: cutoff.addingTimeInterval(-3600)))
        XCTAssertFalse(changeKindAvailable(nil, kind: .place, now: cutoff.addingTimeInterval(-3600)))
    }

    func testTheLockedCardsTilesAndTheChatsChipsAreOneCheck() {
        let now = cutoff.addingTimeInterval(-3600)
        XCTAssertEqual(changeTilesFor(change(), now: now), [.changeTime, .changePlace])
        XCTAssertEqual(changeTilesFor(change(slots: []), now: now), [.changePlace])
        XCTAssertEqual(changeTilesFor(change(), now: cutoff), [])
        XCTAssertEqual(visibleReasons(change(), now: now),
                       [.onMyWay, .arrived, .cantFind, .whenArrive, .changeTime, .changePlace, .runningLate, .cantMakeIt])
        XCTAssertEqual(visibleReasons(change(), now: cutoff),
                       [.onMyWay, .arrived, .cantFind, .whenArrive, .runningLate, .cantMakeIt])
    }

    func testAChipOrTileOpensThePickerOfItsKindWithTheEntryThatNamesWhereItWasTapped() {
        XCTAssertEqual(changePickerRoute(.changeTime, surface: .entry), AppRoute.changePicker(kind: .time, entry: .chatChip))
        XCTAssertEqual(changePickerRoute(.changePlace, surface: .dock), AppRoute.changePicker(kind: .place, entry: .chatChip))
        XCTAssertEqual(changePickerRoute(.changeTime, surface: .coordinateCard),
                       AppRoute.changePicker(kind: .time, entry: .coordinateTile))
        XCTAssertNil(changePickerRoute(.runningLate, surface: .dock))
    }
}
