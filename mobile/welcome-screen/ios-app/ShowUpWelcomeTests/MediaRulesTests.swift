//
//  MediaRulesTests.swift
//  ShowUp · the media step's rules and payloads, with no camera and no microphone (SHOWUP-161)
//
//  The Swift half of `MediaRulesTest.kt` and `MediaTrackingTest.kt`.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHAT THIS CAN AND CANNOT PROVE
//  ─────────────────────────────────────────────────────────────────────────────
//
//  It proves the DECISIONS that do not depend on stopping a take part-way: the cap, stop-versus-keep,
//  the retake loop, the two slots, delete, the upload states, and every tracking payload.
//
//  It proves NOTHING about the recorders. `FakeMediaCapture` writes no bytes and opens no hardware,
//  so "AVCaptureSession produces a playable file" and "AVAudioRecorder survives a real phone call"
//  are claims only a device can settle.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHAT IS DELIBERATELY NOT TESTED HERE, AND WHY
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The MID-TAKE user stop and the two-second interruption threshold are proved on Android and not
//  here. Kotlin gets virtual time from `runTest`, so its clock can be advanced to exactly 2,000ms
//  and stopped; Swift has no equivalent, and the machinery to fake one — a continuation-driven gate
//  interleaved with the model's own loop — is more test infrastructure than the rule is worth, and
//  the kind that fails in ways that look like product bugs.
//
//  What holds the two platforms together instead is that the RULES live in one place on each side
//  and `check-analytics-parity.py` compares the catalogues. This gap is stated in the ticket's
//  report rather than papered over: on iOS the interruption threshold is verified by reading and by
//  a device, not by this file.
//
//  `tickWait` returns immediately, so a ten-second cap is reached in no time at all.
//

import XCTest
@testable import ShowUpWelcome

/// Answers from memory, so the rules rather than the transport are what is measured.
private actor FakeMediaRepo: MediaRepositoring {
    var snapshot: MediaSnapshot?
    var uploadFails = false
    private(set) var removed: [String] = []
    private(set) var uploads = 0

    init(snapshot: MediaSnapshot? = MediaSnapshot(items: [], previewVideoId: nil,
                                                  previewVoiceId: nil, previewSource: .fallback)) {
        self.snapshot = snapshot
    }

    func setSnapshot(_ value: MediaSnapshot?) { snapshot = value }
    func setUploadFails(_ value: Bool) { uploadFails = value }

    func load() async -> MediaSnapshot? { snapshot }

    func upload(kind: MediaKind, promptId: String, durationMs: Int,
                bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadMediaResult {
        uploads += 1
        if uploadFails { return .failed }
        return .stored(StoredMedia(id: "remote-\(uploads)", kind: kind, url: "u",
                                   promptId: promptId, durationMs: durationMs))
    }

    func remove(id: String) async -> RemoveMediaResult {
        removed.append(id)
        return .removed
    }
}

/// Collects what the model reported, in order.
///
/// A plain class with no `Sendable` and no isolation annotation, deliberately: it is created on the
/// main actor, stored in a main-actor-isolated property of `MediaModel`, and only ever called from
/// there. `@unchecked Sendable` would be the lazy way to say that and is banned in this project for
/// exactly the reason it would be wrong here — it asserts a guarantee instead of arranging one.
@MainActor
private final class Recorder: AnalyticsTracking {
    var events: [(String, [String: any Sendable])] = []

    nonisolated func track(_ name: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((name, properties)) }
    }

    func names() -> [String] { events.map(\.0) }
    func count(_ name: String) -> Int { events.filter { $0.0 == name }.count }
    func only(_ name: String) -> [String: any Sendable] {
        events.first { $0.0 == name }?.1 ?? [:]
    }
    func last(_ name: String) -> [String: any Sendable] {
        events.last { $0.0 == name }?.1 ?? [:]
    }
    func has(_ name: String) -> Bool { events.contains { $0.0 == name } }
    func clear() { events.removeAll() }
}

@MainActor
final class MediaRulesTests: XCTestCase {

    private var repo = FakeMediaRepo()
    private var capture = FakeMediaCapture()
    private var events = Recorder()
    private var clock: Int64 = 0

    private static let granted = MediaAccess(camera: .granted, microphone: .granted)
    private static let prompt = "comfort_snack"

    /// Every path a take can produce here, so a test can make the player refuse all of them.
    ///
    /// Spelled out rather than matched: the point of the test is the branch where `start` returns
    /// false, not how the fake decides to.
    private static let everyPath: Set<String> = [
        "/dev/null/video.take", "/dev/null/voice.take",
    ]

    override func setUp() async throws {
        repo = FakeMediaRepo()
        capture = FakeMediaCapture()
        events = Recorder()
        clock = 0
    }

    /// Overridden by a test that needs the player to refuse, or to run a known length.
    private var player: (any MediaPlayerMaking)?
    /// The maker the last `build` used, so a test can ask whether a press became a play.
    private var madePlayers: FakeMediaPlayerMaker?

    /// The injected maker, or a fresh fake remembered in `madePlayers`.
    ///
    /// A method rather than an inline closure: the two branches return different concrete types,
    /// and a closure would leave Swift inferring its return type from the first one.
    private func playerMaker() -> any MediaPlayerMaking {
        if let player { return player }
        let made = FakeMediaPlayerMaker(durationMs: 5_000)
        madePlayers = made
        return made
    }

    private func build(_ access: MediaAccess = granted) -> MediaModel {
        // `clock` is read through a box so the test can move it after the model is built.
        let box = ClockBox()
        box.value = clock
        clocks.append(box)
        let model = MediaModel(
            repo: repo,
            access: FixedMediaAccess(access: access),
            capture: capture,
            player: playerMaker(),
            analytics: events,
            now: { box.value },
            tickMs: 10,
            // Returns immediately: a ten-second cap costs a thousand loop iterations and no time.
            tickWait: { _ in },
            readFile: { _ in Data(count: 8) },
            removeFile: { _ in }
        )
        // The model starts at notDetermined for both and only learns otherwise by asking. A fixture
        // that skipped this would have every test blocked at the permission gate -- which is the
        // gate working. `arrived` does the same thing on a real screen.
        model.refreshAccess()
        return model
    }

    /// Lets a test move the injected clock forward after the model has been built.
    ///
    /// A plain class with no `Sendable`: `MediaModel.now` is deliberately not `@Sendable`, so this
    /// never crosses an isolation boundary and does not need to claim that it can. `@unchecked
    /// Sendable` would be the lazy way to say the same thing and is banned in this project.
    private final class ClockBox {
        var value: Int64 = 0
    }
    private var clocks: [ClockBox] = []

    private func advanceClock(by ms: Int64) {
        clock += ms
        for box in clocks { box.value = clock }
    }

    /// Lets the model's own Tasks run to a quiet point.
    ///
    /// Kotlin gets `advanceUntilIdle()`; Swift has no equivalent, and the model's clock, its
    /// capture session and its upload all run in detached Tasks. With `tickWait` returning
    /// immediately none of them actually suspends, so one scheduling slice is enough in practice --
    /// these are yields rather than sleeps, so the cost is a scheduler round trip and not time.
    private func settle(_ times: Int = 16) async {
        for _ in 0..<times { await Task.yield() }
    }

    /// Records one take to the cap and keeps it. The whole happy path in one helper.
    private func record(_ model: MediaModel, _ kind: MediaKind,
                        promptId: String = prompt) async {
        model.openPrompts(kind, entryPoint: .seeThePrompts)
        model.pickPrompt(promptId)
        await model.commitPrompt()
        await settle()
        model.acceptTake()
        await settle()
    }

    // MARK: the caps

    func testTheVideoCapStopsTheTakeAndShowsReview() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()

        XCTAssertEqual(.review, model.state.take?.phase)
        XCTAssertEqual(MediaLimits.videoMs, model.state.take?.elapsedMs)
        XCTAssertEqual(.maxLength, model.state.take?.stopReason)
        // REVIEW, not an artefact. Stopping produces a take; only accepting produces one.
        XCTAssertNil(model.state.video)
    }

    func testTheVoiceCapIsFifteenSecondsNotTen() async {
        let model = build()
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        XCTAssertEqual(MediaLimits.voiceMs, model.state.take?.elapsedMs)
    }

    // MARK: stop versus keep

    func testAcceptingCreatesTheArtefactAndSpendsTheTake() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        XCTAssertNil(model.state.video, "the cap must not fill the card")

        model.acceptTake()
        await settle()
        XCTAssertNotNil(model.state.video)
        XCTAssertNil(model.state.take, "the take is spent once kept")
        XCTAssertEqual(Self.prompt, model.state.video?.promptId)
    }

    func testCancelSavesNothingAndDiscardsTheFile() async {
        let model = build()
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        await model.cancelTake()
        await settle()
        XCTAssertNil(model.state.take)
        XCTAssertNil(model.state.voice)
        XCTAssertTrue(capture.sessions.first?.discarded ?? false)
    }

    func testARecorderThatWillNotStartReturnsToTheCard() async {
        capture = FakeMediaCapture(startSucceeds: false)
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        XCTAssertNil(model.state.take)
    }

    // MARK: the retake loop

    func testRetakeFromReviewStaysOnTheSamePromptAndCountsUp() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        XCTAssertEqual(1, model.state.take?.attempt)

        await model.retakeFromReview()
        await settle()
        XCTAssertEqual(Self.prompt, model.state.take?.promptId,
                       "the same prompt -- it does NOT reopen the list")
        XCTAssertEqual(2, model.state.take?.attempt)
        XCTAssertNil(model.state.sheet, "a retake is not a sheet")
    }

    func testRetakeFromAFilledCardReopensTheListWithTheAnsweredPromptSelected() async {
        let model = build()
        await record(model, .video)
        model.retakeFromCard(.video)
        XCTAssertEqual(Self.prompt, model.state.sheet?.selectedId)
        XCTAssertEqual(.retake, model.state.sheet?.entryPoint)
        XCTAssertNotNil(model.state.video, "the artefact stays until a new take is accepted")
    }

    // MARK: the two slots

    func testTheTwoSlotsAreIndependent() async {
        let model = build()
        await record(model, .video, promptId: "relaxed_and_happy")
        await record(model, .voice, promptId: "relaxing_sound")
        XCTAssertEqual("relaxed_and_happy", model.state.video?.promptId)
        XCTAssertEqual("relaxing_sound", model.state.voice?.promptId)
    }

    func testDeleteIsImmediateAndReturnsTheCardToItsPreviewedPrompt() async {
        let model = build()
        await record(model, .video)
        XCTAssertNotNil(model.state.video)

        model.delete(.video)
        await settle()
        // NO CONFIRMATION STEP: the state changes on the call, not after an answer.
        XCTAssertNil(model.state.video)
        XCTAssertEqual("relaxed_and_happy", model.state.preview(.video).id)
    }

    func testDeletingOneSlotLeavesTheOther() async {
        let model = build()
        await record(model, .video)
        await record(model, .voice)
        model.delete(.video)
        await settle()
        XCTAssertNil(model.state.video)
        XCTAssertNotNil(model.state.voice)
    }

    // MARK: uploading

    func testTheCardFillsOptimisticallyAndConfirms() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        model.acceptTake()
        await settle()
        // Filled before anything has been sent -- that is what optimistic means.
        XCTAssertNotNil(model.state.video)

        await settle()
        XCTAssertEqual(.confirmed, model.state.video?.status)
        XCTAssertEqual("remote-1", model.state.video?.remoteId)
    }

    func testAFailedUploadSurfacesOnTheCardAndKeepsTheArtefact() async {
        await repo.setUploadFails(true)
        let model = build()
        await record(model, .voice)
        await settle()
        XCTAssertEqual(.failed, model.state.voice?.status)
        XCTAssertNotNil(model.state.voice, "a failure must not empty the card")
    }

    func testAServerReadNeverReplacesATakeThatIsStillUploading() async {
        // The same defect the photo grid had to be fixed for.
        await repo.setUploadFails(true)
        let model = build()
        await record(model, .voice, promptId: "made_me_smile")
        await settle()

        await repo.setSnapshot(MediaSnapshot(
            items: [StoredMedia(id: "old", kind: .voice, url: "u",
                                promptId: "best_weather", durationMs: 3_000)],
            previewVideoId: nil, previewVoiceId: nil, previewSource: .fallback
        ))
        await model.arrived()
        XCTAssertEqual("made_me_smile", model.state.voice?.promptId)
    }

    // MARK: the sheet

    func testNothingIsPreselectedFromAnEmptyCard() {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        XCTAssertNil(model.state.sheet?.selectedId)
    }

    func testDismissingTheSheetKeepsNothing() {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        model.dismissPrompts(.backdrop)
        XCTAssertNil(model.state.sheet)
        XCTAssertNil(model.state.video, "the card is unchanged")
    }

    func testACommitWithNoSelectionDoesNothingAtAll() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        let missing = await model.commitPrompt()
 await settle()
        XCTAssertTrue(missing.isEmpty)
        XCTAssertNil(model.state.take)
        XCTAssertFalse(events.has(ProfileAnalytics.mediaPromptSelectedName))
    }

    func testPickingTheSameRowTwiceCountsOneSelection() {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        model.pickPrompt(Self.prompt)
        model.pickPrompt("best_weather")
        XCTAssertEqual(2, model.state.sheet?.selectionsBefore)
    }

    func testTheCommitCTAReportsWhatMustBeRequestedAndStartsNoTake() async {
        let model = build(MediaAccess(camera: .notDetermined, microphone: .notDetermined))
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        let missing = await model.commitPrompt()
 await settle()
        XCTAssertEqual([.microphone], missing)
        XCTAssertNil(model.state.take, "no take until the OS has answered")
        XCTAssertNotNil(model.state.sheet, "the sheet stays open behind the alert")
    }

    // MARK: tracking

    func testArrivingReportsTheScreenBothStepsAndTheEntryState() async {
        let model = build()
        await model.arrived()

        XCTAssertEqual(1, events.count(ProfileAnalytics.screenViewedName))
        let view = events.only(ProfileAnalytics.screenViewedName)
        XCTAssertEqual("profile_media", view["screen_id"] as? String)
        XCTAssertEqual("ProfileMedia", view["screen_name"] as? String)
        XCTAssertEqual("profile_prompts", view["referrer_screen_id"] as? String)

        // ONE SCREEN, TWO STEPS.
        XCTAssertEqual(2, events.count(ProfileAnalytics.profileStepViewed))
        let steps = events.events.filter { $0.0 == ProfileAnalytics.profileStepViewed }
            .compactMap { $0.1["step_id"] as? String }
        XCTAssertEqual(["media_video", "media_voice"], steps)
    }

    func testMediaScreenViewedNamesBothPreviewedPromptsAndTheSource() async {
        await repo.setSnapshot(MediaSnapshot(
            items: [], previewVideoId: "made_me_smile",
            previewVoiceId: "song_makes_me_move", previewSource: .ranked
        ))
        let model = build()
        await model.arrived()

        let e = events.only(ProfileAnalytics.mediaScreenViewedName)
        XCTAssertEqual(false, e["has_video"] as? Bool)
        XCTAssertEqual("made_me_smile", e["preview_video_prompt_id"] as? String)
        XCTAssertEqual("song_makes_me_move", e["preview_voice_prompt_id"] as? String)
        XCTAssertEqual("ranked", e["preview_source"] as? String)
        // It describes the SCREEN, not a medium -- the one exception to the type rule.
        XCTAssertNil(e["type"])
    }

    func testMediaScreenViewedFiresAgainOnTheReturnFromAnAcceptedTake() async {
        let model = build()
        await model.arrived()
        XCTAssertEqual(1, events.count(ProfileAnalytics.mediaScreenViewedName))

        await record(model, .video)
        XCTAssertEqual(2, events.count(ProfileAnalytics.mediaScreenViewedName))
        XCTAssertEqual(true, events.last(ProfileAnalytics.mediaScreenViewedName)["has_video"] as? Bool)
    }

    func testNoRowTapEverEmitsASelection() {
        // Rows are radio-select. A tap is a considered look, not a choice.
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        model.pickPrompt("best_weather")
        XCTAssertFalse(events.has(ProfileAnalytics.mediaPromptSelectedName))
    }

    func testTheCommitCTAEmitsTheSelectionWithPositionAndRowsTried() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt("best_weather")
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()

        let e = events.only(ProfileAnalytics.mediaPromptSelectedName)
        XCTAssertEqual("video", e["type"] as? String)
        XCTAssertEqual(Self.prompt, e["media_prompt_id"] as? String)
        XCTAssertEqual(3, e["position"] as? Int)
        XCTAssertEqual(false, e["is_own_prompt"] as? Bool)
        XCTAssertEqual(2, e["selections_before"] as? Int)
        XCTAssertEqual(false, e["was_previewed"] as? Bool)
    }

    func testWasPreviewedIsTrueOnlyForThePromptThatWasOnTheCard() async {
        // THE SINGLE RULE THAT KEEPS THE RANKING HONEST.
        let model = build()
        await model.arrived()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt("relaxed_and_happy")
        await model.commitPrompt()
        await settle()
        XCTAssertEqual(true,
                       events.only(ProfileAnalytics.mediaPromptSelectedName)["was_previewed"] as? Bool)
    }

    func testDismissingCarriesTheCanonicalMethodAndTheTime() {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        advanceClock(by: 7_400)
        model.dismissPrompts(.swipe)

        let e = events.only(ProfileAnalytics.mediaPromptListDismissedName)
        XCTAssertEqual("video", e["type"] as? String)
        // `dismiss_method`, NOT `method` -- section 23.
        XCTAssertEqual("swipe", e["dismiss_method"] as? String)
        XCTAssertNil(e["method"], "`method` must never appear on a sheet close")
        XCTAssertEqual(true, e["had_selection"] as? Bool)
        XCTAssertEqual(7, e["time_on_sheet_s"] as? Int)
    }

    func testStoppingNeverEmitsARecordedEventAndAcceptingDoes() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        XCTAssertFalse(events.has(ProfileAnalytics.videoPromptRecordedName),
                       "a take that reached review is not a recording")

        model.acceptTake()
        await settle()
        XCTAssertTrue(events.has(ProfileAnalytics.videoPromptRecordedName))
    }

    func testTheReviewEventCarriesTheStopReasonAndLength() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()

        let e = events.only(ProfileAnalytics.mediaReviewShownName)
        XCTAssertEqual("video", e["type"] as? String)
        XCTAssertEqual(10.0, e["duration_s"] as? Double)
        XCTAssertEqual(1, e["attempt"] as? Int)
        // Hitting the cap is how the cap itself is judged.
        XCTAssertEqual("max_length", e["stop_reason"] as? String)
    }

    // MARK: - playback
    //
    // A feature that was drawn and not built. `playPressed` set a flag, fired
    // `media_preview_played` and played nothing; the card's button reached that same function and
    // returned early because there is no take on that screen; `playedMs` was a hardcoded zero.
    //
    // WHAT THIS FILE CAN AND CANNOT PROVE, in the same spirit as the header. `tickWait` returns
    // immediately here, so the playback clock runs its whole bounded length the moment it starts
    // and there is no mid-play frame to observe -- the PLAYHEAD's progression is proved on Android,
    // where `runTest` provides virtual time, exactly as the interruption threshold is. What these
    // prove is the part that was actually wrong: whether a press becomes a play at all, and
    // whether the event tells the truth about it.

    func testAFilledCardPlaysAndSaysSoOnce() async {
        let model = build()
        await record(model, .voice)

        model.cardPlayPressed(.voice)
        await settle()

        XCTAssertEqual(
            true, madePlayers?.players.contains { $0.didStart },
            "the card's play button must actually play"
        )
        XCTAssertEqual(
            0, events.count(ProfileAnalytics.mediaPreviewPlayedName),
            """
            NO REVIEW EVENT FROM A CARD. `media_preview_played` is specified as a play ON REVIEW, \
            and `media_review_shown` is its denominator; counting card plays against it would \
            corrupt the ratio the event exists to measure. The registry has no row for playing a \
            saved artefact -- see E19.
            """
        )
    }

    func testACardWhoseClipWillNotOpenReportsNothing() async {
        // The real cases are a file deleted under us, an unsupported container, a URL that will not
        // open. The old code could not tell the difference because it never asked.
        // Held locally rather than read back through `madePlayers`: that property only records a
        // maker this fixture CREATED, and this test injects its own, so it stayed nil. The
        // assertion then compared `false` against nil and failed on CI with a message that sounded
        // like a product bug -- a test lying about the code is worse than no test.
        let refusing = FakeMediaPlayerMaker(durationMs: 5_000, failFor: Self.everyPath)
        player = refusing
        let model = build()
        await record(model, .voice)

        model.cardPlayPressed(.voice)
        await settle()

        XCTAssertNil(model.state.playback)
        XCTAssertFalse(
            refusing.players.contains { $0.didStart },
            "nothing opened, so nothing played"
        )
    }

    func testTheCardPlaysWithNoTakeOnScreen() async {
        // THE ORIGINAL BUG, as a test. The card's control was wired to `playPressed`, which guards
        // on `state.take` -- and on the media screen there is no take, so the guard rejected every
        // press. Nothing on the screen said so.
        let model = build()
        await record(model, .voice)
        XCTAssertNil(model.state.take, "the premise: no take is on screen once one is accepted")

        model.cardPlayPressed(.voice)
        await settle()

        XCTAssertEqual(
            true, madePlayers?.players.contains { $0.didStart },
            "the press has to reach a player, which is what it never did before"
        )
    }

    func testNothingPlaysWhileTheTakeIsStillBeingMade() async {
        let model = build()
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        // No `settle` here: settling runs the recording clock to the cap, which ends the take and
        // lands on review -- so the premise this test is about would already be gone.
        model.playPressed()
        await settle()

        XCTAssertEqual(
            0, events.count(ProfileAnalytics.mediaPreviewPlayedName),
            "there is nothing to play back until the take has been stopped"
        )
    }

    func testPlaybackStopsWhenTheUserLeaves() async {
        let model = build()
        await record(model, .voice)
        model.cardPlayPressed(.voice)
        await settle()

        model.stopPlayback()
        await settle()

        XCTAssertNil(model.state.playback, "sound does not follow the user off the screen")
        XCTAssertEqual(0, model.state.playedMs(.voice))
    }

    func testLeavingWhileItIsStillOpeningAlsoStopsTheSound() async {
        // The race, and it is not theoretical: opening an asset is async -- a disk read locally, a
        // network reach for an uploaded clip -- so a user who presses play and immediately presses
        // Continue leaves a task in mid-start. Without a generation check the start finishes
        // afterwards and writes the playing state back over the stop, which is sound playing on a
        // screen the user has left.
        let model = build()
        await record(model, .voice)

        model.cardPlayPressed(.voice)
        // NO `settle` here: the start has not been given a chance to run, which is exactly the
        // window the bug lived in.
        model.stopPlayback()
        await settle()

        XCTAssertNil(
            model.state.playback,
            "a start that was superseded must not resurrect itself"
        )
    }

    func testPlayingOnReviewCountsUp() async {
        let model = build()
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        // `settle` after each: the event now fires when the player actually STARTS, not on the
        // press. That is the whole point of the change -- it used to fire for a play that never
        // happened, because there was no player -- and it means a press has to be given the chance
        // to become a play before it is counted.
        model.playPressed()
        await settle()
        model.playPressed()
        await settle()
        model.playPressed()
        await settle()

        XCTAssertEqual(3, events.count(ProfileAnalytics.mediaPreviewPlayedName))
        XCTAssertEqual(1, events.only(ProfileAnalytics.mediaPreviewPlayedName)["play_count"] as? Int)
        XCTAssertEqual(3, events.last(ProfileAnalytics.mediaPreviewPlayedName)["play_count"] as? Int)
    }

    func testTheRecordedEventCarriesRetakesAndPlays() async {
        let model = build()
        model.openPrompts(.voice, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        await model.retakeFromReview()
        await settle()
        // See above: a press becomes a play asynchronously now.
        model.playPressed()
        await settle()
        model.playPressed()
        await settle()
        model.acceptTake()
        await settle()

        let e = events.only(ProfileAnalytics.voicePromptRecordedName)
        XCTAssertEqual("voice", e["type"] as? String)
        XCTAssertEqual(15.0, e["duration_s"] as? Double)
        // attempt counts from 1, so two attempts is ONE retake.
        XCTAssertEqual(1, e["retakes"] as? Int)
        XCTAssertEqual(2, e["plays_before_accept"] as? Int)
    }

    func testARetakeFromReviewSaysSoAndOneFromACardSaysTheOther() async {
        let model = build()
        model.openPrompts(.video, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
        await model.retakeFromReview()
        await settle()
        XCTAssertEqual("review", events.only(ProfileAnalytics.mediaRetakenName)["from"] as? String)

        model.acceptTake()
        await settle()
        events.clear()
        model.retakeFromCard(.video)
        // `review` is a take not yet kept; `media_card` is an artefact already on the profile.
        let fromCard = events.only(ProfileAnalytics.mediaRetakenName)
        XCTAssertEqual("media_card", fromCard["from"] as? String)
        XCTAssertEqual(true, fromCard["had_video"] as? Bool)
    }

    func testDeletingCarriesTheStateBeforeAndIsNeverARetake() async {
        let model = build()
        await record(model, .video)
        events.clear()

        model.delete(.video)
        await settle()
        let e = events.only(ProfileAnalytics.mediaDeletedName)
        XCTAssertEqual("video", e["type"] as? String)
        XCTAssertEqual(10.0, e["duration_s"] as? Double)
        // BEFORE: the user was looking at a filled card when they decided.
        XCTAssertEqual(true, e["had_video"] as? Bool)
        XCTAssertFalse(events.has(ProfileAnalytics.mediaRetakenName),
                       "deleting is not retaking -- there is no replacement take")
    }

    func testAnEmptyContinueIsASkipTheUserDidNotCallOne() async {
        let model = build()
        await model.arrived()
        advanceClock(by: 31_000)
        events.clear()
        model.continuePressed()

        XCTAssertEqual(2, events.count(ProfileAnalytics.profileStepSkipped))
        XCTAssertEqual(0, events.count(ProfileAnalytics.profileStepCompleted))
        let e = events.only(ProfileAnalytics.profileStepSkipped)
        XCTAssertEqual("media_video", e["step_id"] as? String)
        // NEWLY REQUIRED AT v1.4 and missing from the build until this ticket.
        XCTAssertEqual("profile_media", e["screen_id"] as? String)
        XCTAssertEqual(false, e["has_video"] as? Bool)
    }

    func testContinueCompletesWhatWasRecordedAndSkipsWhatWasNot() async {
        let model = build()
        await model.arrived()
        await record(model, .video)
        advanceClock(by: 45_000)
        events.clear()

        model.continuePressed()
        XCTAssertEqual(1, events.count(ProfileAnalytics.profileStepCompleted))
        XCTAssertEqual(1, events.count(ProfileAnalytics.profileStepSkipped))
        XCTAssertEqual("media_video",
                       events.only(ProfileAnalytics.profileStepCompleted)["step_id"] as? String)
        XCTAssertEqual(45,
                       events.only(ProfileAnalytics.profileStepCompleted)["time_on_step_s"] as? Int)
        XCTAssertEqual("media_voice",
                       events.only(ProfileAnalytics.profileStepSkipped)["step_id"] as? String)
    }

    func testContinueWithNothingFiresNoValidationEvent() async {
        let model = build()
        await model.arrived()
        model.continuePressed()
        XCTAssertFalse(events.has(ProfileAnalytics.formValidationFailed))
    }

    func testSkipRecordsASkipForBothMediaWhateverIsOnTheCards() async {
        let model = build()
        await model.arrived()
        await record(model, .voice)
        events.clear()

        model.skipPressed()
        XCTAssertEqual(2, events.count(ProfileAnalytics.profileStepSkipped))
        // The event describes the ACT; has_voice carries the state alongside it.
        XCTAssertEqual(true, events.last(ProfileAnalytics.profileStepSkipped)["has_voice"] as? Bool)
    }

    // MARK: the rules that hold across every event

    func testEveryMediaEventExceptTheScreenViewsCarriesType() async {
        let model = build()
        await model.arrived()
        await record(model, .video)
        model.retakeFromCard(.video)
        model.dismissPrompts(.close)
        model.delete(.video)
        await settle()

        let exempt: Set<String> = [
            ProfileAnalytics.screenViewedName, ProfileAnalytics.mediaScreenViewedName,
            ProfileAnalytics.profileStepViewed, ProfileAnalytics.profileStepCompleted,
            ProfileAnalytics.profileStepSkipped,
        ]
        let offenders = events.events
            .filter { !exempt.contains($0.0) && $0.1["type"] == nil }
            .map(\.0)
        XCTAssertEqual([], offenders, "every media event must carry type")
    }

    func testEveryEventIsStampedWithTheRegistryVersion() async {
        let model = build()
        await model.arrived()
        await record(model, .video)

        XCTAssertFalse(events.events.isEmpty)
        for (name, payload) in events.events {
            XCTAssertEqual(Stamp.fieldRegistryVersion,
                           payload["field_registry_version"] as? String, "\(name) is unstamped")
            XCTAssertNotNil(payload["sensitivity_class"], "\(name) has no sensitivity class")
        }
    }

    func testNoPayloadEverCarriesTheRecordingItself() async {
        // Media of a user's face and voice is the most sensitive artefact in profile creation and
        // none of it belongs in the analytics pipeline.
        let model = build()
        await model.arrived()
        await record(model, .video)

        let forbidden = ["path", "file", "uri", "url", "frame", "thumbnail", "transcript",
                         "waveform", "bytes", "data", "content"]
        for (name, payload) in events.events {
            for key in payload.keys {
                for bad in forbidden {
                    XCTAssertFalse(key.lowercased().contains(bad), "\(name) carries `\(key)`")
                }
            }
        }
    }

    func testNoCaptionEventIsEverEmitted() async {
        // Caption authoring was removed from THIS flow on 16 September 2026; the two events stay in
        // the registry so the profile-editing flow cannot invent a second name for the same act.
        let model = build()
        await model.arrived()
        await record(model, .video)
        model.continuePressed()
        XCTAssertFalse(events.names().contains { $0.hasPrefix("caption_") })
    }

    func testNoEventUsesTheWrittenPromptRegistrysKey() async {
        // `media_prompt_id` IS NOT `prompt_id`.
        let model = build()
        await record(model, .video)
        for (name, payload) in events.events {
            XCTAssertNil(payload["prompt_id"], "\(name) carries prompt_id")
            XCTAssertNil(payload["topic_id"], "\(name) carries topic_id")
        }
    }

    func testTheScreenEmitsNoRankingEvent() async {
        let model = build()
        await model.arrived()
        XCTAssertFalse(events.names().contains("media_prompt_ranking_published"))
    }
}
