//
//  PickModelTests.swift
//  ShowUp · Interests, Life and Future, with no device and no network (SHOWUP-174 to SHOWUP-176)
//
//  The Swift half of `PickStepsViewModelTest.kt`: every tracking row and its order, the events that
//  must NOT fire, what Continue saves and what Skip does not, and the arrival rules -- and that no
//  Continue ever saves over answers the screen has not shown (a slow or failed first read).
//

import ShowUpAPI
import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    var names: [String] { events.map(\.0) }
    func of(_ name: String) -> [[String: any Sendable]] { events.filter { $0.0 == name }.map(\.1) }
    func clear() { events.removeAll() }
}

/// A store that remembers every body and applies it the way the server does, and can fail.
private actor FakeStore: ProfileDetailsStoring {
    private(set) var held = SavedDetails()
    private(set) var bodies: [Components.Schemas.UpsertProfileDto] = []
    private var saves = true
    private var reads = true
    /// Nanoseconds the NEXT read takes, once.
    private var nextReadDelay: UInt64 = 0
    /// Nanoseconds the NEXT save takes, once.
    private var nextSaveDelay: UInt64 = 0

    func setHeld(_ value: SavedDetails) { held = value }
    func setSaves(_ value: Bool) { saves = value }
    func setReads(_ value: Bool) { reads = value }
    func slowNextRead(_ nanoseconds: UInt64) { nextReadDelay = nanoseconds }
    func slowNextSave(_ nanoseconds: UInt64) { nextSaveDelay = nanoseconds }

    func load() async -> SavedDetails? {
        let snapshot = held
        let ok = reads
        let wait = nextReadDelay
        nextReadDelay = 0
        if wait > 0 { try? await Task.sleep(nanoseconds: wait) }
        return ok ? snapshot : nil
    }

    func save(_ body: Components.Schemas.UpsertProfileDto) async -> SavedDetails? {
        bodies.append(body)
        let wait = nextSaveDelay
        nextSaveDelay = 0
        if wait > 0 { try? await Task.sleep(nanoseconds: wait) }
        guard saves else { return nil }
        if let v = body.activity { held.lifestyle["activity"] = v.value1.rawValue }
        if let v = body.diet { held.lifestyle["diet"] = v.value1.rawValue }
        if let v = body.alcohol { held.lifestyle["alcohol"] = v.value1.rawValue }
        if let v = body.smoking { held.lifestyle["smoking"] = v.value1.rawValue }
        if let v = body.rhythm { held.lifestyle["rhythm"] = v.value1.rawValue }
        if let v = body.social { held.lifestyle["social"] = v.value1.rawValue }
        if let v = body.interests { held.interests = v.map(\.rawValue) }
        if let v = body.partnership { held.life["partnership"] = v.map(\.rawValue) }
        if let v = body.family { held.life["family"] = v.map(\.rawValue) }
        if let v = body.living { held.life["living"] = v.map(\.rawValue) }
        if let v = body.familyAhead { held.life["family_ahead"] = v.map(\.rawValue) }
        if let v = body.partnershipAhead { held.life["partnership_ahead"] = v.map(\.rawValue) }
        if let v = body.livingAhead { held.life["living_ahead"] = v.map(\.rawValue) }
        return held
    }
}

private actor FakePositions: FlowPositionReporting {
    private(set) var reported: [Components.Schemas.FlowPosition] = []
    func report(_ position: Components.Schemas.FlowPosition) async -> Bool {
        reported.append(position)
        return true
    }
}

@MainActor
final class PickModelTests: XCTestCase {

    private var store = FakeStore()
    private var positions = FakePositions()
    private var analytics = Recorder()
    private var clock: Double = 1_000
    private var advancedAfterJump = false
    private var model: PickModel!

    override func setUp() {
        super.setUp()
        store = FakeStore()
        positions = FakePositions()
        analytics = Recorder()
        clock = 1_000
        model = build()
    }

    private func build() -> PickModel {
        PickModel(store: store, positions: positions, analytics: analytics, now: { [unowned self] in self.clock })
    }

    /// Lets the model's own tasks — the account read, a position report — run to the end.
    private func settle() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
    }

    private func arrive(_ step: PickStep, referrer: ProfileScreen? = nil) async {
        model.arrived(step, referrer: referrer)
        await settle()
    }

    // MARK: - arriving

    func testArrivingOnInterestsReportsTheScreenWithItsReferrerAndStep8() async {
        await arrive(.interests, referrer: .politics)
        XCTAssertEqual(analytics.names, ["screen_viewed", "profile_step_viewed"])
        let view = analytics.of("screen_viewed")[0]
        XCTAssertEqual(view["screen_id"] as? String, "profile_interests")
        XCTAssertEqual(view["screen_name"] as? String, "ProfileInterests")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "profile_politics")
        let step = analytics.of("profile_step_viewed")[0]
        XCTAssertEqual(step["step_id"] as? String, "interests")
        XCTAssertEqual(step["step_index"] as? Int, 8)
        XCTAssertEqual(step["field_registry_version"] as? String, "1.4.29")
    }

    func testLifeAndFutureReportSteps9And10() async {
        await arrive(.lifeNow, referrer: .interests)
        await arrive(.lifeAhead, referrer: .lifeNow)
        XCTAssertEqual(analytics.of("screen_viewed").map { $0["screen_id"] as? String },
                       ["profile_life_now", "profile_life_ahead"])
        XCTAssertEqual(analytics.of("screen_viewed").map { $0["referrer_screen_id"] as? String },
                       ["profile_interests", "profile_life_now"])
        XCTAssertEqual(analytics.of("profile_step_viewed").map { $0["step_index"] as? Int }, [9, 10])
    }

    func testARedrawOfTheStepOnScreenIsNotASecondArrival() async {
        await arrive(.interests)
        model.lifestyleTapped(.diet, "vegan")
        await arrive(.interests)
        XCTAssertEqual(analytics.of("screen_viewed").count, 1)
        XCTAssertEqual(model.state.interests.lifestyle, ["diet": "vegan"])
    }

    func testAResumePreFillsTheSavedAnswersEveryGroupCollapsed() async {
        var held = SavedDetails()
        held.lifestyle = ["rhythm": "night_owl"]
        held.interests = ["wine", "gym"]
        await store.setHeld(held)
        await arrive(.interests)
        XCTAssertEqual(model.state.interests.lifestyle, ["rhythm": "night_owl"])
        XCTAssertEqual(model.state.interests.interests, ["wine", "gym"])
        XCTAssertTrue(model.state.interests.expanded.isEmpty)
    }

    // MARK: - Interests

    func testWhatImIntoReportsEveryTapWithTheRunningTotal() async {
        await arrive(.interests)
        analytics.clear()
        model.interestTapped("gym")
        model.interestTapped("wine")
        model.interestTapped("gym")
        XCTAssertEqual(analytics.names, ["interest_selected", "interest_selected", "interest_deselected"])
        XCTAssertEqual(analytics.events.map { $0.1["total_selected"] as? Int }, [1, 2, 1])
        XCTAssertEqual(analytics.events.map { $0.1["tag_id"] as? String }, ["gym", "wine", "gym"])
    }

    func testHowILiveReportsNothingPerTap() async {
        await arrive(.interests)
        analytics.clear()
        model.lifestyleTapped(.activity, "active")
        model.lifestyleTapped(.activity, "active")
        XCTAssertTrue(analytics.names.isEmpty)
        XCTAssertTrue(model.state.interests.lifestyle.isEmpty)
    }

    func testAtTenAFadedTapChangesNothingAndReportsTheCapEveryTime() async {
        await arrive(.interests)
        for tag in interestOrder.prefix(10) { model.interestTapped(tag) }
        analytics.clear()
        model.interestTapped(interestOrder[20])
        model.interestTapped(interestOrder[30])
        XCTAssertEqual(analytics.names, ["interest_cap_reached", "interest_cap_reached"])
        XCTAssertEqual(analytics.events[0].1["cap"] as? Int, 10)
        XCTAssertEqual(model.state.interests.interests, Set(interestOrder.prefix(10)))
    }

    func testShowMoreReportsOnExpandOnly() async {
        await arrive(.interests)
        analytics.clear()
        model.showMoreTapped(.food)
        model.showMoreTapped(.food)
        XCTAssertEqual(analytics.names, ["show_more_expanded"])
        let expanded = analytics.events[0].1
        XCTAssertEqual(expanded["screen_id"] as? String, "profile_interests")
        XCTAssertEqual(expanded["section_id"] as? String, "food")
        XCTAssertEqual(expanded["revealed_count"] as? Int, 5)
    }

    func testContinueSavesTheAnsweredGroupsAndTheTagsInListOrderThenReports() async {
        await arrive(.interests)
        model.lifestyleTapped(.social, "balanced")
        model.lifestyleTapped(.smoking, "no")
        model.interestTapped("cruises")
        model.interestTapped("gym")
        analytics.clear()
        clock += 12.4
        var advanced = false
        await model.continuePressed(.interests) { advanced = true }
        XCTAssertTrue(advanced)
        let body = await store.bodies[0]
        XCTAssertEqual(body.flowPosition?.value1, .interests)
        XCTAssertEqual(body.smoking?.value1, .no)
        XCTAssertEqual(body.social?.value1, .balanced)
        XCTAssertNil(body.activity)
        XCTAssertEqual(body.interests?.map(\.rawValue), ["gym", "cruises"])
        XCTAssertEqual(analytics.names, ["detail_answered", "detail_answered", "profile_step_completed"])
        XCTAssertEqual(analytics.of("detail_answered").map { $0["field_id"] as? String }, ["smoking", "social"])
        XCTAssertEqual(analytics.of("detail_answered").map { $0["sensitivity_class"] as? Int }, [2, 0])
        XCTAssertEqual(analytics.of("profile_step_completed")[0]["time_on_step_s"] as? Int, 12)
    }

    func testALifestyleAnswerAloneSendsNoInterestList() async {
        await arrive(.interests)
        model.lifestyleTapped(.diet, "vegan")
        await model.continuePressed(.interests) {}
        let body = await store.bodies[0]
        XCTAssertNil(body.interests)
    }

    func testContinueWithNothingPickedIsExactlySkip() async {
        await arrive(.interests)
        model.showMoreTapped(.move)
        analytics.clear()
        var advanced = false
        await model.continuePressed(.interests) { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
        XCTAssertEqual(analytics.names, ["profile_step_skipped"])
        XCTAssertEqual(analytics.events[0].1["screen_id"] as? String, "profile_interests")
        let reported = await positions.reported
        XCTAssertEqual(reported, [.interests])
    }

    func testSkipNeverFiresTheEventsThatMustNotFire() async {
        await arrive(.interests)
        model.lifestyleTapped(.diet, "vegan")
        analytics.clear()
        model.skipPressed(.interests) {}
        await settle()
        for never in ["detail_skipped", "form_validation_failed", "field_display_opted_out", "habit_answered"] {
            XCTAssertTrue(analytics.of(never).isEmpty, never)
        }
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    func testBackDiscardsUnsavedPicks() async {
        var held = SavedDetails()
        held.interests = ["wine"]
        await store.setHeld(held)
        await arrive(.interests)
        model.interestTapped("gym")
        model.showMoreTapped(.move)
        model.backPressed(.interests) {}
        XCTAssertEqual(model.state.interests.interests, ["wine"])
        XCTAssertTrue(model.state.interests.expanded.isEmpty)
    }

    func testAFailedSaveKeepsTheStepAndItsPicksAndReportsNothing() async {
        await store.setSaves(false)
        await arrive(.interests)
        model.interestTapped("gym")
        analytics.clear()
        var advanced = false
        await model.continuePressed(.interests) { advanced = true }
        XCTAssertFalse(advanced)
        XCTAssertTrue(model.state.saveFailed)
        XCTAssertFalse(model.state.saving)
        XCTAssertEqual(model.state.interests.interests, ["gym"])
        XCTAssertTrue(analytics.names.isEmpty)
    }

    // MARK: - a slow or failed first read never costs a saved answer

    /// The read is slowed past `settle()`; this waits it out.
    private func waitOutSlowRead() async {
        try? await Task.sleep(nanoseconds: 400_000_000)
    }

    func testALateReadFillsEveryHowILiveGroupAndInterestTheUserHasNotTouched() async {
        await store.setHeld(SavedDetails(lifestyle: ["diet": "vegan", "rhythm": "night_owl"], interests: ["wine", "gym"]))
        await store.slowNextRead(200_000_000)
        model.arrived(.interests, referrer: nil)
        model.lifestyleTapped(.diet, "everything")
        model.interestTapped("cooking")
        await waitOutSlowRead()
        XCTAssertEqual(model.state.interests.lifestyle, ["diet": "everything", "rhythm": "night_owl"])
        XCTAssertEqual(model.state.interests.interests, ["cooking", "wine", "gym"])
        await model.continuePressed(.interests) {}
        let body = await store.bodies[0]
        XCTAssertEqual(body.interests?.map(\.rawValue), ["gym", "cooking", "wine"])
    }

    func testALateReadThatRunsTheListPastTenKeepsTheUsersOwnPicksFirst() async {
        await store.setHeld(SavedDetails(interests: Array(interestOrder.prefix(10))))
        await store.slowNextRead(200_000_000)
        model.arrived(.interests, referrer: nil)
        model.interestTapped(interestOrder[20])
        model.interestTapped(interestOrder[21])
        await waitOutSlowRead()
        XCTAssertEqual(model.state.interests.interests,
                       Set([interestOrder[20], interestOrder[21]] + interestOrder.prefix(8)))
    }

    func testALateReadLeavesAnUnpickedTagUnpickedAndOpenGroupsOpen() async {
        await store.setHeld(SavedDetails(interests: ["gym", "wine"]))
        await store.slowNextRead(200_000_000)
        model.arrived(.interests, referrer: nil)
        model.interestTapped("gym")
        model.interestTapped("gym")
        model.showMoreTapped(.culture)
        await waitOutSlowRead()
        XCTAssertEqual(model.state.interests.interests, ["wine"])
        XCTAssertEqual(model.state.interests.expanded, ["culture"])
    }

    func testALateReadOnAnUntouchedInterestsKeepsTheGroupsTheUserOpened() async {
        await store.setHeld(SavedDetails(interests: ["wine"]))
        await store.slowNextRead(200_000_000)
        model.arrived(.interests, referrer: nil)
        model.showMoreTapped(.food)
        await waitOutSlowRead()
        XCTAssertEqual(model.state.interests.interests, ["wine"])
        XCTAssertEqual(model.state.interests.expanded, ["food"])
    }

    func testALateReadOnLifeFillsTheUntouchedQuestionsSoContinueKeepsThem() async {
        await store.setHeld(SavedDetails(life: ["family": ["has_children"], "living": ["living_alone"]]))
        await store.slowNextRead(200_000_000)
        model.arrived(.lifeNow, referrer: nil)
        model.pillTapped(.lifeNow, .partnership, "single")
        await waitOutSlowRead()
        await model.continuePressed(.lifeNow) {}
        let body = await store.bodies[0]
        XCTAssertEqual(body.partnership?.map(\.rawValue), ["single"])
        XCTAssertEqual(body.family?.map(\.rawValue), ["has_children"])
        XCTAssertEqual(body.living?.map(\.rawValue), ["living_alone"])
    }

    func testContinueWithNoReadYetReadsFirstAndSavesWhatTheScreenWouldHaveShown() async {
        await store.setReads(false)
        await store.setHeld(SavedDetails(life: ["family": ["has_children"]]))
        await arrive(.lifeNow)
        model.pillTapped(.lifeNow, .partnership, "single")
        await store.setReads(true)
        var advanced = false
        await model.continuePressed(.lifeNow) { advanced = true }
        XCTAssertTrue(advanced)
        let body = await store.bodies[0]
        XCTAssertEqual(body.partnership?.map(\.rawValue), ["single"])
        XCTAssertEqual(body.family?.map(\.rawValue), ["has_children"])
    }

    func testAContinueWhoseFirstReadCutsASavedInterestStopsSoTheNextOneSavesWhatWasShown() async {
        await store.setReads(false)
        await store.setHeld(SavedDetails(interests: Array(interestOrder.prefix(10))))
        await arrive(.interests)
        model.interestTapped(interestOrder[20])
        await store.setReads(true)
        var advanced = false
        await model.continuePressed(.interests) { advanced = true }
        // Nothing saved: the tenth saved tag would have been deleted without ever being shown.
        XCTAssertFalse(advanced)
        let none = await store.bodies
        XCTAssertTrue(none.isEmpty)
        XCTAssertFalse(model.state.saving)
        XCTAssertFalse(model.state.saveFailed)
        let merged = Set([interestOrder[20]] + interestOrder.prefix(9))
        XCTAssertEqual(model.state.interests.interests, merged)
        // Now on screen, the merged ten save on the next Continue.
        await model.continuePressed(.interests) { advanced = true }
        XCTAssertTrue(advanced)
        let body = await store.bodies[0]
        XCTAssertEqual(body.interests?.map(\.rawValue), interestsInListOrder(merged))
    }

    // MARK: - values this build cannot draw, and a jump mid-save

    func testALifeQuestionHoldingAValueThisBuildCannotDrawIsNotSentUnlessTouched() async {
        await store.setHeld(SavedDetails(life: ["partnership": ["widowed"], "living": ["living_alone", "van_life"]]))
        await arrive(.lifeNow)
        model.pillTapped(.lifeNow, .family, "no_children")
        await model.continuePressed(.lifeNow) {}
        let body = await store.bodies[0]
        XCTAssertNil(body.partnership)
        XCTAssertNil(body.living)
        XCTAssertEqual(body.family?.map(\.rawValue), ["no_children"])
        let held = await store.held
        XCTAssertEqual(held.life["partnership"], ["widowed"])
        XCTAssertEqual(held.life["living"], ["living_alone", "van_life"])
    }

    func testAnUntouchedInterestListHoldingAnUnknownTagIsNotSentATouchedOneIs() async {
        await store.setHeld(SavedDetails(interests: ["gym", "skydiving"]))
        await arrive(.interests)
        model.lifestyleTapped(.rhythm, "early_bird")
        await model.continuePressed(.interests) {}
        let first = await store.bodies[0]
        XCTAssertNil(first.interests)
        // Touched, the user's list is their answer, as any answer is.
        await arrive(.interests)
        model.interestTapped("wine")
        await model.continuePressed(.interests) {}
        let second = await store.bodies[1]
        XCTAssertEqual(second.interests?.map(\.rawValue), ["gym", "wine"])
    }

    func testASaveThatLandsAfterADeveloperMenuJumpDoesNotMoveTheFlow() async {
        await arrive(.interests)
        model.interestTapped("gym")
        await store.slowNextSave(200_000_000)
        // A property, not a captured local: the press runs in its own Task.
        advancedAfterJump = false
        let pressing = Task { await self.model.continuePressed(.interests) { self.advancedAfterJump = true } }
        try? await Task.sleep(nanoseconds: 50_000_000)
        model.leftElsewhere()
        await pressing.value
        XCTAssertFalse(advancedAfterJump)
        XCTAssertFalse(model.state.saving)
        let held = await store.held
        XCTAssertEqual(held.interests, ["gym"])
    }

    func testContinueWhoseReadFailsIsAFailedSave() async {
        await store.setReads(false)
        await arrive(.interests)
        model.interestTapped("gym")
        analytics.clear()
        var advanced = false
        await model.continuePressed(.interests) { advanced = true }
        XCTAssertFalse(advanced)
        XCTAssertTrue(model.state.saveFailed)
        XCTAssertFalse(model.state.saving)
        XCTAssertEqual(model.state.interests.interests, ["gym"])
        XCTAssertTrue(analytics.names.isEmpty)
        let none = await store.bodies
        XCTAssertTrue(none.isEmpty)
        // Once the account can be read, the same Continue goes through.
        await store.setReads(true)
        await model.continuePressed(.interests) { advanced = true }
        XCTAssertTrue(advanced)
        let body = await store.bodies[0]
        XCTAssertEqual(body.interests?.map(\.rawValue), ["gym"])
    }

    // MARK: - Life and Future

    func testLifeSavesAllThreeAnUnansweredQuestionAsEmpty() async {
        await arrive(.lifeNow)
        model.pillTapped(.lifeNow, .partnership, "divorced")
        model.pillTapped(.lifeNow, .partnership, "single")
        model.pillTapped(.lifeNow, .living, "living_alone")
        analytics.clear()
        await model.continuePressed(.lifeNow) {}
        let body = await store.bodies[0]
        XCTAssertEqual(body.flowPosition?.value1, .life_now)
        XCTAssertEqual(body.partnership?.map(\.rawValue), ["single", "divorced"])
        XCTAssertEqual(body.family?.map(\.rawValue), [])
        XCTAssertEqual(body.living?.map(\.rawValue), ["living_alone"])
        XCTAssertNil(body.familyAhead)
        XCTAssertEqual(analytics.names, ["life_question_answered", "life_question_answered", "profile_step_completed"])
        XCTAssertEqual(analytics.of("life_question_answered").map { $0["value_bucketed"] as? String },
                       ["single,divorced", "living_alone"])
        XCTAssertEqual(analytics.of("life_question_answered").map { $0["sensitivity_class"] as? Int }, [2, 1])
    }

    func testFutureReportsAheadIdsAndMarksProfileCreationComplete() async {
        await arrive(.lifeAhead)
        model.pillTapped(.lifeAhead, .partnershipAhead, "find_out_together")
        model.pillTapped(.lifeAhead, .familyAhead, "want_kids")
        model.pillTapped(.lifeAhead, .familyAhead, "dont_want_kids")
        analytics.clear()
        await model.continuePressed(.lifeAhead) {}
        let body = await store.bodies[0]
        XCTAssertEqual(body.flowPosition?.value1, .life_ahead)
        XCTAssertEqual(body.familyAhead?.map(\.rawValue), ["dont_want_kids"])
        XCTAssertEqual(analytics.names, ["future_question_answered", "future_question_answered", "profile_step_completed"])
        XCTAssertEqual(analytics.of("future_question_answered").map { $0["question_id"] as? String },
                       ["family_ahead", "partnership_ahead"])
        XCTAssertTrue(analytics.of("life_question_answered").isEmpty)
    }

    func testFutureSendsOnlyTheQuestionsAnsweredSoASavedOneIsKept() async {
        await store.setHeld(SavedDetails(life: ["partnership_ahead": ["monogamous_relationship"]]))
        await arrive(.lifeAhead)
        model.pillTapped(.lifeAhead, .partnershipAhead, "monogamous_relationship")
        model.pillTapped(.lifeAhead, .familyAhead, "want_kids")
        await model.continuePressed(.lifeAhead) {}
        let body = await store.bodies[0]
        XCTAssertNil(body.partnershipAhead)
        XCTAssertNil(body.livingAhead)
        XCTAssertEqual(body.familyAhead?.map(\.rawValue), ["want_kids"])
        let held = await store.held
        XCTAssertEqual(held.life["partnership_ahead"], ["monogamous_relationship"])
    }

    func testFuturesSkipMarksProfileCreationCompleteAndSavesNothing() async {
        await arrive(.lifeAhead)
        analytics.clear()
        model.skipPressed(.lifeAhead) {}
        await settle()
        let reported = await positions.reported
        XCTAssertEqual(reported, [.life_ahead])
        XCTAssertEqual(analytics.names, ["profile_step_skipped"])
        XCTAssertEqual(analytics.events[0].1["step_id"] as? String, "life_ahead")
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    // MARK: - scene restore

    func testASceneRestoreKeepsTheStepItsPicksAndTheOpenGroups() async {
        var scene = ""
        model.attach(stored: scene) { scene = $0 }
        await arrive(.interests)
        model.lifestyleTapped(.rhythm, "early_bird")
        model.interestTapped("gym")
        model.showMoreTapped(.culture)
        let revived = build()
        revived.attach(stored: scene) { scene = $0 }
        analytics.clear()
        revived.arrived(.interests, referrer: nil)
        await settle()
        XCTAssertTrue(analytics.names.isEmpty)
        XCTAssertEqual(revived.state.interests.lifestyle, ["rhythm": "early_bird"])
        XCTAssertEqual(revived.state.interests.interests, ["gym"])
        XCTAssertEqual(revived.state.interests.expanded, ["culture"])
    }

    func testASceneRestoreKeepsWhichQuestionsWereTouchedSoTheNextReadFillsOnlyTheRest() async {
        await store.setHeld(SavedDetails(life: ["partnership": ["divorced"], "family": ["has_children"]]))
        var scene = ""
        model.attach(stored: scene) { scene = $0 }
        await arrive(.lifeNow)
        model.pillTapped(.lifeNow, .partnership, "divorced")
        // The revived model has never read the account; its first read must not undo the clear.
        let revived = build()
        revived.attach(stored: scene) { scene = $0 }
        revived.arrived(.lifeNow, referrer: nil)
        await settle()
        XCTAssertTrue(revived.state.life.picked(.partnership).isEmpty)
        XCTAssertEqual(revived.state.life.picked(.family), ["has_children"])
    }
}
