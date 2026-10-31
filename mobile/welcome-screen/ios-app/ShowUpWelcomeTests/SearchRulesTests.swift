//
//  SearchRulesTests.swift
//  ShowUp · Date search 01's rules, with no UI (SHOWUP-184)
//
//  The Swift twin of `SearchRulesTest.kt`: the same cases, the same answers.
//

import XCTest
@testable import ShowUpWelcome

final class SearchRulesTests: XCTestCase {

    func testDistanceReadsUnderOneOrAboutNAndTheDistrictOnlyWhenThereIsOne() {
        XCTAssertEqual(searchDistance(0), "< 1 km")
        XCTAssertEqual(searchDistance(1), "~1 km")
        XCTAssertEqual(searchDistance(14), "~14 km")
        XCTAssertEqual(distanceLine(3, "Kreuzberg"), "~3 km · Kreuzberg")
        XCTAssertEqual(distanceLine(0, "Neukölln, Berlin"), "< 1 km · Neukölln, Berlin")
        XCTAssertEqual(distanceLine(3, nil), "~3 km")
        XCTAssertEqual(distanceLine(3, "   "), "~3 km")
        // Three runs, drawn 6 apart as the sheet renders them; the line is their join.
        XCTAssertEqual(distanceParts(3, " Kreuzberg "), ["~3 km", "·", "Kreuzberg"])
        XCTAssertEqual(distanceParts(0, nil), ["< 1 km"])
    }

    func testFiltersActiveNamesAgeAndDistanceWhenTheProfileDefaultIsUnavailable() {
        let defaults = CheckInFilters(genders: ["woman"])
        XCTAssertEqual(filtersActive(defaults), [])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["man", "woman"])), [])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], minAge: 25)), ["age_range"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], maxAge: 40)), ["age_range"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], maxAge: 40, distanceKm: 25)), ["age_range", "distance_km"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], distanceKm: 5)), ["distance_km"])
    }

    func testGenderTrackingComparesSetsWithTheCurrentProfileDefault() {
        let defaults = checkinGenderDefault("woman", "straight")
        XCTAssertEqual(defaults, ["man"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["man"]), defaultGenders: defaults), [])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"]), defaultGenders: defaults), ["meet_gender"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman", "man"]),
                                     defaultGenders: checkinGenderDefault("man", "bisexual")), [])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], minAge: 25, distanceKm: 5),
                                     defaultGenders: defaults), ["meet_gender", "age_range", "distance_km"])
    }

    func testTrueDealbreakersAreListedBesideTheFilterEachHardens() {
        let f = CheckInFilters(genders: ["woman"])
        XCTAssertEqual(filtersActive(f), [])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], ageDealbreaker: true)), ["age_dealbreaker"])
        XCTAssertEqual(filtersActive(CheckInFilters(genders: ["woman"], minAge: 25, distanceKm: 5,
                                                    ageDealbreaker: true, distanceDealbreaker: true)),
                       ["age_range", "age_dealbreaker", "distance_km", "distance_dealbreaker"])
    }

    func testGenderDefaultsMatchTheCheckInTableAndTheServersPreFill() {
        XCTAssertEqual(checkinGenderDefault("man", "straight"), ["woman"])
        XCTAssertEqual(checkinGenderDefault("man", "gay"), ["man"])
        XCTAssertEqual(checkinGenderDefault("woman", "lesbian"), ["woman"])
        XCTAssertEqual(checkinGenderDefault("woman", "pansexual"), ["woman", "man"])
        let all: Set<String> = ["woman", "man", "non_binary", "other"]
        XCTAssertEqual(checkinGenderDefault("non_binary", "straight"), all)
        XCTAssertEqual(checkinGenderDefault("man", "other"), all)
        // The server's `checkInGenderDefault`: an orientation not yet answered pre-fills everyone.
        XCTAssertEqual(checkinGenderDefault("woman", nil), all)
        XCTAssertEqual(checkinGenderDefault(nil, nil), all)
    }

    func testAHiddenAgeLeavesTheNameAloneWithNoComma() {
        XCTAssertEqual(nameLine("Leonie", 28), "Leonie, 28")
        XCTAssertEqual(nameLine("Leonie", nil), "Leonie")
    }

    func testTheMeetLineIsTheHourOnThePhonesClockAndTheServersDay() throws {
        let at = SearchSamples.meetAt
        XCTAssertEqual(meetLine(at, day: "today", zone: try XCTUnwrap(TimeZone(identifier: "Europe/Berlin"))), "19:00 today")
        XCTAssertEqual(meetLine(at, day: "tomorrow", zone: try XCTUnwrap(TimeZone(identifier: "America/New_York"))), "13:00 tomorrow")
    }

    func testShowUpIsACountAndZeroIsNewInTheApp() {
        XCTAssertEqual(showUpCountLabel(0), "New in the App")
        XCTAssertEqual(showUpCountLabel(1), "1 date attended")
        XCTAssertEqual(showUpCountLabel(4), "4 dates attended")
        XCTAssertFalse(showUpCountLabel(4).contains("%"))
    }

    func testSharedVibesKeepTheProfilesOrderAtMostThreeThenPlusN() {
        let theirs = ["coffee_chat", "sunset", "deep_talk", "playful", "no_rush"]
        let shared = sharedVibes(theirs, mine: ["no_rush", "coffee_chat", "sunset", "deep_talk"])
        XCTAssertEqual(shared, ["coffee_chat", "sunset", "deep_talk", "no_rush"])
        XCTAssertEqual(sharedVibesRow(shared), SharedVibesRow(shown: ["coffee_chat", "sunset", "deep_talk"], more: 1))
        XCTAssertEqual(sharedVibesRow(["sunset"]), SharedVibesRow(shown: ["sunset"], more: 0))
        XCTAssertTrue(sharedVibes(theirs, mine: []).isEmpty)
    }

    func testTheInterestsSubAddsInCommonOnlyFromOne() {
        XCTAssertEqual(interestsSub("Leonie", total: 10, common: 4), "10 things Leonie is into · 4 in common")
        XCTAssertEqual(interestsSub("Leonie", total: 10, common: 0), "10 things Leonie is into")
        XCTAssertEqual(sharedInterests(["yoga_pilates", "hiking", "reading"], mine: ["reading", "hiking"]), ["hiking", "reading"])
        XCTAssertTrue(sharedInterests(["hiking"], mine: []).isEmpty)
    }

    func testLabelsComeFromTheAppsOwnTablesAndAnUnknownValueHasNone() {
        XCTAssertEqual(vibeLabel("coffee_chat"), "Coffee & a chat")
        XCTAssertNil(vibeLabel("teleportation"))
        XCTAssertEqual(motivationOf("long_term")?.title, "Long-term partner")
        XCTAssertEqual(motivationOf("long_term")?.icon, .heartFilled)
        XCTAssertNil(motivationOf("something_new"))
    }

    func testMediaSubtitlesUseTheTakesLengthAsItsOwnerSawIt() {
        XCTAssertEqual(voiceSub(14_600), "Voice prompt · 0:14")
        XCTAssertEqual(videoSub(10_000), "Video prompt · 0:10")
    }

    func testAtRestTheWaveformIsTheReferences18VioletBarsWhilePlayingItFollowsThePlayhead() {
        XCTAssertEqual(voiceBars.count, 48)
        XCTAssertEqual(voiceVioletBars(playing: false, progress: 0), 18)
        XCTAssertEqual(voiceVioletBars(playing: false, progress: 0.9), 18)
        XCTAssertEqual(voiceVioletBars(playing: true, progress: 0), 0)
        XCTAssertEqual(voiceVioletBars(playing: true, progress: 0.5), 24)
        XCTAssertEqual(voiceVioletBars(playing: true, progress: 1), 48)
        XCTAssertEqual(voiceVioletBars(playing: true, progress: 1.4), 48)
    }

    func testEssentialsAreInTheTicketsOrderAndAMissingOrUnknownAnswerDropsItsRow() {
        let rows = essentials(EssentialAnswers(
            heightCm: 172, gender: "woman", orientation: "straight",
            datingLanguages: ["german", "english", "klingon"],
            education: nil, religion: "spiritual_other", politics: "unknown_value"))
        XCTAssertEqual(rows.map(\.row), [.height, .gender, .orientation, .datesIn, .religion])
        XCTAssertEqual(rows[0].value, "172 cm")
        XCTAssertEqual(rows[3].value, "German, English")
        XCTAssertEqual(EssentialRow.allCases.map(\.label), ["Height", "Gender", "Orientation", "Dates in", "Education", "Religion", "Politics"])
        XCTAssertTrue(essentials(EssentialAnswers()).isEmpty)
    }

    func testHowILiveDrawsOnlyTheAnsweredGroupsInTheGroupOrderWithTheirIcons() {
        let tiles = lifestyleTiles(["social": "balanced", "activity": "active", "diet": "nope"])
        XCTAssertEqual(tiles.map(\.field), [.activity, .social])
        XCTAssertEqual(tiles.map(\.value), ["Active", "Balanced"])
        XCTAssertEqual(lifestyleIcon(.diet), .leaf)
        XCTAssertTrue(lifestyleTiles([:]).isEmpty)
    }

    func testTheMessageIsCountedAndCutInGraphemesNeverSplittingOne() {
        // One family: a man, a woman and a girl joined by zero-width joiners -- five code points.
        let family = "\u{1F468}\u{200D}\u{1F469}\u{200D}\u{1F467}"
        XCTAssertEqual(graphemes(family), 1)
        let long = String(repeating: "a", count: 99) + family + "b"
        XCTAssertEqual(graphemes(long), 101)
        let cut = limitMessage(long)
        XCTAssertEqual(graphemes(cut), 100)
        XCTAssertTrue(cut.hasSuffix(family))
        XCTAssertEqual(limitMessage(String(repeating: "a", count: 150)), String(repeating: "a", count: 100))
        XCTAssertEqual(messageCounter("Hello there!"), "12 / 100")
    }

    func testTheDraftNamesThePersonAndTheDayAndALongNameNeverStartsItOverTheLimit() {
        XCTAssertEqual(messageDraft("Leonie", "today"),
                       "Hi Leonie, I love the vibe of your profile. Would be great to meet you today :)")
        XCTAssertEqual(graphemes(messageDraft(String(repeating: "A", count: 60), "tomorrow")), 100)
    }

    func testSendMessageSendsTheTrimmedTextAndBlankSendsNone() {
        XCTAssertEqual(messageToSend("  Hi!  "), "Hi!")
        XCTAssertNil(messageToSend("   "))
        XCTAssertNil(messageToSend(""))
    }

    func testTheCoachShowsOnceAVisitOverAProfileWithLikeUntilDontShowAgain() {
        XCTAssertTrue(shouldShowCoach(hiddenForever: false, shownThisVisit: false, hasProfile: true, likedYou: false))
        XCTAssertFalse(shouldShowCoach(hiddenForever: true, shownThisVisit: false, hasProfile: true, likedYou: false))
        XCTAssertFalse(shouldShowCoach(hiddenForever: false, shownThisVisit: true, hasProfile: true, likedYou: false))
        XCTAssertFalse(shouldShowCoach(hiddenForever: false, shownThisVisit: false, hasProfile: false, likedYou: false))
        XCTAssertFalse(shouldShowCoach(hiddenForever: false, shownThisVisit: false, hasProfile: true, likedYou: true))
    }

    func testTheCoachRowsMirrorTheThreeButtonsInTheirOrder() {
        XCTAssertEqual(CoachRow.allCases.map(\.title), ["Not interested", "Not now", "Like"])
        XCTAssertEqual(CoachRow.allCases.map(\.icon), [.close, .clock, .heart])
    }

    func testTheDistrictReadsSubLocalityThenLocalityNeverOneNameTwice() {
        XCTAssertEqual(districtOf("Neukölln", "Berlin"), "Neukölln, Berlin")
        XCTAssertEqual(districtOf(nil, "Berlin"), "Berlin")
        XCTAssertEqual(districtOf(" Neukölln ", "  "), "Neukölln")
        XCTAssertEqual(districtOf("Hamburg", "hamburg"), "Hamburg")
        XCTAssertNil(districtOf(nil, nil))
        XCTAssertNil(districtOf(" ", ""))
        let longSub = Array(repeating: "Kleinstadt", count: 12).joined(separator: " ")
        let cut = districtOf(longSub, "Berlin") ?? ""
        XCTAssertLessThanOrEqual(cut.count, districtMax)
        XCTAssertFalse(cut.hasSuffix(" "))
        XCTAssertTrue(longSub.hasPrefix(cut))
    }
}
