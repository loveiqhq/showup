//
//  DetailsModelTests.swift
//  ShowUp · Embrace 2 and the seven detail steps, with no device and no network
//  (SHOWUP-166 to SHOWUP-173)
//
//  The Swift half of `ProfileDetailsViewModelTest.kt`. What it proves: which events fire and in
//  what order, that an accepted Continue is ONE request carrying the answer, its visibility and the
//  position, that a skip writes no answer, that `hidden_fields` is never sent on a guess, that a
//  failed save keeps the user where they are, and that a scene restore neither loses a pick nor
//  reports a second showing.
//
//  What it cannot: that the request reaches a server. `FakeStore` stands in for the repository.
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

/// A store that remembers every body, can be made to fail either call, and can hold a read open.
private actor FakeStore: ProfileDetailsStoring {
    private(set) var held = SavedDetails(hiddenFields: ["age"])
    private(set) var bodies: [Components.Schemas.UpsertProfileDto] = []
    private(set) var loadCalls = 0
    private var loads = true
    private var saves = true
    private var gated = false
    private var gate: CheckedContinuation<Void, Never>?

    func setHeld(_ value: SavedDetails) { held = value }
    func setLoads(_ value: Bool) { loads = value }
    func setSaves(_ value: Bool) { saves = value }
    func setGated(_ value: Bool) { gated = value }

    /// Lets a held read finish. Safe to call when nothing is waiting.
    func release() {
        let waiting = gate
        gate = nil
        waiting?.resume()
    }

    func load() async -> SavedDetails? {
        loadCalls += 1
        if gated { await withCheckedContinuation { gate = $0 } }
        return loads ? held : nil
    }

    func save(_ body: Components.Schemas.UpsertProfileDto) async -> SavedDetails? {
        bodies.append(body)
        guard saves else { return nil }
        if let cm = body.heightCm { held.heightCm = cm }
        if let gender = body.gender { held.gender = gender.value1.rawValue }
        if let orientation = body.orientation { held.orientation = orientation.value1.rawValue }
        if let languages = body.datingLanguages { held.datingLanguages = languages.map(\.rawValue) }
        if let education = body.education { held.education = education.value1.rawValue }
        if let religion = body.religion { held.religion = religion.value1.rawValue }
        if let politics = body.politics { held.politics = politics.value1.rawValue }
        if let hidden = body.hiddenFields { held.hiddenFields = Set(hidden) }
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

/// What a scene would keep across a restore.
@MainActor
private final class SceneBox {
    var text = ""
}

@MainActor
final class DetailsModelTests: XCTestCase {

    private var store = FakeStore()
    private var positions = FakePositions()
    private var analytics = Recorder()
    private var clock: Double = 1_000
    private var model: DetailsModel!

    override func setUp() {
        super.setUp()
        store = FakeStore()
        positions = FakePositions()
        analytics = Recorder()
        clock = 1_000
        model = build()
    }

    private func build() -> DetailsModel {
        DetailsModel(store: store, positions: positions, analytics: analytics, now: { [unowned self] in self.clock })
    }

    /// Lets the model's own tasks — the account read, a position report — run to the end.
    private func settle() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
    }

    private func arrive(_ step: DetailStep, referrer: ProfileScreen? = nil) async {
        model.arrived(step, referrer: referrer)
        await settle()
    }

    // MARK: - Embrace 2

    func testEmbrace2FiresScreenViewedAndEmbraceBridgeViewedAddDetailsOnAPush() {
        model.embraceArrived(referrer: .location, pop: false)
        XCTAssertEqual(analytics.names, ["screen_viewed", "embrace_bridge_viewed"])
        let view = analytics.of("screen_viewed")[0]
        XCTAssertEqual(view["screen_id"] as? String, "profile_embrace_details")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "profile_location")
        XCTAssertEqual(analytics.of("embrace_bridge_viewed")[0]["variant"] as? String, "add_details")
    }

    func testABackPopOntoEmbrace2FiresScreenViewedThereAndNothingElse() {
        model.embraceArrived(referrer: .height, pop: true)
        XCTAssertEqual(analytics.names, ["screen_viewed"])
    }

    func testEmbrace2FiresNoStepEventAndItsContinueRestatesLocation() async {
        model.embraceArrived(referrer: .location, pop: false)
        var advanced = false
        model.embraceContinue { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        XCTAssertFalse(analytics.names.contains { $0.hasPrefix("profile_step") })
        XCTAssertEqual(analytics.events.count, 2)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testEmbrace2StartsTheReadSoHeightCanPreFill() async {
        model.embraceArrived(referrer: .location, pop: false)
        await settle()
        let calls = await store.loadCalls
        XCTAssertEqual(calls, 1)
    }

    // MARK: - arrival

    func testAStepFiresScreenViewedWithItsReferrerThenProfileStepViewed() async {
        await arrive(.height, referrer: .embraceDetails)
        XCTAssertEqual(analytics.names, ["screen_viewed", "profile_step_viewed"])
        let view = analytics.of("screen_viewed")[0]
        XCTAssertEqual(view["screen_id"] as? String, "profile_height")
        XCTAssertEqual(view["screen_name"] as? String, "ProfileHeight")
        XCTAssertEqual(view["referrer_screen_id"] as? String, "profile_embrace_details")
        let step = analytics.of("profile_step_viewed")[0]
        XCTAssertEqual(step["step_id"] as? String, "height")
        XCTAssertEqual(step["step_index"] as? Int, 1)
    }

    func testARedrawOfTheSameStepIsNotASecondArrival() async {
        await arrive(.gender)
        model.optionTapped(.gender, "man")
        await arrive(.gender)
        XCTAssertEqual(analytics.of("screen_viewed").count, 1)
        // And the choice survives -- a dark-mode switch must not throw it away.
        XCTAssertEqual(model.state.draft.gender, "man")
    }

    func testAResumePreFillsTheSavedValueAndVisibilityWithNoToast() async {
        await store.setHeld(SavedDetails(heightCm: 181, hiddenFields: ["height"]))
        await arrive(.height)
        XCTAssertEqual(model.state.draft.heightText, "181")
        XCTAssertTrue(model.state.draft.isHidden(.height))
        XCTAssertNil(model.state.toast)
    }

    func testASlowReadLandsOnAnUntouchedStepAndNeverOverAChoiceAlreadyMade() async {
        await store.setHeld(SavedDetails(gender: "woman"))
        model.arrived(.gender, referrer: nil)
        // The user picks before the read finishes.
        model.optionTapped(.gender, "other")
        await settle()
        XCTAssertEqual(model.state.draft.gender, "other")
    }

    func testBackDiscardsAnUnsavedSelection() async {
        await arrive(.religion)
        model.optionTapped(.religion, "hindu")
        var back = false
        model.backPressed(.religion) { back = true }
        XCTAssertTrue(back)
        await arrive(.religion)
        XCTAssertNil(model.state.draft.religion)
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    // MARK: - height

    func testAnEmptyContinueOnHeightIsRefusedWithRequiredMissingNoSaveNoNavigation() async {
        await arrive(.height)
        analytics.clear()
        var advanced = false
        await model.continuePressed(.height) { advanced = true }
        XCTAssertFalse(advanced)
        XCTAssertEqual(model.state.toast, .refusal)
        XCTAssertEqual(analytics.names, ["form_validation_failed"])
        let failed = analytics.of("form_validation_failed")[0]
        XCTAssertEqual(failed["field_id"] as? String, "height")
        XCTAssertEqual(failed["rule"] as? String, "required_missing")
        XCTAssertEqual(failed["screen_id"] as? String, "profile_height")
        XCTAssertEqual(failed["step_id"] as? String, "height")
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    func testOutOfRangeIsImpossible() async {
        await arrive(.height)
        model.heightChanged("99")
        await model.continuePressed(.height) {}
        XCTAssertEqual(analytics.of("form_validation_failed").last?["rule"] as? String, "impossible")
    }

    func testASecondRefusalRestartsTheToastAndAValidValueHidesIt() async {
        await arrive(.height)
        await model.continuePressed(.height) {}
        let first = model.state.toastTick
        await model.continuePressed(.height) {}
        XCTAssertEqual(model.state.toastTick, first + 1)
        model.heightChanged("175")
        XCTAssertNil(model.state.toast)
    }

    func testTypingIsFilteredToThreeDigits() async {
        await arrive(.height)
        model.heightChanged("1a8b05")
        XCTAssertEqual(model.state.draft.heightText, "180")
    }

    func testAValidContinueSavesHeightVisibilityAndPositionInOneRequestThenReportsThenNavigates() async {
        await arrive(.height)
        model.heightChanged("181")
        model.visibilityToggled(.height)
        analytics.clear()
        clock += 12
        var advanced = false
        await model.continuePressed(.height) { advanced = true }
        XCTAssertTrue(advanced)
        let bodies = await store.bodies
        XCTAssertEqual(bodies.count, 1)
        XCTAssertEqual(bodies[0].heightCm, 181)
        XCTAssertEqual(bodies[0].hiddenFields, ["age", "height"])
        XCTAssertEqual(bodies[0].flowPosition?.value1, .height)
        XCTAssertEqual(analytics.names, ["detail_answered", "profile_step_completed"])
        let answered = analytics.of("detail_answered")[0]
        XCTAssertEqual(answered["field_id"] as? String, "height")
        XCTAssertEqual(answered["value_bucketed"] as? String, "180_184")
        XCTAssertEqual(analytics.of("profile_step_completed")[0]["time_on_step_s"] as? Int, 12)
        // The position travelled in the body; there is no second request for it.
        let reported = await positions.reported
        XCTAssertTrue(reported.isEmpty)
    }

    func testSkipWritesNoHeightEvenWithAValidValueTyped() async {
        await store.setHeld(SavedDetails(heightCm: 170))
        await arrive(.height)
        model.heightChanged("181")
        analytics.clear()
        var advanced = false
        model.skipPressed(.height) { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
        XCTAssertEqual(analytics.names, ["detail_skipped", "profile_step_skipped"])
        let reported = await positions.reported
        XCTAssertEqual(reported, [.height])
        let held = await store.held
        XCTAssertEqual(held.heightCm, 170)
    }

    func testTheVisibilityBoxReportsOnTickOnly() async {
        await arrive(.height)
        analytics.clear()
        model.visibilityToggled(.height)
        model.visibilityToggled(.height)
        XCTAssertEqual(analytics.names, ["field_display_opted_out"])
        XCTAssertEqual(analytics.of("field_display_opted_out")[0]["field_id"] as? String, "height")
    }

    func testTypingDuringTheSaveCannotChangeTheAnswerThatWasAccepted() async {
        // The account read is still to happen when Continue is pressed, so the save waits on it --
        // and height's field still takes typing meanwhile.
        await store.setLoads(false)
        await arrive(.height)
        await store.setLoads(true)
        await store.setGated(true)
        model.heightChanged("175")
        var advanced = false
        let press = Task { await model.continuePressed(.height) { advanced = true } }
        while await store.loadCalls < 2 { await Task.yield() }
        model.heightChanged("")
        await store.release()
        await press.value
        XCTAssertTrue(advanced)
        let bodies = await store.bodies
        XCTAssertEqual(bodies.first?.heightCm, 175)
        XCTAssertEqual(analytics.of("detail_answered").first?["value_bucketed"] as? String, "175_179")
    }

    // MARK: - gender and orientation

    func testGenderRefusesAnEmptyContinueAndHasNoSkipAtAll() async {
        await arrive(.gender)
        var advanced = false
        await model.continuePressed(.gender) { advanced = true }
        model.skipPressed(.gender) { advanced = true }
        XCTAssertFalse(advanced)
        XCTAssertEqual(analytics.of("form_validation_failed").count, 1)
        XCTAssertTrue(analytics.of("detail_skipped").isEmpty)
    }

    func testTappingTheSelectedGenderKeepsItAndPickingDoesNotNavigate() async {
        await arrive(.gender)
        model.optionTapped(.gender, "woman")
        model.optionTapped(.gender, "woman")
        XCTAssertEqual(model.state.draft.gender, "woman")
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    func testGenderSavesTheSection1ValueClass2WithItsVisibility() async {
        await arrive(.gender)
        model.optionTapped(.gender, "non_binary")
        model.visibilityToggled(.gender)
        await model.continuePressed(.gender) {}
        let bodies = await store.bodies
        XCTAssertEqual(bodies.first?.gender?.value1, .non_binary)
        XCTAssertEqual(bodies.first?.hiddenFields, ["age", "gender"])
        let answered = analytics.of("detail_answered")[0]
        XCTAssertEqual(answered["value_bucketed"] as? String, "non_binary")
        XCTAssertEqual(answered["sensitivity_class"] as? Int, 2)
    }

    func testAPickHidesTheRefusalToast() async {
        await arrive(.orientation)
        await model.continuePressed(.orientation) {}
        XCTAssertEqual(model.state.toast, .refusal)
        model.optionTapped(.orientation, "gay")
        XCTAssertNil(model.state.toast)
    }

    // MARK: - the skippable lists

    func testAnEmptyContinueOnDatingLanguageIsASkipNeverARefusal() async {
        await arrive(.datingLanguage)
        analytics.clear()
        var advanced = false
        await model.continuePressed(.datingLanguage) { advanced = true }
        await settle()
        XCTAssertTrue(advanced)
        XCTAssertEqual(analytics.names, ["detail_skipped", "profile_step_skipped"])
        let reported = await positions.reported
        XCTAssertEqual(reported, [.dating_language])
    }

    func testLanguagesSaveAndReportInListOrderCommaJoinedOnce() async {
        await arrive(.datingLanguage)
        model.languageTapped("arabic")
        model.languageTapped("german")
        model.languageTapped("spanish")
        await model.continuePressed(.datingLanguage) {}
        let bodies = await store.bodies
        XCTAssertEqual(bodies.first?.datingLanguages, [.german, .spanish, .arabic])
        XCTAssertEqual(analytics.of("detail_answered").count, 1)
        XCTAssertEqual(analytics.of("detail_answered")[0]["value_bucketed"] as? String, "german,spanish,arabic")
    }

    func testEducationClearsOnASecondTapAndTheClearSendsNothing() async {
        await arrive(.education)
        model.optionTapped(.education, "phd")
        model.optionTapped(.education, "phd")
        XCTAssertNil(model.state.draft.education)
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
    }

    func testSelectingThenClearingThenContinueIsASkipThatKeepsTheEarlierValue() async {
        await store.setHeld(SavedDetails(religion: "catholic"))
        await arrive(.religion)
        model.optionTapped(.religion, "catholic")
        await model.continuePressed(.religion) {}
        await settle()
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
        let held = await store.held
        XCTAssertEqual(held.religion, "catholic")
    }

    func testPoliticsAnswersWithTheSection1ValueAndClass2() async {
        await arrive(.politics)
        model.optionTapped(.politics, "mid_left")
        await model.continuePressed(.politics) {}
        let bodies = await store.bodies
        XCTAssertEqual(bodies.first?.politics?.value1, .mid_left)
        XCTAssertEqual(bodies.first?.flowPosition?.value1, .politics)
        XCTAssertEqual(analytics.of("detail_answered")[0]["sensitivity_class"] as? Int, 2)
    }

    // MARK: - failure

    func testAFailedSaveKeepsTheUserOnTheStepWithTheAnswerSaysSoAndReportsNothing() async {
        await arrive(.education)
        model.optionTapped(.education, "phd")
        await store.setSaves(false)
        analytics.clear()
        var advanced = false
        await model.continuePressed(.education) { advanced = true }
        XCTAssertFalse(advanced)
        XCTAssertEqual(model.state.toast, .saveFailed)
        XCTAssertEqual(model.state.draft.education, "phd")
        XCTAssertFalse(model.state.saving)
        XCTAssertTrue(analytics.events.isEmpty)
    }

    func testHiddenFieldsIsNeverSentOnAGuessNoReadNoSave() async {
        await store.setLoads(false)
        await arrive(.height)
        model.heightChanged("181")
        var advanced = false
        await model.continuePressed(.height) { advanced = true }
        XCTAssertFalse(advanced)
        let bodies = await store.bodies
        XCTAssertTrue(bodies.isEmpty)
        XCTAssertEqual(model.state.toast, .saveFailed)
    }

    func testContinueCannotBePressedTwiceWhileASaveIsInFlight() async {
        await store.setLoads(false)
        await arrive(.gender)
        await store.setLoads(true)
        await store.setGated(true)
        model.optionTapped(.gender, "man")
        let press = Task { await model.continuePressed(.gender) {} }
        while await store.loadCalls < 2 { await Task.yield() }
        await model.continuePressed(.gender) {}
        model.optionTapped(.gender, "woman")
        await store.release()
        await press.value
        let bodies = await store.bodies
        XCTAssertEqual(bodies.count, 1)
        XCTAssertEqual(bodies.first?.gender?.value1, .man)
    }

    // MARK: - a scene restore

    /// A new model attached to the SAME stored text is what a scene restore builds: the storage
    /// comes back, everything else starts again — including the account read.
    private func restored(_ box: SceneBox) -> DetailsModel {
        let next = build()
        next.attach(stored: box.text) { box.text = $0 }
        return next
    }

    func testAfterARestoreTheReadDoesNotLandOnTopOfWhatWasPicked() async {
        let box = SceneBox()
        model = restored(box)
        await store.setHeld(SavedDetails(religion: "catholic"))
        await arrive(.religion)
        model.optionTapped(.religion, "hindu")

        model = restored(box)
        await arrive(.religion)
        XCTAssertEqual(model.state.draft.religion, "hindu")
    }

    func testAfterARestoreAnUntouchedStepStillTakesTheLateRead() async {
        let box = SceneBox()
        model = restored(box)
        await store.setLoads(false)
        await arrive(.religion)

        await store.setLoads(true)
        await store.setHeld(SavedDetails(religion: "catholic"))
        model = restored(box)
        await arrive(.religion)
        XCTAssertEqual(model.state.draft.religion, "catholic")
    }

    func testARestoreOntoAStepOrTheBridgeIsNotASecondShowingOfIt() async {
        let box = SceneBox()
        model = restored(box)
        model.embraceArrived(referrer: .location, pop: false)
        model = restored(box)
        model.embraceArrived(referrer: .location, pop: false)
        XCTAssertEqual(analytics.of("embrace_bridge_viewed").count, 1)

        model.embraceContinue {}
        await arrive(.height)
        model = restored(box)
        await arrive(.height)
        XCTAssertEqual(analytics.of("profile_step_viewed").count, 1)
    }

    func testAValueStoredBeforeTheFlagsExistedStillRestoresTheDraft() async {
        let box = SceneBox()
        box.text = #"{"step":"religion","draft":{"heightText":"","languages":[],"hidden":[],"religion":"hindu"}}"#
        model = restored(box)
        XCTAssertEqual(model.state.draft.religion, "hindu")
    }

    // MARK: - the account copy is never stale where it matters

    func testEverySaveReadsTheAccountFreshSoAFieldHiddenSinceTheFirstReadStaysHidden() async {
        // The read taken on arrival knows no hidden fields; the age is hidden before Continue (on
        // the date-of-birth step, or on another device). The PATCH must not send it back.
        await store.setHeld(SavedDetails())
        await arrive(.height)
        await store.setHeld(SavedDetails(hiddenFields: ["age"]))
        model.heightChanged("175")
        await model.continuePressed(.height) {}
        let bodies = await store.bodies
        XCTAssertEqual(bodies.first?.hiddenFields, ["age"])
    }

    func testLeavingAStepPutsItsDraftBackToTheSavedValueAtOnce() async {
        await store.setHeld(SavedDetails(religion: "catholic"))
        await arrive(.religion)
        model.optionTapped(.religion, "hindu")
        model.backPressed(.religion) {}
        XCTAssertEqual(model.state.draft.religion, "catholic")
    }

    func testTheFirstReadFillsTheStepsThatAreNotOnScreenToo() async {
        await store.setHeld(SavedDetails(politics: "middle"))
        model.embraceArrived(referrer: .location, pop: false)
        await settle()
        XCTAssertEqual(model.state.draft.politics, "middle")
    }

    // MARK: - the outgoing screen during its exit transition

    func testTapsOnAStepThatIsNoLongerOnScreenDoNothing() async {
        await arrive(.education)
        var advances = 0
        model.skipPressed(.education) { advances += 1 }
        model.skipPressed(.education) { advances += 1 }
        await model.continuePressed(.education) { advances += 1 }
        model.backPressed(.education) { advances += 1 }
        await settle()
        XCTAssertEqual(advances, 1)
        XCTAssertEqual(analytics.of("detail_skipped").count, 1)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.education])
    }

    func testInputsOnAStepThatIsNoLongerOnScreenDoNothing() async {
        await arrive(.height)
        model.heightChanged("181")
        model.backPressed(.height) {}
        await arrive(.gender)
        analytics.clear()
        model.heightChanged("170")
        model.visibilityToggled(.height)
        model.optionTapped(.religion, "hindu")
        model.languageTapped("german")
        XCTAssertEqual(model.state.draft.heightText, "")
        XCTAssertNil(model.state.draft.religion)
        XCTAssertTrue(model.state.draft.languages.isEmpty)
        XCTAssertTrue(analytics.events.isEmpty)
    }

    func testASecondTapOnEmbrace2DoesNotNavigateAgain() async {
        model.embraceArrived(referrer: .location, pop: false)
        var advances = 0
        model.embraceContinue { advances += 1 }
        model.embraceContinue { advances += 1 }
        await settle()
        XCTAssertEqual(advances, 1)
        let reported = await positions.reported
        XCTAssertEqual(reported, [.location])
    }

    func testTheStepTimerSurvivesASceneRestore() async {
        let box = SceneBox()
        model = restored(box)
        await arrive(.gender)
        model.optionTapped(.gender, "man")
        clock += 12
        model = restored(box)
        await arrive(.gender)
        await model.continuePressed(.gender) {}
        XCTAssertEqual(analytics.of("profile_step_completed").first?["time_on_step_s"] as? Int, 12)
    }

    // MARK: - the registry

    func testNoEventNameOutsideTheRegistryEverLeavesThisModel() async {
        model.embraceArrived(referrer: .location, pop: false)
        model.embraceContinue {}
        for step in DetailStep.allCases {
            await arrive(step)
            model.visibilityToggled(step)
            await model.continuePressed(step) {}
            // Back on the step: an empty Continue on the four skippable lists has just LEFT it, and
            // input on a step that is not on screen does nothing -- so every step answers here.
            await arrive(step)
            if let option = step.options.first {
                if step.multiSelect { model.languageTapped(option.value) } else { model.optionTapped(step, option.value) }
            } else {
                model.heightChanged("175")
            }
            await model.continuePressed(step) {}
        }
        let allowed: Set<String> = [
            "screen_viewed", "embrace_bridge_viewed", "profile_step_viewed", "profile_step_completed",
            "profile_step_skipped", "detail_answered", "detail_skipped", "field_display_opted_out",
            "form_validation_failed",
        ]
        XCTAssertTrue(Set(analytics.names).isSubset(of: allowed), "\(Set(analytics.names).subtracting(allowed))")
        // An answer is stamped with ITS FIELD's class, so a class 2 value can never travel as class 0.
        for name in ["detail_answered", "detail_skipped"] {
            for properties in analytics.of(name) {
                let field = properties["field_id"] as? String ?? ""
                let step = DetailStep(rawValue: field)
                XCTAssertNotNil(step, "\(name) for an unknown field \(field)")
                XCTAssertEqual(properties["sensitivity_class"] as? Int, step?.sensitivityClass, "\(name) / \(field)")
                XCTAssertEqual(properties["field_registry_version"] as? String, "1.4.15")
            }
        }
    }
}
