//
//  PickRulesTests.swift
//  ShowUp · every rule of Interests, Life and Future that is not a pixel (SHOWUP-174 to SHOWUP-176)
//
//  The Swift half of `PickRulesTest.kt`, case for case: the cap, the single-select groups, the
//  exclusive answers, list order and the arrival reset — plain functions, no view.
//

import XCTest
@testable import ShowUpWelcome

final class PickRulesTests: XCTestCase {

    // MARK: - the steps

    func testTheThreeStepsAre8To10WithTheirRegistryIds() {
        XCTAssertEqual(PickStep.allCases.map(\.stepIndex), [8, 9, 10])
        XCTAssertEqual(PickStep.allCases.map(\.stepId), ["interests", "life_now", "life_ahead"])
        XCTAssertEqual(PickStep.allCases.map(\.position.rawValue), ["interests", "life_now", "life_ahead"])
        XCTAssertEqual(PickStep.allCases.map(\.screen.screenId),
                       ["profile_interests", "profile_life_now", "profile_life_ahead"])
        XCTAssertEqual(PickStep.allCases.map(\.screen.screenName),
                       ["ProfileInterests", "ProfileLifeNow", "ProfileLifeAhead"])
        XCTAssertEqual(shareStepsTotal, 10)
    }

    // MARK: - Interests: the lists

    func testHowILiveIsTheTicketsSixGroupsInOrder() {
        XCTAssertEqual(LifestyleField.allCases.map(\.label),
                       ["Activity level", "Diet", "Alcohol", "Smoking", "Rhythm", "Social battery"])
        XCTAssertEqual(LifestyleField.activity.options.map(\.label),
                       ["Couch-lover", "Moderately active", "Active", "Sport is my life"])
        XCTAssertEqual(LifestyleField.activity.options.map(\.value),
                       ["couch_lover", "moderately_active", "active", "sport_is_my_life"])
        XCTAssertEqual(LifestyleField.smoking.options.map(\.label), ["No", "Sometimes", "Yes"])
        XCTAssertEqual(LifestyleField.allCases.map(\.sensitivityClass), [1, 1, 2, 2, 0, 0])
    }

    func testWhatImIntoIsTheReferencesSixGroupsAnd51Chips() {
        XCTAssertEqual(InterestSection.allCases.map(\.label),
                       ["Move", "Food & Drink", "Culture", "Cozy at home", "Going out", "Travel style"])
        XCTAssertEqual(InterestSection.allCases.map(\.sectionId), ["move", "food", "culture", "home", "out", "travel"])
        XCTAssertEqual(InterestSection.allCases.map(\.options.count), [12, 9, 8, 6, 7, 9])
        XCTAssertEqual(interestOrder.count, 51)
        XCTAssertEqual(Set(interestOrder).count, 51)
        XCTAssertEqual(InterestSection.move.options[2].label, "Hyrox/CrossFit")
        XCTAssertEqual(InterestSection.move.options[2].value, "hyrox_crossfit")
    }

    func testEveryGroupCollapsesToFourAndRevealsTheRest() {
        XCTAssertTrue(InterestSection.allCases.allSatisfy(\.collapsible))
        XCTAssertEqual(InterestSection.allCases.map(\.hiddenCount), [8, 5, 4, 2, 3, 5])
        XCTAssertEqual(visibleOptions(.move, expanded: false).map(\.label),
                       ["Gym", "Running", "Hyrox/CrossFit", "Yoga/Pilates"])
        XCTAssertEqual(visibleOptions(.move, expanded: true).count, 12)
    }

    // MARK: - Interests: the taps

    func testAHowILiveTapMovesTheAnswerAndTheChosenChipClearsIt() {
        XCTAssertEqual(pickLifestyle(current: nil, tapped: "active"), "active")
        XCTAssertEqual(pickLifestyle(current: "vegetarian", tapped: "vegan"), "vegan")
        XCTAssertNil(pickLifestyle(current: "vegan", tapped: "vegan"))
    }

    func testAtTenAnUnpickedChipDoesNothingAndAPickedOneStillUnpicks() {
        let ten = Set(interestOrder.prefix(10))
        XCTAssertEqual(tapInterest(ten, tapped: interestOrder[10]), .capReached)
        XCTAssertEqual(tapInterest(ten, tapped: interestOrder[3]), .deselected(ten.subtracting([interestOrder[3]])))
        XCTAssertEqual(tapInterest(Set(interestOrder.prefix(9)), tapped: interestOrder[9]), .selected(ten))
    }

    func testTheCounterReadsNPickedMLeftAndTenOfTenAtTheCap() {
        XCTAssertEqual(interestsCounterLabel(0), "0 picked \u{00B7} 10 left")
        XCTAssertEqual(interestsCounterLabel(6), "6 picked \u{00B7} 4 left")
        XCTAssertEqual(interestsCounterLabel(10), "10 of 10 picked")
        XCTAssertEqual(interestsFill(6), 0.6, accuracy: 0.0001)
        XCTAssertEqual(interestsFill(10), 1)
    }

    func testInterestsSaveInListOrder() {
        XCTAssertEqual(interestsInListOrder(["cruises", "gym", "reading"]), ["gym", "reading", "cruises"])
    }

    func testHowILiveReportsInListOrderAndUnansweredGroupsSendNothing() {
        let draft = InterestsDraft(lifestyle: ["social": "balanced", "activity": "active"])
        let answered = answeredLifestyle(draft)
        XCTAssertEqual(answered.map(\.0), [.activity, .social])
        XCTAssertEqual(answered.map(\.1), ["active", "balanced"])
    }

    func testArrivalPreFillsWhatThisBuildKnowsEveryGroupCollapsed() {
        var saved = SavedDetails()
        saved.lifestyle = ["activity": "active", "diet": "carnivore"]
        saved.interests = ["gym", "skydiving", "wine"]
        let draft = interestsOnArrival(saved)
        XCTAssertEqual(draft.lifestyle, ["activity": "active"])
        XCTAssertEqual(draft.interests, ["gym", "wine"])
        XCTAssertTrue(draft.expanded.isEmpty)
    }

    // MARK: - Life and Future

    func testLifeAsksPartnershipFamilyLivingWithTheTicketsLabels() {
        XCTAssertEqual(PickStep.lifeNow.questions.map(\.label),
                       ["Partnership situation", "Family situation", "Living situation"])
        XCTAssertEqual(PickStep.lifeNow.questions.map(\.sensitivityClass), [2, 1, 1])
        XCTAssertEqual(PickStep.lifeNow.questions.map(\.icon), [.heart, .users, .home])
        XCTAssertEqual(LifeQuestion.living.options.map(\.value),
                       ["living_alone", "living_with_roommates", "living_with_family", "living_with_children"])
    }

    func testFutureAsksFamilyFirst() {
        XCTAssertEqual(PickStep.lifeAhead.questions.map(\.label),
                       ["Family vision", "Partnership vision", "Living style vision"])
        XCTAssertEqual(PickStep.lifeAhead.questions.map(\.questionId),
                       ["family_ahead", "partnership_ahead", "living_ahead"])
        XCTAssertEqual(PickStep.lifeAhead.questions.map(\.sensitivityClass), [1, 2, 1])
        XCTAssertEqual(PickStep.lifeAhead.questions.map(\.icon), [.users, .heart, .compass])
        XCTAssertEqual(LifeQuestion.familyAhead.options.map(\.label),
                       ["I want kids", "I don't want kids", "I want to find out together"])
        XCTAssertTrue(PickStep.interests.questions.isEmpty)
    }

    func testNoChildrenReplacesEveryFamilyPickAndAnyOtherPickClearsIt() {
        let parent: Set<String> = ["has_children", "co_parenting", "single_parent"]
        XCTAssertEqual(toggleLifePick(.family, current: parent, tapped: "no_children"), ["no_children"])
        XCTAssertEqual(toggleLifePick(.family, current: ["no_children"], tapped: "empty_nester"), ["empty_nester"])
        XCTAssertEqual(toggleLifePick(.family, current: ["no_children"], tapped: "no_children"), [])
    }

    func testLifePillsCombineAndAPickedOneUnpicks() {
        var picked = toggleLifePick(.partnership, current: [], tapped: "single")
        picked = toggleLifePick(.partnership, current: picked, tapped: "divorced")
        XCTAssertEqual(picked, ["single", "divorced"])
        XCTAssertEqual(toggleLifePick(.partnership, current: picked, tapped: "single"), ["divorced"])
    }

    func testFamilyVisionHoldsOneAnswerAtMost() {
        var picked = toggleLifePick(.familyAhead, current: [], tapped: "want_kids")
        picked = toggleLifePick(.familyAhead, current: picked, tapped: "dont_want_kids")
        XCTAssertEqual(picked, ["dont_want_kids"])
        XCTAssertEqual(toggleLifePick(.familyAhead, current: ["find_out_together"], tapped: "find_out_together"), [])
    }

    func testFindOutTogetherStandsAloneAndTheRestCombine() {
        let both: Set<String> = ["monogamous_relationship", "open_relationship"]
        XCTAssertEqual(toggleLifePick(.partnershipAhead, current: ["monogamous_relationship"], tapped: "open_relationship"), both)
        XCTAssertEqual(toggleLifePick(.partnershipAhead, current: both, tapped: "find_out_together"), ["find_out_together"])
        XCTAssertEqual(toggleLifePick(.livingAhead, current: ["find_out_together"], tapped: "expat_life"), ["expat_life"])
    }

    func testAnswersSaveAndReportInListOrderCommaJoined() {
        XCTAssertEqual(lifeInListOrder(.partnership, ["divorced", "single"]), ["single", "divorced"])
        XCTAssertEqual(lifeBucketed(.partnership, ["divorced", "single"]), "single,divorced")
        XCTAssertEqual(lifeBucketed(.livingAhead, ["move_abroad", "stay_local"]), "stay_local,move_abroad")
    }

    func testArrivalResetsOnlyTheStepsOwnQuestions() {
        let current = LifeDraft(answers: ["partnership": ["single"], "family_ahead": ["want_kids"]])
        var saved = SavedDetails()
        saved.life = ["family_ahead": ["dont_want_kids"], "living_ahead": ["mars"]]
        let draft = lifeOnArrival(.lifeAhead, current: current, saved: saved)
        XCTAssertEqual(draft.picked(.partnership), ["single"])
        XCTAssertEqual(draft.picked(.familyAhead), ["dont_want_kids"])
        XCTAssertTrue(draft.picked(.livingAhead).isEmpty)
    }

    func testASavedSetThatBreaksItsExclusiveRuleArrivesAsOneTheScreenCouldDraw() {
        var saved = SavedDetails()
        saved.life = ["family": ["single_parent", "no_children"]]
        XCTAssertEqual(lifeOnArrival(.lifeNow, current: LifeDraft(), saved: saved).picked(.family), ["no_children"])
    }
}
