//
//  PromptsTrackingTests.swift
//  ShowUp · the fourteen rows this screen has to produce, and the four it must never (SHOWUP-158)
//
//  The Swift half of `PromptsTrackingTest.kt`. The same rules in the same order, because the thing
//  being protected is that the two platforms agree: a topic-demand chart fed by one platform that
//  counts edits and one that does not is worse than no chart, and nothing about that disagreement
//  is visible in either codebase on its own.
//
//  Registry 1.4.2 (16 September 2026) made three changes a reasonable implementation gets wrong by
//  default, and each has a test named after it:
//
//    1. `selection_index` COUNTS PER VISIT TO THE STEP, NOT PER SHEET.
//    2. `prompt_topic_selected` MUST NOT FIRE ON AN EDIT.
//    3. `dismiss_method` IS ONE CANONICAL SET — close, backdrop, swipe, system_back.
//

import XCTest
@testable import ShowUpWelcome

/// Saves whatever it is given, so the tracking rather than the transport is what is measured.
private struct FakePromptsRepo: PromptsRepositoring {
    let fail: Bool

    init(fail: Bool = false) { self.fail = fail }

    func list() async -> [SavedPrompt]? { nil }
    func save(topicId: String, answer: String) async -> SavePromptResult {
        fail ? .failed : .saved(SavedPrompt(topicId: topicId, answer: answer))
    }
    func remove(topicId: String) async -> RemovePromptResult { .removed }
}

/// Collects what the model reported, in order.
///
/// A plain class with no `Sendable` and no isolation annotation, deliberately: it is created on
/// the main actor, stored in a main-actor-isolated property of `PromptsModel`, and only ever
/// called from there, so it never crosses an isolation boundary. `@unchecked Sendable` would be
/// the lazy way to say that and is banned in this project for exactly the reason it would be
/// wrong here -- it asserts a guarantee instead of arranging one.
private final class Recorder: AnalyticsTracking {
    private(set) var events: [(String, [String: any Sendable])] = []
    func track(_ event: String, properties: [String: any Sendable]) {
        events.append((event, properties))
    }

    // `events.map { $0.0 }` rather than `events.map(\.0)`: Swift key paths do not address tuple
    // components, and the compiler only says so on a Mac.
    func names() -> [String] { events.map { $0.0 } }
    func count(_ name: String) -> Int { events.filter { $0.0 == name }.count }
    func all(_ name: String) -> [[String: any Sendable]] {
        events.filter { $0.0 == name }.map { $0.1 }
    }
    func only(_ name: String) -> [String: any Sendable] { all(name).first ?? [:] }
    func clear() { events.removeAll() }
}

@MainActor
final class PromptsTrackingTests: XCTestCase {

    private var analytics = Recorder()
    private var model: PromptsModel!
    /// A clock the test drives, so `time_on_*_s` is an assertion rather than a guess.
    private var clock: Double = 1_000_000

    override func setUp() {
        super.setUp()
        analytics = Recorder()
        clock = 1_000_000
        model = makeModel()
    }

    private func makeModel(fail: Bool = false) -> PromptsModel {
        // `clock` is read through the closure, so advancing it in a test advances the model's.
        PromptsModel(repo: FakePromptsRepo(fail: fail), analytics: analytics,
                     now: { [unowned self] in clock })
    }

    /// Types an answer and saves it, letting the request finish.
    private func write(_ answer: String) async {
        model.draftChanged(answer)
        model.save()
        await settle()
    }

    /// Lets the model's save task run to completion.
    ///
    /// There is no test dispatcher here as there is on Android, so the task is waited for rather
    /// than stepped. Bounded, so a broken rule fails rather than hangs.
    private func settle(file: StaticString = #filePath, line: UInt = #line) async {
        for _ in 0..<2_000 {
            if !model.state.saving { return }
            await Task.yield()
        }
        XCTFail("the save never settled", file: file, line: line)
    }

    private func int(_ payload: [String: any Sendable], _ key: String) -> Int? {
        payload[key] as? Int
    }

    private func string(_ payload: [String: any Sendable], _ key: String) -> String? {
        payload[key] as? String
    }

    // MARK: selection_index

    func testSelectionIndexIs1BasedAndCountsAcrossTheWholeVisitToTheStep() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.dismissSheet(.close)
        model.openTopics()
        model.pickTopic("hot_take", position: 14)

        let selections = analytics.all("prompt_topic_selected")
        XCTAssertEqual(selections.count, 2)
        XCTAssertEqual(int(selections[0], "selection_index"), 1)
        // NOT 1 AGAIN. The sheet closed in between, and the counter does not care: the registry
        // says it counts per visit to the STEP. Reset it per sheet and the answer to "which topic
        // did they reach for first" becomes an answer to a different question.
        XCTAssertEqual(int(selections[1], "selection_index"), 2)
    }

    func testAnEditNeitherIncrementsSelectionIndexNorFiresASelection() async {
        model.writeSuggestion("first_date_usually", position: 0)
        await write("An answer.")
        analytics.clear()

        model.editPrompt("first_date_usually")
        // THE registry change of 16 September 2026. An edit is not a fresh choice of topic.
        XCTAssertEqual(analytics.count("prompt_topic_selected"), 0)
        XCTAssertEqual(analytics.count("prompt_editor_opened"), 1)
        XCTAssertEqual(analytics.only("prompt_editor_opened")["is_edit"] as? Bool, true)
        XCTAssertEqual(string(analytics.only("prompt_editor_opened"), "entry_point"), "edit")

        model.dismissSheet(.close)
        model.writeSuggestion("weird_habit", position: 0)
        XCTAssertEqual(int(analytics.only("prompt_topic_selected"), "selection_index"), 2)
    }

    // MARK: entry_point

    func testEntryPointComesFromTheControlTappedNotFromWhetherASheetWasOpen() {
        model.writeSuggestion("first_date_usually", position: 1)
        XCTAssertEqual(string(analytics.only("prompt_topic_selected"), "entry_point"), "suggestion")
        XCTAssertEqual(int(analytics.only("prompt_topic_selected"), "position"), 1)
        XCTAssertEqual(string(analytics.only("prompt_topic_selected"), "topic_group"), "dating_me")
        analytics.clear()

        model.dismissSheet(.close)
        model.openTopics()
        model.pickTopic("talk_for_hours", position: 10)
        // The sheet was open in BOTH cases, which is exactly why it cannot be inferred.
        XCTAssertEqual(string(analytics.only("prompt_topic_selected"), "entry_point"), "browse")
        XCTAssertEqual(int(analytics.only("prompt_topic_selected"), "position"), 10)
        XCTAssertEqual(string(analytics.only("prompt_topic_selected"), "topic_group"), "opinions")
    }

    func testTheEntryPointThatOpenedTheSheetIsTheOneTheSaveReports() async {
        model.openTopics()
        model.pickTopic("hot_take", position: 14)
        await write("A hot take.")
        // A save that started in the browse sheet stays separable from one that started at a card,
        // which is the whole point of carrying it on the sheet rather than re-deriving it.
        XCTAssertEqual(string(analytics.only("prompt_saved"), "entry_point"), "browse")
    }

    // MARK: the browse sheet's funnel

    func testPickingATopicIsASelectionAndNeverAlsoADismissal() {
        model.openTopics()
        model.pickTopic("hot_take", position: 14)
        // opened = selected + dismissed. One act, one event.
        XCTAssertEqual(analytics.count("prompt_topic_list_opened"), 1)
        XCTAssertEqual(analytics.count("prompt_topic_selected"), 1)
        XCTAssertEqual(analytics.count("prompt_topic_list_dismissed"), 0)
    }

    func testCancellingTheBrowseSheetReportsHowAndHowLongItWasUp() {
        model.openTopics()
        clock += 7.4
        model.dismissSheet(.swipe)

        let payload = analytics.only("prompt_topic_list_dismissed")
        XCTAssertEqual(string(payload, "dismiss_method"), "swipe")
        XCTAssertEqual(int(payload, "used_count"), 0)
        // Whole seconds, floored. 7.4 is 7.
        XCTAssertEqual(int(payload, "time_on_sheet_s"), 7)
        XCTAssertEqual(string(payload, "screen_id"), "profile_prompts")
    }

    func testAllFourDismissMethodsReachThePayloadAsTheCanonicalSpelling() {
        var seen: [String] = []
        for method in [SheetDismissMethod.close, .backdrop, .swipe, .systemBack] {
            model.openTopics()
            model.dismissSheet(method)
            seen.append(string(analytics.all("prompt_topic_list_dismissed").last ?? [:],
                               "dismiss_method") ?? "")
        }
        XCTAssertEqual(seen, ["close", "backdrop", "swipe", "system_back"])

        // The two spellings 1.4.2 replaced must not survive anywhere in the payloads.
        let rendered = analytics.events
            .map { $0.1.values.map { "\($0)" }.joined(separator: " ") }
            .joined(separator: " ")
        XCTAssertFalse(rendered.contains("scrim"), "a pre-1.4.2 dismiss value survived")
        XCTAssertFalse(rendered.contains("cancel"), "a pre-1.4.2 dismiss value survived")
        XCTAssertFalse(rendered.split(separator: " ").contains("back"))
    }

    // MARK: the write sheet's abandonment

    func testAbandoningTheWriteSheetReportsTheBucketAndNeverTheText() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.draftChanged("half a sentence about something real")
        model.dismissSheet(.backdrop)

        let payload = analytics.only("prompt_editor_dismissed")
        XCTAssertEqual(payload["had_draft"] as? Bool, true)
        XCTAssertEqual(string(payload, "draft_length_bucket"), "1_40")
        XCTAssertEqual(string(payload, "dismiss_method"), "backdrop")
        XCTAssertEqual(string(payload, "entry_point"), "suggestion")
        let rendered = payload.values.map { "\($0)" }.joined(separator: " ")
        XCTAssertFalse(rendered.contains("something real"), "the draft leaked")
    }

    func testAnUntouchedSheetReportsNoDraft() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.dismissSheet(.close)
        let payload = analytics.only("prompt_editor_dismissed")
        // `had_draft` is what separates "changed their mind" from "could not finish".
        XCTAssertEqual(payload["had_draft"] as? Bool, false)
        XCTAssertEqual(string(payload, "draft_length_bucket"), "0")
    }

    func testWhitespaceAloneIsNotADraft() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.draftChanged("   ")
        model.dismissSheet(.close)
        XCTAssertEqual(analytics.only("prompt_editor_dismissed")["had_draft"] as? Bool, false)
    }

    // MARK: saving

    func testASaveReportsTheBucketTheCountAfterItAndNeverTheAnswer() async {
        model.writeSuggestion("first_date_usually", position: 0)
        await write(String(repeating: "x", count: 95))

        let payload = analytics.only("prompt_saved")
        XCTAssertEqual(string(payload, "topic_id"), "first_date_usually")
        XCTAssertEqual(string(payload, "topic_group"), "dating_me")
        XCTAssertEqual(payload["is_edit"] as? Bool, false)
        XCTAssertEqual(string(payload, "length_bucket"), "81_120")
        XCTAssertEqual(int(payload, "prompt_count"), 1)
    }

    func testAnEditIsHonestAboutNotConsumingASlot() async {
        model.writeSuggestion("first_date_usually", position: 0)
        await write("First answer.")
        model.editPrompt("first_date_usually")
        await write("A better answer.")

        let saves = analytics.all("prompt_saved")
        XCTAssertEqual(saves.count, 2)
        XCTAssertEqual(saves[0]["is_edit"] as? Bool, false)
        XCTAssertEqual(saves[1]["is_edit"] as? Bool, true)
        // STILL ONE. An edit overwrites; a count that went to 2 here would be a profile the user
        // does not have.
        XCTAssertEqual(int(saves[1], "prompt_count"), 1)
    }

    func testTheMinimumIsReportedOnceOnTheFirstSaveWithWhatGotThemThere() async {
        model.writeSuggestion("first_date_usually", position: 0)
        await write("An answer.")

        let met = analytics.only("prompts_minimum_met")
        XCTAssertEqual(int(met, "count"), 1)
        XCTAssertEqual(string(met, "topic_id"), "first_date_usually")
        XCTAssertEqual(string(met, "entry_point"), "suggestion")

        model.openTopics()
        model.pickTopic("hot_take", position: 14)
        await write("Another.")
        // Once. The second save does not re-cross anything.
        XCTAssertEqual(analytics.count("prompts_minimum_met"), 1)
    }

    func testAFailedSaveReportsNothing() async {
        model = makeModel(fail: true)
        model.writeSuggestion("first_date_usually", position: 0)
        await write("An answer.")
        // The server does not have it, so nothing may say it does.
        XCTAssertEqual(analytics.count("prompt_saved"), 0)
        XCTAssertEqual(analytics.count("prompts_minimum_met"), 0)
    }

    func testAnEmptySaveNudgesAndRecordsNothing() {
        model.writeSuggestion("first_date_usually", position: 0)
        analytics.clear()
        model.save()
        // State H is a nudge, not a validation failure. The one refusal this screen registers is
        // Continue; a second would double-count the same user against a rule that exists once.
        XCTAssertTrue(analytics.events.isEmpty)
        XCTAssertTrue(model.state.nudge)
    }

    // MARK: the cap

    func testTheCapIsReportedOncePerEditorSessionNotPerKeystroke() {
        model.writeSuggestion("first_date_usually", position: 0)
        for _ in 0..<3 { model.draftChanged(String(repeating: "x", count: promptMaxChars)) }
        XCTAssertEqual(analytics.count("prompt_char_limit_reached"), 1)
        XCTAssertEqual(string(analytics.only("prompt_char_limit_reached"), "topic_id"),
                       "first_date_usually")
    }

    func testReopeningASheetStartsANewEditorSessionForTheCap() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.draftChanged(String(repeating: "x", count: promptMaxChars))
        model.dismissSheet(.close)
        model.writeSuggestion("first_date_usually", position: 0)
        model.draftChanged(String(repeating: "x", count: promptMaxChars))
        // Per SESSION, so twice — two sittings at the cap are two facts.
        XCTAssertEqual(analytics.count("prompt_char_limit_reached"), 2)
    }

    func testStoppingShortOfTheCapReportsNothing() {
        model.writeSuggestion("first_date_usually", position: 0)
        model.draftChanged(String(repeating: "x", count: promptMaxChars - 1))
        XCTAssertEqual(analytics.count("prompt_char_limit_reached"), 0)
    }

    // MARK: the example

    func testDismissingTheExampleNamesTheTopicItWasFor() {
        model.writeSuggestion("weird_habit", position: 1)
        model.hideExample()
        XCTAssertEqual(string(analytics.only("prompt_example_dismissed"), "topic_id"),
                       "weird_habit")
    }

    // MARK: arriving and leaving

    func testArrivalReportsTheScreenAndTheStepWithTheStepIndexFromTheRegistry() {
        model.arrived(referrer: .photos)

        let viewed = analytics.only("screen_viewed")
        XCTAssertEqual(string(viewed, "screen_id"), "profile_prompts")
        // 11 says ProfilePrompts. It said "Profile - Prompts" here until 16 September 2026, which
        // is a label nobody searching the analytics tool would have found.
        XCTAssertEqual(string(viewed, "screen_name"), "ProfilePrompts")
        XCTAssertEqual(string(viewed, "referrer_screen_id"), "profile_photos")

        let step = analytics.only("profile_step_viewed")
        XCTAssertEqual(string(step, "step_id"), "prompts")
        // Photos 1, prompts 2, media 3 — read from RealYouStep, never a literal.
        XCTAssertEqual(int(step, "step_index"), 2)
    }

    func testContinueAtZeroPromptsReportsTheRefusalOncePerPress() {
        model.arrived()
        analytics.clear()

        XCTAssertFalse(model.continuePressed())
        XCTAssertFalse(model.continuePressed())

        // Continue is never disabled, so this is the ONLY record that the user tried to leave.
        XCTAssertEqual(analytics.count("form_validation_failed"), 2)
        let payload = analytics.all("form_validation_failed")[0]
        XCTAssertEqual(string(payload, "field_id"), "prompts")
        // 1.4.2 added this as the sibling of photos_below_minimum. It used to be
        // `nothing_selected`, which belongs to a chooser where nothing was ticked.
        XCTAssertEqual(string(payload, "rule"), "prompts_below_minimum")
        XCTAssertEqual(string(payload, "screen_id"), "profile_prompts")
        XCTAssertEqual(string(payload, "step_id"), "prompts")
        XCTAssertEqual(analytics.count("profile_step_completed"), 0)
    }

    func testContinueWithPromptsReportsTheTrueCountAndTheTimeOnTheStep() async {
        model.arrived()
        model.writeSuggestion("first_date_usually", position: 0)
        await write("An answer.")
        model.openTopics()
        model.pickTopic("hot_take", position: 14)
        await write("Another.")
        clock += 42
        analytics.clear()

        XCTAssertTrue(model.continuePressed())
        let payload = analytics.only("profile_step_completed")
        XCTAssertEqual(string(payload, "step_id"), "prompts")
        XCTAssertEqual(int(payload, "time_on_step_s"), 42)
        // The number the funnel reads: 1 to 3, at the moment Continue was ACCEPTED.
        XCTAssertEqual(int(payload, "prompt_count"), 2)
        XCTAssertEqual(analytics.count("form_validation_failed"), 0)
    }

    func testPromptCountIsOmittedRatherThanZeroedOnAStepThatHasNoPrompts() {
        // "OPTIONAL, SCREEN-SCOPED... OMIT on every other step." An empty property is worse than
        // an absent one, because a zero is a number somebody will chart.
        let (_, payload) = ProfileAnalytics.realYouStepCompleted(.photos, timeOnStepSeconds: 12)
        XCTAssertNil(payload["prompt_count"])
        XCTAssertEqual(payload["time_on_step_s"] as? Int, 12)
    }

    // MARK: the rules that hold across every row

    func testNoFamilyFNameIsEverEmitted() async {
        model.arrived()
        model.openTopics()
        model.pickTopic("first_date_usually", position: 0)
        model.draftChanged("An answer that is long enough to be interesting.")
        model.hideExample()
        model.save()
        await settle()
        model.editPrompt("first_date_usually")
        model.dismissSheet(.close)
        _ = model.continuePressed()

        // SUPERSEDED — DO NOT FIRE. The registry keeps these four rows, emptied of their
        // payloads, precisely so the names resolve to a notice rather than being re-implemented.
        for name in ["prompt_topic_picker_opened", "prompt_answered",
                     "prompt_edited", "prompt_removed"] {
            XCTAssertEqual(analytics.count(name), 0, "family F name fired: \(name)")
        }
        // And the walk above really did produce the family E set, so this is not vacuous.
        for name in ["screen_viewed", "profile_step_viewed", "prompt_topic_list_opened",
                     "prompt_topic_selected", "prompt_editor_opened", "prompt_example_dismissed",
                     "prompt_saved", "prompts_minimum_met", "prompt_editor_dismissed",
                     "profile_step_completed"] {
            XCTAssertTrue(analytics.names().contains(name), "missing: \(name)")
        }
    }

    func testEveryPayloadIsStampedWithTheRegistryTheValuesCameFrom() async {
        model.arrived()
        model.writeSuggestion("first_date_usually", position: 0)
        await write("An answer.")
        XCTAssertFalse(analytics.events.isEmpty)
        for (name, payload) in analytics.events {
            XCTAssertEqual(payload["field_registry_version"] as? String, "1.4.2",
                           "\(name) was stamped with the wrong registry")
        }
    }

    func testNoEventAnywhereOnThisScreenCarriesAnswerTextOrDraftText() async {
        let secret = "the specific sentence a person wrote about themselves"
        model.arrived()
        model.openTopics()
        model.pickTopic("first_date_usually", position: 0)
        model.draftChanged(secret)
        model.save()
        await settle()
        model.editPrompt("first_date_usually")
        model.draftChanged(secret)
        model.dismissSheet(.backdrop)
        _ = model.continuePressed()

        let rendered = analytics.events
            .map { event in
                event.0 + " "
                    + event.1.map { "\($0.key)=\($0.value)" }.joined(separator: " ")
            }
            .joined(separator: " ")
        for fragment in ["specific sentence", "wrote about themselves", secret] {
            XCTAssertFalse(rendered.contains(fragment), "text reached the pipeline")
        }
    }
}
