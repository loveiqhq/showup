//  PhotosTrackingTests.swift
//  ShowUp · the photos screen reports itself and its refused Continue (SHOWUP-156)
//
//  The slot, upload and minimum events already fired; the screen itself and the refused press did
//  not, until 8 October 2026. Android twin: the two tests at the end of `PhotosUploadStateTest.kt`.

import XCTest
@testable import ShowUpWelcome

@MainActor
private final class Recorder: AnalyticsTracking {
    private(set) var events: [(String, [String: any Sendable])] = []
    nonisolated init() {}
    nonisolated func track(_ event: String, properties: [String: any Sendable]) {
        MainActor.assumeIsolated { events.append((event, properties)) }
    }
    var names: [String] { events.map(\.0) }
    func props(_ name: String) -> [String: any Sendable]? { events.first { $0.0 == name }?.1 }
}

/// Answers nothing interesting: these tests never upload.
private struct QuietRepo: PhotosRepositoring {
    func upload(bytes: Data, mimeType: String, fileName: String,
                onProgress: @Sendable (Double) -> Void) async -> UploadPhotoResult { .failed }
    func list() async -> [StoredPhoto]? { nil }
    func remove(id: String) async -> RemovePhotoResult { .removed }
    func reorder(ids: [String]) async -> ReorderPhotosResult { .failed }
}

@MainActor
final class PhotosTrackingTests: XCTestCase {

    func testArrivingReportsTheScreenAndTheStep() {
        let recorder = Recorder()
        let model = PhotosModel(repo: QuietRepo(), access: FixedPhotoAccess(), analytics: recorder)

        model.arrived(referrer: .embraceBuild)

        XCTAssertEqual(recorder.names, ["screen_viewed", "profile_step_viewed"])
        XCTAssertEqual(recorder.props("screen_viewed")?["screen_id"] as? String, "profile_photos")
        XCTAssertEqual(recorder.props("screen_viewed")?["screen_name"] as? String, "ProfilePhotos")
        XCTAssertEqual(recorder.props("screen_viewed")?["referrer_screen_id"] as? String, "profile_embrace_build")
        XCTAssertEqual(recorder.props("profile_step_viewed")?["step_id"] as? String, "photos")
    }

    func testContinueBelowFourReportsPhotosBelowMinimumOnThePress() {
        let recorder = Recorder()
        let model = PhotosModel(repo: QuietRepo(), access: FixedPhotoAccess(), analytics: recorder)

        model.continueRefused()

        let failed = recorder.props("form_validation_failed")
        XCTAssertEqual(failed?["field_id"] as? String, "photos")
        XCTAssertEqual(failed?["rule"] as? String, "photos_below_minimum")
        XCTAssertEqual(failed?["screen_id"] as? String, "profile_photos")
        XCTAssertEqual(failed?["step_id"] as? String, "photos")
    }
}
