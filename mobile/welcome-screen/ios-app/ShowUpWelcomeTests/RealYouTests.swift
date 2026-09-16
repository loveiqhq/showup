//
//  RealYouTests.swift
//  ShowUp · the rules of "The real you", checked with no device (SHOWUP-156, SHOWUP-158)
//
//  The Swift half of `PhotoGridTest.kt` and `PromptTopicsTest.kt`. The same assertions in the same
//  order, because the thing being protected is that the two platforms agree — a rule that holds on
//  one and not the other is how a screen drifts, and it is what `audit/verify-profile.py` cannot
//  see, since a checker reads strings rather than behaviour.
//

import XCTest
@testable import ShowUpWelcome

final class PhotoGridTests: XCTestCase {

    private func photo(_ id: Int64, _ status: UploadStatus) -> PickedPhoto {
        PickedPhoto(localId: id, uri: nil, status: status)
    }

    // MARK: the count

    func testAnInFlightPhotoOccupiesItsSlotAndDoesNotCount() {
        let state = PhotoGridState(photos: [photo(0, .confirmed), photo(1, .inFlight)])
        // Two slots look occupied; one has been added.
        XCTAssertEqual(state.photos.count, 2)
        XCTAssertEqual(state.confirmedCount, 1)
    }

    func testStateCReadsOneOfSixWithThreeSlotsOccupied() {
        // The ticket's own artboard: one confirmed, one uploading, one failed. The count row reads
        // "1 of 6" and Continue still refuses — which is the whole point of the state.
        let state = PhotoGridState(photos: [
            photo(0, .confirmed), photo(1, .inFlight), photo(2, .failed),
        ])
        XCTAssertEqual(PhotosCopy.countValue(state.confirmedCount), "1 of 6")
        XCTAssertFalse(state.meetsMinimum)
    }

    func testFourInFlightIsNotFourPhotos() {
        let state = PhotoGridState(photos: (0..<4).map { photo(Int64($0), .inFlight) })
        XCTAssertEqual(state.confirmedCount, 0)
        XCTAssertFalse(state.meetsMinimum, "Continue must still refuse at four in flight")
    }

    func testFourConfirmedIsEnough() {
        let state = PhotoGridState(photos: (0..<4).map { photo(Int64($0), .confirmed) })
        XCTAssertTrue(state.meetsMinimum)
        XCTAssertEqual(PhotosCopy.countValue(state.confirmedCount), "4 of 6")
    }

    // MARK: photos_minimum_met fires once

    func testTheMinimumCrossingIsReportedOnTheFourthConfirmedUpload() {
        let three = PhotoGridState(photos: (0..<3).map { photo(Int64($0), .confirmed) })
        XCTAssertTrue(three.crossesMinimum(nextConfirmed: 4))
    }

    func testTheMinimumCrossingIsNotReportedTwice() {
        let already = PhotoGridState(photos: (0..<4).map { photo(Int64($0), .confirmed) },
                                     minimumReported: true)
        XCTAssertFalse(already.crossesMinimum(nextConfirmed: 5))
        XCTAssertFalse(already.crossesMinimum(nextConfirmed: 6))
        // And neither is dropping below four and coming back — a funnel step that can fire twice
        // for one user cannot be counted.
        XCTAssertFalse(already.crossesMinimum(nextConfirmed: 4))
    }

    // MARK: the reorder hint

    func testTheReorderHintAppearsFromTheSecondPhoto() {
        XCTAssertFalse(PhotoGridState(photos: [photo(0, .confirmed)]).canReorder)
        XCTAssertTrue(PhotoGridState(photos: [photo(0, .confirmed), photo(1, .confirmed)])
            .canReorder)
    }

    // MARK: the slot hints

    func testAnEmptyGridAsksSlotOneForAFace() {
        XCTAssertEqual(slotHint(index: 0, confirmedCount: 0), "Start with your face")
        // And only slot one, and only while the grid is empty.
        XCTAssertEqual(slotHint(index: 1, confirmedCount: 0), "With friends")
        XCTAssertEqual(slotHint(index: 0, confirmedCount: 1), "Portrait")
    }

    func testEverySlotHasAHintAndTheOptionalPairShareOne() {
        XCTAssertEqual(PhotosCopy.slotHints.count, photosMax)
        XCTAssertEqual(slotHint(index: 4, confirmedCount: 2), "Anything you like")
        XCTAssertEqual(slotHint(index: 5, confirmedCount: 2), "Anything you like")
    }

    func testSlotsFiveAndSixAreTheOptionalOnes() {
        for i in 0..<photosRequired { XCTAssertFalse(isOptionalSlot(i)) }
        for i in photosRequired..<photosMax { XCTAssertTrue(isOptionalSlot(i)) }
    }

    // MARK: the one number the grid's geometry rests on

    func testEverySlotIs158Tall() {
        // Asserted as a constant rather than measured, because what matters is that there is ONE of
        // it: the slot's four renderings all read this, and `reorderTarget` is only valid because
        // the cell height does not depend on what is in the cell.
        XCTAssertEqual(photoSlotHeight, 158)
    }

    // MARK: the reorder arithmetic

    private let cell: CGFloat = 170
    private let tall: CGFloat = 158
    private let gap: CGFloat = 12

    private func target(from: Int, dx: CGFloat, dy: CGFloat, count: Int = 4) -> Int {
        reorderTarget(from: from, dx: dx, dy: dy,
                      cellWidth: cell, cellHeight: tall, gap: gap, count: count)
    }

    func testADragThatHasNotMovedLandsWhereItStarted() {
        XCTAssertEqual(target(from: 0, dx: 0, dy: 0), 0)
        // And a nudge shorter than half a cell is still not a move.
        XCTAssertEqual(target(from: 0, dx: 40, dy: 20), 0)
    }

    func testDraggingRightByOneCellMovesOneColumn() {
        XCTAssertEqual(target(from: 0, dx: cell + gap, dy: 0), 1)
    }

    func testDraggingDownByOneRowMovesTwoSlots() {
        XCTAssertEqual(target(from: 0, dx: 0, dy: tall + gap), 2)
    }

    func testTheLastSlotDraggedToTheFrontBecomesTheMainPhoto() {
        XCTAssertEqual(target(from: 3, dx: -(cell + gap), dy: -(tall + gap)), 0)
    }

    func testADragOffTheSideIsRefusedRatherThanWrapped() {
        // Column -1 does not exist, and wrapping it to the previous row's right-hand slot would
        // move the photo somewhere the finger never was.
        XCTAssertEqual(target(from: 0, dx: -(cell + gap), dy: 0), 0)
        XCTAssertEqual(target(from: 1, dx: cell + gap, dy: 0), 1)
    }

    func testADragBelowTheLastSlotIsRefused() {
        XCTAssertEqual(target(from: 3, dx: 0, dy: (tall + gap) * 2, count: 4), 3)
        // And is allowed once the optional pair is on screen.
        XCTAssertEqual(target(from: 3, dx: 0, dy: tall + gap, count: 6), 5)
    }

    func testAnUnmeasuredCellCannotMoveAnything() {
        // The view reports a size of zero for one frame before layout. Dividing by it would produce
        // a NaN target and, rounded, slot 0 — silently promoting a photo to main on a drag that had
        // not finished.
        XCTAssertEqual(reorderTarget(from: 2, dx: 500, dy: 500,
                                     cellWidth: 0, cellHeight: 0, gap: gap, count: 4), 2)
    }

    // MARK: moving is an insert, not a swap

    func testMovingToTheFrontShiftsEverythingElseAlong() {
        XCTAssertEqual(moved(["a", "b", "c", "d"], from: 3, to: 0), ["d", "a", "b", "c"])
    }

    func testMovingToTheBackShiftsEverythingElseBack() {
        XCTAssertEqual(moved(["a", "b", "c", "d"], from: 0, to: 3), ["b", "c", "d", "a"])
    }

    func testAMoveToTheSamePlaceOrOffTheEndChangesNothing() {
        let list = ["a", "b", "c"]
        XCTAssertEqual(moved(list, from: 1, to: 1), list)
        XCTAssertEqual(moved(list, from: 1, to: 9), list)
        XCTAssertEqual(moved(list, from: -1, to: 0), list)
    }

    // MARK: the tracking vocabulary

    func testATapOnAFilledSlotIsAReplaceAndAnEmptyOneIsAnAdd() {
        XCTAssertEqual(slotTapAction(occupied: false), "add")
        XCTAssertEqual(slotTapAction(occupied: true), "replace")
    }

    func testTheSourceValuesAreTheRegistrys() {
        XCTAssertEqual(PhotoSource.library.trackingValue, "library")
        XCTAssertEqual(PhotoSource.camera.trackingValue, "camera")
    }
}

final class PromptTopicsTests: XCTestCase {

    // MARK: the content

    func testFifteenTopicsInThreeGroupsOfFive() {
        XCTAssertEqual(topicGroups.count, 3)
        for group in topicGroups { XCTAssertEqual(group.topics.count, 5) }
        XCTAssertEqual(promptTopics.count, 15)
        // The control says "Browse all 15 topics"; a sixteenth topic would make that a lie.
        XCTAssertTrue(PromptsCopy.browseAll.contains("\(promptTopics.count)"))
    }

    func testTheGroupsAreInTheReferencesOrder() {
        XCTAssertEqual(topicGroups.map(\.label),
                       ["Dating me", "Me in real life", "Opinions & obsessions"])
    }

    func testEveryTopicIdIsUniqueAndStable() {
        let ids = promptTopics.map(\.id)
        XCTAssertEqual(ids.count, Set(ids).count)
        // ASSIGNED, NOT DERIVED. A slug computed from the display string would change with a copy
        // edit and orphan every answer saved under the old one.
        XCTAssertEqual(topicFor("first_date_usually")?.id, "first_date_usually")
        XCTAssertEqual(topicText("first_date_usually"), "On a first date, I usually…")
    }

    func testEveryTopicHasAShortWorkedExample() {
        for topic in promptTopics {
            XCTAssertNotNil(promptExamples[topic.id], "no example for \(topic.id)")
        }
        // "Every example is under 120 characters, so the example itself says this length is fine."
        for (id, example) in promptExamples {
            XCTAssertLessThan(example.count, 120,
                              "example for \(id) is over the 120 the ticket sets")
        }
    }

    func testTheThreeSuggestionsAreOneFromEachGroup() {
        let groupOf: (String) -> Int = { id in
            topicGroups.firstIndex { $0.topics.contains { $0.id == id } } ?? -1
        }
        XCTAssertEqual(suggestedTopicIds.map(groupOf), [0, 1, 2])
    }

    // MARK: suggestions

    func testThreeAreOfferedAtZeroSavedAndTwoAfterThat() {
        XCTAssertEqual(suggestionsFor(used: [], count: 3).count, 3)
        XCTAssertEqual(suggestionsFor(used: ["first_date_usually"], count: 2).count, 2)
    }

    func testAUsedTopicIsNeverSuggestedAgain() {
        let used = ["first_date_usually", "weird_habit"]
        let offered = suggestionsFor(used: used, count: 2).map(\.id)
        XCTAssertTrue(offered.allSatisfy { !used.contains($0) })
    }

    func testWithAllThreeSuggestionsUsedItFallsThroughToGroupOrder() {
        // Otherwise the "Add another · optional" block would be an empty section label, which is
        // worse than a topic the user did not expect.
        let offered = suggestionsFor(used: suggestedTopicIds, count: 2)
        XCTAssertEqual(offered.count, 2)
        XCTAssertEqual(offered.first?.id, "out_the_door")
    }

    // MARK: the cap, the floor and the counter

    func testTheCapIs160AndSlicingIsSilent() {
        XCTAssertEqual(promptMaxChars, 160)
        XCTAssertEqual(cappedAnswer(String(repeating: "x", count: 400)).count, 160)
    }

    func testTheCounterStartsAtOneHundred() {
        // The single change this revision is most about: below 100 a numeral is not information,
        // it is a target.
        XCTAssertEqual(promptCounterFrom, 100)
        XCTAssertTrue(promptCounterFrom > 0 && promptCounterFrom < promptMaxChars)
    }

    func testWhitespaceOnlyCountsAsEmpty() {
        XCTAssertTrue(promptAnswerIsEmpty(""))
        XCTAssertTrue(promptAnswerIsEmpty("   "))
        XCTAssertTrue(promptAnswerIsEmpty("\n\t "))
        XCTAssertFalse(promptAnswerIsEmpty("a"))
    }

    func testOnePromptIsEnoughAndThreeIsTheMost() {
        XCTAssertEqual(promptsRequired, 1)
        XCTAssertEqual(promptsMax, 3)
    }

    func testTheCounterSaysEnoughToContinueOnlyOnceSomethingIsSaved() {
        XCTAssertEqual(PromptsCopy.counter(0), "0/3 prompts")
        XCTAssertEqual(PromptsCopy.counter(1), "1/3 prompts · enough to continue")
        XCTAssertEqual(PromptsCopy.counter(3), "3/3 prompts · enough to continue")
    }

    // MARK: the ids are the registry's

    func testEveryTopicIdIsTheOneTheRegistryDictates() {
        // enums.json 17, verbatim and in order. THE POINT OF THE TEST is that these ids are not
        // ours to choose: they are what the analytics warehouse joins on and what the
        // `profile_prompts` rows store, so a drift here orphans data on both sides at once. Six
        // of these were invented against registry 1.3.0, which had no 17, and six were wrong.
        XCTAssertEqual(promptTopics.map(\.id), [
            "first_date_usually", "out_the_door", "ideal_30_min", "cross_town_for",
            "spontaneous_plan",
            "thirty_min_feels", "in_real_life_more", "unsexy_truth", "sunday_energy",
            "weird_habit",
            "talk_for_hours", "know_too_much", "hill_to_die_on", "green_flag", "hot_take",
        ])
    }

    func testEveryGroupIdIsTheOneTheRegistryDictates() {
        XCTAssertEqual(topicGroups.map(\.id), ["dating_me", "real_life", "opinions"])
    }

    func testATopicReportsTheGroupItIsActuallyListedUnder() {
        XCTAssertEqual(topicGroupFor("first_date_usually"), "dating_me")
        XCTAssertEqual(topicGroupFor("weird_habit"), "real_life")
        XCTAssertEqual(topicGroupFor("hot_take"), "opinions")
        // An id nobody registered reports nothing rather than guessing a group.
        XCTAssertEqual(topicGroupFor("not_a_topic"), "")
    }

    func testEveryExampleIsWrittenAgainstARealTopicId() {
        // The examples are keyed by id, so a renamed topic with an unrenamed example key is a
        // silent fallback to the generic line rather than an error.
        let ids = Set(promptTopics.map(\.id))
        XCTAssertEqual(Set(promptExamples.keys).subtracting(ids), [])
        XCTAssertEqual(ids.subtracting(Set(promptExamples.keys)), [])
    }

    func testTheThreeSuggestionsAreRealTopicsOneFromEachGroup() {
        XCTAssertEqual(suggestedTopicIds.map(topicGroupFor),
                       ["dating_me", "real_life", "opinions"])
    }

    // MARK: length buckets, which exist so the text never travels

    func testALengthFallsInTheBucket19SaysItDoes() {
        XCTAssertEqual(promptLengthBucket(0), "0")
        XCTAssertEqual(promptLengthBucket(1), "1_40")
        XCTAssertEqual(promptLengthBucket(40), "1_40")
        XCTAssertEqual(promptLengthBucket(41), "41_80")
        XCTAssertEqual(promptLengthBucket(80), "41_80")
        XCTAssertEqual(promptLengthBucket(81), "81_120")
        XCTAssertEqual(promptLengthBucket(120), "81_120")
        XCTAssertEqual(promptLengthBucket(121), "121_160")
        // The cap is 160 and the top bucket is open-ended, so a value that somehow got past the
        // slice still lands somewhere rather than falling out of the set.
        XCTAssertEqual(promptLengthBucket(promptMaxChars), "121_160")
        XCTAssertEqual(promptLengthBucket(400), "121_160")
    }

    func testNoPromptPayloadCanCarryAnAnswerOrADraft() {
        // STRUCTURAL, NOT A CONVENTION. Every builder takes a LENGTH, so there is no signature
        // that could carry the text even if a caller wanted it to. This asserts the outcome.
        let answer = "Talk about anything real, not the safe thing."
        let payloads = [
            ProfileAnalytics.promptSaved(topicId: "first_date_usually", entryPoint: .suggestion,
                                         isEdit: false, answerLength: answer.count,
                                         promptCount: 1).1,
            ProfileAnalytics.promptEditorDismissed(topicId: "first_date_usually",
                                                   entryPoint: .browse,
                                                   draftLength: answer.count,
                                                   method: .backdrop).1,
        ]
        for payload in payloads {
            let rendered = payload.values.map { "\($0)" }.joined(separator: " ")
            XCTAssertFalse(rendered.contains("anything real"), "text reached a payload")
            XCTAssertFalse(rendered.contains("safe thing"), "text reached a payload")
        }
        XCTAssertEqual(payloads[0]["length_bucket"] as? String, "41_80")
        XCTAssertEqual(payloads[1]["draft_length_bucket"] as? String, "41_80")
    }

    // MARK: the state the screen is a function of

    func testADraftBelongsToATopicSoDismissingAndReopeningRestoresIt() {
        var state = PromptsState(sheet: .write(topicId: "first_date_usually", editing: false, entryPoint: .suggestion),
                                 drafts: ["first_date_usually": "half a sentence"])
        state.sheet = nil
        XCTAssertEqual(state.draftFor("first_date_usually"), "half a sentence")
        state.sheet = .write(topicId: "first_date_usually", editing: false, entryPoint: .suggestion)
        XCTAssertEqual(state.draftFor("first_date_usually"), "half a sentence")
        // And a different topic starts empty.
        XCTAssertEqual(state.draftFor("hot_take"), "")
    }

    func testOnePromptIsEnoughToContinueAndNoneIsNot() {
        XCTAssertFalse(PromptsState().canContinue)
        XCTAssertTrue(PromptsState(prompts: [SavedPrompt(topicId: "first_date_usually", answer: "x")])
            .canContinue)
    }

    func testTheSavedStateSurvivesARoundTripThroughItsEncoding() {
        let state = PromptsState(
            prompts: [SavedPrompt(topicId: "first_date_usually", answer: "an answer")],
            sheet: .write(topicId: "hot_take", editing: true, entryPoint: .edit),
            drafts: ["hot_take": "half written"],
            nudge: true,
            exampleHiddenFor: "hot_take")
        XCTAssertEqual(PromptsState.decode(state.encoded), state)
    }

    func testAnEmptyStateRoundTripsTooAndRubbishDecodesToOne() {
        // The case a saver usually gets wrong: no sheet, no drafts, nothing hidden.
        XCTAssertEqual(PromptsState.decode(PromptsState().encoded), PromptsState())
        // And a stored value from an older build is an empty screen rather than a crash.
        XCTAssertEqual(PromptsState.decode("not json at all"), PromptsState())
        XCTAssertEqual(PromptsState.decode(""), PromptsState())
    }
}

final class RealYouStepTests: XCTestCase {

    func testTheGroupIsThreeSegmentsAndProfileVerifyIsNotOneOfThem() {
        // SHOWUP-158 supersedes SHOWUP-156's `steps={4}` in as many words. The count lives in one
        // place so profile verification returning post-MVP is one edit rather than three screens.
        XCTAssertEqual(RealYouStep.count, 3)
        XCTAssertEqual(RealYouStep.allCases.count, 3)
        XCTAssertEqual(RealYouStep.photos.progressSegment, 1)
        XCTAssertEqual(RealYouStep.prompts.progressSegment, 2)
        XCTAssertEqual(RealYouStep.media.progressSegment, 3)
    }

    func testTheStepIdsAreTheTicketsVocabulary() {
        XCTAssertEqual(RealYouStep.allCases.map(\.stepId), ["photos", "prompts", "media"])
    }

    func testPhotosAndPromptsCarryBothScreenVocabularies() {
        let (_, photos) = ProfileAnalytics.screenViewed(.photos)
        XCTAssertEqual(photos["screen_id"] as? String, "profile_photos")
        XCTAssertEqual(photos["screen_name"] as? String, "ProfilePhotos")
        let (_, prompts) = ProfileAnalytics.screenViewed(.prompts)
        XCTAssertEqual(prompts["screen_id"] as? String, "profile_prompts")
        // 11 says ProfilePrompts. It said "Profile - Prompts" until 16 September 2026, which is a
        // label nobody searching the analytics tool would have found.
        XCTAssertEqual(prompts["screen_name"] as? String, "ProfilePrompts")
    }

    func testNoPhotoPayloadCarriesAFilenameOrAUri() {
        let payloads: [[String: any Sendable]] = [
            ProfileAnalytics.photoAdded(slotIndex: 0, source: .library, filledCount: 1).1,
            ProfileAnalytics.photoRemoved(slotIndex: 0, filledCount: 0).1,
            ProfileAnalytics.photoSlotTapped(slotIndex: 0, isOptional: false, action: "add").1,
        ]
        for payload in payloads {
            let rendered = payload.values.map { "\($0)" }.joined(separator: " ")
            for secret in ["content://", "photo.jpeg", "image/jpeg"] {
                XCTAssertFalse(rendered.contains(secret), "a payload leaked '\(secret)'")
            }
        }
    }
}

/// Where a half-finished profile picks up (flow README rule 4a).
///
/// The Swift half of `ProfileResumeTest.kt`. The rule is one sentence -- "route straight to the
/// last incomplete step" -- and six ways to get wrong, so every gap is checked, plus the two cases
/// that are not gaps: the bridge, which must never be resumed onto, and a finished profile.
final class ProfileResumeTests: XCTestCase {

    private let complete = ProfileProgress(
        displayName: "Leo",
        email: "leo@hey.com",
        emailVerified: true,
        hasDateOfBirth: true,
        photoCount: photosRequired,
        promptCount: promptsRequired)

    func testABrandNewAccountStartsAtTheName() {
        XCTAssertEqual(resumePoint(ProfileProgress()), .name)
    }

    func testANameButNoAddressHoldsAtTheEmailStep() {
        var progress = complete
        progress.email = nil
        progress.emailVerified = false
        XCTAssertEqual(resumePoint(progress), .email)
    }

    func testAnUnconfirmedAddressHoldsAtTheCodeScreenNotTheEmailScreen() {
        // The address is already stored; sending the user back to re-type it would lose the
        // challenge that is in flight. The progress bar holds at 2 for the same reason.
        var progress = complete
        progress.emailVerified = false
        XCTAssertEqual(resumePoint(progress), .verifyEmail)
    }

    func testAVerifiedAddressWithNoDateOfBirthHoldsAtTheDate() {
        var progress = complete
        progress.hasDateOfBirth = false
        XCTAssertEqual(resumePoint(progress), .dob)
    }

    func testAFinishedBasicsSectionGoesToPhotosAndNeverToTheBridge() {
        // SHOWUP-155: "relaunching lands on photos and not on this bridge".
        var progress = complete
        progress.photoCount = 0
        progress.promptCount = 0
        XCTAssertEqual(resumePoint(progress), .photos)
    }

    func testThreePhotosIsNotFour() {
        var progress = complete
        progress.photoCount = photosRequired - 1
        progress.promptCount = 0
        XCTAssertEqual(resumePoint(progress), .photos)
    }

    func testFourPhotosAndNoPromptHoldsAtPrompts() {
        var progress = complete
        progress.promptCount = 0
        XCTAssertEqual(resumePoint(progress), .prompts)
    }

    func testOnePromptIsEnoughToBeFinished() {
        XCTAssertEqual(resumePoint(complete), .done)
    }

    func testABlankNameIsNoName() {
        // The screen refuses whitespace, but a value that arrived some other way must not skip the
        // step -- otherwise the user lands on email with an empty profile behind them.
        var progress = complete
        progress.displayName = "   "
        XCTAssertEqual(resumePoint(progress), .name)
    }

    func testTheOrderIsTheRuleSoAnEarlyGapWinsOverALaterOne() {
        var progress = complete
        progress.displayName = nil
        XCTAssertEqual(resumePoint(progress), .name)
    }

    func testEveryStepIsReachableAsAResumePoint() {
        // A guard against a future reorder quietly making one unreachable -- which would mean a
        // user could get stuck on a step the resume never returns them to.
        var noEmail = complete; noEmail.email = nil
        var unverified = complete; unverified.emailVerified = false
        var noDob = complete; noDob.hasDateOfBirth = false
        var noPhotos = complete; noPhotos.photoCount = 0
        var noPrompts = complete; noPrompts.promptCount = 0
        let reached = Set([
            resumePoint(ProfileProgress()),
            resumePoint(noEmail),
            resumePoint(unverified),
            resumePoint(noDob),
            resumePoint(noPhotos),
            resumePoint(noPrompts),
            resumePoint(complete),
        ])
        XCTAssertEqual(reached, Set(ResumePoint.allCases))
    }
}
