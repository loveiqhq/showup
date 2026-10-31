//
//  CheckInRulesTests.swift
//  ShowUp · every rule the six Check-In screens decide, with no UI (SHOWUP-178 to SHOWUP-183)
//
//  The Swift half of `CheckInRulesTest.kt`, case for case. The cases are the tickets' own wherever a
//  ticket gives one -- 4:20pm starts Today at 5pm, `6–9pm`, `12–2pm · 7–8pm`, `Any age`,
//  `1 character left`. The registry ids are written out verbatim from `enums.json` §27 to §32: the
//  Kotlin twin reads the file itself, and a value renamed there fails that test and this one.
//

import XCTest
@testable import ShowUpWelcome

final class CheckInRulesTests: XCTestCase {

    private func at(_ h: Int, _ m: Int, day: Int = 13) -> CivilDateTime {
        CivilDateTime(date: CivilDate(year: 2026, month: 10, day: day), hour: h, minute: m)
    }

    private func date(_ day: Int, month: Int = 10) -> CivilDate {
        CivilDate(year: 2026, month: month, day: day)
    }

    // MARK: - the 7am day and the next full hour (02)

    func testAt420pmTodayStartsAt5pmAndTomorrowAt7amBothEndingAtThe6amSlot() {
        let days = checkInDays(at(16, 20))
        XCTAssertEqual(days.todayDate, date(13))
        XCTAssertEqual(days.todayStartHour, 17)
        XCTAssertEqual(availabilityHours(days.todayStartHour), [17, 18, 19, 20, 21, 22, 23, 0, 1, 2, 3, 4, 5, 6])
        XCTAssertEqual(availabilityHours(17).count, 14, "14 slots at 4:20, as the sheet says")
        let tomorrow = availabilityHours(checkInDayStart)
        XCTAssertEqual(tomorrow.count, 24)
        XCTAssertEqual(tomorrow.first, 7)
        XCTAssertEqual(tomorrow.last, 6)
    }

    func testAnHourThatHasStartedIsNeverOffered() {
        // 4:00 sharp: 4pm has started, so Today still starts at 5pm.
        XCTAssertEqual(checkInDays(at(16, 0)).todayStartHour, 17)
        XCTAssertEqual(checkInDays(at(16, 59)).todayStartHour, 17)
    }

    func testNightHoursBelongToTheEveningBefore() {
        // 1:20am: Today is 2am-6am of the block that began yesterday at 7am.
        let night = checkInDays(at(1, 20))
        XCTAssertEqual(night.todayDate, date(12))
        XCTAssertEqual(night.todayStartHour, 2)
        XCTAssertEqual(availabilityHours(2), [2, 3, 4, 5, 6])
        // 11:20pm: the next full hour is midnight, still the evening's block.
        let late = checkInDays(at(23, 20))
        XCTAssertEqual(late.todayDate, date(13))
        XCTAssertEqual(late.todayStartHour, 0)
        // 5:20am: one slot left, 6am.
        XCTAssertEqual(availabilityHours(checkInDays(at(5, 20)).todayStartHour), [6])
    }

    func testAt620amTodayIsTheNewDayFrom7am() {
        let days = checkInDays(at(6, 20))
        XCTAssertEqual(days.todayDate, date(13))
        XCTAssertEqual(days.todayStartHour, 7)
        XCTAssertEqual(availabilityHours(7).count, 24)
    }

    func testAGridsStartsPutTheSmallHoursOnTheNextCalendarDate() {
        let grid = gridOf(.today, checkInDays(at(16, 20)))
        XCTAssertEqual(grid.starts.first, at(17, 0))
        XCTAssertEqual(grid.starts[grid.hours.firstIndex(of: 0)!], at(0, 0, day: 14))
        XCTAssertEqual(grid.starts.last, at(6, 0, day: 14))
        let tomorrow = gridOf(.tomorrow, checkInDays(at(16, 20)))
        XCTAssertEqual(tomorrow.starts.first, at(7, 0, day: 14))
        XCTAssertEqual(tomorrow.starts.last, at(6, 0, day: 15))
    }

    func testASlotStartIsAnInstantInTheDevicesZoneAcrossADstChange() throws {
        let berlin = try XCTUnwrap(TimeZone(identifier: "Europe/Berlin"))
        // 25 Oct 2026, the night the clocks go back: 2am happens twice; the first is +02:00.
        let start = slotStart(date(24), hour: 2, zone: berlin)
        let wall = CivilDateTime(start, in: berlin)
        XCTAssertEqual(wall.date, date(25))
        XCTAssertEqual(wall.hour, 2)
    }

    func testDatesRollAcrossMonthAndYearEnds() {
        XCTAssertEqual(CivilDate(year: 2026, month: 10, day: 31).adding(days: 1), date(1, month: 11))
        XCTAssertEqual(CivilDate(year: 2026, month: 12, day: 31).adding(days: 1), CivilDate(year: 2027, month: 1, day: 1))
        XCTAssertEqual(date(1, month: 11).adding(days: -1), CivilDate(year: 2026, month: 10, day: 31))
    }

    // MARK: - slots, labels and the summary (02)

    func testATapCyclesIdlePreferredAvailableIdle() {
        XCTAssertEqual(cycleSlot(nil), .preferred)
        XCTAssertEqual(cycleSlot(.preferred), .available)
        XCTAssertNil(cycleSlot(.available))
    }

    func testHourLabelsAre12HourWithNoonAnd12am() {
        XCTAssertEqual(formatHour(7), "7am")
        XCTAssertEqual(formatHour(11), "11am")
        XCTAssertEqual(formatHour(12), "noon")
        XCTAssertEqual(formatHour(13), "1pm")
        XCTAssertEqual(formatHour(23), "11pm")
        XCTAssertEqual(formatHour(0), "12am")
        XCTAssertEqual(formatHour(6), "6am")
    }

    func testTheSummaryReadsTheSheetsRanges() {
        let today = availabilityHours(17)
        XCTAssertEqual(availabilitySummary(today, [18: .preferred, 19: .preferred, 20: .available]), "6–9pm")
        let tomorrow = availabilityHours(7)
        XCTAssertEqual(availabilitySummary(tomorrow, [12: .preferred, 13: .preferred, 19: .available]),
                       "12–2pm · 7–8pm")
        XCTAssertNil(availabilitySummary(today, [:]), "nothing selected yet is the screen's, not a range")
    }

    func testARunAcrossMidnightStaysOneRangeAndTiersAreNotDistinguished() {
        let today = availabilityHours(17)
        XCTAssertEqual(availabilitySummary(today, [23: .available, 0: .preferred, 1: .available]), "11pm–2am")
        XCTAssertEqual(availabilitySummary(today, [5: .preferred, 6: .preferred]), "5–7am")
        XCTAssertEqual(availabilitySummary(availabilityHours(7), [11: .preferred]), "11am–12pm")
    }

    func testPastFourRunsTheSummarySaysHowManyAreHidden() {
        let tomorrow = availabilityHours(7)
        let four = Dictionary(uniqueKeysWithValues: [7, 9, 11, 13].map { ($0, SlotTier.preferred) })
        XCTAssertEqual(availabilitySummary(tomorrow, four), "7–8am · 9–10am · 11am–12pm · 1–2pm")
        let six = Dictionary(uniqueKeysWithValues: [7, 9, 11, 13, 15, 17].map { ($0, SlotTier.preferred) })
        XCTAssertEqual(availabilitySummary(tomorrow, six), "7–8am · 9–10am · 11am–12pm · +3 more")
    }

    func testTierCountsFeedAvailabilitySlotsSet() {
        let picked: [Int: SlotTier] = [18: .preferred, 19: .preferred, 20: .available]
        XCTAssertEqual(tierCount(picked, .preferred), 2)
        XCTAssertEqual(tierCount(picked, .available), 1)
    }

    // MARK: - vibes (04)

    func testAtFiveAnIdleChipSelectsNothingAndAPickedChipStillDeselects() {
        let five: Set<String> = ["coffee_chat", "sunset", "calm", "playful", "no_rush"]
        XCTAssertEqual(tapVibe(five, "cozy"), .limit)
        XCTAssertEqual(tapVibe(five, "calm"), .changed(five.subtracting(["calm"])))
        XCTAssertEqual(tapVibe([], "calm"), .changed(["calm"]))
    }

    func testVibeIdsIsListOrderNeverTapOrder() {
        XCTAssertEqual(vibeIds(["no_rush", "playful", "coffee_chat"]), "coffee_chat,playful,no_rush")
    }

    func testTwentyVibesInThreeGroupsTheLabelsTheTicketGives() {
        XCTAssertEqual(VibeGroup.allCases.map(\.vibes.count), [8, 6, 6])
        XCTAssertEqual(Set(vibeOrder).count, 20)
        XCTAssertEqual(vibeLabel["no_rush"], "Free evening (no rush)")
        XCTAssertEqual(VibeGroup.allCases.map(\.label),
                       ["What you want to do", "How you feel right now", "Your energy & pacing"])
    }

    // MARK: - filters (05)

    func testTheAgeHintReadsAnyAgeNPlusAndARange() {
        XCTAssertEqual(ageLabel(18, 65), "Any age")
        XCTAssertEqual(ageLabel(30, 65), "30+")
        XCTAssertEqual(ageLabel(25, 35), "25 – 35")
        XCTAssertEqual(ageLabel(18, 40), "18 – 40")
        XCTAssertEqual(ageLabel(30, 30), "30 – 30", "one age: the thumbs may meet")
    }

    func test65IsNoUpperLimitAndIsSentAsNil() {
        XCTAssertNil(CheckInFilters(genders: ["woman"]).maxAgeOrNil)
        XCTAssertEqual(CheckInFilters(genders: ["woman"], maxAge: 64).maxAgeOrNil, 64)
    }

    func testThumbsNeverCrossDraggingOnePastTheOtherCarriesIt() {
        let f = CheckInFilters(genders: ["woman"], minAge: 25, maxAge: 35)
        XCTAssertEqual(moveAgeThumb(f, thumb: 0, value: 40), CheckInFilters(genders: ["woman"], minAge: 40, maxAge: 40))
        XCTAssertEqual(moveAgeThumb(f, thumb: 1, value: 20), CheckInFilters(genders: ["woman"], minAge: 20, maxAge: 20))
        XCTAssertEqual(moveAgeThumb(f, thumb: 0, value: 5), CheckInFilters(genders: ["woman"], minAge: 18, maxAge: 35))
        XCTAssertEqual(moveAgeThumb(f, thumb: 1, value: 99), CheckInFilters(genders: ["woman"], minAge: 25, maxAge: 65))
    }

    func testFiltersChangedNamesTheChangedFiltersInRegistryOrderOrNone() {
        let entry = CheckInFilters(genders: ["woman"])
        XCTAssertEqual(filtersChanged(entry, entry), "none")
        var further = entry
        further.distanceKm = 15
        XCTAssertEqual(filtersChanged(entry, further), "distance_km")
        XCTAssertEqual(
            filtersChanged(entry, CheckInFilters(genders: ["woman", "non_binary"], minAge: 25, maxAge: 35, distanceKm: 15)),
            "meet_gender,age_range,distance_km")
        var capped = entry
        capped.maxAge = 64
        XCTAssertEqual(filtersChanged(entry, capped), "age_range")
    }

    // MARK: - uplift (06)

    func testAnEmojiAFlagAndAFamilyEachCountAsOneCharacter() {
        XCTAssertEqual(graphemeCount("\u{1F600}"), 1)
        XCTAssertEqual(graphemeCount("\u{1F1E9}\u{1F1EA}"), 1)
        XCTAssertEqual(graphemeCount("\u{1F468}\u{200D}\u{1F469}\u{200D}\u{1F467}"), 1)
        XCTAssertEqual(graphemeCount("hello"), 5)
    }

    func testInputPast100IsNotAcceptedAndAPasteIsCutAt100Graphemes() {
        let long = String(repeating: "a", count: 150)
        XCTAssertEqual(clampUplift(long).count, 100)
        let emoji = String(repeating: "\u{1F600}", count: 120)
        let cut = clampUplift(emoji)
        XCTAssertEqual(graphemeCount(cut), 100)
        XCTAssertEqual(cut.utf16.count, 200, "an emoji is never split in half")
        XCTAssertEqual(clampUplift("short"), "short")
    }

    func testTheCounterCountsDownAndSaysCharacterAtOne() {
        XCTAssertEqual(upliftCounterLabel(""), "100 characters left")
        XCTAssertEqual(upliftCounterLabel(String(repeating: "a", count: 99)), "1 character left")
        XCTAssertEqual(upliftCounterLabel(String(repeating: "a", count: 100)), "0 characters left")
        XCTAssertEqual(upliftCounterLabel("Text me on 0176 2345 6789 and we'll grab a drink tonight"),
                       "44 characters left")
    }

    func testEmailsPhoneNumbersAndLinksAreCaught() {
        for text in [
            "Text me on 0176 2345 6789",
            "+49 176 23456789",
            "call (030) 123-4567",
            "0176/2345678",
            "mail me leo@hey.com",
            "LEO@HEY.DE",
            "https://show.up/me",
            "www.leo.com",
            "find me at leo.de",
            "insta.com/leo",
        ] {
            XCTAssertTrue(upliftHasContact(text), text)
        }
    }

    func testOrdinaryUpliftsPass() {
        for text in [
            "",
            "will take you out on my boat on Wannsee",
            "have 2x tickets for Eminem today",
            "123456 is six digits",
            "meet at 7.30 by the U2",
            "If we meet today, I will take you on a rafting tour on Wannsee with Picnic :)",
        ] {
            XCTAssertFalse(upliftHasContact(text), text)
        }
    }

    func testAnswerValueIsWrittenOrEmptyNeverTheText() {
        XCTAssertEqual(upliftAnswerValue(""), "empty")
        XCTAssertEqual(upliftAnswerValue("   \n "), "empty")
        XCTAssertEqual(upliftAnswerValue(" boat "), "written")
    }

    // MARK: - the registry, verbatim from enums.json §27 to §32

    func testMotivationVibeAndGenderIdsAreTheRegistrys() {
        XCTAssertEqual(MotivationOption.allCases.map(\.id),
                       ["long_term", "non_monogamy", "short_term", "casual", "friends"])
        XCTAssertEqual(vibeOrder, [
            "coffee_chat", "casual_drink", "grab_food", "nature_walk", "bouldering_table_tennis", "workout",
            "sunset", "drinks_music", "calm", "deep_talk", "playful", "cozy", "spontaneous", "curious",
            "quick_hello", "low_key", "high_energy", "no_rush", "early_night", "meet_now",
        ])
        XCTAssertEqual(CheckInGender.allCases.map(\.id), ["woman", "man", "non_binary", "other"])
    }

    // MARK: - what VoiceOver is told (Android's CheckInSemanticsTest, the part a unit test can reach)
    //
    // SwiftUI builds no accessibility tree in a unit test (`FitDevices.swift` records the three
    // attempts), so the strings the views hand to VoiceOver are plain functions, asserted here.

    func testASlotIsAnnouncedWithItsHourAndItsState() {
        XCTAssertEqual(formatHour(17), "5pm")
        XCTAssertEqual(CheckInA11y.slotValue(nil), "not selected")
        XCTAssertEqual(CheckInA11y.slotValue(.preferred), "preferred")
        XCTAssertEqual(CheckInA11y.slotValue(.available), "available")
    }

    func testHowItWorksSaysWhetherItIsOpen() {
        XCTAssertEqual(CheckInA11y.disclosureValue(true), "Expanded")
        XCTAssertEqual(CheckInA11y.disclosureValue(false), "Collapsed")
    }

    func testTheNamesVoiceOverReadsAreTheTicketsCopy() {
        XCTAssertEqual(CheckInCopy.closeLabel, "Close Check-In")
        XCTAssertEqual(CheckInCopy.noHoursDialogLabel, "No available hours today")
        XCTAssertEqual(CheckInCopy.motivationGroup, "Motivation to date")
        XCTAssertEqual(CheckInCopy.upliftFieldLabel, "Your reason to be picked")
        XCTAssertEqual([CheckInCopy.sliderMinAge, CheckInCopy.sliderMaxAge, CheckInCopy.sliderDistance],
                       ["Minimum age", "Maximum age", "Distance in km"])
        // Search · Likes · Instant · Settings for every user (SHOWUP-187 rule 10).
        XCTAssertEqual(AppTab.allCases.map(\.label), ["Search", "Likes", "Instant", "Settings"])
    }

    func testStepIdsGateTargetsNoHoursActionsAndCommitFailuresAreTheRegistrys() {
        XCTAssertEqual(CheckInStep.allCases.map(\.stepId),
                       ["intro", "today", "tomorrow", "motivation", "vibe", "filters", "uplift"])
        XCTAssertEqual(CheckInAnalytics.GateTarget.allCases.map(\.wire), ["cta", "search_tab", "instant_tab"])
        XCTAssertEqual(CheckInAnalytics.NoHoursAction.allCases.map(\.wire),
                       ["continue_tomorrow", "pick_today", "dismissed"])
        XCTAssertEqual(CheckInAnalytics.CommitFailure.allCases.map(\.wire), ["network", "server", "contact_info"])
    }

    // MARK: - 05b (SHOWUP-189)

    func testFiltersChangedPutsEachSwitchRightAfterTheFilterItHardens() {
        let entry = CheckInFilters(genders: ["woman"])
        var age = entry
        age.ageDealbreaker = true
        XCTAssertEqual(filtersChanged(entry, age), "age_dealbreaker")
        let all = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 15,
                                 ageDealbreaker: true, distanceDealbreaker: true)
        XCTAssertEqual(filtersChanged(entry, all), "meet_gender,age_range,age_dealbreaker,distance_km,distance_dealbreaker")
    }

    func testASwitchSetOnTheStandInSurvivesTheServersSettings() {
        let server = CheckInFilters(genders: ["man"], minAge: 25, maxAge: 35, distanceKm: 15,
                                    ageDealbreaker: true, distanceDealbreaker: false)
        let local = CheckInFilters(genders: ["woman"], distanceDealbreaker: true)
        var expected = server
        expected.distanceDealbreaker = true
        XCTAssertEqual(filtersOverStandIn(local, server: server, edited: [.distanceDealbreaker]), expected)
    }
}
