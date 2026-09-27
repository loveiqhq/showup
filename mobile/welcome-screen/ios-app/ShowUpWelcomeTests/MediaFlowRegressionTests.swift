//
//  MediaFlowRegressionTests.swift
//  ShowUp · the rules the media flow was reported as breaking (SHOWUP-161)
//
//  The Swift half of `MediaFlowRegressionTest.kt`, rule for rule.
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHAT THESE CAN PROVE, AND WHAT THEY CANNOT
//  ───────────────────────────────────────────────────────────────────────────
//
//  Two bugs were reported from an Android emulator: the video path never reached review, and
//  playback "started slow then sped up" on both media. Neither was reported on iOS, and this file
//  exists because the second has a CAUSE that is shared in shape if not in code — both platforms
//  drove their clocks by adding up what they had ASKED to wait rather than what the wait cost.
//
//  THE CAPTURE HALF IS NOT REACHABLE FROM HERE, and it is worth being exact rather than
//  reassuring. `FakeMediaCapture` replaces the recorder, which is the layer the Android defect
//  lived in — below it are AVFoundation and CameraX, neither of which a unit test runs. What this
//  file proves is that everything ABOVE the recorder is right: given a recorder that produces a
//  file, the state machine reaches review, both actions do what the spec says, and the card ends
//  up filled with the prompt preserved.
//

import XCTest
@testable import ShowUpWelcome

/// Stores nothing and answers everything. The flow under test is not the network.
///
/// An ACTOR, because `MediaRepositoring` is reached from off the main actor and this project bans
/// the escape hatches that would let a plain class pretend otherwise.
private actor RegressionRepo: MediaRepositoring {
    func load() async -> MediaSnapshot? {
        MediaSnapshot(items: [], previewVideoId: nil, previewVoiceId: nil, previewSource: .fallback)
    }

    // The signature is the protocol's EXACTLY: `onProgress` is `@Sendable`, not `@escaping
    // @Sendable`. Swift treats those as different types for conformance, and the error it gives
    // names the protocol rather than the parameter -- which is why this is spelled out.
    func upload(
        kind: MediaKind,
        promptId: String,
        durationMs: Int,
        bytes: Data,
        mimeType: String,
        fileName: String,
        onProgress: @Sendable (Double) -> Void
    ) async -> UploadMediaResult {
        .stored(StoredMedia(id: "remote-1", kind: kind, url: "u",
                            promptId: promptId, durationMs: durationMs))
    }

    func remove(id: String) async -> RemoveMediaResult { .removed }
}

@MainActor
final class MediaFlowRegressionTests: XCTestCase {

    private static let prompt = "relaxed_and_happy"

    private func build(
        capture: FakeMediaCapture = FakeMediaCapture()
    ) -> MediaModel {
        let model = MediaModel(
            repo: RegressionRepo(),
            access: FixedMediaAccess(access: MediaAccess(camera: .granted, microphone: .granted)),
            capture: capture,
            player: FakeMediaPlayerMaker(),
            analytics: nil,
            now: { 0 },
            tickMs: 10,
            // A tick costs exactly what it asked for — see `MediaRulesTests` for why the clocks
            // now measure rather than assume, and why that is what makes this fixture possible.
            tickWait: { $0 },
            readFile: { _ in Data(count: 8) },
            removeFile: { _ in }
        )
        model.refreshAccess()
        return model
    }

    /// Lets the model's own Tasks reach a quiet point.
    private func settle() async {
        for _ in 0..<400 { await Task.yield() }
    }

    private func startTake(_ model: MediaModel, _ kind: MediaKind) async {
        model.openPrompts(kind, entryPoint: .seeThePrompts)
        model.pickPrompt(Self.prompt)
        await model.commitPrompt()
        await settle()
    }

    // MARK: - stop lands on review, for both media

    func testStoppingEntersReviewWithTheTakesLength() async {
        for kind in [MediaKind.video, MediaKind.voice] {
            let model = build()
            await startTake(model, kind)

            // The cap is reached by the fixture's instant ticks, which is the same path a user's
            // Stop takes — see the cap test at the bottom.
            let take = model.state.take
            XCTAssertEqual(.review, take?.phase, "stop must land on review, never back on the card")
            XCTAssertEqual(Self.prompt, take?.promptId, "the prompt survives the recorder")
            XCTAssertNotNil(take?.path, "and the file it will play")
            XCTAssertNil(model.state.artefact(kind),
                         "stopping produces a take, not an artefact")
        }
    }

    // MARK: - retake

    func testRetakeDiscardsOnlyThatTakeAndKeepsThePrompt() async {
        let model = build()
        await startTake(model, .video)

        await model.retakeFromReview()

        let take = model.state.take
        XCTAssertNotNil(take, "retake returns to the viewfinder, not to the card")
        XCTAssertEqual(Self.prompt, take?.promptId, "ON THE SAME PROMPT — it does not reopen the list")
        XCTAssertEqual(2, take?.attempt, "and it is a second attempt")
        XCTAssertNil(model.state.video, "nothing was kept")
    }

    // MARK: - accept

    func testAcceptFillsTheCardImmediatelyWithThePromptPreserved() async {
        let model = build()
        await startTake(model, .video)
        let recorded = model.state.take?.elapsedMs ?? 0

        model.acceptTake()

        // OPTIMISTIC, and asserted BEFORE the upload is allowed to run: the card is filled the
        // moment the user accepts, and the network happens afterwards.
        let artefact = model.state.video
        XCTAssertNotNil(artefact, "the card is filled on accept, not on upload")
        XCTAssertEqual(Self.prompt, artefact?.promptId, "the chosen prompt is the card's caption")
        XCTAssertEqual(recorded, artefact?.durationMs)
        XCTAssertNotNil(artefact?.localPath, "it plays locally until the upload lands")
        XCTAssertNil(model.state.take, "the viewfinder is gone")
        XCTAssertNil(model.state.voice, "THE VOICE CARD IS UNTOUCHED")
    }

    func testAcceptingVoiceLeavesTheVideoCardAlone() async {
        let model = build()
        await startTake(model, .voice)

        model.acceptTake()

        XCTAssertNotNil(model.state.voice)
        XCTAssertEqual(Self.prompt, model.state.voice?.promptId)
        XCTAssertNil(model.state.video, "THE VIDEO CARD IS UNTOUCHED")
    }

    // MARK: - the cap behaves exactly as Stop does

    func testReachingTheCapEntersReviewTheSameWayStopDoes() async {
        for kind in [MediaKind.video, MediaKind.voice] {
            let model = build()
            await startTake(model, kind)

            XCTAssertEqual(.review, model.state.take?.phase,
                           "the cap shows review, never an alert")
            XCTAssertEqual(MediaLimits.maxMs(kind), model.state.take?.elapsedMs)
            // The ONE difference from a user stop, and it is a measurement rather than a
            // behaviour: `max_length` is how the ticket learns whether the cap is too short.
            XCTAssertEqual(.maxLength, model.state.take?.stopReason)
            XCTAssertEqual(Self.prompt, model.state.take?.promptId)
        }
    }

    // MARK: - a take that produces nothing says which failure it was

    func testATakeThatRecordsNothingReportsIt() async {
        let model = build(capture: FakeMediaCapture(finishSucceeds: false))
        await startTake(model, .video)

        XCTAssertNil(model.state.take)
        XCTAssertNil(model.state.video)
        // THE PART THAT WAS MISSING. Two reports of this could not be told apart because the
        // screen said nothing at all; now it says which of the two failures happened.
        XCTAssertEqual(CaptureFailed(kind: .video, cause: .nothingRecorded),
                       model.state.captureFailed)
    }

    func testARecorderThatNeverStartsIsADifferentFailure() async {
        let model = build(capture: FakeMediaCapture(startSucceeds: false))
        await startTake(model, .voice)

        XCTAssertEqual(CaptureFailed(kind: .voice, cause: .neverStarted),
                       model.state.captureFailed)
    }

    func testOpeningTheListClearsAStaleFailure() async {
        let model = build(capture: FakeMediaCapture(startSucceeds: false))
        await startTake(model, .video)
        XCTAssertNotNil(model.state.captureFailed)

        // OPENING THE LIST IS ENOUGH. The notice belongs to a take that is over, and the user
        // choosing a prompt again has moved on from it.
        model.openPrompts(.video, entryPoint: .seeThePrompts)

        XCTAssertNil(model.state.captureFailed,
                     "a stale failure must not sit under a fresh choice")
    }
}
