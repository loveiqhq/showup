//
//  MediaPromptsTests.swift
//  ShowUp · the eleven, the escape hatch, the two waveforms and the permission matrix (SHOWUP-161)
//
//  The Swift half of `MediaPromptsTest.kt` and the pure half of `MediaRulesTest.kt`. The same rules
//  in the same order, because the thing being protected is that the two platforms agree: a
//  per-prompt completion chart fed by one platform that stores ids and one that stores display
//  strings is worse than no chart, and nothing about that disagreement is visible in either
//  codebase on its own.
//
//  The prompts are QUOTED here rather than derived from the list they check. A test that read the
//  strings back out of `MediaPrompts.all` would pass on any strings at all, which is the failure
//  mode the ticket warns about twice — "do not retype them, keep the order" — and the only way to
//  catch a retype is to have the ticket's own words sitting next to the code's.
//

import XCTest
@testable import ShowUpWelcome

final class MediaPromptsTests: XCTestCase {

    /// Verbatim from the reference file's `MEDIA_PROMPTS`, in its order.
    private let quoted = [
        "The little everyday thing that instantly puts me in a good mood",
        "What my ideal sunny morning looks like",
        "A sound that always makes me feel relaxed",
        "My go-to comfort snack when having a good day",
        "How my friends would describe my energy in three words",
        "The kind of weather that brings out the best in me",
        "A song that always makes me want to move",
        "What I usually look like when I'm relaxed and happy",
        "The best simple pleasure in my daily routine",
        "Something cute or funny that made me smile this week",
        "My favorite way to spend an easy 30 minutes outside",
    ]

    func testTheElevenReadExactlyAsTheReferenceWritesThem() {
        XCTAssertEqual(quoted, MediaPrompts.all.filter { !$0.isOwn }.map(\.display))
    }

    func testTheEscapeHatchIsLastAndReadsAsTheTicketQuotesIt() {
        let last = MediaPrompts.all[MediaPrompts.all.count - 1]
        XCTAssertEqual("Something else — my own idea", last.display)
        XCTAssertTrue(last.isOwn)
        XCTAssertEqual(MediaPrompts.ownIdea, last.id)
        XCTAssertEqual(1, MediaPrompts.all.filter(\.isOwn).count)
    }

    func testTheIdsAreTheSectionTwentyRegistrysNotInvented() {
        XCTAssertEqual(
            ["everyday_good_mood", "ideal_sunny_morning", "relaxing_sound", "comfort_snack",
             "friends_three_words", "best_weather", "song_makes_me_move", "relaxed_and_happy",
             "simple_pleasure", "made_me_smile", "easy_30_outside", "own_idea"],
            MediaPrompts.all.map(\.id)
        )
    }

    func testPositionsAreDenseFromZero() {
        for (index, prompt) in MediaPrompts.all.enumerated() {
            XCTAssertEqual(index, prompt.position)
        }
    }

    func testTheEyebrowsCountIsDerivedAndExcludesTheEscapeHatch() {
        // `One of 11 prompts` has to stay true if a twelfth is ever added, and a hard-coded 11
        // beside a list of twelve entries is the kind of drift no test notices.
        XCTAssertEqual(11, MediaPrompts.count)
        XCTAssertEqual(12, MediaPrompts.all.count)
    }

    func testTheColdStartsDifferPerMediumAndAreBothRealPrompts() {
        XCTAssertEqual("relaxed_and_happy", MediaPrompts.coldStart(.video).id)
        XCTAssertEqual("relaxing_sound", MediaPrompts.coldStart(.voice).id)
        // One you would show and one you would play, so the two empty cards never read as
        // duplicates of each other.
        XCTAssertNotEqual(MediaPrompts.coldStart(.video), MediaPrompts.coldStart(.voice))
    }

    func testAnUnknownIdResolvesToNothingRatherThanSomethingPlausible() {
        // `first_date_usually` is a WRITTEN-prompt topic id from section 17. It must not resolve
        // here: these are two registries for two screens, and the rename at taxonomy 1.4 happened
        // because they had been confused once already.
        XCTAssertNil(MediaPrompts.byId("first_date_usually"))
        XCTAssertNil(MediaPrompts.byId(nil))
    }

    func testOwnIdeaIsNeverPreviewedHoweverItArrives() {
        XCTAssertEqual("relaxed_and_happy",
                       MediaPrompts.preview(.video, fromServer: MediaPrompts.ownIdea).id)
        XCTAssertEqual("relaxing_sound", MediaPrompts.preview(.voice, fromServer: "nonsense").id)
    }

    func testTheClientRendersWhatTheServerSentAndNeverRanks() {
        XCTAssertEqual("made_me_smile",
                       MediaPrompts.preview(.video, fromServer: "made_me_smile").id)
    }

    func testTheStepIdsAndIndexComeFromSectionTwo() {
        XCTAssertEqual("media_video", MediaKind.video.stepId)
        XCTAssertEqual("media_voice", MediaKind.voice.stepId)
        XCTAssertEqual(3, MediaKind.stepIndex)
        XCTAssertEqual("video", MediaKind.video.trackingValue)
        XCTAssertEqual("voice", MediaKind.voice.trackingValue)
    }
}

/// The two waveforms.
///
/// "48 deterministic bars whose heights do not change between renders" is an acceptance criterion,
/// and determinism is the part a test can actually hold: SwiftUI redraws for reasons that have
/// nothing to do with audio, and a waveform re-rolled on redraw makes a finished recording appear
/// to wobble while the user is looking at it.
final class MediaWaveformTests: XCTestCase {

    func testBothWaveformsAre48Bars() {
        XCTAssertEqual(48, MediaWaveform.bars)
        XCTAssertEqual(48, MediaWaveform.recorded.count)
        XCTAssertEqual(48, MediaWaveform.live.count)
    }

    func testEveryBarIsWithinTheDrawableRange() {
        for value in MediaWaveform.recorded + MediaWaveform.live {
            XCTAssertTrue(value >= 0 && value <= 1, "bar out of range: \(value)")
        }
    }

    func testTheValuesDoNotChangeBetweenReads() {
        XCTAssertEqual(MediaWaveform.recorded, MediaWaveform.recorded)
        XCTAssertEqual(MediaWaveform.live, MediaWaveform.live)
    }

    func testTheLiveWaveformPeaksInTheMiddleAndFadesToTheEdges() {
        // Centre-anchored, "so it reads as 'live mic' rather than 'scrubbing'". A port that dropped
        // the envelope would still be deterministic and still be wrong.
        let middle = MediaWaveform.live[24]
        XCTAssertGreaterThan(middle, MediaWaveform.live[0])
        XCTAssertGreaterThan(middle, MediaWaveform.live[47])
    }

    func testTheTwoWaveformsAreNotTheSameCurve() {
        XCTAssertNotEqual(MediaWaveform.recorded, MediaWaveform.live)
    }

    /// THE PARITY THAT MATTERS. Both platforms compute these from the same formula, so a port that
    /// rounded differently would draw two different waveforms for one recording.
    func testTheFirstBarsMatchTheFormulaToFourPlaces() {
        // |sin(0 * 0.7) * 0.55 + sin(0 * 0.31) * 0.4| + 0.1 = 0.1
        XCTAssertEqual(0.1, MediaWaveform.recorded[0], accuracy: 0.0001)
        // The centre bar of the live envelope: centre 0, envelope 1.
        let i = 24.0
        let v = (abs(sin(i * 0.93) + cos(i * 0.5) * 0.7) / 1.7)
        XCTAssertEqual(min(1.0, 0.15 + v * 0.85), MediaWaveform.live[24], accuracy: 0.0001)
    }
}

/// The readouts under every take.
final class MediaDurationFormatTests: XCTestCase {

    func testLengthsReadAsTheDesignWritesThem() {
        XCTAssertEqual("0:09", formatTakeLength(9_400))
        XCTAssertEqual("0:14", formatTakeLength(14_100))
        XCTAssertEqual("0:00", formatTakeLength(0))
        XCTAssertEqual("0:10", formatTakeLength(10_000))
    }

    func testSecondsAreTruncatedNeverRounded() {
        // A 9.6-second take displayed as 0:10 on a 10-second cap reads as though it hit the cap
        // when it did not, which is the one thing this screen's measurement must not confuse.
        XCTAssertEqual("0:09", formatTakeLength(9_900))
    }

    func testANegativeLengthCannotBeProduced() {
        XCTAssertEqual("0:00", formatTakeLength(-500))
        XCTAssertEqual(0.0, durationSeconds(-500), accuracy: 0.0001)
    }

    func testDurationSecondsKeepsOneDecimal() {
        XCTAssertEqual(9.4, durationSeconds(9_400), accuracy: 0.0001)
        XCTAssertEqual(10.0, durationSeconds(10_000), accuracy: 0.0001)
        XCTAssertEqual(9.9, durationSeconds(9_999), accuracy: 0.0001)
    }
}

/// The permission matrix, which is pure and therefore fully testable on both platforms.
final class MediaAccessTests: XCTestCase {

    func testABlockedMicrophoneBlocksBothCards() {
        let access = MediaAccess(camera: .granted, microphone: .blocked)
        XCTAssertEqual(.microphone, access.blocker(for: .video)?.capability)
        XCTAssertEqual(.microphone, access.blocker(for: .voice)?.capability)
        XCTAssertFalse(access.isReady(for: .video))
        XCTAssertFalse(access.isReady(for: .voice))
    }

    func testABlockedCameraBlocksVideoAndLeavesVoiceFullyFunctional() {
        // The one that matters most: scaling the treatment to what is actually blocked.
        let access = MediaAccess(camera: .blocked, microphone: .granted)
        XCTAssertEqual(.camera, access.blocker(for: .video)?.capability)
        XCTAssertNil(access.blocker(for: .voice))
        XCTAssertFalse(access.isReady(for: .video))
        XCTAssertTrue(access.isReady(for: .voice))
    }

    func testNothingIsDrawnBeforeTheFirstRefusal() {
        // A user who has never been asked sees two clean cards, and the OS alert fires on the
        // commit CTA instead.
        let access = MediaAccess(camera: .notDetermined, microphone: .notDetermined)
        XCTAssertNil(access.blocker(for: .video))
        XCTAssertNil(access.blocker(for: .voice))
        XCTAssertFalse(MediaPermission.notDetermined.needsRow)
        XCTAssertTrue(MediaPermission.canAsk.needsRow)
        XCTAssertTrue(MediaPermission.blocked.needsRow)
    }

    func testVideoAsksForBothPermissionsAndVoiceAsksForOne() {
        let none = MediaAccess(camera: .notDetermined, microphone: .notDetermined)
        XCTAssertEqual([.camera, .microphone], none.missing(for: .video))
        XCTAssertEqual([.microphone], none.missing(for: .voice))
    }

    func testTheMicrophoneIsNamedFirstWhenBothAreRefused() {
        // Reporting the camera first would send somebody to Settings, have them fix the camera,
        // and return to a video card still blocked.
        let access = MediaAccess(camera: .blocked, microphone: .blocked)
        XCTAssertEqual(.microphone, access.blocker(for: .video)?.capability)
    }

    /// iOS NEVER PRODUCES `.canAsk`, and the case exists anyway.
    ///
    /// The ticket's matrix labels that row "(Android, after one refusal)": `AVCaptureDevice` reports
    /// `.denied` forever once refused and there is no second prompt to offer. The case stays so the
    /// two platforms share one state machine, its copy and its tests — see MediaAccess.swift.
    func testTheReaderMapsRestrictedAndDeniedToBlocked() {
        let fixed = FixedMediaAccess(access: MediaAccess(camera: .blocked, microphone: .granted))
        XCTAssertEqual(.blocked, fixed.read().camera)
        XCTAssertEqual("Camera", fixed.platformLabel(.camera))
        XCTAssertEqual("Microphone", fixed.platformLabel(.microphone))
    }
}
