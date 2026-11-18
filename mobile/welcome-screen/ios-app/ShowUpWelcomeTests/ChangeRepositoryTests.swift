// ChangeRepositoryTests.swift
// ShowUp · what Date 05's routes answer and how the app reads them (SHOWUP-196). Android's
// `ChangeRepositoryTest.kt`: every assertion is made against bytes the generated client parsed.

import XCTest
import OpenAPIRuntime
import HTTPTypes
import ShowUpAPI
@testable import ShowUpWelcome

final class ChangeRepositoryTests: XCTestCase {

    /// Returns a canned response, whatever was asked.
    private struct StubTransport: ClientTransport {
        let status: Int
        let responseBody: String

        func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
                  operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
            (HTTPResponse(status: .init(code: status), headerFields: [.contentType: "application/json"]),
             responseBody.isEmpty ? nil : HTTPBody(responseBody))
        }
    }

    private func repo(status: Int, body: String) -> ChangeRepository {
        ChangeRepository(api: ShowUpAPI(tokens: InMemoryTokenStore(accessToken: "t"),
                                        transport: StubTransport(status: status, responseBody: body)))
    }

    private let request = SuggestRequest(kind: .time, time: Date(timeIntervalSince1970: 1_794_940_200), placeId: nil,
                                         entry: .chatChip, fromSearch: nil,
                                         clientId: "22222222-2222-4222-8222-222222222222", tz: "UTC")

    private let suggestion = #"{"id":"11111111-1111-4111-8111-111111111111","kind":"time","byYou":true,"time":"2026-11-17T18:30:00.000Z","wasTime":"2026-11-17T18:00:00.000Z","wasPlace":{"name":"Cafe Krone"},"deadline":"2026-11-17T17:30:00.000Z","createdAt":"2026-11-17T15:00:00.000Z","status":"pending"}"#

    func testAPlaceDistanceIsRoundedNotTruncated() async {
        let body = #"{"attribution":"google","places":[{"placeId":"p1","name":"Bar Lenz","type":"bar","address":"A 1","distanceMeters":350.6}]}"#
        guard case .success(let list) = await repo(status: 200, body: body).places(dateId: "d1", query: nil) else {
            return XCTFail("expected places")
        }
        XCTAssertTrue(list.google)
        XCTAssertEqual(list.places.first?.distanceMeters, 351)
        XCTAssertEqual(list.places.first?.type, .bar)
    }

    func testASuggestionIsSentAndReadBack() async {
        let result = await repo(status: 201, body: suggestion).suggest(dateId: "d1", request: request)
        guard case .sent(let sent) = result else { return XCTFail("expected sent, got \(result)") }
        XCTAssertEqual(sent.kind, .time)
        XCTAssertEqual(sent.status, .pending)
        XCTAssertEqual(sent.wasPlaceName, "Cafe Krone")
    }

    func testA409IsStaleNotAFailureThePersonCanRetry() async {
        let result = await repo(status: 409, body: #"{"message":"past the cutoff"}"#).suggest(dateId: "d1", request: request)
        XCTAssertEqual(result, .stale)
    }

    func testA503AndA500AreFailuresWhichKShows() async {
        let a = await repo(status: 503, body: #"{"message":"places down"}"#).suggest(dateId: "d1", request: request)
        XCTAssertEqual(a, .failed(.server))
        let b = await repo(status: 500, body: #"{"message":"boom"}"#).suggest(dateId: "d1", request: request)
        XCTAssertEqual(b, .failed(.server))
    }
}
