// ServerDateTranscoderTests.swift
// ShowUp · the server writes dates with milliseconds (`toISOString()`), and the runtime's default
// transcoder rejects them -- every server date failed to decode until this existed (SHOWUP-195).

import XCTest
import ShowUpAPI

final class ServerDateTranscoderTests: XCTestCase {

    private let transcoder = ServerDateTranscoder()
    /// 2026-11-03T17:58:00Z
    private let instant = 1_793_728_680.0

    func testDecodesTheServersMillisecondForm() throws {
        let date = try transcoder.decode("2026-11-03T17:58:00.000Z")
        XCTAssertEqual(date.timeIntervalSince1970, instant, accuracy: 0.0005)
    }

    func testKeepsTheFractionOfASecond() throws {
        let date = try transcoder.decode("2026-11-03T17:58:00.123Z")
        XCTAssertEqual(date.timeIntervalSince1970, instant + 0.123, accuracy: 0.0005)
    }

    func testStillDecodesTheFormWithoutFractionalSeconds() throws {
        let date = try transcoder.decode("2026-11-03T17:58:00Z")
        XCTAssertEqual(date.timeIntervalSince1970, instant, accuracy: 0.0005)
    }

    func testEncodesWithMillisecondsInUTC() throws {
        let text = try transcoder.encode(Date(timeIntervalSince1970: instant + 0.5))
        XCTAssertEqual(text, "2026-11-03T17:58:00.500Z")
    }

    func testRejectsGarbage() {
        for bad in ["", "not a date", "2026-11-03", "17:58:00"] {
            XCTAssertThrowsError(try transcoder.decode(bad), bad)
        }
    }
}
