//
//  PhotoUploadTests.swift
//  ShowUp · the size a picked photo is decoded at (SHOWUP-156)
//
//  The Swift half of `PhotoUploadTest.kt`, assertion for assertion. The decode itself needs a
//  device; the arithmetic does not, and the arithmetic is where the bug was — a photo sent at full
//  resolution came back 413, and a HEIC one came back 415, and both rendered as `Upload failed`
//  with a Retry that re-sent the same bytes and failed the same way.
//

import XCTest
@testable import ShowUpWelcome

final class PhotoUploadTests: XCTestCase {

    func testAPhotoAlreadyWithinTheCapIsLeftAlone() {
        // Nil rather than the original size, so the caller does not resize to exactly what it was
        // about to produce anyway.
        XCTAssertNil(uploadTargetSize(width: 1600, height: 1200))
        XCTAssertNil(uploadTargetSize(width: uploadMaxEdge, height: uploadMaxEdge))
        XCTAssertNil(uploadTargetSize(width: 100, height: 100))
    }

    func testAPhotoOverTheCapIsScaledSoItsLongestEdgeIsTheCap() {
        // A 12 megapixel phone photo, which is what actually broke this.
        let size = uploadTargetSize(width: 4032, height: 3024)
        XCTAssertEqual(size?.width, uploadMaxEdge)
        XCTAssertEqual(size?.height, 1536)
    }

    func testPortraitScalesOnItsHeightNotItsWidth() {
        let size = uploadTargetSize(width: 3024, height: 4032)
        XCTAssertEqual(size?.height, uploadMaxEdge)
        XCTAssertEqual(size?.width, 1536)
        // The bug this guards against is scaling on `width` because it is written first: a
        // portrait photo would come out 2048 wide and 2731 tall, which is BIGGER than it started.
    }

    func testTheAspectRatioSurvives() {
        let size = uploadTargetSize(width: 4000, height: 2000)!
        XCTAssertEqual(Double(size.width) / Double(size.height), 2.0, accuracy: 0.01)
    }

    func testAnAbsurdlyWidePanoramaKeepsAShortEdgeOfAtLeastOnePixel() {
        // 8000 x 1 scales to 2048 x 0.00025, and a zero-height image is one the encoder refuses.
        // Absurd as a profile photo, and still not a crash.
        let size = uploadTargetSize(width: 8000, height: 1)
        XCTAssertEqual(size?.width, uploadMaxEdge)
        XCTAssertEqual(size?.height, 1)
    }

    func testAPhotoWithNoPixelsIsNothingToResize() {
        // What a decoder reports for a file whose header it could not read.
        XCTAssertNil(uploadTargetSize(width: 0, height: 0))
        XCTAssertNil(uploadTargetSize(width: -1, height: 100))
    }

    func testTheCapIsTheSameNumberTheAndroidSideUses() {
        // Stated here so a change to one platform fails a test rather than quietly making the two
        // apps produce photos of different quality. `verify-profile.py` checks the pair.
        XCTAssertEqual(uploadMaxEdge, 2048)
        XCTAssertEqual(uploadJPEGQuality, 0.9, accuracy: 0.0001)
    }

    func testTheResultAlwaysFitsInsideTheCapOnBothEdges() {
        // A property rather than an example: whatever goes in, neither edge comes out over the cap.
        for (w, h) in [(4032, 3024), (3024, 4032), (8000, 6000), (2049, 2049),
                       (5000, 100), (100, 5000)] {
            let size = uploadTargetSize(width: w, height: h)!
            XCTAssertLessThanOrEqual(size.width, uploadMaxEdge, "\(w) x \(h) produced a width")
            XCTAssertLessThanOrEqual(size.height, uploadMaxEdge, "\(w) x \(h) produced a height")
        }
    }
}
