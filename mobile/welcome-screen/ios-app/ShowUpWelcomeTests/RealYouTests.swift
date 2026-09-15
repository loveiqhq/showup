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
        XCTAssertEqual(topicFor("first_date")?.id, "first_date")
        XCTAssertEqual(topicText("first_date"), "On a first date, I usually…")
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
        XCTAssertEqual(suggestionsFor(used: ["first_date"], count: 2).count, 2)
    }

    func testAUsedTopicIsNeverSuggestedAgain() {
        let used = ["first_date", "weird_habit"]
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

    // MARK: prompt_id

    func testPromptIdIsTheTopicIdPlusTheSlotNumberedFromOne() {
        // §5's own prose and its own example: "prompt_id is topic_id plus the slot it occupies —
        // match_me_if_you__slot2 — so a topic moved between slots stays traceable".
        XCTAssertEqual(promptId(topicId: "first_date", slot: 0), "first_date__slot1")
        XCTAssertEqual(promptId(topicId: "hot_take", slot: 2), "hot_take__slot3")
    }

    func testNoPromptPayloadCanCarryAnAnswer() {
        let answer = "Talk about anything real."
        let (_, payload) = ProfileAnalytics.promptAnswered(
            promptId: promptId(topicId: "first_date", slot: 0),
            charCount: answer.count, atCharLimit: false)
        let rendered = payload.values.map { "\($0)" }.joined(separator: " ")
        XCTAssertFalse(rendered.contains("anything real"), "the answer reached a payload")
        XCTAssertEqual(payload["char_count"] as? Int, answer.count)
    }

    // MARK: the state the screen is a function of

    func testADraftBelongsToATopicSoDismissingAndReopeningRestoresIt() {
        var state = PromptsState(sheet: .write(topicId: "first_date", editing: false),
                                 drafts: ["first_date": "half a sentence"])
        state.sheet = nil
        XCTAssertEqual(state.draftFor("first_date"), "half a sentence")
        state.sheet = .write(topicId: "first_date", editing: false)
        XCTAssertEqual(state.draftFor("first_date"), "half a sentence")
        // And a different topic starts empty.
        XCTAssertEqual(state.draftFor("hot_take"), "")
    }

    func testOnePromptIsEnoughToContinueAndNoneIsNot() {
        XCTAssertFalse(PromptsState().canContinue)
        XCTAssertTrue(PromptsState(prompts: [SavedPrompt(topicId: "first_date", answer: "x")])
            .canContinue)
    }

    func testTheSavedStateSurvivesARoundTripThroughItsEncoding() {
        let state = PromptsState(
            prompts: [SavedPrompt(topicId: "first_date", answer: "an answer")],
            sheet: .write(topicId: "hot_take", editing: true),
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
        XCTAssertEqual(prompts["screen_name"] as? String, "Profile - Prompts")
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
