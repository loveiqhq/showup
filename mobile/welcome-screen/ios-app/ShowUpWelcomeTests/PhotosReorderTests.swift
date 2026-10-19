//
//  PhotosReorderTests.swift
//  ShowUp · when the dragged order is sent, and what happens when the server refuses it
//  (SHOWUP-156)
//
//  The Swift half of `PhotosUploadStateTest.kt`'s reorder section. The same rules in the same
//  order, because the thing being protected is that the two platforms agree: the order the user
//  drags the grid into is the order everyone else sees their photos in, and a rule that holds on
//  one platform and not the other is how a screen drifts.
//
//  There is no test dispatcher here as there is on Android, so the model's tasks are waited for
//  rather than stepped. `until` spins on the cooperative executor with a bound, which is honest
//  about what it does and cannot hang a suite.
//

import XCTest
@testable import ShowUpWelcome

/// Records what the model asked for, and answers what a test tells it to.
private actor PhotosSpy {
    private(set) var orders: [[String]] = []
    private var uploads = 0
    private var held = false
    private var reorderAnswer: ReorderPhotosResult?
    private var listAnswer: [StoredPhoto]?

    /// Holds every upload until `release`, so a test can drag a grid that is mid-upload.
    func hold() { held = true }
    func release() { held = false }
    func answerReorder(with answer: ReorderPhotosResult?) { reorderAnswer = answer }
    func answerList(with photos: [StoredPhoto]?) { listAnswer = photos }

    func upload() async -> UploadPhotoResult {
        // The actor is reentrant at this suspension point, which is what lets `release` arrive.
        while held { await Task.yield() }
        uploads += 1
        return .stored(StoredPhoto(id: "id-\(uploads)", url: "u", position: uploads - 1))
    }

    func list() -> [StoredPhoto]? { listAnswer }

    func reorder(_ ids: [String]) -> ReorderPhotosResult {
        orders.append(ids)
        return reorderAnswer ?? .stored(
            ids.enumerated().map { StoredPhoto(id: $1, url: "u", position: $0) })
    }
}

private struct SpyRepo: PhotosRepositoring {
    let spy: PhotosSpy

    func upload(bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult {
        await spy.upload()
    }

    func list() async -> [StoredPhoto]? { await spy.list() }
    func remove(id: String) async -> RemovePhotoResult { .removed }
    func reorder(ids: [String]) async -> ReorderPhotosResult { await spy.reorder(ids) }
}

@MainActor
final class PhotosReorderTests: XCTestCase {

    private var spy = PhotosSpy()
    private var model: PhotosModel!

    override func setUp() {
        super.setUp()
        spy = PhotosSpy()
        model = PhotosModel(repo: SpyRepo(spy: spy), access: FixedPhotoAccess())
    }

    /// Waits for something the model does on a task of its own. Bounded, so a broken rule fails
    /// rather than hangs.
    private func until(_ condition: @MainActor () async -> Bool,
                       _ what: String,
                       file: StaticString = #filePath,
                       line: UInt = #line) async {
        for _ in 0..<2_000 {
            if await condition() { return }
            await Task.yield()
        }
        XCTFail("never happened: \(what)", file: file, line: line)
    }

    private func pick(slot: Int) async {
        model.tapSlot(slot)
        model.picked(
            PickedBytes(bytes: Data([0xff, 0xd8]), mimeType: "image/jpeg", fileName: "p.jpg"),
            source: .library)
        await until({ self.model.grid.at(slot)?.status == .confirmed }, "slot \(slot) confirmed")
    }

    private func remoteIds() -> [String] { model.grid.photos.compactMap(\.remoteId) }

    // MARK: sending

    func testADragSendsTheWholeOrderNotThePairThatMoved() async {
        for slot in 0..<3 { await pick(slot: slot) }
        let ids = remoteIds()

        model.reorder(from: 2, to: 0)
        // The tile has already moved, before anything has been sent. A grid that waited for the
        // round trip would read as the drag having failed.
        // A SWAP, not a shuffle. The grid is six fixed boxes and any of them may be empty, so
        // "take it out and push everything along" has nothing to mean — there is nothing to push
        // into an empty box. Box 0 and box 2 trade places and box 1 does not move.
        XCTAssertEqual(remoteIds(), [ids[2], ids[1], ids[0]])

        await until({ await self.spy.orders.count == 1 }, "the order was sent")
        let sent = await spy.orders
        // One call, carrying the complete list. Two drags racing as two diffs is exactly what
        // sending the whole order avoids.
        XCTAssertEqual(sent.first, [ids[2], ids[1], ids[0]])
    }

    func testDeletingTheSecondPhotoLeavesTheOthersExactlyWhereTheyWere() async {
        for slot in 0..<4 { await pick(slot: slot) }
        let ids = (0..<4).map { model.grid.at($0)?.remoteId }

        model.remove(1)

        // THE BUG THIS EXISTS FOR, reported from a device: the list used to close up, so deleting
        // the second photo slid the third and fourth one box left and the empty box appeared at
        // the END. It read as "the last one was deleted" and there was no way to put a new photo
        // back where the old one had been.
        XCTAssertEqual(model.grid.at(0)?.remoteId, ids[0])
        XCTAssertNil(model.grid.at(1))
        XCTAssertEqual(model.grid.at(2)?.remoteId, ids[2])
        XCTAssertEqual(model.grid.at(3)?.remoteId, ids[3])
        XCTAssertEqual(model.grid.confirmedCount, 3)
    }

    func testANewPhotoGoesIntoTheBoxThatWasEmptied() async {
        for slot in 0..<4 { await pick(slot: slot) }
        model.remove(1)
        await pick(slot: 1)
        // Back in the second box, not appended to the end.
        XCTAssertEqual(model.grid.at(1)?.status, .confirmed)
        XCTAssertEqual(model.grid.confirmedCount, 4)
        XCTAssertEqual(model.grid.photos.count, 4)
    }

    func testTheFirstFreeBoxIsTheGapNotTheEnd() async {
        for slot in 0..<4 { await pick(slot: slot) }
        XCTAssertEqual(model.grid.firstFreeSlot(), 4)
        model.remove(2)
        // A hole in the middle is the first place a photo should land.
        XCTAssertEqual(model.grid.firstFreeSlot(), 2)
    }

    func testADragThatChangesNothingSendsNothing() async {
        for slot in 0..<2 { await pick(slot: slot) }
        model.reorder(from: 1, to: 1)
        for _ in 0..<50 { await Task.yield() }
        let sent = await spy.orders
        XCTAssertTrue(sent.isEmpty)
    }

    func testADragMadeDuringAnUploadIsSentOnceTheUploadLands() async {
        for slot in 0..<2 { await pick(slot: slot) }
        await spy.hold()

        model.tapSlot(2)
        model.picked(
            PickedBytes(bytes: Data([0xff, 0xd8]), mimeType: "image/jpeg", fileName: "late.jpg"),
            source: .library)
        model.reorder(from: 1, to: 0)

        // Nothing yet: the route takes the complete set of STORED photos and one of these is not.
        let duringUpload = await spy.orders
        XCTAssertTrue(duringUpload.isEmpty)

        await spy.release()
        await until({ await self.spy.orders.count == 1 }, "the pending order was sent")
        // The drag made while the upload was in flight was not lost, which is the whole reason it
        // is remembered rather than dropped.
        let sent = await spy.orders
        XCTAssertEqual(sent.first, remoteIds())
    }

    // MARK: refusal

    func testARefusedOrderIsReplacedByTheOneTheServerReports() async {
        for slot in 0..<3 { await pick(slot: slot) }
        let ids = remoteIds()
        await spy.answerReorder(with: .failed)
        // The server no longer holds the middle photo — removed on another device, say.
        await spy.answerList(with: [
            StoredPhoto(id: ids[2], url: "u", position: 0),
            StoredPhoto(id: ids[0], url: "u", position: 1),
        ])

        model.reorder(from: 2, to: 0)
        await until({ self.remoteIds() == [ids[2], ids[0]] }, "the server's order was adopted")
    }

    func testARefusedOrderThatCannotBeReReadLeavesTheGridAlone() async {
        for slot in 0..<2 { await pick(slot: slot) }
        await spy.answerReorder(with: .failed)
        await spy.answerList(with: nil)

        model.reorder(from: 1, to: 0)
        await until({ await self.spy.orders.count == 1 }, "the order was attempted")
        for _ in 0..<50 { await Task.yield() }
        // Nothing answered, so there is nothing to adopt. Emptying a grid the user just filled
        // because the network dropped would be the worse bug.
        XCTAssertEqual(model.grid.photos.count, 2)
    }

    func testAdoptingTheServerOrderKeepsAPhotoThatIsStillUploading() async {
        for slot in 0..<2 { await pick(slot: slot) }
        let ids = remoteIds()
        await spy.answerReorder(with: .failed)
        await spy.answerList(with: [
            StoredPhoto(id: ids[1], url: "u", position: 0),
            StoredPhoto(id: ids[0], url: "u", position: 1),
        ])
        await spy.hold()

        model.reorder(from: 1, to: 0)
        // A new photo starts uploading before the refusal comes back.
        model.tapSlot(2)
        model.picked(
            PickedBytes(bytes: Data([0xff, 0xd8]), mimeType: "image/jpeg", fileName: "late.jpg"),
            source: .library)

        await until({ self.remoteIds() == [ids[1], ids[0]] }, "the server's order was adopted")
        // Three slots still: the in-flight one exists only here, so a read cannot know about it
        // and must not delete it.
        XCTAssertEqual(model.grid.photos.count, 3)
        await spy.release()
    }
}
