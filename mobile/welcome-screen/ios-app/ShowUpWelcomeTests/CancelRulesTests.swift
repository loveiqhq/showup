// CancelRulesTests.swift
// ShowUp · Date 07's plain rules: Date 05's gate read for the guard sheet, `Message {name} instead`, the sent label, the
// link's visibility and where an ended cancel lands (SHOWUP-198, R12). Android's `CancelRulesTest.kt`.

import XCTest
@testable import ShowUpWelcome

final class CancelRulesTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-21T18:00:00Z, 19:00 in Berlin
    private var cutoff: Date { meet.addingTimeInterval(-30 * 60) }

    private func change(allowed: Bool = true, slots: [Date]? = nil) -> DateChange {
        DateChange(cutoffAt: cutoff, allowed: allowed, slots: slots ?? [meet.addingTimeInterval(900)], timeWas: nil,
                   placeWas: nil, pending: [])
    }

    private func date(checkedIn: Bool = false, endsEnabled: Bool = true, chatOpen: Bool = false,
                      place: DatePlace? = DatePlace(name: "Cafe Krone", address: nil)) -> ActiveDate {
        var d = DateSamples.date
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = chatOpen
        d.place = place
        d.checkedIn = checkedIn
        d.endsEnabled = endsEnabled
        return d
    }

    // MARK: changeClosed -- Date 05's gate, the same function

    func testChangeClosedIsFalseBeforeTheCutoffWithNoCheckIn() {
        XCTAssertFalse(changeClosed(change(), now: cutoff.addingTimeInterval(-1)))
    }

    func testChangeClosedIsTrueFromTheCutoffToTheSecond() {
        XCTAssertTrue(changeClosed(change(), now: cutoff))
        XCTAssertTrue(changeClosed(change(), now: meet.addingTimeInterval(60)))
    }

    func testChangeClosedIsTrueOnceEitherPersonHasCheckedInBeforeTheCutoff() {
        XCTAssertTrue(changeClosed(change(allowed: false), now: cutoff.addingTimeInterval(-3600)))
    }

    func testChangeClosedIsFalseWhenTheServerSentNoChangeBlockAndThereAreNoTiles() {
        // Nothing was ever offered, so I (and its Message button) must not appear: the chat may be locked.
        let early = cutoff.addingTimeInterval(-3600)
        XCTAssertFalse(changeClosed(nil, now: early))
        XCTAssertEqual(cancelTiles(nil, now: early), [])
        XCTAssertFalse(showMessage(chatOpen: false, changeClosed: changeClosed(nil, now: cutoff)))
        XCTAssertTrue(showMessage(chatOpen: true, changeClosed: changeClosed(nil, now: cutoff)))
    }

    func testChangeClosedAgreesWithDate05sTilesWhenABlockIsThere() {
        let times = [cutoff.addingTimeInterval(-7200), cutoff.addingTimeInterval(-1), cutoff, meet]
        for allowed in [true, false] {
            for now in times {
                let c = change(allowed: allowed)
                let anyTile = changeKindAvailable(c, kind: .time, now: now) || changeKindAvailable(c, kind: .place, now: now)
                XCTAssertEqual(!anyTile, changeClosed(c, now: now), "allowed=\(allowed) now=\(now)")
            }
        }
    }

    func testChangeWindowOpenIsTheFirstHalfOfChangeKindAvailable() {
        XCTAssertTrue(changeWindowOpen(change(), now: cutoff.addingTimeInterval(-1)))
        XCTAssertFalse(changeWindowOpen(change(), now: cutoff))
        XCTAssertFalse(changeWindowOpen(change(allowed: false), now: cutoff.addingTimeInterval(-3600)))
        XCTAssertFalse(changeWindowOpen(nil, now: cutoff.addingTimeInterval(-3600)))
    }

    // MARK: the tiles

    func testBothTilesShowWhileTheWindowIsOpenAndALaterSlotExists() {
        XCTAssertEqual(cancelTiles(change(), now: cutoff.addingTimeInterval(-60)), [.time, .place])
    }

    func testChangeMeetTimeHidesWithNoLaterSlotAndChangeLocationStays() {
        XCTAssertEqual(cancelTiles(change(slots: []), now: cutoff.addingTimeInterval(-60)), [.place])
    }

    func testNoTileShowsOnceTheChangeIsClosed() {
        XCTAssertEqual(cancelTiles(change(), now: cutoff), [])
    }

    func testTheBodyLineNeedsATileOrASentRequestAndNeverShowsInI() {
        XCTAssertTrue(solutionBodyShown(tiles: [.place], sent: false, changeClosed: false))
        XCTAssertTrue(solutionBodyShown(tiles: [], sent: true, changeClosed: false))
        XCTAssertFalse(solutionBodyShown(tiles: [], sent: false, changeClosed: false))
        XCTAssertFalse(solutionBodyShown(tiles: [.place], sent: true, changeClosed: true))
    }

    // MARK: Message instead

    func testShowMessageIsChatOpenOrChangeClosed() {
        XCTAssertFalse(showMessage(chatOpen: false, changeClosed: false))
        XCTAssertTrue(showMessage(chatOpen: true, changeClosed: false))
        XCTAssertTrue(showMessage(chatOpen: false, changeClosed: true))
        XCTAssertTrue(showMessage(chatOpen: true, changeClosed: true))
    }

    func testTheMatchHavingCheckedInBeforeMeetMinus1h30ShowsCTheChatBeingOpen() {
        // The server opens the chat on either check-in, and Date 05 closes the change: C, not B.
        var d = date(chatOpen: true)
        d.change = change(allowed: false)
        let closed = changeClosed(d.change, now: meet.addingTimeInterval(-95 * 60))
        XCTAssertTrue(showMessage(chatOpen: d.coordinateOpen, changeClosed: closed))
    }

    // MARK: sentLabel

    func testSentLabelForATimeIsTheClockTimeOnThePhoneForAPlaceItsName() {
        XCTAssertEqual(sentLabel(kind: .time, slot: Date(timeIntervalSince1970: 1_795_028_400), placeName: nil, zone: berlin), "20:00")
        XCTAssertEqual(sentLabel(kind: .place, slot: nil, placeName: "Bar Lenz", zone: berlin), "Bar Lenz")
    }

    func testSentLabelIsEmptyWhenNothingWasChosen() {
        XCTAssertEqual(sentLabel(kind: .time, slot: nil, placeName: nil, zone: berlin), "")
        XCTAssertEqual(sentLabel(kind: .place, slot: nil, placeName: nil, zone: berlin), "")
    }

    // MARK: the link

    func testTheLinkShowsWhileActiveNotCheckedInAndTheEndsAreOn() {
        XCTAssertTrue(cancelLinkShown(date()))
    }

    func testTheLinkIsGoneAfterThisUsersOwnCheckIn() {
        XCTAssertFalse(cancelLinkShown(date(checkedIn: true)))
    }

    func testTheMatchsCheckInDoesNotHideTheLink() {
        var d = date()
        d.arrival = DateArrival(checkedInAt: meet.addingTimeInterval(-600))
        XCTAssertTrue(cancelLinkShown(d))
    }

    func testTheLinkIsHiddenWhileTheEndRoutesAreOff() {
        XCTAssertFalse(cancelLinkShown(date(endsEnabled: false)))
    }

    // MARK: the pill

    func testThePillIsTheChatHeadersLineDayTimeAndPlace() {
        XCTAssertEqual(guardPill(date(), zone: berlin), "Today · 19:00 · Cafe Krone")
        XCTAssertEqual(guardPill(date(place: nil), zone: berlin), "Today · 19:00")
    }

    // MARK: where a cancel lands

    func testWhereAnEndRoutesTheChatsGTheOthersCancelHEverythingElseLands() {
        func outcome(_ reason: OutcomeReason, byYou: Bool) -> EndedOutcome {
            EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: byYou, reason: reason)
        }
        XCTAssertEqual(endedRoute(outcome(.chat, byYou: false)), .dateEnded)
        XCTAssertEqual(endedRoute(outcome(.chat, byYou: true)), .dateEnded)
        XCTAssertEqual(endedRoute(outcome(.cancelled, byYou: false)), .partnerCancelled)
        XCTAssertEqual(endedRoute(outcome(.cancelled, byYou: true)), .land)
        // A Date 06 end reaching the person through the chat never shows Date 02's screen.
        for reason in [OutcomeReason.met, .partnerNoShow, .uncomfortable] {
            XCTAssertEqual(endedRoute(outcome(reason, byYou: false)), .land)
            XCTAssertEqual(endedRoute(outcome(reason, byYou: true)), .land)
        }
    }

    func testACancelByTheOtherPersonIsHOneByYouOrAnyOtherReasonIsNot() {
        func outcome(_ reason: OutcomeReason, byYou: Bool) -> EndedOutcome {
            EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: byYou, reason: reason)
        }
        XCTAssertTrue(cancelledOnYou(outcome(.cancelled, byYou: false)))
        XCTAssertFalse(cancelledOnYou(outcome(.cancelled, byYou: true)))
        XCTAssertFalse(cancelledOnYou(outcome(.chat, byYou: false)))
        XCTAssertFalse(cancelledOnYou(outcome(.met, byYou: false)))
    }

    // MARK: the entry

    func testTheSheetsPickerEntryIsCancelSheetOnTheWire() {
        XCTAssertEqual(ChangeEntry.cancelSheet.rawValue, "cancel_sheet")
    }
}
