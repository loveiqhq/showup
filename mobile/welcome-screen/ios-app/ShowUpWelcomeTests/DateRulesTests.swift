// DateRulesTests.swift
// ShowUp · the Date screen's rules and where a launch opens, with no UI (SHOWUP-193)
//
// Android's `DateRulesTest.kt` and `DateRoutingTest.kt`: the same cases, the same values.

import XCTest
@testable import ShowUpWelcome

final class DateRulesTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_793_728_800) // 2026-11-03T18:00:00Z, 19:00 Berlin
    private var unlock: Date { meet.addingTimeInterval(-90 * 60) } // 17:30 Berlin
    private let place = DatePlace(name: "Cafe Krone", address: "Oranienstrasse 42, Kreuzberg")

    private func unread() -> DateUnread {
        DateUnread(count: 1, reason: "running_late", body: "Ten minutes late!", sentAt: meet.addingTimeInterval(-50 * 60))
    }

    /// `place` nil uses the sample place; `.some(nil)` is a date with none.
    private func date(place: DatePlace?? = nil, meetDay: String = "today", name: String = "Leonie") -> ActiveDate {
        ActiveDate(id: "d1", meetAt: meet, meetDay: meetDay, unlockAt: unlock, coordinateOpen: false,
                   place: place ?? self.place,
                   match: DateMatch(userId: "u2", name: name, age: 28, district: "Neukölln", distanceKm: 1,
                                    photoUrl: nil, attendedCount: 4),
                   motivationYou: "long_term", motivationThem: "long_term",
                   sharedVibes: [], sharedInterests: [], unread: nil, checkedIn: false)
    }

    // MARK: - the state

    func testTheStateFlipsExactlyAtMeetMinus1h30() {
        XCTAssertEqual(dateState(now: unlock.addingTimeInterval(-1), unlockAt: unlock, unread: nil), .locked)
        XCTAssertEqual(dateState(now: unlock, unlockAt: unlock, unread: nil), .available)
        XCTAssertEqual(dateState(now: unlock, unlockAt: unlock, unread: unread()), .unread)
    }

    func testAnUnreadMessageNeverHoistsTheCardWhileItIsStillLocked() {
        XCTAssertEqual(dateState(now: unlock.addingTimeInterval(-60), unlockAt: unlock, unread: unread()), .locked)
    }

    func testMinutesBeforeStartCountFromTheAgreedTimeNegativeAfterTowardZero() {
        XCTAssertEqual(minutesBeforeStart(meetAt: meet, now: unlock), 90)
        XCTAssertEqual(minutesBeforeStart(meetAt: meet, now: meet), 0)
        XCTAssertEqual(minutesBeforeStart(meetAt: meet, now: meet.addingTimeInterval(15 * 60)), -15)
        XCTAssertEqual(minutesBeforeStart(meetAt: meet, now: meet.addingTimeInterval(-(89 * 60 + 30))), 89)
        XCTAssertEqual(minutesBeforeStart(meetAt: meet, now: meet.addingTimeInterval(30)), 0)
    }

    // MARK: - the copy that depends on values

    func testTheLockedLineNamesTheUnlockTimeAndTomorrowWhenItIs() {
        XCTAssertEqual(lockedLine(unlockAt: unlock, now: unlock.addingTimeInterval(-3600), zone: berlin),
                       "Messaging available from 17:30")
        // At 23:00 the evening before, the unlock is on the next calendar day.
        let eveningBefore = meet.addingTimeInterval(-20 * 3600) // 2026-11-02T22:00:00Z
        XCTAssertEqual(lockedLine(unlockAt: unlock, now: eveningBefore, zone: berlin),
                       "Messaging available tomorrow from 17:30")
    }

    func testTheShareTextIsTheTicketsSentenceAndDropsThePlaceWhenThereIsNone() {
        XCTAssertEqual(shareText(date(place: .some(place)), zone: berlin),
                       "I'm meeting Leonie today at 19:00 at Cafe Krone, Oranienstrasse 42, Kreuzberg.")
        XCTAssertEqual(shareText(date(place: .some(DatePlace(name: "Cafe Krone", address: nil)), meetDay: "tomorrow"), zone: berlin),
                       "I'm meeting Leonie tomorrow at 19:00 at Cafe Krone.")
        XCTAssertEqual(shareText(date(place: .some(nil)), zone: berlin), "I'm meeting Leonie today at 19:00.")
    }

    func testTheCalendarEventIsDateWithNameOneHourAtThePlaceAndAddress() {
        XCTAssertEqual(calendarEvent(date(place: .some(place))),
                       CalendarEvent(title: "Date with Leonie", start: meet, end: meet.addingTimeInterval(3600),
                                     location: "Cafe Krone, Oranienstrasse 42, Kreuzberg"))
        XCTAssertNil(calendarEvent(date(place: .some(nil))).location)
    }

    func testCopyWritesPlaceCommaAddressOrTheNameAlone() {
        XCTAssertEqual(placeText(place), "Cafe Krone, Oranienstrasse 42, Kreuzberg")
        XCTAssertEqual(placeText(DatePlace(name: "Cafe Krone", address: nil)), "Cafe Krone")
    }

    func testDirectionsOpenAppleMapsWithThePlaceAsTheDestination() {
        XCTAssertEqual(directionsURL(place)?.absoluteString,
                       "https://maps.apple.com/?daddr=Cafe%20Krone,%20Oranienstrasse%2042,%20Kreuzberg")
    }

    func testTheUnreadTimeIsNowUnderAMinuteThenTheClockTime() {
        let sent = meet.addingTimeInterval(-50 * 60) // 18:10 Berlin
        XCTAssertEqual(unreadTime(sentAt: sent, now: sent.addingTimeInterval(59), zone: berlin), "now")
        XCTAssertEqual(unreadTime(sentAt: sent, now: sent.addingTimeInterval(60), zone: berlin), "18:10")
    }

    func testTheAboutLineIsAreaAndDistanceRoundedAsSearchRoundsAndAbsentWithNeither() {
        XCTAssertEqual(aboutLine(district: "Neukölln", distanceKm: 1), "Neukölln · ~1 km away")
        XCTAssertEqual(aboutLine(district: "Neukölln", distanceKm: 0), "Neukölln · < 1 km away")
        XCTAssertEqual(aboutLine(district: nil, distanceKm: 3), "~3 km away")
        XCTAssertEqual(aboutLine(district: "Neukölln", distanceKm: nil), "Neukölln")
        XCTAssertNil(aboutLine(district: nil, distanceKm: nil))
    }

    func testTheIntentLineShowsOnlyWhenBothMotivationsAreTheSame() {
        XCTAssertTrue(intentShared("long_term", "long_term"))
        XCTAssertFalse(intentShared("long_term", "casual"))
        XCTAssertFalse(intentShared(nil, nil))
        XCTAssertFalse(intentShared("long_term", nil))
    }

    func testHowTheDateWorksOpensWithTheAgreedPlaceAndTime() {
        XCTAssertEqual(worksMeetLine(date(place: .some(place)), zone: berlin), "Meet at Cafe Krone at 19:00")
        XCTAssertEqual(worksMeetLine(date(place: .some(nil)), zone: berlin), "Meet at 19:00")
    }

    func testTheMeetingHeadlinePutsTheDayInItsOwnRun() {
        let headline = meetingHeadline(name: "Leonie", day: "today")
        XCTAssertEqual(headline.lead, "You and Leonie are meeting ")
        XCTAssertEqual(headline.day, "today")
    }

    func testEveryServerChatReasonHasTheReferencesBadgeAndAnUnknownOneFallsBack() {
        XCTAssertEqual(chatReasonStyle("running_late").label, "I'm running late")
        XCTAssertEqual(chatReasonStyle("running_late").tone, .orange)
        XCTAssertEqual(chatReasonStyle("change_meet_time").label, "Change meet time")
        XCTAssertEqual(chatReasonStyle("change_location").tone, .violet)
        XCTAssertEqual(chatReasonStyle("cant_make_it_today").tone, .darkRed)
        XCTAssertEqual(chatReasonStyle("cant_find_you").label, "I can't spot you")
        XCTAssertEqual(chatReasonStyle("on_my_way").label, "I'm on my way")
        XCTAssertEqual(chatReasonStyle("something_new").label, "I'm on my way")
    }

    func testTheUnreadTagSaysNewMessageForOneAndNNewForMore() {
        XCTAssertEqual(unreadTag(1), "New message")
        XCTAssertEqual(unreadTag(3), "3 new")
    }

    // MARK: - the chips' icons, as the sheet draws them

    func testInCommonTheSharedIntentCarriesTheHeartAndTheInterestsCarryNone() {
        var d = date()
        d.sharedInterests = ["reading", "hiking"]
        XCTAssertEqual(commonChips(d), [CommonChip(label: "Long-term partner", icon: .heart),
                                        CommonChip(label: "Reading", icon: nil),
                                        CommonChip(label: "Hiking", icon: nil)])
    }

    func testInCommonHasNoIntentChipWhenTheMotivationsDiffer() {
        var d = date()
        d.motivationThem = "casual"
        d.sharedInterests = ["reading"]
        XCTAssertEqual(commonChips(d), [CommonChip(label: "Reading", icon: nil)])
    }

    func testOnlyTheThreeVibesTheReferenceDrawsCarryAnIcon() {
        XCTAssertEqual(vibeIcon("coffee_chat"), .coffee)
        XCTAssertEqual(vibeIcon("nature_walk"), .navigation)
        XCTAssertEqual(vibeIcon("playful"), .flame)
        XCTAssertNil(vibeIcon("deep_talk"))
        XCTAssertNil(vibeIcon("sunset"))
    }

    func testRideIsNeverAQuickAction() {
        XCTAssertEqual(QuickAction.allCases.map(\.wire), ["directions", "share", "calendar", "copy_address"])
    }

    // MARK: - where a launch opens

    func testAnActiveDateOpensTheDateScreenBeforeAnyCheckInTarget() {
        let active: Result<DateRead, CallFailure> = .success(.active(date()))
        XCTAssertEqual(launchTarget(date: active, checkInActive: true), .date)
        XCTAssertEqual(launchTarget(date: active, checkInActive: false), .date)
        XCTAssertEqual(launchTarget(date: active, checkInActive: nil), .date)
    }

    func testNoDateKeepsTheSearchTabsRule() {
        let none: Result<DateRead, CallFailure> = .success(.none)
        XCTAssertEqual(launchTarget(date: none, checkInActive: true), .checkInSearch)
        XCTAssertEqual(launchTarget(date: none, checkInActive: false), .checkInGate)
        XCTAssertEqual(launchTarget(date: none, checkInActive: nil), .checkInGate, "an unknown Check-In is the gate")
    }

    func testAFailedDateReadIsNoDate() {
        let failed: Result<DateRead, CallFailure> = .failure(.network)
        XCTAssertEqual(launchTarget(date: failed, checkInActive: true), .checkInSearch)
        XCTAssertEqual(launchTarget(date: failed, checkInActive: nil), .checkInGate)
    }

    func testTheDateScreenIsNotACheckInScreenAndAMatchFromSearchReadsForward() {
        XCTAssertFalse(FlowScreen.date.isCheckIn, "the Check-In foreground re-read must not cover it")
        XCTAssertTrue(FlowScreen.date > FlowScreen.checkInSearch)
    }
}
