//
//  ProfileDetailsRulesTests.swift
//  ShowUp · every rule in "Share some details" that is not a pixel (SHOWUP-167 to SHOWUP-173)
//
//  The Swift half of `ProfileDetailsRulesTest.kt`, assertion for assertion. Plain functions over
//  plain data, so no view, no device and no network. The model is tested in DetailsModelTests.
//

import ShowUpAPI
import XCTest
@testable import ShowUpWelcome

final class ProfileDetailsRulesTests: XCTestCase {

    // MARK: - the table

    func testSevenStepsInWalkOrderIndexed1To7AsSection2GivesThem() {
        XCTAssertEqual(
            DetailStep.allCases.map(\.stepId),
            ["height", "gender", "orientation", "dating_language", "education", "religion", "politics"])
        XCTAssertEqual(DetailStep.allCases.map(\.stepIndex), Array(1...7))
    }

    func testTheBarIsTenSegmentsFromOnePlace() {
        XCTAssertEqual(shareStepsTotal, 10)
    }

    func testOnlyGenderAndOrientationAreMandatory() {
        XCTAssertEqual(Set(DetailStep.allCases.filter(\.mandatory)), [.gender, .orientation])
    }

    func testOnlyTheSkippableSingleSelectsClearOnASecondTap() {
        XCTAssertEqual(Set(DetailStep.allCases.filter(\.clearsOnReselect)), [.education, .religion, .politics])
    }

    func testEveryOptionListIsTheTicketsLabelsInTheTicketsOrder() {
        XCTAssertEqual(DetailStep.gender.options.map(\.label), ["Woman", "Man", "Non-binary", "Other"])
        XCTAssertEqual(
            DetailStep.orientation.options.map(\.label),
            ["Straight", "Gay", "Lesbian", "Bisexual", "Pansexual", "Other"])
        XCTAssertEqual(
            DetailStep.datingLanguage.options.map(\.label),
            ["German", "English", "Spanish", "Italian", "French", "Turkish", "Russian", "Arabic"])
        XCTAssertEqual(
            DetailStep.education.options.map(\.label),
            ["A-Levels / Abitur", "Apprenticeship", "University degree", "PhD"])
        XCTAssertEqual(
            DetailStep.religion.options.map(\.label),
            ["Protestant", "Catholic", "Orthodox", "Muslim", "Jewish", "Buddhist", "Hindu",
             "Atheist", "Spiritual / other"])
        XCTAssertEqual(
            DetailStep.politics.options.map(\.label),
            ["Left", "Mid-left", "Middle", "Mid-right", "Right", "Conservative", "Libertarian", "Apolitical"])
        XCTAssertTrue(DetailStep.height.options.isEmpty)
    }

    func testWhatIsStoredIsTheSection1ValueNeverTheLabel() {
        XCTAssertEqual(DetailStep.gender.options[2].value, "non_binary")
        XCTAssertEqual(DetailStep.education.options[0].value, "a_levels_abitur")
        XCTAssertEqual(DetailStep.religion.options.last?.value, "spiritual_other")
        XCTAssertEqual(DetailStep.politics.options[1].value, "mid_left")
        // Conservative AFTER right -- the 1.4.15 reorder, keyed on value.
        XCTAssertEqual(Array(DetailStep.politics.options.map(\.value)[4..<6]), ["right", "conservative"])
    }

    func testClassesFollowSection1() {
        XCTAssertEqual(DetailStep.height.sensitivityClass, 0)
        XCTAssertEqual(DetailStep.gender.sensitivityClass, 2)
        XCTAssertEqual(DetailStep.orientation.sensitivityClass, 2)
        XCTAssertEqual(DetailStep.datingLanguage.sensitivityClass, 1)
        XCTAssertEqual(DetailStep.education.sensitivityClass, 1)
        XCTAssertEqual(DetailStep.religion.sensitivityClass, 2)
        XCTAssertEqual(DetailStep.politics.sensitivityClass, 2)
    }

    func testNextAndPreviousWalkTheStepsWithNothingPastEitherEnd() {
        XCTAssertNil(DetailStep.height.previous)
        XCTAssertEqual(DetailStep.height.next, .gender)
        XCTAssertEqual(DetailStep.politics.previous, .religion)
        XCTAssertNil(DetailStep.politics.next)
    }

    func testEachStepReportsItsOwnScreenAndPosition() {
        XCTAssertEqual(DetailStep.allCases.map(\.screen.screenId), [
            "profile_height", "profile_gender", "profile_orientation", "profile_dating_language",
            "profile_education", "profile_religion", "profile_politics",
        ])
        XCTAssertEqual(DetailStep.allCases.map(\.position.rawValue), DetailStep.allCases.map(\.stepId))
    }

    // MARK: - height

    func testTheFieldKeepsDigitsOnlyAtMostThreePasteIncluded() {
        XCTAssertEqual(filterHeightInput("175"), "175")
        XCTAssertEqual(filterHeightInput("1a7.5cm"), "175")
        XCTAssertEqual(filterHeightInput("1802"), "180")
        XCTAssertEqual(filterHeightInput("abc"), "")
        XCTAssertEqual(filterHeightInput(" 165 cm "), "165")
        // ASCII only, as on Android: a digit from another script is not a height.
        XCTAssertEqual(filterHeightInput("١٧٥"), "")
    }

    func testValidIsAWholeNumberFrom120To230Inclusive() {
        XCTAssertNil(parseHeight("119"))
        XCTAssertEqual(parseHeight("120"), 120)
        XCTAssertEqual(parseHeight("230"), 230)
        XCTAssertNil(parseHeight("231"))
        XCTAssertNil(parseHeight(""))
    }

    func testEmptyIsRequiredMissingAndOutOfRangeIsImpossible() {
        XCTAssertEqual(heightRefusal(""), "required_missing")
        XCTAssertEqual(heightRefusal("99"), "impossible")
        XCTAssertEqual(heightRefusal("231"), "impossible")
        XCTAssertNil(heightRefusal("175"))
    }

    func testBucketsFollowSection1TensAtTheEndsAndFivesInTheMiddle() {
        XCTAssertEqual(heightBucket(120), "<150")
        XCTAssertEqual(heightBucket(149), "<150")
        XCTAssertEqual(heightBucket(150), "150_159")
        XCTAssertEqual(heightBucket(159), "150_159")
        XCTAssertEqual(heightBucket(160), "160_164")
        XCTAssertEqual(heightBucket(169), "165_169")
        XCTAssertEqual(heightBucket(175), "175_179")
        XCTAssertEqual(heightBucket(189), "185_189")
        XCTAssertEqual(heightBucket(190), "190_199")
        XCTAssertEqual(heightBucket(199), "190_199")
        XCTAssertEqual(heightBucket(200), "200+")
        XCTAssertEqual(heightBucket(230), "200+")
    }

    // MARK: - taps

    func testARadioNeverClearsOnGenderAndOrientation() {
        XCTAssertEqual(pickSingle(.gender, current: "woman", tapped: "woman"), "woman")
        XCTAssertEqual(pickSingle(.orientation, current: "gay", tapped: "gay"), "gay")
    }

    func testASecondTapClearsOnEducationReligionAndPolitics() {
        XCTAssertNil(pickSingle(.education, current: "phd", tapped: "phd"))
        XCTAssertNil(pickSingle(.religion, current: "muslim", tapped: "muslim"))
        XCTAssertNil(pickSingle(.politics, current: "middle", tapped: "middle"))
    }

    func testADifferentRowAlwaysMovesTheSelection() {
        XCTAssertEqual(pickSingle(.gender, current: "woman", tapped: "man"), "man")
        XCTAssertEqual(pickSingle(.education, current: "apprenticeship", tapped: "phd"), "phd")
        XCTAssertEqual(pickSingle(.politics, current: nil, tapped: "left"), "left")
    }

    func testLanguagesToggleWithNoLimit() {
        var set = Set<String>()
        for option in DetailStep.datingLanguage.options { set = toggleMulti(set, tapped: option.value) }
        XCTAssertEqual(set.count, 8)
        set = toggleMulti(set, tapped: "german")
        XCTAssertFalse(set.contains("german"))
    }

    func testLanguagesAreSavedAndReportedInListOrderNotTapOrder() {
        let ticked = toggleMulti(toggleMulti([], tapped: "spanish"), tapped: "german")
        XCTAssertEqual(inListOrder(.datingLanguage, ticked), ["german", "spanish"])
        XCTAssertEqual(languagesBucketed(inListOrder(.datingLanguage, ticked)), "german,spanish")
    }

    // MARK: - what Continue resolves to

    func testHeightRefusesEmptyAndOutOfRangeAndAnswersAValidValue() {
        XCTAssertEqual(resolveContinue(.height, DetailDraft()), .refused(rule: "required_missing"))
        XCTAssertEqual(resolveContinue(.height, DetailDraft(heightText: "300")), .refused(rule: "impossible"))
        XCTAssertEqual(resolveContinue(.height, DetailDraft(heightText: "175")), .answer)
    }

    func testGenderAndOrientationRefuseAnEmptyContinueTheyAreMandatory() {
        XCTAssertEqual(resolveContinue(.gender, DetailDraft()), .refused(rule: "required_missing"))
        XCTAssertEqual(resolveContinue(.orientation, DetailDraft()), .refused(rule: "required_missing"))
    }

    func testTheFourSkippableListsTreatAnEmptyContinueAsASkipNeverARefusal() {
        for step in [DetailStep.datingLanguage, .education, .religion, .politics] {
            XCTAssertEqual(resolveContinue(step, DetailDraft()), .skip, "\(step)")
        }
    }

    func testASelectionIsAnAnswerOnEveryList() {
        XCTAssertEqual(resolveContinue(.gender, DetailDraft(gender: "other")), .answer)
        XCTAssertEqual(resolveContinue(.datingLanguage, DetailDraft(languages: ["arabic"])), .answer)
        XCTAssertEqual(resolveContinue(.politics, DetailDraft(politics: "apolitical")), .answer)
    }

    // MARK: - arrival and visibility

    func testArrivalShowsTheSavedValueAndVisibilityForThatStepOnly() {
        let saved = SavedDetails(heightCm: 181, gender: "man", hiddenFields: ["gender", "age"])
        let current = DetailDraft(orientation: "gay", hidden: ["orientation"])
        let draft = draftOnArrival(.gender, current: current, saved: saved)
        XCTAssertEqual(draft.gender, "man")
        XCTAssertTrue(draft.isHidden(.gender))
        // Orientation's part is untouched by gender's arrival.
        XCTAssertEqual(draft.orientation, "gay")
        XCTAssertTrue(draft.isHidden(.orientation))
    }

    func testArrivalDiscardsAnUnsavedSelectionBackThrowsItAway() {
        let draft = draftOnArrival(.religion, current: DetailDraft(religion: "hindu"), saved: SavedDetails())
        XCTAssertNil(draft.religion)
    }

    func testHeightArrivesAsItsSavedNumberOrEmpty() {
        XCTAssertEqual(
            draftOnArrival(.height, current: DetailDraft(), saved: SavedDetails(heightCm: 181)).heightText, "181")
        XCTAssertEqual(
            draftOnArrival(.height, current: DetailDraft(heightText: "17"), saved: SavedDetails()).heightText, "")
    }

    func testASavedValueThisBuildDoesNotKnowSelectsNothing() {
        XCTAssertNil(draftOnArrival(.gender, current: DetailDraft(), saved: SavedDetails(gender: "agender")).gender)
        let langs = draftOnArrival(
            .datingLanguage, current: DetailDraft(),
            saved: SavedDetails(datingLanguages: ["german", "klingon"]))
        XCTAssertEqual(langs.languages, ["german"])
    }

    func testHiddenFieldsIsAReplaceSoTheAgeChosenOnTheDateStepSurvives() {
        XCTAssertEqual(hiddenFieldsToSend(.height, saved: ["age"], hideThis: true), ["age", "height"])
        XCTAssertEqual(hiddenFieldsToSend(.height, saved: ["age", "height"], hideThis: false), ["age"])
        XCTAssertEqual(hiddenFieldsToSend(.religion, saved: ["age", "gender"], hideThis: false), ["age", "gender"])
    }

    /// The draft round-trips through scene storage — a restore mid-step keeps what was picked.
    func testTheDraftSurvivesTheSceneStorageRoundTrip() throws {
        let draft = DetailDraft(heightText: "181", gender: "man", languages: ["german", "arabic"],
                                politics: "middle", hidden: ["height"])
        let data = try JSONEncoder().encode(draft)
        XCTAssertEqual(try JSONDecoder().decode(DetailDraft.self, from: data), draft)
    }
}
