//
//  DevOfflineUploadsTests.swift
//  ShowUp · the debug upload stand-ins never fail on their own (SHOWUP-156 / SHOWUP-161)
//
//  The Swift half of `DevOfflineUploadsTest.kt`. Both stand-ins used to fail every third upload on
//  purpose, and on a phone with no server to reach that read as a real bug: the third photo failed
//  every time and Retry fixed it, with nothing on screen saying the failure was staged.
//
//  THREE IN A ROW, from wherever the shared counter is: any three consecutive uploads include one
//  whose number is a multiple of three, so this fails against the old stand-ins whatever ran first.
//  Real time, not a test clock -- the actors sleep -- so three each, not six (about 9 seconds).
//

import XCTest
@testable import ShowUpWelcome

final class DevOfflineUploadsTests: XCTestCase {

    func testThePhotoStandInStoresEveryUploadTheThirdIncluded() async {
        await DevOfflinePhotos.shared.reset()
        for n in 1...3 {
            let result = await DevOfflinePhotos.shared.upload { _ in }
            guard case .stored = result else { return XCTFail("upload \(n) was \(result)") }
        }
        await DevOfflinePhotos.shared.reset()
    }

    func testTheMediaStandInStoresEveryUploadTheThirdIncluded() async {
        for n in 1...3 {
            let result = await DevOfflineMedia.shared.upload(
                kind: .video, promptId: "relaxed_and_happy", durationMs: 9_400, onProgress: { _ in })
            guard case .stored = result else { return XCTFail("upload \(n) was \(result)") }
        }
    }
}
